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
}
