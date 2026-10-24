package com.fivedoorsescape.world;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.fivedoorsescape.audio.PasosAnimatronicos;
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
 *   Susto (CLAUDE.md §2.74, una sola vez por partida): si el jugador se le acerca a menos de 2.3 m mirandolo (o a
 *   menos de 1.5 m aunque no lo mire), Freddy embiste hacia el (hasta 0.45 m, sin entrar en la camara), echa el torso
 *   adelante, abre la mandibula del todo y la cabeza se sacude; suenan un golpe, el servo y un grito corto, y la camara
 *   tiembla y se cierra (getIntensidadSusto). Su collider no se mueve: no empuja ni atrapa, no hay derrota.
 * - PERSIGUE (solo en la cinematica del auto): echa a correr hacia el auto (la animacion de caminar al 1.9x, hasta
 *   5.2 m/s), lo persigue mientras se aleja y queda atras. Sus pisadas salen de PasosAnimatronicos con el perfil
 *   "carrera" (asfalto), sincronizadas con la animacion (CLAUDE.md §2.73).
 * - EN_PORTON: se detiene en el porton abierto, sin salir del estacionamiento, y se queda mirando como se va el auto.
 */
public class FreddyExterior {

    public enum Estado { AUSENTE, EN_PUERTA, PERSIGUE, EN_PORTON }

    private static final float DISTANCIA_AMENAZA = 3.4f;
    private static final float GIRO_MAX_DESDE_PUERTA = 70f;
    private static final float VELOCIDAD_MAXIMA = 5.2f;
    private static final float ACELERACION = 6.5f;
    private static final float VELOCIDAD_ANIMACION_CORRER = 1.9f;
    private static final float YAW_PUERTA = -90f;
    private static final float DISTANCIA_SUSTO_MIRANDO = 2.3f;
    private static final float DISTANCIA_SUSTO = 1.5f;
    private static final float DURACION_SUSTO = 1.15f;
    private static final float EMBESTIDA_MAX = 0.45f;
    private static final float DURACION_GRITO = 0.75f;

    private final Entity freddy;
    private final TransformComponent transform;
    private final CollisionWorld mundo;
    private final ExteriorDefinition def;
    private final PoseProcedural pose;
    private final Sound sonidoServo;
    private final Sound sonidoRisa;
    private final Sound sonidoGrito;
    private final Sound sonidoGolpe;
    private final PasosAnimatronicos pasos;
    private final BoundingBox collider = new BoundingBox();
    private boolean colliderPuesto = false;

    private Estado estado = Estado.AUSENTE;
    private float amenaza = 0f;
    private boolean sonoAmenaza = false;
    private float velocidad = 0f;
    private final Vector3 mirarA = new Vector3();
    private boolean mirando = false;
    private final Vector3 tmp = new Vector3();
    private boolean sustoHecho = false;
    private float tiempoSusto = -1f;
    private float embestida = 0f;
    private long idGrito = -1;

    public FreddyExterior(Entity freddy, CollisionWorld mundo, ExteriorDefinition def, PoseProcedural pose,
            Sound servo, Sound risa, Sound grito, Sound golpe, PasosAnimatronicos pasos) {
        this.freddy = freddy;
        this.transform = Mappers.transform.get(freddy);
        this.mundo = mundo;
        this.def = def;
        this.pose = pose;
        this.sonidoServo = servo;
        this.sonidoRisa = risa;
        this.pasos = pasos;
        this.sonidoGrito = grito;
        this.sonidoGolpe = golpe;
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

    /** Durante el juego (exterior): reaccion de amenaza si el jugador se acerca, y el susto (una vez). */
    public void actualizarJuego(float dt, Vector3 ojoJugador, Vector3 direccionMirada) {
        if (estado != Estado.EN_PUERTA) {
            return;
        }
        float d = Vector3.dst(ojoJugador.x, 0f, ojoJugador.z, def.freddyDoorX, 0f, def.freddyDoorZ);
        actualizarSusto(dt, ojoJugador, direccionMirada, d);
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
        transform.yawDegrees = acercarAngulo(transform.yawDegrees, deseado, (tiempoSusto >= 0f ? 400f : 90f) * dt);
        mirarA.set(ojoJugador);
        mirando = amenaza > 0.001f;
        // la embestida es solo visual: el collider sigue en la puerta
        transform.position.set(def.freddyDoorX + MathUtils.sinDeg(transform.yawDegrees) * embestida, 0f,
                def.freddyDoorZ + MathUtils.cosDeg(transform.yawDegrees) * embestida);
    }

    private void actualizarSusto(float dt, Vector3 ojo, Vector3 mirada, float d) {
        if (!sustoHecho) {
            tmp.set(def.freddyDoorX - ojo.x, 1.4f - ojo.y, def.freddyDoorZ - ojo.z).nor();
            boolean loMira = mirada != null && tmp.dot(mirada) > 0.8f;
            if (d < DISTANCIA_SUSTO || (d < DISTANCIA_SUSTO_MIRANDO && loMira)) {
                sustoHecho = true;
                tiempoSusto = 0f;
                amenaza = 1f;
                sonoAmenaza = true;   // la risa de la amenaza no se suma encima del susto
                if (sonidoGolpe != null) {
                    sonidoGolpe.play(0.9f, 0.8f, 0f);
                }
                if (sonidoServo != null) {
                    sonidoServo.play(1f, 1.15f, 0f);
                }
                if (sonidoGrito != null) {
                    idGrito = sonidoGrito.play(0.55f, 0.82f, 0f);
                }
            }
        }
        if (tiempoSusto < 0f) {
            return;
        }
        tiempoSusto += dt;
        if (idGrito != -1 && tiempoSusto >= DURACION_GRITO) {
            sonidoGrito.stop(idGrito);   // un grito corto: el completo es el del jumpscare de verdad
            idGrito = -1;
        }
        // embiste en 0.12 s, se queda encima hasta 0.5 s y vuelve despacio a la puerta; nunca a menos de 0.9 m del ojo
        float t = tiempoSusto;
        float u = t < 0.12f ? t / 0.12f : t < 0.5f ? 1f : Math.max(0f, 1f - (t - 0.5f) / (DURACION_SUSTO - 0.5f));
        u = u * u * (3f - 2f * u);
        embestida = MathUtils.clamp(Math.min(EMBESTIDA_MAX, d - 0.9f), 0f, EMBESTIDA_MAX) * u;
        if (tiempoSusto >= DURACION_SUSTO) {
            tiempoSusto = -1f;
            embestida = 0f;
        }
    }

    /** 0..1: fuerza del susto en este instante (para el temblor y el cierre de la camara). 0 fuera del susto. */
    public float getIntensidadSusto() {
        if (tiempoSusto < 0f) {
            return 0f;
        }
        return tiempoSusto < 0.08f ? tiempoSusto / 0.08f : Math.max(0f, 1f - (tiempoSusto - 0.08f) / (DURACION_SUSTO - 0.08f));
    }

    public boolean isSustoHecho() {
        return sustoHecho;
    }

    /** Corta el grito si el susto queda a medias (pausa larga, cinematica, salida de la pantalla). */
    public void detenerSonidosSusto() {
        if (idGrito != -1 && sonidoGrito != null) {
            sonidoGrito.stop(idGrito);
            idGrito = -1;
        }
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
        detenerSonidosSusto();
        tiempoSusto = -1f;
        embestida = 0f;
        estado = Estado.PERSIGUE;
        velocidad = 0f;
        animar("walk", VELOCIDAD_ANIMACION_CORRER);
        if (pasos != null) {
            pasos.usarPerfil(freddy, "carrera");
        }
    }

    /**
     * Cinematica: corre hacia "objetivo" (un punto detras del auto) sin salir del estacionamiento: al llegar a la linea
     * del porton se detiene ahi (EN_PORTON).
     */
    public void actualizarPersecucion(float dt, Vector3 objetivo) {
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
            float susto = getIntensidadSusto();
            if (susto > 0f) {
                // torso adelante, mandibula abierta del todo y la cabeza que se sacude
                torso += 16f * susto;
                mandibula += 22f * susto;
                ladeo += MathUtils.sin(tiempoSusto * 55f) * 9f * susto;
                peso = 1f;
            }
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
