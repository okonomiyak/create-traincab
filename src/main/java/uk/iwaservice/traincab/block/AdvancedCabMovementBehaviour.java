package uk.iwaservice.traincab.block;

import com.simibubi.create.content.contraptions.actors.trainControls.ControlsMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import uk.iwaservice.traincab.client.AdvancedCabHandle;

/**
 * The 高度運転台 on a train: Create's controls behaviour, but instead of Create's cover and levers (which would stick
 * out of this block's model) it draws the block's own mascon handle, tilted to the notch.
 */
public class AdvancedCabMovementBehaviour extends ControlsMovementBehaviour {

    @Override
    public void tick(MovementContext context) {
        // Not super: that keeps Create's lever angles in temporaryData, where the handle's angle lives instead.
        if (context.world.isClientSide) {
            AdvancedCabHandle.tick(context);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices,
                                    MultiBufferSource buffer) {
        AdvancedCabHandle.render(context, renderWorld, matrices, buffer);
    }
}
