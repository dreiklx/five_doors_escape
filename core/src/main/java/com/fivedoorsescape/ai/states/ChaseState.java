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

    private static final Vector3 tmpDireccion = new Vector3();
    private static final Vector3 tmpDelta = new Vector3();

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

            tmpDelta.set(tmpDireccion).scl(ai.velocidadPersecucion * deltaTime);
            if (ai.collisionWorld != null) {
                CollisionComponent collision = Mappers.collision.get(entity);
                propio.position.set(ai.collisionWorld.resolveMovement(propio.position, tmpDelta, collision.halfExtents));
            } else {
                propio.position.add(tmpDelta);
            }
        }
    }

    @Override
    public void exit(Entity entity) {
    }

    @Override
    public boolean onMessage(Entity entity, Telegram telegram) {
        return false;
    }
}
