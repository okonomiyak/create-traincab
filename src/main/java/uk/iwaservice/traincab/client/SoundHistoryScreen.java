package uk.iwaservice.traincab.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity.Clip;
import uk.iwaservice.traincab.sound.CustomSounds;

import java.util.List;

/** The sounds this player uploaded before: pick one to use again without choosing the file. */
class SoundHistoryScreen extends Screen {

    private static final int GREY = 0xA0A0A0;
    private static final int ROW_H = 22;

    private final DepartureBellScreen parent;
    private Component status = Component.empty();

    SoundHistoryScreen(DepartureBellScreen parent) {
        super(Component.literal("アップロードの履歴"));
        this.parent = parent;
    }

    private int top() {
        return Math.max(4, height / 2 - 110);
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = top() + 30;
        List<Clip> clips = CustomSoundClient.history();
        for (int i = 0; i < clips.size(); i++) {
            Clip clip = clips.get(i);
            String label = clip.name() + " (" + String.format("%.1f 秒", clip.bytes() / 2.0 / CustomSounds.SAMPLE_RATE) + ")";
            addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(label, 166)), b -> pick(clip))
                .bounds(x, y + ROW_H * i, 176, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▶"), b -> {
                byte[] pcm = CustomSoundClient.get(clip.hash());
                status = pcm == null ? Component.literal("この音声のキャッシュが見つかりません") : Component.empty();
                CustomSoundClient.preview(pcm);
            }).bounds(x + 180, y + ROW_H * i, 20, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
            .bounds(x, y + ROW_H * Math.max(clips.size(), 1) + 8, 200, 20).build());
    }

    private void pick(Clip clip) {
        byte[] pcm = CustomSoundClient.get(clip.hash());
        if (pcm == null) {
            status = Component.literal("この音声のキャッシュが見つかりません");
            return;
        }
        CustomSoundClient.preview(null);
        parent.pick(clip.name(), pcm);
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        CustomSoundClient.preview(null);
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, top(), 0xFFFFFF);
        if (CustomSoundClient.history().isEmpty()) {
            g.drawCenteredString(font, "まだありません（アップロードした音声がここに残ります）", width / 2, top() + 36, GREY);
        }
        g.drawCenteredString(font, status, width / 2, top() + 16, GREY);
    }
}
