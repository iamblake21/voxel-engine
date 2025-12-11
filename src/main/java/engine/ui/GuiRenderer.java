package engine.ui;

import engine.rendering.Shader;
import engine.world.block.Block;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

public class GuiRenderer {

    // GUI Scale factor (1 = normal, 2 = 2x, 3 = 3x larger)
    private int guiScale = 2;

    private final int windowWidth;
    private final int windowHeight;

    private Shader shader;
    private int vao, vbo;
    private int defaultTexture;

    private FloatBuffer quadBuffer;

    // Shader uniform locations
    private int uProjectionLoc, uTextureLoc, uColorLoc;
    private int uTextureArrayLoc, uUseTextureArrayLoc, uLayerLoc;

    private engine.rendering.TextureArray atlasTexture;

    public GuiRenderer(int windowWidth, int windowHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        init();
        System.out.println("[GuiRenderer] Initialized with Scale=" + guiScale);
    }

    // ... (Getter/Setter per Scale e Atlas invariati) ...
    public void setGuiScale(int scale) {
        this.guiScale = Math.max(1, scale);
    }

    public int getGuiScale() {
        return guiScale;
    }

    public void setAtlasTexture(engine.rendering.TextureArray atlasTexture) {
        this.atlasTexture = atlasTexture;
    }

    private void init() {
        // ... (Logica di init shader identica a prima) ...
        try {
            shader = new Shader(getVertexShader(), getFragmentShader());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        uProjectionLoc = glGetUniformLocation(shader.getProgramId(), "uProjection");
        uTextureLoc = glGetUniformLocation(shader.getProgramId(), "uTexture");
        uColorLoc = glGetUniformLocation(shader.getProgramId(), "uColor");
        uTextureArrayLoc = glGetUniformLocation(shader.getProgramId(), "uTextureArray");
        uUseTextureArrayLoc = glGetUniformLocation(shader.getProgramId(), "uUseTextureArray");
        uLayerLoc = glGetUniformLocation(shader.getProgramId(), "uLayer");

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 6 * 4 * Float.BYTES, GL_DYNAMIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        quadBuffer = BufferUtils.createFloatBuffer(6 * 4);

        // Default Texture Creation
        defaultTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, defaultTexture);
        ByteBuffer whitePixel = BufferUtils.createByteBuffer(4).put((byte) 255).put((byte) 255).put((byte) 255)
                .put((byte) 255).flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, whitePixel);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    }

    public void begin() {
        glClear(GL_DEPTH_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();

        float logicalW = windowWidth / (float) guiScale;
        float logicalH = windowHeight / (float) guiScale;
        float[] projectionMatrix = createOrthoMatrix(0, logicalW, logicalH, 0, -1, 1);
        glUniformMatrix4fv(uProjectionLoc, false, projectionMatrix);

        glUniform4f(uColorLoc, 1, 1, 1, 1);
        glUniform1i(uTextureLoc, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, defaultTexture);
        glUniform1i(uTextureArrayLoc, 1);
        glUniform1i(uUseTextureArrayLoc, 0);
    }

    public void end() {
        shader.unbind();
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
    }

    // ==================== METODI DI RENDERING MODIFICATI ====================

    /**
     * Render a textured quad using the FULL texture.
     */
    public void renderQuad(float x, float y, float width, float height, GuiTexture texture) {
        renderRawQuad(x, y, width, height, texture, 0, 0, 1, 1, 1, 1, 1, 1);
    }

    public void renderQuad(float x, float y, float width, float height, GuiTexture texture, float r, float g, float b,
            float a) {
        renderRawQuad(x, y, width, height, texture, 0, 0, 1, 1, r, g, b, a);
    }

    /**
     * NUOVO: Renderizza solo una porzione (Region) della texture.
     * Calcola automaticamente le coordinate UV basandosi sulla dimensione totale
     * dell'immagine.
     * * @param regionW Larghezza della porzione in pixel (es. 176)
     * 
     * @param regionH Altezza della porzione in pixel (es. 166)
     * @param u       Start X in pixel (solitamente 0)
     * @param v       Start Y in pixel (solitamente 0)
     */
    public void renderSubTexture(float x, float y, float width, float height, GuiTexture texture,
            float u, float v, float regionW, float regionH) {
        if (texture == null) {
            renderRect(x, y, width, height, 1, 0, 1, 1);
            return;
        }

        // Calcolo UV (Da 0.0 a 1.0)
        float totalW = (float) texture.getWidth();
        float totalH = (float) texture.getHeight();

        float uMin = u / totalW;
        float vMin = v / totalH;
        float uMax = (u + regionW) / totalW;
        float vMax = (v + regionH) / totalH;

        renderRawQuad(x, y, width, height, texture, uMin, vMin, uMax, vMax, 1, 1, 1, 1);
    }

    /**
     * Metodo interno "raw" che accetta UV custom.
     */
    private void renderRawQuad(float x, float y, float width, float height, GuiTexture texture,
            float uMin, float vMin, float uMax, float vMax,
            float r, float g, float b, float a) {
        if (texture != null) {
            texture.bind();
        } else {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, defaultTexture);
        }

        glUniform1i(uUseTextureArrayLoc, 0);
        glUniform4f(uColorLoc, r, g, b, a);

        quadBuffer.clear();

        // Triangolo 1
        quadBuffer.put(x).put(y).put(uMin).put(vMin); // TL
        quadBuffer.put(x).put(y + height).put(uMin).put(vMax); // BL
        quadBuffer.put(x + width).put(y).put(uMax).put(vMin); // TR

        // Triangolo 2
        quadBuffer.put(x + width).put(y).put(uMax).put(vMin); // TR
        quadBuffer.put(x).put(y + height).put(uMin).put(vMax); // BL
        quadBuffer.put(x + width).put(y + height).put(uMax).put(vMax); // BR

        quadBuffer.flip();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, quadBuffer);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        if (texture != null)
            texture.unbind();
    }

    /**
     * Render a colored rect (no texture)
     */
    public void renderRect(float x, float y, float width, float height,
            float r, float g, float b, float a) {
        renderQuad(x, y, width, height, null, r, g, b, a);
    }

    /**
     * Render text (simple implementation - pixel art digits)
     */
    public void renderText(String text, float x, float y, float size,
            float r, float g, float b, float a) {

        float currentX = x;
        float spacing = size * 0.7f; // Spacing between chars

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                renderDigit(c - '0', currentX, y, size, r, g, b, a);
            } else {
                // Placeholder for non-digits
                renderRect(currentX, y, size / 2, size, r, g, b, a);
            }
            currentX += spacing;
        }
    }

    /**
     * Helper to render a single digit using rectangles (pixel art style)
     * 3x5 grid
     */
    private void renderDigit(int digit, float x, float y, float h, float r, float g, float b, float a) {
        float w = h * 0.6f; // Aspect ratio
        float pW = w / 3.0f; // Pixel width
        float pH = h / 5.0f; // Pixel height

        // Bitmask for 3x5 font (1 = pixel, 0 = empty)
        // Row 0 is top
        int[] font = {
                0b111_101_101_101_111, // 0
                0b010_010_010_010_010, // 1
                0b111_001_111_100_111, // 2
                0b111_001_111_001_111, // 3
                0b101_101_111_001_001, // 4
                0b111_100_111_001_111, // 5
                0b111_100_111_101_111, // 6
                0b111_001_001_001_001, // 7
                0b111_101_111_101_111, // 8
                0b111_101_111_001_111 // 9
        };

        int mask = font[digit];

        // Draw pixels
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                int bitIndex = (4 - row) * 3 + (2 - col); // LSB is bottom-right
                boolean isSet = ((mask >> bitIndex) & 1) == 1;

                if (isSet) {
                    renderRect(x + col * pW, y + row * pH, pW, pH, r, g, b, a);
                }
            }
        }
    }

    /**
     * Render an isometric cube (3 visible faces) with guaranteed vertex
     * connections.
     */
    public void renderIsometricCube(float x, float y, float size, Block block) {
        if (atlasTexture == null)
            return;

        atlasTexture.bind(1);
        glUniform1i(uUseTextureArrayLoc, 1);

        // Radius/Half-width
        float r = size * 0.65f;

        // Dimensions
        float w = r; // Half Width
        float h = r * 0.5f; // Iso Height Step (Top Face Half Height)
        float v = r * 0.8f; // Side Height (Vertical drop)

        // Center Position (C) - The Y junction
        float cX = x + size * 0.5f;

        // Vertical centering logic:
        float boundingBoxOffset = (v - 2 * h) * 0.5f;
        float cY = (y + size * 0.5f) - boundingBoxOffset;

        // Define Vertices
        float xC = cX;
        float yC = cY;
        float xR = cX + w;
        float yR = cY - h;
        float xL = cX - w;
        float yL = cY - h;
        float xT = cX;
        float yT = cY - 2 * h;
        float xB = cX;
        float yB = cY + v;
        float xBR = cX + w;
        float yBR = cY - h + v;
        float xBL = cX - w;
        float yBL = cY - h + v;

        // Get Textures
        int topTX = block.getTextureTileX(0, 1, 0);
        int topTY = block.getTextureTileY(0, 1, 0);
        int topL = atlasTexture.getLayerIndex(topTX, topTY);

        int sideTX = block.getTextureTileX(1, 0, 0);
        int sideTY = block.getTextureTileY(1, 0, 0);
        int sideL = atlasTexture.getLayerIndex(sideTX, sideTY);

        // TOP FACE
        renderQuadPoly(topL, xT, yT, xR, yR, xC, yC, xL, yL, 1.0f);

        // RIGHT FACE
        renderQuadPoly(sideL, xC, yC, xR, yR, xBR, yBR, xB, yB, 0.6f);

        // LEFT FACE
        renderQuadPoly(sideL, xL, yL, xC, yC, xB, yB, xBL, yBL, 0.8f);

        glUniform1i(uUseTextureArrayLoc, 0);
        atlasTexture.unbind();
    }

    /**
     * Render a block as a flat sprite from the texture atlas.
     * Used for custom models (flowers, torches) that shouldn't be isometric cubes.
     */
    public void renderBlockFlat(float x, float y, float size, Block block) {
        if (atlasTexture == null)
            return;

        atlasTexture.bind(1);
        glUniform1i(uUseTextureArrayLoc, 1);

        // Get texture from "side" face (usually sufficient for simple blocks)
        int tileX = block.getTextureTileX(1, 0, 0);
        int tileY = block.getTextureTileY(1, 0, 0);
        int layer = atlasTexture.getLayerIndex(tileX, tileY);

        glUniform1f(uLayerLoc, (float) layer);

        // Render simple quad
        // Use full brightness
        glUniform4f(uColorLoc, 1.0f, 1.0f, 1.0f, 1.0f);

        quadBuffer.clear();
        quadBuffer.put(x).put(y).put(0).put(0);
        quadBuffer.put(x).put(y + size).put(0).put(1);
        quadBuffer.put(x + size).put(y).put(1).put(0);

        quadBuffer.put(x + size).put(y).put(1).put(0);
        quadBuffer.put(x).put(y + size).put(0).put(1);
        quadBuffer.put(x + size).put(y + size).put(1).put(1);

        quadBuffer.flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, quadBuffer);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        glUniform1i(uUseTextureArrayLoc, 0);
        atlasTexture.unbind();
    }

    /**
     * Render a quad defined by 4 arbitrary points (P1, P2, P3, P4).
     */
    private void renderQuadPoly(int layer, float x1, float y1, float x2, float y2, float x3, float y3, float x4,
            float y4, float bright) {
        glUniform1f(uLayerLoc, (float) layer);
        glUniform4f(uColorLoc, bright, bright, bright, 1.0f);

        quadBuffer.clear();

        // Triangle 1: P1, P2, P4
        quadBuffer.put(x1).put(y1).put(0).put(0); // TL
        quadBuffer.put(x4).put(y4).put(0).put(1); // BL
        quadBuffer.put(x2).put(y2).put(1).put(0); // TR

        // Triangle 2: P2, P4, P3
        quadBuffer.put(x2).put(y2).put(1).put(0); // TR
        quadBuffer.put(x4).put(y4).put(0).put(1); // BL
        quadBuffer.put(x3).put(y3).put(1).put(1); // BR

        quadBuffer.flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, quadBuffer);
        glDrawArrays(GL_TRIANGLES, 0, 6);
    }

    /**
     * Create orthographic projection matrix
     */
    private float[] createOrthoMatrix(float left, float right, float bottom, float top,
            float near, float far) {
        float[] matrix = new float[16];

        matrix[0] = 2f / (right - left);
        matrix[5] = 2f / (top - bottom);
        matrix[10] = -2f / (far - near);
        matrix[12] = -(right + left) / (right - left);
        matrix[13] = -(top + bottom) / (top - bottom);
        matrix[14] = -(far + near) / (far - near);
        matrix[15] = 1f;

        return matrix;
    }

    /**
     * Cleanup OpenGL resources
     */
    public void cleanup() {
        if (vao != 0) {
            glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (shader != null) {
            shader.cleanup();
            shader = null;
        }
    }

    // ==================== SHADERS ====================

    private String getVertexShader() {
        return """
                #version 330 core

                layout(location = 0) in vec2 aPosition;
                layout(location = 1) in vec2 aTexCoord;

                uniform mat4 uProjection;

                out vec2 vTexCoord;

                void main() {
                    vTexCoord = aTexCoord;
                    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
                }
                """;
    }

    private String getFragmentShader() {
        return """
                #version 330 core

                in vec2 vTexCoord;

                uniform sampler2D uTexture;
                uniform vec4 uColor;

                // Texture Array support
                uniform sampler2DArray uTextureArray;
                uniform int uUseTextureArray;
                uniform float uLayer;

                out vec4 FragColor;

                void main() {
                    vec4 texColor;

                    if (uUseTextureArray == 1) {
                        texColor = texture(uTextureArray, vec3(vTexCoord, uLayer));
                    } else {
                        texColor = texture(uTexture, vTexCoord);
                    }

                    if (texColor.a < 0.1) discard;

                    FragColor = texColor * uColor;
                }
                """;
    }

    // Getters
    public int getWindowWidth() {
        return windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }
}
