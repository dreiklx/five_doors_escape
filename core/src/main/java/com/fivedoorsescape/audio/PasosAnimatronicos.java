package com.fivedoorsescape.audio;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.fivedoorsescape.assets.AssetService;
import com.fivedoorsescape.content.EntityDefinition;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AnimationComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;

/**
 * Pasos de los animatronicos sincronizados con su animacion de caminar (CLAUDE.md §2.73).
 *
 * El apoyo de cada pie se detecta en la pose real de cada cuadro, no con un temporizador: se sigue la altura del
 * hueso del pie sobre el origen del personaje y la pisada suena cuando el pie, despues de haberse levantado (mas del
 * 60 % de su recorrido vertical), baja al 25 % inferior: el instante en que llega al suelo. Medido en los cuatro ciclos
 * de caminar (1 s; 1.07 s el de Chica) y comparado con los cuadros renderizados: un unico apoyo por pie y por ciclo,
 * en el cuadro donde se ve el pie asentarse (Freddy: izquierdo a 0.63 s, derecho a 0.91 s; en los cuadros, 0.60-0.65
 * y 0.90-0.95). El punto mas adelantado del pie NO sirve en estos rigs: el pie llega adelante todavia en el aire (paso
 * alto) y apoya 0.35 s despues. Al acelerar la animacion (Freddy corre al 1.9x) los apoyos se acercan solos.
 *
 * Solo suena cuando el personaje camina de verdad: animacion "walk" activa y desplazamiento real (un animatronico
 * atascado contra una pared o aturdido no hace ruido). Volumen por distancia horizontal y paneo segun la camara, con
 * una ganancia suavizada para que un corte de camara (la cinematica del auto) no produzca un salto de volumen.
 * Cada pisada elige una variacion distinta de la anterior, con tono y volumen levemente aleatorios.
 */
public class PasosAnimatronicos {

    /** Fracciones del recorrido vertical del pie: por debajo de BAJADA esta apoyado, por encima de SUBIDA levantado. */
    private static final float BAJADA = 0.25f, SUBIDA = 0.6f;
    /** Velocidad horizontal minima (m/s) para considerar que camina. */
    private static final float VELOCIDAD_MINIMA = 0.25f;
    /** Recorrido vertical minimo del pie (m) para que cuente como paso (el pie que menos se levanta: 0.087 m, Bonnie). */
    private static final float AMPLITUD_MINIMA = 0.05f;
    /** Nunca dos pisadas del mismo personaje mas juntas que esto (s); Freddy corriendo: 0.145 s entre pies. */
    private static final float SEPARACION_MINIMA = 0.1f;
    /** Constante de tiempo de la ganancia por distancia (s). */
    private static final float TAU_GANANCIA = 0.3f;
    private static final int MAX_ACTIVOS = 8;

    private static final class Pie {
        final Node nodo;
        /** Extremos de altura: se conservan entre caminatas (el ciclo es siempre el mismo), con un olvido muy lento. */
        float min = Float.NaN, max = Float.NaN;
        boolean levantado = false;
        boolean primeraMuestra = true;

        Pie(Node nodo) {
            this.nodo = nodo;
        }

        /** Al dejar de caminar: la proxima caminata decide desde su primera muestra si el pie esta en el aire. */
        void reiniciar() {
            primeraMuestra = true;
        }
    }

    private static final class Seguidor {
        Entity entidad;
        TransformComponent transform;
        ModelInstance instancia;
        Pie izquierdo, derecho;
        ObjectMap<String, Perfil> perfiles = new ObjectMap<>();
        Perfil perfil;
        int ultimaVariante = -1;
        float ganancia = -1f;
        final Vector3 posicionAnterior = new Vector3();
        boolean conPosicionAnterior = false;
        float velocidad = 0f;
        float desdeUltima = 99f;
        int pisadas = 0, audibles = 0;
    }

    private static final class Perfil {
        EntityDefinition.PerfilPasos def;
        Sound[] sonidos;
    }

    private final Array<Seguidor> seguidores = new Array<>();
    private final Sound[] activosSonido = new Sound[MAX_ACTIVOS];
    private final long[] activosId = new long[MAX_ACTIVOS];
    private int siguienteActivo = 0;
    private final Vector3 tmp = new Vector3();
    private final Vector3 derecha = new Vector3();

    /** Rutas de todos los sonidos de pasos de una entidad (para encolarlos en BootScreen). */
    public static Array<String> sonidosDe(EntityDefinition def) {
        Array<String> rutas = new Array<>();
        if (def != null && def.pasos != null) {
            for (EntityDefinition.PerfilPasos p : def.pasos.values()) {
                for (String s : p.sonidos) {
                    if (!rutas.contains(s, false)) {
                        rutas.add(s);
                    }
                }
            }
        }
        return rutas;
    }

    /**
     * Agrega una entidad con su perfil inicial ("interior"). Sin huesos de pies o sin perfiles no hace nada; registrar
     * dos veces la misma entidad tampoco (sin pasos duplicados).
     */
    public void registrar(Entity entidad, EntityDefinition def, AssetService assets) {
        if (entidad == null || def == null || def.pasos == null || def.pasos.size == 0 || buscar(entidad) != null
                || !Mappers.model.has(entidad)) {
            return;
        }
        ModelInstance instancia = Mappers.model.get(entidad).scene.modelInstance;
        String pi = def.huesos.get("pieIzquierdo"), pd = def.huesos.get("pieDerecho");
        Node ni = pi != null ? instancia.getNode(pi, true) : null;
        Node nd = pd != null ? instancia.getNode(pd, true) : null;
        if (ni == null || nd == null) {
            com.badlogic.gdx.Gdx.app.error("PasosAnimatronicos", "sin huesos de pies para " + def.id + ": sin pasos");
            return;
        }
        Seguidor s = new Seguidor();
        s.entidad = entidad;
        s.transform = Mappers.transform.get(entidad);
        s.instancia = instancia;
        s.izquierdo = new Pie(ni);
        s.derecho = new Pie(nd);
        for (ObjectMap.Entry<String, EntityDefinition.PerfilPasos> e : def.pasos) {
            Perfil p = new Perfil();
            p.def = e.value;
            p.sonidos = new Sound[e.value.sonidos.length];
            for (int i = 0; i < p.sonidos.length; i++) {
                p.sonidos[i] = assets.getSound(e.value.sonidos[i]);
            }
            s.perfiles.put(e.key, p);
        }
        s.perfil = s.perfiles.containsKey("interior") ? s.perfiles.get("interior") : s.perfiles.values().next();
        calibrar(s, def, pi, pd);
        seguidores.add(s);
    }

    /**
     * Recorrido vertical de cada pie en el ciclo "walk", medido una vez al cargar sobre una copia del modelo (no toca
     * la instancia que se dibuja): asi la primera caminata ya detecta bien sus apoyos, sin un ciclo de aprendizaje.
     */
    private void calibrar(Seguidor s, EntityDefinition def, String pi, String pd) {
        String clip = def.animaciones.get("walk");
        if (clip == null) {
            return;
        }
        // una Scene nueva (no un ModelInstance suelto: la Scene marca los huesos animados, sin eso la pose no se aplica)
        net.mgsx.gltf.scene3d.scene.Scene escena = new net.mgsx.gltf.scene3d.scene.Scene(s.instancia.model);
        ModelInstance copia = escena.modelInstance;
        com.fivedoorsescape.ecs.components.ModelComponent mc = Mappers.model.get(s.entidad);
        copia.transform.idt().rotate(Vector3.X, mc.correctionRotationXDegrees).scale(mc.scale, mc.scale, mc.scale);
        Node ni = copia.getNode(pi, true), nd = copia.getNode(pd, true);
        com.badlogic.gdx.graphics.g3d.utils.AnimationController ac = escena.animationController;
        com.badlogic.gdx.graphics.g3d.model.Animation anim = copia.getAnimation(clip);
        if (anim == null || ni == null || nd == null) {
            return;
        }
        ac.setAnimation(clip, -1);
        int muestras = 64;
        float paso = anim.duration / muestras;
        // la primera muestra es tras un paso: antes del primer update la copia sigue en la pose de reposo
        for (int i = 0; i < muestras; i++) {
            ac.update(paso);
            muestrear(s.izquierdo, ni, copia);
            muestrear(s.derecho, nd, copia);
        }
    }

    private void muestrear(Pie pie, Node nodo, ModelInstance copia) {
        nodo.globalTransform.getTranslation(tmp);
        float y = tmp.mul(copia.transform).y;
        pie.min = Float.isNaN(pie.min) ? y : Math.min(pie.min, y);
        pie.max = Float.isNaN(pie.max) ? y : Math.max(pie.max, y);
    }

    /** Cambia el juego de sonidos de una entidad (p. ej. "carrera" cuando Freddy sale corriendo detras del auto). */
    public void usarPerfil(Entity entidad, String nombre) {
        Seguidor s = buscar(entidad);
        if (s != null && s.perfiles.containsKey(nombre)) {
            s.perfil = s.perfiles.get(nombre);
            s.ultimaVariante = -1;
        }
    }

    /**
     * Un cuadro: llamar DESPUES de sceneManager.update (la pose de los huesos ya es la de este cuadro). permitido=false
     * (pausa, jumpscare, cinematica, mundo congelado) no reproduce nada y descarta lo pendiente.
     */
    public void actualizar(float dt, Camera camara, boolean permitido) {
        for (Seguidor s : seguidores) {
            actualizar(s, dt, camara, permitido);
        }
    }

    private void actualizar(Seguidor s, float dt, Camera camara, boolean permitido) {
        Vector3 pos = s.transform.position;
        if (dt > 0f && s.conPosicionAnterior) {
            float v = Vector3.dst(pos.x, 0f, pos.z, s.posicionAnterior.x, 0f, s.posicionAnterior.z) / dt;
            s.velocidad += (v - s.velocidad) * Math.min(1f, dt / 0.15f);
        }
        s.posicionAnterior.set(pos);
        s.conPosicionAnterior = true;
        s.desdeUltima += dt;

        float objetivo = gananciaPorDistancia(s, camara);
        s.ganancia = s.ganancia < 0f ? objetivo : s.ganancia + (objetivo - s.ganancia) * Math.min(1f, dt / TAU_GANANCIA);

        AnimationComponent an = Mappers.animation.get(s.entidad);
        boolean caminando = permitido && dt > 0f && an != null && "walk".equals(an.nombreLogicoActual)
                && s.velocidad > VELOCIDAD_MINIMA;
        if (!caminando) {
            s.izquierdo.reiniciar();
            s.derecho.reiniciar();
            return;
        }
        seguirPie(s, s.izquierdo, dt, camara);
        seguirPie(s, s.derecho, dt, camara);
    }

    private void seguirPie(Seguidor s, Pie pie, float dt, Camera camara) {
        // altura del pie en el mundo sobre el origen del personaje (sus pies en reposo)
        pie.nodo.globalTransform.getTranslation(tmp);
        float altura = tmp.mul(s.instancia.transform).y - s.transform.position.y;

        if (Float.isNaN(pie.min)) {
            pie.min = pie.max = altura;
        } else {
            // olvido muy lento: sigue a la animacion si cambia de amplitud sin deformar los umbrales dentro de un ciclo
            pie.min = altura < pie.min ? altura : pie.min + (altura - pie.min) * Math.min(1f, dt * 0.02f);
            pie.max = altura > pie.max ? altura : pie.max + (altura - pie.max) * Math.min(1f, dt * 0.02f);
        }
        float amplitud = pie.max - pie.min;
        if (amplitud < AMPLITUD_MINIMA) {
            return;
        }
        float u = (altura - pie.min) / amplitud;
        if (pie.primeraMuestra) {
            pie.primeraMuestra = false;
            pie.levantado = u > 0.45f;
            return;
        }
        if (pie.levantado && u < BAJADA) {
            pie.levantado = false;
            if (s.desdeUltima >= SEPARACION_MINIMA) {
                pisar(s, camara);
            }
        } else if (!pie.levantado && u > SUBIDA) {
            pie.levantado = true;
        }
    }

    private float gananciaPorDistancia(Seguidor s, Camera camara) {
        EntityDefinition.PerfilPasos d = s.perfil.def;
        float dist = Vector3.dst(camara.position.x, 0f, camara.position.z, s.transform.position.x, 0f, s.transform.position.z);
        float u = MathUtils.clamp((d.distanciaMaxima - dist) / Math.max(0.01f, d.distanciaMaxima - d.distanciaMinima), 0f, 1f);
        return u * u;   // caida cuadratica: se apaga suave al alejarse, sin un corte al llegar a la distancia maxima
    }

    private void pisar(Seguidor s, Camera camara) {
        Perfil p = s.perfil;
        float volumen = p.def.volumen * s.ganancia * MathUtils.random(0.88f, 1f);
        s.desdeUltima = 0f;
        s.pisadas++;
        if (volumen < 0.01f || p.sonidos.length == 0) {
            return;
        }
        int variante = MathUtils.random(p.sonidos.length - 1);
        if (p.sonidos.length > 1 && variante == s.ultimaVariante) {
            variante = (variante + 1 + MathUtils.random(p.sonidos.length - 2)) % p.sonidos.length;
        }
        s.ultimaVariante = variante;
        float dx = s.transform.position.x - camara.position.x, dz = s.transform.position.z - camara.position.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float pan = 0f;
        if (dist > 0.01f) {
            derecha.set(camara.direction).crs(camara.up).nor();
            pan = MathUtils.clamp((dx * derecha.x + dz * derecha.z) / dist, -1f, 1f) * 0.85f;
        }
        Sound sonido = p.sonidos[variante];
        long id = sonido.play(volumen, MathUtils.random(p.def.tonoMin, p.def.tonoMax), pan);
        activosSonido[siguienteActivo] = sonido;
        activosId[siguienteActivo] = id;
        siguienteActivo = (siguienteActivo + 1) % MAX_ACTIVOS;
        s.audibles++;
    }

    /** Pausa las pisadas que todavia suenan (menu de pausa); reanudar() las continua. */
    public void pausar() {
        for (int i = 0; i < MAX_ACTIVOS; i++) {
            if (activosSonido[i] != null) {
                activosSonido[i].pause(activosId[i]);
            }
        }
    }

    public void reanudar() {
        for (int i = 0; i < MAX_ACTIVOS; i++) {
            if (activosSonido[i] != null) {
                activosSonido[i].resume(activosId[i]);
            }
        }
    }

    /** Corta todo lo que suena y lo pendiente (jumpscare, cinematica de derrota, salida de la pantalla). */
    public void detenerTodo() {
        for (int i = 0; i < MAX_ACTIVOS; i++) {
            if (activosSonido[i] != null) {
                activosSonido[i].stop(activosId[i]);
                activosSonido[i] = null;
            }
        }
        for (Seguidor s : seguidores) {
            s.izquierdo.reiniciar();
            s.derecho.reiniciar();
            s.conPosicionAnterior = false;
            s.velocidad = 0f;
        }
    }

    /** Apoyos detectados de una entidad desde que se registro, se oyeran o no (para las pruebas). */
    public int pisadasDe(Entity entidad) {
        Seguidor s = buscar(entidad);
        return s != null ? s.pisadas : 0;
    }

    /** Pisadas que llegaron a reproducirse (las lejanas, por debajo del 1 % de volumen, no suenan). */
    public int audiblesDe(Entity entidad) {
        Seguidor s = buscar(entidad);
        return s != null ? s.audibles : 0;
    }

    /** Ganancia por distancia (suavizada) actual de una entidad, para las pruebas. */
    public float gananciaDe(Entity entidad) {
        Seguidor s = buscar(entidad);
        return s != null ? s.ganancia : -1f;
    }

    public void dispose() {
        detenerTodo();
        seguidores.clear();
    }

    private Seguidor buscar(Entity entidad) {
        for (Seguidor s : seguidores) {
            if (s.entidad == entidad) {
                return s;
            }
        }
        return null;
    }
}
