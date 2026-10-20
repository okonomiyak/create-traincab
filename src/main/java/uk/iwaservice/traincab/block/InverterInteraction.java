package uk.iwaservice.traincab.block;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/** Right-clicking a VVVFインバーター on a train picks its next sound. */
public class InverterInteraction extends MovingInteractionBehaviour {

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
                                           AbstractContraptionEntity contraptionEntity) {
        StructureBlockInfo info = contraptionEntity.getContraption().getBlocks().get(localPos);
        if (!player.level().isClientSide && info != null && info.state().hasProperty(InverterBlock.SOUND)) {
            BlockState next = InverterBlock.next(info.state());
            // Synced to the clients tracking the train (VvvfSounds looks again within a second), and saved with it.
            contraptionEntity.setBlock(localPos, new StructureBlockInfo(localPos, next, info.nbt()));
            InverterBlock.notify(player, next);
        }
        return true;
    }
}
