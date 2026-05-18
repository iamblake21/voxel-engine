package engine.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import engine.registry.Registries;
import engine.world.Chunk;
import engine.world.ChunkSection;
import engine.world.block.Block;
import engine.world.block.BlockProperties;
import engine.world.block.Blocks;

class ChunkSnapshotLightDefaultsTest {

    @BeforeAll
    static void registerBlocks() {
        if (!Registries.BLOCKS.contains("engine:air")) {
            Blocks.registerEngineBlocks();
        }
        if (!Registries.BLOCKS.contains("test:snapshot_stone")) {
            Blocks.register("test:snapshot_stone", new Block(BlockProperties.create().standardSolid().tile(1, 0)));
        }
    }

    @Test
    void centerSnapshotUsesImplicitSkylightForCompactedAirSections() {
        Chunk center = new Chunk(0, 0);
        center.setBlock(0, 0, 0, Blocks.get("test:snapshot_stone").getNumericId());
        center.compactToSections();
        center.applyLightData(skylightAboveBottomSection());

        Chunk[][] neighbors = new Chunk[3][3];
        neighbors[1][1] = center;

        ChunkSnapshot snapshot = new ChunkSnapshot(0, 0, neighbors, Chunk.SIZE, Chunk.HEIGHT, null);

        assertEquals(15, center.getSkyLight(8, 200, 8));
        assertEquals(15, snapshot.getSkyLight(8, 200, 8));
        assertEquals(15, snapshot.peekSkyLight(8, 200, 8));
    }

    private short[] skylightAboveBottomSection() {
        short[] light = new short[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        for (int y = ChunkSection.SIZE; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    light[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = 15;
                }
            }
        }
        return light;
    }
}
