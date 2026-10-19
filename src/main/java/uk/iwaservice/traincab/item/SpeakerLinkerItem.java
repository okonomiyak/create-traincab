package uk.iwaservice.traincab.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import uk.iwaservice.traincab.TrainCabItems;
import uk.iwaservice.traincab.block.BellSound;
import uk.iwaservice.traincab.block.SpeakerLinks;

/**
 * スピーカー接続ツール: right-click a 放送装置 or 高度駅 to pick it, then right-click スピーカー to link them to it
 * (again to unlink). The picked source is kept on the item, which glints while it holds one.
 */
public class SpeakerLinkerItem extends Item {

    public SpeakerLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (level.isClientSide || player == null) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        if (level.getBlockEntity(pos) instanceof BellSound) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Source", pos.asLong());
            tag.putString("Dimension", level.dimension().location().toString());
            setData(stack, tag);
            notify(player, "放送装置を選びました。つなぐスピーカーを右クリックしてください");
            return InteractionResult.SUCCESS;
        }
        if (!level.getBlockState(pos).is(TrainCabItems.SPEAKER.get())) {
            return InteractionResult.PASS;
        }
        CompoundTag tag = getData(stack);
        if (tag == null || !tag.contains("Source")) {
            notify(player, "先に放送装置か高度駅を右クリックしてください");
            return InteractionResult.SUCCESS;
        }
        BlockPos source = BlockPos.of(tag.getLong("Source"));
        if (!tag.getString("Dimension").equals(level.dimension().location().toString())
            || !source.closerThan(pos, SpeakerLinks.RANGE)) {
            notify(player, "放送装置から遠すぎます（" + SpeakerLinks.RANGE + " ブロックまで）");
        } else if (!player.mayBuild() || !level.mayInteract(player, pos)
            || !(level.getBlockEntity(source) instanceof BellSound bell)) {
            notify(player, "選んだ放送装置が見つかりません");
        } else {
            boolean linked = bell.speakers().toggle(pos);
            ((BlockEntity) bell).setChanged();
            notify(player, (linked ? "スピーカーをつなぎました" : "スピーカーを外しました")
                + "（この放送装置に " + bell.speakers().size() + " 台）");
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = getData(stack);
        return tag != null && tag.contains("Source");
    }

    private static void notify(Player player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private static CompoundTag getData(ItemStack stack) {
        return stack.getTag();
    }

    private static void setData(ItemStack stack, CompoundTag tag) {
        stack.setTag(tag);
    }
}
