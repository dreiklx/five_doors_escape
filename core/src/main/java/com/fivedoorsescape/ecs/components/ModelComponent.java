package com.fivedoorsescape.ecs.components;

import com.badlogic.ashley.core.Component;

import net.mgsx.gltf.scene3d.scene.Scene;

/**
 * Referencia al Scene (gdx-gltf) de la entidad, mas la correccion fija determinada durante la
 * Fase 3 (rotacion de 180 grados en X para los 4 personajes) y el factor de escala propio del
 * modelo -- ambos vienen de EntityDefinition, no hardcodeados aqui.
 */
public class ModelComponent implements Component {

    public Scene scene;
    public float correctionRotationXDegrees = 0f;
    public float scale = 1f;
}
