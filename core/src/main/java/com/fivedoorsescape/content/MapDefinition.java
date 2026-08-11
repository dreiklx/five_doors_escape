package com.fivedoorsescape.content;

/**
 * Describe el mapa a cargar (Architecture.md, principio rector: sin nombres de assets
 * hardcodeados en Java). La posicion/orientacion inicial del jugador viaja aqui en vez de
 * quedar fija en codigo, por la misma razon.
 */
public class MapDefinition {

    public String id;
    public String modelPath;
    public float scale = 1f;
    public float rotationXDegrees = 0f;

    public float playerStartX = 0f;
    public float playerStartY = 1.6f;
    public float playerStartZ = 0f;
    public float playerStartYawDegrees = 0f;

    /** Posicion inicial de Freddy -- fija, no derivada de playerStart (era fragil, ver hardening Fase 4). */
    public float freddyStartX = 0f;
    public float freddyStartZ = 0f;

    /** Punto de salida (condicion de victoria del Escape): distancia horizontal (X/Z). */
    public float exitX = 0f;
    public float exitZ = 0f;
    public float exitRadius = 1.5f;

    /** Posiciones fijas de guardia de Bonnie/Chica/Foxy (IdleState -- no patrullan, ver decision
     * de diseno del usuario 2026-08-02/03). */
    public float bonnieStartX = 0f;
    public float bonnieStartZ = 0f;
    public float chicaStartX = 0f;
    public float chicaStartZ = 0f;
    public float foxyStartX = 0f;
    public float foxyStartZ = 0f;

    /** Huella (X/Z) y altura del "stage" -- pedido explicito del usuario 2026-08-10: se debe
     * poder subir el pequeño escalon de forma natural (elevacion suave de la posicion, nunca un
     * bloqueo de colision) tanto el jugador como cualquier animatronico que camine sobre el.
     * stageHeight=0 (valor por defecto) desactiva el escalon por completo -- ver
     * CollisionWorld.configurarEscalon/alturaEscalonEn. */
    public float stageMinX = 0f;
    public float stageMaxX = 0f;
    public float stageMinZ = 0f;
    public float stageMaxZ = 0f;
    public float stageHeight = 0f;

    /** Posicion + orientacion de la llave en la cocina (pedido explicito del usuario 2026-08-10),
     * sobre una superficie real (mueble de cocina), no flotando. */
    public float keyX = 0f;
    public float keyY = 0f;
    public float keyZ = 0f;
    public float keyYawDegrees = 0f;

    /** Posicion + orientacion de Golden Freddy, sentado dentro del bano al final del pasillo
     * (pedido explicito del usuario 2026-08-10) -- estatico, sin IA de movimiento. */
    public float goldenFreddyX = 0f;
    public float goldenFreddyZ = 0f;
    public float goldenFreddyYawDegrees = 0f;
}
