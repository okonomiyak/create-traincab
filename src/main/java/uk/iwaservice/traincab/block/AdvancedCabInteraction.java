package uk.iwaservice.traincab.block;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsInteractionBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/** A 高度運転台 on a train: right-click drives as with Create's controls, sneak + right-click flips ワンマン. */
public class AdvancedCabInteraction extends ControlsInteractionBehaviour {

    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand activeHand, BlockPos localPos,
                                           AbstractContraptionEntity contraptionEntity) {
        if (!player.isShiftKeyDown()) {
            return super.handlePlayerInteraction(player, activeHand, localPos, contraptionEntity);
        }
        StructureBlockInfo info = contraptionEntity.getContraption().getBlocks().get(localPos);
        if (!player.level().isClientSide && info != null && info.state().hasProperty(AdvancedCabBlock.ONE_MAN)) {
            BlockState toggled = info.state().cycle(AdvancedCabBlock.ONE_MAN);
            // Synced to the clients tracking the train, and saved with it.
            contraptionEntity.setBlock(localPos, new StructureBlockInfo(localPos, toggled, info.nbt()));
            AdvancedCabBlock.notify(player, toggled);
        }
        return true;
    }
}
