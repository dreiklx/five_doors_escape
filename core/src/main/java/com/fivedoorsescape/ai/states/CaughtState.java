package com.fivedoorsescape.ai.states;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.msg.Telegram;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AIComponent;
import com.fivedoorsescape.ecs.components.AnimationComponent;

/**
 * Freddy alcanzo al jugador. Se detiene el movimiento y se marca jugadorAtrapado=true;
 * GameplayScreen consulta ese flag cada frame para transicionar a NightGameOverScreen (MVP #9).
 */
public class CaughtState implements State<Entity> {

    public static final CaughtState INSTANCE = new CaughtState();

    private CaughtState() {
    }

    @Override
    public void enter(Entity entity) {
        AIComponent ai = Mappers.ai.get(entity);
        ai.jugadorAtrapado = true;
        AnimationComponent animation = Mappers.animation.get(entity);
        if (animation != null && animation.tieneAnimaciones()) {
            animation.nombreLogicoActual = "idle";
        }
    }

    @Override
    public void update(Entity entity) {
    }

    @Override
    public void exit(Entity entity) {
    }

    @Override
    public boolean onMessage(Entity entity, Telegram telegram) {
        return false;
    }
}
