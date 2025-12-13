package engine.world;

public enum Direction implements engine.utils.StringRepresentable {
    DOWN(0, 0, -1, 0, "down"),
    UP(1, 0, 1, 0, "up"),
    NORTH(2, 0, 0, -1, "north"),
    SOUTH(3, 0, 0, 1, "south"),
    WEST(4, -1, 0, 0, "west"),
    EAST(5, 1, 0, 0, "east");

    private final int id;
    private final int x;
    private final int y;
    private final int z;
    private final String name;

    Direction(int id, int x, int y, int z, String name) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Direction getOpposite() {
        switch (this) {
            case DOWN:
                return UP;
            case UP:
                return DOWN;
            case NORTH:
                return SOUTH;
            case SOUTH:
                return NORTH;
            case WEST:
                return EAST;
            case EAST:
                return WEST;
            default:
                throw new IllegalStateException();
        }
    }

    public static Direction fromVector(int x, int y, int z) {
        if (x == 0 && y == 0 && z == -1)
            return NORTH; // Warning: Z is flipped in some engines, checking conventions
        // Convention in MeshBuilder:
        // +Z = SOUTH (FACE_POS_Z = 4)
        // -Z = NORTH (FACE_NEG_Z = 5)
        // +X = EAST
        // -X = WEST
        // +Y = UP
        // -Y = DOWN

        // Wait, MeshBuilder.java:
        // { 1, 0, 0 }, // +X
        // { -1, 0, 0 }, // -X ...
        // { 0, 0, 1 }, // +Z
        // { 0, 0, -1 } // -Z

        if (x == 1)
            return EAST;
        if (x == -1)
            return WEST;
        if (y == 1)
            return UP;
        if (y == -1)
            return DOWN;
        if (z == 1)
            return SOUTH;
        if (z == -1)
            return NORTH;
        return NORTH; // Default
    }
}
