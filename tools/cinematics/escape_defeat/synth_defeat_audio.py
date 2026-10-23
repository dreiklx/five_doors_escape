# Sonidos de la derrota definitiva de Five Doors Escape, sintetizados aqui (sin audio externo): un tiron de arrastre
# por el piso, el golpe del cuerpo sobre el escenario, el servo del animatronico al inclinarse y el foco que hace
# cortocircuito. WAV PCM 16 bit 44.1 kHz mono.
# Uso: python synth_defeat_audio.py <carpeta assets de Escape>
import sys, os, wave
import numpy as np

SR = 44100
OUT = sys.argv[1]
rng = np.random.default_rng(1993)


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


# tiron de arrastre (0.7 s): roce de tela y suela contra el piso + un golpe metalico del pie del animatronico
n = int(0.7 * SR); t = np.arange(n) / SR
roce = pasa_bajos(rng.standard_normal(n), 900) * np.sin(np.pi * np.clip(t / 0.45, 0, 1)) ** 1.5
roce += (rng.standard_normal(n) * 0.25 * (pasa_bajos(rng.standard_normal(n), 30) > 0.002)) * np.sin(np.pi * np.clip(t / 0.45, 0, 1))
pie = np.sin(2 * np.pi * 70 * t) * env(n, 0.002, 0.08) * 1.4
metal = sum(np.sin(2 * np.pi * f * t) * env(n, 0.001, d) * a for f, d, a in ((520, 0.09, 0.35), (1310, 0.05, 0.2), (2900, 0.02, 0.12)))
escribir('audio/sfx/defeat_drag.wav', roce * 1.6 + pie + metal * 0.6)

# golpe del cuerpo al subirlo al escenario (madera hueca)
n = int(1.1 * SR); t = np.arange(n) / SR
golpe = np.sin(2 * np.pi * (75 * np.exp(-t * 2.5)) * t) * env(n, 0.002, 0.22)
golpe += pasa_bajos(rng.standard_normal(n), 500) * env(n, 0.001, 0.06) * 3
golpe += sum(np.sin(2 * np.pi * f * t) * env(n, 0.002, d) * a for f, d, a in ((180, 0.25, 0.4), (260, 0.18, 0.25)))
escribir('audio/sfx/defeat_body_thud.wav', golpe)

# servo al inclinarse (1.6 s): motor que tironea y se detiene
n = int(1.6 * SR); t = np.arange(n) / SR
f = np.linspace(150, 95, n); fase = 2 * np.pi * np.cumsum(f) / SR
sierra = 2 * ((fase / (2 * np.pi)) % 1) - 1
servo = pasa_bajos(sierra, 1600) * 0.7 + np.sin(2 * fase) * 0.2
servo *= (1 + 0.4 * np.sign(np.sin(2 * np.pi * 19 * t))) * np.minimum(1, t / 0.05) * np.minimum(1, (1.6 - t) / 0.15)
escribir('audio/sfx/defeat_servo.wav', servo)

# cortocircuito del foco (1.2 s): zumbido de 60 Hz, chasquidos y el estallido final que deja silencio
n = int(1.2 * SR); t = np.arange(n) / SR
zumbido = (np.sin(2 * np.pi * 60 * t) + 0.6 * np.sin(2 * np.pi * 120 * t) + 0.3 * np.sin(2 * np.pi * 180 * t)) * 0.25
corte = np.ones(n)
for _ in range(10):
    a = rng.integers(0, int(0.8 * SR)); corte[a:a + rng.integers(300, 2500)] = 0.0
zumbido *= corte * (t < 0.85)
chasquidos = np.zeros(n)
for _ in range(14):
    a = rng.integers(0, int(0.85 * SR)); m = int(0.01 * SR)
    chasquidos[a:a + m] += rng.standard_normal(m) * env(m, 0.0003, 0.002) * 1.5
estallido = np.zeros(n); i0 = int(0.85 * SR); m = n - i0
estallido[i0:] = (rng.standard_normal(m) * env(m, 0.0005, 0.03) * 2.5 + np.sin(2 * np.pi * 90 * t[:m]) * env(m, 0.001, 0.1))
escribir('audio/sfx/defeat_light_short.wav', zumbido + chasquidos + estallido)
