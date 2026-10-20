package uk.iwaservice.traincab.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.traincab.client.VvvfSounds;

import java.util.function.Supplier;

/**
 * Server → players near a train: the inverter at local {@code pos} of contraption {@code entity} (one of its
 * carriages) got a new sound, {@code code} (VvvfProfile's text form).
 */
public record VvvfSync(int entity, BlockPos pos, String code) {

    public static void encode(VvvfSync sync, FriendlyByteBuf buf) {
        buf.writeVarInt(sync.entity);
        buf.writeBlockPos(sync.pos);
        buf.writeUtf(sync.code, VvvfProfile.MAX_CODE);
    }

    public static VvvfSync decode(FriendlyByteBuf buf) {
        return new VvvfSync(buf.readVarInt(), buf.readBlockPos(), buf.readUtf(VvvfProfile.MAX_CODE));
    }

    public static void handle(VvvfSync sync, Supplier<NetworkEvent.Context> context) {
        VvvfSounds.onSync(sync.entity, sync.pos, sync.code);
    }
}
