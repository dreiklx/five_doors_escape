package com.fivedoorsescape.audio;

import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.Queue;

/**
 * Un Sound de libGDX con el volumen de su categoria aplicado (VolumenAudio, CLAUDE.md §2.75). El juego sigue pidiendo
 * volumenes "de diseno" (0..1) exactamente como antes; este envoltorio los multiplica por la ganancia del menu y
 * recuerda el volumen y el paneo pedidos de cada instancia reciente para reaplicarlos en vivo cuando el jugador mueve
 * un deslizador (un bucle como la caja musical o el ambiente cambia al instante, sin reiniciarse).
 *
 * dispose() no libera el Sound real: es del AssetManager (o de quien lo creo), igual que antes de envolverlo.
 */
public class SonidoConVolumen implements Sound {

    private static final int MAX_RECORDADOS = 48;

    private final Sound real;
    private final VolumenAudio.Categoria categoria;
    private final LongMap<float[]> pedidos = new LongMap<>();   // id -> {volumen, paneo, 1 si se pidio paneo}
    private final Queue<Long> orden = new Queue<>();

    public SonidoConVolumen(Sound real, VolumenAudio.Categoria categoria) {
        this.real = real;
        this.categoria = categoria;
        VolumenAudio.registrar(this);
    }

    public Sound getReal() {
        return real;
    }

    private float g() {
        return VolumenAudio.ganancia(categoria);
    }

    private long recordar(long id, float volumen, float pan, boolean conPan) {
        if (id == -1) {
            return id;
        }
        pedidos.put(id, new float[] { volumen, pan, conPan ? 1f : 0f });
        orden.addLast(id);
        while (orden.size > MAX_RECORDADOS) {
            pedidos.remove(orden.removeFirst());
        }
        return id;
    }

    void reaplicar() {
        float g = g();
        for (LongMap.Entry<float[]> e : pedidos) {
            // sin paneo pedido se reaplica solo el volumen: setPan tambien mueve la fuente en OpenAL
            if (e.value[2] > 0f) {
                real.setPan(e.key, e.value[1], e.value[0] * g);
            } else {
                real.setVolume(e.key, e.value[0] * g);
            }
        }
    }

    @Override public long play() { return play(1f); }
    @Override public long play(float volume) { return recordar(real.play(volume * g()), volume, 0f, false); }
    @Override public long play(float volume, float pitch, float pan) { return recordar(real.play(volume * g(), pitch, pan), volume, pan, true); }
    @Override public long loop() { return loop(1f); }
    @Override public long loop(float volume) { return recordar(real.loop(volume * g()), volume, 0f, false); }
    @Override public long loop(float volume, float pitch, float pan) { return recordar(real.loop(volume * g(), pitch, pan), volume, pan, true); }
    @Override public void stop() { real.stop(); }
    @Override public void pause() { real.pause(); }
    @Override public void resume() { real.resume(); }
    @Override public void stop(long soundId) { real.stop(soundId); }
    @Override public void pause(long soundId) { real.pause(soundId); }
    @Override public void resume(long soundId) { real.resume(soundId); }
    @Override public void setLooping(long soundId, boolean looping) { real.setLooping(soundId, looping); }
    @Override public void setPitch(long soundId, float pitch) { real.setPitch(soundId, pitch); }

    @Override
    public void setVolume(long soundId, float volume) {
        float[] p = pedidos.get(soundId);
        if (p != null) {
            p[0] = volume;
        }
        real.setVolume(soundId, volume * g());
    }

    @Override
    public void setPan(long soundId, float pan, float volume) {
        float[] p = pedidos.get(soundId);
        if (p != null) {
            p[0] = volume;
            p[1] = pan;
            p[2] = 1f;
        }
        real.setPan(soundId, pan, volume * g());
    }

    /** Deja de seguir el volumen (no libera el Sound real, que no es de este envoltorio). */
    @Override
    public void dispose() {
        VolumenAudio.olvidar(this);
        pedidos.clear();
        orden.clear();
    }
}
