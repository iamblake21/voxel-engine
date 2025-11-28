package engine.world.biome;

/**
 * Properties for a biome.
 * All blocks are referenced by ID string - resolved at runtime.
 * This allows biomes to be defined before blocks exist.
 */
public class BiomeProperties {
    
    // Terrain blocks (as registry IDs, resolved at runtime)
    String surfaceBlock = "game:grass";      // Top block
    String subsurfaceBlock = "game:dirt";    // Under surface
    String underwaterBlock = "game:sand";    // Underwater surface
    String stoneBlock = "game:stone";        // Deep/base block
    String liquidBlock = "game:water";       // Ocean/lake liquid
    
    // Climate
    float temperature = 0.5f;    // 0 = frozen, 1 = hot
    float humidity = 0.5f;       // 0 = desert, 1 = swamp
    
    // Features
    float treeDensity = 0.3f;    // 0 = none, 1 = dense forest
    float grassDensity = 0.5f;   // 0 = none, 1 = full coverage
    
    // Terrain shape (base livelli e variazione locale)
    float baseHeight = 64f;      // Base terrain height
    float heightVariation = 8f;  // How much terrain varies

    // Terrain frequency & mountains (usate dal TerrainGenerator)
    float terrainFrequency = 0.002f; // scala del noise per questo bioma
    float mountainHeight   = 0f;     // extra altezza massima per montagne
    float mountainSharpness = 1.0f;  // >1 = montagne più appuntite
    
    // Colors (ARGB format, though A is typically ignored)
    int grassColor = 0xFF7CDB51;   // Default grass green
    int foliageColor = 0xFF6BBF48; // Default foliage green
    int waterColor = 0xFF3F76E4;   // Default water blue

    private BiomeProperties() {}
    
    public static BiomeProperties create() {
        return new BiomeProperties();
    }
    
    // ==================== TERRAIN BLOCKS ====================
    
    public BiomeProperties surfaceBlock(String id) {
        this.surfaceBlock = id;
        return this;
    }
    
    public BiomeProperties subsurfaceBlock(String id) {
        this.subsurfaceBlock = id;
        return this;
    }
    
    public BiomeProperties underwaterBlock(String id) {
        this.underwaterBlock = id;
        return this;
    }
    
    public BiomeProperties stoneBlock(String id) {
        this.stoneBlock = id;
        return this;
    }
    
    public BiomeProperties liquidBlock(String id) {
        this.liquidBlock = id;
        return this;
    }
    
    // ==================== CLIMATE ====================
    
    public BiomeProperties temperature(float temp) {
        this.temperature = clamp(temp, 0f, 1f);
        return this;
    }
    
    public BiomeProperties humidity(float humidity) {
        this.humidity = clamp(humidity, 0f, 1f);
        return this;
    }
    
    public BiomeProperties climate(float temperature, float humidity) {
        return temperature(temperature).humidity(humidity);
    }
    
    // ==================== FEATURES ====================
    
    public BiomeProperties treeDensity(float density) {
        this.treeDensity = clamp(density, 0f, 1f);
        return this;
    }
    
    public BiomeProperties grassDensity(float density) {
        this.grassDensity = clamp(density, 0f, 1f);
        return this;
    }
    
    // ==================== TERRAIN SHAPE ====================
    
    public BiomeProperties baseHeight(float height) {
        this.baseHeight = height;
        return this;
    }
    
    public BiomeProperties heightVariation(float variation) {
        this.heightVariation = variation;
        return this;
    }
    
    public BiomeProperties terrain(float baseHeight, float variation) {
        this.baseHeight = baseHeight;
        this.heightVariation = variation;
        return this;
    }

    /**
     * Scala del noise per questo bioma (più alta = variazioni più ravvicinate).
     */
    public BiomeProperties terrainFrequency(float frequency) {
        this.terrainFrequency = frequency;
        return this;
    }

    /**
     * Altezza massima aggiuntiva per le montagne di questo bioma.
     * (es. 0 per pianure, 60–120 per montagne)
     */
    public BiomeProperties mountainHeight(float height) {
        this.mountainHeight = height;
        return this;
    }

    /**
     * Quanto sono appuntite le montagne (1 = morbide, 3 = molto verticali).
     */
    public BiomeProperties mountainSharpness(float sharpness) {
        this.mountainSharpness = sharpness;
        return this;
    }
    
    // ==================== COLORS ====================    
    public BiomeProperties grassColor(int rgb) {
        this.grassColor = rgb | 0xFF000000; // Ensure alpha
        return this;
    }
    
    public BiomeProperties grassColor(int r, int g, int b) {
        return grassColor((r << 16) | (g << 8) | b);
    }
    
    public BiomeProperties foliageColor(int rgb) {
        this.foliageColor = rgb | 0xFF000000;
        return this;
    }
    
    public BiomeProperties foliageColor(int r, int g, int b) {
        return foliageColor((r << 16) | (g << 8) | b);
    }
    
    public BiomeProperties waterColor(int rgb) {
        this.waterColor = rgb | 0xFF000000;
        return this;
    }
    
    public BiomeProperties waterColor(int r, int g, int b) {
        return waterColor((r << 16) | (g << 8) | b);
    }
    
    // ==================== PRESETS ====================

    /**
     * Preset for plains biome: pianure larghe e piatte.
     */
    public BiomeProperties plains() {
        this.temperature = 0.5f;
        this.humidity = 0.5f;
        this.treeDensity = 0.05f;
        this.grassDensity = 0.8f;

        this.baseHeight = 60f;
        this.heightVariation = 4f;
        this.terrainFrequency = 0.0015f;  // colline larghissime
        this.mountainHeight = 0f;         // niente montagne
        this.mountainSharpness = 1.0f;
        return this;
    }
    
    /**
     * Preset for forest biome: colline morbide, un po' di variazione.
     */
    public BiomeProperties forest() {
        this.temperature = 0.5f;
        this.humidity = 0.7f;
        this.treeDensity = 0.6f;
        this.grassDensity = 0.6f;

        this.baseHeight = 62f;
        this.heightVariation = 10f;
        this.terrainFrequency = 0.0020f;
        this.mountainHeight = 20f;      // colline un po' più alte
        this.mountainSharpness = 1.4f;
        return this;
    }
    
    /**
     * Preset for desert biome: dune morbide, terreno piuttosto piatto.
     */
    public BiomeProperties desert() {
        this.surfaceBlock = "game:sand";
        this.subsurfaceBlock = "game:sand";
        this.underwaterBlock = "game:sand";
        this.temperature = 0.95f;
        this.humidity = 0.0f;
        this.treeDensity = 0.0f;
        this.grassDensity = 0.0f;

        this.baseHeight = 58f;
        this.heightVariation = 5f;
        this.terrainFrequency = 0.0018f;
        this.mountainHeight = 8f;       // dune un po' più alte
        this.mountainSharpness = 1.2f;

        this.grassColor = 0xFFBFB755;
        this.foliageColor = 0xFFAEA42A;
        return this;
    }
    
    /**
     * Preset for snowy biome: colline fredde, un po' irregolari.
     */
    public BiomeProperties snowy() {
        this.temperature = 0.0f;
        this.humidity = 0.4f;
        this.treeDensity = 0.15f;
        this.grassDensity = 0.1f;

        this.baseHeight = 66f;
        this.heightVariation = 12f;
        this.terrainFrequency = 0.0022f;
        this.mountainHeight = 30f;
        this.mountainSharpness = 1.6f;

        this.grassColor = 0xFF80B497;
        this.foliageColor = 0xFF60A17B;
        return this;
    }
    
    /**
     * Preset for ocean biome: terreno basso, quasi piatto.
     */
    public BiomeProperties ocean() {
        this.surfaceBlock = "game:sand";
        this.subsurfaceBlock = "game:sand";
        this.underwaterBlock = "game:sand";
        this.temperature = 0.5f;
        this.humidity = 0.9f;
        this.treeDensity = 0.0f;
        this.grassDensity = 0.0f;

        this.baseHeight = 40f;          // fondale marino
        this.heightVariation = 4f;
        this.terrainFrequency = 0.0015f;
        this.mountainHeight = 0f;
        this.mountainSharpness = 1.0f;
        return this;
    }
    
    /**
     * Preset for mountains: montagne alte (120–130 blocchi) con valli dolci.
     */
    public BiomeProperties mountains() {
        this.temperature = 0.3f;
        this.humidity = 0.3f;
        this.treeDensity = 0.1f;
        this.grassDensity = 0.3f;

        this.baseHeight = 72f;          // base delle montagne
        this.heightVariation = 24f;     // colline alla base
        this.terrainFrequency = 0.0040f; // terreno più frastagliato

        return this;
    }
    
    // ==================== UTILITY ====================    
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    // ==================== GETTERS ====================    
    public String getSurfaceBlock()   { return surfaceBlock; }
    public String getSubsurfaceBlock(){ return subsurfaceBlock; }
    public String getUnderwaterBlock(){ return underwaterBlock; }
    public String getStoneBlock()     { return stoneBlock; }
    public String getLiquidBlock()    { return liquidBlock; }
    public float getTemperature()     { return temperature; }
    public float getHumidity()        { return humidity; }
    public float getTreeDensity()     { return treeDensity; }
    public float getGrassDensity()    { return grassDensity; }
    public float getBaseHeight()      { return baseHeight; }
    public float getHeightVariation() { return heightVariation; }
    public float getTerrainFrequency(){ return terrainFrequency; }
    public float getMountainHeight()  { return mountainHeight; }
    public float getMountainSharpness(){ return mountainSharpness; }
    public int getGrassColor()        { return grassColor; }
    public int getFoliageColor()      { return foliageColor; }
    public int getWaterColor()        { return waterColor; }
}
