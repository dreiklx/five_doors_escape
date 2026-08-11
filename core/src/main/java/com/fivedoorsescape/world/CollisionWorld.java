package com.fivedoorsescape.world;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;

/**
 * Colision AABB manual (sin gdx-bullet) contra un conjunto de cajas estaticas del mapa. Resuelve
 * el movimiento eje por eje (X, luego Z) para permitir deslizarse a lo largo de una pared en vez
 * de detenerse en seco al tocarla en diagonal.
 */
public class CollisionWorld {

    private final Array<BoundingBox> staticColliders = new Array<>();
    // Colliders adicionales que solo bloquean al jugador (pedido explicito del usuario 2026-08-04:
    // la cortina de Pirate Cove debe bloquear al jugador pero seguir dejando pasar a Foxy). Los
    // guardias (ChaseState) siguen llamando al resolveMovement de 3 argumentos, que nunca consulta
    // esta lista -- ver LevelLoader.buildStaticColliders.
    private final Array<BoundingBox> collidersSoloJugador = new Array<>();
    private final BoundingBox tmpEntityBox = new BoundingBox();

    // "Escalon" del stage (pedido explicito del usuario 2026-08-10): una zona rectangular XZ
    // donde la altura de "suelo" real es mayor que la base (0), para que el jugador y los
    // animatronicos SUBAN de forma suave al entrar ahi, en vez de simplemente pasar por encima sin
    // cambiar de altura (que es lo que ya ocurria, ya que el stage no tiene colision de bloqueo --
    // su collider real, tan bajo, nunca llega a solaparse en Y con la caja de colision de ningun
    // personaje). Esto es puramente una ayuda de POSICIONAMIENTO (altura Y objetivo), nunca de
    // colision/bloqueo -- nunca impide entrar ni salir de la zona.
    private float pasoMinX, pasoMaxX, pasoMinZ, pasoMaxZ, pasoAltura;

    /** Velocidad maxima (unidades/segundo) a la que la altura de un personaje puede subir/bajar
     * hacia el objetivo de alturaEscalonEn -- evita un salto instantaneo de altura (teletransporte
     * vertical) al cruzar el borde del stage, pedido explicito del usuario ("sin teletransportes
     * ni saltos bruscos"). Con stageHeight=0.56 (valor real de pizzeria.json), subir el escalon
     * completo toma ~0.28s -- rapido pero perceptible, no instantaneo. */
    public static final float VELOCIDAD_ESCALON = 2.0f;

    public void configurarEscalon(float minX, float maxX, float minZ, float maxZ, float altura) {
        this.pasoMinX = minX;
        this.pasoMaxX = maxX;
        this.pasoMinZ = minZ;
        this.pasoMaxZ = maxZ;
        this.pasoAltura = altura;
    }

    /** Altura de "suelo" objetivo (offset sobre la base 0) en la posicion XZ dada -- pasoAltura si
     * cae dentro de la zona configurada, 0 en cualquier otro punto del mapa. */
    public float alturaEscalonEn(float x, float z) {
        if (pasoAltura != 0f && x >= pasoMinX && x <= pasoMaxX && z >= pasoMinZ && z <= pasoMaxZ) {
            return pasoAltura;
        }
        return 0f;
    }

    /** Mueve "actual" hacia "objetivo" a velocidad maxima VELOCIDAD_ESCALON -- utilidad compartida
     * por GameplayScreen (jugador) y AISystem (los 4 animatronicos), para que ambos suban/bajen el
     * escalon exactamente igual de suave. */
    public static float aplicarSuavizadoAltura(float actual, float objetivo, float dt) {
        float maxPaso = VELOCIDAD_ESCALON * dt;
        if (actual < objetivo) {
            return Math.min(objetivo, actual + maxPaso);
        } else if (actual > objetivo) {
            return Math.max(objetivo, actual - maxPaso);
        }
        return actual;
    }

    public void addStaticCollider(BoundingBox box) {
        staticColliders.add(box);
    }

    /** Retira un collider previamente agregado con addStaticCollider (comparacion por identidad,
     * no por valor) -- usado por Golden Freddy para dejar de bloquear al jugador/guardias en
     * cuanto desaparece tras su jumpscare (pedido explicito del usuario). */
    public void removeStaticCollider(BoundingBox box) {
        staticColliders.removeValue(box, true);
    }

    public void addColliderSoloJugador(BoundingBox box) {
        collidersSoloJugador.add(box);
    }

    public int getStaticColliderCount() {
        return staticColliders.size;
    }

    /** Resuelve movimiento contra los colliders estaticos generales unicamente (guardias/IA). */
    public Vector3 resolveMovement(Vector3 position, Vector3 delta, Vector3 halfExtents) {
        return resolveMovement(position, delta, halfExtents, false);
    }

    /** Con esJugador=true, tambien respeta los colliders solo-jugador (ver collidersSoloJugador). */
    public Vector3 resolveMovement(Vector3 position, Vector3 delta, Vector3 halfExtents, boolean esJugador) {
        Vector3 result = new Vector3(position);

        result.x += delta.x;
        if (overlaps(result, halfExtents, esJugador)) {
            result.x = position.x;
        }

        result.z += delta.z;
        if (overlaps(result, halfExtents, esJugador)) {
            result.z = position.z;
        }

        return result;
    }

    /** True si una caja centrada en position, con esas semi-extensiones, se solapa con algun collider estatico. */
    public boolean overlapsStatic(Vector3 center, Vector3 halfExtents) {
        return overlaps(center, halfExtents, false);
    }

    /** Igual que overlapsStatic, mas los colliders solo-jugador (ver collidersSoloJugador) -- para
     * sondeos/diagnosticos que necesitan ver exactamente lo que el jugador (no un guardia) puede
     * pisar. */
    public boolean overlaps(Vector3 center, Vector3 halfExtents, boolean incluirSoloJugador) {
        tmpEntityBox.set(
                new Vector3(center.x - halfExtents.x, center.y - halfExtents.y, center.z - halfExtents.z),
                new Vector3(center.x + halfExtents.x, center.y + halfExtents.y, center.z + halfExtents.z)
        );
        for (BoundingBox collider : staticColliders) {
            if (collider.intersects(tmpEntityBox)) {
                return true;
            }
        }
        if (incluirSoloJugador) {
            for (BoundingBox collider : collidersSoloJugador) {
                if (collider.intersects(tmpEntityBox)) {
                    return true;
                }
            }
        }
        return false;
    }
}
