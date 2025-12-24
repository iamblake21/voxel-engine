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

    private int windowWidth;
    private int windowHeight;

    public void updateDimensions(int width, int height) {
        this.windowWidth = width;
        this.windowHeight = height;
    }

    private Shader shader;
    private int vao, vbo;
    private int defaultTexture;

    // Batching
    private static final int MAX_QUADS = 2048;
    private static final int VERTEX_SIZE = 9; // Pos(2) + UV(2) + Color(4) + Layer(1)
    private static final int VERTICES_PER_QUAD = 6;
    private FloatBuffer batchBuffer;
    private int quadCount = 0;

    // Current State for Batching
    private int currentTextureId = 0;
    private boolean currentUseArray = false;

    // Shader uniform locations
    private int uProjectionLoc, uTextureLoc;
    private int uTextureArrayLoc, uUseTextureArrayLoc;

    private engine.rendering.TextureArray atlasTexture;
    private FontTexture fontTexture;

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
        uProjectionLoc = glGetUniformLocation(shader.getProgramId(), "uProjection");
        uTextureLoc = glGetUniformLocation(shader.getProgramId(), "uTexture");
        // uColorLoc removed
        uTextureArrayLoc = glGetUniformLocation(shader.getProgramId(), "uTextureArray");
        uUseTextureArrayLoc = glGetUniformLocation(shader.getProgramId(), "uUseTextureArray");
        // uLayerLoc removed

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        // Buffer size: MAX_QUADS * 6 vertices * VERTEX_SIZE floats
        glBufferData(GL_ARRAY_BUFFER, MAX_QUADS * VERTICES_PER_QUAD * VERTEX_SIZE * Float.BYTES, GL_DYNAMIC_DRAW);

        int stride = VERTEX_SIZE * Float.BYTES;

        // 0: Pos (2)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);

        // 1: UV (2)
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2 * Float.BYTES);
        glEnableVertexAttribArray(1);

        // 2: Color (4)
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 4 * Float.BYTES);
        glEnableVertexAttribArray(2);

        // 3: Layer (1)
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 8 * Float.BYTES);
        glEnableVertexAttribArray(3);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

        batchBuffer = BufferUtils.createFloatBuffer(MAX_QUADS * VERTICES_PER_QUAD * VERTEX_SIZE);

        // Default Texture Creation
        defaultTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, defaultTexture);
        ByteBuffer whitePixel = BufferUtils.createByteBuffer(4).put((byte) 255).put((byte) 255).put((byte) 255)
                .put((byte) 255).flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, whitePixel);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Init Font
        this.fontTexture = new FontTexture();
    }

    public void begin() {
        glClear(GL_DEPTH_BUFFER_BIT);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        shader.bind();

        // Always bind Atlas to Unit 1 to satisfy Sampler2DArray validation
        if (atlasTexture != null) {
            atlasTexture.bind(1);
        }

        float logicalW = windowWidth / (float) guiScale;
        float logicalH = windowHeight / (float) guiScale;
        float[] projectionMatrix = createOrthoMatrix(0, logicalW, logicalH, 0, -1, 1);
        glUniformMatrix4fv(uProjectionLoc, false, projectionMatrix);

        glUniform1i(uTextureLoc, 0);
        glUniform1i(uTextureArrayLoc, 1);

        // Reset state
        currentTextureId = 0;
        currentUseArray = false;
        quadCount = 0;
        batchBuffer.clear();
    }

    public void end() {
        flush(); // Draw remaining quads
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

    private void flush() {
        if (quadCount == 0)
            return;

        // Bind correct textures
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, currentTextureId != 0 ? currentTextureId : defaultTexture);

        glUniform1i(uUseTextureArrayLoc, currentUseArray ? 1 : 0);

        // Upload and Draw
        batchBuffer.flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, batchBuffer);

        glDrawArrays(GL_TRIANGLES, 0, quadCount * 6);

        glBindVertexArray(0);

        // Reset
        batchBuffer.clear();
        quadCount = 0;
    }

    private void checkFlush(int textureId, boolean useArray) {
        // If state changed or buffer full, flush
        if (textureId != currentTextureId || useArray != currentUseArray || quadCount >= MAX_QUADS) {
            flush();
            currentTextureId = textureId;
            currentUseArray = useArray;
        }
    }

    /**
     * Metodo interno "raw" che accetta UV custom.
     */
    private void renderRawQuad(float x, float y, float width, float height, GuiTexture texture,
            float uMin, float vMin, float uMax, float vMax,
            float r, float g, float b, float a) {

        int texID = (texture != null) ? texture.getId() : defaultTexture;
        checkFlush(texID, false);

        // Add 6 vertices to buffer
        addVertex(x, y, uMin, vMin, r, g, b, a, 0); // TL
        addVertex(x, y + height, uMin, vMax, r, g, b, a, 0); // BL
        addVertex(x + width, y, uMax, vMin, r, g, b, a, 0); // TR

        addVertex(x + width, y, uMax, vMin, r, g, b, a, 0); // TR
        addVertex(x, y + height, uMin, vMax, r, g, b, a, 0); // BL
        addVertex(x + width, y + height, uMax, vMax, r, g, b, a, 0); // BR

        quadCount++;
    }

    private void addVertex(float x, float y, float u, float v, float r, float g, float b, float a, float layer) {
        batchBuffer.put(x).put(y);
        batchBuffer.put(u).put(v);
        batchBuffer.put(r).put(g).put(b).put(a);
        batchBuffer.put(layer);
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
    /**
     * Render text using Bitmap Font Texture.
     */
    public void renderText(String text, float x, float y, float size,
            float r, float g, float b, float a) {

        if (fontTexture == null)
            return;

        // Font size scaler.
        // Base font size is 8px.
        // "size" parameter is historically around 2.0 to 12.0 in previous calls.
        // Pixel font 8px * 2.0 = 16px char height.
        // Let's treat "size" as a general scale factor relative to 8px base?
        // Or keep consistency.
        // Previous renderDigit: 3x5 grid.
        // size was Height.
        // New font: 8px height.

        // If passed size is 12.0f, then we scale 8px -> 12px?
        // Let's assume passed size is roughly desired height in pixels.
        float scale = size / 8.0f;

        // Snap scale to nearest integer for perfect pixel art, or keep float for smooth
        // Zoom?
        // For text legibility, integer scaling is best.
        if (scale < 1.0f)
            scale = 1.0f;
        // scale = (float) Math.floor(scale); // Optional: Force Integer Scale

        float charW = 8 * scale;
        float charH = 8 * scale;

        // 5px wide glyph + 1px space = 6px stride
        float spacing = 6 * scale;

        fontTexture.bind();
        checkFlush(fontTexture.getTextureId(), false);

        float currentX = x;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            float[] uv = fontTexture.getUV(c);
            float u = uv[0];
            float v = uv[1];
            float uw = uv[2];
            float vh = uv[3];

            // Draw Quad
            // Vertices:
            // TL
            addVertex(currentX, y, u, v, r, g, b, a, 0);
            // BL
            addVertex(currentX, y + charH, u, v + vh, r, g, b, a, 0);
            // TR
            addVertex(currentX + charW, y, u + uw, v, r, g, b, a, 0);

            // TR
            addVertex(currentX + charW, y, u + uw, v, r, g, b, a, 0);
            // BL
            addVertex(currentX, y + charH, u, v + vh, r, g, b, a, 0);
            // BR
            addVertex(currentX + charW, y + charH, u + uw, v + vh, r, g, b, a, 0);

            quadCount++;

            currentX += spacing;
        }
    }

    /**
     * Render an isometric cube (3 visible faces) with guaranteed vertex
     * connections.
     */
    public void renderIsometricCube(float x, float y, float size, Block block) {
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

        // CHECK FOR CUSTOM TEXTURES
        if (block.getProperties().hasCustomTextures()) {
            String topPath = block.getProperties().getTextureTop();
            String sidePath = block.getProperties().getTextureSide();
            String bottomPath = block.getProperties().getTextureBottom();

            // Fallbacks
            if (topPath == null && sidePath != null)
                topPath = sidePath;
            if (sidePath == null && topPath != null)
                sidePath = topPath;
            if (sidePath == null && bottomPath != null)
                sidePath = bottomPath;

            // Resolve textures
            GuiTexture topTex = resolveTexture(topPath);
            GuiTexture sideTex = resolveTexture(sidePath);

            // TOP FACE
            renderTexturedPoly(topTex, xT, yT, xR, yR, xC, yC, xL, yL, 1.0f);

            // RIGHT FACE
            renderTexturedPoly(sideTex, xC, yC, xR, yR, xBR, yBR, xB, yB, 0.6f);

            // LEFT FACE
            renderTexturedPoly(sideTex, xL, yL, xC, yC, xB, yB, xBL, yBL, 0.8f);

            return;
        }

        if (atlasTexture == null)
            return;

        atlasTexture.bind(1);
        checkFlush(-1, true); // Ensure we are in Array mode

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
    }

    private GuiTexture resolveTexture(String path) {
        if (path == null)
            return null;
        // Basic fixup if needed, though getTexture usually handles clean paths
        // The user provided "game:textures/blocks/cobblestone.png"
        // Most loaders need "textures/blocks/cobblestone.png"
        String cleanPath = path;
        if (cleanPath.startsWith("game:")) {
            cleanPath = cleanPath.substring(5);
        }
        return getTexture(cleanPath);
    }

    private void renderTexturedPoly(GuiTexture texture, float x1, float y1, float x2, float y2, float x3, float y3,
            float x4,
            float y4, float bright) {

        int texID = (texture != null) ? texture.getId() : defaultTexture;
        checkFlush(texID, false); // False = use 2D texture

        // Triangle 1
        addVertex(x1, y1, 0, 0, bright, bright, bright, 1, 0);
        addVertex(x4, y4, 0, 1, bright, bright, bright, 1, 0);
        addVertex(x2, y2, 1, 0, bright, bright, bright, 1, 0);

        // Triangle 2
        addVertex(x2, y2, 1, 0, bright, bright, bright, 1, 0);
        addVertex(x4, y4, 0, 1, bright, bright, bright, 1, 0);
        addVertex(x3, y3, 1, 1, bright, bright, bright, 1, 0);

        quadCount++;
    }

    // Cache for custom block textures in GUI
    private final java.util.Map<String, GuiTexture> customBlockTextures = new java.util.HashMap<>();
    private final java.util.Map<String, GuiTexture> textureCache = new java.util.HashMap<>();

    public GuiTexture getTexture(String path) {
        if (textureCache.containsKey(path)) {
            return textureCache.get(path);
        }
        try {
            GuiTexture tex = new GuiTexture(path);
            textureCache.put(path, tex);
            return tex;
        } catch (Exception e) {
            System.err.println("Failed to load GUI texture: " + path);
            textureCache.put(path, null); // Cache miss to prevent retry
            return null;
        }
    }

    private GuiTexture getCustomBlockTexture(String modelPath) {
        if (customBlockTextures.containsKey(modelPath)) {
            return customBlockTextures.get(modelPath);
        }

        // Load model to find texture
        engine.world.block.model.BlockModel model = engine.world.block.model.BlockModelLoader.load(modelPath);
        if (model != null && !model.elements.isEmpty()) {
            // Find first texture
            for (engine.world.block.model.BlockModel.ModelElement elem : model.elements) {
                for (engine.world.block.model.BlockModel.ModelElement.Face face : elem.faces.values()) {
                    if (face.texture != null) {
                        String resolved = engine.world.block.model.BlockModelLoader.resolveTexture(model, face.texture);
                        if (resolved != null) {
                            // Path correction (same as ItemEntityRenderer)
                            String loadPath = resolved;
                            if (!resolved.endsWith(".png")) {
                                if (resolved.startsWith("block/"))
                                    loadPath = "textures/blocks/" + resolved.substring(6) + ".png";
                                else
                                    loadPath = "textures/" + resolved + ".png";
                            }

                            try {
                                GuiTexture tex = new GuiTexture(loadPath);
                                customBlockTextures.put(modelPath, tex);
                                return tex;
                            } catch (Exception e) {
                                System.err.println("Failed to load GUI texture: " + loadPath);
                            }
                        }
                    }
                }
            }
        }
        customBlockTextures.put(modelPath, null); // Cache miss
        return null;
    }

    /**
     * Render a block as a flat sprite from the texture atlas.
     * Used for custom models (flowers, torches) that shouldn't be isometric cubes.
     */
    public void renderBlockFlat(float x, float y, float size, Block block) {
        // Try custom model/texture first
        if (block.getProperties().hasCustomModel()) {
            String modelPath = block.getProperties().getModelPath();
            GuiTexture customTex = getCustomBlockTexture(modelPath);

            if (customTex != null) {
                checkFlush(customTex.getId(), false);

                addVertex(x, y, 0, 0, 1, 1, 1, 1, 0);
                addVertex(x, y + size, 0, 1, 1, 1, 1, 1, 0);
                addVertex(x + size, y, 1, 0, 1, 1, 1, 1, 0);

                addVertex(x + size, y, 1, 0, 1, 1, 1, 1, 0);
                addVertex(x, y + size, 0, 1, 1, 1, 1, 1, 0);
                addVertex(x + size, y + size, 1, 1, 1, 1, 1, 1, 0);

                quadCount++;
                return;
            }
        }

        if (atlasTexture == null)
            return;

        atlasTexture.bind(1);
        // Check Flush (Atlas is Texture Unit 1? No, we need to bind actual texture ID
        // of atlas)
        // We use a special "textureId" for Atlas state to indicate "We are using
        // Array".
        // Say -1.

        checkFlush(0, true);

        // Calculate Layer
        int tileX = block.getTextureTileX(1, 0, 0);
        int tileY = block.getTextureTileY(1, 0, 0);
        int layer = atlasTexture.getLayerIndex(tileX, tileY);

        // Ensure atlas is bound to unit 1 (do this once per batch or lazily?
        // For now, let's just assume it's bound if we are in this state.
        // But we need to bind it at least once.
        atlasTexture.bind(1);

        // Add vertices with LAYER
        addVertex(x, y, 0, 0, 1, 1, 1, 1, layer);
        addVertex(x, y + size, 0, 1, 1, 1, 1, 1, layer);
        addVertex(x + size, y, 1, 0, 1, 1, 1, 1, layer);

        addVertex(x + size, y, 1, 0, 1, 1, 1, 1, layer);
        addVertex(x, y + size, 0, 1, 1, 1, 1, 1, layer);
        addVertex(x + size, y + size, 1, 1, 1, 1, 1, 1, layer);

        quadCount++;
    }

    /**
     * Render a quad defined by 4 arbitrary points (P1, P2, P3, P4).
     */
    private void renderQuadPoly(int layer, float x1, float y1, float x2, float y2, float x3, float y3, float x4,
            float y4, float bright) {

        checkFlush(0, true);
        atlasTexture.bind(1);

        // Triangle 1
        addVertex(x1, y1, 0, 0, bright, bright, bright, 1, layer);
        addVertex(x4, y4, 0, 1, bright, bright, bright, 1, layer);
        addVertex(x2, y2, 1, 0, bright, bright, bright, 1, layer);

        // Triangle 2
        addVertex(x2, y2, 1, 0, bright, bright, bright, 1, layer);
        addVertex(x4, y4, 0, 1, bright, bright, bright, 1, layer);
        addVertex(x3, y3, 1, 1, bright, bright, bright, 1, layer);

        quadCount++;
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
                layout(location = 2) in vec4 aColor;
                layout(location = 3) in float aLayer;

                uniform mat4 uProjection;

                out vec2 vTexCoord;
                out vec4 vColor;
                out float vLayer;

                void main() {
                    vTexCoord = aTexCoord;
                    vColor = aColor;
                    vLayer = aLayer;
                    gl_Position = uProjection * vec4(aPosition, 0.0, 1.0);
                }
                """;
    }

    private String getFragmentShader() {
        return """
                #version 330 core

                in vec2 vTexCoord;
                in vec4 vColor;
                in float vLayer;

                uniform sampler2D uTexture;
                // uniform vec4 uColor; // Removed, using attribute

                // Texture Array support
                uniform sampler2DArray uTextureArray;
                uniform int uUseTextureArray;
                // uniform float uLayer; // Removed, using attribute

                out vec4 FragColor;

                void main() {
                    vec4 texColor;

                    if (uUseTextureArray == 1) {
                        texColor = texture(uTextureArray, vec3(vTexCoord, vLayer));
                    } else {
                        texColor = texture(uTexture, vTexCoord);
                    }

                    if (texColor.a < 0.1) discard;

                    FragColor = texColor * vColor;
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
