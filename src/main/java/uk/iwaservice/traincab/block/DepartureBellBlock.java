package uk.iwaservice.traincab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 発車ベル: placed on a platform, rung by the conductor of a train stopped nearby (see DepartureBell).
 * Right-click switches between the electric bell and the departure melody.
 */
public class DepartureBellBlock extends Block {

    public static final BooleanProperty MELODY = BooleanProperty.create("melody");

    public DepartureBellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MELODY, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MELODY);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide) {
            toggleMode(state, level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void toggleMode(BlockState state, Level level, BlockPos pos, Player player) {
        boolean melody = !state.getValue(MELODY);
        level.setBlock(pos, state.setValue(MELODY, melody), Block.UPDATE_ALL);
        player.displayClientMessage(Component.literal(melody ? "発車メロディ" : "発車ベル"), true);
    }
}
