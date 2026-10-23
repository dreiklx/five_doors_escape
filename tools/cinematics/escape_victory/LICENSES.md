# Recursos externos de la victoria de Five Doors Escape

Todos los recursos de terceros que entran en `assets/models/exterior_parking.glb` y `assets/models/car_sedan.glb`.
Se comprobaron en la fuente oficial el 2026-10-23. Ninguno exige atribución, pero queda registrada.

| Recurso | Autor | Fuente | Licencia | Uso |
|---|---|---|---|---|
| Car (NormalCar1) | Quaternius | poly.pizza/m/Cz6yDaUcM9 (GLB `static.poly.pizza/59a67a6c-490e-472e-bae6-5a4d2541f1c7.glb`) | CC0 1.0 (dominio público), según la página del modelo | Auto de la huida. Adaptado con `prep_car.py`: escala, puerta del conductor separada con pivote en la bisagra, ruedas, volante e interior propios, materiales nocturnos |
| street_lamp_01 | Josh Dean | polyhaven.com | CC0 | Faroles del estacionamiento |
| modular_chainlink_fence | James Ray Cock, Amal Kumar | polyhaven.com | CC0 | Valla (postes y paneles dobles) |
| covered_car | MP | polyhaven.com | CC0 | Auto tapado estacionado |
| metal_trash_can | GurJas Studios | polyhaven.com | CC0 | Tachos junto a la fachada |
| trashbag | Benny Weimer | polyhaven.com | CC0 | Bolsas de basura |
| cardboard_box_01 | Rahul Chaudhary | polyhaven.com | CC0 | Cajas |
| utility_box_01 | James Ray Cock | polyhaven.com | CC0 | Caja eléctrica |
| concrete_road_barrier_02 | Amal Kumar | polyhaven.com | CC0 | Bloques junto al portón |
| water_manhole_cover | Raunox | polyhaven.com | CC0 | Tapa de alcantarilla |
| industrial_wall_lamp | Kuutti Siitonen | polyhaven.com | CC0 | Lámpara sobre la puerta de emergencia |
| security_camera_01 | Alexander Otterbeck, Yann Kervran | polyhaven.com | CC0 | Cámara de la esquina |
| security_light | Maximilian Schuster | polyhaven.com | CC0 | Reflector de la fachada |
| exterior_aircon_unit | Monsta3D | polyhaven.com | CC0 | Aire acondicionado (variante oxidada) |
| asphalt_02 (textura) | Rob Tuytel | polyhaven.com | CC0 | Asfalto |
| brick_wall_08 (textura) | Amal Kumar | polyhaven.com | CC0 | Fachada de ladrillo |
| concrete_pavement (textura) | Charlotte Baglioni | polyhaven.com | CC0 | Vereda |
| shrub_03 | Rico Cilliers | polyhaven.com | CC0 | Arbustos junto a la valla (detalle v2) |
| weed_plant_02 | Rob Tuytel, Rico Cilliers | polyhaven.com | CC0 | Maleza en grietas y bordes (detalle v2) |
| nettle_plant | Rob Tuytel, Rico Cilliers | polyhaven.com | CC0 | Ortigas junto a la fachada (detalle v2) |
| dead_tree_trunk_02 | Jenelle van Heerden, Rico Cilliers | polyhaven.com | CC0 | Tronco seco junto al portón (detalle v2) |
| wooden_crate_02 | James Ray Cock, Jurita Burger | polyhaven.com | CC0 | Cajas de madera junto al cierre de carga (detalle v2) |
| plastic_crate_01 | PierreB3D | polyhaven.com | CC0 | Cajones de plástico (detalle v2) |
| road_damaged (textura) | Dimitrios Savva | polyhaven.com | CC0 | Calle y parches de asfalto reparado (detalle v2) |
| metal_grate_rusty (textura) | Rob Tuytel, Dimitrios Savva | polyhaven.com | CC0 | Rejillas de desagüe (detalle v2) |
| painted_metal_shutter (textura) | Dario Barresi, Rico Cilliers, Charlotte Baglioni | polyhaven.com | CC0 | Cierre metálico "DELIVERIES" (detalle v2) |

Licencia de Poly Haven (polyhaven.com/license): "Our assets are all licensed as CC0"; uso comercial permitido, sin
obligación de dar crédito. Descargas por la API oficial (`api.polyhaven.com/files/<id>`, gltf 1k) con verificación MD5.

Hecho en el proyecto (sin terceros): fachada, vereda, líneas, puerta de emergencia, letreros (letras 3D con la fuente
integrada de Blender, DejaVu/Bitstream Vera, licencia permisiva), barrera del portón, siluetas lejanas y el cielo
nocturno (generado en código). Detalle v2 (CLAUDE.md §2.61): charcos, manchas de aceite, papeles, topes, flecha,
contenedor, ventanas, tuberías, respiraderos, poste y cables, letrero EMPLOYEE PARKING, sombras de contacto y charcos
de luz (texturas procedurales generadas por `build_exterior.py`); en el auto, matrículas, retrovisores, manijas,
escape, limpiaparabrisas, espejo, indicadores (textura generada) y sombra de contacto (`prep_car.py`). Los sonidos nuevos se sintetizan con `synth_victory_audio.py`; los que se reutilizan
(`heavy_breathing`, `heartbeat_fast`, `freddy_laugh`) ya estaban en el juego.

Originales descargados: `Desktop\FDAF assets\source_files\cinematics\escape_victory\downloads\`.
