# Sonidos de la victoria de Five Doors Escape, sintetizados aqui (sin audio de procedencia externa):
# puerta de salida con llave, puerta de emergencia, ambiente nocturno del estacionamiento, puerta del auto,
# arranque, marcha y barrera del porton. WAV PCM 16 bit 44.1 kHz (lo que admite Sound de libGDX).
# Uso: python synth_victory_audio.py <carpeta assets de Escape>
import sys, os, wave
import numpy as np

SR = 44100
OUT = sys.argv[1]
rng = np.random.default_rng(2026)


def t_(d):
    return np.arange(int(d * SR)) / SR


def env(n, ataque, caida):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(ataque, 1e-4)) * np.exp(-t / caida)


def pasa_bajos(x, corte):
    # filtro de un polo en forma vectorizada por bloques (suficiente para estos efectos)
    a = np.exp(-2 * np.pi * corte / SR)
    y = np.empty_like(x); acc = 0.0
    for i in range(0, len(x), 4096):
        bloque = x[i:i + 4096]
        out = np.empty_like(bloque)
        for k in range(len(bloque)):
            acc = (1 - a) * bloque[k] + a * acc; out[k] = acc
        y[i:i + 4096] = out
    return y


def pasa_altos(x, corte):
    return x - pasa_bajos(x, corte)


def ruido(n):
    return rng.standard_normal(n)


def clic_metal(intensidad=1.0, frecs=(2300, 3900, 5200, 1500), dur=0.16):
    n = int(dur * SR); t = np.arange(n) / SR
    s = ruido(n) * env(n, 0.0004, 0.005) * 0.9
    for f, d, amp in zip(frecs, (0.03, 0.02, 0.012, 0.05), (0.5, 0.35, 0.25, 0.3)):
        s += np.sin(2 * np.pi * f * t + rng.uniform(0, 6)) * env(n, 0.0003, d) * amp
    return intensidad * s


def golpe(frec=55, dur=0.6, cuerpo=0.15, intensidad=1.0):
    n = int(dur * SR); t = np.arange(n) / SR
    s = np.sin(2 * np.pi * (frec * np.exp(-t * 3)) * t) * env(n, 0.002, cuerpo)
    s += pasa_bajos(ruido(n), 400) * env(n, 0.001, 0.04) * 2.5
    return intensidad * s


def chirrido(dur, f0, f1, aspereza=0.6):
    """Bisagra metalica: tono que se desliza con friccion irregular (modulacion de amplitud y de frecuencia)."""
    n = int(dur * SR); t = np.arange(n) / SR
    f = np.linspace(f0, f1, n) * (1 + 0.04 * np.sin(2 * np.pi * 5.3 * t) + 0.02 * pasa_bajos(ruido(n), 8) * 8)
    fase = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(fase) + 0.5 * np.sin(2 * fase + 0.3) + 0.25 * np.sin(3 * fase + 1.1)
    friccion = np.clip(0.6 + 0.6 * pasa_bajos(ruido(n), 22) * 6, 0, 1.4)
    s *= friccion * aspereza + (1 - aspereza)
    s *= np.minimum(1, t / 0.06) * np.minimum(1, (dur - t) / 0.15)
    return s


def mezclar(dur, piezas, estereo=False):
    out = np.zeros((int(dur * SR), 2 if estereo else 1))
    for senal, t0, gan, pan in piezas:
        i0 = int(t0 * SR); i1 = min(len(out), i0 + len(senal)); s = senal[:i1 - i0] * gan
        if estereo:
            out[i0:i1, 0] += s * np.sqrt((1 - pan) / 2) * 1.41
            out[i0:i1, 1] += s * np.sqrt((1 + pan) / 2) * 1.41
        else:
            out[i0:i1, 0] += s
    return out


def escribir(ruta, datos, techo_db=-1.0):
    datos = np.atleast_2d(datos.T).T if datos.ndim == 1 else datos
    pico = np.abs(datos).max()
    datos = datos * (10 ** (techo_db / 20)) / max(pico, 1e-9)
    p = os.path.join(OUT, ruta); os.makedirs(os.path.dirname(p), exist_ok=True)
    w = wave.open(p, 'wb'); w.setnchannels(datos.shape[1]); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((np.clip(datos, -1, 1) * 32767).astype(np.int16).tobytes()); w.close()
    print('escrito', ruta, round(len(datos) / SR, 2), 's')


# --- puerta de salida: llave, dos vueltas de cerrojo, picaporte y el giro pesado con chirrido (1.6 s de giro)
llave = pasa_altos(ruido(int(0.35 * SR)), 1800) * env(int(0.35 * SR), 0.05, 0.12) * 0.25
escribir('audio/sfx/exit_door_unlock.wav', mezclar(2.5, [
    (llave, 0.0, 1.0, 0),
    (clic_metal(0.8), 0.32, 1.0, 0),
    (clic_metal(1.0, (1900, 3300, 4600, 1200)), 0.52, 1.0, 0),
    (golpe(90, 0.3, 0.06, 0.5), 0.55, 1.0, 0),
    (clic_metal(0.6, (1500, 2700, 3800, 900)), 0.70, 1.0, 0),
    (chirrido(1.45, 410, 330, 0.7) * 0.32, 0.86, 1.0, 0),
    (pasa_bajos(ruido(int(1.5 * SR)), 300) * np.linspace(0.05, 0.2, int(1.5 * SR)), 0.86, 1.0, 0),
    (golpe(48, 0.7, 0.2, 0.55), 2.28, 1.0, 0),
])[:, 0])

# --- puerta de emergencia: barra antipanico, cerrojo que salta, la hoja golpea la pared y entra el viento
viento_corto = pasa_bajos(ruido(int(1.4 * SR)), 700) * np.concatenate([np.linspace(0, 1, int(0.25 * SR)), np.linspace(1, 0.3, int(1.15 * SR))])
escribir('audio/sfx/emergency_door_open.wav', mezclar(1.9, [
    (clic_metal(1.2, (1700, 2900, 4100, 800), 0.22), 0.0, 1.0, 0),
    (golpe(110, 0.25, 0.04, 0.6), 0.03, 1.0, 0),
    (clic_metal(0.9, (2500, 4300, 5900, 1300)), 0.09, 1.0, 0),
    (chirrido(0.4, 520, 640, 0.5) * 0.18, 0.12, 1.0, 0),
    (golpe(62, 0.9, 0.28, 1.2), 0.55, 1.0, 0),
    (clic_metal(0.7, (900, 1600, 2400, 600), 0.3), 0.56, 1.0, 0),
    (viento_corto * 0.6, 0.15, 1.0, 0),
])[:, 0])

# --- ambiente nocturno (20 s, estereo, cierra en bucle): viento, grillos, zumbido de los faroles de sodio y una ruta lejana
D = 20.0
n = int(D * SR); t = np.arange(n) / SR
viento = pasa_bajos(ruido(n), 450) * (0.5 + 0.35 * np.sin(2 * np.pi * t / D * 2) + 0.15 * np.sin(2 * np.pi * t / D * 5 + 1))
viento_r = pasa_bajos(ruido(n), 450) * (0.5 + 0.35 * np.sin(2 * np.pi * t / D * 2 + 1.3) + 0.15 * np.sin(2 * np.pi * t / D * 7))
ruta = pasa_bajos(ruido(n), 90) * (0.6 + 0.4 * np.sin(2 * np.pi * t / D * 3))
zumbido = (np.sin(2 * np.pi * 120 * t) + 0.35 * np.sin(2 * np.pi * 240 * t)) * 0.03
amb = np.zeros((n, 2))
amb[:, 0] = viento * 1.0 + ruta * 1.5 + zumbido
amb[:, 1] = viento_r * 1.0 + ruta * 1.5 + zumbido
for lado, base in ((0, 4650), (1, 4380)):
    k = rng.uniform(0, 0.7)
    while k < D - 0.3:
        ch = np.zeros(int(0.09 * SR)); tt = np.arange(len(ch)) / SR
        for p in range(3):
            i0 = int(p * 0.028 * SR); m = int(0.016 * SR)
            ch[i0:i0 + m] += np.sin(2 * np.pi * base * tt[:m]) * np.hanning(m)
        i0 = int(k * SR); amb[i0:i0 + len(ch), lado] += ch * 0.05 * rng.uniform(0.5, 1.0)
        k += rng.uniform(0.35, 0.9) if rng.uniform() > 0.12 else rng.uniform(1.5, 3.0)
# cierre en bucle: los ultimos 1.5 s se funden con los primeros
c = int(1.5 * SR); r = np.linspace(0, 1, c)[:, None]
amb[:c] = amb[:c] * r + amb[-c:] * (1 - r)
amb = amb[:-c]
escribir('audio/ambience/night_parking_lot.wav', amb, -9.0)

# --- puerta del auto
escribir('audio/sfx/car_door_open.wav', mezclar(0.9, [
    (clic_metal(0.7, (1400, 2600, 3500, 700)), 0.0, 1.0, 0),
    (golpe(140, 0.2, 0.03, 0.4), 0.05, 1.0, 0),
    (chirrido(0.45, 300, 260, 0.4) * 0.08, 0.12, 1.0, 0),
    (pasa_bajos(ruido(int(0.5 * SR)), 900) * env(int(0.5 * SR), 0.05, 0.15) * 0.3, 0.1, 1.0, 0),
])[:, 0])
escribir('audio/sfx/car_door_close.wav', mezclar(0.8, [
    (golpe(58, 0.7, 0.16, 1.4), 0.0, 1.0, 0),
    (clic_metal(0.8, (1100, 2100, 3000, 600), 0.2), 0.012, 1.0, 0),
    (pasa_bajos(ruido(int(0.3 * SR)), 250) * env(int(0.3 * SR), 0.001, 0.06), 0.0, 1.5, 0),
])[:, 0])


# --- motor: el sonido de un 4 cilindros a partir de la frecuencia de encendido (rpm/60*2) y sus armonicos
def motor(rpm, carga, aspereza=0.35):
    n = len(rpm)
    f = rpm / 60.0 * 2.0
    fase = 2 * np.pi * np.cumsum(f) / SR
    s = np.zeros(n)
    for h, amp in ((0.5, 0.35), (1, 1.0), (2, 0.55), (3, 0.3), (4, 0.18), (6, 0.08)):
        s += amp * np.sin(h * fase + h)
    # cada explosion como un pulso aspero (modulacion por ruido sincronizada con el ciclo)
    pulso = 0.5 + 0.5 * np.cos(fase)
    s += aspereza * pasa_bajos(ruido(n), 1200) * pulso ** 3 * 2.5
    s *= 0.6 + 0.4 * carga
    return pasa_bajos(s, 2500)


# arranque: burro de arranque (whine + compresiones a ~9 Hz), explosion y ralenti con un acelerón
d_arr = 1.05
ta = t_(d_arr)
burro = (np.sin(2 * np.pi * (380 + 40 * ta) * ta) * 0.25 + pasa_bajos(ruido(len(ta)), 1500) * 0.4) * (0.55 + 0.45 * np.sin(2 * np.pi * 9.2 * ta) ** 2)
burro *= np.minimum(1, ta / 0.05)
D2 = 3.8
tt = t_(D2 - d_arr)
rpm = 820 + 1300 * np.exp(-((tt - 0.55) / 0.35) ** 2) + 500 * np.exp(-tt / 0.25)
idle = motor(rpm, 0.4 + 0.6 * np.exp(-((tt - 0.55) / 0.3) ** 2))
escribir('audio/sfx/car_engine_start.wav', mezclar(D2, [
    (clic_metal(0.4, (900, 1700, 2600, 500)), 0.0, 1.0, 0),
    (burro, 0.05, 1.0, 0),
    (golpe(45, 0.5, 0.1, 0.9), d_arr, 1.0, 0),
    (idle * np.minimum(1, tt / 0.03) * np.minimum(1, (D2 - d_arr - tt) / 0.2), d_arr, 0.9, 0),
])[:, 0])

# marcha: sale en primera, sube de vueltas, cambio y se aleja (7.5 s), con ruido de rodadura creciente
D3 = 7.5
tt = t_(D3)
rpm = np.where(tt < 2.6, 900 + 2300 * np.clip(tt / 2.6, 0, 1) ** 0.8, 0)
rpm = np.where((tt >= 2.6) & (tt < 2.9), 3200 - 1300 * (tt - 2.6) / 0.3, rpm)
rpm = np.where(tt >= 2.9, 1900 + 900 * np.clip((tt - 2.9) / 3.0, 0, 1), rpm)
carga = np.where((tt > 2.6) & (tt < 2.9), 0.2, 0.9)
marcha = motor(rpm, carga, 0.25)
rodadura = pasa_bajos(ruido(len(tt)), 600) * np.clip(tt / 4.0, 0, 1) * 0.6
grava = pasa_altos(ruido(len(tt)), 3000) * np.clip(1 - tt / 2.0, 0, 1) * 0.08
salida = marcha + rodadura + grava
salida *= np.minimum(1, tt / 0.1) * np.minimum(1, (D3 - tt) / 1.6)
escribir('audio/sfx/car_drive_away.wav', salida)

# barrera del porton: motor electrico que sube y tope
D4 = 1.6
tt = t_(D4)
motor_el = (np.sin(2 * np.pi * (180 + 60 * tt) * tt) * 0.4 + np.sin(2 * np.pi * (360 + 120 * tt) * tt) * 0.2 + pasa_bajos(ruido(len(tt)), 2000) * 0.25)
motor_el *= np.minimum(1, tt / 0.08) * np.minimum(1, (1.25 - tt) / 0.1).clip(0, 1)
escribir('audio/sfx/gate_barrier_raise.wav', mezclar(D4, [
    (clic_metal(0.6, (1300, 2300, 3300, 700)), 0.0, 1.0, 0),
    (motor_el, 0.05, 0.8, 0),
    (golpe(120, 0.3, 0.05, 0.5), 1.25, 1.0, 0),
])[:, 0])
