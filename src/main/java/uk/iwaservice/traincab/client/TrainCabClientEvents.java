package uk.iwaservice.traincab.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.traincab.TrainCabMod;

/**
 * As of NeoForge 1.21.1, EventBusSubscriber.bus() is deprecated and ignored: each listener is
 * routed automatically to the mod bus (IModBusEvent, e.g. RegisterGuiLayersEvent) or the game
 * bus (e.g. ClientTickEvent) based on the event type, so both can live in one class.
 */
@EventBusSubscriber(modid = TrainCabMod.MODID, value = Dist.CLIENT)
public final class TrainCabClientEvents {

    private TrainCabClientEvents() {
    }

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
            ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "train_cab_hud"),
            TrainCabHudLayer::render);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TrainDrivingState.REVERSER_FORWARD);
        event.register(TrainDrivingState.REVERSER_BACKWARD);
        event.register(TrainDrivingState.EMERGENCY);
        event.register(ConductorClient.DOORS);
        event.register(ConductorClient.BELL);
        event.register(ConductorClient.ANNOUNCE);
        event.register(ConductorClient.HORN);
        event.register(ConductorClient.MUSIC_HORN);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        TrainDrivingState.tick();
    }
}
