package com.fivedoorsescape.content;

/**
 * Disposicion del exterior de la pizzeria (victoria de Escape: puerta de emergencia, estacionamiento y auto).
 * Se lee de content/maps/exterior.json, el mismo archivo con el que tools/cinematics/escape_victory/build_exterior.py
 * construye models/exterior_parking.glb -- colisiones, camaras y modelo salen de una sola fuente.
 * Coordenadas del juego (Y arriba). Los nombres siguen el JSON.
 */
public class ExteriorDefinition {

    public String id;
    public String modelPath;
    public String carModelPath;

    /** Pared oeste del deposito: cara exterior (fachada) y cara interior -- es una pared gruesa (0.71 m) -- y el
     * tramo que se reemplaza por un panel con el hueco de la puerta. */
    public float wallX;
    public float wallInnerX;
    public float wallPanelMinZ;
    public float wallPanelMaxZ;
    public float wallPanelTop;
    /** Caja del estante que tapaba la pared oeste del deposito: se retira para dejar llegar a la puerta. */
    public float shelfRemoveMinX;
    public float shelfRemoveMaxX;
    public float shelfRemoveMinZ;
    public float shelfRemoveMaxZ;

    public float emergencyDoorZ;
    public float emergencyDoorWidth;
    public float emergencyDoorHeight;

    /** Hoja de la puerta de salida existente (pizzeria.glb) y su bisagra. */
    public String exitDoorNode;
    public float exitDoorHingeX;
    public float exitDoorHingeZ;
    public float exitDoorMinX;
    public float exitDoorMaxX;
    public float exitDoorMinZ;
    public float exitDoorMaxZ;

    public float lotMinX;
    public float lotMinZ;
    public float lotMaxZ;
    public float northFaceX;
    public float gateMinX;
    public float gateMaxX;
    public float roadZ;

    public float carX;
    public float carZ;
    public float carYawDegrees;
    public float carHalfWidth;
    public float carHalfLength;
    /** Bisagra de la puerta del conductor y ojo del conductor, en coordenadas locales del auto (+Z = frente). */
    public float[] carDoorHingeLocal;
    public float[] carDriverEyeLocal;

    public float freddyDoorX;
    public float freddyDoorZ;

    public Lamp[] lamps;
    public WallLamp wallLamp;
    public Prop[] props;

    public static class Lamp {
        public float x;
        public float z;
        public float yaw;
        public float height;
        public float intensity;
        public boolean flicker;
    }

    public static class WallLamp {
        public float x;
        public float y;
        public float z;
    }

    public static class Prop {
        public String model;
        public float x;
        public float z;
        public float yaw;
        /** Semi-extensiones locales [x, z] del collider (null = sin colision). */
        public float[] collider;
    }
}
