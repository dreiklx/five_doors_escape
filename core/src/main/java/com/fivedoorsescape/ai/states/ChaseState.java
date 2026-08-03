package com.fivedoorsescape.ai.states;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ai.fsm.State;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AIComponent;
import com.fivedoorsescape.ecs.components.AnimationComponent;
import com.fivedoorsescape.ecs.components.CollisionComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;

/** Avanza hacia el jugador cada frame y rota para encararlo. Atrapa o pierde el rastro segun distancia. */
public class ChaseState implements State<Entity> {

    public static final ChaseState INSTANCE = new ChaseState();

    /** Mismo tope que GameplayScreen.MAX_FRAME_DELTA -- evita que un deltaTime enorme (p.ej. el
     * primer frame tras la carga de assets) haga que Freddy atraviese de un salto una pared
     * delgada sin que ninguna posicion intermedia registre colision. */
    private static final float MAX_FRAME_DELTA = 0.1f;

    /** Ventana de muestreo para detectar atasco (ver comentario mas abajo). */
    private static final float INTERVALO_MUESTRA = 0.3f;
    /** Avance minimo esperado en una ventana de muestreo para NO considerarse atascado. */
    private static final float AVANCE_MINIMO = 0.15f;
    /** Tiempo atascado antes de aplicar el empujon lateral. */
    private static final float UMBRAL_ATASCO = 0.6f;

    private static final Vector3 tmpDireccion = new Vector3();
    private static final Vector3 tmpDelta = new Vector3();
    private static final Vector3 tmpMovimiento = new Vector3();

    private ChaseState() {
    }

    @Override
    public void enter(Entity entity) {
        AnimationComponent animation = Mappers.animation.get(entity);
        if (animation != null && animation.tieneAnimaciones()) {
            animation.nombreLogicoActual = "walk";
        }
    }

    @Override
    public void update(Entity entity) {
        AIComponent ai = Mappers.ai.get(entity);
        if (ai.objetivo == null) {
            return;
        }
        TransformComponent propio = Mappers.transform.get(entity);
        TransformComponent delJugador = Mappers.transform.get(ai.objetivo);

        // Distancia horizontal (XZ) unicamente -- ver nota en IdleState sobre por que la 3D
        // completa queda dominada por la diferencia de altura pies-vs-ojos y nunca se cierra.
        tmpDireccion.set(delJugador.position).sub(propio.position);
        tmpDireccion.y = 0f;
        float distancia = tmpDireccion.len();
        if (distancia <= ai.rangoAtrape) {
            ai.stateMachine.changeState(CaughtState.INSTANCE);
            return;
        }
        if (distancia > ai.rangoDeteccion * 1.5f) {
            ai.stateMachine.changeState(IdleState.INSTANCE);
            return;
        }

        if (tmpDireccion.len2() > 0.0001f) {
            tmpDireccion.nor();
            float deltaTime = Math.min(Gdx.graphics.getDeltaTime(), MAX_FRAME_DELTA);
            propio.yawDegrees = MathUtils.atan2(tmpDireccion.x, tmpDireccion.z) * MathUtils.radiansToDegrees;

            // Deteccion de atasco: la persecucion es una linea recta hacia el objetivo con
            // resolucion de colision eje por eje (X, luego Z) -- si esa linea recta pasa lo
            // bastante cerca de la esquina de un obstaculo delgado (p.ej. el marco de una puerta),
            // el eje bloqueado puede quedar congelado para siempre mientras el otro eje sigue
            // "avanzando" sin cerrar la distancia real (confirmado en ejecucion real: Freddy
            // atascado indefinidamente contra el marco de la puerta de la oficina). Sin
            // pathfinding real en el MVP, la solucion pragmatica es detectar la falta de avance
            // real y aplicar un empujon lateral perpendicular para rodear el obstaculo.
            actualizarDeteccionAtasco(ai, propio, deltaTime);

            if (ai.tiempoAtascado > UMBRAL_ATASCO) {
                // Un empujon perpendicular generico puede empujar hacia el lado equivocado
                // segun la geometria exacta (confirmado en ejecucion real: empeoraba el atasco
                // en vez de resolverlo). En cambio, moverse en linea recta por el eje horizontal
                // con mayor diferencia respecto al objetivo es lo que realmente hace falta para
                // deslizarse hacia el centro de un hueco angosto (puerta) y liberar el otro eje.
                float diferenciaX = delJugador.position.x - propio.position.x;
                float diferenciaZ = delJugador.position.z - propio.position.z;
                if (Math.abs(diferenciaX) >= Math.abs(diferenciaZ)) {
                    tmpMovimiento.set(Math.signum(diferenciaX), 0f, 0f);
                } else {
                    tmpMovimiento.set(0f, 0f, Math.signum(diferenciaZ));
                }
            } else {
                tmpMovimiento.set(tmpDireccion);
            }

            tmpDelta.set(tmpMovimiento).scl(ai.velocidadPersecucion * deltaTime);
            if (ai.collisionWorld != null) {
                CollisionComponent collision = Mappers.collision.get(entity);
                propio.position.set(ai.collisionWorld.resolveMovement(propio.position, tmpDelta, collision.halfExtents));
            } else {
                propio.position.add(tmpDelta);
            }
        }
    }

    private void actualizarDeteccionAtasco(AIComponent ai, TransformComponent propio, float deltaTime) {
        if (Float.isNaN(ai.posicionMuestreada.x)) {
            ai.posicionMuestreada.set(propio.position);
            return;
        }
        ai.temporizadorMuestra += deltaTime;
        if (ai.temporizadorMuestra < INTERVALO_MUESTRA) {
            return;
        }
        float avance = propio.position.dst(ai.posicionMuestreada);
        if (avance < AVANCE_MINIMO) {
            ai.tiempoAtascado += ai.temporizadorMuestra;
        } else {
            ai.tiempoAtascado = 0f;
        }
        ai.posicionMuestreada.set(propio.position);
        ai.temporizadorMuestra = 0f;
    }

    @Override
    public void exit(Entity entity) {
    }

    @Override
    public boolean onMessage(Entity entity, Telegram telegram) {
        return false;
    }
}
