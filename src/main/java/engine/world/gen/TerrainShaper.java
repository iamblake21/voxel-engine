package engine.world.gen;

/**
 * Calculates terrain height and squashing using nested splines.
 * 
 * Like Minecraft 1.18+:
 * - Continentalness spline points, each with its own Erosion sub-spline
 * - Erosion spline points, each with its own PV sub-spline
 * - Returns: target height and squashing factor
 * 
 * Squashing factor controls how "fractured" the 3D noise appears:
 * - High squashing = flat, smooth terrain
 * - Low squashing = crazy overhangs and weird shapes
 */
public class TerrainShaper {
    
    private final NoiseRouter noiseRouter;
    private final float seaLevel;
    
    public TerrainShaper(NoiseRouter noiseRouter, float seaLevel) {
        this.noiseRouter = noiseRouter;
        this.seaLevel = seaLevel;
    }
    
    /**
     * Get terrain parameters at a position.
     * Returns [targetHeight, squashingFactor]
     */
    public float[] getTerrainParams(int x, int z) {
        float c = noiseRouter.getContinentalness(x, z);  // -1 to 1
        float e = noiseRouter.getErosion(x, z);          // -1 to 1
        float pv = noiseRouter.getPeaks(x, z);           // -1 to 1
        
        // Sample the nested spline system
        return sampleNestedSpline(c, e, pv);
    }
    
    /**
     * Nested spline system.
     * 
     * Structure:
     * Continentalness -> [Erosion -> [PV -> height]]
     * 
     * Each continentalness value has different erosion behavior,
     * and each erosion value has different PV behavior.
     */
    private float[] sampleNestedSpline(float c, float e, float pv) {
        // === CONTINENTALNESS CONTROLS BASE TERRAIN TYPE ===
        
        float height;
        float squashing;
        
        if (c < -0.6f) {
            // === DEEP OCEAN ===
            // Flat ocean floor, high squashing (no weird shapes)
            height = lerp(30, 40, (c + 1f) / 0.4f);
            squashing = 1.5f;
            
        } else if (c < -0.3f) {
            // === OCEAN ===
            // Gradual rise toward coast
            float t = (c + 0.6f) / 0.3f;
            height = lerp(40, 55, t);
            squashing = lerp(1.5f, 1.2f, t);
            
        } else if (c < -0.1f) {
            // === COAST / SHALLOW WATER ===
            // Quick transition to land - this creates beaches
            float t = (c + 0.3f) / 0.2f;
            height = lerp(55, seaLevel + 3, t);
            squashing = lerp(1.2f, 1.0f, t);
            
        } else if (c < 0.3f) {
            // === LOWLANDS ===
            // Erosion matters a lot here
            float[] lowlandParams = sampleLowlandSpline(e, pv);
            float t = (c + 0.1f) / 0.4f;
            height = lerp(seaLevel + 3, lowlandParams[0], t);
            squashing = lerp(1.0f, lowlandParams[1], t);
            
        } else if (c < 0.6f) {
            // === MIDLANDS ===
            // Higher base, erosion controls hills vs mountains
            float[] midlandParams = sampleMidlandSpline(e, pv);
            float t = (c - 0.3f) / 0.3f;
            float[] lowlandParams = sampleLowlandSpline(e, pv);
            height = lerp(lowlandParams[0], midlandParams[0], t);
            squashing = lerp(lowlandParams[1], midlandParams[1], t);
            
        } else {
            // === HIGHLANDS / INLAND ===
            // Highest terrain, most dramatic mountains possible
            float[] highlandParams = sampleHighlandSpline(e, pv);
            float t = (c - 0.6f) / 0.4f;
            float[] midlandParams = sampleMidlandSpline(e, pv);
            height = lerp(midlandParams[0], highlandParams[0], t);
            squashing = lerp(midlandParams[1], highlandParams[1], t);
        }
        
        return new float[] { height, squashing };
    }
    
    /**
 * Lowland erosion spline with dynamic PV modulation.
 */
private float[] sampleLowlandSpline(float e, float pv) {
    float height;
    float squashing;

    // Modulatore di PV in base all'erosione (0 = high, 1 = low)
    float pvFactor = 1.0f - e; // e da -1 a 1 → pvFactor da 2 a 0
    pvFactor = Math.max(0.2f, Math.min(1.5f, pvFactor)); // clamp

    if (e > 0.5f) {
        // High erosion = FLAT PLAINS
        height = seaLevel + 5 + pv * 2 * pvFactor;
        squashing = 2.0f;

    } else if (e > 0.0f) {
        // Medium erosion = gentle hills
        float t = e / 0.5f;
        float hillHeight = seaLevel + 10 + pv * 8 * pvFactor;
        height = lerp(hillHeight, seaLevel + 5 + pv * 2 * pvFactor, t);
        squashing = lerp(1.2f, 2.0f, t);

    } else {
        // Low erosion = some hills can be dramatic
        float t = (e + 1f) / 1f;
        float dramaticHeight = seaLevel + 20 + pv * 20 * pvFactor;
        float hillHeight = seaLevel + 10 + pv * 8 * pvFactor;
        height = lerp(dramaticHeight, hillHeight, t);
        squashing = lerp(0.8f, 1.2f, t);
    }

    return new float[] { height, squashing };
}

/**
 * Midland erosion spline with dynamic PV modulation.
 */
private float[] sampleMidlandSpline(float e, float pv) {
    float height;
    float squashing;

    float pvFactor = 1.0f - e;
    pvFactor = Math.max(0.2f, Math.min(1.5f, pvFactor));

    if (e > 0.5f) {
        height = seaLevel + 12 + pv * 4 * pvFactor;
        squashing = 1.6f;

    } else if (e > 0.0f) {
        float t = e / 0.5f;
        height = lerp(seaLevel + 35 + pv * 25 * pvFactor, seaLevel + 12 + pv * 4 * pvFactor, t);
        squashing = lerp(0.9f, 1.6f, t);

    } else if (e > -0.5f) {
        float t = (e + 0.5f) / 0.5f;
        height = lerp(seaLevel + 80 + pv * 55 * pvFactor, seaLevel + 35 + pv * 25 * pvFactor, t);
        squashing = lerp(0.5f, 0.9f, t);

    } else {
        float t = (e + 1f) / 0.5f;
        height = lerp(seaLevel + 130 + pv * 90 * pvFactor, seaLevel + 110 + pv * 75 * pvFactor, t);
        squashing = lerp(0.3f, 0.5f, t);
    }

    return new float[] { height, squashing };
}

/**
 * Highland erosion spline with dynamic PV modulation.
 */
private float[] sampleHighlandSpline(float e, float pv) {
    float height;
    float squashing;

    float pvFactor = 1.0f - e;
    pvFactor = Math.max(0.2f, Math.min(1.5f, pvFactor));

    if (e > 0.5f) {
        height = seaLevel + 25 + pv * 8 * pvFactor;
        squashing = 1.3f;

    } else if (e > 0.0f) {
        float t = e / 0.5f;
        height = lerp(seaLevel + 70 + pv * 50 * pvFactor, seaLevel + 25 + pv * 8 * pvFactor, t);
        squashing = lerp(0.6f, 1.3f, t);

    } else if (e > -0.5f) {
        float t = (e + 0.5f) / 0.5f;
        height = lerp(seaLevel + 120 + pv * 80 * pvFactor, seaLevel + 70 + pv * 50 * pvFactor, t);
        squashing = lerp(0.35f, 0.6f, t);

    } else {
        float t = (e + 1f) / 0.5f;
        height = lerp(seaLevel + 160 + pv * 90 * pvFactor, seaLevel + 120 + pv * 80 * pvFactor, t);
        squashing = 0.2f;
    }

    return new float[] { height, squashing };
}


    
    // === UTILITY ===
    
    private float lerp(float a, float b, float t) {
        t = Math.max(0, Math.min(1, t));
        return a + (b - a) * t;
    }
    
    public float getSeaLevel() {
        return seaLevel;
    }
    
    public NoiseRouter getNoiseRouter() {
        return noiseRouter;
    }
}