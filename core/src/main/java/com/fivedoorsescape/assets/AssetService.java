package com.fivedoorsescape.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.fivedoorsescape.audio.SonidoConVolumen;
import com.fivedoorsescape.audio.VolumenAudio;

import net.mgsx.gltf.loaders.glb.GLBAssetLoader;
import net.mgsx.gltf.scene3d.scene.SceneAsset;

/**
 * Encapsula por completo el AssetManager -- ningun sistema debe llamar assetManager.get(...)
 * directamente (Architecture.md).
 */
public class AssetService implements Disposable {

    private final AssetManager assetManager;
    private final ObjectMap<String, SonidoConVolumen> sonidos = new ObjectMap<>();

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

    public void queueSound(String path) {
        assetManager.load(path, Sound.class);
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

    /** El sonido con el volumen del menu aplicado (musica o efectos segun su carpeta, CLAUDE.md §2.75): uno por ruta. */
    public Sound getSound(String path) {
        SonidoConVolumen s = sonidos.get(path);
        if (s == null) {
            VolumenAudio.cargar();
            s = new SonidoConVolumen(assetManager.get(path, Sound.class), VolumenAudio.categoriaDe(path));
            sonidos.put(path, s);
        }
        return s;
    }

    @Override
    public void dispose() {
        for (SonidoConVolumen s : sonidos.values()) {
            s.dispose();
        }
        sonidos.clear();
        assetManager.dispose();
    }
}
