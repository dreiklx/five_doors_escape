package com.fivedoorsescape.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector3;

/** Posicion y orientacion horizontal de una entidad en el mundo (espacio del mapa, Y-up). */
public class TransformComponent implements Component {

    public final Vector3 position = new Vector3();
    public float yawDegrees = 0f;
}
