package engine.world.gen;

import engine.registry.Registries;
import engine.registry.ResourceLocation;
import engine.utils.SimpleJson;
import engine.utils.Math3D.Vec3i;
// deleted Logger import
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Loads structures from JSON files.
 */
public class StructureLoader {

    public static void loadStructures() {
        File dataDir = new File("src/main/resources/data/structures");
        // Also check if running from build dir? fallback
        if (!dataDir.exists()) {
            dataDir = new File("build/resources/main/data/structures");
        }

        System.out.println("[StructureLoader] Loading structures from: " + dataDir.getAbsolutePath());

        if (!dataDir.exists()) {
            System.out.println("[StructureLoader] No structure directory found.");
            return;
        }

        File[] files = dataDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            System.out.println("[StructureLoader] No JSON files found.");
            return;
        }

        System.out.println("[StructureLoader] Found " + files.length + " structure files.");

        for (File file : files) {
            loadStructure(file);
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadStructure(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()));
            Map<String, Object> json = (Map<String, Object>) SimpleJson.parse(content);

            String name = file.getName().replace(".json", "");
            ResourceLocation id = new ResourceLocation("game", name); // Defaulting to 'game' namespace

            // Parse Size
            List<Object> sizeList = (List<Object>) json.get("size");
            int w = ((Number) sizeList.get(0)).intValue();
            int h = ((Number) sizeList.get(1)).intValue();
            int d = ((Number) sizeList.get(2)).intValue();

            Structure structure = new Structure(w, h, d);

            // Parse Palette
            List<Object> paletteList = (List<Object>) json.get("palette");
            String[] palette = new String[paletteList.size()];
            for (int i = 0; i < paletteList.size(); i++) {
                palette[i] = (String) paletteList.get(i);
            }

            // Parse Blocks
            List<Object> blocksList = (List<Object>) json.get("blocks");
            for (Object blockObj : blocksList) {
                Map<String, Object> blockMap = (Map<String, Object>) blockObj;
                List<Object> pos = (List<Object>) blockMap.get("pos");
                int x = ((Number) pos.get(0)).intValue();
                int y = ((Number) pos.get(1)).intValue();
                int z = ((Number) pos.get(2)).intValue();

                int state = ((Number) blockMap.get("state")).intValue();

                if (state >= 0 && state < palette.length) {
                    structure.addBlock(x, y, z, palette[state]);
                }
            }

            // Parse Entities (Optional)
            if (json.containsKey("entities")) {
                List<Object> entitiesList = (List<Object>) json.get("entities");
                for (Object entityObj : entitiesList) {
                    Map<String, Object> entityMap = (Map<String, Object>) entityObj;
                    List<Object> pos = (List<Object>) entityMap.get("pos");
                    int x = ((Number) pos.get(0)).intValue();
                    int y = ((Number) pos.get(1)).intValue();
                    int z = ((Number) pos.get(2)).intValue();
                    String entityId = (String) entityMap.get("id");

                    structure.addEntity(x, y, z, entityId);
                }
            }

            // Parse Constraints (Optional)
            if (json.containsKey("constraints")) {
                Map<String, Object> constraints = (Map<String, Object>) json.get("constraints");

                List<String> validGround = null;
                if (constraints.containsKey("valid_ground")) {
                    List<Object> vgList = (List<Object>) constraints.get("valid_ground");
                    validGround = new java.util.ArrayList<>();
                    for (Object o : vgList) {
                        validGround.add((String) o);
                    }
                }

                boolean denyLiquid = false;
                if (constraints.containsKey("deny_liquid")) {
                    denyLiquid = (Boolean) constraints.get("deny_liquid");
                }

                structure.setConstraints(validGround, denyLiquid);
            }

            // Parse Options (Optional)
            if (json.containsKey("options")) {
                Map<String, Object> options = (Map<String, Object>) json.get("options");

                boolean createFoundation = false;
                if (options.containsKey("create_foundation")) {
                    createFoundation = (Boolean) options.get("create_foundation");
                }

                String foundationMaterial = "game:stone";
                if (options.containsKey("foundation_material")) {
                    foundationMaterial = (String) options.get("foundation_material");
                }

                structure.setOptions(createFoundation, foundationMaterial);
            }

            Registries.STRUCTURES.register(id, structure);
            System.out.println("[StructureLoader] Loaded structure: " + id);

        } catch (Exception e) {
            System.err.println("[StructureLoader] Failed to load structure " + file.getName());
            e.printStackTrace();
        }
    }
}
