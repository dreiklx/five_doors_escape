package com.fivedoorsescape.ecs;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.fsm.DefaultStateMachine;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ObjectMap;
import com.fivedoorsescape.ai.states.IdleState;
import com.fivedoorsescape.assets.AssetService;
import com.fivedoorsescape.content.ContentRegistry;
import com.fivedoorsescape.content.EntityDefinition;
import com.fivedoorsescape.ecs.components.AIComponent;
import com.fivedoorsescape.ecs.components.AnimationComponent;
import com.fivedoorsescape.ecs.components.CollisionComponent;
import com.fivedoorsescape.ecs.components.ModelComponent;
import com.fivedoorsescape.ecs.components.PlayerComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;

import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Construye entidades de Ashley a partir de EntityDefinition + ContentRegistry + AssetService --
 * ningun nombre de asset queda hardcodeado aqui, todo viene del JSON de contenido.
 */
public class EntityFactory {

    private final Engine engine;
    private final ContentRegistry registry;
    private final AssetService assets;

    public EntityFactory(Engine engine, ContentRegistry registry, AssetService assets) {
        this.engine = engine;
        this.registry = registry;
        this.assets = assets;
    }

    public Entity createEntity(String entityId, Vector3 position, float yawDegrees) {
        EntityDefinition def = registry.getEntityDefinition(entityId);
        SceneAsset sceneAsset = assets.getModel(def.modelPath);

        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(position);
        transform.yawDegrees = yawDegrees;
        entity.add(transform);

        ModelComponent model = new ModelComponent();
        model.scene = new Scene(sceneAsset.scene);
        model.correctionRotationXDegrees = def.rotationXDegrees;
        model.scale = def.scale;
        entity.add(model);

        if (def.animaciones.size > 0) {
            entity.add(new AnimationComponent(new ObjectMap<>(def.animaciones)));
        }

        if (def.aiControlled) {
            AIComponent ai = new AIComponent();
            ai.stateMachine = new DefaultStateMachine<Entity, State<Entity>>(entity, IdleState.INSTANCE);
            entity.add(ai);
            entity.add(new CollisionComponent());
        }

        engine.addEntity(entity);
        return entity;
    }

    public Entity createPlayer(PerspectiveCamera camera, Vector3 position, float yawDegrees) {
        Entity entity = new Entity();

        TransformComponent transform = new TransformComponent();
        transform.position.set(position);
        transform.yawDegrees = yawDegrees;
        entity.add(transform);

        PlayerComponent player = new PlayerComponent();
        player.camera = camera;
        entity.add(player);

        entity.add(new CollisionComponent());

        engine.addEntity(entity);
        return entity;
    }
}
