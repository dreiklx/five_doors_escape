package com.fivedoorsescape;

import com.badlogic.gdx.Game;
import com.fivedoorsescape.screens.BootScreen;

/**
 * Punto de entrada real del MVP (Fase 4). BootScreen decide que cargar; GameplayScreen contiene
 * el vertical slice; NightGameOverScreen reemplaza la app al ser atrapado por Freddy.
 */
public class FiveDoorsEscapeGame extends Game {

    @Override
    public void create() {
        setScreen(new BootScreen(this));
    }

    @Override
    public void dispose() {
        if (getScreen() != null) {
            getScreen().dispose();
        }
    }
}
