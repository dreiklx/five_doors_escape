# Fuentes bitmap de Escape (CLAUDE.md §2.88): Inter (SIL Open Font License 1.1) rasterizada a alta resolucion en
# formato AngelCode (.fnt de texto + .png), que libGDX carga con BitmapFont sin dependencias nuevas (sin freetype).
# Sustituye a la BitmapFont() por defecto de libGDX (Arial de 15 px), que escalada x1.3-x6 se veia borrosa o
# pixelada. Cada fuente se dibuja en blanco con antialias; el color y la sombra se ponen en el juego.
# Uso: python build_bitmap_fonts.py <ruta a Inter.woff2 o Inter.ttf variable> <carpeta assets de Escape>
# (Inter viene con Blender: <Blender>/<version>/datafiles/fonts/Inter.woff2; tambien en github.com/rsms/inter)
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ORIGEN = sys.argv[1]
OUT = os.path.join(sys.argv[2], 'ui', 'fonts')
os.makedirs(OUT, exist_ok=True)

# ASCII imprimible + Latin-1 (tildes, enie, signos de apertura) + tipografia comun de los textos ES/EN
CARACTERES = [chr(c) for c in range(32, 127)] + [chr(c) for c in range(160, 256)] + list('‘’“”…–—•')
# (nombre, peso de la variable, tamano en px). La de interfaz cubre HUD, menus y mensajes; la de titulos, los
# rotulos grandes (victoria, derrota), que asi no se escalan por encima de su resolucion.
FUENTES = [('inter_ui', b'SemiBold', 56), ('inter_title', b'ExtraBold', 128)]
PADDING = 3     # margen transparente alrededor de cada glifo (filtrado lineal y mipmaps sin sangrado)
ANCHO_ATLAS = 1024


def construir(nombre, peso, tam):
    fuente = ImageFont.truetype(ORIGEN, tam)
    fuente.set_variation_by_name(peso)
    ascenso, descenso = fuente.getmetrics()
    glifos = []
    for ch in CARACTERES:
        izq, arr, der, aba = fuente.getbbox(ch, anchor='ls')   # relativo a la linea base
        ancho, alto = max(0, der - izq), max(0, aba - arr)
        img = None
        if ancho and alto:
            img = Image.new('L', (ancho + 2 * PADDING, alto + 2 * PADDING), 0)
            ImageDraw.Draw(img).text((PADDING - izq, PADDING - arr), ch, font=fuente, fill=255, anchor='ls')
        glifos.append((ch, img, izq, arr, fuente.getlength(ch)))
    # empaquetado por filas, de mas alto a mas bajo
    orden = sorted(range(len(glifos)), key=lambda i: -(glifos[i][1].height if glifos[i][1] else 0))
    x = y = fila = 0
    pos = {}
    for i in orden:
        img = glifos[i][1]
        if img is None:
            pos[i] = (0, 0, 0, 0)
            continue
        if x + img.width > ANCHO_ATLAS:
            x, y, fila = 0, y + fila + 1, 0
        pos[i] = (x, y, img.width, img.height)
        x += img.width + 1
        fila = max(fila, img.height)
    alto_atlas = 1
    while alto_atlas < y + fila + 1:
        alto_atlas *= 2
    atlas = Image.new('LA', (ANCHO_ATLAS, alto_atlas), (255, 0))
    for i, (ch, img, izq, arr, avance) in enumerate(glifos):
        if img is not None:
            px, py, _, _ = pos[i]
            capa = Image.new('LA', img.size, (255, 0))
            capa.putalpha(img)
            atlas.paste(capa, (px, py))
    atlas.convert('RGBA').save(os.path.join(OUT, nombre + '.png'), optimize=True)
    linea = ascenso + descenso
    with open(os.path.join(OUT, nombre + '.fnt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write('info face="Inter %s" size=%d bold=0 italic=0 charset="" unicode=1 stretchH=100 smooth=1 aa=1 '
                'padding=0,0,0,0 spacing=1,1\n' % (peso.decode(), tam))
        f.write('common lineHeight=%d base=%d scaleW=%d scaleH=%d pages=1 packed=0\n'
                % (linea, ascenso, ANCHO_ATLAS, alto_atlas))
        f.write('page id=0 file="%s.png"\n' % nombre)
        f.write('chars count=%d\n' % len(glifos))
        for i, (ch, img, izq, arr, avance) in enumerate(glifos):
            px, py, w, h = pos[i]
            xo, yo = (izq - PADDING, ascenso + arr - PADDING) if img is not None else (0, 0)
            f.write('char id=%d x=%d y=%d width=%d height=%d xoffset=%d yoffset=%d xadvance=%d page=0 chnl=15\n'
                    % (ord(ch), px, py, w, h, xo, yo, round(avance)))
    print(nombre, 'linea', linea, 'base', ascenso, 'atlas', ANCHO_ATLAS, 'x', alto_atlas, 'glifos', len(glifos))


for n, p, t in FUENTES:
    construir(n, p, t)
