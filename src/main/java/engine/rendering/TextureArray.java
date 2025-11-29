package engine.rendering;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL12.glTexImage3D;
import static org.lwjgl.opengl.GL12.glTexSubImage3D;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.*;

import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.EXTTextureFilterAnisotropic.*;


/**
 * Texture2DArray costruita a partire da un atlas 2D.
 * L'atlas viene suddiviso in tilesX x tilesY tile, ognuna in un layer.
 */
public class TextureArray {

    private int textureId;
    private int width;
    private int height;
    private int layers;
    private int tileWidth;
    private int tileHeight;
    private int tilesX;
    private int tilesY;

    public TextureArray(String atlasPath, int tilesX, int tilesY) {
        this.tilesX = tilesX;
        this.tilesY = tilesY;
        loadFromAtlas(atlasPath, tilesX, tilesY);
    }

    private void loadFromAtlas(String resPath, int tilesX, int tilesY) {
        STBImage.stbi_set_flip_vertically_on_load(false);

        IntBuffer w = memAllocInt(1);
        IntBuffer h = memAllocInt(1);
        IntBuffer comp = memAllocInt(1);

        ByteBuffer data;
        try (var in = TextureArray.class.getResourceAsStream("/" + resPath)) {
            if (in == null) {
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
            throw new RuntimeException("Failed to load atlas: " + resPath +
                    " -> " + STBImage.stbi_failure_reason());
        }

        this.width = w.get(0);
        this.height = h.get(0);
        this.layers = tilesX * tilesY;

        this.tileWidth = width / tilesX;
        this.tileHeight = height / tilesY;

        // Crea la texture array
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            float maxAniso = glGetFloat(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            // usa un valore "ragionevole", non per forza il massimo
            float aniso = Math.min(4.0f, maxAniso); // puoi provare 8.0f, 16.0f, ecc.
            glTexParameterf(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_ANISOTROPY_EXT, aniso);
                System.out.println("Anisotropic filtering enabled: " + aniso + "x");
            } else {
                System.out.println("Anisotropic filtering NOT supported on this GPU");
            }


        // Alloco lo storage 3D: tileWidth x tileHeight x layers
        glTexImage3D(
                GL_TEXTURE_2D_ARRAY,
                0,
                GL_RGBA8,
                tileWidth,
                tileHeight,
                layers,
                0,
                GL_RGBA,
                GL_UNSIGNED_BYTE,
                (ByteBuffer) null
        );

        // Buffer temporaneo per una singola tile
        ByteBuffer tileBuffer = BufferUtils.createByteBuffer(tileWidth * tileHeight * 4);

        // Copia ogni tile in un layer
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                int layer = ty * tilesX + tx;

                tileBuffer.clear();

                // Copia riga per riga dalla grande texture alla tile
                for (int yTile = 0; yTile < tileHeight; yTile++) {
                    int srcY = ty * tileHeight + yTile;
                    for (int xTile = 0; xTile < tileWidth; xTile++) {
                        int srcX = tx * tileWidth + xTile;
                        int srcIndex = (srcY * width + srcX) * 4;

                        tileBuffer.put(pixels.get(srcIndex));
                        tileBuffer.put(pixels.get(srcIndex + 1));
                        tileBuffer.put(pixels.get(srcIndex + 2));
                        tileBuffer.put(pixels.get(srcIndex + 3));
                    }
                }

                tileBuffer.flip();

                glTexSubImage3D(
                        GL_TEXTURE_2D_ARRAY,
                        0,
                        0, 0, layer,
                        tileWidth,
                        tileHeight,
                        1,
                        GL_RGBA,
                        GL_UNSIGNED_BYTE,
                        tileBuffer
                );
            }
        }

        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

        STBImage.stbi_image_free(pixels);
        memFree(w);
        memFree(h);
        memFree(comp);

        System.out.println("Loaded TextureArray from atlas: " + resPath +
                " tiles=" + tilesX + "x" + tilesY +
                " tileSize=" + tileWidth + "x" + tileHeight +
                " layers=" + layers);
    }

    public void bind(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
    }

    public void bind() {
        bind(0);
    }

    public void unbind() {
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
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
    public int getTileWidth() { return tileWidth; }
    public int getTileHeight() { return tileHeight; }
    public int getLayers() { return layers; }
    public int getTilesX() { return tilesX; }
    public int getTilesY() { return tilesY; }

    /** indice layer = ty * tilesX + tx */
    public int getLayerIndex(int tileX, int tileY) {
        return tileY * tilesX + tileX;
    }
}
