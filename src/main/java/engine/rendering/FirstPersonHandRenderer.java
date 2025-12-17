package engine.rendering;

import engine.core.Config;
import engine.entity.Player;
import engine.entity.model.EntityModel;
import engine.entity.render.EntityRenderer;
import engine.utils.Math3D.Mat4;
import engine.world.item.BlockItem;
import engine.world.item.ItemStack;

import static org.lwjgl.opengl.GL11.*;

public class FirstPersonHandRenderer {

    private final EntityRenderer entityRenderer;
    private final ItemEntityRenderer itemRenderer;
    private final Config config;

    public FirstPersonHandRenderer(EntityRenderer entityRenderer, ItemEntityRenderer itemRenderer, Config config) {
        this.entityRenderer = entityRenderer;
        this.itemRenderer = itemRenderer;
        this.config = config;
    }

    public void render(Player player, float partialTick) {
        if (player.isThirdPerson())
            return;

        ItemStack stack = player.getInventory().getSelectedStack();

        // 1. Clear Depth Buffer
        glClear(GL_DEPTH_BUFFER_BIT);

        // 2. Enable Depth Test
        glEnable(GL_DEPTH_TEST);

        // 3. Enable Culling
        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        if (stack == null || stack.isEmpty()) {
            renderEmptyHand(player, partialTick);
        } else {
            renderHeldItem(player, stack, partialTick);
        }
    }

    private void renderEmptyHand(Player player, float partialTick) {
        EntityModel model = entityRenderer.getModel(player);
        if (model == null)
            return;
        engine.rendering.Texture skin = entityRenderer.getTexture(player);

        Camera dummyCam = new Camera(config);
        dummyCam.setFov(player.getCamera().getFov());
        dummyCam.updateProjectionMatrix();

        entityRenderer.begin(dummyCam, null);

        // Disable culling for the arm
        glDisable(GL_CULL_FACE);

        Mat4 modelMat = Mat4.identity();

        // Right Hand Position - Adjusted for Pivot Centering
        // Ora X, Y, Z controllano la posizione della SPALLA sullo schermo
        float x = 1.0f;
        float y = -0.7f;
        float z = -0.6f;

        // 1. Translate to Screen Position
        modelMat = Mat4.mul(modelMat, Mat4.translate(x, y, z));

        // --- APPLY SWING ANIMATION ---
        float swing = player.getSwingProgress(partialTick);
        modelMat = applySwing(modelMat, swing, true);

        // 2. Scale 1/16.
        float s = 2.0f / 16.0f;
        modelMat = Mat4.mul(modelMat, Mat4.scale(s, s, s));

        // 3. Apply User Rotations (90 Z, 35 X).
        modelMat = Mat4.mul(modelMat, Mat4.rotate(145, 0, 0, 1));
        modelMat = Mat4.mul(modelMat, Mat4.rotate(90, 1, 0, 0));
        modelMat = Mat4.mul(modelMat, Mat4.rotate(90, 0, 1, 0));

        // 4. Translate *First* (conceptually) by (+5, -22, 0)
        modelMat = Mat4.mul(modelMat, Mat4.translate(5f, -22f, 0f));

        entityRenderer.renderSpecificBone(model, "arm_right", modelMat, skin);

        entityRenderer.end();
        glEnable(GL_CULL_FACE);
    }

    private void renderHeldItem(Player player, ItemStack stack, float partialTick) {
        Camera dummyCam = new Camera(config);
        dummyCam.setFov(player.getCamera().getFov());
        dummyCam.updateProjectionMatrix();

        itemRenderer.begin(dummyCam, null);

        Mat4 model = Mat4.identity();

        // Position: Bottom Right
        float x = 0.6f;
        float y = -0.5f;
        float z = -1.0f;

        model = Mat4.mul(model, Mat4.translate(x, y, z));

        // --- APPLY SWING ANIMATION ---
        float swing = player.getSwingProgress(partialTick);
        model = applySwing(model, swing, false);

        boolean isBlock = false;
        if (stack.getItem() instanceof BlockItem) {
            String icon = stack.getItem().getIconTexture();
            if (icon != null) {
                isBlock = false;
            } else {
                isBlock = true;
            }
        }

        if (isBlock) {
            // Block Item: Render as a block
            // Rotate Y to face inward/left
            model = Mat4.mul(model, Mat4.rotate(160, 0, 1, 0)); // Original: 160
            // Scale
            float scale = 0.5f;
            model = Mat4.mul(model, Mat4.scale(scale, scale, scale));
        } else {
            // Regular Item
            model = Mat4.mul(model, Mat4.rotate(65, 0, 1, 0)); // Face the player
            model = Mat4.mul(model, Mat4.rotate(0, 0, 0, 1)); // Tilt Z
            model = Mat4.mul(model, Mat4.rotate(25, 1, 0, 0)); // Tilt X (forward)
            model = Mat4.mul(model, Mat4.rotate(50, 0, 1, 0)); // Slight Y tilt

            // Larger scale
            float scale = 0.95f;
            model = Mat4.mul(model, Mat4.scale(scale, scale, scale));
        }

        itemRenderer.renderItem(stack, model);

        itemRenderer.end();
    }

    /**
     * Applies a procedural swing animation to the matrix.
     */
    private Mat4 applySwing(Mat4 model, float progress, boolean isEmptyHand) {
        if (progress <= 0)
            return model;

        // Simple Sine Curve
        float sqrtProg = (float) Math.sqrt(progress);
        float sinProg = (float) Math.sin(sqrtProg * Math.PI);

        // 1. Dip Down
        // model = Mat4.mul(model, Mat4.translate(0, -sinProg * 0.2f, 0));

        // 2. Rotate Pitch (Down) & Yaw (Inward)
        // Adjust values for "feel"
        // Pitch: -70 degrees at peak
        // Yaw: -30 degrees at peak

        float pitch = -sinProg * 80f;
        float yaw = -sinProg * 20f;

        // Apply rotations
        // Note: Order matters. Usually translate -> rotateY -> rotateX
        // But here we are multiplying onto an existing matrix that is already
        // translated.
        // So these rotations happen around the "Hand Anchor" point on screen.

        if (isEmptyHand) {
            // For empty hand, since we pivoted it weirdly, we might want different axis
            // Applying to World Axis (since we are at screen pos)
            // Check if rotation order is intuitive
            model = Mat4.mul(model, Mat4.rotate(pitch, 1, 0, 0)); // X axis down
            model = Mat4.mul(model, Mat4.rotate(yaw, 0, 1, 0)); // Y axis
        } else {
            // For items
            model = Mat4.mul(model, Mat4.rotate(pitch, 1, 0, 0));
            model = Mat4.mul(model, Mat4.rotate(yaw, 0, 1, 0));
        }

        return model;
    }
}
