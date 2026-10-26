# Pasos de los animatronicos, version 2 (CLAUDE.md §2.85; la v1 era §2.73), sintetizados aqui sin audio externo.
# Objetivo: METAL PESADO golpeando el suelo, con masa, no una lata. Lo que suena "a lata" son parciales agudos que
# resuenan mucho rato; aqui el metal es GRAVE y muy amortiguado, y nada por encima de 2 kHz dura mas de ~40 ms.
# Capas de cada pisada:
#   1. golpe sub (45-60 Hz) con caida de tono y saturacion suave: la masa del cuerpo;
#   2. cuerpo metalico: modos inarmonicos de una placa gruesa de acero (proporciones de placa) sobre una base grave
#      (135-240 Hz segun el personaje), excitados por un impulso; los modos altos se apagan antes que los bajos;
#   3. contacto: un chasquido de banda media (0.6-3 kHz) de menos de 15 ms, la "definicion" del impacto;
#   4. mecanica secundaria: traqueteo de articulaciones (micro golpes 15-110 ms despues) y servo, filtrados;
#   5. cola corta: sala (interior) o rebote abierto (estacionamiento), filtrada por debajo de ~1.5 kHz para que no
#      quede brillo.
# Una compresion suave sube el cuerpo del sonido (peso percibido) sin subir el pico, normalizado a -1 dBFS.
# Cuatro variaciones por personaje con semilla fija. Mismos nombres de archivo que la v1: el juego no cambia.
# Uso: python synth_animatronic_steps.py <carpeta assets de Escape>
import os
import sys
import wave

import numpy as np
from scipy import signal

SR = 44100
OUT = os.path.join(sys.argv[1], 'audio', 'sfx', 'steps')
os.makedirs(OUT, exist_ok=True)
# proporciones de los modos de una placa cuadrada simplemente apoyada (m^2 + n^2 normalizado): metal "macizo"
MODOS_PLACA = [1.00, 2.50, 4.00, 5.00, 6.50, 8.50, 9.00, 10.00]


def t_de(dur):
    return np.arange(int(dur * SR)) / SR


def filtro(x, tipo, f, orden=2):
    return signal.sosfilt(signal.butter(orden, f, btype=tipo, fs=SR, output='sos'), x)


def env(t, ataque, caida):
    e = np.exp(-t / caida)
    a = int(ataque * SR)
    if a > 0:
        e[:a] *= np.linspace(0, 1, a)
    return e


def golpe_sub(t, f0, caida, barrido):
    f = f0 * (1 + barrido * np.exp(-t / 0.03))
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(t, 0.0015, caida)
    return np.tanh(2.2 * x) / np.tanh(2.2)


def cuerpo_metalico(t, rng, base, caida_base, dispersion=0.03, n=8, brillo=0.55):
    # cada modo: seno amortiguado con fase aleatoria y una leve desafinacion (variacion natural); la caida se acorta
    # con la frecuencia (los modos altos se apagan antes: acero grueso, no lamina)
    x = np.zeros(len(t))
    for k, r in enumerate(MODOS_PLACA[:n]):
        f = base * r * (1 + rng.uniform(-dispersion, dispersion))
        if f > 2600:
            break
        caida = caida_base * (base / f) ** 0.7
        amp = brillo ** k * rng.uniform(0.7, 1.0)
        x += amp * np.sin(2 * np.pi * f * t + rng.uniform(0, 6.28)) * env(t, 0.0008, caida)
    return x / (np.abs(x).max() + 1e-9)


def contacto(t, rng, lo, hi, caida=0.008):
    r = filtro(rng.standard_normal(len(t)), 'bandpass', [lo, hi], 2) * env(t, 0.0003, caida)
    return r / (np.abs(r).max() + 1e-9)


def traqueteo(t, rng, retardos, fc, amp):
    x = np.zeros(len(t))
    for d in retardos:
        n0 = int(d * SR)
        tt = t[n0:] - d
        r = filtro(rng.standard_normal(len(tt)), 'bandpass', [fc * 0.6, fc * 1.5], 2) * np.exp(-tt / 0.006)
        x[n0:] += rng.uniform(0.5, 1.0) * r / (np.abs(r).max() + 1e-9)
    return amp * filtro(x, 'lowpass', 2200, 2)


def servo(t, inicio, dur, f_ini, f_fin, amp, fc=1200):
    x = np.zeros(len(t))
    m = (t >= inicio) & (t < inicio + dur)
    ts = t[m] - inicio
    f = f_ini + (f_fin - f_ini) * ts / dur
    fase = 2 * np.pi * np.cumsum(f) / SR
    x[m] = (2 * ((fase / (2 * np.pi)) % 1) - 1) * np.sin(np.pi * ts / dur) ** 2
    return amp * filtro(x, 'lowpass', fc, 4)


def cola(x, rng, dur, caida, fc, mezcla, predelay=0.012):
    n = int(dur * SR)
    tt = np.arange(n) / SR
    ir = filtro(rng.standard_normal(n), 'lowpass', fc, 2) * np.exp(-tt / caida)
    ir[:int(predelay * SR)] = 0
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    mojado = signal.fftconvolve(x, ir)[:len(x) + n]
    mojado = np.pad(mojado, (0, len(x) + n - len(mojado)))
    seco = np.concatenate([x, np.zeros(n)])
    mojado *= np.abs(seco).max() / (np.abs(mojado).max() + 1e-9)
    return seco + mezcla * mojado


def comprimir(x, umbral=0.35, razon=3.0):
    # compresion suave por envolvente: mas cuerpo (peso) sin cambiar el pico final
    e = filtro(np.abs(x), 'lowpass', 40, 1) + 1e-9
    g = np.where(e > umbral, (umbral + (e - umbral) / razon) / e, 1.0)
    return x * g


def escribir(nombre, x):
    e = np.abs(signal.hilbert(x))
    sobre = np.nonzero(e > np.abs(x).max() * 1e-3)[0]
    x = x[:min(len(x), sobre[-1] + int(0.05 * SR))].copy()
    cola_ = int(0.03 * SR)
    x[-cola_:] *= np.linspace(1, 0, cola_)
    x = filtro(x, 'highpass', 30, 2)
    x = comprimir(x / np.abs(x).max())
    x = x * (10 ** (-1.0 / 20)) / np.abs(x).max()
    p = os.path.join(OUT, nombre)
    w = wave.open(p, 'wb')
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
    w.close()
    return p


def pisada(rng, t, sub, base, caida_metal, metal_amp, contacto_amp, contacto_banda, traq, serv, colap, brillo=0.7):
    x = 1.0 * golpe_sub(t, rng.uniform(*sub[0]), rng.uniform(*sub[1]), rng.uniform(0.6, 1.0))
    x += metal_amp * cuerpo_metalico(t, rng, rng.uniform(*base), rng.uniform(*caida_metal), brillo=brillo)
    x += contacto_amp * contacto(t, rng, *contacto_banda)
    if traq:
        x += traqueteo(t, rng, [rng.uniform(*d) for d in traq[0]], traq[1], traq[2])
    if serv:
        x += servo(t, rng.uniform(*serv[0]), serv[1], serv[2], serv[3], serv[4], serv[5])
    # el ultimo cuarto de la senal seca se desvanece: el modo grave del acero todavia suena ahi y cortarlo de golpe
    # antes de la cola producia un chasquido (visible como una raya vertical en el espectrograma)
    n = len(x)
    i0 = int(n * 0.75)
    x[i0:] *= 0.5 * (1 + np.cos(np.linspace(0, np.pi, n - i0)))
    return cola(x, rng, *colap)


def freddy_chase(rng):
    # Freddy corriendo sobre asfalto: el golpe mas pesado, acero grave, contacto aspero y rebote abierto
    t = t_de(0.5)
    return pisada(rng, t, ((44, 50), (0.09, 0.12)), (140, 158), (0.16, 0.22), 0.85, 0.30, (700, 2600),
                  (((0.03, 0.045), (0.07, 0.09)), 900, 0.10), None, (0.25, 0.06, 1400, 0.16, 0.02), brillo=0.72)


def freddy(rng):
    # Freddy dentro: igual de macizo pero mas lento y con la sala de la pizzeria
    t = t_de(0.6)
    return pisada(rng, t, ((42, 48), (0.11, 0.14)), (135, 150), (0.18, 0.25), 0.80, 0.24, (600, 2200),
                  (((0.035, 0.05), (0.08, 0.1)), 800, 0.09), ((0.09, 0.12), 0.2, 95, 140, 0.035, 700),
                  (0.45, 0.13, 1500, 0.30), brillo=0.70)


def bonnie(rng):
    # Bonnie: mecanico; acero un poco mas agudo, traqueteo y servo bien presentes (filtrados)
    t = t_de(0.55)
    return pisada(rng, t, ((54, 62), (0.08, 0.10)), (185, 205), (0.12, 0.16), 0.85, 0.32, (900, 2800),
                  (((0.02, 0.03), (0.05, 0.065), (0.09, 0.11)), 1100, 0.20),
                  ((0.04, 0.06), 0.17, 180, 290, 0.10, 1500), (0.42, 0.11, 1500, 0.28), brillo=0.76)


def chica(rng):
    # Chica: pesada y hueca; cuerpo metalico mas largo y un modo grave de armazon hueco
    t = t_de(0.7)
    x = pisada(rng, t, ((48, 55), (0.10, 0.13)), (160, 175), (0.22, 0.30), 0.90, 0.22, (600, 2000),
               (((0.04, 0.06),), 800, 0.07), ((0.1, 0.14), 0.2, 120, 165, 0.03, 800), (0.5, 0.15, 1400, 0.33),
               brillo=0.66)
    th = t_de(len(x) / SR)
    return x + 0.35 * np.sin(2 * np.pi * rng.uniform(108, 118) * th) * env(th, 0.002, 0.18)


def foxy(rng):
    # Foxy: urgente; el acero mas agudo y mas seco, contacto con raspado, poca cola (cadencia rapida)
    t = t_de(0.4)
    return pisada(rng, t, ((62, 72), (0.05, 0.07)), (220, 240), (0.08, 0.11), 0.80, 0.38, (1000, 3000),
                  (((0.015, 0.025), (0.04, 0.055)), 1300, 0.14), ((0.025, 0.04), 0.09, 230, 360, 0.07, 1600),
                  (0.32, 0.08, 1500, 0.22), brillo=0.78)


PERSONAJES = {'freddy_chase': (freddy_chase, 2111), 'freddy': (freddy, 1987), 'bonnie': (bonnie, 1993),
              'chica': (chica, 2014), 'foxy': (foxy, 2015)}

if __name__ == '__main__':
    for nombre, (fn, semilla) in PERSONAJES.items():
        rng = np.random.default_rng(semilla)
        for i in range(1, 5):
            print('escrito', escribir('%s_step_%d.wav' % (nombre, i), fn(rng)))
