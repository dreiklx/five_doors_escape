package com.fivedoorsescape.world;

import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;
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

    /**
     * Dimension minima (en cualquier eje) para que un nodo del mapa se convierta en collider
     * estatico. Sin este filtro, decoraciones pequenas (gorros de fiesta colgando, tazones,
     * cubiertos) tambien bloquean el movimiento -- suficiente para atrapar al jugador o a Freddy
     * en el punto de spawn. Los objetos estructurales (paredes, mesas, columnas) son notablemente
     * mas grandes que esto en la Pizzeria real.
     */
    private static final float COLLIDER_MIN_DIMENSION = 0.4f;

    /**
     * Fraccion del ancho/profundidad total del mapa a partir de la cual un solo nodo se
     * considera "degenerado" como collider AABB y se descarta. Hallazgo real: el mapa incluye
     * un objeto (probablemente todas las paredes fusionadas en una sola malla) cuyo AABB cubre
     * ~27x21 unidades -- practicamente todo el edificio -- porque una caja alineada a los ejes
     * no puede representar una forma hueca/compleja; el resultado era que TODO el interior
     * quedaba bloqueado para el jugador y Freddy. Una caja que cubre la mayor parte del ancho Y
     * de la profundidad del mapa a la vez es inutil como collider solido, asi que se excluye.
     */
    private static final float DEGENERATE_FOOTPRINT_FRACTION = 0.7f;

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
        BoundingBox mapFootprint = new BoundingBox();
        mapScene.modelInstance.calculateBoundingBox(mapFootprint);
        Vector3 mapDims = mapFootprint.getDimensions(new Vector3());

        Array<Node> nodes = mapScene.modelInstance.nodes;
        for (Node node : nodes) {
            addNodeAndChildren(node, mapScene.modelInstance.transform, mapDims, collisionWorld);
        }
    }

    private void addNodeAndChildren(Node node, Matrix4 instanceTransform, Vector3 mapDims,
            CollisionWorld collisionWorld) {
        if (node.parts.size > 0) {
            BoundingBox nodeBox = new BoundingBox();
            node.calculateBoundingBox(nodeBox, true);
            if (nodeBox.isValid()) {
                nodeBox.mul(instanceTransform);
                Vector3 dims = nodeBox.getDimensions(new Vector3());
                boolean tooSmall = dims.x < COLLIDER_MIN_DIMENSION && dims.y < COLLIDER_MIN_DIMENSION
                        && dims.z < COLLIDER_MIN_DIMENSION;
                boolean spansWholeFootprint = dims.x >= mapDims.x * DEGENERATE_FOOTPRINT_FRACTION
                        && dims.z >= mapDims.z * DEGENERATE_FOOTPRINT_FRACTION;
                if (!tooSmall && !spansWholeFootprint) {
                    collisionWorld.addStaticCollider(nodeBox);
                }
            }
        }
        for (Node child : node.getChildren()) {
            addNodeAndChildren(child, instanceTransform, mapDims, collisionWorld);
        }
    }
}
