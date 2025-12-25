package game.init;

import engine.command.CommandManager;
import engine.world.World;
import game.command.GiveCommand;
import game.command.TimeCommand;

public class GameCommands {

    public static void register(CommandManager manager, World world) {
        manager.register("give", new GiveCommand());
        manager.register("time", new TimeCommand(world));
        System.out.println("[Game] Commands Registered");
    }
}
