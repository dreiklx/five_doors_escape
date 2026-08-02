package com.fivedoorsescape.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.fivedoorsescape.FiveDoorsEscapeGame;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("five_doors_escape");
        config.setWindowedMode(960, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new FiveDoorsEscapeGame(), config);
    }
}
