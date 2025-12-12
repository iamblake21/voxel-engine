package engine.rendering;

import engine.entity.Entity;
import engine.entity.ItemEntity;
import engine.rendering.Camera;
import engine.rendering.Shader;
import engine.rendering.TextureArray;
import engine.utils.Math3D.Mat4;
import engine.utils.Math3D.Vec3;
import engine.world.block.Block;
import engine.world.block.model.BlockModel;
import engine.world.block.model.BlockModelLoader;
import engine.world.item.BlockItem;
import engine.world.item.Item;
import engine.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders ItemEntity as 3D block models floating and spinning.
 */
public class ItemEntityRenderer {
    
    private Shader shader;
    private TextureArray atlas;
    
    // Standard cube mesh
    private int cubeVAO, cubeVBO;
    private int cubeVertexCount;
    
    // Custom model cache
    private final Map<String, CompiledItemModel> modelCache = new HashMap<>();
    
    // Shader uniforms
    private int uProj, uView, uModel, uTex, uTileIndex;
    private int uLightLevel, uTint;
    
    // Constants
    private static final int ATLAS_TILES_X = 8;
    private static final int ATLAS_TILES_Y = 8;
    private static final float ITEM_SCALE = 0.25f;
    private static final float BOB_HEIGHT = 0.1f;
    private static final float BOB_SPEED = 0.1f;
    private static final float SPIN_SPEED = 2.0f;
    
    private boolean initialized = false;
    
    public void init(TextureArray atlasTexture) {
        if (initialized) return;
        
        this.atlas = atlasTexture;
        
        shader = new Shader(VERTEX_SHADER, FRAGMENT_SHADER);
        uProj = shader.getUniformLocation("uProj");
        uView = shader.getUniformLocation("uView");
        uModel = shader.getUniformLocation("uModel");
        uTex = shader.getUniformLocation("uTex");
        uTileIndex = shader.getUniformLocation("uTileIndex");
        uLightLevel = shader.getUniformLocation("uLightLevel");
        uTint = shader.getUniformLocation("uTint");
        
        createCubeMesh();
        
        initialized = true;
        System.out.println("[ItemEntityRenderer] Initialized");
    }
    
    /**
     * Create a unit cube mesh centered at origin.
     * Each face has proper UVs for atlas sampling.
     */
    private void createCubeMesh() {
        // Format: x, y, z, u, v, nx, ny, nz
        // Cube from -0.5 to 0.5 (centered)
float[] vertices = {
        // +X face (right) - CORRETTI
        0.5f, -0.5f, 0.5f, 0, 1, 1, 0, 0,
        0.5f, -0.5f, -0.5f, 1, 1, 1, 0, 0,
        0.5f, 0.5f, -0.5f, 1, 0, 1, 0, 0,
        0.5f, -0.5f, 0.5f, 0, 1, 1, 0, 0,
        0.5f, 0.5f, -0.5f, 1, 0, 1, 0, 0,
        0.5f, 0.5f, 0.5f, 0, 0, 1, 0, 0,
        
        // -X face (left) - CORRETTI
        -0.5f, -0.5f, -0.5f, 0, 1, -1, 0, 0,
        -0.5f, -0.5f, 0.5f, 1, 1, -1, 0, 0,
        -0.5f, 0.5f, 0.5f, 1, 0, -1, 0, 0,
        -0.5f, -0.5f, -0.5f, 0, 1, -1, 0, 0,
        -0.5f, 0.5f, 0.5f, 1, 0, -1, 0, 0,
        -0.5f, 0.5f, -0.5f, 0, 0, -1, 0, 0,
        
        // +Y face (top) - CORRETTI
        -0.5f, 0.5f, 0.5f, 0, 1, 0, 1, 0,
         0.5f, 0.5f, 0.5f, 1, 1, 0, 1, 0,
         0.5f, 0.5f, -0.5f, 1, 0, 0, 1, 0,
        -0.5f, 0.5f, 0.5f, 0, 1, 0, 1, 0,
         0.5f, 0.5f, -0.5f, 1, 0, 0, 1, 0,
        -0.5f, 0.5f, -0.5f, 0, 0, 0, 1, 0,
        
        // -Y face (bottom) - CORRETTI
        -0.5f, -0.5f, -0.5f, 0, 1, 0, -1, 0,
         0.5f, -0.5f, -0.5f, 1, 1, 0, -1, 0,
         0.5f, -0.5f, 0.5f, 1, 0, 0, -1, 0,
        -0.5f, -0.5f, -0.5f, 0, 1, 0, -1, 0,
         0.5f, -0.5f, 0.5f, 1, 0, 0, -1, 0,
        -0.5f, -0.5f, 0.5f, 0, 0, 0, -1, 0,
        
        // +Z face (front) - CORRETTI
         0.5f, -0.5f, 0.5f, 0, 1, 0, 0, 1,
        -0.5f, -0.5f, 0.5f, 1, 1, 0, 0, 1,
        -0.5f, 0.5f, 0.5f, 1, 0, 0, 0, 1,
         0.5f, -0.5f, 0.5f, 0, 1, 0, 0, 1,
        -0.5f, 0.5f, 0.5f, 1, 0, 0, 0, 1,
         0.5f, 0.5f, 0.5f, 0, 0, 0, 0, 1,
        
        // -Z face (back) - CORRETTI
        -0.5f, -0.5f, -0.5f, 0, 1, 0, 0, -1,
         0.5f, -0.5f, -0.5f, 1, 1, 0, 0, -1,
         0.5f, 0.5f, -0.5f, 1, 0, 0, 0, -1,
        -0.5f, -0.5f, -0.5f, 0, 1, 0, 0, -1,
         0.5f, 0.5f, -0.5f, 1, 0, 0, 0, -1,
        -0.5f, 0.5f, -0.5f, 0, 0, 0, 0, -1,
    };
        
        cubeVertexCount = vertices.length / 8;
        
        cubeVAO = glGenVertexArrays();
        cubeVBO = glGenBuffers();
        
        glBindVertexArray(cubeVAO);
        glBindBuffer(GL_ARRAY_BUFFER, cubeVBO);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        
        int stride = 8 * Float.BYTES;
        // Position
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        // UV
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        // Normal
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5 * Float.BYTES);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
    
    public void begin(Camera camera, Vec3 sunDir) {
        if (!initialized) return;
        
        shader.bind();
        shader.setUniform(uProj, camera.getProjectionMatrix());
        shader.setUniform(uView, camera.getViewMatrix());
        glUniform1i(uTex, 0);
        
        // Light level based on sun
        float lightLevel = sunDir != null ? Math.max(0.4f, sunDir.y * 0.6f + 0.4f) : 0.8f;
        glUniform1f(uLightLevel, lightLevel);
        
        atlas.bind(0);
        
    glEnable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE); // Disabilita il culling
       }
    
    public void renderItemEntity(ItemEntity entity, float partialTick) {
        if (entity == null || entity.isRemoved()) return;
        
        ItemStack stack = entity.getStack();
        if (stack == null || stack.isEmpty()) return;
        
        Item item = stack.getItem();
        
        // Position with bobbing
        float x = entity.getLerpedX(partialTick);
        float y = entity.getLerpedY(partialTick);
        float z = entity.getLerpedZ(partialTick);
        
        // Bob offset
        float bob = entity.getLerpedBobOffset(partialTick);
        y += bob + 0.25f; // Offset so it floats above ground
        
        // Spin angle
        float spin = entity.getLerpedSpinAngle(partialTick);
        
        if (item instanceof BlockItem) {
            Block block = ((BlockItem) item).getBlock();
            renderBlockItem(block, x, y, z, spin);
        } else {
            // Generic item - render as flat sprite or small cube
            renderGenericItem(item, x, y, z, spin);
        }
    }
    
    private void renderBlockItem(Block block, float x, float y, float z, float spin) {
        // Check for custom model
        if (block.getProperties().hasCustomModel()) {
            renderCustomModelItem(block, x, y, z, spin);
            return;
        }
        
        // Standard cube block
        Mat4 model = Mat4.identity();
        model = Mat4.mul(model, Mat4.translate(x, y, z));
        model = Mat4.mul(model, rotateY((float) Math.toRadians(spin)));
        model = Mat4.mul(model, Mat4.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE));
        
        shader.setUniform(uModel, model);
        glUniform3f(uTint, 1f, 1f, 1f);
        
        // Render each face with correct tile
        renderCubeFaces(block);
    }
    
    private void renderCubeFaces(Block block) {
        // We need to render each face with potentially different textures
        // For simplicity, use the default texture for all faces
        // Multi-texture blocks will need special handling
        
        int tileX = block.getTextureTileX(0, 1, 0); // Top face as default
        int tileY = block.getTextureTileY(0, 1, 0);
        int tileIndex = tileY * ATLAS_TILES_X + tileX;
        
        // For multi-texture blocks, we'd need to render 6 separate quads
        // For now, use a single tile for the whole cube
        glUniform1i(uTileIndex, tileIndex);
        
        // Apply tint for grass/leaves
        if (block.getProperties().hasTintGrass()) {
            glUniform3f(uTint, 0.54f, 0.78f, 0.38f);
        } else if (block.getProperties().hasTintFoliage()) {
            glUniform3f(uTint, 0.52f, 0.75f, 0.35f);
        } else {
            glUniform3f(uTint, 1f, 1f, 1f);
        }
        
        glBindVertexArray(cubeVAO);
        glDrawArrays(GL_TRIANGLES, 0, cubeVertexCount);
        glBindVertexArray(0);
    }
    
    private void renderCustomModelItem(Block block, float x, float y, float z, float spin) {
        String modelPath = block.getProperties().getModelPath();
        CompiledItemModel compiled = getOrCompileModel(modelPath, block);
        
        if (compiled == null || compiled.vao == 0) {
            // Fallback to cube
            renderBlockItem(block, x, y, z, spin);
            return;
        }
        
        Mat4 model = Mat4.identity();
        model = Mat4.mul(model, Mat4.translate(x, y, z));
        model = Mat4.mul(model, rotateY((float) Math.toRadians(spin)));
        model = Mat4.mul(model, Mat4.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE));
        
        shader.setUniform(uModel, model);
        glUniform1i(uTileIndex, compiled.tileIndex);
        glUniform3f(uTint, 1f, 1f, 1f);
        
        glBindVertexArray(compiled.vao);
        glDrawArrays(GL_TRIANGLES, 0, compiled.vertexCount);
        glBindVertexArray(0);
    }
    
    private void renderGenericItem(Item item, float x, float y, float z, float spin) {
        // For non-block items, render as a small colored cube
        // In the future, this could render a 2D sprite billboard
        
        Mat4 model = Mat4.identity();
        model = Mat4.mul(model, Mat4.translate(x, y, z));
        model = Mat4.mul(model, rotateY((float) Math.toRadians(spin)));
        model = Mat4.mul(model, Mat4.scale(ITEM_SCALE * 0.5f, ITEM_SCALE * 0.5f, ITEM_SCALE * 0.5f));
        
        shader.setUniform(uModel, model);
        glUniform1i(uTileIndex, 0); // Default tile
        glUniform3f(uTint, 0.8f, 0.8f, 0.8f);
        
        glBindVertexArray(cubeVAO);
        glDrawArrays(GL_TRIANGLES, 0, cubeVertexCount);
        glBindVertexArray(0);
    }
    
    public void end() {
        shader.unbind();
    }
    
    // ==================== CUSTOM MODEL COMPILATION ====================
    
    private static class CompiledItemModel {
        int vao;
        int vbo;
        int vertexCount;
        int tileIndex;
    }
    
    private CompiledItemModel getOrCompileModel(String path, Block block) {
        String cacheKey = path + ":" + block.getNumericId();
        
        if (modelCache.containsKey(cacheKey)) {
            return modelCache.get(cacheKey);
        }
        
        BlockModel model = BlockModelLoader.load(path);
        if (model == null || model.elements == null || model.elements.isEmpty()) {
            modelCache.put(cacheKey, null);
            return null;
        }
        
        CompiledItemModel compiled = compileModel(model, block);
        modelCache.put(cacheKey, compiled);
        return compiled;
    }
    
    private CompiledItemModel compileModel(BlockModel model, Block block) {
        java.util.ArrayList<Float> vertices = new java.util.ArrayList<>();
        
        for (BlockModel.ModelElement elem : model.elements) {
            float minX = (elem.from[0] - 8) / 16f; // Center the model
            float minY = (elem.from[1] - 8) / 16f;
            float minZ = (elem.from[2] - 8) / 16f;
            float maxX = (elem.to[0] - 8) / 16f;
            float maxY = (elem.to[1] - 8) / 16f;
            float maxZ = (elem.to[2] - 8) / 16f;
            
            for (Map.Entry<String, BlockModel.ModelElement.Face> entry : elem.faces.entrySet()) {
                String faceName = entry.getKey();
                BlockModel.ModelElement.Face face = entry.getValue();
                
                addFaceVertices(vertices, faceName, minX, minY, minZ, maxX, maxY, maxZ, face, elem);
            }
        }
        
        if (vertices.isEmpty()) {
            return null;
        }
        
        float[] vertexArray = new float[vertices.size()];
        for (int i = 0; i < vertices.size(); i++) {
            vertexArray[i] = vertices.get(i);
        }
        
        CompiledItemModel compiled = new CompiledItemModel();
        compiled.vertexCount = vertexArray.length / 8;
        compiled.tileIndex = block.getTextureTileY(0, 0, 0) * ATLAS_TILES_X + block.getTextureTileX(0, 0, 0);
        
        compiled.vao = glGenVertexArrays();
        compiled.vbo = glGenBuffers();
        
        glBindVertexArray(compiled.vao);
        glBindBuffer(GL_ARRAY_BUFFER, compiled.vbo);
        glBufferData(GL_ARRAY_BUFFER, vertexArray, GL_STATIC_DRAW);
        
        int stride = 8 * Float.BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 5 * Float.BYTES);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        
        return compiled;
    }
    
    private void addFaceVertices(java.util.ArrayList<Float> vertices, String faceName,
                                  float minX, float minY, float minZ,
                                  float maxX, float maxY, float maxZ,
                                  BlockModel.ModelElement.Face face,
                                  BlockModel.ModelElement elem) {
        float[][] pos;
        float nx, ny, nz;
        
        switch (faceName) {
            case "north":
                pos = new float[][] {
                    {minX, minY, minZ}, {maxX, minY, minZ},
                    {maxX, maxY, minZ}, {minX, maxY, minZ}
                };
                nx = 0; ny = 0; nz = -1;
                break;
            case "south":
                pos = new float[][] {
                    {maxX, minY, maxZ}, {minX, minY, maxZ},
                    {minX, maxY, maxZ}, {maxX, maxY, maxZ}
                };
                nx = 0; ny = 0; nz = 1;
                break;
            case "west":
                pos = new float[][] {
                    {minX, minY, maxZ}, {minX, minY, minZ},
                    {minX, maxY, minZ}, {minX, maxY, maxZ}
                };
                nx = -1; ny = 0; nz = 0;
                break;
            case "east":
                pos = new float[][] {
                    {maxX, minY, minZ}, {maxX, minY, maxZ},
                    {maxX, maxY, maxZ}, {maxX, maxY, minZ}
                };
                nx = 1; ny = 0; nz = 0;
                break;
            case "down":
                pos = new float[][] {
                    {minX, minY, minZ}, {minX, minY, maxZ},
                    {maxX, minY, maxZ}, {maxX, minY, minZ}
                };
                nx = 0; ny = -1; nz = 0;
                break;
            case "up":
            default:
                pos = new float[][] {
                    {minX, maxY, minZ}, {maxX, maxY, minZ},
                    {maxX, maxY, maxZ}, {minX, maxY, maxZ}
                };
                nx = 0; ny = 1; nz = 0;
                break;
        }
        
        // Apply rotation if present
        if (elem.rotation != null && elem.rotation.angle != 0) {
            float ox = (elem.rotation.origin[0] - 8) / 16f;
            float oy = (elem.rotation.origin[1] - 8) / 16f;
            float oz = (elem.rotation.origin[2] - 8) / 16f;
            float angle = (float) Math.toRadians(elem.rotation.angle);
            
            for (int i = 0; i < 4; i++) {
                pos[i] = rotateVertex(pos[i], ox, oy, oz, elem.rotation.axis, angle);
            }
        }
        
        float[][] uv;
        if (face.uv != null && face.uv.length == 4) {
            uv = new float[][] {
                {face.uv[0] / 16f, face.uv[3] / 16f},
                {face.uv[2] / 16f, face.uv[3] / 16f},
                {face.uv[2] / 16f, face.uv[1] / 16f},
                {face.uv[0] / 16f, face.uv[1] / 16f}
            };
        } else {
            uv = new float[][] {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        }
        
        // Triangle 1: 0, 1, 2
        addVertex(vertices, pos[0], uv[0], nx, ny, nz);
        addVertex(vertices, pos[1], uv[1], nx, ny, nz);
        addVertex(vertices, pos[2], uv[2], nx, ny, nz);
        
        // Triangle 2: 0, 2, 3
        addVertex(vertices, pos[0], uv[0], nx, ny, nz);
        addVertex(vertices, pos[2], uv[2], nx, ny, nz);
        addVertex(vertices, pos[3], uv[3], nx, ny, nz);
    }
    
    private void addVertex(java.util.ArrayList<Float> vertices, float[] pos, float[] uv, float nx, float ny, float nz) {
        vertices.add(pos[0]);
        vertices.add(pos[1]);
        vertices.add(pos[2]);
        vertices.add(uv[0]);
        vertices.add(uv[1]);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
    }
    
    private float[] rotateVertex(float[] v, float ox, float oy, float oz, String axis, float angle) {
        float x = v[0] - ox;
        float y = v[1] - oy;
        float z = v[2] - oz;
        
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        
        float nx, ny, nz;
        switch (axis) {
            case "y":
                nx = x * cos - z * sin;
                ny = y;
                nz = x * sin + z * cos;
                break;
            case "x":
                nx = x;
                ny = y * cos - z * sin;
                nz = y * sin + z * cos;
                break;
            default: // z
                nx = x * cos - y * sin;
                ny = x * sin + y * cos;
                nz = z;
        }
        
        return new float[] {nx + ox, ny + oy, nz + oz};
    }
    
    private Mat4 rotateY(float radians) {
        Mat4 m = Mat4.identity();
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        m.m[0] = c;
        m.m[2] = -s;
        m.m[8] = s;
        m.m[10] = c;
        return m;
    }
    
    public void cleanup() {
        if (shader != null) shader.cleanup();
        if (cubeVAO != 0) glDeleteVertexArrays(cubeVAO);
        if (cubeVBO != 0) glDeleteBuffers(cubeVBO);
        
        for (CompiledItemModel model : modelCache.values()) {
            if (model != null) {
                if (model.vao != 0) glDeleteVertexArrays(model.vao);
                if (model.vbo != 0) glDeleteBuffers(model.vbo);
            }
        }
        modelCache.clear();
    }
    
    // ==================== SHADERS ====================
    
    private static final String VERTEX_SHADER = 
        "#version 330 core\n" +
        "layout(location=0) in vec3 aPos;\n" +
        "layout(location=1) in vec2 aUV;\n" +
        "layout(location=2) in vec3 aNormal;\n" +
        "\n" +
        "uniform mat4 uProj, uView, uModel;\n" +
        "\n" +
        "out vec2 vUV;\n" +
        "out vec3 vNormal;\n" +
        "\n" +
        "void main() {\n" +
        "    vUV = aUV;\n" +
        "    vNormal = mat3(uModel) * aNormal;\n" +
        "    gl_Position = uProj * uView * uModel * vec4(aPos, 1.0);\n" +
        "}\n";
    
    private static final String FRAGMENT_SHADER = 
        "#version 330 core\n" +
        "in vec2 vUV;\n" +
        "in vec3 vNormal;\n" +
        "\n" +
        "uniform sampler2DArray uTex;\n" +
        "uniform int uTileIndex;\n" +
        "uniform float uLightLevel;\n" +
        "uniform vec3 uTint;\n" +
        "\n" +
        "out vec4 FragColor;\n" +
        "\n" +
        "void main() {\n" +
        "    vec4 texColor = texture(uTex, vec3(vUV, float(uTileIndex)));\n" +
        "    if (texColor.a < 0.1) discard;\n" +
        "\n" +
        "    // Simple directional lighting\n" +
        "    vec3 lightDir = normalize(vec3(0.3, 1.0, 0.5));\n" +
        "    float diff = max(dot(normalize(vNormal), lightDir), 0.0);\n" +
        "    float light = 0.5 + 0.5 * diff;\n" +
        "    light *= uLightLevel;\n" +
        "\n" +
        "    vec3 finalColor = texColor.rgb * uTint * light;\n" +
        "    FragColor = vec4(finalColor, texColor.a);\n" +
        "}\n";
}