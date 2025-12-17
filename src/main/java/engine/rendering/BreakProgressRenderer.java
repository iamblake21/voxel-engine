package engine.rendering;

import engine.utils.Math3D.Mat4;
import engine.mechanics.MiningManager;
import engine.world.World; // Assicurati di importare la tua classe World
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.stb.STBImage;

import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class BreakProgressRenderer {

    // ... (Tutte le variabili vao, vbo, shader, textureIds rimangono uguali) ...
    private int vao;
    private int vbo;
    private Shader shader;
    private final int[] textureIds = new int[9];
    private int uProj, uView, uModel;

    public BreakProgressRenderer() {
        init();
    }

    private void init() {
        for (int i = 0; i < 9; i++) {
            String path = "src/main/resources/textures/blocks/destroy_stage_" + i + ".png";
            textureIds[i] = loadTexture(path);
        }

        // Vertici (Setup standard)
        float[] vertices = {
                // Front (Z+1)
                0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1, 1, 1, 1,
                1, 1, 1, 1, 1, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0,
                // Back (Z-1)
                1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 1,
                0, 1, 0, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 0, 0,
                // Left (X-1)
                0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 1,
                0, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0,
                // Right (X+1)
                1, 0, 1, 0, 0, 1, 0, 0, 1, 0, 1, 1, 0, 1, 1,
                1, 1, 0, 1, 1, 1, 1, 1, 0, 1, 1, 0, 1, 0, 0,
                // Top (Y+1)
                0, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 0,
                1, 1, 0, 1, 0, 0, 1, 0, 0, 0, 0, 1, 1, 0, 1,
                // Bottom (Y-1)
                0, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0,
                1, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 1
        };

        // ... (Creazione buffer identica a prima) ...
        int vertexCount = vertices.length / 5;
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = MemoryUtil.memAllocFloat(vertices.length);
        fb.put(vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        MemoryUtil.memFree(fb);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 5 * 4, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 5 * 4, 3 * 4);
        glBindVertexArray(0);

        shader = new Shader(getVertexShader(), getFragmentShader());
        uProj = shader.getUniformLocation("uProj");
        uView = shader.getUniformLocation("uView");
        uModel = shader.getUniformLocation("uModel");
        shader.bind();
        glUniform1i(shader.getUniformLocation("uTexture"), 0);
        shader.unbind();
    }

    // === ECCO LA MODIFICA IMPORTANTE ===
    // Aggiungi "World world" ai parametri
    public void render(MiningManager miningManager, Camera camera, World world) {

        if (!miningManager.isBreaking())
            return;

        shader.bind();

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_DEPTH_TEST); // Fix: Ensure depth testing is enabled
        glDepthMask(false);

        shader.setUniform(uProj, camera.getProjectionMatrix());
        shader.setUniform(uView, camera.getViewMatrix());

        int x = miningManager.getCurrentBlockX();
        int y = miningManager.getCurrentBlockY();
        int z = miningManager.getCurrentBlockZ();

        Mat4 model = Mat4.translate(x - 0.002f, y - 0.002f, z - 0.002f);
        Mat4 scale = Mat4.scale(1.004f, 1.004f, 1.004f);
        model = Mat4.mul(model, scale);
        shader.setUniform(uModel, model);

        float progress = miningManager.getBreakProgress();
        int index = (int) (progress * 9.0f);
        if (index < 0)
            index = 0;
        if (index > 8)
            index = 8;

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureIds[index]);

        glBindVertexArray(vao);

        // --- FACE CULLING ---
        // Usiamo world.isBlockTransparent(...) che abbiamo aggiunto a World.java

        // 1. Front (Z+1)
        if (world.isBlockTransparent(x, y, z + 1)) {
            glDrawArrays(GL_TRIANGLES, 0, 6);
        }
        // 2. Back (Z-1)
        if (world.isBlockTransparent(x, y, z - 1)) {
            glDrawArrays(GL_TRIANGLES, 6, 6);
        }
        // 3. Left (X-1)
        if (world.isBlockTransparent(x - 1, y, z)) {
            glDrawArrays(GL_TRIANGLES, 12, 6);
        }
        // 4. Right (X+1)
        if (world.isBlockTransparent(x + 1, y, z)) {
            glDrawArrays(GL_TRIANGLES, 18, 6);
        }
        // 5. Top (Y+1)
        if (world.isBlockTransparent(x, y + 1, z)) {
            glDrawArrays(GL_TRIANGLES, 24, 6);
        }
        // 6. Bottom (Y-1)
        if (world.isBlockTransparent(x, y - 1, z)) {
            glDrawArrays(GL_TRIANGLES, 30, 6);
        }

        glBindVertexArray(0);

        glDepthMask(true);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        shader.unbind();
    }

    private int loadTexture(String path) {
        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        java.nio.IntBuffer w = MemoryUtil.memAllocInt(1);
        java.nio.IntBuffer h = MemoryUtil.memAllocInt(1);
        java.nio.IntBuffer comp = MemoryUtil.memAllocInt(1);
        java.nio.ByteBuffer image = STBImage.stbi_load(path, w, h, comp, 4);
        if (image != null) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0, GL_RGBA, GL_UNSIGNED_BYTE, image);
            STBImage.stbi_image_free(image);
        }
        MemoryUtil.memFree(w);
        MemoryUtil.memFree(h);
        MemoryUtil.memFree(comp);
        return texId;
    }

    public void cleanup() {
        if (vao != 0)
            glDeleteVertexArrays(vao);
        if (vbo != 0)
            glDeleteBuffers(vbo);
        for (int i : textureIds)
            if (i != 0)
                glDeleteTextures(i);
        if (shader != null)
            shader.cleanup();
    }

    private String getVertexShader() {
        return "#version 330 core\n" +
                "layout(location=0) in vec3 aPos;\n" +
                "layout(location=1) in vec2 aUV;\n" +
                "uniform mat4 uProj, uView, uModel;\n" +
                "out vec2 vUV;\n" +
                "void main() {\n" +
                "    vUV = aUV;\n" + // Passiamo le UV direttamente
                "    gl_Position = uProj * uView * uModel * vec4(aPos, 1.0);\n" +
                "}";
    }

    private String getFragmentShader() {
        return "#version 330 core\n" +
                "in vec2 vUV;\n" +
                "out vec4 FragColor;\n" +
                "\n" +
                "uniform sampler2D uTexture;\n" +
                "\n" +
                "void main() {\n" +
                "    // Legge il colore dalla texture (che deve avere sfondo trasparente)\n" +
                "    vec4 texColor = texture(uTexture, vUV);\n" +
                "    \n" +
                "    // Se il pixel è trasparente (alpha basso), lo scartiamo.\n" +
                "    // In questo modo vediamo il blocco originale sotto perfettamente intatto.\n" +
                "    if (texColor.a < 0.1) {\n" +
                "        discard;\n" +
                "    }\n" +
                "    \n" +
                "    FragColor = texColor;\n" +
                "}";
    }
}