package com.fivedoorsescape.ecs.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Mapa nombre-logico -> nombre-de-clip para una entidad animada, mas el nombre logico
 * actualmente en reproduccion. Si el mapa viene vacio (personaje sin animaciones, p.ej.
 * Bonnie/Chica/Foxy), AnimationSystem debe omitir la entidad limpiamente -- contrato de
 * animaciones opcionales de Architecture.md.
 */
public class AnimationComponent implements Component {

    public final ObjectMap<String, String> clipsPorNombreLogico;
    public String nombreLogicoActual = null;
    public String nombreLogicoAplicado = null;

    public AnimationComponent(ObjectMap<String, String> clipsPorNombreLogico) {
        this.clipsPorNombreLogico = clipsPorNombreLogico;
    }

    public boolean tieneAnimaciones() {
        return clipsPorNombreLogico != null && clipsPorNombreLogico.size > 0;
    }
}
