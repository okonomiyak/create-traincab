package uk.iwaservice.traincab.sound;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.block.InverterBlockEntity;

/**
 * Client → server: keep the sound edited in VvvfEditorScreen ({@code code}, VvvfProfile's text form) on an inverter:
 * the placed one at {@code pos}, or with an {@code entity} id the one at local {@code pos} of that contraption (a
 * train's carriage), whose watchers then get VvvfSync.
 */
public record VvvfEdit(int entity, BlockPos pos, String code) implements CustomPacketPayload {

    /** {@code entity} for a placed inverter. */
    public static final int PLACED = -1;

    public static final Type<VvvfEdit> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "vvvf_edit"));

    public static final StreamCodec<ByteBuf, VvvfEdit> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, VvvfEdit::entity,
        BlockPos.STREAM_CODEC, VvvfEdit::pos,
        ByteBufCodecs.stringUtf8(VvvfProfile.MAX_CODE), VvvfEdit::code,
        VvvfEdit::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VvvfEdit edit, IPayloadContext context) {
        VvvfProfile profile = VvvfProfile.parse(edit.code);
        if (!(context.player() instanceof ServerPlayer player) || !player.mayBuild() || profile == null) {
            return;
        }
        String code = profile.code();
        if (edit.entity == PLACED) {
            InverterBlockEntity.edit(player, edit.pos, code);
            return;
        }
        AbstractContraptionEntity carriage = InverterBlockEntity.editOnTrain(player, edit.entity, edit.pos, code);
        if (carriage != null) {
            PacketDistributor.sendToPlayersTrackingEntity(carriage, new VvvfSync(edit.entity, edit.pos, code));
        }
    }
}
