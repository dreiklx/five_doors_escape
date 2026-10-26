import com.badlogic.gdx.*; import com.badlogic.gdx.backends.lwjgl3.*; import com.badlogic.gdx.graphics.*; import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.graphics.g3d.model.*; import com.badlogic.gdx.math.*; import com.badlogic.gdx.files.*;
import net.mgsx.gltf.loaders.glb.GLBLoader; import net.mgsx.gltf.scene3d.scene.*; import net.mgsx.gltf.scene3d.lights.*; import net.mgsx.gltf.scene3d.shaders.*;
// Hoja de contacto de un clip desde frente, lado y tres cuartos, con correcciones de rotacion LOCAL opcionales por hueso
// (para probar ajustes de pose antes de aplicarlos, CLAUDE.md §2.81). Las correcciones se aplican despues de la
// animacion y se escriben en localTransform (en los huesos animados es lo unico que cuenta).
// args: salida.png modelo escala clip("-" = sin animacion) duracion cuadros altoCamara distancia [ocultar=nodo1,nodo2] [hueso:ax,ay,az,grados ...]
public class DiagPose extends ApplicationAdapter {
  String[] a; DiagPose(String[] a){ this.a=a; }
  public static void main(String[] a){ Lwjgl3ApplicationConfiguration c=new Lwjgl3ApplicationConfiguration(); c.setWindowedMode(320,400); c.setInitialVisible(false); new Lwjgl3Application(new DiagPose(a),c); }
  public void create(){
    SceneAsset sa=new GLBLoader().load(new FileHandle(a[1])); Scene s=new Scene(sa.scene); float esc=Float.parseFloat(a[2]);
    s.modelInstance.transform.setToScaling(esc,esc,esc); if(!a[3].equals("-")) s.animationController.setAnimation(a[3],-1);
    float dur=Float.parseFloat(a[4]); int nc=Integer.parseInt(a[5]); float alto=Float.parseFloat(a[6]), dist=Float.parseFloat(a[7]);
    java.util.List<Node> nodos=new java.util.ArrayList<>(); java.util.List<Quaternion> corr=new java.util.ArrayList<>();
    for(int i=8;i<a.length;i++){
      if(a[i].startsWith("ocultar=")){ for(String n: a[i].substring(8).split(",")){ Node nd=s.modelInstance.getNode(n,true); if(nd!=null){ nd.parts.clear(); for(Node h: nd.getChildren()) h.parts.clear(); } else System.out.println("no existe "+n); } continue; }
      String[] p=a[i].split(":"); String[] v=p[1].split(","); Node nd=s.modelInstance.getNode(p[0],true); if(nd==null){ System.out.println("no existe "+p[0]); continue; }
      nodos.add(nd); corr.add(new Quaternion(new Vector3(Float.parseFloat(v[0]),Float.parseFloat(v[1]),Float.parseFloat(v[2])).nor(), Float.parseFloat(v[3]))); }
    SceneManager sm=new SceneManager(PBRShaderProvider.createDefault(80), PBRShaderProvider.createDefaultDepth(80)); sm.addScene(s);
    PerspectiveCamera cam=new PerspectiveCamera(38,320,400); cam.near=0.05f; cam.far=50; sm.setCamera(cam);
    DirectionalLightEx l=new DirectionalLightEx(); l.direction.set(-0.5f,-1.5f,-1f).nor(); l.intensity=3f; sm.environment.add(l); sm.setAmbientLight(0.9f);
    FrameBuffer fb=new FrameBuffer(Pixmap.Format.RGBA8888,320,400,true);
    String[] vistas={"frente","tres_cuartos","lado"}; float[][] pos={{0,alto,dist},{dist*0.7f,alto,dist*0.7f},{dist,alto,0}};
    Pixmap hoja=new Pixmap(320*nc,400*3,Pixmap.Format.RGBA8888);
    Quaternion q=new Quaternion(); Vector3 t=new Vector3(), sc=new Vector3();
    if(s.animationController!=null) s.animationController.update(0.5f);
    for(int i=0;i<nc;i++){
      sm.update(i==0?0.001f:dur/nc);
      for(int k=0;k<nodos.size();k++){ Node nd=nodos.get(k); nd.localTransform.getTranslation(t); nd.localTransform.getRotation(q,true); nd.localTransform.getScale(sc); q.mul(corr.get(k)); nd.localTransform.set(t,q,sc); }
      s.modelInstance.calculateTransforms();
      for(int v=0;v<3;v++){ cam.position.set(pos[v][0],pos[v][1],pos[v][2]); cam.lookAt(0,alto*0.85f,0); cam.up.set(Vector3.Y); cam.update();
        fb.begin(); Gdx.gl.glClearColor(0.72f,0.72f,0.76f,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT); sm.getRenderableProviders(); sm.render();
        Pixmap px=Pixmap.createFromFrameBuffer(0,0,320,400); fb.end();
        for(int y=0;y<400;y++) hoja.drawPixmap(px,0,y,320,1,320*i,400*v+399-y,320,1); px.dispose(); } }
    PixmapIO.writePNG(new FileHandle(a[0]),hoja); Gdx.app.exit();
  }
}
