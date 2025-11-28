package engine.world.gen;

import java.util.HashMap;
import java.util.Map;

/**
 * Nested spline terrain system (Minecraft 1.18+ style)
 *
 * Structure:
 * Continentalness -> [Erosion -> [Peaks -> height & squashing]]
 */
public class NestedTerrainSpline {

    private final float seaLevel;

    public NestedTerrainSpline(float seaLevel) {
        this.seaLevel = seaLevel;
        initializeSplines();
    }

    // === Data structure ===
    // Continentalness -> Erosion -> Peaks -> [height, squashing]
    private final Map<Float, Map<Float, float[]>> nestedSpline = new HashMap<>();

    private void initializeSplines() {
        // Example: 3 continentalness points
        addSplinePoint(-0.5f, -1.0f, new float[]{30, 2.0f}); // deep lowland, low erosion
        addSplinePoint(-0.5f, 0.0f, new float[]{40, 1.5f});  // deep lowland, medium erosion
        addSplinePoint(-0.5f, 1.0f, new float[]{50, 1.2f});  // deep lowland, high erosion

        addSplinePoint(0.0f, -1.0f, new float[]{70, 1.0f});  // lowland, low erosion
        addSplinePoint(0.0f, 0.0f, new float[]{65, 1.2f});   // lowland, medium erosion
        addSplinePoint(0.0f, 1.0f, new float[]{60, 1.5f});   // lowland, high erosion

        addSplinePoint(0.5f, -1.0f, new float[]{120, 0.5f}); // highland, low erosion
        addSplinePoint(0.5f, 0.0f, new float[]{100, 0.8f});  // highland, medium erosion
        addSplinePoint(0.5f, 1.0f, new float[]{90, 1.0f});   // highland, high erosion
    }

    private void addSplinePoint(float continentalness, float erosion, float[] heightSquash) {
        nestedSpline.computeIfAbsent(continentalness, k -> new HashMap<>())
                    .put(erosion, heightSquash);
    }

    /**
     * Sample terrain height and squashing using nested splines.
     * 
     * @param c Continentalness [-1,1]
     * @param e Erosion [-1,1]
     * @param pv Peaks [-1,1]
     * @return [height, squashing]
     */
    public float[] sample(float c, float e, float pv) {
        // Step 1: Find two closest continentalness points
        float c0 = floorKey(nestedSpline, c);
        float c1 = ceilKey(nestedSpline, c);
        Map<Float, float[]> erosionMap0 = nestedSpline.get(c0);
        Map<Float, float[]> erosionMap1 = nestedSpline.get(c1);

        // Step 2: Sample erosion spline for each continentalness
        float[] low = sampleErosion(erosionMap0, e, pv);
        float[] high = sampleErosion(erosionMap1, e, pv);

        // Step 3: Interpolate between continentalness points
        float tC = (c - c0) / (c1 - c0);
        tC = clamp01(tC);
        float height = lerp(low[0], high[0], tC);
        float squash = lerp(low[1], high[1], tC);

        return new float[]{height, squash};
    }

    private float[] sampleErosion(Map<Float, float[]> erosionMap, float e, float pv) {
        float e0 = floorKey(erosionMap, e);
        float e1 = ceilKey(erosionMap, e);

        float[] low = erosionMap.get(e0);
        float[] high = erosionMap.get(e1);

        float tE = (e - e0) / (e1 - e0);
        tE = clamp01(tE);

        // Interpolate height & squashing
        float height = lerp(low[0], high[0], tE);
        float squash = lerp(low[1], high[1], tE);

        // Step 4: Apply Peaks (PV) variation
        // Peaks adds a fraction of height based on squashing
        float peakEffect = pv * (2f - squash); // more squashing = flatter effect
        height += peakEffect * 10; // scale factor for dramatic peaks

        return new float[]{height, squash};
    }

    // === UTILITY ===
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float clamp01(float t) {
        return Math.max(0f, Math.min(1f, t));
    }

    private float floorKey(Map<Float, ?> map, float key) {
        float best = Float.NEGATIVE_INFINITY;
        for (float k : map.keySet()) if (k <= key && k > best) best = k;
        return best;
    }

    private float ceilKey(Map<Float, ?> map, float key) {
        float best = Float.POSITIVE_INFINITY;
        for (float k : map.keySet()) if (k >= key && k < best) best = k;
        return best;
    }

    public float getSeaLevel() {
        return seaLevel;
    }
}
