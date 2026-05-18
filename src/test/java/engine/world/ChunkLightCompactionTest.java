package engine.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Objects;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import engine.registry.Registries;
import engine.world.block.Block;
import engine.world.block.BlockProperties;
import engine.world.block.Blocks;

class ChunkLightCompactionTest {

    @BeforeAll
    static void registerBlocks() {
        if (!Registries.BLOCKS.contains("engine:air")) {
            Blocks.registerEngineBlocks();
        }
        if (!Registries.BLOCKS.contains("test:stone")) {
            Blocks.register("test:stone", new Block(BlockProperties.create().standardSolid().tile(1, 0)));
        }
    }

    @Test
    void skylightOnlyAirSectionsStaySparseAfterLightApply() {
        Chunk chunk = new Chunk(0, 0);
        chunk.setBlock(0, 0, 0, Blocks.get("test:stone").getNumericId());
        chunk.compactToSections();

        short[] light = new short[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        for (int y = ChunkSection.SIZE; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    light[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = 15;
                }
            }
        }

        chunk.applyLightData(light);

        assertEquals(1, allocatedSectionCount(chunk));
        assertEquals(15, chunk.getSkyLight(8, 200, 8));
    }

    @Test
    void flatLightExportPreservesImplicitSkylightDefaults() {
        Chunk chunk = new Chunk(0, 0);
        chunk.setBlock(0, 0, 0, Blocks.get("test:stone").getNumericId());
        chunk.compactToSections();

        short[] light = new short[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        for (int y = ChunkSection.SIZE; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    light[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = 15;
                }
            }
        }
        chunk.applyLightData(light);

        short[] flat = chunk.getLightData();

        assertEquals(15, flat[(200 * Chunk.SIZE + 8) * Chunk.SIZE + 8] & 0xF);
    }

    @Test
    void zeroFilledLegacySkySectionsStayImplicitDuringCompaction() {
        Chunk chunk = new Chunk(0, 0);
        chunk.setBlock(0, 0, 0, Blocks.get("test:stone").getNumericId());
        chunk.getLightData();

        chunk.compactToSections();

        assertEquals(1, allocatedSectionCount(chunk));
        assertEquals(15, chunk.getSkyLight(8, 200, 8));
    }

    @Test
    void nonDefaultLightInAirSectionStillAllocatesSection() {
        Chunk chunk = new Chunk(0, 0);
        chunk.setBlock(0, 0, 0, Blocks.get("test:stone").getNumericId());
        chunk.compactToSections();

        short[] light = new short[Chunk.SIZE * Chunk.HEIGHT * Chunk.SIZE];
        for (int y = ChunkSection.SIZE; y < Chunk.HEIGHT; y++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    light[(y * Chunk.SIZE + z) * Chunk.SIZE + x] = 15;
                }
            }
        }
        light[(80 * Chunk.SIZE + 8) * Chunk.SIZE + 8] = (short) ((5 << 4) | 15);

        chunk.applyLightData(light);

        assertEquals(2, allocatedSectionCount(chunk));
        assertEquals(5, chunk.getBlockLight(8, 80, 8));
    }

    private static long allocatedSectionCount(Chunk chunk) {
        return Arrays.stream(chunk.getSections()).filter(Objects::nonNull).count();
    }
}
