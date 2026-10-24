import com.badlogic.gdx.*; import com.badlogic.gdx.backends.lwjgl3.*; import com.badlogic.gdx.graphics.*; import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.math.*; import com.badlogic.gdx.math.collision.BoundingBox; import com.badlogic.gdx.utils.*; import com.badlogic.gdx.files.*;
import com.fivedoorsescape.assets.*; import com.fivedoorsescape.content.*; import com.fivedoorsescape.world.*; import com.fivedoorsescape.screens.*;
import com.fivedoorsescape.io.*; import com.fivedoorsescape.cinematics.*; import com.fivedoorsescape.ecs.*; import com.fivedoorsescape.ecs.components.*;
import java.lang.reflect.*;
// Recorrido real de la victoria con GameplayScreen: llave -> puerta de salida -> deposito -> puerta de emergencia ->
// estacionamiento -> puerta del auto -> cinematica -> EscapeVictoryScreen. El jugador se mueve con CollisionWorld real
// (si un tramo queda bloqueado, falla). Pruebas de limites (valla, barrera, auto, patio). Capturas en FrameBuffer.
// args: salidaDir modo(completa|saltar) idioma(ESPANOL|INGLES)
public class PruebaVictoria extends Game {
  String out, modo; HandoffData.Idioma idioma; GameplayScreen gs; FrameBuffer fb; int fallos = 0; float dt = 1f / 30f;
  static boolean inyectarEsc = false;
  PruebaVictoria(String[] a){ out=a[0]; modo=a[1]; idioma=HandoffData.Idioma.valueOf(a[2]); }
  public static void main(String[] a){ Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration(); c.setWindowedMode(1280,720); c.setInitialVisible(false); c.setForegroundFPS(0); c.useVsync(false); new Lwjgl3Application(new PruebaVictoria(a),c); }
  void ok(boolean b, String s){ System.out.println((b?"OK    ":"FALLO ")+s); if(!b) fallos++; }
  Object campo(String n) throws Exception { Field f=GameplayScreen.class.getDeclaredField(n); f.setAccessible(true); return f.get(gs); }
  void set(String n, Object v) throws Exception { Field f=GameplayScreen.class.getDeclaredField(n); f.setAccessible(true); f.set(gs,v); }
  void llamar(String n) throws Exception { Method m=GameplayScreen.class.getDeclaredMethod(n); m.setAccessible(true); m.invoke(gs); }

  public void create(){
    final Input real=Gdx.input;
    Gdx.input=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class}, (p,m,args)->{
      String n=m.getName();
      if(n.equals("setCursorCatched")) return null;                       // nunca capturar el raton real
      if(n.equals("getDeltaX")||n.equals("getDeltaY")) return 0;
      if(n.equals("isKeyPressed")){ int k=(Integer)args[0]; return inyectarEsc && k==Input.Keys.ESCAPE; }
      if(n.equals("isKeyJustPressed")) return false;
      return m.invoke(real,args); });
    try {
      ContentRegistry reg=new ContentRegistry(); AssetService as=new AssetService(); MapDefinition md=reg.getMapDefinition("pizzeria");
      as.queueModel(md.modelPath);
      for(String e: new String[]{"freddy","bonnie","chica","foxy","llave","golden_freddy"}){ as.queueModel(reg.getEntityDefinition(e).modelPath); for(String snd: com.fivedoorsescape.audio.PasosAnimatronicos.sonidosDe(reg.getEntityDefinition(e))) as.queueSound(snd); }
      for(Field f: GameplayScreen.class.getFields()) if(f.getName().startsWith("SONIDO_")) as.queueSound((String)f.get(null));
      ExteriorDefinition ex=reg.getExteriorDefinition(); as.queueModel(ex.modelPath); as.queueModel(ex.carModelPath);
      for(String s: CinematicaVictoria.SONIDOS) as.queueSound(s); for(String s: CinematicaDerrota.SONIDOS) as.queueSound(s);
      long t0=System.nanoTime(); as.finishLoading(); System.out.println("carga "+(System.nanoTime()-t0)/1000000+" ms");
      gs=new GameplayScreen(this, reg, as, new HandoffData(idioma, 3));
      setScreen(gs);
      fb=new FrameBuffer(Pixmap.Format.RGBA8888,1280,720,true);
      new Thread(this::guion,"guion").start();
    } catch(Exception e){ e.printStackTrace(); Gdx.app.exit(); }
  }

  // ---- el guion corre en otro hilo y pide frames al hilo GL de a uno
  final Object lock=new Object(); int framesPedidos=0; String capturaPendiente=null; Runnable tareaGL=null;
  public void render(){
    synchronized(lock){
      if(tareaGL!=null){ try{ tareaGL.run(); }catch(Throwable t){ t.printStackTrace(); fallos++; } tareaGL=null; lock.notifyAll(); }
      if(framesPedidos>0){
        Gdx.gl.glViewport(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
        try{ getScreen().render(dt); }catch(Throwable t){ t.printStackTrace(); fallos++; framesPedidos=1; }
        if(capturaPendiente!=null && framesPedidos==1){ int W=Gdx.graphics.getBackBufferWidth(),H=Gdx.graphics.getBackBufferHeight(); Pixmap px=Pixmap.createFromFrameBuffer(0,0,W,H);
          Pixmap fl=new Pixmap(W,H,Pixmap.Format.RGBA8888); for(int y=0;y<H;y++) fl.drawPixmap(px,0,y,W,1,0,H-1-y,W,1);
          PixmapIO.writePNG(new FileHandle(out+"/"+capturaPendiente+".png"),Opaco.aplicar(fl)); px.dispose(); fl.dispose(); capturaPendiente=null; }
        framesPedidos--; lock.notifyAll(); }
    }
  }
  void frames(int n, String captura){ synchronized(lock){ framesPedidos=n; capturaPendiente=captura; while(framesPedidos>0) try{ lock.wait(); }catch(InterruptedException e){} } }
  void gl(Runnable r){ synchronized(lock){ tareaGL=r; while(tareaGL!=null) try{ lock.wait(); }catch(InterruptedException e){} } }

  TransformComponent jugador() throws Exception { return Mappers.transform.get((com.badlogic.ashley.core.Entity)campo("playerEntity")); }
  com.fivedoorsescape.camera.FirstPersonCameraController cc() throws Exception { return (com.fivedoorsescape.camera.FirstPersonCameraController)campo("cameraController"); }
  void colocar(float x, float z, float yaw){ gl(()->{ try{ TransformComponent t=jugador(); t.position.set(x,1.6f,z); cc().resetOrientation(yaw); }catch(Exception e){ throw new RuntimeException(e);} }); }
  void mirarA(float x, float y, float z){ gl(()->{ try{ TransformComponent t=jugador(); float yaw=MathUtils.atan2(x-t.position.x, z-t.position.z)*MathUtils.radiansToDegrees; cc().resetOrientation(yaw);
      Field fp=cc().getClass().getDeclaredField("pitchDegrees"); fp.setAccessible(true); float d=(float)Math.hypot(x-t.position.x,z-t.position.z); fp.setFloat(cc(), MathUtils.atan2(y-1.6f,d)*MathUtils.radiansToDegrees); }catch(NoSuchFieldException e){ }catch(Exception e){ throw new RuntimeException(e);} }); }

  /** Camina hacia (x,z) a 2.2 m/s con la colision real del jugador. Devuelve la distancia final al objetivo. */
  float caminar(float x, float z, boolean mirar) throws Exception {
    CollisionWorld cw=(CollisionWorld)campo("collisionWorld"); final float[] res={0};
    int quieto=0; float prev=Float.MAX_VALUE;
    for(int i=0;i<600;i++){
      final boolean[] fin={false};
      gl(()->{ try{ TransformComponent t=jugador(); Vector3 d=new Vector3(x-t.position.x,0,z-t.position.z); float dist=d.len(); res[0]=dist;
        if(dist<0.08f){ fin[0]=true; return; } d.nor().scl(Math.min(dist,2.2f*dt));
        Vector3 r=cw.resolveMovement(t.position,d,new Vector3(0.3f,0.9f,0.3f),true); t.position.x=r.x; t.position.z=r.z;
        if(mirar) cc().resetOrientation(MathUtils.atan2(x-t.position.x,z-t.position.z)*MathUtils.radiansToDegrees); }catch(Exception e){ throw new RuntimeException(e);} });
      if(fin[0]) break; frames(1,null);
      if(res[0]>prev-0.002f) quieto++; else quieto=0; prev=res[0]; if(quieto>20) break; }
    return res[0];
  }
  Vector3 pos() throws Exception { final Vector3 v=new Vector3(); gl(()->{ try{ v.set(jugador().position);}catch(Exception e){} }); return v; }

  void guion(){
    try {
      frames(3,null);
      ExteriorDefinition ex=((SalidaExterior)campo("salidaExterior")).getDefinicion();
      SalidaExterior sal=(SalidaExterior)campo("salidaExterior");
      System.out.println("triangulos retirados: pared="+sal.getTriangulosParedRetirados()+" estante="+sal.getTriangulosEstanteRetirados());
      ok(sal.getTriangulosParedRetirados()>=6, "se abrio el hueco de la pared oeste del deposito");
      ok(sal.getTriangulosEstanteRetirados()>0, "se retiro el estante que tapaba la pared");
      // saltar la cinematica inicial
      gl(()->{ try{ Class<?> e=Class.forName("com.fivedoorsescape.screens.GameplayScreen$EstadoPartida"); for(Object o: e.getEnumConstants()) if(o.toString().equals("JUGANDO")) set("estado",o); set("tiempoJugandoTranscurrido",100f); }catch(Exception e){ throw new RuntimeException(e);} });
      // 1) sin llave la puerta sigue cerrada
      colocar(-8.15f,-6.35f,-90f); mirarA(-8.87f,1.2f,-6.32f); frames(4,"01_puerta_salida_cerrada");
      float dSinLlave=caminar(-9.8f,-6.4f,true); Vector3 p=pos(); ok(p.x>-8.6f, "sin abrir, la puerta de salida bloquea el paso (x="+p.x+")");
      // 2) con llave: E sobre la puerta
      colocar(-8.15f,-6.35f,-90f); mirarA(-8.87f,1.2f,-6.32f); set("tieneLlave",true);
      gl(()->{ try{ llamar("interactuarConPuertaSalida"); }catch(Exception e){ throw new RuntimeException(e);} });
      ok(sal.puertaSalidaAbierta(), "con la llave, la puerta de salida se abre");
      gl(()->{ try{ CollisionWorld cw=(CollisionWorld)campo("collisionWorld"); for(com.badlogic.gdx.math.collision.BoundingBox b: cw.getStaticColliders()) if(b.max.x>-12.7f&&b.min.x<-11.0f&&b.max.z>-6.0f&&b.min.z<-4.4f&&b.min.y<2.5f&&b.max.y>0.7f) System.out.println("      queda "+b); }catch(Exception e){} });
      frames(18,"02_puerta_salida_abriendose"); frames(40,"03_puerta_salida_abierta");
      // 3) cruzar el deposito hasta la puerta de emergencia (colision real)
      float d1=caminar(-9.7f,-6.4f,true), d2=caminar(-11.3f,-6.35f,true);
      mirarA(ex.wallX,1.3f,ex.emergencyDoorZ); frames(3,"04_deposito_exit");
      float d3=caminar(-11.6f,ex.emergencyDoorZ,true), d4=caminar(-12.2f,ex.emergencyDoorZ,true);
      p=pos(); System.out.println("      deposito: restos "+d1+" "+d2+" "+d3+" "+d4+" -> "+p);
      ok(d1<0.1f&&d2<0.1f&&d3<0.1f&&d4<0.1f, "el jugador cruza el deposito hasta la puerta de emergencia");
      float dPared=caminar(-13.6f,ex.emergencyDoorZ,true); p=pos(); ok(p.x>ex.wallX, "la puerta de emergencia cerrada bloquea (x="+p.x+")");
      // 4) barra de la puerta de emergencia
      mirarA(ex.wallX,1.0f,ex.emergencyDoorZ); frames(2,null);
      gl(()->{ try{ System.out.println("      objetivo interactivo frente a la barra: "+(campo("objetivoInteractivoActual")==campo("interactivoBarraEmergencia"))); llamar("abrirPuertaEmergencia"); }catch(Exception e){ throw new RuntimeException(e);} });
      ok(sal.puertaEmergenciaAbierta() && sal.isExteriorActivo(), "la barra abre la puerta de emergencia y activa el exterior");
      mirarA(-20f,1.2f,-5f); frames(30,"05_puerta_emergencia_afuera");
      // 5) afuera, hasta la puerta del conductor rodeando el frente del auto
      float e1=caminar(-13.6f,ex.emergencyDoorZ,true); mirarA(ex.carX,1.0f,ex.carZ); frames(3,"06_estacionamiento");
      float e2=caminar(-17.5f,-8.0f,true), e3=caminar(-21.9f,-7.6f,true), e4=caminar(-21.95f,-4.6f,true);
      p=pos(); System.out.println("      estacionamiento: restos "+e1+" "+e2+" "+e3+" "+e4+" -> "+p);
      ok(e1<0.1f&&e2<0.1f&&e3<0.1f&&e4<0.1f, "el jugador llega a la puerta del conductor");
      mirarA(-13f,1.6f,-5.2f); frames(3,"07_mirando_la_puerta");
      ok((Boolean)campo("freddyEnPuertaEmergencia"), "Freddy ya esta en la puerta de emergencia (aparecio fuera de la vista)");
      // 5b) Freddy tiene presencia fisica: no se lo atraviesa, tapa el regreso al edificio y amenaza al acercarse (§2.68)
      final FreddyExterior fe=(FreddyExterior)campo("freddyExterior");
      ok(fe!=null && fe.tieneCollider(), "Freddy en la puerta tiene collider");
      // 5a) susto (§2.74): una sola vez, al acercarse mirandolo; sin derrota, collider fijo, camara restaurada
      { final BoundingBox cajaAntes=new BoundingBox(fe.getCollider());
        colocar(-16.5f, ex.freddyDoorZ, 90f); mirarA(ex.freddyDoorX,1.6f,ex.freddyDoorZ); frames(10,null);
        ok(!fe.isSustoHecho() && fe.getIntensidadSusto()==0f, "a 4.4 m todavia no hay susto");
        final float[] dAct={-1}; float maxEmb=0, minOjo=99, minFov=99, maxInt=0; int fSusto=-1; String est="";
        for(int f=0; f<200; f++){
          final boolean activo=fe.isSustoHecho();
          gl(()->{ try{ TransformComponent t=jugador(); if(!activo){ t.position.x+=2.2f*dt; } cc().resetOrientation(MathUtils.atan2(ex.freddyDoorX-t.position.x,ex.freddyDoorZ-t.position.z)*MathUtils.radiansToDegrees);
            if(!activo) dAct[0]=Vector3.dst(t.position.x,0,t.position.z,ex.freddyDoorX,0,ex.freddyDoorZ); }catch(Exception e){ throw new RuntimeException(e);} });
          String cap=null; if(fSusto>=0 && f-fSusto==5) cap="07c_susto_embestida"; if(fSusto>=0 && f-fSusto==14) cap="07d_susto_vuelve";
          frames(1,cap);
          if(fe.isSustoHecho() && fSusto<0) fSusto=f;
          if(fSusto>=0){ final float[] q=new float[3]; gl(()->{ try{ PerspectiveCamera cam=(PerspectiveCamera)campo("camera"); q[0]=cam.fieldOfView; q[1]=cam.position.dst(fe.getPosicion().x,1.6f,fe.getPosicion().z); q[2]=Vector3.dst(fe.getPosicion().x,0,fe.getPosicion().z,ex.freddyDoorX,0,ex.freddyDoorZ); }catch(Exception e){} });
            minFov=Math.min(minFov,q[0]); minOjo=Math.min(minOjo,q[1]); maxEmb=Math.max(maxEmb,q[2]); maxInt=Math.max(maxInt,fe.getIntensidadSusto());
            if(!campo("estado").toString().equals("JUGANDO")) est=campo("estado").toString(); }
          if(fSusto>=0 && f-fSusto>45) break; }
        System.out.println(String.format("      susto: se activo a %.2f m de Freddy, embestida maxima %.2f m, ojo-Freddy minimo %.2f m, campo de vision minimo %.1f, intensidad maxima %.2f", dAct[0], maxEmb, minOjo, minFov, maxInt));
        ok(fSusto>=0 && dAct[0]<=2.31f && dAct[0]>1.0f, "el susto se activa al acercarse mirandolo (a "+dAct[0]+" m)");
        ok(maxEmb>0.2f && maxEmb<=0.451f, "Freddy embiste hacia el jugador ("+maxEmb+" m)");
        ok(minFov<60f && maxInt>0.9f, "refuerzo de camara: el campo de vision se cierra hasta "+minFov);
        final float[] fin=new float[3]; gl(()->{ try{ PerspectiveCamera cam=(PerspectiveCamera)campo("camera"); fin[0]=cam.fieldOfView; fin[1]=Vector3.dst(fe.getPosicion().x,0,fe.getPosicion().z,ex.freddyDoorX,0,ex.freddyDoorZ); }catch(Exception e){} });
        ok(fe.getIntensidadSusto()==0f && fin[0]==67f && fin[1]<0.01f, "termina: camara restaurada (fov "+fin[0]+") y Freddy de vuelta en la puerta ("+fin[1]+" m)");
        ok(est.isEmpty() && !(Boolean)campo("escapado"), "el susto no es una derrota ni una victoria (estado siempre JUGANDO)");
        ok(cajaAntes.min.epsilonEquals(fe.getCollider().min,1e-4f) && cajaAntes.max.epsilonEquals(fe.getCollider().max,1e-4f) && fe.tieneCollider(), "el collider de Freddy no se movio con la embestida");
        // segunda vez: alejarse y volver a acercarse, sin susto
        colocar(-16.5f, ex.freddyDoorZ, 90f); frames(30,null); float maxInt2=0;
        for(int f=0; f<120; f++){ gl(()->{ try{ TransformComponent t=jugador(); t.position.x=Math.min(t.position.x+2.2f*dt, ex.freddyDoorX-1.2f); cc().resetOrientation(-90f+180f); }catch(Exception e){ throw new RuntimeException(e);} }); mirarA(ex.freddyDoorX,1.6f,ex.freddyDoorZ); frames(1,null); maxInt2=Math.max(maxInt2,fe.getIntensidadSusto()); }
        ok(maxInt2==0f, "una sola vez: al volver a acercarse no hay otro susto (intensidad maxima "+maxInt2+")"); }
      colocar(-15.5f, ex.freddyDoorZ, 90f); frames(5,null); float yaw0=fe.getPosicion().x; final float[] yawAntes={0}; gl(()->{ try{ yawAntes[0]=Mappers.transform.get((com.badlogic.ashley.core.Entity)campo("freddyEntity")).yawDegrees; }catch(Exception e){} });
      caminar(-11.0f, ex.freddyDoorZ, true); p=pos();
      ok(p.x < ex.freddyDoorX - 0.6f, "caminando hacia Freddy el jugador se detiene contra el (x="+p.x+", Freddy en "+ex.freddyDoorX+")");
      CollisionWorld cwF=(CollisionWorld)campo("collisionWorld"); ok(!cwF.overlaps(p,new Vector3(0.3f,0.9f,0.3f),true), "y no queda incrustado");
      caminar(-12.6f, ex.freddyDoorZ - 1.1f, true); caminar(-11.5f, ex.freddyDoorZ - 0.2f, true); p=pos();
      ok(p.x < ex.wallX + 0.05f, "tampoco se cuela por el costado al edificio (x="+p.x+")");
      mirarA(ex.freddyDoorX,1.9f,ex.freddyDoorZ); frames(40,"07b_freddy_amenaza");
      final float[] yawDespues={0}; gl(()->{ try{ yawDespues[0]=Mappers.transform.get((com.badlogic.ashley.core.Entity)campo("freddyEntity")).yawDegrees; }catch(Exception e){} });
      System.out.println("      yaw de Freddy: "+yawAntes[0]+" -> "+yawDespues[0]);
      ok(Math.abs(yawDespues[0]-yawAntes[0])>5f, "al acercarse, Freddy gira hacia el jugador (amenaza)");
      ok(!(Boolean)campo("escapado") && campo("estado").toString().equals("JUGANDO"), "la amenaza no mata ni termina la partida");
      // 6) limites del exterior (posiciones de prueba, luego se vuelve)
      colocar(-24f,-9f,-90f); caminar(-30f,-9f,false); p=pos(); ok(p.x>ex.lotMinX, "la valla oeste no se atraviesa (x="+p.x+")");
      colocar(-21f,-13.5f,180f); caminar(-21f,-19f,false); p=pos(); ok(p.z>ex.lotMinZ, "la barrera del porton no se atraviesa (z="+p.z+")");
      colocar(-23.2f,-4.5f,90f); caminar(-19.5f,-4.5f,false); p=pos(); ok(p.x<ex.carX-ex.carHalfWidth, "el auto no se atraviesa (x="+p.x+")");
      colocar(-14f,-11f,90f); caminar(-10f,-11f,false); p=pos(); ok(p.x<ex.wallX, "la valla del patio trasero no se atraviesa (x="+p.x+")");
      colocar(-16f,0.2f,0f); caminar(-16f,4f,false); p=pos(); ok(p.z<ex.lotMaxZ, "la valla norte no se atraviesa (z="+p.z+")");
      // 7) puerta del conductor
      colocar(-21.95f,-4.6f,90f); final Vector3 manija=new Vector3(); gl(()->{ try{ Object o=campo("interactivoAuto"); Field f=o.getClass().getDeclaredField("posicion"); f.setAccessible(true); manija.set((Vector3)f.get(o)); }catch(Exception e){ throw new RuntimeException(e);} });
      mirarA(manija.x,manija.y,manija.z); frames(2,"08_puerta_conductor");
      final boolean[] apunta={false}; gl(()->{ try{ apunta[0]=campo("objetivoInteractivoActual")==campo("interactivoAuto"); }catch(Exception e){} });
      ok(apunta[0], "mirando la manija, la puerta del conductor es el objetivo interactivo (E)");
      gl(()->{ try{ llamar("subirAlAuto"); }catch(Exception e){ throw new RuntimeException(e);} });
      float[] marcas = modo.equals("saltar") ? new float[]{2.0f} : new float[]{0.9f,2.0f,3.3f,4.4f,5.3f,5.9f,6.3f,6.7f,7.0f,7.6f,8.3f,9.2f,9.4f,9.6f,10.8f,11.9f,12.6f,13.4f,14.3f,15.8f};
      final float[] minDist={99f};
      final java.util.List<float[]> registroPasos=new java.util.ArrayList<>();   // t, pisadas, audibles, ganancia, distancia camara-Freddy
      float t=0; int k=0; long w0=System.nanoTime();
      while(t<CinematicaVictoria.DURACION+1f && getScreen()==gs){
        String cap = k<marcas.length && t+dt>=marcas[k] ? String.format("c%02d_%04.1fs", k, marcas[k]) : null; if(cap!=null) k++;
        if(modo.equals("saltar") && t>=2.0f) inyectarEsc=true;   // mantener ESC desde los 2 s
        frames(1,cap); t+=dt;
        if(getScreen()==gs){ final float tt=t; gl(()->{ try{ FreddyExterior f2=(FreddyExterior)campo("freddyExterior"); SalidaExterior se=(SalidaExterior)campo("salidaExterior");
          if(f2!=null && f2.getEstado()!=FreddyExterior.Estado.AUSENTE && f2.getEstado()!=FreddyExterior.Estado.EN_PUERTA) minDist[0]=Math.min(minDist[0], Vector3.dst(f2.getPosicion().x,0,f2.getPosicion().z,se.posicionAuto.x,0,se.posicionAuto.z));
          if(Math.abs(tt-12.0f)<dt/2) System.out.println("      t=12: Freddy "+f2.getEstado()+" en "+f2.getPosicion());
          com.fivedoorsescape.audio.PasosAnimatronicos pa=(com.fivedoorsescape.audio.PasosAnimatronicos)campo("pasos"); com.badlogic.ashley.core.Entity fe2=(com.badlogic.ashley.core.Entity)campo("freddyEntity"); Camera cam=(Camera)campo("camera");
          registroPasos.add(new float[]{tt, pa.pisadasDe(fe2), pa.audiblesDe(fe2), pa.gananciaDe(fe2), Vector3.dst(cam.position.x,0,cam.position.z,f2.getPosicion().x,0,f2.getPosicion().z), f2.getEstado().ordinal()}); }catch(Exception e){} }); } }
      if(!modo.equals("saltar") && registroPasos.size()>2){
        float maxSalto=0, tSalto=0, maxSaltoDist=0; int p0=-1, p1=0, a0=0, a1=0; float tIni=-1, tFin=0;
        for(int i=1;i<registroPasos.size();i++){ float[] q=registroPasos.get(i), w=registroPasos.get(i-1);
          if(Math.abs(q[3]-w[3])>maxSalto){ maxSalto=Math.abs(q[3]-w[3]); tSalto=q[0]; } maxSaltoDist=Math.max(maxSaltoDist,Math.abs(q[4]-w[4]));
          if(q[5]==FreddyExterior.Estado.PERSIGUE.ordinal()){ if(p0<0){ p0=(int)w[1]; a0=(int)w[2]; tIni=q[0]; } p1=(int)q[1]; a1=(int)q[2]; tFin=q[0]; } }
        float durP=tFin-tIni; System.out.println(String.format("      persecucion %.2f..%.2f s: %d pisadas detectadas (%.1f/s), %d audibles; mayor salto de distancia camara-Freddy en un cuadro %.1f m; mayor salto de ganancia en un cuadro %.3f (t=%.2f)", tIni, tFin, p1-p0, (p1-p0)/Math.max(0.01f,durP), a1-a0, maxSaltoDist, maxSalto, tSalto));
        StringBuilder sb=new StringBuilder("      ganancia alrededor del corte (8.8-10.1 s):"); for(float[] q: registroPasos) if(q[0]>=8.8f&&q[0]<=10.1f) sb.append(String.format(" %.2f:%.2f/%.1fm",q[0],q[3],q[4])); System.out.println(sb);
        ok(p1-p0 >= (int)(durP*3.0f), "Freddy pisa al ritmo de su carrera (animacion al 1.9x: ~3.8 pisadas/s)");
        ok(maxSaltoDist<4f || maxSalto<0.12f, "el corte de camara no produce un salto de volumen de los pasos (ganancia suavizada)");
      }
      if(!modo.equals("saltar")) ok(minDist[0] > 2.6f, "Freddy persigue al auto sin alcanzarlo (distancia minima al centro del auto "+minDist[0]+" m)");
      System.out.println("      cinematica: "+t+" s simulados en "+(System.nanoTime()-w0)/1000000+" ms reales");
      ok(getScreen() instanceof EscapeVictoryScreen, "termina en EscapeVictoryScreen ("+getScreen().getClass().getSimpleName()+")");
      inyectarEsc=false;
      if(modo.equals("saltar")) ok(t>3.15f && t<3.35f, "mantener ESC 1.2 s la omite (t="+t+", empezo a los 2.0 s)"); else ok(t>16.2f&&t<16.8f, "dura lo previsto ("+t+" s)");
      ok((Boolean)campo("escapado"), "la victoria queda registrada (escapado)");
      java.io.File senal=new java.io.File(System.getProperty("user.home"), ".fivedoorsatfreddys/escape_victoria.flag");
      ok(senal.isFile(), "se escribio la senal de victoria para FDAF ("+senal+")"); senal.delete();
      frames(2,"09_pantalla_victoria");
    } catch(Throwable e){ e.printStackTrace(); fallos++; }
    System.out.println("FALLOS: "+fallos);
    Gdx.app.postRunnable(()->Gdx.app.exit());
  }
}
