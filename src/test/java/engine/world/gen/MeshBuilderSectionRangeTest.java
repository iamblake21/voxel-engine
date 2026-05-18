package engine.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import engine.registry.Registries;
import engine.world.block.Block;
import engine.world.block.BlockProperties;
import engine.world.block.Blocks;

class MeshBuilderSectionRangeTest {

    private static final int FLOATS_PER_VERTEX = 15;
    private static int stoneId;

    @BeforeAll
    static void registerBlocks() {
        if (!Registries.BLOCKS.contains("engine:air")) {
            Blocks.registerEngineBlocks();
        }
        if (!Registries.BLOCKS.contains("test:stone")) {
            Blocks.register("test:stone", new Block(BlockProperties.create().standardSolid().tile(1, 0)));
        }
        stoneId = Blocks.get("test:stone").getNumericId();
    }

    @Test
    void buildMeshRangeOnlyEmitsBlocksInsideVerticalSlice() {
        MeshBuilder builder = new MeshBuilder(16, 384);
        TwoBlockChunkData chunk = new TwoBlockChunkData();

        MeshBuilder.MeshData mesh = builder.buildMeshRange(chunk, chunk, 0, 16, 32);

        int vertices = mesh.solidVertices.length / FLOATS_PER_VERTEX;
        assertEquals(36, vertices);
    }

    private static final class TwoBlockChunkData implements MeshBuilder.ChunkData, MeshBuilder.WorldAccess {
        @Override
        public int getBlock(int x, int y, int z) {
            if ((x == 4 && y == 4 && z == 4) || (x == 4 && y == 20 && z == 4)) {
                return stoneId;
            }
            return Blocks.AIR().getNumericId();
        }

        @Override
        public int getWorldX() {
            return 0;
        }

        @Override
        public int getWorldZ() {
            return 0;
        }

        @Override
        public int getSkyLight(int x, int y, int z) {
            return 15;
        }

        @Override
        public int peekBlock(int worldX, int worldY, int worldZ) {
            if ((worldX == 4 && worldY == 4 && worldZ == 4)
                    || (worldX == 4 && worldY == 20 && worldZ == 4)) {
                return stoneId;
            }
            return Blocks.AIR().getNumericId();
        }

        @Override
        public int peekSkyLight(int worldX, int worldY, int worldZ) {
            return 15;
        }

        @Override
        public int peekBlockLight(int worldX, int worldY, int worldZ) {
            return 0;
        }
    }
}
