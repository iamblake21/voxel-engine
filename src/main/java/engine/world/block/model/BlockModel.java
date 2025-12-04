package engine.world.block.model;

import java.util.List;
import java.util.Map;

public class BlockModel {
    public String parent;
    public String modelType;
    public Map<String, String> textures;
    public List<ModelElement> elements;
    
    public static class ModelElement {
        public float[] from;  // [x, y, z] min (0-16)
        public float[] to;    // [x, y, z] max (0-16)
        public Rotation rotation;
        public Map<String, Face> faces;
        public boolean shade = true;
        
        public static class Rotation {
            public float[] origin;
            public String axis;
            public float angle;
            public boolean rescale = false;
        }
        
        public static class Face {
            public float[] uv;  // [u1, v1, u2, v2] in pixel (0-16)
            public String texture;
            public String cullface;
            public int rotation = 0;
            public int tintindex = -1;
        }
    }
}