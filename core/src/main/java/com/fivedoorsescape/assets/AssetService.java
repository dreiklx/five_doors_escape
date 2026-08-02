package com.fivedoorsescape.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.utils.Disposable;

import net.mgsx.gltf.loaders.glb.GLBAssetLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Encapsula por completo el AssetManager -- ningun sistema debe llamar assetManager.get(...)
 * directamente (Architecture.md).
 */
public class AssetService implements Disposable {

    private final AssetManager assetManager;

    public AssetService() {
        this(new InternalFileHandleResolver());
    }

    public AssetService(FileHandleResolver resolver) {
        assetManager = new AssetManager(resolver);
        assetManager.setLoader(SceneAsset.class, ".glb", new GLBAssetLoader(resolver));
    }

    public void queueModel(String path) {
        assetManager.load(path, SceneAsset.class);
    }

    public boolean update() {
        return assetManager.update();
    }

    public void finishLoading() {
        assetManager.finishLoading();
    }

    public float getProgress() {
        return assetManager.getProgress();
    }

    public SceneAsset getModel(String path) {
        return assetManager.get(path, SceneAsset.class);
    }

    public boolean isLoaded(String path) {
        return assetManager.isLoaded(path, SceneAsset.class);
    }

    @Override
    public void dispose() {
        assetManager.dispose();
    }
}
