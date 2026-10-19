package uk.iwaservice.traincab.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import uk.iwaservice.traincab.TrainCabServerConfig;
import uk.iwaservice.traincab.block.DepartureBellBlock;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity;
import uk.iwaservice.traincab.mascon.TrainCabNetwork;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity.Clip;
import uk.iwaservice.traincab.sound.BellEdit;
import uk.iwaservice.traincab.sound.CustomSounds;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

/**
 * Right-click on a departure bell. 発車ベル tab: pick a local mp3 / wav / ogg, preview it and upload it, or go back to
 * a built-in sound. 車内放送 tab: the sounds the conductor can pick from with N once the bell is built into a train.
 */
public class DepartureBellScreen extends Screen {

    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0xA0A0A0;
    private static final int ROW_H = 21;

    private final BlockPos pos;
    private boolean announcementTab;
    /** Bell tab: the file picked but not uploaded yet. */
    private String fileName;
    private byte[] pcm;
    private Component status = Component.empty();
    private boolean loading;
    /** The bell's contents when the widgets were built, to rebuild them once the server changes it. */
    private String shown;

    private DepartureBellScreen(BlockPos pos) {
        super(Component.literal("発車ベルの設定"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new DepartureBellScreen(pos));
    }

    private int top() {
        return Math.max(4, height / 2 - 125);
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = top() + 14;
        Button bellTab = addRenderableWidget(Button.builder(Component.literal("発車ベル"), b -> switchTab(false))
            .bounds(x, y, 98, 20).build());
        Button announcementTabButton = addRenderableWidget(Button.builder(Component.literal("車内放送"), b -> switchTab(true))
            .bounds(x + 102, y, 98, 20).build());
        bellTab.active = announcementTab;
        announcementTabButton.active = !announcementTab;
        DepartureBellBlockEntity bell = bell();
        if (announcementTab) {
            initAnnouncements(x, y + 50, bell);
        } else {
            initBell(x, y + 50, bell);
        }
        shown = contents();
    }

    private void initBell(int x, int y, DepartureBellBlockEntity bell) {
        addRenderableWidget(Button.builder(Component.literal("ファイルを選ぶ…"), b -> choose())
            .bounds(x, y, 200, 20).build()).active = !loading;
        addRenderableWidget(Button.builder(Component.literal("試聴"), b -> previewBell())
            .bounds(x, y + 24, 98, 20).build()).active = pcm != null || bell != null && bell.hasSound();
        addRenderableWidget(Button.builder(Component.literal("アップロードして保存"), b -> {
            CustomSoundClient.upload(pos, fileName, false, pcm);
            onClose();
        }).bounds(x + 102, y + 24, 98, 20).build()).active = pcm != null;
        addRenderableWidget(Button.builder(Component.literal("内蔵メロディ"), b -> builtin(BellEdit.MELODY))
            .bounds(x, y + 62, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("内蔵ベル"), b -> builtin(BellEdit.BELL))
            .bounds(x + 102, y + 62, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(x, y + 94, 200, 20).build());
    }

    private void initAnnouncements(int x, int y, DepartureBellBlockEntity bell) {
        List<Clip> clips = bell == null ? List.of() : bell.announcements();
        for (int i = 0; i < clips.size(); i++) {
            int index = i;
            Clip clip = clips.get(i);
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> previewClip(clip.hash()))
                .bounds(x + 156, y + ROW_H * i, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("×"), b -> {
                TrainCabNetwork.CHANNEL.sendToServer(new BellEdit(pos, index));
                CustomSoundClient.preview(null);
            }).bounds(x + 180, y + ROW_H * i, 20, 20).build());
        }
        int bottom = y + ROW_H * Math.max(clips.size(), 1) + 4;
        addRenderableWidget(Button.builder(Component.literal("追加…"), b -> choose())
            .bounds(x, bottom, 98, 20).build()).active =
            !loading && clips.size() < DepartureBellBlockEntity.MAX_ANNOUNCEMENTS;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(x + 102, bottom, 98, 20).build());
    }

    private void switchTab(boolean announcements) {
        announcementTab = announcements;
        fileName = null;
        pcm = null;
        status = Component.empty();
        CustomSoundClient.preview(null);
        rebuildWidgets();
    }

    private DepartureBellBlockEntity bell() {
        return minecraft.level.getBlockEntity(pos) instanceof DepartureBellBlockEntity be ? be : null;
    }

    private String contents() {
        DepartureBellBlockEntity bell = bell();
        return bell == null ? "" : bell.soundHash() + bell.getBlockState() + bell.announcements();
    }

    @Override
    public void tick() {
        if (!contents().equals(shown)) {
            rebuildWidgets();
        }
    }

    private void choose() {
        String chosen;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(3);
            filters.put(stack.UTF8("*.mp3")).put(stack.UTF8("*.wav")).put(stack.UTF8("*.ogg")).flip();
            chosen = TinyFileDialogs.tinyfd_openFileDialog("音声ファイル", null, filters, "mp3 / wav / ogg", false);
        }
        if (chosen != null) {
            load(Path.of(chosen));
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (!paths.isEmpty() && !loading) {
            load(paths.get(0));
        }
    }

    /** Bell tab: keep the file to preview / upload. 車内放送 tab: upload it right away. */
    private void load(Path path) {
        boolean announcement = announcementTab;
        decode(path, (name, result) -> {
            if (announcement) {
                CustomSoundClient.upload(pos, name, true, result.pcm());
                status = Component.literal("「" + name + "」をアップロードしました");
            } else {
                fileName = name;
                pcm = result.pcm();
            }
        });
    }

    private void decode(Path path, BiConsumer<String, AudioDecoder.Result> then) {
        CustomSoundClient.preview(null);
        fileName = null;
        pcm = null;
        loading = true;
        status = Component.literal("読み込み中…");
        rebuildWidgets();
        int maxSeconds = TrainCabServerConfig.maxSoundSeconds;
        CompletableFuture.supplyAsync(() -> {
            try {
                return AudioDecoder.decode(path, maxSeconds);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, Util.backgroundExecutor()).whenCompleteAsync((result, error) -> {
            loading = false;
            if (error != null) {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                status = Component.literal("読み込めませんでした: " + cause.getMessage());
            } else {
                String name = path.getFileName().toString().replaceFirst("\\.[^.]*$", "");
                status = result.clipped() ? Component.literal(maxSeconds + " 秒で切りました") : Component.empty();
                then.accept(name.length() > 64 ? name.substring(0, 64) : name, result);
            }
            rebuildWidgets();
        }, minecraft);
    }

    private void previewBell() {
        DepartureBellBlockEntity bell = bell();
        if (pcm != null) {
            CustomSoundClient.preview(pcm);
        } else if (bell != null && bell.hasSound()) {
            previewClip(bell.soundHash());
        }
    }

    private void previewClip(String hash) {
        byte[] sound = CustomSoundClient.get(hash);
        if (sound == null) {
            CustomSoundClient.prefetch(hash);
            status = Component.literal("ダウンロード中です。少し待ってからもう一度押してください");
        }
        CustomSoundClient.preview(sound);
    }

    private void builtin(int op) {
        TrainCabNetwork.CHANNEL.sendToServer(new BellEdit(pos, op));
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
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int x = cx - 100;
        int y = top() + 64;
        g.drawCenteredString(font, title, cx, top(), WHITE);
        DepartureBellBlockEntity bell = bell();
        if (announcementTab) {
            g.drawCenteredString(font, "列車に組み込むと、車掌が N で選んで流せます", cx, y - 24, GREY);
            g.drawCenteredString(font, "（最大 " + DepartureBellBlockEntity.MAX_ANNOUNCEMENTS + " 本・ドラッグ＆ドロップでも追加できます）",
                cx, y - 13, GREY);
            List<Clip> clips = bell == null ? List.of() : bell.announcements();
            for (int i = 0; i < clips.size(); i++) {
                String label = (i + 1) + ". " + clips.get(i).name() + " (" + seconds(clips.get(i).bytes()) + ")";
                g.drawString(font, font.plainSubstrByWidth(label, 152), x, y + ROW_H * i + 6, WHITE);
            }
            if (clips.isEmpty()) {
                g.drawString(font, "まだありません", x, y + 6, GREY);
            }
            g.drawCenteredString(font, status, cx, y + ROW_H * Math.max(clips.size(), 1) + 28, GREY);
        } else {
            g.drawCenteredString(font, "現在: " + current(bell), cx, y - 24, WHITE);
            g.drawCenteredString(font, pcm != null ? "選択中: " + fileName + " (" + seconds(pcm.length) + ")"
                : "mp3 / wav / ogg をここへドラッグ＆ドロップしても選べます", cx, y - 12, GREY);
            g.drawCenteredString(font, status, cx, y + 48, GREY);
        }
    }

    private static String current(DepartureBellBlockEntity bell) {
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
