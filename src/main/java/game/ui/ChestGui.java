package game.ui;



import engine.entity.Player;

import engine.entity.inventory.PlayerInventory;

import engine.registry.Registries;

import engine.ui.ContainerGui;

import engine.ui.definition.GuiDefinition;

import engine.ui.definition.GuiSlotDefinition;

import engine.world.item.ItemStack;

import game.blockentity.ChestBlockEntity;



import java.util.Optional;



public class ChestGui extends ContainerGui {

   

    private static final int CHEST_SLOTS = 27;

    private final ChestBlockEntity chest;

   

    public ChestGui(Player player, ChestBlockEntity chest, int windowWidth, int windowHeight) {

        super(loadDefinition(), player, chest, windowWidth, windowHeight);

        this.chest = chest;

    }

   

    private static GuiDefinition loadDefinition() {

        Optional<GuiDefinition> def = Registries.GUIS.get("game:chest");

        if (def.isPresent()) {

            return def.get();

        }

        throw new IllegalStateException("[ChestGui] No chest GUI definition found!");

    }

   

    @Override

    protected ItemStack getStackForSlot(int absoluteIndex) {

        GuiSlotDefinition slotDef = findSlotByAbsoluteIndex(absoluteIndex);

        if (slotDef == null) return ItemStack.EMPTY;

       

        String type = slotDef.getType();

        int index = slotDef.getIndex();

       

        return switch (type) {

            case "container" -> chest.getItem(index);

            case "hotbar" -> playerInventory.getHotbarStack(index);

            case "main" -> playerInventory.getMainStack(index);

            default -> ItemStack.EMPTY;

        };

    }

   

    @Override

    protected void setStackInSlot(int absoluteIndex, ItemStack stack) {

        GuiSlotDefinition slotDef = findSlotByAbsoluteIndex(absoluteIndex);

        if (slotDef == null) return;

       

        String type = slotDef.getType();

        int index = slotDef.getIndex();

       

        switch (type) {

            case "container" -> chest.setItem(index, stack);

            case "hotbar" -> playerInventory.setHotbarStack(index, stack);

            case "main" -> playerInventory.setMainStack(index, stack);

        }

    }

   

    private GuiSlotDefinition findSlotByAbsoluteIndex(int absoluteIndex) {

        for (GuiSlotDefinition slot : definition.getSlots()) {

            if (slot.getAbsoluteIndex() == absoluteIndex) {

                return slot;

            }

        }

        return null;

    }

   

    @Override

    protected int getContainerSlotCount() {

        return CHEST_SLOTS;

    }

}

