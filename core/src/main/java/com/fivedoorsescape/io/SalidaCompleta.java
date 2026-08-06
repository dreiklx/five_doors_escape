package com.fivedoorsescape.io;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * Señal de "cerrar TODO, no solo volver al menu de Swing" (pedido explicito del usuario
 * 2026-08-06: unificar las pantallas finales de Escape con las del juego principal -- Retry
 * vuelve al menu, Salir cierra el juego). Los dos proyectos son procesos independientes
 * (Architecture.md), y el codigo de salida del proceso de Escape no se puede usar de forma
 * confiable para esto porque se lanza envuelto en Gradle (gradlew.bat lwjgl3:run), que no
 * garantiza propagar el codigo de salida real de la aplicacion. En su lugar, se reutiliza el
 * mismo mecanismo de archivo compartido que ya usa handoff.json (~/.fivedoorsatfreddys/) --
 * LanzadorEscape del lado Swing revisa si este archivo existe justo despues de que el proceso
 * termina, y si esta presente, cierra la aplicacion Swing por completo en vez de volver al menu.
 */
public final class SalidaCompleta {

    private static final String RELATIVE_PATH = ".fivedoorsatfreddys/salir_completo.flag";

    private SalidaCompleta() {
    }

    public static void marcar() {
        FileHandle handle = Gdx.files.external(RELATIVE_PATH);
        handle.writeString("1", false);
    }
}
