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
        if (data == null || data.length == 0) {
            cleanup();
            return;
        }

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

        // ✅ AGGIORNATO: 15 float per vertice
        // pos(3) + uv(2) + ao(1) + faceIdx(1) + tileIndex(1) + skyLight(1) +
        // blockLight(3) + color(3)
        int floatsPerVertex = 15;
        int stride = floatsPerVertex * Float.BYTES;

        // layout(location=0) in vec3 aPos;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0L);

        // layout(location=1) in vec2 aUV;
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 3L * Float.BYTES);

        // layout(location=2) in float aAO;
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 5L * Float.BYTES);

        // layout(location=3) in float aFaceIdx;
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);

        // layout(location=4) in float aTileIndex;
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(4, 1, GL_FLOAT, false, stride, 7L * Float.BYTES);

        // layout(location=5) in float aSkyLight;
        glEnableVertexAttribArray(5);
        glVertexAttribPointer(5, 1, GL_FLOAT, false, stride, 8L * Float.BYTES);

        // layout(location=6) in vec3 aBlockLight; // Changed to size 3
        glEnableVertexAttribArray(6);
        glVertexAttribPointer(6, 3, GL_FLOAT, false, stride, 9L * Float.BYTES);

        // ✅ NUOVO: layout(location=7) in vec3 aColor;
        glEnableVertexAttribArray(7);
        glVertexAttribPointer(7, 3, GL_FLOAT, false, stride, 12L * Float.BYTES);

        vertexCount = data.length / floatsPerVertex;

        glBindVertexArray(0);
    }

    public void draw() {
        if (vertexCount == 0)
            return;

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
        if (vao != 0)
            glDeleteVertexArrays(vao);
        if (vbo != 0)
            glDeleteBuffers(vbo);
        vao = 0;
        vbo = 0;
        vertexCount = 0;
    }

    public int getVertexCount() {
        return vertexCount;
    }

    public long getEstimatedVboBytes() {
        return (long) vertexCount * 15L * Float.BYTES;
    }

    public boolean isTransparent() {
        return transparent;
    }

    public boolean isEmpty() {
        return vertexCount == 0;
    }
}
