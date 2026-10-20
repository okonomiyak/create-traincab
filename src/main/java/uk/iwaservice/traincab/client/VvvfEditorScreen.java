package uk.iwaservice.traincab.client;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import uk.iwaservice.traincab.block.InverterBlockEntity;
import uk.iwaservice.traincab.mascon.TrainCabNetwork;
import uk.iwaservice.traincab.sound.VvvfEdit;
import uk.iwaservice.traincab.sound.VvvfProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Predicate;

/**
 * Right-click on a VVVFインバーター, placed or built into a train: the editor for its sound (VvvfProfile). 力行 / 回生
 * tabs: the pulse modes from each fundamental (Hz; VvvfSynth.F_MAX at Create's top speed) on. 非同期 tab: the
 * asynchronous carrier. 全般 tab: name, presets, levels, and copy / paste of the text form to share a sound. ▶ plays a
 * station run of the sound being edited and follows the edits as they are made. ✓ or Esc saves, × closes without
 * saving.
 */
public class VvvfEditorScreen extends Screen {

    private static final int WHITE = 0xFFFFFF;
    private static final int GREY = 0xA0A0A0;
    private static final int ASYNC_COLOR = 0xFFD080;
    private static final int TEXT = 0xE0E0E0;
    private static final int RED = 0xFF6060;
    private static final int PANEL_W = 256;
    private static final int CONTENT_W = 240;
    private static final int ROW_H = 20;
    private static final int BOX_H = 16;
    private static final int TABS_H = 24;
    /** The line above a table: a hint, the 回生 cut-off or the carrier options. */
    private static final int OPTIONS_H = 22;
    private static final int TABLE_HEADER_H = 12;
    private static final int ADD_H = 24;
    private static final int STATUS_H = 14;
    private static final int FIXED_BODY_H = 4 + TABS_H + OPTIONS_H + TABLE_HEADER_H + ADD_H + STATUS_H + 4;
    /**
     * Table rows shown at once: one more than the longest table, as far as the screen allows (the rest scroll), but at
     * least what the 全般 tab needs.
     */
    private static final int MIN_ROWS = 4;
    private static final int MAX_ROWS = 10;
    private static final int ACCEL = 0;
    private static final int BRAKE = 1;
    private static final int ASYNC = 2;
    private static final int GENERAL = 3;
    private static final String[] TABS = {"力行", "回生", "非同期", "全般"};
    private static final String[] NOTES = {"C", "C#", "D", "E♭", "E", "F", "F#", "G", "G#", "A", "B♭", "B"};
    private static final String[] SOLFEGE = {"ド", "ド#", "レ", "ミ♭", "ミ", "ファ", "ファ#", "ソ", "ソ#", "ラ", "シ♭", "シ"};

    /** A pulse mode (VvvfProfile.Mode) being edited. */
    private static final class ModeRow {
        double from;
        int pulses;
        double index;

        ModeRow(double from, int pulses, double index) {
            this.from = from;
            this.pulses = pulses;
            this.index = index;
        }
    }

    /** A carrier step (VvvfProfile.Step) being edited. */
    private static final class StepRow {
        double from;
        double hz;

        StepRow(double from, double hz) {
            this.from = from;
            this.hz = hz;
        }
    }

    private final Consumer<String> save;
    private final VvvfProfile saved;
    // The sound being edited.
    private String name = "";
    private final List<ModeRow> accel = new ArrayList<>();
    private final List<ModeRow> brake = new ArrayList<>();
    private final List<StepRow> carrier = new ArrayList<>();
    private double regenMinF;
    private boolean stepped;
    private double jitter;
    private double asyncLevel;
    private double asyncSwell;

    private int tab = ACCEL;
    private int scroll;
    /** Table rows shown at once (see MIN_ROWS). */
    private int rows = MIN_ROWS;
    private String status = "";
    private IconButton previewButton;
    /** Whether previewButton shows ■, to swap it back once the run is over. */
    private boolean showsStop;

    VvvfEditorScreen(VvvfProfile saved, Consumer<String> save) {
        super(Component.literal("VVVF インバーターの音"));
        this.saved = saved;
        this.save = save;
        load(saved);
    }

    /** For the placed inverter at pos. */
    public static void openPlaced(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        String code = mc.level.getBlockEntity(pos) instanceof InverterBlockEntity be ? be.code() : "";
        mc.setScreen(new VvvfEditorScreen(VvvfProfile.orDefault(code),
            c -> TrainCabNetwork.CHANNEL.sendToServer(new VvvfEdit(VvvfEdit.PLACED, pos, c))));
    }

    /** For the inverter at localPos of a train's carriage. */
    public static void openOnTrain(AbstractContraptionEntity entity, BlockPos localPos) {
        StructureBlockInfo info = entity.getContraption() == null ? null : entity.getContraption().getBlocks().get(localPos);
        String code = info == null || info.nbt() == null ? "" : info.nbt().getString(InverterBlockEntity.TAG);
        int id = entity.getId();
        Minecraft.getInstance().setScreen(new VvvfEditorScreen(VvvfProfile.orDefault(code),
            c -> TrainCabNetwork.CHANNEL.sendToServer(new VvvfEdit(id, localPos, c))));
    }

    /** The speed (km/h) at fundamental f, on this server. */
    static long kmh(double f) {
        double topSpeed;
        try {
            topSpeed = AllConfigs.server().trains.trainTopSpeed.getF();
        } catch (RuntimeException e) {
            // The server's config hasn't arrived: Create's default.
            topSpeed = 28;
        }
        return Math.round(f / VvvfSynth.F_MAX * topSpeed * 3.6);
    }

    /** "F4 ファ", with how many cents off when not right on a note. */
    private static String noteName(double hz) {
        double midi = 69 + 12 * Math.log(hz / 440) / Math.log(2);
        int note = (int) Math.round(midi);
        int cents = (int) Math.round((midi - note) * 100);
        String name = NOTES[Math.floorMod(note, 12)] + (Math.floorDiv(note, 12) - 1) + " " + SOLFEGE[Math.floorMod(note, 12)];
        return Math.abs(cents) < 5 ? name : name + String.format(Locale.ROOT, " %+d¢", cents);
    }

    private void load(VvvfProfile p) {
        name = p.name();
        accel.clear();
        p.accel().forEach(m -> accel.add(new ModeRow(m.from(), m.pulses(), m.index())));
        brake.clear();
        p.brake().forEach(m -> brake.add(new ModeRow(m.from(), m.pulses(), m.index())));
        carrier.clear();
        p.carrier().forEach(s -> carrier.add(new StepRow(s.from(), s.hz())));
        regenMinF = p.regenMinF();
        stepped = p.stepped();
        jitter = p.jitter();
        asyncLevel = p.asyncLevel();
        asyncSwell = p.asyncSwell();
        scroll = 0;
    }

    /** The sound as edited so far. */
    private VvvfProfile profile() {
        return new VvvfProfile(name, modes(accel), modes(brake), regenMinF,
            carrier.stream().map(r -> new VvvfProfile.Step(r.from, r.hz)).toList(), stepped, jitter, asyncLevel,
            asyncSwell).normalized();
    }

    private static List<VvvfProfile.Mode> modes(List<ModeRow> rows) {
        return rows.stream().map(r -> new VvvfProfile.Mode(r.from, r.pulses, r.index)).toList();
    }

    private int bodyH() {
        return FIXED_BODY_H + rows * ROW_H;
    }

    private int top() {
        return Math.max(4, (height - CreateGui.HEADER_H - bodyH() - CreateGui.FOOTER_H) / 2);
    }

    private int left() {
        return width / 2 - CONTENT_W / 2;
    }

    /** Where the open tab's own widgets start, under the tab buttons. */
    private int contentTop() {
        return top() + CreateGui.HEADER_H + 4 + TABS_H;
    }

    private int tableTop() {
        return contentTop() + OPTIONS_H + TABLE_HEADER_H;
    }

    private int addTop() {
        return tableTop() + rows * ROW_H + 3;
    }

    private int statusTop() {
        return tableTop() + rows * ROW_H + ADD_H + 3;
    }

    @Override
    protected void init() {
        int longest = Math.max(Math.max(accel.size(), brake.size()), carrier.size());
        int fit = (height - 8 - CreateGui.HEADER_H - CreateGui.FOOTER_H - FIXED_BODY_H) / ROW_H;
        rows = Math.max(MIN_ROWS, Math.min(Math.min(longest + 1, MAX_ROWS), fit));
        int x = left();
        for (int i = 0; i < TABS.length; i++) {
            int target = i;
            addRenderableWidget(new CreateGui.TextButton(Button.builder(Component.literal(TABS[i]), b -> switchTab(target))
                .bounds(x + 1 + 60 * i, top() + CreateGui.HEADER_H + 4, 58, 20), tab == i));
        }
        int y = contentTop();
        switch (tab) {
            case ACCEL -> initModes(accel);
            case BRAKE -> {
                number(x + 104, y + 2, 40, regenMinF, 0, VvvfProfile.MAX_F, 1,
                    "これより遅くなると回生の音が消える (0〜" + VvvfProfile.num(VvvfProfile.MAX_F) + " Hz)",
                    v -> regenMinF = v);
                initModes(brake);
            }
            case ASYNC -> {
                addRenderableWidget(Button.builder(Component.literal(stepped ? "変え方: 段々 (音階)" : "変え方: なめらか"), b -> {
                    stepped = !stepped;
                    changed();
                    rebuildWidgets();
                }).bounds(x, y + 2, 120, 18).tooltip(Tooltip.create(Component.literal(
                    "段々: 行ごとの周波数に飛ぶ (音階になる)。なめらか: 行の間を少しずつ変わる"))).build(CreateGui.TextButton::new));
                number(x + 176, y + 2, 34, jitter, 0, VvvfProfile.MAX_JITTER, 100,
                    "キャリアをランダムに揺らす量 (0〜" + VvvfProfile.num(VvvfProfile.MAX_JITTER * 100) + " %)。ランダム変調の「シャー」",
                    v -> jitter = v);
                initSteps();
            }
            default -> initGeneral();
        }
        int footer = top() + CreateGui.HEADER_H + bodyH();
        int panelLeft = width / 2 - PANEL_W / 2;
        showsStop = VvvfPreview.isPlaying();
        previewButton = addRenderableWidget(CreateGui.icon(panelLeft + 8, footer + 6, AllIcons.I_PLAY, "",
            this::togglePreview));
        showPreviewState();
        addRenderableWidget(CreateGui.icon(panelLeft + PANEL_W - 52, footer + 6, AllIcons.I_MTD_CLOSE, "保存せずに閉じる",
            super::onClose));
        addRenderableWidget(CreateGui.icon(panelLeft + PANEL_W - 30, footer + 6, AllIcons.I_CONFIRM, "保存して閉じる",
            this::onClose));
    }

    private void initModes(List<ModeRow> modes) {
        modes.sort(Comparator.comparingDouble(r -> r.from));
        modes.get(0).from = 0;
        scroll = Mth.clamp(scroll, 0, Math.max(0, modes.size() - rows));
        int x = left();
        int y = tableTop();
        for (int i = scroll; i < Math.min(modes.size(), scroll + rows); i++, y += ROW_H) {
            ModeRow row = modes.get(i);
            fromBox(x, y, row.from, i == 0, v -> row.from = v);
            box(x + 80, y + 2, 30, String.valueOf(row.pulses),
                "1 周期のパルス数 (0 = 非同期、1 = 1 パルス、最大 " + VvvfProfile.MAX_PULSES + ")", true, s -> {
                    int v = Integer.parseInt(s);
                    if (v > VvvfProfile.MAX_PULSES) {
                        return false;
                    }
                    row.pulses = v;
                    return true;
                });
            number(x + 158, y + 2, 40, row.index, 0, VvvfProfile.MAX_INDEX, 1,
                "変調率 (0〜" + VvvfProfile.num(VvvfProfile.MAX_INDEX) + ")。次の行の値までなめらかに変わる", v -> row.index = v);
            trash(x + 222, y + 1, modes, i);
        }
        tableButtons(modes, VvvfProfile.MAX_MODES, () -> {
            ModeRow last = modes.get(modes.size() - 1);
            modes.add(new ModeRow(Math.min(last.from + 5, VvvfProfile.MAX_F), last.pulses, last.index));
        });
    }

    private void initSteps() {
        carrier.sort(Comparator.comparingDouble(r -> r.from));
        carrier.get(0).from = 0;
        scroll = Mth.clamp(scroll, 0, Math.max(0, carrier.size() - rows));
        int x = left();
        int y = tableTop();
        for (int i = scroll; i < Math.min(carrier.size(), scroll + rows); i++, y += ROW_H) {
            StepRow row = carrier.get(i);
            fromBox(x, y, row.from, i == 0, v -> row.from = v);
            number(x + 80, y + 2, 44, row.hz, VvvfProfile.MIN_HZ, VvvfProfile.MAX_HZ, 1,
                "非同期のキャリア周波数 (" + VvvfProfile.num(VvvfProfile.MIN_HZ) + "〜" + VvvfProfile.num(VvvfProfile.MAX_HZ)
                    + " Hz)", v -> row.hz = v);
            trash(x + 222, y + 1, carrier, i);
        }
        tableButtons(carrier, VvvfProfile.MAX_STEPS, () -> {
            StepRow last = carrier.get(carrier.size() - 1);
            // In a scale, the next note a whole tone up.
            double hz = stepped ? Math.round(last.hz * Math.pow(2, 2 / 12.0) * 10) / 10.0 : last.hz;
            carrier.add(new StepRow(Math.min(last.from + 1, VvvfProfile.MAX_F), Math.min(hz, VvvfProfile.MAX_HZ)));
        });
    }

    private void initGeneral() {
        int x = left();
        int y = contentTop();
        EditBox nameBox = new EditBox(font, x + 40, y + 2, 200, BOX_H, Component.literal("名前"));
        nameBox.setMaxLength(VvvfProfile.MAX_NAME);
        nameBox.setValue(name);
        nameBox.setResponder(s -> name = s);
        addRenderableWidget(nameBox);
        for (int i = 0; i < VvvfProfile.PRESETS.size(); i++) {
            VvvfProfile preset = VvvfProfile.PRESETS.get(i);
            addRenderableWidget(Button.builder(Component.literal(preset.name()), b -> {
                load(preset);
                changed();
                status = "プリセット「" + preset.name() + "」を読み込みました";
                rebuildWidgets();
            }).bounds(x + 1 + 60 * i, y + 34, 58, 18).build(CreateGui.TextButton::new));
        }
        number(x + 200, y + 58, 40, asyncLevel, 0, 1, 1,
            "発車時の非同期の音の大きさ (0〜1)。最初の同期モードまでに 1 になる", v -> asyncLevel = v);
        number(x + 200, y + 78, 40, asyncSwell, 0, 1, 1,
            "発車時の非同期の音が、モーターの回転 (2f) でうなる強さ (0〜1)", v -> asyncSwell = v);
        String[] labels = {"コピー", "貼り付け", "元に戻す"};
        String[] tips = {"この音をコードにしてクリップボードへ", "クリップボードのコードを読み込む", "保存してある音に戻す"};
        Runnable[] actions = {this::copy, this::paste, this::revert};
        for (int i = 0; i < labels.length; i++) {
            Runnable action = actions[i];
            addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> action.run())
                .bounds(x + 1 + 80 * i, y + 100, 78, 18).tooltip(Tooltip.create(Component.literal(tips[i])))
                .build(CreateGui.TextButton::new));
        }
    }

    /**
     * A field showing value; accept gets each edit and says whether it is valid (else the field turns red and the
     * sound keeps the last valid value).
     */
    private EditBox box(int x, int y, int w, String value, String tooltip, boolean integer, Predicate<String> accept) {
        EditBox box = new EditBox(font, x, y, w, BOX_H, Component.literal(tooltip));
        box.setMaxLength(8);
        box.setFilter(s -> s.matches(integer ? "[0-9]*" : "[0-9]*\\.?[0-9]*"));
        box.setValue(value);
        box.setTooltip(Tooltip.create(Component.literal(tooltip)));
        box.setResponder(s -> {
            boolean ok;
            try {
                ok = accept.test(s);
            } catch (NumberFormatException e) {
                ok = false;
            }
            box.setTextColor(ok ? TEXT : RED);
            if (ok) {
                changed();
            }
        });
        return addRenderableWidget(box);
    }

    /** A field for a number in [min, max], shown multiplied by scale (100 for percent). */
    private EditBox number(int x, int y, int w, double value, double min, double max, double scale, String tooltip,
                           DoubleConsumer set) {
        return box(x, y, w, VvvfProfile.num(value * scale), tooltip, false, s -> {
            double v = Double.parseDouble(s) / scale;
            if (v < min || v > max) {
                return false;
            }
            set.accept(v);
            return true;
        });
    }

    /** The fundamental a row starts from; the first row always starts from 0. */
    private void fromBox(int x, int y, double from, boolean first, DoubleConsumer set) {
        number(x, y + 2, 40, from, 0, VvvfProfile.MAX_F, 1, first ? "最初の行はいつも 0 Hz から"
            : "この速さから (インバーターの周波数 Hz。最高速で " + VvvfProfile.num(VvvfSynth.F_MAX) + " Hz)", set)
            .setEditable(!first);
    }

    private void trash(int x, int y, List<?> list, int index) {
        addRenderableWidget(CreateGui.icon(x, y, AllIcons.I_TRASH, "この行を消す", () -> {
            list.remove(index);
            changed();
            rebuildWidgets();
        })).active = list.size() > 1;
    }

    /** Under the table: add a row, and scroll when they don't all fit. */
    private void tableButtons(List<?> list, int max, Runnable add) {
        int x = left();
        int y = addTop();
        addRenderableWidget(Button.builder(Component.literal("＋ 行を追加"), b -> {
            add.run();
            scroll = Math.max(0, list.size() - rows);
            changed();
            rebuildWidgets();
        }).bounds(x, y, 100, 18).build(CreateGui.TextButton::new)).active = list.size() < max;
        if (list.size() > rows) {
            addRenderableWidget(CreateGui.icon(x + 200, y, AllIcons.I_PRIORITY_HIGH, "上へ (ホイールでも)", () -> scrollBy(-1)))
                .active = scroll > 0;
            addRenderableWidget(CreateGui.icon(x + 222, y, AllIcons.I_PRIORITY_LOW, "下へ (ホイールでも)", () -> scrollBy(1)))
                .active = scroll + rows < list.size();
        }
    }

    private int tableSize() {
        return tab == ACCEL ? accel.size() : tab == BRAKE ? brake.size() : tab == ASYNC ? carrier.size() : 0;
    }

    private void scrollBy(int by) {
        scroll += by;
        rebuildWidgets();
    }

    private void switchTab(int target) {
        tab = target;
        scroll = 0;
        rebuildWidgets();
    }

    /** The sound was edited: the 試聴 follows. */
    private void changed() {
        if (VvvfPreview.isPlaying()) {
            VvvfPreview.update(VvvfSynth.Tables.of(profile()));
        }
    }

    private void togglePreview() {
        if (VvvfPreview.isPlaying()) {
            VvvfPreview.stop();
        } else {
            VvvfPreview.play(VvvfSynth.Tables.of(profile()));
        }
        showPreviewState();
    }

    private void showPreviewState() {
        showsStop = VvvfPreview.isPlaying();
        previewButton.setIcon(showsStop ? AllIcons.I_STOP : AllIcons.I_PLAY);
        previewButton.setToolTip(Component.literal(showsStop ? "試聴を止める"
            : "試聴: 発車→最高速→停車 (約 27 秒)。鳴らしながら編集するとすぐ反映されます"));
    }

    private void copy() {
        String code = profile().code();
        minecraft.keyboardHandler.setClipboard(code);
        status = "コードをコピーしました (" + code.length() + " 文字"
            + (code.length() > 256 ? "。長いのでチャットには貼れません)" : ")");
    }

    private void paste() {
        VvvfProfile pasted = VvvfProfile.parse(minecraft.keyboardHandler.getClipboard());
        if (pasted == null) {
            status = "クリップボードに VVVF の音のコードがありません";
            return;
        }
        load(pasted);
        changed();
        status = "「" + (pasted.name().isEmpty() ? "名前なし" : pasted.name()) + "」を貼り付けました";
        rebuildWidgets();
    }

    private void revert() {
        load(saved);
        changed();
        status = "保存してある音に戻しました";
        rebuildWidgets();
    }

    @Override
    public void tick() {
        if (VvvfPreview.isPlaying() != showsStop) {
            showPreviewState();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta != 0 && tableSize() > rows) {
            scrollBy(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** ✓ and Esc: save (when anything changed) and close. */
    @Override
    public void onClose() {
        VvvfProfile edited = profile();
        if (!edited.equals(saved)) {
            save.accept(edited.code());
        }
        super.onClose();
    }

    @Override
    public void removed() {
        VvvfPreview.stop();
    }

    @Override
    public boolean isPauseScreen() {
        // Pausing would also pause the 試聴.
        return false;
    }

    private void drawPanel(GuiGraphics g) {
        String title = "VVVF: " + (name.isBlank() ? "名前なし" : name.strip()) + (profile().equals(saved) ? "" : " *");
        CreateGui.panel(g, font, Component.literal(CreateGui.fit(font, title, PANEL_W - 16)), width / 2 - PANEL_W / 2,
            top(), PANEL_W, bodyH());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        drawPanel(g);
        super.render(g, mouseX, mouseY, partialTick);
        int x = left();
        int y = contentTop() + 6;
        switch (tab) {
            case ACCEL -> {
                g.drawString(font, "速さごとのパルス数 (0 = 非同期、1 = 1 パルス)", x, y, GREY);
                drawModes(g, accel);
            }
            case BRAKE -> {
                g.drawString(font, "回生が消える速さ", x, y, WHITE);
                g.drawString(font, "Hz  ≈" + kmh(regenMinF) + " km/h", x + 148, y, GREY);
                drawModes(g, brake);
            }
            case ASYNC -> {
                g.drawString(font, "揺らぎ ±", x + 128, y, WHITE);
                g.drawString(font, "%", x + 214, y, WHITE);
                drawSteps(g);
            }
            default -> drawGeneral(g);
        }
        String line = VvvfPreview.isPlaying() ? VvvfPreview.describe() : status;
        g.drawCenteredString(font, CreateGui.fit(font, line, CONTENT_W), width / 2, statusTop(), GREY);
    }

    private void drawModes(GuiGraphics g, List<ModeRow> modes) {
        int x = left();
        int y = tableTop();
        g.drawString(font, "開始 Hz", x, y - 10, GREY);
        g.drawString(font, "km/h", x + 44, y - 10, GREY);
        g.drawString(font, "パルス", x + 80, y - 10, GREY);
        g.drawString(font, "変調率", x + 158, y - 10, GREY);
        for (int i = scroll; i < Math.min(modes.size(), scroll + rows); i++, y += ROW_H) {
            ModeRow row = modes.get(i);
            g.drawString(font, "≈" + kmh(row.from), x + 44, y + 6, GREY);
            g.drawString(font, row.pulses == 0 ? "非同期" : row.pulses + "P", x + 114, y + 6,
                row.pulses == 0 ? ASYNC_COLOR : WHITE);
        }
        drawRange(g, modes.size());
    }

    private void drawSteps(GuiGraphics g) {
        int x = left();
        int y = tableTop();
        g.drawString(font, "開始 Hz", x, y - 10, GREY);
        g.drawString(font, "km/h", x + 44, y - 10, GREY);
        g.drawString(font, "キャリア Hz", x + 80, y - 10, GREY);
        g.drawString(font, "音名", x + 136, y - 10, GREY);
        for (int i = scroll; i < Math.min(carrier.size(), scroll + rows); i++, y += ROW_H) {
            StepRow row = carrier.get(i);
            g.drawString(font, "≈" + kmh(row.from), x + 44, y + 6, GREY);
            g.drawString(font, CreateGui.fit(font, noteName(row.hz), 84), x + 136, y + 6, WHITE);
        }
        drawRange(g, carrier.size());
    }

    /** "3–6 / 7" when the table doesn't fit. */
    private void drawRange(GuiGraphics g, int size) {
        if (size > rows) {
            g.drawString(font, (scroll + 1) + "–" + Math.min(size, scroll + rows) + " / " + size, left() + 108,
                addTop() + 5, GREY);
        }
    }

    private void drawGeneral(GuiGraphics g) {
        int x = left();
        int y = contentTop();
        g.drawString(font, "名前", x, y + 6, WHITE);
        g.drawString(font, "プリセットを読み込む (今の編集と置き換え)", x, y + 24, GREY);
        g.drawString(font, "発車時の非同期の大きさ", x, y + 62, WHITE);
        g.drawString(font, "非同期のうなり (2f)", x, y + 82, WHITE);
        g.drawString(font, CreateGui.fit(font, "コードはチャットなどに貼って共有できます", CONTENT_W), x, y + 124, GREY);
    }
}
