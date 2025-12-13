package engine.rendering.model;

/**
 * Represents a single variant in a blockstate file.
 * Defines the model to use and its transformations.
 */
public class ModelVariant {
    private String model; // Path to the model (e.g. "block/door_bottom")
    private int x = 0; // Rotation around X axis
    private int y = 0; // Rotation around Y axis
    private boolean uvlock = false;

    public String getModel() {
        return model;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public boolean isUvlock() {
        return uvlock;
    }
}
