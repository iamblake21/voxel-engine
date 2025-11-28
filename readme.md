Voxel Engine (Java / LWJGL)

Motore voxel scritto in Java, basato su LWJGL 3.3.3, con mondo procedurale a chunk, generazione multi-thread, livelli di dettaglio (LOD) per i chunk, sistema di blocchi e biomi registrati, fisica di base e un esempio di gioco completo (ExampleGame).

Stato del progetto: motore giocabile e già organizzato in sottosistemi chiari (core, rendering, world, gen, entity, physics, registries). Alcune funzionalità avanzate sono già previste ma solo abbozzate (es. ombre, collider generici, rigid body, materiale di rendering complesso).

Indice

Caratteristiche principali

Requisiti e build

Struttura del progetto

engine.core & engine.api

engine.window

engine.rendering

engine.world & sub-package

engine.entity & engine.physics

engine.registry

game & game.init

Ciclo di vita del motore e interfaccia IGame

Mondo, chunk e generazione procedurale

Rendering, LOD e nebbia

Fisica, entità e giocatore

Input, controlli e toggle di debug

Configurazione

Estendere il motore

Limitazioni e TODO

Caratteristiche principali

Motore voxel Java/LWJGL

Java 17 (configurato tramite toolchain Gradle).

LWJGL 3.3.3 (OpenGL + GLFW) per rendering e input.

Mondo a chunk 3D

Chunk di dimensione 16×256×16 blocchi (Config.chunkSize = 16, Config.worldHeight = 256).

Gestione dei chunk tramite engine.world.World e engine.world.Chunk.

Generazione procedurale avanzata

engine.world.gen.WorldGenerator orchestratE terreno + features.

TerrainGenerator basato su funzioni di densità 3D:

Non solo heightmap: supporta overhangs, grotte e formazioni “strane” (commento in TerrainGenerator).

NoiseRouter ispirato al modello di Minecraft:

Parametri: continentalness, erosion, peaks & valleys, temperature, humidity.

BiomeProvider seleziona i biomi a partire da questi valori di noise.

FeatureGenerator genera alberi e altre feature dopo il terreno.

Biomi e blocchi registrati

Sistema generico di registry (engine.registry.Registry, Registries, ResourceLocation).

Biomi di esempio (game.init.GameBiomes):

PLAINS, FOREST, DESERT, OCEAN, MOUNTAINS (con preset in BiomeProperties).

Blocchi di esempio (game.init.GameBlocks), tra cui:

stone, dirt, grass, wood, leaves, water, ecc.

Biomi/Blocchi dell’engine base (engine.world.block.Blocks, engine.world.biome.Biomes) più contenuti di esempio.

LOD (Level of Detail) per chunk

engine.world.Chunk supporta più livelli di mesh LOD:

LOD 0: dettaglio massimo (una quad per ogni faccia visibile).

LOD 1,2,3: versioni semplificate (celle 2×2, 4×4, 8×8) tramite MeshBuilder/LODMeshBuilder.

Riduce significativamente il numero di triangoli a distanza.

Generazione multi-thread dei chunk

engine.world.WorldGenerationExecutor:

Usa ExecutorService e strutture tipo ConcurrentQueue e Priority per gestire i task.

ChunkGenerationTask contiene (chunkX, chunkZ, priority, blockData, heightMap, complete).

Il main thread sottomette task e recupera i risultati, i worker thread generano i dati del chunk.

Rendering

engine.rendering.Renderer: gestisce intero pipeline:

Rendering chunk (LOD), acqua, mesh trasparenti,

Fog/distanza configurabile,

Culling basato su frustum (engine.rendering.Frustum),

Underwater rendering (colore di clearing modificato se la testa è sott’acqua).

Mesh incapsula VAO/VBO e vertex count.

Shader (non mostrato nei dettagli ma presente) gestisce uniform per fog e trasformazioni.

RenderSettings consente di cambiare a runtime:

distanza di vista, fog, frustum culling, ambient occlusion, preset qualità, ecc.

Fisica ed entità

engine.entity.Entity base per tutti gli oggetti dinamici.

engine.entity.Player con:

Movimento WASD, salto, sprint, volo, interazione con acqua.

Camera in prima persona (engine.rendering.Camera).

engine.physics.PhysicsEngine gestisce:

gravità, movimento, collisioni con i blocchi di World (via Blocks),

calcolo MovementResult (posizione finale, delta effettivo, flag di collisione, onGround).

Input e gestione runtime

engine.window.InputManager per input tastiera e mouse (tramite GLFW).

engine.core.RenderInputHandler per toggle di debug/render in base a tasti funzione (F1/F3/F4/F5, ecc.).

engine.core.GameLoop con fixed timestep per fisica (60Hz) e rendering variabile.

Esempio di gioco incluso

game.ExampleGame implementa IGame e mostra l’uso del motore:

Setup di Config, creazione di World, registrazione contenuti (GameInit),

Creazione player (EntityTypes, EntityType), binding degli input,

Aggiornamento di player e interazione blocchi, gestione RenderInputHandler.

Requisiti e build
Requisiti

Java 17 (configurato via java.toolchain in build.gradle).

Gradle (wrapper incluso: ./gradlew / gradlew.bat).

Sistema compatibile con LWJGL:

Windows, Linux, macOS (con fix -XstartOnFirstThread per macOS già configurato).

Build e run

Nella root del progetto (voxel-engine/):

# Compilazione
./gradlew build

# Esecuzione (usa mainClass = "game.ExampleGame")
./gradlew run


Su Windows:

gradlew.bat run

Struttura del progetto
engine.core & engine.api

engine.core.Config
Configurazione globale del motore. Alcuni parametri rilevanti:

public int windowWidth = 1280;
public int windowHeight = 720;
public String windowTitle = "Voxel Engine";
public boolean vsync = false;
public boolean fullscreen = false;

// Rendering
public float fov = 70f;
public float nearPlane = 0.1f;
public float farPlane = 1000f;
public boolean wireframe = false;

// World
public int chunkSize = 16;
public int worldHeight = 256;
public int viewDistance = 1;
public float waterLevel = 62f;

// Physics
public float gravity = 22f;
public float playerSpeed = 16f;
public float playerSprintMultiplier = 1.8f;
public float playerFlySpeed = 40f;
public float playerHeight = 1.80f;
public float playerEyeHeight = 1.62f;
public float playerWidth = 0.6f;
public float jumpForce = 7.8f;

// Water physics
public float waterBuoyancy = 11f;
public float waterDrag = 3.5f;
public float waterSwimForce = 18f;


Contiene anche worldSeed, maxChunkUpdatesPerFrame, showChunkBorders e un Builder interno con metodo validate() per controllare i parametri.

engine.core.Engine
Classe principale del motore. Coordina:

Window (GLFW),

InputManager,

Renderer,

World,

EntityManager,

PhysicsEngine,

GameLoop.

Espone vari getter (getWindow(), getInput(), getRenderer(), getPhysics(), getEntities(), getWorld(), getGameLoop()) e setWorld(World).

engine.core.GameLoop
Gestisce il ciclo principale con:

Fixed timestep per fisica: FIXED_TIMESTEP = 1f / 60f;

Limite MAX_FRAME_TIME per prevenire “spiral of death”.

Usa GLFW per polling eventi e gestione exit.

engine.core.RenderInputHandler
Gestisce input per render/debug settings. Dal commento:

F1: toggle debug info.

F3: toggle wireframe.

F4: toggle frustum culling.

F5: toggle fog.

+ / = e KP_ADD: aumenta view distance.

- / KP_SUBTRACT: riduce view distance.

Ctrl + 1..4: preset qualità (low/medium/high/extreme).

Si appoggia a RenderSettings e al World per applicare i cambi.

engine.core.Time
Utility per delta time, FPS e gestione temporale usata da GameLoop.

engine.api.IGame
Interfaccia implementata dal gioco:

public interface IGame {

    // Facoltativo, default vuoto
    default void registerContent() {}

    // Chiamato dopo che l'engine è inizializzato
    void init(Engine engine);

    // Chiamato ogni tick
    void update(float deltaTime);

    // Chiamato ogni frame per il rendering del gioco
    void render(Renderer renderer);

    // Pulizia risorse
    void cleanup();
}

engine.window

engine.window.Window
Gestisce creazione e gestione della finestra (GLFW):

Inizializza GLFW.

Crea la finestra con dimensioni/title presi da Config.

Imposta il contesto OpenGL (GL.createCapabilities()).

Esporta handle di finestra (getHandle()) e funzioni shouldClose(), setCursorMode(), ecc.

engine.window.InputManager
Wrapper per input:

Traccia tasti premuti (mappa key → stato).

Gestisce posizione mouse, delta, eventuale lock del cursore.

Usa callback GLFW per aggiornare lo stato.

Espone metodi come isKeyDown(int key), eventuali isMouseButtonDown, ecc. (la logica completa è parzialmente abbreviata, ma la struttura è presente).

engine.rendering

engine.rendering.Renderer
“Main renderer” (come scritto nel commento):

Usa Config, World, Chunk, MeshBuilder, Blocks e Raycast.

Supporta:

LOD per chunk.

Fog/distanza (uniform uFogEnabled, uFogColor, uFogStart, uFogEnd).

Frustum culling.

Colore di sfondo diverso se la testa è sott’acqua (isHeadUnderwater(world)).

Usa Camera (matrici view/projection da Math3D.Mat4).

engine.rendering.RenderSettings
Impostazioni runtime:

viewDistance, minViewDistance, maxViewDistance.

fogEnabled.

frustumCullingEnabled.

ambientOcclusionEnabled.

Campi segnati come “Future feature”:

occlusionCullingEnabled, shadowsEnabled (attualmente non implementati).

Metodi di preset:

applyLowPreset(), applyMediumPreset(), applyHighPreset(), applyFancyPreset(), applyExtremePreset(), ecc.

engine.rendering.Mesh
Gestisce VAO/VBO:

Campi vao, vbo, vertexCount, transparent.

Metodi per allocare buffer, caricare dati e fare glDrawArrays.

cleanup() libera VAO/VBO.

engine.rendering.Camera
Camera in spazio 3D (position, yaw, pitch) e vettori forward, right, up.
Si occupa di aggiornare la view matrix in base a posizione/orientamento del giocatore.

engine.rendering.Frustum
Rappresenta il frustum di vista; usato da World/Renderer per culling.

engine.rendering.WireframeRenderer
Renderer alternativo per modalità wireframe (usato come debug).

engine.rendering.Material
Classe presente ma marcata // TODO: Implement Material (stub, non usata nella logica principale).

engine.world & sub-package
engine.world

engine.world.World
Classe centrale che rappresenta il mondo:

Mantiene una mappa di Chunk indicizzati per coordinate (x,z).

Si occupa di:

Caricare/schedulare i chunk visibili in base alla posizione del player e viewDistance.

Delegare la generazione dei chunk a WorldGenerationExecutor.

Aggiornare i mesh (via MeshBuilder / LODMeshBuilder).

Fornire accesso ai blocchi: getBlock, setBlock, ecc. (metodi non mostrati al completo ma impliciti dalle chiamate).

Usa un adattatore interno ChunkDataAdapter che implementa MeshBuilder.ChunkData per passare i dati chunk al mesh builder.

engine.world.Chunk
Rappresenta un chunk 16×256×16:

Array di blocchi (IDs numerici).

Gestione mesh separate per:

Blocchi solidi,

Trasparenti,

Acqua.

Supporto per più LOD:

Campi tipo solidLOD[0..3], transparentLOD[...], waterLOD[...].

Flag dirty per indicare quando rifare il mesh.

Metodo cleanup() che libera tutte le mesh allocate.

engine.world.WorldGenerationExecutor
Gestore multi-thread:

Tiene un ExecutorService con un certo numero di worker.

Gestisce code/strutture di priorità per i ChunkGenerationTask.

API per sottomettere task e recuperare chunk generati nel main thread.

shutdown() logga quante chunk sono state generate e chiude in modo ordinato il pool.

engine.world.ChunkGenerationTask
Rappresenta un task di generazione:

Campi:

chunkX, chunkZ,

Priority priority,

volatile int[] blockData,

volatile int[] heightMap,

volatile boolean complete.

equals e hashCode basati su (chunkX, chunkZ) per evitare duplicati.

engine.world.block

engine.world.block.Block
Rappresenta un tipo di blocco:

Le istanze sono immutabili dopo la creazione (proprietà settate via BlockProperties).

Integrato con il sistema di registry:

Block.register(String id, Block block) registra in Registries.BLOCKS.

Dopo la registrazione, il blocco riceve un ResourceLocation e un numericId.

engine.world.block.BlockProperties
Builder per definire il comportamento di un blocco:

Flag: solid, opaque, hard, liquidLike(), ecc.

Proprietà di rendering:

Coordinate tile (tile(int x, int y)), multi-texture, tinting (es. tintGrass()).

Metodi helper:

solid(), nonSolid(), airLike(), liquidLike(), replaceable(boolean), ecc.

engine.world.block.MultiTextureBlock
Estende il concetto di blocco per avere texture diverse per facce (top/bottom/side, ecc.).

engine.world.block.Blocks
Helper statico:

Metodi tipo get(String id), get(int numericId).

registerEngineBlocks() per blocchi base del motore.

Usato estensivamente da world, render e gioco.

engine.world.biome

engine.world.biome.Biome
Rappresenta un bioma, in gran parte configurato tramite BiomeProperties.

engine.world.biome.BiomeProperties
Descrive un bioma:

Id dei blocchi di superficie, subsuperficie, underwater, stone, liquid (come stringhe di registry).

Parametri ambientali:

temperature, humidity (0..1),

parametri di variazione del terreno (baseHeight, heightVariation, terrainFrequency, mountainHeight, ecc.).

Metodi preset:

plains(), forest(), desert(), ocean(), mountains().

engine.world.biome.Biomes
Registry helper per biomi:

registerEngineBiomes() crea un bioma di default (engine:default).

Usa Registries.BIOMES.

Setta il bioma di default, loggando il risultato.

engine.world.gen

WorldGenerator
Coordina la generazione:

Tiene seed, chunkSize, chunkHeight, waterLevel.

Usa:

TerrainGenerator per il terreno di base,

FeatureGenerator per caratteristiche come alberi,

BiomeProvider per bioma per coordinate,

NoiseRouter come sorgente di noise.

API:

Generazione di un chunk in base a (chunkX, chunkZ).

Query di temperatura e umidità normalizzate (0-1).

TerrainGenerator
Generatore basato su funzioni di densità 3D:

Commento:

Unlike simple heightmap generation, this:

Calculates density for each 3D position

Solid where density > 0, air where density < 0

Creates overhangs, caves, weird formations

Usa dati di bioma e noise 3D (NoiseRouter) per calcolare densità e heightmap.

Applica successivamente i blocchi di superficie in base al bioma (applySurface).

BiomeProvider
Seleziona il bioma in base ai valori di noise:

Appoggiandosi a NoiseRouter per temperature/humidity.

Normalizza i valori di temperature e humidity a 0..1.

Mapping tipico: biomi land in base a temp/umid, default game:plains se nulla corrisponde.

NoiseRouter, FastNoiseLite, PerlinNoise, DensityFunction, TerrainShaper, NestedTerrainSpline
Componenti per costruire i campi di noise:

NoiseRouter: coordina i canali di noise (continentalness, erosion, peaks&valleys, temperature, humidity, density3D).

FastNoiseLite e PerlinNoise: implementazioni di noise (2D/3D) usate dal router.

TerrainShaper + NestedTerrainSpline: trasformano i valori di noise in altezze/forme coerenti (pianure, montagne, coste).

MeshBuilder & LODMeshBuilder
Si occupano di costruire i mesh dei chunk:

MeshBuilder:

Interfaccia interna ChunkData (usata da World.ChunkDataAdapter).

LOD 0: crea una face per ogni faccia visibile (con luce/ambient occlusion per vertice).

LOD 1/2/3:

Divide il chunk in celle 2×2, 4×4, 8×8.

Calcola CellInfo (maxHeight, minHeight, avgHeight, waterLevel, block dominante, flag hasSolid/hasWater).

Genera geometria semplificata (es. un quad per cella).

LODMeshBuilder è orientato specificamente alla costruzione di mesh LOD (usa logiche simili, ma adattate).

FeatureGenerator
Genera feature di mondo come alberi:

Usa maschere di foresta e densità di base per decidere se piazzare un albero su una cella.

Genera tronco + chioma (“leaf blob”).

engine.entity & engine.physics

engine.entity.Entity
Base astratta per tutte le entità (player, mob, proiettili, item, ecc.):

Ha un ResourceLocation come tipo (collegato alle registries EntityType/EntityTypes).

Mantiene stato di posizione, velocità, ecc. (dettagli del corpo elisi, ma riconoscibili dalle chiamate).

engine.entity.EntityType / engine.entity.EntityTypes
Sistema di registrazione per i tipi di entità.
EntityType incapsula factory e metadata; EntityTypes contiene il registry statico.

engine.entity.EntityManager
Gestisce la lista di entità nel mondo, update e ciclo di vita (aggiunta/rimozione).

engine.entity.Player
Implementa il giocatore:

Dipendenze: Config, Engine, Camera, World, Blocks, PhysicsEngine, InputManager.

Gestione input (semplificata ma evidente dal codice):

W/A/S/D: movimento sul piano in base ai vettori forward/right della camera.

Space: salto (se onGround) o movimento verso l’alto in modalità volo.

Left Shift: sprint (a terra) e movimento verso il basso in volo.

F: legato a qualche modalità (es. toggle volo; la logica interna è abbreviata ma boolean fDown = input.isKeyDown(GLFW_KEY_F); è presente).

1..5: selezione tipo di blocco da posare:

1 → game:stone

2 → game:dirt

3 → game:wood

4 → game:leaves

5 → game:water

Stato acqua:

Flag bodyInWater, headInWater, calcolati tramite funzioni di utility isBlockWater(..) e altezza (playerEyeHeight).

Metodo update(float deltaTime, InputManager input):

Salta se world == null.

handleInput, applyPhysics, updateCamera, world.maintainChunks(x,z).

Metodo handleBlockInteraction(InputManager input):

Chiamato da ExampleGame.update per piazzare/distruggere blocchi (non mostrato integralmente, ma il wiring è presente).

engine.physics.PhysicsEngine
Gestisce la fisica:

Usa Config (gravity, dimensioni player, ecc.) e World per collisioni con blocchi.

Fornisce un metodo tipo move(...) che restituisce MovementResult:

finalX/Y/Z, actualDX/DY/DZ, collidedX/Y/Z, onGround.

La logica è elisa nel sorgente, ma commenti e struttura suggeriscono un semplice swept AABB vs blocchi.

engine.physics.AABB
Rappresenta axis-aligned bounding box per collisioni.

engine.physics.Collider, engine.physics.RigidBody
Presenti ma marcati // TODO: Implement ... (non usati attualmente).

engine.registry

engine.registry.Registry<T>
Registry generico:

Mappa ResourceLocation → T.

Supporta:

Default value per entry mancante.

Numeric IDs per serializzazione.

“freezing” per impedire registrazioni tardive.

Iterazione/stream.

engine.registry.RegistryEntry<T>
Wrapper per un oggetto registrato, include numeric ID.

engine.registry.ResourceLocation
Gestisce gli identificatori namespace:path, con validazione di:

Namespace non vuoto, path non vuoto,

Path con soli caratteri [a-z0-9_\/].

engine.registry.Registries
Collezione di registries principali:

BLOCKS, BIOMES, ENTITIES, ecc.

Metodi per registrare blocchi/biomi dell’engine base.

Usato da EngineBootstrap, Blocks, Biomes, GameInit.

game & game.init

game.init.GameInit
Punto unico di registrazione dei contenuti di gioco:

register():

Chiama GameBlocks.register().

Chiama GameBiomes.register().

(Commentato) GameEntities.register() per futuro.

Flag registered per evitare doppie inizializzazioni.

game.init.GameBlocks
Registra tutti i blocchi del gioco:

Usa BlockProperties per definire proprietà (solidità, trasparenza, texture, ecc.).

Usa Blocks.register(...) per inserire in registry.

game.init.GameBiomes
Registra tutti i biomi del gioco:

PLAINS, FOREST, DESERT, OCEAN, MOUNTAINS.

Usa preset di BiomeProperties (plains(), forest(), desert(), ocean(), mountains()).

Logga il numero totale di biomi in Registries.BIOMES.

game.ExampleGame
Implementazione demo di IGame:

Importa Config, Engine, RenderInputHandler, RenderSettings, EntityType, EntityTypes, Player, World, GameInit.

In init(Engine engine) (parte rilevante):

Configura Config (seed, view distance, ecc. – i dettagli nel file sono abbreviati, ma il tipo di operazioni è chiaro).

Chiama GameInit.register() per contenuti.

Crea World configWorld = new World(config); e lo imposta su engine.setWorld(world);.

Crea il player:

Ottiene un EntityType da EntityTypes,

player.init(engine),

engine.getEntities().addEntity(player).

Setup di RenderSettings:

settings.setViewDistance(config.viewDistance);

renderInputHandler = new RenderInputHandler(settings, world);.

In update(float deltaTime):

Elabora input render: renderInputHandler.processInput(engine.getWindow().getHandle());

player.update(deltaTime, engine.getInput());

player.handleBlockInteraction(engine.getInput());

Ciclo di vita del motore e interfaccia IGame

Bootstrap engine (engine.EngineBootstrap):

init():

Registra contenuti dell’engine (blocchi/biomi base, tipi entità base) usando Registries.

Il gioco registra i propri contenuti (blocchi, biomi, entità) tramite GameInit.register().

freeze():

Blocca i registries (nessun nuovo blocco/bioma/entità dopo questa fase).

Metodo isInitialized() / isFrozen() per controllare stato.

IGame.registerContent() (opzionale, default vuoto)

Ideale per registrare contenuti specifici del gioco prima dell’inizializzazione del mondo (ma nel demo la registrazione è centralizzata in GameInit).

IGame.init(Engine engine)

Si costruisce il World a partire dal Config.

Si aggancia il World all’Engine (engine.setWorld(world)).

Si crea il Player e lo si registra in EntityManager.

Si crea e collega RenderSettings/RenderInputHandler.

Loop di gioco (GameLoop):

Per ogni frame:

Poll input, calcolo deltaTime.

Esegue update logico a fixed timestep:

IGame.update(deltaTime),

update entità, fisica, generazione chunk, ecc.

Esegue render:

Renderer.beginFrame(world),

Renderer.renderWorld(...),

IGame.render(renderer) per HUD e overlay.

Shutdown:

IGame.cleanup() per pulizia risorse di gioco.

Pulizia di World, Renderer, PhysicsEngine, WorldGenerationExecutor.shutdown() ecc.

Mondo, chunk e generazione procedurale
Rappresentazione del mondo

Il mondo usa un grid di chunk (chunkX, chunkZ):

Ogni Chunk è 16×256×16.

World mantiene la mappa dei chunk caricati e la lista dei chunk in pending/generazione.

Il World è responsabile di:

Caricare chunk attorno al player in base alla view distance.

Sottomettere task a WorldGenerationExecutor.

Sostituire chunk generati quando i worker completano.

Fornire funzioni di accesso blocchi per fisica e rendering.

Generazione del chunk

Per ciascun Chunk:

WorldGenerationExecutor crea un ChunkGenerationTask per (chunkX, chunkZ) con una priority.

Un worker thread:

Usa WorldGenerator per calcolare:

int[] blockData (ID numerici dei blocchi),

int[] heightMap.

Riempie i campi task.blockData, task.heightMap, task.complete = true.

Il main thread recupera i task completi e:

Aggiorna/crea il Chunk corrispondente.

Marca il chunk come dirty per la ricostruzione dei mesh.

Pipeline di generazione

All’interno di WorldGenerator:

BiomeProvider:

Per ogni (wx, wz) calcola:

temperature, humidity, continentalness, erosion, peaks & valleys.

Seleziona un Biome.

TerrainGenerator:

Per ogni (x, y, z) nel chunk calcola la densità tramite:

Bias di altezza (dipendente da bioma),

Noise 3D (noiseRouter.getDensity3D),

Parametro di “squashing” per definire versanti/salite/pendenze.

density > 0 → blocco solido; density < 0 → aria.

Genera così:

Grotte, overhangs, montagne, pianure.

Applica successivamente i blocchi di superficie in base a BiomeProperties (surface/subsurface/stone/liquid).

FeatureGenerator:

Dopo il terreno, itera celle e decide se piazzare alberi (e altre feature):

Usa maschere di foresta e funzioni di hash per randomizzare.

Genera tronco (colonna di blocchi wood) e chioma (blob di leaves).

Rendering, LOD e nebbia
LOD per i chunk

MeshBuilder/LODMeshBuilder:

LOD 0:

Per ogni blocco, controlla le facce visibili,

Genera vertici con colori/brightness per simulare ambient occlusion (es. fattori 0.7f/1.0f nelle normali).

LOD 1:

Celle 2×2 blocchi: calcolo di CellInfo e generazione di un quad semplificato.

LOD 2:

Celle 4×4.

LOD 3:

Celle 8×8: chunk distante ridotto a pochissimi quad (ottimo per distanze enormi).

Chunk mantiene mesh separate per ogni LOD e categoria (solid/transparent/water); World e Renderer decidono quale LOD usare in base alla distanza.

Fog / nebbia

Renderer implementa una distance fog:

Campi:

fogEnabled,

fogStart, fogEnd (es. fogStart = 0.6 * viewDistance).

Uniform nel voxel shader:

uFogEnabled, uFogColor, uFogStart, uFogEnd.

Se la testa del player è sott’acqua:

Colore di clear cambia (es. blu più saturo per simulare acqua),

I parametri di fog vengono adattati di conseguenza.

Frustum culling

Frustum + dati di camera sono usati da Renderer/World per evitare di disegnare chunk completamente fuori dal volume visibile.

Opzioni grafiche

Controllate da RenderSettings + RenderInputHandler:

Wireframe (F3).

Fog on/off (F5).

Frustum culling (F4).

Preset qualità (Ctrl + 1..4).

View distance ± ( + / - ).

Campi occlusionCullingEnabled e shadowsEnabled sono marcati come “Future feature” e non hanno implementazione corrente.

Fisica, entità e giocatore
Fisica

PhysicsEngine:

Usa Config.gravity, dimensioni del player, World e Blocks.

Gestisce movimento con step in X/Y/Z, rilevando collisioni con i blocchi solidi (AABB vs voxel).

Ritorna un MovementResult con:

Posizione finale,

Delta effettivi per asse,

Flag collidedX, collidedY, collidedZ, onGround.

AABB:

Rappresenta il volume di collisione del player (e in futuro altre entità).

Usato per sampling dei blocchi circostanti.

Entità

Entity:

Classe astratta base.

Collegata a EntityType/EntityTypes e ResourceLocation.

Pensata per essere estesa (player, mob, item, proiettili).

EntityManager:

Mantiene la lista delle entità.

Fornisce metodi per update, iterazione e rimozione.

Player

Movimento:

Calcola direzione forward e right dalla Camera.

W/S: avanti/indietro.

A/D: strafe sinistro/destro.

Left Shift:

Sprint a terra (playerSprintMultiplier).

Movimento verticale in basso in volo.

Space:

Salto se onGround.

Movimento verso l’alto in modo analogo in volo.

Stato acqua:

Determina se bodyInWater e/o headInWater confrontando la posizione con i blocchi water e le altezze (playerEyeHeight).

Parametri fisici diversi in acqua (waterBuoyancy, waterDrag, waterSwimForce).

Selezione blocco e interazione:

Tasti 1..5 selezionano tipo di blocco da posare (stone, dirt, wood, leaves, water).

Player.handleBlockInteraction() utilizza presumibilmente un raycast (via engine.utils.Raycast) per determinare quale blocco colpire.

Input, controlli e toggle di debug
Movimento e gioco

Dal codice di Player e InputManager:

W / A / S / D: movimento sul piano (dipendente da orientamento della camera).

Space:

Salto a terra.

Movimento verso l’alto in modalità volo.

Left Shift:

Sprint (quando a terra e non in volo).

Movimento verso il basso in volo.

F:

Usato per una funzionalità legata al volo o altra modalità (il codice completo è abbreviato, ma fDown è usato nel gestore input del player).

1..5:

Selezione blocco attivo:

1 → game:stone

2 → game:dirt

3 → game:wood

4 → game:leaves

5 → game:water

Il mouse è gestito da InputManager (posizione/delta), e collegato alla rotazione della camera del player.

Toggle di rendering / debug

Da RenderInputHandler:

F1: toggle debug info.

F3: toggle wireframe.

F4: toggle frustum culling.

F5: toggle fog.

+ / = / keypad +: aumenta view distance (finché ≤ maxViewDistance).

- / keypad -: diminuisce view distance (finché ≥ minViewDistance).

Ctrl + 1..4: preset qualità (low/medium/high/extreme).

Configurazione

La configurazione globale è centralizzata in Config:

Window: windowWidth, windowHeight, windowTitle, vsync, fullscreen.

Rendering: fov, nearPlane, farPlane, wireframe.

World: chunkSize, worldHeight, viewDistance, waterLevel, worldSeed, maxChunkUpdatesPerFrame, showChunkBorders.

Physics: gravità, velocità player, dimensioni player, forza di salto.

Water physics: parametri di galleggiamento e drag.

Sono disponibili:

un Builder interno per costruire una Config validata,

validate() che lancia eccezioni se:

dimensioni finestra ≤ 0,

dimensioni mondo invalidE,

view distance < 1.

RenderSettings fornisce un secondo livello di configurazione dinamica per aspetti grafici a runtime (senza riavviare il motore).

Estendere il motore
Creare un nuovo gioco

Implementare IGame:

public class MyGame implements IGame {
    @Override
    public void registerContent() {
        // Opzionale: registrazione blocchi/biomi/entità custom
    }

    @Override
    public void init(Engine engine) {
        // Creazione Config, World, Player, ecc.
    }

    @Override
    public void update(float deltaTime) {
        // Logica di gioco
    }

    @Override
    public void render(Renderer renderer) {
        // HUD, menu, overlay
    }

    @Override
    public void cleanup() {
        // Pulizia risorse
    }
}


Registrare contenuti di gioco:

Usare pattern simile a GameBlocks e GameBiomes:

Definire blocchi via BlockProperties e registrarli tramite Blocks.register("namespace:id", block).

Definire biomi via BiomeProperties e registrarli via Biomes.register("namespace:biome", biome).

Creare player custom:

Registrare un EntityType custom in EntityTypes.

Implementare una classe che estende Entity (o Player) per logica specifica.

Modificare il main:

Aggiornare mainClass in build.gradle (se si vuole usare un entrypoint diverso da game.ExampleGame).

Aggiungere nuovi blocchi

Esempio (simile a GameBlocks):

Block MY_BLOCK = Blocks.register("mygame:my_block",
    new Block(new BlockProperties()
        .solid()
        .opaque()
        .tile(0, 0)));

Aggiungere nuovi biomi

Esempio (simile a GameBiomes):

Biome MY_BIOME = Biomes.register("mygame:my_biome",
    new Biome(BiomeProperties.create()
        .plains()  // preset di base
        .temperature(0.7f)
        .humidity(0.3f)
        .surfaceBlock("mygame:my_block")));

Estendere la generazione

Si può:

Modificare/estendere FeatureGenerator per aggiungere nuove strutture (villaggi, rocce, ecc.).

Aggiungere nuovi parametri a NoiseRouter/TerrainShaper.

Inserire logica di selezione bioma custom in BiomeProvider.

Limitazioni e TODO

Dal codice e dai commenti emergono alcune aree non completate o in work-in-progress:

engine.physics.Collider, engine.physics.RigidBody:

Presenti ma vuoti (// TODO), non usati nella logica attuale.

engine.rendering.Material:

Definito ma non implementato, non integrato nel pipeline.

engine.utils.Ray:

Stub // TODO: Implement Ray.

Campi di RenderSettings:

occlusionCullingEnabled, shadowsEnabled marcati come Future feature (nessuna implementazione corrente).

File nel package di default (Logger, MathUtils, Physics, Collision, TextRenderer, Ui) sono presenti ma vuoti (placeholder).

Non sono presenti (nel codice fornito):

Sistema di inventario / GUI avanzata per il giocatore.

Networking / multiplayer.

Sistema di salvataggio/persistenza del mondo.

Il motore è tuttavia già strutturato in modo modulare e pronto per essere esteso nelle aree indicate sopra.