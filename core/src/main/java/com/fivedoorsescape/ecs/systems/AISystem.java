package com.fivedoorsescape.ecs.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AIComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;
import com.fivedoorsescape.world.CollisionWorld;

/** Avanza la maquina de estados (gdx-ai) de cada entidad con IA, un tick por frame. */
public class AISystem extends IteratingSystem {

    public AISystem() {
        super(Family.all(AIComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        AIComponent ai = Mappers.ai.get(entity);
        ai.stateMachine.update();

        // Escalon del stage (pedido explicito del usuario 2026-08-10: "los animatronicos tambien
        // deben poder subir ese pequeño escalon y caminar por el stage") -- centralizado aqui (no
        // en ChaseState/IdleState) para que aplique por igual sin importar el estado real de la
        // maquina de estados (Idle, Chase o Caught), incluso si un guardia queda parado sobre el
        // stage. CollisionWorld nunca es null para una entidad con IA (asignado en
        // GameplayScreen.crearGuardia junto con el resto del AIComponent).
        if (ai.collisionWorld != null) {
            TransformComponent transform = Mappers.transform.get(entity);
            float alturaObjetivo = ai.collisionWorld.alturaEscalonEn(transform.position.x, transform.position.z);
            transform.position.y = CollisionWorld.aplicarSuavizadoAltura(transform.position.y, alturaObjetivo, deltaTime);
        }
    }
}
