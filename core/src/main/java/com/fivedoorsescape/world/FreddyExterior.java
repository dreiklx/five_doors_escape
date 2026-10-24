package com.fivedoorsescape.world;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.fivedoorsescape.cinematics.PoseProcedural;
import com.fivedoorsescape.content.ExteriorDefinition;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AnimationComponent;
import com.fivedoorsescape.ecs.components.ModelComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;

/**
 * Freddy fuera de la pizzeria (CLAUDE.md §2.68). Desde que se abre la salida la IA esta congelada (§2.49): fuera de
 * aqui nadie puede atraparlo, asi que la victoria y una atrapada nunca coinciden.
 *
 * - EN_PUERTA: de pie en el hueco de la puerta de emergencia que el jugador dejo abierta (la explicacion visual de
 *   por que esta afuera: lo siguio). Tiene un collider de su tamano (antes se lo atravesaba como a un decorado) que
 *   tapa el regreso al edificio. Si el jugador se acerca a menos de 3.4 m, lo amenaza: gira el cuerpo y la cabeza
 *   hacia el, se inclina, abre la mandibula y suenan un servo y su risa (una vez por acercamiento). No ataca.
 * - PERSIGUE (solo en la cinematica del auto): echa a correr hacia el auto (la animacion de caminar al 1.9x, hasta
 *   5.2 m/s), lo persigue mientras se aleja y queda atras.
 * - EN_PORTON: se detiene en el porton abierto, sin salir del estacionamiento, y se queda mirando como se va el auto.
 */
public class FreddyExterior {

    public enum Estado { AUSENTE, EN_PUERTA, PERSIGUE, EN_PORTON }

    public static final String SONIDO_PASO = "audio/sfx/freddy_run_step.wav";

    private static final float DISTANCIA_AMENAZA = 3.4f;
    private static final float GIRO_MAX_DESDE_PUERTA = 70f;
    private static final float VELOCIDAD_MAXIMA = 5.2f;
    private static final float ACELERACION = 6.5f;
    private static final float VELOCIDAD_ANIMACION_CORRER = 1.9f;
    private static final float PERIODO_PASO = 0.31f;
    private static final float YAW_PUERTA = -90f;

    private final Entity freddy;
    private final TransformComponent transform;
    private final CollisionWorld mundo;
    private final ExteriorDefinition def;
    private final PoseProcedural pose;
    private final Sound sonidoServo;
    private final Sound sonidoRisa;
    private final Sound sonidoPaso;
    private final BoundingBox collider = new BoundingBox();
    private boolean colliderPuesto = false;

    private Estado estado = Estado.AUSENTE;
    private float amenaza = 0f;
    private boolean sonoAmenaza = false;
    private float velocidad = 0f;
    private float relojPaso = 0f;
    private final Vector3 mirarA = new Vector3();
    private boolean mirando = false;
    private final Vector3 tmp = new Vector3();

    public FreddyExterior(Entity freddy, CollisionWorld mundo, ExteriorDefinition def, PoseProcedural pose,
            Sound servo, Sound risa, Sound paso) {
        this.freddy = freddy;
        this.transform = Mappers.transform.get(freddy);
        this.mundo = mundo;
        this.def = def;
        this.pose = pose;
        this.sonidoServo = servo;
        this.sonidoRisa = risa;
        this.sonidoPaso = paso;
    }

    public Estado getEstado() {
        return estado;
    }

    public boolean estaEnPuerta() {
        return estado == Estado.EN_PUERTA;
    }

    /** Lo deja en el hueco de la puerta de emergencia, mirando al estacionamiento, con su collider. */
    public void colocarEnPuerta() {
        if (estado != Estado.AUSENTE) {
            return;
        }
        estado = Estado.EN_PUERTA;
        transform.position.set(def.freddyDoorX, 0f, def.freddyDoorZ);
        transform.yawDegrees = YAW_PUERTA;
        animar("idle", 1f);
        // del suelo hasta la cabeza (la caja del jugador va de 0.7 a 2.5 m); 0.32 m de semiancho, el torso de Freddy
        collider.set(new Vector3(def.freddyDoorX - 0.32f, 0f, def.freddyDoorZ - 0.32f),
                new Vector3(def.freddyDoorX + 0.32f, 2.3f, def.freddyDoorZ + 0.32f));
        mundo.addStaticCollider(collider);
        colliderPuesto = true;
    }

    /** Durante el juego (exterior): reaccion de amenaza si el jugador se acerca. */
    public void actualizarJuego(float dt, Vector3 ojoJugador) {
        if (estado != Estado.EN_PUERTA) {
            return;
        }
        float d = Vector3.dst(ojoJugador.x, 0f, ojoJugador.z, transform.position.x, 0f, transform.position.z);
        boolean cerca = d < DISTANCIA_AMENAZA;
        amenaza = MathUtils.clamp(amenaza + (cerca ? dt / 0.6f : -dt / 1.5f), 0f, 1f);
        if (cerca && !sonoAmenaza) {
            sonoAmenaza = true;
            if (sonidoServo != null) {
                sonidoServo.play(0.7f);
            }
            if (sonidoRisa != null) {
                sonidoRisa.play(0.5f, 0.85f, 0f);
            }
        } else if (!cerca && d > DISTANCIA_AMENAZA + 1.5f) {
            sonoAmenaza = false;   // volver a acercarse lo vuelve a provocar
        }
        // el cuerpo gira hacia el jugador (sin dar la espalda al estacionamiento), a 90 grados/s
        float objetivo = MathUtils.atan2(ojoJugador.x - transform.position.x, ojoJugador.z - transform.position.z)
                * MathUtils.radiansToDegrees;
        float deseado = YAW_PUERTA + MathUtils.clamp(diferencia(objetivo, YAW_PUERTA), -GIRO_MAX_DESDE_PUERTA,
                GIRO_MAX_DESDE_PUERTA) * amenaza;
        transform.yawDegrees = acercarAngulo(transform.yawDegrees, deseado, 90f * dt);
        mirarA.set(ojoJugador);
        mirando = amenaza > 0.001f;
    }

    /** Cinematica: hacia donde mira la cabeza mientras sigue en la puerta (null = sin mirada). */
    public void mirarDesdePuerta(Vector3 punto) {
        if (punto == null) {
            mirando = false;
            return;
        }
        mirarA.set(punto);
        mirando = true;
        amenaza = 1f;
    }

    /** Cinematica: sale corriendo; su collider deja de existir (ya no esta en la puerta). */
    public void iniciarPersecucion() {
        if (estado != Estado.EN_PUERTA) {
            return;
        }
        quitarCollider();
        estado = Estado.PERSIGUE;
        velocidad = 0f;
        relojPaso = PERIODO_PASO * 0.5f;
        animar("walk", VELOCIDAD_ANIMACION_CORRER);
    }

    /**
     * Cinematica: corre hacia "objetivo" (un punto detras del auto) sin salir del estacionamiento: al llegar a la linea
     * del porton se detiene ahi (EN_PORTON). camara: para el volumen de los pasos.
     */
    public void actualizarPersecucion(float dt, Vector3 objetivo, Vector3 camara) {
        if (estado == Estado.EN_PORTON) {
            mirarA.set(objetivo.x, 1.2f, objetivo.z);
            mirando = true;
            return;
        }
        if (estado != Estado.PERSIGUE) {
            return;
        }
        float zTope = def.lotMinZ + 0.8f;
        float xMin = def.gateMinX + 0.7f, xMax = def.gateMaxX - 0.7f;
        tmp.set(objetivo).sub(transform.position);
        tmp.y = 0f;
        float d = tmp.len();
        velocidad = Math.min(VELOCIDAD_MAXIMA, velocidad + ACELERACION * dt);
        if (d > 0.05f) {
            float yaw = MathUtils.atan2(tmp.x, tmp.z) * MathUtils.radiansToDegrees;
            transform.yawDegrees = acercarAngulo(transform.yawDegrees, yaw, 300f * dt);
            float paso = Math.min(d, velocidad * dt);
            transform.position.x += MathUtils.sinDeg(transform.yawDegrees) * paso;
            transform.position.z += MathUtils.cosDeg(transform.yawDegrees) * paso;
        }
        // cerca del porton se encamina al hueco y se planta en la linea
        if (transform.position.z < zTope + 2.5f) {
            transform.position.x = MathUtils.lerp(transform.position.x, MathUtils.clamp(transform.position.x, xMin, xMax),
                    Math.min(1f, dt * 3f));
        }
        if (transform.position.z <= zTope) {
            transform.position.z = zTope;
            estado = Estado.EN_PORTON;
            animar("idle", 1f);
            if (sonidoServo != null) {
                sonidoServo.play(0.35f, 0.8f, 0f);
            }
        }
        relojPaso -= dt;
        if (estado == Estado.PERSIGUE && relojPaso <= 0f) {
            relojPaso += PERIODO_PASO;
            if (sonidoPaso != null) {
                float dist = camara.dst(transform.position);
                float vol = MathUtils.clamp(1.1f - dist / 18f, 0.08f, 0.95f);
                sonidoPaso.play(vol, MathUtils.random(0.9f, 1.08f), 0f);
            }
        }
        mirarA.set(objetivo.x, 1.3f, objetivo.z);
        mirando = true;
    }

    public void posicionCabeza(Vector3 out) {
        if (pose != null && pose.cabezaMundo(out)) {
            out.y += 0.35f;
            return;
        }
        out.set(transform.position.x, transform.position.y + 1.9f, transform.position.z);
    }

    public Vector3 getPosicion() {
        return transform.position;
    }

    // ------------------------------------------------------------------ pose procedural (cabeza, torso, mandibula)

    public void antesDeAnimar() {
        if (pose != null) {
            pose.restaurar();
        }
    }

    public void despuesDeAnimar() {
        if (pose == null || estado == Estado.AUSENTE) {
            return;
        }
        float torso = 0f, mandibula = 0f, peso = mirando ? amenaza : 0f, ladeo = 0f;
        if (estado == Estado.EN_PUERTA) {
            torso = 9f * amenaza;
            mandibula = 12f * amenaza;
            ladeo = -5f * amenaza;
        } else if (estado == Estado.PERSIGUE) {
            torso = 14f;                       // corriendo, echado hacia adelante
            peso = 0.7f;
            mandibula = 8f;
        } else {
            torso = 4f;
            peso = 0.9f;
            ladeo = 7f;
        }
        pose.aplicar(transform.yawDegrees, mirando ? mirarA : null, peso, ladeo, torso, mandibula);
    }

    public void quitarCollider() {
        if (colliderPuesto) {
            mundo.removeStaticCollider(collider);
            colliderPuesto = false;
        }
    }

    public boolean tieneCollider() {
        return colliderPuesto;
    }

    public BoundingBox getCollider() {
        return collider;
    }

    private void animar(String nombre, float velocidadAnimacion) {
        AnimationComponent an = Mappers.animation.get(freddy);
        ModelComponent model = Mappers.model.get(freddy);
        if (an == null || model == null) {
            return;
        }
        String clip = an.clipsPorNombreLogico.get(nombre);
        if (clip == null) {
            return;
        }
        model.scene.animationController.setAnimation(clip, -1, velocidadAnimacion, null);
        an.nombreLogicoActual = nombre;
        an.nombreLogicoAplicado = nombre;   // AnimationSystem no la vuelve a poner a velocidad 1
    }

    private static float diferencia(float a, float b) {
        float d = (a - b) % 360f;
        if (d > 180f) {
            d -= 360f;
        } else if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    private static float acercarAngulo(float actual, float objetivo, float maxPaso) {
        float d = diferencia(objetivo, actual);
        return actual + MathUtils.clamp(d, -maxPaso, maxPaso);
    }
}
