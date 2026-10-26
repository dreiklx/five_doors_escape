# Corrige la pose de un clip de un .glb rotando la rotacion LOCAL de huesos concretos en todos sus keyframes
# (CLAUDE.md §2.81). Cada keyframe q del canal "rotation" del hueso en ese clip pasa a q * c, donde c es el giro
# corrector en el espacio local del hueso: el mismo efecto que un giro fijo del hueso respecto de su padre durante todo
# el clip. Solo cambian esos floats (en el sitio, mismo tamano); mallas, esqueleto, otros clips y texturas quedan
# identicos. Se niega a editar un acceso compartido con otro canal (para no cambiar otro clip sin querer).
#
# Uso: python corregir_pose_glb.py entrada.glb salida.glb "Clip:hueso:ax,ay,az,grados" ["Clip:hueso:..." ...]
# Ejemplo (Freddy, §2.81): "Freddy_Walk:j_r_arm_01_021:1,0,0,90"
import math
import struct
import sys

sys.path.insert(0, __import__('os').path.dirname(__file__))
from reducir_texturas_glb import leer_glb, escribir_glb


def quat_eje_angulo(ax, ay, az, grados):
    n = math.sqrt(ax * ax + ay * ay + az * az)
    ax, ay, az = ax / n, ay / n, az / n
    m = math.radians(grados) / 2
    s = math.sin(m)
    return (ax * s, ay * s, az * s, math.cos(m))


def mul(a, b):
    # producto de cuaterniones (x, y, z, w): a * b
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw,
            aw * bw - ax * bx - ay * by - az * bz)


def main():
    entrada, salida = sys.argv[1], sys.argv[2]
    js, binario = leer_glb(entrada)
    binario = bytearray(binario)
    nombres = [n.get('name', '') for n in js['nodes']]
    uso = {}
    for an in js['animations']:
        for ch in an['channels']:
            uso.setdefault(an['samplers'][ch['sampler']]['output'], []).append(an['name'])
    for orden in sys.argv[3:]:
        clip, hueso, eje = orden.split(':')
        ax, ay, az, grados = (float(v) for v in eje.split(','))
        c = quat_eje_angulo(ax, ay, az, grados)
        an = next(a for a in js['animations'] if a['name'] == clip)
        nodo = nombres.index(hueso)
        ch = next(ch for ch in an['channels'] if ch['target']['node'] == nodo and ch['target']['path'] == 'rotation')
        smp = an['samplers'][ch['sampler']]
        acc = js['accessors'][smp['output']]
        assert acc['componentType'] == 5126 and acc['type'] == 'VEC4', 'solo cuaterniones float'
        assert smp.get('interpolation', 'LINEAR') != 'CUBICSPLINE', 'CUBICSPLINE no soportado'
        assert len(uso[smp['output']]) == 1, 'el acceso lo usa mas de un canal: %s' % uso[smp['output']]
        bv = js['bufferViews'][acc['bufferView']]
        paso = bv.get('byteStride', 16)
        base = bv.get('byteOffset', 0) + acc.get('byteOffset', 0)
        for i in range(acc['count']):
            o = base + i * paso
            q = struct.unpack_from('<4f', binario, o)
            r = mul(q, c)
            n = math.sqrt(sum(v * v for v in r))
            struct.pack_into('<4f', binario, o, *(v / n for v in r))
        print('  %s / %s: %d keyframes girados %.1f grados alrededor de (%g, %g, %g) local' % (clip, hueso, acc['count'], grados, ax, ay, az))
    escribir_glb(salida, js, bytes(binario))
    print('escrito', salida)


if __name__ == '__main__':
    main()
