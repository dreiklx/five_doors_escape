package com.fivedoorsescape.cinematics;

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
import com.badlogic.gdx.utils.FloatArray;
import com.fivedoorsescape.assets.AssetService;
import com.fivedoorsescape.content.ExteriorDefinition;
import com.fivedoorsescape.world.FreddyExterior;
import com.fivedoorsescape.world.SalidaExterior;

/**
 * Cinematica de victoria de Five Doors Escape (CLAUDE.md §2.49): empieza cuando el jugador pulsa E en la puerta del
 * conductor y termina en EscapeVictoryScreen. ~16.5 s:
 *
 *  0.0-1.3   la camara se acerca a la puerta del conductor y la abre
 *  1.3-2.9   entra y se sienta (la mirada pasa de la puerta al parabrisas)
 *  2.9-3.6   cierra la puerta: el ambiente nocturno queda amortiguado
 *  3.6-5.0   respiracion, llave, burro de arranque; el motor prende y se encienden los faros
 *  5.0-7.4   gira la cabeza hacia la pizzeria: Freddy esta parado en la puerta de emergencia, mirando. A los 6.15 s
 *            su cabeza se clava en el auto, se rie, y a los 6.55 s sale corriendo hacia el (CLAUDE.md §2.68); la camara
 *            lo sigue hasta que el jugador vuelve la vista al frente y arranca
 *  7.4-11.4  arranca con Freddy corriendo detras: sale del espacio, se levanta la barrera; a los 9 s el jugador mira
 *            por encima del hombro y lo ve por la luneta, a pocos metros, rojo por las luces traseras; cruza el porton
 *            y dobla hacia la calle. Freddy no puede alcanzarlo y se planta en el porton
 * 11.4-16.5  corte a una camara fija en la calle: el auto pasa y se aleja; Freddy queda inmovil en el porton abierto,
 *            bajo el farol, mirando; quedan el letrero y la pizzeria; negro
 *
 * Se omite manteniendo ESC ~1.2 s (OmitirCinematica, CLAUDE.md §2.51). El auto se mueve por una trayectoria fija calculada a partir de
 * exterior.json (recta, curva de radio 5.5 m que pasa por el porton y recta por la calle) y sus ruedas giran segun la
 * distancia real recorrida.
 */
public class CinematicaVictoria {

    public static final String SONIDO_PUERTA_ABRE = "audio/sfx/car_door_open.wav";
    public static final String SONIDO_PUERTA_CIERRA = "audio/sfx/car_door_close.wav";
    public static final String SONIDO_ARRANQUE = "audio/sfx/car_engine_start.wav";
    public static final String SONIDO_MARCHA = "audio/sfx/car_drive_away.wav";
    public static final String SONIDO_BARRERA = "audio/sfx/gate_barrier_raise.wav";
    public static final String SONIDO_RESPIRACION = "audio/sfx/heavy_breathing.wav";
    public static final String SONIDO_LATIDO = "audio/sfx/heartbeat_fast.wav";
    public static final String SONIDO_RISA = "audio/sfx/freddy_laugh.wav";
    /** Todo lo que BootScreen debe cargar para esta secuencia (incluidos los de la apertura de puertas). */
    public static final String[] SONIDOS = {SalidaExterior.SONIDO_AMBIENTE_NOCHE, SalidaExterior.SONIDO_PUERTA_SALIDA,
            SalidaExterior.SONIDO_PUERTA_EMERGENCIA, SONIDO_PUERTA_ABRE, SONIDO_PUERTA_CIERRA, SONIDO_ARRANQUE,
            SONIDO_MARCHA, SONIDO_BARRERA, FreddyExterior.SONIDO_PASO, CinematicaDerrota.SONIDO_SERVO};

    public static final float DURACION = 16.5f;
    private static final float T_SENTADO = 2.9f;
    private static final float T_CIERRA = 3.25f;
    private static final float T_FAROS = 5.0f;
    private static final float T_MIRA_FREDDY = 5.4f;
    private static final float T_ARRANCA = 7.4f;
    private static final float T_CORTE_CALLE = 11.4f;
    private static final float T_FUNDIDO = 15.3f;
    // persecucion de Freddy (§2.68)
    private static final float T_FREDDY_MIRA_AUTO = 6.15f;
    private static final float T_FREDDY_CORRE = 6.55f;
    private static final float T_VUELTA = T_ARRANCA - 0.35f;
    private static final float T_MIRA_ATRAS = 8.95f;
    private static final float DUR_MIRA_ATRAS = 1.0f;

    /** Volumen del ambiente nocturno, que maneja GameplayScreen (el loop sigue vivo durante toda la secuencia). */
    public interface ControlAmbiente {
        void volumen(float v);
    }

    private final PerspectiveCamera camera;
    private final SalidaExterior salida;
    private final ExteriorDefinition def;
    private final AssetService assets;
    private final ControlAmbiente ambiente;
    private final FreddyExterior freddy;
    private final OmitirCinematica omision;

    private float t = 0f;
    private boolean terminada = false;
    private boolean saltada = false;
    private final Vector3 camInicioPos = new Vector3();
    private final Vector3 camInicioDir = new Vector3();
    private final Vector3 ojo = new Vector3();
    private final Vector3 mira = new Vector3();
    private final Vector3 tmp = new Vector3();
    private final Vector3 tmp2 = new Vector3();

    // trayectoria del auto: puntos cada 0.25 m con su distancia acumulada
    private final FloatArray rutaX = new FloatArray();
    private final FloatArray rutaZ = new FloatArray();
    private final FloatArray rutaS = new FloatArray();
    private float distancia = 0f;
    private float distanciaAnterior = 0f;
    private float velocidad = 0f;
    private boolean barreraIniciada = false;
    private float tBarrera = 0f;

    private final boolean[] disparado = new boolean[12];
    private Sound sMarcha;
    private long idMarcha = -1;
    private Sound sArranque;
    private long idArranque = -1;
    private Sound sRespiracion;
    private long idRespiracion = -1;
    private Sound sLatido;
    private long idLatido = -1;

    private final Vector3 camCalle = new Vector3();
    private final Vector3 miraCalle = new Vector3();

    public CinematicaVictoria(PerspectiveCamera camera, SalidaExterior salida, AssetService assets, ControlAmbiente ambiente,
            FreddyExterior freddy, String textoSaltar) {
        this.camera = camera;
        this.salida = salida;
        this.def = salida.getDefinicion();
        this.assets = assets;
        this.ambiente = ambiente;
        this.freddy = freddy;
        this.omision = new OmitirCinematica(textoSaltar);
        construirRuta();
    }

    public void iniciar() {
        camInicioPos.set(camera.position);
        camInicioDir.set(camera.direction);
        t = 0f;
    }

    private void construirRuta() {
        float x0 = def.carX, z0 = def.carZ;
        float zCurva = def.roadZ + 5.5f;      // la curva termina justo sobre la calle
        float r = 5.5f;
        for (float z = z0; z > zCurva; z -= 0.25f) {
            agregarPunto(x0, z);
        }
        float cx = x0 - r, cz = zCurva;
        for (int i = 0; i <= 40; i++) {
            float a = -MathUtils.PI / 2f * i / 40f;
            agregarPunto(cx + r * MathUtils.cos(a), cz + r * MathUtils.sin(a));
        }
        for (float x = cx - 0.25f; x > -95f; x -= 0.25f) {
            agregarPunto(x, def.roadZ);
        }
        camCalle.set(def.lotMinX - 8.5f, 1.15f, def.roadZ - 3.7f);
    }

    private void agregarPunto(float x, float z) {
        float s = 0f;
        if (rutaX.size > 0) {
            s = rutaS.peek() + (float) Math.hypot(x - rutaX.peek(), z - rutaZ.peek());
        }
        rutaX.add(x);
        rutaZ.add(z);
        rutaS.add(s);
    }

    /** Punto de la ruta a la distancia s (interpolado) y el yaw de la tangente. */
    private float muestrearRuta(float s, Vector3 out) {
        int i = 0;
        while (i < rutaS.size - 2 && rutaS.get(i + 1) < s) {
            i++;
        }
        float s0 = rutaS.get(i), s1 = rutaS.get(i + 1);
        float u = s1 > s0 ? MathUtils.clamp((s - s0) / (s1 - s0), 0f, 1f) : 0f;
        float x = MathUtils.lerp(rutaX.get(i), rutaX.get(i + 1), u);
        float z = MathUtils.lerp(rutaZ.get(i), rutaZ.get(i + 1), u);
        out.set(x, 0f, z);
        return MathUtils.atan2(rutaX.get(i + 1) - rutaX.get(i), rutaZ.get(i + 1) - rutaZ.get(i)) * MathUtils.radiansToDegrees;
    }

    /** Avanza la cinematica. Devuelve true cuando termino (o se salto). */
    public boolean actualizar(float dt) {
        if (terminada) {
            return true;
        }
        t += dt;
        if (omision.actualizar(dt)) {
            saltada = true;
            terminar();
            return true;
        }
        eventos();
        moverAuto(dt);
        moverFreddy(dt);
        moverCamara();
        if (t >= DURACION) {
            terminar();
            return true;
        }
        return false;
    }

    private boolean una(int i, float cuando) {
        if (!disparado[i] && t >= cuando) {
            disparado[i] = true;
            return true;
        }
        return false;
    }

    private void eventos() {
        if (una(0, 0.35f)) {
            assets.getSound(SONIDO_PUERTA_ABRE).play(0.9f);
        }
        if (una(1, 2.6f) && freddy != null) {
            // mientras el jugador entra al auto (mirando adentro): nunca aparece a la vista
            freddy.colocarEnPuerta();
        }
        if (una(2, 3.15f)) {
            assets.getSound(SONIDO_PUERTA_CIERRA).play(1f);
            ambiente.volumen(0.28f);
        }
        if (una(3, 3.5f)) {
            sRespiracion = assets.getSound(SONIDO_RESPIRACION);
            idRespiracion = sRespiracion.play(0.55f);
        }
        if (una(4, 3.95f)) {
            sArranque = assets.getSound(SONIDO_ARRANQUE);
            idArranque = sArranque.play(0.95f);
        }
        if (una(5, T_FAROS)) {
            salida.encenderFaros(true);
        }
        if (una(6, T_MIRA_FREDDY + 0.4f)) {
            sLatido = assets.getSound(SONIDO_LATIDO);
            idLatido = sLatido.loop(0.7f);
        }
        if (una(7, T_MIRA_FREDDY + 1.0f)) {
            assets.getSound(SONIDO_RISA).play(0.45f, 1f, 0.6f);
        }
        if (una(8, T_ARRANCA - 0.1f)) {
            sMarcha = assets.getSound(SONIDO_MARCHA);
            idMarcha = sMarcha.play(1f);
            if (sLatido != null) {
                sLatido.stop(idLatido);
                idLatido = -1;
            }
        }
        if (una(9, T_CORTE_CALLE)) {
            // en la calle el auto se oye de afuera: el ambiente vuelve y la marcha baja a medida que se aleja
            ambiente.volumen(0.8f);
        }
        if (t > T_CORTE_CALLE && sMarcha != null && idMarcha != -1) {
            float lejos = MathUtils.clamp(1f - (t - T_CORTE_CALLE) / 4.0f, 0f, 1f);
            sMarcha.setVolume(idMarcha, 0.15f + 0.65f * lejos * lejos);
        }
        if (t > T_FUNDIDO) {
            ambiente.volumen(0.8f * MathUtils.clamp(1f - (t - T_FUNDIDO) / (DURACION - T_FUNDIDO), 0f, 1f));
        }
        float u = 0f;
        if (t >= 0.45f && t < T_SENTADO) {
            u = suave((t - 0.45f) / 0.85f);
        } else if (t >= T_SENTADO && t < T_CIERRA + 0.25f) {
            float c = (t - T_SENTADO) / (T_CIERRA - T_SENTADO);
            u = c < 1f ? 1f - c * c : -0.04f * MathUtils.sin((c - 1f) * 12f) * (1f - (c - 1f));
        }
        salida.setAperturaPuertaAuto(Math.max(0f, u));
    }

    private void moverAuto(float dt) {
        if (t < T_ARRANCA) {
            return;
        }
        float te = t - T_ARRANCA;
        float objetivo = te < 1.8f ? 4.6f * te / 1.8f : Math.min(10f, 4.6f + 2.4f * (te - 1.8f));
        velocidad += (objetivo - velocidad) * Math.min(1f, dt * 3f);
        distanciaAnterior = distancia;
        distancia += velocidad * dt;
        float yaw = muestrearRuta(distancia, tmp);
        salida.moverAuto(tmp.x, tmp.z, yaw, distancia - distanciaAnterior);
        if (!barreraIniciada && distancia > 4.2f) {
            barreraIniciada = true;
            assets.getSound(SONIDO_BARRERA).play(0.8f);
        }
        if (barreraIniciada && tBarrera < 1.3f) {
            tBarrera += dt;
            salida.setAperturaBarrera(suave(tBarrera / 1.25f));
        }
    }

    private final Vector3 objetivoFreddy = new Vector3();
    private final Vector3 cabezaFreddy = new Vector3();

    /** Freddy: mira al auto, sale corriendo y lo persigue hasta el porton (§2.68). */
    private void moverFreddy(float dt) {
        if (freddy == null) {
            return;
        }
        if (freddy.estaEnPuerta()) {
            if (t >= T_FREDDY_MIRA_AUTO) {
                salida.puntoAuto(def.carDriverEyeLocal[0], def.carDriverEyeLocal[1], def.carDriverEyeLocal[2], objetivoFreddy);
                freddy.mirarDesdePuerta(objetivoFreddy);
            }
            if (t >= T_FREDDY_CORRE) {
                freddy.iniciarPersecucion();
            }
            return;
        }
        // antes de que arranque, corre hacia la puerta del conductor; despues, a un punto 3.5 m detras del auto
        // (nunca se le echa encima: al llegar a ese punto solo lo sigue)
        if (t < T_ARRANCA) {
            salida.puntoAuto(2.2f, 0f, 0.2f, objetivoFreddy);
        } else {
            salida.puntoAuto(0f, 0f, -3.5f, objetivoFreddy);
        }
        freddy.actualizarPersecucion(dt, objetivoFreddy, camera.position);
    }

    private void moverCamara() {
        float[] ej = def.carDriverEyeLocal;
        if (t < 1.3f) {
            // de donde estaba el jugador a un costado de la puerta, mirando la manija
            float u = suave(t / 1.3f);
            salida.puntoAuto(1.95f, 1.52f, 0.25f, tmp);
            ojo.set(camInicioPos).lerp(tmp, u);
            salida.puntoAuto(1.0f, 0.92f, 0.1f, tmp2);
            tmp.set(camInicioPos).add(camInicioDir);
            mira.set(tmp).lerp(tmp2, u);
        } else if (t < T_SENTADO) {
            // se agacha y entra: por un punto intermedio junto al marco hasta el ojo del conductor
            float u = (t - 1.3f) / (T_SENTADO - 1.3f);
            float a = suave(Math.min(1f, u * 1.6f));
            float b = suave(Math.max(0f, (u - 0.35f) / 0.65f));
            salida.puntoAuto(1.95f, 1.52f, 0.25f, tmp);
            salida.puntoAuto(1.18f, 1.22f, 0.05f, tmp2);
            ojo.set(tmp).lerp(tmp2, a);
            salida.puntoAuto(ej[0], ej[1], ej[2], tmp);
            ojo.lerp(tmp, b);
            salida.puntoAuto(1.0f, 0.92f, 0.1f, tmp);
            salida.puntoAuto(ej[0], ej[1] - 0.06f, 8f, tmp2);
            mira.set(tmp).lerp(tmp2, suave(u));
        } else if (t < T_CORTE_CALLE) {
            salida.puntoAuto(ej[0], ej[1], ej[2], ojo);
            // respiracion: la cabeza sube y baja un poco; temblor del motor una vez encendido
            ojo.y += 0.012f * MathUtils.sin(t * 2.4f);
            if (t > T_FAROS - 0.05f) {
                float vib = t < T_ARRANCA ? 0.0025f : 0.004f;
                ojo.x += vib * MathUtils.sin(t * 61f);
                ojo.y += vib * MathUtils.sin(t * 47f + 1f);
            }
            salida.puntoAuto(ej[0], ej[1] - 0.06f, 8f, mira);
            if (t >= T_MIRA_FREDDY && t < T_ARRANCA) {
                // gira la cabeza hacia la pizzeria (lado del acompanante) y la sostiene en Freddy; cuando el sale
                // corriendo la mirada lo sigue, y vuelve al frente de golpe (0.35 s) justo antes de arrancar
                float ida = suave((t - T_MIRA_FREDDY) / 0.9f);
                float vuelta = suave((t - T_VUELTA) / 0.35f);
                float w = ida * (1f - vuelta);
                if (freddy != null) {
                    freddy.posicionCabeza(tmp);
                } else {
                    tmp.set(def.freddyDoorX, 1.75f, def.freddyDoorZ);
                }
                mira.lerp(tmp, w);
            } else if (t >= T_ARRANCA) {
                // mira hacia donde va: un punto de la ruta unos metros por delante
                muestrearRuta(distancia + 7f, tmp);
                tmp.y = 1.0f;
                mira.lerp(tmp, 0.55f);
                // Freddy detras: corte breve a una camara pegada al maletero, que va con el auto, mirando hacia atras.
                // Freddy corre a pocos metros, rojo por las luces traseras. (Primero se probo girar la cabeza del
                // conductor: desde el asiento se veia sobre todo el techo y Freddy quedaba diminuto en la luneta.)
                float u = (t - T_MIRA_ATRAS) / DUR_MIRA_ATRAS;
                if (freddy != null && u > 0f && u < 1f) {
                    salida.puntoAuto(0.45f, 1.05f, -2.9f, ojo);
                    ojo.y += 0.02f * MathUtils.sin(t * 37f);
                    freddy.posicionCabeza(cabezaFreddy);
                    mira.set(cabezaFreddy);
                    mira.y -= 0.35f;
                }
            }
        } else {
            // camara fija en la calle: sigue al auto mientras pasa y despues se queda con la pizzeria
            ojo.set(camCalle);
            salida.puntoAuto(0f, 0.9f, 0f, tmp);
            float u = suave((t - (T_CORTE_CALLE + 2.1f)) / 1.8f);
            // el letrero de la pizzeria (dos caras, ver build_exterior.py) y, debajo, el porton abierto donde quedo
            // Freddy mirando (§2.68): el encuadre final queda entre los dos
            miraCalle.set(def.lotMinX - 2.2f, 4.2f, def.lotMinZ - 1.4f);
            if (freddy != null) {
                freddy.posicionCabeza(tmp2);
                miraCalle.lerp(tmp2, 0.45f);
            }
            mira.set(tmp).lerp(miraCalle, u);
            // empuje lento hacia adelante al final
            float empuje = suave((t - (T_CORTE_CALLE + 2.5f)) / 3.5f);
            tmp2.set(miraCalle).sub(camCalle).nor().scl(1.2f * empuje);
            ojo.add(tmp2);
        }
        camera.position.set(ojo);
        camera.up.set(0f, 1f, 0f);
        camera.lookAt(mira);
        camera.update();
    }

    private static float suave(float u) {
        u = MathUtils.clamp(u, 0f, 1f);
        return u * u * (3f - 2f * u);
    }

    /** Barras de cine, fundidos de los cortes y el aviso para saltar. Se dibuja despues de la escena. */
    public void dibujarOverlay(SpriteBatch batch, ShapeRenderer shapes, BitmapFont fuente) {
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float barras = suave(t / 0.7f) * h * 0.085f;
        float negro = 0f;
        if (t > T_CORTE_CALLE - 0.25f && t < T_CORTE_CALLE + 0.35f) {
            negro = t < T_CORTE_CALLE ? (t - (T_CORTE_CALLE - 0.25f)) / 0.25f : 1f - (t - T_CORTE_CALLE) / 0.35f;
        }
        if (t > T_FUNDIDO) {
            negro = Math.max(negro, (t - T_FUNDIDO) / (DURACION - T_FUNDIDO - 0.3f));
        }
        negro = MathUtils.clamp(negro, 0f, 1f);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 1f);
        shapes.rect(0, 0, w, barras);
        shapes.rect(0, h - barras, w, barras);
        if (negro > 0f) {
            shapes.setColor(0f, 0f, 0f, negro);
            shapes.rect(0, 0, w, h);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        if (t < T_FUNDIDO) {
            omision.dibujar(batch, shapes, fuente, barras);
        }
    }

    public boolean fueSaltada() {
        return saltada;
    }

    public float getTiempo() {
        return t;
    }

    /** Detiene todo lo que la cinematica dejo sonando (marcha, arranque, respiracion, latido). */
    public void terminar() {
        terminada = true;
        if (sMarcha != null && idMarcha != -1) {
            sMarcha.stop(idMarcha);
        }
        if (sArranque != null && idArranque != -1) {
            sArranque.stop(idArranque);
        }
        if (sRespiracion != null && idRespiracion != -1) {
            sRespiracion.stop(idRespiracion);
        }
        if (sLatido != null && idLatido != -1) {
            sLatido.stop(idLatido);
        }
        idMarcha = idArranque = idRespiracion = idLatido = -1;
    }
}
