package engine.command;

/**
 * Interface for a command execution.
 */
public interface Command {
    /**
     * Execute the command.
     * 
     * @param sender The entity that sent the command.
     * @param args   Arguments passed to the command.
     */
    void execute(CommandSender sender, String[] args);
}
