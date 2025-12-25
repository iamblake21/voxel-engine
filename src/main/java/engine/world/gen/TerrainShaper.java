package engine.world.gen;

/**
 * Calculates terrain height and squashing using nested splines.
 * Matches Minecraft 1.18+ generation style.
 */
public class TerrainShaper {

    private final NoiseRouter noiseRouter;
    private final float seaLevel;

    private final NestedTerrainSpline offsetSpline;
    private final NestedTerrainSpline factorSpline;

    public TerrainShaper(NoiseRouter noiseRouter, float seaLevel) {
        this.noiseRouter = noiseRouter;
        this.seaLevel = seaLevel;

        this.offsetSpline = buildOffsetSpline();
        this.factorSpline = buildFactorSpline();
    }

    /**
     * Get terrain parameters at a position.
     * Returns [targetHeight, squashingFactor]
     */
    public float[] getTerrainParams(int x, int z) {
        float c = noiseRouter.getContinentalness(x, z);
        float e = noiseRouter.getErosion(x, z);
        float w = noiseRouter.getWeirdness(x, z);

        float height = offsetSpline.apply(c, e, w);
        float squashing = factorSpline.apply(c, e, w);

        return new float[] { height, squashing };
    }

    private NestedTerrainSpline buildOffsetSpline() {
        // Top level: Continentalness
        NestedTerrainSpline s = new NestedTerrainSpline(NestedTerrainSpline.CONTINENTALNESS);

        // === OCEAN ===
        s.addPoint(-1.1f, seaLevel - 40, 0f);
        s.addPoint(-1.0f, seaLevel - 20, 0f); // Deep ocean rising
        s.addPoint(-0.4f, seaLevel - 5, 0f); // Shallow ocean approach

        // === COAST TRANSITION ===
        // Smooth transition from sea level to land
        s.addPoint(-0.15f, seaLevel + 3, 0f); // Beach level
        s.addPoint(-0.1f, seaLevel + 10, 0f); // Gentle rise inland

        // === INLAND ===
        // From -0.1 to 1.0, we are fully inland.
        // We nest Erosion here to allow biomes to emerge.

        // Start of inland proper
        s.addPoint(-0.05f, buildInlandSpline(), 0f);

        // Deep inland
        s.addPoint(1.0f, buildInlandSpline(), 0f);

        return s;
    }

    private NestedTerrainSpline buildInlandSpline() {
        // Second level: Erosion
        // -1 (High Erosion/Mountainous) -> 1 (Flat/Low Erosion)

        NestedTerrainSpline s = new NestedTerrainSpline(NestedTerrainSpline.EROSION);

        // E = -1.0: EXTREME MOUNTAINS (Jagged peaks)
        s.addPoint(-1.0f, buildMountainWeirdness(), 0f);

        // E = -0.65: MOUNTAINS (Rarer)
        s.addPoint(-0.65f, buildMountainWeirdness(), 0f);

        // E = -0.4: HIGH HILLS
        // BOOSTED HEIGHTS slightly to fix "empty middle ground" feeling
        s.addPoint(-0.4f, buildHillWeirdness(), 0f);

        // E = 0.0: ROLLING HILLS
        // BOOSTED HEIGHTS slightly
        s.addPoint(0.0f, buildRollingWeirdness(), 0f);

        // E = 0.4: PLAINS START
        s.addPoint(0.4f, buildFlatWeirdness(), 0f);

        // E = 1.0: VERY FLAT
        s.addPoint(1.0f, seaLevel + 5, 0f);

        return s;
    }

    private NestedTerrainSpline buildMountainWeirdness() {
        // High variation
        NestedTerrainSpline s = new NestedTerrainSpline(NestedTerrainSpline.WEIRDNESS);
        s.addPoint(-1.0f, seaLevel + 30, 0f); // Valleys
        s.addPoint(0.0f, seaLevel + 110, 0f); // Slopes (Steeper)
        s.addPoint(1.0f, seaLevel + 220, 0f); // Peaks (Higher)
        return s;
    }

    private NestedTerrainSpline buildHillWeirdness() {
        NestedTerrainSpline s = new NestedTerrainSpline(NestedTerrainSpline.WEIRDNESS);
        // Boosted: Was 8, 25, 50. Now higher to bridge gaps.
        s.addPoint(-1.0f, seaLevel + 15, 0f);
        s.addPoint(0.0f, seaLevel + 40, 0f);
        s.addPoint(1.0f, seaLevel + 70, 0f);
        return s;
    }

    private NestedTerrainSpline buildRollingWeirdness() {
        // Boosted: Was 5, 12, 20
        NestedTerrainSpline s = new NestedTerrainSpline(NestedTerrainSpline.WEIRDNESS);
        s.addPoint(-1.0f, seaLevel + 8, 0f);
        s.addPoint(0.0f, seaLevel + 20, 0f);
        s.addPoint(1.0f, seaLevel + 40, 0f);
        return s;
    }

    private NestedTerrainSpline buildFlatWeirdness() {
        // Plains - subtle variation
        NestedTerrainSpline s = new NestedTerrainSpline(NestedTerrainSpline.WEIRDNESS);
        s.addPoint(-1.0f, seaLevel + 2, 0f);
        s.addPoint(-0.5f, seaLevel + 6, 0f);
        s.addPoint(0.5f, seaLevel + 4, 0f);
        s.addPoint(1.0f, seaLevel + 10, 0f);
        return s;
    }

    private NestedTerrainSpline buildFactorSpline() {
        // Squashing: High = Flat, Low = Crazy
        NestedTerrainSpline s = new NestedTerrainSpline(NestedTerrainSpline.CONTINENTALNESS);

        // Ocean: Smooth
        s.addPoint(-1.1f, 2.8f, 0f);
        s.addPoint(-0.4f, 2.5f, 0f);

        // Coast: Transition
        s.addPoint(-0.15f, 2.0f, 0f);

        // Inland: Varied by erosion
        s.addPoint(-0.05f, buildInlandFactor(), 0f);
        s.addPoint(1.0f, buildInlandFactor(), 0f);

        return s;
    }

    private NestedTerrainSpline buildInlandFactor() {
        NestedTerrainSpline s = new NestedTerrainSpline(NestedTerrainSpline.EROSION);

        // === GLOBAL SQUASHING REDUCTION for TERRACING ===
        // To get "Minecraft feel", we need lower squashing factors everywhere.
        // This allows the 3D density noise to create steps/overhangs.

        // Mountains: Low squashing
        s.addPoint(-1.0f, 0.45f, 0f);
        s.addPoint(-0.65f, 0.6f, 0f);

        // Hills: SIGNIFICANTLY REDUCED SQUASHING
        // Was 1.0 -> 0.75. Lower = More verticality/steps within the hill.
        s.addPoint(-0.4f, 0.75f, 0f);

        // Rolling Hills:
        // Was 1.3 -> 0.95. Enables terracing on gentle slopes.
        s.addPoint(0.0f, 0.95f, 0f);

        // Plains:
        // Was 2.8 -> 1.8. Even plains should have small ledges, not be smooth sheets.
        s.addPoint(0.4f, 1.8f, 0f);
        s.addPoint(1.0f, 2.0f, 0f);

        return s;
    }

    public NoiseRouter getNoiseRouter() {
        return noiseRouter;
    }

    public float getSeaLevel() {
        return seaLevel;
    }
}