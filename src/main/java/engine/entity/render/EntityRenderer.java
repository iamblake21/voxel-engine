package engine.entity.render;

import engine.entity.Entity;
import engine.entity.LivingEntity;
import engine.entity.NpcEntity;
import engine.entity.model.*;
import engine.rendering.Camera;
import engine.rendering.Shader;
import engine.rendering.Texture;
import engine.utils.Math3D.Mat4;
import engine.utils.Math3D.Vec3;
import engine.entity.ItemEntity;

import java.util.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class EntityRenderer {
    
    private Shader shader;
    private int cubeVAO, cubeVBO, cubeVertexCount;
    private int uProj, uView, uModel, uTex, uColor, uUseTexture, uLightDir, uAmbient;
    private Texture defaultTexture;
    private final Map<String, Texture> textureCache = new HashMap<>();
    private final Map<String, EntityModel> modelCache = new HashMap<>();
    private boolean initialized = false;
    
    public void init() {
        if (initialized) return;
        shader = new Shader(VS, FS);
        uProj = shader.getUniformLocation("uProj");
        uView = shader.getUniformLocation("uView");
        uModel = shader.getUniformLocation("uModel");
        uTex = shader.getUniformLocation("uTex");
        uColor = shader.getUniformLocation("uColor");
        uUseTexture = shader.getUniformLocation("uUseTexture");
        uLightDir = shader.getUniformLocation("uLightDir");
        uAmbient = shader.getUniformLocation("uAmbient");
        createCubeMesh();
        defaultTexture = Texture.createSolidColor(1f, 1f, 1f, 1f);
        initialized = true;
        System.out.println("[EntityRenderer] Initialized");
    }
    
    private void createCubeMesh() {
        float[] vertices = {
            0,0,1,0,0,1,0,1, 1,0,1,0,0,1,1,1, 1,1,1,0,0,1,1,0, 0,0,1,0,0,1,0,1, 1,1,1,0,0,1,1,0, 0,1,1,0,0,1,0,0,
            1,0,0,0,0,-1,0,1, 0,0,0,0,0,-1,1,1, 0,1,0,0,0,-1,1,0, 1,0,0,0,0,-1,0,1, 0,1,0,0,0,-1,1,0, 1,1,0,0,0,-1,0,0,
            1,0,1,1,0,0,0,1, 1,0,0,1,0,0,1,1, 1,1,0,1,0,0,1,0, 1,0,1,1,0,0,0,1, 1,1,0,1,0,0,1,0, 1,1,1,1,0,0,0,0,
            0,0,0,-1,0,0,0,1, 0,0,1,-1,0,0,1,1, 0,1,1,-1,0,0,1,0, 0,0,0,-1,0,0,0,1, 0,1,1,-1,0,0,1,0, 0,1,0,-1,0,0,0,0,
            0,1,1,0,1,0,0,1, 1,1,1,0,1,0,1,1, 1,1,0,0,1,0,1,0, 0,1,1,0,1,0,0,1, 1,1,0,0,1,0,1,0, 0,1,0,0,1,0,0,0,
            0,0,0,0,-1,0,0,1, 1,0,0,0,-1,0,1,1, 1,0,1,0,-1,0,1,0, 0,0,0,0,-1,0,0,1, 1,0,1,0,-1,0,1,0, 0,0,1,0,-1,0,0,0
        };
        cubeVertexCount = vertices.length / 8;
        cubeVAO = glGenVertexArrays();
        cubeVBO = glGenBuffers();
        glBindVertexArray(cubeVAO);
        glBindBuffer(GL_ARRAY_BUFFER, cubeVBO);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        int stride = 8 * Float.BYTES;
        glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1); glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3*Float.BYTES);
        glEnableVertexAttribArray(2); glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6*Float.BYTES);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
    
    public void begin(Camera camera, Vec3 sunDir) {
        if (!initialized) init();
        shader.bind();
        shader.setUniform(uProj, camera.getProjectionMatrix());
        shader.setUniform(uView, camera.getViewMatrix());
        glUniform1i(uTex, 0);
        Vec3 ld = (sunDir != null) ? sunDir.normalize() : new Vec3(0.3f, 1f, 0.5f).normalize();
        glUniform3f(uLightDir, ld.x, ld.y, ld.z);
        glUniform1f(uAmbient, 0.4f);
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }
    
    public void renderEntity(Entity entity, float partialTick) {
        if (entity == null || entity.isRemoved()) return;
            if (entity instanceof ItemEntity) return;

        float x = entity.getLerpedX(partialTick), y = entity.getLerpedY(partialTick), z = entity.getLerpedZ(partialTick);
        float yaw = entity.getLerpedYaw(partialTick);
        EntityModel model = getModel(entity);
        Texture tex = getTexture(entity);
        if (model != null) {
            if (entity instanceof LivingEntity) updateAnimation(model, (LivingEntity) entity);
            renderModel(model, x, y, z, yaw, tex);
        } else {
            renderFallback(x, y, z, entity.getWidth(), entity.getHeight(), yaw);
        }
    }
    
    private EntityModel getModel(Entity e) {
        String path = null;
        if (e instanceof NpcEntity && ((NpcEntity)e).getModelId() != null) path = ((NpcEntity)e).getModelId().toString();
        if (path == null) path = e.getType().getModelPath();
        if (path == null) return null;
        if (!modelCache.containsKey(path)) {
            EntityModel m = EntityModelLoader.loadModel(path);
            EntityModelLoader.loadAnimations(m, path.replace(".geo.json", ".animation.json"));
            modelCache.put(path, m);
        }
        return modelCache.get(path);
    }
    
    private Texture getTexture(Entity e) {
        String path = null;
        if (e instanceof NpcEntity && ((NpcEntity)e).getTextureId() != null) path = ((NpcEntity)e).getTextureId().toString();
        if (path == null) path = e.getType().getTexturePath();
        if (path == null) return defaultTexture;
        if (!textureCache.containsKey(path)) textureCache.put(path, new Texture(path));
        return textureCache.get(path);
    }
    
    private void updateAnimation(EntityModel model, LivingEntity e) {
        model.resetPose();
        EntityAnimation anim = model.getAnimation(e.getCurrentAnimation());
        if (anim != null) anim.apply(model, e.getAnimationTime());
        if (e.getLimbSwingAmount() > 0.01f) {
            float leg = (float)Math.sin(e.getLimbSwing()) * 30f * e.getLimbSwingAmount();
            float arm = (float)Math.sin(e.getLimbSwing()) * 20f * e.getLimbSwingAmount();
            ModelBone ll = model.getBone("leg_left"), lr = model.getBone("leg_right");
            ModelBone al = model.getBone("arm_left"), ar = model.getBone("arm_right");
            if (ll != null) ll.addRotation(leg, 0, 0);
            if (lr != null) lr.addRotation(-leg, 0, 0);
            if (al != null) al.addRotation(-arm, 0, 0);
            if (ar != null) ar.addRotation(arm, 0, 0);
        }
    }
    
    private void renderModel(EntityModel model, float x, float y, float z, float yaw, Texture tex) {
        tex.bind(0);
        glUniform1i(uUseTexture, tex != defaultTexture ? 1 : 0);
        glUniform4f(uColor, 1f, 1f, 1f, 1f);
        Mat4 base = Mat4.mul(Mat4.translate(x, y, z), Mat4.mul(rotY((float)Math.toRadians(-yaw)), Mat4.scale(1f/16f, 1f/16f, 1f/16f)));
        for (ModelBone bone : model.getRootBones()) renderBone(bone, base);
    }
    
private void renderBone(ModelBone bone, Mat4 parent) {
        if (!bone.isVisible()) return;

        // --- MATEMATICA PIVOT CORRETTA ---
        // 1. Trasla AL pivot
        Mat4 tPivot = Mat4.translate(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
        
        // 2. Ruota (Animazione + Default)
        Mat4 r = rotXYZ(
            (float)Math.toRadians(bone.getRotationX()), 
            (float)Math.toRadians(bone.getRotationY()), 
            (float)Math.toRadians(bone.getRotationZ())
        );
        
        // 3. Trasla DAL pivot (Torna indietro)
        Mat4 tInvPivot = Mat4.translate(-bone.getPivotX(), -bone.getPivotY(), -bone.getPivotZ());
        
        // 4. Trasla Posizione (Animazione)
        Mat4 tPos = Mat4.translate(bone.getPositionX(), bone.getPositionY(), bone.getPositionZ());
        
        // 5. Scala (Animazione)
        Mat4 s = Mat4.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());

        // Ordine moltiplicazione: Parent * TranslatePos * Pivot * Rotation * Scale * InvPivot
        // Nota: L'ordine esatto dipende dalla libreria matrici (Column-major vs Row-major).
        // Per OpenGL standard (e Bedrock) è solitamente:
        // Global = Parent * (TranslatePos + Pivot) * Rotate * Scale * -Pivot
        
        Mat4 localTransform = Mat4.identity();
        localTransform = Mat4.mul(localTransform, tPos); // Sposta osso (animazione)
        localTransform = Mat4.mul(localTransform, tPivot); // Vai al pivot
        localTransform = Mat4.mul(localTransform, r);      // Ruota
        localTransform = Mat4.mul(localTransform, s);      // Scala
        localTransform = Mat4.mul(localTransform, tInvPivot); // Torna dal pivot (così i cubi figli sono relativi al pivot)

        Mat4 globalTransform = Mat4.mul(parent, localTransform);

        // Renderizza i cubi di questo osso
        for (ModelCube c : bone.getCubes()) {
            renderCube(c, globalTransform);
        }
        
        // Ricorsione figli
        for (ModelBone ch : bone.getChildren()) {
            renderBone(ch, globalTransform);
        }
    }
    
    private void renderCube(ModelCube c, Mat4 boneMatrix) {
        // I cubi in Blockbench sono definiti da "Origin" (angolo min) e "Size".
        // Sono coordinate ASSOLUTE nello spazio modello, MA quando esporti come "Bedrock"
        // o "Modded Entity", spesso sono relative.
        
        // Se i tuoi pezzi esplodono ancora, prova a togliere o aggiungere questo offset.
        // Solitamente con la logica Pivot sopra, qui basta traslare all'origine del cubo.
        
        float mx = c.getMinX();
        float my = c.getMinY();
        float mz = c.getMinZ();
        
        float sx = c.getSizeX() + c.getInflate()*2;
        float sy = c.getSizeY() + c.getInflate()*2;
        float sz = c.getSizeZ() + c.getInflate()*2;
        
        Mat4 cubeMat = Mat4.mul(boneMatrix, Mat4.translate(mx, my, mz));
        cubeMat = Mat4.mul(cubeMat, Mat4.scale(sx, sy, sz));
        
        shader.setUniform(uModel, cubeMat);
        
        // ... (Render Mesh) ...
        glBindVertexArray(cubeVAO);
        glDrawArrays(GL_TRIANGLES, 0, cubeVertexCount);
        glBindVertexArray(0);
    }
    
    private void renderFallback(float x, float y, float z, float w, float h, float yaw) {
        defaultTexture.bind(0);
        glUniform1i(uUseTexture, 0);
        glUniform4f(uColor, 0.8f, 0.5f, 0.3f, 1f);
        Mat4 t = Mat4.mul(Mat4.translate(x-w/2, y, z-w/2), Mat4.mul(rotY((float)Math.toRadians(-yaw)), Mat4.scale(w, h, w)));
        shader.setUniform(uModel, t);
        glBindVertexArray(cubeVAO);
        glDrawArrays(GL_TRIANGLES, 0, cubeVertexCount);
        glBindVertexArray(0);
    }
    
    public void end() { shader.unbind(); }
    
    private Mat4 rotY(float r) {
        Mat4 m = Mat4.identity(); float c = (float)Math.cos(r), s = (float)Math.sin(r);
        m.m[0] = c; m.m[2] = -s; m.m[8] = s; m.m[10] = c; return m;
    }
    
    private Mat4 rotXYZ(float rx, float ry, float rz) {
        Mat4 x = Mat4.identity(); float cx = (float)Math.cos(rx), sx = (float)Math.sin(rx);
        x.m[5] = cx; x.m[6] = sx; x.m[9] = -sx; x.m[10] = cx;
        Mat4 y = rotY(ry);
        Mat4 z = Mat4.identity(); float cz = (float)Math.cos(rz), sz = (float)Math.sin(rz);
        z.m[0] = cz; z.m[1] = sz; z.m[4] = -sz; z.m[5] = cz;
        return Mat4.mul(z, Mat4.mul(y, x));
    }
    
    public void cleanup() {
        if (shader != null) shader.cleanup();
        if (cubeVAO != 0) glDeleteVertexArrays(cubeVAO);
        if (cubeVBO != 0) glDeleteBuffers(cubeVBO);
        if (defaultTexture != null) defaultTexture.cleanup();
        for (Texture t : textureCache.values()) t.cleanup();
        textureCache.clear(); modelCache.clear();
    }
    
    private static final String VS = "#version 330 core\nlayout(location=0)in vec3 aPos;layout(location=1)in vec3 aNormal;layout(location=2)in vec2 aUV;uniform mat4 uProj,uView,uModel;out vec3 vN;out vec2 vUV;void main(){vN=mat3(uModel)*aNormal;vUV=aUV;gl_Position=uProj*uView*uModel*vec4(aPos,1.0);}";
    private static final String FS = "#version 330 core\nin vec3 vN;in vec2 vUV;uniform sampler2D uTex;uniform vec4 uColor;uniform int uUseTexture;uniform vec3 uLightDir;uniform float uAmbient;out vec4 F;void main(){vec3 n=normalize(vN);float d=max(dot(n,normalize(uLightDir)),0.0);float l=uAmbient+(1.0-uAmbient)*d;vec4 b=uUseTexture==1?texture(uTex,vUV)*uColor:uColor;F=vec4(b.rgb*l,b.a);}";
}
