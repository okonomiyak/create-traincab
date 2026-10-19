package uk.iwaservice.traincab.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import uk.iwaservice.traincab.mascon.Conductor;

import java.util.List;

/** The conductor's N on a train carrying 車内放送: pick one (or press its number), or the usual chime and text (0). */
public class AnnouncementMenuScreen extends Screen {

    private final List<String> names;

    private AnnouncementMenuScreen(List<String> names) {
        super(Component.literal("車内放送"));
        this.names = names;
    }

    public static void open(List<String> names, List<String> hashes) {
        hashes.forEach(CustomSoundClient::prefetch);
        Minecraft.getInstance().setScreen(new AnnouncementMenuScreen(names));
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - (names.size() + 2) * 11;
        addRenderableWidget(Button.builder(Component.literal("0. 次駅案内（チャイムと文字）"), b -> choose(-1))
            .bounds(x, y, 200, 20).build());
        for (int i = 0; i < names.size(); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.literal((i + 1) + ". " + names.get(i)), b -> choose(index))
                .bounds(x, y + 22 * (i + 1), 200, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
            .bounds(x, y + 22 * (names.size() + 1) + 4, 200, 20).build());
    }

    private void choose(int index) {
        ConductorClient.send(Conductor.ANNOUNCE_CLIP, index);
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int digit = keyCode - GLFW.GLFW_KEY_0;
        if (digit >= 0 && digit <= Math.min(9, names.size())) {
            choose(digit - 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, height / 2 - (names.size() + 2) * 11 - 14, 0xFFFFFF);
    }
}
