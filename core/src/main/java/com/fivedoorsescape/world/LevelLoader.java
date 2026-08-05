package com.fivedoorsescape.world;

import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.LongMap;
import com.fivedoorsescape.assets.AssetService;
import com.fivedoorsescape.content.ContentRegistry;
import com.fivedoorsescape.content.MapDefinition;

import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Carga el Scene del mapa (via AssetService/ContentRegistry, sin nombres hardcodeados) y deriva
 * un collider AABB estatico por cada nodo de malla -- la "colision AABB manual" del contrato,
 * sin gdx-bullet. Ajuste fino por sala/objeto queda para iteracion posterior (Architecture.md
 * #12, riesgo conocido).
 */
public class LevelLoader {

    /**
     * Dimension minima (en cualquier eje) para que un nodo del mapa se convierta en collider
     * estatico. Sin este filtro, decoraciones pequenas (gorros de fiesta colgando, tazones,
     * cubiertos) tambien bloquean el movimiento -- suficiente para atrapar al jugador o a Freddy
     * en el punto de spawn. Los objetos estructurales (paredes, mesas, columnas) son notablemente
     * mas grandes que esto en la Pizzeria real.
     */
    private static final float COLLIDER_MIN_DIMENSION = 0.4f;

    /**
     * Fraccion del ancho/profundidad total del mapa a partir de la cual un solo nodo se
     * considera "degenerado" como collider AABB y se descarta. Hallazgo real: el mapa incluye
     * un objeto (probablemente todas las paredes fusionadas en una sola malla) cuyo AABB cubre
     * ~27x21 unidades -- practicamente todo el edificio -- porque una caja alineada a los ejes
     * no puede representar una forma hueca/compleja; el resultado era que TODO el interior
     * quedaba bloqueado para el jugador y Freddy. Una caja que cubre la mayor parte del ancho Y
     * de la profundidad del mapa a la vez es inutil como collider solido, asi que se excluye.
     */
    private static final float DEGENERATE_FOOTPRINT_FRACTION = 0.7f;

    /**
     * Tamano de celda (en unidades de mundo, plano XZ) usada para subdividir un nodo "degenerado"
     * (arriba) en varios colliders mas pequenos derivados de su geometria real, en vez de
     * descartarlo por completo. Hallazgo real que motivo esto: el nodo degenerado resulto ser
     * TODAS las paredes del edificio (interiores y exteriores) fusionadas en una sola malla -- al
     * descartarlo entero para evitar bloquear todo el interior, tambien se perdio la colision de
     * CUALQUIER pared interior real (se podia atravesar cualquier pared que no fuera el perimetro
     * exterior sintetico). Este tamano de celda es menor que un hueco de puerta tipico (~1 unidad)
     * para no bloquear pasillos reales, pero mayor que el grosor tipico de una pared para agrupar
     * su geometria en una sola caja por celda.
     */
    private static final float WALL_GRID_CELL_SIZE = 0.2f;

    /** Dimension minima de una celda de pared subdividida para contar como collider real (evita
     * cajas de grosor casi nulo por triangulos que solo rozan el borde de una celda). */
    private static final float WALL_CELL_MIN_DIMENSION = 0.05f;

    /**
     * Altura minima (Y) para que un nodo "degenerado" (spansWholeFootprint) se subdivida en vez
     * de descartarse silenciosamente. Sin este filtro, el piso (Object_6, delgado en Y, a nivel
     * del suelo) tambien se subdividiria -- y como su rango de Y se solapa con la caja de colision
     * de los personajes con IA (que estan centrados en Y=0, no a la altura de ojos del jugador),
     * el piso entero bloquearia a Freddy/Bonnie/Chica/Foxy en cada celda de la grilla. Las paredes
     * reales (altura completa del edificio, ~2.96) quedan muy por encima de este umbral; el piso
     * y la malla delgada de techo/lamparas quedan muy por debajo.
     */
    private static final float WALL_MIN_HEIGHT = 1.0f;

    /**
     * Grosor de las 4 paredes limite sinteticas que reemplazan al collider descartado arriba.
     * Deliberadamente grueso (no un grosor de pared realista) como defensa adicional contra
     * "tunneling" -- ver MAX_FRAME_DELTA en GameplayScreen/ChaseState para la causa raiz real
     * que se corrigio (un deltaTime enorme en el primer frame tras la carga de assets).
     */
    private static final float BOUNDARY_WALL_THICKNESS = 1.5f;

    private final ContentRegistry registry;
    private final AssetService assets;

    public LevelLoader(ContentRegistry registry, AssetService assets) {
        this.registry = registry;
        this.assets = assets;
    }

    public Scene loadMapScene(String mapId) {
        MapDefinition def = registry.getMapDefinition(mapId);
        SceneAsset sceneAsset = assets.getModel(def.modelPath);

        Scene mapScene = new Scene(sceneAsset.scene);
        mapScene.modelInstance.transform
                .idt()
                .rotate(Vector3.X, def.rotationXDegrees)
                .scale(def.scale, def.scale, def.scale);

        // El export Blender->glTF de Pizzeria (export_yup) invierte el orden de bobinado de los
        // triangulos respecto a lo que gdx-gltf espera: con backface culling normal, el mapa
        // completo queda invisible (confirmado empiricamente -- sin excepciones, sin fallo de
        // carga, simplemente cada cara se descarta por culling). Freddy no tiene este problema
        // (su pipeline de export fue distinto). Se desactiva el culling solo para los materiales
        // del mapa, no globalmente.
        for (Material material : mapScene.modelInstance.materials) {
            material.set(IntAttribute.createCullFace(0));
        }

        return mapScene;
    }

    /**
     * Margen (unidades de mundo, en X/Z) que se suma alrededor de la huella de una puerta real al
     * despejarla de colision. Sin margen, el hueco libre coincidiria exactamente con el ancho de
     * la hoja de puerta modelada -- que puede ser mas angosto que la caja de colision del jugador
     * (semi-extension 0.3, es decir 0.6 de ancho total), dejando la puerta "abierta" pero
     * igualmente intransitable.
     */
    private static final float DOOR_CLEARANCE_MARGIN = 0.4f;

    /**
     * Radio (unidades de mundo, X/Z) alrededor de la puerta de salida real dentro del cual una
     * puerta detectada por nombre ("puerta...") se excluye del despejado automatico de colision
     * (ver esParteDePuertaOCortina/collectDoorZones). Sin esta exclusion, la puerta de salida
     * (nodo real "puerta.005_46", hijo real "Object_60" con la malla de la hoja de puerta)
     * quedaba atravesable como cualquier otra puerta interior "permanentemente abierta" -- pero
     * la puerta de salida es la unica que debe seguir siendo solida: la victoria se activa solo
     * al TOCARLA (ver exitRadius en GameplayScreen), no al caminar a traves de ella.
     *
     * Nota importante: "exitX"/"exitZ" en pizzeria.json originalmente apuntaban a un punto en
     * piso abierto real, ~1.5 unidades separado de la posicion real de la hoja de puerta
     * (Object_60) -- de ahi la regresion real reportada por el usuario (se podia "ganar" sin
     * tocar nunca la puerta, caminando por una zona nunca protegida por ninguna colision). Se
     * corrigieron esas coordenadas para que coincidan con el centro real de Object_60, y
     * exitRadius se ajusto a 0.75 (medido con sondeo real de colision: el jugador nunca puede
     * acercarse a menos de ~0.65 unidades del centro real de la puerta antes de quedar bloqueado).
     */
    private static final float RADIO_EXCLUSION_PUERTA_SALIDA = 2.0f;

    public void buildStaticColliders(Scene mapScene, CollisionWorld collisionWorld, float exitX, float exitZ) {
        BoundingBox mapFootprint = new BoundingBox();
        mapScene.modelInstance.calculateBoundingBox(mapFootprint);
        Vector3 mapDims = mapFootprint.getDimensions(new Vector3());

        Array<BoundingBox> puertaZonas = new Array<>();
        Array<BoundingBox> zonasCortinaSoloJugador = new Array<>();
        Array<Node> nodes = mapScene.modelInstance.nodes;
        for (Node node : nodes) {
            collectDoorZones(node, mapScene.modelInstance.transform, puertaZonas, zonasCortinaSoloJugador, exitX, exitZ);
        }

        for (Node node : nodes) {
            addNodeAndChildren(node, mapScene.modelInstance.transform, mapDims, collisionWorld, puertaZonas);
        }

        // Pirate Cove (pedido explicito del usuario 2026-08-04): la cortina sigue pasable para
        // Foxy/la IA (esta en puertaZonas como cualquier otra puerta, ver esParteDePuertaOCortina),
        // pero ademas se agrega su huella como collider SOLO-JUGADOR -- CollisionWorld.
        // resolveMovement(..., esJugador=true) (usado unicamente por el jugador en
        // GameplayScreen) la respeta, mientras que el movimiento de los guardias (ChaseState,
        // resolveMovement de 3 argumentos, esJugador=false implicito) la ignora por completo.
        for (BoundingBox zona : zonasCortinaSoloJugador) {
            collisionWorld.addColliderSoloJugador(zona);
        }

        addBoundaryWalls(mapFootprint, collisionWorld);
    }

    /**
     * Recorre el arbol buscando nodos cuyo ancestro sea una puerta o cortina real (ver
     * esParteDePuertaOCortina) y guarda su huella X/Z (expandida por DOOR_CLEARANCE_MARGIN) para
     * despejarla de colision mas adelante -- tanto de si misma como de cualquier celda de pared
     * subdividida que la cubra. Los nodos de cortina (Pirate Cove) ademas se registran por
     * separado en zonasCortinaSoloJugador, sin la expansion "de paso libre" (aqui el proposito es
     * bloquear, no despejar), para bloquear unicamente al jugador.
     */
    private void collectDoorZones(Node node, Matrix4 instanceTransform, Array<BoundingBox> out,
            Array<BoundingBox> zonasCortinaSoloJugador, float exitX, float exitZ) {
        if (node.parts.size > 0 && esParteDePuertaOCortina(node)) {
            BoundingBox nodeBox = new BoundingBox();
            node.calculateBoundingBox(nodeBox, true);
            if (nodeBox.isValid()) {
                nodeBox.mul(instanceTransform);

                if (esParteDeCortina(node)) {
                    BoundingBox zonaBloqueo = new BoundingBox(nodeBox);
                    zonaBloqueo.min.x -= DOOR_CLEARANCE_MARGIN;
                    zonaBloqueo.max.x += DOOR_CLEARANCE_MARGIN;
                    zonaBloqueo.min.z -= DOOR_CLEARANCE_MARGIN;
                    zonaBloqueo.max.z += DOOR_CLEARANCE_MARGIN;
                    zonasCortinaSoloJugador.add(zonaBloqueo);
                }

                Vector3 centro = nodeBox.getCenter(new Vector3());
                float dx = centro.x - exitX;
                float dz = centro.z - exitZ;
                boolean esPuertaDeSalida = (dx * dx + dz * dz)
                        <= RADIO_EXCLUSION_PUERTA_SALIDA * RADIO_EXCLUSION_PUERTA_SALIDA;
                if (!esPuertaDeSalida) {
                    nodeBox.min.x -= DOOR_CLEARANCE_MARGIN;
                    nodeBox.max.x += DOOR_CLEARANCE_MARGIN;
                    nodeBox.min.z -= DOOR_CLEARANCE_MARGIN;
                    nodeBox.max.z += DOOR_CLEARANCE_MARGIN;
                    out.add(nodeBox);
                }
            }
        }
        for (Node child : node.getChildren()) {
            collectDoorZones(child, instanceTransform, out, zonasCortinaSoloJugador, exitX, exitZ);
        }
    }

    /** True si este nodo o alguno de sus ancestros es especificamente la cortina de Pirate Cove
     * ("CORTINA_37"), no cualquier puerta -- ver bloqueo solo-jugador en collectDoorZones. */
    private boolean esParteDeCortina(Node node) {
        Node actual = node;
        while (actual != null) {
            if (actual.id != null && actual.id.toLowerCase().contains("cortina")) {
                return true;
            }
            actual = actual.getParent();
        }
        return false;
    }

    /**
     * True si la huella X/Z de candidato se solapa con la de alguna zona de puerta (ya expandida
     * por el margen). Deliberadamente ignora Y -- se trata la puerta como un hueco de piso a
     * techo en esa franja angosta, no solo del alto exacto de la hoja modelada (que puede ser mas
     * baja que la caja de colision del jugador mas el dintel solido justo encima).
     */
    private boolean dentroDeZonaPuerta(BoundingBox candidato, Array<BoundingBox> puertaZonas) {
        for (BoundingBox zona : puertaZonas) {
            boolean seSolapaEnX = candidato.min.x <= zona.max.x && candidato.max.x >= zona.min.x;
            boolean seSolapaEnZ = candidato.min.z <= zona.max.z && candidato.max.z >= zona.min.z;
            if (seSolapaEnX && seSolapaEnZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reemplaza el collider degenerado descartado arriba con 4 paredes limite delgadas, sinteticas
     * (no derivadas de ningun nodo del mapa), alineadas con la huella total del edificio. Hallazgo
     * real que motivo esto: sin ellas, el jugador podia caminar en linea recta y salir del mapa
     * indefinidamente (probado con un autopiloto de prueba -- posicion X pasando de 300 unidades
     * sin detenerse, cuando el mapa real solo mide ~27 de ancho). Las paredes reales del edificio
     * parecen estar fusionadas en el mismo objeto que se filtra como "degenerado" arriba, asi que
     * esta es la unica fuente de colision perimetral que le queda al jugador y a Freddy.
     */
    private void addBoundaryWalls(BoundingBox mapFootprint, CollisionWorld collisionWorld) {
        float minX = mapFootprint.min.x;
        float maxX = mapFootprint.max.x;
        float minY = mapFootprint.min.y;
        float maxY = mapFootprint.max.y;
        float minZ = mapFootprint.min.z;
        float maxZ = mapFootprint.max.z;
        float t = BOUNDARY_WALL_THICKNESS;

        collisionWorld.addStaticCollider(new BoundingBox(
                new Vector3(minX - t, minY, minZ - t), new Vector3(maxX + t, maxY, minZ)));
        collisionWorld.addStaticCollider(new BoundingBox(
                new Vector3(minX - t, minY, maxZ), new Vector3(maxX + t, maxY, maxZ + t)));
        collisionWorld.addStaticCollider(new BoundingBox(
                new Vector3(minX - t, minY, minZ - t), new Vector3(minX, maxY, maxZ + t)));
        collisionWorld.addStaticCollider(new BoundingBox(
                new Vector3(maxX, minY, minZ - t), new Vector3(maxX + t, maxY, maxZ + t)));
    }

    /**
     * Acumulador de una celda de la grilla: rango real de Y observado (de los triangulos que
     * caen en esa celda) mas el rango real de X/Z observado (recortado a los limites de la
     * celda), para que el collider resultante no sea mas grande que la geometria real que
     * contiene.
     */
    private static final class CeldaAcumulador {
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;

        void expandir(float x, float y, float z) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
    }

    /**
     * Subdivide un nodo cuya bounding box completa fue descartada por "degenerada" (spansWholeFootprint)
     * en varios colliders mas pequenos, derivados de sus triangulos reales en vez de su unica
     * bounding box. Una sola caja no puede representar una malla hueca/compleja (paredes con
     * huecos de puerta) sin bloquear los huecos tambien -- rasterizar cada triangulo real contra
     * una grilla XZ preserva los huecos (celdas sin triangulos = sin collider = pasable) mientras
     * recupera colision solida donde realmente hay pared.
     */
    private void subdivideNode(Node node, Matrix4 instanceTransform, CollisionWorld collisionWorld,
            Array<BoundingBox> puertaZonas) {
        Matrix4 nodeWorldTransform = new Matrix4(instanceTransform).mul(node.globalTransform);
        LongMap<CeldaAcumulador> celdas = new LongMap<>();

        Vector3 v0 = new Vector3();
        Vector3 v1 = new Vector3();
        Vector3 v2 = new Vector3();

        for (NodePart part : node.parts) {
            MeshPart meshPart = part.meshPart;
            Mesh mesh = meshPart.mesh;
            VertexAttribute posAttr = mesh.getVertexAttribute(VertexAttributes.Usage.Position);
            if (posAttr == null) {
                continue;
            }
            int vertexSizeFloats = mesh.getVertexSize() / 4;
            int posOffsetFloats = posAttr.offset / 4;

            float[] vertices = new float[mesh.getNumVertices() * vertexSizeFloats];
            mesh.getVertices(vertices);

            short[] indices = new short[meshPart.size];
            mesh.getIndices(meshPart.offset, meshPart.size, indices, 0);

            for (int i = 0; i + 2 < indices.length; i += 3) {
                leerVertice(vertices, indices[i], vertexSizeFloats, posOffsetFloats, v0).mul(nodeWorldTransform);
                leerVertice(vertices, indices[i + 1], vertexSizeFloats, posOffsetFloats, v1).mul(nodeWorldTransform);
                leerVertice(vertices, indices[i + 2], vertexSizeFloats, posOffsetFloats, v2).mul(nodeWorldTransform);
                rasterizarTriangulo(v0, v1, v2, celdas);
            }
        }

        for (LongMap.Entry<CeldaAcumulador> entry : celdas.entries()) {
            CeldaAcumulador c = entry.value;
            float dimX = c.maxX - c.minX;
            float dimY = c.maxY - c.minY;
            float dimZ = c.maxZ - c.minZ;
            if (dimX < WALL_CELL_MIN_DIMENSION && dimY < WALL_CELL_MIN_DIMENSION && dimZ < WALL_CELL_MIN_DIMENSION) {
                continue;
            }
            BoundingBox celdaBox = new BoundingBox(
                    new Vector3(c.minX, c.minY, c.minZ), new Vector3(c.maxX, c.maxY, c.maxZ));
            if (dentroDeZonaPuerta(celdaBox, puertaZonas)) {
                continue;
            }
            collisionWorld.addStaticCollider(celdaBox);
        }
    }

    private Vector3 leerVertice(float[] vertices, short indice, int vertexSizeFloats, int posOffsetFloats, Vector3 out) {
        int base = indice * vertexSizeFloats + posOffsetFloats;
        return out.set(vertices[base], vertices[base + 1], vertices[base + 2]);
    }

    /**
     * Marca como ocupadas todas las celdas de la grilla XZ que se solapan con la bounding box XZ
     * del triangulo (aproximacion conservadora: no rasteriza el triangulo exacto dentro de cada
     * celda, solo su huella rectangular -- preferible errar hacia "un poco mas solido" que hacia
     * "un hueco donde no deberia haberlo").
     */
    private void rasterizarTriangulo(Vector3 v0, Vector3 v1, Vector3 v2, LongMap<CeldaAcumulador> celdas) {
        float minX = Math.min(v0.x, Math.min(v1.x, v2.x));
        float maxX = Math.max(v0.x, Math.max(v1.x, v2.x));
        float minZ = Math.min(v0.z, Math.min(v1.z, v2.z));
        float maxZ = Math.max(v0.z, Math.max(v1.z, v2.z));
        float minY = Math.min(v0.y, Math.min(v1.y, v2.y));
        float maxY = Math.max(v0.y, Math.max(v1.y, v2.y));

        int ixMin = (int) Math.floor(minX / WALL_GRID_CELL_SIZE);
        int ixMax = (int) Math.floor(maxX / WALL_GRID_CELL_SIZE);
        int izMin = (int) Math.floor(minZ / WALL_GRID_CELL_SIZE);
        int izMax = (int) Math.floor(maxZ / WALL_GRID_CELL_SIZE);

        for (int ix = ixMin; ix <= ixMax; ix++) {
            float celdaMinX = ix * WALL_GRID_CELL_SIZE;
            float celdaMaxX = celdaMinX + WALL_GRID_CELL_SIZE;
            float xRecortadoMin = Math.max(minX, celdaMinX);
            float xRecortadoMax = Math.min(maxX, celdaMaxX);
            for (int iz = izMin; iz <= izMax; iz++) {
                float celdaMinZ = iz * WALL_GRID_CELL_SIZE;
                float celdaMaxZ = celdaMinZ + WALL_GRID_CELL_SIZE;
                float zRecortadoMin = Math.max(minZ, celdaMinZ);
                float zRecortadoMax = Math.min(maxZ, celdaMaxZ);

                long clave = claveDeCelda(ix, iz);
                CeldaAcumulador acumulador = celdas.get(clave);
                if (acumulador == null) {
                    acumulador = new CeldaAcumulador();
                    celdas.put(clave, acumulador);
                }
                acumulador.expandir(xRecortadoMin, minY, zRecortadoMin);
                acumulador.expandir(xRecortadoMax, maxY, zRecortadoMax);
            }
        }
    }

    /** Offset grande para mantener ambos indices no-negativos antes de combinarlos en una sola clave. */
    private static final int CELDA_OFFSET = 100000;

    private long claveDeCelda(int ix, int iz) {
        return ((long) (ix + CELDA_OFFSET) << 32) | (long) (iz + CELDA_OFFSET);
    }

    /**
     * True si este nodo o alguno de sus ancestros esta nombrado "puerta ..." en el modelo
     * original (p.ej. "puerta Izquierda_56", el padre real de Object_97 -- confirmado leyendo la
     * jerarquia real del glTF). Las hojas de puerta de la oficina vienen modeladas cerradas y
     * solidas -- sin este filtro, ahora que existe colision de pared interior real (ver
     * subdivideNode), el jugador queda sellado dentro de la oficina sin ninguna salida posible,
     * ya que no existe ningun mecanismo de apertura/cierre de puertas en este MVP. Se trata la
     * puerta como permanentemente abierta/pasable en vez de agregar una mecanica interactiva
     * completa (fuera de alcance de esta correccion).
     *
     * Tambien incluye "CORTINA" (nodo real "CORTINA_37", material "cortina" en el .blend original
     * -- confirmado que este mapa SI tiene una Pirate Cove real: base_39 usa el material literal
     * "pirateCove" y METAL_38 usa "aroPirateCove", el aro de la cortina). La cortina viene modelada
     * como una tela solida y cerrada, exactamente como en el lore real de FNAF (Foxy detras de una
     * cortina cerrada) -- funciona como una puerta mas para efectos de colision: sin este filtro,
     * la cortina sella el hueco de la Pirate Cove para siempre, ya que tampoco hay una mecanica de
     * abrir/cerrar cortinas en este MVP. Se trata como permanentemente corrida/pasable, igual que
     * las puertas, para poder ubicar a Foxy realmente detras de ella.
     *
     * Tambien incluye "base_39" (la tarima/piso propio de la Pirate Cove, confirmado con el
     * material "pirateCove" en sus 14 fragmentos hijos): es solo piso decorativo a nivel de suelo
     * (~0.42 unidades de alto en el .blend original), no una pared -- sin este filtro, cada
     * fragmento genera un collider solido que actua como un bordillo infranqueable alrededor de
     * toda la Pirate Cove (esta colision no tiene logica de "subir un escalon"), sellando el
     * acceso igual que lo hacia la cortina.
     */
    private boolean esParteDePuertaOCortina(Node node) {
        Node actual = node;
        while (actual != null) {
            if (actual.id != null) {
                String idMinuscula = actual.id.toLowerCase();
                if (idMinuscula.contains("puerta") || idMinuscula.contains("cortina") || idMinuscula.contains("base_39")) {
                    return true;
                }
            }
            actual = actual.getParent();
        }
        return false;
    }

    private void addNodeAndChildren(Node node, Matrix4 instanceTransform, Vector3 mapDims,
            CollisionWorld collisionWorld, Array<BoundingBox> puertaZonas) {
        if (node.parts.size > 0) {
            BoundingBox nodeBox = new BoundingBox();
            node.calculateBoundingBox(nodeBox, true);
            if (nodeBox.isValid()) {
                nodeBox.mul(instanceTransform);
                Vector3 dims = nodeBox.getDimensions(new Vector3());
                boolean tooSmall = dims.x < COLLIDER_MIN_DIMENSION && dims.y < COLLIDER_MIN_DIMENSION
                        && dims.z < COLLIDER_MIN_DIMENSION;
                boolean spansWholeFootprint = dims.x >= mapDims.x * DEGENERATE_FOOTPRINT_FRACTION
                        && dims.z >= mapDims.z * DEGENERATE_FOOTPRINT_FRACTION;
                boolean enZonaPuerta = dentroDeZonaPuerta(nodeBox, puertaZonas);
                if (!tooSmall && !spansWholeFootprint && !enZonaPuerta) {
                    collisionWorld.addStaticCollider(nodeBox);
                } else if (spansWholeFootprint && dims.y >= WALL_MIN_HEIGHT) {
                    subdivideNode(node, instanceTransform, collisionWorld, puertaZonas);
                }
            }
        }
        for (Node child : node.getChildren()) {
            addNodeAndChildren(child, instanceTransform, mapDims, collisionWorld, puertaZonas);
        }
    }
}
