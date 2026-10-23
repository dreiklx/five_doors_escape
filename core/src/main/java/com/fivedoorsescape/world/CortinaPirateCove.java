package com.fivedoorsescape.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.IntArray;

import net.mgsx.gltf.scene3d.scene.Scene;

/**
 * Cortina de Pirate Cove animada (CLAUDE.md §2.57). En pizzeria.glb es una unica malla sin animacion (CORTINA_37,
 * 1528 triangulos), asi que se anima por vertices en la CPU, solo mientras se mueve: las dos mitades se recogen hacia
 * sus extremos con pliegues que se marcan al juntarse y un balanceo que se amortigua. Se abre cuando Foxy pasa de
 * quieto a perseguir dentro de la cueva y se cierra sola cuando Foxy ya se alejo (o al instante, cuando todos
 * vuelven a su sitio tras una atrapada). No toca la colision: la zona de la cortina sigue bloqueando solo al jugador.
 */
public class CortinaPirateCove {

    private static final float DURACION_APERTURA = 0.65f;
    private static final float DURACION_CIERRE = 1.4f;
    private static final float RECOGIDO = 0.8f;     // fraccion de cada mitad que se junta contra su extremo
    private static final float ESPERA_CIERRE = 2.5f;

    private Mesh mesh;
    private float[] vertices;
    private int paso, desp;
    private final IntArray usados = new IntArray();
    private float[] mundoOriginal;   // x, y, z por vertice usado
    private final Matrix4 aMundo = new Matrix4();
    private final Matrix4 aLocal = new Matrix4();
    private float zMin, zMax, xCentro;

    private float apertura = 0f;
    private boolean abierta = false;
    private float tiempo = 0f;
    private float tiempoFueraFoxy = 0f;
    private float balanceo = 0f;
    private boolean activa = false;

    public CortinaPirateCove(Scene mapScene, String nombreNodo) {
        if (nombreNodo == null || nombreNodo.isEmpty()) {
            return;
        }
        Node grupo = mapScene.modelInstance.getNode(nombreNodo, true);
        if (grupo == null) {
            Gdx.app.log("CortinaPirateCove", "ADVERTENCIA: no se encontro " + nombreNodo);
            return;
        }
        Node hoja = grupo.parts.size > 0 ? grupo : grupo.getChild(0);
        NodePart parte = hoja.parts.get(0);
        mesh = parte.meshPart.mesh;
        VertexAttribute pos = mesh.getVertexAttribute(VertexAttributes.Usage.Position);
        paso = mesh.getVertexSize() / 4;
        desp = pos.offset / 4;
        vertices = new float[mesh.getNumVertices() * paso];
        mesh.getVertices(vertices);
        short[] indices = new short[parte.meshPart.size];
        mesh.getIndices(parte.meshPart.offset, parte.meshPart.size, indices, 0);
        boolean[] visto = new boolean[mesh.getNumVertices()];
        for (short i : indices) {
            int v = i & 0xFFFF;
            if (!visto[v]) {
                visto[v] = true;
                usados.add(v);
            }
        }
        mapScene.modelInstance.calculateTransforms();
        aMundo.set(mapScene.modelInstance.transform).mul(hoja.globalTransform);
        aLocal.set(aMundo).inv();
        mundoOriginal = new float[usados.size * 3];
        Vector3 t = new Vector3();
        zMin = Float.MAX_VALUE;
        zMax = -Float.MAX_VALUE;
        float sx = 0f;
        for (int k = 0; k < usados.size; k++) {
            int b = usados.get(k) * paso + desp;
            t.set(vertices[b], vertices[b + 1], vertices[b + 2]).mul(aMundo);
            mundoOriginal[k * 3] = t.x;
            mundoOriginal[k * 3 + 1] = t.y;
            mundoOriginal[k * 3 + 2] = t.z;
            zMin = Math.min(zMin, t.z);
            zMax = Math.max(zMax, t.z);
            sx += t.x;
        }
        xCentro = sx / usados.size;
        activa = true;
        Gdx.app.log("CortinaPirateCove", "Cortina lista: " + usados.size + " vertices, ancho z " + zMin + ".." + zMax);
    }

    public void abrir() {
        if (activa && !abierta) {
            abierta = true;
            balanceo = 1f;
            tiempoFueraFoxy = 0f;
        }
    }

    public void cerrarAlInstante() {
        if (!activa) {
            return;
        }
        abierta = false;
        apertura = 0f;
        balanceo = 0f;
        aplicar();
    }

    public boolean isAbierta() {
        return abierta;
    }

    public float getApertura() {
        return apertura;
    }

    /** foxyLejos: Foxy ya salio de la cueva (la cortina se cierra sola tras ESPERA_CIERRE). */
    public void actualizar(float dt, boolean foxyLejos) {
        if (!activa) {
            return;
        }
        tiempo += dt;
        if (abierta && foxyLejos) {
            tiempoFueraFoxy += dt;
            if (tiempoFueraFoxy > ESPERA_CIERRE) {
                abierta = false;
                balanceo = 0.7f;
            }
        } else if (abierta) {
            tiempoFueraFoxy = 0f;
        }
        float antes = apertura;
        apertura = abierta ? Math.min(1f, apertura + dt / DURACION_APERTURA) : Math.max(0f, apertura - dt / DURACION_CIERRE);
        balanceo = Math.max(0f, balanceo - dt * 0.55f);
        if (antes != apertura || balanceo > 0f) {
            aplicar();
        }
    }

    private final Vector3 tmp = new Vector3();

    private void aplicar() {
        float u = apertura * apertura * (3f - 2f * apertura);
        float zc = (zMin + zMax) / 2f;
        float mitad = (zMax - zMin) / 2f;
        for (int k = 0; k < usados.size; k++) {
            float x = mundoOriginal[k * 3], y = mundoOriginal[k * 3 + 1], z = mundoOriginal[k * 3 + 2];
            float zn;
            float rel;   // 0 en el extremo, 1 en el centro de la mitad
            if (z < zc) {
                rel = (z - zMin) / mitad;
                zn = zMin + (z - zMin) * (1f - RECOGIDO * u);
            } else {
                rel = (zMax - z) / mitad;
                zn = zMax - (zMax - z) * (1f - RECOGIDO * u);
            }
            // pliegues que se marcan al recogerse; la parte baja (y chica) se mueve un poco mas, como tela suelta
            float pliegue = MathUtils.sin(z * 17f) * 0.07f * u * rel;
            float suelto = MathUtils.clamp(1.6f - y * 0.55f, 0.3f, 1f);
            float onda = MathUtils.sin(tiempo * 7f + z * 4f) * 0.06f * balanceo * suelto;
            tmp.set(x + pliegue + onda, y, zn).mul(aLocal);
            int b = usados.get(k) * paso + desp;
            vertices[b] = tmp.x;
            vertices[b + 1] = tmp.y;
            vertices[b + 2] = tmp.z;
        }
        mesh.updateVertices(0, vertices);
    }

    public float getXCentro() {
        return xCentro;
    }
}
