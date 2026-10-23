# Construye el exterior de la pizzeria (estacionamiento trasero) para la victoria de Five Doors Escape y lo exporta
# como un unico GLB en coordenadas del juego. Lee la disposicion de assets/content/maps/exterior.json, el mismo
# archivo que usa el juego para colisiones y camaras, asi que nada esta duplicado.
#
# Uso: blender -b --factory-startup -P build_exterior.py -- <exterior.json> <carpeta poly haven> <carpeta texturas> <salida.glb>
#   <carpeta poly haven>: una subcarpeta por modelo descargado de Poly Haven (gltf 1k), ver LICENSES.md
#   <carpeta texturas>:   asphalt_02, brick_wall_08, concrete_pavement (1k)
import bpy, bmesh, mathutils, math, sys, os, json, glob, random

a = sys.argv[sys.argv.index('--') + 1:]
LAYOUT, PH, TEX, OUT = a[0], a[1], a[2], a[3]
L = json.load(open(LAYOUT, encoding='utf-8'))
V = mathutils.Vector
random.seed(1987)
bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene


def G(x, y, z):
    """Coordenadas del juego (Y arriba) -> Blender (Z arriba). El exportador glTF con export_yup hace la inversa."""
    return V((x, -z, y))


# ---------------------------------------------------------------- materiales
def mat_pbr(nombre, carpeta, prefijo, tinte=(1, 1, 1), rug_extra=0.0):
    m = bpy.data.materials.new(nombre)
    m.use_nodes = True
    nt = m.node_tree
    p = nt.nodes['Principled BSDF']
    d = os.path.join(TEX, carpeta)
    dif = nt.nodes.new('ShaderNodeTexImage'); dif.image = bpy.data.images.load(os.path.join(d, prefijo + '_diff_1k.jpg'))
    if tinte != (1, 1, 1):
        mix = nt.nodes.new('ShaderNodeMix'); mix.data_type = 'RGBA'; mix.blend_type = 'MULTIPLY'
        mix.inputs['Factor'].default_value = 1.0; mix.inputs['B'].default_value = (*tinte, 1)
        nt.links.new(dif.outputs['Color'], mix.inputs['A']); nt.links.new(mix.outputs['Result'], p.inputs['Base Color'])
    else:
        nt.links.new(dif.outputs['Color'], p.inputs['Base Color'])
    rug = nt.nodes.new('ShaderNodeTexImage'); rug.image = bpy.data.images.load(os.path.join(d, prefijo + '_rough_1k.jpg'))
    rug.image.colorspace_settings.name = 'Non-Color'
    nt.links.new(rug.outputs['Color'], p.inputs['Roughness'])
    nor = nt.nodes.new('ShaderNodeTexImage'); nor.image = bpy.data.images.load(os.path.join(d, prefijo + '_nor_1k.jpg'))
    nor.image.colorspace_settings.name = 'Non-Color'
    nm = nt.nodes.new('ShaderNodeNormalMap'); nt.links.new(nor.outputs['Color'], nm.inputs['Color'])
    nt.links.new(nm.outputs['Normal'], p.inputs['Normal'])
    p.inputs['Metallic'].default_value = 0.0
    return m


def mat_plano(nombre, color, rug=0.6, met=0.0, emis=None, fuerza=1.0):
    m = bpy.data.materials.new(nombre)
    m.use_nodes = True
    p = m.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (*color, 1)
    p.inputs['Roughness'].default_value = rug
    p.inputs['Metallic'].default_value = met
    if emis:
        p.inputs['Emission Color'].default_value = (*emis, 1)
        p.inputs['Emission Strength'].default_value = fuerza
    return m


asfalto = mat_pbr('ext_asphalt', 'asphalt_02', 'asphalt_02', (0.55, 0.55, 0.58))
asfalto_calle = mat_pbr('ext_road', 'asphalt_02', 'asphalt_02', (0.32, 0.32, 0.34))
ladrillo = mat_pbr('ext_brick', 'brick_wall_08', 'brick_wall_08', (0.62, 0.55, 0.52))
vereda = mat_pbr('ext_sidewalk', 'concrete_pavement', 'concrete_pavement', (0.6, 0.6, 0.6))
pintura = mat_plano('ext_paint_lines', (0.75, 0.72, 0.6), 0.85)
pintura_amarilla = mat_plano('ext_paint_yellow', (0.7, 0.55, 0.08), 0.85)
remate = mat_plano('ext_parapet', (0.16, 0.15, 0.15), 0.8)
metal_puerta = mat_plano('ext_door_metal', (0.18, 0.22, 0.2), 0.45, 0.6)
metal_marco = mat_plano('ext_door_frame', (0.06, 0.06, 0.065), 0.5, 0.8)
barra = mat_plano('ext_push_bar', (0.6, 0.6, 0.6), 0.3, 1.0)
exit_caja = mat_plano('ext_exit_box', (0.05, 0.05, 0.05), 0.4)
exit_letras = mat_plano('ext_exit_letters', (1.0, 0.1, 0.06), 0.4, 0.0, (1.0, 0.08, 0.04), 6.0)
letrero_caja = mat_plano('ext_sign_box', (0.08, 0.07, 0.07), 0.6, 0.3)
letrero_on = mat_plano('ext_sign_lit', (1.0, 0.75, 0.35), 0.4, 0.0, (1.0, 0.62, 0.22), 4.0)
letrero_off = mat_plano('ext_sign_dead', (0.25, 0.2, 0.15), 0.5)
poste = mat_plano('ext_pole', (0.2, 0.2, 0.21), 0.5, 0.9)
silueta = mat_plano('ext_far_building', (0.035, 0.035, 0.045), 0.95)
ventana_luz = mat_plano('ext_far_window', (0.9, 0.7, 0.4), 0.5, 0.0, (1.0, 0.72, 0.35), 2.5)


def uv_mundo(obj, tam):
    """UV proporcional al tamano real (tam = metros por repeticion de la textura), por proyeccion segun la normal."""
    me = obj.data
    bm = bmesh.new(); bm.from_mesh(me)
    uv = bm.loops.layers.uv.verify()
    mw = obj.matrix_world
    for f in bm.faces:
        n = (mw.to_3x3() @ f.normal).normalized()
        for l in f.loops:
            w = mw @ l.vert.co
            if abs(n.z) > 0.7:
                l[uv].uv = (w.x / tam, w.y / tam)
            elif abs(n.x) > abs(n.y):
                l[uv].uv = (w.y / tam, w.z / tam)
            else:
                l[uv].uv = (w.x / tam, w.z / tam)
    bm.to_mesh(me); bm.free()


def caja(nombre, gmin, gmax, mat, uv=None):
    """Caja alineada a los ejes entre dos esquinas en coordenadas del juego."""
    a0, a1 = G(*gmin), G(*gmax)
    mn = V((min(a0.x, a1.x), min(a0.y, a1.y), min(a0.z, a1.z))); mx = V((max(a0.x, a1.x), max(a0.y, a1.y), max(a0.z, a1.z)))
    bpy.ops.mesh.primitive_cube_add(size=1, location=(mn + mx) / 2)
    o = bpy.context.object; o.name = nombre; o.scale = mx - mn
    bpy.ops.object.transform_apply(scale=True)
    o.data.materials.append(mat)
    if uv:
        uv_mundo(o, uv)
    return o


def plano_suelo(nombre, x0, z0, x1, z1, y, mat, tam):
    bm = bmesh.new()
    vs = [bm.verts.new(G(x, y, z)) for x, z in ((x0, z0), (x1, z0), (x1, z1), (x0, z1))]
    f = bm.faces.new(vs)
    if f.normal.z < 0:
        f.normal_flip()
    me = bpy.data.meshes.new(nombre); bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new(nombre, me); sc.collection.objects.link(o)
    o.data.materials.append(mat); uv_mundo(o, tam)
    return o


# ---------------------------------------------------------------- suelo, vereda, calle
X0, Z0, Z1 = L['lotMinX'], L['lotMinZ'], L['lotMaxZ']
WX = L['wallX']
plano_suelo('ext_ground_lot', X0 - 0.5, Z0 + 0.3, WX + 0.05, Z1 + 0.6, -0.10, asfalto, 4.0)
# alrededor del estacionamiento (afuera de la valla): mas oscuro, se pierde en la niebla
plano_suelo('ext_ground_out_w', -70.0, -45.0, X0 - 0.5, 30.0, -0.11, asfalto_calle, 5.0)
plano_suelo('ext_ground_out_s', X0 - 0.5, -45.0, 10.0, Z0 + 0.3, -0.11, asfalto_calle, 5.0)
plano_suelo('ext_ground_out_n', X0 - 0.5, Z1 + 0.6, WX - 0.2, 30.0, -0.11, asfalto_calle, 5.0)
plano_suelo('ext_courtyard', WX - 0.2, Z0 + 0.3, 6.0, -8.0, -0.105, asfalto_calle, 5.0)
# vereda de hormigon pegada a la fachada oeste, con su cordon
VX = WX - 1.6
caja('ext_sidewalk', (VX, -0.10, -8.0), (WX, 0.0, Z1 + 0.6), vereda, 2.0)
caja('ext_curb_paint', (VX - 0.02, -0.10, -8.0), (VX + 0.03, 0.005, Z1 + 0.6), pintura_amarilla)
# lineas de estacionamiento (espacios orientados norte-sur, la trompa hacia el sur)
for x in (-24.2, -21.4, -18.6, -15.8):
    caja('ext_line_%d' % int(-x * 10), (x - 0.05, -0.098, -7.2), (x + 0.05, -0.094, -1.6), pintura)
caja('ext_line_back', (-24.2, -0.098, -1.65), (-15.8, -0.094, -1.55), pintura)
# calle detras del porton: dos franjas y linea central discontinua
RZ = L['roadZ']
for i in range(-60, 8, 3):
    caja('ext_road_dash_%d' % (i + 100), (i, -0.105, RZ - 0.06), (i + 1.6, -0.1, RZ + 0.06), pintura_amarilla)

# ---------------------------------------------------------------- fachada de ladrillo
ALTO = 3.30          # sobresale del techo real (2.97) como parapeto
G_ = 0.07            # separacion de la pared real, evita el z-fighting
puerta_z0 = L['emergencyDoorZ'] - L['emergencyDoorWidth'] / 2
puerta_z1 = L['emergencyDoorZ'] + L['emergencyDoorWidth'] / 2
PH_ = L['emergencyDoorHeight']
xf = WX - G_
# fachada oeste: tres piezas alrededor del hueco de la puerta de emergencia
caja('ext_facade_w_s', (xf - 0.06, 0.0, -8.0 - G_), (xf, ALTO, puerta_z0), ladrillo, 1.2)
caja('ext_facade_w_n', (xf - 0.06, 0.0, puerta_z1), (xf, ALTO, -2.1 + G_), ladrillo, 1.2)
caja('ext_facade_w_top', (xf - 0.06, PH_, puerta_z0), (xf, ALTO, puerta_z1), ladrillo, 1.2)
# escalon de la fachada (z=-2.1) y tramo norte (x=-11.9)
caja('ext_facade_step', (xf - 0.06, 0.0, -2.1 + G_), (L['northFaceX'], ALTO, -2.1 + G_ + 0.06), ladrillo, 1.2)
caja('ext_facade_nw', (L['northFaceX'] - 0.06, 0.0, -2.1 + G_), (L['northFaceX'], ALTO, Z1 + 0.6), ladrillo, 1.2)
# caras sur visibles a traves de la valla
caja('ext_facade_s1', (xf - 0.06, 0.0, -8.0 - G_ - 0.06), (-4.5 + G_, ALTO, -8.0 - G_), ladrillo, 1.2)
caja('ext_facade_s2', (-4.5 + G_, 0.0, -10.1 - G_), (-4.5 + G_ + 0.06, ALTO, -8.0 - G_), ladrillo, 1.2)
caja('ext_facade_s3', (-4.5 + G_, 0.0, -10.1 - G_ - 0.06), (4.2, ALTO, -10.1 - G_), ladrillo, 1.2)
# remate superior (parapeto)
for nombre, a0, a1 in (('w', (xf - 0.14, ALTO, -8.0 - G_ - 0.08), (xf + 0.02, ALTO + 0.12, -2.1 + G_ + 0.06)),
                       ('nw', (L['northFaceX'] - 0.14, ALTO, -2.1 + G_), (L['northFaceX'] + 0.02, ALTO + 0.12, Z1 + 0.6)),
                       ('s1', (xf - 0.14, ALTO, -8.0 - G_ - 0.14), (-4.5 + G_ + 0.06, ALTO + 0.12, -8.0 - G_ + 0.02))):
    caja('ext_parapet_' + nombre, a0, a1, remate)

# ---------------------------------------------------------------- puerta de emergencia
mz = 0.08   # ancho del marco
caja('ext_door_frame_s', (xf - 0.10, 0.0, puerta_z0 - mz), (WX + 0.02, PH_ + mz, puerta_z0), metal_marco)
caja('ext_door_frame_n', (xf - 0.10, 0.0, puerta_z1), (WX + 0.02, PH_ + mz, puerta_z1 + mz), metal_marco)
caja('ext_door_frame_t', (xf - 0.10, PH_, puerta_z0), (WX + 0.02, PH_ + mz, puerta_z1), metal_marco)
caja('ext_door_sill', (xf - 0.12, -0.02, puerta_z0), (WX + 0.02, 0.012, puerta_z1), metal_marco)
# hoja: pivote en la bisagra norte, cara exterior en xf; se abre hacia afuera (+90 grados de yaw)
hoja_partes = [
    caja('door_leaf_panel', (xf - 0.055, 0.01, puerta_z0 + 0.01), (xf - 0.005, PH_ - 0.01, puerta_z1 - 0.01), metal_puerta),
    caja('door_leaf_bar', (xf + 0.0, 0.98, puerta_z0 + 0.10), (xf + 0.07, 1.06, puerta_z1 - 0.10), barra),
    caja('door_leaf_bar_l', (xf - 0.005, 0.95, puerta_z0 + 0.08), (xf + 0.075, 1.09, puerta_z0 + 0.13), barra),
    caja('door_leaf_bar_r', (xf - 0.005, 0.95, puerta_z1 - 0.13), (xf + 0.075, 1.09, puerta_z1 - 0.08), barra),
    caja('door_leaf_kick', (xf - 0.06, 0.02, puerta_z0 + 0.02), (xf - 0.05, 0.30, puerta_z1 - 0.02), barra),
]
bpy.ops.object.select_all(action='DESELECT')
for o in hoja_partes:
    o.select_set(True)
bpy.context.view_layer.objects.active = hoja_partes[0]
bpy.ops.object.join()
hoja = bpy.context.object; hoja.name = 'emergency_door_leaf'
bisagra = G(xf - 0.03, 0.0, puerta_z1 - 0.01)
hoja.data.transform(mathutils.Matrix.Translation(-(bisagra - hoja.location)))
hoja.location = bisagra
print('BISAGRA_EMERGENCIA juego', (round(xf - 0.03, 3), 0.0, round(puerta_z1 - 0.01, 3)))

# ---------------------------------------------------------------- letreros EXIT (afuera y adentro) con letras 3D
def texto(nombre, cuerpo, tam, mat, pos, rot_z, extru=0.01):
    cu = bpy.data.curves.new(nombre, 'FONT'); cu.body = cuerpo; cu.size = tam; cu.extrude = extru
    cu.align_x = 'CENTER'; cu.align_y = 'CENTER'
    o = bpy.data.objects.new(nombre, cu); sc.collection.objects.link(o)
    o.location = pos; o.rotation_euler = (math.radians(90), 0, rot_z)
    bpy.context.view_layer.objects.active = o; o.select_set(True)
    bpy.ops.object.convert(target='MESH')
    o = bpy.context.object; o.data.materials.clear(); o.data.materials.append(mat)
    o.select_set(False)
    return o


for lado, x_caja, signo in (('out', xf - 0.10, -1), ('in', L['wallInnerX'] + 0.005, 1)):
    yc = PH_ + 0.28
    caja('exit_sign_box_' + lado, (min(x_caja, x_caja + 0.10 * signo), yc - 0.11, L['emergencyDoorZ'] - 0.24),
         (max(x_caja, x_caja + 0.10 * signo), yc + 0.11, L['emergencyDoorZ'] + 0.24), exit_caja)
    # cara del letrero mirando hacia -X (afuera) o +X (adentro): el texto se escribe en el plano XZ de Blender y se gira
    t = texto('exit_sign_text_' + lado, 'EXIT', 0.17, exit_letras, G(x_caja + 0.10 * signo + 0.004 * signo, yc, L['emergencyDoorZ']),
              math.radians(-90 if signo < 0 else 90))

# ---------------------------------------------------------------- props de Poly Haven
# Cada modelo se importa UNA vez como plantilla (transformaciones aplicadas a la malla, objeto en el origen) y cada
# colocacion es una copia enlazada (comparte la malla): el GLB no duplica geometria ni texturas.
plantillas = {}
plantillas_col = bpy.data.collections.new('plantillas'); sc.collection.children.link(plantillas_col)


def plantilla(modelo, solo=None, centrar_xy=False):
    clave = (modelo, tuple(solo) if solo else None, centrar_xy)
    if clave in plantillas:
        return plantillas[clave]
    f = glob.glob(os.path.join(PH, modelo, '*.gltf'))[0]
    antes = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=f)
    nuevos = [o for o in bpy.data.objects if o not in antes]
    mallas = []
    for o in nuevos:
        if o.type == 'MESH' and (solo is None or o.name.split('.')[0] in solo):
            mallas.append(o)
    for o in mallas:
        mw = o.matrix_world.copy(); o.parent = None; o.matrix_world = mw
    for o in nuevos:
        if o not in mallas:
            bpy.data.objects.remove(o, do_unlink=True)
    bpy.ops.object.select_all(action='DESELECT')
    for o in mallas:
        o.data = o.data.copy()   # malla propia de la plantilla antes de aplicar la transformacion
        o.select_set(True)
    bpy.context.view_layer.objects.active = mallas[0]
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    if centrar_xy:
        pts = [v.co for o in mallas for v in o.data.vertices]
        cx = (min(p.x for p in pts) + max(p.x for p in pts)) / 2; cy = (min(p.y for p in pts) + max(p.y for p in pts)) / 2
        for o in mallas:
            o.data.transform(mathutils.Matrix.Translation(V((-cx, -cy, 0))))
    for o in mallas:
        for c in o.users_collection:
            c.objects.unlink(o)
        plantillas_col.objects.link(o)
    plantillas[clave] = mallas
    return mallas


def colocar(modelo, gx, gy, gz, yaw, escala=1.0, solo=None, centrar_xy=False):
    raiz = bpy.data.objects.new('prop_' + modelo, None); sc.collection.objects.link(raiz)
    for t in plantilla(modelo, solo, centrar_xy):
        o = t.copy(); sc.collection.objects.link(o); o.parent = raiz
    raiz.location = G(gx, gy, gz); raiz.rotation_euler = (0, 0, math.radians(yaw)); raiz.scale = (escala,) * 3
    return raiz


SUELO_PROP = {'trashbag': 0.0, 'cardboard_box_01': 0.0, 'utility_box_01': 0.0}
for p in L['props']:
    colocar(p['model'], p['x'], SUELO_PROP.get(p['model'], -0.10), p['z'], p['yaw'])

# faroles del estacionamiento
for i, lmp in enumerate(L['lamps']):
    colocar('street_lamp_01', lmp['x'], -0.10, lmp['z'], lmp['yaw'])
    caja('lamp_base_%d' % i, (lmp['x'] - 0.25, -0.10, lmp['z'] - 0.25), (lmp['x'] + 0.25, 0.05, lmp['z'] + 0.25), vereda)
# lampara de pared sobre la puerta de emergencia (mira hacia -X: yaw -90)
wl = L['wallLamp']
colocar('industrial_wall_lamp', wl['x'], wl['y'], wl['z'], -90.0)
# camara de seguridad en la esquina sudoeste, apuntando al estacionamiento
colocar('security_camera_01', xf - 0.12, 2.85, -7.75, -135.0)
colocar('security_light', xf - 0.10, 2.75, -1.0, -90.0)
# aire acondicionado en el tramo norte de la fachada (solo la variante oxidada)
colocar('exterior_aircon_unit', L['northFaceX'] - 0.25, 0.55, -0.6, -90.0, solo=['exterior_aircon_unit_rusted'])


# valla perimetral: postes cada ~1.95 m y paneles dobles entre ellos, con el porton abierto en la cara sur.
# El panel del modelo va a lo largo de su eje X local; yaw lo orienta segun el tramo.
def valla(xa, za, xb, zb, hueco=None):
    largo = math.hypot(xb - xa, zb - za)
    n = max(1, round(largo / 1.95))
    dx, dz = (xb - xa) / n, (zb - za) / n
    yaw = math.degrees(math.atan2(dz, dx))
    def en_hueco(px, pz, margen):
        return hueco and abs(pz - za) < 0.01 and abs(zb - za) < 0.01 and hueco[0] - margen < px < hueco[1] + margen
    for i in range(n + 1):
        px, pz = xa + dx * i, za + dz * i
        if not en_hueco(px, pz, -0.05):
            colocar('modular_chainlink_fence', px, -0.10, pz, 0, solo=['modular_chainlink_fence_post'], centrar_xy=True)
    for i in range(n):
        cx, cz = xa + dx * (i + 0.5), za + dz * (i + 0.5)
        if not en_hueco(cx, cz, 0.9):
            colocar('modular_chainlink_fence', cx, -0.10, cz, -yaw, solo=['modular_chainlink_fence_double'], centrar_xy=True)


valla(X0, Z0, WX, Z0, hueco=(L['gateMinX'], L['gateMaxX']))
valla(X0, Z1, X0, Z0)
valla(X0, Z1, L['northFaceX'], Z1)
valla(WX, -8.0 - G_ - 0.1, WX, Z0)
# porton: postes de los dos lados del hueco
for gx in (L['gateMinX'], L['gateMaxX']):
    colocar('modular_chainlink_fence', gx, -0.10, Z0, 0, solo=['modular_chainlink_fence_post'], centrar_xy=True)

# ---------------------------------------------------------------- barrera del porton (se levanta en la cinematica)
barrera_roja = mat_plano('ext_barrier_red', (0.55, 0.04, 0.03), 0.5)
barrera_blanca = mat_plano('ext_barrier_white', (0.75, 0.75, 0.72), 0.5)
amarillo = mat_plano('ext_barrier_post', (0.65, 0.5, 0.05), 0.55, 0.2)
PX = L['gateMinX'] + 0.18
caja('gate_barrier_post', (PX - 0.16, -0.10, Z0 - 0.16), (PX + 0.16, 1.12, Z0 + 0.16), amarillo)
caja('gate_barrier_rest', (L['gateMaxX'] - 0.24, -0.10, Z0 - 0.05), (L['gateMaxX'] - 0.14, 0.92, Z0 + 0.05), amarillo)
largo_brazo = L['gateMaxX'] - 0.2 - PX
segmentos = []
n_seg = 8
for k in range(n_seg):
    xa = PX + 0.12 + k * (largo_brazo - 0.12) / n_seg
    xb = PX + 0.12 + (k + 1) * (largo_brazo - 0.12) / n_seg
    segmentos.append(caja('gate_arm_seg_%d' % k, (xa, 0.97, Z0 - 0.045), (xb, 1.06, Z0 + 0.045), barrera_roja if k % 2 == 0 else barrera_blanca))
segmentos.append(caja('gate_arm_hub', (PX - 0.1, 0.93, Z0 - 0.09), (PX + 0.14, 1.10, Z0 + 0.09), amarillo))
bpy.ops.object.select_all(action='DESELECT')
for o in segmentos:
    o.select_set(True)
bpy.context.view_layer.objects.active = segmentos[0]
bpy.ops.object.join()
brazo = bpy.context.object; brazo.name = 'gate_barrier_arm'
pivote = G(PX, 1.015, Z0)
brazo.data.transform(mathutils.Matrix.Translation(-(pivote - brazo.location)))
brazo.location = pivote

# ---------------------------------------------------------------- letrero de la pizzeria junto a la calle
SX, SZ = X0 - 2.2, Z0 - 1.4
caja('sign_pole_a', (SX - 0.09, -0.11, SZ - 1.3), (SX + 0.09, 5.2, SZ - 1.12), poste)
caja('sign_pole_b', (SX - 0.09, -0.11, SZ + 1.12), (SX + 0.09, 5.2, SZ + 1.3), poste)
caja('sign_box', (SX - 0.22, 4.2, SZ - 1.6), (SX + 0.22, 5.6, SZ + 1.6), letrero_caja)
palabras = [('FREDDY', 5.25), ("FAZBEAR'S", 4.9), ('PIZZA', 4.5)]
apagadas = {('FREDDY', 3), ("FAZBEAR'S", 1), ("FAZBEAR'S", 6), ('PIZZA', 4)}   # letras quemadas
for palabra, y in palabras:
    ancho = 0.27 * len(palabra)
    for k, ch in enumerate(palabra):
        if ch == "'":
            continue
        mat = letrero_off if (palabra, k) in apagadas else letrero_on
        # letrero de dos caras: la que mira al estacionamiento (+X) se lee hacia -Z; la que mira a la calle (-X), hacia +Z
        texto('sign_letter_%s_%d_e' % (palabra, k), ch, 0.30, mat, G(SX + 0.225, y, SZ + ancho / 2 - (k + 0.5) * 0.27), math.radians(90), 0.02)
        texto('sign_letter_%s_%d_w' % (palabra, k), ch, 0.30, mat, G(SX - 0.225, y, SZ - ancho / 2 + (k + 0.5) * 0.27), math.radians(-90), 0.02)

# ---------------------------------------------------------------- siluetas lejanas (barrio dormido)
for i in range(9):
    bx = -58 + random.uniform(-3, 3); bz = -36 + i * 8.5 + random.uniform(-1.5, 1.5)
    alto = random.uniform(5, 11); prof = random.uniform(4, 7); ancho = random.uniform(5, 8)
    caja('far_building_%d' % i, (bx - prof, -0.11, bz - ancho / 2), (bx, alto, bz + ancho / 2), silueta)
    for k in range(random.randint(0, 3)):
        wy = random.uniform(1.5, alto - 1.5); wz = bz + random.uniform(-ancho / 2 + 0.8, ancho / 2 - 0.8)
        caja('far_window_%d_%d' % (i, k), (bx + 0.01, wy, wz - 0.4), (bx + 0.03, wy + 0.6, wz + 0.4), ventana_luz)


# ================================================================ detalle v2 (CLAUDE.md §2.61)
# Todo lo de esta seccion es decoracion sin colision salvo el contenedor (collider en exterior.json, "extraColliders").
import numpy as np

# texturas procedurales intermedias: fuera de assets/ (el GLB las lleva embebidas)
import tempfile
TMP = tempfile.mkdtemp(prefix="fde_ext_")


def textura_procedural(nombre, funcion, lado=256):
    """Genera una textura RGBA en disco a partir de funcion(x, y) -> (r, g, b, a) en [0, 1] (arrays numpy)."""
    yy, xx = np.mgrid[0:lado, 0:lado] / (lado - 1.0) * 2 - 1
    r, g, b, a = funcion(xx, yy)
    img = bpy.data.images.new(nombre, lado, lado, alpha=True)
    px = np.stack([r, g, b, a], axis=-1).astype(np.float32)[::-1].reshape(-1)
    img.pixels.foreach_set(px)
    ruta = os.path.join(TMP, nombre + '.png')
    img.filepath_raw = ruta; img.file_format = 'PNG'; img.save()
    return img


def mat_decal(nombre, img, color=(1, 1, 1), emis=None, fuerza=0.0, rug=0.9):
    m = bpy.data.materials.new(nombre); m.use_nodes = True
    nt = m.node_tree; p = nt.nodes['Principled BSDF']
    tx = nt.nodes.new('ShaderNodeTexImage'); tx.image = img
    p.inputs['Base Color'].default_value = (*color, 1); p.inputs['Roughness'].default_value = rug
    nt.links.new(tx.outputs['Alpha'], p.inputs['Alpha'])
    if emis:
        p.inputs['Emission Color'].default_value = (*emis, 1); p.inputs['Emission Strength'].default_value = fuerza
    try:
        m.surface_render_method = 'BLENDED'
    except Exception:
        m.blend_method = 'BLEND'
    return m


def quad(nombre, cx, y, cz, ancho, largo, yaw, mat, uv_tam=None):
    """Quad horizontal en el suelo (coordenadas del juego), girado yaw grados."""
    bm = bmesh.new(); c, s = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    vs = []
    for dx, dz, u, v in ((-ancho / 2, -largo / 2, 0, 0), (ancho / 2, -largo / 2, 1, 0), (ancho / 2, largo / 2, 1, 1), (-ancho / 2, largo / 2, 0, 1)):
        x = cx + dx * c + dz * s; z = cz - dx * s + dz * c
        vs.append(bm.verts.new(G(x, y, z)))
    f = bm.faces.new(vs)
    if f.normal.z < 0:
        f.normal_flip()
    uv = bm.loops.layers.uv.new()
    for l, (u, v) in zip(f.loops, ((0, 0), (1, 0), (1, 1), (0, 1))):
        l[uv].uv = (u, v)
    me = bpy.data.meshes.new(nombre); bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new(nombre, me); sc.collection.objects.link(o); o.data.materials.append(mat)
    if uv_tam:
        uv_mundo(o, uv_tam)
    return o


rng_d = random.Random(4242)
# --- texturas procedurales: sombra de contacto, charco de luz, mancha de aceite, papel
def f_sombra(x, y):
    r = np.sqrt(x * x + y * y); a = np.clip(1 - r, 0, 1) ** 1.6 * 0.75
    z = np.zeros_like(r); return z, z, z, a
def f_pozo(x, y):
    r = np.sqrt(x * x + y * y); a = np.clip(1 - r, 0, 1) ** 2.2 * 0.5
    return np.ones_like(r), np.full_like(r, 0.62), np.full_like(r, 0.32), a
def f_aceite(x, y):
    ang = np.arctan2(y, x); r = np.sqrt(x * x + y * y)
    borde = 0.62 + 0.18 * np.sin(ang * 3 + 1.3) + 0.1 * np.sin(ang * 7 + 0.4)
    a = np.clip((borde - r) * 5, 0, 1) * (0.55 + 0.25 * np.sin(x * 9 + y * 5))
    return np.full_like(r, 0.04), np.full_like(r, 0.03), np.full_like(r, 0.025), np.clip(a, 0, 0.85)
def f_papel(x, y):
    a = ((np.abs(x) < 0.82) & (np.abs(y) < 0.95)).astype(np.float32)
    lineas = (np.sin((y + 1) * 40) > 0.85) & (np.abs(x) < 0.6)
    tono = np.where(lineas, 0.45, 0.82); return tono, tono, tono * 0.92, a


mat_sombra = mat_decal('ext_contact_shadow', textura_procedural('tex_contact_shadow', f_sombra))
mat_pozo = mat_decal('ext_light_pool', textura_procedural('tex_light_pool', f_pozo), (0, 0, 0), (1.0, 0.62, 0.32), 0.9)
mat_aceite = mat_decal('ext_oil_stain', textura_procedural('tex_oil_stain', f_aceite), (0.012, 0.01, 0.008), rug=0.75)
mat_papel = mat_decal('ext_paper', textura_procedural('tex_paper', f_papel, 64), (0.85, 0.83, 0.76), rug=0.8)

# --- charcos de luz bajo cada farol (refuerzan la luz real de los focos, que no tiene sombras)
for i, lmp in enumerate(L['lamps']):
    quad('light_pool_%d' % i, lmp['x'], -0.094, lmp['z'], 6.4, 6.4, 0, mat_pozo)

# --- charcos de agua: superficie casi espejo, apenas por encima del asfalto, con borde irregular
agua = mat_plano('ext_puddle', (0.012, 0.014, 0.018), 0.05, 0.0)
for i, (cx, cz, ax, az, rot) in enumerate(((-17.5, -9.0, 1.3, 0.75, 20), (-21.2, -12.9, 1.05, 0.6, -15), (-25.4, -6.1, 0.85, 0.5, 40),
                                          (-14.9, -13.4, 0.95, 0.55, 5), (-19.1, -0.3, 0.6, 0.4, 70), (-23.1, -9.4, 0.55, 0.35, -30))):
    bm = bmesh.new(); n = 22; vs = []
    for k in range(n):
        ang = 2 * math.pi * k / n; rr = 1 + 0.18 * math.sin(ang * 3 + i) + 0.1 * math.sin(ang * 5 + 2 * i)
        lx, lz = math.cos(ang) * ax * rr, math.sin(ang) * az * rr
        c, s = math.cos(math.radians(rot)), math.sin(math.radians(rot))
        vs.append(bm.verts.new(G(cx + lx * c + lz * s, -0.093, cz - lx * s + lz * c)))
    f = bm.faces.new(vs)
    if f.normal.z < 0:
        f.normal_flip()
    me = bpy.data.meshes.new('puddle_%d' % i); bm.to_mesh(me); bm.free()
    o = bpy.data.objects.new('puddle_%d' % i, me); sc.collection.objects.link(o); o.data.materials.append(agua)

# --- manchas de aceite en los espacios de estacionamiento
for i, (cx, cz, t) in enumerate(((-22.8, -4.3, 1.1), (-17.2, -4.9, 0.9), (-20.0, -2.9, 0.8), (-24.4, -11.0, 1.2), (-16.1, -7.7, 0.7), (-25.3, -3.7, 0.6))):
    quad('oil_%d' % i, cx, -0.095, cz, t * 1.3, t, rng_d.uniform(0, 180), mat_aceite)

# --- parches de asfalto reparado y calle con textura de asfalto danado
parche = mat_pbr('ext_asphalt_patch', 'road_damaged', 'road_damaged', (0.42, 0.42, 0.44))
for i, (cx, cz, w_, l_, rot) in enumerate(((-18.6, -12.4, 2.5, 1.4, 7), (-25.0, -2.6, 1.7, 1.2, -4), (-14.9, -9.8, 1.2, 2.1, 0), (-22.5, -15.0, 1.6, 0.9, 12))):
    quad('asphalt_patch_%d' % i, cx, -0.0965, cz, w_, l_, rot, parche, 2.5)
calle = mat_pbr('ext_road_damaged', 'road_damaged', 'road_damaged', (0.36, 0.36, 0.38))
for o in bpy.data.objects:
    if o.name == 'ext_ground_out_s':
        o.data.materials[0] = calle

# --- rejillas de desague y topes de estacionamiento
rejilla = mat_pbr('ext_drain_grate', 'metal_grate_rusty', 'metal_grate_rusty', (0.7, 0.7, 0.7))
for i, (cx, cz, rot) in enumerate(((-19.6, -14.7, 0), (-14.35, -0.8, 90), (-14.35, -10.5, 90))):
    quad('drain_%d' % i, cx, -0.092, cz, 0.7, 0.42, rot, rejilla, 0.7)
hormigon = mat_pbr('ext_concrete_block', 'concrete_pavement', 'concrete_pavement', (0.55, 0.55, 0.55))
for cx in (-22.8, -17.2):
    caja('wheel_stop_%d' % int(-cx * 10), (cx - 0.8, -0.10, -7.05), (cx + 0.8, 0.04, -6.88), hormigon, 0.6)
# flecha pintada hacia el porton
bm = bmesh.new()
pts = [(-0.18, 1.2), (0.18, 1.2), (0.18, 0.0), (0.45, 0.0), (0.0, -0.75), (-0.45, 0.0), (-0.18, 0.0)]
vs = [bm.verts.new(G(-21.0 + px, -0.094, -12.6 + pz)) for px, pz in pts]
f = bm.faces.new(vs)
if f.normal.z < 0:
    f.normal_flip()
me = bpy.data.meshes.new('painted_arrow'); bm.to_mesh(me); bm.free()
o = bpy.data.objects.new('painted_arrow', me); sc.collection.objects.link(o); o.data.materials.append(pintura)

# --- contenedor de basura (geometria propia): cuerpo verde, tapas de plastico negro (una entreabierta), ruedas
verde = mat_plano('ext_dumpster_paint', (0.07, 0.16, 0.08), 0.55, 0.35)
negro = mat_plano('ext_dumpster_lid', (0.02, 0.02, 0.02), 0.7)
d = L['dumpster']
DX, DZ = d['x'], d['z']
caja('dumpster_body', (DX - 0.95, 0.02, DZ - 0.55), (DX + 0.95, 1.22, DZ + 0.55), verde, 1.0)
caja('dumpster_rim', (DX - 1.0, 1.18, DZ - 0.6), (DX + 1.0, 1.26, DZ + 0.6), verde)
caja('dumpster_lid_a', (DX - 0.98, 1.26, DZ - 0.6), (DX - 0.02, 1.31, DZ + 0.6), negro)
tapa = caja('dumpster_lid_b', (DX + 0.02, 1.26, DZ - 0.6), (DX + 0.98, 1.31, DZ + 0.6), negro)
tapa.rotation_euler.y = math.radians(14)   # entreabierta
for sx in (-0.8, 0.8):
    for sz in (-0.42, 0.42):
        caja('dumpster_wheel', (DX + sx - 0.06, -0.10, DZ + sz - 0.06), (DX + sx + 0.06, 0.04, DZ + sz + 0.06), negro)
quad('dumpster_shadow', DX, -0.093, DZ, 2.8, 1.9, 0, mat_sombra)
for k, (ox, oz, yy) in enumerate(((1.3, 0.3, 30), (1.1, -0.4, 120), (-1.35, 0.1, 75))):
    colocar('trashbag', DX + ox, -0.10, DZ + oz, yy)

# --- papeles y cartones sueltos
for k in range(14):
    px = rng_d.uniform(L['lotMinX'] + 1, L['wallX'] - 1.8); pz = rng_d.uniform(L['lotMinZ'] + 0.6, L['lotMaxZ'] - 0.6)
    if abs(px - L['carX']) < 1.4 and abs(pz - L['carZ']) < 2.8:
        continue
    quad('paper_%d' % k, px, -0.091, pz, 0.21, 0.28, rng_d.uniform(0, 360), mat_papel)
carton = mat_plano('ext_cardboard_flat', (0.32, 0.22, 0.12), 0.9)
for k, (cx, cz, rot) in enumerate(((-15.4, -13.6, 25), (-26.0, -14.6, -40))):
    o = caja('cardboard_flat_%d' % k, (cx - 0.4, -0.098, cz - 0.3), (cx + 0.4, -0.088, cz + 0.3), carton)

# --- cajas junto a la puerta de carga
colocar('wooden_crate_02', -13.55, 0.0, -2.75, 80)
colocar('plastic_crate_01', -13.15, 0.0, -4.35, 10)
colocar('plastic_crate_01', -13.15, 0.26, -4.35, 25)

# --- puerta de carga con cierre metalico enrollable y su letrero (fachada oeste)
cortina = mat_pbr('ext_shutter', 'painted_metal_shutter', 'painted_metal_shutter', (0.55, 0.57, 0.6))
sz0, sz1 = -3.95, -2.35
caja('shutter_frame_s', (xf - 0.12, 0.0, sz0 - 0.1), (xf, 2.45, sz0), metal_marco)
caja('shutter_frame_n', (xf - 0.12, 0.0, sz1), (xf, 2.45, sz1 + 0.1), metal_marco)
caja('shutter_box', (xf - 0.22, 2.3, sz0 - 0.1), (xf, 2.62, sz1 + 0.1), metal_marco)
cierre = caja('shutter_door', (xf - 0.07, 0.0, sz0), (xf - 0.02, 2.3, sz1), cortina)
uv_mundo(cierre, 1.1)
amarillo_sign = mat_plano('ext_sign_yellow', (0.75, 0.6, 0.08), 0.6)
caja('delivery_sign', (xf - 0.03, 2.72, -3.55), (xf - 0.01, 3.02, -2.75), amarillo_sign)
texto('delivery_text', 'DELIVERIES', 0.14, mat_plano('ext_sign_black', (0.02, 0.02, 0.02), 0.6), G(xf - 0.035, 2.87, -3.15), math.radians(-90), 0.005)

# --- ventanas altas con barrotes y vidrio oscuro
vidrio = mat_plano('ext_window_glass', (0.01, 0.012, 0.016), 0.08, 0.2)
for k, (wx, wz) in enumerate(((xf - 0.01, -7.1), (L['northFaceX'] - 0.07, 0.25))):
    caja('window_frame_%d' % k, (wx - 0.06, 1.85, wz - 0.48), (wx, 2.45, wz + 0.48), metal_marco)
    caja('window_glass_%d' % k, (wx - 0.065, 1.9, wz - 0.42), (wx - 0.055, 2.4, wz + 0.42), vidrio)
    for b in range(5):
        bz = wz - 0.36 + b * 0.18
        caja('window_bar_%d_%d' % (k, b), (wx - 0.1, 1.88, bz - 0.012), (wx - 0.07, 2.42, bz + 0.012), poste)

# --- tuberias y bajantes en la fachada, rejillas de ventilacion y respiraderos en el techo
def tubo(nombre, a, b, radio, mat):
    pa, pb = G(*a), G(*b)
    largo = (pb - pa).length
    bpy.ops.mesh.primitive_cylinder_add(radius=radio, depth=largo, vertices=10, location=(pa + pb) / 2)
    o = bpy.context.object; o.name = nombre
    o.rotation_euler = (pb - pa).to_track_quat('Z', 'Y').to_euler()
    o.data.materials.append(mat)
    return o


gris_tubo = mat_plano('ext_pipe', (0.28, 0.28, 0.29), 0.45, 0.7)
tubo('downpipe_sw', (xf - 0.08, -0.05, -7.92), (xf - 0.08, 3.25, -7.92), 0.055, gris_tubo)
tubo('downpipe_n', (L['northFaceX'] - 0.08, -0.05, 0.85), (L['northFaceX'] - 0.08, 3.25, 0.85), 0.055, gris_tubo)
tubo('conduit_w', (xf - 0.05, 2.78, -7.7), (xf - 0.05, 2.78, -2.25), 0.028, gris_tubo)
for k in range(6):
    zc = -7.5 + k * 1.05
    caja('conduit_bracket_%d' % k, (xf - 0.09, 2.74, zc - 0.02), (xf, 2.82, zc + 0.02), metal_marco)
for k, (vx, vz) in enumerate(((-11.4, -6.4), (-10.6, -3.4), (-11.2, -0.6))):
    caja('roof_vent_%d' % k, (vx - 0.35, 2.95, vz - 0.35), (vx + 0.35, 3.75, vz + 0.35), gris_tubo)
    caja('roof_vent_cap_%d' % k, (vx - 0.45, 3.75, vz - 0.45), (vx + 0.45, 3.82, vz + 0.45), metal_marco)
caja('wall_vent', (xf - 0.04, 0.35, -6.6), (xf - 0.01, 0.75, -6.1), rejilla)

# --- poste de luz electrica fuera de la valla y cableado contra el cielo
madera = mat_plano('ext_wood_pole', (0.16, 0.11, 0.07), 0.85)
PX, PZ = L['lotMinX'] - 1.7, -6.0
tubo('utility_pole', (PX, -0.11, PZ), (PX, 7.4, PZ), 0.14, madera)
caja('utility_pole_arm', (PX - 0.08, 6.8, PZ - 1.1), (PX + 0.08, 6.95, PZ + 1.1), madera)
for dz in (-0.95, 0.95):
    caja('insulator_%d' % int(dz * 10), (PX - 0.05, 6.95, PZ + dz - 0.04), (PX + 0.05, 7.1, PZ + dz + 0.04), vidrio)
cable_mat = mat_plano('ext_cable', (0.01, 0.01, 0.01), 0.6)


def cable(nombre, a, b, caida):
    cu = bpy.data.curves.new(nombre, 'CURVE'); cu.dimensions = '3D'; cu.bevel_depth = 0.012; cu.bevel_resolution = 1
    sp = cu.splines.new('POLY'); n = 16; sp.points.add(n - 1)
    pa, pb = G(*a), G(*b)
    for k in range(n):
        u = k / (n - 1); p = pa.lerp(pb, u); p.z -= caida * 4 * u * (1 - u)
        sp.points[k].co = (p.x, p.y, p.z, 1)
    o = bpy.data.objects.new(nombre, cu); sc.collection.objects.link(o); o.data.materials.append(cable_mat)
    bpy.context.view_layer.objects.active = o; o.select_set(True); bpy.ops.object.convert(target='MESH'); o.select_set(False)


cable('cable_to_building', (PX, 6.9, PZ - 0.95), (xf - 0.1, 3.2, -7.85), 0.9)
cable('cable_north', (PX, 6.9, PZ + 0.95), (PX, 6.9, PZ + 26), 1.4)
cable('cable_south', (PX, 6.9, PZ - 0.95), (PX, 6.9, PZ - 26), 1.4)
cable('cable_north_b', (PX, 7.05, PZ + 0.95), (PX, 7.05, PZ + 26), 1.2)

# --- letrero de estacionamiento para empleados
caja('parking_sign_post', (-25.62, -0.10, -0.92), (-25.56, 2.05, -0.86), poste)
caja('parking_sign_plate', (-25.9, 1.55, -0.86), (-25.28, 2.1, -0.84), mat_plano('ext_sign_white', (0.75, 0.75, 0.72), 0.5))
texto('parking_sign_text1', 'EMPLOYEE', 0.1, mat_plano('ext_sign_red_text', (0.5, 0.03, 0.02), 0.6), G(-25.59, 1.92, -0.835), math.radians(0), 0.004)
texto('parking_sign_text2', 'PARKING', 0.1, mat_plano('ext_sign_red_text2', (0.5, 0.03, 0.02), 0.6), G(-25.59, 1.74, -0.835), math.radians(0), 0.004)

# --- vegetacion: arbustos y maleza en la base de la valla y en grietas del asfalto
for k, (vx, vz, yy, var) in enumerate(((-26.55, -3.0, 20, 'shrub_03_a'), (-26.6, -9.6, 80, 'shrub_03_b'), (-26.5, 0.55, 150, 'shrub_03_c'),
                                       (-13.05, -15.55, 0, 'shrub_03_a'), (-14.4, 0.55, 260, 'shrub_03_b'), (-12.95, -9.4, 40, 'shrub_03_c'))):
    colocar('shrub_03', vx, -0.10, vz, yy, 1.3, solo=[var], centrar_xy=True)
for k, (vx, vz, yy, var) in enumerate(((-14.35, -2.7, 0, 'weed_plant_02_a_LOD0'), (-14.3, -7.65, 90, 'weed_plant_02_b_LOD0'), (-19.2, -15.75, 30, 'weed_plant_02_c_LOD0'),
                                       (-26.75, -13.8, 200, 'weed_plant_02_a_LOD0'), (-22.95, -7.25, 120, 'weed_plant_02_b_LOD0'), (-16.9, -1.65, 60, 'weed_plant_02_c_LOD0'))):
    colocar('weed_plant_02', vx, -0.10, vz, yy, 1.0, solo=[var], centrar_xy=True)
for vx, vz, var in ((-12.92, -8.25, 'nettle_plant_medium_a_LOD0'), (-26.7, -6.3, 'nettle_plant_medium_b_LOD0'), (-24.0, -15.8, 'nettle_plant_small_a_LOD0')):
    colocar('nettle_plant', vx, -0.10, vz, rng_d.uniform(0, 360), 1.0, solo=[var], centrar_xy=True)
colocar('dead_tree_trunk_02', L['lotMinX'] - 3.0, -0.11, -10.5, 30)

# --- sombras de contacto bajo lo que toca el suelo (alternativa barata a sombras de los faroles)
for p in L['props']:
    tam = {'covered_car': (2.1, 4.7), 'metal_trash_can': (2.1, 0.8), 'utility_box_01': (0.8, 0.7), 'concrete_road_barrier_02': (1.9, 0.7)}.get(p['model'])
    if tam:
        quad('shadow_' + p['model'], p['x'], -0.0935, p['z'], tam[0], tam[1], p['yaw'], mat_sombra)
for i, lmp in enumerate(L['lamps']):
    quad('shadow_lamp_%d' % i, lmp['x'], -0.0935, lmp['z'], 1.0, 1.0, 0, mat_sombra)

# ---------------------------------------------------------------- exportar
for o in bpy.data.objects:
    if o.type == 'LIGHT':
        bpy.data.objects.remove(o, do_unlink=True)
sc.collection.children.unlink(plantillas_col)
# Presupuesto de texturas: 1k solo para el color de las superficies grandes (suelo, ladrillo, vereda); el resto a
# 512 o 256. Medido: con todo a 1k el GLB pesaba 46 MB, 42 de ellos en imagenes.
GRANDES = ('asphalt_02', 'brick_wall_08', 'concrete_pavement')
for img in bpy.data.images:
    if img.size[0] == 0:
        continue
    n = img.name.lower()
    if n.startswith(GRANDES):
        lado = 1024 if '_diff' in n else 512
    elif '_diff' in n or 'opacity' in n or 'emissive' in n:
        lado = 512
    else:
        lado = 256
    if img.size[0] > lado:
        img.scale(lado, lado)   # las plantillas no se exportan; sus mallas siguen en uso por las copias
bpy.ops.export_scene.gltf(filepath=OUT, export_format='GLB', export_yup=True, export_apply=True,
                          export_lights=False, export_cameras=False, export_image_format='JPEG', export_jpeg_quality=82)
print('EXPORTADO', OUT, os.path.getsize(OUT) // 1024, 'KB', len([o for o in bpy.data.objects if o.type == 'MESH']), 'mallas')
