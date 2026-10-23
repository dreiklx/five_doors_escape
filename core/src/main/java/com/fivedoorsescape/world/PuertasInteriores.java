package com.fivedoorsescape.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;

import net.mgsx.gltf.scene3d.scene.Scene;

/**
 * Puertas interiores que se abren al acercarse y se cierran despues (CLAUDE.md §2.56). Antes las hojas quedaban
 * cerradas a la vista mientras su vano era atravesable: el jugador (y los animatronicos) pasaban "a traves" de la
 * puerta.
 *
 * - Abatibles: giran 88 grados sobre la bisagra. Para cada puerta se mide, contra las colisiones reales, hacia que
 *   lado y con que bisagra el barrido de la hoja queda libre; si los dos lados lo estan, se abre hacia el lado
 *   contrario de quien llega (como una puerta de vaiven).
 * - Corredizas (las puertas de seguridad de la oficina, como en FNAF): suben 1.9 m y vuelven a bajar.
 *
 * Colision: con la puerta cerrada su hoja tiene un collider fino; al empezar a abrirse se retira (la hoja en
 * movimiento no tiene colision para no atrapar a nadie en el barrido) y vuelve cuando termina de cerrarse. La
 * apertura se dispara a 1.5 m del centro: antes de que el jugador (semiancho 0.3) llegue a tocarla.
 */
public class PuertasInteriores {

    private static final float DISTANCIA_ABRIR = 1.5f;
    private static final float DISTANCIA_CERRAR = 2.1f;
    private static final float ESPERA_CIERRE = 0.6f;
    private static final float DURACION_APERTURA = 0.45f;
    private static final float DURACION_CIERRE = 0.75f;
    private static final float ANGULO_ABIERTA = 88f;
    private static final float ALTURA_CORREDIZA = 1.9f;

    private final Array<Puerta> puertas = new Array<>();
    private final CollisionWorld mundo;
    private final Sound sonidoAbrir;
    private final Sound sonidoCerrar;
    private final Sound sonidoCorredera;

    private static final class Puerta {
        String id;
        boolean corrediza;
        Node nodo;
        final Matrix4 globalCerrada = new Matrix4();
        final Matrix4 padreInv = new Matrix4();
        final Vector3 centro = new Vector3();
        boolean delgadaEnX;
        float ancho;
        // bisagra elegida y signo del giro para cada lado del vano (+1 / -1 en el eje delgado); NaN = lado bloqueado
        final float[] bisagraX = new float[2];
        final float[] bisagraZ = new float[2];
        final float[] signoGiro = new float[2];
        final boolean[] ladoLibre = new boolean[2];
        int ladoActual = 0;
        BoundingBox collider;
        float apertura = 0f;      // 0 cerrada .. 1 abierta
        boolean abriendo = false;
        float tiempoLejos = 0f;
    }

    public PuertasInteriores(Scene mapScene, CollisionWorld mundo, String[] abatibles, String[] corredizas,
            Sound abrir, Sound cerrar, Sound corredera) {
        this.mundo = mundo;
        this.sonidoAbrir = abrir;
        this.sonidoCerrar = cerrar;
        this.sonidoCorredera = corredera;
        mapScene.modelInstance.calculateTransforms();
        for (String id : abatibles) {
            preparar(mapScene, id, false);
        }
        for (String id : corredizas) {
            preparar(mapScene, id, true);
        }
    }

    private void preparar(Scene mapScene, String id, boolean corrediza) {
        Node grupo = mapScene.modelInstance.getNode(id, true);
        if (grupo == null) {
            Gdx.app.log("PuertasInteriores", "ADVERTENCIA: puerta no encontrada " + id);
            return;
        }
        // la hoja es el primer descendiente con malla (en este mapa, el unico hijo "Object_NN")
        Node hoja = grupo.parts.size > 0 ? grupo : (grupo.getChildCount() > 0 ? grupo.getChild(0) : null);
        if (hoja == null) {
            return;
        }
        Puerta p = new Puerta();
        p.id = id;
        p.corrediza = corrediza;
        p.nodo = hoja;
        Matrix4 instancia = mapScene.modelInstance.transform;
        p.globalCerrada.set(instancia).mul(hoja.globalTransform);
        Matrix4 padre = new Matrix4(instancia);
        if (hoja.hasParent()) {
            padre.mul(hoja.getParent().globalTransform);
        }
        p.padreInv.set(padre).inv();
        BoundingBox caja = new BoundingBox();
        hoja.calculateBoundingBox(caja, true);
        caja.mul(instancia);
        caja.getCenter(p.centro);
        p.delgadaEnX = (caja.max.x - caja.min.x) < (caja.max.z - caja.min.z);
        p.ancho = p.delgadaEnX ? caja.max.z - caja.min.z : caja.max.x - caja.min.x;
        p.collider = new BoundingBox(new Vector3(caja.min.x, 0f, caja.min.z), new Vector3(caja.max.x, caja.max.y, caja.max.z));
        if (p.delgadaEnX) {
            p.collider.min.x = p.centro.x - 0.06f;
            p.collider.max.x = p.centro.x + 0.06f;
        } else {
            p.collider.min.z = p.centro.z - 0.06f;
            p.collider.max.z = p.centro.z + 0.06f;
        }
        if (!corrediza) {
            elegirBisagras(p, caja);
        }
        mundo.addStaticCollider(p.collider);
        puertas.add(p);
        Gdx.app.log("PuertasInteriores", id + (corrediza ? " corrediza" : " abatible, lado - libre=" + p.ladoLibre[0]
                + ", lado + libre=" + p.ladoLibre[1]));
    }

    /** Para cada lado del vano (-/+ en el eje delgado) prueba las dos bisagras y se queda con la que deja el barrido
     * de la hoja (un cuarto de circulo de radio = ancho) mas libre de colisiones reales. */
    private void elegirBisagras(Puerta p, BoundingBox caja) {
        Vector3 punto = new Vector3();
        Vector3 semi = new Vector3(0.05f, 0.4f, 0.05f);
        for (int lado = 0; lado < 2; lado++) {
            float s = lado == 0 ? -1f : 1f;
            int mejorChoques = Integer.MAX_VALUE;
            for (int extremo = 0; extremo < 2; extremo++) {
                float hx, hz, vx, vz;   // bisagra y vector bisagra -> canto libre con la puerta cerrada
                if (p.delgadaEnX) {
                    hx = p.centro.x;
                    hz = extremo == 0 ? caja.min.z : caja.max.z;
                    vx = 0f;
                    vz = extremo == 0 ? p.ancho : -p.ancho;
                } else {
                    hz = p.centro.z;
                    hx = extremo == 0 ? caja.min.x : caja.max.x;
                    vz = 0f;
                    vx = extremo == 0 ? p.ancho : -p.ancho;
                }
                // signo del giro que lleva el canto hacia el lado s: a 90 grados, Ry lleva (vx, vz) a (vz, -vx)
                float tComp90 = p.delgadaEnX ? vz : -vx;
                float signo = Math.signum(tComp90) == Math.signum(s) ? 1f : -1f;
                int choques = 0;
                for (int a = 10; a <= 85; a += 15) {
                    float ang = signo * a * MathUtils.degreesToRadians;
                    for (float r = 0.25f; r <= 0.95f; r += 0.23f) {
                        float rx = (vx * MathUtils.cos(ang) + vz * MathUtils.sin(ang)) * r;
                        float rz = (-vx * MathUtils.sin(ang) + vz * MathUtils.cos(ang)) * r;
                        punto.set(hx + rx, 1.0f, hz + rz);
                        if (mundo.overlapsStatic(punto, semi)) {
                            choques++;
                        }
                    }
                }
                if (choques < mejorChoques) {
                    mejorChoques = choques;
                    p.bisagraX[lado] = hx;
                    p.bisagraZ[lado] = hz;
                    p.signoGiro[lado] = signo;
                }
            }
            p.ladoLibre[lado] = mejorChoques <= 2;
        }
        if (!p.ladoLibre[0] && !p.ladoLibre[1]) {
            p.ladoLibre[1] = true;   // nunca dejar una puerta sin poder abrirse: el lado menos malo
        }
    }

    /** Avanza todas las puertas. posiciones: jugador y animatronicos (cualquiera las abre). */
    public void actualizar(float dt, Array<Vector3> posiciones) {
        for (Puerta p : puertas) {
            Vector3 cercano = null;
            float mejor = Float.MAX_VALUE;
            for (Vector3 pos : posiciones) {
                float d = Vector3.dst(pos.x, 0f, pos.z, p.centro.x, 0f, p.centro.z);
                if (d < mejor) {
                    mejor = d;
                    cercano = pos;
                }
            }
            if (mejor < DISTANCIA_ABRIR) {
                if (!p.abriendo) {
                    abrir(p, cercano);
                }
                p.tiempoLejos = 0f;
            } else if (p.abriendo && mejor > DISTANCIA_CERRAR) {
                p.tiempoLejos += dt;
                if (p.tiempoLejos > ESPERA_CIERRE) {
                    p.abriendo = false;
                    if (sonidoCerrar != null && !p.corrediza) {
                        sonidoCerrar.play(0.5f);
                    }
                    if (p.corrediza && sonidoCorredera != null) {
                        sonidoCorredera.play(0.45f);
                    }
                }
            }
            float antes = p.apertura;
            if (p.abriendo) {
                p.apertura = Math.min(1f, p.apertura + dt / DURACION_APERTURA);
            } else {
                p.apertura = Math.max(0f, p.apertura - dt / DURACION_CIERRE);
            }
            if (antes > 0f && p.apertura == 0f) {
                mundo.addStaticCollider(p.collider);   // cerrada otra vez: vuelve a ser solida
            }
            if (antes != p.apertura) {
                aplicar(p);
            }
        }
    }

    private void abrir(Puerta p, Vector3 quien) {
        if (p.apertura == 0f) {
            mundo.removeStaticCollider(p.collider);
            if (!p.corrediza) {
                // hacia el lado contrario de quien llega, si ese lado esta libre
                float t = p.delgadaEnX ? quien.x - p.centro.x : quien.z - p.centro.z;
                int contrario = t > 0f ? 0 : 1;
                p.ladoActual = p.ladoLibre[contrario] ? contrario : 1 - contrario;
            }
            if (p.corrediza) {
                if (sonidoCorredera != null) {
                    sonidoCorredera.play(0.45f);
                }
            } else if (sonidoAbrir != null) {
                sonidoAbrir.play(0.55f);
            }
        }
        p.abriendo = true;
    }

    private final Matrix4 tmp = new Matrix4();
    private final Matrix4 local = new Matrix4();

    private void aplicar(Puerta p) {
        float u = p.apertura * p.apertura * (3f - 2f * p.apertura);
        if (p.corrediza) {
            tmp.idt().translate(0f, ALTURA_CORREDIZA * u, 0f);
        } else {
            int l = p.ladoActual;
            float ang = p.signoGiro[l] * ANGULO_ABIERTA * u;
            tmp.idt().translate(p.bisagraX[l], 0f, p.bisagraZ[l]).rotate(Vector3.Y, ang).translate(-p.bisagraX[l], 0f, -p.bisagraZ[l]);
        }
        local.set(p.padreInv).mul(tmp).mul(p.globalCerrada);
        local.getTranslation(p.nodo.translation);
        local.getRotation(p.nodo.rotation, true);
        local.getScale(p.nodo.scale);
        p.nodo.calculateTransforms(true);
    }

    public int cantidad() {
        return puertas.size;
    }

    /** Para pruebas: apertura (0..1) de la puerta con ese id, o -1. */
    public float apertura(String id) {
        for (Puerta p : puertas) {
            if (p.id.equals(id)) {
                return p.apertura;
            }
        }
        return -1f;
    }
}
