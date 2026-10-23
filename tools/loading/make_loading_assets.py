# Recursos de la pantalla de carga de Five Doors Escape (CLAUDE.md §2.59):
#  - ui/loading_title.png: el titulo de build_loading_title.py con un resplandor rojo suave
#  - ui/loading_background.jpg: el comedor visto desde el escenario (fotograma 1 del render de Rendirse, que usa los
#    mismos modelos de Escape), recortado, desenfocado, muy oscuro, con vineta y grano. No muestra la llave ni la salida.
# Uso: python make_loading_assets.py <titulo_render.png> <fotograma_comedor.jpg> <carpeta assets de Escape>
import sys, os
import numpy as np
from PIL import Image, ImageFilter

titulo_src, fondo_src, assets = sys.argv[1], sys.argv[2], sys.argv[3]
ui = os.path.join(assets, 'ui'); os.makedirs(ui, exist_ok=True)

# titulo: resplandor = alfa desenfocado, tenido de rojo, debajo del texto
t = Image.open(titulo_src).convert('RGBA')
t = t.crop(t.getbbox())
pad = 60
lienzo = Image.new('RGBA', (t.width + 2 * pad, t.height + 2 * pad), (0, 0, 0, 0))
lienzo.paste(t, (pad, pad), t)
alfa = lienzo.split()[3].filter(ImageFilter.GaussianBlur(22))
brillo = Image.new('RGBA', lienzo.size, (160, 10, 6, 0)); brillo.putalpha(alfa.point(lambda v: int(v * 0.55)))
final = Image.alpha_composite(brillo, lienzo)
final.save(os.path.join(ui, 'loading_title.png'))
print('titulo', final.size)

# fondo
f = Image.open(fondo_src).convert('RGB')
w, h = f.size                       # 1800 x 600 (equirectangular de 160 grados)
f = f.crop((int(w * 0.22), 0, int(w * 0.78), h)).resize((1600, 900), Image.LANCZOS)
f = f.filter(ImageFilter.GaussianBlur(5))
a = np.asarray(f).astype(np.float32) / 255.0
a = a ** 1.35 * 0.55                # mas oscuro y con mas contraste
yy, xx = np.mgrid[0:900, 0:1600]
r = np.sqrt(((xx - 800) / 800.0) ** 2 + ((yy - 450) / 450.0) ** 2)
a *= np.clip(1.15 - r * 0.75, 0.12, 1.0)[..., None]          # vineta
a[..., 0] *= 1.06; a[..., 2] *= 0.92                          # un poco calido
rng = np.random.default_rng(7); a += rng.normal(0, 0.012, a.shape)   # grano
Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).save(os.path.join(ui, 'loading_background.jpg'), quality=86)
print('fondo listo')
