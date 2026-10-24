package com.fivedoorsescape.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.fivedoorsescape.audio.VolumenAudio;
import com.fivedoorsescape.io.HandoffData;
import com.fivedoorsescape.util.Lang;

/**
 * Menu de pausa de Escape (CLAUDE.md §2.75). Paneles:
 * - principal: Continuar, Volumen, Controles, Reiniciar partida y Salir al menu principal (estos dos con confirmacion);
 * - volumen: deslizadores general, musica y efectos (VolumenAudio): se aplican al instante a lo que suena y se
 *   guardan al salir del panel o cerrar el menu;
 * - controles: solo las teclas que el juego usa de verdad (las mismas filas que la pantalla de instrucciones).
 *
 * Raton (paso por encima, clic, arrastrar los deslizadores) y teclado (W/S o flechas para elegir, A/D o flechas para
 * el volumen, E/ENTER/ESPACIO para aceptar). ESC en un subpanel vuelve al principal; en el principal lo cierra
 * (GameplayScreen: escape() devuelve false). El mundo no avanza mientras esta abierto: eso lo hace GameplayScreen.
 */
public class MenuPausa {

    public interface Acciones {
        void continuar();
        void reiniciar();
        void salir();
    }

    public enum Panel { PRINCIPAL, VOLUMEN, CONTROLES, CONFIRMAR_REINICIO, CONFIRMAR_SALIDA }

    /** Filas de controles: clave de la accion y clave de la tecla (las mismas de la pantalla de instrucciones). */
    static final String[][] CONTROLES = {
            {"instr.move", "instr.move.key"}, {"instr.look", "instr.look.key"}, {"instr.sprint", "instr.sprint.key"},
            {"instr.flashlight", "instr.flashlight.key"}, {"instr.interact", "instr.interact.key"},
            {"instr.pause", "instr.pause.key"}};
    private static final String[] PRINCIPAL = {"pause.continue", "pause.volume", "pause.controls", "pause.restart",
            "pause.exitToMenu"};
    private static final String[] VOLUMENES = {"pause.volume.general", "pause.volume.music", "pause.volume.effects"};
    private static final float PASO_TECLADO = 0.05f;

    private final HandoffData.Idioma idioma;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final Acciones acciones;
    private final GlyphLayout layout = new GlyphLayout();

    private Panel panel = Panel.PRINCIPAL;
    private int seleccion = 0;
    private int arrastrando = -1;
    private float ratonX = Float.NaN, ratonY = Float.NaN;
    private boolean volumenCambiado = false;
    /** Rectangulos de los elementos del panel actual en el ultimo dibujo (para el raton). */
    private final Array<Rectangle> zonas = new Array<>();
    private final Array<Rectangle> barras = new Array<>();

    public MenuPausa(HandoffData.Idioma idioma, SpriteBatch batch, ShapeRenderer shapes, Acciones acciones) {
        this.idioma = idioma;
        this.batch = batch;
        this.shapes = shapes;
        this.acciones = acciones;
        // fuente propia con filtro lineal: escalada a 1.0-2.6x se ve suave (la del HUD usa vecino mas cercano)
        this.font = new BitmapFont();
        for (com.badlogic.gdx.graphics.g2d.TextureRegion region : font.getRegions()) {
            region.getTexture().setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear,
                    com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        }
        VolumenAudio.cargar();
    }

    public void dispose() {
        font.dispose();
    }

    public void abrir() {
        panel = Panel.PRINCIPAL;
        seleccion = 0;
        arrastrando = -1;
        ratonX = ratonY = Float.NaN;
    }

    /** Al cerrar (Continuar o ESC): guarda el volumen si cambio. */
    public void cerrar() {
        guardarVolumenSiCambio();
        arrastrando = -1;
    }

    /** ESC con el menu abierto: true si lo consumio (volver de un subpanel); false = cerrar el menu. */
    public boolean escape() {
        if (panel == Panel.PRINCIPAL) {
            return false;
        }
        volverAlPrincipal();
        return true;
    }

    public Panel getPanel() {
        return panel;
    }

    public int getSeleccion() {
        return seleccion;
    }

    private String t(String clave) {
        return Lang.get(idioma, clave);
    }

    private int cantidadElementos() {
        switch (panel) {
            case PRINCIPAL: return PRINCIPAL.length;
            case VOLUMEN: return VOLUMENES.length + 1;     // + Volver
            case CONTROLES: return 1;                      // Volver
            default: return 2;                             // Si / No
        }
    }

    private void irA(Panel nuevo, int sel) {
        if (panel == Panel.VOLUMEN && nuevo != Panel.VOLUMEN) {
            guardarVolumenSiCambio();
        }
        panel = nuevo;
        seleccion = sel;
        arrastrando = -1;
    }

    private void volverAlPrincipal() {
        int origen = panel == Panel.VOLUMEN ? 1 : panel == Panel.CONTROLES ? 2 : panel == Panel.CONFIRMAR_REINICIO ? 3
                : panel == Panel.CONFIRMAR_SALIDA ? 4 : 0;
        irA(Panel.PRINCIPAL, origen);
    }

    private void guardarVolumenSiCambio() {
        if (volumenCambiado) {
            VolumenAudio.guardar();
            volumenCambiado = false;
        }
    }

    private float getVolumen(int i) {
        return i == 0 ? VolumenAudio.getGeneral() : i == 1 ? VolumenAudio.getMusica() : VolumenAudio.getEfectos();
    }

    private void setVolumen(int i, float v) {
        v = MathUtils.clamp(Math.round(v * 100f) / 100f, 0f, 1f);
        if (v == getVolumen(i)) {
            return;
        }
        if (i == 0) {
            VolumenAudio.setGeneral(v);
        } else if (i == 1) {
            VolumenAudio.setMusica(v);
        } else {
            VolumenAudio.setEfectos(v);
        }
        volumenCambiado = true;
    }

    private void activar(int i) {
        switch (panel) {
            case PRINCIPAL:
                if (i == 0) {
                    acciones.continuar();
                } else if (i == 1) {
                    irA(Panel.VOLUMEN, 0);
                } else if (i == 2) {
                    irA(Panel.CONTROLES, 0);
                } else if (i == 3) {
                    irA(Panel.CONFIRMAR_REINICIO, 1);   // el foco empieza en "No": un ENTER de mas no borra la partida
                } else {
                    irA(Panel.CONFIRMAR_SALIDA, 1);
                }
                break;
            case VOLUMEN:
                if (i == VOLUMENES.length) {
                    volverAlPrincipal();
                }
                break;
            case CONTROLES:
                volverAlPrincipal();
                break;
            case CONFIRMAR_REINICIO:
            case CONFIRMAR_SALIDA:
                if (i == 0) {
                    Panel p = panel;
                    guardarVolumenSiCambio();
                    if (p == Panel.CONFIRMAR_REINICIO) {
                        acciones.reiniciar();
                    } else {
                        acciones.salir();
                    }
                } else {
                    volverAlPrincipal();
                }
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------ entrada

    private void leerTeclado() {
        int n = cantidadElementos();
        boolean confirmacion = panel == Panel.CONFIRMAR_REINICIO || panel == Panel.CONFIRMAR_SALIDA;
        boolean arriba = Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W);
        boolean abajo = Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S);
        boolean izquierda = Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A);
        boolean derecha = Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D);
        if (confirmacion) {
            // Si / No van uno al lado del otro
            arriba |= izquierda;
            abajo |= derecha;
            izquierda = derecha = false;
        }
        if (arriba) {
            seleccion = (seleccion + n - 1) % n;
        }
        if (abajo) {
            seleccion = (seleccion + 1) % n;
        }
        if (panel == Panel.VOLUMEN && seleccion < VOLUMENES.length && (izquierda || derecha)) {
            setVolumen(seleccion, getVolumen(seleccion) + (derecha ? PASO_TECLADO : -PASO_TECLADO));
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.E)
                || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            activar(seleccion);
        }
    }

    private void leerRaton(float alto) {
        float x = Gdx.input.getX(), y = alto - Gdx.input.getY();
        boolean movio = x != ratonX || y != ratonY;
        ratonX = x;
        ratonY = y;
        int bajo = -1;
        for (int i = 0; i < zonas.size; i++) {
            if (zonas.get(i).contains(x, y)) {
                bajo = i;
            }
        }
        if (movio && bajo >= 0) {
            seleccion = bajo;
        }
        if (arrastrando >= 0) {
            if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
                Rectangle b = barras.get(arrastrando);
                setVolumen(arrastrando, (x - b.x) / b.width);
            } else {
                arrastrando = -1;
            }
        }
        if (Gdx.input.justTouched()) {
            if (panel == Panel.VOLUMEN) {
                for (int i = 0; i < barras.size; i++) {
                    Rectangle b = barras.get(i);
                    // la barra con un margen vertical generoso: es facil de agarrar
                    if (x >= b.x - 10f && x <= b.x + b.width + 10f && y >= b.y - 14f && y <= b.y + b.height + 14f) {
                        arrastrando = i;
                        seleccion = i;
                        setVolumen(i, (x - b.x) / b.width);
                        return;
                    }
                }
            }
            if (bajo >= 0) {
                activar(bajo);
            }
        }
    }

    // ------------------------------------------------------------------ dibujo

    /** Un cuadro del menu: dibuja sobre la escena congelada y procesa la entrada. */
    public void actualizarYDibujar() {
        float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        float k = Math.max(0.6f, h / 1080f);
        batch.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        shapes.getProjectionMatrix().setToOrtho2D(0, 0, w, h);
        float escalaOriginal = font.getData().scaleX;

        zonas.clear();
        barras.clear();
        float anchoPanel = 640f * k, cx = w / 2f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.72f);
        shapes.rect(0, 0, w, h);
        shapes.end();

        switch (panel) {
            case PRINCIPAL: dibujarPrincipal(cx, h, k, anchoPanel); break;
            case VOLUMEN: dibujarVolumen(cx, h, k, anchoPanel); break;
            case CONTROLES: dibujarControles(cx, h, k, anchoPanel); break;
            default: dibujarConfirmacion(cx, h, k, anchoPanel); break;
        }
        Gdx.gl.glDisable(GL20.GL_BLEND);
        font.getData().setScale(escalaOriginal);
        font.setColor(1f, 1f, 1f, 1f);

        // la entrada despues del dibujo: las zonas del raton son las de este mismo cuadro
        Panel antes = panel;
        leerRaton(h);
        if (panel == antes) {
            leerTeclado();
        }
    }

    private void marco(float cx, float yArriba, float yAbajo, float ancho, float k) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.05f, 0.05f, 0.06f, 0.93f);
        shapes.rect(cx - ancho / 2f, yAbajo, ancho, yArriba - yAbajo);
        shapes.setColor(0.55f, 0.08f, 0.08f, 1f);
        shapes.rect(cx - ancho / 2f, yArriba - 4f * k, ancho, 4f * k);   // filete rojo arriba
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.35f, 0.35f, 0.38f, 1f);
        shapes.rect(cx - ancho / 2f, yAbajo, ancho, yArriba - yAbajo);
        shapes.end();
    }

    private void texto(String s, float x, float y, float escala, float r, float g, float b, int align, float ancho) {
        font.getData().setScale(escala);
        font.setColor(r, g, b, 1f);
        batch.begin();
        layout.setText(font, s, font.getColor(), ancho, align, false);
        font.draw(batch, layout, x, y + layout.height / 2f);
        batch.end();
    }

    private void boton(int i, String s, float cx, float y, float ancho, float alto, float k) {
        Rectangle r = new Rectangle(cx - ancho / 2f, y - alto / 2f, ancho, alto);
        zonas.add(r);
        boolean sel = i == seleccion;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (sel) {
            shapes.setColor(0.42f, 0.05f, 0.05f, 0.95f);
        } else {
            shapes.setColor(0.13f, 0.13f, 0.14f, 0.95f);
        }
        shapes.rect(r.x, r.y, r.width, r.height);
        if (sel) {
            shapes.setColor(0.9f, 0.2f, 0.18f, 1f);
            shapes.rect(r.x, r.y, 5f * k, r.height);                      // marca a la izquierda del elegido
        }
        shapes.end();
        texto(s, r.x, y, 1.45f * k, 1f, sel ? 1f : 0.86f, sel ? 1f : 0.86f, Align.center, r.width);
    }

    private void pie(String clave, float cx, float y, float k) {
        texto(t(clave), cx - 300f * k, y, 1.0f * k, 0.6f, 0.6f, 0.62f, Align.center, 600f * k);
    }

    private void dibujarPrincipal(float cx, float h, float k, float anchoPanel) {
        float altoBoton = 58f * k, sep = 14f * k;
        float alto = 150f * k + PRINCIPAL.length * (altoBoton + sep) + 50f * k;
        float arriba = h / 2f + alto / 2f, abajo = h / 2f - alto / 2f;
        marco(cx, arriba, abajo, anchoPanel, k);
        texto(t("pause.title").toUpperCase(), cx - anchoPanel / 2f, arriba - 70f * k, 2.6f * k, 1f, 1f, 1f, Align.center, anchoPanel);
        float y = arriba - 150f * k;
        for (int i = 0; i < PRINCIPAL.length; i++) {
            boton(i, t(PRINCIPAL[i]), cx, y, 460f * k, altoBoton, k);
            y -= altoBoton + sep;
        }
        pie("pause.hint.main", cx, abajo + 28f * k, k);
    }

    private void dibujarVolumen(float cx, float h, float k, float anchoPanel) {
        float fila = 74f * k;
        float alto = 150f * k + VOLUMENES.length * fila + 110f * k;
        float arriba = h / 2f + alto / 2f, abajo = h / 2f - alto / 2f;
        marco(cx, arriba, abajo, anchoPanel, k);
        texto(t("pause.volume").toUpperCase(), cx - anchoPanel / 2f, arriba - 70f * k, 2.2f * k, 1f, 1f, 1f, Align.center, anchoPanel);
        float y = arriba - 150f * k, izq = cx - anchoPanel / 2f + 40f * k;
        float anchoBarra = 230f * k, xBarra = cx - 40f * k;
        for (int i = 0; i < VOLUMENES.length; i++) {
            boolean sel = i == seleccion;
            Rectangle zona = new Rectangle(izq - 16f * k, y - fila / 2f + 4f * k, anchoPanel - 48f * k, fila - 8f * k);
            zonas.add(zona);
            Rectangle barra = new Rectangle(xBarra, y - 5f * k, anchoBarra, 10f * k);
            barras.add(barra);
            float v = getVolumen(i);
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            if (sel) {
                shapes.setColor(0.3f, 0.04f, 0.04f, 0.9f);
                shapes.rect(zona.x, zona.y, zona.width, zona.height);
            }
            shapes.setColor(0.25f, 0.25f, 0.27f, 1f);
            shapes.rect(barra.x, barra.y, barra.width, barra.height);
            shapes.setColor(0.85f, 0.18f, 0.15f, 1f);
            shapes.rect(barra.x, barra.y, barra.width * v, barra.height);
            shapes.setColor(sel ? 1f : 0.85f, sel ? 1f : 0.85f, sel ? 1f : 0.85f, 1f);
            shapes.circle(barra.x + barra.width * v, y, 12f * k, 20);
            shapes.end();
            texto(t(VOLUMENES[i]), izq, y, 1.35f * k, 1f, 1f, 1f, Align.left, 260f * k);
            texto(Math.round(v * 100f) + " %", xBarra + anchoBarra + 22f * k, y, 1.35f * k, 0.9f, 0.9f, 0.9f, Align.right, 80f * k);
            y -= fila;
        }
        boton(VOLUMENES.length, t("pause.back"), cx, y - 12f * k, 300f * k, 54f * k, k);
        pie("pause.hint.volume", cx, abajo + 28f * k, k);
    }

    private void dibujarControles(float cx, float h, float k, float anchoPanel) {
        float fila = 48f * k;
        float alto = 150f * k + CONTROLES.length * fila + 120f * k;
        float arriba = h / 2f + alto / 2f, abajo = h / 2f - alto / 2f;
        marco(cx, arriba, abajo, anchoPanel, k);
        texto(t("pause.controls").toUpperCase(), cx - anchoPanel / 2f, arriba - 70f * k, 2.2f * k, 1f, 1f, 1f, Align.center, anchoPanel);
        float y = arriba - 140f * k, izq = cx - anchoPanel / 2f + 50f * k, anchoUtil = anchoPanel - 100f * k;
        for (String[] c : CONTROLES) {
            texto(t(c[0]), izq, y, 1.3f * k, 0.85f, 0.85f, 0.87f, Align.left, anchoUtil * 0.62f);
            texto(t(c[1]), izq + anchoUtil * 0.55f, y, 1.3f * k, 1f, 0.82f, 0.4f, Align.right, anchoUtil * 0.45f);
            y -= fila;
        }
        boton(0, t("pause.back"), cx, y - 24f * k, 300f * k, 54f * k, k);
        pie("pause.hint.sub", cx, abajo + 28f * k, k);
    }

    private void dibujarConfirmacion(float cx, float h, float k, float anchoPanel) {
        float alto = 300f * k;
        float arriba = h / 2f + alto / 2f, abajo = h / 2f - alto / 2f;
        marco(cx, arriba, abajo, anchoPanel, k);
        String pregunta = t(panel == Panel.CONFIRMAR_REINICIO ? "pause.confirm.restart" : "pause.confirm.exit");
        texto(pregunta, cx - anchoPanel / 2f + 40f * k, arriba - 90f * k, 1.4f * k, 1f, 1f, 1f, Align.center, anchoPanel - 80f * k);
        texto(t("pause.confirm.lost"), cx - anchoPanel / 2f + 40f * k, arriba - 140f * k, 1.1f * k, 0.75f, 0.75f, 0.78f, Align.center, anchoPanel - 80f * k);
        float y = abajo + 80f * k;
        boton(0, t("pause.yes"), cx - 120f * k, y, 200f * k, 54f * k, k);
        boton(1, t("pause.no"), cx + 120f * k, y, 200f * k, 54f * k, k);
    }
}
