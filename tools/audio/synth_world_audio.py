# Sonidos del mundo de Five Doors Escape sintetizados aqui (sin audio externo): puertas interiores (abrir/cerrar),
# puerta de seguridad corrediza y el aturdimiento de un animatronico con la linterna. WAV PCM 16 bit 44.1 kHz mono.
# Uso: python synth_world_audio.py <carpeta assets de Escape>
import sys, os, wave
import numpy as np

SR = 44100
OUT = sys.argv[1]
rng = np.random.default_rng(2077)


def env(n, ataque, caida):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(ataque, 1e-4)) * np.exp(-t / caida)


def pasa_bajos(x, corte):
    a = np.exp(-2 * np.pi * corte / SR)
    y = np.empty_like(x); acc = 0.0
    for k in range(len(x)):
        acc = (1 - a) * x[k] + a * acc; y[k] = acc
    return y


def escribir(ruta, datos, techo_db=-1.0):
    datos = datos * (10 ** (techo_db / 20)) / max(np.abs(datos).max(), 1e-9)
    p = os.path.join(OUT, ruta); os.makedirs(os.path.dirname(p), exist_ok=True)
    w = wave.open(p, 'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((np.clip(datos, -1, 1) * 32767).astype(np.int16).tobytes()); w.close()
    print('escrito', ruta, round(len(datos) / SR, 2), 's')


def clic(frecs, dur=0.12, intensidad=1.0):
    n = int(dur * SR); t = np.arange(n) / SR
    s = rng.standard_normal(n) * env(n, 0.0004, 0.004)
    for f, d, a in zip(frecs, (0.03, 0.02, 0.012), (0.5, 0.3, 0.2)):
        s += np.sin(2 * np.pi * f * t) * env(n, 0.0003, d) * a
    return s * intensidad


def chirrido(dur, f0, f1):
    n = int(dur * SR); t = np.arange(n) / SR
    f = np.linspace(f0, f1, n) * (1 + 0.03 * np.sin(2 * np.pi * 6 * t))
    fase = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(fase) + 0.4 * np.sin(2 * fase) + 0.2 * np.sin(3 * fase)
    s *= np.clip(0.5 + 3 * pasa_bajos(rng.standard_normal(n), 20), 0, 1.3)
    return s * np.minimum(1, t / 0.05) * np.minimum(1, (dur - t) / 0.1)


def golpe(frec, dur, caida, intensidad=1.0):
    n = int(dur * SR); t = np.arange(n) / SR
    s = np.sin(2 * np.pi * (frec * np.exp(-t * 3)) * t) * env(n, 0.002, caida)
    s += pasa_bajos(rng.standard_normal(n), 450) * env(n, 0.001, 0.03) * 2
    return s * intensidad


def mezclar(dur, piezas):
    out = np.zeros(int(dur * SR))
    for s, t0, g in piezas:
        i0 = int(t0 * SR); i1 = min(len(out), i0 + len(s)); out[i0:i1] += s[:i1 - i0] * g
    return out


escribir('audio/sfx/interior_door_open.wav', mezclar(0.75, [
    (clic((1800, 3100, 4400)), 0.0, 0.8), (chirrido(0.55, 330, 410) * 0.25, 0.08, 1.0)]))
escribir('audio/sfx/interior_door_close.wav', mezclar(0.7, [
    (chirrido(0.3, 400, 340) * 0.15, 0.0, 1.0), (golpe(70, 0.5, 0.12), 0.24, 1.0), (clic((1500, 2600, 3800)), 0.25, 0.7)]))
# puerta de seguridad: motor electrico + rodamiento metalico + tope
n = int(1.1 * SR); t = np.arange(n) / SR
motor = (np.sin(2 * np.pi * 95 * t) * 0.5 + np.sin(2 * np.pi * 190 * t) * 0.25 + pasa_bajos(rng.standard_normal(n), 1500) * 0.5)
motor *= np.minimum(1, t / 0.06) * np.minimum(1, (0.85 - t) / 0.08).clip(0, 1)
escribir('audio/sfx/security_door_slide.wav', mezclar(1.1, [(motor, 0.0, 0.8), (golpe(110, 0.25, 0.05, 0.8), 0.84, 1.0)]))
# aturdimiento: chispazo electrico, servos que tartamudean y se traban
n = int(0.9 * SR); t = np.arange(n) / SR
zap = rng.standard_normal(n) * env(n, 0.0005, 0.05) * 1.6
f = 140 + 60 * np.sign(np.sin(2 * np.pi * 13 * t)); fase = 2 * np.pi * np.cumsum(f) / SR
servo = (2 * ((fase / (2 * np.pi)) % 1) - 1) * 0.5 * (t > 0.08) * np.minimum(1, (0.9 - t) / 0.2)
servo = pasa_bajos(servo, 1800)
escribir('audio/sfx/animatronic_stun.wav', mezclar(0.9, [(zap, 0.0, 1.0), (servo, 0.0, 1.0), (clic((900, 1700, 2600), 0.2), 0.62, 0.8)]))
