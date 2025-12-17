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
        public static Biome SNOWY_TAIGA;
        public static Biome TAIGA;
        public static Biome SAVANNA;
        public static Biome JUNGLE;
        public static Biome CHERRY_GROVE;

        private GameBiomes() {
        }

        public static void register() {
                System.out.println("[GameBiomes] Registering biomes...");

                // 1. REGISTER BIOME CONTENTS (The "What")

                // Plains
                Biome plainsBiome = new Biome(BiomeProperties.create()
                                .plains()
                                .surfaceBlock("game:grass")
                                .subsurfaceBlock("game:dirt")
                                .stoneBlock("game:stone"));
                PLAINS = Biomes.register("game:plains", plainsBiome);
                PLAINS.addStructure(new engine.world.gen.WeightedStructure("game:big_oak_tree", 1, 3.0f));

                // Forest
                Biome forestBiome = new Biome(BiomeProperties.create()
                                .forest()
                                .surfaceBlock("game:grass")
                                .subsurfaceBlock("game:dirt")
                                .stoneBlock("game:stone")
                                .treeDensity(0.8f));
                FOREST = Biomes.register("game:forest", forestBiome);
                FOREST.addStructure(new engine.world.gen.WeightedStructure("game:big_oak_tree", 1, 2f));

                // Desert
                Biome desertBiome = new Biome(BiomeProperties.create()
                                .desert()
                                .surfaceBlock("game:sand")
                                .subsurfaceBlock("game:sand")
                                .underwaterBlock("game:sand")
                                .stoneBlock("game:stone"));
                DESERT = Biomes.register("game:desert", desertBiome);

                // Ocean
                Biome oceanBiome = new Biome(BiomeProperties.create()
                                .ocean()
                                .surfaceBlock("game:sand")
                                .subsurfaceBlock("game:sand")
                                .underwaterBlock("game:sand")
                                .stoneBlock("game:stone")
                                .liquidBlock("game:water"));
                OCEAN = Biomes.register("game:ocean", oceanBiome);

                // Beach
                Biome beachBiome = new Biome(BiomeProperties.create()
                                .surfaceBlock("game:sand")
                                .subsurfaceBlock("game:sand")
                                .stoneBlock("game:stone")
                                .treeDensity(0.0f)
                                .grassDensity(0.0f)
                                .terrain(62f, 2f)
                                .terrainFrequency(0.002f)
                                .mountainHeight(0f));
                BEACH = Biomes.register("game:beach", beachBiome);

                // Mountains
                Biome mountainsBiome = new Biome(BiomeProperties.create()
                                .mountains()
                                .surfaceBlock("game:snow")
                                .subsurfaceBlock("game:stone")
                                .stoneBlock("game:stone"));
                MOUNTAINS = Biomes.register("game:mountains", mountainsBiome);

                // Snowy Taiga
                Biome snowyTaigaBiome = new Biome(BiomeProperties.create()
                                .forest()
                                .surfaceBlock("game:snow")
                                .subsurfaceBlock("game:dirt")
                                .stoneBlock("game:stone")
                                .temperature(-0.5f)
                                .humidity(0.4f)
                                .treeDensity(0.5f));
                SNOWY_TAIGA = Biomes.register("game:snowy_taiga", snowyTaigaBiome);
                SNOWY_TAIGA.addStructure(new engine.world.gen.WeightedStructure("game:spruce_tree", 1, 1.0f));

                // Taiga
                Biome taigaBiome = new Biome(BiomeProperties.create()
                                .forest()
                                .surfaceBlock("game:grass")
                                .subsurfaceBlock("game:dirt")
                                .stoneBlock("game:stone")
                                .temperature(-0.3f)
                                .humidity(0.7f)
                                .grassColor(0xFF6BA971) // Brighter, more vivid green
                                .foliageColor(0xFF5A8E5F));
                TAIGA = Biomes.register("game:taiga", taigaBiome);
                TAIGA.addStructure(new engine.world.gen.WeightedStructure("game:spruce_tree", 1, 3.0f));

                // Savanna
                Biome savannaBiome = new Biome(BiomeProperties.create()
                                .plains() // Flat-ish like plains
                                .surfaceBlock("game:grass")
                                .subsurfaceBlock("game:dirt")
                                .stoneBlock("game:stone")
                                .temperature(1.2f)
                                .humidity(0.0f)
                                .grassColor(0xFFBFB755) // Brownish yellow-green
                                .foliageColor(0xFFAEAB5E));
                SAVANNA = Biomes.register("game:savanna", savannaBiome);
                SAVANNA.addStructure(new engine.world.gen.WeightedStructure("game:acacia_tree", 1, 0.5f));

                // Jungle
                Biome jungleBiome = new Biome(BiomeProperties.create()
                                .forest()
                                .surfaceBlock("game:grass")
                                .subsurfaceBlock("game:dirt")
                                .stoneBlock("game:stone")
                                .temperature(0.95f)
                                .humidity(0.9f)
                                .treeDensity(1.0f) // Very dense
                                .grassColor(0xFF59C93C) // Vibrant bright green
                                .foliageColor(0xFF30BB0B));
                JUNGLE = Biomes.register("game:jungle", jungleBiome);
                JUNGLE.addStructure(new engine.world.gen.WeightedStructure("game:huge_jungle_tree", 1, 4.0f));

                // Cherry Grove
                Biome cherryBiome = new Biome(BiomeProperties.create()
                                .plains() // Flat/Rolling
                                .surfaceBlock("game:grass")
                                .subsurfaceBlock("game:dirt")
                                .stoneBlock("game:stone")
                                .temperature(0.3f)
                                .humidity(0.4f)
                                .grassColor(0xFF7DFF4F) // Green, slightly saturated
                                .foliageColor(0xFFF4C2D6)); // Pastel Pink
                CHERRY_GROVE = Biomes.register("game:cherry_grove", cherryBiome);
                CHERRY_GROVE.addStructure(new engine.world.gen.WeightedStructure("game:big_oak_tree", 1, 1.0f));

                // 2. DEFINE PLACEMENT (The "Where") - Map Pins
                // (Continentalness, Erosion, Temp, Humid, Weirdness)

                // Plains (Center-ish)
                Biomes.addPoint(PLAINS, 0.0f, 0.0f, 0.1f, 0.0f, 0.0f);

                // Forest - Standard
                Biomes.addPoint(FOREST, 0.0f, 0.0f, 0.1f, 0.4f, 0.0f);
                // Forest - Variant: Cooler but humid
                Biomes.addPoint(FOREST, 0.0f, 0.0f, 0.0f, 0.5f, 0.0f);

                // Desert (Hot and Dry) -> Moved slightly away to preserve mountains
                Biomes.addPoint(DESERT, 0.1f, 0.0f, 0.7f, -0.6f, 0.0f);

                // Ocean
                Biomes.addPoint(OCEAN, -0.8f, 0.0f, 0.5f, 0.5f, 0.0f);

                // Beach
                Biomes.addPoint(BEACH, -0.4f, 0.0f, 0.5f, 0.5f, 0.0f);

                // Mountains
                Biomes.addPoint(MOUNTAINS, 0.6f, -0.6f, 0.0f, 0.0f, 0.0f);

                // Snowy Taiga
                Biomes.addPoint(SNOWY_TAIGA, 0.6f, -0.5f, -0.5f, 0.4f, 0.0f);

                // Taiga
                Biomes.addPoint(TAIGA, 0.4f, 0.4f, -0.3f, 0.7f, 0.0f);

                // Savanna (Hot and Moderate Dry) -> Moved slightly away
                Biomes.addPoint(SAVANNA, 0.0f, 0.0f, 0.6f, -0.2f, 0.0f);

                // Jungle (Hot and Wet)
                Biomes.addPoint(JUNGLE, 0.0f, 0.0f, 0.6f, 0.6f, 0.0f);

                // Cherry Grove (Mild and Pleasant)
                Biomes.addPoint(CHERRY_GROVE, 0.2f, 0.0f, 0.2f, 0.4f, 0.0f);

                System.out.println("[GameBiomes] Registered " +
                                engine.registry.Registries.BIOMES.size() + " biomes total");
        }
}
