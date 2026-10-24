package com.fivedoorsescape.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.fivedoorsescape.assets.AssetService;
import com.fivedoorsescape.cinematics.CinematicaDerrota;
import com.fivedoorsescape.cinematics.CinematicaVictoria;
import com.fivedoorsescape.content.ContentRegistry;
import com.fivedoorsescape.content.EntityDefinition;
import com.fivedoorsescape.content.ExteriorDefinition;
import com.fivedoorsescape.content.MapDefinition;
import com.fivedoorsescape.io.HandoffData;
import com.fivedoorsescape.io.HandoffReader;
import com.fivedoorsescape.util.EstadoVentana;
import com.fivedoorsescape.util.Lang;

/**
 * Pantalla de carga de Five Doors Escape (CLAUDE.md §2.59). Fondo del comedor oscurecido con un acercamiento lento,
 * titulo con un leve parpadeo, barra con el progreso REAL del AssetManager (modelos, texturas y sonidos se cargan de a
 * poco en update(), sin bloquear), un indicador que gira para mostrar actividad aunque un archivo grande tarde, y un
 * consejo distinto en cada arranque (no se repite el anterior). Al llegar al 100 % se dibuja un cuadro mas con
 * "Preparando la pizzeria..." y recien entonces se construye GameplayScreen (colisiones, iluminacion, GIFs): antes ese
 * tramo pasaba con la pantalla congelada en "Cargando 100%".
 */
public class BootScreen implements Screen {

    private static final int CANTIDAD_CONSEJOS = 7;
    private boolean focoPedido = false;
    private boolean focoReintentado = false;

    private final Game game;
    private final AssetService assets = new AssetService();
    private final ContentRegistry registry = new ContentRegistry();
    private final HandoffData handoff;

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private Texture fondo;
    private Texture titulo;
    private String consejo;
    private float tiempo = 0f;
    private float progresoMostrado = 0f;
    private int fase = 0;            // 0 cargando, 1 "preparando" ya dibujado, 2 construido
    private long inicioCargaNanos;

    public BootScreen(Game game) {
        this.game = game;
        this.handoff = HandoffReader.read();
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        fondo = new Texture(Gdx.files.internal("ui/loading_background.jpg"));
        fondo.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        titulo = new Texture(Gdx.files.internal("ui/loading_title.png"), true);
        titulo.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        consejo = elegirConsejo();
        inicioCargaNanos = System.nanoTime();

        MapDefinition mapDef = registry.getMapDefinition("pizzeria");
        assets.queueModel(mapDef.modelPath);
        for (String entityId : new String[] {"freddy", "bonnie", "chica", "foxy", "llave", "golden_freddy"}) {
            EntityDefinition def = registry.getEntityDefinition(entityId);
            assets.queueModel(def.modelPath);
        }

        assets.queueSound(GameplayScreen.SONIDO_JUMPSCARE);
        assets.queueSound(GameplayScreen.SONIDO_ESTATICA);
        assets.queueSound(GameplayScreen.SONIDO_RISA_FREDDY);
        assets.queueSound(GameplayScreen.SONIDO_LATIDO_NORMAL);
        assets.queueSound(GameplayScreen.SONIDO_LATIDO_RAPIDO);
        assets.queueSound(GameplayScreen.SONIDO_LINTERNA_CLICK);
        assets.queueSound(GameplayScreen.SONIDO_MUSICA_FREDDY);
        assets.queueSound(GameplayScreen.SONIDO_FORZANDO_PUERTA);
        assets.queueSound(GameplayScreen.SONIDO_AGARRANDO_OBJETO);
        assets.queueSound(GameplayScreen.SONIDO_GLITCH_GRAVE);
        assets.queueSound(GameplayScreen.SONIDO_XSCREAM2);
        assets.queueSound(GameplayScreen.SONIDO_ROBOTVOICE);
        assets.queueSound(GameplayScreen.SONIDO_RESPIRACION_AGITADA);
        assets.queueSound(GameplayScreen.SONIDO_PUERTA_INTERIOR_ABRE);
        assets.queueSound(GameplayScreen.SONIDO_PUERTA_INTERIOR_CIERRA);
        assets.queueSound(GameplayScreen.SONIDO_PUERTA_SEGURIDAD);
        assets.queueSound(GameplayScreen.SONIDO_ATURDIMIENTO);
        assets.queueSound(GameplayScreen.rutaAudioEscape(handoff.idioma));

        // Victoria con salida fisica (CLAUDE.md §2.49): exterior, auto y sus sonidos se cargan con la partida para
        // que abrir la puerta no provoque ninguna pausa de carga.
        ExteriorDefinition exterior = registry.getExteriorDefinition();
        assets.queueModel(exterior.modelPath);
        assets.queueModel(exterior.carModelPath);
        for (String sonido : CinematicaVictoria.SONIDOS) {
            assets.queueSound(sonido);
        }
        for (String sonido : CinematicaDerrota.SONIDOS) {
            assets.queueSound(sonido);
        }
    }

    /** Un consejo distinto del de la vez anterior (guardado en las preferencias propias de Escape, no en el
     * progreso del jugador de FDAF). */
    private String elegirConsejo() {
        int indice = MathUtils.random(CANTIDAD_CONSEJOS - 1);
        try {
            Preferences prefs = Gdx.app.getPreferences("five_doors_escape");
            int anterior = prefs.getInteger("ultimoConsejo", -1);
            if (indice == anterior) {
                indice = (indice + 1 + MathUtils.random(CANTIDAD_CONSEJOS - 2)) % CANTIDAD_CONSEJOS;
            }
            prefs.putInteger("ultimoConsejo", indice);
            prefs.flush();
        } catch (Exception e) {
            Gdx.app.log("BootScreen", "No se pudieron leer/guardar las preferencias del consejo: " + e.getMessage());
        }
        return Lang.get(handoff.idioma, "tip." + (indice + 1));
    }

    public String getConsejo() {
        return consejo;
    }

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.1f);
        tiempo += dt;
        // primer cuadro ya visible: la ventana pide el foco; si a los 0.6 s no lo tiene, un segundo intento (§2.71)
        if (!focoPedido) {
            focoPedido = true;
            EstadoVentana.pedirFoco();
        } else if (!EstadoVentana.isEnfocada() && tiempo > 0.6f && !focoReintentado) {
            focoReintentado = true;
            EstadoVentana.pedirFoco();
        }
        boolean listo = fase != 0 || assets.update();
        float progreso = listo ? 1f : assets.getProgress();
        // la barra se acerca al progreso real sin saltos, pero nunca lo adelanta
        float paso = Math.max(dt * 2.5f * (progreso - progresoMostrado), dt * 0.15f);
        progresoMostrado = Math.min(progreso, progresoMostrado + paso);
        if (listo) {
            progresoMostrado = Math.min(1f, progresoMostrado + dt * 3f);
        }
        dibujar();
        if (listo && progresoMostrado >= 0.999f) {
            if (fase == 0) {
                fase = 1;   // este cuadro ya muestra "Preparando la pizzeria..."; se construye en el siguiente
                Gdx.app.log("BootScreen", "Recursos cargados en " + (System.nanoTime() - inicioCargaNanos) / 1_000_000L + " ms");
            } else if (fase == 1) {
                fase = 2;
                long t0 = System.nanoTime();
                GameplayScreen gameplayScreen = new GameplayScreen(game, registry, assets, handoff);
                game.setScreen(gameplayScreen);
                Gdx.app.log("BootScreen", "Nivel preparado en " + (System.nanoTime() - t0) / 1_000_000L + " ms");
                disposeUi();
            }
        }
    }

    private void dibujar() {
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        float aparicion = MathUtils.clamp(tiempo / 0.5f, 0f, 1f);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);

        batch.begin();
        // fondo "cover" con un acercamiento muy lento
        float escalaFondo = Math.max((float) w / fondo.getWidth(), (float) h / fondo.getHeight()) * (1.04f + 0.03f * Math.min(1f, tiempo / 25f));
        float fw = fondo.getWidth() * escalaFondo, fh = fondo.getHeight() * escalaFondo;
        batch.setColor(1f, 1f, 1f, aparicion);
        batch.draw(fondo, (w - fw) / 2f, (h - fh) / 2f, fw, fh);
        // titulo: parpadeo leve e irregular, como un letrero viejo
        float parpadeo = MathUtils.sin(tiempo * 23f) * MathUtils.sin(tiempo * 3.1f) > 0.93f ? 0.55f : 1f;
        float tw = Math.min(w * 0.46f, 760f), th = tw * titulo.getHeight() / titulo.getWidth();
        batch.setColor(1f, 1f, 1f, aparicion * parpadeo);
        batch.draw(titulo, (w - tw) / 2f, h * 0.58f, tw, th);
        batch.setColor(Color.WHITE);
        batch.end();

        // barra
        float bw = Math.min(w * 0.42f, 640f), bh = 6f;
        float bx = (w - bw) / 2f, by = h * 0.30f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(1f, 1f, 1f, 0.12f * aparicion);
        shapes.rect(bx, by, bw, bh);
        shapes.setColor(0.72f, 0.06f, 0.04f, aparicion);
        shapes.rect(bx, by, bw * progresoMostrado, bh);
        shapes.setColor(1f, 0.45f, 0.35f, aparicion);
        shapes.rect(bx + bw * progresoMostrado - 2f, by - 1f, 2f, bh + 2f);
        // indicador de actividad: arco que gira a la izquierda de la barra
        float cx = bx - 22f, cy = by + bh / 2f, r = 8f;
        for (int i = 0; i < 18; i++) {
            float a0 = -tiempo * 360f + i * 14f;
            shapes.setColor(1f, 1f, 1f, (i / 18f) * 0.9f * aparicion);
            shapes.rectLine(cx + MathUtils.cosDeg(a0) * r, cy + MathUtils.sinDeg(a0) * r,
                    cx + MathUtils.cosDeg(a0 + 14f) * r, cy + MathUtils.sinDeg(a0 + 14f) * r, 2.4f);
        }
        shapes.end();

        batch.begin();
        float escala = font.getScaleX();
        font.getData().setScale(1.1f);
        String estado = fase == 0 ? Lang.get(handoff.idioma, "loading.text") + " " + (int) (progresoMostrado * 100) + "%"
                : Lang.get(handoff.idioma, "loading.preparing");
        font.setColor(0.85f, 0.85f, 0.85f, 0.8f * aparicion);
        GlyphLayout le = new GlyphLayout(font, estado);
        font.draw(batch, le, (w - le.width) / 2f, by - 14f);
        font.getData().setScale(1.0f);
        font.setColor(0.95f, 0.55f, 0.45f, 0.9f * aparicion);
        GlyphLayout etiqueta = new GlyphLayout(font, Lang.get(handoff.idioma, "tip.label"));
        font.draw(batch, etiqueta, (w - etiqueta.width) / 2f, h * 0.17f + 28f);
        font.setColor(0.9f, 0.9f, 0.9f, 0.85f * aparicion);
        font.getData().setScale(1.15f);
        font.draw(batch, consejo, w * 0.2f, h * 0.17f, w * 0.6f, Align.center, true);
        font.getData().setScale(escala);
        font.setColor(Color.WHITE);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    @Override
    public void resize(int width, int height) {
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

    private boolean uiLiberada = false;

    private void disposeUi() {
        if (uiLiberada) {
            return;
        }
        uiLiberada = true;
        batch.dispose();
        shapes.dispose();
        font.dispose();
        fondo.dispose();
        titulo.dispose();
    }

    @Override
    public void dispose() {
        if (batch != null) {
            disposeUi();
        }
        // los recursos pasan a GameplayScreen al construirlo; solo se liberan aqui si la carga se interrumpio antes
        if (fase < 2) {
            assets.dispose();
        }
    }
}
