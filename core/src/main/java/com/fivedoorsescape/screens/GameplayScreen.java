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
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
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

    // Boton discreto de salida manual (esquina superior izquierda, ver memoria de Claude
    // "project-libgdx-office-spawn-exit-design"): solo responde a clics cuando el cursor no
    // esta capturado (ESC libera el cursor, ver isKeyJustPressed(ESCAPE) mas abajo) -- con el
    // cursor capturado para mouse-look no hay una posicion de mouse visible que reciba el clic.
    private static final float BOTON_SALIR_ANCHO = 140f;
    private static final float BOTON_SALIR_ALTO = 44f;
    private static final float BOTON_SALIR_MARGEN = 16f;

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

    /** Duracion de la cinematica inicial (pedido explicito del usuario 2026-08-04): recorrido de
     * camara lento mostrando a los 4 animatronicos, todos completamente inmoviles (durante este
     * estado nunca se llama engine.update(), asi que ni la IA ni las animaciones avanzan -- ver
     * actualizarCinematicaInicial). El usuario grabara audio narrativo mas adelante para
     * acompanarla; por ahora solo la temporizacion y el recorrido de camara quedan listos. */
    private static final float DURACION_CINEMATICA_INICIAL = 17f;

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
    private static final int VIDAS_INICIALES = 2;
    private EstadoPartida estado = EstadoPartida.CINEMATICA_INICIAL;
    private float tiempoEnEstado = 0f;
    private int vidasRestantes = VIDAS_INICIALES;
    private AIComponent guardiaQueAtrapo;
    private Sound sonidoJumpscare;
    private Sound sonidoEstatica;
    private Sound sonidoRisaFreddy;
    private boolean risaFreddyReproducida = false;
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
        // Niebla sutil nativa de gdx-gltf, a partir de los 9 unidades y totalmente opaca a los 22
        // -- cubre los pasillos mas largos del mapa sin ocultar nada dentro del alcance normal de
        // juego. El color hacia el que funde (ColorAttribute.Fog, uniform u_fogColor leido por el
        // shader PBR de gdx-gltf) se deja igual de oscuro que antes pero con el mismo tinte azulado
        // frio de la luz direccional en vez del negro puro por defecto -- pedido explicito del
        // usuario 2026-08-05 de una niebla "mas cinematografica": que se sienta parte de la misma
        // atmosfera de luz de luna en vez de un simple "desvanecido a negro" generico.
        sceneManager.environment.set(FogAttribute.createFog(9f, 22f, 2f));
        sceneManager.environment.set(ColorAttribute.createFog(new Color(0.02f, 0.025f, 0.04f, 1f)));

        // Linterna del jugador (pedido explicito del usuario 2026-08-04): SpotLightEx nativo de
        // gdx-gltf (no un PointLight omnidireccional -- un cono real dirigido se siente mucho mas
        // como una linterna real, ademas de gastar luz solo hacia donde el jugador mira en vez de
        // en todas direcciones). Posicion/direccion se actualizan cada frame en
        // actualizarLinternaJugador() para seguir a la camara exactamente. Intensidad y alcance
        // moderados (no "exagerada", pedido explicito) -- ver esa nota para los valores medidos.
        linternaJugador = new SpotLightEx();
        linternaJugador.setColor(1f, 0.96f, 0.85f, 1f);
        linternaJugador.intensity = 18f;
        linternaJugador.setConeDeg(28f, 14f);
        linternaJugador.range = 11f;
        sceneManager.environment.add(linternaJugador);

        skybox = new SceneSkybox(environmentCubemap);
        sceneManager.setSkyBox(skybox);

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
            Gdx.input.setCursorCatched(!Gdx.input.isCursorCatched());
        }

        cameraController.update();

        TransformComponent playerTransform = Mappers.transform.get(playerEntity);
        playerTransform.yawDegrees = cameraController.getYawDegrees();

        Vector3 desiredDelta = cameraController.computeWasdDelta(dt);

        CollisionComponent playerCollision = Mappers.collision.get(playerEntity);
        Vector3 resolved = collisionWorld.resolveMovement(playerTransform.position, desiredDelta, playerCollision.halfExtents, true);
        playerTransform.position.set(resolved);

        cameraController.applyToCamera(playerTransform.position);
        linternaJugador.position.set(camera.position);
        linternaJugador.direction.set(camera.direction);

        engine.update(dt);

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        sceneManager.update(dt);
        sceneManager.render();

        actualizarLatidosYVineta(playerTransform.position);

        manejarBotonSalir();

        float dxSalida = playerTransform.position.x - exitX;
        float dzSalida = playerTransform.position.z - exitZ;
        boolean llegoALaSalida = (dxSalida * dxSalida + dzSalida * dzSalida) <= exitRadius * exitRadius;

        AIComponent guardiaAtrapante = null;
        for (AIComponent ai : guardias) {
            if (ai.jugadorAtrapado) {
                guardiaAtrapante = ai;
                break;
            }
        }

        if (llegoALaSalida && !escapado) {
            escapado = true;
            EscapeVictoryScreen victoria = new EscapeVictoryScreen(game, handoff);
            game.setScreen(victoria);
            dispose();
        } else if (guardiaAtrapante != null) {
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
     * inicio del jugador) y del breve fundido a negro en los cortes entre tomas. */
    private static final float DURACION_TOMA = DURACION_CINEMATICA_INICIAL / 5f;
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
            tomaTiempoInicio[i] = i * DURACION_TOMA;
            tomaTiempoFin[i] = (i + 1) * DURACION_TOMA;
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
        }
        tiempoEnEstado += dt;
        float t = Math.min(tiempoEnEstado, DURACION_CINEMATICA_INICIAL);

        int toma = Math.min((int) (t / DURACION_TOMA), tomaPosInicio.length - 1);
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

        if (tiempoEnEstado >= DURACION_CINEMATICA_INICIAL) {
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
                Gdx.app.log("GameplayScreen", "Risa de Freddy reproducida (una sola vez) al mostrar RUN");
            }
        }
    }

    private void actualizarIntroRun(float dt) {
        tiempoEnEstado += dt;
        dibujarPantallaNegraConTexto("RUN");
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
     * Dibuja el boton de salida manual y responde al clic. El clic solo se evalua con el cursor
     * liberado (ESC lo libera/recaptura, ver arriba) -- con el cursor capturado para mouse-look
     * su posicion no representa un punto real que el jugador este viendo/apuntando.
     */
    private void manejarBotonSalir() {
        float x = BOTON_SALIR_MARGEN;
        // Y-up estandar de SpriteBatch/ShapeRenderer: valor grande = cerca del borde superior
        // real de la pantalla. (Nota: las capturas de pantalla tomadas con
        // PixmapIO.writePNG(file, pixmap) de 2 argumentos NO reflejan esto correctamente --
        // esa sobrecarga usa flipY=false internamente, produciendo una imagen espejada
        // verticalmente respecto a la pantalla real. Confirmado leyendo el codigo fuente de
        // gdx-1.14.2-sources.jar. No usar esa sobrecarga para diagnosticos visuales futuros --
        // usar la de 4 argumentos con flipY=true.)
        float yDibujo = Gdx.graphics.getHeight() - BOTON_SALIR_MARGEN - BOTON_SALIR_ALTO;

        uiBatch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiShapes.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        Gdx.gl.glEnable(GL20.GL_BLEND);
        uiShapes.begin(ShapeRenderer.ShapeType.Filled);
        uiShapes.setColor(0f, 0f, 0f, 0.55f);
        uiShapes.rect(x, yDibujo, BOTON_SALIR_ANCHO, BOTON_SALIR_ALTO);
        uiShapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        String texto = handoff.idioma == HandoffData.Idioma.INGLES ? "Exit" : "Salir";
        uiBatch.begin();
        uiFont.draw(uiBatch, texto, x + 20f, yDibujo + BOTON_SALIR_ALTO - 13f);
        uiBatch.end();

        if (!Gdx.input.isCursorCatched() && Gdx.input.justTouched()) {
            float touchX = Gdx.input.getX();
            float touchYDesdeArriba = Gdx.input.getY();
            boolean dentroX = touchX >= x && touchX <= x + BOTON_SALIR_ANCHO;
            boolean dentroY = touchYDesdeArriba >= BOTON_SALIR_MARGEN && touchYDesdeArriba <= BOTON_SALIR_MARGEN + BOTON_SALIR_ALTO;
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
        detenerLatidos();
        texturaVineta.dispose();
        assets.dispose();
    }
}
