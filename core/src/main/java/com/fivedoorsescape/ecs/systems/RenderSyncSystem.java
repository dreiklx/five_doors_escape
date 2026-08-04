package com.fivedoorsescape.ecs.systems;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.ModelComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;

/**
 * Vuelca TransformComponent (posicion + yaw dinamico) mas la correccion fija de ModelComponent
 * (rotacion en X, escala) dentro del transform del ModelInstance de cada entidad, cada frame.
 */
public class RenderSyncSystem extends IteratingSystem {

    /** Amplitud/velocidad del balanceo vertical de "respiracion" -- ver comentario en
     * processEntity sobre por que Bonnie/Chica/Foxy lo necesitan y Freddy no. */
    private static final float AMPLITUD_RESPIRACION = 0.025f;
    private static final float VELOCIDAD_RESPIRACION = 1.6f;

    private float tiempoTranscurrido = 0f;

    public RenderSyncSystem() {
        super(Family.all(TransformComponent.class, ModelComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        tiempoTranscurrido += deltaTime;
        super.update(deltaTime);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        TransformComponent transform = Mappers.transform.get(entity);
        ModelComponent model = Mappers.model.get(entity);

        float vaivenY = 0f;
        // Los 4 animatronicos (Freddy, Bonnie, Chica, Foxy) tienen ahora Walking/Breathing Idle
        // retargeteados individualmente (ver documentacion), asi que en la practica todas las
        // entidades con AIComponent tambien tienen AnimationComponent y esta rama no se ejecuta.
        // Se deja como respaldo defensivo por si se agrega en el futuro un animatronico sin
        // animacion esqueletica propia, para que no quede congelado frente a la camara.
        if (Mappers.ai.has(entity) && !Mappers.animation.has(entity)) {
            float fase = transform.position.x * 0.7f + transform.position.z * 0.3f;
            vaivenY = AMPLITUD_RESPIRACION * MathUtils.sin(tiempoTranscurrido * VELOCIDAD_RESPIRACION + fase);
        }

        model.scene.modelInstance.transform
                .idt()
                .translate(transform.position.x, transform.position.y + vaivenY, transform.position.z)
                .rotate(Vector3.Y, transform.yawDegrees)
                .rotate(Vector3.X, model.correctionRotationXDegrees)
                .scale(model.scale, model.scale, model.scale);
    }
}
