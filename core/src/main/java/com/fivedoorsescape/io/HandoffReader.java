package com.fivedoorsescape.io;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Lee el archivo de traspaso en ~/.fivedoorsatfreddys/handoff.json. Un archivo ausente,
 * corrupto o incompleto NO es un error fatal (Architecture.md #7) -- este es el escenario
 * esperado cuando el proyecto se ejecuta directo desde el IDE sin pasar por Swing, asi que se
 * usan valores por defecto de desarrollo y se arranca igual.
 */
public class HandoffReader {

    private static final String RELATIVE_PATH = ".fivedoorsatfreddys/handoff.json";

    public static HandoffData read() {
        FileHandle handle = Gdx.files.external(RELATIVE_PATH);
        if (!handle.exists()) {
            Gdx.app.log("HandoffReader", "No handoff.json found at " + handle.file().getAbsolutePath()
                    + ", using defaults");
            return HandoffData.defaults();
        }

        try {
            JsonValue root = new JsonReader().parse(handle);
            String idiomaRaw = root.getString("idioma", HandoffData.Idioma.ESPANOL.name());
            int vidasFinales = root.getInt("vidasFinales", HandoffData.defaults().vidasFinales);

            HandoffData.Idioma idioma;
            try {
                idioma = HandoffData.Idioma.valueOf(idiomaRaw);
            } catch (IllegalArgumentException e) {
                Gdx.app.log("HandoffReader", "Valor de idioma desconocido '" + idiomaRaw + "', usando ESPANOL");
                idioma = HandoffData.Idioma.ESPANOL;
            }

            return new HandoffData(idioma, vidasFinales);
        } catch (Exception e) {
            Gdx.app.log("HandoffReader", "handoff.json invalido o corrupto, usando defaults: " + e.getMessage());
            return HandoffData.defaults();
        }
    }
}
