import com.badlogic.gdx.*; import com.badlogic.gdx.backends.lwjgl3.*; import com.badlogic.gdx.graphics.*; import com.badlogic.gdx.graphics.profiling.*; import com.badlogic.gdx.math.*; import com.badlogic.gdx.files.*;
import com.fivedoorsescape.assets.*; import com.fivedoorsescape.content.*; import com.fivedoorsescape.world.*; import com.fivedoorsescape.screens.*;
import com.fivedoorsescape.io.*; import com.fivedoorsescape.cinematics.*; import com.fivedoorsescape.ecs.*; import com.fivedoorsescape.ecs.components.*; import com.fivedoorsescape.ai.states.*;
import java.lang.reflect.*;
// Rendimiento con GameplayScreen real: tiempo por fotograma (render real, sin vsync), draw calls, cambios de shader,
// enlaces de textura y memoria Java, en las zonas pedidas por la auditoria. args: salida etiqueta
public class PruebaRendimiento extends Game {
  String out, etiqueta; GameplayScreen gs; GLProfiler prof; long cargaMs;
  PruebaRendimiento(String[] a){ out=a[0]; etiqueta=a[1]; }
  public static void main(String[] a){ Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration(); c.setWindowedMode(1280,720); c.setInitialVisible(false); c.setForegroundFPS(0); c.useVsync(false); new Lwjgl3Application(new PruebaRendimiento(a),c); }
  Object campo(String n) throws Exception { Field f=GameplayScreen.class.getDeclaredField(n); f.setAccessible(true); return f.get(gs); }
  void set(String n, Object v) throws Exception { Field f=GameplayScreen.class.getDeclaredField(n); f.setAccessible(true); f.set(gs,v); }
  void llamar(String n) throws Exception { Method m=GameplayScreen.class.getDeclaredMethod(n); m.setAccessible(true); m.invoke(gs); }
  public void create(){ final Input real=Gdx.input; Gdx.input=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class},(p,m,ar)->{ String n=m.getName(); if(n.equals("setCursorCatched")) return null; if(n.startsWith("getDelta")) return 0; if(n.startsWith("isKey")||n.equals("justTouched")||n.equals("isButtonPressed")) return false; return m.invoke(real,ar);});
    try { long t0=System.nanoTime(); ContentRegistry reg=new ContentRegistry(); AssetService as=new AssetService(); MapDefinition md=reg.getMapDefinition("pizzeria");
      as.queueModel(md.modelPath); for(String e: new String[]{"freddy","bonnie","chica","foxy","llave","golden_freddy"}) { as.queueModel(reg.getEntityDefinition(e).modelPath); for(String snd: com.fivedoorsescape.audio.PasosAnimatronicos.sonidosDe(reg.getEntityDefinition(e))) as.queueSound(snd); }
      for(Field f: GameplayScreen.class.getFields()) if(f.getName().startsWith("SONIDO_")) as.queueSound((String)f.get(null));
      ExteriorDefinition ex=reg.getExteriorDefinition(); as.queueModel(ex.modelPath); as.queueModel(ex.carModelPath);
      for(String s: CinematicaVictoria.SONIDOS) as.queueSound(s); for(String s: CinematicaDerrota.SONIDOS) as.queueSound(s);
      as.finishLoading(); long t1=System.nanoTime(); gs=new GameplayScreen(this, reg, as, new HandoffData(HandoffData.Idioma.ESPANOL, 3)); setScreen(gs); long t2=System.nanoTime();
      System.out.println(String.format("CARGA recursos %d ms, nivel %d ms", (t1-t0)/1000000, (t2-t1)/1000000));
      prof=new GLProfiler(Gdx.graphics); prof.enable();
      Class<?> en=Class.forName("com.fivedoorsescape.screens.GameplayScreen$EstadoPartida"); for(Object o: en.getEnumConstants()) if(o.toString().equals("JUGANDO")) set("estado",o); set("tiempoJugandoTranscurrido",100f);
      // nadie persigue salvo en la zona de persecucion
      for(Object o: (com.badlogic.gdx.utils.Array<?>)campo("guardias")){ AIComponent ai=(AIComponent)o; ai.siempreEnPersecucion=false; ai.rangoDeteccion=0.01f; }
    } catch(Exception e){ e.printStackTrace(); Gdx.app.exit(); } }
  String[][] zonas = { {"comedor","0","1.6","4","180"}, {"parts_service","-10.6","1.6","-5.2","-90"}, {"pirate_cove","-6.5","1.6","-0.5","-90"}, {"persecucion","-1.9","1.6","2","180"}, {"estacionamiento","-14","1.6","-5.2","-100"}, {"victoria","0","0","0","0"} };
  { if(System.getProperty("solo")!=null){ zonas=new String[][]{ {"estacionamiento","-14","1.6","-5.2","-100"} }; } }
  int zona=0, fr=0; double sumaMs=0, peorMs=0; int sumaDraw=0, sumaShader=0, sumaTex=0; long nAnterior=0;
  public void render(){
    try {
      if(zona>=zonas.length){ Gdx.app.exit(); return; }
      String[] z=zonas[zona];
      if(fr==0){ prepararZona(z); }
      prof.reset(); long a=System.nanoTime();
      Gdx.gl.glViewport(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
      if(System.getProperty("variante","").contains("resetBlend")) Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
      getScreen().render(1f/60f);
      Gdx.gl.glFinish(); double ms=(System.nanoTime()-a)/1e6;
      if(fr>=30){ sumaMs+=ms; peorMs=Math.max(peorMs,ms); sumaDraw+=prof.getDrawCalls(); sumaShader+=prof.getShaderSwitches(); sumaTex+=prof.getTextureBindings(); }
      fr++;
      if(fr==30+240 || !(getScreen() instanceof GameplayScreen)){ int n=Math.max(1,fr-30); Runtime rt=Runtime.getRuntime(); System.gc();
        System.out.println(String.format("ZONA %-16s %6.2f ms/f (%.0f fps)  peor %6.2f ms  draw %5d  shaders %4d  texturas %5d  heap %4d MB", z[0], sumaMs/n, 1000/(sumaMs/n), peorMs, sumaDraw/n, sumaShader/n, sumaTex/n, (rt.totalMemory()-rt.freeMemory())>>20));
        int W=Gdx.graphics.getBackBufferWidth(),H=Gdx.graphics.getBackBufferHeight(); Pixmap px=Pixmap.createFromFrameBuffer(0,0,W,H); Pixmap fl=new Pixmap(W,H,Pixmap.Format.RGBA8888); for(int y=0;y<H;y++) fl.drawPixmap(px,0,y,W,1,0,H-1-y,W,1); PixmapIO.writePNG(new FileHandle(out+"/"+etiqueta+"_"+z[0]+".png"),Opaco.aplicar(fl)); px.dispose(); fl.dispose();
        zona++; fr=0; sumaMs=0; peorMs=0; sumaDraw=sumaShader=sumaTex=0; }
    } catch(Exception e){ e.printStackTrace(); Gdx.app.exit(); } }
  void prepararZona(String[] z) throws Exception {
    TransformComponent t=Mappers.transform.get((com.badlogic.ashley.core.Entity)campo("playerEntity"));
    com.fivedoorsescape.camera.FirstPersonCameraController cc=(com.fivedoorsescape.camera.FirstPersonCameraController)campo("cameraController");
    if(z[0].equals("estacionamiento")){ set("tieneLlave",true); llamar("iniciarSalida"); llamar("abrirPuertaEmergencia");
      String v=System.getProperty("variante","");
      SalidaExterior se=(SalidaExterior)campo("salidaExterior");
      for(com.badlogic.gdx.graphics.g3d.Material m: se.getEscenaAuto().modelInstance.materials) if(v.contains("sinSombraAuto") && m.id.equals("car_contact_shadow")) m.set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(0f));
      for(com.badlogic.gdx.graphics.g3d.Material m: se.getEscenaExterior().modelInstance.materials) if(v.contains("sinDecal") && (m.id.startsWith("ext_oil")||m.id.startsWith("ext_paper")||m.id.startsWith("ext_contact")||m.id.startsWith("ext_light_pool"))) m.set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(0f));
      if(v.contains("lunaVieja")) ((net.mgsx.gltf.scene3d.lights.DirectionalShadowLight)campo("luzDireccional")).intensity=1.1f;
      if(v.contains("sinLuna")) ((net.mgsx.gltf.scene3d.lights.DirectionalShadowLight)campo("luzDireccional")).intensity=0f;
      for(com.badlogic.gdx.graphics.g3d.Material m: se.getEscenaExterior().modelInstance.materials) if(v.contains("rojo") && m.id.startsWith("ext_oil")) m.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(new Color(1,0,0,1)));
      if(v.contains("sinFog")) ((net.mgsx.gltf.scene3d.scene.SceneManager)campo("sceneManager")).environment.remove(net.mgsx.gltf.scene3d.attributes.FogAttribute.FogEquation);
      if(v.contains("sinFog")) ((net.mgsx.gltf.scene3d.scene.SceneManager)campo("sceneManager")).environment.remove(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Fog);
      for(com.badlogic.gdx.graphics.g3d.Material m: se.getEscenaExterior().modelInstance.materials) if(v.contains("attrs") && (m.id.startsWith("ext_oil")||m.id.startsWith("ext_ground_lot")||m.id.startsWith("asphalt"))){ StringBuilder sb=new StringBuilder("ATTR "+m.id+":"); for(com.badlogic.gdx.graphics.g3d.Attribute at: m) sb.append(" ").append(at.toString()); System.out.println(sb); }
      if(v.contains("attrs")){ StringBuilder sb=new StringBuilder("ENV:"); for(com.badlogic.gdx.graphics.g3d.Attribute at: ((net.mgsx.gltf.scene3d.scene.SceneManager)campo("sceneManager")).environment) sb.append(" ").append(com.badlogic.gdx.graphics.g3d.Attribute.getAttributeAlias(at.type)); System.out.println(sb); }
      for(com.badlogic.gdx.graphics.g3d.Material m: se.getEscenaExterior().modelInstance.materials) if(v.contains("opaco") && m.id.startsWith("ext_oil")) m.remove(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type);
      for(com.badlogic.gdx.graphics.g3d.Material m: se.getEscenaExterior().modelInstance.materials) if(m.id.startsWith("ext_ground")) System.out.println("GROUND "+m.id+" blend="+m.has(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type));
      if(v.contains("sinLinterna")) set("linternaEncendida", false);
      for(com.badlogic.gdx.graphics.g3d.Material m: se.getEscenaExterior().modelInstance.materials) if(v.contains("listar") && m.has(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type)) System.out.println("MATBLEND "+m.id);
    }
    if(z[0].equals("persecucion")){ for(Object o: (com.badlogic.gdx.utils.Array<?>)campo("guardias")){ AIComponent ai=(AIComponent)o; if(ai.id.equals("freddy")){ ai.siempreEnPersecucion=true; ai.stateMachine.changeState(ChaseState.INSTANCE);} } }
    if(z[0].equals("victoria")){ t.position.set(-21.95f,1.6f,-4.6f); llamar("subirAlAuto"); return; }
    t.position.set(Float.parseFloat(z[1]),Float.parseFloat(z[2]),Float.parseFloat(z[3])); cc.resetOrientation(Float.parseFloat(z[4]));
  }
}
