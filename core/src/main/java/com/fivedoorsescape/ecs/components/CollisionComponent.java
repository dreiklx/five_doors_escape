package com.fivedoorsescape.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

/** Semi-extensiones de la caja AABB de colision de una entidad dinamica, centrada en su posicion. */
public class CollisionComponent implements Component {

    public final Vector3 halfExtents = new Vector3(0.3f, 0.9f, 0.3f);
}
