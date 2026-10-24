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

    /** Huesos para el movimiento procedural de las cinematicas (CLAUDE.md §2.67): "cabeza", "torso" y, si la tiene,
     * "mandibula" -> nombre del nodo del esqueleto en el glb. Opcional: sin entrada, la entidad solo usa su animacion. */
    public ObjectMap<String, String> huesos = new ObjectMap<>();

    /** Pasos sincronizados con la animacion (CLAUDE.md §2.73): perfil por nombre ("interior" al caminar dentro de la
     * pizzeria, "carrera" para Freddy en la persecucion del estacionamiento). Los huesos de los pies van en "huesos"
     * con las claves "pieIzquierdo" y "pieDerecho". Sin perfiles, la entidad no tiene pasos. */
    public ObjectMap<String, PerfilPasos> pasos = new ObjectMap<>();

    public boolean aiControlled = false;

    public static class PerfilPasos {
        /** Variaciones (se elige una distinta de la anterior en cada pisada). */
        public String[] sonidos = new String[0];
        public float volumen = 0.8f;
        /** Distancia horizontal a la que la pisada deja de oirse; a menos de distanciaMinima suena al volumen pleno. */
        public float distanciaMinima = 1.5f;
        public float distanciaMaxima = 16f;
        public float tonoMin = 0.95f;
        public float tonoMax = 1.05f;
    }
}
