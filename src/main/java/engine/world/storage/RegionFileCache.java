package engine.world.storage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RegionFileCache {

    private static final Map<String, RegionFile> regionsByFile = new HashMap<>();

    public static synchronized RegionFile getRegionFile(File basePath, int chunkX, int chunkZ) throws IOException {
        int regionX = chunkX >> 5;
        int regionZ = chunkZ >> 5;
        File regionDir = new File(basePath, "region");
        File file = new File(regionDir, "r." + regionX + "." + regionZ + ".mca");

        String key = file.getAbsolutePath();
        RegionFile regionFile = regionsByFile.get(key);

        if (regionFile == null) {
            regionFile = new RegionFile(file.toPath());
            regionsByFile.put(key, regionFile);
        }

        return regionFile;
    }

    public static synchronized void clear() {
        Iterator<RegionFile> iterator = regionsByFile.values().iterator();
        while (iterator.hasNext()) {
            try {
                iterator.next().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            iterator.remove();
        }
    }
}
