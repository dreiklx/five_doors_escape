# Reduce las texturas embebidas de un .glb sin tocar nada mas (CLAUDE.md §2.77): mallas, esqueleto, animaciones,
# materiales y JSON quedan identicos; solo cambian los bytes de cada imagen mayor que el lado maximo, reescalada con
# Lanczos y guardada en su mismo formato (PNG optimizado / JPEG calidad 92). Los mapas de normales se renormalizan
# despues de reescalar (promediar normales las acorta). Los bufferViews se reubican con alineacion de 4 bytes.
#
# Uso: python reducir_texturas_glb.py entrada.glb salida.glb [lado_maximo=2048]
# El original de Freddy (texturas 4096) esta en FDAF assets/source_files/3d_models/LibGDX_Ready/Freddy.glb.
import io
import json
import struct
import sys

import numpy as np
from PIL import Image


def leer_glb(ruta):
    b = open(ruta, 'rb').read()
    magia, version, largo = struct.unpack_from('<III', b, 0)
    assert magia == 0x46546C67 and version == 2, 'no es un GLB 2.0'
    n_json, tipo = struct.unpack_from('<II', b, 12)
    assert tipo == 0x4E4F534A
    js = json.loads(b[20:20 + n_json])
    off = 20 + n_json
    n_bin, tipo = struct.unpack_from('<II', b, off)
    assert tipo == 0x004E4942
    return js, b[off + 8:off + 8 + n_bin]


def escribir_glb(ruta, js, binario):
    j = json.dumps(js, separators=(',', ':')).encode('utf-8')
    j += b' ' * ((4 - len(j) % 4) % 4)
    binario += b'\x00' * ((4 - len(binario) % 4) % 4)
    total = 12 + 8 + len(j) + 8 + len(binario)
    with open(ruta, 'wb') as f:
        f.write(struct.pack('<III', 0x46546C67, 2, total))
        f.write(struct.pack('<II', len(j), 0x4E4F534A)); f.write(j)
        f.write(struct.pack('<II', len(binario), 0x004E4942)); f.write(binario)


def es_normal(js, indice_imagen):
    for mat in js.get('materials', []):
        nt = mat.get('normalTexture')
        if nt is not None and js['textures'][nt['index']].get('source') == indice_imagen:
            return True
    return False


def reducir(datos, mime, lado, normal):
    im = Image.open(io.BytesIO(datos))
    if max(im.size) <= lado:
        return None, im.size, im.size
    f = lado / max(im.size)
    nuevo = (max(1, round(im.size[0] * f)), max(1, round(im.size[1] * f)))
    modo = im.mode
    r = im.convert('RGB' if modo not in ('RGB', 'RGBA', 'L', 'LA') else modo).resize(nuevo, Image.LANCZOS)
    if normal and r.mode == 'RGB':
        a = np.asarray(r).astype(np.float32) / 127.5 - 1.0
        a /= np.maximum(np.linalg.norm(a, axis=2, keepdims=True), 1e-6)
        r = Image.fromarray(np.clip((a + 1.0) * 127.5 + 0.5, 0, 255).astype(np.uint8), 'RGB')
    salida = io.BytesIO()
    if mime == 'image/jpeg':
        r.convert('RGB').save(salida, 'JPEG', quality=92, optimize=True)
    else:
        r.save(salida, 'PNG', optimize=True)
    return salida.getvalue(), im.size, nuevo


def main():
    entrada, salida = sys.argv[1], sys.argv[2]
    lado = int(sys.argv[3]) if len(sys.argv) > 3 else 2048
    js, binario = leer_glb(entrada)
    reemplazos = {}
    for i, img in enumerate(js.get('images', [])):
        if 'bufferView' not in img:
            continue
        bv = js['bufferViews'][img['bufferView']]
        ini = bv.get('byteOffset', 0)
        nuevo, antes, despues = reducir(binario[ini:ini + bv['byteLength']], img.get('mimeType'), lado, es_normal(js, i))
        if nuevo is not None:
            reemplazos[img['bufferView']] = nuevo
            print('  %-30s %s -> %s  %6d KB -> %6d KB%s' % (img.get('name', i), antes, despues, bv['byteLength'] // 1024,
                                                          len(nuevo) // 1024, ' (normales)' if es_normal(js, i) else ''))
    # reconstruye el buffer en el orden original de los bufferViews
    nuevo_bin = bytearray()
    for k, bv in enumerate(js['bufferViews']):
        ini = bv.get('byteOffset', 0)
        datos = reemplazos.get(k, binario[ini:ini + bv['byteLength']])
        nuevo_bin += b'\x00' * ((4 - len(nuevo_bin) % 4) % 4)
        bv['byteOffset'] = len(nuevo_bin)
        bv['byteLength'] = len(datos)
        nuevo_bin += datos
    js['buffers'][0]['byteLength'] = len(nuevo_bin)
    escribir_glb(salida, js, bytes(nuevo_bin))
    print('escrito %s: %.1f MB (antes %.1f MB)' % (salida, (len(nuevo_bin) + 2048) / 1e6, len(binario) / 1e6))


if __name__ == '__main__':
    main()
