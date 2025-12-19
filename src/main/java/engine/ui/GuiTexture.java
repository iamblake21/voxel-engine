package engine.ui;

import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.stb.STBImage.*;

/**
 * 2D Texture wrapper for GUI rendering.
 * Loads PNG images from resources.
 */
public class GuiTexture {

    private int textureId;
    private int width;
    private int height;

    /**
     * Load a 2D texture from resources
     * 
     * @param path Path relative to resources (e.g., "textures/items/stone.png")
     */
    public GuiTexture(String path) {
        load(path);
    }

    private void load(String path) {
        // Load image with STB
        IntBuffer widthBuf = BufferUtils.createIntBuffer(1);
        IntBuffer heightBuf = BufferUtils.createIntBuffer(1);
        IntBuffer channelsBuf = BufferUtils.createIntBuffer(1);

        String resourcePath = "/" + path;
        ByteBuffer imageData;

        try (var stream = GuiTexture.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                System.err.println("[GuiTexture] Failed to find: " + resourcePath);
                // Create 16x16 checkerboard as fallback
                createFallbackTexture();
                return;
            }

            byte[] bytes = stream.readAllBytes();
            ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
            buffer.put(bytes);
            buffer.flip();

            imageData = stbi_load_from_memory(buffer, widthBuf, heightBuf, channelsBuf, 4);

            if (imageData == null) {
                System.err.println("[GuiTexture] STB failed to load: " + path);
                createFallbackTexture();
                return;
            }
        } catch (Exception e) {
            System.err.println("[GuiTexture] Error loading " + path + ": " + e.getMessage());
            createFallbackTexture();
            return;
        }

        width = widthBuf.get(0);
        height = heightBuf.get(0);

        // Create OpenGL texture
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Upload to GPU
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, imageData);

        // Set texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glBindTexture(GL_TEXTURE_2D, 0);

        // Free STB memory
        stbi_image_free(imageData);
    }

    /**
     * Create a magenta/black checkerboard as fallback texture
     */
    private void createFallbackTexture() {
        width = 16;
        height = 16;

        ByteBuffer data = BufferUtils.createByteBuffer(16 * 16 * 4);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean checker = ((x / 4) + (y / 4)) % 2 == 0;
                data.put((byte) (checker ? 255 : 0)); // R
                data.put((byte) 0); // G
                data.put((byte) (checker ? 255 : 0)); // B
                data.put((byte) 255); // A
            }
        }
        data.flip();

        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("[GuiTexture] Created fallback texture");
    }

    /**
     * Bind this texture for rendering
     */
    public void bind() {
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    /**
     * Bind to specific texture unit
     */
    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }

    /**
     * Unbind texture
     */
    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /**
     * Cleanup OpenGL resources
     */
    public void cleanup() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
    }

    // Getters
    public int getTextureId() {
        return textureId;
    }

    public int getId() {
        return textureId;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
