package uk.iwaservice.traincab.block;

import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 高度運転台: Create's train controls with a ワンマン switch, flipped by sneak + right-click (placed, or on a train
 * through AdvancedCabInteraction). It is part of the block state, so it travels with the train and is saved.
 */
public class AdvancedCabBlock extends ControlsBlock {

    public static final BooleanProperty ONE_MAN = BooleanProperty.create("one_man");

    public AdvancedCabBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(ONE_MAN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ONE_MAN);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            BlockState toggled = state.cycle(ONE_MAN);
            level.setBlock(pos, toggled, Block.UPDATE_ALL);
            notify(player, toggled);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    static void notify(Player player, BlockState state) {
        player.displayClientMessage(Component.literal(state.getValue(ONE_MAN)
            ? "ワンマン: 入(運転士がドアを扱います)" : "ワンマン: 切(ドアは Create が開閉します)"), true);
    }
}
