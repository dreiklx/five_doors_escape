# Fase 3 — Validación de assets estáticos en LibGDX (cerrada 2026-07-31)

Registro de la validación final de los 4 personajes (Freddy, Bonnie, Chica, Foxy) dentro del
proyecto LibGDX real, usando gdx-gltf. Ver `CLAUDE.md` del proyecto Swing (`FiveDoorsAtFreddys`)
para el contexto completo del roadmap; este archivo documenta únicamente lo verificado en esta
fase, dentro de este repositorio.

## Resultado por personaje

Los 4 se cargaron uno por uno con el mismo visor de validación (`FiveDoorsEscapeGame`), probando
sistemáticamente 4 rotaciones candidatas sobre `Scene.modelInstance.transform`: identidad, 180°X,
180°Y, 180°Z.

| Personaje | Carga sin errores/warnings | Materiales/texturas | `maxBones` = huesos del rig | 180°X | 180°Z |
|---|---|---|---|---|---|
| Freddy | ✅ | ✅ | ✅ 59 | ✅ correcto | ✅ correcto |
| Bonnie | ✅ | ✅ | ✅ 62 | ✅ correcto | ❌ sigue mal orientada |
| Chica  | ✅ | ✅ | ✅ 67 | ✅ correcto | ✅ correcto |
| Foxy   | ✅ | ✅ | ✅ 36 | ✅ correcto | ✅ correcto |

## Conclusión

- **Los 4 personajes cargan invertidos por defecto en gdx-gltf**, incluso en la pose de reposo sin
  ninguna animación reproduciéndose. Esto ocurre con el mismo archivo que Blender renderiza
  correctamente — es una diferencia de interpretación de gdx-gltf para este tipo de rig
  (`*.qc_skeleton`), no un defecto de los assets.
- **La rotación de 180° en el eje X es la única corrección que funciona para los 4 sin
  excepción.** 180°Z también corrige a Freddy, Chica y Foxy, pero falla específicamente en
  Bonnie, así que no sirve como corrección universal. 180°Y nunca corrige nada (no puede arreglar
  una inversión vertical).
- **La corrección se resuelve completamente en código**, aplicando la rotación al
  `ModelInstance` de cada `Scene` antes de renderizar. **Ningún asset original (`.glb`) fue
  modificado** para llegar a esta conclusión.
- Diferencias de escala confirmadas entre personajes (dimensiones "en bruto" del bounding box):
  Bonnie ~123 unidades de alto, Chica ~83, Foxy ~20, Freddy ~19 (sin su factor de escala interno
  de 0.01). Cada uno necesitará su propio factor de escala al ensamblarlos en la escena real de
  la Fase 4 — no es un problema, es una consecuencia esperada de venir de artistas/uploads
  independientes en Sketchfab.
- El ejemplo mínimo comiteado (`FiveDoorsEscapeGame.java`) sigue usando 180°Z para Freddy — sigue
  siendo válido para ese personaje específico. Al integrar Bonnie/Chica/Foxy en la Fase 4, usar
  180°X para los 4 por consistencia, ya que es la única corrección confirmada sin excepción.

## Nota metodológica

Una investigación previa (mismo día) había registrado que Foxy mostraba "una pose colapsada",
distinta de un simple problema de orientación. Esa conclusión fue un error del arnés de prueba
usado en ese momento (la cámara automática quedó demasiado cerca del modelo, ya que el bounding
box local de Foxy es mucho más compacto que el de los otros tres) — no un problema real del
asset. Con una distancia de cámara adecuada, Foxy se corrige exactamente igual que los demás.

## Estado de los 4 `.glb`

Ninguno de los cuatro se mantiene copiado dentro de `assets/` de este proyecto de forma
permanente salvo `Freddy.glb` (el único usado por el ejemplo mínimo comiteado). Bonnie, Chica y
Foxy viven en `C:\Users\dfarl\Desktop\FDAF assets\3D Models\LibGDX_Ready\` y se copiarán a este
proyecto cuando la Fase 4 los integre a la escena real.
