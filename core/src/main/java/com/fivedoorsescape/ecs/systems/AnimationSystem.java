package com.fivedoorsescape.ecs.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AnimationComponent;
import com.fivedoorsescape.ecs.components.ModelComponent;

/**
 * Aplica el clip correspondiente al nombre logico solicitado por la IA (o por defecto "idle").
 * Entidades sin AnimationComponent, o con el mapa de clips vacio, se omiten limpiamente -- ver
 * el contrato de animaciones opcionales de Architecture.md (aplica de forma literal aqui: Bonnie/
 * Chica/Foxy no tienen AnimationComponent en absoluto, asi que ni siquiera entran a esta familia).
 */
public class AnimationSystem extends IteratingSystem {

    public AnimationSystem() {
        super(Family.all(AnimationComponent.class, ModelComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        AnimationComponent animation = Mappers.animation.get(entity);
        if (!animation.tieneAnimaciones()) {
            return;
        }

        String deseado = animation.nombreLogicoActual != null ? animation.nombreLogicoActual : "idle";
        if (!deseado.equals(animation.nombreLogicoAplicado)) {
            String clip = animation.clipsPorNombreLogico.get(deseado);
            if (clip != null) {
                ModelComponent model = Mappers.model.get(entity);
                model.scene.animationController.setAnimation(clip, -1);
                animation.nombreLogicoAplicado = deseado;
            }
        }
    }
}
