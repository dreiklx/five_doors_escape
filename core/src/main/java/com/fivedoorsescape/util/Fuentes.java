package com.fivedoorsescape.util;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Punto unico de creacion de fuentes de Escape (CLAUDE.md §2.88), como {@code com.fdaf.util.Fuentes} en FDAF. Antes
 * cada pantalla usaba {@code new BitmapFont()}, la Arial de 15 px que trae libGDX, escalada x0.85-x6: los mensajes de
 * interaccion quedaban diminutos y los rotulos grandes ("YOU ESCAPED", "GAME OVER") pixelados.
 *
 * Ahora son Inter SemiBold (56 px) para la interfaz e Inter ExtraBold (128 px) para los titulos, rasterizadas con
 * {@code tools/ui/build_bitmap_fonts.py}, con mipmaps para que se vean nitidas tambien al reducirlas. Para no rehacer
 * cada maquetacion, la escala 1 de {@link #s(float)} y {@link #t(float)} equivale a la altura de linea de la fuente
 * anterior (18 px): {@code setScale(Fuentes.s(1.3f))} ocupa lo mismo que el antiguo {@code setScale(1.3f)}.
 */
public final class Fuentes {

    private static final float ALTO_LINEA_ANTERIOR = 18f;   // lsans-15.fnt de libGDX
    private static final float ALTO_LINEA_UI = 69f;         // inter_ui.fnt
    private static final float ALTO_LINEA_TITULO = 155f;    // inter_title.fnt

    private Fuentes() {
    }

    /** Fuente de interfaz (HUD, menus, mensajes). Quien la crea la libera con dispose(), que tambien libera su textura. */
    public static BitmapFont ui() {
        return cargar("ui/fonts/inter_ui.fnt", "ui/fonts/inter_ui.png", s(1f));
    }

    /** Fuente de titulos grandes. */
    public static BitmapFont titulo() {
        return cargar("ui/fonts/inter_title.fnt", "ui/fonts/inter_title.png", t(1f));
    }

    /** Escala de la fuente de interfaz equivalente a la escala {@code anterior} de la BitmapFont por defecto. */
    public static float s(float anterior) {
        return anterior * ALTO_LINEA_ANTERIOR / ALTO_LINEA_UI;
    }

    /** Lo mismo para la fuente de titulos. */
    public static float t(float anterior) {
        return anterior * ALTO_LINEA_ANTERIOR / ALTO_LINEA_TITULO;
    }

    /** Escala de la fuente de interfaz para que su linea mida {@code fraccion} del alto de la ventana. */
    public static float porAltoUi(float fraccion) {
        return Gdx.graphics.getHeight() * fraccion / ALTO_LINEA_UI;
    }

    /** Lo mismo para la fuente de titulos. */
    public static float porAltoTitulo(float fraccion) {
        return Gdx.graphics.getHeight() * fraccion / ALTO_LINEA_TITULO;
    }

    private static BitmapFont cargar(String fnt, String png, float escalaInicial) {
        Texture textura = new Texture(Gdx.files.internal(png), true);
        textura.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        BitmapFont fuente = new BitmapFont(Gdx.files.internal(fnt), new TextureRegion(textura), false);
        fuente.setOwnsTexture(true);
        fuente.setUseIntegerPositions(false);
        fuente.getData().setScale(escalaInicial);
        return fuente;
    }
}
