package com.fivedoorsescape.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.fivedoorsescape.assets.AssetService;
import com.fivedoorsescape.content.ExteriorDefinition;

import net.mgsx.gltf.scene3d.attributes.FogAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.lights.PointLightEx;
import net.mgsx.gltf.scene3d.lights.SpotLightEx;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import net.mgsx.gltf.scene3d.scene.SceneSkybox;

/**
 * Salida fisica de la pizzeria para la victoria de Escape: la puerta de salida existente se abre de verdad hacia el
 * deposito trasero, en la pared oeste del deposito hay una puerta de emergencia (con su letrero EXIT) y detras esta el
 * estacionamiento con el auto. Todo vive en el MISMO mundo que el interior (sin cambio de escena ni pantalla de carga):
 * el exterior se carga con la partida y queda oculto tras paredes hasta que se abren las puertas.
 *
 * pizzeria.glb no se modifica en disco. En prepararMapa(), antes de generar colisiones, se vuelven degenerados los
 * triangulos de la pared oeste del deposito (un unico plano sin grosor en x=wallX) y del estante que la tapaba; en su
 * lugar se dibuja un panel propio con el hueco de la puerta, con el MISMO material de las paredes (main_material).
 *
 * Disposicion (posiciones, porton, faroles, props) en content/maps/exterior.json, compartida con el script de Blender
 * que construye models/exterior_parking.glb. Ver CLAUDE.md §2.49.
 */
public class SalidaExterior implements Disposable {

    public static final String SONIDO_AMBIENTE_NOCHE = "audio/ambience/night_parking_lot.wav";
    public static final String SONIDO_PUERTA_SALIDA = "audio/sfx/exit_door_unlock.wav";
    public static final String SONIDO_PUERTA_EMERGENCIA = "audio/sfx/emergency_door_open.wav";

    private static final float ANGULO_PUERTA_SALIDA = 92f;
    private static final float DURACION_PUERTA_SALIDA = 1.6f;
    private static final float ANGULO_PUERTA_EMERGENCIA = 98f;
    private static final float DURACION_PUERTA_EMERGENCIA = 0.55f;
    private static final float ANGULO_PUERTA_AUTO = -64f;
    private static final float ANGULO_BARRERA = 82f;
    private static final float RADIO_RUEDA = 0.30f;

    private final ExteriorDefinition def;
    private final AssetService assets;

    private Scene escenaExterior;
    private Scene escenaAuto;
    private Model modeloPanel;
    private ModelInstance panel;
    private Cubemap cieloNocturno;
    private SceneSkybox skyboxNocturno;

    private Node nodoPuertaSalida;
    private final Matrix4 puertaSalidaGlobalCerrada = new Matrix4();
    private final Matrix4 puertaSalidaPadreInv = new Matrix4();
    private Node nodoPuertaEmergencia;
    private final Quaternion rotPuertaEmergenciaCerrada = new Quaternion();
    private Node nodoBarrera;
    private final Quaternion rotBarreraCerrada = new Quaternion();
    private Node nodoPuertaAuto;
    private final Quaternion rotPuertaAutoCerrada = new Quaternion();
    private final Array<Node> ruedasAuto = new Array<>();

    private float tPuertaSalida = -1f;
    private float tPuertaEmergencia = -1f;
    private float aperturaPuertaAuto = 0f;
    private float aperturaBarrera = 0f;
    private float giroRuedas = 0f;

    private BoundingBox colliderPuertaEmergencia;
    private BoundingBox colliderBarrera;
    private BoundingBox colliderAuto;
    private final Array<BoundingBox> paredesLimiteOriginales = new Array<>();
    private CollisionWorld collisionWorld;

    private final Array<SpotLightEx> faroles = new Array<>();
    private final Array<Float> intensidadFaroles = new Array<>();
    private PointLightEx luzExitInterior;
    private PointLightEx luzExitExterior;
    private PointLightEx luzLamparaPared;
    private SpotLightEx faroIzquierdo;
    private SpotLightEx faroDerecho;
    /** Resplandor rojo de las luces traseras (CLAUDE.md §2.68): ilumina a Freddy cuando persigue al auto. */
    private PointLightEx luzTraseras;
    private Material materialFaros;
    private Material materialTraseras;
    private Material materialIndicadores;
    private float tiempo = 0f;
    private boolean exteriorActivo = false;

    /** Posicion y orientacion actuales del auto (se mueven durante la cinematica). */
    public final Vector3 posicionAuto = new Vector3();
    public float yawAuto;

    private int triangulosParedRetirados;
    private int triangulosEstanteRetirados;

    public SalidaExterior(ExteriorDefinition def, AssetService assets) {
        this.def = def;
        this.assets = assets;
        posicionAuto.set(def.carX, -0.10f, def.carZ);
        yawAuto = def.carYawDegrees;
    }

    public ExteriorDefinition getDefinicion() {
        return def;
    }

    // ------------------------------------------------------------------ mapa y colisiones

    /** Abre el hueco de la pared oeste del deposito y retira el estante que la tapaba. Llamar ANTES de
     * LevelLoader.buildStaticColliders: las colisiones se generan de los triangulos, asi que el hueco y el estante
     * desaparecen tambien de la colision sin tocar LevelLoader. */
    public void prepararMapa(Scene mapScene) {
        mapScene.modelInstance.calculateTransforms();
        Matrix4 instancia = mapScene.modelInstance.transform;
        for (Node raiz : mapScene.modelInstance.nodes) {
            retirarTriangulos(raiz, instancia);
        }
        Gdx.app.log("SalidaExterior", "Triangulos retirados: pared oeste del deposito " + triangulosParedRetirados
                + ", estante " + triangulosEstanteRetirados);
        nodoPuertaSalida = mapScene.modelInstance.getNode(def.exitDoorNode, true);
        if (nodoPuertaSalida != null) {
            puertaSalidaGlobalCerrada.set(instancia).mul(nodoPuertaSalida.globalTransform);
            Matrix4 padre = new Matrix4(instancia);
            if (nodoPuertaSalida.hasParent()) {
                padre.mul(nodoPuertaSalida.getParent().globalTransform);
            }
            puertaSalidaPadreInv.set(padre).inv();
        } else {
            Gdx.app.log("SalidaExterior", "ADVERTENCIA: no se encontro la hoja de la puerta de salida " + def.exitDoorNode);
        }
    }

    private void retirarTriangulos(Node nodo, Matrix4 instancia) {
        Matrix4 mundo = new Matrix4(instancia).mul(nodo.globalTransform);
        Vector3 a = new Vector3(), b = new Vector3(), c = new Vector3();
        for (NodePart parte : nodo.parts) {
            String material = parte.material != null ? parte.material.id : "";
            boolean esPared = "main_material".equals(material);
            boolean esEstante = "Estantes_MAdera".equals(material);
            if (!esPared && !esEstante) {
                continue;
            }
            Mesh mesh = parte.meshPart.mesh;
            VertexAttribute pos = mesh.getVertexAttribute(VertexAttributes.Usage.Position);
            int paso = mesh.getVertexSize() / 4;
            int desp = pos.offset / 4;
            float[] vertices = new float[mesh.getNumVertices() * paso];
            mesh.getVertices(vertices);
            short[] indices = new short[mesh.getNumIndices()];
            mesh.getIndices(indices);
            boolean cambio = false;
            int ini = parte.meshPart.offset;
            int fin = ini + parte.meshPart.size;
            for (int i = ini; i + 2 < fin; i += 3) {
                leer(vertices, indices[i] & 0xFFFF, paso, desp, a).mul(mundo);
                leer(vertices, indices[i + 1] & 0xFFFF, paso, desp, b).mul(mundo);
                leer(vertices, indices[i + 2] & 0xFFFF, paso, desp, c).mul(mundo);
                boolean retirar = esPared ? caraDelPanel(a, b, c) : enEstante(a) && enEstante(b) && enEstante(c);
                if (retirar) {
                    indices[i + 1] = indices[i];
                    indices[i + 2] = indices[i];
                    cambio = true;
                    if (esPared) {
                        triangulosParedRetirados++;
                    } else {
                        triangulosEstanteRetirados++;
                    }
                }
            }
            if (cambio) {
                mesh.setIndices(indices);
            }
        }
        for (Node hijo : nodo.getChildren()) {
            retirarTriangulos(hijo, instancia);
        }
    }

    /** Triangulo vertical de la cara exterior (wallX) o de la interior (wallInnerX) dentro del tramo del panel. Los tres
     * vertices deben estar en el MISMO plano: asi las tapas superior e inferior de la pared gruesa (que unen las dos
     * caras) se conservan y la hoja de la puerta queda en un "tunel" con piso y techo propios. */
    private boolean caraDelPanel(Vector3 a, Vector3 b, Vector3 c) {
        boolean exterior = Math.abs(a.x - def.wallX) < 0.02f && Math.abs(b.x - def.wallX) < 0.02f && Math.abs(c.x - def.wallX) < 0.02f;
        boolean interior = Math.abs(a.x - def.wallInnerX) < 0.02f && Math.abs(b.x - def.wallInnerX) < 0.02f
                && Math.abs(c.x - def.wallInnerX) < 0.02f;
        return (exterior || interior) && enTramo(a) && enTramo(b) && enTramo(c);
    }

    private boolean enTramo(Vector3 p) {
        return p.z >= def.wallPanelMinZ - 0.01f && p.z <= def.wallPanelMaxZ + 0.01f && p.y >= -0.01f && p.y <= def.wallPanelTop + 0.01f;
    }

    private boolean enEstante(Vector3 p) {
        return p.x >= def.shelfRemoveMinX && p.x <= def.shelfRemoveMaxX && p.z >= def.shelfRemoveMinZ && p.z <= def.shelfRemoveMaxZ;
    }

    private static Vector3 leer(float[] v, int i, int paso, int desp, Vector3 out) {
        int base = i * paso + desp;
        return out.set(v[base], v[base + 1], v[base + 2]);
    }

    public int getTriangulosParedRetirados() {
        return triangulosParedRetirados;
    }

    public int getTriangulosEstanteRetirados() {
        return triangulosEstanteRetirados;
    }

    /** Colisiones propias: panel de la pared con su hueco, hoja cerrada de la puerta de emergencia, valla, barrera del
     * porton, auto, faroles y props. El exterior queda inaccesible hasta que se abren las dos puertas, asi que todo se
     * agrega desde el principio. Las 4 paredes limite sinteticas de LevelLoader se guardan para recortarlas en
     * activarExterior(). */
    public void agregarColisionadores(CollisionWorld mundo, Array<BoundingBox> paredesLimite) {
        this.collisionWorld = mundo;
        paredesLimiteOriginales.addAll(paredesLimite);
        float x0 = def.wallX - 0.16f, x1 = def.wallInnerX + 0.04f;
        float z0 = def.emergencyDoorZ - def.emergencyDoorWidth / 2f;
        float z1 = def.emergencyDoorZ + def.emergencyDoorWidth / 2f;
        // Las tapas superior (y=2.96) e inferior (y=0) de la pared gruesa se conservan, pero LevelLoader las junta en
        // la misma celda de 0.2 m y la celda queda de altura completa: se retiran las del hueco de la puerta (medido:
        // sin esto el jugador se detenia en la cara interior aunque las dos caras ya no existieran).
        Array<BoundingBox> tunel = new Array<>();
        Vector3 centro = new Vector3();
        for (BoundingBox c : mundo.getStaticColliders()) {
            c.getCenter(centro);
            if (centro.x > def.wallX - 0.1f && centro.x < def.wallInnerX + 0.1f && centro.z > z0 + 0.02f && centro.z < z1 - 0.02f) {
                tunel.add(c);
            }
        }
        for (BoundingBox c : tunel) {
            mundo.removeStaticCollider(c);
        }
        mundo.addStaticCollider(caja(x0, 0f, def.wallPanelMinZ, x1, def.wallPanelTop, z0));
        mundo.addStaticCollider(caja(x0, 0f, z1, x1, def.wallPanelTop, def.wallPanelMaxZ));
        // la hoja cerrada esta en la cara exterior: se puede entrar al hueco de la pared hasta la barra
        colliderPuertaEmergencia = caja(x0, 0f, z0, def.wallX + 0.12f, def.emergencyDoorHeight, z1);
        mundo.addStaticCollider(colliderPuertaEmergencia);

        float e = 0.12f;
        mundo.addStaticCollider(caja(def.lotMinX - e, 0f, def.lotMinZ - e, def.gateMinX, 2.4f, def.lotMinZ + e));
        mundo.addStaticCollider(caja(def.gateMaxX, 0f, def.lotMinZ - e, def.wallX, 2.4f, def.lotMinZ + e));
        mundo.addStaticCollider(caja(def.lotMinX - e, 0f, def.lotMinZ, def.lotMinX + e, 2.4f, def.lotMaxZ + e));
        mundo.addStaticCollider(caja(def.lotMinX, 0f, def.lotMaxZ - e, def.northFaceX, 2.4f, def.lotMaxZ + e));
        mundo.addStaticCollider(caja(def.wallX - e, 0f, def.lotMinZ, def.wallX + e, 2.4f, -7.9f));
        colliderBarrera = caja(def.gateMinX, 0f, def.lotMinZ - 0.2f, def.gateMaxX, 1.2f, def.lotMinZ + 0.2f);
        mundo.addStaticCollider(colliderBarrera);

        colliderAuto = cajaGirada(def.carX, def.carZ, def.carHalfWidth, def.carHalfLength, def.carYawDegrees, 1.5f);
        mundo.addStaticCollider(colliderAuto);
        for (ExteriorDefinition.Lamp farol : def.lamps) {
            mundo.addStaticCollider(caja(farol.x - 0.22f, 0f, farol.z - 0.22f, farol.x + 0.22f, 3.8f, farol.z + 0.22f));
        }
        for (float[] c : def.extraColliders) {
            mundo.addStaticCollider(cajaGirada(c[0], c[1], c[2], c[3], 0f, 1.4f));
        }
        for (ExteriorDefinition.Prop prop : def.props) {
            if (prop.collider != null) {
                mundo.addStaticCollider(cajaGirada(prop.x, prop.z, prop.collider[0], prop.collider[1], prop.yaw, 1.4f));
            }
        }
    }

    private static BoundingBox caja(float x0, float y0, float z0, float x1, float y1, float z1) {
        return new BoundingBox(new Vector3(Math.min(x0, x1), y0, Math.min(z0, z1)), new Vector3(Math.max(x0, x1), y1, Math.max(z0, z1)));
    }

    /** Caja alineada que envuelve un rectangulo local [hx, hz] girado yaw grados (mismo convenio que RenderSyncSystem). */
    private static BoundingBox cajaGirada(float x, float z, float hx, float hz, float yaw, float alto) {
        float c = Math.abs(MathUtils.cosDeg(yaw)), s = Math.abs(MathUtils.sinDeg(yaw));
        float ex = hx * c + hz * s, ez = hx * s + hz * c;
        return caja(x - ex, 0f, z - ez, x + ex, alto, z + ez);
    }

    /** Retira los colliders del hueco de la puerta de salida (cajas cuyo centro cae en el hueco, a cualquier altura):
     * las genero LevelLoader a partir de sus triangulos, porque la puerta de salida es la unica que se excluye del
     * despejado automatico de puertas. Igual que ese despejado, ignora la altura: la caja del jugador va centrada en
     * los ojos (y=1.6) y llega a ~2.5 m, asi que el dintel (1.85 m) tambien la bloquearia. Las celdas del borde del
     * hueco mezclan hoja y jamba; sin ellas el paso medido quedaba en 0.6 m, justo el ancho del jugador. */
    private int liberarHuecoPuertaSalida() {
        Array<BoundingBox> retirar = new Array<>();
        Vector3 centro = new Vector3();
        for (BoundingBox caja : collisionWorld.getStaticColliders()) {
            caja.getCenter(centro);
            if (centro.x >= def.exitDoorMinX && centro.x <= def.exitDoorMaxX && centro.z >= def.exitDoorMinZ
                    && centro.z <= def.exitDoorMaxZ) {
                retirar.add(caja);
            }
        }
        for (BoundingBox caja : retirar) {
            collisionWorld.removeStaticCollider(caja);
        }
        Gdx.app.log("SalidaExterior", "Colliders retirados del hueco de la puerta de salida: " + retirar.size);
        return retirar.size;
    }

    // ------------------------------------------------------------------ escena

    /** Agrega el exterior, el auto, el panel de pared y las luces a la escena. Las luces del estacionamiento empiezan
     * apagadas: no tienen sombras y atravesarian las paredes; se encienden al abrir la puerta de emergencia. */
    public void agregarAEscena(SceneManager sceneManager, Scene mapScene) {
        escenaExterior = new Scene(assets.getModel(def.modelPath).scene);
        sceneManager.addScene(escenaExterior);
        nodoPuertaEmergencia = escenaExterior.modelInstance.getNode("emergency_door_leaf", true);
        if (nodoPuertaEmergencia != null) {
            rotPuertaEmergenciaCerrada.set(nodoPuertaEmergencia.rotation);
        }
        nodoBarrera = escenaExterior.modelInstance.getNode("gate_barrier_arm", true);
        if (nodoBarrera != null) {
            rotBarreraCerrada.set(nodoBarrera.rotation);
        }
        for (Material m : escenaExterior.modelInstance.materials) {
            m.set(IntAttribute.createCullFace(0));
        }

        escenaAuto = new Scene(assets.getModel(def.carModelPath).scene);
        sceneManager.addScene(escenaAuto);
        nodoPuertaAuto = escenaAuto.modelInstance.getNode("car_door_driver", true);
        if (nodoPuertaAuto != null) {
            rotPuertaAutoCerrada.set(nodoPuertaAuto.rotation);
        }
        for (String rueda : new String[] {"car_wheel_fl", "car_wheel_fr", "car_wheels_rear"}) {
            Node n = escenaAuto.modelInstance.getNode(rueda, true);
            if (n != null) {
                ruedasAuto.add(n);
            }
        }
        materialFaros = escenaAuto.modelInstance.getMaterial("car_headlight");
        materialTraseras = escenaAuto.modelInstance.getMaterial("car_taillight");
        materialIndicadores = escenaAuto.modelInstance.getMaterial("car_gauges");
        for (Material m : escenaAuto.modelInstance.materials) {
            m.set(IntAttribute.createCullFace(0));
        }
        actualizarTransformAuto();

        Material pared = mapScene.modelInstance.getMaterial("main_material");
        construirPanel(pared != null ? pared : new Material(ColorAttribute.createDiffuse(Color.DARK_GRAY)));
        sceneManager.getRenderableProviders().add(panel);

        for (ExteriorDefinition.Lamp farol : def.lamps) {
            SpotLightEx luz = new SpotLightEx();
            luz.setColor(1f, 0.71f, 0.40f, 1f);
            luz.position.set(farol.x, farol.height - 0.15f, farol.z);
            luz.direction.set(0f, -1f, 0f);
            luz.setConeDeg(72f, 34f);
            luz.range = 12f;
            luz.intensity = 0f;
            faroles.add(luz);
            intensidadFaroles.add(farol.intensity);
            sceneManager.environment.add(luz);
        }
        luzExitInterior = new PointLightEx();
        luzExitInterior.setColor(1f, 0.12f, 0.08f, 1f);
        luzExitInterior.position.set(def.wallInnerX + 0.35f, def.emergencyDoorHeight + 0.25f, def.emergencyDoorZ);
        luzExitInterior.range = 2.8f;
        luzExitInterior.intensity = 1.6f;
        sceneManager.environment.add(luzExitInterior);
        luzExitExterior = new PointLightEx();
        luzExitExterior.setColor(1f, 0.12f, 0.08f, 1f);
        luzExitExterior.position.set(def.wallX - 0.45f, def.emergencyDoorHeight + 0.25f, def.emergencyDoorZ);
        luzExitExterior.range = 2.6f;
        luzExitExterior.intensity = 0f;
        sceneManager.environment.add(luzExitExterior);
        luzLamparaPared = new PointLightEx();
        luzLamparaPared.setColor(1f, 0.86f, 0.62f, 1f);
        luzLamparaPared.position.set(def.wallLamp.x - 0.25f, def.wallLamp.y - 0.1f, def.wallLamp.z);
        luzLamparaPared.range = 5f;
        luzLamparaPared.intensity = 0f;
        sceneManager.environment.add(luzLamparaPared);
        faroIzquierdo = crearFaroAuto(sceneManager);
        faroDerecho = crearFaroAuto(sceneManager);
        luzTraseras = new PointLightEx();
        luzTraseras.setColor(1f, 0.06f, 0.04f, 1f);
        luzTraseras.range = 7f;
        luzTraseras.intensity = 0f;
        sceneManager.environment.add(luzTraseras);
    }

    private SpotLightEx crearFaroAuto(SceneManager sceneManager) {
        SpotLightEx faro = new SpotLightEx();
        faro.setColor(0.95f, 0.95f, 1f, 1f);
        faro.setConeDeg(34f, 18f);
        faro.range = 22f;
        faro.intensity = 0f;
        sceneManager.environment.add(faro);
        return faro;
    }

    /** Panel interior de la pared oeste del deposito con el hueco de la puerta, con el material real de las paredes. */
    private void construirPanel(Material materialPared) {
        Material mat = materialPared.copy();
        mat.set(IntAttribute.createCullFace(0));
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder p = mb.part("panel_deposito", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, mat);
        float x = def.wallInnerX + 0.004f;
        float z0 = def.emergencyDoorZ - def.emergencyDoorWidth / 2f;
        float z1 = def.emergencyDoorZ + def.emergencyDoorWidth / 2f;
        float alto = def.wallPanelTop;
        float h = def.emergencyDoorHeight;
        rectPared(p, x, 0f, def.wallPanelMinZ, alto, z0);
        rectPared(p, x, 0f, z1, alto, def.wallPanelMaxZ);
        rectPared(p, x, h, z0, alto, z1);
        // jambas y dintel del hueco a traves de los 0.71 m de pared, hasta la fachada exterior (sin rendija visible)
        float xe = def.wallX - 0.13f;
        p.setUVRange(0f, 0f, 0.1f, h);
        p.rect(xe, 0f, z0, x, 0f, z0, x, h, z0, xe, h, z0, 0f, 0f, 1f);
        p.rect(x, 0f, z1, xe, 0f, z1, xe, h, z1, x, h, z1, 0f, 0f, -1f);
        p.rect(xe, h, z0, x, h, z0, x, h, z1, xe, h, z1, 0f, -1f, 0f);
        modeloPanel = mb.end();
        panel = new ModelInstance(modeloPanel);
    }

    private static void rectPared(MeshPartBuilder p, float x, float y0, float z0, float y1, float z1) {
        p.setUVRange(z0 / 2f, y0 / 2f, z1 / 2f, y1 / 2f);
        p.rect(x, y0, z0, x, y0, z1, x, y1, z1, x, y1, z0, 1f, 0f, 0f);
    }

    // ------------------------------------------------------------------ secuencia

    /** Con la llave: la hoja de la puerta de salida gira hacia el deposito y su colision desaparece. */
    public void abrirPuertaSalida() {
        if (tPuertaSalida >= 0f) {
            return;
        }
        tPuertaSalida = 0f;
        liberarHuecoPuertaSalida();
    }

    public boolean puertaSalidaAbierta() {
        return tPuertaSalida >= 0f;
    }

    /** La barra de la puerta de emergencia: la hoja se abre hacia afuera de un golpe y aparece el estacionamiento. */
    public void abrirPuertaEmergencia() {
        if (tPuertaEmergencia >= 0f) {
            return;
        }
        tPuertaEmergencia = 0f;
        collisionWorld.removeStaticCollider(colliderPuertaEmergencia);
        // la hoja abierta queda pegada a la fachada, del lado norte del hueco
        float zb = def.emergencyDoorZ + def.emergencyDoorWidth / 2f;
        collisionWorld.addStaticCollider(caja(def.wallX - 1.08f, 0f, zb - 0.12f, def.wallX - 0.1f, 2.1f, zb + 0.02f));
    }

    public boolean puertaEmergenciaAbierta() {
        return tPuertaEmergencia >= 0f;
    }

    /** Cambia el mundo a "exterior": cielo nocturno, niebla abierta, faroles encendidos, sombras sobre el
     * estacionamiento y las paredes limite sinteticas del oeste/sur reemplazadas por la valla. */
    public void activarExterior(SceneManager sceneManager, net.mgsx.gltf.scene3d.lights.DirectionalShadowLight luzDireccional) {
        if (exteriorActivo) {
            return;
        }
        exteriorActivo = true;
        for (int i = 0; i < faroles.size; i++) {
            faroles.get(i).intensity = intensidadFaroles.get(i);
        }
        luzExitExterior.intensity = 1.4f;
        luzLamparaPared.intensity = 4.5f;
        cieloNocturno = crearCieloNocturno();
        skyboxNocturno = new SceneSkybox(cieloNocturno);
        sceneManager.setSkyBox(skyboxNocturno);
        sceneManager.environment.set(FogAttribute.createFog(7f, 46f, 1.4f));
        sceneManager.environment.set(ColorAttribute.createFog(new Color(0.035f, 0.04f, 0.06f, 1f)));
        if (luzDireccional != null) {
            // afuera la luna pesa mas: sus sombras (las unicas reales del exterior) tienen que leerse
            luzDireccional.intensity = 1.7f;
            luzDireccional.setCenter((def.lotMinX + def.wallX) / 2f, 0f, (def.lotMinZ + def.lotMaxZ) / 2f);
        }
        // Las paredes limite del oeste y del sur pasaban por dentro del estacionamiento: la del oeste se retira y la
        // del sur se acorta para empezar en la fachada. La valla (agregarColisionadores) es ahora el limite.
        if (paredesLimiteOriginales.size == 4) {
            BoundingBox sur = paredesLimiteOriginales.get(0);
            BoundingBox oeste = paredesLimiteOriginales.get(2);
            collisionWorld.removeStaticCollider(oeste);
            collisionWorld.removeStaticCollider(sur);
            collisionWorld.addStaticCollider(caja(def.wallX + 0.02f, sur.min.y, sur.min.z, sur.max.x, sur.max.y, sur.max.z));
        }
    }

    public boolean isExteriorActivo() {
        return exteriorActivo;
    }

    public Scene getEscenaExterior() {
        return escenaExterior;
    }

    public Scene getEscenaAuto() {
        return escenaAuto;
    }

    public SceneSkybox getSkyboxNocturno() {
        return skyboxNocturno;
    }

    /** Cielo nocturno generado: degradado azul muy oscuro, estrellas tenues y una luna velada. Sin assets externos. */
    private static Cubemap crearCieloNocturno() {
        int lado = 512;
        Pixmap[] caras = new Pixmap[6];
        java.util.Random rnd = new java.util.Random(1987);
        Vector3 dir = new Vector3();
        Vector3 luna = new Vector3(-0.55f, 0.42f, -0.72f).nor();
        for (int cara = 0; cara < 6; cara++) {
            Pixmap px = new Pixmap(lado, lado, Pixmap.Format.RGB888);
            for (int y = 0; y < lado; y++) {
                for (int x = 0; x < lado; x++) {
                    direccionCubemap(cara, (x + 0.5f) / lado * 2f - 1f, (y + 0.5f) / lado * 2f - 1f, dir);
                    float alt = MathUtils.clamp(dir.y, -1f, 1f);
                    float t = MathUtils.clamp((alt + 0.05f) / 0.6f, 0f, 1f);
                    float r = MathUtils.lerp(0.050f, 0.008f, t);
                    float g = MathUtils.lerp(0.058f, 0.011f, t);
                    float b = MathUtils.lerp(0.085f, 0.026f, t);
                    float dl = dir.dot(luna);
                    if (dl > 0.9985f) {
                        // disco con borde suave (antes era un umbral duro: se veia pixelado)
                        float k = MathUtils.clamp((dl - 0.9985f) / 0.0006f, 0f, 1f);
                        r = MathUtils.lerp(r + 0.2f, 0.78f, k); g = MathUtils.lerp(g + 0.2f, 0.80f, k); b = MathUtils.lerp(b + 0.22f, 0.86f, k);
                    } else if (dl > 0.98f) {
                        float halo = (dl - 0.98f) / 0.019f;
                        halo = halo * halo * 0.22f;
                        r += halo; g += halo; b += halo * 1.1f;
                    }
                    if (alt > 0.06f && rnd.nextFloat() < 0.0016f) {
                        float s = 0.25f + rnd.nextFloat() * 0.55f;
                        r = g = s; b = Math.min(1f, s * 1.08f);
                    }
                    px.setColor(r, g, b, 1f);
                    px.drawPixel(x, y);
                }
            }
            caras[cara] = px;
        }
        Cubemap cubemap = new Cubemap(caras[0], caras[1], caras[2], caras[3], caras[4], caras[5]);
        for (Pixmap px : caras) {
            px.dispose();
        }
        return cubemap;
    }

    /** Convenio de caras de OpenGL (+X, -X, +Y, -Y, +Z, -Z); u, v en [-1, 1] con v hacia abajo. */
    private static void direccionCubemap(int cara, float u, float v, Vector3 out) {
        switch (cara) {
            case 0: out.set(1f, -v, -u); break;
            case 1: out.set(-1f, -v, u); break;
            case 2: out.set(u, 1f, v); break;
            case 3: out.set(u, -1f, -v); break;
            case 4: out.set(u, -v, 1f); break;
            default: out.set(-u, -v, -1f); break;
        }
        out.nor();
    }

    // ------------------------------------------------------------------ actualizacion

    public void actualizar(float dt) {
        tiempo += dt;
        if (tPuertaSalida >= 0f && tPuertaSalida <= DURACION_PUERTA_SALIDA) {
            tPuertaSalida += dt;
            float u = MathUtils.clamp(tPuertaSalida / DURACION_PUERTA_SALIDA, 0f, 1f);
            // primer tirón corto (el cerrojo cede) y despues el giro pesado de la hoja
            float angulo = ANGULO_PUERTA_SALIDA * (u < 0.15f ? 0.06f * (u / 0.15f) : 0.06f + 0.94f * suave((u - 0.15f) / 0.85f));
            girarPuertaSalida(angulo);
        }
        if (tPuertaEmergencia >= 0f && tPuertaEmergencia <= DURACION_PUERTA_EMERGENCIA + 0.4f) {
            tPuertaEmergencia += dt;
            float u = MathUtils.clamp(tPuertaEmergencia / DURACION_PUERTA_EMERGENCIA, 0f, 1f);
            // empujon: sale rapido y rebota un poco al llegar al tope
            float angulo = ANGULO_PUERTA_EMERGENCIA * (1f - (1f - u) * (1f - u) * (1f - u));
            float rebote = tPuertaEmergencia > DURACION_PUERTA_EMERGENCIA
                    ? -6f * MathUtils.sin((tPuertaEmergencia - DURACION_PUERTA_EMERGENCIA) / 0.4f * MathUtils.PI) : 0f;
            if (nodoPuertaEmergencia != null) {
                nodoPuertaEmergencia.rotation.set(rotPuertaEmergenciaCerrada).mulLeft(new Quaternion(Vector3.Y, angulo + rebote));
                escenaExterior.modelInstance.calculateTransforms();
            }
        }
        if (exteriorActivo) {
            // el farol junto al porton parpadea de forma irregular (lampara de sodio fallando)
            for (int i = 0; i < faroles.size; i++) {
                if (def.lamps[i].flicker) {
                    float n = MathUtils.sin(tiempo * 23f) * MathUtils.sin(tiempo * 7.3f + 1.1f) + MathUtils.sin(tiempo * 2.1f);
                    float base = intensidadFaroles.get(i);
                    faroles.get(i).intensity = n > 1.15f ? base * 0.08f : (n > 0.9f ? base * 0.55f : base);
                }
            }
        }
    }

    private static float suave(float u) {
        u = MathUtils.clamp(u, 0f, 1f);
        return u * u * (3f - 2f * u);
    }

    private void girarPuertaSalida(float anguloGrados) {
        if (nodoPuertaSalida == null) {
            return;
        }
        Matrix4 giro = new Matrix4().translate(def.exitDoorHingeX, 0f, def.exitDoorHingeZ)
                .rotate(Vector3.Y, anguloGrados).translate(-def.exitDoorHingeX, 0f, -def.exitDoorHingeZ);
        Matrix4 local = new Matrix4(puertaSalidaPadreInv).mul(giro).mul(puertaSalidaGlobalCerrada);
        local.getTranslation(nodoPuertaSalida.translation);
        local.getRotation(nodoPuertaSalida.rotation, true);
        local.getScale(nodoPuertaSalida.scale);
        nodoPuertaSalida.calculateTransforms(true);
    }

    // ------------------------------------------------------------------ auto (lo maneja la cinematica)

    /** 0 = cerrada, 1 = abierta (ANGULO_PUERTA_AUTO). */
    public void setAperturaPuertaAuto(float u) {
        aperturaPuertaAuto = MathUtils.clamp(u, 0f, 1.08f);
        if (nodoPuertaAuto != null) {
            nodoPuertaAuto.rotation.set(rotPuertaAutoCerrada).mulLeft(new Quaternion(Vector3.Y, ANGULO_PUERTA_AUTO * aperturaPuertaAuto));
            escenaAuto.modelInstance.calculateTransforms();
        }
    }

    /** 0 = baja (bloquea el porton), 1 = levantada. */
    public void setAperturaBarrera(float u) {
        aperturaBarrera = MathUtils.clamp(u, 0f, 1f);
        if (nodoBarrera != null) {
            nodoBarrera.rotation.set(rotBarreraCerrada).mulLeft(new Quaternion(Vector3.Z, ANGULO_BARRERA * aperturaBarrera));
            escenaExterior.modelInstance.calculateTransforms();
        }
        if (aperturaBarrera > 0.5f && colliderBarrera != null) {
            collisionWorld.removeStaticCollider(colliderBarrera);
            colliderBarrera = null;
        }
    }

    /** Mueve el auto: la colision del estacionamiento deja de valer en cuanto arranca (el jugador ya esta adentro). */
    public void moverAuto(float x, float z, float yaw, float distanciaRecorrida) {
        if (colliderAuto != null) {
            collisionWorld.removeStaticCollider(colliderAuto);
            colliderAuto = null;
        }
        posicionAuto.x = x;
        posicionAuto.z = z;
        yawAuto = yaw;
        giroRuedas -= distanciaRecorrida / RADIO_RUEDA * MathUtils.radiansToDegrees;
        actualizarTransformAuto();
    }

    private final Quaternion tmpQ = new Quaternion();

    private void actualizarTransformAuto() {
        if (escenaAuto == null) {
            return;
        }
        escenaAuto.modelInstance.transform.idt().translate(posicionAuto).rotate(Vector3.Y, yawAuto);
        for (Node rueda : ruedasAuto) {
            rueda.rotation.set(tmpQ.set(Vector3.X, giroRuedas));
        }
        escenaAuto.modelInstance.calculateTransforms();
        actualizarFaros();
    }

    /** Punto local del auto (+Z = frente) en coordenadas del mundo. */
    public Vector3 puntoAuto(float lx, float ly, float lz, Vector3 out) {
        float c = MathUtils.cosDeg(yawAuto), s = MathUtils.sinDeg(yawAuto);
        return out.set(posicionAuto.x + lx * c + lz * s, posicionAuto.y + ly, posicionAuto.z - lx * s + lz * c);
    }

    public Vector3 frenteAuto(Vector3 out) {
        return out.set(MathUtils.sinDeg(yawAuto), 0f, MathUtils.cosDeg(yawAuto));
    }

    private boolean farosEncendidos = false;

    public void encenderFaros(boolean encendidos) {
        farosEncendidos = encendidos;
        if (materialFaros != null) {
            materialFaros.set(new ColorAttribute(ColorAttribute.Emissive, encendidos ? 1f : 0f, encendidos ? 0.97f : 0f, encendidos ? 0.9f : 0f, 1f));
        }
        if (materialTraseras != null) {
            materialTraseras.set(new ColorAttribute(ColorAttribute.Emissive, encendidos ? 0.9f : 0f, encendidos ? 0.04f : 0f, encendidos ? 0.03f : 0f, 1f));
        }
        if (materialIndicadores != null) {
            // el tablero se ilumina con el motor: multiplica la textura emisiva de las esferas (verde palido, como los
            // indicadores de un sedan viejo); apagado = 0
            materialIndicadores.set(new ColorAttribute(ColorAttribute.Emissive, encendidos ? 0.8f : 0f, encendidos ? 0.8f : 0f, encendidos ? 0.8f : 0f, 1f));
        }
        actualizarFaros();
    }

    private final Vector3 tmpV = new Vector3();

    private void actualizarFaros() {
        if (faroIzquierdo == null) {
            return;
        }
        float intensidad = farosEncendidos ? 60f : 0f;
        frenteAuto(tmpV);
        faroIzquierdo.direction.set(tmpV).add(0f, -0.12f, 0f).nor();
        faroDerecho.direction.set(faroIzquierdo.direction);
        puntoAuto(0.62f, 0.62f, 2.42f, faroIzquierdo.position);
        puntoAuto(-0.62f, 0.62f, 2.42f, faroDerecho.position);
        faroIzquierdo.intensity = intensidad;
        faroDerecho.intensity = intensidad;
        if (luzTraseras != null) {
            puntoAuto(0f, 0.8f, -2.75f, luzTraseras.position);
            luzTraseras.intensity = farosEncendidos ? 2.6f : 0f;
        }
    }

    @Override
    public void dispose() {
        if (modeloPanel != null) {
            modeloPanel.dispose();
        }
        if (skyboxNocturno != null) {
            skyboxNocturno.dispose();
        }
        if (cieloNocturno != null) {
            cieloNocturno.dispose();
        }
    }
}
