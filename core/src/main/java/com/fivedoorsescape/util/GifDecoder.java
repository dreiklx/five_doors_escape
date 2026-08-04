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
 * esto evita agregar una libreria externa nueva solo para reproducir la estatica existente.
 */
public final class GifDecoder {

    /** Duracion por defecto (segundos) si el GIF no trae metadata de retardo para un cuadro. */
    private static final float RETARDO_POR_DEFECTO = 0.1f;

    public static final class Cuadro {
        public final Pixmap pixmap;
        public final float duracionSegundos;

        Cuadro(Pixmap pixmap, float duracionSegundos) {
            this.pixmap = pixmap;
            this.duracionSegundos = duracionSegundos;
        }
    }

    private GifDecoder() {
    }

    public static Array<Cuadro> decodificar(FileHandle archivo) {
        Array<Cuadro> cuadros = new Array<>();
        try (ImageInputStream iis = ImageIO.createImageInputStream(archivo.read())) {
            Iterator<ImageReader> lectores = ImageIO.getImageReadersBySuffix("gif");
            if (!lectores.hasNext()) {
                throw new GdxRuntimeException("Este JDK no tiene un lector de GIF disponible via ImageIO");
            }
            ImageReader lector = lectores.next();
            lector.setInput(iis);
            int numCuadros = lector.getNumImages(true);
            for (int i = 0; i < numCuadros; i++) {
                BufferedImage imagen = lector.read(i);
                float duracion = leerDuracionCuadro(lector, i);
                cuadros.add(new Cuadro(convertirAPixmap(imagen), duracion));
            }
            lector.dispose();
        } catch (IOException e) {
            throw new GdxRuntimeException("Error decodificando GIF: " + archivo.path(), e);
        }
        if (cuadros.isEmpty()) {
            throw new GdxRuntimeException("El GIF no tiene ningun cuadro: " + archivo.path());
        }
        return cuadros;
    }

    /** Lee delayTime (centisegundos) de la extension GraphicControlExtension del cuadro, si existe. */
    private static float leerDuracionCuadro(ImageReader lector, int indice) {
        try {
            IIOMetadata metadata = lector.getImageMetadata(indice);
            Node raiz = metadata.getAsTree(metadata.getMetadataFormatNames()[0]);
            NodeList hijos = raiz.getChildNodes();
            for (int j = 0; j < hijos.getLength(); j++) {
                Node nodo = hijos.item(j);
                if ("GraphicControlExtension".equals(nodo.getNodeName())) {
                    NamedNodeMap atributos = nodo.getAttributes();
                    Node delayNodo = atributos.getNamedItem("delayTime");
                    if (delayNodo != null) {
                        int centisegundos = Integer.parseInt(delayNodo.getNodeValue());
                        return centisegundos <= 0 ? RETARDO_POR_DEFECTO : centisegundos / 100f;
                    }
                }
            }
        } catch (Exception ignorada) {
            // Sin metadata de retardo para este cuadro -- se usa el valor por defecto.
        }
        return RETARDO_POR_DEFECTO;
    }

    /** Reutiliza el decodificador PNG nativo de libGDX en vez de copiar pixeles a mano. */
    private static Pixmap convertirAPixmap(BufferedImage imagen) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", bytes);
        byte[] datos = bytes.toByteArray();
        return new Pixmap(datos, 0, datos.length);
    }
}
