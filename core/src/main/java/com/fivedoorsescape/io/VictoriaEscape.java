package com.fivedoorsescape.io;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * Senal de "Escape completado de verdad" para Five Doors at Freddy's (CLAUDE.md §1.66): FDAF la consume al volver
 * al menu (com.fdaf.util.LogroEscape) para desbloquear la estrella permanente y mostrar el agradecimiento de NYDER.
 * Mismo mecanismo de archivo compartido que handoff.json y SalidaCompleta (~/.fivedoorsatfreddys/). Solo se escribe
 * en el unico punto que registra la victoria (GameplayScreen, "escapado", al terminar o saltar la cinematica del
 * auto): abrir la puerta de salida, rendirse, perder o cerrar Escape no la escriben. Se escribe a un temporal y se
 * renombra, asi FDAF nunca puede leer un archivo a medias; Escape no toca config.properties (solo FDAF la escribe).
 */
public final class VictoriaEscape {

    private static final String RELATIVE_PATH = ".fivedoorsatfreddys/escape_victoria.flag";

    private VictoriaEscape() {
    }

    public static void marcar() {
        try {
            FileHandle destino = Gdx.files.external(RELATIVE_PATH);
            FileHandle temporal = Gdx.files.external(RELATIVE_PATH + ".tmp");
            temporal.writeString(String.valueOf(System.currentTimeMillis()), false);
            temporal.moveTo(destino);
            Gdx.app.log("VictoriaEscape", "victoria registrada en " + destino.file().getAbsolutePath());
        } catch (Exception e) {
            // nunca rompe la victoria: en el peor caso FDAF no se entera de esta
            Gdx.app.error("VictoriaEscape", "no se pudo escribir la senal de victoria", e);
        }
    }
}
