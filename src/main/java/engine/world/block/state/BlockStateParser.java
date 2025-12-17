package engine.world.block.state;

import engine.world.block.Block;
import engine.world.block.Blocks;
import engine.world.block.state.property.Property;

import java.util.Optional;

public class BlockStateParser {

    /**
     * Parse a block state string (e.g. "game:door[half=upper,open=true]")
     * into a numeric state ID.
     */
    public static int parse(String input) {
        if (input == null || input.isEmpty())
            return Blocks.AIR().getNumericId();

        int bracketStart = input.indexOf('[');
        if (bracketStart == -1) {
            // Simple block ID
            return Blocks.get(input).getNumericId();
        }

        String blockId = input.substring(0, bracketStart);
        String properties = input.substring(bracketStart + 1, input.length() - 1); // remove [ and ]

        Optional<Block> blockOpt = Blocks.tryGet(blockId);
        if (!blockOpt.isPresent()) {
            return Blocks.AIR().getNumericId();
        }

        Block block = blockOpt.get();
        BlockState state = block.getDefaultState();

        if (properties.isEmpty()) {
            return Block.STATE_IDS.getId(state);
        }

        String[] pairs = properties.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();

                Property<?> prop = block.getStateDefinition().getProperty(key);
                if (prop != null) {
                    state = tryApplyProperty(state, prop, value);
                }
            }
        }

        return Block.STATE_IDS.getId(state);
    }

    private static <T extends Comparable<T>> BlockState tryApplyProperty(BlockState state, Property<T> prop,
            String valueString) {
        Optional<T> val = prop.parse(valueString);
        return val.map(t -> state.with(prop, t)).orElse(state);
    }
}
