package engine.rendering;

import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Mesh {
    private int vao, vbo, vertexCount;
    private boolean transparent;
    
    public Mesh() {
        this.vao = 0;
        this.vbo = 0;
        this.vertexCount = 0;
    }
    
    public void upload(float[] data, boolean transparent) {
        this.transparent = transparent;
        if (vao == 0) {
            vao = glGenVertexArrays();
            vbo = glGenBuffers();
        }
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        FloatBuffer buffer = memAllocFloat(data.length);
        buffer.put(data).flip();
        glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        memFree(buffer);
        int stride = 7 * Float.BYTES;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3 * Float.BYTES);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5 * Float.BYTES);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6 * Float.BYTES);
        vertexCount = data.length / 7;
        glBindVertexArray(0);
    }
    
    public void draw() {
        if (vertexCount == 0) return;
        if (transparent) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        } else {
            glDisable(GL_BLEND);
        }
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }
    
    public void cleanup() {
        if (vao != 0) glDeleteVertexArrays(vao);
        if (vbo != 0) glDeleteBuffers(vbo);
        vao = 0;
        vbo = 0;
        vertexCount = 0;
    }
    
    public int getVertexCount() { return vertexCount; }
    public boolean isTransparent() { return transparent; }
    public boolean isEmpty() { return vertexCount == 0; }
}