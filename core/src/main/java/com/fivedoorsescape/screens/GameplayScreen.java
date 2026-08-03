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
import com.badlogic.gdx.graphics.Pixmap;
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
import com.fivedoorsescape.world.CollisionWorld;
import com.fivedoorsescape.world.LevelLoader;

import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute;
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute;
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx;
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

    /** Duracion de cada tramo de la secuencia de atrapada (ver mecanica de 2 vidas). */
    private static final float DURACION_JUMPSCARE = 1f;
    private static final float DURACION_ESTATICA = 5f;

    /** Altura (sobre la posicion base, que es la de los pies) y distancia de la camara de
     * jumpscare -- valores de arranque razonables para que el personaje llene casi toda la
     * pantalla sin recortar la cabeza, ajustados a ojo contra los 4 modelos reales. */
    private static final float JUMPSCARE_ALTURA_FOCO = 1.3f;
    private static final float JUMPSCARE_DISTANCIA_BASE = 1.15f;

    /** Resolucion baja e intencional del ruido de estatica -- se estira a pantalla completa, el
     * costo de regenerarlo cada frame se mantiene bajo y el aspecto "pixelado" es consistente con
     * un efecto de estatica de TV real. */
    private static final int ESTATICA_ANCHO = 160;
    private static final int ESTATICA_ALTO = 90;

    private enum EstadoPartida { JUGANDO, JUMPSCARE, ESTATICA }

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

    // Mecanica de 2 vidas (decision de diseno del usuario 2026-08-03): al atrapar, jumpscare
    // cinematografico (camara + modelo 3D del culpable) -> estatica -> reaparece si queda vida,
    // Game Over definitivo si no.
    private EstadoPartida estado = EstadoPartida.JUGANDO;
    private float tiempoEnEstado = 0f;
    private int vidasRestantes = 2;
    private AIComponent guardiaQueAtrapo;
    private Sound sonidoJumpscare;
    private Sound sonidoEstatica;
    private Pixmap pixmapEstatica;
    private Texture texturaEstatica;

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

        Vector3 freddyStart = new Vector3(mapDef.freddyStartX, 0f, mapDef.freddyStartZ);
        numBones = Math.max(numBones, crearGuardia(factory, "freddy", freddyStart, "Freddy"));

        // Bonnie/Chica/Foxy: guardias estaticos en un punto fijo (IdleState -- no patrullan, ver
        // decision de diseno del usuario). Reutilizan exactamente el mismo IdleState/ChaseState/
        // CaughtState generico que ya usaba Freddy -- ninguno es especifico de un personaje.
        Vector3 bonnieStart = new Vector3(mapDef.bonnieStartX, 0f, mapDef.bonnieStartZ);
        numBones = Math.max(numBones, crearGuardia(factory, "bonnie", bonnieStart, "Bonnie"));

        Vector3 chicaStart = new Vector3(mapDef.chicaStartX, 0f, mapDef.chicaStartZ);
        numBones = Math.max(numBones, crearGuardia(factory, "chica", chicaStart, "Chica"));

        Vector3 foxyStart = new Vector3(mapDef.foxyStartX, 0f, mapDef.foxyStartZ);
        numBones = Math.max(numBones, crearGuardia(factory, "foxy", foxyStart, "Foxy"));

        sceneManager = new SceneManager(PBRShaderProvider.createDefault(numBones), PBRShaderProvider.createDefaultDepth(numBones));
        sceneManager.addScene(mapScene);
        for (Entity guardia : engine.getEntities()) {
            if (Mappers.model.has(guardia)) {
                sceneManager.addScene(Mappers.model.get(guardia).scene);
            }
        }
        sceneManager.setCamera(camera);

        DirectionalLightEx light = new DirectionalLightEx();
        light.direction.set(1f, -3f, 1f).nor();
        light.color.set(Color.WHITE);
        light.intensity = 3f;
        sceneManager.environment.add(light);

        IBLBuilder iblBuilder = IBLBuilder.createOutdoor(light);
        environmentCubemap = iblBuilder.buildEnvMap(1024);
        diffuseCubemap = iblBuilder.buildIrradianceMap(256);
        specularCubemap = iblBuilder.buildRadianceMap(10);
        iblBuilder.dispose();

        brdfLUT = new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));
        sceneManager.setAmbientLight(1f);
        sceneManager.environment.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT));
        sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap));
        sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap));

        skybox = new SceneSkybox(environmentCubemap);
        sceneManager.setSkyBox(skybox);

        uiBatch = new SpriteBatch();
        uiFont = new BitmapFont();
        uiFont.getData().setScale(1.3f);
        uiFont.setColor(Color.WHITE);
        uiShapes = new ShapeRenderer();

        sonidoJumpscare = assets.getSound(SONIDO_JUMPSCARE);
        sonidoEstatica = assets.getSound(SONIDO_ESTATICA);

        // Ruido de estatica generado por procedimiento (no un GIF decodificado): libGDX no trae
        // un loader de GIF animado nativo, y agregar una libreria nueva solo para esto seria mas
        // invasivo que generar el ruido con Pixmap/Texture ya disponibles. Visualmente cumple el
        // mismo rol (interferencia de TV) que el GIF de estatica de FiveDoorsAtFreddys.
        pixmapEstatica = new Pixmap(ESTATICA_ANCHO, ESTATICA_ALTO, Pixmap.Format.RGB888);
        texturaEstatica = new Texture(pixmapEstatica);
        texturaEstatica.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
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

        if (estado == EstadoPartida.JUMPSCARE) {
            actualizarJumpscare(dt);
            return;
        }
        if (estado == EstadoPartida.ESTATICA) {
            actualizarEstatica(dt);
            return;
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

    /**
     * Secuencia de atrapada (mecanica de 2 vidas, decision de diseno del usuario 2026-08-03):
     * jumpscare cinematografico nativo de LibGDX (camara pegada al modelo 3D real del culpable,
     * con temblor/zoom/rotacion agresivos -- nunca los GIFs 2D del proyecto Swing, para que el
     * Escape tenga identidad visual propia) durante ~1s, seguido de estatica ~5s, y recien despues
     * reaparece (si queda vida) o Game Over definitivo (si no).
     */
    private void iniciarJumpscare(AIComponent culpable) {
        estado = EstadoPartida.JUMPSCARE;
        tiempoEnEstado = 0f;
        guardiaQueAtrapo = culpable;
        sonidoJumpscare.play();
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

        // Zoom rapido oscilante + temblor de posicion/rotacion -- deliberadamente agresivo y poco
        // suave, para que se sienta como un corte de camara real y no una imagen fija.
        float pulsoZoom = 0.3f * MathUtils.sin(t * 16f);
        float distancia = JUMPSCARE_DISTANCIA_BASE - pulsoZoom;
        float shakeX = 0.12f * MathUtils.sin(t * 45f + 1.7f) + 0.05f * MathUtils.sin(t * 91f);
        float shakeY = 0.10f * MathUtils.cos(t * 37f + 0.5f);
        float shakeRoll = 6f * MathUtils.sin(t * 21f);

        camera.position.set(foco.x + shakeX, foco.y + shakeY, foco.z + distancia);
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
            iniciarEstatica();
        }
    }

    private void iniciarEstatica() {
        estado = EstadoPartida.ESTATICA;
        tiempoEnEstado = 0f;
        sonidoEstatica.play();
    }

    private void actualizarEstatica(float dt) {
        tiempoEnEstado += dt;
        dibujarEstatica();
        if (tiempoEnEstado >= DURACION_ESTATICA) {
            resolverFinDeAtrapada();
        }
    }

    /**
     * Ruido de estatica generado por procedimiento cada frame (ver campo texturaEstatica): mas
     * simple y robusto que decodificar el GIF de estatica existente, ya que libGDX no trae un
     * loader de GIF animado nativo y agregar una libreria nueva solo para esto seria mas invasivo
     * que generar el ruido con las clases Pixmap/Texture ya disponibles.
     */
    private void dibujarEstatica() {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        for (int y = 0; y < ESTATICA_ALTO; y++) {
            for (int x = 0; x < ESTATICA_ANCHO; x++) {
                float gris = MathUtils.random();
                pixmapEstatica.setColor(gris, gris, gris, 1f);
                pixmapEstatica.drawPixel(x, y);
            }
        }
        texturaEstatica.draw(pixmapEstatica, 0, 0);

        uiBatch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiBatch.begin();
        uiBatch.draw(texturaEstatica, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        uiBatch.end();
    }

    private void resolverFinDeAtrapada() {
        vidasRestantes--;
        if (vidasRestantes > 0) {
            respawnJugador();
            estado = EstadoPartida.JUGANDO;
        } else {
            NightGameOverScreen gameOver = new NightGameOverScreen(game, handoff);
            game.setScreen(gameOver);
            dispose();
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
    private int crearGuardia(EntityFactory factory, String entityId, Vector3 posicion, String nombreParaLog) {
        Entity entidad = factory.createEntity(entityId, posicion, 0f);
        AIComponent ai = Mappers.ai.get(entidad);
        ai.objetivo = playerEntity;
        ai.collisionWorld = collisionWorld;
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
        pixmapEstatica.dispose();
        texturaEstatica.dispose();
        assets.dispose();
    }
}
