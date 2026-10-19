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
import uk.iwaservice.traincab.block.AdvancedStationBlockEntity;
import uk.iwaservice.traincab.block.BellSound;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity.Clip;
import uk.iwaservice.traincab.mascon.TrainCabNetwork;
import uk.iwaservice.traincab.sound.BellEdit;
import uk.iwaservice.traincab.sound.CustomSounds;
import uk.iwaservice.traincab.sound.SoundChunk;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;

/**
 * Right-click on a departure bell (sneak-right-click on a 高度駅, which has the 発車メロディ and 接近メロディ tabs
 * and its 停車する向き instead). 発車ベル tab: pick a local mp3 / wav / ogg, preview it and upload it, or go back to
 * a built-in sound. 車内放送 tab: the sounds the conductor can pick from with N once the bell is built into a train.
 * MH tab: like the 発車ベル tab, for the sound the driver plays with M once the bell is built into a train.
 */
public class DepartureBellScreen extends Screen {

    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0xA0A0A0;
    private static final int ROW_H = 21;

    private final BlockPos pos;
    /** The SoundChunk target the open tab edits. */
    private int tab = SoundChunk.BELL;
    /** 発車ベル / MH tab: the file picked but not uploaded yet. */
    private String fileName;
    private byte[] pcm;
    private Component status = Component.empty();
    private boolean loading;
    /** The bell's contents when the widgets were built, to rebuild them once the server changes it. */
    private String shown;

    private DepartureBellScreen(BlockPos pos) {
        super(Component.literal(Minecraft.getInstance().level.getBlockEntity(pos) instanceof AdvancedStationBlockEntity
            ? "高度駅の設定" : "放送装置の設定"));
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
        if (trainBell() != null) {
            // Tab i edits SoundChunk target i (BELL, ANNOUNCEMENT, MUSIC_HORN, APPROACH).
            String[] tabs = {"発車ベル", "車内放送", "MH", "接近メロディ"};
            int[] widths = {50, 50, 34, 60};
            for (int i = 0, tx = x; i < tabs.length; tx += widths[i] + 2, i++) {
                int target = i;
                addRenderableWidget(Button.builder(Component.literal(tabs[i]), b -> switchTab(target))
                    .bounds(tx, y, widths[i], 20).build()).active = tab != i;
            }
        } else if (bell() instanceof AdvancedStationBlockEntity station) {
            if (tab != SoundChunk.APPROACH) {
                tab = SoundChunk.BELL;
            }
            String[] tabs = {"発車メロディ", "接近メロディ"};
            int[] targets = {SoundChunk.BELL, SoundChunk.APPROACH};
            for (int i = 0; i < tabs.length; i++) {
                int target = targets[i];
                addRenderableWidget(Button.builder(Component.literal(tabs[i]), b -> switchTab(target))
                    .bounds(x + 102 * i, y, 98, 20).build()).active = tab != target;
            }
            addRenderableWidget(Button.builder(
                Component.literal("停車する向き: " + (station.twoWay() ? "双方向" : "一方向(矢印の向き)")),
                b -> TrainCabNetwork.CHANNEL.sendToServer(new BellEdit(pos, BellEdit.TWO_WAY))).bounds(x, y + 196, 200, 20).build());
        } else {
            tab = SoundChunk.BELL;
        }
        if (tab == SoundChunk.ANNOUNCEMENT) {
            initAnnouncements(x, y + 50, trainBell());
        } else {
            initSingle(x, y + 50, bell());
        }
        shown = contents();
    }

    /** The 発車ベル, MH or 接近メロディ tab. */
    private void initSingle(int x, int y, BellSound bell) {
        addRenderableWidget(Button.builder(Component.literal("ファイルを選ぶ…"), b -> choose())
            .bounds(x, y, 98, 20).build()).active = !loading;
        addRenderableWidget(Button.builder(Component.literal("履歴から選ぶ…"), b -> openHistory())
            .bounds(x + 102, y, 98, 20).build()).active = !loading;
        addRenderableWidget(Button.builder(Component.literal("試聴"), b -> previewBell())
            .bounds(x, y + 24, 98, 20).build()).active = pcm != null || uploaded(bell) != null;
        addRenderableWidget(Button.builder(Component.literal("アップロードして保存"), b -> {
            CustomSoundClient.upload(pos, fileName, tab, pcm);
            onClose();
        }).bounds(x + 102, y + 24, 98, 20).build()).active = pcm != null;
        if (tab == SoundChunk.MUSIC_HORN) {
            addRenderableWidget(Button.builder(Component.literal("内蔵 MH に戻す"), b -> builtin(BellEdit.MUSIC_HORN))
                .bounds(x, y + 62, 200, 20).build());
        } else if (tab == SoundChunk.APPROACH) {
            addRenderableWidget(Button.builder(Component.literal("内蔵チャイム"), b -> builtin(BellEdit.APPROACH_CHIME))
                .bounds(x, y + 62, 98, 20).build());
            addRenderableWidget(Button.builder(Component.literal("鳴らさない"), b -> builtin(BellEdit.APPROACH_OFF))
                .bounds(x + 102, y + 62, 98, 20).build());
            // Distance row: −50 −10 [n m] +10 +50 (the label is drawn in render).
            int[] ops = {BellEdit.APPROACH_MUCH_NEARER, BellEdit.APPROACH_NEARER, BellEdit.APPROACH_FARTHER,
                BellEdit.APPROACH_MUCH_FARTHER};
            String[] labels = {"−50", "−10", "+10", "+50"};
            int[] xs = {0, 32, 138, 170};
            for (int i = 0; i < ops.length; i++) {
                int op = ops[i];
                addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> send(op))
                    .bounds(x + xs[i], y + 98, 30, 20).build());
            }
        } else {
            addRenderableWidget(Button.builder(Component.literal("内蔵メロディ"), b -> builtin(BellEdit.MELODY))
                .bounds(x, y + 62, 98, 20).build());
            addRenderableWidget(Button.builder(Component.literal("内蔵ベル"), b -> builtin(BellEdit.BELL))
                .bounds(x + 102, y + 62, 98, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(x, y + (tab == SoundChunk.APPROACH ? 122 : 94), 200, 20).build());
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
        boolean room = !loading && clips.size() < DepartureBellBlockEntity.MAX_ANNOUNCEMENTS;
        addRenderableWidget(Button.builder(Component.literal("追加…"), b -> choose())
            .bounds(x, bottom, 64, 20).build()).active = room;
        addRenderableWidget(Button.builder(Component.literal("履歴…"), b -> openHistory())
            .bounds(x + 68, bottom, 64, 20).build()).active = room;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
            .bounds(x + 136, bottom, 64, 20).build());
    }

    private void openHistory() {
        minecraft.setScreen(new SoundHistoryScreen(this));
    }

    /** A sound picked from the history: like a file just loaded, but already decoded. */
    void pick(String name, byte[] sound) {
        if (tab == SoundChunk.ANNOUNCEMENT) {
            CustomSoundClient.upload(pos, name, SoundChunk.ANNOUNCEMENT, sound);
            status = Component.literal("「" + name + "」をアップロードしました");
        } else {
            fileName = name;
            pcm = sound;
            status = Component.empty();
        }
    }

    private void switchTab(int target) {
        tab = target;
        fileName = null;
        pcm = null;
        status = Component.empty();
        CustomSoundClient.preview(null);
        rebuildWidgets();
    }

    private BellSound bell() {
        return minecraft.level.getBlockEntity(pos) instanceof BellSound be ? be : null;
    }

    /** The departure bell block (which also holds 車内放送 and MH), or null for a 高度駅. */
    private DepartureBellBlockEntity trainBell() {
        return bell() instanceof DepartureBellBlockEntity be ? be : null;
    }

    private String contents() {
        BellSound bell = bell();
        DepartureBellBlockEntity trainBell = trainBell();
        return bell == null ? "" : bell.soundHash() + bell.melody()
            + bell.approach() + bell.approachChime() + bell.approachDistance()
            + (bell instanceof AdvancedStationBlockEntity station && station.twoWay())
            + (trainBell == null ? "" : trainBell.announcements() + "" + trainBell.musicHorn());
    }

    /** Hash of the uploaded sound the 発車ベル / MH tab shows, or null for the built-in one. */
    private String uploaded(BellSound bell) {
        if (bell == null) {
            return null;
        }
        if (tab == SoundChunk.MUSIC_HORN) {
            return bell instanceof DepartureBellBlockEntity be && be.musicHorn() != null ? be.musicHorn().hash() : null;
        }
        if (tab == SoundChunk.APPROACH) {
            return bell.approach() != null ? bell.approach().hash() : null;
        }
        return bell.hasSound() ? bell.soundHash() : null;
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

    /** 発車ベル / MH tab: keep the file to preview / upload. 車内放送 tab: upload it right away. */
    private void load(Path path) {
        boolean announcement = tab == SoundChunk.ANNOUNCEMENT;
        decode(path, (name, result) -> {
            if (announcement) {
                CustomSoundClient.upload(pos, name, SoundChunk.ANNOUNCEMENT, result.pcm());
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
        String uploaded = uploaded(bell());
        if (pcm != null) {
            CustomSoundClient.preview(pcm);
        } else if (uploaded != null) {
            previewClip(uploaded);
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
        send(op);
        onClose();
    }

    private void send(int op) {
        TrainCabNetwork.CHANNEL.sendToServer(new BellEdit(pos, op));
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
        if (tab == SoundChunk.ANNOUNCEMENT) {
            DepartureBellBlockEntity bell = trainBell();
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
            g.drawCenteredString(font, "現在: " + current(bell()), cx, y - 24, WHITE);
            if (tab == SoundChunk.MUSIC_HORN && status.getString().isEmpty()) {
                g.drawCenteredString(font, "列車に組み込むと、運転士が M で鳴らせます", cx, y + 48, GREY);
            }
            if (tab == SoundChunk.APPROACH && status.getString().isEmpty()) {
                g.drawCenteredString(font, trainBell() != null ? "近くの駅（32 ブロック以内）に停まる列車が近づくと鳴ります"
                    : "ここに停まる列車が近づくと、駅で鳴ります", cx, y + 48, GREY);
            }
            if (tab == SoundChunk.APPROACH && bell() != null) {
                g.drawCenteredString(font, "鳴らし始める距離（線路に沿って）", cx, y + 86, GREY);
                g.drawCenteredString(font, bell().approachDistance() + " m", cx, y + 104, WHITE);
            }
            g.drawCenteredString(font, pcm != null ? "選択中: " + fileName + " (" + seconds(pcm.length) + ")"
                : "mp3 / wav / ogg をここへドラッグ＆ドロップしても選べます", cx, y - 12, GREY);
            g.drawCenteredString(font, status, cx, y + 48, GREY);
        }
    }

    private String current(BellSound bell) {
        if (bell == null) {
            return "-";
        }
        if (tab == SoundChunk.MUSIC_HORN && bell instanceof DepartureBellBlockEntity be) {
            Clip horn = be.musicHorn();
            return horn == null ? "内蔵ミュージックホーン" : horn.name() + " (" + seconds(horn.bytes()) + ")";
        }
        if (tab == SoundChunk.APPROACH) {
            Clip clip = bell.approach();
            return clip != null ? clip.name() + " (" + seconds(clip.bytes()) + ")"
                : bell.approachChime() ? "内蔵チャイム" : "鳴らさない";
        }
        if (bell.hasSound()) {
            return bell.soundName() + " (" + seconds(bell.soundBytes()) + ")";
        }
        return bell.melody() ? "内蔵メロディ" : "内蔵ベル";
    }

    private static String seconds(int bytes) {
        return String.format("%.1f 秒", bytes / 2.0 / CustomSounds.SAMPLE_RATE);
    }
}
