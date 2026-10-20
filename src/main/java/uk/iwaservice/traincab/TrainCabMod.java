package uk.iwaservice.traincab;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import uk.iwaservice.traincab.client.AdvancedCabHandle;
import uk.iwaservice.traincab.compat.crn.CrnCompat;
import uk.iwaservice.traincab.mascon.Announcement;
import uk.iwaservice.traincab.mascon.AnnouncementMenu;
import uk.iwaservice.traincab.mascon.ConductorAction;
import uk.iwaservice.traincab.mascon.CrewStatus;
import uk.iwaservice.traincab.mascon.MasconPayload;
import uk.iwaservice.traincab.mascon.StationInfo;
import uk.iwaservice.traincab.mascon.StopResult;
import uk.iwaservice.traincab.sound.BellEdit;
import uk.iwaservice.traincab.sound.SoundChunk;
import uk.iwaservice.traincab.sound.SoundPlay;
import uk.iwaservice.traincab.sound.SoundRequest;
import uk.iwaservice.traincab.sound.VvvfEdit;
import uk.iwaservice.traincab.sound.VvvfSync;

@Mod(TrainCabMod.MODID)
public class TrainCabMod {

    public static final String MODID = "traincab";

    public TrainCabMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, TrainCabConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.SERVER, TrainCabServerConfig.SPEC);
        TrainCabItems.register(modEventBus);
        if (FMLEnvironment.dist.isClient()) {
            AdvancedCabHandle.init();
        }
        if (ModList.get().isLoaded(CrnCompat.MODID)) {
            CrnCompat.init();
        }
        modEventBus.addListener((RegisterPayloadHandlersEvent event) -> event.registrar("11")
            .playToServer(MasconPayload.TYPE, MasconPayload.STREAM_CODEC, MasconPayload::handle)
            .playToServer(ConductorAction.TYPE, ConductorAction.STREAM_CODEC, ConductorAction::handle)
            .playToClient(StationInfo.TYPE, StationInfo.STREAM_CODEC, StationInfo::handle)
            .playToClient(CrewStatus.TYPE, CrewStatus.STREAM_CODEC, CrewStatus::handle)
            .playToClient(Announcement.TYPE, Announcement.STREAM_CODEC, Announcement::handle)
            .playToClient(StopResult.TYPE, StopResult.STREAM_CODEC, StopResult::handle)
            .playBidirectional(SoundChunk.TYPE, SoundChunk.STREAM_CODEC, SoundChunk::handle)
            .playToServer(SoundRequest.TYPE, SoundRequest.STREAM_CODEC, SoundRequest::handle)
            .playToServer(BellEdit.TYPE, BellEdit.STREAM_CODEC, BellEdit::handle)
            .playToClient(SoundPlay.TYPE, SoundPlay.STREAM_CODEC, SoundPlay::handle)
            .playToClient(AnnouncementMenu.TYPE, AnnouncementMenu.STREAM_CODEC, AnnouncementMenu::handle)
            .playToServer(VvvfEdit.TYPE, VvvfEdit.STREAM_CODEC, VvvfEdit::handle)
            .playToClient(VvvfSync.TYPE, VvvfSync.STREAM_CODEC, VvvfSync::handle));
    }
}
