package com.fivedoorsescape;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.Vector3;

import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneAsset;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import net.mgsx.gltf.scene3d.scene.SceneSkybox;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;
import net.mgsx.gltf.scene3d.utils.IBLBuilder;

/**
 * Minimal working example: loads Freddy.glb through gdx-gltf and renders it with an orbit
 * camera, PBR lighting and the Idle animation looping. This is the validated baseline for the
 * Blender -> glTF -> gdx-gltf -> LibGDX pipeline; gameplay/AI/camera-control logic belongs in
 * later screens, not here.
 *
 * Known issue: the "Freddy_Walk" animation renders upside-down (the "Freddy_Idle" one does
 * not), even though both play through the same corrective transform below. Root cause not
 * fully isolated yet - suspected quaternion sign inconsistency in the retargeted hip rotation
 * keyframes for the walk cycle. Revisit before Freddy's chase animation is wired into gameplay.
 */
public class FiveDoorsEscapeGame extends ApplicationAdapter {
    private SceneManager sceneManager;
    private SceneAsset sceneAsset;
    private Scene freddyScene;
    private PerspectiveCamera camera;
    private CameraInputController camController;

    private Cubemap diffuseCubemap, environmentCubemap, specularCubemap;
    private Texture brdfLUT;
    private SceneSkybox skybox;

    @Override
    public void create() {
        sceneAsset = new GLBLoader().load(Gdx.files.internal("Freddy.glb"));
        Gdx.app.log("FiveDoorsEscape", "Freddy.glb loaded: " + sceneAsset.scene.model.meshes.size
                + " meshes, " + sceneAsset.animations.size + " animations, maxBones=" + sceneAsset.maxBones);

        freddyScene = new Scene(sceneAsset.scene);
        // gdx-gltf renders this model's skin rotated 180 deg around Z relative to how Blender
        // interprets the same file (confirmed independent of any animation - affects the bind
        // pose too). This is the standard corrective-transform mitigation.
        freddyScene.modelInstance.transform.rotate(Vector3.Z, 180f);

        int numBones = Math.max(sceneAsset.maxBones, 1);
        sceneManager = new SceneManager(PBRShaderProvider.createDefault(numBones), PBRShaderProvider.createDefaultDepth(numBones));
        sceneManager.addScene(freddyScene);

        camera = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0.4f, 0.3f, 0.4f);
        camera.up.set(Vector3.Y);
        camera.lookAt(0f, 0.1f, 0f);
        camera.near = 0.001f;
        camera.far = 10f;
        camera.update();
        sceneManager.setCamera(camera);

        camController = new CameraInputController(camera);
        InputMultiplexer inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(camController);
        Gdx.input.setInputProcessor(inputMultiplexer);

        DirectionalLightEx light = new DirectionalLightEx();
        light.direction.set(1, -3, 1).nor();
        light.color.set(Color.WHITE);
        light.intensity = 3f;
        sceneManager.environment.add(light);

        IBLBuilder iblBuilder = IBLBuilder.createOutdoor(light);
        environmentCubemap = iblBuilder.buildEnvMap(1024);
        diffuseCubemap = iblBuilder.buildIrradianceMap(256);
        specularCubemap = iblBuilder.buildRadianceMap(10);
        iblBuilder.dispose();

        brdfLUT = new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));

        sceneManager.setAmbientLight(1f);
        sceneManager.environment.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT));
        sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap));
        sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap));

        skybox = new SceneSkybox(environmentCubemap);
        sceneManager.setSkyBox(skybox);

        if (sceneAsset.animations.size > 0) {
            freddyScene.animationController.setAnimation("Freddy_Idle", -1);
        }
    }

    @Override
    public void resize(int width, int height) {
        sceneManager.updateViewport(width, height);
    }

    @Override
    public void render() {
        float deltaTime = Gdx.graphics.getDeltaTime();
        camController.update();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        sceneManager.update(deltaTime);
        sceneManager.render();
    }

    @Override
    public void dispose() {
        sceneManager.dispose();
        sceneAsset.dispose();
        environmentCubemap.dispose();
        diffuseCubemap.dispose();
        specularCubemap.dispose();
        brdfLUT.dispose();
        skybox.dispose();
    }
}
