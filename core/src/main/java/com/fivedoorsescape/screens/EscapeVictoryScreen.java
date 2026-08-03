package com.fivedoorsescape.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.fivedoorsescape.io.HandoffData;

/**
 * Condicion de victoria del Escape (Architecture.md original la excluia del MVP; expandido con
 * aprobacion explicita del usuario -- ver memoria de Claude "project-libgdx-office-spawn-exit-design").
 * Simetrica a NightGameOverScreen en estructura y estetica; unica interaccion: cerrar el juego
 * (mismo mecanismo Gdx.app.exit() que Swing ya detecta via Process.waitFor() para volver al menu).
 */
public class EscapeVictoryScreen implements Screen {

    private final Game game;
    private final HandoffData handoff;

    private SpriteBatch batch;
    private BitmapFont fontTitulo;
    private BitmapFont fontMensaje;

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
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        boolean ingles = handoff.idioma == HandoffData.Idioma.INGLES;
        String titulo = ingles ? "YOU ESCAPED" : "ESCAPASTE";
        String mensaje = ingles ? "Press ESC to quit" : "Presiona ESC para salir";

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
    }
}
