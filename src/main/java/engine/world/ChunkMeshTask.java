package engine.world;

import engine.world.gen.ChunkSnapshot;
import engine.world.gen.MeshBuilder;
import java.util.ArrayList;
import java.util.List;

public class ChunkMeshTask {
    public final int chunkX;
    public final int chunkZ;
    public final ChunkSnapshot snapshot;
    public final int sectionY;
    public final int batchId;

    public volatile MeshBuilder.MeshData meshData;
    public volatile boolean complete = false;

    public final List<Long> neighborsToPropagate = new ArrayList<>();

    public ChunkMeshTask(int x, int z, ChunkSnapshot snapshot) {
        this(x, z, snapshot, -1, 0);
    }

    public ChunkMeshTask(int x, int z, ChunkSnapshot snapshot, int sectionY, int batchId) {
        this.chunkX = x;
        this.chunkZ = z;
        this.snapshot = snapshot;
        this.sectionY = sectionY;
        this.batchId = batchId;
    }
}
