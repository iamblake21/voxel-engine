package engine.world.block;

/**
 * Block with different textures for different faces.
 * Used for grass (green top, dirt bottom, side texture) and similar blocks.
 */
public class MultiTextureBlock extends Block {
    
    private final int topTileX, topTileY;
    private final int bottomTileX, bottomTileY;
    private final int sideTileX, sideTileY;
    
    public MultiTextureBlock(BlockProperties properties, 
                             int topX, int topY,
                             int bottomX, int bottomY,
                             int sideX, int sideY) {
        super(properties);
        this.topTileX = topX;
        this.topTileY = topY;
        this.bottomTileX = bottomX;
        this.bottomTileY = bottomY;
        this.sideTileX = sideX;
        this.sideTileY = sideY;
    }
    
    @Override
    public int getTextureTileX(int normalX, int normalY, int normalZ) {
        if (normalY == 1) return topTileX;      // Top face
        if (normalY == -1) return bottomTileX;  // Bottom face
        return sideTileX;                        // Side faces
    }
    
    @Override
    public int getTextureTileY(int normalX, int normalY, int normalZ) {
        if (normalY == 1) return topTileY;
        if (normalY == -1) return bottomTileY;
        return sideTileY;
    }
    
    /**
     * Builder for cleaner construction
     */
    public static Builder builder(BlockProperties properties) {
        return new Builder(properties);
    }
    
    public static class Builder {
        private final BlockProperties properties;
        private int topX = 0, topY = 0;
        private int bottomX = 0, bottomY = 0;
        private int sideX = 0, sideY = 0;
        
        public Builder(BlockProperties properties) {
            this.properties = properties;
        }
        
        public Builder top(int x, int y) {
            this.topX = x;
            this.topY = y;
            return this;
        }
        
        public Builder bottom(int x, int y) {
            this.bottomX = x;
            this.bottomY = y;
            return this;
        }
        
        public Builder side(int x, int y) {
            this.sideX = x;
            this.sideY = y;
            return this;
        }
        
        public Builder allSame(int x, int y) {
            this.topX = this.bottomX = this.sideX = x;
            this.topY = this.bottomY = this.sideY = y;
            return this;
        }
        
        public MultiTextureBlock build() {
            return new MultiTextureBlock(properties, topX, topY, bottomX, bottomY, sideX, sideY);
        }
    }
}
