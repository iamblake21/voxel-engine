package game.command;

import engine.command.Command;
import engine.command.CommandSender;
import engine.entity.Player;
import engine.world.World;

public class TimeCommand implements Command {

    private final World world;

    public TimeCommand(World world) {
        this.world = world;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (world == null) {
            sender.sendMessage("World not loaded.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("Usage: /time set <day|night|midnight|noon|value>");
            return;
        }

        String sub = args[0];
        if (!sub.equalsIgnoreCase("set")) {
            sender.sendMessage("Usage: /time set <value>");
            return;
        }

        String valueStr = args[1].toLowerCase();
        long newTime = 0;

        switch (valueStr) {
            case "day":
                newTime = 7000; // Morning (Sunrise is 6000)
                break;
            case "noon":
                newTime = 12000; // Zenith (0.5)
                break;
            case "night":
                newTime = 19000; // Night
                break;
            case "midnight":
                newTime = 0; // Midnight (0.0)
                break;
            default:
                try {
                    newTime = Long.parseLong(valueStr);
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid time value: " + valueStr);
                    return;
                }
        }

        world.setTime(newTime);
        sender.sendMessage("Set specific time to " + newTime);
    }
}
