package engine.world.block.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class BlockModelLoader {
    
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, BlockModel> CACHE = new HashMap<>();
    
    /**
     * Carica modello JSON da resources/models/{path}.json
     * Es: load("block/poppy") carica resources/models/block/poppy.json
     */
    public static BlockModel load(String path) {
        if (CACHE.containsKey(path)) {
            return CACHE.get(path);
        }
        
        String fullPath = "/models/" + path + ".json";
        InputStream stream = BlockModelLoader.class.getResourceAsStream(fullPath);
        
        if (stream == null) {
            System.err.println("[BlockModelLoader] Model not found: " + fullPath);
            return null;
        }
        
        try (InputStreamReader reader = new InputStreamReader(stream)) {
            BlockModel model = GSON.fromJson(reader, BlockModel.class);
            
            // Merge con parent se presente
            if (model.parent != null) {
                BlockModel parent = load(model.parent);
                if (parent != null) {
                    model = mergeWithParent(model, parent);
                }
            }
            
            CACHE.put(path, model);
            return model;
            
        } catch (Exception e) {
            System.err.println("[BlockModelLoader] Failed to load: " + fullPath);
            e.printStackTrace();
            return null;
        }
    }
    
    private static BlockModel mergeWithParent(BlockModel child, BlockModel parent) {
        BlockModel merged = new BlockModel();
        
        // Eredita elementi
        merged.elements = (child.elements != null && !child.elements.isEmpty()) 
            ? child.elements : parent.elements;
        
        // Merge textures
        merged.textures = new HashMap<>();
        if (parent.textures != null) merged.textures.putAll(parent.textures);
        if (child.textures != null) merged.textures.putAll(child.textures);
        
        merged.modelType = child.modelType != null ? child.modelType : parent.modelType;
        
        return merged;
    }
    
    public static String resolveTexture(BlockModel model, String texVar) {
        if (!texVar.startsWith("#")) return texVar;
        String key = texVar.substring(1);
        return model.textures != null ? model.textures.getOrDefault(key, "missing") : "missing";
    }
    
    public static void clearCache() {
        CACHE.clear();
    }
}