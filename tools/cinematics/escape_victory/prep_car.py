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
tablero = caja('car_dashboard', V((0, y_parabrisas + 0.33, techo_en(y_parabrisas + 0.33) - 0.30 - 0.15)), V((ancho - 0.25, 0.30, 0.30)), plastico)
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
