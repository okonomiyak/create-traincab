package uk.iwaservice.traincab.sound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: send me the departure bell sound with this hash (answered with SoundChunks). */
public record SoundRequest(String hash) {

    public static void encode(SoundRequest request, FriendlyByteBuf buf) {
        buf.writeUtf(request.hash, 64);
    }

    public static SoundRequest decode(FriendlyByteBuf buf) {
        return new SoundRequest(buf.readUtf(64));
    }

    public static void handle(SoundRequest request, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            CustomSounds.send(player, request.hash);
        }
    }
}
