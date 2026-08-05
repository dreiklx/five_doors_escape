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

    public void addStaticCollider(BoundingBox box) {
        staticColliders.add(box);
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
