# Pasos de Freddy corriendo por el estacionamiento en la persecucion final (CLAUDE.md §2.68), sintetizados aqui (sin
# audio externo): un golpe grave de un cuerpo pesado sobre asfalto, el chasquido metalico del endoesqueleto y un
# rechinar corto de servo. WAV PCM 16 bit 44.1 kHz mono, ~0.42 s. El juego lo reproduce en cada pisada con volumen y
# tono levemente distintos.
# Uso: python synth_freddy_chase.py <carpeta assets de Escape>
import sys, os, wave
import numpy as np

SR = 44100
OUT = sys.argv[1]
rng = np.random.default_rng(1987)
n = int(0.42 * SR)
t = np.arange(n) / SR

# golpe: seno grave con caida de tono y ruido filtrado
golpe = np.sin(2 * np.pi * (62 * t + 30 * (1 - np.exp(-t / 0.03)) * 0.03)) * np.exp(-t / 0.075)
ruido = rng.standard_normal(n)
acc = 0.0
grave = np.empty(n)
for k in range(n):
    acc = 0.06 * ruido[k] + 0.94 * acc
    grave[k] = acc
golpe += 2.2 * grave * np.exp(-t / 0.05)

# chasquido metalico: parciales inarmonicos agudos, muy cortos, 12 ms despues del golpe
metal = np.zeros(n)
m = t >= 0.012
tm = t[m] - 0.012
for f, a, d in ((1240, 0.35, 0.05), (1873, 0.22, 0.04), (2711, 0.14, 0.03), (3907, 0.08, 0.02)):
    metal[m] += a * np.sin(2 * np.pi * f * tm) * np.exp(-tm / d)

# servo: zumbido de diente de sierra que sube un poco de tono, de 0.06 a 0.26 s
servo = np.zeros(n)
s = (t > 0.06) & (t < 0.26)
ts = t[s] - 0.06
f_s = 180 + 220 * ts / 0.2
fase = np.cumsum(2 * np.pi * f_s / SR)
servo[s] = 0.12 * (2 * ((fase / (2 * np.pi)) % 1) - 1) * np.sin(np.pi * ts / 0.2)

datos = golpe + metal + servo
datos[-int(0.03 * SR):] *= np.linspace(1, 0, int(0.03 * SR))
datos = datos * (10 ** (-1.0 / 20)) / np.abs(datos).max()
p = os.path.join(OUT, 'audio', 'sfx', 'freddy_run_step.wav')
w = wave.open(p, 'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
w.writeframes((np.clip(datos, -1, 1) * 32767).astype(np.int16).tobytes()); w.close()
print('escrito', p, round(n / SR, 2), 's')
