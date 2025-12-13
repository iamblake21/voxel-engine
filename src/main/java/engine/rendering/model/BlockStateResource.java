package engine.rendering.model;

import java.util.Map;

/**
 * Represents the JSON structure of a blockstate file.
 * Example: assets/blockstates/door.json
 */
public class BlockStateResource {
    // Map of variant string (e.g. "facing=north,half=lower") to the variant
    // definition
    private Map<String, ModelVariant> variants;

    public Map<String, ModelVariant> getVariants() {
        return variants;
    }

    public void setVariants(Map<String, ModelVariant> variants) {
        this.variants = variants;
    }
}
