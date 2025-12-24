package engine.utils;

import java.io.File;

public class GameStorage {

    private static final String APP_NAME = "VoxelEngine";

    public static File getGameDir() {
        String userHome = System.getProperty("user.home", ".");
        String os = System.getProperty("os.name").toLowerCase();
        File gameDir;

        if (os.contains("win")) {
            // Windows: %APPDATA%/VoxelEngine
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                gameDir = new File(appData, APP_NAME);
            } else {
                gameDir = new File(userHome, "." + APP_NAME);
            }
        } else if (os.contains("mac")) {
            // macOS: ~/Library/Application Support/VoxelEngine
            gameDir = new File(userHome, "Library/Application Support/" + APP_NAME);
        } else {
            // Linux/Unix: ~/.voxelengine
            gameDir = new File(userHome, "." + APP_NAME.toLowerCase());
        }

        if (!gameDir.exists()) {
            gameDir.mkdirs();
        }

        return gameDir;
    }

    public static File getSavesDir() {
        File savesDir = new File(getGameDir(), "saves");
        if (!savesDir.exists()) {
            savesDir.mkdirs();
        }
        return savesDir;
    }

    public static File getLogsDir() {
        File logsDir = new File(getGameDir(), "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        return logsDir;
    }

    /**
     * Get the keybinds configuration file.
     */
    public static File getKeybindsFile() {
        return new File(getGameDir(), "keybinds.json");
    }
}
