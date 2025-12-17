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
    private int uProj, uView, uModel, uTex, uColor, uUseTexture, uLightDir, uAmbient, uSunColor, uUVTransform,
            uTorchLevel; // NEW uTorchLevel

    private Texture defaultTexture;
    private final Map<String, Texture> textureCache = new HashMap<>();
    private final Map<String, EntityModel> modelCache = new HashMap<>();
    private boolean initialized = false;

    public void init() {
        if (initialized)
            return;
        shader = new Shader(VS, FS);
        uProj = shader.getUniformLocation("uProj");
        uView = shader.getUniformLocation("uView");
        uModel = shader.getUniformLocation("uModel");
        uTex = shader.getUniformLocation("uTex");
        uColor = shader.getUniformLocation("uColor");
        uUseTexture = shader.getUniformLocation("uUseTexture");
        uLightDir = shader.getUniformLocation("uLightDir");
        uAmbient = shader.getUniformLocation("uAmbient");
        uSunColor = shader.getUniformLocation("uSunColor");
        uUVTransform = shader.getUniformLocation("uUVTransform");
        uTorchLevel = shader.getUniformLocation("uTorchLevel"); // NEW

        createCubeMesh();
        defaultTexture = Texture.createSolidColor(1f, 1f, 1f, 1f);
        initialized = true;
        System.out.println("[EntityRenderer] Initialized (UV + winding patch applied)");
    }

    private void createCubeMesh() {
        /*
         * We build a cube where each face is a quad split into two triangles.
         * For every face we use CCW winding when looking at the face:
         * uv order: (0,0), (1,0), (1,1), (0,1)
         * Vertex layout: pos.x,pos.y,pos.z, normal.x,normal.y,normal.z, u,v
         *
         * Face order (used later by computeFaceUVs): FRONT, BACK, RIGHT, LEFT, TOP,
         * BOTTOM
         */

        float[] vertices = {
                // FRONT (z = 1) (v0,v1,v2,v3) -> triangles (v0,v1,v2) (v2,v3,v0)
                0, 0, 1, 0, 0, 1, 0, 0,
                1, 0, 1, 0, 0, 1, 1, 0,
                1, 1, 1, 0, 0, 1, 1, 1,
                1, 1, 1, 0, 0, 1, 1, 1,
                0, 1, 1, 0, 0, 1, 0, 1,
                0, 0, 1, 0, 0, 1, 0, 0,

                // BACK (z = 0) (note vertices chosen so face is CCW when looking at -Z)
                1, 0, 0, 0, 0, -1, 0, 0,
                0, 0, 0, 0, 0, -1, 1, 0,
                0, 1, 0, 0, 0, -1, 1, 1,
                0, 1, 0, 0, 0, -1, 1, 1,
                1, 1, 0, 0, 0, -1, 0, 1,
                1, 0, 0, 0, 0, -1, 0, 0,

                // RIGHT (x = 1)
                1, 0, 1, 1, 0, 0, 0, 0,
                1, 0, 0, 1, 0, 0, 1, 0,
                1, 1, 0, 1, 0, 0, 1, 1,
                1, 1, 0, 1, 0, 0, 1, 1,
                1, 1, 1, 1, 0, 0, 0, 1,
                1, 0, 1, 1, 0, 0, 0, 0,

                // LEFT (x = 0)
                0, 0, 0, -1, 0, 0, 0, 0,
                0, 0, 1, -1, 0, 0, 1, 0,
                0, 1, 1, -1, 0, 0, 1, 1,
                0, 1, 1, -1, 0, 0, 1, 1,
                0, 1, 0, -1, 0, 0, 0, 1,
                0, 0, 0, -1, 0, 0, 0, 0,

                // TOP (y = 1)
                0, 1, 1, 0, 1, 0, 0, 0,
                1, 1, 1, 0, 1, 0, 1, 0,
                1, 1, 0, 0, 1, 0, 1, 1,
                1, 1, 0, 0, 1, 0, 1, 1,
                0, 1, 0, 0, 1, 0, 0, 1,
                0, 1, 1, 0, 1, 0, 0, 0,

                // BOTTOM (y = 0)
                0, 0, 0, 0, -1, 0, 0, 0,
                1, 0, 0, 0, -1, 0, 1, 0,
                1, 0, 1, 0, -1, 0, 1, 1,
                1, 0, 1, 0, -1, 0, 1, 1,
                0, 0, 1, 0, -1, 0, 0, 1,
                0, 0, 0, 0, -1, 0, 0, 0
        };

        cubeVertexCount = vertices.length / 8;
        cubeVAO = glGenVertexArrays();
        cubeVBO = glGenBuffers();
        glBindVertexArray(cubeVAO);
        glBindBuffer(GL_ARRAY_BUFFER, cubeVBO);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        int stride = 8 * Float.BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, stride, 6 * Float.BYTES);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public void begin(Camera camera, Vec3 sunDir, Vec3 sunColor, Vec3 ambientColor) {
        if (!initialized)
            init();
        shader.bind();
        shader.setUniform(uProj, camera.getProjectionMatrix());
        shader.setUniform(uView, camera.getViewMatrix());
        glUniform1i(uTex, 0);

        Vec3 ld = (sunDir != null) ? sunDir.normalize() : new Vec3(0.3f, 1f, 0.5f).normalize();
        glUniform3f(uLightDir, ld.x, ld.y, ld.z);

        if (ambientColor != null) {
            glUniform3f(uAmbient, ambientColor.x, ambientColor.y, ambientColor.z);
        } else {
            glUniform3f(uAmbient, 0.4f, 0.4f, 0.4f);
        }

        if (sunColor != null) {
            glUniform3f(uSunColor, sunColor.x, sunColor.y, sunColor.z);
        } else {
            glUniform3f(uSunColor, 1.0f, 1.0f, 1.0f);
        }

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
    }

    public void renderSpecificBone(EntityModel model, String boneName, Mat4 transform, Texture tex) {
        if (model == null) {
            System.out.println("NULL MODEL for renderSpecificBone");
            return;
        }

        ModelBone bone = model.getBone(boneName);
        if (bone == null) {
            // Only print for primary checks or limit frequency
            // System.out.println("Bone missing: " + boneName);
            return;
        }

        shader.bind(); // Ensure shader is bound
        shader.setUniform(uUseTexture, tex != defaultTexture ? 1 : 0);
        shader.setUniform(uColor, 1f, 1f, 1f, 1f); // Assuming white color for specific bone rendering

        tex.bind(0); // Bind texture to unit 0

        // Recursively render this bone and children with the custom transform
        // We assume 'transform' is the Model Matrix for the bone's root.
        renderBoneTree(model, bone, transform);
    }

    private void renderBoneTree(EntityModel model, ModelBone bone, Mat4 parentTransform) {
        if (!bone.isVisible())
            return;

        // Calculate local transform for this bone
        Mat4 tPivot = Mat4.translate(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
        Mat4 r = rotXYZ((float) Math.toRadians(bone.getRotationX()), (float) Math.toRadians(bone.getRotationY()),
                (float) Math.toRadians(bone.getRotationZ()));
        Mat4 tInvPivot = Mat4.translate(-bone.getPivotX(), -bone.getPivotY(), -bone.getPivotZ());
        Mat4 tPos = Mat4.translate(bone.getPositionX(), bone.getPositionY(), bone.getPositionZ());
        Mat4 s = Mat4.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());

        Mat4 localTransform = Mat4.identity();
        localTransform = Mat4.mul(localTransform, tPos);
        localTransform = Mat4.mul(localTransform, tPivot);
        localTransform = Mat4.mul(localTransform, r);
        localTransform = Mat4.mul(localTransform, s);
        localTransform = Mat4.mul(localTransform, tInvPivot);

        Mat4 globalTransform = Mat4.mul(parentTransform, localTransform);

        // Render cubes
        for (ModelCube c : bone.getCubes()) {
            renderCube(model, c, globalTransform);
        }

        for (ModelBone child : bone.getChildren()) {
            renderBoneTree(model, child, globalTransform); // Pass the accumulated transform
        }
    }

    public void renderEntity(Entity entity, engine.world.World world, float partialTick) {
        if (entity == null || entity.isRemoved())
            return;
        if (entity instanceof ItemEntity)
            return;

        float x = entity.getLerpedX(partialTick), y = entity.getLerpedY(partialTick),
                z = entity.getLerpedZ(partialTick);
        float yaw = entity.getLerpedYaw(partialTick);

        // --- Block Light Sampling (RGB packed) ---
        float lightVal = 0.0f;
        if (world != null) {
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y + 0.5f); // Sample at eye/center level
            int bz = (int) Math.floor(z);
            int packed = world.peekBlockLight(bx, by, bz);
            // Unpack RGB components (4 bits each, format 0x0RGB)
            int r = (packed >> 8) & 0xF;
            int g = (packed >> 4) & 0xF;
            int b = packed & 0xF;
            // Use max component for overall intensity (entities don't need colored light)
            lightVal = Math.max(r, Math.max(g, b)) / 15.0f;
        }

        shader.bind(); // Ensure bound (should be bound by begin(), but renderEntity might be called
                       // isolated? No, normally batched)
        // Actually usually renderEntity is called inside loop.
        // begin() binds shader.
        // We can just set uniform.
        glUniform1f(uTorchLevel, lightVal);

        EntityModel model = getModel(entity);
        Texture tex = getTexture(entity);
        if (model != null) {
            if (entity instanceof LivingEntity)
                updateAnimation(model, (LivingEntity) entity);
            renderModel(model, x, y, z, yaw, tex);
        } else {
            renderFallback(x, y, z, entity.getWidth(), entity.getHeight(), yaw);
        }
    }

    public EntityModel getModel(Entity e) {
        String path = null;
        if (e instanceof engine.entity.Player) {
            path = ((engine.entity.Player) e).getModelPath();
        } else if (e instanceof NpcEntity && ((NpcEntity) e).getModelId() != null) {
            path = ((NpcEntity) e).getModelId().toString();
        }
        if (path == null)
            path = e.getType().getModelPath();
        if (path == null)
            return null;
        if (!modelCache.containsKey(path)) {
            EntityModel m = EntityModelLoader.loadModel(path);
            EntityModelLoader.loadAnimations(m, path.replace(".geo.json", ".animation.json"));
            modelCache.put(path, m);
        }
        return modelCache.get(path);
    }

    public Texture getTexture(Entity e) {
        String path = null;
        if (e instanceof engine.entity.Player) {
            path = ((engine.entity.Player) e).getSkinPath();
        } else if (e instanceof NpcEntity && ((NpcEntity) e).getTextureId() != null) {
            path = ((NpcEntity) e).getTextureId().toString();
        }
        if (path == null)
            path = e.getType().getTexturePath();
        if (path == null)
            return defaultTexture;
        if (!textureCache.containsKey(path))
            textureCache.put(path, new Texture(path));
        return textureCache.get(path);
    }

    private void updateAnimation(EntityModel model, LivingEntity e) {
        model.resetPose();
        EntityAnimation anim = model.getAnimation(e.getCurrentAnimation());
        if (anim != null)
            anim.apply(model, e.getAnimationTime());
        if (e.getLimbSwingAmount() > 0.01f) {
            float leg = (float) Math.sin(e.getLimbSwing()) * 30f * e.getLimbSwingAmount();
            float arm = (float) Math.sin(e.getLimbSwing()) * 20f * e.getLimbSwingAmount();
            ModelBone ll = model.getBone("leg_left"), lr = model.getBone("leg_right");
            ModelBone al = model.getBone("arm_left"), ar = model.getBone("arm_right");
            if (ll != null)
                ll.addRotation(leg, 0, 0);
            if (lr != null)
                lr.addRotation(-leg, 0, 0);
            if (al != null)
                al.addRotation(-arm, 0, 0);
            if (ar != null)
                ar.addRotation(arm, 0, 0);
        }
    }

    private void renderModel(EntityModel model, float x, float y, float z, float yaw, Texture tex) {
        tex.bind(0);
        glUniform1i(uUseTexture, tex != defaultTexture ? 1 : 0);
        glUniform4f(uColor, 1f, 1f, 1f, 1f);
        // Fix: Blockbench models face North (-Z) by default, but our engine might
        // differ.
        // User says model looks "Right".
        // Let's try adding 180 deg offset if it's completely backwards, or 90 if side.
        // Usually fixing "Looking Right" needs -90 or +90.
        // NOTE: User said "turned to the right".
        // Let's try -90 (or +270) to turn it Left (back to Center).
        // Actually, let's try 180 first if it was standard inversion, but "Right"
        // implies 90.
        // Let's go with +180 first as a common fix for "Backwards", but for "Right"?
        // Wait, if I press W and camera Follows, and model looks Right:
        // Camera Yaw = 0 (Forward). Model Rot = 0. Model looks Right (+X).
        // We want Model to look Forward (-Z).
        // To turn +X to -Z is +90 degrees (CCW).
        // Let's try adding 90 degrees.
        Mat4 base = Mat4.mul(Mat4.translate(x, y, z),
                Mat4.mul(rotY((float) Math.toRadians(-yaw + 90)), Mat4.scale(1f / 16f, 1f / 16f, 1f / 16f)));
        for (ModelBone bone : model.getRootBones())
            renderBone(model, bone, base, tex);
    }

    private void renderBone(EntityModel model, ModelBone bone, Mat4 parent, Texture tex) {
        if (!bone.isVisible())
            return;

        Mat4 tPivot = Mat4.translate(bone.getPivotX(), bone.getPivotY(), bone.getPivotZ());
        Mat4 r = rotXYZ((float) Math.toRadians(bone.getRotationX()), (float) Math.toRadians(bone.getRotationY()),
                (float) Math.toRadians(bone.getRotationZ()));
        Mat4 tInvPivot = Mat4.translate(-bone.getPivotX(), -bone.getPivotY(), -bone.getPivotZ());
        Mat4 tPos = Mat4.translate(bone.getPositionX(), bone.getPositionY(), bone.getPositionZ());
        Mat4 s = Mat4.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());

        Mat4 localTransform = Mat4.identity();
        localTransform = Mat4.mul(localTransform, tPos);
        localTransform = Mat4.mul(localTransform, tPivot);
        localTransform = Mat4.mul(localTransform, r);
        localTransform = Mat4.mul(localTransform, s);
        localTransform = Mat4.mul(localTransform, tInvPivot);

        Mat4 globalTransform = Mat4.mul(parent, localTransform);

        // Render cubes
        for (ModelCube c : bone.getCubes()) {
            renderCube(model, c, globalTransform);
        }

        for (ModelBone ch : bone.getChildren()) {
            renderBone(model, ch, globalTransform, tex);
        }
    }

    private void renderCube(EntityModel model, ModelCube c, Mat4 boneMatrix) {
        // cube geometry (min corner = origin used by your writer)
        float mx = c.getMinX();
        float my = c.getMinY();
        float mz = c.getMinZ();

        float sx = c.getSizeX() + c.getInflate() * 2;
        float sy = c.getSizeY() + c.getInflate() * 2;
        float sz = c.getSizeZ() + c.getInflate() * 2;

        Mat4 cubeMat = Mat4.mul(boneMatrix, Mat4.translate(mx, my, mz));
        cubeMat = Mat4.mul(cubeMat, Mat4.scale(sx, sy, sz));

        shader.setUniform(uModel, cubeMat);

        // Calcola UV per ogni faccia (6 facce), restituisce array di 6 rect
        // (u0,v0,u1,v1) in 0..1
        float texW = model.getTextureWidth();
        float texH = model.getTextureHeight();
        Rect[] faceRects = computeFaceUVs(c, texW, texH, c.isMirror());

        // DRAW: il cubeVAO contiene 6 facce, ogni faccia ha N vertices.
        int faces = 6;
        int vertsPerFace = cubeVertexCount / faces; // tipicamente 6
        glBindVertexArray(cubeVAO);
        for (int f = 0; f < faces; f++) {
            Rect r = faceRects[f];
            // uOffset.x = r.u0, uOffset.y = r.v0, scale z = (u1-u0), scale w = (v1-v0)
            glUniform4f(uUVTransform, r.u0, r.v0, r.u1 - r.u0, r.v1 - r.v0);
            int base = f * vertsPerFace;
            glDrawArrays(GL_TRIANGLES, base, vertsPerFace);
        }
        glBindVertexArray(0);
    }

    /**
     * Compute UV rectangles for the six faces of the cube.
     * Returns array ordered as: FRONT, BACK, RIGHT, LEFT, TOP, BOTTOM
     */
    private Rect[] computeFaceUVs(ModelCube c, float texW, float texH, boolean mirror) {
        float u0 = c.getUvX();
        float v0 = c.getUvY();
        float W = c.getSizeX(); // Larghezza
        float H = c.getSizeY(); // Altezza
        float D = c.getSizeZ(); // Profondità

        // Le coordinate V sono tutte sfalsate da D (Profondità), tranne Top e Bottom.
        float V_D = v0 + D;

        // --- Calcolo Posizioni U (Orizzontali) ---
        // Blocchi di Larghezza: D (Left) | W (Front) | D (Right) | W (Back)
        float U_LEFT_START = u0;
        float U_FRONT_START = u0 + D;
        float U_RIGHT_START = u0 + D + W;
        float U_BACK_START = u0 + D + W + D;

        // --- TOP e BOTTOM ---
        // Blocchi di Larghezza: W (Top) | W (Bottom)
        float U_TOP_START = U_FRONT_START;
        float U_BOTTOM_START = U_RIGHT_START;

        Rect front = new Rect(U_FRONT_START / texW, V_D / texH, (U_FRONT_START + W) / texW, (V_D + H) / texH);
        Rect back = new Rect(U_BACK_START / texW, V_D / texH, (U_BACK_START + W) / texW, (V_D + H) / texH);

        Rect left = new Rect(U_LEFT_START / texW, V_D / texH, (U_LEFT_START + D) / texW, (V_D + H) / texH);
        Rect right = new Rect(U_RIGHT_START / texW, V_D / texH, (U_RIGHT_START + D) / texW, (V_D + H) / texH);

        Rect top = new Rect(U_TOP_START / texW, v0 / texH, (U_TOP_START + W) / texW, (v0 + D) / texH);
        Rect bottom = new Rect(U_BOTTOM_START / texW, v0 / texH, (U_BOTTOM_START + W) / texW, (v0 + D) / texH);

        if (mirror) {
            // La mirroratura in Bedrock è complessa e spesso influisce solo sugli X,
            // ma è gestita in modo migliore con la logica Blockbench. Qui assumiamo
            // una semplice inversione U per il momento, se necessario.
            // left = left.flippedU(); // Potrebbe essere necessario, ma proviamo prima
            // senza.
            // right = right.flippedU();
        }

        Rect[] res = new Rect[6];
        res[0] = front;
        res[1] = back;
        res[2] = right;
        res[3] = left;
        res[4] = top;
        res[5] = bottom;
        return res;
    }

    private void renderFallback(float x, float y, float z, float w, float h, float yaw) {
        defaultTexture.bind(0);
        glUniform1i(uUseTexture, 0);
        glUniform4f(uColor, 0.8f, 0.5f, 0.3f, 1f);
        Mat4 t = Mat4.mul(Mat4.translate(x - w / 2, y, z - w / 2),
                Mat4.mul(rotY((float) Math.toRadians(-yaw)), Mat4.scale(w, h, w)));
        shader.setUniform(uModel, t);
        glBindVertexArray(cubeVAO);
        glDrawArrays(GL_TRIANGLES, 0, cubeVertexCount);
        glBindVertexArray(0);
    }

    public void end() {
        shader.unbind();
    }

    private Mat4 rotY(float r) {
        Mat4 m = Mat4.identity();
        float c = (float) Math.cos(r), s = (float) Math.sin(r);
        m.m[0] = c;
        m.m[2] = -s;
        m.m[8] = s;
        m.m[10] = c;
        return m;
    }

    private Mat4 rotXYZ(float rx, float ry, float rz) {
        Mat4 x = Mat4.identity();
        float cx = (float) Math.cos(rx), sx = (float) Math.sin(rx);
        x.m[5] = cx;
        x.m[6] = sx;
        x.m[9] = -sx;
        x.m[10] = cx;
        Mat4 y = rotY(ry);
        Mat4 z = Mat4.identity();
        float cz = (float) Math.cos(rz), sz = (float) Math.sin(rz);
        z.m[0] = cz;
        z.m[1] = sz;
        z.m[4] = -sz;
        z.m[5] = cz;
        return Mat4.mul(z, Mat4.mul(y, x));
    }

    public void cleanup() {
        if (shader != null)
            shader.cleanup();
        if (cubeVAO != 0)
            glDeleteVertexArrays(cubeVAO);
        if (cubeVBO != 0)
            glDeleteBuffers(cubeVBO);
        if (defaultTexture != null)
            defaultTexture.cleanup();
        for (Texture t : textureCache.values())
            t.cleanup();
        textureCache.clear();
        modelCache.clear();
    }

    // Simple rect helper
    private static class Rect {
        final float u0, v0, u1, v1;

        Rect(float u0, float v0, float u1, float v1) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }

        Rect flippedU() {
            return new Rect(1f - u1, v0, 1f - u0, v1);
        }
    }

    private static final String VS_FIXED = "#version 330 core\n"
            + "layout(location=0)in vec3 aPos;layout(location=1)in vec3 aNormal;layout(location=2)in vec2 aUV;"
            + "uniform mat4 uProj,uView,uModel;uniform vec4 uUVTransform;out vec3 vN;out vec2 vUV;"
            + "void main(){"
            + "vN=mat3(uModel)*aNormal;"

            // FIX: Inversione dell'asse V (aUV.y) per allineare OpenGL (0=Bottom) con
            // Blockbench (0=Top)
            + "vUV.x = uUVTransform.x + aUV.x * uUVTransform.z;"
            + "vUV.y = uUVTransform.y + (1.0 - aUV.y) * uUVTransform.w;" // <--- LA RIGA CRITICA

            + "gl_Position=uProj*uView*uModel*vec4(aPos,1.0);"
            + "}";

    private static final String VS = VS_FIXED; // Usa il Vertex Shader Corretto!

    private static final String FS = "#version 330 core\n"
            + "in vec3 vN;in vec2 vUV;uniform sampler2D uTex;uniform vec4 uColor;uniform int uUseTexture;uniform vec3 uLightDir;uniform vec3 uSunColor;uniform vec3 uAmbient;\n"
            + "uniform float uTorchLevel;\n" // NEW: Block light intensity (0.0 - 1.0)
            + "out vec4 F;"
            + "void main(){"
            + "vec3 n=normalize(vN);"
            + "float d=max(dot(n,normalize(uLightDir)),0.0);"
            + "vec3 torchColor = vec3(1.0, 0.9, 0.8) * uTorchLevel;" // Warm torch light
            + "vec3 ambient = max(uAmbient, torchColor);" // Use max of global ambient vs local torch
            + "vec3 light = ambient + uSunColor * d;"
            + "vec4 b=uUseTexture==1?texture(uTex,vUV)*uColor:uColor;"
            + "F=vec4(b.rgb*light,b.a);"
            + "}";
}
