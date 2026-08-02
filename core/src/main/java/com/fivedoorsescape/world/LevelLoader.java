package com.fivedoorsescape.world;

import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.fivedoorsescape.assets.AssetService;
import com.fivedoorsescape.content.ContentRegistry;
import com.fivedoorsescape.content.MapDefinition;

import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Carga el Scene del mapa (via AssetService/ContentRegistry, sin nombres hardcodeados) y deriva
 * un collider AABB estatico por cada nodo de malla -- la "colision AABB manual" del contrato,
 * sin gdx-bullet. Ajuste fino por sala/objeto queda para iteracion posterior (Architecture.md
 * #12, riesgo conocido).
 */
public class LevelLoader {

    private final ContentRegistry registry;
    private final AssetService assets;

    public LevelLoader(ContentRegistry registry, AssetService assets) {
        this.registry = registry;
        this.assets = assets;
    }

    public Scene loadMapScene(String mapId) {
        MapDefinition def = registry.getMapDefinition(mapId);
        SceneAsset sceneAsset = assets.getModel(def.modelPath);

        Scene mapScene = new Scene(sceneAsset.scene);
        mapScene.modelInstance.transform
                .idt()
                .rotate(Vector3.X, def.rotationXDegrees)
                .scale(def.scale, def.scale, def.scale);

        // El export Blender->glTF de Pizzeria (export_yup) invierte el orden de bobinado de los
        // triangulos respecto a lo que gdx-gltf espera: con backface culling normal, el mapa
        // completo queda invisible (confirmado empiricamente -- sin excepciones, sin fallo de
        // carga, simplemente cada cara se descarta por culling). Freddy no tiene este problema
        // (su pipeline de export fue distinto). Se desactiva el culling solo para los materiales
        // del mapa, no globalmente.
        for (Material material : mapScene.modelInstance.materials) {
            material.set(IntAttribute.createCullFace(0));
        }

        return mapScene;
    }

    public void buildStaticColliders(Scene mapScene, CollisionWorld collisionWorld) {
        Array<Node> nodes = mapScene.modelInstance.nodes;
        for (Node node : nodes) {
            addNodeAndChildren(node, mapScene.modelInstance.transform, collisionWorld);
        }
    }

    private void addNodeAndChildren(Node node, com.badlogic.gdx.math.Matrix4 instanceTransform, CollisionWorld collisionWorld) {
        if (node.parts.size > 0) {
            BoundingBox nodeBox = new BoundingBox();
            node.calculateBoundingBox(nodeBox, true);
            if (!nodeBox.isValid()) {
                return;
            }
            nodeBox.mul(instanceTransform);
            collisionWorld.addStaticCollider(nodeBox);
        }
        for (Node child : node.getChildren()) {
            addNodeAndChildren(child, instanceTransform, collisionWorld);
        }
    }
}
