# Pasos de los animatronicos (CLAUDE.md §2.73), sintetizados aqui sin audio externo. Reemplazan al antiguo
# freddy_run_step.wav (un golpe con chasquido metalico agudo que sonaba a lata). Cada pisada se arma por capas:
#   1. impacto grave: seno con caida de tono (el peso del cuerpo) mas ruido muy filtrado (el "pum" sordo);
#   2. cuerpo estructural: resonancias graves y medias del suelo o del armazon, segun el personaje;
#   3. componente mecanica secundaria: servo o articulacion, baja y filtrada (nunca protagonista);
#   4. reverberacion corta: sala de la pizzeria (interior) o rebote abierto (estacionamiento).
# Cuatro variaciones por personaje (parametros aleatorios con semilla fija), para que dos pisadas seguidas nunca
# suenen identicas. El juego ademas varia tono y volumen en cada pisada (PasosAnimatronicos).
# WAV PCM 16 bit 44.1 kHz mono, pico a -1 dBFS.
# Uso: python synth_animatronic_steps.py <carpeta assets de Escape>
import os
import sys
import wave

import numpy as np
from scipy import signal

SR = 44100
OUT = os.path.join(sys.argv[1], 'audio', 'sfx', 'steps')
os.makedirs(OUT, exist_ok=True)


def t_de(dur):
    return np.arange(int(dur * SR)) / SR


def filtro(x, tipo, f, orden=2):
    sos = signal.butter(orden, f, btype=tipo, fs=SR, output='sos')
    return signal.sosfilt(sos, x)


def env(t, ataque, caida):
    e = np.exp(-t / caida)
    a = int(ataque * SR)
    if a > 0:
        e[:a] *= np.linspace(0, 1, a)
    return e


def impacto(t, rng, f0, caida, ruido_fc, ruido_amp, barrido):
    # el tono baja de f0*(1+barrido) a f0 en los primeros 40 ms: el "golpe" que se hunde
    f = f0 * (1 + barrido * np.exp(-t / 0.04))
    fase = 2 * np.pi * np.cumsum(f) / SR
    tono = np.sin(fase) * env(t, 0.002, caida)
    # saturacion suave: agrega armonicos (2f, 3f) para que el peso se oiga tambien en altavoces sin graves
    tono = np.tanh(2.4 * tono) / np.tanh(2.4)
    ruido = filtro(rng.standard_normal(len(t)), 'lowpass', ruido_fc, 4) * env(t, 0.001, caida * 0.6)
    return tono + ruido_amp * ruido / (np.abs(ruido).max() + 1e-9)


def resonancias(t, rng, modos, retardo=0.004):
    # modos: (frecuencia, amplitud, caida) -- ruido excitando filtros resonantes (suena a material, no a seno puro)
    x = np.zeros(len(t))
    exc = rng.standard_normal(len(t)) * env(t, 0.0005, 0.012)
    for f, a, d in modos:
        q = np.pi * f * d               # Q aproximado para esa caida
        b, aa = signal.iirpeak(f, max(q, 0.7), fs=SR)
        y = signal.lfilter(b, aa, exc)
        x += a * y / (np.abs(y).max() + 1e-9)
    n = int(retardo * SR)
    return np.concatenate([np.zeros(n), x[:len(x) - n]])


def servo(t, rng, inicio, dur, f_ini, f_fin, amp, fc=1400):
    x = np.zeros(len(t))
    m = (t >= inicio) & (t < inicio + dur)
    ts = t[m] - inicio
    f = f_ini + (f_fin - f_ini) * ts / dur
    fase = 2 * np.pi * np.cumsum(f) / SR
    diente = 2 * ((fase / (2 * np.pi)) % 1) - 1
    x[m] = diente * np.sin(np.pi * ts / dur) ** 2
    return amp * filtro(x, 'lowpass', fc, 4)


def chasquido(t, rng, inicio, fc, amp, caida=0.012):
    # contacto de articulacion: ruido corto pasabanda, grave-medio (no un "ting" agudo)
    x = np.zeros(len(t))
    n0 = int(inicio * SR)
    tt = t[n0:] - inicio
    r = filtro(rng.standard_normal(len(tt)), 'bandpass', [fc * 0.6, fc * 1.4], 2) * np.exp(-tt / caida)
    x[n0:] = r / (np.abs(r).max() + 1e-9)
    return amp * x


def roce(t, rng, inicio, dur, banda, amp):
    # arrastre/raspado contra el suelo (asfalto, baldosa)
    x = np.zeros(len(t))
    m = (t >= inicio) & (t < inicio + dur)
    ts = t[m] - inicio
    r = filtro(rng.standard_normal(m.sum()), 'bandpass', banda, 2)
    x[m] = r / (np.abs(r).max() + 1e-9) * np.sin(np.pi * ts / dur) ** 1.5
    return amp * x


def reverb(x, rng, dur, caida, fc, mezcla, predelay=0.012):
    n = int(dur * SR)
    tt = np.arange(n) / SR
    ir = rng.standard_normal(n) * np.exp(-tt / caida)
    ir = filtro(ir, 'lowpass', fc, 2)
    ir[:int(predelay * SR)] = 0
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    mojado = signal.fftconvolve(x, ir)[:len(x) + n]
    mojado = np.pad(mojado, (0, len(x) + n - len(mojado)))
    seco = np.concatenate([x, np.zeros(n)])
    mojado *= np.abs(seco).max() / (np.abs(mojado).max() + 1e-9)
    return seco + mezcla * mojado


def escribir(nombre, x):
    # recorta la cola de reverberacion por debajo de -60 dB respecto del pico (silencio que solo ocuparia disco)
    e = np.abs(signal.hilbert(x))
    sobre = np.nonzero(e > np.abs(x).max() * 1e-3)[0]
    x = x[:min(len(x), sobre[-1] + int(0.05 * SR))]
    cola = int(0.03 * SR)
    x = x.copy()
    x[-cola:] *= np.linspace(1, 0, cola)
    x = filtro(x, 'highpass', 28, 2)              # sin subgraves inutiles (DC, rumble)
    x = x * (10 ** (-1.0 / 20)) / np.abs(x).max()
    p = os.path.join(OUT, nombre)
    w = wave.open(p, 'wb')
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
    w.close()
    return p


def freddy_chase(rng):
    # Freddy corriendo sobre asfalto: muy pesado, sordo, con el roce aspero del pie; casi nada de metal.
    t = t_de(0.55)
    x = 0.7 * impacto(t, rng, rng.uniform(44, 52), rng.uniform(0.085, 0.11), 180, 1.1, rng.uniform(0.7, 1.0))
    x += 1.4 * resonancias(t, rng, [(rng.uniform(95, 110), 1.0, 0.07), (rng.uniform(150, 175), 0.6, 0.05),
                                     (rng.uniform(240, 280), 0.3, 0.03)])
    x += 0.10 * roce(t, rng, 0.004, rng.uniform(0.07, 0.1), [350, 1600], 1.0)
    x += 0.05 * chasquido(t, rng, rng.uniform(0.018, 0.03), rng.uniform(600, 800), 1.0, 0.015)
    x += servo(t, rng, rng.uniform(0.07, 0.1), 0.16, 110, 160, 0.035, 900)
    return reverb(x, rng, 0.22, 0.05, 2500, 0.18, 0.025)


def freddy(rng):
    # Freddy caminando dentro: grave y lento, el suelo de la pizzeria vibra; un servo apenas audible.
    t = t_de(0.6)
    x = 0.7 * impacto(t, rng, rng.uniform(40, 47), rng.uniform(0.11, 0.14), 150, 1.0, rng.uniform(0.5, 0.8))
    x += 1.4 * resonancias(t, rng, [(rng.uniform(80, 92), 1.0, 0.1), (rng.uniform(130, 150), 0.7, 0.07),
                                    (rng.uniform(205, 230), 0.35, 0.05)])
    x += 0.06 * chasquido(t, rng, rng.uniform(0.02, 0.035), rng.uniform(500, 700), 1.0, 0.02)
    x += servo(t, rng, rng.uniform(0.09, 0.13), 0.22, 95, 140, 0.04, 800)
    return reverb(x, rng, 0.5, 0.14, 3000, 0.35)


def bonnie(rng):
    # Bonnie: mecanico. Impacto medio-pesado, la articulacion que encaja y el servo de la pierna bien presentes
    # (filtrados: mecanico, no metalico).
    t = t_de(0.55)
    x = 0.65 * impacto(t, rng, rng.uniform(62, 72), rng.uniform(0.07, 0.09), 260, 0.9, rng.uniform(0.6, 0.9))
    x += 0.9 * resonancias(t, rng, [(rng.uniform(140, 160), 1.0, 0.05), (rng.uniform(310, 350), 0.5, 0.035)])
    x += 0.22 * chasquido(t, rng, rng.uniform(0.012, 0.02), rng.uniform(900, 1150), 1.0, 0.01)
    x += 0.12 * chasquido(t, rng, rng.uniform(0.055, 0.075), rng.uniform(700, 850), 1.0, 0.008)
    x += servo(t, rng, rng.uniform(0.04, 0.06), rng.uniform(0.15, 0.2), rng.uniform(170, 200), rng.uniform(260, 300), 0.11, 1600)
    return reverb(x, rng, 0.45, 0.12, 3200, 0.3)


def chica(rng):
    # Chica: pesada y hueca. El golpe excita el armazon (resonancias graves largas, como un tambor sordo).
    t = t_de(0.7)
    x = 0.75 * impacto(t, rng, rng.uniform(50, 57), rng.uniform(0.1, 0.12), 200, 0.9, rng.uniform(0.6, 0.9))
    x += 1.2 * resonancias(t, rng, [(rng.uniform(170, 190), 1.0, 0.22), (rng.uniform(250, 275), 0.65, 0.18),
                                     (rng.uniform(390, 430), 0.35, 0.12), (rng.uniform(110, 120), 0.5, 0.15)], 0.006)
    x += 0.07 * chasquido(t, rng, rng.uniform(0.02, 0.03), rng.uniform(600, 750), 1.0, 0.015)
    x += servo(t, rng, rng.uniform(0.1, 0.14), 0.2, 120, 165, 0.035, 900)
    return reverb(x, rng, 0.55, 0.16, 2800, 0.38)


def foxy(rng):
    # Foxy: urgente. Mas liviano y seco, ataque rapido, el raspado del pie metalico desgastado y un servo agudo y
    # breve; poca cola, para que la cadencia rapida no se emborrone.
    t = t_de(0.4)
    x = 0.8 * impacto(t, rng, rng.uniform(78, 92), rng.uniform(0.045, 0.06), 320, 0.8, rng.uniform(0.8, 1.1))
    x += 0.35 * resonancias(t, rng, [(rng.uniform(190, 220), 1.0, 0.035), (rng.uniform(420, 470), 0.4, 0.02)])
    x += 0.14 * roce(t, rng, rng.uniform(0.006, 0.012), rng.uniform(0.05, 0.075), [600, 2200], 1.0)
    x += 0.16 * chasquido(t, rng, rng.uniform(0.008, 0.014), rng.uniform(1000, 1250), 1.0, 0.007)
    x += servo(t, rng, rng.uniform(0.025, 0.04), rng.uniform(0.08, 0.11), rng.uniform(230, 260), rng.uniform(330, 380), 0.08, 1800)
    return reverb(x, rng, 0.35, 0.09, 3500, 0.25)


PERSONAJES = {'freddy_chase': (freddy_chase, 2111), 'freddy': (freddy, 1987), 'bonnie': (bonnie, 1993),
              'chica': (chica, 2014), 'foxy': (foxy, 2015)}

if __name__ == '__main__':
    for nombre, (fn, semilla) in PERSONAJES.items():
        rng = np.random.default_rng(semilla)
        for i in range(1, 5):
            p = escribir('%s_step_%d.wav' % (nombre, i), fn(rng))
            print('escrito', p)
