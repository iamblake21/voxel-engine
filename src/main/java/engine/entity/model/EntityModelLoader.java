package engine.entity.model;

import com.google.gson.*;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;

/**
 * Loads entity models from Blockbench JSON format.
 * Fix: Supporta sia percorsi con namespace che percorsi diretti in resources.
 */
public class EntityModelLoader {
    
    public static EntityModel loadModel(String location) {
        // Preparazione dei percorsi possibili
        
        // 1. Percorso Standard Namespace: "game:models/..." -> "/game/models/..."
        String standardPath = location.replace(':', '/');
        if (!standardPath.startsWith("/")) standardPath = "/" + standardPath;

        // 2. Percorso Diretto (IL TUO CASO): "game:models/..." -> "/models/..."
        // Ignora il namespace "game" e cerca direttamente nella cartella resources
        String directPath = location;
        if (location.contains(":")) {
            directPath = "/" + location.split(":")[1];
        }

        try {
            InputStream in = null;

            // TENTATIVO A: Cerca "/game/models/..." (Standard)
            in = EntityModelLoader.class.getResourceAsStream(standardPath);

            // TENTATIVO B: Cerca "/models/..." (Struttura tua attuale)
            if (in == null) {
                // System.out.println("Trying direct path: " + directPath); // Debug opzionale
                in = EntityModelLoader.class.getResourceAsStream(directPath);
            }

            // TENTATIVO C: Cerca in "/assets/game/models/..." (Compatibilità futura)
            if (in == null) {
                in = EntityModelLoader.class.getResourceAsStream("/assets" + standardPath);
            }

            // Se fallisce tutto...
            if (in == null) {
                System.err.println("[EntityModelLoader] ❌ Model NOT found!");
                System.err.println("   Checked paths:");
                System.err.println("   1. " + standardPath);
                System.err.println("   2. " + directPath + " (User folder structure)");
                return createFallbackModel();
            }
            
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
            in.close();
            
            return parseModel(root, location);
            
        } catch (Exception e) {
            System.err.println("[EntityModelLoader] Failed to load model: " + location);
            e.printStackTrace();
            return createFallbackModel();
        }
    }
    
    private static EntityModel parseModel(JsonObject root, String path) {
        JsonArray geometryArray = root.getAsJsonArray("minecraft:geometry");
        
        if (geometryArray == null) {
             System.err.println("[EntityModelLoader] 'minecraft:geometry' array missing in: " + path);
             return createFallbackModel();
        }
        
        if (geometryArray.size() == 0) {
            return createFallbackModel();
        }
        
        JsonObject geometry = geometryArray.get(0).getAsJsonObject();
        JsonObject desc = geometry.getAsJsonObject("description");
        
        String identifier = "unknown";
        int texWidth = 64;
        int texHeight = 64;

        if (desc != null) {
            identifier = desc.has("identifier") ? desc.get("identifier").getAsString() : "unknown";
            texWidth = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
            texHeight = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;
        }
        
        EntityModel model = new EntityModel(identifier);
        model.setTextureWidth(texWidth);
        model.setTextureHeight(texHeight);
        
        JsonArray bonesArray = geometry.getAsJsonArray("bones");
        if (bonesArray != null) {
            for (JsonElement elem : bonesArray) {
                JsonObject boneJson = elem.getAsJsonObject();
                String name = boneJson.get("name").getAsString();
                ModelBone bone = new ModelBone(name);
                
                if (boneJson.has("pivot")) {
                    JsonArray pivot = boneJson.getAsJsonArray("pivot");
                    bone.setPivot(pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat(), pivot.get(2).getAsFloat());
                }
                
                if (boneJson.has("rotation")) {
                    JsonArray rot = boneJson.getAsJsonArray("rotation");
                    bone.setDefaultRotation(rot.get(0).getAsFloat(), rot.get(1).getAsFloat(), rot.get(2).getAsFloat());
                }
                
                if (boneJson.has("cubes")) {
                    for (JsonElement cubeElem : boneJson.getAsJsonArray("cubes")) {
                        JsonObject cubeJson = cubeElem.getAsJsonObject();
                        ModelCube cube = new ModelCube();
                        
                        if (cubeJson.has("origin")) {
                            JsonArray origin = cubeJson.getAsJsonArray("origin");
                            cube.setOrigin(origin.get(0).getAsFloat(), origin.get(1).getAsFloat(), origin.get(2).getAsFloat());
                        }
                        if (cubeJson.has("size")) {
                            JsonArray size = cubeJson.getAsJsonArray("size");
                            cube.setSize(size.get(0).getAsFloat(), size.get(1).getAsFloat(), size.get(2).getAsFloat());
                        }
                        if (cubeJson.has("uv") && cubeJson.get("uv").isJsonArray()) {
                            JsonArray uv = cubeJson.getAsJsonArray("uv");
                            cube.setUv(uv.get(0).getAsFloat(), uv.get(1).getAsFloat());
                        }
                        if (cubeJson.has("inflate")) cube.setInflate(cubeJson.get("inflate").getAsFloat());
                        if (cubeJson.has("mirror")) cube.setMirror(cubeJson.get("mirror").getAsBoolean());
                        
                        bone.addCube(cube);
                    }
                }
                model.addBone(bone);
            }
            
            // Second pass: parents
            for (JsonElement elem : bonesArray) {
                JsonObject boneJson = elem.getAsJsonObject();
                String name = boneJson.get("name").getAsString();
                if (boneJson.has("parent")) {
                    String parentName = boneJson.get("parent").getAsString();
                    ModelBone bone = model.getBone(name);
                    ModelBone parent = model.getBone(parentName);
                    if (bone != null && parent != null) bone.setParent(parent);
                }
            }
        }
        
        System.out.println("[EntityModelLoader] Loaded: " + identifier + " from " + path);
        return model;
    }
    
    public static void loadAnimations(EntityModel model, String location) {
        // Applichiamo la stessa logica "Smart" anche per le animazioni
        String standardPath = location.replace(':', '/');
        if (!standardPath.startsWith("/")) standardPath = "/" + standardPath;

        String directPath = location;
        if (location.contains(":")) {
            directPath = "/" + location.split(":")[1];
        }

        try {
            InputStream in = EntityModelLoader.class.getResourceAsStream(standardPath);
            if (in == null) in = EntityModelLoader.class.getResourceAsStream(directPath); // Tentativo B
            if (in == null) in = EntityModelLoader.class.getResourceAsStream("/assets" + standardPath);
            
            if (in == null) return; // Niente animazioni, pazienza
            
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
            in.close();
            
            JsonObject animations = root.getAsJsonObject("animations");
            if (animations == null) return;
            
            for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
                String animName = entry.getKey();
                JsonObject animJson = entry.getValue().getAsJsonObject();
                
                EntityAnimation anim = new EntityAnimation(animName);
                if (animJson.has("animation_length")) anim.setDuration(animJson.get("animation_length").getAsFloat());
                if (animJson.has("loop")) anim.setLoop(animJson.get("loop").getAsBoolean());
                
                if (animJson.has("bones")) {
                    for (Map.Entry<String, JsonElement> boneEntry : animJson.getAsJsonObject("bones").entrySet()) {
                        String boneName = boneEntry.getKey();
                        JsonObject boneAnimJson = boneEntry.getValue().getAsJsonObject();
                        AnimationChannel channel = new AnimationChannel();
                        
                        if (boneAnimJson.has("rotation")) parseKeyframes(boneAnimJson.get("rotation"), channel, "rotation");
                        if (boneAnimJson.has("position")) parseKeyframes(boneAnimJson.get("position"), channel, "position");
                        if (boneAnimJson.has("scale")) parseKeyframes(boneAnimJson.get("scale"), channel, "scale");
                        
                        anim.addChannel(boneName, channel);
                    }
                }
                model.addAnimation(animName, anim);
            }
        } catch (Exception e) {
            System.err.println("[EntityModelLoader] Failed to load animations: " + location);
        }
    }

    private static void parseKeyframes(JsonElement elem, AnimationChannel channel, String type) {
        if (elem.isJsonObject()) {
            for (Map.Entry<String, JsonElement> kf : elem.getAsJsonObject().entrySet()) {
                float time = Float.parseFloat(kf.getKey());
                float[] v = parseVector(kf.getValue());
                switch (type) {
                    case "rotation": channel.addRotationKey(time, v[0], v[1], v[2]); break;
                    case "position": channel.addPositionKey(time, v[0], v[1], v[2]); break;
                    case "scale": channel.addScaleKey(time, v[0], v[1], v[2]); break;
                }
            }
        } else if (elem.isJsonArray()) {
            float[] v = parseVector(elem);
            switch (type) {
                case "rotation": channel.addRotationKey(0, v[0], v[1], v[2]); break;
                case "position": channel.addPositionKey(0, v[0], v[1], v[2]); break;
                case "scale": channel.addScaleKey(0, v[0], v[1], v[2]); break;
            }
        }
    }
    
    private static float[] parseVector(JsonElement elem) {
        if (elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
        }
        float v = elem.getAsFloat();
        return new float[]{v, v, v};
    }
    
    private static EntityModel createFallbackModel() {
        EntityModel model = new EntityModel("fallback");
        ModelBone body = new ModelBone("body");
        body.setPivot(0, 12, 0);
        ModelCube cube = new ModelCube();
        cube.setOrigin(-4, 0, -4);
        cube.setSize(8, 24, 8);
        body.addCube(cube);
        model.addBone(body);
        return model;
    }
}