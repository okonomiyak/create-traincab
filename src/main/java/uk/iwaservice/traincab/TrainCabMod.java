package uk.iwaservice.traincab;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import uk.iwaservice.traincab.mascon.Announcement;
import uk.iwaservice.traincab.mascon.ConductorAction;
import uk.iwaservice.traincab.mascon.CrewStatus;
import uk.iwaservice.traincab.mascon.MasconPayload;
import uk.iwaservice.traincab.mascon.StationInfo;
import uk.iwaservice.traincab.mascon.StopResult;

@Mod(TrainCabMod.MODID)
public class TrainCabMod {

    public static final String MODID = "traincab";

    public TrainCabMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, TrainCabConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, TrainCabServerConfig.SPEC);
        TrainCabItems.register(modEventBus);
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("6")
            .playToServer(MasconPayload.TYPE, MasconPayload.STREAM_CODEC, MasconPayload::handle)
            .playToServer(ConductorAction.TYPE, ConductorAction.STREAM_CODEC, ConductorAction::handle)
            .playToClient(StationInfo.TYPE, StationInfo.STREAM_CODEC, StationInfo::handle)
            .playToClient(CrewStatus.TYPE, CrewStatus.STREAM_CODEC, CrewStatus::handle)
            .playToClient(Announcement.TYPE, Announcement.STREAM_CODEC, Announcement::handle)
            .playToClient(StopResult.TYPE, StopResult.STREAM_CODEC, StopResult::handle));
    }
}
