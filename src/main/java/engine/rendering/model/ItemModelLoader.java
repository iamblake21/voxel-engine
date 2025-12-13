package engine.rendering.model;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class ItemModelLoader {

    private static final Gson GSON = new Gson();
    private static final Map<String, ItemModel> CACHE = new HashMap<>();

    public static ItemModel load(String path) {
        // Normalize path: items/torch -> models/item/torch.json
        String location = path;
        if (!location.endsWith(".json")) {
            location = "models/item/" + location + ".json";
        }

        if (CACHE.containsKey(location)) {
            return CACHE.get(location);
        }

        try (InputStream in = ItemModelLoader.class.getClassLoader().getResourceAsStream(location)) {
            if (in == null) {
                // If specific item model missing, return NULL (renderer handles fallback)
                System.err.println("[ItemModelLoader] Model not found: " + location);
                CACHE.put(location, null);
                return null;
            }

            try (Reader reader = new InputStreamReader(in)) {
                ItemModel model = GSON.fromJson(reader, ItemModel.class);
                CACHE.put(location, model);
                return model;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
