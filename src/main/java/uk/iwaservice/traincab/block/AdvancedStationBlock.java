package uk.iwaservice.traincab.block;

import com.simibubi.create.content.trains.station.StationBlock;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        // Create's station passes sneaking clicks on to here.
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            DepartureBellScreen.open(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
