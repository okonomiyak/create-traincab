package uk.iwaservice.traincab.client;

import com.simibubi.create.content.trains.station.StationRenderer;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import uk.iwaservice.traincab.TrainCabItems;
import uk.iwaservice.traincab.TrainCabMod;

/** Forge 1.20.1 still splits listeners by bus: registration events on the mod bus, ticks on the game bus. */
public final class TrainCabClientEvents {

    private TrainCabClientEvents() {
    }

    @Mod.EventBusSubscriber(modid = TrainCabMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModBus {

        private ModBus() {
        }

        /** Mods → Train Cab HUD → Config opens the same settings screen Create uses, for the client and server configs. */
        @SubscribeEvent
        static void onClientSetup(FMLClientSetupEvent event) {
            ModList.get().getModContainerById(TrainCabMod.MODID).ifPresent(container -> container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new BaseConfigScreen(parent, TrainCabMod.MODID))));
        }

        /** The 高度駅 draws its flag like Create's station. */
        @SubscribeEvent
        static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(TrainCabItems.ADVANCED_STATION_ENTITY.get(), StationRenderer::new);
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
            event.register(ConductorClient.DEPARTURE_BELL);
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
