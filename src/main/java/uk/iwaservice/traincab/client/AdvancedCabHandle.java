package uk.iwaservice.traincab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.mascon.Mascon;

/**
 * The 高度運転台's mascon handle on a train: drawn on its own (the block's "open" model, which Create uses on
 * contraptions, leaves it out) and tilted
 * to the notch, away from the driver for power and towards them for braking. Only the driver knows the notch, so everyone
 * else sees it in N.
 */
public final class AdvancedCabHandle {

    private static final PartialModel HANDLE = PartialModel.of(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "block/advanced_cab/handle"));
    /** Where the handle's stem meets the desk (block units), which it tilts around. */
    private static final float PIVOT_X = 4.5f / 16, PIVOT_Y = 11.1f / 16, PIVOT_Z = 7f / 16;

    private AdvancedCabHandle() {
    }

    /** Partial models have to exist before models are baked: loading this class creates it. */
    public static void init() {
    }

    /**
     * Degrees of tilt about the x axis: power leans away from the driver (positive), braking towards them. The desk
     * leaves little room on the power side (about 16°), so N already leans a little towards the driver.
     */
    static float angle(int notch) {
        float n = -10f;
        if (notch > 0) {
            return n + 5f * notch;
        }
        return notch == Mascon.EB ? n - 45f : n + 5f * notch;
    }

    /** Every client tick: chases the angle of the notch this player holds the handle in, if it is theirs. */
    public static void tick(MovementContext context) {
        if (!(context.temporaryData instanceof LerpedFloat)) {
            context.temporaryData = LerpedFloat.linear();
        }
        LerpedFloat lerped = (LerpedFloat) context.temporaryData;
        boolean driving = ControlsHandler.getContraption() == context.contraption.entity
            && context.localPos.equals(ControlsHandler.getControlsPos());
        lerped.chase(angle(driving ? TrainDrivingState.getNotch() : 0), 0.4f, LerpedFloat.Chaser.EXP);
        lerped.tickChaser();
    }

    public static void render(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices,
                              MultiBufferSource buffer) {
        float angle = context.temporaryData instanceof LerpedFloat lerped
            ? lerped.getValue(AnimationTickHolder.getPartialTicks(context.world)) : 0;
        BlockState state = context.state;
        // Same turn as Create's own controls renderer, matching the blockstate's y rotation.
        float hAngle = 180 + AngleHelper.horizontalAngle(state.getValue(ControlsBlock.FACING));
        PoseStack ms = matrices.getModel();
        ms.pushPose();
        TransformStack.of(ms)
            .center()
            .rotateYDegrees(hAngle)
            .uncenter()
            .translate(PIVOT_X, PIVOT_Y, PIVOT_Z)
            .rotateXDegrees(angle)
            .translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
        CachedBuffers.partial(HANDLE, state)
            .transform(ms)
            .light(LevelRenderer.getLightColor(renderWorld, context.localPos))
            .useLevelLight(context.world, matrices.getWorld())
            .renderInto(matrices.getViewProjection(), buffer.getBuffer(RenderType.cutoutMipped()));
        ms.popPose();
    }
}
