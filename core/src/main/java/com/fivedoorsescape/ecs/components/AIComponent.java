package com.fivedoorsescape.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.fivedoorsescape.world.CollisionWorld;

/** Estado de IA de una entidad controlada por maquina de estados (gdx-ai). Solo Freddy en el MVP. */
public class AIComponent implements Component {

    public StateMachine<Entity, State<Entity>> stateMachine;
    public Entity objetivo;
    /** Mismo CollisionWorld que usa el jugador -- ChaseState lo necesita para no atravesar paredes. */
    public CollisionWorld collisionWorld;
    public float rangoDeteccion = 8f;
    public float rangoAtrape = 0.6f;
    public float velocidadPersecucion = 1.4f;
    public boolean jugadorAtrapado = false;
}
