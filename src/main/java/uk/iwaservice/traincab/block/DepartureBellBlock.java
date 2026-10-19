package uk.iwaservice.traincab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import uk.iwaservice.traincab.client.DepartureBellScreen;

/**
 * 発車ベル: placed on a platform, rung by the conductor of a train stopped nearby (see DepartureBell).
 * Right-click opens its settings: the built-in electric bell / departure melody, or an uploaded sound.
 */
public class DepartureBellBlock extends Block implements EntityBlock {

    /** Which built-in sound plays when no sound has been uploaded. */
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DepartureBellBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide) {
            DepartureBellScreen.open(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
