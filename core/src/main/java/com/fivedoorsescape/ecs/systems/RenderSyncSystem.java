package com.fivedoorsescape.ecs.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.Vector3;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.ModelComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;

/**
 * Vuelca TransformComponent (posicion + yaw dinamico) mas la correccion fija de ModelComponent
 * (rotacion en X, escala) dentro del transform del ModelInstance de cada entidad, cada frame.
 */
public class RenderSyncSystem extends IteratingSystem {

    public RenderSyncSystem() {
        super(Family.all(TransformComponent.class, ModelComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = Mappers.transform.get(entity);
        ModelComponent model = Mappers.model.get(entity);

        model.scene.modelInstance.transform
                .idt()
                .translate(transform.position)
                .rotate(Vector3.Y, transform.yawDegrees)
                .rotate(Vector3.X, model.correctionRotationXDegrees)
                .scale(model.scale, model.scale, model.scale);
    }
}
