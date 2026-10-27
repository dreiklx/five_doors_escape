package com.fivedoorsescape.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.fivedoorsescape.util.Fuentes;
import com.fivedoorsescape.io.HandoffData;
import com.fivedoorsescape.util.Lang;

/**
 * Condicion de victoria del Escape (Architecture.md original la excluia del MVP; expandido con
 * aprobacion explicita del usuario -- ver memoria de Claude "project-libgdx-office-spawn-exit-design").
 * Simetrica a NightGameOverScreen en estructura y estetica; unica interaccion: cerrar el juego
 * (mismo mecanismo Gdx.app.exit() que Swing ya detecta via Process.waitFor() para volver al menu).
 *
 * Unificada con el juego principal (pedido explicito del usuario 2026-08-06): mantiene una
 * musica de victoria real (circus.wav, el mismo tema que ya usa la pantalla de Win de Swing --
 * reutilizado, no un asset nuevo) en loop mientras esta pantalla esta visible, en vez de silencio
 * total. "Luego volver al menu normalmente" -- sin botones extra aqui, ESC sigue siendo la unica
 * salida (a diferencia de Game Over, que si necesita distinguir Reintentar de Salir).
 */
public class EscapeVictoryScreen implements Screen {

    private final Game game;
    private final HandoffData handoff;

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    /** Fondo: el comedor de la pantalla de carga (BootScreen), muy oscurecido y alejandose -- el lugar del que se escapo. */
    private Texture fondo;
    private float tiempo;
    private BitmapFont fontTitulo;
    private BitmapFont fontMensaje;
    private Sound sonidoVictoria;
    private Sound sonidoVictoriaReal;
    private long idSonidoVictoria = -1;

    public EscapeVictoryScreen(Game game, HandoffData handoff) {
        this.game = game;
        this.handoff = handoff;
    }

    @Override
    public void show() {
        Gdx.input.setCursorCatched(false);

        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        fondo = new Texture(Gdx.files.internal("ui/loading_background.jpg"));
        fondo.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        // CLAUDE.md §2.88: antes eran la Arial de 15 px de libGDX ampliada x6 (pixelada) sobre negro liso
        fontTitulo = Fuentes.titulo();
        fontMensaje = Fuentes.ui();

        // Cargado directo (no via AssetService/BootScreen): esta pantalla solo se alcanza una
        // vez, al final de una partida ganada -- no vale la pena precargarlo desde el arranque
        // para un uso que puede no llegar a ocurrir nunca en una sesion dada.
        // Con el volumen de musica del menu de pausa (CLAUDE.md §2.75).
        com.fivedoorsescape.audio.VolumenAudio.cargar();
        sonidoVictoriaReal = Gdx.audio.newSound(Gdx.files.internal("audio/music/victory_circus.wav"));
        sonidoVictoria = new com.fivedoorsescape.audio.SonidoConVolumen(sonidoVictoriaReal,
                com.fivedoorsescape.audio.VolumenAudio.Categoria.MUSICA);
        idSonidoVictoria = sonidoVictoria.loop();
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }

        tiempo += delta;
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);

        // La cinematica del auto termina en negro: todo entra con un fundido. Mismo lenguaje que la pantalla de
        // carga (titulo claro con resplandor rojo, filete rojo, texto secundario tenue), sin efectos nuevos.
        float fondoA = MathUtils.clamp(tiempo / 2.5f, 0f, 1f) * 0.32f;
        float tituloA = suave((tiempo - 0.6f) / 1.6f);
        float lineaA = suave((tiempo - 1.6f) / 1.0f);
        float mensajeA = suave((tiempo - 3.0f) / 0.8f) * (0.55f + 0.25f * MathUtils.sin(tiempo * 2.2f));

        float escalaFondo = Math.max((float) w / fondo.getWidth(), (float) h / fondo.getHeight()) * (1.10f - 0.05f * Math.min(1f, tiempo / 30f));
        float fw = fondo.getWidth() * escalaFondo, fh = fondo.getHeight() * escalaFondo;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        batch.begin();
        batch.setColor(1f, 1f, 1f, fondoA);
        batch.draw(fondo, (w - fw) / 2f, (h - fh) / 2f, fw, fh);
        batch.setColor(Color.WHITE);
        batch.end();
        // viñeta: bandas oscuras arriba y abajo con degradado
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        Color negro = new Color(0f, 0f, 0f, 0.9f), claro = new Color(0f, 0f, 0f, 0f);
        shapes.rect(0, 0, w, h * 0.35f, negro, negro, claro, claro);
        shapes.rect(0, h * 0.65f, w, h * 0.35f, claro, claro, negro, negro);
        shapes.end();

        String titulo = Lang.get(handoff.idioma, "victory.title");
        String mensaje = Lang.get(handoff.idioma, "victory.message");
        // GlyphLayout guarda el color al crearse y draw(batch, layout, ...) usa ese, no el de setColor: un layout por color
        fontTitulo.getData().setScale(Fuentes.porAltoTitulo(0.15f));
        GlyphLayout layoutTitulo = new GlyphLayout(fontTitulo, titulo, new Color(0.97f, 0.93f, 0.88f, tituloA), 0f,
                com.badlogic.gdx.utils.Align.left, false);
        GlyphLayout layoutBrillo = new GlyphLayout(fontTitulo, titulo, new Color(0.95f, 0.30f, 0.22f, 0.10f * tituloA), 0f,
                com.badlogic.gdx.utils.Align.left, false);
        float tx = (w - layoutTitulo.width) / 2f, ty = h * 0.56f + layoutTitulo.height / 2f;
        batch.begin();
        // resplandor: el mismo texto en rojo salmon, desplazado en anillo y muy tenue
        float radio = Math.max(2f, h / 260f);
        for (int i = 0; i < 8; i++) {
            float ang = i * MathUtils.PI2 / 8f;
            fontTitulo.draw(batch, layoutBrillo, tx + MathUtils.cos(ang) * radio * 2f, ty + MathUtils.sin(ang) * radio * 2f);
        }
        fontTitulo.draw(batch, layoutTitulo, tx, ty);
        batch.end();

        float anchoLinea = layoutTitulo.width * 0.55f * lineaA;
        float yLinea = ty - layoutTitulo.height - h * 0.045f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.70f, 0.10f, 0.08f, 0.9f * lineaA);
        shapes.rect((w - anchoLinea) / 2f, yLinea, anchoLinea, Math.max(2f, h / 360f));
        shapes.end();

        fontMensaje.getData().setScale(Fuentes.porAltoUi(0.032f));
        GlyphLayout layoutMensaje = new GlyphLayout(fontMensaje, mensaje, new Color(0.85f, 0.85f, 0.85f, mensajeA), 0f,
                com.badlogic.gdx.utils.Align.left, false);
        batch.begin();
        fontMensaje.draw(batch, layoutMensaje, (w - layoutMensaje.width) / 2f, yLinea - h * 0.05f);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private static float suave(float x) {
        float c = MathUtils.clamp(x, 0f, 1f);
        return c * c * (3f - 2f * c);
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

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        fondo.dispose();
        fontTitulo.dispose();
        fontMensaje.dispose();
        if (idSonidoVictoria != -1) {
            sonidoVictoria.stop(idSonidoVictoria);
        }
        sonidoVictoria.dispose();
        sonidoVictoriaReal.dispose();
    }
}
