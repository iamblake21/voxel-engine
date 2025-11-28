package engine.rendering;

import engine.utils.Math3D.Mat4;
import engine.utils.Math3D.Vec3;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Shader program wrapper - handles compilation and uniforms
 */
public class Shader {
    
    private int programId;
    private boolean bound = false;
    
    /**
     * Create shader from source code
     */
    public Shader(String vertexSource, String fragmentSource) {
        programId = createShaderProgram(vertexSource, fragmentSource);
    }
    
    /**
     * Compile and link shader program
     */
    private int createShaderProgram(String vs, String fs) {
        int vertexShader = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertexShader, vs);
        glCompileShader(vertexShader);
        
        if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(vertexShader);
            glDeleteShader(vertexShader);
            throw new RuntimeException("Vertex shader compilation failed:\n" + log);
        }
        
        int fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragmentShader, fs);
        glCompileShader(fragmentShader);
        
        if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(fragmentShader);
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            throw new RuntimeException("Fragment shader compilation failed:\n" + log);
        }
        
        int program = glCreateProgram();
        glAttachShader(program, vertexShader);
        glAttachShader(program, fragmentShader);
        glLinkProgram(program);
        
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            glDeleteProgram(program);
            glDeleteShader(vertexShader);
            glDeleteShader(fragmentShader);
            throw new RuntimeException("Shader linking failed:\n" + log);
        }
        
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        
        return program;
    }
    
    /**
     * Bind this shader for rendering
     */
    public void bind() {
        if (!bound) {
            glUseProgram(programId);
            bound = true;
        }
    }
    
    /**
     * Unbind shader
     */
    public void unbind() {
        if (bound) {
            glUseProgram(0);
            bound = false;
        }
    }
    
    /**
     * Get uniform location (cached internally by OpenGL)
     */
    public int getUniformLocation(String name) {
        return glGetUniformLocation(programId, name);
    }
    
    // Uniform setters
    
    public void setUniform(String name, int value) {
        glUniform1i(getUniformLocation(name), value);
    }
    
    public void setUniform(String name, float value) {
        glUniform1f(getUniformLocation(name), value);
    }
    
    public void setUniform(String name, float x, float y) {
        glUniform2f(getUniformLocation(name), x, y);
    }
    
    public void setUniform(String name, float x, float y, float z) {
        glUniform3f(getUniformLocation(name), x, y, z);
    }
    
    public void setUniform(String name, float x, float y, float z, float w) {
        glUniform4f(getUniformLocation(name), x, y, z, w);
    }
    
    public void setUniform(String name, Vec3 vec) {
        glUniform3f(getUniformLocation(name), vec.x, vec.y, vec.z);
    }
    
    public void setUniform(String name, Mat4 matrix) {
        glUniformMatrix4fv(getUniformLocation(name), false, matrix.m);
    }
    
    // Integer vector uniforms
    public void setUniform2i(String name, int x, int y) {
        glUniform2i(getUniformLocation(name), x, y);
    }
    
    public void setUniform2i(int location, int x, int y) {
        glUniform2i(location, x, y);
    }
    
    public void setUniform(int location, int value) {
        glUniform1i(location, value);
    }
    
    public void setUniform(int location, float value) {
        glUniform1f(location, value);
    }
    
    public void setUniform(int location, float x, float y, float z) {
        glUniform3f(location, x, y, z);
    }
    
    public void setUniform(int location, Vec3 vec) {
        glUniform3f(location, vec.x, vec.y, vec.z);
    }
    
    public void setUniform(int location, float x, float y, float z, float w) {
        glUniform4f(location, x, y, z, w);
    }
    
    public void setUniform(int location, Mat4 matrix) {
        glUniformMatrix4fv(location, false, matrix.m);
    }
    
    /**
     * Clean up shader resources
     */
    public void cleanup() {
        unbind();
        if (programId != 0) {
            glDeleteProgram(programId);
            programId = 0;
        }
    }
    
    public int getProgramId() {
        return programId;
    }
}