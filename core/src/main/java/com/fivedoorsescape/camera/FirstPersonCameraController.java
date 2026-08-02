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
        float rightX = dirZ;
        float rightZ = -dirX;

        delta.x = dirX * forward + rightX * strafe;
        delta.z = dirZ * forward + rightZ * strafe;
        if (delta.len2() > 0.0001f) {
            delta.nor().scl(moveSpeed * deltaTime);
        }
        return delta;
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
        camera.update();
    }

    public float getYawDegrees() {
        return yawDegrees;
    }
}
