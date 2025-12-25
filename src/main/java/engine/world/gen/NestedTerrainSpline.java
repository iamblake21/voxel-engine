package engine.world.gen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generic Cubic Spline implementation usually used for terrain generation.
 * Supports nesting (values can be other splines).
 */
public class NestedTerrainSpline {

    @FunctionalInterface
    public interface CoordinateExtractor {
        float apply(float c, float e, float w);
    }

    // Strategies to pick the coordinate
    public static final CoordinateExtractor CONTINENTALNESS = (c, e, w) -> c;
    public static final CoordinateExtractor EROSION = (c, e, w) -> e;
    public static final CoordinateExtractor WEIRDNESS = (c, e, w) -> w; // Peaks/Weirdness

    private final CoordinateExtractor extractor;
    private final List<SplinePoint> points = new ArrayList<>();

    public NestedTerrainSpline(CoordinateExtractor extractor) {
        this.extractor = extractor;
    }

    /**
     * Add a point with a constant value.
     */
    public NestedTerrainSpline addPoint(float location, float value, float derivative) {
        points.add(new SplinePoint(location, value, derivative));
        points.sort(Comparator.comparingDouble(p -> p.location));
        return this;
    }

    /**
     * Add a point with a nested spline as value.
     */
    public NestedTerrainSpline addPoint(float location, NestedTerrainSpline value, float derivative) {
        points.add(new SplinePoint(location, value, derivative));
        points.sort(Comparator.comparingDouble(p -> p.location));
        return this;
    }

    /**
     * Add a point valid for MC 1.18 style (helper).
     */
    public NestedTerrainSpline add(float location, float value) {
        return addPoint(location, value, 0.0f);
    }

    public NestedTerrainSpline add(float location, NestedTerrainSpline value) {
        return addPoint(location, value, 0.0f);
    }

    /**
     * Sample the spline at the given coordinates.
     */
    public float apply(float c, float e, float w) {
        float x = extractor.apply(c, e, w); // The position on this spline

        // Find the segment [i, i+1] that contains x
        int i = findIndex(x);

        if (i < 0) {
            // Clamp to first
            return evaluateValue(points.get(0).value, c, e, w);
        }
        if (i >= points.size() - 1) {
            // Clamp to last
            return evaluateValue(points.get(points.size() - 1).value, c, e, w);
        }

        SplinePoint p0 = points.get(i);
        SplinePoint p1 = points.get(i + 1);

        float x0 = p0.location;
        float x1 = p1.location;
        float dist = x1 - x0;

        if (dist <= 0)
            return evaluateValue(p0.value, c, e, w);

        float t = (x - x0) / dist;

        // Fetch values from sub-splines (or constants)
        float y0 = evaluateValue(p0.value, c, e, w);
        float y1 = evaluateValue(p1.value, c, e, w);

        // Derivatives
        float m0 = p0.derivative * dist;
        float m1 = p1.derivative * dist;

        // Cubic interpolation
        return cubicLerp(t, y0, y1, m0, m1);
    }

    private float evaluateValue(Object val, float c, float e, float w) {
        if (val instanceof Float) {
            return (Float) val;
        } else if (val instanceof NestedTerrainSpline) {
            return ((NestedTerrainSpline) val).apply(c, e, w);
        }
        return 0f;
    }

    private int findIndex(float x) {
        for (int i = 0; i < points.size() - 1; i++) {
            if (x >= points.get(i).location && x < points.get(i + 1).location) {
                return i;
            }
        }
        if (x < points.get(0).location)
            return -1;
        return points.size() - 1;
    }

    private float cubicLerp(float t, float y0, float y1, float m0, float m1) {
        // Hermite basis functions
        float t2 = t * t;
        float t3 = t2 * t;

        float h00 = 2 * t3 - 3 * t2 + 1;
        float h10 = t3 - 2 * t2 + t;
        float h01 = -2 * t3 + 3 * t2;
        float h11 = t3 - t2;

        return h00 * y0 + h10 * m0 + h01 * y1 + h11 * m1;
    }

    private static class SplinePoint {
        final float location;
        final Object value; // Float or NestedTerrainSpline
        final float derivative;

        SplinePoint(float location, Object value, float derivative) {
            this.location = location;
            this.value = value;
            this.derivative = derivative;
        }
    }
}
