package engine.registry;

import java.util.Optional;

/**
 * A registered entry in a Registry.
 * Contains the object, its ID, and numeric index.
 * 
 * @param <T> Type of registered object
 */
public final class RegistryEntry<T> {
    
    private final ResourceLocation id;
    private final T value;
    private final int numericId;
    
    RegistryEntry(ResourceLocation id, T value, int numericId) {
        this.id = id;
        this.value = value;
        this.numericId = numericId;
    }
    
    public ResourceLocation getId() {
        return id;
    }
    
    public T getValue() {
        return value;
    }
    
    /**
     * Numeric ID for serialization (world save/load).
     * Stable within a registry, but may change between runs if registration order changes.
     */
    public int getNumericId() {
        return numericId;
    }
    
    /**
     * Get value as Optional (never empty for valid entries)
     */
    public Optional<T> asOptional() {
        return Optional.of(value);
    }
    
    @Override
    public String toString() {
        return "RegistryEntry{" + id + " -> " + value.getClass().getSimpleName() + "#" + numericId + "}";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegistryEntry)) return false;
        RegistryEntry<?> that = (RegistryEntry<?>) o;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
