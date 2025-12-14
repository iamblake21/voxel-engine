package game.init;

import engine.world.biome.Biome;
import engine.world.biome.BiomeProperties;
import engine.world.biome.Biomes;

/**
 * All biomes for the game.
 */
public final class GameBiomes {

    public static Biome PLAINS;
    public static Biome FOREST;
    public static Biome DESERT;
    public static Biome OCEAN;
    public static Biome BEACH;
    public static Biome MOUNTAINS;

    private GameBiomes() {
    }

    public static void register() {
        System.out.println("[GameBiomes] Registering biomes...");

        // Plains - vere pianure: piatte, poche variazioni
        PLAINS = Biomes.register("game:plains",
                new Biome(BiomeProperties.create()
                        .plains() // preset pianura (baseHeight ~60, var bassa)
                        .surfaceBlock("game:grass")
                        .subsurfaceBlock("game:dirt")
                        .stoneBlock("game:stone")));

        PLAINS.addStructure(new engine.world.gen.WeightedStructure("game:small_house", 1, 0.02f));
        PLAINS.addStructure(new engine.world.gen.WeightedStructure("game:big_oak_tree", 1, 0.10f));
        // Forest - colline morbide, più alberi
        FOREST = Biomes.register("game:forest",
                new Biome(BiomeProperties.create()
                        .forest() // preset forest (più variazione)
                        .surfaceBlock("game:grass")
                        .subsurfaceBlock("game:dirt")
                        .stoneBlock("game:stone")
                        .treeDensity(0.8f)));

        FOREST.addStructure(new engine.world.gen.WeightedStructure("game:big_oak_tree", 1, 2f));

        // Desert - sabbia, caldo, niente alberi
        DESERT = Biomes.register("game:desert",
                new Biome(BiomeProperties.create()
                        .desert()
                        .surfaceBlock("game:sand")
                        .subsurfaceBlock("game:sand")
                        .underwaterBlock("game:sand")
                        .stoneBlock("game:stone")));

        // Ocean - fondale sabbioso sotto il livello dell'acqua
        OCEAN = Biomes.register("game:ocean",
                new Biome(BiomeProperties.create()
                        .ocean()
                        .surfaceBlock("game:sand")
                        .subsurfaceBlock("game:sand")
                        .underwaterBlock("game:sand")
                        .stoneBlock("game:stone")
                        .liquidBlock("game:water")));

        // Beach - fascia bassa tra oceano e terra
        BEACH = Biomes.register("game:beach",
                new Biome(BiomeProperties.create()
                        .surfaceBlock("game:sand")
                        .subsurfaceBlock("game:sand")
                        .stoneBlock("game:stone")
                        .treeDensity(0.0f)
                        .grassDensity(0.0f)
                        // base bassa e quasi piatta
                        .terrain(62f, 2f)
                        .terrainFrequency(0.002f)
                        .mountainHeight(0f)));

        // Mountains - montagne alte e rocciose
        MOUNTAINS = Biomes.register("game:mountains",
                new Biome(BiomeProperties.create()
                        .mountains() // preset montagne (base 72, var 24, mountainHeight ~80)
                        .surfaceBlock("game:snow") // niente erba in cima per ora
                        .subsurfaceBlock("game:stone")
                        .stoneBlock("game:stone")));

        System.out.println("[GameBiomes] Registered " +
                engine.registry.Registries.BIOMES.size() + " biomes total");
    }
}
