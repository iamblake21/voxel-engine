package engine.rendering.model;

import java.util.Map;

/**
 * Data class for deserializing item model JSONs.
 * Format:
 * {
 * "parent": "item/generated",
 * "textures": {
 * "layer0": "items/stick"
 * }
 * }
 */
public class ItemModel {
    public String parent;
    public Map<String, String> textures;
}
