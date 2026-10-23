package com.fivedoorsescape.content;

import com.badlogic.gdx.utils.ObjectMap;

/**
 * Equilibrio del aturdimiento con la linterna (CLAUDE.md §2.58), leido de content/balance/flashlight.json: el UNICO
 * lugar donde viven los umbrales y duraciones. Un animatronico sin entrada en "stun" (Freddy) no se aturde.
 */
public class FlashlightBalance {

    /** Alcance del haz (m) y semiangulo del cono (grados) dentro del cual un animatronico cuenta como iluminado. */
    public float beamRange = 11f;
    public float beamHalfAngleDegrees = 22f;
    /** Altura sobre los pies del punto que tiene que alcanzar el haz (pecho). */
    public float targetHeight = 1.15f;
    public ObjectMap<String, Stun> stun = new ObjectMap<>();

    public static class Stun {
        /** Ciclos validos de encendido/apagado necesarios. */
        public int cycles;
        /** Duracion del aturdimiento (s). */
        public float seconds;
    }
}
