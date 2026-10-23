# Titulo de la pantalla de carga de Five Doors Escape: "FIVE DOORS" + "ESCAPE" en letras 3D con la fuente integrada de
# Blender (DejaVu, licencia permisiva), renderizado con fondo transparente. El resplandor se agrega despues con
# make_loading_assets.py.
# Uso: blender -b --factory-startup -P build_loading_title.py -- <salida.png>
import bpy, sys, math
OUT = sys.argv[sys.argv.index('--') + 1]
bpy.ops.wm.read_factory_settings(use_empty=True)
sc = bpy.context.scene


def mat(nombre, color, emis, fuerza):
    m = bpy.data.materials.new(nombre); m.use_nodes = True
    p = m.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (*color, 1); p.inputs['Roughness'].default_value = 0.45
    p.inputs['Metallic'].default_value = 0.3
    p.inputs['Emission Color'].default_value = (*emis, 1); p.inputs['Emission Strength'].default_value = fuerza
    return m


def texto(cuerpo, tam, y, material, espaciado=1.0):
    cu = bpy.data.curves.new(cuerpo, 'FONT'); cu.body = cuerpo; cu.size = tam; cu.extrude = 0.06; cu.bevel_depth = 0.012
    cu.align_x = 'CENTER'; cu.align_y = 'CENTER'; cu.space_character = espaciado
    o = bpy.data.objects.new(cuerpo, cu); sc.collection.objects.link(o)
    o.location = (0, y, 0); o.rotation_euler = (math.radians(90), 0, 0)
    o.data.materials.append(material)
    return o


texto('FIVE DOORS', 0.62, 0, mat('blanco', (0.78, 0.74, 0.70), (0.85, 0.80, 0.75), 0.7), 1.15).location.z = 0.42
texto('ESCAPE', 1.05, 0, mat('rojo', (0.55, 0.03, 0.02), (1.0, 0.06, 0.03), 3.2), 1.25).location.z = -0.42
cam = bpy.data.cameras.new('c'); cam.type = 'ORTHO'; cam.ortho_scale = 5.2
co = bpy.data.objects.new('c', cam); sc.collection.objects.link(co); co.location = (0, -8, 0); co.rotation_euler = (math.radians(90), 0, 0)
sc.camera = co
luz = bpy.data.lights.new('l', 'AREA'); luz.energy = 400; luz.size = 4
lo = bpy.data.objects.new('l', luz); sc.collection.objects.link(lo); lo.location = (1.5, -5, 3); lo.rotation_euler = (math.radians(55), 0, math.radians(15))
sc.render.engine = 'CYCLES'; sc.cycles.samples = 48; sc.cycles.device = 'GPU'
sc.render.film_transparent = True
sc.render.resolution_x = 1400; sc.render.resolution_y = 560
sc.view_settings.view_transform = 'Standard'
sc.render.image_settings.file_format = 'PNG'; sc.render.image_settings.color_mode = 'RGBA'
sc.render.filepath = OUT
bpy.ops.render.render(write_still=True)
print('TITULO', OUT)
