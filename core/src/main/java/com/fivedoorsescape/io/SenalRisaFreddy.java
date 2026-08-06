package com.fivedoorsescape.io;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

/**
 * Señal cruzada de proceso (pedido explicito del usuario 2026-08-06): el lado Swing sigue
 * reproduciendo LesToreadorsRemix.wav durante toda la transicion + partida de Escape (procesos
 * completamente independientes, Architecture.md), y debe bajarle el volumen ~25% justo despues
 * de que suena la risa de Freddy aqui en Escape -- Swing no tiene forma de saber cuando eso pasa
 * salvo por este mismo mecanismo de archivo compartido ya establecido (ver SalidaCompleta, mismo
 * directorio que handoff.json).
 */
public final class SenalRisaFreddy {

    private static final String RELATIVE_PATH = ".fivedoorsatfreddys/risa_freddy.flag";

    private SenalRisaFreddy() {
    }

    public static void marcar() {
        FileHandle handle = Gdx.files.external(RELATIVE_PATH);
        handle.writeString("1", false);
    }
}
