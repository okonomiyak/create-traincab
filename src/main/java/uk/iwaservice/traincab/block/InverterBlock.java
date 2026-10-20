package uk.iwaservice.traincab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import uk.iwaservice.traincab.client.VvvfEditorScreen;

/**
 * VVVFインバーター: built into a train, it makes the train sound like one (see VvvfSounds), in the sound its block
 * entity keeps. Right-click opens the editor for that sound; on a train InverterInteraction does the same.
 */
public class InverterBlock extends Block implements EntityBlock {

    public InverterBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InverterBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            VvvfEditorScreen.openPlaced(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
