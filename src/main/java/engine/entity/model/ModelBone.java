package engine.entity.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A bone in the entity model skeleton.
 * Can contain cubes and child bones.
 */
public class ModelBone {
    
    private final String name;
    private ModelBone parent;
    private final List<ModelBone> children = new ArrayList<>();
    private final List<ModelCube> cubes = new ArrayList<>();
    
    // Pivot point (rotation center)
    private float pivotX, pivotY, pivotZ;
    
    // Default pose rotation (degrees)
    private float defaultRotX, defaultRotY, defaultRotZ;
    
    // Current pose (animated)
    private float rotationX, rotationY, rotationZ;
    private float positionX, positionY, positionZ;
    private float scaleX = 1, scaleY = 1, scaleZ = 1;
    
    // Visibility
    private boolean visible = true;
    
    public ModelBone(String name) {
        this.name = name;
    }
    
    // ==================== HIERARCHY ====================
    
    public String getName() { return name; }
    
    public ModelBone getParent() { return parent; }
    public void setParent(ModelBone parent) { 
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
    }
    
    public List<ModelBone> getChildren() { return children; }
    
    // ==================== CUBES ====================
    
    public void addCube(ModelCube cube) {
        cubes.add(cube);
    }
    
    public List<ModelCube> getCubes() { return cubes; }
    
    // ==================== PIVOT ====================
    
    public float getPivotX() { return pivotX; }
    public float getPivotY() { return pivotY; }
    public float getPivotZ() { return pivotZ; }
    
    public void setPivot(float x, float y, float z) {
        this.pivotX = x;
        this.pivotY = y;
        this.pivotZ = z;
    }
    
    // ==================== DEFAULT ROTATION ====================
    
    public void setDefaultRotation(float x, float y, float z) {
        this.defaultRotX = x;
        this.defaultRotY = y;
        this.defaultRotZ = z;
        // Also set current to default
        this.rotationX = x;
        this.rotationY = y;
        this.rotationZ = z;
    }
    
    public float getDefaultRotX() { return defaultRotX; }
    public float getDefaultRotY() { return defaultRotY; }
    public float getDefaultRotZ() { return defaultRotZ; }
    
    // ==================== CURRENT POSE ====================
    
    public float getRotationX() { return rotationX; }
    public float getRotationY() { return rotationY; }
    public float getRotationZ() { return rotationZ; }
    
    public void setRotation(float x, float y, float z) {
        this.rotationX = x;
        this.rotationY = y;
        this.rotationZ = z;
    }
    
    public void addRotation(float dx, float dy, float dz) {
        this.rotationX += dx;
        this.rotationY += dy;
        this.rotationZ += dz;
    }
    
    public float getPositionX() { return positionX; }
    public float getPositionY() { return positionY; }
    public float getPositionZ() { return positionZ; }
    
    public void setPosition(float x, float y, float z) {
        this.positionX = x;
        this.positionY = y;
        this.positionZ = z;
    }
    
    public float getScaleX() { return scaleX; }
    public float getScaleY() { return scaleY; }
    public float getScaleZ() { return scaleZ; }
    
    public void setScale(float x, float y, float z) {
        this.scaleX = x;
        this.scaleY = y;
        this.scaleZ = z;
    }
    
    // ==================== VISIBILITY ====================
    
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    
    // ==================== POSE RESET ====================
    
    public void resetPose() {
        rotationX = defaultRotX;
        rotationY = defaultRotY;
        rotationZ = defaultRotZ;
        positionX = 0;
        positionY = 0;
        positionZ = 0;
        scaleX = 1;
        scaleY = 1;
        scaleZ = 1;
    }
}
