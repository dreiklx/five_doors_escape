package com.fivedoorsescape.cinematics;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.fivedoorsescape.assets.AssetService;
import com.fivedoorsescape.content.MapDefinition;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AnimationComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;

import net.mgsx.gltf.scene3d.lights.PointLightEx;
import net.mgsx.gltf.scene3d.lights.SpotLightEx;
import net.mgsx.gltf.scene3d.scene.SceneManager;

/**
 * Derrota definitiva de Five Doors Escape, "La ultima funcion" (CLAUDE.md §2.50). Solo cuando se pierde la ULTIMA
 * vida: reemplaza la estatica que sigue al jumpscare. Las atrapadas normales no cambian. ~12.3 s:
 *
 *  0.0-1.0   negro: respiracion entrecortada y el latido
 *  1.0-5.3   abre los ojos en el piso: el animatronico que lo atrapo lo arrastra por el pasillo del comedor hacia el
 *            escenario, a tirones (uno por paso). Dos animatronicos miran desde el pasillo transversal; la linterna
 *            quedo tirada atras y parpadea hasta apagarse. Un parpadeo a mitad de camino.
 *  5.3-6.0   negro: lo suben al escenario (golpe sobre la madera)
 *  6.0-10.6  boca arriba en el escenario, rodeado por los cuatro, que lo miran desde arriba bajo un unico foco que
 *            falla. El culpable se inclina sobre el (servo) hasta llenar el cuadro
 *  10.6-11.3 el foco revienta: oscuridad, solo los ojos del culpable; el latido se detiene
 *  11.3-12.3 negro -> NightGameOverScreen de siempre
 *
 * ESC o ENTER la saltan desde el primer segundo (no E: es la tecla de juego y podia estar apretada al morir).
 */
public class CinematicaDerrota {

    public static final String SONIDO_ARRASTRE = "audio/sfx/defeat_drag.wav";
    public static final String SONIDO_GOLPE = "audio/sfx/defeat_body_thud.wav";
    public static final String SONIDO_SERVO = "audio/sfx/defeat_servo.wav";
    public static final String SONIDO_FOCO = "audio/sfx/defeat_light_short.wav";
    public static final String SONIDO_RESPIRACION = "audio/sfx/heavy_breathing.wav";
    public static final String SONIDO_LATIDO = "audio/sfx/heartbeat_normal.wav";
    public static final String[] SONIDOS = {SONIDO_ARRASTRE, SONIDO_GOLPE, SONIDO_SERVO, SONIDO_FOCO};

    public static final float DURACION = 12.3f;
    private static final float SALTO_PERMITIDO_DESDE = 1.0f;
    private static final float T_ARRASTRE = 1.0f;
    private static final float T_FIN_ARRASTRE = 5.3f;
    private static final float T_ESCENARIO = 6.0f;
    private static final float T_INCLINA = 8.8f;
    private static final float T_FOCO_REVIENTA = 10.6f;
    private static final float T_NEGRO_FINAL = 11.3f;
    private static final float PERIODO_PASO = 0.86f;
    private static final float ALTURA_ESCENARIO = 0.56f;

    /** Para callar la caja musical de Freddy (la maneja GameplayScreen) cuando revienta el foco. */
    public interface ControlMusica {
        void silenciar();
    }

    private final PerspectiveCamera camera;
    private final MapDefinition md;
    private final Entity culpable;
    private final Array<Entity> otros;
    private final SceneManager sceneManager;
    private final SpotLightEx linterna;
    private final AssetService assets;
    private final ControlMusica musica;
    private final String textoSaltar;

    private SpotLightEx foco;
    private PointLightEx ojos;
    private float t = 0f;
    private boolean terminada = false;
    private final boolean[] disparado = new boolean[10];
    private int pasos = 0;
    private Sound sLatido;
    private long idLatido = -1;
    private Sound sRespiracion;
    private long idRespiracion = -1;
    private final Vector3 ojo = new Vector3();
    private final Vector3 mira = new Vector3();
    private final Vector3 tmp = new Vector3();

    public CinematicaDerrota(PerspectiveCamera camera, MapDefinition md, Entity culpable, Array<Entity> otros,
            SceneManager sceneManager, SpotLightEx linterna, AssetService assets, ControlMusica musica, String textoSaltar) {
        this.camera = camera;
        this.md = md;
        this.culpable = culpable;
        this.otros = otros;
        this.sceneManager = sceneManager;
        this.linterna = linterna;
        this.assets = assets;
        this.musica = musica;
        this.textoSaltar = textoSaltar;
    }

    public void iniciar() {
        foco = new SpotLightEx();
        foco.setColor(1f, 0.9f, 0.75f, 1f);
        foco.setConeDeg(38f, 20f);
        foco.range = 7f;
        foco.intensity = 0f;
        foco.position.set(md.defeatStageX, 3.5f, md.defeatStageZ + 0.75f);
        foco.direction.set(0f, -1f, -0.18f).nor();
        sceneManager.environment.add(foco);
        ojos = new PointLightEx();
        ojos.setColor(1f, 0.08f, 0.04f, 1f);
        ojos.range = 0.9f;
        ojos.intensity = 0f;
        sceneManager.environment.add(ojos);
        sRespiracion = assets.getSound(SONIDO_RESPIRACION);
        idRespiracion = sRespiracion.play(0.6f);
        sLatido = assets.getSound(SONIDO_LATIDO);
        idLatido = sLatido.loop(0.55f);
        // testigos en el pasillo transversal, mirando hacia el carril; el tercero ya espera en el escenario
        colocar(otros.get(0), md.defeatWitnessAX, 0f, md.defeatWitnessAZ, 90f, "idle");
        colocar(otros.get(1), md.defeatWitnessBX, 0f, md.defeatWitnessBZ, -90f, "idle");
        colocar(otros.get(2), md.defeatStageX, ALTURA_ESCENARIO, md.defeatStageZ - 1.05f, 0f, "idle");
        linterna.intensity = 0f;
    }

    private static void colocar(Entity e, float x, float y, float z, float yaw, String animacion) {
        TransformComponent tr = Mappers.transform.get(e);
        tr.position.set(x, y, z);
        tr.yawDegrees = yaw;
        AnimationComponent an = Mappers.animation.get(e);
        if (an != null) {
            an.nombreLogicoActual = animacion;
        }
    }

    private boolean una(int i, float cuando) {
        if (!disparado[i] && t >= cuando) {
            disparado[i] = true;
            return true;
        }
        return false;
    }

    /** Avanza la cinematica. Devuelve true cuando termino (o se salto). */
    public boolean actualizar(float dt) {
        if (terminada) {
            return true;
        }
        t += dt;
        if (t >= SALTO_PERMITIDO_DESDE && (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER))) {
            terminar();
            return true;
        }
        if (t < T_ESCENARIO) {
            arrastre();
        } else {
            escenario();
        }
        if (una(0, 5.55f)) {
            assets.getSound(SONIDO_GOLPE).play(1f);
        }
        if (una(1, T_INCLINA)) {
            assets.getSound(SONIDO_SERVO).play(0.8f);
        }
        if (una(2, T_FOCO_REVIENTA - 0.85f)) {
            assets.getSound(SONIDO_FOCO).play(0.9f);
        }
        if (una(3, T_FOCO_REVIENTA)) {
            musica.silenciar();
        }
        if (una(4, T_FOCO_REVIENTA + 0.35f) && idLatido != -1) {
            // el corazon se detiene
            sLatido.stop(idLatido);
            idLatido = -1;
        }
        camera.position.set(ojo);
        camera.up.set(0f, 1f, 0f);
        camera.lookAt(mira);
        camera.update();
        if (t >= DURACION) {
            terminar();
            return true;
        }
        return false;
    }

    /** Distancia recorrida por el carril: a tirones, cada PERIODO_PASO avanza 0.62 m en los primeros 0.32 s. */
    private float distanciaArrastre(float te) {
        int paso = (int) (te / PERIODO_PASO);
        float u = MathUtils.clamp((te - paso * PERIODO_PASO) / 0.32f, 0f, 1f);
        return (paso + suave(u)) * 0.62f;
    }

    private void arrastre() {
        float te = Math.max(0f, t - T_ARRASTRE);
        float total = md.defeatLaneStartZ - md.defeatLaneEndZ;
        float d = Math.min(total, distanciaArrastre(te));
        int paso = (int) (te / PERIODO_PASO);
        if (t >= T_ARRASTRE && paso >= pasos && d < total) {
            pasos = paso + 1;
            assets.getSound(SONIDO_ARRASTRE).play(0.85f, MathUtils.random(0.94f, 1.06f), 0f);
        }
        float z = md.defeatLaneStartZ - d;
        float tiron = 1f - MathUtils.clamp((te - paso * PERIODO_PASO) / 0.32f, 0f, 1f);
        // la cabeza sobre el piso, ladeada; cada tiron la sacude
        ojo.set(md.defeatLaneX + 0.18f + 0.03f * MathUtils.sin(t * 1.7f), 0.30f + 0.03f * tiron, z);
        colocar(culpable, md.defeatLaneX, 0f, z - 1.25f, 180f, "walk");
        mira.set(md.defeatLaneX - 0.1f, 1.45f + 0.08f * tiron, z - 1.25f);
        // la linterna quedo tirada al principio del carril, apuntando hacia el escenario; parpadea y se apaga
        linterna.position.set(md.defeatLaneX + 0.3f, 0.08f, md.defeatLaneStartZ + 0.7f);
        linterna.direction.set(-0.04f, 0.06f, -1f).nor();
        float vida = 1f - MathUtils.clamp((t - 3.6f) / 1.6f, 0f, 1f);
        boolean falla = MathUtils.sin(t * 31f) * MathUtils.sin(t * 7.7f) > 0.55f - 0.5f * (1f - vida);
        linterna.intensity = t < T_ARRASTRE ? 0f : (falla ? 0.6f : 14f) * vida;
    }

    private void escenario() {
        linterna.intensity = 0f;
        float sx = md.defeatStageX, sz = md.defeatStageZ;
        if (una(5, T_ESCENARIO)) {
            colocar(otros.get(0), sx - 1.0f, ALTURA_ESCENARIO, sz - 0.35f, MathUtils.atan2(1.0f, 0.35f) * MathUtils.radiansToDegrees, "idle");
            colocar(otros.get(1), sx + 1.0f, ALTURA_ESCENARIO, sz - 0.35f, MathUtils.atan2(-1.0f, 0.35f) * MathUtils.radiansToDegrees, "idle");
        }
        float inclina = suave((t - T_INCLINA) / 1.8f);
        float zc = MathUtils.lerp(sz + 0.85f, sz + 0.40f, inclina);
        colocar(culpable, sx, ALTURA_ESCENARIO, zc, 180f, "idle");
        ojo.set(sx + 0.08f, ALTURA_ESCENARIO + 0.24f, sz);
        // mira hacia arriba, al circulo de caras, con una deriva lenta; termina clavada en la cara del culpable
        // medido con capturas: mirando casi vertical solo se veia techo; se mira al cuerpo y la cara del culpable, con
        // los otros dos en los bordes del cuadro
        tmp.set(sx + 0.3f * MathUtils.sin((t - T_ESCENARIO) * 0.45f), ALTURA_ESCENARIO + 1.9f, sz + 1.0f);
        mira.set(tmp).lerp(new Vector3(sx, ALTURA_ESCENARIO + 1.7f, zc), 0.35f + 0.65f * inclina);
        if (t < T_FOCO_REVIENTA) {
            float encendido = suave((t - (T_ESCENARIO + 0.2f)) / 0.5f);
            boolean falla = MathUtils.sin(t * 17f) * MathUtils.sin(t * 5.3f + 2f) > 0.82f || (t > T_FOCO_REVIENTA - 0.85f && MathUtils.sin(t * 43f) > 0.1f);
            foco.intensity = encendido * (falla ? 2f : 24f);
            ojos.intensity = 0f;
        } else {
            foco.intensity = 0f;
            ojos.position.set(sx, ALTURA_ESCENARIO + 1.72f, zc - 0.28f);
            ojos.intensity = t < T_NEGRO_FINAL ? 1.4f : 0f;
        }
    }

    private static float suave(float u) {
        u = MathUtils.clamp(u, 0f, 1f);
        return u * u * (3f - 2f * u);
    }

    /** Parpados (negro desde arriba y abajo), fundidos y el aviso para saltar. */
    public void dibujarOverlay(SpriteBatch batch, ShapeRenderer shapes, BitmapFont fuente) {
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float abierto;
        if (t < T_ARRASTRE) {
            abierto = 0f;
        } else if (t < T_FIN_ARRASTRE) {
            abierto = suave((t - T_ARRASTRE) / 0.6f);
            if (t > 3.15f && t < 3.55f) {
                abierto *= Math.abs((t - 3.35f) / 0.2f);   // parpadeo
            }
            abierto *= 1f - suave((t - (T_FIN_ARRASTRE - 0.4f)) / 0.4f);
        } else if (t < T_ESCENARIO) {
            abierto = 0f;
        } else if (t < T_NEGRO_FINAL) {
            abierto = suave((t - T_ESCENARIO) / 0.7f);
        } else {
            abierto = 0f;
        }
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 1f);
        float parpado = h * 0.5f * (1f - abierto);
        shapes.rect(0, h - parpado, w, parpado);
        shapes.rect(0, 0, w, parpado);
        // bordes siempre oscuros: vision borrosa de alguien que apenas esta consciente
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0, h - h * 0.08f - parpado, w, h * 0.08f);
        shapes.rect(0, parpado, w, h * 0.08f);
        shapes.end();
        if (t > SALTO_PERMITIDO_DESDE && t < T_NEGRO_FINAL) {
            batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
            batch.begin();
            float escala = fuente.getScaleX();
            fuente.getData().setScale(0.85f);
            GlyphLayout layout = new GlyphLayout(fuente, textoSaltar);
            fuente.setColor(1f, 1f, 1f, 0.4f);
            fuente.draw(batch, layout, w - layout.width - 24f, 30f);
            fuente.setColor(Color.WHITE);
            fuente.getData().setScale(escala);
            batch.end();
        }
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public float getTiempo() {
        return t;
    }

    /** Detiene sonidos propios y apaga sus luces (quedan fuera del environment). */
    public void terminar() {
        if (terminada) {
            return;
        }
        terminada = true;
        if (idLatido != -1) {
            sLatido.stop(idLatido);
            idLatido = -1;
        }
        if (idRespiracion != -1) {
            sRespiracion.stop(idRespiracion);
            idRespiracion = -1;
        }
        if (foco != null) {
            sceneManager.environment.remove(foco);
        }
        if (ojos != null) {
            sceneManager.environment.remove(ojos);
        }
    }
}
