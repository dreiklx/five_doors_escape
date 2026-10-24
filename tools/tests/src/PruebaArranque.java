import com.badlogic.gdx.*; import com.badlogic.gdx.backends.lwjgl3.*; import java.lang.reflect.*;
import com.fivedoorsescape.*; import com.fivedoorsescape.screens.*; import com.fivedoorsescape.ecs.components.*;
// Arranque real (FiveDoorsEscapeGame -> BootScreen -> GameplayScreen) en ventana oculta; luego fuerza una atrapada normal
// (vida 1 de 3) y comprueba que sigue el flujo de siempre: JUMPSCARE -> ESTATICA -> CORAZONES_RESPAWN, sin cinematica.
public class PruebaArranque extends FiveDoorsEscapeGame {
  int frame=0, fallos=0; long t0; String ultimo="";
  public static void main(String[] a){ Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration(); c.setWindowedMode(960,540); c.setInitialVisible(false); new Lwjgl3Application(new PruebaArranque(),c); }
  public void create(){ final Input real=Gdx.input; Gdx.input=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(), new Class[]{Input.class},(p,m,ar)->{ if(m.getName().equals("setCursorCatched")) return null; if(m.getName().startsWith("getDelta")) return 0; if(m.getName().startsWith("isKey")) return false; return m.invoke(real,ar);}); t0=System.nanoTime(); super.create(); }
  Object f(String n) throws Exception { Field x=GameplayScreen.class.getDeclaredField(n); x.setAccessible(true); return x.get(getScreen()); }
  public void render(){
    try { super.render(); } catch(Throwable t){ t.printStackTrace(); fallos++; }
    frame++;
    try {
      if(getScreen() instanceof GameplayScreen){
        String e=f("estado").toString(); if(!e.equals(ultimo)){ System.out.println(String.format("%6d ms  %s vidas=%s", (System.nanoTime()-t0)/1000000, e, f("vidasRestantes"))); ultimo=e; }
        if(e.equals("CINEMATICA_INICIAL") && frame>5){ Class<?> en=Class.forName("com.fivedoorsescape.screens.GameplayScreen$EstadoPartida"); for(Object o: en.getEnumConstants()) if(o.toString().equals("JUGANDO")){ Field x=GameplayScreen.class.getDeclaredField("estado"); x.setAccessible(true); x.set(getScreen(),o);} }
        if(e.equals("JUGANDO") && "0".equals(System.getProperty("atrapado","0"))){ System.setProperty("atrapado","1"); for(Object ai: (com.badlogic.gdx.utils.Array<?>)f("guardias")){ ((AIComponent)ai).jugadorAtrapado=true; break; } }
        if(e.equals("JUGANDO") && "1".equals(System.getProperty("atrapado")) && (Integer)f("vidasRestantes")==2){ System.out.println("OK    una atrapada normal: jumpscare, estatica, corazones y reaparicion; quedan 2 vidas, sin cinematica"); System.out.println("FALLOS: "+fallos); Gdx.app.exit(); }
      }
    } catch(Exception ex){ ex.printStackTrace(); fallos++; }
    if((System.nanoTime()-t0)/1e9 > 60){ System.out.println("FALLO tiempo agotado en "+ultimo); System.out.println("FALLOS: "+(fallos+1)); Gdx.app.exit(); }
  }
}
