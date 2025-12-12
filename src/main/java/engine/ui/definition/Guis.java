package engine.ui.definition;

import engine.registry.Registries;
import engine.registry.RegistryEntry;
import engine.registry.ResourceLocation;

import java.util.Optional;

/**
 * Helper class for GUI registration and lookup.
 * Similar to Items helper for item registration.
 */
public final class Guis {

    private Guis() {
    } // No instantiation

    /**
     * Register a GUI definition.
     */
    public static GuiDefinition register(String id, GuiDefinition definition) {
        ResourceLocation resLoc = ResourceLocation.of(id);
        definition.setId(resLoc);

        RegistryEntry<GuiDefinition> entry = Registries.GUIS.register(resLoc, definition);

        System.out.println("[Guis] Registered: " + id);
        return definition;
    }

    /**
     * Register a GUI definition loaded from JSON resources.
     */
    public static GuiDefinition registerFromJson(String id) {
        try {
            ResourceLocation resLoc = ResourceLocation.of(id);
            GuiDefinition definition = GuiDefinitionLoader.load(resLoc);

            Registries.GUIS.register(resLoc, definition);

            System.out.println("[Guis] Registered from JSON: " + id);
            return definition;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load GUI definition: " + id, e);
        }
    }

    /**
     * Register a GUI with a builder pattern.
     */
    public static GuiBuilder builder(String id, String texture, int width, int height) {
        return new GuiBuilder(id, texture, width, height);
    }

    // ==================== LOOKUP METHODS ====================

    /**
     * Get a GUI definition by ID
     */
    public static Optional<GuiDefinition> get(String id) {
        return Registries.GUIS.get(id);
    }

    /**
     * Get a GUI definition or throw if not found
     */
    public static GuiDefinition getOrThrow(String id) {
        return Registries.GUIS.get(id).orElseThrow(
                () -> new IllegalArgumentException("No GUI registered with ID: " + id));
    }

    /**
     * Check if a GUI is registered
     */
    public static boolean exists(String id) {
        return Registries.GUIS.contains(id);
    }

    // ==================== BUILDER ====================

    /**
     * Builder for programmatic GUI definition creation.
     */
    public static class GuiBuilder {
        private final String id;
        private final GuiDefinition definition;
        // Tracciamo l'ultimo slot solo per il metodo .absoluteIndex()
        private GuiSlotDefinition lastSlot = null; 

        private int slotCounter = 0;

        GuiBuilder(String id, String texture, int width, int height) {
            this.id = id;
            this.definition = new GuiDefinition(texture, width, height);
        }

        /**
         * Set background color (used if no texture)
         */
        public GuiBuilder backgroundColor(float r, float g, float b, float a) {
            definition.setBackgroundColor(r, g, b, a);
            return this;
        }

        /**
         * Add a slot (Traccia l'ultimo slot creato)
         */
        public GuiBuilder slot(String slotId, int x, int y, String type, int index) {
            GuiSlotDefinition newSlot = new GuiSlotDefinition(slotId, x, y, type, index);
            definition.addSlot(newSlot);
            this.lastSlot = newSlot; 
            return this;
        }
        
        /**
         * IMPOSTA L'INDICE ASSOLUTO SULL'ULTIMO SLOT AGGIUNTO.
         */
        public GuiBuilder absoluteIndex(int absoluteIndex) {
            if (lastSlot != null) {
                lastSlot.setAbsoluteIndex(absoluteIndex);
                this.lastSlot = null;
            }
            return this;
        }


        /**
         * Add a row of slots (9 slots like hotbar or inventory row)
         * USA IL FALLBACK PER L'INDICE ASSOLUTO (non lo imposta)
         */
        public GuiBuilder slotRow(String typePrefix, int startX, int y, String type, int startIndex, int count) {
            for (int i = 0; i < count; i++) {
                String slotId = typePrefix + "_" + (startIndex + i);
                definition.addSlot(new GuiSlotDefinition(slotId, startX + i * 18, y, type, startIndex + i));
            }
            this.lastSlot = null;
            return this;
        }
        
        /** NUOVO METODO: Aggiunge una riga di slot con indice assoluto esplicito. */
        public GuiBuilder slotRow(String typePrefix, int startX, int y, String type, int startIndex, int absoluteStart, int count) {
            for (int i = 0; i < count; i++) {
                String slotId = typePrefix + "_" + (startIndex + i);
                
                GuiSlotDefinition newSlot = new GuiSlotDefinition(slotId, startX + i * 18, y, type, startIndex + i);
                newSlot.setAbsoluteIndex(absoluteStart + i); // APPLICA L'INDICE ASSOLUTO ESPLICITO
                
                definition.addSlot(newSlot);
            }
            this.lastSlot = null;
            return this;
        }


        /**
         * Add hotbar slots (9 slots) - USA IL NUOVO OVERLOAD
         */
        public GuiBuilder hotbar(int startX, int y) {
            // Hotbar ha sempre indice assoluto 0-8
            return slotRow("hotbar", startX, y, "hotbar", 0, 0, 9);
        }

        /**
         * Add main inventory (3 rows of 9 slots) - USA IL VECCHIO OVERLOAD, non usare in GUIs composte.
         */
        public GuiBuilder mainInventory(int startX, int startY) {
            for (int row = 0; row < 3; row++) {
                slotRow("main", startX, startY + row * 18, "main", row * 9, 9);
            }
            return this;
        }

        /**
         * Add a label
         */
        public GuiBuilder label(String text, int x, int y) {
            GuiLabelDefinition label = new GuiLabelDefinition(text, x, y);
            definition.addLabel(label);
            return this;
        }

        /**
         * Add a centered label
         */
        public GuiBuilder centeredLabel(String text, int x, int y) {
            GuiLabelDefinition label = new GuiLabelDefinition(text, x, y);
            label.setCentered(true);
            definition.addLabel(label);
            return this;
        }

        /**
         * Build and register the GUI definition
         */
        public GuiDefinition register() {
            return Guis.register(id, definition);
        }

        /**
         * Build without registering (for testing)
         */
        public GuiDefinition build() {
            definition.setId(ResourceLocation.of(id));
            return definition;
        }
    }
}