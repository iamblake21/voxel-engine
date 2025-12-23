package engine.world.storage;

import engine.core.Config;
import engine.entity.Entity;
import engine.entity.EntityManager;
import engine.entity.Player;
import engine.world.Chunk;
import engine.world.World;
import engine.world.item.nbt.NBTIO;
import engine.world.item.nbt.NBTTagCompound;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WorldStorage {

    private final File savesDir;
    private File worldDir;

    public WorldStorage(File runDir) {
        this.savesDir = new File(runDir, "saves");
        if (!savesDir.exists()) {
            savesDir.mkdirs();
        }
    }

    /**
     * Initializes storage for a specific world.
     */
    public void prepareWorld(String worldName) {
        this.worldDir = new File(savesDir, worldName);
        if (!worldDir.exists()) {
            worldDir.mkdirs();
        }
    }

    public File getWorldDir() {
        return worldDir;
    }

    /**
     * Saves the entire world (level.dat + all chunks).
     */
    public void saveWorld(World world, String worldName) {
        prepareWorld(worldName);

        System.out.println("[WorldStorage] Saving world '" + worldName + "'...");

        // 1. Save level.dat
        saveLevelData(world);

        // 2. Save Chunks
        // Group entities by chunk first? Or just iterate chunks and filter entities?
        // Querying entities for each chunk is slow (Chunks * Entities).
        // Since we don't have spatial hash for entities yet, let's just do the slow way
        // or
        // iterate entities once andbucket them.

        // Bucket entities
        EntityManager entityManager = world.getEntityManager();
        // We need access to EntityManager. World has it private but maybe has getter?
        // Step 50 view showed `private EntityManager entityManager;`.
        // We need a getter in World.java. Assuming we add/have `getEntityManager()`.

        // Assuming we can get chunks
        java.util.Collection<Chunk> chunks = world.getChunks(); // Need getter in World

        for (Chunk chunk : chunks) {
            // Filter entities in this chunk
            List<Entity> chunkEntities = new ArrayList<>();
            if (entityManager != null) {
                for (Entity e : entityManager.getEntities()) {
                    if (e.isRemoved())
                        continue;
                    // Don't save player here, saved in level.dat
                    if (e instanceof Player)
                        continue;

                    int cx = (int) Math.floor(e.getX() / 16.0f);
                    int cz = (int) Math.floor(e.getZ() / 16.0f);

                    if (cx == chunk.getX() && cz == chunk.getZ()) {
                        chunkEntities.add(e);
                    }
                }
            }

            saveChunk(chunk, chunkEntities);
        }

        System.out.println("[WorldStorage] World saved.");
    }

    public void saveChunk(Chunk chunk, List<Entity> entities) {
        try {
            NBTTagCompound tag = ChunkSerializer.save(chunk, entities);

            RegionFile region = RegionFileCache.getRegionFile(worldDir, chunk.getX(), chunk.getZ());
            try (java.io.DataOutputStream dos = region.getChunkDataOutputStream(chunk.getX() & 31, chunk.getZ() & 31)) {
                if (dos != null) {
                    NBTIO.write(tag, dos);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Chunk loadChunk(World world, int x, int z) {
        try {
            RegionFile region = RegionFileCache.getRegionFile(worldDir, x, z);
            try (java.io.DataInputStream dis = region.getChunkDataInputStream(x & 31, z & 31)) {
                if (dis == null)
                    return null;

                NBTTagCompound tag = NBTIO.read(dis);

                // Create Chunk
                Chunk chunk = new Chunk(x, z);

                // Load data into chunk
                List<Entity> loadedEntities = ChunkSerializer.loadInto(chunk, tag);

                // Add entities to world
                EntityManager em = world.getEntityManager();
                if (em != null) {
                    for (Entity e : loadedEntities) {
                        e.setPosition(e.getX(), e.getY(), e.getZ()); // Ensure position is set
                        em.addEntity(e);
                    }
                }

                return chunk;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== LEVEL DATA ====================

    public void saveLevelData(World world) {
        NBTTagCompound level = new NBTTagCompound();

        // Metadata
        level.setLong("Seed", world.getSeed());
        level.setFloat("Time", world.getTime());
        level.setFloat("DayTime", world.getDayTime());

        // Player
        Player player = world.getPlayer(); // Need getter
        if (player != null) {
            System.out.println(">>> [DEBUG_SAVE] Saving Player: " + player.getTypeId() + " Pos: " + player.getX() + ","
                    + player.getY());
            NBTTagCompound playerTag = new NBTTagCompound();
            player.save(playerTag);
            level.setTag("Player", playerTag);
        } else {
            System.out.println(">>> [DEBUG_SAVE] CRITICAL: Player is NULL in World! Cannot save inventory/pos.");
        }

        try {
            File levelFile = new File(worldDir, "level.dat");
            NBTIO.writeCompressed(level, levelFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public NBTTagCompound loadLevelData() {
        File levelFile = new File(worldDir, "level.dat");
        if (!levelFile.exists())
            return null;

        try {
            return NBTIO.readCompressed(levelFile);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
