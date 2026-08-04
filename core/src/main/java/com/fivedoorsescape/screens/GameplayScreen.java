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
import com.badlogic.gdx.graphics.PerspectiveCamera;
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

    /** Altura (sobre la posicion base, que es la de los pies) y distancia de la camara de
     * jumpscare -- acercada al maximo sobre el rostro sin que el near plane (0.05) recorte el
     * modelo, con temblor mas agresivo que la primera version. */
    private static final float JUMPSCARE_ALTURA_FOCO = 1.5f;
    private static final float JUMPSCARE_DISTANCIA_BASE = 0.75f;

    private enum EstadoPartida { INTRO_CORAZONES, INTRO_RUN, JUGANDO, JUMPSCARE, ESTATICA, CORAZONES_RESPAWN }

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
    private EstadoPartida estado = EstadoPartida.INTRO_CORAZONES;
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
        levelLoader.buildStaticColliders(mapScene, collisionWorld);
        Gdx.app.log("GameplayScreen", "Colisionadores estaticos generados: " + collisionWorld.getStaticColliderCount());

        exitX = mapDef.exitX;
        exitZ = mapDef.exitZ;
        exitRadius = mapDef.exitRadius;

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

        sceneManager = new SceneManager(PBRShaderProvider.createDefault(numBones), PBRShaderProvider.createDefaultDepth(numBones));
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
        DirectionalShadowLight light = new DirectionalShadowLight(1024, 1024, 26f, 26f, 0f, 30f);
        light.direction.set(1f, -3f, 1f).nor();
        light.color.set(Color.WHITE);
        light.intensity = 3f;
        light.setCenter(-1.5f, 1f, 1.5f);
        sceneManager.environment.add(light);

        IBLBuilder iblBuilder = IBLBuilder.createOutdoor(light);
        environmentCubemap = iblBuilder.buildEnvMap(1024);
        diffuseCubemap = iblBuilder.buildIrradianceMap(256);
        specularCubemap = iblBuilder.buildRadianceMap(10);
        iblBuilder.dispose();

        brdfLUT = new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));
        // Ambiente reducido de 1.0 a 0.35 (medido visualmente, ver CLAUDE.md): con luz ambiente al
        // maximo las sombras nuevas practicamente no se notaban y la escena se veia plana. Este
        // valor mantiene la visibilidad del jugador (no es un juego de sigilo con oscuridad total)
        // pero deja contraste real entre luz y sombra.
        sceneManager.setAmbientLight(0.35f);
        sceneManager.environment.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT));
        sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap));
        sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap));
        // Niebla sutil nativa de gdx-gltf, funde hacia el color de fondo (negro, ver glClearColor)
        // a partir de los 9 unidades y totalmente opaca a los 22 -- cubre los pasillos mas largos
        // del mapa sin ocultar nada dentro del alcance normal de juego.
        sceneManager.environment.set(FogAttribute.createFog(9f, 22f, 2f));

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
    }

    private Entity entidadDeGuardia(AIComponent ai) {
        for (Entity entidad : engine.getEntities()) {
            if (Mappers.ai.has(entidad) && Mappers.ai.get(entidad) == ai) {
                return entidad;
            }
        }
        throw new IllegalStateException("No se encontro la entidad para el AIComponent dado");
    }

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, MAX_FRAME_DELTA);

        switch (estado) {
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
        Vector3 resolved = collisionWorld.resolveMovement(playerTransform.position, desiredDelta, playerCollision.halfExtents);
        playerTransform.position.set(resolved);

        cameraController.applyToCamera(playerTransform.position);

        engine.update(dt);

        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        sceneManager.update(dt);
        sceneManager.render();

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
        assets.dispose();
    }
}
