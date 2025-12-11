package engine.world.blockentity;

/**
 * Interface for block entities that need to update each game tick.
 * 
 * Examples: Furnaces (smelting), hoppers (item transfer), spawners, etc.
 * 
 * Usage:
 *   public class FurnaceBlockEntity extends BlockEntity implements ITickableBlockEntity {
 *       @Override
 *       public void tick() {
 *           // Smelting logic here
 *       }
 *   }
 */
public interface ITickableBlockEntity {
    
    /**
     * Called once per game tick (20 times per second).
     * 
     * Implementations should be efficient as this is called frequently
     * for potentially many block entities.
     */
    void tick();
}
