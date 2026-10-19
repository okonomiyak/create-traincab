package uk.iwaservice.traincab.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.traincab.client.CustomSoundClient;

import java.util.function.Supplier;

/** Server → nearby players: play the uploaded sound {@code hash} at the departure bell {@code pos}, or stop it if empty. */
public record SoundPlay(BlockPos pos, String hash) {

    public static void encode(SoundPlay play, FriendlyByteBuf buf) {
        buf.writeBlockPos(play.pos);
        buf.writeUtf(play.hash, 64);
    }

    public static SoundPlay decode(FriendlyByteBuf buf) {
        return new SoundPlay(buf.readBlockPos(), buf.readUtf(64));
    }

    public static void handle(SoundPlay play, Supplier<NetworkEvent.Context> context) {
        CustomSoundClient.onPlay(play);
    }
}
