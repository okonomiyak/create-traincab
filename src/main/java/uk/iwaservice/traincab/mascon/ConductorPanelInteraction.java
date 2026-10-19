package uk.iwaservice.traincab.mascon;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * Right-clicking a 車掌ブロック that is part of an assembled train toggles conductor duty on that train.
 * Create calls this on both sides; the duty change itself happens on the server.
 */
public class ConductorPanelInteraction extends MovingInteractionBehaviour {

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
        AbstractContraptionEntity contraptionEntity) {
        if (!(contraptionEntity instanceof CarriageContraptionEntity cce) || cce.getCarriage() == null) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Conductor.toggleDuty(serverPlayer, cce.getCarriage().train);
        }
        return true;
    }
}
