package game.init;

import engine.ui.definition.GuiDefinition;
import engine.ui.definition.Guis;
import engine.entity.inventory.PlayerInventory;

public final class GameGuis {

    // CORREZIONE FONDAMENTALE: L'inventario ha 54 slot (9 Hotbar + 45 Main).
    // L'offset per i contenitori DEVE essere 54.
    private static final int PLAYER_INVENTORY_SLOTS = 54;
    private static final int SLOT_ROW_SIZE = 9;
    private static final int SLOT_PX = 18;

    // ... (dichiarazioni statiche omesse per brevità) ...
    public static GuiDefinition INVENTORY;
    public static GuiDefinition CREATIVE_INVENTORY;
    public static GuiDefinition FURNACE;
    public static GuiDefinition CRAFTING_TABLE;
    public static GuiDefinition CHEST;
    public static GuiDefinition DOUBLE_CHEST;

    private GameGuis() {
    }

    // --- Metodo Helper per la Definizione degli Slot Contenitore ---
    // Usato per non ripetere la catena di 9 slot per ogni riga di Chest.
    private static Guis.GuiBuilder createContainerSlots(Guis.GuiBuilder builder, int rows, int startY) {
        int currentRelativeIndex = 0;
        int currentAbsoluteIndex = PLAYER_INVENTORY_SLOTS; // INIZIA SEMPRE DA 54

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < SLOT_ROW_SIZE; col++) {

                builder.slot("chest_" + currentRelativeIndex,
                        8 + col * SLOT_PX,
                        startY + row * SLOT_PX,
                        "container",
                        currentRelativeIndex)
                        .absoluteIndex(currentAbsoluteIndex);

                currentRelativeIndex++;
                currentAbsoluteIndex++;
            }
        }
        return builder;
    }
    // --- Fine Metodo Helper Locale ---

    public static void register() {
        System.out.println("[GameGuis] Registering GUIs...");

        // Hotbar (9 slot): 0-8. Inizia la Main Inventory assoluta a 9.
        final int MAIN_ABSOLUTE_START = 9;

        // ==================== INVENTORY (5 Righe Main + Hotbar) ====================
        // Nota: Qui usiamo la catena esplicita di slot().absoluteIndex() per tutte le 5
        // righe,
        // garantendo che l'absoluteIndex sia impostato correttamente fino a 53.
        INVENTORY = Guis.builder("game:inventory", "textures/gui/inventory.png", 196, 166)
                .backgroundColor(0.15f, 0.15f, 0.15f, 0.95f)
                .centeredLabel("Inventory", 88, 6)

                // Hotbar (Assoluto 0 - 8)
                .slot("hotbar_0", 8 + 0 * SLOT_PX, 111, "hotbar", 0).absoluteIndex(0)
                .slot("hotbar_1", 8 + 1 * SLOT_PX, 111, "hotbar", 1).absoluteIndex(1)
                .slot("hotbar_2", 8 + 2 * SLOT_PX, 111, "hotbar", 2).absoluteIndex(2)
                .slot("hotbar_3", 8 + 3 * SLOT_PX, 111, "hotbar", 3).absoluteIndex(3)
                .slot("hotbar_4", 8 + 4 * SLOT_PX, 111, "hotbar", 4).absoluteIndex(4)
                .slot("hotbar_5", 8 + 5 * SLOT_PX, 111, "hotbar", 5).absoluteIndex(5)
                .slot("hotbar_6", 8 + 6 * SLOT_PX, 111, "hotbar", 6).absoluteIndex(6)
                .slot("hotbar_7", 8 + 7 * SLOT_PX, 111, "hotbar", 7).absoluteIndex(7)
                .slot("hotbar_8", 8 + 8 * SLOT_PX, 111, "hotbar", 8).absoluteIndex(8)

                // Main Inventory - Riga 1 (Assoluto 9-17)
                .slot("main_0", 8 + 0 * SLOT_PX, 17, "main", 0).absoluteIndex(MAIN_ABSOLUTE_START + 0)
                .slot("main_1", 8 + 1 * SLOT_PX, 17, "main", 1).absoluteIndex(MAIN_ABSOLUTE_START + 1)
                .slot("main_2", 8 + 2 * SLOT_PX, 17, "main", 2).absoluteIndex(MAIN_ABSOLUTE_START + 2)
                .slot("main_3", 8 + 3 * SLOT_PX, 17, "main", 3).absoluteIndex(MAIN_ABSOLUTE_START + 3)
                .slot("main_4", 8 + 4 * SLOT_PX, 17, "main", 4).absoluteIndex(MAIN_ABSOLUTE_START + 4)
                .slot("main_5", 8 + 5 * SLOT_PX, 17, "main", 5).absoluteIndex(MAIN_ABSOLUTE_START + 5)
                .slot("main_6", 8 + 6 * SLOT_PX, 17, "main", 6).absoluteIndex(MAIN_ABSOLUTE_START + 6)
                .slot("main_7", 8 + 7 * SLOT_PX, 17, "main", 7).absoluteIndex(MAIN_ABSOLUTE_START + 7)
                .slot("main_8", 8 + 8 * SLOT_PX, 17, "main", 8).absoluteIndex(MAIN_ABSOLUTE_START + 8)

                // Main Inventory - Riga 2 (Assoluto 18-26)
                .slot("main_9", 8 + 0 * SLOT_PX, 35, "main", 9).absoluteIndex(MAIN_ABSOLUTE_START + 9)
                .slot("main_10", 8 + 1 * SLOT_PX, 35, "main", 10).absoluteIndex(MAIN_ABSOLUTE_START + 10)
                .slot("main_11", 8 + 2 * SLOT_PX, 35, "main", 11).absoluteIndex(MAIN_ABSOLUTE_START + 11)
                .slot("main_12", 8 + 3 * SLOT_PX, 35, "main", 12).absoluteIndex(MAIN_ABSOLUTE_START + 12)
                .slot("main_13", 8 + 4 * SLOT_PX, 35, "main", 13).absoluteIndex(MAIN_ABSOLUTE_START + 13)
                .slot("main_14", 8 + 5 * SLOT_PX, 35, "main", 14).absoluteIndex(MAIN_ABSOLUTE_START + 14)
                .slot("main_15", 8 + 6 * SLOT_PX, 35, "main", 15).absoluteIndex(MAIN_ABSOLUTE_START + 15)
                .slot("main_16", 8 + 7 * SLOT_PX, 35, "main", 16).absoluteIndex(MAIN_ABSOLUTE_START + 16)
                .slot("main_17", 8 + 8 * SLOT_PX, 35, "main", 17).absoluteIndex(MAIN_ABSOLUTE_START + 17)

                // Main Inventory - Riga 3 (Assoluto 27-35)
                .slot("main_18", 8 + 0 * SLOT_PX, 53, "main", 18).absoluteIndex(MAIN_ABSOLUTE_START + 18)
                .slot("main_19", 8 + 1 * SLOT_PX, 53, "main", 19).absoluteIndex(MAIN_ABSOLUTE_START + 19)
                .slot("main_20", 8 + 2 * SLOT_PX, 53, "main", 20).absoluteIndex(MAIN_ABSOLUTE_START + 20)
                .slot("main_21", 8 + 3 * SLOT_PX, 53, "main", 21).absoluteIndex(MAIN_ABSOLUTE_START + 21)
                .slot("main_22", 8 + 4 * SLOT_PX, 53, "main", 22).absoluteIndex(MAIN_ABSOLUTE_START + 22)
                .slot("main_23", 8 + 5 * SLOT_PX, 53, "main", 23).absoluteIndex(MAIN_ABSOLUTE_START + 23)
                .slot("main_24", 8 + 6 * SLOT_PX, 53, "main", 24).absoluteIndex(MAIN_ABSOLUTE_START + 24)
                .slot("main_25", 8 + 7 * SLOT_PX, 53, "main", 25).absoluteIndex(MAIN_ABSOLUTE_START + 25)
                .slot("main_26", 8 + 8 * SLOT_PX, 53, "main", 26).absoluteIndex(MAIN_ABSOLUTE_START + 26)

                // Main Inventory - Riga 4 (Assoluto 36-44)
                .slot("main_27", 8 + 0 * SLOT_PX, 71, "main", 27).absoluteIndex(MAIN_ABSOLUTE_START + 27)
                .slot("main_28", 8 + 1 * SLOT_PX, 71, "main", 28).absoluteIndex(MAIN_ABSOLUTE_START + 28)
                .slot("main_29", 8 + 2 * SLOT_PX, 71, "main", 29).absoluteIndex(MAIN_ABSOLUTE_START + 29)
                .slot("main_30", 8 + 3 * SLOT_PX, 71, "main", 30).absoluteIndex(MAIN_ABSOLUTE_START + 30)
                .slot("main_31", 8 + 4 * SLOT_PX, 71, "main", 31).absoluteIndex(MAIN_ABSOLUTE_START + 31)
                .slot("main_32", 8 + 5 * SLOT_PX, 71, "main", 32).absoluteIndex(MAIN_ABSOLUTE_START + 32)
                .slot("main_33", 8 + 6 * SLOT_PX, 71, "main", 33).absoluteIndex(MAIN_ABSOLUTE_START + 33)
                .slot("main_34", 8 + 7 * SLOT_PX, 71, "main", 34).absoluteIndex(MAIN_ABSOLUTE_START + 34)
                .slot("main_35", 8 + 8 * SLOT_PX, 71, "main", 35).absoluteIndex(MAIN_ABSOLUTE_START + 35)

                // Main Inventory - Riga 5 (Assoluto 45-53) <-- L'ultima riga del tuo
                // PlayerInventory
                .slot("main_36", 8 + 0 * SLOT_PX, 89, "main", 36).absoluteIndex(MAIN_ABSOLUTE_START + 36)
                .slot("main_37", 8 + 1 * SLOT_PX, 89, "main", 37).absoluteIndex(MAIN_ABSOLUTE_START + 37)
                .slot("main_38", 8 + 2 * SLOT_PX, 89, "main", 38).absoluteIndex(MAIN_ABSOLUTE_START + 38)
                .slot("main_39", 8 + 3 * SLOT_PX, 89, "main", 39).absoluteIndex(MAIN_ABSOLUTE_START + 39)
                .slot("main_40", 8 + 4 * SLOT_PX, 89, "main", 40).absoluteIndex(MAIN_ABSOLUTE_START + 40)
                .slot("main_41", 8 + 5 * SLOT_PX, 89, "main", 41).absoluteIndex(MAIN_ABSOLUTE_START + 41)
                .slot("main_42", 8 + 6 * SLOT_PX, 89, "main", 42).absoluteIndex(MAIN_ABSOLUTE_START + 42)
                .slot("main_43", 8 + 7 * SLOT_PX, 89, "main", 43).absoluteIndex(MAIN_ABSOLUTE_START + 43)
                .slot("main_44", 8 + 8 * SLOT_PX, 89, "main", 44).absoluteIndex(MAIN_ABSOLUTE_START + 44)
                .register();

        // ==================== CRAFTING TABLE ====================
        // Non toccato: non è la fonte del problema.
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
                // Player inventory (Hotbar e Main Inventory standard)
                .hotbar(8, 142)
                .slotRow("main", 8, 84, "main", 0, 9)
                .slotRow("main", 8, 102, "main", 9, 9)
                .slotRow("main", 8, 120, "main", 18, 9)
                .register();

        // ==================== FURNACE ====================
        // Non toccato: non è la fonte del problema.
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

        // ==================== CHEST (CORRETTO OFFSET 54) ====================

        CHEST = createContainerSlots(
                // Altezza base 168 + 2 * (18 * 2) = 168 + 72 = 240
                Guis.builder("game:chest", "textures/gui/chest.png", 176, 240) // Aumenta l'altezza della GUI
                        .backgroundColor(0.15f, 0.15f, 0.15f, 0.95f)
                        .centeredLabel("Chest", 88, 6),
                3, // 3 righe di chest
                18 // startY
        )
                // Player inventory (Indici Assoluti 0-53 ESPLICITI)

                // Hotbar (0-8) -- RIGA POSIZIONATA PIÙ IN BASSO (180 = 144 + 36)
                .slot("hotbar_0", 8 + 0 * SLOT_PX, 180, "hotbar", 0).absoluteIndex(0)
                .slot("hotbar_1", 8 + 1 * SLOT_PX, 180, "hotbar", 1).absoluteIndex(1)
                .slot("hotbar_2", 8 + 2 * SLOT_PX, 180, "hotbar", 2).absoluteIndex(2)
                .slot("hotbar_3", 8 + 3 * SLOT_PX, 180, "hotbar", 3).absoluteIndex(3)
                .slot("hotbar_4", 8 + 4 * SLOT_PX, 180, "hotbar", 4).absoluteIndex(4)
                .slot("hotbar_5", 8 + 5 * SLOT_PX, 180, "hotbar", 5).absoluteIndex(5)
                .slot("hotbar_6", 8 + 6 * SLOT_PX, 180, "hotbar", 6).absoluteIndex(6)
                .slot("hotbar_7", 8 + 7 * SLOT_PX, 180, "hotbar", 7).absoluteIndex(7)
                .slot("hotbar_8", 8 + 8 * SLOT_PX, 180, "hotbar", 8).absoluteIndex(8)

                // Main Inventory (9-53) - TUTTE E 5 LE RIGHE MOSTRATE
                // (La posizione Y è stata aggiustata per la GUI estesa)

                // Riga 1 (Assoluto 9-17)
                .slot("main_0", 8 + 0 * SLOT_PX, 108, "main", 0).absoluteIndex(MAIN_ABSOLUTE_START + 0)
                .slot("main_1", 8 + 1 * SLOT_PX, 108, "main", 1).absoluteIndex(MAIN_ABSOLUTE_START + 1)
                .slot("main_2", 8 + 2 * SLOT_PX, 108, "main", 2).absoluteIndex(MAIN_ABSOLUTE_START + 2)
                .slot("main_3", 8 + 3 * SLOT_PX, 108, "main", 3).absoluteIndex(MAIN_ABSOLUTE_START + 3)
                .slot("main_4", 8 + 4 * SLOT_PX, 108, "main", 4).absoluteIndex(MAIN_ABSOLUTE_START + 4)
                .slot("main_5", 8 + 5 * SLOT_PX, 108, "main", 5).absoluteIndex(MAIN_ABSOLUTE_START + 5)
                .slot("main_6", 8 + 6 * SLOT_PX, 108, "main", 6).absoluteIndex(MAIN_ABSOLUTE_START + 6)
                .slot("main_7", 8 + 7 * SLOT_PX, 108, "main", 7).absoluteIndex(MAIN_ABSOLUTE_START + 7)
                .slot("main_8", 8 + 8 * SLOT_PX, 108, "main", 8).absoluteIndex(MAIN_ABSOLUTE_START + 8)

                // Riga 2 (Assoluto 18-26)
                .slot("main_9", 8 + 0 * SLOT_PX, 126, "main", 9).absoluteIndex(MAIN_ABSOLUTE_START + 9)
                .slot("main_10", 8 + 1 * SLOT_PX, 126, "main", 10).absoluteIndex(MAIN_ABSOLUTE_START + 10)
                .slot("main_11", 8 + 2 * SLOT_PX, 126, "main", 11).absoluteIndex(MAIN_ABSOLUTE_START + 11)
                .slot("main_12", 8 + 3 * SLOT_PX, 126, "main", 12).absoluteIndex(MAIN_ABSOLUTE_START + 12)
                .slot("main_13", 8 + 4 * SLOT_PX, 126, "main", 13).absoluteIndex(MAIN_ABSOLUTE_START + 13)
                .slot("main_14", 8 + 5 * SLOT_PX, 126, "main", 14).absoluteIndex(MAIN_ABSOLUTE_START + 14)
                .slot("main_15", 8 + 6 * SLOT_PX, 126, "main", 15).absoluteIndex(MAIN_ABSOLUTE_START + 15)
                .slot("main_16", 8 + 7 * SLOT_PX, 126, "main", 16).absoluteIndex(MAIN_ABSOLUTE_START + 16)
                .slot("main_17", 8 + 8 * SLOT_PX, 126, "main", 17).absoluteIndex(MAIN_ABSOLUTE_START + 17)

                // Riga 3 (Assoluto 27-35)
                .slot("main_18", 8 + 0 * SLOT_PX, 144, "main", 18).absoluteIndex(MAIN_ABSOLUTE_START + 18)
                .slot("main_19", 8 + 1 * SLOT_PX, 144, "main", 19).absoluteIndex(MAIN_ABSOLUTE_START + 19)
                .slot("main_20", 8 + 2 * SLOT_PX, 144, "main", 20).absoluteIndex(MAIN_ABSOLUTE_START + 20)
                .slot("main_21", 8 + 3 * SLOT_PX, 144, "main", 21).absoluteIndex(MAIN_ABSOLUTE_START + 21)
                .slot("main_22", 8 + 4 * SLOT_PX, 144, "main", 22).absoluteIndex(MAIN_ABSOLUTE_START + 22)
                .slot("main_23", 8 + 5 * SLOT_PX, 144, "main", 23).absoluteIndex(MAIN_ABSOLUTE_START + 23)
                .slot("main_24", 8 + 6 * SLOT_PX, 144, "main", 24).absoluteIndex(MAIN_ABSOLUTE_START + 24)
                .slot("main_25", 8 + 7 * SLOT_PX, 144, "main", 25).absoluteIndex(MAIN_ABSOLUTE_START + 25)
                .slot("main_26", 8 + 8 * SLOT_PX, 144, "main", 26).absoluteIndex(MAIN_ABSOLUTE_START + 26)

                // Riga 4 (Assoluto 36-44)
                .slot("main_27", 8 + 0 * SLOT_PX, 162, "main", 27).absoluteIndex(MAIN_ABSOLUTE_START + 27)
                .slot("main_28", 8 + 1 * SLOT_PX, 162, "main", 28).absoluteIndex(MAIN_ABSOLUTE_START + 28)
                .slot("main_29", 8 + 2 * SLOT_PX, 162, "main", 29).absoluteIndex(MAIN_ABSOLUTE_START + 29)
                .slot("main_30", 8 + 3 * SLOT_PX, 162, "main", 30).absoluteIndex(MAIN_ABSOLUTE_START + 30)
                .slot("main_31", 8 + 4 * SLOT_PX, 162, "main", 31).absoluteIndex(MAIN_ABSOLUTE_START + 31)
                .slot("main_32", 8 + 5 * SLOT_PX, 162, "main", 32).absoluteIndex(MAIN_ABSOLUTE_START + 32)
                .slot("main_33", 8 + 6 * SLOT_PX, 162, "main", 33).absoluteIndex(MAIN_ABSOLUTE_START + 33)
                .slot("main_34", 8 + 7 * SLOT_PX, 162, "main", 34).absoluteIndex(MAIN_ABSOLUTE_START + 34)
                .slot("main_35", 8 + 8 * SLOT_PX, 162, "main", 35).absoluteIndex(MAIN_ABSOLUTE_START + 35)

                // Riga 5 (Assoluto 45-53)
                .slot("main_36", 8 + 0 * SLOT_PX, 180, "main", 36).absoluteIndex(MAIN_ABSOLUTE_START + 36)
                .slot("main_37", 8 + 1 * SLOT_PX, 180, "main", 37).absoluteIndex(MAIN_ABSOLUTE_START + 37)
                .slot("main_38", 8 + 2 * SLOT_PX, 180, "main", 38).absoluteIndex(MAIN_ABSOLUTE_START + 38)
                .slot("main_39", 8 + 3 * SLOT_PX, 180, "main", 39).absoluteIndex(MAIN_ABSOLUTE_START + 39)
                .slot("main_40", 8 + 4 * SLOT_PX, 180, "main", 40).absoluteIndex(MAIN_ABSOLUTE_START + 40)
                .slot("main_41", 8 + 5 * SLOT_PX, 180, "main", 41).absoluteIndex(MAIN_ABSOLUTE_START + 41)
                .slot("main_42", 8 + 6 * SLOT_PX, 180, "main", 42).absoluteIndex(MAIN_ABSOLUTE_START + 42)
                .slot("main_43", 8 + 7 * SLOT_PX, 180, "main", 43).absoluteIndex(MAIN_ABSOLUTE_START + 43)
                .slot("main_44", 8 + 8 * SLOT_PX, 180, "main", 44).absoluteIndex(MAIN_ABSOLUTE_START + 44)
                .register();

        // ==================== DOUBLE CHEST (CORRETTO OFFSET 54) ====================

        // Uso del helper locale per la Double Chest (6 righe)
        DOUBLE_CHEST = createContainerSlots(
                Guis.builder("game:double_chest", "textures/gui/double_chest.png", 176, 222)
                        .backgroundColor(0.15f, 0.15f, 0.15f, 0.95f)
                        .centeredLabel("Large Chest", 88, 6),
                6, // 6 righe di chest
                18 // startY
        )
                // Player inventory (Indici Assoluti 0-53 ESPLICITI con catena slot())
                .slot("hotbar_0", 8 + 0 * SLOT_PX, 198, "hotbar", 0).absoluteIndex(0)
                .slot("hotbar_1", 8 + 1 * SLOT_PX, 198, "hotbar", 1).absoluteIndex(1)
                .slot("hotbar_2", 8 + 2 * SLOT_PX, 198, "hotbar", 2).absoluteIndex(2)
                .slot("hotbar_3", 8 + 3 * SLOT_PX, 198, "hotbar", 3).absoluteIndex(3)
                .slot("hotbar_4", 8 + 4 * SLOT_PX, 198, "hotbar", 4).absoluteIndex(4)
                .slot("hotbar_5", 8 + 5 * SLOT_PX, 198, "hotbar", 5).absoluteIndex(5)
                .slot("hotbar_6", 8 + 6 * SLOT_PX, 198, "hotbar", 6).absoluteIndex(6)
                .slot("hotbar_7", 8 + 7 * SLOT_PX, 198, "hotbar", 7).absoluteIndex(7)
                .slot("hotbar_8", 8 + 8 * SLOT_PX, 198, "hotbar", 8).absoluteIndex(8)

                // Main Inventory (3 Righe Main mostrate: Assoluto 9-35)
                .slot("main_0", 8 + 0 * SLOT_PX, 140, "main", 0).absoluteIndex(MAIN_ABSOLUTE_START + 0)
                .slot("main_1", 8 + 1 * SLOT_PX, 140, "main", 1).absoluteIndex(MAIN_ABSOLUTE_START + 1)
                .slot("main_2", 8 + 2 * SLOT_PX, 140, "main", 2).absoluteIndex(MAIN_ABSOLUTE_START + 2)
                .slot("main_3", 8 + 3 * SLOT_PX, 140, "main", 3).absoluteIndex(MAIN_ABSOLUTE_START + 3)
                .slot("main_4", 8 + 4 * SLOT_PX, 140, "main", 4).absoluteIndex(MAIN_ABSOLUTE_START + 4)
                .slot("main_5", 8 + 5 * SLOT_PX, 140, "main", 5).absoluteIndex(MAIN_ABSOLUTE_START + 5)
                .slot("main_6", 8 + 6 * SLOT_PX, 140, "main", 6).absoluteIndex(MAIN_ABSOLUTE_START + 6)
                .slot("main_7", 8 + 7 * SLOT_PX, 140, "main", 7).absoluteIndex(MAIN_ABSOLUTE_START + 7)
                .slot("main_8", 8 + 8 * SLOT_PX, 140, "main", 8).absoluteIndex(MAIN_ABSOLUTE_START + 8)

                .slot("main_9", 8 + 0 * SLOT_PX, 158, "main", 9).absoluteIndex(MAIN_ABSOLUTE_START + 9)
                .slot("main_10", 8 + 1 * SLOT_PX, 158, "main", 10).absoluteIndex(MAIN_ABSOLUTE_START + 10)
                .slot("main_11", 8 + 2 * SLOT_PX, 158, "main", 11).absoluteIndex(MAIN_ABSOLUTE_START + 11)
                .slot("main_12", 8 + 3 * SLOT_PX, 158, "main", 12).absoluteIndex(MAIN_ABSOLUTE_START + 12)
                .slot("main_13", 8 + 4 * SLOT_PX, 158, "main", 13).absoluteIndex(MAIN_ABSOLUTE_START + 13)
                .slot("main_14", 8 + 5 * SLOT_PX, 158, "main", 14).absoluteIndex(MAIN_ABSOLUTE_START + 14)
                .slot("main_15", 8 + 6 * SLOT_PX, 158, "main", 15).absoluteIndex(MAIN_ABSOLUTE_START + 15)
                .slot("main_16", 8 + 7 * SLOT_PX, 158, "main", 16).absoluteIndex(MAIN_ABSOLUTE_START + 16)
                .slot("main_17", 8 + 8 * SLOT_PX, 158, "main", 17).absoluteIndex(MAIN_ABSOLUTE_START + 17)

                .slot("main_18", 8 + 0 * SLOT_PX, 176, "main", 18).absoluteIndex(MAIN_ABSOLUTE_START + 18)
                .slot("main_19", 8 + 1 * SLOT_PX, 176, "main", 19).absoluteIndex(MAIN_ABSOLUTE_START + 19)
                .slot("main_20", 8 + 2 * SLOT_PX, 176, "main", 20).absoluteIndex(MAIN_ABSOLUTE_START + 20)
                .slot("main_21", 8 + 3 * SLOT_PX, 176, "main", 21).absoluteIndex(MAIN_ABSOLUTE_START + 21)
                .slot("main_22", 8 + 4 * SLOT_PX, 176, "main", 22).absoluteIndex(MAIN_ABSOLUTE_START + 22)
                .slot("main_23", 8 + 5 * SLOT_PX, 176, "main", 23).absoluteIndex(MAIN_ABSOLUTE_START + 23)
                .slot("main_24", 8 + 6 * SLOT_PX, 176, "main", 24).absoluteIndex(MAIN_ABSOLUTE_START + 24)
                .slot("main_25", 8 + 7 * SLOT_PX, 176, "main", 25).absoluteIndex(MAIN_ABSOLUTE_START + 25)
                .slot("main_26", 8 + 8 * SLOT_PX, 176, "main", 26).absoluteIndex(MAIN_ABSOLUTE_START + 26)
                .register();

        // ==================== CREATIVE INVENTORY ====================

        CREATIVE_INVENTORY = Guis.builder("game:creative_inventory", null, 195, 136)
                .backgroundColor(0.15f, 0.15f, 0.15f, 0.95f)
                .centeredLabel("Creative", 97, 6)
                // Creative slots - scrollable area would be handled by code
                // For now, just basic layout
                .hotbar(8, 112)
                .register();

        // ==================== Fine Registrazione ====================

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