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
import com.fivedoorsescape.util.Lang;

/**
 * Lee el traspaso Swing->LibGDX, decide explicitamente que contenido cargar para esta sesion
 * (el mapa y los 4 personajes -- Bonnie/Chica/Foxy ya no estan fuera de alcance, ver decision de
 * diseno del usuario 2026-08-02/03: guardias estaticos ademas de Freddy) y transiciona a
 * GameplayScreen cuando termina.
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
        assets.queueModel(mapDef.modelPath);
        for (String entityId : new String[] {"freddy", "bonnie", "chica", "foxy", "llave"}) {
            EntityDefinition def = registry.getEntityDefinition(entityId);
            assets.queueModel(def.modelPath);
        }

        // Sonidos de la secuencia de atrapada (2 vidas, ver decision de diseno del usuario
        // 2026-08-03): un jumpscare y una estatica genericos, reutilizados del pool de assets
        // FDAF con aprobacion explicita del usuario (no son exclusivos de un personaje ni del
        // proyecto Swing en si -- Swing tampoco tiene sonido distinto por animatronico).
        assets.queueSound(GameplayScreen.SONIDO_JUMPSCARE);
        assets.queueSound(GameplayScreen.SONIDO_ESTATICA);
        assets.queueSound(GameplayScreen.SONIDO_RISA_FREDDY);
        assets.queueSound(GameplayScreen.SONIDO_LATIDO_NORMAL);
        assets.queueSound(GameplayScreen.SONIDO_LATIDO_RAPIDO);
        assets.queueSound(GameplayScreen.SONIDO_LINTERNA_CLICK);
        assets.queueSound(GameplayScreen.SONIDO_MUSICA_FREDDY);
        assets.queueSound(GameplayScreen.SONIDO_FORZANDO_PUERTA);
        assets.queueSound(GameplayScreen.SONIDO_AGARRANDO_OBJETO);
        assets.queueSound(GameplayScreen.rutaAudioEscape(handoff.idioma));
    }

    @Override
    public void render(float delta) {
        boolean listo = assets.update();

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        String texto = Lang.get(handoff.idioma, "loading.text") + " " + (int) (assets.getProgress() * 100) + "%";
        font.draw(batch, texto, 20, Gdx.graphics.getHeight() - 20);
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
