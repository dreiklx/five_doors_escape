package com.fivedoorsescape.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.fivedoorsescape.io.HandoffData;
import com.fivedoorsescape.io.SalidaCompleta;
import com.fivedoorsescape.util.Lang;

/**
 * Replica visual (color/tipografia/mensaje, no geometria pixel-perfecta) de la pantalla de Game
 * Over de Swing -- continuidad narrativa al ser atrapado por Freddy (Architecture.md #11.3).
 *
 * Unificada con el comportamiento del juego principal (pedido explicito del usuario 2026-08-06):
 * dos botones reales, "Reintentar" (vuelve al menu principal de Swing -- mismo mecanismo
 * Gdx.app.exit() que LanzadorEscape ya detecta) y "Salir" (cierra la aplicacion COMPLETA, no solo
 * Escape -- ver SalidaCompleta para el porque de la señal de archivo compartido en vez de un
 * codigo de salida de proceso).
 */
public class NightGameOverScreen implements Screen {

    private static final float BOTON_ANCHO = 260f;
    private static final float BOTON_ALTO = 54f;
    private static final float ESPACIO_ENTRE_BOTONES = 24f;

    private final Game game;
    private final HandoffData handoff;

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private BitmapFont fontTitulo;
    private BitmapFont fontBoton;

    public NightGameOverScreen(Game game, HandoffData handoff) {
        this.game = game;
        this.handoff = handoff;
    }

    @Override
    public void show() {
        Gdx.input.setCursorCatched(false);

        batch = new SpriteBatch();
        shapes = new ShapeRenderer();

        fontTitulo = new BitmapFont();
        fontTitulo.getData().setScale(6f);
        fontTitulo.setColor(Color.WHITE);

        fontBoton = new BitmapFont();
        fontBoton.getData().setScale(1.6f);
        fontBoton.setColor(Color.WHITE);
    }

    @Override
    public void render(float delta) {
        int anchoPantalla = Gdx.graphics.getWidth();
        int altoPantalla = Gdx.graphics.getHeight();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        String titulo = Lang.get(handoff.idioma, "gameOver.title");
        String textoReintentar = Lang.get(handoff.idioma, "gameOver.retry");
        String textoSalir = Lang.get(handoff.idioma, "gameOver.exit");

        float botonReintentarX = anchoPantalla / 2f - BOTON_ANCHO - ESPACIO_ENTRE_BOTONES / 2f;
        float botonSalirX = anchoPantalla / 2f + ESPACIO_ENTRE_BOTONES / 2f;
        float botonY = altoPantalla / 2f - 80f;

        shapes.getProjectionMatrix().setToOrtho2D(0, 0, anchoPantalla, altoPantalla);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, anchoPantalla, altoPantalla);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.15f, 0.15f, 0.15f, 0.95f);
        shapes.rect(botonReintentarX, botonY, BOTON_ANCHO, BOTON_ALTO);
        shapes.rect(botonSalirX, botonY, BOTON_ANCHO, BOTON_ALTO);
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        GlyphLayout layoutTitulo = new GlyphLayout(fontTitulo, titulo);
        GlyphLayout layoutReintentar = new GlyphLayout(fontBoton, textoReintentar);
        GlyphLayout layoutSalir = new GlyphLayout(fontBoton, textoSalir);

        batch.begin();
        fontTitulo.draw(batch, layoutTitulo, (anchoPantalla - layoutTitulo.width) / 2f, altoPantalla / 2f + 120f);
        fontBoton.draw(batch, layoutReintentar,
                botonReintentarX + (BOTON_ANCHO - layoutReintentar.width) / 2f,
                botonY + (BOTON_ALTO + layoutReintentar.height) / 2f);
        fontBoton.draw(batch, layoutSalir,
                botonSalirX + (BOTON_ANCHO - layoutSalir.width) / 2f,
                botonY + (BOTON_ALTO + layoutSalir.height) / 2f);
        batch.end();

        if (Gdx.input.justTouched()) {
            float touchX = Gdx.input.getX();
            float touchYDesdeAbajo = altoPantalla - Gdx.input.getY();
            boolean dentroY = touchYDesdeAbajo >= botonY && touchYDesdeAbajo <= botonY + BOTON_ALTO;
            if (dentroY && touchX >= botonReintentarX && touchX <= botonReintentarX + BOTON_ANCHO) {
                Gdx.app.exit();
            } else if (dentroY && touchX >= botonSalirX && touchX <= botonSalirX + BOTON_ANCHO) {
                SalidaCompleta.marcar();
                Gdx.app.exit();
            }
        }
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
        fontTitulo.dispose();
        fontBoton.dispose();
    }
}
