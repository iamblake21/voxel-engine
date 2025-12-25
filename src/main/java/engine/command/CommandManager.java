package engine.command;

import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public class CommandManager {

    private final Map<String, Command> commands = new HashMap<>();

    public void register(String name, Command command) {
        commands.put(name.toLowerCase(), command);
    }

    public boolean dispatch(CommandSender sender, String input) {
        if (!input.startsWith("/")) {
            return false;
        }

        String cleanInput = input.substring(1).trim();
        if (cleanInput.isEmpty())
            return false;

        String[] parts = cleanInput.split("\\s+");
        String commandName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        Command cmd = commands.get(commandName);
        if (cmd != null) {
            try {
                cmd.execute(sender, args);
            } catch (Exception e) {
                sender.sendMessage("Error executing command: " + e.getMessage());
                e.printStackTrace();
            }
            return true;
        } else {
            sender.sendMessage("Unknown command: " + commandName);
            return true;
        }
    }
}
