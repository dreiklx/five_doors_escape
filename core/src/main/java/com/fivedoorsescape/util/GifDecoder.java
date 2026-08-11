package com.fivedoorsescape.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

/**
 * Decodifica un GIF animado a una secuencia de Pixmap + duracion por cuadro, usando el lector de
 * GIF ya incluido en el JDK (javax.imageio) -- libGDX no trae un loader de GIF animado nativo, y
 * esto evita agregar una libreria externa nueva solo para reproducir gifs ya existentes.
 *
 * IMPORTANTE (hallazgo real, investigado 2026-08-11 al depurar "ItsMe.gif aparece con fondo"): un
 * GIF no es una simple secuencia de imagenes independientes del mismo tamaño -- cada cuadro puede
 * ser solo la REGION que cambio respecto al cuadro anterior (ImageDescriptor con su propio ancho/
 * alto/offset, casi siempre mas chico que el canvas real declarado en el LogicalScreenDescriptor),
 * y el "metodo de disposicion" (GraphicControlExtension.disposalMethod) de cada cuadro le dice al
 * reproductor que hacer con esa region DESPUES de mostrarla y ANTES de dibujar el cuadro
 * siguiente (dejarla tal cual, o restaurarla a transparente). `ImageReader.read(i)` de
 * javax.imageio devuelve el BufferedImage LOCAL de ese cuadro (con su propio tamaño, a veces de
 * 1x1 pixeles) -- NUNCA el canvas completo ya compuesto.
 *
 * El decodificador anterior trataba cada cuadro como si ya fuera la imagen completa, lo cual
 * funcionaba por casualidad con Static.gif (cada cuadro de ruido ocupa el canvas completo, sin
 * optimizacion de GIF) pero rompia con ItsMe.gif -- confirmado con una prueba real fuera del
 * juego (dump de metadata): la mitad de sus 11 cuadros son un unico pixel de 1x1
 * (disposalMethod=doNotDispose, transparente, "sin cambios reales") alternando con cuadros
 * completos de 1600x900 (disposalMethod=restoreToBackgroundColor). Al estirar un cuadro de 1x1 a
 * pantalla completa se veia como un rectangulo solido de fondo en vez de dejar ver la escena
 * detras. Este decodificador ahora compone un canvas real (Pixmap persistente del tamaño
 * declarado en el LogicalScreenDescriptor) cuadro a cuadro, aplicando el metodo de disposicion
 * del cuadro ANTERIOR antes de dibujar el actual, y preserva la transparencia real (GIF
 * transparentColorFlag, ya reflejada por BufferedImage.getRGB() a traves del ColorModel) para que
 * las zonas sin contenido queden con alpha=0 en vez de un color solido.
 */
public final class GifDecoder {

    private static final float RETARDO_POR_DEFECTO = 0.1f;
    private static final int DISPOSAL_SIN_DISPOSICION = 0;
    private static final int DISPOSAL_RESTAURAR_FONDO = 1;

    public static final class Cuadro {
        public final Pixmap pixmap;
        public final float duracionSegundos;

        Cuadro(Pixmap pixmap, float duracionSegundos) {
            this.pixmap = pixmap;
            this.duracionSegundos = duracionSegundos;
        }
    }

    private static final class InfoCuadro {
        final int left;
        final int top;
        final int disposal;
        final float duracionSegundos;

        InfoCuadro(int left, int top, int disposal, float duracionSegundos) {
            this.left = left;
            this.top = top;
            this.disposal = disposal;
            this.duracionSegundos = duracionSegundos;
        }
    }

    private GifDecoder() {
    }

    public static Array<Cuadro> decodificar(FileHandle archivo) {
        return decodificar(archivo, false);
    }

    /**
     * @param clavePorLuminancia si es true, convierte el fondo NEGRO OPACO de cada cuadro en
     * transparente (alpha = luminancia del pixel: negro puro -> alpha 0, blanco -> alpha completo)
     * DESPUES de componer el canvas real. Investigado 2026-08-11 (pedido del usuario: "sin un
     * rectangulo/fondo visible"): se confirmo con una prueba real fuera del juego (lectura directa
     * de pixeles) que los cuadros de contenido real de ItsMe.gif (los de 1600x900, con el texto
     * "IT'S ME"/"SOY YO") tienen un fondo NEGRO COMPLETAMENTE OPACO (alpha=255) horneado en el
     * propio asset -- no es un bug de transparencia mal leida, es como se exporto el GIF. Para que
     * el efecto se vea "integrado sobre la escena" como pidio el usuario, en vez de un rectangulo
     * solido, se aplica esta clave de luminancia (tecnica estandar para GIFs de texto blanco sobre
     * negro pensados para superponerse sobre otro contenido) -- SOLO cuando se pide explicitamente
     * (Static.gif sigue usando el decodificador de 1 argumento, sin esto: aplicarlo a ruido de TV
     * le abriria agujeros de transparencia arbitrarios en todo el cuadro, rompiendo ese efecto).
     */
    public static Array<Cuadro> decodificar(FileHandle archivo, boolean clavePorLuminancia) {
        Array<Cuadro> cuadros = new Array<>();
        try (ImageInputStream iis = ImageIO.createImageInputStream(archivo.read())) {
            Iterator<ImageReader> lectores = ImageIO.getImageReadersBySuffix("gif");
            if (!lectores.hasNext()) {
                throw new GdxRuntimeException("Este JDK no tiene un lector de GIF disponible via ImageIO");
            }
            ImageReader lector = lectores.next();
            lector.setInput(iis);
            int numCuadros = lector.getNumImages(true);

            int[] canvasDim = leerDimensionesCanvas(lector);
            Pixmap canvas = new Pixmap(canvasDim[0], canvasDim[1], Pixmap.Format.RGBA8888);
            canvas.setBlending(Pixmap.Blending.None);
            canvas.setColor(0f, 0f, 0f, 0f);
            canvas.fill();

            int disposalAnterior = DISPOSAL_SIN_DISPOSICION;
            int[] regionAnterior = null;

            for (int i = 0; i < numCuadros; i++) {
                BufferedImage imagenLocal = lector.read(i);
                InfoCuadro info = leerInfoCuadro(lector, i);

                // Aplica la disposicion del cuadro ANTERIOR antes de dibujar este -- asi es como
                // funciona realmente la composicion de GIF (la disposicion describe que pasa con
                // la region del cuadro anterior, no del actual).
                if (disposalAnterior == DISPOSAL_RESTAURAR_FONDO && regionAnterior != null) {
                    canvas.setBlending(Pixmap.Blending.None);
                    canvas.setColor(0f, 0f, 0f, 0f);
                    canvas.fillRectangle(regionAnterior[0], regionAnterior[1], regionAnterior[2], regionAnterior[3]);
                }

                Pixmap cuadroLocal = convertirAPixmap(imagenLocal);
                canvas.setBlending(Pixmap.Blending.SourceOver);
                canvas.drawPixmap(cuadroLocal, info.left, info.top);
                cuadroLocal.dispose();

                // Copia real del estado actual del canvas -- el canvas sigue mutando en las
                // siguientes iteraciones, cada cuadro necesita su propia instantanea independiente.
                Pixmap instantanea = new Pixmap(canvas.getWidth(), canvas.getHeight(), Pixmap.Format.RGBA8888);
                instantanea.setBlending(Pixmap.Blending.None);
                instantanea.drawPixmap(canvas, 0, 0);
                if (clavePorLuminancia) {
                    aplicarClavePorLuminancia(instantanea);
                }
                cuadros.add(new Cuadro(instantanea, info.duracionSegundos));

                disposalAnterior = info.disposal;
                regionAnterior = new int[]{info.left, info.top, imagenLocal.getWidth(), imagenLocal.getHeight()};
            }
            canvas.dispose();
            lector.dispose();
        } catch (IOException e) {
            throw new GdxRuntimeException("Error decodificando GIF: " + archivo.path(), e);
        }
        if (cuadros.isEmpty()) {
            throw new GdxRuntimeException("El GIF no tiene ningun cuadro: " + archivo.path());
        }
        return cuadros;
    }

    /** Tamaño real del canvas (LogicalScreenDescriptor) -- NO el tamaño del primer cuadro, que
     * puede ser mas chico si el GIF esta optimizado (ver comentario de la clase). */
    private static int[] leerDimensionesCanvas(ImageReader lector) throws IOException {
        IIOMetadata streamMeta = lector.getStreamMetadata();
        if (streamMeta != null) {
            Node raiz = streamMeta.getAsTree(streamMeta.getMetadataFormatNames()[0]);
            NodeList hijos = raiz.getChildNodes();
            for (int j = 0; j < hijos.getLength(); j++) {
                Node nodo = hijos.item(j);
                if ("LogicalScreenDescriptor".equals(nodo.getNodeName())) {
                    NamedNodeMap attrs = nodo.getAttributes();
                    int w = Integer.parseInt(attrs.getNamedItem("logicalScreenWidth").getNodeValue());
                    int h = Integer.parseInt(attrs.getNamedItem("logicalScreenHeight").getNodeValue());
                    return new int[]{w, h};
                }
            }
        }
        // Respaldo (no deberia pasar en un GIF real): usa el tamaño del primer cuadro.
        BufferedImage primero = lector.read(0);
        return new int[]{primero.getWidth(), primero.getHeight()};
    }

    /** Lee offset, metodo de disposicion y duracion de un cuadro desde su metadata real. */
    private static InfoCuadro leerInfoCuadro(ImageReader lector, int indice) throws IOException {
        IIOMetadata metadata = lector.getImageMetadata(indice);
        Node raiz = metadata.getAsTree(metadata.getMetadataFormatNames()[0]);
        NodeList hijos = raiz.getChildNodes();
        int left = 0;
        int top = 0;
        int disposal = DISPOSAL_SIN_DISPOSICION;
        float duracion = RETARDO_POR_DEFECTO;
        for (int j = 0; j < hijos.getLength(); j++) {
            Node nodo = hijos.item(j);
            if ("ImageDescriptor".equals(nodo.getNodeName())) {
                NamedNodeMap attrs = nodo.getAttributes();
                left = Integer.parseInt(attrs.getNamedItem("imageLeftPosition").getNodeValue());
                top = Integer.parseInt(attrs.getNamedItem("imageTopPosition").getNodeValue());
            } else if ("GraphicControlExtension".equals(nodo.getNodeName())) {
                NamedNodeMap attrs = nodo.getAttributes();
                Node delayNodo = attrs.getNamedItem("delayTime");
                if (delayNodo != null) {
                    int centisegundos = Integer.parseInt(delayNodo.getNodeValue());
                    duracion = centisegundos <= 0 ? RETARDO_POR_DEFECTO : centisegundos / 100f;
                }
                Node disposalNodo = attrs.getNamedItem("disposalMethod");
                if (disposalNodo != null && "restoreToBackgroundColor".equals(disposalNodo.getNodeValue())) {
                    disposal = DISPOSAL_RESTAURAR_FONDO;
                }
            }
        }
        return new InfoCuadro(left, top, disposal, duracion);
    }

    /** Convierte el fondo negro opaco en transparente usando la luminancia de cada pixel como
     * alpha -- negro puro (luminancia 0) queda con alpha 0, blanco puro mantiene su alpha
     * original, y los bordes anti-aliasing del texto (grises intermedios) quedan con transparencia
     * parcial en vez de un corte duro. Opera directamente sobre el ByteBuffer RGBA8888 del Pixmap
     * (mas rapido que getPixel/setPixel pixel a pixel para un cuadro de 1600x900). */
    private static void aplicarClavePorLuminancia(Pixmap pixmap) {
        java.nio.ByteBuffer buffer = pixmap.getPixels();
        int totalPixeles = pixmap.getWidth() * pixmap.getHeight();
        for (int i = 0; i < totalPixeles; i++) {
            int base = i * 4;
            int r = buffer.get(base) & 0xFF;
            int g = buffer.get(base + 1) & 0xFF;
            int b = buffer.get(base + 2) & 0xFF;
            int a = buffer.get(base + 3) & 0xFF;
            int luminancia = Math.round(0.2126f * r + 0.7152f * g + 0.0722f * b);
            int nuevaAlpha = Math.min(a, luminancia);
            buffer.put(base + 3, (byte) nuevaAlpha);
        }
        buffer.rewind();
    }

    /** Convierte el BufferedImage LOCAL de un cuadro (posiblemente mas chico que el canvas) a un
     * Pixmap -- reutiliza el decodificador PNG nativo de libGDX en vez de copiar pixeles a mano,
     * preservando la transparencia real via el tRNS chunk que ImageIO.write ya genera a partir
     * del ColorModel indexado del GIF. */
    private static Pixmap convertirAPixmap(BufferedImage imagen) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", bytes);
        byte[] datos = bytes.toByteArray();
        return new Pixmap(datos, 0, datos.length);
    }
}
