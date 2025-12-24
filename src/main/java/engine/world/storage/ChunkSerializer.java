package engine.world.storage;

import engine.world.Chunk;
import engine.world.item.nbt.NBTTagCompound;

public class ChunkSerializer {

    public static NBTTagCompound save(Chunk chunk, java.util.List<engine.entity.Entity> entities) {
        NBTTagCompound tag = new NBTTagCompound();

        tag.setInt("x", chunk.getX());
        tag.setInt("z", chunk.getZ());

        // Save Blocks
        int[] blocks = new int[16 * 256 * 16];
        short[] chunkData = chunk.getBlockData();
        for (int i = 0; i < chunkData.length; i++) {
            blocks[i] = chunkData[i];
        }
        tag.setRaw("Blocks", blocks);

        // Save Light
        short[] light = chunk.getLightData();
        // NBTIO supports int[] but not short[]. copy to int[] or byte[]?
        // Packed light is short.
        int[] lightInts = new int[light.length];
        for (int i = 0; i < light.length; i++)
            lightInts[i] = light[i];
        tag.setRaw("Light", lightInts);

        // Save Fluid
        byte[] fluid = chunk.getFluidData();
        tag.setByteArray("Fluid", fluid);

        // Save BlockEntities
        java.util.List<NBTTagCompound> beList = new java.util.ArrayList<>();
        for (engine.world.blockentity.BlockEntity be : chunk.getBlockEntities()) {
            NBTTagCompound beTag = be.save();
            System.out.println("[ChunkSerializer] Saving BlockEntity: " + be.getType().getRegistryId() +
                    " at (" + be.getX() + "," + be.getY() + "," + be.getZ() + ")");
            beList.add(beTag);
        }
        if (!beList.isEmpty()) {
            tag.setList("BlockEntities", beList);
            System.out.println("[ChunkSerializer] Saved " + beList.size() + " BlockEntities for chunk (" + chunk.getX()
                    + "," + chunk.getZ() + ")");
        }

        // Save Entities
        if (entities != null && !entities.isEmpty()) {
            java.util.List<NBTTagCompound> entityList = new java.util.ArrayList<>();
            for (engine.entity.Entity e : entities) {
                // Hybrid Robust Check:
                // 1. Explicitly exclude Player class (Safety net)
                // 2. Exclude non-persistent types (Architectural)
                if (e instanceof engine.entity.Player || !e.getType().isPersistent()) {
                    continue;
                }

                NBTTagCompound eTag = new NBTTagCompound();
                e.save(eTag);
                entityList.add(eTag);
            }
            if (!entityList.isEmpty()) {
                tag.setList("Entities", entityList);
            }
        }

        return tag;
    }

    /**
     * Loads chunk data.
     * 
     * @param chunk The chunk to populate
     * @param tag   The NBT data
     * @return List of entities loaded from this chunk (to be added to world)
     */
    public static java.util.List<engine.entity.Entity> loadInto(Chunk chunk, NBTTagCompound tag) {
        // Robustness Check: Verify coordinates match!
        if (tag.hasKey("x") && tag.hasKey("z")) {
            int savedX = tag.getInt("x");
            int savedZ = tag.getInt("z");
            if (savedX != chunk.getX() || savedZ != chunk.getZ()) {
                System.err.println("[ChunkSerializer] CRITICAL DATA MISMATCH! Requested Chunk ("
                        + chunk.getX() + "," + chunk.getZ() + ") but got data for ("
                        + savedX + "," + savedZ + "). Ignoring corrupted save data.");
                return new java.util.ArrayList<>(); // Return empty entities, do not load blocks
            }
        }

        // Load Blocks
        int[] blocks = (int[]) tag.getRaw("Blocks");
        if (blocks != null) {
            short[] chunkData = chunk.getBlockData();
            for (int i = 0; i < Math.min(blocks.length, chunkData.length); i++) {
                chunkData[i] = (short) blocks[i];
            }
        }

        // Load Light
        int[] lightInts = (int[]) tag.getRaw("Light");
        if (lightInts != null) {
            short[] light = chunk.getLightData();
            for (int i = 0; i < Math.min(lightInts.length, light.length); i++) {
                light[i] = (short) lightInts[i];
            }
        }

        // Load Fluid
        byte[] fluid = tag.getByteArray("Fluid");
        if (fluid.length > 0) {
            chunk.setFluidData(fluid);
        }

        // Set loaded chunks to FEATURES phase.
        // FEATURES means features (structures, entities) are already generated.
        // This skips ensureFeatures() since it checks phase >= FEATURES.
        // The chunk will proceed to LIGHT_DONE → MESH_DONE.
        chunk.setPhase(Chunk.Phase.FEATURES);

        // Load BlockEntities
        if (tag.hasKey("BlockEntities")) {
            @SuppressWarnings("unchecked")
            java.util.List<NBTTagCompound> list = (java.util.List<NBTTagCompound>) tag.getList("BlockEntities");
            System.out.println("[ChunkSerializer] Loading " + list.size() + " BlockEntities for chunk (" + chunk.getX()
                    + "," + chunk.getZ() + ")");
            for (NBTTagCompound beTag : list) {
                // Create BE from ID
                if (beTag.hasKey("id")) {
                    String id = beTag.getString("id");
                    int x = beTag.getInt("x");
                    int y = beTag.getInt("y");
                    int z = beTag.getInt("z");
                    System.out.println(
                            "[ChunkSerializer] Loading BlockEntity '" + id + "' at (" + x + "," + y + "," + z + ")");

                    engine.world.blockentity.BlockEntityType<?> type = engine.registry.Registries.BLOCK_ENTITY_TYPES
                            .get(id).orElse(null);
                    if (type != null) {
                        engine.world.blockentity.BlockEntity be = type.create(new engine.world.BlockPos(x, y, z));
                        if (be != null) {
                            be.load(beTag);
                            int localX = x & 15; // Correct modulo for negative coords
                            int localZ = z & 15;
                            chunk.setBlockEntity(localX, y, localZ, be);
                            System.out.println("[ChunkSerializer] Created BlockEntity at local (" + localX + "," + y
                                    + "," + localZ + ")");
                        } else {
                            System.err.println("[ChunkSerializer] Failed to create BlockEntity of type: " + id);
                        }
                    } else {
                        System.err.println("[ChunkSerializer] Unknown BlockEntity type: " + id);
                    }
                }
            }
        } else {
            System.out
                    .println("[ChunkSerializer] No BlockEntities in chunk (" + chunk.getX() + "," + chunk.getZ() + ")");
        }

        // Load Entities
        java.util.List<engine.entity.Entity> loadedEntities = new java.util.ArrayList<>();
        if (tag.hasKey("Entities")) {
            @SuppressWarnings("unchecked")
            java.util.List<NBTTagCompound> list = (java.util.List<NBTTagCompound>) tag.getList("Entities");
            for (NBTTagCompound eTag : list) {
                if (eTag.hasKey("id")) {
                    String id = eTag.getString("id");

                    // Explicitly skip known player IDs to clean up old saves (Ghost Players)
                    if (id.equals("game:player")) {
                        continue;
                    }

                    engine.registry.Registries.ENTITY_TYPES.get(id).ifPresent(type -> {
                        // Robust check: Do not load non-persistent entities
                        if (!type.isPersistent()) {
                            return;
                        }

                        engine.entity.Entity e = type.create();
                        // Double safety: Check class type
                        if (e instanceof engine.entity.Player) {
                            return;
                        }

                        if (e != null) {
                            e.load(eTag);
                            loadedEntities.add(e);
                        }
                    });
                }
            }
        }

        // Recalculate heightmap?
        // Or save it. Let's recalculate for robustness.
        // Iterate x, z -> find top block
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 255; y >= 0; y--) {
                    if (!engine.world.block.Blocks.get(chunk.getBlock(x, y, z)).isTransparent()) {
                        chunk.setHeight(x, z, y);
                        break;
                    }
                }
            }
        }

        chunk.markSaved(); // Chunk matches disk state perfectly now.

        return loadedEntities;
    }
}
