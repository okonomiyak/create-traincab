package uk.iwaservice.traincab.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.traincab.client.CustomSoundClient;

import java.util.function.Supplier;

/**
 * Server → nearby players: play the uploaded sound {@code hash} at the departure bell {@code pos}, or following the
 * entity {@code entityId} if not -1 (an MH); an empty hash stops what plays there.
 */
public record SoundPlay(BlockPos pos, int entityId, String hash) {

    public static void encode(SoundPlay play, FriendlyByteBuf buf) {
        buf.writeBlockPos(play.pos);
        buf.writeVarInt(play.entityId);
        buf.writeUtf(play.hash, 64);
    }

    public static SoundPlay decode(FriendlyByteBuf buf) {
        return new SoundPlay(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(64));
    }

    public static void handle(SoundPlay play, Supplier<NetworkEvent.Context> context) {
        CustomSoundClient.onPlay(play);
    }
}
