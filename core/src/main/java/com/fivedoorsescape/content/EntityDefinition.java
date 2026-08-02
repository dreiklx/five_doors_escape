package com.fivedoorsescape.content;

import com.badlogic.gdx.utils.ObjectMap;

/**
 * Describe un TIPO de entidad (no una instancia) leido desde JSON -- ningun nombre de modelo va
 * hardcodeado en Java (Architecture.md, principio rector). Las animaciones son opcionales: si
 * el mapa "animaciones" viene vacio, AnimationSystem debe omitir la entidad limpiamente
 * (Architecture.md, contrato de animaciones opcionales).
 */
public class EntityDefinition {

    public String id;
    public String modelPath;
    public float scale = 1f;
    public float rotationXDegrees = 0f;

    /** Nombre logico (p.ej. "idle", "walk") -> nombre del clip dentro del glb. Vacio si no aplica. */
    public ObjectMap<String, String> animaciones = new ObjectMap<>();

    public boolean aiControlled = false;
}
