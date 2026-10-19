package uk.iwaservice.traincab.mascon;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.sound.BellMode;
import uk.iwaservice.traincab.sound.SoundChunk;
import uk.iwaservice.traincab.sound.SoundPlay;
import uk.iwaservice.traincab.sound.SoundRequest;

public final class TrainCabNetwork {

    private static final String PROTOCOL_VERSION = "7";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(TrainCabMod.MODID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals);

    private TrainCabNetwork() {
    }

    public static void register() {
        CHANNEL.messageBuilder(MasconPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
            .encoder(MasconPacket::encode)
            .decoder(MasconPacket::decode)
            .consumerMainThread(MasconPacket::handle)
            .add();
        CHANNEL.messageBuilder(StationInfo.class, 1, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(StationInfo::encode)
            .decoder(StationInfo::decode)
            .consumerMainThread(StationInfo::handle)
            .add();
        CHANNEL.messageBuilder(ConductorAction.class, 2, NetworkDirection.PLAY_TO_SERVER)
            .encoder(ConductorAction::encode)
            .decoder(ConductorAction::decode)
            .consumerMainThread(ConductorAction::handle)
            .add();
        CHANNEL.messageBuilder(CrewStatus.class, 3, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(CrewStatus::encode)
            .decoder(CrewStatus::decode)
            .consumerMainThread(CrewStatus::handle)
            .add();
        CHANNEL.messageBuilder(Announcement.class, 4, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(Announcement::encode)
            .decoder(Announcement::decode)
            .consumerMainThread(Announcement::handle)
            .add();
        CHANNEL.messageBuilder(StopResult.class, 5, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(StopResult::encode)
            .decoder(StopResult::decode)
            .consumerMainThread(StopResult::handle)
            .add();
        // Both directions: uploads to the server, downloads to clients.
        CHANNEL.messageBuilder(SoundChunk.class, 6)
            .encoder(SoundChunk::encode)
            .decoder(SoundChunk::decode)
            .consumerMainThread(SoundChunk::handle)
            .add();
        CHANNEL.messageBuilder(SoundRequest.class, 7, NetworkDirection.PLAY_TO_SERVER)
            .encoder(SoundRequest::encode)
            .decoder(SoundRequest::decode)
            .consumerMainThread(SoundRequest::handle)
            .add();
        CHANNEL.messageBuilder(BellMode.class, 8, NetworkDirection.PLAY_TO_SERVER)
            .encoder(BellMode::encode)
            .decoder(BellMode::decode)
            .consumerMainThread(BellMode::handle)
            .add();
        CHANNEL.messageBuilder(SoundPlay.class, 9, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SoundPlay::encode)
            .decoder(SoundPlay::decode)
            .consumerMainThread(SoundPlay::handle)
            .add();
    }
}
