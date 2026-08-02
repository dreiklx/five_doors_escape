package com.fivedoorsescape.ai.states;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.msg.Telegram;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AIComponent;
import com.fivedoorsescape.ecs.components.AnimationComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;

/**
 * Estado inicial de un personaje con IA: no se mueve, reproduce su animacion "idle" si tiene, y
 * vigila la distancia al jugador. Al entrar en rango de deteccion, transiciona a ChaseState.
 */
public class IdleState implements State<Entity> {

    public static final IdleState INSTANCE = new IdleState();

    private IdleState() {
    }

    @Override
    public void enter(Entity entity) {
        AnimationComponent animation = Mappers.animation.get(entity);
        if (animation != null && animation.tieneAnimaciones()) {
            animation.nombreLogicoActual = "idle";
        }
    }

    @Override
    public void update(Entity entity) {
        AIComponent ai = Mappers.ai.get(entity);
        if (ai.objetivo == null) {
            return;
        }
        TransformComponent propio = Mappers.transform.get(entity);
        TransformComponent delJugador = Mappers.transform.get(ai.objetivo);
        float distancia = propio.position.dst(delJugador.position);
        if (distancia <= ai.rangoDeteccion) {
            ai.stateMachine.changeState(ChaseState.INSTANCE);
        }
    }

    @Override
    public void exit(Entity entity) {
    }

    @Override
    public boolean onMessage(Entity entity, Telegram telegram) {
        return false;
    }
}
