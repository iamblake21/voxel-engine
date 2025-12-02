package engine.world;

import engine.world.gen.ChunkSnapshot;
import engine.world.gen.MeshBuilder;
import java.util.ArrayList;
import java.util.List; 

public class ChunkMeshTask {
    public final int chunkX;
    public final int chunkZ;
    public final ChunkSnapshot snapshot;
    
    public volatile MeshBuilder.MeshData meshData;
    public volatile boolean complete = false;
    
    public final List<Long> neighborsToPropagate = new ArrayList<>(); 
    
    public ChunkMeshTask(int x, int z, ChunkSnapshot snapshot) {
        this.chunkX = x;
        this.chunkZ = z;
        this.snapshot = snapshot;
    }
}