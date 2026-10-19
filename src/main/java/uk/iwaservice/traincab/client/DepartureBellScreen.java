package uk.iwaservice.traincab.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import uk.iwaservice.traincab.TrainCabServerConfig;
import uk.iwaservice.traincab.block.DepartureBellBlock;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity;
import uk.iwaservice.traincab.sound.BellMode;
import uk.iwaservice.traincab.sound.CustomSounds;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Right-click on a departure bell: pick a local mp3 / wav / ogg, preview it and upload it, or go back to a built-in sound. */
public class DepartureBellScreen extends Screen {

    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0xA0A0A0;

    private final BlockPos pos;
    private String fileName;
    private byte[] pcm;
    private Component status = Component.empty();
    private Button previewButton;
    private Button uploadButton;

    private DepartureBellScreen(BlockPos pos) {
        super(Component.literal("発車ベルの設定"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new DepartureBellScreen(pos));
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 30;
        addRenderableWidget(Button.builder(Component.literal("ファイルを選ぶ…"), b -> choose())
            .bounds(x, y, 200, 20).build());
        previewButton = addRenderableWidget(Button.builder(Component.literal("試聴"), b -> preview())
            .bounds(x, y + 24, 98, 20).build());
        uploadButton = addRenderableWidget(Button.builder(Component.literal("アップロードして保存"), b -> upload())
            .bounds(x + 102, y + 24, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("内蔵メロディ"), b -> builtin(true))
            .bounds(x, y + 56, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("内蔵ベル"), b -> builtin(false))
            .bounds(x + 102, y + 56, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(x, y + 88, 200, 20).build());
        updateButtons();
    }

    private DepartureBellBlockEntity bell() {
        return minecraft.level.getBlockEntity(pos) instanceof DepartureBellBlockEntity be ? be : null;
    }

    private void updateButtons() {
        DepartureBellBlockEntity bell = bell();
        previewButton.active = pcm != null || bell != null && bell.hasSound();
        uploadButton.active = pcm != null;
    }

    private void choose() {
        String chosen;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(3);
            filters.put(stack.UTF8("*.mp3")).put(stack.UTF8("*.wav")).put(stack.UTF8("*.ogg")).flip();
            chosen = TinyFileDialogs.tinyfd_openFileDialog("発車ベルの音声", null, filters, "mp3 / wav / ogg", false);
        }
        if (chosen != null) {
            load(Path.of(chosen));
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (!paths.isEmpty()) {
            load(paths.get(0));
        }
    }

    private void load(Path path) {
        CustomSoundClient.preview(null);
        fileName = null;
        pcm = null;
        status = Component.literal("読み込み中…");
        updateButtons();
        int maxSeconds = TrainCabServerConfig.maxSoundSeconds;
        CompletableFuture.supplyAsync(() -> {
            try {
                return AudioDecoder.decode(path, maxSeconds);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, Util.backgroundExecutor()).whenCompleteAsync((result, error) -> {
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                status = Component.literal("読み込めませんでした: " + cause.getMessage());
            } else {
                String name = path.getFileName().toString().replaceFirst("\\.[^.]*$", "");
                fileName = name.length() > 64 ? name.substring(0, 64) : name;
                pcm = result.pcm();
                status = result.clipped() ? Component.literal(maxSeconds + " 秒で切りました") : Component.empty();
            }
            updateButtons();
        }, minecraft);
    }

    private void preview() {
        if (pcm != null) {
            CustomSoundClient.preview(pcm);
            return;
        }
        DepartureBellBlockEntity bell = bell();
        if (bell != null && bell.hasSound()) {
            byte[] current = CustomSoundClient.get(bell.soundHash());
            if (current == null) {
                CustomSoundClient.prefetch(bell.soundHash());
                status = Component.literal("ダウンロード中です。少し待ってからもう一度押してください");
            }
            CustomSoundClient.preview(current);
        }
    }

    private void upload() {
        CustomSoundClient.upload(pos, fileName, pcm);
        onClose();
    }

    private void builtin(boolean melody) {
        PacketDistributor.sendToServer(new BellMode(pos, melody));
        onClose();
    }

    @Override
    public void removed() {
        CustomSoundClient.preview(null);
    }

    @Override
    public boolean isPauseScreen() {
        // Pausing would also pause the preview.
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int y = height / 2 - 30;
        g.drawCenteredString(font, title, cx, y - 50, WHITE);
        g.drawCenteredString(font, "現在: " + current(), cx, y - 34, WHITE);
        g.drawCenteredString(font, pcm != null ? "選択中: " + fileName + " (" + seconds(pcm.length) + ")"
            : "mp3 / wav / ogg をここへドラッグ＆ドロップしても選べます", cx, y - 20, GREY);
        g.drawCenteredString(font, status, cx, y + 46, GREY);
    }

    private String current() {
        DepartureBellBlockEntity bell = bell();
        if (bell == null) {
            return "-";
        }
        if (bell.hasSound()) {
            return bell.soundName() + " (" + seconds(bell.soundBytes()) + ")";
        }
        return bell.getBlockState().getValue(DepartureBellBlock.MELODY) ? "内蔵メロディ" : "内蔵ベル";
    }

    private static String seconds(int bytes) {
        return String.format("%.1f 秒", bytes / 2.0 / CustomSounds.SAMPLE_RATE);
    }
}
