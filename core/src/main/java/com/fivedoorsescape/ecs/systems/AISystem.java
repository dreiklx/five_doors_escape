package com.fivedoorsescape.ecs.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AIComponent;

/** Avanza la maquina de estados (gdx-ai) de cada entidad con IA, un tick por frame. */
public class AISystem extends IteratingSystem {

    public AISystem() {
        super(Family.all(AIComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        AIComponent ai = Mappers.ai.get(entity);
        ai.stateMachine.update();
    }
}
