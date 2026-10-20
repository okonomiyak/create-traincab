package uk.iwaservice.traincab.block;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import uk.iwaservice.traincab.client.VvvfEditorScreen;

/**
 * Right-clicking a VVVFインバーター on a train opens the editor for its sound. Create calls this on the client first
 * (then on the server, where there is nothing to do); saving goes through VvvfEdit.
 */
public class InverterInteraction extends MovingInteractionBehaviour {

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
                                           AbstractContraptionEntity contraptionEntity) {
        if (player.level().isClientSide) {
            VvvfEditorScreen.openOnTrain(contraptionEntity, localPos);
        }
        return true;
    }
}
