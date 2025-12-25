package engine.command;

/**
 * Interface for any entity that can send commands (Player, Console,
 * CommandBlock).
 */
public interface CommandSender {
    /**
     * Send a message to the sender.
     */
    void sendMessage(String text);

    /**
     * Check if sender has permission to execute a command.
     */
    boolean hasPermission(String node);

    /**
     * Get the name of the sender.
     */
    String getName();
}
