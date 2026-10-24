import com.badlogic.gdx.graphics.Pixmap;
import java.nio.ByteBuffer;

/** Captura sin alfa (CLAUDE.md §2.78, pendiente B5): el framebuffer guarda alfa < 1 donde la escena lo escribe, y un
 * PNG con ese alfa se ve "lavado" o con agujeros en un visor aunque en el juego no pase nada. Pone alfa 255 en un
 * Pixmap RGBA8888 antes de guardarlo. */
public final class Opaco {
  private Opaco() {}
  public static Pixmap aplicar(Pixmap p) {
    if (p.getFormat() != Pixmap.Format.RGBA8888) return p;
    ByteBuffer b = p.getPixels();
    for (int i = 3; i < b.limit(); i += 4) b.put(i, (byte) 0xFF);
    return p;
  }
}
