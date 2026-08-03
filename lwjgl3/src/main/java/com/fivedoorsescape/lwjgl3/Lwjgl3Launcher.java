package com.fivedoorsescape.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.fivedoorsescape.FiveDoorsEscapeGame;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("five_doors_escape");
        // Pantalla completa segun el flujo final pretendido (ver memoria de Claude
        // "project-libgdx-office-spawn-exit-design"): el Escape reemplaza visualmente a la
        // ventana de Swing, sin bordes de ventana de por medio.
        config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new FiveDoorsEscapeGame(), config);
    }
}
