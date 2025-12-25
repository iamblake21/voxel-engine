package game.command;

import engine.command.Command;
import engine.command.CommandSender;
import engine.entity.Player;
import engine.registry.Registries;
import engine.world.item.Item;
import engine.world.item.ItemStack;

public class GiveCommand implements Command {

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            sender.sendMessage("Usage: /give <item_id> [amount]");
            return;
        }

        String itemId = args[0];
        // Allow omitting namespace if "game:"
        if (!itemId.contains(":")) {
            itemId = "game:" + itemId;
        }

        // Fix: Unwrap Optional
        Item item = Registries.ITEMS.get(itemId).orElse(null);
        if (item == null) {
            sender.sendMessage("Unknown item: " + itemId);
            return;
        }

        int amount = 1;
        if (args.length >= 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("Invalid amount: " + args[1]);
                return;
            }
        }

        ItemStack stack = new ItemStack(item, amount);
        player.getInventory().addItem(stack);
        // Fix: Item now has getName()
        sender.sendMessage("Gave " + amount + " " + item.getName() + " to " + sender.getName());
    }
}
