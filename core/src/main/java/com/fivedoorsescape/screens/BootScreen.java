package com.fivedoorsescape.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.fivedoorsescape.assets.AssetService;
import com.fivedoorsescape.content.ContentRegistry;
import com.fivedoorsescape.content.EntityDefinition;
import com.fivedoorsescape.content.MapDefinition;
import com.fivedoorsescape.io.HandoffData;
import com.fivedoorsescape.io.HandoffReader;

/**
 * Lee el traspaso Swing->LibGDX, decide explicitamente que contenido cargar para esta sesion
 * (solo el mapa y Freddy en el MVP -- Bonnie/Chica/Foxy quedan fuera de alcance, ver
 * Architecture.md #2.2) y transiciona a GameplayScreen cuando termina.
 */
public class BootScreen implements Screen {

    private final Game game;
    private final AssetService assets = new AssetService();
    private final ContentRegistry registry = new ContentRegistry();
    private final HandoffData handoff;

    private SpriteBatch batch;
    private BitmapFont font;

    public BootScreen(Game game) {
        this.game = game;
        this.handoff = HandoffReader.read();
    }

    @Override
    public void show() {
        batch = new SpriteBatch();
        font = new BitmapFont();

        MapDefinition mapDef = registry.getMapDefinition("pizzeria");
        EntityDefinition freddyDef = registry.getEntityDefinition("freddy");
        assets.queueModel(mapDef.modelPath);
        assets.queueModel(freddyDef.modelPath);
    }

    @Override
    public void render(float delta) {
        boolean listo = assets.update();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        font.draw(batch, "Cargando... " + (int) (assets.getProgress() * 100) + "%", 20, Gdx.graphics.getHeight() - 20);
        batch.end();

        if (listo) {
            GameplayScreen gameplayScreen = new GameplayScreen(game, registry, assets, handoff);
            batch.dispose();
            font.dispose();
            game.setScreen(gameplayScreen);
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
        // Solo se llega aqui si la aplicacion se cierra mientras todavia se esta cargando --
        // en el flujo normal, Game.setScreen() no dispone la pantalla anterior, asi que este
        // metodo nunca corre despues de transferir assets/batch/font a GameplayScreen.
        if (batch != null) {
            batch.dispose();
        }
        if (font != null) {
            font.dispose();
        }
        assets.dispose();
    }
}
