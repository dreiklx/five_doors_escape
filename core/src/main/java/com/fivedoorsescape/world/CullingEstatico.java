package com.fivedoorsescape.world;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;

/**
 * Frustum culling de la geometria estatica (CLAUDE.md §2.60). SceneManager/ModelBatch no descartan nada fuera de
 * camara: con el mapa, el exterior y el auto se median 1544 draw calls en CUALQUIER punto del mapa (pasada de sombras
 * + pasada de color). Aqui cada NodePart estatico tiene una esfera en el mundo calculada una vez; antes de cada render
 * se apaga (NodePart.enabled) lo que queda fuera del frustum de la camara, con un margen para que los objetos justo
 * fuera de cuadro sigan proyectando su sombra hacia adentro. Los nodos animados (puertas, cortina, hoja de emergencia,
 * barrera) no entran: siempre se dibujan. Un grupo entero puede apagarse (el exterior mientras la salida esta cerrada).
 */
public class CullingEstatico {

    private static final float MARGEN = 2.5f;

    private static final class Entrada {
        NodePart parte;
        final Vector3 centro = new Vector3();
        float radio;
        int grupo;
    }

    private final Array<Entrada> entradas = new Array<>();

    /** Instancias que se mueven (animatronicos con esqueleto): se prueban con una esfera alrededor de su posicion
     * ACTUAL y se encienden o apagan enteras. */
    private static final class Dinamica {
        ModelInstance instancia;
        float radio;
        float alturaCentro;
        final Array<NodePart> partes = new Array<>();
    }

    private final Array<Dinamica> dinamicas = new Array<>();

    /** Calcomanias translucidas (sombras de contacto, charcos de luz, aceite, papeles): no entran en la pasada de
     * sombras. Si entran, se sombrean a si mismas a 5 mm del suelo y aparecen franjas (acne), medido en capturas. */
    private final Array<NodePart> sinSombra = new Array<>();
    private final Array<NodePart> apagadasEnSombras = new Array<>();

    public void excluirDeSombras(ModelInstance instancia, String... prefijosMaterial) {
        Array<NodePart> todas = new Array<>();
        juntarPartes(instancia.nodes, todas);
        for (NodePart p : todas) {
            String id = p.material != null ? p.material.id : "";
            for (String pre : prefijosMaterial) {
                if (id.startsWith(pre)) {
                    sinSombra.add(p);
                    break;
                }
            }
        }
    }

    public int getExcluidasDeSombras() {
        return sinSombra.size;
    }

    public void antesDeSombras() {
        apagadasEnSombras.clear();
        for (NodePart p : sinSombra) {
            if (p.enabled) {
                p.enabled = false;
                apagadasEnSombras.add(p);
            }
        }
    }

    public void despuesDeSombras() {
        for (NodePart p : apagadasEnSombras) {
            p.enabled = true;
        }
        apagadasEnSombras.clear();
    }

    public void agregarDinamica(ModelInstance instancia, float radio, float alturaCentro) {
        Dinamica d = new Dinamica();
        d.instancia = instancia;
        d.radio = radio;
        d.alturaCentro = alturaCentro;
        juntarPartes(instancia.nodes, d.partes);
        dinamicas.add(d);
    }

    private static void juntarPartes(Iterable<Node> nodos, Array<NodePart> out) {
        for (Node n : nodos) {
            out.addAll(n.parts);
            juntarPartes(n.getChildren(), out);
        }
    }
    private final boolean[] grupoActivo = new boolean[8];
    /** Grupos que no se prueban contra el frustum (objetos que se mueven: el auto), solo se encienden o apagan. */
    private final boolean[] sinFrustum = new boolean[8];
    private boolean todoVisible = false;
    private int visibles;

    public CullingEstatico() {
        for (int i = 0; i < grupoActivo.length; i++) {
            grupoActivo[i] = true;
        }
    }

    /** Registra las partes estaticas de una instancia; excluidos: nombres de nodos animados (se excluyen con sus hijos). */
    public void agregar(ModelInstance instancia, int grupo, ObjectSet<String> excluidos) {
        instancia.calculateTransforms();
        for (Node n : instancia.nodes) {
            recorrer(n, instancia.transform, grupo, excluidos);
        }
    }

    private final Vector3 tmp = new Vector3();

    private void recorrer(Node n, Matrix4 instancia, int grupo, ObjectSet<String> excluidos) {
        if (excluidos != null && excluidos.contains(n.id)) {
            return;
        }
        Matrix4 mundo = new Matrix4(instancia).mul(n.globalTransform);
        float escala = Math.max(Math.abs(mundo.getScaleX()), Math.max(Math.abs(mundo.getScaleY()), Math.abs(mundo.getScaleZ())));
        for (NodePart p : n.parts) {
            if (p.bones != null) {
                continue;   // mallas con esqueleto: nunca
            }
            p.meshPart.update();
            Entrada e = new Entrada();
            e.parte = p;
            e.centro.set(p.meshPart.center).mul(mundo);
            e.radio = p.meshPart.radius * escala;
            e.grupo = grupo;
            entradas.add(e);
        }
        for (Node h : n.getChildren()) {
            recorrer(h, instancia, grupo, excluidos);
        }
    }

    public void setGrupoActivo(int grupo, boolean activo) {
        grupoActivo[grupo] = activo;
    }

    public void setSinFrustum(int grupo, boolean valor) {
        sinFrustum[grupo] = valor;
    }

    /** Para el precalentamiento de shaders: dibuja todo, ignorando grupos y frustum. */
    public void setTodoVisible(boolean valor) {
        todoVisible = valor;
    }

    /** Llamar antes de cada render de la escena. */
    public void actualizar(Camera camara) {
        int v = 0;
        for (Entrada e : entradas) {
            boolean ver = todoVisible || (grupoActivo[e.grupo]
                    && (sinFrustum[e.grupo] || camara.frustum.sphereInFrustum(e.centro, e.radio + MARGEN)));
            e.parte.enabled = ver;
            if (ver) {
                v++;
            }
        }
        for (Dinamica d : dinamicas) {
            d.instancia.transform.getTranslation(tmp);
            tmp.y += d.alturaCentro;
            boolean ver = todoVisible || camara.frustum.sphereInFrustum(tmp, d.radio + MARGEN);
            for (NodePart p : d.partes) {
                p.enabled = ver;
            }
            if (ver) {
                v++;
            }
        }
        visibles = v;
    }

    public int getVisibles() {
        return visibles;
    }

    public int getTotal() {
        return entradas.size;
    }
}
