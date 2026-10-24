import com.badlogic.gdx.*; import com.badlogic.gdx.backends.lwjgl3.*; import com.badlogic.gdx.graphics.*; import com.badlogic.gdx.math.*; import com.badlogic.gdx.utils.*; import com.badlogic.gdx.files.*;
import com.fivedoorsescape.assets.*; import com.fivedoorsescape.content.*; import com.fivedoorsescape.world.*; import com.fivedoorsescape.screens.*;
import com.fivedoorsescape.io.*; import com.fivedoorsescape.cinematics.*; import com.fivedoorsescape.ecs.*; import com.fivedoorsescape.ecs.components.*; import com.fivedoorsescape.ai.states.*;
import java.lang.reflect.*; import java.util.Set; import java.util.HashSet; import java.util.Collections;
// Pruebas del mundo interactivo con GameplayScreen real. args: salida modo(puertas|sprint|aturdir|cortina|intro|bordes)
public class PruebaMundo extends Game {
  String out, modo; GameplayScreen gs; int fallos = 0; float dt = 1f / 60f;
  static volatile boolean clic=false; static volatile float ratonX=-1, ratonY=-1; static volatile boolean boton=false; static volatile Boolean ultimoCatched=null;
  static final Set<Integer> apretadas = Collections.synchronizedSet(new HashSet<>()); static final Set<Integer> pulsadas = Collections.synchronizedSet(new HashSet<>());
  PruebaMundo(String[] a){ out=a[0]; modo=a[1]; }
  public static void main(String[] a){ Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration(); c.setWindowedMode(1280,720); c.setInitialVisible(false); c.setForegroundFPS(0); c.useVsync(false); new Lwjgl3Application(new PruebaMundo(a),c); }
  void ok(boolean b, String s){ System.out.println((b?"OK    ":"FALLO ")+s); if(!b) fallos++; }
  Object campo(String n) throws Exception { Field f=GameplayScreen.class.getDeclaredField(n); f.setAccessible(true); return f.get(gs); }
  void set(String n, Object v) throws Exception { Field f=GameplayScreen.class.getDeclaredField(n); f.setAccessible(true); f.set(gs,v); }
  public void create(){
    final Input real=Gdx.input;
    Gdx.input=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class}, (p,m,args)->{ String n=m.getName();
      if(n.equals("setCursorCatched")){ ultimoCatched=(Boolean)args[0]; return null; } if(n.equals("isCursorCatched")) return true;
      if(n.equals("getX") && args==null && ratonX>=0) return (int)ratonX; if(n.equals("getY") && args==null && ratonY>=0) return (int)ratonY; if(n.equals("getDeltaX")||n.equals("getDeltaY")) return 0;
      if(n.equals("isKeyPressed")) return apretadas.contains((Integer)args[0]);
      if(n.equals("isKeyJustPressed")) return pulsadas.contains((Integer)args[0]);
      if(n.equals("justTouched")) return clic; if(n.equals("isButtonPressed")) return boton;
      return m.invoke(real,args); });
    if(modo.equals("foco")) com.fivedoorsescape.util.EstadoVentana.configurar(()->{});
    // la IA (ChaseState) lee Gdx.graphics.getDeltaTime(): que sea el mismo dt fijo de cada cuadro de la prueba
    final Graphics gr=Gdx.graphics; Gdx.graphics=(Graphics)Proxy.newProxyInstance(Graphics.class.getClassLoader(), new Class[]{Graphics.class}, (p,m,args)->{ if(m.getName().equals("getDeltaTime")) return dt; return m.invoke(gr,args); });
    try { ContentRegistry reg=new ContentRegistry(); AssetService as=new AssetService(); MapDefinition md=reg.getMapDefinition("pizzeria");
      as.queueModel(md.modelPath); for(String e: new String[]{"freddy","bonnie","chica","foxy","llave","golden_freddy"}){ as.queueModel(reg.getEntityDefinition(e).modelPath); for(String snd: com.fivedoorsescape.audio.PasosAnimatronicos.sonidosDe(reg.getEntityDefinition(e))) as.queueSound(snd); }
      for(Field f: GameplayScreen.class.getFields()) if(f.getName().startsWith("SONIDO_")) as.queueSound((String)f.get(null));
      ExteriorDefinition ex=reg.getExteriorDefinition(); as.queueModel(ex.modelPath); as.queueModel(ex.carModelPath);
      for(String s: CinematicaVictoria.SONIDOS) as.queueSound(s); for(String s: CinematicaDerrota.SONIDOS) as.queueSound(s);
      as.finishLoading(); gs=new GameplayScreen(this, reg, as, new HandoffData(System.getProperty("idioma","es").equals("en")?HandoffData.Idioma.INGLES:HandoffData.Idioma.ESPANOL, 3)); setScreen(gs);
      new Thread(this::guion,"guion").start(); } catch(Exception e){ e.printStackTrace(); Gdx.app.exit(); }
  }
  long ultimoFrame=0;
  final Object lock=new Object(); int framesPedidos=0; String capturaPendiente=null; Runnable tareaGL=null;
  public void render(){ synchronized(lock){
      if(tareaGL!=null){ try{ tareaGL.run(); }catch(Throwable t){ t.printStackTrace(); fallos++; } tareaGL=null; lock.notifyAll(); }
      if(framesPedidos>0){ Gdx.gl.glViewport(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
        try{ getScreen().render(dt); }catch(Throwable t){ t.printStackTrace(); fallos++; framesPedidos=1; }
        pulsadas.clear();
        if(capturaPendiente!=null && framesPedidos==1){ int W=Gdx.graphics.getBackBufferWidth(),H=Gdx.graphics.getBackBufferHeight(); Pixmap px=Pixmap.createFromFrameBuffer(0,0,W,H);
          Pixmap fl=new Pixmap(W,H,Pixmap.Format.RGBA8888); for(int y=0;y<H;y++) fl.drawPixmap(px,0,y,W,1,0,H-1-y,W,1);
          PixmapIO.writePNG(new FileHandle(out+"/"+capturaPendiente+".png"),Opaco.aplicar(fl)); px.dispose(); fl.dispose(); capturaPendiente=null; }
        framesPedidos--; lock.notifyAll(); } } }
  void frames(int n, String cap){ synchronized(lock){ framesPedidos=n; capturaPendiente=cap; while(framesPedidos>0) try{ lock.wait(); }catch(InterruptedException e){} } }
  void gl(Runnable r){ synchronized(lock){ tareaGL=r; while(tareaGL!=null) try{ lock.wait(); }catch(InterruptedException e){} } }
  void pulsar(int k){ pulsadas.add(k); frames(1,null); }
  TransformComponent jugador() throws Exception { return Mappers.transform.get((com.badlogic.ashley.core.Entity)campo("playerEntity")); }
  com.fivedoorsescape.camera.FirstPersonCameraController cc() throws Exception { return (com.fivedoorsescape.camera.FirstPersonCameraController)campo("cameraController"); }
  void colocar(float x, float z, float yaw){ gl(()->{ try{ jugador().position.set(x,1.6f,z); cc().resetOrientation(yaw); }catch(Exception e){ throw new RuntimeException(e);} }); }
  void mirarA(float x, float y, float z){ gl(()->{ try{ TransformComponent t=jugador(); cc().resetOrientation(MathUtils.atan2(x-t.position.x,z-t.position.z)*MathUtils.radiansToDegrees);
      Field fp=cc().getClass().getDeclaredField("pitchDegrees"); fp.setAccessible(true); fp.setFloat(cc(), MathUtils.atan2(y-1.6f,(float)Math.hypot(x-t.position.x,z-t.position.z))*MathUtils.radiansToDegrees); }catch(Exception e){ throw new RuntimeException(e);} }); }
  Vector3 pos() throws Exception { final Vector3 v=new Vector3(); gl(()->{ try{ v.set(jugador().position);}catch(Exception e){} }); return v; }
  void jugando() throws Exception { gl(()->{ try{ Class<?> e=Class.forName("com.fivedoorsescape.screens.GameplayScreen$EstadoPartida"); for(Object o: e.getEnumConstants()) if(o.toString().equals("JUGANDO")) set("estado",o); set("tiempoJugandoTranscurrido",100f); }catch(Exception e){ throw new RuntimeException(e);} }); }
  /** Camina con las teclas reales (W) mirando hacia el objetivo; devuelve la distancia final. */
  float caminarConTeclas(float x, float z, boolean sprint, int maxFrames) throws Exception {
    float d=0; for(int i=0;i<maxFrames;i++){ Vector3 p=pos(); d=(float)Math.hypot(x-p.x,z-p.z); if(d<0.1f) break; mirarA(x,1.6f,z);
      apretadas.add(Input.Keys.W); if(sprint) apretadas.add(Input.Keys.SHIFT_LEFT); frames(1,null); }
    apretadas.clear(); return d; }
  PuertasInteriores puertas() throws Exception { return (PuertasInteriores)campo("puertasInteriores"); }
  AIComponent guardia(String id) throws Exception { for(Object o: (com.badlogic.gdx.utils.Array<?>)campo("guardias")) if(((AIComponent)o).id.equals(id)) return (AIComponent)o; return null; }
  TransformComponent tGuardia(String id) throws Exception { com.badlogic.gdx.utils.Array<?> g=(com.badlogic.gdx.utils.Array<?>)campo("guardias"); com.badlogic.gdx.utils.Array<?> e=(com.badlogic.gdx.utils.Array<?>)campo("entidadesGuardias"); for(int i=0;i<g.size;i++) if(((AIComponent)g.get(i)).id.equals(id)) return Mappers.transform.get((com.badlogic.ashley.core.Entity)e.get(i)); return null; }
  void apartarGuardias() throws Exception { gl(()->{ try{ com.badlogic.gdx.utils.Array<?> g=(com.badlogic.gdx.utils.Array<?>)campo("guardias"); com.badlogic.gdx.utils.Array<?> e=(com.badlogic.gdx.utils.Array<?>)campo("entidadesGuardias"); for(int i=0;i<g.size;i++){ Mappers.transform.get((com.badlogic.ashley.core.Entity)e.get(i)).position.set(30+i*3,0,30); ((AIComponent)g.get(i)).siempreEnPersecucion=false; ((AIComponent)g.get(i)).stateMachine.changeState(IdleState.INSTANCE);} }catch(Exception ex){ throw new RuntimeException(ex);} }); }

  void guion(){ try { frames(3,null);
      if(modo.equals("foco")) { pruebaFoco(); } else if(modo.equals("intro")) { pruebaIntro(); } else { jugando(); apartarGuardias(); frames(2,null);
        if(modo.equals("puertas")) pruebaPuertas(); if(modo.equals("sprint")) pruebaSprint(); if(modo.equals("aturdir")) pruebaAturdir(); if(modo.equals("cortina")) pruebaCortina(); if(modo.equals("bordes")) pruebaBordes(); if(modo.equals("vista")) pruebaVistaPuertas(); if(modo.equals("mesas")) pruebaMesas(); if(modo.equals("colliders")) volcarColliders(); if(modo.equals("escalon")) pruebaEscalonMesas(); if(modo.equals("pasos")) pruebaPasos(); if(modo.equals("pausa")) pruebaPausa(); }
    } catch(Throwable e){ e.printStackTrace(); fallos++; }
    System.out.println("FALLOS: "+fallos); Gdx.app.postRunnable(()->Gdx.app.exit()); }

  // ---- pasos: un animatronico persiguiendo suena en cada apoyo (altura del pie en el suelo), quieto no suena,
  // en pausa no suena, perfil de distancia
  com.badlogic.ashley.core.Entity eGuardia(String id) throws Exception { com.badlogic.gdx.utils.Array<?> g=(com.badlogic.gdx.utils.Array<?>)campo("guardias"); com.badlogic.gdx.utils.Array<?> e=(com.badlogic.gdx.utils.Array<?>)campo("entidadesGuardias"); for(int i=0;i<g.size;i++) if(((AIComponent)g.get(i)).id.equals(id)) return (com.badlogic.ashley.core.Entity)e.get(i); return null; }
  void pruebaPasos() throws Exception {
    com.fivedoorsescape.audio.PasosAnimatronicos pasos=(com.fivedoorsescape.audio.PasosAnimatronicos)campo("pasos");
    ContentRegistry reg=new ContentRegistry();
    // tramo horizontal (en x) mas largo libre para la caja de un guardia
    final CollisionWorld cw=(CollisionWorld)campo("collisionWorld"); final Vector3 he=new Vector3(Mappers.collision.get(eGuardia("bonnie")).halfExtents);
    final float[] mejor={0,0,0,0};
    gl(()->{ Vector3 q=new Vector3(); for(float z=-9.5f;z<=11f;z+=0.25f){ float ini=Float.NaN; for(float x=-12.5f;x<=14.8f;x+=0.1f){ q.set(x,0f,z); boolean libre=!cw.overlapsStatic(q,he);
        if(libre && Float.isNaN(ini)) ini=x; if((!libre||x>14.7f) && !Float.isNaN(ini)){ if(x-ini>mejor[0]){ mejor[0]=x-ini; mejor[1]=ini; mejor[2]=x; mejor[3]=z; } ini=Float.NaN; } } } });
    System.out.println(String.format("      tramo libre mas largo: z=%.2f x %.2f..%.2f (%.1f m)", mejor[3], mejor[1], mejor[2], mejor[0]));
    final float zT=mejor[3], xA=mejor[1]+0.6f, xB=Math.min(mejor[2]-0.6f, xA+9f);
    for(String id: new String[]{"freddy","bonnie","chica","foxy"}){
      apartarGuardias(); colocar(xA,zT,90f); frames(2,null);
      final com.badlogic.ashley.core.Entity e=eGuardia(id); final AIComponent ai=guardia(id);
      EntityDefinition def=reg.getEntityDefinition(id);
      final com.badlogic.gdx.graphics.g3d.model.Node pie[]={ Mappers.model.get(e).scene.modelInstance.getNode(def.huesos.get("pieIzquierdo"),true), Mappers.model.get(e).scene.modelInstance.getNode(def.huesos.get("pieDerecho"),true)};
      gl(()->{ Mappers.transform.get(e).position.set(xB,0f,zT); ai.stateMachine.changeState(IdleState.INSTANCE); ai.rangoDeteccion=0.01f; });
      int c0=pasos.pisadasDe(e); frames(120,null); int quieto=pasos.pisadasDe(e)-c0;
      ok(quieto==0, id+": quieto (idle 2 s) no suena: "+quieto+" pisadas");
      gl(()->{ ai.rangoDeteccion=50f; ai.stateMachine.changeState(ChaseState.INSTANCE); });
      int antes=pasos.pisadasDe(e); java.util.List<Float> tiempos=new java.util.ArrayList<>(); float maxU=0; int n=0; float[] mn={9,9},mx={-9,-9}; java.util.List<float[]> alt=new java.util.ArrayList<>();
      for(int f=0;f<200;f++){ frames(1,null); final float[] h=new float[2];
        gl(()->{ Vector3 v=new Vector3(); for(int k=0;k<2;k++){ pie[k].globalTransform.getTranslation(v); v.mul(Mappers.model.get(e).scene.modelInstance.transform); h[k]=v.y-Mappers.transform.get(e).position.y; } });
        alt.add(h); for(int k=0;k<2;k++){ mn[k]=Math.min(mn[k],h[k]); mx[k]=Math.max(mx[k],h[k]); }
        int c=pasos.pisadasDe(e); if(c>antes){ tiempos.add(f*dt); antes=c; alt.get(alt.size()-1)[0]+=0; n++; float u=Math.min((h[0]-mn[0])/Math.max(1e-4f,mx[0]-mn[0]),(h[1]-mn[1])/Math.max(1e-4f,mx[1]-mn[1])); } }
      // al momento de cada pisada, el pie mas bajo debe estar cerca del suelo (en el 30 % inferior de su recorrido)
      int cerca=0; for(Float t: tiempos){ float[] h=alt.get(Math.round(t/dt)); float u=Math.min((h[0]-mn[0])/(mx[0]-mn[0]),(h[1]-mn[1])/(mx[1]-mn[1])); if(u<0.3f) cerca++; }
      StringBuilder sb=new StringBuilder(); for(int i=1;i<tiempos.size();i++) sb.append(String.format(" %.2f",tiempos.get(i)-tiempos.get(i-1)));
      Vector3 pg=Mappers.transform.get(e).position;
      if(id.equals("bonnie")){ StringBuilder cv=new StringBuilder("      curva bonnie (izq/der cada 3 cuadros desde t=1 s):"); for(int f=60;f<140;f+=3) cv.append(String.format(" %.2f/%.2f",alt.get(f)[0],alt.get(f)[1])); System.out.println(cv); }
      System.out.println(String.format("      pies: izq %.3f..%.3f der %.3f..%.3f; altura a t=0.5,1,2,3 s: %.3f/%.3f %.3f/%.3f %.3f/%.3f %.3f/%.3f", mn[0],mx[0],mn[1],mx[1], alt.get(30)[0],alt.get(30)[1],alt.get(60)[0],alt.get(60)[1],alt.get(120)[0],alt.get(120)[1],alt.get(180)[0],alt.get(180)[1]));
      try{ Field fs=pasos.getClass().getDeclaredField("seguidores"); fs.setAccessible(true); for(Object sg: (com.badlogic.gdx.utils.Array<?>)fs.get(pasos)){ Field fe=sg.getClass().getDeclaredField("entidad"); fe.setAccessible(true); if(fe.get(sg)!=e) continue;
        for(String pn: new String[]{"izquierdo","derecho"}){ Field fp=sg.getClass().getDeclaredField(pn); fp.setAccessible(true); Object pie2=fp.get(sg); Field a=pie2.getClass().getDeclaredField("min"), b=pie2.getClass().getDeclaredField("max"); a.setAccessible(true); b.setAccessible(true); System.out.println("      seguidor "+pn+": "+a.get(pie2)+" .. "+b.get(pie2)); }
        Field fv=sg.getClass().getDeclaredField("velocidad"); fv.setAccessible(true); System.out.println("      velocidad "+fv.get(sg)); } }catch(Exception ex){ ex.printStackTrace(); }
      System.out.println(String.format("      %s persiguiendo 3.3 s: %d pisadas, intervalos%s; avanzo hasta x=%.2f", id, n, sb, pg.x));
      ok(n>=5 && n<=8, id+": ~2 pisadas por ciclo de caminar mientras persigue ("+n+" en 3.3 s)");
      ok(pasos.audiblesDe(e)==pasos.pisadasDe(e), id+": a menos de 9 m todas se reproducen ("+pasos.audiblesDe(e)+"/"+pasos.pisadasDe(e)+")");
      ok(cerca==n, id+": en cada pisada un pie esta apoyado ("+cerca+"/"+n+")");
      // pausa: ninguna pisada nueva
      pulsar(Input.Keys.ESCAPE); int cp=pasos.pisadasDe(e); frames(60,null); ok(pasos.pisadasDe(e)==cp, id+": en pausa no suena"); pulsar(Input.Keys.ESCAPE); frames(2,null);
      gl(()->{ ai.stateMachine.changeState(IdleState.INSTANCE); ai.rangoDeteccion=0.01f; });
    }
    // jumpscare: al atrapar, los pasos se cortan y no vuelven a sonar mientras dura
    apartarGuardias(); colocar(xA,zT,90f); frames(2,null);
    final com.badlogic.ashley.core.Entity eb=eGuardia("bonnie"); final AIComponent ab=guardia("bonnie");
    gl(()->{ Mappers.transform.get(eb).position.set(xA+3.5f,0f,zT); ab.rangoDeteccion=50f; ab.stateMachine.changeState(ChaseState.INSTANCE); });
    String est=""; int f=0; for(;f<400;f++){ frames(1,null); est=campo("estado").toString(); if(est.equals("JUMPSCARE")) break; }
    int cj=pasos.pisadasDe(eb); frames(40,null);
    ok(est.equals("JUMPSCARE") && pasos.pisadasDe(eb)==cj, "jumpscare (Bonnie atrapa a los "+(f*dt)+" s): ninguna pisada durante el susto ("+campo("estado")+")");
  }

  // ---- pausa: menu completo, mundo congelado, volumen persistente, controles, reinicio, foco
  Object menu() throws Exception { return campo("menuPausa"); }
  String panel() throws Exception { Method m=menu().getClass().getMethod("getPanel"); return m.invoke(menu()).toString(); }
  int sel() throws Exception { return (Integer)menu().getClass().getMethod("getSeleccion").invoke(menu()); }
  com.badlogic.gdx.math.Rectangle zona(String campoMenu, int i) throws Exception { Field f=menu().getClass().getDeclaredField(campoMenu); f.setAccessible(true); return (com.badlogic.gdx.math.Rectangle)((com.badlogic.gdx.utils.Array<?>)f.get(menu())).get(i); }
  float vol(String m) throws Exception { return (Float)com.fivedoorsescape.audio.VolumenAudio.class.getMethod(m).invoke(null); }
  void pruebaPausa() throws Exception {
    String sufijo=System.getProperty("idioma","es");
    final com.badlogic.ashley.core.Entity eb=eGuardia("bonnie"); final AIComponent ab=guardia("bonnie");
    colocar(-6f,1f,90f); gl(()->{ Mappers.transform.get(eb).position.set(2f,0f,1f); ab.rangoDeteccion=50f; ab.stateMachine.changeState(ChaseState.INSTANCE); }); frames(20,null);
    pulsar(Input.Keys.ESCAPE); frames(1,"pausa_principal_"+sufijo);
    ok((Boolean)campo("pausado") && panel().equals("PRINCIPAL") && sel()==0, "ESC abre el menu en el panel principal, con Continuar elegido");
    ok(Boolean.FALSE.equals(ultimoCatched), "el cursor queda libre en la pausa");
    final Vector3 g0=new Vector3(Mappers.transform.get(eb).position); Vector3 j0=pos(); float t0=(Float)campo("tiempoJugandoTranscurrido");
    apretadas.add(Input.Keys.W); frames(90,null); apretadas.clear();
    ok(g0.dst(Mappers.transform.get(eb).position)<1e-5f && j0.dst(pos())<1e-5f && (Float)campo("tiempoJugandoTranscurrido")==t0, "mundo congelado 1.5 s: Bonnie, el jugador y el reloj de juego no avanzan");
    pulsar(Input.Keys.S); ok(sel()==1, "S baja a Volumen"); pulsar(Input.Keys.E); frames(1,"pausa_volumen_"+sufijo);
    ok(panel().equals("VOLUMEN"), "E entra en Volumen");
    float gIni=vol("getGeneral");
    pulsar(Input.Keys.A); pulsar(Input.Keys.A); pulsar(Input.Keys.A); pulsar(Input.Keys.A);
    float g=vol("getGeneral");
    ok(Math.abs(g-(gIni-0.2f))<0.011f, "A baja el volumen general un 5 % por pulsacion ("+gIni+" -> "+g+")");
    float gan=com.fivedoorsescape.audio.VolumenAudio.ganancia(com.fivedoorsescape.audio.VolumenAudio.Categoria.EFECTOS);
    ok(Math.abs(gan-(g*vol("getEfectos"))*(g*vol("getEfectos")))<1e-4f, "la ganancia de efectos es (general x efectos)^2 = "+gan);
    pulsar(Input.Keys.S); com.badlogic.gdx.math.Rectangle b=zona("barras",1); float H=Gdx.graphics.getHeight();
    ratonX=b.x+b.width*0.5f; ratonY=H-(b.y+b.height/2f); clic=true; boton=true; frames(1,null); clic=false;
    for(int i=0;i<10;i++){ ratonX=b.x+b.width*(0.5f-0.02f*(i+1)); frames(1,null); } boton=false; frames(1,"pausa_volumen_arrastre_"+sufijo);
    float mu=vol("getMusica");
    ok(Math.abs(mu-0.30f)<0.011f, "arrastrando el deslizador de musica con el raton queda en "+mu);
    ratonX=-1; ratonY=-1;
    pulsar(Input.Keys.ESCAPE); ok(panel().equals("PRINCIPAL") && sel()==1 && (Boolean)campo("pausado"), "ESC en Volumen vuelve al principal (sin cerrar la pausa)");
    java.io.File prefs=new java.io.File(System.getProperty("user.home"),".prefs/five_doors_escape"); String contenido=prefs.isFile()? new String(java.nio.file.Files.readAllBytes(prefs.toPath())) : "(no existe)";
    ok(contenido.contains("volumen.general") && contenido.contains("volumen.musica"), "al salir de Volumen se guardo en las preferencias de Escape ("+prefs+")");
    System.out.println("      preferencias: "+contenido.replaceAll("\\s+"," "));
    pulsar(Input.Keys.S); pulsar(Input.Keys.E); frames(1,"pausa_controles_"+sufijo); ok(panel().equals("CONTROLES"), "Controles");
    pulsar(Input.Keys.E); ok(panel().equals("PRINCIPAL") && sel()==2, "Volver (E) regresa al principal");
    pulsar(Input.Keys.S); pulsar(Input.Keys.S); ok(sel()==4, "Salir al menu principal elegido"); pulsar(Input.Keys.E); frames(1,"pausa_confirmar_salida_"+sufijo);
    ok(panel().equals("CONFIRMAR_SALIDA") && sel()==1, "Salir pide confirmacion con No elegido");
    pulsar(Input.Keys.E); ok(panel().equals("PRINCIPAL") && sel()==4, "No vuelve al principal sin salir");
    frames(1,null); com.badlogic.gdx.math.Rectangle z0=zona("zonas",0);
    ratonX=z0.x+z0.width/2f; ratonY=H-(z0.y+z0.height/2f); frames(1,null); ok(sel()==0, "pasar el raton sobre Continuar lo elige");
    clic=true; frames(1,null); clic=false; ratonX=-1; ratonY=-1;
    ok(!(Boolean)campo("pausado") && Boolean.TRUE.equals(ultimoCatched), "clic en Continuar reanuda y vuelve a capturar el cursor");
    frames(30,null); ok(g0.dst(Mappers.transform.get(eb).position)>0.3f, "al reanudar Bonnie sigue persiguiendo");
    com.fivedoorsescape.util.EstadoVentana.alCambiarFoco(false); frames(2,null);
    ok((Boolean)campo("pausado") && panel().equals("PRINCIPAL"), "perder el foco (ALT+TAB simulado) abre el menu");
    com.fivedoorsescape.util.EstadoVentana.alCambiarFoco(true); frames(5,null); ok((Boolean)campo("pausado"), "al recuperar el foco sigue en pausa hasta que el jugador decida");
    pulsar(Input.Keys.ESCAPE); ok(!(Boolean)campo("pausado"), "ESC en el principal reanuda");
    gl(()->{ Mappers.transform.get(eb).position.set(30f,0f,30f); ab.stateMachine.changeState(IdleState.INSTANCE); ab.rangoDeteccion=0.01f; });
    pulsar(Input.Keys.ESCAPE); pulsar(Input.Keys.S); pulsar(Input.Keys.S); pulsar(Input.Keys.S); ok(sel()==3, "Reiniciar partida elegido");
    pulsar(Input.Keys.E); ok(panel().equals("CONFIRMAR_REINICIO") && sel()==1, "Reiniciar pide confirmacion con No elegido");
    int collAntes=((CollisionWorld)campo("collisionWorld")).getStaticColliders().size;
    pulsar(Input.Keys.A); ok(sel()==0, "A pasa a Si"); GameplayScreen viejo=gs; pulsar(Input.Keys.E); frames(3,null);
    ok(getScreen()!=viejo && getScreen() instanceof GameplayScreen, "Si reinicia: pantalla de juego nueva");
    gs=(GameplayScreen)getScreen(); frames(5,"pausa_tras_reinicio_"+sufijo);
    ok(campo("estado").toString().equals("INSTRUCCIONES"), "la partida reiniciada empieza en las instrucciones (sin repetir la introduccion): "+campo("estado"));
    ok(((CollisionWorld)campo("collisionWorld")).getStaticColliders().size==collAntes && (Integer)campo("vidasRestantes")>0, "mismo mundo ("+collAntes+" colliders) y vidas completas");
    ok(Math.abs(vol("getMusica")-mu)<1e-4f, "el volumen elegido se conserva en la partida nueva");
    pulsar(Input.Keys.E); frames(240,null); String e2=campo("estado").toString(); jugando(); apartarGuardias(); frames(20,null);
    ok(campo("estado").toString().equals("JUGANDO") && !(Boolean)campo("pausado"), "la partida nueva se juega con normalidad (estado tras E: "+e2+")");
    pulsar(Input.Keys.ESCAPE); ok((Boolean)campo("pausado"), "y su menu de pausa funciona"); pulsar(Input.Keys.ESCAPE);
  }

  // ---- puertas: se abren antes de llegar, se cruzan, se cierran al alejarse y se vuelven a cruzar
  void pruebaPuertas() throws Exception {
    // {id, A (inicio, >1.5 m), B (al otro lado), F (lejos del lado A, >2.1 m)}
    Object[][] casos = { {"puerta.005_46",-3.9f,5.5f,-7.6f,5.5f,-2.6f,5.5f}, {"puerta.001_42",10.6f,-4.3f,6.9f,-4.3f,10.2f,-1.5f},
        {"puerta.004_45",7.2f,3.7f,7.2f,0.1f,7.2f,4.6f}, {"puerta.002_43",9.8f,-3.0f,12.2f,-3.0f,9.8f,-0.6f},
        {"puerta Derecha_57",-0.1f,9.2f,3.9f,8.8f,-0.8f,10.5f}, {"puerta Izquierda_56",0.1f,9.2f,-4.1f,8.8f,0.8f,10.5f} };
    for(Object[] c: casos){ String id=(String)c[0]; float ax=(Float)c[1], az=(Float)c[2], bx=(Float)c[3], bz=(Float)c[4], fx=(Float)c[5], fz=(Float)c[6];
      colocar(ax,az,0); frames(90,null); float cerrada=puertas().apertura(id);
      float maxApertura=0; float dIda=0; for(int i=0;i<500;i++){ Vector3 p=pos(); dIda=(float)Math.hypot(bx-p.x,bz-p.z); if(dIda<0.1f) break; mirarA(bx,1.6f,bz); apretadas.add(Input.Keys.W); frames(1,i==35?"puerta_"+id.replace(' ','_'):null); maxApertura=Math.max(maxApertura,puertas().apertura(id)); }
      apretadas.clear();
      float dVuelta=caminarConTeclas(ax,az,false,500); float dF=caminarConTeclas(fx,fz,false,500); frames(150,null); float trasAlejarse=puertas().apertura(id);
      System.out.println(String.format("      %-20s cerrada=%.2f  cruce: resto %.2f apertura max %.2f  vuelta: resto %.2f  lejos (resto %.2f): apertura %.2f", id, cerrada, dIda, maxApertura, dVuelta, dF, trasAlejarse));
      ok(cerrada==0f && dIda<0.15f && maxApertura>0.95f && dVuelta<0.15f && trasAlejarse==0f, id+": cerrada, se abre al llegar, se cruza en los dos sentidos y se cierra al alejarse"); }
  }
  // ---- vista: cada abatible entreabierta (u=0.55) hacia un lado, vista desde 2.6 m, para ver bisagra y picaporte
  void pruebaVistaPuertas() throws Exception {
    Object[][] casos = { {"puerta.001_42",10.9f,-4.3f}, {"puerta.002_43",9.4f,-3.0f}, {"puerta.003_44",9.4f,0.5f}, {"puerta.004_45",7.2f,4.4f}, {"puerta.005_46",-3.2f,5.5f},
                         {"puerta.001_42",6.2f,-4.3f}, {"puerta.004_45",7.2f,-0.6f}, {"puerta.005_46",-8.4f,5.5f} };
    int n=0; for(Object[] c: casos){ final String id=(String)c[0]; float x=(Float)c[1], z=(Float)c[2]; final int nn=n++;
      colocar(x,z,0); frames(2,null);
      gl(()->{ try{ PuertasInteriores pi=puertas(); Field fl=PuertasInteriores.class.getDeclaredField("puertas"); fl.setAccessible(true);
        for(Object o: (com.badlogic.gdx.utils.Array<?>)fl.get(pi)){ Class<?> k=o.getClass(); Field fid=k.getDeclaredField("id"); fid.setAccessible(true); if(!fid.get(o).equals(id)) continue;
          Field fc=k.getDeclaredField("centro"); fc.setAccessible(true); Vector3 ce=(Vector3)fc.get(o);
          Field fd=k.getDeclaredField("delgadaEnX"); fd.setAccessible(true); boolean dx=fd.getBoolean(o);
          float t = dx ? x-ce.x : z-ce.z; Field fla=k.getDeclaredField("ladoActual"); fla.setAccessible(true); fla.setInt(o, t>0?0:1);
          Field fa=k.getDeclaredField("apertura"); fa.setAccessible(true); fa.setFloat(o,0.55f);
          Method ap=PuertasInteriores.class.getDeclaredMethod("aplicar", k); ap.setAccessible(true); ap.invoke(pi,o);
          TransformComponent tj=jugador(); cc().resetOrientation(MathUtils.atan2(ce.x-tj.position.x,ce.z-tj.position.z)*MathUtils.radiansToDegrees); }
        Field fp=PuertasInteriores.class.getDeclaredField("puertas"); }catch(Exception e){ throw new RuntimeException(e);} });
      // congelar la puerta: nadie la cierra porque se reaplica cada cuadro
      frames(1,"vista_"+nn+"_"+id); }
  }
  // ---- mesas: caminatas aleatorias (WASD, diagonales, sprint, giros) por el comedor; detecta quedar incrustado o sin salida
  Vector3 halfJ() throws Exception { return Mappers.collision.get((com.badlogic.ashley.core.Entity)campo("playerEntity")).halfExtents; }
  boolean libre(CollisionWorld cw, Vector3 p, Vector3 h){ return !cw.overlaps(p,h,true); }
  int salidas(CollisionWorld cw, Vector3 p, Vector3 h){ int n=0; for(int a=0;a<8;a++){ Vector3 d=new Vector3(MathUtils.cosDeg(a*45)*0.05f,0,MathUtils.sinDeg(a*45)*0.05f); if(cw.resolveMovement(p,d,h,true).dst(p)>0.001f) n++; } return n; }
  void pruebaMesas() throws Exception {
    final CollisionWorld cw=(CollisionWorld)campo("collisionWorld"); final Vector3 h=halfJ();
    long seed=Long.getLong("seed",7L); java.util.Random rnd=new java.util.Random(seed);
    float x0=Float.parseFloat(System.getProperty("x0","-6.5")), x1=Float.parseFloat(System.getProperty("x1","6.5")), z0=Float.parseFloat(System.getProperty("z0","-9.3")), z1=Float.parseFloat(System.getProperty("z1","4.5"));
    int corridas=Integer.getInteger("corridas",40), fr=Integer.getInteger("frames",400); int incrustados=0, sinSalida=0; float recorrido=0;
    int[] teclas={Input.Keys.W,Input.Keys.A,Input.Keys.S,Input.Keys.D};
    for(int c=0;c<corridas;c++){ Vector3 ini=null; for(int t=0;t<500 && ini==null;t++){ Vector3 q=new Vector3(x0+rnd.nextFloat()*(x1-x0),1.6f,z0+rnd.nextFloat()*(z1-z0)); q.y=1.6f+cw.alturaEscalonEn(q.x,q.z); if(libre(cw,q,h)) ini=q; }
      if(ini==null) continue; final Vector3 fi=ini; gl(()->{ try{ jugador().position.set(fi); }catch(Exception e){} });
      float yaw=rnd.nextFloat()*360f; colocar(ini.x,ini.z,yaw); gl(()->{ try{ jugador().position.y=fi.y; }catch(Exception e){} });
      boolean reportado=false; Vector3 antes=new Vector3(ini);
      for(int f=0;f<fr;f++){ if(f%25==0){ apretadas.clear(); int k=rnd.nextInt(4); apretadas.add(teclas[k]); if(rnd.nextFloat()<0.5f) apretadas.add(teclas[(k+1+2*rnd.nextInt(2))%4]); if(rnd.nextFloat()<0.4f) apretadas.add(Input.Keys.SHIFT_LEFT); }
        if(f%7==0){ yaw+= (rnd.nextFloat()-0.5f)*50f; final float yy=yaw; gl(()->{ try{ Field fy=cc().getClass().getDeclaredField("yawDegrees"); fy.setAccessible(true); fy.setFloat(cc(),yy);}catch(Exception e){} }); }
        frames(1,null); Vector3 p=pos(); recorrido+=p.dst(antes);
        if(!reportado && !libre(cw,p,h)){ incrustados++; reportado=true; System.out.println(String.format("INCRUSTADO corrida %d frame %d en (%.3f, %.3f, %.3f) desde (%.3f, %.3f, %.3f) teclas=%s", c,f,p.x,p.y,p.z,antes.x,antes.y,antes.z,apretadas)); listarSolapes(cw,p,h); frames(1,"incrustado_"+c); }
        else if(!reportado && f%5==0 && salidas(cw,p,h)==0){ sinSalida++; reportado=true; System.out.println(String.format("SIN SALIDA corrida %d frame %d en (%.3f, %.3f, %.3f)", c,f,p.x,p.y,p.z)); listarSolapes(cw,p,new Vector3(h.x+0.06f,h.y,h.z+0.06f)); }
        antes.set(p); }
      apretadas.clear(); }
    System.out.println(String.format("mesas: %d corridas x %d frames, recorrido %.1f m, incrustados=%d, sin salida=%d", corridas, fr, recorrido, incrustados, sinSalida));
    ok(incrustados==0 && sinSalida==0, "caminatas aleatorias por el comedor sin quedar incrustado ni sin salida");
  }
  void listarSolapes(CollisionWorld cw, Vector3 p, Vector3 h){ com.badlogic.gdx.math.collision.BoundingBox b=new com.badlogic.gdx.math.collision.BoundingBox(new Vector3(p).sub(h),new Vector3(p).add(h));
    for(com.badlogic.gdx.math.collision.BoundingBox c: cw.getStaticColliders()) if(c.intersects(b)) System.out.println("     solapa "+c.min+" .. "+c.max); }
  // ---- bajar del escenario hacia una mesa (caminando y corriendo, recto y en diagonal): nunca termina incrustado
  void pruebaEscalonMesas() throws Exception {
    final CollisionWorld cw=(CollisionWorld)campo("collisionWorld"); final Vector3 h=halfJ(); int malos=0, n=0;
    float[] xs={-3.6f,-2.2f,-0.1f,1.0f,2.4f,3.3f}; float[] yaws={0f,25f,-25f,45f,-45f};
    for(float x: xs) for(float yaw: yaws) for(int sp=0;sp<2;sp++){ n++;
      if(!libre(cw,new Vector3(x,2.16f,-7.35f),h)){ System.out.println("  (inicio x="+x+" dentro de la utileria del escenario, se omite)"); n--; continue; }
      colocar(x,-7.35f,yaw); gl(()->{ try{ jugador().position.y=1.6f+0.56f; }catch(Exception e){} }); frames(2,null);
      apretadas.add(Input.Keys.W); if(sp==1) apretadas.add(Input.Keys.SHIFT_LEFT);
      boolean malo=false; for(int f=0;f<70;f++){ frames(1,null); Vector3 p=pos(); if(!libre(cw,p,h)){ malo=true; System.out.println(String.format("INCRUSTADO x=%.1f yaw=%.0f sprint=%d frame %d en (%.3f, %.3f, %.3f)",x,yaw,sp,f,p.x,p.y,p.z)); break; } }
      apretadas.clear(); frames(30,null); Vector3 p=pos();
      // intenta salir en las 4 direcciones
      Vector3 a=new Vector3(p); for(int k: new int[]{Input.Keys.S,Input.Keys.A,Input.Keys.D}){ apretadas.add(k); frames(20,null); apretadas.clear(); }
      boolean seMovio=pos().dst(a)>0.2f; if(malo) malos++; if(!seMovio){ System.out.println(String.format("SIN MOVIMIENTO tras bajar: x=%.1f yaw=%.0f sprint=%d en (%.3f, %.3f, %.3f)",x,yaw,sp,a.x,a.y,a.z)); if(!malo) malos++; } }
    System.out.println("escalon: "+n+" casos, "+malos+" con atasco");
    // red de seguridad: incrustado a mano dentro del borde de una mesa, retrocediendo sale
    colocar(-0.1f,-5.81f,0f); frames(2,null); boolean dentro=!libre(cw,pos(),h); apretadas.add(Input.Keys.S); frames(40,null); apretadas.clear(); Vector3 q=pos();
    System.out.println(String.format("rescate: incrustado=%s -> (%.3f, %.3f) libre=%s", dentro, q.x, q.z, libre(cw,q,h)));
    ok(dentro && libre(cw,q,h), "si el jugador quedara dentro de una mesa, al retroceder sale sin atravesar nada nuevo");
    // y no permite atravesar la mesa hacia delante desde fuera
    colocar(-0.1f,-6.5f,0f); frames(2,null); apretadas.add(Input.Keys.W); frames(90,null); apretadas.clear(); Vector3 r=pos();
    ok(libre(cw,r,h) && r.z < -5.9f, String.format("desde fuera, W contra la mesa se detiene en el borde (z=%.3f)", r.z));
    ok(malos==0, "bajar del escenario hacia las mesas nunca deja al jugador incrustado ni sin poder moverse");
  }
  // ---- foco: ventana que nunca tuvo el teclado -> "Haz clic para entrar" con el mundo detenido; el clic se consume;
  //      despues, perder el foco en JUGANDO abre la pausa y recuperarlo no la cierra (se sigue con ESC)
  void pruebaFoco() throws Exception {
    Class<?> ev=com.fivedoorsescape.util.EstadoVentana.class;
    String e0=campo("estado").toString(); float t0=(Float)campo("tiempoEnEstado"); long id0=(Long)campo("idSonidoEscape");
    frames(60,"foco_activacion"); String e1=campo("estado").toString(); float t1=(Float)campo("tiempoEnEstado"); long id1=(Long)campo("idSonidoEscape");
    System.out.println("      antes del clic: estado "+e0+"->"+e1+" tiempoEnEstado "+t0+"->"+t1+" narracion "+id0+"->"+id1);
    ok(e1.equals("CINEMATICA_INICIAL") && t1==t0 && id1==-1L, "sin foco: la introduccion no avanza ni empieza la narracion");
    clic=true; frames(1,null); clic=false; String e2=campo("estado").toString(); float t2=(Float)campo("tiempoEnEstado"); long id2=(Long)campo("idSonidoEscape");
    ok(com.fivedoorsescape.util.EstadoVentana.isEnfocadaAlgunaVez() && t2==t0 && id2==-1L, "el clic activa la ventana y se consume (ese cuadro no llega al juego)");
    frames(30,null); ok((Float)campo("tiempoEnEstado")>0.3f && (Long)campo("idSonidoEscape")!=-1L, "despues del clic la introduccion arranca normalmente");
    jugando(); apartarGuardias(); frames(3,null); ok(!(Boolean)campo("pausado"), "jugando con foco: sin pausa");
    com.fivedoorsescape.util.EstadoVentana.alCambiarFoco(false); frames(2,"foco_perdido_pausa");
    ok((Boolean)campo("pausado"), "al perder el foco (ALT+TAB) se abre la pausa");
    Vector3 a=pos(); apretadas.add(Input.Keys.W); frames(20,null); apretadas.clear(); ok(a.dst(pos())<0.001f, "en pausa el jugador no se mueve");
    com.fivedoorsescape.util.EstadoVentana.alCambiarFoco(true); frames(3,null); ok((Boolean)campo("pausado"), "al volver sigue en pausa");
    pulsar(Input.Keys.ESCAPE); frames(2,null); ok(!(Boolean)campo("pausado"), "ESC reanuda como siempre");
  }
  void volcarColliders() throws Exception { final CollisionWorld cw=(CollisionWorld)campo("collisionWorld"); StringBuilder sb=new StringBuilder();
    Field f=CollisionWorld.class.getDeclaredField("collidersSoloJugador"); f.setAccessible(true);
    for(com.badlogic.gdx.math.collision.BoundingBox c: cw.getStaticColliders()) sb.append("S "+c.min.x+" "+c.min.y+" "+c.min.z+" "+c.max.x+" "+c.max.y+" "+c.max.z+"\n");
    for(Object o: (com.badlogic.gdx.utils.Array<?>)f.get(cw)){ com.badlogic.gdx.math.collision.BoundingBox c=(com.badlogic.gdx.math.collision.BoundingBox)o; sb.append("J "+c.min.x+" "+c.min.y+" "+c.min.z+" "+c.max.x+" "+c.max.y+" "+c.max.z+"\n"); }
    Vector3 h=halfJ(); sb.append("H "+h.x+" "+h.y+" "+h.z+"\n");
    new FileHandle(out+"/colliders.txt").writeString(sb.toString(),false); System.out.println("volcados "+cw.getStaticColliders().size); }
  // ---- bordes: pegado a la pared junto a cada puerta, el centro del jugador nunca entra en la pared
  void pruebaBordes() throws Exception {
    // {id, punto en el vano, direccion a lo largo de la pared (x,z), semiancho del vano}
    Object[][] casos = { {"puerta.005_46",-5.85f,5.5f,0f,1f,0.455f}, {"puerta.001_42",8.73f,-4.3f,0f,1f,0.455f}, {"puerta.004_45",7.2f,1.96f,1f,0f,0.455f},
        {"puerta Derecha_57",1.9f,8.78f,0f,1f,0.44f}, {"puerta Izquierda_56",-2.13f,8.78f,0f,1f,0.44f} };
    for(Object[] c: casos){ String id=(String)c[0]; float cx=(Float)c[1], cz=(Float)c[2], ux=(Float)c[3], uz=(Float)c[4], semi=(Float)c[5];
      for(int signo=-1; signo<=1; signo+=2){ colocar(cx,cz,0); frames(40,null);
        // empujar con sprint a lo largo de la pared, 3 m
        float tx=cx+ux*signo*3f, tz=cz+uz*signo*3f; for(int i=0;i<120;i++){ mirarA(tx,1.6f,tz); apretadas.add(Input.Keys.W); apretadas.add(Input.Keys.SHIFT_LEFT); frames(1,null);} apretadas.clear();
        Vector3 p=pos(); float avance=Math.abs((p.x-cx)*ux+(p.z-cz)*uz);
        System.out.println(String.format("      %-20s hacia %s: el centro avanzo %.2f m a lo largo de la pared (semiancho del vano %.2f)", id, signo<0?"-":"+", avance, semi));
        ok(avance <= semi + 0.06f, id+": no se entra en la pared junto al marco"); } }
    // esquinas en diagonal con sprint: comedor, esquina sudoeste del pasillo
    colocar(-1.0f,-6.5f,0); for(int i=0;i<160;i++){ mirarA(-5f,1.6f,-9.5f); apretadas.add(Input.Keys.W); apretadas.add(Input.Keys.D); apretadas.add(Input.Keys.SHIFT_LEFT); frames(1,null);} apretadas.clear();
    Vector3 p=pos(); final boolean[] dentro={false}; gl(()->{ try{ dentro[0]=((CollisionWorld)campo("collisionWorld")).overlaps(new Vector3(p.x,1.6f,p.z),new Vector3(0.29f,0.89f,0.29f),true);}catch(Exception e){} });
    ok(!dentro[0], "diagonal con sprint contra una esquina: el jugador no queda dentro de ningun collider ("+p+")");
  }
  // ---- sprint: velocidad real con las teclas
  void pruebaSprint() throws Exception {
    for(String[] caso: new String[][]{{"WD","no"},{"WD","si"}}){ apretadas.add(Input.Keys.W); apretadas.add(Input.Keys.D); if(caso[1].equals("si")) apretadas.add(Input.Keys.SHIFT_LEFT);
      final float[] v={0}; gl(()->{ try{ v[0]=cc().computeWasdDelta(1f).len(); }catch(Exception e){} }); apretadas.clear();
      float esperado = caso[1].equals("si") ? 3.5f*1.65f : 3.5f; System.out.println(String.format("      diagonal W+D %s (controlador real): %.3f m/s", caso[1].equals("si")?"corriendo":"caminando", v[0]));
      ok(Math.abs(v[0]-esperado)<0.01f, "diagonal normalizada: no se corre mas rapido en diagonal"); }
    for(String[] caso: new String[][]{{"W","no"},{"W","si"}}){ colocar(-1.9f,1.2f,180f); frames(5,null); Vector3 a=pos();
      for(int i=0;i<60;i++){ apretadas.add(Input.Keys.W); if(caso[0].contains("D")) apretadas.add(Input.Keys.D); if(caso[1].equals("si")) apretadas.add(Input.Keys.SHIFT_LEFT); frames(1,null);} apretadas.clear();
      Vector3 b=pos(); float v=(float)Math.hypot(b.x-a.x,b.z-a.z)/(60*dt);
      System.out.println(String.format("      %s %s: %.2f m/s", caso[0], caso[1].equals("si")?"corriendo":"caminando", v));
      float esperado = caso[1].equals("si") ? 3.5f*1.65f : 3.5f; ok(Math.abs(v-esperado)<0.15f, caso[0]+" "+caso[1]+": velocidad "+esperado+" m/s (diagonal normalizada)"); }
  }
  // ---- aturdimiento: umbrales exactos, el ciclo encendido largo cuenta uno, fuera del haz no cuenta, detras de pared no cuenta
  void ciclo(int veces, float segundosEncendida) throws Exception { for(int i=0;i<veces;i++){ boolean enc=(Boolean)campo("linternaEncendida");
      if(!enc){ pulsar(Input.Keys.CONTROL_LEFT); } frames(Math.max(2,(int)(segundosEncendida/dt)),null); pulsar(Input.Keys.CONTROL_LEFT); frames(3,null); } }
  void pruebaAturdir() throws Exception {
    for(String[] c: (System.getProperty("solopared")!=null? new String[0][] : new String[][]{{"foxy","3"},{"bonnie","7"},{"chica","7"}})){ String id=c[0]; int umbral=Integer.parseInt(c[1]);
      AIComponent ai=guardia(id); TransformComponent t=tGuardia(id);
      colocar(-1.9f,1.0f,180f); gl(()->{ t.position.set(-1.9f,0f,-3.5f); ai.destellos=0; ai.aturdidoRestante=0; ai.stateMachine.changeState(IdleState.INSTANCE); ai.rangoDeteccion=0.1f; });
      mirarA(-1.9f,1.2f,-3.5f); frames(2,null);
      if(!(Boolean)campo("linternaEncendida")) pulsar(Input.Keys.CONTROL_LEFT);
      // apagar la linterna inicial (sin iluminar a nadie antes) para empezar desde cero
      mirarA(5f,1.2f,5f); frames(2,null); pulsar(Input.Keys.CONTROL_LEFT); frames(2,null); ai.destellos=0; mirarA(-1.9f,1.2f,-3.5f); frames(2,null);
      ciclo(1, 2.5f); ok(ai.destellos==1, id+": linterna encendida 2.5 s sobre el animatronico cuenta UN ciclo ("+ai.destellos+")");
      mirarA(3f,1.2f,6f); frames(2,null); ciclo(3, 0.2f); ok(ai.destellos==1, id+": ciclos sin iluminarlo (fuera del haz) no cuentan ("+ai.destellos+")");
      mirarA(-1.9f,1.2f,-3.5f); frames(2,null); ciclo(umbral-2, 0.15f);
      ok(ai.destellos==umbral-1 && ai.aturdidoRestante==0f, id+": con "+(umbral-1)+" ciclos todavia no se aturde");
      ciclo(1, 0.15f); float r=ai.aturdidoRestante; ok(r>1.4f && ai.destellos==0, id+": el ciclo "+umbral+" lo aturde ("+r+" s) y reinicia el contador");
      frames(2, "aturdido_"+id);
      // durante el aturdimiento no se mueve ni atrapa aunque el jugador este al lado
      gl(()->{ try{ jugador().position.set(t.position.x, 1.6f, t.position.z+0.4f); }catch(Exception e){} ai.rangoDeteccion=8f; });
      Vector3 antes=new Vector3(t.position); int f=0; while(ai.aturdidoRestante>0f && f<200){ frames(1,null); f++; }
      float dur=f*dt+ (1.5f-r); System.out.println(String.format("      %s aturdido %.2f s; se movio %.3f m; atrapado=%s", id, f*dt+(1.5f-r), antes.dst(t.position), ai.jugadorAtrapado));
      ok(Math.abs(f*dt - r) < 0.05f && antes.dst(t.position) < 0.01f && !ai.jugadorAtrapado, id+": 1.5 s quieto y sin atrapar");
      frames(30,null); ok(ai.jugadorAtrapado || ai.stateMachine.getCurrentState()!=IdleState.INSTANCE, id+": despues se recupera y retoma la IA");
      gl(()->{ try{ set("estado", Enum.valueOf((Class)Class.forName("com.fivedoorsescape.screens.GameplayScreen$EstadoPartida"), "JUGANDO")); }catch(Exception e){} ai.jugadorAtrapado=false; ai.stateMachine.changeState(IdleState.INSTANCE); ai.rangoDeteccion=0.1f; t.position.set(30,0,30); });
      frames(2,null); }
    // detras de una pared no cuenta: Foxy al otro lado de la pared del pasillo
    AIComponent foxy=guardia("foxy"); TransformComponent tf=tGuardia("foxy"); gl(()->{ tf.position.set(-7.0f,0f,4.5f); foxy.destellos=0; });
    colocar(-4.5f,4.5f,0f); mirarA(-7.0f,1.2f,4.5f); frames(2,null); final boolean[] los={true}; gl(()->{ try{ los[0]=((CollisionWorld)campo("collisionWorld")).lineaDeVisionLibre(((PerspectiveCamera)campo("camera")).position,new Vector3(-7f,1.15f,4.5f)); }catch(Exception e){} });
    ciclo(2,0.2f); ok(!los[0] && foxy.destellos==0, "detras de una pared (sin linea de vision) no cuenta (los="+los[0]+", destellos="+foxy.destellos+")");
  }
  // ---- cortina: Foxy empieza a perseguir -> la cortina se abre antes de que la cruce, despues se cierra sola
  void pruebaCortina() throws Exception {
    AIComponent foxy=guardia("foxy"); TransformComponent tf=tGuardia("foxy"); CortinaPirateCove cor=(CortinaPirateCove)campo("cortinaPirateCove");
    gl(()->{ tf.position.set(-10.7f,0f,-0.5f); foxy.rangoDeteccion=8f; foxy.stateMachine.changeState(IdleState.INSTANCE); });
    colocar(-0.5f,-0.5f,-90f); mirarA(-8.5f,1.6f,-0.5f); frames(10,"cortina_0_cerrada"); ok(cor.getApertura()==0f, "cortina cerrada mientras Foxy esta quieto");
    colocar(-6.0f,-0.5f,-90f); mirarA(-8.5f,1.6f,-0.5f);
    float xFoxyAlAbrir=Float.NaN; for(int i=0;i<130;i++){ frames(1, i==25?"cortina_1_abriendo":(i==60?"cortina_2_abierta":null)); if(Float.isNaN(xFoxyAlAbrir) && cor.getApertura()>=0.99f) xFoxyAlAbrir=tf.position.x; }
    System.out.println("      Foxy en x="+xFoxyAlAbrir+" cuando la cortina termino de abrirse (cortina en x="+cor.getXCentro()+")");
    ok(!Float.isNaN(xFoxyAlAbrir) && xFoxyAlAbrir < cor.getXCentro(), "la cortina termina de abrirse ANTES de que Foxy la cruce");
    // Foxy sigue persiguiendo (jugador lejos) -> se aleja de la cueva -> cierre natural
    gl(()->{ tf.position.set(-5.0f,0f,-0.5f); foxy.stateMachine.changeState(IdleState.INSTANCE); foxy.rangoDeteccion=0.1f; });
    colocar(-3.5f,-0.5f,-90f); mirarA(-8.5f,1.6f,-0.5f); for(int i=0;i<4;i++){ frames(60,null); System.out.println("      t+"+(i+1)+"s foxy x="+tf.position.x+" estado="+foxy.stateMachine.getCurrentState().getClass().getSimpleName()+" apertura="+cor.getApertura()+" abierta="+cor.isAbierta()); } frames(1,"cortina_3_cerrandose"); frames(90,null);
    ok(cor.getApertura()==0f, "con Foxy fuera de la cueva la cortina se cierra sola ("+cor.getApertura()+")");
  }
  // ---- intro: un toque de ESC no la salta; mantener ESC si; instrucciones con el mundo detenido; E empieza
  void pruebaIntro() throws Exception {
    frames(60,null); String e=campo("estado").toString(); ok(e.equals("CINEMATICA_INICIAL"), "arranca en la introduccion");
    apretadas.add(Input.Keys.ESCAPE); frames(18,null); apretadas.clear(); frames(60,null); ok(campo("estado").toString().equals("CINEMATICA_INICIAL"), "un toque de 0.3 s no la omite");
    apretadas.add(Input.Keys.ESCAPE); frames(40,"intro_omitiendo"); frames(40,null); apretadas.clear();
    ok(campo("estado").toString().equals("INSTRUCCIONES") && (Long)campo("idSonidoEscape")==-1L, "mantener ESC 1.3 s la omite -> instrucciones, narracion cortada");
    // mundo detenido: Freddy no se mueve en 3 s
    Vector3 f0=new Vector3(tGuardia("freddy").position); frames(180,"instrucciones"); Vector3 f1=new Vector3(tGuardia("freddy").position);
    ok(f0.dst(f1)<0.001f && campo("estado").toString().equals("INSTRUCCIONES"), "en instrucciones el mundo esta detenido (Freddy se movio "+f0.dst(f1)+" m)");
    apretadas.add(Input.Keys.W); frames(30,null); apretadas.clear(); Vector3 pj=pos(); ok(Math.abs(pj.z-10f)<0.01f, "en instrucciones el jugador no se mueve con W");
    pulsar(Input.Keys.E); ok(campo("estado").toString().equals("INTRO_CORAZONES"), "E comienza: corazones -> CORRE -> juego");
    frames((int)(4.2f/dt),null); ok(campo("estado").toString().equals("JUGANDO"), "despues el juego empieza ("+campo("estado")+")");
  }
}
