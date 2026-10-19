package uk.iwaservice.traincab.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import uk.iwaservice.traincab.TrainCabMod;

/** Forge 1.20.1 still splits listeners by bus: registration events on the mod bus, ticks on the game bus. */
public final class TrainCabClientEvents {

    private TrainCabClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = TrainCabMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        @SubscribeEvent
        static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("train_cab_hud", TrainCabHudLayer::render);
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
    }

    @Mod.EventBusSubscriber(modid = TrainCabMod.MODID, value = Dist.CLIENT)
    public static final class GameBus {

        private GameBus() {
        }

        @SubscribeEvent
        static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                TrainDrivingState.tick();
            }
        }
    }
}
