import com.badlogic.gdx.*; import com.badlogic.gdx.backends.lwjgl3.*; import com.badlogic.gdx.graphics.*; import com.badlogic.gdx.files.*; import java.lang.reflect.*;
import com.fivedoorsescape.*; import com.fivedoorsescape.screens.*;
// Arranque real con capturas: pantalla de carga (progreso, preparando), introduccion con el indicador de omision,
// omision manteniendo ESC, pantalla de instrucciones. args: salida prefijo
public class PruebaCarga extends FiveDoorsEscapeGame {
  String out, pre; int frame = 0, fallos = 0; long t0; boolean esc = false; float tEstado = 0; String ultimo = ""; int capturas = 0; float ultProg = -1;
  PruebaCarga(String[] a){ out=a[0]; pre=a[1]; }
  public static void main(String[] a){ Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration(); c.setWindowedMode(1280,720); c.setInitialVisible(false); new Lwjgl3Application(new PruebaCarga(a),c); }
  void cap(String n){ int W=Gdx.graphics.getBackBufferWidth(),H=Gdx.graphics.getBackBufferHeight(); Pixmap px=Pixmap.createFromFrameBuffer(0,0,W,H); Pixmap fl=new Pixmap(W,H,Pixmap.Format.RGBA8888);
    for(int y=0;y<H;y++) fl.drawPixmap(px,0,y,W,1,0,H-1-y,W,1); PixmapIO.writePNG(new FileHandle(out+"/"+pre+"_"+n+".png"),Opaco.aplicar(fl)); px.dispose(); fl.dispose(); }
  public void create(){ final Input real=Gdx.input; Gdx.input=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class},(p,m,ar)->{ String n=m.getName(); if(n.equals("setCursorCatched")) return null; if(n.startsWith("getDelta")) return 0;
      if(n.equals("isKeyPressed")) return esc && (Integer)ar[0]==Input.Keys.ESCAPE; if(n.startsWith("isKey")||n.equals("justTouched")||n.equals("isButtonPressed")) return false; return m.invoke(real,ar);}); t0=System.nanoTime(); super.create(); }
  Object f(String n) throws Exception { Field x=GameplayScreen.class.getDeclaredField(n); x.setAccessible(true); return x.get(getScreen()); }
  public void render(){
    try { super.render(); } catch(Throwable t){ t.printStackTrace(); fallos++; }
    frame++;
    try {
      if(getScreen() instanceof BootScreen){
        Field fp=BootScreen.class.getDeclaredField("progresoMostrado"); fp.setAccessible(true); float p=(Float)fp.get(getScreen());
        Field fa=BootScreen.class.getDeclaredField("fase"); fa.setAccessible(true); int fase=(Integer)fa.get(getScreen());
        if((capturas==0 && p>0.25f) || (capturas==1 && p>0.7f) || (capturas==2 && fase==1)){ cap("carga_"+capturas); System.out.println(String.format("      captura de carga %d: progreso %.2f fase %d a los %d ms", capturas, p, fase, (System.nanoTime()-t0)/1000000)); capturas++; }
        if(p < ultProg - 1e-4f){ System.out.println("FALLO la barra retrocedio"); fallos++; } ultProg=p;
        if(frame==3){ Field fc=BootScreen.class.getDeclaredField("consejo"); fc.setAccessible(true); System.out.println("      consejo: "+fc.get(getScreen())); }
      } else if(getScreen() instanceof GameplayScreen){
        String e=f("estado").toString(); if(!e.equals(ultimo)){ System.out.println(String.format("%6d ms  %s", (System.nanoTime()-t0)/1000000, e)); ultimo=e; tEstado=0; }
        tEstado+=Gdx.graphics.getDeltaTime();
        if(e.equals("CINEMATICA_INICIAL") && tEstado>2.0f && !esc){ cap("intro"); esc=true; }
        if(e.equals("CINEMATICA_INICIAL") && esc && tEstado>2.6f && tEstado<2.7f) cap("intro_omitiendo");
        if(e.equals("INSTRUCCIONES")){ esc=false; if(tEstado>0.8f){ cap("instrucciones"); System.out.println("FALLOS: "+fallos); Gdx.app.exit(); } }
      }
    } catch(Exception ex){ ex.printStackTrace(); fallos++; }
    if((System.nanoTime()-t0)/1e9 > 100){ System.out.println("FALLO tiempo agotado"); Gdx.app.exit(); }
  }
}
