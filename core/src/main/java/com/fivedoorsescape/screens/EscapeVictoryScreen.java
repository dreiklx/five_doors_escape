package com.fivedoorsescape.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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
    private BitmapFont fontTitulo;
    private BitmapFont fontMensaje;
    private Sound sonidoVictoria;
    private long idSonidoVictoria = -1;

    public EscapeVictoryScreen(Game game, HandoffData handoff) {
        this.game = game;
        this.handoff = handoff;
    }

    @Override
    public void show() {
        Gdx.input.setCursorCatched(false);

        batch = new SpriteBatch();

        fontTitulo = new BitmapFont();
        fontTitulo.getData().setScale(6f);
        fontTitulo.setColor(Color.WHITE);

        fontMensaje = new BitmapFont();
        fontMensaje.getData().setScale(2f);
        fontMensaje.setColor(Color.WHITE);

        // Cargado directo (no via AssetService/BootScreen): esta pantalla solo se alcanza una
        // vez, al final de una partida ganada -- no vale la pena precargarlo desde el arranque
        // para un uso que puede no llegar a ocurrir nunca en una sesion dada.
        sonidoVictoria = Gdx.audio.newSound(Gdx.files.internal("sounds/circus.wav"));
        idSonidoVictoria = sonidoVictoria.loop();
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        String titulo = Lang.get(handoff.idioma, "victory.title");
        String mensaje = Lang.get(handoff.idioma, "victory.message");

        GlyphLayout layoutTitulo = new GlyphLayout(fontTitulo, titulo);
        GlyphLayout layoutMensaje = new GlyphLayout(fontMensaje, mensaje);

        batch.begin();
        fontTitulo.draw(batch, layoutTitulo, (Gdx.graphics.getWidth() - layoutTitulo.width) / 2f,
                Gdx.graphics.getHeight() / 2f + 60f);
        fontMensaje.draw(batch, layoutMensaje, (Gdx.graphics.getWidth() - layoutMensaje.width) / 2f,
                Gdx.graphics.getHeight() / 2f - 40f);
        batch.end();
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
        fontTitulo.dispose();
        fontMensaje.dispose();
        if (idSonidoVictoria != -1) {
            sonidoVictoria.stop(idSonidoVictoria);
        }
        sonidoVictoria.dispose();
    }
}
