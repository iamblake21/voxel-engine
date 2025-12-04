package engine.world;

import engine.world.gen.ChunkSnapshot;
import java.util.ArrayList;
import java.util.List;

public class LightPropagationTask {
    public final int chunkX;
    public final int chunkZ;
    public final ChunkSnapshot snapshot;

    public volatile boolean complete = false;

    public final List<Long> neighborsToPropagate = new ArrayList<>();

    public LightPropagationTask(int x, int z, ChunkSnapshot snapshot) {
        this.chunkX = x;
        this.chunkZ = z;
        this.snapshot = snapshot;
    }
}
