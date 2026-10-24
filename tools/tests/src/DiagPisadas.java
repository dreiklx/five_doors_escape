import com.badlogic.gdx.*; import com.badlogic.gdx.backends.lwjgl3.*; import com.badlogic.gdx.math.*; import com.badlogic.gdx.graphics.g3d.model.*;
import net.mgsx.gltf.loaders.glb.GLBLoader; import net.mgsx.gltf.scene3d.scene.*;
// Altura en el mundo de los huesos de los pies durante el ciclo "walk" de cada animatronico: minimos (contactos),
// rango, periodo. args: modelo escala clip velocidad hueso1 hueso2 [...]
public class DiagPisadas extends ApplicationAdapter {
  String[] a; DiagPisadas(String[] a){ this.a=a; }
  public static void main(String[] a){ Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration(); c.setWindowedMode(64,64); c.setInitialVisible(false); new Lwjgl3Application(new DiagPisadas(a),c); }
  public void create(){
    SceneAsset sa=new GLBLoader().load(Gdx.files.internal(a[0]));
    Scene s=new Scene(sa.scene); float esc=Float.parseFloat(a[1]); s.modelInstance.transform.setToScaling(esc,esc,esc);
    float vel=Float.parseFloat(a[3]);
    s.animationController.setAnimation(a[2],-1,vel,null);
    int nh=a.length-4; Node[] n=new Node[nh];
    for(int i=0;i<nh;i++){ n[i]=s.modelInstance.getNode(a[4+i],true); if(n[i]==null) System.out.println("NO ENCONTRADO "+a[4+i]); }
    float dt=1/240f; int pasos=(int)(4f/dt); float[][] y=new float[nh][pasos]; float[][] fz=new float[nh][pasos]; Vector3 v=new Vector3();
    for(int k=0;k<pasos;k++){ s.update(new com.badlogic.gdx.graphics.PerspectiveCamera(),dt);
      for(int i=0;i<nh;i++){ if(n[i]==null) continue; n[i].globalTransform.getTranslation(v); v.mul(s.modelInstance.transform); y[i][k]=v.y; fz[i][k]=v.z; } }
    for(int i=0;i<nh;i++){ if(n[i]==null) continue; float mn=1e9f,mx=-1e9f; for(int k=pasos/4;k<pasos;k++){ mn=Math.min(mn,y[i][k]); mx=Math.max(mx,y[i][k]); }
      StringBuilder sb=new StringBuilder(); float r=mx-mn;
      // contactos: entra por debajo de min+20% del rango tras haber estado por encima de min+50%
      boolean arriba=true; for(int k=pasos/4;k<pasos;k++){ float u=(y[i][k]-mn)/r; if(arriba && u<Float.parseFloat(System.getProperty("ba","0.2"))){ sb.append(String.format(" %.3f",k*dt)); arriba=false; } else if(!arriba && u>Float.parseFloat(System.getProperty("su","0.5"))) arriba=true; }
      float zmn=1e9f,zmx=-1e9f; for(int k=pasos/4;k<pasos;k++){ zmn=Math.min(zmn,fz[i][k]); zmx=Math.max(zmx,fz[i][k]); }
      StringBuilder pk=new StringBuilder(); for(int k=pasos/4+1;k<pasos-1;k++) if(fz[i][k]>=fz[i][k-1]&&fz[i][k]>fz[i][k+1]&&fz[i][k]>zmn+0.6f*(zmx-zmn)) pk.append(String.format(" %.3f",k*dt));
      System.out.println(String.format("   picos adelante (z %.3f..%.3f):%s", zmn, zmx, pk));
      System.out.println(String.format("%-36s y %.3f..%.3f (rango %.3f m) contactos:%s", a[4+i], mn, mx, r, sb));
    }
    if(System.getProperty("curva")!=null) for(int k=pasos/4;k<pasos/4+(int)(1.1f/dt);k+=8){ StringBuilder sb=new StringBuilder(String.format("%.3f",k*dt)); for(int i=0;i<nh;i++) if(n[i]!=null) sb.append(String.format("  y%.3f z%+.3f",y[i][k],fz[i][k])); System.out.println(sb); }
    Gdx.app.exit();
  }
}
