package com.fivedoorsescape.cinematics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;

/**
 * Omitir una cinematica manteniendo ESC ~1.2 s (CLAUDE.md §2.51), igual que FDAF (OmisionMantenida, §1.62). Un toque
 * no hace nada; el progreso avanza solo mientras ESC (o el boton izquierdo sobre el propio indicador) sigue
 * apretado, se reinicia al soltar y actualizar() devuelve true UNA sola vez. Lo usan la introduccion, la victoria y
 * la derrota definitiva; no crea hilos ni timers: lo avanza el render() de la escena.
 */
public class OmitirCinematica {

    public static final float SEGUNDOS_PARA_OMITIR = 1.2f;

    private final String texto;
    private float progreso = 0f;
    private boolean disparada = false;
    private float zonaX, zonaY, zonaW, zonaH;

    public OmitirCinematica(String texto) {
        this.texto = texto;
    }

    public boolean actualizar(float dt) {
        if (disparada) {
            return false;
        }
        boolean raton = Gdx.input.isButtonPressed(Input.Buttons.LEFT) && enZona(Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
        if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE) || raton) {
            progreso += dt;
            if (progreso >= SEGUNDOS_PARA_OMITIR) {
                disparada = true;
                return true;
            }
        } else {
            progreso = 0f;
        }
        return false;
    }

    private boolean enZona(float x, float y) {
        return zonaW > 0 && x >= zonaX && x <= zonaX + zonaW && y >= zonaY && y <= zonaY + zonaH;
    }

    public float getFraccion() {
        return Math.min(1f, progreso / SEGUNDOS_PARA_OMITIR);
    }

    public boolean isDisparada() {
        return disparada;
    }

    /** Indicador abajo a la derecha: texto tenue y un circulo pequeno que se completa mientras se mantiene. */
    public void dibujar(SpriteBatch batch, ShapeRenderer shapes, BitmapFont fuente, float baseY) {
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float r = Math.max(10f, h / 66f);
        float margen = 26f;
        float cx = w - margen - r;
        float cy = baseY + margen + r;
        float f = getFraccion();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        anillo(shapes, cx, cy, r, 360f, 0.22f);
        if (f > 0f) {
            anillo(shapes, cx, cy, r, 360f * f, 0.95f);
        }
        shapes.end();
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        batch.begin();
        float escala = fuente.getScaleX();
        fuente.getData().setScale(0.9f);
        GlyphLayout layout = new GlyphLayout(fuente, texto);
        fuente.setColor(1f, 1f, 1f, 0.45f + 0.5f * f);
        float tx = cx - r - 12f - layout.width;
        fuente.draw(batch, layout, tx, cy + layout.height / 2f);
        fuente.setColor(Color.WHITE);
        fuente.getData().setScale(escala);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        zonaX = tx - 8f;
        zonaY = cy - r - 8f;
        zonaW = w - zonaX;
        zonaH = 2f * r + 16f;
    }

    /** Arco grueso desde las 12 en sentido horario, hecho con triangulos (ShapeRenderer no tiene arcos con grosor). */
    private static void anillo(ShapeRenderer s, float cx, float cy, float r, float grados, float alfa) {
        s.setColor(1f, 1f, 1f, alfa);
        float grosor = Math.max(2.5f, r * 0.28f);
        int pasos = Math.max(4, (int) (grados / 6f));
        float r0 = r - grosor, r1 = r;
        for (int i = 0; i < pasos; i++) {
            float a0 = (90f - grados * i / pasos) * MathUtils.degreesToRadians;
            float a1 = (90f - grados * (i + 1) / pasos) * MathUtils.degreesToRadians;
            float x00 = cx + MathUtils.cos(a0) * r0, y00 = cy + MathUtils.sin(a0) * r0;
            float x01 = cx + MathUtils.cos(a0) * r1, y01 = cy + MathUtils.sin(a0) * r1;
            float x10 = cx + MathUtils.cos(a1) * r0, y10 = cy + MathUtils.sin(a1) * r0;
            float x11 = cx + MathUtils.cos(a1) * r1, y11 = cy + MathUtils.sin(a1) * r1;
            s.triangle(x00, y00, x01, y01, x11, y11);
            s.triangle(x00, y00, x11, y11, x10, y10);
        }
    }
}
