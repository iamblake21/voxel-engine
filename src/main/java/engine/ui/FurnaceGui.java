package engine.ui;

import engine.entity.inventory.PlayerInventory;
import engine.registry.Registries;
import engine.ui.definition.GuiDefinition;
import engine.ui.definition.GuiProgressBarDefinition;
import engine.world.item.ItemStack;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Furnace GUI screen.
 * 
 * Demonstrates how to extend TexturedGui for containers with:
 * - Custom slot types (input, fuel, output)
 * - Progress bars (burn progress, cook progress)
 * - Container binding (furnace tile entity)
 * 
 * Usage:
 * FurnaceGui gui = new FurnaceGui(playerInventory, furnaceContainer, width,
 * height);
 */
public class FurnaceGui extends TexturedGui {

    private final PlayerInventory playerInventory;

    // Furnace data suppliers (provided by tile entity or container)
    private Supplier<Float> burnProgressSupplier;
    private Supplier<Float> cookProgressSupplier;
    private Supplier<ItemStack> inputStackSupplier;
    private Supplier<ItemStack> fuelStackSupplier;
    private Supplier<ItemStack> outputStackSupplier;

    /**
     * Create furnace GUI.
     * 
     * @param playerInventory Player's inventory for the bottom slots
     * @param windowWidth     Window width
     * @param windowHeight    Window height
     */
    public FurnaceGui(PlayerInventory playerInventory, int windowWidth, int windowHeight) {
        super(loaDefinition(), windowWidth, windowHeight);

        this.playerInventory = playerInventory;

        // Default suppliers (no furnace connected)
        this.burnProgressSupplier = () -> 0f;
        this.cookProgressSupplier = () -> 0f;
        this.inputStackSupplier = () -> ItemStack.EMPTY;
        this.fuelStackSupplier = () -> ItemStack.EMPTY;
        this.outputStackSupplier = () -> ItemStack.EMPTY;

        // Set up the stack provider
        setStackProvider(this::getStackForSlot);

        System.out.println("[FurnaceGui] Created");
    }

    /**
     * Get the GUI definition from registry.
     */
    private static GuiDefinition loaDefinition() {
        Optional<GuiDefinition> def = Registries.GUIS.get("game:furnace");
        if (def.isPresent()) {
            return def.get();
        }

        throw new IllegalStateException(
                "[FurnaceGui] No furnace GUI definition found! " +
                        "Make sure GameGuis.register() is called before creating FurnaceGui.");
    }

    /**
     * Bind to a furnace container/tile entity.
     * Call this to connect the GUI to actual furnace data.
     */
    public void bindFurnace(Supplier<ItemStack> input,
            Supplier<ItemStack> fuel,
            Supplier<ItemStack> output,
            Supplier<Float> burnProgress,
            Supplier<Float> cookProgress) {
        this.inputStackSupplier = input;
        this.fuelStackSupplier = fuel;
        this.outputStackSupplier = output;
        this.burnProgressSupplier = burnProgress;
        this.cookProgressSupplier = cookProgress;
    }

    /**
     * Get ItemStack for a slot based on type.
     */
    private ItemStack getStackForSlot(int absoluteIndex) {
        // Furnace slots use special indices
        // We determine the slot type from the definition

        // Check if it's a furnace-specific slot
        var slotDef = definition.getSlots().stream()
                .filter(s -> s.getAbsoluteIndex() == absoluteIndex)
                .findFirst();

        if (slotDef.isPresent()) {
            String type = slotDef.get().getType();

            return switch (type) {
                case "furnace_input" -> inputStackSupplier.get();
                case "furnace_fuel" -> fuelStackSupplier.get();
                case "furnace_output" -> outputStackSupplier.get();
                case "hotbar" -> playerInventory.getHotbarStack(slotDef.get().getIndex());
                case "main" -> playerInventory.getMainStack(slotDef.get().getIndex());
                default -> ItemStack.EMPTY;
            };
        }

        return ItemStack.EMPTY;
    }

    @Override
    protected void renderCustom(GuiRenderer renderer) {
        // Render progress bars
        renderBurnProgress(renderer);
        renderCookProgress(renderer);
    }

    /**
     * Render the burn progress indicator (fire icon)
     */
    private void renderBurnProgress(GuiRenderer renderer) {
        float progress = burnProgressSupplier.get();
        if (progress <= 0)
            return;

        // Find the burn progress bar definition
        Optional<GuiProgressBarDefinition> barDef = definition.getProgressBars().stream()
                .filter(b -> "burn_progress".equals(b.getId()))
                .findFirst();

        if (barDef.isEmpty())
            return;

        GuiProgressBarDefinition bar = barDef.get();
        int barX = x + bar.getX();
        int barY = y + bar.getY();
        int barW = bar.getWidth();
        int barH = bar.getHeight();

        // Burn progress fills from bottom to top
        int filledHeight = (int) (barH * progress);
        int yOffset = barH - filledHeight;

        // Render filled portion (orange/yellow for fire)
        renderer.renderRect(barX, barY + yOffset, barW, filledHeight,
                1.0f, 0.6f, 0.1f, 1.0f);
    }

    /**
     * Render the cooking progress arrow
     */
    private void renderCookProgress(GuiRenderer renderer) {
        float progress = cookProgressSupplier.get();
        if (progress <= 0)
            return;

        // Find the cook progress bar definition
        Optional<GuiProgressBarDefinition> barDef = definition.getProgressBars().stream()
                .filter(b -> "cook_progress".equals(b.getId()))
                .findFirst();

        if (barDef.isEmpty())
            return;

        GuiProgressBarDefinition bar = barDef.get();
        int barX = x + bar.getX();
        int barY = y + bar.getY();
        int barW = bar.getWidth();
        int barH = bar.getHeight();

        // Cook progress fills from left to right
        int filledWidth = (int) (barW * progress);

        // Render filled portion (white arrow)
        renderer.renderRect(barX, barY, filledWidth, barH,
                1.0f, 1.0f, 1.0f, 0.8f);
    }

    /**
     * Get player inventory
     */
    public PlayerInventory getPlayerInventory() {
        return playerInventory;
    }
}
