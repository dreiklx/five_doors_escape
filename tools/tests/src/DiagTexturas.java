import com.badlogic.gdx.*; import com.badlogic.gdx.backends.lwjgl3.*; import com.badlogic.gdx.graphics.*; import com.badlogic.gdx.graphics.glutils.*; import com.badlogic.gdx.files.*;
import net.mgsx.gltf.loaders.glb.GLBLoader; import net.mgsx.gltf.scene3d.scene.*; import net.mgsx.gltf.scene3d.lights.*; import net.mgsx.gltf.scene3d.shaders.*;
// args: modelo salida.png distancia alturaOjo  -- Freddy en idle, camara de frente (encuadre de jumpscare)
public class DiagTexturas extends ApplicationAdapter {
  String[] a; DiagTexturas(String[] a){ this.a=a; }
  public static void main(String[] a){ Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration(); c.setWindowedMode(1280,720); c.setInitialVisible(false); new Lwjgl3Application(new DiagTexturas(a),c); }
  public void create(){
    long t0=System.nanoTime(); SceneAsset sa=new GLBLoader().load(new FileHandle(a[0])); System.out.println("carga GLB "+(System.nanoTime()-t0)/1000000+" ms");
    Scene s=new Scene(sa.scene); s.modelInstance.transform.setToScaling(10,10,10); s.animationController.setAnimation("Freddy_Idle",-1);
    SceneManager sm=new SceneManager(PBRShaderProvider.createDefault(80), PBRShaderProvider.createDefaultDepth(80)); sm.addScene(s);
    float d=Float.parseFloat(a[2]), y=Float.parseFloat(a[3]);
    PerspectiveCamera cam=new PerspectiveCamera(67,1280,720); cam.position.set(0,y,d); cam.lookAt(0,y-0.05f,0); cam.near=0.05f; cam.far=50; cam.update(); sm.setCamera(cam);
    DirectionalLightEx l=new DirectionalLightEx(); l.direction.set(-0.4f,-0.6f,-1f).nor(); l.intensity=3f; sm.environment.add(l); sm.setAmbientLight(0.8f);
    FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,1280,720,true);
    sm.update(0.5f); fb.begin(); Gdx.gl.glClearColor(0.2f,0.2f,0.22f,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT); sm.render();
    Pixmap px=Pixmap.createFromFrameBuffer(0,0,1280,720); fb.end(); Pixmap fl=new Pixmap(1280,720,Pixmap.Format.RGBA8888); for(int i=0;i<720;i++) fl.drawPixmap(px,0,i,1280,1,0,719-i,1280,1);
    PixmapIO.writePNG(new FileHandle(a[1]),Opaco.aplicar(fl)); Gdx.app.exit(); }
}
