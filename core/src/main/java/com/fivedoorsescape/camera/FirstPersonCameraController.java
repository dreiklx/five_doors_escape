package com.fivedoorsescape.camera;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * Camara FPS propia (no el ejemplo de referencia de LibGDX): WASD + mouse-look. No mueve la
 * posicion directamente -- expone el movimiento deseado via computeWasdDelta() para que el
 * llamador lo resuelva contra CollisionWorld antes de aplicarlo (Architecture.md: "consciente de
 * colision").
 */
public class FirstPersonCameraController {

    private final PerspectiveCamera camera;
    private float yawDegrees;
    private float pitchDegrees = 0f;

    private float mouseSensitivity = 0.15f;
    private float moveSpeed = 3.5f;

    // Head bob (pedido explicito del usuario 2026-08-10: "que se sienta mas como que el jugador
    // realmente esta caminando", muy sutil, sin marear). Puramente cosmetico -- ver aplicarBob():
    // solo desplaza camera.position un poco despues de fijarla en la posicion real ya resuelta
    // contra colisiones; NUNCA toca playerTransform.position ni el yaw/pitch controlados por el
    // mouse. Basado en DISTANCIA recorrida de verdad (no en tiempo), no en input crudo -- si el
    // jugador queda atascado deslizandose contra una pared, el bob se ralentiza junto con el
    // movimiento real en vez de seguir "caminando en el aire" a velocidad constante.
    private float bobFase = 0f;
    private float bobEnvolvente = 0f; // 0..1 -- sube/baja LINEAL, llega a exactamente 0 en reposo

    // Ciclos completos de balanceo horizontal por unidad de mundo recorrida (el vertical es el
    // doble de frecuente que el horizontal -- un rebote por CADA pie, como al caminar de verdad;
    // patron estandar de head bob, no un capricho). Con moveSpeed=3.5 y este valor, un ciclo
    // horizontal completo dura ~1.4 unidades recorridas (~0.4s a velocidad maxima).
    private static final float BOB_CICLOS_POR_UNIDAD = 0.7f;
    private static final float BOB_AMPLITUD_VERTICAL = 0.03f;
    private static final float BOB_AMPLITUD_HORIZONTAL = 0.015f;
    // Tiempo real en segundos para que el bob suba a intensidad completa al empezar a moverse, o
    // baje a exactamente cero al detenerse -- evita tanto un salto brusco como (mas importante,
    // pedido explicito del usuario) cualquier residual cuando el jugador esta quieto.
    private static final float BOB_TIEMPO_TRANSICION = 0.15f;

    private final Vector3 bobDerechaTmp = new Vector3();
    private final Vector3 bobOffsetTmp = new Vector3();

    public FirstPersonCameraController(PerspectiveCamera camera, float initialYawDegrees) {
        this.camera = camera;
        this.yawDegrees = initialYawDegrees;
    }

    public void update() {
        if (Gdx.input.isCursorCatched()) {
            yawDegrees -= Gdx.input.getDeltaX() * mouseSensitivity;
            pitchDegrees -= Gdx.input.getDeltaY() * mouseSensitivity;
            pitchDegrees = MathUtils.clamp(pitchDegrees, -89f, 89f);
        }
    }

    public Vector3 computeWasdDelta(float deltaTime) {
        float forward = 0f;
        float strafe = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) forward += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) forward -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) strafe += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) strafe -= 1f;

        Vector3 delta = new Vector3();
        if (forward == 0f && strafe == 0f) {
            return delta;
        }

        float yawRad = yawDegrees * MathUtils.degreesToRadians;
        float dirX = MathUtils.sin(yawRad);
        float dirZ = MathUtils.cos(yawRad);
        // Vector "derecha" real respecto a la direccion de mirada -- invertido hasta ahora
        // (bug real reportado por el usuario 2026-08-05: A movia a la derecha, D a la izquierda).
        // El signo correcto es (-dirZ, dirX), no (dirZ, -dirX): verificado jugando, sin tocar el
        // mouse-look (yaw) ni W/S, que ya funcionaban bien.
        float rightX = -dirZ;
        float rightZ = dirX;

        delta.x = dirX * forward + rightX * strafe;
        delta.z = dirZ * forward + rightZ * strafe;
        if (delta.len2() > 0.0001f) {
            delta.nor().scl(moveSpeed * deltaTime);
        }
        return delta;
    }

    /** Avanza el head bob segun cuanto se movio de verdad el jugador este frame (post-colision,
     * nunca segun input crudo). Llamar UNA vez por frame, antes de applyToCamera(). */
    public void actualizarBob(float dt, float distanciaMovidaEsteFrame) {
        boolean seEstaMoviendo = distanciaMovidaEsteFrame > 0.0001f;
        float objetivo = seEstaMoviendo ? 1f : 0f;
        float paso = dt / BOB_TIEMPO_TRANSICION;
        if (bobEnvolvente < objetivo) {
            bobEnvolvente = Math.min(objetivo, bobEnvolvente + paso);
        } else if (bobEnvolvente > objetivo) {
            bobEnvolvente = Math.max(objetivo, bobEnvolvente - paso);
        }
        if (seEstaMoviendo) {
            bobFase += distanciaMovidaEsteFrame * BOB_CICLOS_POR_UNIDAD * MathUtils.PI2;
            if (bobFase > MathUtils.PI2 * 1000f) {
                bobFase -= MathUtils.PI2 * 1000f; // evita crecimiento sin limite en sesiones largas
            }
        }
    }

    public void applyToCamera(Vector3 position) {
        camera.position.set(position);
        float yawRad = yawDegrees * MathUtils.degreesToRadians;
        float pitchRad = pitchDegrees * MathUtils.degreesToRadians;
        camera.direction.set(
                MathUtils.sin(yawRad) * MathUtils.cos(pitchRad),
                MathUtils.sin(pitchRad),
                MathUtils.cos(yawRad) * MathUtils.cos(pitchRad)
        ).nor();
        camera.up.set(Vector3.Y);

        // Head bob: desplazamiento puramente cosmetico de camera.position, aplicado DESPUES de
        // fijarla en la posicion real (arriba) -- playerTransform.position (colisiones/IA) nunca
        // se toca, ver comentario del campo bobFase. bobEnvolvente==0 en reposo (exactamente, no
        // asintoticamente) -> offset exactamente (0,0,0), camara perfectamente estable.
        if (bobEnvolvente > 0f) {
            float vertical = MathUtils.sin(bobFase * 2f) * BOB_AMPLITUD_VERTICAL * bobEnvolvente;
            float horizontal = MathUtils.sin(bobFase) * BOB_AMPLITUD_HORIZONTAL * bobEnvolvente;
            bobDerechaTmp.set(camera.direction).crs(camera.up).nor();
            bobOffsetTmp.set(camera.up).scl(vertical).add(bobDerechaTmp.scl(horizontal));
            camera.position.add(bobOffsetTmp);
        }

        camera.update();
    }

    public float getYawDegrees() {
        return yawDegrees;
    }

    /** Reinicia la orientacion (usado al reaparecer tras ser atrapado, ver mecanica de vidas). */
    public void resetOrientation(float yawDegreesNuevo) {
        this.yawDegrees = yawDegreesNuevo;
        this.pitchDegrees = 0f;
    }
}
