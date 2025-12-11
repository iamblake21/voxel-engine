package game.init;

import engine.ui.definition.GuiDefinition;
import engine.ui.definition.Guis;

/**
 * All GUI definitions for the game.
 * 
 * These are registered during the content registration phase.
 * GUIs can be defined either:
 * 1. Programmatically with the builder pattern
 * 2. Loaded from JSON files in resources/gui/
 * 
 * The order here doesn't matter - GUIs are looked up by ID.
 */
public final class GameGuis {

        // ==================== PLAYER GUIs ====================

        /** Main player inventory (E key) */
        public static GuiDefinition INVENTORY;

        /** Creative mode inventory */
        public static GuiDefinition CREATIVE_INVENTORY;

        // ==================== CONTAINER GUIs ====================

        /** Furnace GUI */
        public static GuiDefinition FURNACE;

        /** Crafting table 3x3 */
        public static GuiDefinition CRAFTING_TABLE;

        /** Chest GUI */
        public static GuiDefinition CHEST;

        /** Double chest GUI */
        public static GuiDefinition DOUBLE_CHEST;

        private GameGuis() {
        }

        /**
         * Register all game GUIs.
         * Called by game during init, BEFORE registries are frozen.
         */
        public static void register() {
                System.out.println("[GameGuis] Registering GUIs...");

                // ==================== INVENTORY ====================
                // Standard player inventory: 9 hotbar + 27 main = 36 slots

                INVENTORY = Guis.builder("game:inventory", "textures/gui/inventory.png", 196, 166)
                                .backgroundColor(0.15f, 0.15f, 0.15f, 0.95f)
                                .centeredLabel("Inventory", 88, 6)
                                .slotRow("main", 8, 17, "main", 0, 9)
                                .slotRow("main", 8, 35, "main", 9, 9)
                                .slotRow("main", 8, 53, "main", 18, 9)
                                .slotRow("main", 8, 71, "main", 27, 9)
                                .slotRow("main", 8, 71, "main", 27, 9)
                                .slotRow("main", 8, 89, "main", 36, 9)
                                .hotbar(8, 111)

                                .register();
                // ==================== CRAFTING TABLE ====================
                // 3x3 crafting input + 1 output + player inventory

                CRAFTING_TABLE = Guis.builder("game:crafting_table", "textures/gui/crafting_table.png", 176, 166)
                                .backgroundColor(0.15f, 0.15f, 0.15f, 0.95f)
                                .centeredLabel("Crafting", 88, 6)
                                // 3x3 crafting grid
                                .slot("craft_0", 30, 17, "crafting_input", 0)
                                .slot("craft_1", 48, 17, "crafting_input", 1)
                                .slot("craft_2", 66, 17, "crafting_input", 2)
                                .slot("craft_3", 30, 35, "crafting_input", 3)
                                .slot("craft_4", 48, 35, "crafting_input", 4)
                                .slot("craft_5", 66, 35, "crafting_input", 5)
                                .slot("craft_6", 30, 53, "crafting_input", 6)
                                .slot("craft_7", 48, 53, "crafting_input", 7)
                                .slot("craft_8", 66, 53, "crafting_input", 8)
                                // Output slot (larger)
                                .slot("craft_output", 124, 35, "crafting_output", 0)
                                // Player inventory
                                .hotbar(8, 142)
                                .slotRow("main", 8, 84, "main", 0, 9)
                                .slotRow("main", 8, 102, "main", 9, 9)
                                .slotRow("main", 8, 120, "main", 18, 9)
                                .register();

                // ==================== FURNACE ====================
                // Input + fuel + output + player inventory

                FURNACE = Guis.builder("game:furnace", "textures/gui/furnace.png", 176, 166)
                                .backgroundColor(0.15f, 0.15f, 0.15f, 0.95f)
                                .centeredLabel("Furnace", 88, 6)
                                // Furnace slots
                                .slot("input", 56, 17, "furnace_input", 0)
                                .slot("fuel", 56, 53, "furnace_fuel", 0)
                                .slot("output", 116, 35, "furnace_output", 0)
                                // Player inventory
                                .hotbar(8, 142)
                                .slotRow("main", 8, 84, "main", 0, 9)
                                .slotRow("main", 8, 102, "main", 9, 9)
                                .slotRow("main", 8, 120, "main", 18, 9)
                                .register();

                // ==================== CHEST ====================
                // 27 chest slots + player inventory

                CHEST = Guis.builder("game:chest", "textures/gui/chest.png", 176, 168)
                                .backgroundColor(0.15f, 0.15f, 0.15f, 0.95f)
                                .centeredLabel("Chest", 88, 6)
                                // Chest slots - 3 rows of 9
                                .slotRow("chest", 8, 18, "container", 0, 9)
                                .slotRow("chest", 8, 36, "container", 9, 9)
                                .slotRow("chest", 8, 54, "container", 18, 9)
                                // Player inventory
                                .hotbar(8, 144)
                                .slotRow("main", 8, 86, "main", 0, 9)
                                .slotRow("main", 8, 104, "main", 9, 9)
                                .slotRow("main", 8, 122, "main", 18, 9)
                                .register();

                // ==================== DOUBLE CHEST ====================
                // 54 chest slots + player inventory

                DOUBLE_CHEST = Guis.builder("game:double_chest", "textures/gui/double_chest.png", 176, 222)
                                .backgroundColor(0.15f, 0.15f, 0.15f, 0.95f)
                                .centeredLabel("Large Chest", 88, 6)
                                // Chest slots - 6 rows of 9
                                .slotRow("chest", 8, 18, "container", 0, 9)
                                .slotRow("chest", 8, 36, "container", 9, 9)
                                .slotRow("chest", 8, 54, "container", 18, 9)
                                .slotRow("chest", 8, 72, "container", 27, 9)
                                .slotRow("chest", 8, 90, "container", 36, 9)
                                .slotRow("chest", 8, 108, "container", 45, 9)
                                // Player inventory
                                .hotbar(8, 198)
                                .slotRow("main", 8, 140, "main", 0, 9)
                                .slotRow("main", 8, 158, "main", 9, 9)
                                .slotRow("main", 8, 176, "main", 18, 9)
                                .register();

                // ==================== CREATIVE INVENTORY ====================
                // For future use

                CREATIVE_INVENTORY = Guis.builder("game:creative_inventory", null, 195, 136)
                                .backgroundColor(0.15f, 0.15f, 0.15f, 0.95f)
                                .centeredLabel("Creative", 97, 6)
                                // Creative slots - scrollable area would be handled by code
                                // For now, just basic layout
                                .hotbar(8, 112)
                                .register();

                System.out.println("[GameGuis] Registered " +
                                engine.registry.Registries.GUIS.size() + " GUIs total");
        }

        /**
         * Alternative: Register GUIs from JSON files.
         * Use this if you prefer JSON definitions over programmatic.
         */
        public static void registerFromJson() {
                System.out.println("[GameGuis] Registering GUIs from JSON...");

                try {
                        INVENTORY = Guis.registerFromJson("game:inventory");
                } catch (Exception e) {
                        System.err.println("[GameGuis] Failed to load inventory.json, using programmatic fallback");
                        registerInventoryFallback();
                }

                // Add more JSON-based GUIs here...

                System.out.println("[GameGuis] Registered " +
                                engine.registry.Registries.GUIS.size() + " GUIs total");
        }

        /**
         * Fallback if JSON loading fails
         */
        private static void registerInventoryFallback() {
                INVENTORY = Guis.builder("game:inventory", null, 176, 166)
                                .backgroundColor(0.15f, 0.15f, 0.15f, 0.95f)
                                .centeredLabel("Inventory", 88, 6)
                                .hotbar(8, 142)
                                .mainInventory(8, 84)
                                .register();
        }
}
