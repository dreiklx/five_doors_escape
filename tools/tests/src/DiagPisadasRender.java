import com.badlogic.gdx.*; import com.badlogic.gdx.backends.lwjgl3.*; import com.badlogic.gdx.graphics.*; import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.math.*; import com.badlogic.gdx.files.*;
import net.mgsx.gltf.loaders.glb.GLBLoader; import net.mgsx.gltf.scene3d.scene.*; import net.mgsx.gltf.scene3d.lights.*; import net.mgsx.gltf.scene3d.shaders.*;
// Vista lateral de un ciclo de "walk": N cuadros PNG (para ver en que instante apoya cada pie).
// args: salida prefijo modelo escala clip duracionCiclo cuadros [altoCamara]
public class DiagPisadasRender extends ApplicationAdapter {
  String[] a; DiagPisadasRender(String[] a){ this.a=a; }
  public static void main(String[] a){ Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration(); c.setWindowedMode(400,500); c.setInitialVisible(false); new Lwjgl3Application(new DiagPisadasRender(a),c); }
  public void create(){
    SceneAsset sa=new GLBLoader().load(Gdx.files.internal(a[2])); Scene s=new Scene(sa.scene); float esc=Float.parseFloat(a[3]); s.modelInstance.transform.setToScaling(esc,esc,esc);
    s.animationController.setAnimation(a[4],-1);
    float ciclo=Float.parseFloat(a[5]); int nc=Integer.parseInt(a[6]); float alto=a.length>7?Float.parseFloat(a[7]):1.0f;
    SceneManager sm=new SceneManager(PBRShaderProvider.createDefault(80), PBRShaderProvider.createDefaultDepth(80)); sm.addScene(s);
    PerspectiveCamera cam=new PerspectiveCamera(40,400,500); cam.position.set(4.2f,alto,0f); cam.lookAt(0,alto*0.8f,0); cam.near=0.05f; cam.far=50; cam.update(); sm.setCamera(cam);
    DirectionalLightEx l=new DirectionalLightEx(); l.direction.set(-1,-2,-0.5f).nor(); l.intensity=3f; sm.environment.add(l); sm.setAmbientLight(1f);
    FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,400,500,true);
    float t=0; float dt=ciclo/nc;
    s.animationController.update(1.0f); // salta el primer segundo (igual que DiagPisadas: t=1.0 es el origen)
    for(int i=0;i<nc;i++){
      sm.update(i==0?0:dt);
      fb.begin(); Gdx.gl.glClearColor(0.75f,0.75f,0.78f,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT); sm.render();
      Pixmap px=Pixmap.createFromFrameBuffer(0,0,400,500); fb.end();
      Pixmap fl=new Pixmap(400,500,Pixmap.Format.RGBA8888); for(int y=0;y<500;y++) fl.drawPixmap(px,0,y,400,1,0,499-y,400,1);
      PixmapIO.writePNG(new FileHandle(a[0]+"/"+a[1]+String.format("_%02d.png",i)),Opaco.aplicar(fl)); px.dispose(); fl.dispose();
    }
    Gdx.app.exit();
  }
}
