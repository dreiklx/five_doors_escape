package com.fivedoorsescape.screens;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;

import com.fivedoorsescape.ai.states.IdleState;
import com.fivedoorsescape.assets.AssetService;
import com.fivedoorsescape.camera.FirstPersonCameraController;
import com.fivedoorsescape.content.ContentRegistry;
import com.fivedoorsescape.content.MapDefinition;
import com.fivedoorsescape.ecs.EntityFactory;
import com.fivedoorsescape.ecs.Mappers;
import com.fivedoorsescape.ecs.components.AIComponent;
import com.fivedoorsescape.ecs.components.AnimationComponent;
import com.fivedoorsescape.ecs.components.CollisionComponent;
import com.fivedoorsescape.ecs.components.ModelComponent;
import com.fivedoorsescape.ecs.components.TransformComponent;
import com.fivedoorsescape.ecs.systems.AISystem;
import com.fivedoorsescape.ecs.systems.AnimationSystem;
import com.fivedoorsescape.ecs.systems.RenderSyncSystem;
import com.fivedoorsescape.io.HandoffData;
import com.fivedoorsescape.util.GifDecoder;
import com.fivedoorsescape.util.Lang;
import com.fivedoorsescape.util.WavDuration;
import com.fivedoorsescape.world.CollisionWorld;
import com.fivedoorsescape.world.LevelLoader;

import net.mgsx.gltf.scene3d.attributes.FogAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;
import net.mgsx.gltf.scene3d.lights.DirectionalShadowLight;
import net.mgsx.gltf.scene3d.lights.SpotLightEx;
import net.mgsx.gltf.scene3d.scene.Scene;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import net.mgsx.gltf.scene3d.scene.SceneSkybox;
import net.mgsx.gltf.scene3d.shaders.PBRShaderProvider;
import net.mgsx.gltf.scene3d.utils.IBLBuilder;

/**
 * Vertical slice del MVP (Architecture.md #2.2, puntos 1-9): carga el mapa y a Freddy, camara FPS
 * con colision, Freddy persigue al jugador via IA y al atraparlo transiciona a
 * NightGameOverScreen. Bonnie/Chica/Foxy quedan fuera de este slice a proposito.
 */
public class GameplayScreen implements Screen {

    /**
     * Tope maximo de deltaTime usado para mover entidades en un solo frame. Sin esto, el primer
     * frame renderizado tras la carga de assets (que toma varios segundos reales) trae un
     * deltaTime enorme -- suficiente para que el jugador (o Freddy) atraviese de un salto el
     * grosor de una pared limite delgada sin que ninguna posicion intermedia registre colision
     * ("tunneling"). Confirmado en ejecucion real con un autopiloto de prueba antes de este fix.
     */
    private static final float MAX_FRAME_DELTA = 0.1f;

    /** Intensidad de la linterna cuando esta encendida (pedido explicito del usuario 2026-08-04:
     * moderada, no exagerada). Apagada, su intensidad real pasa a 0 -- no ilumina nada, ver
     * CTRL en el bucle JUGANDO mas abajo (pedido explicito del usuario 2026-08-05: poder
     * apagarla con CTRL, con un clic de sonido al cambiar de estado). */
    private static final float LINTERNA_INTENSIDAD_ENCENDIDA = 18f;

    // Panel de ayuda de controles (pedido explicito del usuario 2026-08-05, reemplaza al viejo
    // boton de salida de la esquina superior izquierda -- ese boton "realmente no servia porque
    // durante la partida no puede interactuarse con el" sin soltar antes el mouse-look). Se
    // muestra solo, no invasivo, durante los primeros segundos de JUGANDO y se desvanece solo --
    // no bloquea el juego ni requiere ninguna accion del jugador. Ver dibujarAyudaInicial().
    // Tambien reaparece tras cada respawn (pedido explicito del usuario 2026-08-10, ver
    // respawnJugador()) -- mismo timer, solo se reinicia a 0.
    private static final float AYUDA_ANCHO = 320f;
    // 118 -> 144: una linea mas para "E: Interactuar" (pedido explicito del usuario 2026-08-10).
    private static final float AYUDA_ALTO = 144f;
    private static final float AYUDA_MARGEN = 16f;
    // 9 -> 10: pedido explicito del usuario 2026-08-10 ("permanece visible aproximadamente 10
    // segundos"), incluye el propio desvanecimiento (AYUDA_DURACION_FUNDIDO) en ese total, igual
    // que ya funcionaba antes -- no una duracion adicional aparte.
    private static final float AYUDA_DURACION_VISIBLE = 10f;
    private static final float AYUDA_DURACION_FUNDIDO = 1.5f;

    // Menu de pausa (pedido explicito del usuario 2026-08-05): ESC ya no solo libera/recaptura
    // el cursor, ahora tambien abre este menu -- unica opcion real: salir al menu principal
    // (mismo mecanismo Gdx.app.exit() que Swing ya detecta via Process.waitFor(), ver
    // LanzadorEscape del lado Swing). Sin botones de configuracion/reanudar (pedido explicito
    // del usuario) -- ESC de nuevo ya cierra el menu, es la unica forma de reanudar.
    private static final float PAUSA_BOTON_ANCHO = 300f;
    private static final float PAUSA_BOTON_ALTO = 54f;

    /** Rutas de los 2 sonidos genericos de la secuencia de atrapada -- ver decision de diseno del
     * usuario 2026-08-03: reutilizar el jumpscare/estatica genericos ya existentes (ni Swing tiene
     * uno distinto por personaje) en vez de importar assets 2D del proyecto Swing. Publicas para
     * que BootScreen las encole junto con los modelos, sin nombres de archivo duplicados. */
    public static final String SONIDO_JUMPSCARE = "sounds/jumpscare.wav";
    public static final String SONIDO_ESTATICA = "sounds/static.wav";
    /** Risa de Freddy reutilizada de FiveDoorsAtFreddys (risa_freddy1.wav), reproducida una sola
     * vez al aparecer "RUN" en la intro -- pedido explicito del usuario. */
    public static final String SONIDO_RISA_FREDDY = "sounds/risa_freddy.wav";
    /** Latidos de tension (pedido explicito del usuario 2026-08-04): dos clips en loop, uno normal
     * y uno acelerado, elegidos segun la distancia real al animatronico mas cercano. Ver
     * actualizarLatidosYVineta. */
    public static final String SONIDO_LATIDO_NORMAL = "sounds/latido_normal.wav";
    public static final String SONIDO_LATIDO_RAPIDO = "sounds/latido_rapido.wav";
    /** Click de la linterna al encender/apagar (pedido explicito del usuario 2026-08-05, CTRL
     * como tecla de alternancia). Reutiliza botones_menu.wav de FiveDoorsAtFreddys (mismo patron
     * de reuso de assets ya establecido para risa_freddy/jumpscare/estatica) -- un clic corto
     * generico encaja igual de bien como sonido de interruptor de linterna. */
    public static final String SONIDO_LINTERNA_CLICK = "sounds/linterna_click.wav";
    /** Musica espacial de Freddy (pedido explicito del usuario 2026-08-06: "Freddy sea una
     * especie de caja musical... que esa musica salga UNICAMENTE desde Freddy... audio
     * espacial"). Ver actualizarMusicaEspacialFreddy(). */
    public static final String SONIDO_MUSICA_FREDDY = "sounds/musica_escape_freddy.wav";
    /** Reutilizado de FDAF assets (pedido explicito del usuario 2026-08-10): se reproduce cuando
     * el jugador interactua con la puerta de salida SIN tener la llave -- ver
     * actualizarInteraccion()/objetosInteractivos. */
    public static final String SONIDO_FORZANDO_PUERTA = "sounds/forzando_puerta.wav";
    /** Reutilizado de FDAF assets (pedido explicito del usuario, misma sesion 2026-08-10): se
     * reproduce EXACTAMENTE al recoger la llave de verdad (dentro de recogerLlave(), no al solo
     * mirarla) -- mismo patron ya establecido para el resto de sonidos de esta pantalla. */
    public static final String SONIDO_AGARRANDO_OBJETO = "sounds/agarrando_objeto.wav";

    /** Narracion de la cinematica inicial del modo Escape, en los 2 idiomas soportados (pedido
     * explicito del usuario 2026-08-05, mismos audios "Escape ES/EN" que el proyecto Swing usa
     * para la llamada de la Noche 5). Solo se encola/carga el archivo del idioma real de
     * handoff.idioma (ver rutaAudioEscape) -- nunca ambos, para no cargar audio que no se va a
     * usar en esta sesion. */
    public static final String SONIDO_ESCAPE_ES = "sounds/escape_es.wav";
    public static final String SONIDO_ESCAPE_EN = "sounds/escape_en.wav";

    /** Unico punto de resolucion idioma->archivo para la narracion de Escape -- BootScreen (al
     * encolar) y GameplayScreen.show() (al reproducir) llaman a este mismo metodo, para que la
     * logica de que archivo corresponde a que idioma exista en un solo lugar. */
    public static String rutaAudioEscape(HandoffData.Idioma idioma) {
        return idioma == HandoffData.Idioma.INGLES ? SONIDO_ESCAPE_EN : SONIDO_ESCAPE_ES;
    }

    /** GIF de estatica real reutilizado de FiveDoorsAtFreddys (efecto de transicion generico, no
     * un recurso exclusivo de un personaje -- aprobado explicitamente por el usuario). Se
     * decodifica con GifDecoder (javax.imageio, sin dependencias nuevas), no con una libreria de
     * GIF animado que libGDX no trae de forma nativa. */
    private static final String GIF_ESTATICA = "gifs/Static.gif";

    /** Duracion de cada tramo de la secuencia de atrapada y de la intro (mecanica de 2 vidas). */
    private static final float DURACION_JUMPSCARE = 1f;
    private static final float DURACION_ESTATICA = 5f;
    private static final float DURACION_INTRO_CORAZONES = 2.5f;
    private static final float DURACION_INTRO_RUN = 1.5f;
    private static final float DURACION_CORAZONES_RESPAWN = 2.5f;

    /** Valor de respaldo si por algun motivo no se puede leer la duracion real del audio de la
     * cinematica (ver WavDuration.leerSegundos) -- mismo patron de fallback que usa el lado
     * Swing (Sonido.getDuracionMs() &lt;= 0). Nunca deberia usarse en la practica. */
    private static final float DURACION_CINEMATICA_RESPALDO = 18f;

    /** Duracion de la cinematica inicial (pedido explicito del usuario 2026-08-04): recorrido de
     * camara lento mostrando a los 4 animatronicos, todos completamente inmoviles (durante este
     * estado nunca se llama engine.update(), asi que ni la IA ni las animaciones avanzan -- ver
     * actualizarCinematicaInicial). Pedido explicito del usuario 2026-08-05: la cinematica ahora
     * tiene narracion real (Escape ES/EN) y debe durar EXACTAMENTE lo mismo que ese audio -- por
     * eso ya no es una constante fija, se calcula en show() leyendo la duracion real del WAV
     * seleccionado (WavDuration.leerSegundos), para que se reajuste sola si el audio cambia en
     * el futuro en vez de quedar desincronizada con un numero hardcodeado. */
    private float duracionCinematicaInicial = DURACION_CINEMATICA_RESPALDO;

    /** Ambiente real de juego (oscuro y dramatico, ver setAmbientLight en show()) vs. ambiente
     * usado solo durante la cinematica inicial. Con el ambiente oscuro de JUGANDO, los 4
     * animatronicos quedaban casi invisibles durante la cinematica -- la linterna (cono angosto,
     * alcance moderado) no llega a los 4 cuando la camara se mueve por el pasillo central en vez
     * de pegarse a cada personaje. Una cinematica de "inspeccion" mas clara narrativamente tiene
     * sentido ademas (antes de que ella escena real, mas oscura, comience con el juego).
     */
    private static final float AMBIENTE_JUGANDO = 0.12f;
    private static final float AMBIENTE_CINEMATICA = 0.55f;

    /** Igual razon que AMBIENTE_JUGANDO/AMBIENTE_CINEMATICA: la luz direccional tambien se
     * intensifica durante la cinematica inicial para que los 4 animatronicos se vean con claridad
     * mientras la camara los recorre, y vuelve a su valor tenue/dramatico apenas empieza el juego. */
    private static final float INTENSIDAD_DIRECCIONAL_JUGANDO = 1.1f;
    private static final float INTENSIDAD_DIRECCIONAL_CINEMATICA = 3f;

    /** Altura (sobre la posicion base, que es la de los pies) y distancia de la camara de
     * jumpscare -- acercada al maximo sobre el rostro sin que el near plane (0.05) recorte el
     * modelo, con temblor mas agresivo que la primera version. */
    private static final float JUMPSCARE_ALTURA_FOCO = 1.5f;
    private static final float JUMPSCARE_DISTANCIA_BASE = 0.75f;

    private enum EstadoPartida { CINEMATICA_INICIAL, INTRO_CORAZONES, INTRO_RUN, JUGANDO, JUMPSCARE, ESTATICA, CORAZONES_RESPAWN }

    private boolean pausado = false;
    /** Tiempo real transcurrido en JUGANDO (no se resetea al pausar/despausar) -- controla el
     * desvanecimiento del panel de ayuda inicial, ver dibujarAyudaInicial(). */
    private float tiempoJugandoTranscurrido = 0f;

    private final Game game;
    private final ContentRegistry registry;
    private final AssetService assets;
    private final HandoffData handoff;

    private final Engine engine = new Engine();
    private final CollisionWorld collisionWorld = new CollisionWorld();

    private SceneManager sceneManager;
    private Scene mapScene;
    private PerspectiveCamera camera;
    private FirstPersonCameraController cameraController;

    private Entity playerEntity;
    private final Array<AIComponent> guardias = new Array<>();
    private final Array<Vector3> guardiaSpawns = new Array<>();
    /** Referencia directa a Freddy (no a "el guardia mas cercano") -- necesaria para la musica
     * espacial, que debe sonar desde Freddy especificamente, nunca desde Bonnie/Chica/Foxy. */
    private Entity freddyEntity;

    private Cubemap diffuseCubemap;
    private Cubemap environmentCubemap;
    private Cubemap specularCubemap;
    private Texture brdfLUT;
    private SceneSkybox skybox;
    private SpotLightEx linternaJugador;
    private DirectionalShadowLight luzDireccional;

    private SpriteBatch uiBatch;
    private BitmapFont uiFont;
    private ShapeRenderer uiShapes;

    private MapDefinition mapDef;

    // Condicion de victoria del Escape (expande el MVP original, aprobado explicitamente por el
    // usuario -- ver memoria de Claude "project-libgdx-office-spawn-exit-design"): distancia
    // horizontal (X/Z) del jugador al punto de salida definido en el mapa.
    private float exitX;
    private float exitZ;
    private float exitRadius;
    private boolean escapado = false;

    // Mecanica de 2 vidas (decision de diseno del usuario 2026-08-03): intro con corazones ->
    // "RUN" -> JUGANDO. Al atrapar: jumpscare cinematografico (camara + modelo 3D del culpable)
    // -> estatica (GIF real + sonido) -> corazones restantes -> reaparece, o Game Over definitivo
    // si no queda ninguno.
    // 3 vidas (antes 2, pedido explicito del usuario 2026-08-10) -- dibujarPantallaNegraConCorazones
    // ya calculaba el ancho total y el espaciado de corazones a partir de esta constante (nunca un
    // "2" hardcodeado en el dibujo), asi que subirla a 3 basta por si sola: se dibujan 3 corazones
    // reales (texturaCorazonLleno/texturaCorazonVacio, ya cargados desde assets/textures) sin
    // ningun otro cambio de logica.
    private static final int VIDAS_INICIALES = 3;
    private EstadoPartida estado = EstadoPartida.CINEMATICA_INICIAL;
    private float tiempoEnEstado = 0f;
    private int vidasRestantes = VIDAS_INICIALES;
    private AIComponent guardiaQueAtrapo;
    private Sound sonidoJumpscare;
    private Sound sonidoEstatica;
    private Sound sonidoRisaFreddy;
    private boolean risaFreddyReproducida = false;
    /** Risa OCASIONAL de Freddy durante el gameplay normal (pedido explicito del usuario
     * 2026-08-10): reutiliza el mismo Sound que ya suena una vez al mostrar RUN -- Sound.play()
     * crea una instancia de reproduccion independiente cada vez (a diferencia de los Clip de
     * javax.sound del lado Swing), asi que no hay ningun riesgo de "cerrarse" ni de interferir
     * con la musica espacial de Freddy o los latidos (Sound distintos, mezclados de forma nativa
     * por OpenAL, nunca se detienen entre si). Espera aleatoria entre RISA_OCASIONAL_ESPERA_MIN_S
     * y _MAX_S (45-100s reales) -- "poco frecuente, impredecible" sin depender de la posicion de
     * Freddy ni de ningun otro estado, solo del tiempo real transcurrido en JUGANDO (el unico
     * estado donde se actualiza este timer -- nunca durante cinematica/intro/jumpscare/estatica/
     * pausa/Game Over, todos esos casos ya retornan antes de llegar a actualizarRisaOcasionalFreddy()). */
    private static final float RISA_OCASIONAL_ESPERA_MIN_S = 45f;
    private static final float RISA_OCASIONAL_ESPERA_MAX_S = 100f;
    private float tiempoHastaRisaOcasionalFreddy =
            MathUtils.random(RISA_OCASIONAL_ESPERA_MIN_S, RISA_OCASIONAL_ESPERA_MAX_S);
    private Sound sonidoLinternaClick;
    /** Estado real de la linterna (pedido explicito del usuario 2026-08-05: poder apagarla con
     * CTRL). Empieza encendida -- comportamiento previo, ninguna regresion para quien no toque
     * la tecla nueva. Ver actualizarLinternaJugador(). */
    private boolean linternaEncendida = true;
    private long idSonidoJumpscareActivo = -1;
    private long idSonidoEstaticaActivo = -1;

    private Array<Texture> cuadrosEstatica;
    private Array<Float> duracionesCuadroEstatica;
    private int indiceCuadroEstatica = 0;
    private float tiempoEnCuadroEstatica = 0f;

    private Texture texturaCorazonLleno;
    private Texture texturaCorazonVacio;

    // Sistema de latidos de tension + vineta (pedido explicito del usuario 2026-08-04). Rangos
    // medidos contra el tamano real de la pizzeria (huella jugable ~18x17 unidades, ver
    // CLAUDE.md) y contra rangoDeteccion=8 de AIComponent, no valores arbitrarios: mas alla de
    // DISTANCIA_LEJOS no hay ningun indicio sensorial (silencio total, sin vineta); por debajo de
    // DISTANCIA_CERCA el latido pasa a la version acelerada. Un solo sistema global, calculado
    // siempre contra el animatronico MAS CERCANO (nunca varios sonidos superpuestos).
    private static final float LATIDO_DISTANCIA_LEJOS = 7.0f;
    private static final float LATIDO_DISTANCIA_CERCA = 3.0f;
    // Histeresis (unidades) alrededor de LATIDO_DISTANCIA_CERCA para decidir normal<->rapido, para
    // que el latido no parpadee entre ambos clips si el jugador se queda justo en el borde.
    private static final float LATIDO_HISTERESIS = 0.5f;
    // Incrementado de 0.6 a 0.75 (2026-08-04) y luego a 0.82 (2026-08-05, pedido explicito del
    // usuario: "un poco mas oscura", sin cambiar el comportamiento/logica existente -- ver
    // actualizarLatidosYVineta).
    private static final float VINETA_ALPHA_MAXIMA = 0.82f;
    private static final float LATIDO_VOLUMEN_MINIMO = 0.25f;

    private enum EstadoLatido { SILENCIO, NORMAL, RAPIDO }

    private Sound sonidoLatidoNormal;
    private Sound sonidoLatidoRapido;
    private EstadoLatido estadoLatidoActual = EstadoLatido.SILENCIO;
    private long idSonidoLatidoActivo = -1;
    private Texture texturaVineta;
    private Sound sonidoEscape;

    // Musica espacial de Freddy -- "caja musical" (pedido explicito del usuario 2026-08-06):
    // suena UNICAMENTE desde la posicion real de Freddy, nunca como musica global. libGDX no
    // trae un motor de audio 3D completo (sin doppler/oclusion), pero Sound SI soporta volumen y
    // pan (izquierda/derecha) por instancia en reproduccion via setVolume(id,...)/setPan(id,...)
    // -- el mismo mecanismo ya usado por el sistema de latidos (Sound.loop(vol) + setVolume(id,
    // vol) cada frame), aqui extendido con pan real para que ademas de "mas cerca=mas fuerte" se
    // sienta la DIRECCION real de Freddy respecto a hacia donde mira la camara. Es la forma
    // idiomatica de audio posicional simple en libGDX sin agregar ninguna libreria nueva.
    private static final float MUSICA_FREDDY_DISTANCIA_MAXIMA = 14f;
    private static final float MUSICA_FREDDY_DISTANCIA_MINIMA = 1.5f;
    private Sound sonidoMusicaFreddy;
    private long idSonidoMusicaFreddy = -1;
    private final Vector3 derechaCamaraTmp = new Vector3();

    // ------------------------------------------------------------------
    // Sistema de interaccion (pedido explicito del usuario 2026-08-10): crosshair discreto en el
    // centro de la pantalla + raycast real (Intersector.intersectRaySphere, no una heuristica de
    // angulo) desde la camara hacia una lista pequeña de objetos interactuables (llave, puerta de
    // salida) + tecla E. Deliberadamente NO es un sistema generico de "cualquier objeto del mapa
    // es interactuable" -- solo los objetos que de verdad necesitan interaccion se registran en
    // objetosInteractivos, tal como pidio el usuario ("no quiero que E interactue con cualquier
    // cosa indiscriminadamente").
    // ------------------------------------------------------------------

    /** Un objeto con el que el jugador puede interactuar (E) si el rayo de la camara lo golpea
     * (esfera de radio "radio" centrada en "posicion") y esta a "distanciaMaxima" o menos. */
    private static final class ObjetoInteractivo {
        final Vector3 posicion = new Vector3();
        float radio;
        float distanciaMaxima;
        boolean activo = true;
        Runnable accion;
    }

    private static final float LLAVE_RADIO_INTERACCION = 0.4f;
    private static final float LLAVE_DISTANCIA_MAXIMA = 2.2f;
    private static final float PUERTA_RADIO_INTERACCION = 0.6f;
    /** Margen sobre exitRadius (calibrado en sesiones anteriores contra la colision real de la
     * puerta -- ver LevelLoader.RADIO_EXCLUSION_PUERTA_SALIDA) para la distancia maxima de
     * interaccion: el jugador nunca puede acercarse mas de exitRadius al centro real de la
     * puerta (la bloquea su propia colision solida), asi que este margen solo da holgura extra,
     * nunca reduce el rango real necesario. */
    private static final float PUERTA_MARGEN_DISTANCIA_INTERACCION = 1.25f;
    // Verificado con una captura real -- 2.5px resultaba casi invisible en 1920x1080 (menos de 5
    // pixeles de diametro). 3.2/5.5 sigue siendo pequeño y discreto (muy por debajo de un
    // crosshair de FPS tipico) pero real perceptible sin dejar de ser sutil.
    private static final float CROSSHAIR_RADIO_NORMAL = 3.2f;
    private static final float CROSSHAIR_RADIO_ACTIVO = 5.5f;
    private static final float MENSAJE_LLAVE_DURACION = 2.5f;

    private final Array<ObjetoInteractivo> objetosInteractivos = new Array<>();
    private ObjetoInteractivo objetivoInteractivoActual;
    private final Ray rayInteraccion = new Ray(new Vector3(), new Vector3(0f, 0f, 1f));
    private final Vector3 tmpInterseccion = new Vector3();

    private Entity entidadLlave;
    private boolean tieneLlave = false;
    private Sound sonidoForzandoPuerta;
    private Sound sonidoAgarrandoObjeto;
    private float mensajeLlaveTiempoRestante = 0f;
    /** Clave real de Lang.get(...) para el mensaje temporal actualmente pendiente -- "key.need"
     * (interactuar con la puerta sin llave) o "key.lost" (perder la llave al morir, pedido
     * explicito del usuario 2026-08-10). Un solo timer/mecanismo de dibujo (dibujarMensajeLlave)
     * cubre ambos casos, ya que nunca pueden estar activos a la vez en la practica. */
    private String mensajeLlaveTextoClave = "key.need";

    private Texture texturaSangre;
    private final Array<Model> modelosDecalSangre = new Array<>();

    public GameplayScreen(Game game, ContentRegistry registry, AssetService assets, HandoffData handoff) {
        this.game = game;
        this.registry = registry;
        this.assets = assets;
        this.handoff = handoff;
    }

    @Override
    public void show() {
        Gdx.input.setCursorCatched(true);

        mapDef = registry.getMapDefinition("pizzeria");
        LevelLoader levelLoader = new LevelLoader(registry, assets);
        mapScene = levelLoader.loadMapScene("pizzeria");

        exitX = mapDef.exitX;
        exitZ = mapDef.exitZ;
        exitRadius = mapDef.exitRadius;

        levelLoader.buildStaticColliders(mapScene, collisionWorld, exitX, exitZ);
        Gdx.app.log("GameplayScreen", "Colisionadores estaticos generados: " + collisionWorld.getStaticColliderCount());
        collisionWorld.configurarEscalon(mapDef.stageMinX, mapDef.stageMaxX, mapDef.stageMinZ, mapDef.stageMaxZ,
                mapDef.stageHeight);

        camera = new PerspectiveCamera(67f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.05f;
        camera.far = 100f;
        cameraController = new FirstPersonCameraController(camera, mapDef.playerStartYawDegrees);

        EntityFactory factory = new EntityFactory(engine, registry, assets);

        Vector3 playerStart = new Vector3(mapDef.playerStartX, mapDef.playerStartY, mapDef.playerStartZ);
        playerEntity = factory.createPlayer(camera, playerStart, mapDef.playerStartYawDegrees);
        warnIfEmbedded("jugador", playerStart, Mappers.collision.get(playerEntity).halfExtents);

        engine.addSystem(new AISystem());
        engine.addSystem(new AnimationSystem());
        engine.addSystem(new RenderSyncSystem());

        int numBones = 1;

        // Freddy es el enemigo principal (decision de diseno del usuario 2026-08-03): persigue al
        // jugador de forma continua durante toda la partida (AIComponent.siempreEnPersecucion),
        // sin esperar a entrar en rangoDeteccion como los demas.
        Vector3 freddyStart = new Vector3(mapDef.freddyStartX, 0f, mapDef.freddyStartZ);
        numBones = Math.max(numBones, crearGuardia(factory, "freddy", freddyStart, "Freddy", true));

        // Bonnie/Chica/Foxy: guardias estaticos en un punto fijo (IdleState -- no patrullan, ver
        // decision de diseno del usuario). Reutilizan exactamente el mismo IdleState/ChaseState/
        // CaughtState generico que ya usaba Freddy -- ninguno es especifico de un personaje.
        Vector3 bonnieStart = new Vector3(mapDef.bonnieStartX, 0f, mapDef.bonnieStartZ);
        numBones = Math.max(numBones, crearGuardia(factory, "bonnie", bonnieStart, "Bonnie", false));

        Vector3 chicaStart = new Vector3(mapDef.chicaStartX, 0f, mapDef.chicaStartZ);
        numBones = Math.max(numBones, crearGuardia(factory, "chica", chicaStart, "Chica", false));

        Vector3 foxyStart = new Vector3(mapDef.foxyStartX, 0f, mapDef.foxyStartZ);
        numBones = Math.max(numBones, crearGuardia(factory, "foxy", foxyStart, "Foxy", false));

        // Llave en la cocina (pedido explicito del usuario 2026-08-10): entidad estatica sin IA,
        // creada ANTES del bucle de mas abajo que agrega cada entidad con ModelComponent a
        // sceneManager -- se suma automaticamente sin plumbing adicional, igual que los 4
        // animatronicos. Se retira de la escena (sceneManager.removeScene) al recogerla, ver
        // recogerLlave().
        Vector3 llavePos = new Vector3(mapDef.keyX, mapDef.keyY, mapDef.keyZ);
        entidadLlave = factory.createEntity("llave", llavePos, mapDef.keyYawDegrees);
        ObjetoInteractivo interactivoLlave = new ObjetoInteractivo();
        interactivoLlave.posicion.set(llavePos);
        interactivoLlave.radio = LLAVE_RADIO_INTERACCION;
        interactivoLlave.distanciaMaxima = LLAVE_DISTANCIA_MAXIMA;
        interactivoLlave.accion = this::recogerLlave;
        objetosInteractivos.add(interactivoLlave);

        // Puerta de salida (pedido explicito del usuario 2026-08-10): ya no gana automaticamente
        // por proximidad -- ahora requiere interactuar (E) con la puerta, exactamente igual que la
        // llave. Y=1.2 (altura de pecho/ojos aproximada) para que el rayo de la camara la golpee
        // con normalidad al mirar hacia adelante desde la distancia real de interaccion.
        ObjetoInteractivo interactivoPuerta = new ObjetoInteractivo();
        interactivoPuerta.posicion.set(exitX, 1.2f, exitZ);
        interactivoPuerta.radio = PUERTA_RADIO_INTERACCION;
        interactivoPuerta.distanciaMaxima = exitRadius + PUERTA_MARGEN_DISTANCIA_INTERACCION;
        interactivoPuerta.accion = this::interactuarConPuertaSalida;
        objetosInteractivos.add(interactivoPuerta);

        // Config manual (no el atajo createDefault(int)) unicamente para habilitar numSpotLights:
        // por defecto (DefaultShader.Config heredado) vale 0 -- la linterna del jugador (SpotLightEx
        // mas abajo) no se renderizaria en absoluto sin esto, sin ningun error visible (el shader
        // simplemente se compila sin soporte de luces spot).
        net.mgsx.gltf.scene3d.shaders.PBRShaderConfig pbrConfig = PBRShaderProvider.createDefaultConfig();
        pbrConfig.numBones = numBones;
        pbrConfig.numSpotLights = 1;
        sceneManager = new SceneManager(PBRShaderProvider.createDefault(pbrConfig), PBRShaderProvider.createDefaultDepth(numBones));
        sceneManager.addScene(mapScene);
        for (Entity guardia : engine.getEntities()) {
            if (Mappers.model.has(guardia)) {
                sceneManager.addScene(Mappers.model.get(guardia).scene);
            }
        }
        sceneManager.setCamera(camera);

        // Sombras direccionales reales (nativas de gdx-gltf, DirectionalShadowLight extiende
        // DirectionalLightEx -- SceneManager.render() ya las detecta y renderiza automaticamente,
        // sin cambios adicionales de shader). Viewport dimensionado sobre la huella jugable real
        // de pizzeria.json (X en [-10.7, 7.0], Z en [-7.0, 10.0]), no un valor adivinado.
        //
        // Ambientacion mas oscura y dramatica (pedido explicito del usuario 2026-08-04: "no bajar
        // solo el brillo global"). En vez de simplemente oscurecer todo por igual, se redujo la
        // intensidad Y se le dio un tono azulado frio de "luz de luna nocturna a traves de
        // ventanas" (color, no solo intensidad) -- deja sombras profundas con contraste real
        // (dramatico) en vez de una escena uniformemente gris. La iluminacion principal real pasa
        // a ser la linterna del jugador (SpotLightEx mas abajo), tal como pidio el usuario.
        luzDireccional = new DirectionalShadowLight(1024, 1024, 26f, 26f, 0f, 30f);
        luzDireccional.direction.set(1f, -3f, 1f).nor();
        luzDireccional.color.set(0.55f, 0.62f, 0.85f, 1f);
        luzDireccional.intensity = INTENSIDAD_DIRECCIONAL_JUGANDO;
        luzDireccional.setCenter(-1.5f, 1f, 1.5f);
        sceneManager.environment.add(luzDireccional);

        // createIndoor (no createOutdoor) -- pedido explicito del usuario 2026-08-05: seguir
        // mejorando el apartado visual investigando opciones razonables de gdx-gltf. createOutdoor
        // genera un gradiente de "cielo diurno" brillante (colores casi blancos/celestes) que, aun
        // sin ser visible directamente (no hay skybox propio en un mapa techado), SI se usa para
        // las reflexiones/iluminacion ambiental especular de los materiales PBR (metal de los
        // animatronicos, superficies brillantes) -- entonces contradice la ambientacion nocturna
        // oscura ya establecida, aportando reflejos irrealmente brillantes. createIndoor genera un
        // ambiente calido y tenue mucho mas coherente con la escena (pizzeria cerrada, de noche,
        // iluminada solo por luz de luna fria + la linterna del jugador).
        IBLBuilder iblBuilder = IBLBuilder.createIndoor(luzDireccional);
        environmentCubemap = iblBuilder.buildEnvMap(1024);
        diffuseCubemap = iblBuilder.buildIrradianceMap(256);
        specularCubemap = iblBuilder.buildRadianceMap(10);
        iblBuilder.dispose();

        brdfLUT = new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));
        // Bajado de 0.35 a 0.12: con la linterna como fuente principal de luz, un ambiente tan
        // alto como antes aplanaba su efecto (el mapa ya se veia casi igual de iluminado con o
        // sin ella). Este valor deja el escenario apenas visible por si solo (nunca negro total)
        // y hace que la linterna real haga una diferencia dramatica al apuntar hacia algo. Se usa
        // solo durante JUGANDO -- ver AMBIENTE_CINEMATICA para la cinematica inicial.
        sceneManager.setAmbientLight(AMBIENTE_JUGANDO);
        sceneManager.environment.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT));
        sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap));
        sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap));
        // Niebla nativa de gdx-gltf. Ajustada 2026-08-05 (pedido explicito del usuario: "casi no
        // se percibe... quiero que contribuya un poco mas a la atmosfera, sin exagerar"). Dos
        // cambios, no uno solo -- el primer intento (solo bajar near/far) seguia sin notarse en
        // capturas reales, confirmando que el color era el limitante real, no la distancia:
        // (1) near baja de 9 a 5 -- con near=9 la niebla practicamente nunca se notaba, porque la
        // mayoria de los pasillos/habitaciones de este mapa (~18x17 de huella jugable) tienen
        // sightlines mas cortas que eso; far tambien baja de 22 a 17 para alcanzar opacidad
        // completa a una distancia mas realista dentro del mapa. (2) el color hacia el que funde
        // (ColorAttribute.Fog) se aclara considerablemente, de (0.02,0.025,0.04) a
        // (0.14,0.17,0.24) -- con el ambiente ya tan oscuro (AMBIENTE_JUGANDO=0.12), un color de
        // niebla casi negro se fundia de forma invisible en la propia oscuridad de la escena sin
        // importar la distancia (confirmado con una primera prueba intermedia, (0.05,0.06,0.09),
        // que TAMPOCO se notaba en una captura real); este tono (siempre dentro del mismo tinte
        // azulado frio de la luz direccional) sí se percibe como una neblina real sobre las
        // paredes lejanas sin taparlas ni afectar nada dentro del alcance normal de la linterna.
        sceneManager.environment.set(FogAttribute.createFog(5f, 17f, 2f));
        sceneManager.environment.set(ColorAttribute.createFog(new Color(0.14f, 0.17f, 0.24f, 1f)));

        // Linterna del jugador (pedido explicito del usuario 2026-08-04): SpotLightEx nativo de
        // gdx-gltf (no un PointLight omnidireccional -- un cono real dirigido se siente mucho mas
        // como una linterna real, ademas de gastar luz solo hacia donde el jugador mira en vez de
        // en todas direcciones). Posicion/direccion se actualizan cada frame en
        // actualizarLinternaJugador() para seguir a la camara exactamente. Intensidad y alcance
        // moderados (no "exagerada", pedido explicito) -- ver esa nota para los valores medidos.
        linternaJugador = new SpotLightEx();
        linternaJugador.setColor(1f, 0.96f, 0.85f, 1f);
        linternaJugador.intensity = LINTERNA_INTENSIDAD_ENCENDIDA;
        linternaJugador.setConeDeg(28f, 14f);
        linternaJugador.range = 11f;
        sceneManager.environment.add(linternaJugador);

        skybox = new SceneSkybox(environmentCubemap);
        sceneManager.setSkyBox(skybox);

        crearDecalesSangre();

        uiBatch = new SpriteBatch();
        uiFont = new BitmapFont();
        uiFont.getData().setScale(1.3f);
        uiFont.setColor(Color.WHITE);
        uiShapes = new ShapeRenderer();

        texturaCorazonLleno = new Texture(Gdx.files.internal("textures/corazon_relleno.png"));
        texturaCorazonVacio = new Texture(Gdx.files.internal("textures/corazon_vacio.png"));

        sonidoJumpscare = assets.getSound(SONIDO_JUMPSCARE);
        sonidoEstatica = assets.getSound(SONIDO_ESTATICA);
        sonidoRisaFreddy = assets.getSound(SONIDO_RISA_FREDDY);
        sonidoLatidoNormal = assets.getSound(SONIDO_LATIDO_NORMAL);
        sonidoLatidoRapido = assets.getSound(SONIDO_LATIDO_RAPIDO);
        sonidoLinternaClick = assets.getSound(SONIDO_LINTERNA_CLICK);
        sonidoMusicaFreddy = assets.getSound(SONIDO_MUSICA_FREDDY);
        sonidoForzandoPuerta = assets.getSound(SONIDO_FORZANDO_PUERTA);
        sonidoAgarrandoObjeto = assets.getSound(SONIDO_AGARRANDO_OBJETO);
        // Arranca en loop UNA sola vez, en silencio -- el volumen/pan real se actualiza cada
        // frame en actualizarMusicaEspacialFreddy() segun la distancia/direccion real a Freddy.
        // Nunca se reinicia/vuelve a lanzar -- un unico Sound.loop() para toda la partida, como
        // corresponde a una "caja musical" que Freddy lleva encima todo el tiempo.
        idSonidoMusicaFreddy = sonidoMusicaFreddy.loop(0f);
        texturaVineta = crearTexturaVineta();

        // GIF real de estatica de FiveDoorsAtFreddys (efecto de transicion generico, aprobado
        // explicitamente por el usuario) decodificado una sola vez a Texture por cuadro -- se
        // recorre en bucle durante la ventana de DURACION_ESTATICA, no una sola pasada (el GIF
        // original no fue pensado para durar exactamente esos segundos).
        Array<GifDecoder.Cuadro> cuadros = GifDecoder.decodificar(Gdx.files.internal(GIF_ESTATICA));
        cuadrosEstatica = new Array<>();
        duracionesCuadroEstatica = new Array<>();
        for (GifDecoder.Cuadro cuadro : cuadros) {
            Texture textura = new Texture(cuadro.pixmap);
            cuadro.pixmap.dispose();
            cuadrosEstatica.add(textura);
            duracionesCuadroEstatica.add(cuadro.duracionSegundos);
        }
        Gdx.app.log("GameplayScreen", "GIF de estatica decodificado: " + cuadrosEstatica.size + " cuadros");

        // Narracion de la cinematica de Escape (pedido explicito del usuario 2026-08-05): se
        // carga el audio del idioma real de esta sesion (ver rutaAudioEscape) y se mide su
        // duracion REAL leyendo el propio archivo WAV (WavDuration) -- la cinematica completa
        // (construirRutaCinematicaInicial, mas abajo) usa ese valor para que dure exactamente lo
        // mismo que el audio, sin ningun numero de segundos hardcodeado.
        String rutaEscape = rutaAudioEscape(handoff.idioma);
        sonidoEscape = assets.getSound(rutaEscape);
        duracionCinematicaInicial = WavDuration.leerSegundos(Gdx.files.internal(rutaEscape), DURACION_CINEMATICA_RESPALDO);

        construirRutaCinematicaInicial();
        posicionarModelosParaCinematica();
    }

    /** Durante CINEMATICA_INICIAL, engine.update() nunca se llama (asi se congelan los 4
     * animatronicos -- ver comentario grande mas abajo sobre por que eso es necesario). Pero
     * RenderSyncSystem (que vuelca TransformComponent al ModelInstance.transform de cada
     * personaje) y el bind inicial de la animacion idle de AnimationSystem SOLO se ejecutan
     * dentro de ese mismo engine.update() -- sin al menos una pasada, cada personaje queda
     * renderizando en la matriz identidad (esencialmente el origen del mundo) en vez de su
     * posicion real de spawn, invisible para cualquier camara apuntada a sus coordenadas reales
     * (confirmado con capturas reales: ninguna toma de la cinematica mostraba a los animatronicos
     * pese a que las posiciones de camara/objetivo estaban verificadas como correctas).
     *
     * Solucion: sacar temporalmente AISystem del engine, hacer una sola pasada de
     * engine.update(0f) (con dt=0 no avanza ningun clip, solo aplica el estado/pose inicial), y
     * devolver AISystem -- asi se posicionan los modelos y se bindea su animacion idle sin que
     * IdleState.update() llegue a ejecutarse ni una vez (evita el caso ya documentado de
     * AIComponent.siempreEnPersecucion pasando a Freddy a ChaseState incluso con dt=0). */
    private void posicionarModelosParaCinematica() {
        AISystem aiSystemTemporal = engine.getSystem(AISystem.class);
        engine.removeSystem(aiSystemTemporal);
        engine.update(0f);
        engine.addSystem(aiSystemTemporal);
    }

    private Entity entidadDeGuardia(AIComponent ai) {
        for (Entity entidad : engine.getEntities()) {
            if (Mappers.ai.has(entidad) && Mappers.ai.get(entidad) == ai) {
                return entidad;
            }
        }
        throw new IllegalStateException("No se encontro la entidad para el AIComponent dado");
    }

    /**
     * Genera una vez la textura de vineta: gradiente radial, transparente en el centro y
     * oscureciendose hacia las esquinas. Generada en codigo (Pixmap) en vez de pedir un archivo
     * de imagen nuevo -- no depende de ningun asset externo.
     */
    private Texture crearTexturaVineta() {
        int tam = 512;
        Pixmap pixmap = new Pixmap(tam, tam, Pixmap.Format.RGBA8888);
        float centro = tam / 2f;
        float radioMax = (float) Math.sqrt(centro * centro + centro * centro);
        for (int y = 0; y < tam; y++) {
            for (int x = 0; x < tam; x++) {
                float dx = x - centro;
                float dy = y - centro;
                float distanciaNormalizada = (float) Math.sqrt(dx * dx + dy * dy) / radioMax;
                float alpha = MathUtils.clamp((distanciaNormalizada - 0.3f) / 0.7f, 0f, 1f);
                alpha = alpha * alpha;
                pixmap.setColor(0f, 0f, 0f, alpha);
                pixmap.drawPixel(x, y);
            }
        }
        Texture textura = new Texture(pixmap);
        pixmap.dispose();
        return textura;
    }

    /**
     * Mancha de sangre ambiental frente a la puerta de salida (pedido explicito del usuario
     * 2026-08-10): terror sutil, nunca una flecha literal -- una mancha principal irregular mas
     * dos gotas mas pequeñas escalonadas, cada vez mas cerca de la puerta, generadas con la misma
     * tecnica ya usada por crearTexturaVineta (Pixmap procedural, sin asset externo nuevo). Quads
     * pintados a nivel de piso (Y=0.015, evita z-fighting con el piso real) con blending real y
     * sin backface culling (mismo patron que el mapa).
     *
     * BUG REAL corregido en esta sesion ("la sangre no aparece"): las 3 posiciones originales
     * (offset puramente en +Z respecto a exitZ: +1.4/+0.85/+0.45, todas con X~exitX) estaban las
     * TRES embebidas dentro de un collider solido real -- confirmado con un barrido de colision en
     * vivo (CollisionWorld.overlapsStatic) que mostro una franja solida angosta (un pilar/elemento
     * de la puerta, no solo la hoja en si) que se extiende mucho mas alla del hueco de la puerta a
     * lo largo del eje +Z, justo sobre la columna X=exitX -- exactamente donde estaban las 3
     * manchas. La suposicion original ("el area abierta real queda al norte de exitZ", basada en
     * la topologia general del edificio documentada en sesiones anteriores) era cierta para el
     * edificio en general pero no se habia verificado especificamente sobre la columna X=exitX,
     * que resulto estar bloqueada. El area realmente libre y mas cercana a la puerta esta
     * desplazada tambien en +X (confirmado con el mismo barrido: a partir de X=exitX+0.3 aprox.,
     * la columna completa queda libre desde muy cerca de la puerta) -- el rastro ahora sigue esa
     * diagonal real en vez de una linea recta en Z. Verificado visualmente con capturas reales
     * (ver CLAUDE.md) tras la correccion.
     */
    private void crearDecalesSangre() {
        texturaSangre = crearTexturaSangre();
        ModelBuilder modelBuilder = new ModelBuilder();
        agregarDecalSangre(modelBuilder, exitX + 1.0f, exitZ + 0.6f, 1.3f);
        agregarDecalSangre(modelBuilder, exitX + 0.6f, exitZ + 0.3f, 0.55f);
        agregarDecalSangre(modelBuilder, exitX + 0.3f, exitZ + 0.1f, 0.4f);
    }

    private void agregarDecalSangre(ModelBuilder modelBuilder, float x, float z, float tamano) {
        // PBRTextureAttribute (gdx-gltf), NO el TextureAttribute estandar de libGDX -- el shader
        // de profundidad/sombras de gdx-gltf (PBRDepthShader.bindMaterial) castea directamente a
        // PBRTextureAttribute sin comprobar el tipo real; usar el estandar producia un
        // ClassCastException real en cuanto SceneManager intentaba renderizar la sombra de este
        // decal (confirmado en ejecucion real).
        Material material = new Material(
                PBRTextureAttribute.createBaseColorTexture(texturaSangre),
                new BlendingAttribute(true, 1f),
                IntAttribute.createCullFace(0));
        modelBuilder.begin();
        MeshPartBuilder parte = modelBuilder.part("sangre", GL20.GL_TRIANGLES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates,
                material);
        float mitad = tamano / 2f;
        parte.rect(
                -mitad, 0f, -mitad, mitad, 0f, -mitad, mitad, 0f, mitad, -mitad, 0f, mitad,
                0f, 1f, 0f);
        Model modelo = modelBuilder.end();
        modelosDecalSangre.add(modelo);

        ModelInstance instancia = new ModelInstance(modelo);
        instancia.transform.setToTranslation(x, 0.015f, z);
        sceneManager.addScene(new Scene(instancia));
    }

    /** Genera una textura de mancha de sangre irregular por procedimiento (varias manchas
     * circulares superpuestas, radios/alphas/desplazamientos aleatorios pero con semilla fija
     * para que el resultado sea siempre el mismo entre partidas) -- nunca un circulo perfecto ni
     * una forma geometrica limpia, para que se sienta organico y no una decoracion artificial. */
    private Texture crearTexturaSangre() {
        int tam = 256;
        Pixmap pixmap = new Pixmap(tam, tam, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        java.util.Random random = new java.util.Random(20260810L);
        float centro = tam / 2f;
        int manchas = 16;
        for (int i = 0; i < manchas; i++) {
            float angulo = random.nextFloat() * MathUtils.PI2;
            float distancia = random.nextFloat() * tam * 0.3f;
            float mx = centro + MathUtils.cos(angulo) * distancia;
            float my = centro + MathUtils.sin(angulo) * distancia;
            int radio = (int) (tam * (0.07f + random.nextFloat() * 0.2f));
            float alpha = 0.5f + random.nextFloat() * 0.4f;
            float rojo = (55 + random.nextInt(55)) / 255f;
            pixmap.setColor(rojo, 0f, 0f, alpha);
            pixmap.fillCircle((int) mx, (int) my, radio);
        }
        Texture textura = new Texture(pixmap);
        pixmap.dispose();
        return textura;
    }

    /**
     * Sistema unico de tension (pedido explicito del usuario 2026-08-04): calcula la distancia
     * real al animatronico MAS CERCANO (nunca varios sonidos superpuestos) y ajusta el latido
     * (silencio/normal/rapido, con histeresis para no parpadear en el borde) y la intensidad de
     * la vineta a partir del mismo valor de "intensidad" normalizado, para que ambos efectos se
     * sientan coherentes entre si.
     */
    private void actualizarLatidosYVineta(Vector3 posicionJugador) {
        float distanciaMinima = Float.MAX_VALUE;
        for (AIComponent ai : guardias) {
            Entity entidad = entidadDeGuardia(ai);
            Vector3 posGuardia = Mappers.transform.get(entidad).position;
            float dx = posicionJugador.x - posGuardia.x;
            float dz = posicionJugador.z - posGuardia.z;
            float distancia = (float) Math.sqrt(dx * dx + dz * dz);
            distanciaMinima = Math.min(distanciaMinima, distancia);
        }

        float intensidad = MathUtils.clamp(
                (LATIDO_DISTANCIA_LEJOS - distanciaMinima) / (LATIDO_DISTANCIA_LEJOS - LATIDO_DISTANCIA_CERCA),
                0f, 1f);

        EstadoLatido estadoDeseado;
        if (distanciaMinima >= LATIDO_DISTANCIA_LEJOS) {
            estadoDeseado = EstadoLatido.SILENCIO;
        } else if (estadoLatidoActual == EstadoLatido.RAPIDO) {
            // histeresis: una vez en rapido, hace falta alejarse un poco mas del limite para
            // volver a normal, evitando parpadeo si el jugador se queda justo en el borde.
            estadoDeseado = distanciaMinima > LATIDO_DISTANCIA_CERCA + LATIDO_HISTERESIS
                    ? EstadoLatido.NORMAL : EstadoLatido.RAPIDO;
        } else {
            estadoDeseado = distanciaMinima < LATIDO_DISTANCIA_CERCA
                    ? EstadoLatido.RAPIDO : EstadoLatido.NORMAL;
        }

        if (estadoDeseado != estadoLatidoActual) {
            if (idSonidoLatidoActivo != -1) {
                Sound sonidoAnterior = estadoLatidoActual == EstadoLatido.RAPIDO ? sonidoLatidoRapido : sonidoLatidoNormal;
                sonidoAnterior.stop(idSonidoLatidoActivo);
                idSonidoLatidoActivo = -1;
            }
            if (estadoDeseado == EstadoLatido.NORMAL) {
                idSonidoLatidoActivo = sonidoLatidoNormal.loop(LATIDO_VOLUMEN_MINIMO);
            } else if (estadoDeseado == EstadoLatido.RAPIDO) {
                idSonidoLatidoActivo = sonidoLatidoRapido.loop(LATIDO_VOLUMEN_MINIMO);
            }
            estadoLatidoActual = estadoDeseado;
        }

        if (idSonidoLatidoActivo != -1) {
            Sound sonidoActivo = estadoLatidoActual == EstadoLatido.RAPIDO ? sonidoLatidoRapido : sonidoLatidoNormal;
            float volumen = LATIDO_VOLUMEN_MINIMO + (1f - LATIDO_VOLUMEN_MINIMO) * intensidad;
            sonidoActivo.setVolume(idSonidoLatidoActivo, volumen);
        }

        if (intensidad > 0f) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            uiBatch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            uiBatch.begin();
            uiBatch.setColor(1f, 1f, 1f, intensidad * VINETA_ALPHA_MAXIMA);
            uiBatch.draw(texturaVineta, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            uiBatch.setColor(Color.WHITE);
            uiBatch.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }
    }

    /**
     * Pausa/reanuda la reproduccion REAL (no solo el volumen ni el estado visual) de los unicos
     * sonidos verdaderamente continuos de la partida -- la musica espacial de Freddy
     * (sonidoMusicaFreddy, en loop() desde show(), nunca se detiene durante toda la sesion) y el
     * latido activo en ese instante, si habia alguno sonando. Sound.pause(id)/resume(id) son los
     * metodos reales de libGDX para esto (respaldados por alSourcePause/alSourcePlay de OpenAL en
     * desktop) -- conservan la posicion de reproduccion real, no reinician desde el principio ni
     * afectan otras instancias de la misma Sound. El resto de los sonidos del juego (jumpscare,
     * estatica, risa de Freddy, narracion de la cinematica, click de linterna) nunca pueden estar
     * sonando mientras pausado==true: sus estados (JUMPSCARE, ESTATICA, CINEMATICA_INICIAL, etc.)
     * retornan en el switch de arriba antes de llegar siquiera al chequeo de ESC, así que no
     * necesitan tratamiento aquí -- pausar/reanudar solo estos dos evita tocar nada que no haga
     * falta.
     */
    private void pausarAudioContinuo() {
        if (idSonidoMusicaFreddy != -1) {
            sonidoMusicaFreddy.pause(idSonidoMusicaFreddy);
        }
        if (idSonidoLatidoActivo != -1) {
            Sound sonidoActivo = estadoLatidoActual == EstadoLatido.RAPIDO ? sonidoLatidoRapido : sonidoLatidoNormal;
            sonidoActivo.pause(idSonidoLatidoActivo);
        }
    }

    private void reanudarAudioContinuo() {
        if (idSonidoMusicaFreddy != -1) {
            sonidoMusicaFreddy.resume(idSonidoMusicaFreddy);
        }
        if (idSonidoLatidoActivo != -1) {
            Sound sonidoActivo = estadoLatidoActual == EstadoLatido.RAPIDO ? sonidoLatidoRapido : sonidoLatidoNormal;
            sonidoActivo.resume(idSonidoLatidoActivo);
        }
    }

    /**
     * "Caja musical" de Freddy (pedido explicito del usuario 2026-08-06): actualiza volumen y pan
     * de la unica instancia de sonidoMusicaFreddy (ya en loop desde show(), nunca se reinicia)
     * segun la posicion real de Freddy respecto a la camara -- mas cerca = mas fuerte, y el pan
     * refleja si Freddy esta a la izquierda o a la derecha de hacia donde mira el jugador ahora
     * mismo. Distancia horizontal (X/Z) solamente, mismo criterio ya usado por IdleState/
     * ChaseState para no penalizar la diferencia de altura pies-vs-camara.
     */
    private void actualizarMusicaEspacialFreddy() {
        if (freddyEntity == null || idSonidoMusicaFreddy == -1) {
            return;
        }
        Vector3 posFreddy = Mappers.transform.get(freddyEntity).position;
        float dx = posFreddy.x - camera.position.x;
        float dz = posFreddy.z - camera.position.z;
        float distancia = (float) Math.sqrt(dx * dx + dz * dz);

        float volumen = MathUtils.clamp(
                (MUSICA_FREDDY_DISTANCIA_MAXIMA - distancia)
                        / (MUSICA_FREDDY_DISTANCIA_MAXIMA - MUSICA_FREDDY_DISTANCIA_MINIMA),
                0f, 1f);

        // Pan real: proyecta la direccion hacia Freddy sobre el eje "derecha" de la camara --
        // mismo cross product (direction x up) que usa Matrix4.setToLookAt internamente, ya
        // verificado contra la convencion real del motor al corregir el strafe A/D (ver
        // FirstPersonCameraController). Positivo = Freddy a la derecha, negativo = a la
        // izquierda, 0 = justo al frente o detras.
        float pan = 0f;
        if (distancia > 0.01f) {
            derechaCamaraTmp.set(camera.direction).crs(camera.up).nor();
            pan = MathUtils.clamp((dx * derechaCamaraTmp.x + dz * derechaCamaraTmp.z) / distancia, -1f, 1f);
        }

        sonidoMusicaFreddy.setPan(idSonidoMusicaFreddy, pan, volumen);
    }

    /** Cuenta regresiva real hacia la proxima risa ocasional de Freddy -- ver comentario del
     * campo tiempoHastaRisaOcasionalFreddy para el diseno completo. Un simple play() de un Sound
     * ya cargado, nunca toca sonidoMusicaFreddy/latidos/jumpscare/estatica. */
    private void actualizarRisaOcasionalFreddy(float dt) {
        tiempoHastaRisaOcasionalFreddy -= dt;
        if (tiempoHastaRisaOcasionalFreddy <= 0f) {
            sonidoRisaFreddy.play();
            tiempoHastaRisaOcasionalFreddy = MathUtils.random(RISA_OCASIONAL_ESPERA_MIN_S, RISA_OCASIONAL_ESPERA_MAX_S);
        }
    }

    /**
     * Detiene el latido activo (si hay alguno). Necesario al entrar a JUMPSCARE/ESTATICA/
     * CORAZONES_RESPAWN: esos estados retornan temprano en render() y nunca vuelven a llamar
     * actualizarLatidosYVineta hasta que el jugador reaparece, asi que sin este corte explicito el
     * latido seguia sonando debajo del grito del jumpscare y la estatica -- viola la regla propia
     * de "un solo sonido, nunca superpuesto" del sistema de tension.
     */
    private void detenerLatidos() {
        if (idSonidoLatidoActivo != -1) {
            Sound sonidoActivo = estadoLatidoActual == EstadoLatido.RAPIDO ? sonidoLatidoRapido : sonidoLatidoNormal;
            sonidoActivo.stop(idSonidoLatidoActivo);
            idSonidoLatidoActivo = -1;
            estadoLatidoActual = EstadoLatido.SILENCIO;
        }
    }

    /**
     * Actualiza el objetivo interactivo actual (raycast real desde la camara, ver ObjetoInteractivo)
     * y dispara su accion si el jugador presiona E este frame. Solo un objetivo a la vez -- el mas
     * cercano de los que el rayo golpea dentro de su distanciaMaxima.
     */
    private void actualizarInteraccion() {
        objetivoInteractivoActual = null;
        float mejorDistancia = Float.MAX_VALUE;
        rayInteraccion.origin.set(camera.position);
        rayInteraccion.direction.set(camera.direction);

        for (ObjetoInteractivo objeto : objetosInteractivos) {
            if (!objeto.activo) {
                continue;
            }
            boolean golpea = Intersector.intersectRaySphere(rayInteraccion, objeto.posicion, objeto.radio, tmpInterseccion);
            if (!golpea) {
                continue;
            }
            float distancia = camera.position.dst(objeto.posicion);
            if (distancia <= objeto.distanciaMaxima && distancia < mejorDistancia) {
                mejorDistancia = distancia;
                objetivoInteractivoActual = objeto;
            }
        }

        if (objetivoInteractivoActual != null && Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            Runnable accion = objetivoInteractivoActual.accion;
            if (accion != null) {
                accion.run();
            }
        }
    }

    /** Recoge la llave: la retira de la escena (deja de dibujarse) y marca su interactivo como
     * inactivo -- la entidad Ashley en si se deja viva (invisible, no forma parte de sceneManager),
     * mas simple que desmontar toda su maquinaria ECS para un objeto que ya no necesita nada mas. */
    private void recogerLlave() {
        if (tieneLlave) {
            return;
        }
        tieneLlave = true;
        sonidoAgarrandoObjeto.play();
        for (ObjetoInteractivo objeto : objetosInteractivos) {
            if (objeto.posicion.epsilonEquals(mapDef.keyX, mapDef.keyY, mapDef.keyZ, 0.001f)) {
                objeto.activo = false;
            }
        }
        if (entidadLlave != null) {
            ModelComponent modelo = Mappers.model.get(entidadLlave);
            if (modelo != null) {
                sceneManager.removeScene(modelo.scene);
            }
        }
    }

    /** Interaccion con la puerta de salida (pedido explicito del usuario 2026-08-10): sin llave,
     * reproduce el sonido de forzar la puerta y muestra el mensaje temporal "Necesitas una llave"
     * -- el jugador sigue jugando con normalidad, nada mas cambia. Con llave, dispara exactamente
     * la misma secuencia de victoria que ya existia (EscapeVictoryScreen). */
    private void interactuarConPuertaSalida() {
        if (escapado) {
            return;
        }
        if (tieneLlave) {
            escapado = true;
            EscapeVictoryScreen victoria = new EscapeVictoryScreen(game, handoff);
            game.setScreen(victoria);
            dispose();
        } else {
            sonidoForzandoPuerta.play();
            mensajeLlaveTextoClave = "key.need";
            mensajeLlaveTiempoRestante = MENSAJE_LLAVE_DURACION;
        }
    }

    /** Pierde la llave si el jugador la tenia al ser atrapado (pedido explicito del usuario
     * 2026-08-10): revierte EXACTAMENTE lo que hizo recogerLlave() -- la llave 3D vuelve a
     * aparecer en su posicion original y su interactivo se reactiva, para que el jugador tenga
     * que recogerla de nuevo si quiere volver a intentar la puerta. Se llama desde
     * resolverFinDeAtrapada(), antes de la secuencia de corazones/respawn (o del Game Over final),
     * asi que el mensaje "Perdiste la llave" ya esta listo para mostrarse en cuanto el jugador
     * recupere el control. No hace nada si no tenia la llave -- el mensaje nunca debe aparecer si
     * murio sin haberla recogido. */
    private void perderLlave() {
        if (!tieneLlave) {
            return;
        }
        tieneLlave = false;
        for (ObjetoInteractivo objeto : objetosInteractivos) {
            if (objeto.posicion.epsilonEquals(mapDef.keyX, mapDef.keyY, mapDef.keyZ, 0.001f)) {
                objeto.activo = true;
            }
        }
        if (entidadLlave != null) {
            ModelComponent modelo = Mappers.model.get(entidadLlave);
            if (modelo != null) {
                sceneManager.addScene(modelo.scene);
            }
        }
        mensajeLlaveTextoClave = "key.lost";
        mensajeLlaveTiempoRestante = MENSAJE_LLAVE_DURACION;
    }

    /** Crosshair pequeño y discreto (pedido explicito del usuario 2026-08-10) -- un punto en el
     * centro de la pantalla, ligeramente mas grande/opaco cuando hay un objetivo interactuable
     * real delante (ver actualizarInteraccion), nunca un HUD invasivo.
     *
     * INVESTIGACION REAL de esta sesion ("el crosshair a veces desaparece"): se probo la
     * hipotesis de oclusion por el depth buffer real que deja sceneManager.render() (el
     * ShapeRenderer no toca GL_DEPTH_TEST) con una comparacion real A/B (mismo frame, con y sin
     * este metodo) en 7 puntos de vista distintos y diversos (pared cercana, area abierta, la
     * llave y la puerta en su estado "activo" real via actualizarInteraccion(), una mesa del
     * comedor de cerca, Pirate Cove, cerca de Freddy) -- en los 7 casos el pixel central SI
     * cambia de forma consistente con la formula real de blending (alpha 0.55/0.95 sobre el color
     * de fondo real), confirmando que dibujarCrosshair() se ejecuta y dibuja correctamente
     * SIEMPRE que se le llama (nunca ocluido, nunca una llamada perdida -- ya se llama de forma
     * incondicional en cada frame de JUGANDO). No existia ningun booleano/estado de "visible" que
     * arreglar -- la causa real es de CONTRASTE: un punto blanco de 3.2px al 55% de opacidad
     * puede genuinamente perderse contra fondos claros reales (paredes iluminadas por la linterna,
     * el piso, superficies del mapa con colores pastel) incluso estando dibujado correctamente,
     * sin ser un bug de logica. Corregido con un contorno oscuro real (mismo patron que cualquier
     * crosshair de FPS -- un circulo mas grande y oscuro detras, el circulo blanco encima) que
     * garantiza contraste sin importar el color de fondo, en vez de un setVilble(true) ciego que
     * no habria arreglado nada (nunca hubo un booleano de visibilidad apagandose).
     */
    private static final float CROSSHAIR_CONTORNO_EXTRA = 2.0f;

    private void dibujarCrosshair() {
        float cx = Gdx.graphics.getWidth() / 2f;
        float cy = Gdx.graphics.getHeight() / 2f;
        boolean activo = objetivoInteractivoActual != null;
        float radio = activo ? CROSSHAIR_RADIO_ACTIVO : CROSSHAIR_RADIO_NORMAL;

        uiShapes.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        uiShapes.begin(ShapeRenderer.ShapeType.Filled);
        // Contorno oscuro primero (mas grande) -- da contraste real contra fondos claros sin
        // dejar de ser discreto: mismo radio extra sin importar el estado activo/normal. Alpha
        // subida de 0.35/0.55 a 0.75/0.9 tras confirmar con una captura real que el contraste
        // inicial seguia siendo insuficiente contra un fondo claro real (pared bien iluminada).
        uiShapes.setColor(0f, 0f, 0f, activo ? 0.9f : 0.75f);
        uiShapes.circle(cx, cy, radio + CROSSHAIR_CONTORNO_EXTRA);
        // Punto blanco real encima. Alpha subida de 0.55/0.95 a 0.85/0.98 por el mismo motivo.
        uiShapes.setColor(1f, 1f, 1f, activo ? 0.98f : 0.85f);
        uiShapes.circle(cx, cy, radio);
        uiShapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Mensaje temporal (dos variantes posibles, ver mensajeLlaveTextoClave): "Necesitas una
     * llave"/"You need a key" (interactuar con la puerta sin llave) o "Perdiste la llave"/"You
     * lost the key" (morir con la llave, pedido explicito del usuario 2026-08-10) -- se desvanece
     * solo tras MENSAJE_LLAVE_DURACION segundos, el jugador nunca queda bloqueado ni pierde
     * control. */
    private void dibujarMensajeLlave(float dt) {
        if (mensajeLlaveTiempoRestante <= 0f) {
            return;
        }
        mensajeLlaveTiempoRestante = Math.max(0f, mensajeLlaveTiempoRestante - dt);

        String texto = Lang.get(handoff.idioma, mensajeLlaveTextoClave);
        GlyphLayout layout = new GlyphLayout(uiFont, texto);
        float x = Gdx.graphics.getWidth() / 2f - layout.width / 2f;
        float y = Gdx.graphics.getHeight() * 0.28f;

        uiBatch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        uiBatch.begin();
        uiFont.draw(uiBatch, layout, x, y);
        uiBatch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, MAX_FRAME_DELTA);

        switch (estado) {
            case CINEMATICA_INICIAL:
                actualizarCinematicaInicial(dt);
                return;
            case INTRO_CORAZONES:
                actualizarIntroCorazones(dt);
                return;
            case INTRO_RUN:
                actualizarIntroRun(dt);
                return;
            case JUMPSCARE:
                actualizarJumpscare(dt);
                return;
            case ESTATICA:
                actualizarEstatica(dt);
                return;
            case CORAZONES_RESPAWN:
                actualizarCorazonesRespawn(dt);
                return;
            default:
                break;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            pausado = !pausado;
            Gdx.input.setCursorCatched(!pausado);
            if (pausado) {
                pausarAudioContinuo();
            } else {
                reanudarAudioContinuo();
            }
        }

        if (pausado) {
            // Mismo patron ya usado por la cinematica inicial para "congelar" la escena sin
            // avanzar ningun clip: sceneManager.update(0f) mantiene camaras/luces sincronizadas,
            // sceneManager.render() sigue dibujando el ultimo frame real -- ni la IA ni el
            // jugador se mueven mientras el menu de pausa esta abierto.
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            sceneManager.update(0f);
            sceneManager.render();
            dibujarMenuPausa();
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.CONTROL_RIGHT)) {
            linternaEncendida = !linternaEncendida;
            sonidoLinternaClick.play();
        }

        cameraController.update();

        TransformComponent playerTransform = Mappers.transform.get(playerEntity);
        playerTransform.yawDegrees = cameraController.getYawDegrees();

        Vector3 desiredDelta = cameraController.computeWasdDelta(dt);

        CollisionComponent playerCollision = Mappers.collision.get(playerEntity);
        Vector3 resolved = collisionWorld.resolveMovement(playerTransform.position, desiredDelta, playerCollision.halfExtents, true);
        // Distancia REAL recorrida este frame (post-colision, no el input crudo) -- ver
        // FirstPersonCameraController.actualizarBob(): si el jugador queda deslizandose contra
        // una pared, el bob se ralentiza junto con el movimiento real en vez de seguir a
        // velocidad constante como si nada lo hubiera bloqueado.
        float distanciaMovidaEsteFrame = playerTransform.position.dst(resolved);
        playerTransform.position.x = resolved.x;
        playerTransform.position.z = resolved.z;

        // Escalon del stage (pedido explicito del usuario 2026-08-10): la altura de ojos sube/baja
        // suavemente sobre la base mapDef.playerStartY segun la zona XZ real -- ver
        // CollisionWorld.alturaEscalonEn/aplicarSuavizadoAltura. Nunca bloquea el movimiento en si
        // (ya resuelto arriba), solo ajusta la altura para que se sienta como subir un escalon real
        // en vez de atravesarlo en linea recta a la misma altura.
        float alturaEscalonObjetivo = mapDef.playerStartY
                + collisionWorld.alturaEscalonEn(playerTransform.position.x, playerTransform.position.z);
        playerTransform.position.y = CollisionWorld.aplicarSuavizadoAltura(playerTransform.position.y, alturaEscalonObjetivo, dt);

        cameraController.actualizarBob(dt, distanciaMovidaEsteFrame);
        cameraController.applyToCamera(playerTransform.position);
        linternaJugador.position.set(camera.position);
        linternaJugador.direction.set(camera.direction);
        linternaJugador.intensity = linternaEncendida ? LINTERNA_INTENSIDAD_ENCENDIDA : 0f;

        engine.update(dt);

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        sceneManager.update(dt);
        sceneManager.render();

        actualizarLatidosYVineta(playerTransform.position);
        actualizarMusicaEspacialFreddy();
        actualizarRisaOcasionalFreddy(dt);
        // Ya no hay disparo automatico por proximidad -- la puerta de salida ahora es un
        // ObjetoInteractivo mas (pedido explicito del usuario 2026-08-10), ver
        // interactuarConPuertaSalida(). actualizarInteraccion() tambien maneja la llave.
        actualizarInteraccion();
        // BUG REAL encontrado y corregido en esta sesion ("comportamiento extraño despues de
        // ganar con la llave"): interactuarConPuertaSalida() (llamada dentro de
        // actualizarInteraccion(), arriba) hace game.setScreen(victoria) + dispose() en el MISMO
        // frame en el que se presiono E sobre la puerta -- dispose() libera uiBatch/uiFont/
        // uiShapes entre otros recursos GL. Sin este guard, el resto de este mismo render()
        // seguia ejecutandose (dibujarCrosshair/dibujarMensajeLlave/dibujarAyudaInicial, todos
        // usan esos mismos objetos ya liberados) DESPUES de haberlos liberado -- dibujando sobre
        // recursos GL invalidos en el instante exacto de la victoria, antes de que el primer
        // frame de EscapeVictoryScreen llegara a mostrarse. Reproducido y confirmado real
        // reproduciendo el flujo completo (recoger llave -> interactuar con la puerta con
        // llave), no solo inferido leyendo el codigo -- ver CLAUDE.md.
        if (escapado) {
            return;
        }
        dibujarCrosshair();
        dibujarMensajeLlave(dt);

        dibujarAyudaInicial(dt);

        AIComponent guardiaAtrapante = null;
        for (AIComponent ai : guardias) {
            if (ai.jugadorAtrapado) {
                guardiaAtrapante = ai;
                break;
            }
        }
        if (guardiaAtrapante != null) {
            iniciarJumpscare(guardiaAtrapante);
        }
    }

    // ------------------------------------------------------------------
    // Cinematica inicial (pedido explicito del usuario 2026-08-04): recorrido de camara lento y
    // cinematografico mostrando a los 4 animatronicos completamente inmoviles antes de que
    // comience la partida normal. Nunca se llama engine.update() durante este estado (mismo
    // patron ya usado por INTRO_CORAZONES/INTRO_RUN) -- eso congela tanto la IA (Freddy no
    // transiciona a ChaseState pese a siempreEnPersecucion) como las animaciones, sin necesitar
    // ninguna logica especial de "pausa". sceneManager.update(0f) (delta=0, no dt) mantiene
    // camaras/luces sincronizadas para que la escena se vea correcta sin avanzar ningun clip.
    // ------------------------------------------------------------------

    /** Duracion de cada toma de la cinematica (5 tomas: Foxy, Bonnie, Chica, Freddy, retorno al
     * inicio del jugador), derivada de duracionCinematicaInicial (ver esa nota) -- ya no es una
     * constante fija, se recalcula en construirRutaCinematicaInicial() una vez conocida la
     * duracion real del audio. Duracion del breve fundido a negro en los cortes entre tomas. */
    private float duracionToma = DURACION_CINEMATICA_RESPALDO / 5f;
    private static final float DURACION_FUNDIDO_CORTE = 0.35f;

    private float[] tomaTiempoInicio;
    private float[] tomaTiempoFin;
    private Vector3[] tomaPosInicio;
    private Vector3[] tomaPosFin;
    private Vector3[] tomaObjInicio;
    private Vector3[] tomaObjFin;

    /** Construye la cinematica una sola vez (en show(), con mapDef ya cargado) como 5 TOMAS
     * independientes tipo "camara de seguridad" (una por animatronico + retorno), con CORTE DURO
     * (fundido a negro breve) entre cada una, en vez de un unico recorrido continuo.
     *
     * Se abandono el diseño original de un solo pasillo recorrido de punta a punta (linea Z=1,
     * la unica fila totalmente libre segun el volcado de CollisionWorld.overlapsStatic) porque,
     * aunque esa linea si esta libre de colisionadores, el objetivo de mirada se aleja de ella
     * hacia cada animatronico (p.ej. Bonnie en Z=6) y el TRAYECTO DE LA MIRADA atraviesa las
     * paredes que separan los "cuartos" del mapa (confirmado con un diagnostico de raycasting por
     * pasos -- DiagCineLOS -- que mostro bloqueos a 0.5-1m de la camara en casi todo el recorrido).
     * La pizzeria esta dividida en varias habitaciones/cabinas separadas por paredes; no existe un
     * unico punto de camara con linea de vista libre a los 4 animatronicos a la vez. Cortar entre
     * una toma fija cerca de cada uno (cada posicion+objetivo verificada individualmente con
     * DiagTomaCamara: overlapsStatic en la posicion de camara + muestreo por pasos en el trayecto
     * hacia el objetivo) es la solucion robusta: cada toma es su propio segmento seguro, sin
     * depender de que el camino ENTRE tomas distintas este libre. */
    private void construirRutaCinematicaInicial() {
        duracionToma = duracionCinematicaInicial / 5f;
        float alturaMirada = 1.3f;
        // Reencuadre pedido por el usuario 2026-08-05: los 4 animatronicos comparten la MISMA
        // direccion de "frente" en espacio de mundo (yaw=0 para los 4, sin rotacion propia por
        // personaje) -- confirmado empiricamente comparando, para Freddy (cuya toma ya mostraba
        // su frente y no se toco), el vector camara->personaje contra el mismo vector en los otros
        // 3 (que mostraban su espalda): en los 4 casos ese vector apunta aproximadamente hacia
        // (-X,+Z). Freddy se veia bien de pura coincidencia de en que lado quedo su camara; Foxy/
        // Bonnie/Chica quedaron del lado opuesto. Sin poder (ni deber) rotar a los personajes --
        // su pose/animacion ya fue calibrada en rondas anteriores -- la correccion es reposicionar
        // la camara de cada uno al lado (-X,+Z) de su spawn, igual que
        // ya pasaba con Freddy, para que se vean "esperando" en vez de dandole la espalda al
        // jugador. Verificado libre de colision (posicion + trayecto a la mirada) con capturas
        // reales antes de fijar estos valores.
        Vector3 foxyPos = new Vector3(-9.5f, 1.7f, 0.4f);
        Vector3 bonniePos = new Vector3(-8.5f, 1.7f, 7.3f);
        Vector3 chicaPos = new Vector3(4f, 1.7f, 3f);
        Vector3 freddyPos = new Vector3(6f, 1.7f, -2f);
        Vector3 finalPos = new Vector3(mapDef.playerStartX, 1.7f, mapDef.playerStartZ - 1f);

        Vector3 foxyObj = new Vector3(mapDef.foxyStartX, alturaMirada, mapDef.foxyStartZ);
        Vector3 bonnieObj = new Vector3(mapDef.bonnieStartX, alturaMirada, mapDef.bonnieStartZ);
        Vector3 chicaObj = new Vector3(mapDef.chicaStartX, alturaMirada, mapDef.chicaStartZ);
        Vector3 freddyObj = new Vector3(mapDef.freddyStartX, alturaMirada, mapDef.freddyStartZ);
        // Ojo: la salita de inicio del jugador esta encerrada por una pared en Z=7 (confirmada
        // por grilla de colision) -- el objetivo de esta ultima toma debe quedarse DENTRO de esa
        // salita (Z=8..10 libres), no apuntar mas alla de la pared como en un primer intento.
        Vector3 finalObj = new Vector3(mapDef.playerStartX, alturaMirada, mapDef.playerStartZ - 1.7f);

        tomaTiempoInicio = new float[5];
        tomaTiempoFin = new float[5];
        for (int i = 0; i < 5; i++) {
            tomaTiempoInicio[i] = i * duracionToma;
            tomaTiempoFin[i] = (i + 1) * duracionToma;
        }

        tomaPosInicio = new Vector3[] {
                foxyPos, bonniePos, chicaPos, freddyPos, finalPos,
        };
        tomaPosFin = new Vector3[] {
                foxyPos, bonniePos, chicaPos, freddyPos, finalPos,
        };
        // El objetivo arranca ligeramente desviado del animatronico -- la camara "lo encuentra"
        // girando un poco durante la toma, en vez de arrancar ya centrado en el.
        tomaObjInicio = new Vector3[] {
                new Vector3(foxyObj).add(0.7f, 0f, 0.6f),
                new Vector3(bonnieObj).add(-0.8f, 0f, 0.9f),
                new Vector3(chicaObj).add(-0.9f, 0f, 0.7f),
                new Vector3(freddyObj).add(0.7f, 0f, 0.9f),
                new Vector3(finalObj).add(1.2f, 0f, 0f),
        };
        tomaObjFin = new Vector3[] {
                foxyObj, bonnieObj, chicaObj, freddyObj, finalObj,
        };
    }

    private final Vector3 cinematicaPosInterp = new Vector3();
    private final Vector3 cinematicaObjInterp = new Vector3();

    private void actualizarCinematicaInicial(float dt) {
        if (tiempoEnEstado == 0f) {
            sceneManager.setAmbientLight(AMBIENTE_CINEMATICA);
            luzDireccional.intensity = INTENSIDAD_DIRECCIONAL_CINEMATICA;
            sonidoEscape.play();
        }
        tiempoEnEstado += dt;
        float t = Math.min(tiempoEnEstado, duracionCinematicaInicial);

        int toma = Math.min((int) (t / duracionToma), tomaPosInicio.length - 1);
        float tInicio = tomaTiempoInicio[toma];
        float tFin = tomaTiempoFin[toma];
        float progreso = tFin > tInicio ? MathUtils.clamp((t - tInicio) / (tFin - tInicio), 0f, 1f) : 1f;
        // smoothstep (no interpolacion lineal): acelera y frena suavemente dentro de cada toma,
        // para que el leve movimiento de camara/mirada se sienta cinematografico y no robotico.
        float progresoSuave = progreso * progreso * (3f - 2f * progreso);

        cinematicaPosInterp.set(tomaPosInicio[toma]).lerp(tomaPosFin[toma], progresoSuave);
        cinematicaObjInterp.set(tomaObjInicio[toma]).lerp(tomaObjFin[toma], progresoSuave);

        camera.position.set(cinematicaPosInterp);
        camera.up.set(Vector3.Y);
        camera.lookAt(cinematicaObjInterp);
        camera.update();
        linternaJugador.position.set(camera.position);
        linternaJugador.direction.set(camera.direction);

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        sceneManager.update(0f);
        sceneManager.render();

        // Fundido a negro breve en los CORTES entre tomas (no al inicio de la primera toma ni al
        // final de la ultima) -- vende el corte como una transicion intencional de "camara de
        // seguridad" en vez de un salto brusco de la imagen.
        float tiempoDesdeInicioToma = t - tInicio;
        float tiempoHastaFinToma = tFin - t;
        float alphaFundido = 0f;
        if (toma > 0 && tiempoDesdeInicioToma < DURACION_FUNDIDO_CORTE) {
            alphaFundido = 1f - (tiempoDesdeInicioToma / DURACION_FUNDIDO_CORTE);
        } else if (toma < tomaPosInicio.length - 1 && tiempoHastaFinToma < DURACION_FUNDIDO_CORTE) {
            alphaFundido = 1f - (tiempoHastaFinToma / DURACION_FUNDIDO_CORTE);
        }
        if (alphaFundido > 0f) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            uiShapes.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            uiShapes.begin(ShapeRenderer.ShapeType.Filled);
            uiShapes.setColor(0f, 0f, 0f, MathUtils.clamp(alphaFundido, 0f, 1f));
            uiShapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            uiShapes.end();
            Gdx.gl.glDisable(GL20.GL_BLEND);
        }

        if (tiempoEnEstado >= duracionCinematicaInicial) {
            sceneManager.setAmbientLight(AMBIENTE_JUGANDO);
            luzDireccional.intensity = INTENSIDAD_DIRECCIONAL_JUGANDO;
            estado = EstadoPartida.INTRO_CORAZONES;
            tiempoEnEstado = 0f;
        }
    }

    // ------------------------------------------------------------------
    // Intro: pantalla negra con corazones (sin texto) -> "RUN" (sin corazones) -> JUGANDO.
    // ------------------------------------------------------------------

    private void actualizarIntroCorazones(float dt) {
        tiempoEnEstado += dt;
        dibujarPantallaNegraConCorazones(vidasRestantes);
        if (tiempoEnEstado >= DURACION_INTRO_CORAZONES) {
            estado = EstadoPartida.INTRO_RUN;
            tiempoEnEstado = 0f;
            // Risa de Freddy al aparecer "RUN" -- una sola vez (pedido explicito del usuario), el
            // flag evita que se repita si este estado se volviera a alcanzar en el futuro.
            if (!risaFreddyReproducida) {
                sonidoRisaFreddy.play();
                risaFreddyReproducida = true;
                // Avisa al lado Swing (proceso independiente) para que baje el volumen de
                // LesToreadorsRemix ~25% en este momento -- ver SenalRisaFreddy.
                com.fivedoorsescape.io.SenalRisaFreddy.marcar();
                Gdx.app.log("GameplayScreen", "Risa de Freddy reproducida (una sola vez) al mostrar RUN");
            }
        }
    }

    private void actualizarIntroRun(float dt) {
        tiempoEnEstado += dt;
        dibujarPantallaNegraConTexto(Lang.get(handoff.idioma, "intro.run"));
        if (tiempoEnEstado >= DURACION_INTRO_RUN) {
            estado = EstadoPartida.JUGANDO;
            tiempoEnEstado = 0f;
        }
    }

    // ------------------------------------------------------------------
    // Secuencia de atrapada (mecanica de 2 vidas, decision de diseno del usuario 2026-08-03):
    // jumpscare cinematografico nativo de LibGDX (camara pegada al modelo 3D real del culpable,
    // con temblor/zoom/rotacion agresivos -- nunca los GIFs 2D de animatronicos del proyecto
    // Swing, para que el Escape tenga identidad visual propia) durante ~1s, seguido del GIF real
    // de estatica ~5s, luego corazones restantes (sin texto) y recien despues reaparece (si queda
    // vida) o Game Over definitivo (si no).
    // ------------------------------------------------------------------

    private void iniciarJumpscare(AIComponent culpable) {
        estado = EstadoPartida.JUMPSCARE;
        tiempoEnEstado = 0f;
        guardiaQueAtrapo = culpable;
        detenerLatidos();
        idSonidoJumpscareActivo = sonidoJumpscare.play();

        // NO se retira mapScene de la escena (a diferencia de una version anterior de esta
        // funcion): retirarlo dejaba al animatronico flotando contra el skybox vacio en vez de
        // aparecer dentro del comedor real -- confirmado visualmente comparando una captura real
        // del jumpscare con y sin el mapa presente. El encuadre cerrado (JUMPSCARE_DISTANCIA_BASE)
        // ya evita que muebles cercanos invadan el cuadro sin necesidad de ocultar el mapa entero.
    }

    private void actualizarJumpscare(float dt) {
        tiempoEnEstado += dt;
        float t = tiempoEnEstado;

        Entity entidadCulpable = entidadDeGuardia(guardiaQueAtrapo);
        TransformComponent transformCulpable = Mappers.transform.get(entidadCulpable);
        ModelComponent modelCulpable = Mappers.model.get(entidadCulpable);
        AnimationComponent animCulpable = Mappers.animation.get(entidadCulpable);

        Vector3 foco = new Vector3(transformCulpable.position.x, transformCulpable.position.y + JUMPSCARE_ALTURA_FOCO,
                transformCulpable.position.z);

        // La camara se acerca desde la direccion real hacia la que mira el personaje (su yaw, ya
        // orientado hacia el jugador desde ChaseState al momento de atrapar), no desde un eje fijo
        // del mundo -- sin esto, la camara podia terminar mirando de costado o por detras segun
        // hacia donde hubiera quedado orientado el culpable, mostrando una mancha de textura
        // irreconocible en vez de su rostro (confirmado en una captura real durante el ajuste de
        // esta distancia).
        float yawRad = transformCulpable.yawDegrees * MathUtils.degreesToRadians;
        float dirX = MathUtils.sin(yawRad);
        float dirZ = MathUtils.cos(yawRad);

        // Zoom rapido oscilante + temblor de posicion/rotacion, mas agresivo que la primera
        // version (mas amplitud y frecuencia) -- deliberadamente poco suave, para que se sienta
        // como un corte de camara real y no una imagen fija.
        float pulsoZoom = 0.12f * MathUtils.sin(t * 19f);
        float distancia = JUMPSCARE_DISTANCIA_BASE - pulsoZoom;
        float shakeX = 0.16f * MathUtils.sin(t * 51f + 1.7f) + 0.08f * MathUtils.sin(t * 97f);
        float shakeY = 0.14f * MathUtils.cos(t * 43f + 0.5f);
        float shakeRoll = 9f * MathUtils.sin(t * 25f);

        camera.position.set(foco.x + dirX * distancia + shakeX, foco.y + shakeY, foco.z + dirZ * distancia);
        camera.up.set(0f, 1f, 0f);
        camera.lookAt(foco);
        camera.rotate(camera.direction, shakeRoll);
        camera.update();

        if (animCulpable == null || !animCulpable.tieneAnimaciones()) {
            // Sin animacion real disponible (Bonnie/Chica/Foxy): en vez de dejarlo perfectamente
            // quieto frente a camara, se le aplica un jitter procedural propio (balanceo de yaw +
            // vaiven vertical) directamente a su transform -- RenderSyncSystem no corre durante el
            // jumpscare (engine.update() pausado), asi que esto no compite con el.
            float jitterYaw = 10f * MathUtils.sin(t * 26f);
            float jitterBob = 0.06f * MathUtils.sin(t * 22f);
            modelCulpable.scene.modelInstance.transform
                    .idt()
                    .translate(transformCulpable.position.x, transformCulpable.position.y + jitterBob, transformCulpable.position.z)
                    .rotate(Vector3.Y, transformCulpable.yawDegrees + jitterYaw)
                    .rotate(Vector3.X, modelCulpable.correctionRotationXDegrees)
                    .scale(modelCulpable.scale, modelCulpable.scale, modelCulpable.scale);
        }
        // Si tiene animacion real (solo Freddy en el MVP), su transform externo ya quedo fijado
        // por RenderSyncSystem en el ultimo frame antes del jumpscare -- sceneManager.update(dt)
        // (abajo) sigue avanzando su animationController (el ciclo "walk" ya activo desde la
        // persecucion), dando movimiento real sin necesidad de tocarlo aqui.

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        sceneManager.update(dt);
        sceneManager.render();

        if (tiempoEnEstado >= DURACION_JUMPSCARE) {
            // El sonido de jumpscare dura solo el susto -- se corta explicitamente aqui en vez de
            // dejar que termine solo, para que nunca se solape con el de estatica que arranca a
            // continuacion (pedido explicito del usuario).
            sonidoJumpscare.stop(idSonidoJumpscareActivo);
            iniciarEstatica();
        }
    }

    private void iniciarEstatica() {
        estado = EstadoPartida.ESTATICA;
        tiempoEnEstado = 0f;
        indiceCuadroEstatica = 0;
        tiempoEnCuadroEstatica = 0f;
        idSonidoEstaticaActivo = sonidoEstatica.play();
    }

    private void actualizarEstatica(float dt) {
        tiempoEnEstado += dt;

        tiempoEnCuadroEstatica += dt;
        while (tiempoEnCuadroEstatica >= duracionesCuadroEstatica.get(indiceCuadroEstatica)) {
            tiempoEnCuadroEstatica -= duracionesCuadroEstatica.get(indiceCuadroEstatica);
            indiceCuadroEstatica = (indiceCuadroEstatica + 1) % cuadrosEstatica.size;
        }
        dibujarTexturaFullscreen(cuadrosEstatica.get(indiceCuadroEstatica));

        if (tiempoEnEstado >= DURACION_ESTATICA) {
            // El GIF (arriba) y el sonido de estatica deben terminar exactamente juntos -- el
            // sonido se corta aqui, en el mismo instante en que se deja de dibujar el GIF, en vez
            // de confiar en que la duracion real del .wav coincida con DURACION_ESTATICA.
            sonidoEstatica.stop(idSonidoEstaticaActivo);
            resolverFinDeAtrapada();
        }
    }

    private void resolverFinDeAtrapada() {
        // Perder la llave al ser atrapado (pedido explicito del usuario 2026-08-10): antes de
        // cualquier otra cosa, para que este siempre en el mismo punto sin importar si al
        // jugador le quedan vidas o no -- perderLlave() ya se guarda solo (no hace nada si nunca
        // tuvo la llave, el mensaje nunca aparece en ese caso).
        perderLlave();
        vidasRestantes--;
        if (vidasRestantes > 0) {
            estado = EstadoPartida.CORAZONES_RESPAWN;
            tiempoEnEstado = 0f;
        } else {
            NightGameOverScreen gameOver = new NightGameOverScreen(game, handoff);
            game.setScreen(gameOver);
            dispose();
        }
    }

    private void actualizarCorazonesRespawn(float dt) {
        tiempoEnEstado += dt;
        dibujarPantallaNegraConCorazones(vidasRestantes);
        if (tiempoEnEstado >= DURACION_CORAZONES_RESPAWN) {
            respawnJugador();
            estado = EstadoPartida.JUGANDO;
        }
    }

    /** Reaparece al jugador y devuelve a los 4 guardias a su spawn original con su IA reiniciada
     * (si no, quedarian donde persiguieron/atraparon, rompiendo la sensacion de reinicio limpio). */
    private void respawnJugador() {
        TransformComponent playerTransform = Mappers.transform.get(playerEntity);
        playerTransform.position.set(mapDef.playerStartX, mapDef.playerStartY, mapDef.playerStartZ);
        playerTransform.yawDegrees = mapDef.playerStartYawDegrees;
        cameraController.resetOrientation(mapDef.playerStartYawDegrees);
        cameraController.applyToCamera(playerTransform.position);

        // Panel de controles otra vez, ~AYUDA_DURACION_VISIBLE segundos (pedido explicito del
        // usuario 2026-08-10) -- el jugador acaba de morir/reaparecer, un buen momento para
        // recordarle los controles reales.
        tiempoJugandoTranscurrido = 0f;

        for (int i = 0; i < guardias.size; i++) {
            AIComponent ai = guardias.get(i);
            ai.jugadorAtrapado = false;
            ai.tiempoAtascado = 0f;
            ai.temporizadorMuestra = 0f;
            ai.distanciaMuestreada = Float.NaN;
            ai.stateMachine.changeState(IdleState.INSTANCE);

            Entity entidad = entidadDeGuardia(ai);
            Mappers.transform.get(entidad).position.set(guardiaSpawns.get(i));
        }
        guardiaQueAtrapo = null;
    }

    // ------------------------------------------------------------------
    // Dibujo de las pantallas negras (intro y respawn): SIN texto en la de corazones, por pedido
    // explicito del usuario ("no quiero mensajes como 'vidas restantes'... solo los corazones").
    // ------------------------------------------------------------------

    private static final float CORAZON_TAMANO = 140f;
    private static final float CORAZON_ESPACIADO = 190f;

    private void dibujarPantallaNegraConCorazones(int vidas) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        float anchoTotal = (VIDAS_INICIALES - 1) * CORAZON_ESPACIADO;
        float xInicial = Gdx.graphics.getWidth() / 2f - anchoTotal / 2f - CORAZON_TAMANO / 2f;
        float y = Gdx.graphics.getHeight() / 2f - CORAZON_TAMANO / 2f;

        uiBatch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glEnable(GL20.GL_BLEND);
        uiBatch.begin();
        for (int i = 0; i < VIDAS_INICIALES; i++) {
            Texture sprite = i < vidas ? texturaCorazonLleno : texturaCorazonVacio;
            uiBatch.draw(sprite, xInicial + i * CORAZON_ESPACIADO, y, CORAZON_TAMANO, CORAZON_TAMANO);
        }
        uiBatch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /** Escala del texto "RUN" de la intro -- mucho mas grande que el tamano normal de uiFont (1.3,
     * usado para el boton de salida), ya que aqui es el unico elemento en toda la pantalla. */
    private static final float ESCALA_TEXTO_INTRO = 8f;

    private void dibujarPantallaNegraConTexto(String texto) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        uiBatch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        float escalaOriginal = uiFont.getScaleX();
        uiFont.getData().setScale(ESCALA_TEXTO_INTRO);
        com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout(uiFont, texto);
        uiBatch.begin();
        uiFont.draw(uiBatch, layout, Gdx.graphics.getWidth() / 2f - layout.width / 2f,
                Gdx.graphics.getHeight() / 2f + layout.height / 2f);
        uiBatch.end();
        uiFont.getData().setScale(escalaOriginal);
    }

    private void dibujarTexturaFullscreen(Texture textura) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        uiBatch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiBatch.begin();
        uiBatch.draw(textura, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiBatch.end();
    }

    /**
     * Panel de ayuda de controles (pedido explicito del usuario 2026-08-05): se dibuja solo,
     * esquina superior izquierda, durante los primeros AYUDA_DURACION_VISIBLE segundos reales de
     * JUGANDO, con un desvanecimiento suave en el ultimo AYUDA_DURACION_FUNDIDO -- nunca captura
     * el mouse ni bloquea el input, el jugador puede moverse libremente desde el primer frame
     * (no invasivo, pedido explicito del usuario). Todo el texto sale de Lang/strings*.properties.
     */
    private void dibujarAyudaInicial(float dt) {
        tiempoJugandoTranscurrido += dt;
        if (tiempoJugandoTranscurrido >= AYUDA_DURACION_VISIBLE) {
            return;
        }

        float tiempoRestante = AYUDA_DURACION_VISIBLE - tiempoJugandoTranscurrido;
        float alpha = tiempoRestante < AYUDA_DURACION_FUNDIDO ? tiempoRestante / AYUDA_DURACION_FUNDIDO : 1f;

        // Y-up estandar de SpriteBatch/ShapeRenderer: valor grande = cerca del borde superior
        // real de la pantalla (gotcha de capturas con flipY=false ya documentado -- no aplica
        // aqui, esto es dibujo en vivo, no una captura de diagnostico).
        float x = AYUDA_MARGEN;
        float yDibujo = Gdx.graphics.getHeight() - AYUDA_MARGEN - AYUDA_ALTO;

        uiBatch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiShapes.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        Gdx.gl.glEnable(GL20.GL_BLEND);
        uiShapes.begin(ShapeRenderer.ShapeType.Filled);
        uiShapes.setColor(0f, 0f, 0f, 0.55f * alpha);
        uiShapes.rect(x, yDibujo, AYUDA_ANCHO, AYUDA_ALTO);
        uiShapes.end();

        uiBatch.begin();
        uiFont.setColor(1f, 1f, 1f, alpha);
        uiFont.draw(uiBatch, Lang.get(handoff.idioma, "help.title"), x + 18f, yDibujo + AYUDA_ALTO - 16f);
        uiFont.draw(uiBatch, Lang.get(handoff.idioma, "help.move"), x + 18f, yDibujo + AYUDA_ALTO - 48f);
        uiFont.draw(uiBatch, Lang.get(handoff.idioma, "help.look"), x + 18f, yDibujo + AYUDA_ALTO - 74f);
        uiFont.draw(uiBatch, Lang.get(handoff.idioma, "help.flashlight"), x + 18f, yDibujo + AYUDA_ALTO - 100f);
        uiFont.draw(uiBatch, Lang.get(handoff.idioma, "help.interact"), x + 18f, yDibujo + AYUDA_ALTO - 126f);
        uiFont.setColor(Color.WHITE);
        uiBatch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * Menu de pausa (pedido explicito del usuario 2026-08-05): overlay semitransparente de
     * pantalla completa + un panel centrado con una unica opcion real, "salir al menu principal"
     * -- sin botones de configuracion/reanudar (ESC de nuevo ya cierra el menu). Mismo mecanismo
     * de salida (Gdx.app.exit()) que ya usan NightGameOverScreen/EscapeVictoryScreen y que Swing
     * ya detecta correctamente via LanzadorEscape/Process.waitFor().
     */
    private void dibujarMenuPausa() {
        float anchoPantalla = Gdx.graphics.getWidth();
        float altoPantalla = Gdx.graphics.getHeight();

        float botonX = anchoPantalla / 2f - PAUSA_BOTON_ANCHO / 2f;
        float botonY = altoPantalla / 2f - PAUSA_BOTON_ALTO / 2f;

        uiBatch.getProjectionMatrix().setToOrtho2D(0, 0, anchoPantalla, altoPantalla);
        uiShapes.getProjectionMatrix().setToOrtho2D(0, 0, anchoPantalla, altoPantalla);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        uiShapes.begin(ShapeRenderer.ShapeType.Filled);
        uiShapes.setColor(0f, 0f, 0f, 0.7f);
        uiShapes.rect(0, 0, anchoPantalla, altoPantalla);
        uiShapes.setColor(0.15f, 0.15f, 0.15f, 0.9f);
        uiShapes.rect(botonX, botonY, PAUSA_BOTON_ANCHO, PAUSA_BOTON_ALTO);
        uiShapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        String titulo = Lang.get(handoff.idioma, "pause.title");
        String textoBoton = Lang.get(handoff.idioma, "pause.exitToMenu");
        GlyphLayout layoutTitulo = new GlyphLayout(uiFont, titulo);
        GlyphLayout layoutBoton = new GlyphLayout(uiFont, textoBoton);

        uiBatch.begin();
        uiFont.draw(uiBatch, layoutTitulo, anchoPantalla / 2f - layoutTitulo.width / 2f, botonY + PAUSA_BOTON_ALTO + 60f);
        uiFont.draw(uiBatch, layoutBoton, anchoPantalla / 2f - layoutBoton.width / 2f, botonY + PAUSA_BOTON_ALTO / 2f + layoutBoton.height / 2f);
        uiBatch.end();

        if (Gdx.input.justTouched()) {
            float touchX = Gdx.input.getX();
            float touchYDesdeArriba = Gdx.input.getY();
            float touchYDesdeAbajo = altoPantalla - touchYDesdeArriba;
            boolean dentroX = touchX >= botonX && touchX <= botonX + PAUSA_BOTON_ANCHO;
            boolean dentroY = touchYDesdeAbajo >= botonY && touchYDesdeAbajo <= botonY + PAUSA_BOTON_ALTO;
            if (dentroX && dentroY) {
                Gdx.app.exit();
            }
        }
    }

    /**
     * Crea un personaje con IA (Freddy o un guardia estatico), lo registra en {@link #guardias}
     * para el chequeo de atrapada y devuelve el maxBones de su modelo (para dimensionar el
     * shader compartido de SceneManager al hueso mas exigente entre todos los personajes).
     */
    private int crearGuardia(EntityFactory factory, String entityId, Vector3 posicion, String nombreParaLog,
            boolean persigueSiempre) {
        Entity entidad = factory.createEntity(entityId, posicion, 0f);
        AIComponent ai = Mappers.ai.get(entidad);
        ai.objetivo = playerEntity;
        ai.collisionWorld = collisionWorld;
        ai.siempreEnPersecucion = persigueSiempre;
        guardias.add(ai);
        guardiaSpawns.add(new Vector3(posicion));
        if (persigueSiempre) {
            // Unico guardia con persigueSiempre=true es Freddy (ver decision de diseno del
            // usuario 2026-08-03) -- identificacion robusta sin depender del orden de creacion.
            freddyEntity = entidad;
        }

        warnIfEmbedded(nombreParaLog, posicion, Mappers.collision.get(entidad).halfExtents);

        return Math.max(assets.getModel(registry.getEntityDefinition(entityId).modelPath).maxBones, 1);
    }

    /**
     * Advierte (sin corregir automaticamente) si un punto de spawn queda incrustado dentro de un
     * collider estatico del mapa -- distinto de estar simplemente detras/oculto por uno, que es
     * normal. Ajuste fino de posiciones queda para iteracion visual, no para logica automatica.
     */
    private void warnIfEmbedded(String nombre, Vector3 position, Vector3 halfExtents) {
        if (collisionWorld.overlapsStatic(position, halfExtents)) {
            Gdx.app.log("GameplayScreen", "ADVERTENCIA: el spawn de " + nombre + " en " + position
                    + " esta incrustado dentro de un collider estatico del mapa -- revisar en "
                    + "content/maps/pizzeria.json o el offset de spawn correspondiente.");
        }
    }

    @Override
    public void resize(int width, int height) {
        sceneManager.updateViewport(width, height);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        sceneManager.dispose();
        environmentCubemap.dispose();
        diffuseCubemap.dispose();
        specularCubemap.dispose();
        brdfLUT.dispose();
        skybox.dispose();
        uiBatch.dispose();
        uiFont.dispose();
        uiShapes.dispose();
        for (Texture cuadro : cuadrosEstatica) {
            cuadro.dispose();
        }
        texturaCorazonLleno.dispose();
        texturaCorazonVacio.dispose();
        texturaSangre.dispose();
        for (Model modelo : modelosDecalSangre) {
            modelo.dispose();
        }
        detenerLatidos();
        if (idSonidoMusicaFreddy != -1) {
            sonidoMusicaFreddy.stop(idSonidoMusicaFreddy);
            idSonidoMusicaFreddy = -1;
        }
        texturaVineta.dispose();
        assets.dispose();
    }
}
