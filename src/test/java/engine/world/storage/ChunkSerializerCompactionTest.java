package engine.world.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import engine.registry.Registries;
import engine.world.Chunk;
import engine.world.item.nbt.NBTTagCompound;
import engine.world.block.Block;
import engine.world.block.BlockProperties;
import engine.world.block.Blocks;

class ChunkSerializerCompactionTest {

    @BeforeAll
    static void registerBlocks() {
        if (!Registries.BLOCKS.contains("engine:air")) {
            Blocks.registerEngineBlocks();
        }
        if (!Registries.BLOCKS.contains("test:serialized_stone")) {
            Blocks.register("test:serialized_stone", new Block(BlockProperties.create().standardSolid().tile(1, 0)));
        }
    }

    @Test
    void loadedChunksAreCompactedBeforeEnteringWorldPipeline() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInt("x", 0);
        tag.setInt("z", 0);

        int[] blocks = new int[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        blocks[0] = Blocks.get("test:serialized_stone").getNumericId();
        tag.setRaw("Blocks", blocks);

        int[] light = new int[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        for (int y = 1; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    light[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = 15;
                }
            }
        }
        tag.setRaw("Light", light);

        Chunk chunk = new Chunk(0, 0);
        ChunkSerializer.loadInto(chunk, tag);

        assertTrue(chunk.isCompacted());
    }
}
