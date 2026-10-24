package com.fivedoorsescape.cinematics;

import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Movimiento procedural de cabeza, torso y mandibula de un animatronico encima de su animacion real (CLAUDE.md
 * §2.67): mirar a un punto (giro + cabeceo, con limites), ladear la cabeza, inclinar el torso y abrir la mandibula.
 *
 * Los huesos salen de EntityDefinition.huesos ("cabeza", "torso", "mandibula"), no de nombres en Java. Los giros se
 * hacen en el espacio del MUNDO alrededor del pivote real de cada hueso (su posicion global): asi no importa como
 * estan orientados los ejes locales de cada esqueleto (son cuatro rigs distintos). El resultado se vuelve a pasar al
 * espacio local del padre.
 *
 * Se escribe tambien Node.localTransform: en los huesos animados (isAnimated) es lo unico que cuenta.
 *
 * Uso por cuadro: restaurar() ANTES de que el SceneManager aplique la animacion (deshace el desvio del cuadro
 * anterior en los huesos que la animacion no toca, para que no se acumule), y despues de aplicarla, aplicar(...).
 * Al terminar la escena, restaurar() deja el esqueleto como lo dejo la animacion.
 */
public class PoseProcedural {

    private final ModelInstance instancia;
    private final Node cabeza, torso, mandibula;
    private final Node[] nodos;
    private final Vector3[] trasGuardado;
    private final Quaternion[] rotGuardado;
    private final Vector3[] escGuardado;
    private final Matrix4[] localGuardado;
    private boolean guardado = false;

    private final Matrix4 mundo = new Matrix4();
    private final Matrix4 padreMundo = new Matrix4();
    private final Matrix4 giro = new Matrix4();
    private final Vector3 pivote = new Vector3();
    private final Vector3 dir = new Vector3();
    private final Vector3 eje = new Vector3();
    private final Quaternion q = new Quaternion();
    private final Quaternion qTmp = new Quaternion();

    public PoseProcedural(ModelInstance instancia, ObjectMap<String, String> huesos) {
        this.instancia = instancia;
        this.cabeza = buscar(huesos, "cabeza");
        this.torso = buscar(huesos, "torso");
        this.mandibula = buscar(huesos, "mandibula");
        nodos = new Node[] { torso, cabeza, mandibula };
        trasGuardado = new Vector3[] { new Vector3(), new Vector3(), new Vector3() };
        rotGuardado = new Quaternion[] { new Quaternion(), new Quaternion(), new Quaternion() };
        escGuardado = new Vector3[] { new Vector3(), new Vector3(), new Vector3() };
        localGuardado = new Matrix4[] { new Matrix4(), new Matrix4(), new Matrix4() };
    }

    private Node buscar(ObjectMap<String, String> huesos, String clave) {
        if (huesos == null || !huesos.containsKey(clave)) {
            return null;
        }
        return instancia.getNode(huesos.get(clave), true);
    }

    public boolean tieneCabeza() {
        return cabeza != null;
    }

    /** Posicion actual de la cabeza en el mundo (para encuadrarla). */
    public boolean cabezaMundo(Vector3 out) {
        if (cabeza == null) {
            return false;
        }
        posicionMundo(cabeza, out);
        return true;
    }

    /** Deshace el desvio aplicado en el cuadro anterior. Llamar antes de que se aplique la animacion. */
    public void restaurar() {
        if (!guardado) {
            return;
        }
        for (int i = 0; i < nodos.length; i++) {
            if (nodos[i] != null) {
                nodos[i].translation.set(trasGuardado[i]);
                nodos[i].rotation.set(rotGuardado[i]);
                nodos[i].scale.set(escGuardado[i]);
                nodos[i].localTransform.set(localGuardado[i]);
            }
        }
        guardado = false;
        instancia.calculateTransforms();
    }

    /**
     * Aplica el desvio de este cuadro sobre la pose ya animada.
     *
     * @param yawCuerpo     hacia donde mira el cuerpo (grados, convencion de TransformComponent: atan2(dx, dz))
     * @param objetivo      punto del mundo al que mira la cabeza (null = sin mirada)
     * @param pesoMirada    0 = la cabeza sigue la animacion, 1 = mira de lleno al objetivo
     * @param ladeo         grados de ladeo de la cabeza (giro sobre la direccion de la mirada)
     * @param inclinaTorso  grados de inclinacion del torso hacia adelante (positivo = hacia el frente)
     * @param mandibulaDeg  grados de apertura de la mandibula (positivo = abre)
     */
    public void aplicar(float yawCuerpo, Vector3 objetivo, float pesoMirada, float ladeo, float inclinaTorso,
            float mandibulaDeg) {
        for (int i = 0; i < nodos.length; i++) {
            if (nodos[i] != null) {
                trasGuardado[i].set(nodos[i].translation);
                rotGuardado[i].set(nodos[i].rotation);
                escGuardado[i].set(nodos[i].scale);
                localGuardado[i].set(nodos[i].localTransform);
            }
        }
        guardado = true;
        float yawRad = yawCuerpo * MathUtils.degreesToRadians;
        // derecha del cuerpo: frente x arriba (ver CLAUDE.md §2.67: un giro positivo sobre este eje levanta el frente)
        eje.set(-MathUtils.cos(yawRad), 0f, MathUtils.sin(yawRad));
        if (torso != null && Math.abs(inclinaTorso) > 0.01f) {
            q.setFromAxis(eje, -inclinaTorso);
            girarEnMundo(torso, q);
            instancia.calculateTransforms();
        }
        if (cabeza != null && objetivo != null && pesoMirada > 0.001f) {
            posicionMundo(cabeza, pivote);
            dir.set(objetivo).sub(pivote);
            float yawObjetivo = MathUtils.atan2(dir.x, dir.z) * MathUtils.radiansToDegrees;
            float giroYaw = MathUtils.clamp(diferenciaAngulo(yawObjetivo, yawCuerpo), -78f, 78f) * pesoMirada;
            float horiz = (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            float cabeceo = MathUtils.clamp(MathUtils.atan2(dir.y, horiz) * MathUtils.radiansToDegrees, -55f, 40f) * pesoMirada;
            float yawFinal = (yawCuerpo + giroYaw) * MathUtils.degreesToRadians;
            q.setFromAxis(Vector3.Y, giroYaw);
            qTmp.setFromAxis(-MathUtils.cos(yawFinal), 0f, MathUtils.sin(yawFinal), cabeceo);
            q.mulLeft(qTmp);
            if (Math.abs(ladeo) > 0.01f) {
                qTmp.setFromAxis(MathUtils.sin(yawFinal), 0f, MathUtils.cos(yawFinal), ladeo);
                q.mulLeft(qTmp);
            }
            girarEnMundo(cabeza, q);
            instancia.calculateTransforms();
        } else if (cabeza != null && Math.abs(ladeo) > 0.01f) {
            q.setFromAxis(MathUtils.sin(yawRad), 0f, MathUtils.cos(yawRad), ladeo);
            girarEnMundo(cabeza, q);
            instancia.calculateTransforms();
        }
        if (mandibula != null && Math.abs(mandibulaDeg) > 0.01f) {
            q.setFromAxis(eje, -mandibulaDeg);
            girarEnMundo(mandibula, q);
            instancia.calculateTransforms();
        }
    }

    private static float diferenciaAngulo(float a, float b) {
        float d = (a - b) % 360f;
        if (d > 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    private void posicionMundo(Node n, Vector3 out) {
        mundo.set(instancia.transform).mul(n.globalTransform);
        mundo.getTranslation(out);
    }

    /** Gira el nodo q (en el mundo) alrededor de su propio pivote y escribe el resultado en su transform local. */
    private void girarEnMundo(Node n, Quaternion qMundo) {
        mundo.set(instancia.transform).mul(n.globalTransform);
        mundo.getTranslation(pivote);
        giro.idt().translate(pivote).rotate(qMundo).translate(-pivote.x, -pivote.y, -pivote.z);
        mundo.mulLeft(giro);
        padreMundo.set(instancia.transform);
        if (n.hasParent()) {
            padreMundo.mul(n.getParent().globalTransform);
        }
        padreMundo.inv();
        mundo.mulLeft(padreMundo);
        mundo.getTranslation(n.translation);
        mundo.getRotation(n.rotation, true);
        mundo.getScale(n.scale);
        // Los nodos que anima el AnimationController quedan con isAnimated = true y entonces Node.calculateTransforms
        // usa localTransform tal cual (no lo rehace desde traslacion/rotacion/escala): hay que escribirlo tambien.
        // Sin esto el desvio no tenia ningun efecto visible (medido con capturas "sin"/"con", identicas).
        n.localTransform.set(mundo);
    }
}
