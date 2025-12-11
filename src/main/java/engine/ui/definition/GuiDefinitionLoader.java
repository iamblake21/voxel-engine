package engine.ui.definition;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import engine.registry.ResourceLocation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads GuiDefinition from JSON files.
 * 
 * Expected resource structure:
 *   resources/
 *     gui/
 *       inventory.json
 *       inventory.png
 *       furnace.json
 *       furnace.png
 */
public class GuiDefinitionLoader {
    
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    
    /**
     * Load a GUI definition from resources.
     * 
     * @param id ResourceLocation like "game:inventory"
     * @return Loaded GuiDefinition
     * @throws IOException if file not found or invalid
     */
    public static GuiDefinition load(ResourceLocation id) throws IOException {
        String path = "/gui/" + id.getPath() + ".json";
        
        try (InputStream stream = GuiDefinitionLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new FileNotFoundException("GUI definition not found: " + path);
            }
            
            return load(stream, id);
        }
    }
    
    /**
     * Load from an input stream
     */
    public static GuiDefinition load(InputStream stream, ResourceLocation id) throws IOException {
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            GuiDefinition definition = GSON.fromJson(reader, GuiDefinition.class);
            
            if (definition == null) {
                throw new IOException("Failed to parse GUI definition: " + id);
            }
            
            definition.setId(id);
            
            // Validate
            List<String> errors = definition.validate();
            if (!errors.isEmpty()) {
                System.err.println("[GuiDefinitionLoader] Validation warnings for " + id + ":");
                for (String error : errors) {
                    System.err.println("  - " + error);
                }
            }
            
            System.out.println("[GuiDefinitionLoader] Loaded: " + definition);
            return definition;
            
        } catch (JsonSyntaxException e) {
            throw new IOException("Invalid JSON in GUI definition " + id + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Load from a file path (for editor/development)
     */
    public static GuiDefinition loadFromFile(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString();
        String name = fileName.substring(0, fileName.lastIndexOf('.'));
        ResourceLocation id = ResourceLocation.of(name);
        
        try (InputStream stream = Files.newInputStream(filePath)) {
            return load(stream, id);
        }
    }
    
    /**
     * Load from a JSON string (for testing/editor)
     */
    public static GuiDefinition loadFromString(String json, ResourceLocation id) {
        GuiDefinition definition = GSON.fromJson(json, GuiDefinition.class);
        if (definition != null) {
            definition.setId(id);
        }
        return definition;
    }
    
    /**
     * Save a GUI definition to JSON string
     */
    public static String toJson(GuiDefinition definition) {
        return GSON.toJson(definition);
    }
    
    /**
     * Save a GUI definition to a file
     */
    public static void saveToFile(GuiDefinition definition, Path filePath) throws IOException {
        String json = toJson(definition);
        Files.writeString(filePath, json, StandardCharsets.UTF_8);
        System.out.println("[GuiDefinitionLoader] Saved: " + filePath);
    }
    
    /**
     * Generate Java code from a GUI definition.
     * Useful for migrating from JSON to hardcoded or for reference.
     */
    public static String generateJavaCode(GuiDefinition definition, String className) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("// Auto-generated from ").append(definition.getId()).append("\n\n");
        sb.append("public class ").append(className).append(" extends TexturedGui {\n\n");
        
        // Constructor
        sb.append("    public ").append(className).append("(int windowWidth, int windowHeight) {\n");
        sb.append("        super(Registries.GUIS.getOrDefault(\"")
          .append(definition.getId()).append("\"), windowWidth, windowHeight);\n");
        sb.append("    }\n\n");
        
        // Slot constants
        sb.append("    // Slot indices\n");
        for (GuiSlotDefinition slot : definition.getSlots()) {
            String constName = slot.getId().toUpperCase().replace("_", "_");
            sb.append("    public static final int SLOT_").append(constName)
              .append(" = ").append(slot.getAbsoluteIndex()).append(";\n");
        }
        
        sb.append("}\n");
        
        return sb.toString();
    }
    
    /**
     * Create a default inventory GUI definition (for bootstrapping)
     */
    public static GuiDefinition createDefaultInventory() {
        GuiDefinition def = new GuiDefinition("textures/gui/inventory.png", 176, 166);
        def.setId(ResourceLocation.of("inventory"));
        
        // Add title label
        GuiLabelDefinition title = new GuiLabelDefinition("Inventory", 88, 6);
        title.setId("title");
        title.setCentered(true);
        def.addLabel(title);
        
        // Add hotbar slots (0-8) at bottom
        int hotbarY = 142;
        for (int i = 0; i < 9; i++) {
            GuiSlotDefinition slot = new GuiSlotDefinition(
                "hotbar_" + i,
                8 + i * 18,
                hotbarY,
                "hotbar",
                i
            );
            def.addSlot(slot);
        }
        
        // Add main inventory slots (9-35) - 3 rows
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                GuiSlotDefinition slot = new GuiSlotDefinition(
                    "main_" + index,
                    8 + col * 18,
                    84 + row * 18,
                    "main",
                    index
                );
                def.addSlot(slot);
            }
        }
        
        return def;
    }
}
