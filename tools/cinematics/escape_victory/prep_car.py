# Adapta el sedan CC0 de Quaternius ("Car", NormalCar1) para la victoria de Five Doors Escape:
#  - escala de sedan americano grande, frente hacia -Y de Blender (= +Z del juego)
#  - puerta del conductor (lado izquierdo) separada con su pivote en la bisagra delantera
#  - ruedas con el origen en su eje, volante propio, interior basico (asientos, tablero, piso)
#  - materiales PBR nocturnos (pintura metalizada, vidrio oscuro translucido, faros/luces traseras emisivos)
# Uso: blender -b --factory-startup -P prep_car.py -- origen.glb salida.glb carpeta_capturas
import bpy, bmesh, mathutils, math, sys, os
a = sys.argv[sys.argv.index('--') + 1:]
SRC, OUT, SHOTS = a[0], a[1], a[2]
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=SRC)
V = mathutils.Vector

objs = {o.name: o for o in bpy.data.objects if o.type == 'MESH'}
body = [o for n, o in objs.items() if 'Cube.012' in n][0]
w_fl = [o for n, o in objs.items() if 'FrontLeftWheel' in n][0]
w_fr = [o for n, o in objs.items() if 'FrontRightWheel' in n][0]
w_r = [o for n, o in objs.items() if 'BackWheels' in n][0]
for o in (body, w_fl, w_fr, w_r):
    o.parent = None
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
for o in list(bpy.data.objects):
    if o.type != 'MESH':
        bpy.data.objects.remove(o, do_unlink=True)

# --- orientacion: el frente es donde estan los faros
def centro_material(o, nombre):
    pts = [o.matrix_world @ p.center for p in o.data.polygons if o.data.materials[p.material_index].name == nombre]
    return sum(pts, V()) / len(pts)
faros = centro_material(body, 'Headlights'); traseras = centro_material(body, 'TailLights')
print('faros', faros, 'traseras', traseras)
giro = 0.0 if faros.y < traseras.y else math.pi   # queremos el frente hacia -Y
ESC = 1.15
for o in (body, w_fl, w_fr, w_r):
    o.rotation_euler = (0, 0, giro); o.scale = (ESC, ESC, ESC)
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
# tras girar 180, la rueda "FrontLeft" del modelo sigue siendo la delantera izquierda del auto (el giro es del auto entero)
mn = V((1e9,) * 3); mx = -mn
for o in (body,):
    for v in o.data.vertices:
        for i in range(3):
            mn[i] = min(mn[i], v.co[i]); mx[i] = max(mx[i], v.co[i])
print('cuerpo', mn, mx, 'tam', mx - mn)
# con el frente hacia -Y, la izquierda del conductor es +X (mirando hacia -Y, la mano izquierda queda en +X)

# --- puerta del conductor: la ventana lateral delantera izquierda define el ancho de la puerta
ventanas = [p for p in body.data.polygons if body.data.materials[p.material_index].name == 'Windows' and p.normal.x > 0.5]
vy = sorted(set(round(body.data.vertices[i].co.y, 3) for p in ventanas for i in p.vertices))
vz = [body.data.vertices[i].co.z for p in ventanas for i in p.vertices]
print('ventanas izquierda y', vy, 'z', min(vz), max(vz))
# la ventana delantera es la que esta mas cerca del frente (y mas negativo)
grupos = []
for p in ventanas:
    ys = [body.data.vertices[i].co.y for i in p.vertices]
    grupos.append((min(ys), max(ys)))
y_frente_puerta = min(g[0] for g in grupos)
# el pilar B: el hueco entre la ventana delantera y la trasera
cortes = sorted(set([round(g[0], 3) for g in grupos] + [round(g[1], 3) for g in grupos]))
print('bordes de ventanas', cortes)
y_pilar_b = None
for g in sorted(grupos):
    if g[0] <= y_frente_puerta + 1e-3:
        y_pilar_b = max(y_pilar_b or -1e9, g[1])
y_frente_puerta -= 0.10   # la chapa de la puerta empieza un poco antes que el vidrio
# el pilar B esta en el hueco entre la ventana delantera y la trasera (y ~ 0 en este modelo): se corta en su centro
huecos = [(cortes[i] + cortes[i + 1]) / 2 for i in range(len(cortes) - 1) if cortes[i + 1] - cortes[i] > 0.2]
y_pilar_b = min(huecos, key=lambda h: abs(h - 0.0)) if huecos else 0.0
# perfil del techo: altura maxima del cuerpo en franjas de y cerca del centro (para que el interior no lo atraviese)
def techo_en(y):
    zs = [v.co.z for v in body.data.vertices if abs(v.co.y - y) < 0.12 and abs(v.co.x) < 0.5]
    return max(zs) if zs else mx.z
print('techo', [(round(y, 2), round(techo_en(y), 2)) for y in (-1.0, -0.6, -0.2, 0.2, 0.6, 1.0)])
z_umbral = mn.z + 0.30
print('puerta y', y_frente_puerta, y_pilar_b, 'umbral z', z_umbral)

bm = bmesh.new(); bm.from_mesh(body.data)
for co, no in ((V((0, y_frente_puerta, 0)), V((0, 1, 0))), (V((0, y_pilar_b, 0)), V((0, 1, 0))), (V((0, 0, z_umbral)), V((0, 0, 1)))):
    geom = bm.verts[:] + bm.edges[:] + bm.faces[:]
    bmesh.ops.bisect_plane(bm, geom=geom, plane_co=co, plane_no=no)
bm.faces.ensure_lookup_table()
puerta = [f for f in bm.faces if f.calc_center_median().x > 0.35 and f.normal.x > 0.25
          and y_frente_puerta < f.calc_center_median().y < y_pilar_b and f.calc_center_median().z > z_umbral]
print('caras de la puerta', len(puerta))
bm_p = bmesh.new()
mapa = {}
for f in puerta:
    vs = []
    for v in f.verts:
        if v not in mapa:
            mapa[v] = bm_p.verts.new(v.co)
        vs.append(mapa[v])
    nf = bm_p.faces.new(vs); nf.material_index = f.material_index
bmesh.ops.delete(bm, geom=puerta, context='FACES')
bm.to_mesh(body.data); bm.free()
me_p = bpy.data.meshes.new('car_door_driver')
for m in body.data.materials:
    me_p.materials.append(m)
bm_p.to_mesh(me_p); bm_p.free()
door = bpy.data.objects.new('car_door_driver', me_p); bpy.context.scene.collection.objects.link(door)
# grosor de la puerta: un panel interior para que no se vea hueca al abrirla
sol = door.modifiers.new('grosor', 'SOLIDIFY'); sol.thickness = 0.05; sol.offset = 1

# --- origen de cada pieza movil en su pivote
def origen_en(o, p):
    d = p - o.location
    o.data.transform(mathutils.Matrix.Translation(-d)); o.location = p
def centro(o):
    vs = [o.matrix_world @ v.co for v in o.data.vertices]; return sum(vs, V()) / len(vs)
bisagra = V((max(v.co.x for v in door.data.vertices), y_frente_puerta, 0.5 * (z_umbral + mx.z)))
origen_en(door, bisagra)
for o in (w_fl, w_fr, w_r):
    origen_en(o, centro(o))

# --- interior basico (se ve desde el asiento y con la puerta abierta)
def caja(nombre, c, t, mat, bisel=0.02):
    bpy.ops.mesh.primitive_cube_add(size=1, location=c); o = bpy.context.object; o.name = nombre
    o.scale = t; bpy.ops.object.transform_apply(scale=True)
    if bisel:
        b = o.modifiers.new('bisel', 'BEVEL'); b.width = bisel; b.segments = 2
    o.data.materials.append(mat); return o
def material(nombre, color, rug, met=0.0, emis=None, alfa=1.0):
    m = bpy.data.materials.new(nombre)
    try:
        m.use_nodes = True
    except Exception:
        pass
    nt = m.node_tree
    p = nt.nodes.get('Principled BSDF') or nt.nodes.new('ShaderNodeBsdfPrincipled')
    out = nt.nodes.get('Material Output') or nt.nodes.new('ShaderNodeOutputMaterial')
    nt.links.new(p.outputs[0], out.inputs[0])
    p.inputs['Base Color'].default_value = (*color, 1); p.inputs['Roughness'].default_value = rug; p.inputs['Metallic'].default_value = met
    if emis:
        p.inputs['Emission Color'].default_value = (*emis, 1); p.inputs['Emission Strength'].default_value = 1.0
    if alfa < 1:
        p.inputs['Alpha'].default_value = alfa
        try:
            m.surface_render_method = 'BLENDED'
        except Exception:
            pass
    m.use_backface_culling = False
    return m
tela = material('car_interior', (0.035, 0.032, 0.03), 0.9)
plastico = material('car_dash', (0.02, 0.02, 0.022), 0.6)
ancho = mx.x - mn.x; largo = mx.y - mn.y
x_cond = 0.42 * (ancho / 2)   # el conductor va a la izquierda (+X)
y_parabrisas = y_frente_puerta + 0.08
z_piso = mn.z + 0.18
piso = caja('car_floor', V((0, 0.0, z_piso)), V((ancho - 0.2, 2.0, 0.04)), tela, 0)
# tablero (CLAUDE.md §2.68): antes era una caja de 0.30 x 0.30 m que desde el asiento se leia como un bloque
# rectangular. Ahora es un perfil lateral (plano Y-Z: Y crece hacia el conductor, el frente del auto es -Y) extruido
# a lo ancho: la cara superior baja hacia la base del parabrisas, un canto redondeado sobre los indicadores y la
# cara frontal se retira hacia las rodillas. El bisel suaviza todas las aristas. z0 = altura del techo de la caja
# anterior, para que la linea del tablero contra el parabrisas no cambie.
z0_tab = techo_en(y_parabrisas + 0.33) - 0.30
PERFIL_TABLERO = [(0.06, -0.10), (0.06, -0.035), (0.22, -0.005), (0.36, 0.022), (0.43, 0.030), (0.475, 0.005),
                  (0.468, -0.105), (0.505, -0.225), (0.45, -0.33), (0.20, -0.33)]
def extruir_perfil(nombre, perfil, x0, x1, mat, bisel=0.018):
    bm_t = bmesh.new()
    cara0 = [bm_t.verts.new(V((x0, y_parabrisas + py, z0_tab + pz))) for py, pz in perfil]
    cara1 = [bm_t.verts.new(V((x1, y_parabrisas + py, z0_tab + pz))) for py, pz in perfil]
    bm_t.faces.new(cara0[::-1]); bm_t.faces.new(cara1)
    n_ = len(perfil)
    for i in range(n_):
        j = (i + 1) % n_
        bm_t.faces.new((cara0[i], cara0[j], cara1[j], cara1[i]))
    bmesh.ops.recalc_face_normals(bm_t, faces=bm_t.faces)
    me_t = bpy.data.meshes.new(nombre); bm_t.to_mesh(me_t); bm_t.free()
    o_t = bpy.data.objects.new(nombre, me_t); bpy.context.scene.collection.objects.link(o_t)
    if bisel:
        b_t = o_t.modifiers.new('bisel', 'BEVEL'); b_t.width = bisel; b_t.segments = 3; b_t.limit_method = 'ANGLE'
    o_t.data.materials.append(mat)
    return o_t
tablero = extruir_perfil('car_dashboard', PERFIL_TABLERO, -(ancho - 0.25) / 2, (ancho - 0.25) / 2, plastico)
# visera sobre los indicadores, solo del lado del conductor: un arco bajo que hace sombra sobre las esferas
extruir_perfil('car_dashboard_visor', [(0.36, 0.022), (0.43, 0.068), (0.505, 0.055), (0.515, 0.020), (0.475, 0.005), (0.43, 0.030)],
               x_cond - 0.22, x_cond + 0.22, plastico, 0.012)
y_asiento = y_pilar_b - 0.08
for nombre, x in (('car_seat_driver', x_cond), ('car_seat_passenger', -x_cond)):
    caja(nombre + '_base', V((x, y_asiento, z_piso + 0.22)), V((0.5, 0.5, 0.16)), tela)
    alto = techo_en(y_asiento + 0.3) - 0.14 - (z_piso + 0.3)
    caja(nombre + '_back', V((x, y_asiento + 0.3, z_piso + 0.3 + alto / 2)), V((0.5, 0.12, alto)), tela)
y_tras = y_pilar_b + 0.6
caja('car_seat_rear', V((0, y_tras, z_piso + 0.22)), V((ancho - 0.35, 0.5, 0.16)), tela)
alto_t = techo_en(y_tras + 0.3) - 0.16 - (z_piso + 0.3)
caja('car_seat_rear_back', V((0, y_tras + 0.3, z_piso + 0.3 + alto_t / 2)), V((ancho - 0.35, 0.12, alto_t)), tela)
OJO = V((x_cond, y_asiento + 0.16, techo_en(y_asiento) - 0.2))
print('OJO_CONDUCTOR', tuple(round(c, 3) for c in OJO))
bpy.ops.mesh.primitive_torus_add(major_radius=0.19, minor_radius=0.022, location=V((x_cond, y_parabrisas + 0.55, techo_en(y_parabrisas + 0.33) - 0.43)))
volante = bpy.context.object; volante.name = 'car_steering_wheel'
volante.rotation_euler = (math.radians(90 - 28), 0, 0); bpy.ops.object.transform_apply(rotation=True)
volante.data.materials.append(plastico)
interior = [o for o in bpy.data.objects if o.name.startswith(('car_floor', 'car_dashboard', 'car_seat'))]
for o in interior:
    for m in o.modifiers:
        bpy.context.view_layer.objects.active = o; bpy.ops.object.modifier_apply(modifier=m.name)
bpy.ops.object.select_all(action='DESELECT')
for o in interior:
    o.select_set(True)
bpy.context.view_layer.objects.active = interior[0]; bpy.ops.object.join(); interior[0].name = 'car_interior'
bpy.context.view_layer.objects.active = door; bpy.ops.object.modifier_apply(modifier='grosor')

# --- materiales nocturnos
reemplazo = {
    'Blue': material('car_paint', (0.16, 0.025, 0.03), 0.32, 0.55),
    'Windows': material('car_glass', (0.02, 0.025, 0.03), 0.05, 0.0, alfa=0.42),
    'Black': material('car_trim', (0.012, 0.012, 0.012), 0.7),
    'Grey': material('car_chrome', (0.55, 0.55, 0.56), 0.25, 1.0),
    'Headlights': material('car_headlight', (0.9, 0.9, 0.85), 0.1, 0.0, emis=(0.0, 0.0, 0.0)),
    'TailLights': material('car_taillight', (0.35, 0.02, 0.02), 0.25, 0.0, emis=(0.0, 0.0, 0.0)),
}
for o in bpy.data.objects:
    if o.type != 'MESH':
        continue
    for i, m in enumerate(o.data.materials):
        if m and m.name in reemplazo:
            o.data.materials[i] = reemplazo[m.name]
    for p in o.data.polygons:
        p.use_smooth = True
    try:
        o.data.set_sharp_from_angle(angle=math.radians(35))
    except Exception:
        pass
# --- detalle v2 (CLAUDE.md §2.61): matriculas, retrovisores, manijas, escape, limpiaparabrisas, espejo interior,
# indicadores del tablero (material car_gauges, emisivo apagado: el juego lo enciende al arrancar) y sombra de contacto
cromo = reemplazo['Grey']; plast = plastico
placa_mat = material('car_plate', (0.82, 0.8, 0.7), 0.6)
for nombre, y in (('car_plate_front', mn.y - 0.005), ('car_plate_rear', mx.y + 0.005)):
    caja(nombre, V((0, y, mn.z + 0.32)), V((0.52, 0.012, 0.13)), placa_mat, 0)
    cu = bpy.data.curves.new(nombre + '_txt', 'FONT'); cu.body = 'FAZ 1987'; cu.size = 0.075; cu.align_x = 'CENTER'; cu.align_y = 'CENTER'; cu.extrude = 0.002
    t = bpy.data.objects.new(nombre + '_txt', cu); bpy.context.scene.collection.objects.link(t)
    t.location = V((0, y + (-0.008 if y < 0 else 0.008), mn.z + 0.32)); t.rotation_euler = (math.radians(90), 0, math.radians(0 if y < 0 else 180))
    bpy.context.view_layer.objects.active = t; t.select_set(True); bpy.ops.object.convert(target='MESH'); t.select_set(False)
    t.data.materials.clear(); t.data.materials.append(material(nombre + '_ink', (0.03, 0.05, 0.25), 0.6))
for lado in (1, -1):
    xm = lado * (ancho / 2 + 0.06)
    caja('car_mirror_%d' % lado, V((xm, y_frente_puerta + 0.05, techo_en(y_frente_puerta) - 0.42)), V((0.13, 0.06, 0.09)), reemplazo['Blue'])
    caja('car_handle_%d' % lado, V((lado * (ancho / 2 + 0.005), y_pilar_b - 0.12, z_umbral + 0.42)), V((0.02, 0.14, 0.025)), cromo, 0.005)
bpy.ops.mesh.primitive_cylinder_add(radius=0.035, depth=0.22, vertices=12, location=V((-0.55, mx.y + 0.02, mn.z + 0.12)))
esc = bpy.context.object; esc.name = 'car_exhaust'; esc.rotation_euler = (math.radians(90), 0, 0); esc.data.materials.append(cromo)
for k, xw in enumerate((-0.32, 0.32)):
    w_ = caja('car_wiper_%d' % k, V((xw, y_parabrisas - 0.02, techo_en(y_parabrisas) - 0.55)), V((0.5, 0.015, 0.012)), plast, 0)
    w_.rotation_euler.y = math.radians(-12 if k == 0 else 12)
caja('car_rearview', V((0, y_parabrisas + 0.42, techo_en(y_parabrisas + 0.42) - 0.08)), V((0.22, 0.03, 0.06)), plast, 0.01)
# indicadores: panel delgado en la cara trasera del tablero (no asoma por encima), con una textura emisiva de dos
# esferas con aguja; el juego multiplica el emisivo por su color al arrancar (apagado = 0)
img_g = bpy.data.images.new('tex_car_gauges', 256, 64, alpha=False)
import numpy as _npg
gy, gx = _npg.mgrid[0:64, 0:256].astype(_npg.float32)
lum = _npg.zeros_like(gx)
for cx, ang in ((64, 2.2), (192, 0.9)):
    dx, dy = gx - cx, gy - 32; r = _npg.sqrt(dx * dx + dy * dy)
    lum = _npg.maximum(lum, _npg.clip(1 - _npg.abs(r - 26) / 2.2, 0, 1))                 # aro
    a_t = _npg.arctan2(dy, dx)
    for k in range(9):                                                                         # marcas
        t_ang = math.pi * (0.85 + 1.3 * k / 8)
        d_ang = _npg.abs(_npg.angle(_npg.exp(1j * (a_t - t_ang))))
        lum = _npg.maximum(lum, ((d_ang < 0.05) & (r > 19) & (r < 24)).astype(_npg.float32) * 0.8)
    nx, ny = math.cos(math.pi + ang), math.sin(math.pi + ang)                                  # aguja
    proy = dx * nx + dy * ny; perp = _npg.abs(dx * ny - dy * nx)
    lum = _npg.maximum(lum, ((proy > -3) & (proy < 22) & (perp < 1.4)).astype(_npg.float32))
    lum = _npg.maximum(lum, _npg.clip(1 - r / 30, 0, 1) * 0.18)                                # resplandor
img_g.pixels.foreach_set(_npg.stack([lum * 0.45, lum, lum * 0.62, _npg.ones_like(lum)], -1)[::-1].reshape(-1))
img_g.filepath_raw = os.path.join(SHOTS, 'tex_car_gauges.png'); img_g.file_format = 'PNG'; img_g.save()
gauges = material('car_gauges', (0.01, 0.012, 0.01), 0.5)
tg = gauges.node_tree.nodes.new('ShaderNodeTexImage'); tg.image = img_g
pg = gauges.node_tree.nodes['Principled BSDF']
gauges.node_tree.links.new(tg.outputs['Color'], pg.inputs['Emission Color']); pg.inputs['Emission Strength'].default_value = 1.0
# pegado a la cara del tablero nuevo (entre los puntos (0.475, 0.005) y (0.468, -0.105) del perfil), bajo la visera
z_g = z0_tab - 0.05
y_g = y_parabrisas + 0.475 - (0.007 * (0.005 - (z_g - z0_tab)) / 0.11) + 0.004
bm_g = bmesh.new(); uv_g = bm_g.loops.layers.uv.new()
vs_g = [bm_g.verts.new(V((x_cond + dx, y_g, z_g + dz))) for dx, dz in ((0.15, -0.0375), (-0.15, -0.0375), (-0.15, 0.0375), (0.15, 0.0375))]
f_g = bm_g.faces.new(vs_g)
for l, uv in zip(f_g.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
    l[uv_g].uv = uv
me_g = bpy.data.meshes.new('car_gauges'); bm_g.to_mesh(me_g); bm_g.free(); me_g.materials.append(gauges)
o_g = bpy.data.objects.new('car_gauges', me_g); bpy.context.scene.collection.objects.link(o_g)
# sombra de contacto: quad con degradado radial (alfa) bajo el auto, se mueve con el
img = bpy.data.images.new('tex_car_shadow', 128, 128, alpha=True)
import numpy as _np
yy, xx = _np.mgrid[0:128, 0:128] / 127.0 * 2 - 1
rr = _np.sqrt((xx / 0.92) ** 2 + (yy / 0.97) ** 2)
al = _np.clip(1 - rr, 0, 1) ** 1.2 * 0.8
img.pixels.foreach_set(_np.stack([_np.zeros_like(al)] * 3 + [al], -1).astype(_np.float32).reshape(-1))
img.filepath_raw = os.path.join(SHOTS, 'tex_car_shadow.png'); img.file_format = 'PNG'; img.save()
sm = bpy.data.materials.new('car_contact_shadow'); sm.use_nodes = True
tx = sm.node_tree.nodes.new('ShaderNodeTexImage'); tx.image = img
pb = sm.node_tree.nodes['Principled BSDF']; pb.inputs['Base Color'].default_value = (0, 0, 0, 1)
sm.node_tree.links.new(tx.outputs['Alpha'], pb.inputs['Alpha'])
try:
    sm.surface_render_method = 'BLENDED'
except Exception:
    sm.blend_method = 'BLEND'
bpy.ops.mesh.primitive_plane_add(size=1, location=V((0, 0, mn.z - 0.19)))
so = bpy.context.object; so.name = 'car_shadow'; so.scale = (ancho + 0.5, largo + 0.6, 1); bpy.ops.object.transform_apply(scale=True)
so.data.materials.append(sm)
body.name = 'car_body'; w_fl.name = 'car_wheel_fl'; w_fr.name = 'car_wheel_fr'; w_r.name = 'car_wheels_rear'
raiz = bpy.data.objects.new('car', None); bpy.context.scene.collection.objects.link(raiz)
for o in bpy.data.objects:
    if o.type == 'MESH':
        mw = o.matrix_world.copy(); o.parent = raiz; o.matrix_world = mw
# el piso del auto apoyado en z=0 (las ruedas tocan el suelo)
zmin = min((o.matrix_world @ v.co).z for o in (w_fl, w_fr, w_r) for v in o.data.vertices)
raiz.location.z -= zmin
for o in bpy.data.objects:
    if o.type == 'MESH':
        print('PIEZA', o.name, 'origen', tuple(round(c, 3) for c in o.matrix_world.translation), 'caras', len(o.data.polygons))
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', export_yup=True, export_apply=True)
print('exportado', OUT)

# --- capturas de control
sc = bpy.context.scene; sc.render.engine = 'BLENDER_EEVEE'; sc.render.resolution_x = 900; sc.render.resolution_y = 506
w = bpy.data.worlds.new('w'); sc.world = w
nt = w.node_tree; bg = nt.nodes.get('Background') or nt.nodes.new('ShaderNodeBackground'); on = nt.nodes.get('World Output') or nt.nodes.new('ShaderNodeOutputWorld')
nt.links.new(bg.outputs[0], on.inputs[0]); bg.inputs[0].default_value = (0.25, 0.27, 0.3, 1)
L = bpy.data.lights.new('s', 'SUN'); L.energy = 3; lo = bpy.data.objects.new('s', L); sc.collection.objects.link(lo); lo.rotation_euler = (0.7, 0.3, 2.2)
cam = bpy.data.cameras.new('c'); co = bpy.data.objects.new('c', cam); sc.collection.objects.link(co); sc.camera = co
door.rotation_euler.z = math.radians(-65)   # puerta abierta para la captura
for i, (pos, mira, lente) in enumerate(((V((4.5, -3.5, 2.2)), V((0, 0, 0.6)), 35), (V((3.2, 0.8, 1.6)), V((0, -0.4, 0.7)), 24),
                                         (OJO + V((0, 0, raiz.location.z)), V((x_cond, -6, 0.9)), 20))):
    co.location = pos; co.rotation_euler = (mira - pos).to_track_quat('-Z', 'Y').to_euler(); cam.lens = lente
    sc.render.filepath = os.path.join(SHOTS, 'car_prep_%d.png' % i); bpy.ops.render.render(write_still=True)
