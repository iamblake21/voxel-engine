package engine.registry;

import java.util.*;
import java.util.function.Consumer;

/**
 * Generic registry for game objects (blocks, entities, biomes, etc.)
 * 
 * Features:
 * - Type-safe registration and lookup
 * - Default value for missing entries
 * - Numeric IDs for serialization
 * - Freezing to prevent late registration
 * - Iteration and streaming
 * 
 * @param <T> Type of objects in this registry
 */
public class Registry<T> implements Iterable<RegistryEntry<T>> {
    
    private final ResourceLocation registryId;
    private final Map<ResourceLocation, RegistryEntry<T>> byId;
    private final Map<T, RegistryEntry<T>> byValue;
    private final List<RegistryEntry<T>> byNumericId;
    
    private RegistryEntry<T> defaultEntry;
    private boolean frozen;
    
    /**
     * Create a new registry
     * @param registryId Unique ID for this registry (e.g., "engine:blocks")
     */
    public Registry(ResourceLocation registryId) {
        this.registryId = registryId;
        this.byId = new LinkedHashMap<>(); // Preserve insertion order
        this.byValue = new IdentityHashMap<>();
        this.byNumericId = new ArrayList<>();
        this.frozen = false;
    }
    
    public Registry(String registryId) {
        this(ResourceLocation.of(registryId));
    }
    
    /**
     * Register an object with an ID.
     * 
     * @param id Unique identifier
     * @param value Object to register
     * @return RegistryEntry for the registered object
     * @throws IllegalStateException if registry is frozen or ID already exists
     */
    public RegistryEntry<T> register(ResourceLocation id, T value) {
        if (frozen) {
            throw new IllegalStateException(
                "Registry '" + registryId + "' is frozen. Cannot register: " + id);
        }
        if (byId.containsKey(id)) {
            throw new IllegalArgumentException(
                "Duplicate registration in '" + registryId + "': " + id);
        }
        if (byValue.containsKey(value)) {
            throw new IllegalArgumentException(
                "Object already registered in '" + registryId + "': " + value);
        }
        
        int numericId = byNumericId.size();
        RegistryEntry<T> entry = new RegistryEntry<>(id, value, numericId);
        
        byId.put(id, entry);
        byValue.put(value, entry);
        byNumericId.add(entry);
        
        return entry;
    }
    
    /**
     * Register with string ID (uses default namespace)
     */
    public RegistryEntry<T> register(String id, T value) {
        return register(ResourceLocation.of(id), value);
    }
    
    /**
     * Set the default entry (returned when lookup fails).
     * Must be called AFTER registering the default value.
     */
    public void setDefault(ResourceLocation id) {
        RegistryEntry<T> entry = byId.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("Cannot set default to unregistered ID: " + id);
        }
        this.defaultEntry = entry;
    }
    
    public void setDefault(String id) {
        setDefault(ResourceLocation.of(id));
    }
    
    // ==================== LOOKUP METHODS ====================
    
    /**
     * Get entry by ID, or empty if not found
     */
    public Optional<RegistryEntry<T>> getEntry(ResourceLocation id) {
        return Optional.ofNullable(byId.get(id));
    }
    
    public Optional<RegistryEntry<T>> getEntry(String id) {
        return getEntry(ResourceLocation.of(id));
    }
    
    /**
     * Get value by ID, or empty if not found
     */
    public Optional<T> get(ResourceLocation id) {
        RegistryEntry<T> entry = byId.get(id);
        return entry != null ? Optional.of(entry.getValue()) : Optional.empty();
    }
    
    public Optional<T> get(String id) {
        return get(ResourceLocation.of(id));
    }
    
    /**
     * Get value by ID, or default if not found
     */
    public T getOrDefault(ResourceLocation id) {
        RegistryEntry<T> entry = byId.get(id);
        if (entry != null) {
            return entry.getValue();
        }
        if (defaultEntry != null) {
            return defaultEntry.getValue();
        }
        throw new NoSuchElementException("No entry and no default for: " + id + " in " + registryId);
    }
    
    public T getOrDefault(String id) {
        return getOrDefault(ResourceLocation.of(id));
    }
    
    /**
     * Get value by numeric ID (for deserialization)
     */
    public Optional<T> getByNumericId(int numericId) {
        if (numericId < 0 || numericId >= byNumericId.size()) {
            return Optional.empty();
        }
        return Optional.of(byNumericId.get(numericId).getValue());
    }
    
    /**
     * Get value by numeric ID, or default
     */
    public T getByNumericIdOrDefault(int numericId) {
        if (numericId >= 0 && numericId < byNumericId.size()) {
            return byNumericId.get(numericId).getValue();
        }
        if (defaultEntry != null) {
            return defaultEntry.getValue();
        }
        throw new NoSuchElementException("No entry for numeric ID " + numericId + " and no default in " + registryId);
    }
    
    /**
     * Get entry for a value
     */
    public Optional<RegistryEntry<T>> getEntryFor(T value) {
        return Optional.ofNullable(byValue.get(value));
    }
    
    /**
     * Get ID for a value
     */
    public Optional<ResourceLocation> getId(T value) {
        RegistryEntry<T> entry = byValue.get(value);
        return entry != null ? Optional.of(entry.getId()) : Optional.empty();
    }
    
    /**
     * Get numeric ID for a value
     */
    public int getNumericId(T value) {
        RegistryEntry<T> entry = byValue.get(value);
        if (entry != null) {
            return entry.getNumericId();
        }
        if (defaultEntry != null) {
            return defaultEntry.getNumericId();
        }
        return -1;
    }
    
    // ==================== QUERY METHODS ====================
    
    /**
     * Check if ID is registered
     */
    public boolean contains(ResourceLocation id) {
        return byId.containsKey(id);
    }
    
    public boolean contains(String id) {
        return contains(ResourceLocation.of(id));
    }
    
    /**
     * Check if value is registered
     */
    public boolean containsValue(T value) {
        return byValue.containsKey(value);
    }
    
    /**
     * Get number of registered entries
     */
    public int size() {
        return byId.size();
    }
    
    /**
     * Check if empty
     */
    public boolean isEmpty() {
        return byId.isEmpty();
    }
    
    /**
     * Get all registered IDs
     */
    public Set<ResourceLocation> getIds() {
        return Collections.unmodifiableSet(byId.keySet());
    }
    
    /**
     * Get all registered values
     */
    public Collection<T> getValues() {
        List<T> values = new ArrayList<>(byNumericId.size());
        for (RegistryEntry<T> entry : byNumericId) {
            values.add(entry.getValue());
        }
        return Collections.unmodifiableList(values);
    }
    
    /**
     * Get default entry (may be null)
     */
    public Optional<RegistryEntry<T>> getDefaultEntry() {
        return Optional.ofNullable(defaultEntry);
    }
    
    /**
     * Get default value
     */
    public Optional<T> getDefault() {
        return defaultEntry != null ? Optional.of(defaultEntry.getValue()) : Optional.empty();
    }
    
    // ==================== LIFECYCLE ====================
    
    /**
     * Freeze the registry - no more registrations allowed
     */
    public void freeze() {
        this.frozen = true;
        System.out.println("[Registry] " + registryId + " frozen with " + size() + " entries");
    }
    
    /**
     * Check if frozen
     */
    public boolean isFrozen() {
        return frozen;
    }
    
    /**
     * Get registry ID
     */
    public ResourceLocation getRegistryId() {
        return registryId;
    }
    
    // ==================== ITERATION ====================
    
    @Override
    public Iterator<RegistryEntry<T>> iterator() {
        return Collections.unmodifiableList(byNumericId).iterator();
    }
    
    /**
     * For-each over entries
     */
    @Override
    public void forEach(Consumer<? super RegistryEntry<T>> action) {
        Objects.requireNonNull(action);
        for (RegistryEntry<T> entry : byNumericId) {
            action.accept(entry);
        }
    }

    
    /**
     * For-each over values only
     */
    public void forEachValue(Consumer<T> action) {
        for (RegistryEntry<T> entry : byNumericId) {
            action.accept(entry.getValue());
        }
    }
    
    @Override
    public String toString() {
        return "Registry{" + registryId + ", size=" + size() + ", frozen=" + frozen + "}";
    }
}
