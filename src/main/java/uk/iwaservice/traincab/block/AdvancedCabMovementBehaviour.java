package uk.iwaservice.traincab.block;

import com.simibubi.create.content.contraptions.actors.trainControls.ControlsMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The 高度運転台 on a train: Create's controls behaviour, minus Create's animated cover and levers, which would
 * stick out of this block's own model (it has its own handles).
 */
public class AdvancedCabMovementBehaviour extends ControlsMovementBehaviour {

    @Override
    @OnlyIn(Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices,
                                    MultiBufferSource buffer) {
    }
}
