package game.block;

import engine.entity.Player;
import engine.ui.ContainerGui;
import engine.ui.GuiProvider;
import engine.world.World;
import engine.world.block.MultiTextureBlock;
import game.ui.CraftingGui;
import game.ui.CraftingInventory;

public class CraftingTableBlock extends MultiTextureBlock implements GuiProvider {

    public CraftingTableBlock(engine.world.block.BlockProperties properties) {
        // Top (11,3), Bottom (4,0), Side (12,3)
        super(properties, 11, 3, 4, 0, 12, 3);
    }

    @Override
    public boolean onInteract(World world, int x, int y, int z, Player player) {
        player.getInteractionManager().openGui(player, this);
        return true;
    }

    // We need to verify if InteractionManager supports opening GUI from Block (not
    // BlockEntity).
    // If not, we need to modify InteractionManager to check Block implements
    // GuiProvider too.

    @Override
    public ContainerGui createGui(Player player, int width, int height) {
        return new CraftingGui(player, new CraftingInventory(), width, height);
    }
}
