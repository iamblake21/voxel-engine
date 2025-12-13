package engine.world.block.state.property;

import engine.utils.StringRepresentable;

public enum DoubleBlockHalf implements StringRepresentable {
    UPPER,
    LOWER;

    @Override
    public String getSerializedName() {
        return this == UPPER ? "upper" : "lower";
    }
}
