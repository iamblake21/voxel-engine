package engine.rendering;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Texture wrapper
 */
public class Texture {
    
    private int textureId;
    private int width;
    private int height;
    
    public Texture(String resourcePath, boolean repeat) {
        this.textureId = loadTexture(resourcePath, repeat);
    }
    
    public Texture(int textureId, int width, int height) {
        this.textureId = textureId;
        this.width = width;
        this.height = height;
    }
    
    private int loadTexture(String resPath, boolean repeat) {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, repeat ? GL_REPEAT : GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, repeat ? GL_REPEAT : GL_CLAMP_TO_EDGE);
        
        STBImage.stbi_set_flip_vertically_on_load(false);
        
        IntBuffer w = memAllocInt(1);
        IntBuffer h = memAllocInt(1);
        IntBuffer comp = memAllocInt(1);
        
        ByteBuffer data;
        try (var in = Texture.class.getResourceAsStream("/" + resPath)) {
            if (in == null) {
                memFree(w);
                memFree(h);
                memFree(comp);
                throw new RuntimeException("Resource not found: " + resPath);
            }
            
            byte[] bytes = in.readAllBytes();
            data = BufferUtils.createByteBuffer(bytes.length);
            data.put(bytes).flip();
        } catch (Exception e) {
            memFree(w);
            memFree(h);
            memFree(comp);
            throw new RuntimeException("Failed to read resource: " + resPath, e);
        }
        
        ByteBuffer pixels = STBImage.stbi_load_from_memory(data, w, h, comp, 4);
        if (pixels == null) {
            memFree(w);
            memFree(h);
            memFree(comp);
            throw new RuntimeException("Failed to load texture: " + resPath + 
                                     " -> " + STBImage.stbi_failure_reason());
        }
        
        this.width = w.get(0);
        this.height = h.get(0);
        
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 
                     0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        
        STBImage.stbi_image_free(pixels);
        memFree(w);
        memFree(h);
        memFree(comp);
        
        glBindTexture(GL_TEXTURE_2D, 0);
        
        System.out.println("Loaded texture: " + resPath + " (" + width + "x" + height + ")");
        return tex;
    }
    
    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, textureId);
    }
    
    public void bind() {
        bind(0);
    }
    
    public void unbind() {
        glBindTexture(GL_TEXTURE_2D, 0);
    }
    
    public void cleanup() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
            textureId = 0;
        }
    }
    
    public int getTextureId() { return textureId; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}