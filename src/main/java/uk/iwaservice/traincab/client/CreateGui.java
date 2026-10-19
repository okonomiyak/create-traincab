package uk.iwaservice.traincab.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import uk.iwaservice.traincab.TrainCabMod;

/**
 * Create's look for this mod's screens: the station screen's brass title bar, dark body and grey footer, and
 * Create's buttons. textures/gui/panel.png holds those pieces, cut from Create's own GUI textures.
 */
final class CreateGui {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "textures/gui/panel.png");
    static final int HEADER_H = 15;
    static final int FOOTER_H = 30;
    private static final int TITLE_COLOR = 0x592424;
    /** Widest a line of text may be inside the panel's body. */
    static final int TEXT_W = 204;

    private CreateGui() {
    }

    /** A w-wide window at (x, y): title bar, a bodyH-tall body, then the footer. */
    static void panel(GuiGraphics g, Font font, Component title, int x, int y, int w, int bodyH) {
        RenderSystem.enableBlend();
        slice(g, x, y, w, HEADER_H, 0, 0, 9, HEADER_H, 4, 0);
        int top = y + HEADER_H;
        slice(g, x, top, w, 1, 0, 48, 9, 1, 4, 0);
        // The checker only tiles, so the body is laid in 64×64 pieces.
        for (int ty = 1; ty < bodyH - 1; ty += 64) {
            int th = Math.min(64, bodyH - 1 - ty);
            g.blit(TEXTURE, x, top + ty, 16, 0, 4, th);
            g.blit(TEXTURE, x + w - 4, top + ty, 84, 0, 4, th);
            for (int tx = 4; tx < w - 4; tx += 64) {
                g.blit(TEXTURE, x + tx, top + ty, 20, 0, Math.min(64, w - 4 - tx), th);
            }
        }
        slice(g, x, top + bodyH - 1, w, 1, 0, 50, 9, 1, 4, 0);
        slice(g, x, top + bodyH, w, FOOTER_H, 0, 16, 9, FOOTER_H, 4, 0);
        RenderSystem.disableBlend();
        g.drawString(font, title, x + (w - font.width(title)) / 2, y + 4, TITLE_COLOR, false);
    }

    /** Text cut to w pixels, ending in … when it had to be cut. */
    static String fit(Font font, String text, int w) {
        return font.width(text) <= w ? text : font.plainSubstrByWidth(text, w - font.width("…")) + "…";
    }

    /** A centred line of text, cut to the panel's width. */
    static void centered(GuiGraphics g, Font font, String text, int cx, int y, int color) {
        g.drawCenteredString(font, fit(font, text, TEXT_W), cx, y, color);
    }

    /** Draws the sw×sh texture piece at (u, v) stretched to w×h, keeping a bx / by wide border unstretched. */
    private static void slice(GuiGraphics g, int x, int y, int w, int h, int u, int v, int sw, int sh, int bx, int by) {
        int[] xs = {x, x + bx, x + w - bx, x + w};
        int[] us = {u, u + bx, u + sw - bx, u + sw};
        int[] ys = {y, y + by, y + h - by, y + h};
        int[] vs = {v, v + by, v + sh - by, v + sh};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (xs[i + 1] > xs[i] && ys[j + 1] > ys[j]) {
                    g.blit(TEXTURE, xs[i], ys[j], xs[i + 1] - xs[i], ys[j + 1] - ys[j], us[i], vs[j],
                        us[i + 1] - us[i], vs[j + 1] - vs[j], 256, 256);
                }
            }
        }
    }

    /** Create's square icon button (18×18). */
    static IconButton icon(int x, int y, AllIcons icon, String tooltip, Runnable action) {
        IconButton button = new IconButton(x, y, icon);
        button.withCallback(action);
        button.setToolTip(Component.literal(tooltip));
        return button;
    }

    /** A text button drawn with Create's button texture; a selected one (an open tab) looks pressed in. */
    static final class TextButton extends Button {

        private final boolean selected;

        TextButton(Button.Builder builder) {
            this(builder, false);
        }

        TextButton(Button.Builder builder, boolean selected) {
            super(builder);
            this.selected = selected;
            active = !selected;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int u = selected ? 36 : !active ? 54 : isHoveredOrFocused() ? 18 : 0;
            slice(g, getX(), getY(), width, height, u, 64, 18, 18, 1, 1);
            Font font = Minecraft.getInstance().font;
            g.drawCenteredString(font, fit(font, getMessage().getString(), width - 6), getX() + width / 2,
                getY() + (height - 8) / 2, active || selected ? 0xFFFFFF : 0xA0A0A0);
        }
    }
}
