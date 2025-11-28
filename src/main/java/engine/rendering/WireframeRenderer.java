package engine.rendering;

import engine.utils.Math3D.Mat4;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Renders wireframe cube for block selection highlight
 * EXACT PORT from MinecraftOneFile
 */
public class WireframeRenderer {
    
    private int vao;
    private int vbo;
    private int vertexCount;
    private Shader shader;
    
    // Uniform locations
    private int uProj, uView, uModel, uTint;
    
    public WireframeRenderer() {
        init();
    }
    
    private void init() {
        // Wireframe cube vertices (12 lines = 24 vertices)
        // Format: x, y, z, r, g, b, a (same as original)
        float[] vertices = {
            // Bottom face edges
            0, 0, 0, 1, 1, 1, 1,   1, 0, 0, 1, 1, 1, 1,
            1, 0, 0, 1, 1, 1, 1,   1, 0, 1, 1, 1, 1, 1,
            1, 0, 1, 1, 1, 1, 1,   0, 0, 1, 1, 1, 1, 1,
            0, 0, 1, 1, 1, 1, 1,   0, 0, 0, 1, 1, 1, 1,
            // Top face edges
            0, 1, 0, 1, 1, 1, 1,   1, 1, 0, 1, 1, 1, 1,
            1, 1, 0, 1, 1, 1, 1,   1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1,   0, 1, 1, 1, 1, 1, 1,
            0, 1, 1, 1, 1, 1, 1,   0, 1, 0, 1, 1, 1, 1,
            // Vertical edges
            0, 0, 0, 1, 1, 1, 1,   0, 1, 0, 1, 1, 1, 1,
            1, 0, 0, 1, 1, 1, 1,   1, 1, 0, 1, 1, 1, 1,
            1, 0, 1, 1, 1, 1, 1,   1, 1, 1, 1, 1, 1, 1,
            0, 0, 1, 1, 1, 1, 1,   0, 1, 1, 1, 1, 1, 1
        };
        
        vertexCount = vertices.length / 7;
        
        // Create VAO/VBO
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        
        FloatBuffer fb = MemoryUtil.memAllocFloat(vertices.length);
        fb.put(vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, fb, GL_STATIC_DRAW);
        MemoryUtil.memFree(fb);
        
        // Position attribute (location 0)
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 7 * 4, 0);
        
        // Color attribute (location 1)
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, 7 * 4, 3 * 4);
        
        glBindVertexArray(0);
        
        // Create simple shader for wireframe
        shader = new Shader(getVertexShader(), getFragmentShader());
        uProj = shader.getUniformLocation("uProj");
        uView = shader.getUniformLocation("uView");
        uModel = shader.getUniformLocation("uModel");
        uTint = shader.getUniformLocation("uTint");
    }
    
    /**
     * Draw wireframe at block position
     */
    public void draw(Mat4 projection, Mat4 view, int blockX, int blockY, int blockZ, 
                     float r, float g, float b, float a) {
        shader.bind();
        
        shader.setUniform(uProj, projection);
        shader.setUniform(uView, view);
        
        // Position the wireframe at block location
        Mat4 model = Mat4.translate(blockX, blockY, blockZ);
        
        shader.setUniform(uModel, model);
        shader.setUniform(uTint, r, g, b, a);
        
        glBindVertexArray(vao);
        glLineWidth(2.0f);
        glDrawArrays(GL_LINES, 0, vertexCount);
        glBindVertexArray(0);
        
        shader.unbind();
    }
    
    /**
     * Draw wireframe at block position with default yellow color
     */
    public void draw(Mat4 projection, Mat4 view, int blockX, int blockY, int blockZ) {
        draw(projection, view, blockX, blockY, blockZ, 0.0f, 0.0f, 0.0f, 1.0f);
    }
    
    public void cleanup() {
        if (vao != 0) glDeleteVertexArrays(vao);
        if (vbo != 0) glDeleteBuffers(vbo);
        if (shader != null) shader.cleanup();
    }
    
    private String getVertexShader() {
        return "#version 330 core\n" +
            "layout(location=0) in vec3 aPos;\n" +
            "layout(location=1) in vec4 aColor;\n" +
            "uniform mat4 uProj, uView, uModel;\n" +
            "out vec4 vColor;\n" +
            "void main() {\n" +
            "    vColor = aColor;\n" +
            "    gl_Position = uProj * uView * uModel * vec4(aPos, 1.0);\n" +
            "}";
    }
    
    private String getFragmentShader() {
        return "#version 330 core\n" +
            "in vec4 vColor;\n" +
            "uniform vec4 uTint;\n" +
            "out vec4 FragColor;\n" +
            "void main() {\n" +
            "    FragColor = vColor * uTint;\n" +
            "}";
    }
}