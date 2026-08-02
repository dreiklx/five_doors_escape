package com.fivedoorsescape.content;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Administra las definiciones de contenido disponibles (EntityDefinition, MapDefinition),
 * leidas de JSON bajo content/entities/ y content/maps/. NO carga automaticamente todo el
 * contenido al arrancar -- es BootScreen/GameplayScreen quien decide explicitamente que pedir
 * (Architecture.md).
 */
public class ContentRegistry {

    private static final String ENTITIES_DIR = "content/entities/";
    private static final String MAPS_DIR = "content/maps/";

    private final Json json = new Json();
    private final ObjectMap<String, EntityDefinition> entityCache = new ObjectMap<>();
    private final ObjectMap<String, MapDefinition> mapCache = new ObjectMap<>();

    public EntityDefinition getEntityDefinition(String id) {
        EntityDefinition cached = entityCache.get(id);
        if (cached != null) {
            return cached;
        }
        FileHandle handle = Gdx.files.internal(ENTITIES_DIR + id + ".json");
        EntityDefinition def = json.fromJson(EntityDefinition.class, handle);
        entityCache.put(id, def);
        return def;
    }

    public MapDefinition getMapDefinition(String id) {
        MapDefinition cached = mapCache.get(id);
        if (cached != null) {
            return cached;
        }
        FileHandle handle = Gdx.files.internal(MAPS_DIR + id + ".json");
        MapDefinition def = json.fromJson(MapDefinition.class, handle);
        mapCache.put(id, def);
        return def;
    }
}
