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
        // Distancia horizontal (XZ) unicamente: la Y del jugador es su altura de ojos (~1.6) y
        // la de Freddy es la de sus pies (0) -- una distancia 3D completa quedaria siempre
        // dominada por esa diferencia de altura, sin relacion con la cercania real.
        float dx = propio.position.x - delJugador.position.x;
        float dz = propio.position.z - delJugador.position.z;
        float distancia = (float) Math.sqrt(dx * dx + dz * dz);
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
