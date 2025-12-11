package engine.registry;

import java.util.Objects;

/**
 * Unique identifier for any registered object.
 * Format: "namespace:path" (e.g., "game:stone", "engine:air")
 * 
 * Immutable and safe to use as HashMap key.
 */
public final class ResourceLocation {
    
    public static final String DEFAULT_NAMESPACE = "game";
    
    private final String namespace;
    private final String path;
    private final String fullPath; // Cached for performance
    
    public ResourceLocation(String namespace, String path) {
        this.namespace = validateNamespace(namespace);
        this.path = validatePath(path);
        this.fullPath = this.namespace + ":" + this.path;
    }
    
    public ResourceLocation(String path) {
        this(DEFAULT_NAMESPACE, path);
    }
    
    /**
     * Parse from string "namespace:path" or just "path" (uses default namespace)
     */
    public static ResourceLocation of(String location) {
        if (location == null || location.isEmpty()) {
            throw new IllegalArgumentException("ResourceLocation cannot be null or empty");
        }
        
        int colonIdx = location.indexOf(':');
        if (colonIdx >= 0) {
            String ns = location.substring(0, colonIdx);
            String path = location.substring(colonIdx + 1);
            return new ResourceLocation(ns, path);
        }
        return new ResourceLocation(location);
    }
    
    /**
     * Try to parse, return null if invalid
     */
    public static ResourceLocation tryParse(String location) {
        try {
            return of(location);
        } catch (Exception e) {
            return null;
        }
    }
    
    private static String validateNamespace(String ns) {
        if (ns == null || ns.isEmpty()) {
            throw new IllegalArgumentException("Namespace cannot be null or empty");
        }
        // Only lowercase letters, numbers, underscores
        for (char c : ns.toCharArray()) {
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                throw new IllegalArgumentException("Invalid namespace: " + ns + " (only a-z, 0-9, _ allowed)");
            }
        }
        return ns;
    }
    
private static String validatePath(String path) {
    if (path == null || path.isEmpty()) {
        throw new IllegalArgumentException("Path cannot be null or empty");
    }
    // Lowercase letters, numbers, underscores, forward slashes, E IL PUNTO.
    for (char c : path.toCharArray()) {
        if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '/' || c == '.')) { // AGGIUNTA QUI
            throw new IllegalArgumentException("Invalid path: " + path + " (only a-z, 0-9, _, /, . allowed)"); // AGGIORNARE ANCHE MESSAGGIO
        }
    }
    return path;
}
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getPath() {
        return path;
    }
    
    @Override
    public String toString() {
        return fullPath;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResourceLocation)) return false;
        ResourceLocation that = (ResourceLocation) o;
        return fullPath.equals(that.fullPath);
    }
    
    @Override
    public int hashCode() {
        return fullPath.hashCode();
    }
    
    /**
     * Create a child location: "game:block" + "stone" = "game:block/stone"
     */
    public ResourceLocation child(String childPath) {
        return new ResourceLocation(namespace, path + "/" + childPath);
    }
    
    /**
     * Check if this location is in a specific namespace
     */
    public boolean isIn(String namespace) {
        return this.namespace.equals(namespace);
    }
}
