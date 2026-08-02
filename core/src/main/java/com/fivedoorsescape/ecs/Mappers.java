package com.fivedoorsescape.ecs;

import com.badlogic.ashley.core.ComponentMapper;
import com.fivedoorsescape.ecs.components.AIComponent;
import com.fivedoorsescape.ecs.components.AnimationComponent;
import com.fivedoorsescape.ecs.components.CollisionComponent;
import com.fivedoorsescape.ecs.components.ModelComponent;
import com.fivedoorsescape.ecs.components.PlayerComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;

/** ComponentMapper compartidos -- convencion estandar de gdx-ashley, evita crear uno por clase. */
public final class Mappers {

    public static final ComponentMapper<TransformComponent> transform = ComponentMapper.getFor(TransformComponent.class);
    public static final ComponentMapper<ModelComponent> model = ComponentMapper.getFor(ModelComponent.class);
    public static final ComponentMapper<AnimationComponent> animation = ComponentMapper.getFor(AnimationComponent.class);
    public static final ComponentMapper<AIComponent> ai = ComponentMapper.getFor(AIComponent.class);
    public static final ComponentMapper<PlayerComponent> player = ComponentMapper.getFor(PlayerComponent.class);
    public static final ComponentMapper<CollisionComponent> collision = ComponentMapper.getFor(CollisionComponent.class);

    private Mappers() {
    }
}
