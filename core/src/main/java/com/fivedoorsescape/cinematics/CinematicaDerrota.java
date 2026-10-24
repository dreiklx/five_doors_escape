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
import com.fivedoorsescape.content.ContentRegistry;
import com.fivedoorsescape.content.EntityDefinition;
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
 *  6.0-10.6  boca arriba en el escenario, rodeado por los cuatro, bajo un unico foco que se balancea y falla. Los
 *            cuatro REACCIONAN (CLAUDE.md §2.67): la mirada recorre el circulo (izquierda, derecha, el que esta detras
 *            de la cabeza) y cada uno gira la cabeza hacia el jugador justo cuando la camara llega, cada uno a su
 *            manera (PoseProcedural sobre su animacion real): Bonnie a tirones de servo, Chica con la cabeza ladeada
 *            y un giro que se pasa de largo, Foxy encorvado con la mandibula chasqueando, Freddy lento y fijo. Despues
 *            todos se inclinan hacia el y el culpable se acerca hasta llenar el cuadro (servo); tics con
 *            interferencias de imagen
 *  10.6-11.3 el foco revienta: oscuridad, solo los ojos del culpable; el latido se detiene
 *  11.3-12.3 negro -> NightGameOverScreen de siempre
 *
 * Se omite manteniendo ESC ~1.2 s (OmitirCinematica, CLAUDE.md §2.51); un toque no hace nada, asi que tampoco
 * se salta sin querer si el jugador tenia ESC apretado al morir.
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
    private static final float T_ARRASTRE = 1.0f;
    private static final float T_FIN_ARRASTRE = 5.3f;
    private static final float T_ESCENARIO = 6.0f;
    private static final float T_INCLINA = 8.95f;
    // recorrido de la mirada por el circulo (§2.67): izquierda, derecha, el de detras y de vuelta al culpable
    private static final float T_MIRA_IZQ = 6.7f;
    private static final float T_MIRA_DER = 7.5f;
    private static final float T_MIRA_ATRAS = 8.3f;
    /** Momentos de los tics (cada uno con ~0.13 s de interferencia en pantalla). */
    private static final float[] T_TICS = {6.95f, 7.78f, 8.52f, 9.42f, 9.93f, 10.28f};
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
    private final OmitirCinematica omision;

    /** Movimiento procedural (cabeza/torso/mandibula) de cada animatronico: [0] culpable, [1..3] otros. */
    private final PoseProcedural[] poses = new PoseProcedural[4];
    private final String[] ids = new String[4];
    private final Entity[] actores = new Entity[4];
    private final Vector3 cabezaTmp = new Vector3();
    private final Vector3 miraEscena = new Vector3();

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
            SceneManager sceneManager, SpotLightEx linterna, AssetService assets, ControlMusica musica, String textoSaltar,
            ContentRegistry registry) {
        this.camera = camera;
        this.md = md;
        this.culpable = culpable;
        this.otros = otros;
        this.sceneManager = sceneManager;
        this.linterna = linterna;
        this.assets = assets;
        this.musica = musica;
        this.omision = new OmitirCinematica(textoSaltar);
        actores[0] = culpable;
        for (int i = 0; i < 3 && i < otros.size; i++) {
            actores[i + 1] = otros.get(i);
        }
        for (int i = 0; i < 4; i++) {
            if (actores[i] == null) {
                continue;
            }
            ids[i] = Mappers.ai.has(actores[i]) ? Mappers.ai.get(actores[i]).id : "";
            EntityDefinition def = registry != null ? registry.getEntityDefinition(ids[i]) : null;
            if (def != null && def.huesos.size > 0 && Mappers.model.has(actores[i])) {
                poses[i] = new PoseProcedural(Mappers.model.get(actores[i]).scene.modelInstance, def.huesos);
            }
        }
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
        if (omision.actualizar(dt)) {
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
        // respiracion entrecortada: el ojo tiembla apenas
        ojo.set(sx + 0.08f + 0.004f * MathUtils.sin(t * 9.1f), ALTURA_ESCENARIO + 0.24f + 0.006f * MathUtils.sin(t * 2.3f), sz);
        // la mirada recorre el circulo (§2.67): el culpable arriba, el de la izquierda, el de la derecha, el que esta
        // detras de la cabeza (mirando hacia arriba y atras) y de vuelta al culpable, que ya se inclina. Cada tramo
        // termina en la CABEZA real de cada uno (posicion del hueso), asi el giro de su cabeza queda en cuadro.
        // medido con capturas (§2.50): mirando casi vertical solo se veia techo; por eso al culpable se lo mira al
        // cuerpo y la cara.
        tmp.set(sx + 0.3f * MathUtils.sin((t - T_ESCENARIO) * 0.45f), ALTURA_ESCENARIO + 1.9f, sz + 1.0f);
        Vector3 caraCulpable = new Vector3(sx, ALTURA_ESCENARIO + 1.7f, zc);
        Vector3 inicio = new Vector3(tmp).lerp(caraCulpable, 0.35f);
        mira.set(inicio);
        mezclarMirada(cabeza(1, sx - 1.0f, sz - 0.35f), T_MIRA_IZQ, T_MIRA_DER - 0.05f);
        mezclarMirada(cabeza(2, sx + 1.0f, sz - 0.35f), T_MIRA_DER, T_MIRA_ATRAS - 0.05f);
        mezclarMirada(cabeza(3, sx, sz - 1.05f), T_MIRA_ATRAS, T_INCLINA);
        if (t >= T_INCLINA) {
            float u = suave((t - T_INCLINA) / 0.55f);
            miraEscena.set(mira);
            mira.set(miraEscena).lerp(caraCulpable, u);
        }
        if (t < T_FOCO_REVIENTA) {
            float encendido = suave((t - (T_ESCENARIO + 0.2f)) / 0.5f);
            boolean falla = MathUtils.sin(t * 17f) * MathUtils.sin(t * 5.3f + 2f) > 0.82f || (t > T_FOCO_REVIENTA - 0.85f && MathUtils.sin(t * 43f) > 0.1f);
            // un tic apaga el foco un instante: la luz "salta" junto con el personaje
            boolean tic = false;
            for (float tt : T_TICS) {
                tic |= t >= tt && t < tt + 0.07f;
            }
            foco.intensity = encendido * (falla || tic ? 2f : 24f);
            // el foco cuelga y se balancea: el charco de luz recorre las caras y deja a alguno en sombra
            foco.direction.set(0.30f * MathUtils.sin((t - T_ESCENARIO) * 1.35f), -1f,
                    -0.18f + 0.22f * MathUtils.sin((t - T_ESCENARIO) * 0.83f + 0.6f)).nor();
            // ...y se va hacia la cara que se esta mirando: el que la camara encuentra queda iluminado y los demas en
            // sombra (medido con capturas: sin esto el de la izquierda y el de detras se veian a oscuras)
            tmp.set(mira).sub(foco.position).nor();
            foco.direction.lerp(tmp, 0.6f).nor();
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

    /** Lleva la mirada a "destino" en 0.45 s a partir de "desde" y la sostiene hasta "hasta" (la suelta despues). */
    private void mezclarMirada(Vector3 destino, float desde, float hasta) {
        if (t < desde) {
            return;
        }
        float u = suave((t - desde) / 0.45f);
        miraEscena.set(mira);
        mira.set(miraEscena).lerp(destino, u);
    }

    /** Posicion de la cabeza del actor i (hueso real si lo hay; si no, estimada sobre su posicion). */
    private Vector3 cabeza(int i, float x, float z) {
        if (poses[i] != null && poses[i].tieneCabeza() && poses[i].cabezaMundo(cabezaTmp)) {
            // el hueso de la cabeza pivota en la base del cuello: la cara queda ~0.35 m mas arriba
            return new Vector3(cabezaTmp.x, cabezaTmp.y + 0.35f, cabezaTmp.z);
        }
        return new Vector3(x, ALTURA_ESCENARIO + 1.75f, z);
    }

    // ------------------------------------------------------------------ movimiento procedural (§2.67)

    /** Llamar ANTES de que el SceneManager aplique las animaciones de este cuadro. */
    public void antesDeAnimar() {
        for (PoseProcedural p : poses) {
            if (p != null) {
                p.restaurar();
            }
        }
    }

    /** Llamar DESPUES de aplicar las animaciones: cabezas, torsos y mandibulas segun la escena y cada personaje. */
    public void despuesDeAnimar() {
        if (terminada) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (poses[i] == null || actores[i] == null) {
                continue;
            }
            TransformComponent tr = Mappers.transform.get(actores[i]);
            if (t < T_ESCENARIO) {
                // pasillo: los testigos siguen con la cabeza al jugador arrastrado; el culpable mira adelante
                if (i == 2 || i == 3 || i == 1) {
                    float peso = 0.85f * suave((t - (T_ARRASTRE + 0.4f * i)) / 0.8f);
                    poses[i].aplicar(tr.yawDegrees, ojo, peso, i == 2 ? 10f : -6f, 4f * peso, 0f);
                }
                continue;
            }
            poseEscenario(i, tr);
        }
    }

    /** Inicio del giro de cabeza de cada rol: el culpable ya mira; los otros cuando la camara llega a ellos. */
    private float inicioGiro(int rol) {
        switch (rol) {
            case 0:
                return T_ESCENARIO;
            case 1:
                return T_MIRA_IZQ + 0.15f;
            case 2:
                return T_MIRA_DER + 0.12f;
            default:
                return T_MIRA_ATRAS + 0.1f;
        }
    }

    private void poseEscenario(int rol, TransformComponent tr) {
        String id = ids[rol] == null ? "" : ids[rol];
        float te = t - inicioGiro(rol);
        float peso;
        float ladeo = 0f;
        float torso = 0f;
        float mandibula = 0f;
        // al final todos se inclinan hacia el jugador; al reventar el foco, todos lo miran de lleno
        float alFinal = suave((t - T_INCLINA) / 1.4f);
        float tics = 0f;
        for (float tt : T_TICS) {
            float d = t - tt;
            if (d >= 0f && d < 0.18f) {
                tics = Math.max(tics, 1f - d / 0.18f);
            }
        }
        if ("bonnie".equals(id)) {
            // servo que se traba: avanza, se frena, retrocede un poco, completa
            float u = MathUtils.clamp(te / 0.75f, 0f, 1f);
            peso = te <= 0f ? 0f : escalonado(u);
            ladeo = (tics > 0f ? 18f * tics * (rol % 2 == 0 ? 1f : -1f) : 0f) + 6f * MathUtils.sin(t * 0.7f);
            torso = 6f * alFinal;
            mandibula = 5f * suave(te / 1.5f);
        } else if ("chica".equals(id)) {
            // giro suave que se pasa de largo y vuelve, con la cabeza ladeada en un angulo antinatural
            float u = MathUtils.clamp(te / 0.9f, 0f, 1f);
            peso = te <= 0f ? 0f : Math.min(1.12f, suave(u) + 0.22f * MathUtils.sin(u * MathUtils.PI));
            ladeo = 22f * suave(te / 0.6f) - 14f * tics;
            if (tics > 0f) {
                peso *= 1f - 0.45f * tics;   // espasmo: la cabeza se suelta y vuelve
            }
            torso = 9f * alFinal;
        } else if ("foxy".equals(id)) {
            // encorvado, la cabeza baja y ladeada, la mandibula chasquea; al final se echa encima
            peso = te <= 0f ? 0f : suave(te / 0.5f);
            ladeo = -12f * suave(te / 0.5f) + 8f * tics;
            torso = 10f + 12f * alFinal;
            float chasquido = te > 0.4f ? Math.max(0f, MathUtils.sin(te * 9f)) : 0f;
            mandibula = te > 0f ? 4f + 20f * chasquido * chasquido * chasquido * chasquido : 0f;
        } else {
            // Freddy: lento, sin prisa, mirada fija y un poco hacia abajo; la mandibula se entreabre
            peso = te <= 0f ? 0f : suave(te / 1.2f);
            ladeo = 4f * MathUtils.sin(t * 0.5f) - 6f * tics;
            torso = 5f + 7f * alFinal;
            mandibula = 9f * alFinal;
        }
        if (rol == 0) {
            // el culpable ya mira desde arriba y es el que se inclina sobre el jugador
            peso = Math.max(peso, 0.65f);
            torso += 14f * suave((t - T_INCLINA) / 1.8f);
        }
        if (t >= T_FOCO_REVIENTA) {
            peso = 1f;
        }
        poses[rol].aplicar(tr.yawDegrees, ojo, MathUtils.clamp(peso, 0f, 1.15f), ladeo, torso, mandibula);
    }

    /** Curva de servo trabado (0..1 -> 0..1): sube, se frena, retrocede un poco, sube y completa. */
    private static float escalonado(float u) {
        if (u < 0.25f) {
            return 0.45f * suave(u / 0.25f);
        } else if (u < 0.45f) {
            return 0.45f - 0.08f * suave((u - 0.25f) / 0.2f);
        } else if (u < 0.7f) {
            return 0.37f + 0.53f * suave((u - 0.45f) / 0.25f);
        }
        return 0.9f + 0.1f * suave((u - 0.7f) / 0.3f);
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
        // interferencias en cada tic (§2.67): ~0.13 s de franjas finas, claras y oscuras, y un velo rojo muy leve.
        // Franjas finas y translucidas: se nota el "salto" de la imagen sin tapar a los personajes.
        if (t >= T_ESCENARIO && t < T_NEGRO_FINAL) {
            for (float tt : T_TICS) {
                float d = t - tt;
                if (d < 0f || d > 0.13f) {
                    continue;
                }
                MathUtils.random.setSeed((long) (tt * 1000f) + (long) (d * 60f));
                shapes.setColor(0.6f, 0.05f, 0.05f, 0.07f);
                shapes.rect(0, 0, w, h);
                int franjas = 5 + MathUtils.random(4);
                for (int k = 0; k < franjas; k++) {
                    float fy = MathUtils.random(0f, h);
                    float alto = MathUtils.random(1.5f, h * 0.012f);
                    boolean clara = MathUtils.randomBoolean(0.45f);
                    shapes.setColor(clara ? 0.85f : 0f, clara ? 0.85f : 0f, clara ? 0.9f : 0f, clara ? 0.12f : 0.4f);
                    shapes.rect(MathUtils.random(-w * 0.1f, w * 0.05f), fy, w * MathUtils.random(0.6f, 1.1f), alto);
                }
                MathUtils.random.setSeed(System.nanoTime());
            }
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        if (t < T_NEGRO_FINAL) {
            omision.dibujar(batch, shapes, fuente, 0f);
        }
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
        // el esqueleto queda como lo deja su animacion (sin el desvio procedural)
        antesDeAnimar();
        if (ojos != null) {
            sceneManager.environment.remove(ojos);
        }
    }
}
