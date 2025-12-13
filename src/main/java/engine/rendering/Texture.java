package engine.rendering;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Single texture wrapper for OpenGL.
 * Handles loading from file and GPU upload.
 * * Usage:
 * Texture tex = new Texture("game:textures/entity/villager.png");
 * tex.bind(0); // Bind to texture unit 0
 * // ... render ...
 * tex.unbind();
 */
public class Texture {

    private int textureId;
    private int width;
    private int height;
    private String path;

    /**
     * Load texture from resources.
     * * @param resourcePath Path with potential namespace (e.g.,
     * "game:textures/entity/villager.png")
     */
    public Texture(String resourcePath) {
        this.path = resourcePath;
        load(resourcePath);
    }

    /**
     * Create empty texture with given size (for render targets).
     */
    public Texture(int width, int height) {
        this.width = width;
        this.height = height;
        this.path = "generated";

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private void load(String resourcePath) {
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Load image
        STBImage.stbi_set_flip_vertically_on_load(false);
        IntBuffer w = memAllocInt(1);
        IntBuffer h = memAllocInt(1);
        IntBuffer comp = memAllocInt(1);

        ByteBuffer imageData = loadResource(resourcePath);
        if (imageData == null) {
            // Create fallback 2x2 pink/black checkerboard
            System.err.println("[Texture] Failed to load: " + resourcePath + ", using fallback");
            createFallback();
            memFree(w);
            memFree(h);
            memFree(comp);
            return;
        }

        ByteBuffer pixels = STBImage.stbi_load_from_memory(imageData, w, h, comp, 4);
        if (pixels == null) {
            System.err.println(
                    "[Texture] STB failed to decode: " + resourcePath + " -> " + STBImage.stbi_failure_reason());
            createFallback();
            memFree(w);
            memFree(h);
            memFree(comp);
            return;
        }

        this.width = w.get(0);
        this.height = h.get(0);

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glGenerateMipmap(GL_TEXTURE_2D);

        STBImage.stbi_image_free(pixels);
        memFree(w);
        memFree(h);
        memFree(comp);

        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("[Texture] Loaded: " + resourcePath + " (" + width + "x" + height + ")");
    }

    private ByteBuffer loadResource(String pathWithNamespace) {

        String path = pathWithNamespace;

        // 1. RIMUOVI IL NAMESPACE (es. "game:")
        if (path.contains(":")) {
            path = path.substring(path.indexOf(":") + 1);
        }

        // 2. TENTA IL CARICAMENTO
        try {
            // Tenta con slash iniziale (rispetto alla radice /resources/)
            InputStream in = Texture.class.getResourceAsStream("/" + path);
            if (in == null) {
                // Tenta senza slash iniziale (potrebbe funzionare a seconda del ClassLoader)
                in = Texture.class.getResourceAsStream(path);
            }
            if (in == null) {
                // Tenta dal ClassLoader di sistema (fallback robusto)
                in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            }
            if (in == null) {
                return null; // Risorsa non trovata
            }

            byte[] bytes = in.readAllBytes();
            in.close();

            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes).flip();
            return buffer;

        } catch (Exception e) {
            System.err.println("[Texture] Error loading resource: " + path + " -> " + e.getMessage());
            return null;
        }
    }

    private void createFallback() {
        this.width = 2;
        this.height = 2;

        // Pink/black checkerboard
        ByteBuffer pixels = BufferUtils.createByteBuffer(16);
        // Top-left: pink
        pixels.put((byte) 255).put((byte) 0).put((byte) 255).put((byte) 255);
        // Top-right: black
        pixels.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 255);
        // Bottom-left: black
        pixels.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 255);
        // Bottom-right: pink
        pixels.put((byte) 255).put((byte) 0).put((byte) 255).put((byte) 255);
        pixels.flip();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 2, 2, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /**
     * Create a solid color texture.
     */
    public static Texture createSolidColor(float r, float g, float b, float a) {
        Texture tex = new Texture(1, 1);

        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        pixel.put((byte) (r * 255));
        pixel.put((byte) (g * 255));
        pixel.put((byte) (b * 255));
        pixel.put((byte) (a * 255));
        pixel.flip();

        glBindTexture(GL_TEXTURE_2D, tex.textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
        glBindTexture(GL_TEXTURE_2D, 0);

        return tex;
    }

    // ==================== BINDING ====================

    /**
     * Bind texture to a texture unit.
     */
    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    /**
     * Bind to unit 0.
     */
    public void bind() {
        bind(0);
    }

    /**
     * Unbind texture.
     */
    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    // ==================== GETTERS ====================

    public int getTextureId() {
        return textureId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getPath() {
        return path;
    }

    // ==================== CLEANUP ====================

    public void cleanup() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
    }
}