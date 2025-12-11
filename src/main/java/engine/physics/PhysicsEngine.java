package engine.physics;

import engine.core.Config;
import engine.entity.Entity;
import engine.world.World;
import engine.world.block.Blocks;

public class PhysicsEngine {
    
    private final Config config;
    
    public PhysicsEngine(Config config) {
        this.config = config;
    }
    
    /**
     * Applica la fisica (Gravità + Acqua + Collisioni)
     * Logica portata direttamente dal Player originale.
     */
    public void processEntity(Entity entity, World world, float deltaTime) {
        if (entity.isStatic()) return;

        // 1. Gravità (Se non vola)
        if (entity.hasGravity()) {
            // Formula originale: vy -= gravity * dt
            entity.setVelY(entity.getVelY() - config.gravity * deltaTime);
        }

        // 2. Fisica Acqua (Copiata 1:1 dal tuo vecchio codice)
        float hw = entity.getWidth() * 0.5f;
        float hh = entity.getHeight();
        
        boolean inWater = checkInWater(entity, world);
        entity.setInWater(inWater);
        
        // Controllo testa sott'acqua
        boolean headInWater = isBlockLiquid(world, entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ());
        
        // Logica specifica galleggiamento/attrito acqua
        if (inWater) {
            float imm = getImmersionFraction(entity, world);
            float buoy = headInWater ? 11f : 9f;
            float currentVy = entity.getVelY();
            float antiSink = (currentVy < 0 ? (3.5f + 2.5f * imm) : 0f);
            
            entity.setVelY(currentVy + (buoy + antiSink) * deltaTime);

            float linDrag = 3.5f * imm;
            entity.setVelX(entity.getVelX() - entity.getVelX() * linDrag * deltaTime);
            entity.setVelZ(entity.getVelZ() - entity.getVelZ() * linDrag * deltaTime);

            float vertDrag = (currentVy < 0 ? 1.4f : 0.6f) * imm;
            entity.setVelY(entity.getVelY() - entity.getVelY() * vertDrag * deltaTime);

            // Clamp
            if (entity.getVelY() < -3.0f) entity.setVelY(-3.0f);
            
        } else if (entity.hasGravity()) {
            // Terminal velocity in aria
            if (entity.getVelY() < -50f) entity.setVelY(-50f);
        }

        // 3. Risoluzione Movimento (Collisioni)
        resolveMovement(entity, world, deltaTime, hw, hh);
        
        // NOTA: Non applichiamo attrito ARIA qui perché il tuo vecchio codice 
        // gestiva il movimento a terra settando vx/vz direttamente dall'input.
        // Questo preserva lo scatto immediato del movimento.
    }

    private void resolveMovement(Entity e, World world, float dt, float hw, float hh) {
        float x = e.getX();
        float y = e.getY();
        float z = e.getZ();
        float vx = e.getVelX();
        float vy = e.getVelY();
        float vz = e.getVelZ();

        // X movement
        if (collides(world, x + vx * dt, y, z, hw, hh)) {
            // Smorzamento leggero contro i muri (0.8f) o stop (0f)
            // Il tuo codice originale usava 0.8f poi commentato. Mettiamo 0 per non "appiccicarsi".
            e.setVelX(0); 
            // Loop while per avvicinarsi al muro (Pixel Perfect)
             while (!collides(world, x + Math.signum(vx) * 0.001f, y, z, hw, hh)) {
                x += Math.signum(vx) * 0.001f;
            }
        } else {
            x += vx * dt;
        }

        // Y movement
        if (collides(world, x, y + vy * dt, z, hw, hh)) {
            if (vy < 0) e.setOnGround(true);
            e.setVelY(0);
            while (!collides(world, x, y + Math.signum(vy) * 0.001f, z, hw, hh)) {
                y += Math.signum(vy) * 0.001f;
            }
        } else {
            y += vy * dt;
            e.setOnGround(false);
        }

        // Z movement
        if (collides(world, x, y, z + vz * dt, hw, hh)) {
            e.setVelZ(0);
            while (!collides(world, x, y, z + Math.signum(vz) * 0.001f, hw, hh)) {
                z += Math.signum(vz) * 0.001f;
            }
        } else {
            z += vz * dt;
        }
        
        e.setPosition(x, y, z);
    }

    // [Image of AABB collision detection logic]
    private boolean collides(World world, float px, float py, float pz, float hw, float hh) {
        // Parametri identici al tuo codice originale
        int minX = (int) Math.floor(px - hw);
        int maxX = (int) Math.floor(px + hw);
        int minY = (int) Math.floor(py - 0.1f); // Quel -0.1f è importante per i piedi!
        int maxY = (int) Math.floor(py + hh);
        int minZ = (int) Math.floor(pz - hw);
        int maxZ = (int) Math.floor(pz + hw);

        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    int blockId = world.getBlock(bx, by, bz);
                    if (Blocks.isSolid(blockId)) return true;
                }
            }
        }
        return false;
    }
    
    // --- Helper Acqua (Invariati) ---
    private boolean checkInWater(Entity e, World world) {
        float hw = e.getWidth() * 0.5f;
        float hh = e.getHeight();
        return isAreaLiquid(world, e.getX(), e.getY(), e.getZ(), hw, hh);
    }

    private boolean isAreaLiquid(World world, float px, float py, float pz, float hw, float hh) {
        int minX = (int) Math.floor(px - hw);
        int maxX = (int) Math.floor(px + hw);
        int minY = (int) Math.floor(py - 0.1f);
        int maxY = (int) Math.floor(py + hh);
        int minZ = (int) Math.floor(pz - hw);
        int maxZ = (int) Math.floor(pz + hw);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (Blocks.isLiquid(world.getBlock(bx, by, bz))) return true;
                }
            }
        }
        return false;
    }

    private boolean isBlockLiquid(World world, float x, float y, float z) {
        return Blocks.isLiquid(world.getBlock((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z)));
    }

    private float getImmersionFraction(Entity e, World world) {
        float y = e.getY();
        float h = e.getHeight();
        float[] offsets = { 0.1f, 0.6f, 1.0f, 1.3f, 1.55f }; // I tuoi offset originali
        int count = 0;
        for(float off : offsets) {
            // check bound
            if (off > h) break;
            if (isBlockLiquid(world, e.getX(), y + off, e.getZ())) count++;
        }
        return (float)count / offsets.length;
    }
}