Assolutamente\! Ecco un file `README.md` ben formattato basato sul testo fornito, strutturato con Markdown per una facile lettura.

-----

# ⛏️ Voxel Engine (Java / LWJGL)

Motore voxel nativo scritto in **Java 17**, basato su **LWJGL 3.3.3** (OpenGL + GLFW). Progettato con una struttura modulare per la creazione di mondi a blocchi procedurali.

## ✨ Caratteristiche Principali

  * **Mondo a Chunk 3D:** Gestione basata su chunk $16 \times 256 \times 16$ gestiti da `engine.world.World`.
  * **Generazione Procedurale Avanzata:**
      * Generazione multi-thread dei chunk (`WorldGenerationExecutor`).
      * `TerrainGenerator` basato su **funzioni di densità 3D** (supporto per grotte, overhangs, e formazioni complesse).
      * `NoiseRouter` ispirato a Minecraft (continentalness, erosion, temperature, humidity, etc.).
      * Sistema di biomi (`BiomeProvider`) e feature (`FeatureGenerator` per alberi).
  * **Livelli di Dettaglio (LOD) per Chunk:**
      * Supporto per LOD 0 (dettaglio massimo) fino a LOD 3 (celle $8 \times 8$).
      * Riduce significativamente il conteggio dei triangoli a distanza.
  * **Registri Generici:** Sistema di `Registry` (blocchi, biomi, entità) per contenuti registrati e serializzazione.
  * **Rendering:** Pipeline con gestione di LOD, **Frustum Culling**, nebbia configurabile (`RenderSettings`) e rendering subacqueo.
  * **Fisica ed Entità:** Fisica di base (`PhysicsEngine`) con collisioni AABB vs voxel, gravità, e un'implementazione completa del **Giocatore** (`Player`) con movimento, sprint, salto e volo.
  * **Esempio di Gioco Completo:** Include `game.ExampleGame` che mostra l'uso dell'interfaccia principale `IGame` del motore.

-----

## 🏗️ Struttura del Progetto

Il motore è organizzato in sottosistemi chiari e modulari:

| Sottosistema | Package Principale | Responsabilità |
| :--- | :--- | :--- |
| **Core & API** | `engine.core`, `engine.api` | Ciclo di vita (`GameLoop`), configurazione (`Config`), interfaccia di gioco (`IGame`). |
| **Mondo & Generazione** | `engine.world`, `engine.world.gen` | Struttura dei chunk, logica di generazione procedurale (`WorldGenerator`, `NoiseRouter`). |
| **Rendering** | `engine.rendering` | Pipeline di rendering, telecamera, LOD, frustum, impostazioni grafiche (`RenderSettings`). |
| **Entità & Fisica** | `engine.entity`, `engine.physics` | Entità di base, player, motore fisico (`PhysicsEngine`), collisioni AABB. |
| **Registri** | `engine.registry` | Sistema per registrare blocchi, biomi, entità con ID numerici e `ResourceLocation`. |

> **Nota sullo Stato:** Il motore è **giocabile**. Alcune funzionalità avanzate (ombre, `RigidBody`, materiali complessi) sono previste ma solo abbozzate con classi placeholder (`// TODO: Implement...`).

-----

## ⚙️ Requisiti e Build

### Requisiti

  * **Java 17** (Configurato tramite `java.toolchain` in `build.gradle`).
  * **Gradle** (Wrapper incluso: `./gradlew` o `gradlew.bat`).
  * Sistema operativo compatibile con LWJGL (Windows, Linux, macOS).

### Build e Run

Esegui i comandi dalla directory radice del progetto (`voxel-engine/`):

| Piattaforma | Compilazione | Esecuzione (Demo) |
| :--- | :--- | :--- |
| **Linux/macOS** | `./gradlew build` | `./gradlew run` |
| **Windows** | `gradlew.bat build` | `gradlew.bat run` |

L'esecuzione predefinita avvia la classe `game.ExampleGame`.

-----

## 🕹️ Input, Controlli e Configurazione

### Controlli di Gioco

| Tasto | Funzione |
| :--- | :--- |
| **W/A/S/D** | Movimento (Avanti, Sinistra, Indietro, Destra) |
| **Mouse** | Rotazione della telecamera (Look) |
| **Space** | Salto (a terra) / Movimento verticale su (in volo) |
| **Left Shift** | Sprint (a terra) / Movimento verticale giù (in volo) |
| **1 - 5** | Selezione del blocco attivo per interazione (stone, dirt, wood, leaves, water) |

### Toggle di Debug e Rendering

Gestiti da `engine.core.RenderInputHandler`:

  * **F1:** Toggle delle informazioni di debug (FPS, chunk, ecc.).
  * **F3:** Toggle della modalità **wireframe**.
  * **F4:** Toggle del **Frustum Culling**.
  * **F5:** Toggle della **nebbia (Fog)**.
  * **+ / -:** Aumenta/diminuisce la **distanza di vista** (`viewDistance`).
  * **Ctrl + 1..4:** Preset di qualità grafica (Low/Medium/High/Extreme).

### Configurazione Globale

Il file `engine.core.Config` centralizza tutti i parametri del motore, tra cui:

  * `windowWidth`, `windowHeight`, `windowTitle`, `vsync`.
  * `fov`, `nearPlane`, `farPlane` (Rendering).
  * `chunkSize` (16), `worldHeight` (256), `viewDistance`, `worldSeed`.
  * `gravity`, `playerSpeed`, `jumpForce` (Fisica).

-----

## 🧩 Estendere il Motore

Il motore è progettato per essere esteso tramite il sistema di registrazione e l'interfaccia `IGame`.

### Creare un Nuovo Gioco

È sufficiente implementare l'interfaccia `engine.api.IGame`:

```java
public class MyGame implements IGame {
    @Override
    public void init(Engine engine) {
        // ... Logica di inizializzazione (Config, World, Player)
    }

    @Override
    public void update(float deltaTime) {
        // ... Logica di gioco per ogni tick
    }
    // ... Altri metodi (render, cleanup)
}
```

### Aggiungere Contenuti (Blocchi/Biomi)

I nuovi contenuti devono essere registrati in una fase di *bootstrap* del gioco (simile a `game.init.GameInit`) utilizzando i registry:

1.  **Blocco di Esempio:**
    ```java
    Block MY_BLOCK = Blocks.register("mygame:my_block",
        new Block(new BlockProperties().solid().opaque().tile(0, 0)));
    ```
2.  **Bioma di Esempio:**
    ```java
    Biome MY_BIOME = Biomes.register("mygame:my_biome",
        new Biome(BiomeProperties.create().plains() // preset di base
            .temperature(0.7f).humidity(0.3f).surfaceBlock("mygame:my_block")));
    ```

-----

## 🚧 Limitazioni e TODO

Di seguito le aree che non sono ancora complete o che sono segnate come lavoro futuro:

| Area | Componenti TODO | Descrizione |
| :--- | :--- | :--- |
| **Fisica Avanzata** | `engine.physics.Collider`, `engine.physics.RigidBody` | Classi placeholder. La fisica attuale gestisce solo collisioni AABB vs Voxel. |
| **Rendering Avanzato** | `engine.rendering.Material` | Classe presente ma non implementata o integrata nel pipeline principale. |
| **Funzionalità Future** | `RenderSettings` campi `occlusionCullingEnabled`, `shadowsEnabled` | Campi di configurazione dinamica presenti, ma senza implementazione corrente. |
| **Assenti** | - | Sistema di inventario/GUI avanzata, networking/multiplayer, salvataggio/persistenza del mondo. |

Il motore è tuttavia strutturato per accogliere queste estensioni in futuro.