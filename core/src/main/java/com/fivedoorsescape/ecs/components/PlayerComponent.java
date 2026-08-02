package com.fivedoorsescape.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.PerspectiveCamera;

/** Marca la entidad controlada por el jugador y referencia la camara FPS que la representa. */
public class PlayerComponent implements Component {

    public PerspectiveCamera camera;
    public float velocidadMovimiento = 3.5f;
    public boolean atrapado = false;
}
