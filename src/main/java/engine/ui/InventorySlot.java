package engine.ui;

import engine.world.item.ItemStack;

/**
 * Represents a single inventory slot in the GUI.
 * Renders item icon, stack count, and durability bar.
 */
public class InventorySlot extends GuiComponent {

    public static final int SLOT_SIZE = 18; // Pixels (16 + 2 for border)

    private ItemStack stack;
    private boolean selected;
    private boolean hovered;
    private int slotIndex; // Absolute slot index in inventory (0-35)

    public InventorySlot(int x, int y) {
        this(x, y, -1);
    }

    public InventorySlot(int x, int y, int slotIndex) {
        super(x, y, SLOT_SIZE, SLOT_SIZE);
        this.stack = ItemStack.EMPTY;
        this.selected = false;
        this.hovered = false;
        this.slotIndex = slotIndex;
    }

    public InventorySlot(int x, int y, ItemStack stack) {
        this(x, y);
        this.stack = stack;
    }

    @Override
    public void render(GuiRenderer renderer) {
        if (!visible)
            return;

        // 1. Render slot background
        renderSlotBackground(renderer);

        // 2. Render item icon if not empty
        if (!stack.isEmpty()) {
            renderItemIcon(renderer);
            renderStackCount(renderer);

            if (stack.isDamageable() && stack.getDamage() > 0) {
                renderDurabilityBar(renderer);
            }
        }

        // 3. Render hover highlight
        if (hovered && !selected) {
            renderHoverHighlight(renderer);
        }

        // 4. Render selection highlight if selected
        if (selected) {
            renderSelectionHighlight(renderer);
        }
    }

    private void renderSlotBackground(GuiRenderer renderer) {
        // Dark gray background
        renderer.renderRect(x, y, width, height, 0.2f, 0.2f, 0.2f, 0.9f);

        // Light border
        renderer.renderRect(x, y, width, 1, 0.4f, 0.4f, 0.4f, 1.0f); // Top
        renderer.renderRect(x, y, 1, height, 0.4f, 0.4f, 0.4f, 1.0f); // Left
        renderer.renderRect(x + width - 1, y, 1, height, 0.6f, 0.6f, 0.6f, 1.0f); // Right
        renderer.renderRect(x, y + height - 1, width, 1, 0.6f, 0.6f, 0.6f, 1.0f); // Bottom
    }

    private void renderItemIcon(GuiRenderer renderer) {
        // 1. Try explicit Item Icon first (Overrides Block rendering if present)
        // This fixes Doors appearing as half-blocks
        String iconPath = stack.getItem().getIconTexture();
        if (iconPath != null) {
            try {
                GuiTexture itemIcon = renderer.getTexture(iconPath);
                if (itemIcon != null) {
                    renderer.renderQuad(x + 1, y + 1, 16, 16, itemIcon);
                } else {
                    renderFallbackIcon(renderer);
                }
                return;
            } catch (Exception e) {
                // Fallback if texture fail
            }
        }

        // 2. Special handling for BlockItem - render isometric cube or flat model
        if (stack.getItem() instanceof engine.world.item.BlockItem) {
            engine.world.item.BlockItem blockItem = (engine.world.item.BlockItem) stack.getItem();
            engine.world.block.Block block = blockItem.getBlock();

            if (block.getProperties().hasCustomModel()) {
                // Custom models (flowers, torches) -> Render flat sprite
                renderer.renderBlockFlat(x + 1, y + 1, 16, block);
            } else {
                // Standard blocks -> Render isometric cube
                renderer.renderIsometricCube(x + 4, y + 4, 10, block);
            }
            return;
        }

        // 3. Last resort fallback
        renderFallbackIcon(renderer);
    }

    private void renderFallbackIcon(GuiRenderer renderer) {
        // Simple colored square as fallback
        float hue = (stack.getItem().getNumericId() * 137.5f) % 360f;
        float[] rgb = hsvToRgb(hue, 0.7f, 0.9f);
        renderer.renderRect(x + 1, y + 1, 16, 16, rgb[0], rgb[1], rgb[2], 1.0f);
    }

    private void renderStackCount(GuiRenderer renderer) {
        if (stack.getCount() > 1) {
            String countText = String.valueOf(stack.getCount());
            // Render text bottom-right of slot in YELLOW for visibility
            // 3x5 font scale factor
            float textSize = 6;
            float textX = x + width - (countText.length() * (textSize * 0.7f)) - 1;
            float textY = y + height - textSize - 1;

            // Draw shadow first
            renderer.renderText(countText, textX + 1, textY + 1, textSize, 0.2f, 0.2f, 0.2f, 1.0f);
            // Draw text
            renderer.renderText(countText, textX, textY, textSize, 1.0f, 1.0f, 1f, 1.0f); // Light Yellow
        }
    }

    private void renderDurabilityBar(GuiRenderer renderer) {
        float durabilityPercent = 1.0f - (stack.getDamage() / (float) stack.getMaxDamage());
        int barWidth = (int) (14 * durabilityPercent);

        // Background (black)
        renderer.renderRect(x + 2, y + height - 4, 14, 2, 0, 0, 0, 0.8f);

        // Durability bar (green to red)
        float r = 1.0f - durabilityPercent;
        float g = durabilityPercent;
        renderer.renderRect(x + 2, y + height - 4, barWidth, 2, r, g, 0, 1.0f);
    }

    private void renderHoverHighlight(GuiRenderer renderer) {
        // Semi-transparent white overlay when hovered
        renderer.renderRect(x + 1, y + 1, width - 2, height - 2, 1, 1, 1, 0.3f);
    }

    private void renderSelectionHighlight(GuiRenderer renderer) {
        // White border around slot
        int thickness = 2;
        renderer.renderRect(x - thickness, y - thickness, width + thickness * 2, thickness, 1, 1, 1, 0.8f); // Top
        renderer.renderRect(x - thickness, y - thickness, thickness, height + thickness * 2, 1, 1, 1, 0.8f); // Left
        renderer.renderRect(x + width, y - thickness, thickness, height + thickness * 2, 1, 1, 1, 0.8f); // Right
        renderer.renderRect(x - thickness, y + height, width + thickness * 2, thickness, 1, 1, 1, 0.8f); // Bottom
    }

    /**
     * Convert HSV to RGB
     */
    private float[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs(((h / 60f) % 2) - 1));
        float m = v - c;

        float r, g, b;
        if (h < 60) {
            r = c;
            g = x;
            b = 0;
        } else if (h < 120) {
            r = x;
            g = c;
            b = 0;
        } else if (h < 180) {
            r = 0;
            g = c;
            b = x;
        } else if (h < 240) {
            r = 0;
            g = x;
            b = c;
        } else if (h < 300) {
            r = x;
            g = 0;
            b = c;
        } else {
            r = c;
            g = 0;
            b = x;
        }

        return new float[] { r + m, g + m, b + m };
    }

    // Getters and Setters
    public ItemStack getStack() {
        return stack;
    }

    public void setStack(ItemStack stack) {
        this.stack = stack;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public boolean isHovered() {
        return hovered;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }
}
