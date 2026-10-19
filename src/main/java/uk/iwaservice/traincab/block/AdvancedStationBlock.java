package uk.iwaservice.traincab.block;

import com.simibubi.create.content.trains.station.StationBlock;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import uk.iwaservice.traincab.TrainCabItems;
import uk.iwaservice.traincab.client.DepartureBellScreen;

/**
 * 高度駅: Create's train station with a departure bell built in. Right-click opens Create's station screen as
 * usual, sneak-right-click with an empty hand the departure melody settings. Scheduled trains play it before
 * leaving (see DepartureBell.holdDeparture); crews ring it with J like a departure bell block.
 */
public class AdvancedStationBlock extends StationBlock {

    public AdvancedStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntityType<? extends StationBlockEntity> getBlockEntityType() {
        return TrainCabItems.ADVANCED_STATION_ENTITY.get();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        // Create's station ignores sneaking clicks.
        if (!player.isShiftKeyDown()) {
            return super.use(state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) {
            DepartureBellScreen.open(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
