package com.fivedoorsescape.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.math.MathUtils;

/**
 * Volumen general, de musica y de efectos de Escape (CLAUDE.md §2.75), ajustable desde el menu de pausa y guardado en
 * las preferencias "five_doors_escape" (las mismas del consejo de carga). Cada nivel va de 0 a 1 en el deslizador; la
 * ganancia aplicada es su cuadrado (curva perceptual: el 50 % del deslizador suena como "la mitad", no casi igual).
 *
 * Todo sonido sale de AssetService envuelto en un SonidoConVolumen de su categoria (musica: audio/music/; el resto
 * son efectos), asi que el volumen se aplica en un solo lugar y al cambiarlo se reaplica en vivo a lo que ya suena.
 */
public final class VolumenAudio {

    public enum Categoria { MUSICA, EFECTOS }

    private static final String PREFS = "five_doors_escape";
    private static float general = 1f, musica = 1f, efectos = 1f;
    private static boolean cargado = false;
    private static final Array<SonidoConVolumen> sonidos = new Array<>();

    private VolumenAudio() {
    }

    public static Categoria categoriaDe(String ruta) {
        return ruta != null && ruta.replace('\\', '/').startsWith("audio/music/") ? Categoria.MUSICA : Categoria.EFECTOS;
    }

    public static void cargar() {
        if (cargado || Gdx.app == null) {
            return;
        }
        cargado = true;
        try {
            Preferences p = Gdx.app.getPreferences(PREFS);
            general = MathUtils.clamp(p.getFloat("volumen.general", 1f), 0f, 1f);
            musica = MathUtils.clamp(p.getFloat("volumen.musica", 1f), 0f, 1f);
            efectos = MathUtils.clamp(p.getFloat("volumen.efectos", 1f), 0f, 1f);
        } catch (RuntimeException e) {
            Gdx.app.error("VolumenAudio", "no se pudieron leer las preferencias de volumen", e);
        }
    }

    public static void guardar() {
        try {
            Preferences p = Gdx.app.getPreferences(PREFS);
            p.putFloat("volumen.general", general);
            p.putFloat("volumen.musica", musica);
            p.putFloat("volumen.efectos", efectos);
            p.flush();
        } catch (RuntimeException e) {
            Gdx.app.error("VolumenAudio", "no se pudieron guardar las preferencias de volumen", e);
        }
    }

    public static float getGeneral() { return general; }
    public static float getMusica() { return musica; }
    public static float getEfectos() { return efectos; }

    public static void setGeneral(float v) { general = MathUtils.clamp(v, 0f, 1f); reaplicar(); }
    public static void setMusica(float v) { musica = MathUtils.clamp(v, 0f, 1f); reaplicar(); }
    public static void setEfectos(float v) { efectos = MathUtils.clamp(v, 0f, 1f); reaplicar(); }

    /** Ganancia final que multiplica el volumen pedido por el juego para un sonido de esa categoria. */
    public static float ganancia(Categoria c) {
        float nivel = general * (c == Categoria.MUSICA ? musica : efectos);
        return nivel * nivel;
    }

    static void registrar(SonidoConVolumen s) {
        sonidos.add(s);
    }

    static void olvidar(SonidoConVolumen s) {
        sonidos.removeValue(s, true);
    }

    private static void reaplicar() {
        for (SonidoConVolumen s : sonidos) {
            s.reaplicar();
        }
    }
}
