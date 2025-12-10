package engine.ui;

import engine.rendering.Shader;
import engine.world.block.Block;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * 2D GUI Renderer with orthographic projection.
 * Renders quads, text, and UI elements.
 */
public class GuiRenderer {

    // GUI Scale factor (1 = normal, 2 = 2x, 3 = 3x larger)
    private int guiScale = 2;

    private final int windowWidth;
    private final int windowHeight;

    private Shader shader;
    private int vao, vbo;

    private FloatBuffer quadBuffer;

    // Shader uniform locations
    private int uProjectionLoc;
    private int uTextureLoc;
    private int uColorLoc;

    // New uniforms for TextureArray support
    private int uTextureArrayLoc;
    private int uUseTextureArrayLoc;
    private int uLayerLoc;

    // Block texture atlas for rendering block items
    private engine.rendering.TextureArray atlasTexture;

    public GuiRenderer(int windowWidth, int windowHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        init();

        System.out.println("[GuiRenderer] Initialized with Scale=" + guiScale);
    }

    /**
     * Set the GUI scale factor
     * 
     * @param scale Scale factor (min 1)
     */
    public void setGuiScale(int scale) {
        this.guiScale = Math.max(1, scale);
    }

    /**
     * Get current GUI scale
     */
    public int getGuiScale() {
        return guiScale;
    }

    /**
     * Set the block texture atlas for rendering block items
     */
    public void setAtlasTexture(engine.rendering.TextureArray atlasTexture) {
        this.atlasTexture = atlasTexture;
    }

    private void init() {
        // Create shader
        System.out.println("[GuiRenderer] Creating shader...");
        try {
            shader = new Shader(getVertexShader(), getFragmentShader());
            System.out.println("[GuiRenderer] Shader compiled successfully");
        } catch (Exception e) {
            System.err.println("[GuiRenderer] SHADER ERROR: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // Cache uniform locations
        uProjectionLoc = glGetUniformLocation(shader.getProgramId(), "uProjection");
        uTextureLoc = glGetUniformLocation(shader.getProgramId(), "uTexture");
        uColorLoc = glGetUniformLocation(shader.getProgramId(), "uColor");

        // Cache new uniform locations
        uTextureArrayLoc = glGetUniformLocation(shader.getProgramId(), "uTextureArray");
        uUseTextureArrayLoc = glGetUniformLocation(shader.getProgramId(), "uUseTextureArray");
        uLayerLoc = glGetUniformLocation(shader.getProgramId(), "uLayer");

        System.out.println("[GuiRenderer] Uniform locations: uProjection=" + uProjectionLoc +
                ", uTexture=" + uTextureLoc + ", uColor=" + uColorLoc +
                ", uTextureArray=" + uTextureArrayLoc + ", uUseTextureArray=" + uUseTextureArrayLoc);

        if (uProjectionLoc == -1 || uTextureLoc == -1 || uColorLoc == -1) {
            System.err.println("[GuiRenderer] WARNING: Some uniforms not found in shader!");
        }

        // Create VAO and VBO for quad
        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);

        // Allocate buffer (6 vertices * 4 floats each: x,y,u,v)
        glBufferData(GL_ARRAY_BUFFER, 6 * 4 * Float.BYTES, GL_DYNAMIC_DRAW);

        // Position attribute (location = 0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        // TexCoord attribute (location = 1)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * Float.BYTES, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        // Create reusable buffer
        quadBuffer = BufferUtils.createFloatBuffer(6 * 4);

        // Create default white 1x1 texture for rendering without textures
        defaultTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, defaultTexture);
        ByteBuffer whitePixel = BufferUtils.createByteBuffer(4);
        whitePixel.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255).flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, whitePixel);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        System.out.println("[GuiRenderer] Initialized (" + windowWidth + "x" + windowHeight + ") VAO=" + vao + ", VBO="
                + vbo + ", DefaultTex=" + defaultTexture);
    }

    private int defaultTexture; // Keep reference to default white texture

    /**
     * Begin 2D rendering
     * Sets up orthographic projection and blending
     */
    public void begin() {
        // CRITICAL: Clear depth buffer so 3D world doesn't block 2D GUI
        glClear(GL_DEPTH_BUFFER_BIT);

        // Disable depth testing and writing completely for 2D
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        // Disable face culling for 2D elements
        glDisable(GL_CULL_FACE);

        // Enable alpha blending
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();

        // Set orthographic projection matrix with GUI Scale
        // Divide logical size by scale to zoom in
        float logicalW = windowWidth / (float) guiScale;
        float logicalH = windowHeight / (float) guiScale;

        float[] projectionMatrix = createOrthoMatrix(0, logicalW, logicalH, 0, -1, 1);
        glUniformMatrix4fv(uProjectionLoc, false, projectionMatrix);

        // Default white color
        glUniform4f(uColorLoc, 1, 1, 1, 1);

        // Bind default white texture to texture unit 0
        glUniform1i(uTextureLoc, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, defaultTexture);

        // Configure texture array unit (unit 1)
        glUniform1i(uTextureArrayLoc, 1);
        glUniform1i(uUseTextureArrayLoc, 0); // Disable array by default
    }

    /**
     * End 2D rendering
     * Restore OpenGL state for 3D world rendering
     */
    public void end() {
        shader.unbind();

        // Restore 3D rendering state
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        // Do not force culling enable/disable, leave it to world
    }

    /**
     * Render a textured quad
     * 
     * @param x       Screen X (pixels from left)
     * @param y       Screen Y (pixels from top)
     * @param width   Width in pixels
     * @param height  Height in pixels
     * @param texture Texture to render
     */
    public void renderQuad(float x, float y, float width, float height, GuiTexture texture) {
        renderQuad(x, y, width, height, texture, 1, 1, 1, 1);
    }

    /**
     * Render a textured quad with color tint
     */
    public void renderQuad(float x, float y, float width, float height, GuiTexture texture,
            float r, float g, float b, float a) {
        if (texture != null) {
            texture.bind();
        } else {
            // CRITICAL: When no texture specified, bind white texture so color works
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, defaultTexture);
        }

        // Ensure array mode is off
        glUniform1i(uUseTextureArrayLoc, 0);

        glUniform4f(uColorLoc, r, g, b, a);

        // Build quad vertices (2 triangles)
        quadBuffer.clear();

        // Triangle 1: top-left, bottom-left, top-right
        quadBuffer.put(x).put(y).put(0).put(0); // Top-left
        quadBuffer.put(x).put(y + height).put(0).put(1); // Bottom-left
        quadBuffer.put(x + width).put(y).put(1).put(0); // Top-right

        // Triangle 2: top-right, bottom-left, bottom-right
        quadBuffer.put(x + width).put(y).put(1).put(0); // Top-right
        quadBuffer.put(x).put(y + height).put(0).put(1); // Bottom-left
        quadBuffer.put(x + width).put(y + height).put(1).put(1); // Bottom-right

        quadBuffer.flip();

        // Upload and draw
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, quadBuffer);
        glDrawArrays(GL_TRIANGLES, 0, 6);

        if (texture != null) {
            texture.unbind();
        }
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
