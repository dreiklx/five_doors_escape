package com.fivedoorsescape.util;

import java.nio.charset.StandardCharsets;

import com.badlogic.gdx.files.FileHandle;

/**
 * Lee la duracion REAL (en segundos) de un archivo WAV PCM parseando directamente sus chunks
 * RIFF/fmt/data -- sin decodificar el audio completo, solo el header. Mismo principio que
 * Clip.getMicrosecondLength() del lado Swing (ver ControllerInterfaz.iniciarLlamada en
 * FiveDoorsAtFreddys, que tampoco hardcodea duraciones de llamada): nunca asumir cuanto dura un
 * audio, leerlo siempre del archivo real para que el codigo se adapte solo si el audio cambia.
 *
 * Usado para la cinematica inicial del modo Escape (pedido explicito del usuario 2026-08-05: la
 * cinematica debe durar exactamente lo mismo que su audio narrativo, sin un numero hardcodeado
 * que se desincronice si el audio cambia).
 */
public final class WavDuration {

    private WavDuration() {
    }

    /** Duracion real en segundos, o respaldoSegundos si el archivo no es un WAV PCM valido, esta
     * truncado, o no se puede leer -- mismo patron de respaldo que el lado Swing usa cuando
     * getDuracionMs() &lt;= 0. */
    public static float leerSegundos(FileHandle archivo, float respaldoSegundos) {
        try {
            byte[] datos = archivo.readBytes();
            if (datos.length < 44 || !esRiffWave(datos)) {
                return respaldoSegundos;
            }
            int pos = 12;
            long byteRate = 0;
            while (pos + 8 <= datos.length) {
                String idChunk = new String(datos, pos, 4, StandardCharsets.US_ASCII);
                long tamanoChunk = leerUInt32LE(datos, pos + 4);
                if ("fmt ".equals(idChunk) && pos + 24 <= datos.length) {
                    byteRate = leerUInt32LE(datos, pos + 16);
                } else if ("data".equals(idChunk)) {
                    if (byteRate <= 0) {
                        return respaldoSegundos;
                    }
                    return tamanoChunk / (float) byteRate;
                }
                pos += 8 + (int) tamanoChunk + (int) (tamanoChunk % 2);
            }
        } catch (Exception e) {
            // Se ignora -- se usa el respaldo, igual que el lado Swing con getDuracionMs() <= 0.
        }
        return respaldoSegundos;
    }

    private static boolean esRiffWave(byte[] datos) {
        return datos[0] == 'R' && datos[1] == 'I' && datos[2] == 'F' && datos[3] == 'F'
                && datos[8] == 'W' && datos[9] == 'A' && datos[10] == 'V' && datos[11] == 'E';
    }

    private static long leerUInt32LE(byte[] datos, int offset) {
        return (datos[offset] & 0xFFL)
                | ((datos[offset + 1] & 0xFFL) << 8)
                | ((datos[offset + 2] & 0xFFL) << 16)
                | ((datos[offset + 3] & 0xFFL) << 24);
    }
}
