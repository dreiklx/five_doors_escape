package com.fivedoorsescape.world;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AIComponent;
import com.fivedoorsescape.ecs.components.ModelComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;
import net.mgsx.gltf.scene3d.attributes.PBRColorAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneManager;

/**
 * Sombras de contacto bajo los animatronicos (CLAUDE.md §2.87 VE1/VE6, §2.88). La linterna (SpotLightEx) no proyecta
 * sombras en gdx-gltf 2.3.0 (§2.62) y la luna es tenue en el interior: iluminados por la linterna, los animatronicos
 * parecian flotar sobre el piso. Cada uno lleva debajo una mancha oscura de borde suave (un quad con degradado radial,
 * mismo material que las calcomanias de sangre) que sigue su posicion. Coste: un quad por animatronico, y no entra en
 * la pasada de sombras (alAntesDeSombras/alDespuesDeSombras), para no proyectar un cuadrado sobre el piso.
 */
public class SombrasContacto implements Disposable {

    /** Medio lado del quad en metros (la mancha visible es algo menor: el degradado llega a 0 en el borde). */
    private static final float MEDIO_LADO = 0.62f;
    private static final float ALTURA_SOBRE_PISO = 0.012f;
    private static final float OPACIDAD_CENTRO = 0.62f;

    private final SceneManager sceneManager;
    private final Engine engine;
    private final Family familia = Family.all(AIComponent.class, TransformComponent.class, ModelComponent.class).get();
    private final Texture textura;
    private final Model modelo;
    private final ObjectMap<Entity, Scene> sombras = new ObjectMap<>();

    public SombrasContacto(SceneManager sceneManager, Engine engine) {
        this.sceneManager = sceneManager;
        this.engine = engine;
        textura = crearTextura();
        Material material = new Material(
                PBRTextureAttribute.createBaseColorTexture(textura),   // PBRTextureAttribute: ver crearDecalesSangre
                PBRColorAttribute.createBaseColorFactor(new com.badlogic.gdx.graphics.Color(0f, 0f, 0f, 1f)),
                new BlendingAttribute(true, 1f),
                IntAttribute.createCullFace(0));
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder p = mb.part("sombra_contacto", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates, material);
        float m = MEDIO_LADO;
        p.rect(-m, 0f, -m, m, 0f, -m, m, 0f, m, -m, 0f, m, 0f, 1f, 0f);
        modelo = mb.end();
    }

    /** Un cuadro: crea las sombras que falten y las coloca bajo cada animatronico. Llamar antes de dibujar. */
    public void actualizar() {
        for (Entity e : engine.getEntitiesFor(familia)) {
            Scene sombra = sombras.get(e);
            if (sombra == null) {
                sombra = new Scene(new ModelInstance(modelo));
                sombras.put(e, sombra);
                sceneManager.addScene(sombra);
            }
            TransformComponent t = Mappers.transform.get(e);
            sombra.modelInstance.transform.setToTranslation(t.position.x, t.position.y + ALTURA_SOBRE_PISO, t.position.z);
        }
    }

    public void alAntesDeSombras() {
        activar(false);
    }

    public void alDespuesDeSombras() {
        activar(true);
    }

    private void activar(boolean si) {
        for (Scene s : sombras.values()) {
            for (com.badlogic.gdx.graphics.g3d.model.Node n : s.modelInstance.nodes) {
                for (NodePart np : n.parts) {
                    np.enabled = si;
                }
            }
        }
    }

    private static Texture crearTextura() {
        int tam = 128;
        Pixmap px = new Pixmap(tam, tam, Pixmap.Format.RGBA8888);
        px.setBlending(Pixmap.Blending.None);
        float c = (tam - 1) / 2f;
        for (int y = 0; y < tam; y++) {
            for (int x = 0; x < tam; x++) {
                float r = (float) Math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c;
                float a = r >= 1f ? 0f : (float) Math.pow(1f - r * r, 1.6f) * OPACIDAD_CENTRO;
                px.drawPixel(x, y, com.badlogic.gdx.graphics.Color.rgba8888(1f, 1f, 1f, a));
            }
        }
        Texture t = new Texture(px, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        px.dispose();
        return t;
    }

    @Override
    public void dispose() {
        for (Scene s : sombras.values()) {
            sceneManager.removeScene(s);
        }
        sombras.clear();
        modelo.dispose();
        textura.dispose();
    }
}
