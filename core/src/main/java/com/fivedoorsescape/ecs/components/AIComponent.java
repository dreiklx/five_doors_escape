package com.fivedoorsescape.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.fsm.StateMachine;
import com.fivedoorsescape.world.CollisionWorld;

/** Estado de IA de una entidad controlada por maquina de estados (gdx-ai): Freddy y los 3
 * guardias estaticos (Bonnie/Chica/Foxy) comparten el mismo componente y las mismas clases de
 * estado -- lo que los distingue es {@link #siempreEnPersecucion}. */
public class AIComponent implements Component {

    public StateMachine<Entity, State<Entity>> stateMachine;
    public Entity objetivo;
    /** Mismo CollisionWorld que usa el jugador -- ChaseState lo necesita para no atravesar paredes. */
    public CollisionWorld collisionWorld;
    public float rangoDeteccion = 8f;
    public float rangoAtrape = 0.6f;
    public float velocidadPersecucion = 1.4f;
    public boolean jugadorAtrapado = false;

    /** True solo para Freddy (decision de diseno del usuario 2026-08-03): enemigo principal, debe
     * perseguir al jugador de forma continua durante toda la partida, sin importar la distancia --
     * a diferencia de Bonnie/Chica/Foxy, que son guardias estaticos (IdleState hasta entrar en
     * rangoDeteccion, vuelven a IdleState si el jugador se aleja lo suficiente). Ver IdleState/
     * ChaseState.update() para donde se consulta este flag. */
    public boolean siempreEnPersecucion = false;

    /** Deteccion de atasco en ChaseState (ver comentario ahi): distancia al objetivo muestreada
     * periodicamente (no posicion cruda -- deslizarse sin acercarse tambien cuenta como atasco)
     * y tiempo acumulado sin progreso real. */
    public float distanciaMuestreada = Float.NaN;
    public float temporizadorMuestra = 0f;
    public float tiempoAtascado = 0f;
}
