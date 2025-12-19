package engine.rendering;

import engine.core.Config;
import engine.utils.Math3D.Mat4;
import engine.utils.Math3D.Vec3;

/**
 * First-person camera with pitch/yaw control
 */
public class Camera {

    private Vec3 position;
    private float pitch; // Up/down rotation (degrees)
    private float yaw; // Left/right rotation (degrees)
    private float fov; // Field of view (degrees)

    private Mat4 viewMatrix;
    private Mat4 projectionMatrix;
    private Vec3 forward;
    private Vec3 right;
    private Vec3 up;

    private final Config config;
    private boolean needsUpdate = true;

    public Camera(Config config) {
        this.config = config;
        this.position = new Vec3(0, 0, 0);
        this.pitch = 0f;
        this.yaw = -90f; // Look down -Z axis
        this.fov = config.fov;
        this.viewMatrix = Mat4.identity();
        this.projectionMatrix = Mat4.identity();
        this.forward = new Vec3(0, 0, -1);
        this.right = new Vec3(1, 0, 0);
        this.up = new Vec3(0, 1, 0);

        updateProjectionMatrix();
        updateVectors();
    }

    /**
     * Update camera matrices
     */
    public void update(float deltaTime) {
        if (needsUpdate) {
            updateVectors();
            updateViewMatrix();
            needsUpdate = false;
        }
    }

    /**
     * Update direction vectors from pitch/yaw
     */
    private void updateVectors() {
        float pitchRad = (float) Math.toRadians(pitch);
        float yawRad = (float) Math.toRadians(yaw);

        // Calculate forward vector
        forward = new Vec3(
                (float) (Math.cos(pitchRad) * Math.cos(yawRad)),
                (float) (Math.sin(pitchRad)),
                (float) (Math.cos(pitchRad) * Math.sin(yawRad)));
        forward = forward.normalize();

        // Calculate right vector
        right = forward.cross(new Vec3(0, 1, 0)).normalize();

        // Calculate up vector
        up = right.cross(forward).normalize();
    }

    /**
     * Update view matrix
     */
    private void updateViewMatrix() {
        Vec3 target = position.add(forward);
        viewMatrix = Mat4.lookAt(
                position.x, position.y, position.z,
                target.x, target.y, target.z,
                up.x, up.y, up.z);
    }

    /**
     * Update projection matrix (call when window resizes)
     */
    /**
     * Update projection matrix (call when window resizes)
     */
    public void updateProjectionMatrix() {
        // Default to config (initial) or current?
        // We should probably rely on updateAspectRatio being called.
        // But for safety, let's keep this as fallback or initialization using config,
        // OR better: deprecate/don't use this one for resizing, use a specific resize
        // one.
        // Let's modify this to use stored aspect ratio or stored dimensions if we had
        // them.
        // But Camera doesn't store dimensions yet.
        // I'll leave this as is for initial setup, and add a specific resize method.
        float aspect = (float) config.windowWidth / config.windowHeight;
        projectionMatrix = Mat4.perspective(fov, aspect, config.nearPlane, config.farPlane);
    }

    public void updateAspectRatio(int width, int height) {
        float aspect = (float) width / height;
        projectionMatrix = Mat4.perspective(fov, aspect, config.nearPlane, config.farPlane);
    }

    /**
     * Set camera position
     */
    public void setPosition(float x, float y, float z) {
        position.x = x;
        position.y = y;
        position.z = z;
        needsUpdate = true;
    }

    /**
     * Set camera rotation
     */
    public void setRotation(float pitch, float yaw) {
        this.pitch = Math.max(-89f, Math.min(89f, pitch));
        this.yaw = yaw;
        needsUpdate = true;
    }

    /**
     * Rotate camera by delta
     */
    public void rotate(float deltaPitch, float deltaYaw) {
        setRotation(pitch + deltaPitch, yaw + deltaYaw);
    }

    /**
     * Set field of view
     */
    public void setFov(float fov) {
        this.fov = fov;
        updateProjectionMatrix();
    }

    // Getters

    public Mat4 getViewMatrix() {
        return viewMatrix;
    }

    public Mat4 getProjectionMatrix() {
        return projectionMatrix;
    }

    public Vec3 getPosition() {
        return position.copy();
    }

    public Vec3 getForward() {
        return forward.copy();
    }

    public Vec3 getRight() {
        return right.copy();
    }

    public Vec3 getUp() {
        return up.copy();
    }

    public float getPitch() {
        return pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getFov() {
        return fov;
    }
}