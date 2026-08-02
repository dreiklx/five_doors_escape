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
    private final BoundingBox tmpEntityBox = new BoundingBox();

    public void addStaticCollider(BoundingBox box) {
        staticColliders.add(box);
    }

    public int getStaticColliderCount() {
        return staticColliders.size;
    }

    public Vector3 resolveMovement(Vector3 position, Vector3 delta, Vector3 halfExtents) {
        Vector3 result = new Vector3(position);

        result.x += delta.x;
        if (overlapsStatic(result, halfExtents)) {
            result.x = position.x;
        }

        result.z += delta.z;
        if (overlapsStatic(result, halfExtents)) {
            result.z = position.z;
        }

        return result;
    }

    /** True si una caja centrada en position, con esas semi-extensiones, se solapa con algun collider estatico. */
    public boolean overlapsStatic(Vector3 center, Vector3 halfExtents) {
        tmpEntityBox.set(
                new Vector3(center.x - halfExtents.x, center.y - halfExtents.y, center.z - halfExtents.z),
                new Vector3(center.x + halfExtents.x, center.y + halfExtents.y, center.z + halfExtents.z)
        );
        for (BoundingBox collider : staticColliders) {
            if (collider.intersects(tmpEntityBox)) {
                return true;
            }
        }
        return false;
    }
}
