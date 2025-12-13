package engine.rendering.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import engine.world.block.Block;
import engine.world.block.state.BlockState;
import engine.world.block.state.property.Property;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class BlockStateLoader {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<Block, BlockStateResource> BLOCKSTATE_CACHE = new HashMap<>();

    private static final BlockStateResource EMPTY_RESOURCE = new BlockStateResource();

    /**
     * Load blockstate definition for a block.
     * Looks for assets/blockstates/{registry_name}.json
     */
    public static void loadBlockState(Block block) {
        if (block.getRegistryId() == null)
            return;

        String path = "/blockstates/" + block.getRegistryId().getPath() + ".json";
        InputStream stream = BlockStateLoader.class.getResourceAsStream(path);

        if (stream == null) {
            // Cache empty resource to avoid checking disk every frame/block
            BLOCKSTATE_CACHE.put(block, EMPTY_RESOURCE);
            return;
        }

        try (InputStreamReader reader = new InputStreamReader(stream)) {
            BlockStateResource resource = GSON.fromJson(reader, BlockStateResource.class);
            BLOCKSTATE_CACHE.put(block, resource);
        } catch (Exception e) {
            System.err.println("Failed to load blockstate for " + block.getRegistryId());
            e.printStackTrace();
            BLOCKSTATE_CACHE.put(block, EMPTY_RESOURCE); // Prevent Retry
        }
    }

    /**
     * Get the model variant for a specific state.
     */
    public static ModelVariant getVariant(BlockState state) {
        Block block = state.getBlock();
        BlockStateResource resource = BLOCKSTATE_CACHE.get(block);

        if (resource == null) {
            // Try loading if not cached (lazy load)
            loadBlockState(block);
            resource = BLOCKSTATE_CACHE.get(block);
            if (resource == null)
                return null;
        }

        if (resource == EMPTY_RESOURCE || resource.getVariants() == null)
            return null;

        // Convert state to string key: "key=value,key2=value2"
        // TreeMap to ensure consistent ordering of keys
        Map<String, String> propertyMap = new TreeMap<>();
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getValues().entrySet()) {
            Property prop = entry.getKey();
            // value is Comparable, prop.name expects T. Raw call works.
            propertyMap.put(prop.getName(), prop.name(entry.getValue()));
        }

        String stateString = propertyMap.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(","));

        // Match exact string (Minecraft 1.12 style)
        // If empty properties, variant is usually "" or "normal"?
        if (stateString.isEmpty()) {
            // Check for "" or "normal" or "default"
            if (resource.getVariants().containsKey(""))
                return resource.getVariants().get("");
            if (resource.getVariants().containsKey("normal"))
                return resource.getVariants().get("normal");
        }

        return resource.getVariants().get(stateString);
    }

    public static void clearCache() {
        BLOCKSTATE_CACHE.clear();
    }
}
