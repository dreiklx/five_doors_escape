package com.fivedoorsescape.io;

/**
 * Datos de traspaso Swing -> LibGDX (Architecture.md #7). Escritos por el proyecto Swing en
 * ~/.fivedoorsatfreddys/handoff.json al ganar la Noche 5; leidos aqui por HandoffReader.
 */
public class HandoffData {

    public enum Idioma {
        ESPANOL,
        INGLES
    }

    public final Idioma idioma;
    public final int vidasFinales;

    public HandoffData(Idioma idioma, int vidasFinales) {
        this.idioma = idioma;
        this.vidasFinales = vidasFinales;
    }

    public static HandoffData defaults() {
        return new HandoffData(Idioma.ESPANOL, 2);
    }
}
