import com.badlogic.gdx.*; import com.badlogic.gdx.backends.lwjgl3.*; import com.badlogic.gdx.graphics.*; import com.badlogic.gdx.graphics.GL20; import com.badlogic.gdx.files.*; import java.lang.reflect.*;
import com.fivedoorsescape.*; import com.fivedoorsescape.screens.*; import com.fivedoorsescape.ecs.*; import com.fivedoorsescape.ecs.components.*;
// Derrota con el arranque real. args: salida modo(ultima|saltar|normal) culpable(0=Freddy,1=Bonnie,2=Chica,3=Foxy)
// ultima/saltar: vidasRestantes=1 antes de la atrapada -> JUMPSCARE -> DERROTA_CINEMATICA -> NightGameOverScreen.
// normal: con 3 vidas -> JUMPSCARE -> ESTATICA -> CORAZONES_RESPAWN (sin cinematica).
public class PruebaDerrota extends FiveDoorsEscapeGame {
  String out, modo; int culpable; int fallos=0; long t0; String ultimo=""; boolean atrapado=false; float tCine=-1; int k=0; boolean esc=false;
  float[] marcas={2.6f,4.0f,6.5f,6.9f,7.2f,7.45f,7.8f,8.05f,8.3f,8.6f,8.8f,9.1f,9.45f,9.8f,10.1f,10.4f,10.9f};
  float[] marcasDiag={7.6f,9.6f}; int kd=0;
  /** Desde el ojo del jugador, encuadra la cabeza de cada actor con una luz auxiliar fuerte y guarda la imagen. */
  void diagnostico(Object c, float tm) throws Exception {
    com.badlogic.gdx.graphics.PerspectiveCamera cam=(com.badlogic.gdx.graphics.PerspectiveCamera)f("camera"); net.mgsx.gltf.scene3d.scene.SceneManager sm=(net.mgsx.gltf.scene3d.scene.SceneManager)f("sceneManager");
    com.badlogic.gdx.math.Vector3 p0=new com.badlogic.gdx.math.Vector3(cam.position), d0=new com.badlogic.gdx.math.Vector3(cam.direction), u0=new com.badlogic.gdx.math.Vector3(cam.up);
    Field fp=c.getClass().getDeclaredField("poses"); fp.setAccessible(true); Object[] poses=(Object[])fp.get(c);
    Field fi=c.getClass().getDeclaredField("ids"); fi.setAccessible(true); String[] ids=(String[])fi.get(c);
    net.mgsx.gltf.scene3d.lights.PointLightEx luz=new net.mgsx.gltf.scene3d.lights.PointLightEx(); luz.setColor(1,1,1,1); luz.intensity=30f; luz.range=6f; sm.environment.add(luz);
    Field fa=c.getClass().getDeclaredField("actores"); fa.setAccessible(true); Object[] act=(Object[])fa.get(c);
    for(int i=0;i<4;i++){ TransformComponent tr=Mappers.transform.get((com.badlogic.ashley.core.Entity)act[i]); com.badlogic.gdx.math.Vector3 hh=new com.badlogic.gdx.math.Vector3(); if(poses[i]!=null) poses[i].getClass().getMethod("cabezaMundo", com.badlogic.gdx.math.Vector3.class).invoke(poses[i],hh);
      System.out.println(String.format("  t=%.2f rol%d %s pos=(%.2f,%.2f,%.2f) yaw=%.0f cabeza=(%.2f,%.2f,%.2f) camara=(%.2f,%.2f,%.2f)", tm,i,ids[i],tr.position.x,tr.position.y,tr.position.z,tr.yawDegrees,hh.x,hh.y,hh.z,p0.x,p0.y,p0.z)); }
    Method antes=c.getClass().getMethod("antesDeAnimar"), despues=c.getClass().getMethod("despuesDeAnimar");
    for(int i=0;i<4;i++){ if(poses[i]==null) continue;
      for(int v=0; v<2; v++){
        if(v==0) antes.invoke(c); else despues.invoke(c);   // v=0: solo la animacion; v=1: con el movimiento procedural
        com.badlogic.gdx.math.Vector3 h=new com.badlogic.gdx.math.Vector3(); Method m=poses[i].getClass().getMethod("cabezaMundo", com.badlogic.gdx.math.Vector3.class); m.invoke(poses[i],h); h.y+=0.35f;
        com.badlogic.gdx.math.Vector3 ojo=new com.badlogic.gdx.math.Vector3(p0).lerp(h,0.4f);
        cam.position.set(ojo); cam.up.set(0,1,0); cam.lookAt(h); cam.update(); luz.position.set(ojo).lerp(h,0.25f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT); sm.render();
        int W=Gdx.graphics.getBackBufferWidth(),H=Gdx.graphics.getBackBufferHeight(); Pixmap px=Pixmap.createFromFrameBuffer(0,0,W,H);
        Pixmap fl=new Pixmap(W,H,Pixmap.Format.RGBA8888); for(int y=0;y<H;y++) fl.drawPixmap(px,0,y,W,1,0,H-1-y,W,1);
        PixmapIO.writePNG(new FileHandle(out+String.format("/diag_%s_rol%d_%05.2f_%s.png",ids[i],i,tm,v==0?"sin":"con")),Opaco.aplicar(fl)); px.dispose(); fl.dispose(); } }
    sm.environment.remove(luz); cam.position.set(p0); cam.direction.set(d0); cam.up.set(u0); cam.update(); }
  public PruebaDerrota(String[] a){ out=a[0]; modo=a[1]; culpable=Integer.parseInt(a[2]); }
  public static void main(String[] a){ Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration(); c.setWindowedMode(1280,720); c.setInitialVisible(false); new Lwjgl3Application(new PruebaDerrota(a),c); }
  void ok(boolean b,String s){ System.out.println((b?"OK    ":"FALLO ")+s); if(!b) fallos++; }
  public void create(){ final Input real=Gdx.input; Gdx.input=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class},(p,m,ar)->{ String n=m.getName(); if(n.equals("setCursorCatched")) return null; if(n.startsWith("getDelta")) return 0;
      if(n.equals("isKeyPressed")) return esc && (Integer)ar[0]==Input.Keys.ESCAPE; if(n.startsWith("isKey")) return false; return m.invoke(real,ar);}); t0=System.nanoTime(); super.create(); }
  Object f(String n) throws Exception { Field x=GameplayScreen.class.getDeclaredField(n); x.setAccessible(true); return x.get(getScreen()); }
  void set(String n,Object v) throws Exception { Field x=GameplayScreen.class.getDeclaredField(n); x.setAccessible(true); x.set(getScreen(),v); }
  void fin(){ System.out.println("FALLOS: "+fallos); Gdx.app.exit(); }
  public void render(){
    String antes = getScreen()==null?"":getScreen().getClass().getSimpleName();
    try { super.render(); } catch(Throwable t){ t.printStackTrace(); fallos++; }
    try {
      if(getScreen() instanceof NightGameOverScreen){ ok(!modo.equals("normal"), "llega a NightGameOverScreen (tiempo de cinematica "+tCine+" s)");
        if(modo.equals("saltar")) ok(tCine>3.1f && tCine<3.4f, "mantener ESC 1.2 s la omite ("+tCine+" s, empezo a los 2.0)"); if(modo.equals("ultima")||modo.equals("diag")) ok(tCine>12.0f && tCine<12.8f, "dura lo previsto");
        fin(); return; }
      if(!(getScreen() instanceof GameplayScreen)) return;
      String e=f("estado").toString();
      if(!e.equals(ultimo)){ System.out.println(String.format("%6d ms  %s vidas=%s", (System.nanoTime()-t0)/1000000, e, f("vidasRestantes"))); ultimo=e; }
      if(e.equals("CINEMATICA_INICIAL")){ Class<?> en=Class.forName("com.fivedoorsescape.screens.GameplayScreen$EstadoPartida"); for(Object o: en.getEnumConstants()) if(o.toString().equals("JUGANDO")) set("estado",o); }
      if(e.equals("JUGANDO") && !atrapado){ atrapado=true; if(!modo.equals("normal")) set("vidasRestantes",1);
        Object ai=((com.badlogic.gdx.utils.Array<?>)f("guardias")).get(culpable); ((AIComponent)ai).jugadorAtrapado=true; }
      if(e.equals("ESTATICA")) { ok(modo.equals("normal"), "con vidas de sobra sigue la estatica normal"); }
      if(e.equals("CORAZONES_RESPAWN") && modo.equals("normal")){ ok(true,"atrapada normal: corazones y reaparicion, sin cinematica"); fin(); return; }
      if(e.equals("DERROTA_CINEMATICA")){
        ok(!modo.equals("normal"), "ultima vida: DERROTA_CINEMATICA en lugar de la estatica");
        Object c=f("cinematicaDerrota"); Method g=c.getClass().getMethod("getTiempo"); tCine=(Float)g.invoke(c);
        if(modo.equals("saltar") && tCine>2.0f) esc=true;
        if(modo.equals("diag") && kd<marcasDiag.length && tCine>=marcasDiag[kd]){ diagnostico(c, marcasDiag[kd]); kd++; }
        if(modo.equals("ultima") && k<marcas.length && tCine>=marcas[k]){ int W=Gdx.graphics.getBackBufferWidth(),H=Gdx.graphics.getBackBufferHeight(); Pixmap px=Pixmap.createFromFrameBuffer(0,0,W,H);
          Pixmap fl=new Pixmap(W,H,Pixmap.Format.RGBA8888); for(int y=0;y<H;y++) fl.drawPixmap(px,0,y,W,1,0,H-1-y,W,1);
          PixmapIO.writePNG(new FileHandle(out+String.format("/d%02d_%04.1fs.png",k,marcas[k])),Opaco.aplicar(fl)); px.dispose(); fl.dispose(); k++; }
      }
    } catch(Exception ex){ ex.printStackTrace(); fallos++; }
    if((System.nanoTime()-t0)/1e9 > 90){ System.out.println("FALLO tiempo agotado en "+ultimo); fallos++; fin(); }
  }
}
