package engine.world.gen;

/**
 * Calculates 3D density for each block position.
 * 
 * This is the core of Minecraft-style terrain:
 * - Positive density = solid (stone)
 * - Negative density = air
 * - The surface is where density crosses zero
 * 
 * The density is influenced by:
 * 1. Base terrain height (from splines) - creates height bias
 * 2. Squashing factor - controls how flat/crazy the terrain is
 * 3. 3D noise - adds detail, overhangs, weird shapes
 * 4. Noise Caves - Cheese, Spaghetti, Noodle carve out air
 */
public class DensityFunction {

    private final TerrainShaper terrainShaper;
    private final NoiseRouter noiseRouter;
    private final float seaLevel;

    public DensityFunction(TerrainShaper terrainShaper) {
        this.terrainShaper = terrainShaper;
        this.noiseRouter = terrainShaper.getNoiseRouter();
        this.seaLevel = terrainShaper.getSeaLevel();
    }

    /**
     * Calculate density at a 3D position.
     * 
     * @return positive = solid, negative = air
     */
    public float getDensity(int x, int y, int z) {
        // === BASE TERRAIN ===
        float[] params = terrainShaper.getTerrainParams(x, z);
        float targetHeight = params[0];
        float squashing = params[1];

        float heightBias = (targetHeight - y) / 16f;
        heightBias *= squashing;

        float noise3D = noiseRouter.getDensity3D(x, y, z);
        float noiseInfluence = 1f / (squashing * squashing);
        noise3D *= noiseInfluence * 0.7f;

        float density = heightBias + noise3D;

        // === CAVE CARVING (NOISE CAVES) ===
        // We modify the density to create caves.
        // If cave function says "CAVE", we reduce density significantly.

        if (isNoiseCave(x, y, z)) {
            // Subtract enough to turn solid into air, but keep gradient for smooth mesh if
            // needed
            density -= 5.0f;
        }

        // === DEPTH GUARANTEE ===
        if (y < 5) {
            density += (5 - y) * 2.0f;
        }

        // === SKY GUARANTEE ===
        if (y > 250) {
            density -= (y - 250) * 0.5f;
        }

        return density;
    }

    /**
     * Determines if a point is within a noise cave (Cheese, Spaghetti, Noodle).
     */
    private boolean isNoiseCave(int x, int y, int z) {
        // 1. CHEESE CAVES (Large caverns)
        // High frequency 3D noise with high threshold
        float cheese = noiseRouter.getCheeseNoise(x, y, z);
        // Cheese caverns usually deep underground or mountains
        float cheeseThreshold = 0.6f;
        if (y > seaLevel) {
            // Harder to have cheese caves near surface
            cheeseThreshold += (y - seaLevel) * 0.005f;
        }
        if (cheese > cheeseThreshold)
            return true;

        // 2. SPAGHETTI CAVES (Wide tunnels)
        // Ridges where two noises are close to 0
        float spagA = noiseRouter.getSpaghettiNoiseA(x, y, z);
        float spagB = noiseRouter.getSpaghettiNoiseB(x, y, z);
        // Thickness logic
        float spagThickness = 0.08f;
        // Taper spaghetti at surface
        if (y > seaLevel) {
            spagThickness *= Math.max(0, 1f - (y - seaLevel) / 30f);
        }

        // Standard ridge noise check
        // scaled by y sometimes
        if (Math.abs(spagA) < spagThickness && Math.abs(spagB) < spagThickness) {
            // Map mappedValue = unapply(spagA) ...
            // Simple version:
            return true;
        }

        // 3. NOODLE CAVES (Thin, twisty tunnels)
        float noodleA = noiseRouter.getNoodleNoiseA(x, y, z);
        float noodleB = noiseRouter.getNoodleNoiseB(x, y, z);
        float noodleThickness = 0.03f;
        if (Math.abs(noodleA) < noodleThickness && Math.abs(noodleB) < noodleThickness) {
            return true;
        }

        return false;
    }

    /**
     * Check if a position should be solid.
     */
    public boolean isSolid(int x, int y, int z) {
        return getDensity(x, y, z) > 0;
    }

    /**
     * Check if position is a cave (Legacy support / Post-process check)
     */
    public boolean isCave(int x, int y, int z) {
        // Used by Carver in TerrainGenerator (Pass 2)
        // Since we moved caves to Pass 1 (getDensity), specific carving might be
        // redundant
        // OR we can use this for extra caves that didn't affect density (rare)
        // For now, delegate to isNoiseCave
        return isNoiseCave(x, y, z);
    }
}