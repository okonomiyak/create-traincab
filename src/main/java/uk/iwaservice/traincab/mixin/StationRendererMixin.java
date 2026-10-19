package uk.iwaservice.traincab.mixin;

import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.station.StationRenderer;
import com.simibubi.create.content.trains.track.TrackTargetingBehaviour.RenderedTrackOverlayType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.iwaservice.traincab.block.AdvancedStationBlockEntity;

/** A 高度駅 set to 双方向 shows on the track with the marker of a signal facing both ways. */
@Mixin(value = StationRenderer.class, remap = false)
public abstract class StationRendererMixin {

    /** The station being rendered (render thread only), for the marker below. */
    @Unique
    private static boolean traincab$twoWay;

    @Inject(method = "renderSafe", at = @At("HEAD"))
    private void traincab$remember(StationBlockEntity be, float partialTicks,
                                   com.mojang.blaze3d.vertex.PoseStack ms,
                                   net.minecraft.client.renderer.MultiBufferSource buffer, int light, int overlay,
                                   CallbackInfo ci) {
        traincab$twoWay = be instanceof AdvancedStationBlockEntity station && station.twoWay();
    }

    @ModifyArg(method = "renderSafe", index = 8, at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/trains/track/TrackTargetingBehaviour;render"))
    private RenderedTrackOverlayType traincab$marker(RenderedTrackOverlayType type) {
        return traincab$twoWay ? RenderedTrackOverlayType.DUAL_SIGNAL : type;
    }
}
