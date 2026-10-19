package uk.iwaservice.traincab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import uk.iwaservice.traincab.TrainCabConfig;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.TrainCabServerConfig;
import uk.iwaservice.traincab.mascon.Announcement;
import uk.iwaservice.traincab.mascon.CrewStatus;
import uk.iwaservice.traincab.mascon.Mascon;
import uk.iwaservice.traincab.mascon.StationInfo;
import uk.iwaservice.traincab.mascon.StopResult;

/**
 * Compact cab panel: analog speedometer, the mascon notch and reverser, a status line, and the
 * distance to the next station's stop point. An on-duty conductor gets their own panel instead.
 */
public final class TrainCabHudLayer {

    private static final ResourceLocation GAUGE_BACKGROUND =
        new ResourceLocation(TrainCabMod.MODID, "textures/gui/train_hud/gauge_background.png");
    private static final ResourceLocation GAUGE_NEEDLE =
        new ResourceLocation(TrainCabMod.MODID, "textures/gui/train_hud/gauge_needle.png");
    private static final ResourceLocation PRESSURE_BACKGROUND =
        new ResourceLocation(TrainCabMod.MODID, "textures/gui/train_hud/pressure_background.png");
    private static final int TEXTURE_SIZE = 400;

    private static final float NEEDLE_MIN_ANGLE = -120f;
    private static final float NEEDLE_MAX_ANGLE = 120f;
    private static final double GAUGE_MAX_KMH = 160.0;

    // ---- panel layout (GUI pixels, before TrainCabConfig.scale) ----
    // Speedometer on the left; notch, notch strip and status line stacked and vertically centred on the right.
    private static final int PAD = 6;
    private static final int GAUGE_SIZE = 72;
    private static final int RIGHT_X = PAD + GAUGE_SIZE + 8;
    private static final int RIGHT_W = 104;
    private static final int PANEL_W = RIGHT_X + RIGHT_W + PAD;
    private static final int PANEL_H = GAUGE_SIZE + 2 * PAD;
    /** With a 高度運転台 the brake cylinder pressure gauge sits on the right, same size and needle as the speedometer. */
    private static final int PRESSURE_X = RIGHT_X + RIGHT_W + 8;
    private static final int ADVANCED_PANEL_W = PRESSURE_X + GAUGE_SIZE + PAD;
    private static final int NOTCH_H = 22;
    private static final int SEGMENT_H = 4;
    private static final int NOTCH_Y = PAD + (GAUGE_SIZE - (NOTCH_H + 3 + SEGMENT_H + 8 + 9 + 3 + 9)) / 2;
    private static final int NOTCH_COUNT = Mascon.MAX_POWER - Mascon.EB + 1;
    private static final int SEGMENT_W = RIGHT_W / NOTCH_COUNT;
    private static final int SEGMENT_Y = NOTCH_Y + NOTCH_H + 3;
    private static final int STATUS_Y = SEGMENT_Y + SEGMENT_H + 8;
    private static final int NEXT_STATION_Y = STATUS_Y + 12;
    private static final int BC_MAX_KPA = 500;
    private static final int CONDUCTOR_W = 190;
    /** How long (ticks) a 車内放送 stays on screen. */
    private static final int ANNOUNCEMENT_TICKS = 100;
    /** How long (ticks) the stop accuracy stays in place of the next-station line after arriving. */
    private static final int STOP_RESULT_TICKS = 100;
    private static final int LAMP_W = 30;

    private static final int COLOR_PANEL_BG = 0xC0222222;
    private static final int COLOR_BORDER = 0xFF101014;
    private static final int COLOR_TEXT = 0xFFE8E8E8;
    private static final int COLOR_TEXT_DIM = 0xFF808088;
    private static final int COLOR_INACTIVE = 0xFF26262B;
    private static final int COLOR_GREEN = 0xFF3FBF5F;
    private static final int COLOR_RED = 0xFFE0483F;
    private static final int COLOR_ORANGE = 0xFFE0A030;
    private static final int COLOR_SCREW = 0xFF0A0A0C;
    private static final int COLOR_SCREW_RIM = 0xFF4A4A50;

    private TrainCabHudLayer() {
    }

    public static void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!TrainCabConfig.enabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }
        drawAnnouncement(guiGraphics, mc.font);
        if (!TrainDrivingState.isDriving()) {
            if (ConductorClient.isConductor()) {
                renderConductor(guiGraphics, mc.font);
            } else {
                renderPassenger(guiGraphics, mc.font);
            }
            return;
        }

        Train train = TrainDrivingState.getActiveTrain();
        if (train == null) {
            return;
        }

        float speedBpt = Math.abs(TrainDrivingState.getInterpolatedSpeedBlocksPerTick(partialTick));
        double speedKmh = speedBpt * 20 * 3.6;
        boolean kmh = TrainCabConfig.unit == TrainCabConfig.SpeedUnit.KMH;

        Font font = mc.font;
        boolean advanced = TrainDrivingState.hasAdvancedCab();
        pushPanel(guiGraphics, advanced ? ADVANCED_PANEL_W : PANEL_W, PANEL_H);

        drawGauge(guiGraphics, font, PAD, GAUGE_BACKGROUND, toAngle(speedKmh / GAUGE_MAX_KMH),
            kmh ? speedKmh : speedBpt * 20, kmh ? "km/h" : "blk/s");
        if (advanced) {
            float bc = TrainDrivingState.getInterpolatedBc(partialTick);
            drawGauge(guiGraphics, font, PRESSURE_X, PRESSURE_BACKGROUND, toAngle(bc / BC_MAX_KPA), bc, "BC kPa");
        }
        drawNotchRow(guiGraphics, font);
        drawStatusLine(guiGraphics, font, train);
        if (StopResult.latest != null && StopResult.age <= STOP_RESULT_TICKS) {
            drawStopResult(guiGraphics, font, StopResult.latest.error());
        } else {
            drawNextStation(guiGraphics, font, RIGHT_X, RIGHT_W, NEXT_STATION_Y);
        }

        guiGraphics.pose().popPose();
    }

    /**
     * Pushes a pose for a w×h panel at the bottom centre of the screen and draws its background. Shrinks it
     * if it would be wider than the screen or cover more than the bottom ~45% (and the crosshair).
     */
    private static void pushPanel(GuiGraphics g, int w, int h) {
        float scale = (float) Math.min(TrainCabConfig.scale,
            Math.min(g.guiWidth() / (double) w, g.guiHeight() * 0.45 / h));
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(
            (g.guiWidth() - w * scale) / 2 + TrainCabConfig.offsetX,
            g.guiHeight() - TrainCabConfig.offsetY - h * scale,
            0);
        pose.scale(scale, scale, 1);
        drawPanel(g, w, h);
    }

    /** Panel background with its border and a screw hole in each corner. */
    private static void drawPanel(GuiGraphics g, int w, int h) {
        drawRoundedRect(g, 0, 0, w, h, COLOR_BORDER);
        drawRoundedRect(g, 1, 1, w - 1, h - 1, COLOR_PANEL_BG);
        for (int x : new int[] {2, w - 6}) {
            for (int y : new int[] {2, h - 6}) {
                // A 4×4 round hole, lit along its lower right edge like a countersunk rim.
                drawRoundedRect(g, x, y, x + 4, y + 4, COLOR_SCREW_RIM);
                drawRoundedRect(g, x, y, x + 3, y + 3, COLOR_SCREW);
            }
        }
    }

    private static void drawRoundedRect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0 + 1, y0, x1 - 1, y1, color);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    private static void drawGauge(GuiGraphics g, Font font, int x, ResourceLocation background, float angle,
                                  double value, String unitLabel) {
        int half = GAUGE_SIZE / 2;
        int cx = x + half;
        int cy = PAD + half;

        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);

        drawGaugeTexture(g, background, 0f, half);
        drawGaugeTexture(g, GAUGE_NEEDLE, angle, half);

        String speedText = String.format("%.0f", value);
        pose.pushPose();
        pose.translate(0, 8, 0);
        pose.scale(2, 2, 1);
        g.drawString(font, speedText, -font.width(speedText) / 2, 0, 0xFFFFFFFF, true);
        pose.popPose();
        g.drawString(font, unitLabel, -font.width(unitLabel) / 2, 26, COLOR_TEXT_DIM, false);

        pose.popPose();
    }

    private static void drawGaugeTexture(GuiGraphics g, ResourceLocation texture, float angle, int half) {
        // The 400px textures are drawn at ~72px; linear filtering keeps the ticks and digits smooth.
        Minecraft.getInstance().getTextureManager().getTexture(texture).setFilter(true, false);
        g.pose().pushPose();
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        g.blit(texture, -half, -half, 2 * half, 2 * half, 0, 0, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_SIZE);
        g.pose().popPose();
    }

    /** 0..1 of full scale → needle angle. */
    private static float toAngle(double fraction) {
        return Mth.lerp(Mth.clamp((float) fraction, 0f, 1f), NEEDLE_MIN_ANGLE, NEEDLE_MAX_ANGLE);
    }

    /** Reverser cell, the current notch in large, and a strip of all notches (EB on the left, P5 on the right). */
    private static void drawNotchRow(GuiGraphics g, Font font) {
        int notch = TrainDrivingState.getNotch();

        g.fill(RIGHT_X, NOTCH_Y, RIGHT_X + NOTCH_H, NOTCH_Y + NOTCH_H, COLOR_INACTIVE);
        int reverser = TrainDrivingState.getReverser();
        g.drawCenteredString(font, reverser > 0 ? "前" : reverser < 0 ? "後" : "中",
            RIGHT_X + NOTCH_H / 2, NOTCH_Y + (NOTCH_H - 8) / 2, reverser == 0 ? COLOR_ORANGE : COLOR_TEXT);

        int labelX = RIGHT_X + NOTCH_H + 3;
        g.fill(labelX, NOTCH_Y, RIGHT_X + RIGHT_W, NOTCH_Y + NOTCH_H, notchColor(notch));
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate((labelX + RIGHT_X + RIGHT_W) / 2f, NOTCH_Y + NOTCH_H / 2f, 0);
        pose.scale(2, 2, 1);
        String label = notchLabel(notch);
        g.drawString(font, label, -font.width(label) / 2, -4, 0xFF101010, false);
        pose.popPose();

        int stripX = RIGHT_X + (RIGHT_W - SEGMENT_W * NOTCH_COUNT) / 2;
        for (int i = 0; i < NOTCH_COUNT; i++) {
            int n = Mascon.EB + i;
            boolean lit = notch < 0 ? n >= notch && n < 0 : notch > 0 ? n > 0 && n <= notch : n == 0;
            int x = stripX + i * SEGMENT_W;
            g.fill(x, SEGMENT_Y, x + SEGMENT_W - 1, SEGMENT_Y + SEGMENT_H, lit ? notchColor(n) : COLOR_INACTIVE);
        }
    }

    private static String notchLabel(int notch) {
        if (notch == Mascon.EB) {
            return "非常";
        }
        return notch < 0 ? "B" + -notch : notch > 0 ? "P" + notch : "N";
    }

    private static int notchColor(int notch) {
        return notch == Mascon.EB ? COLOR_RED : notch < 0 ? COLOR_ORANGE : notch > 0 ? COLOR_GREEN : COLOR_TEXT;
    }

    private static void drawStatusLine(GuiGraphics g, Font font, Train train) {
        String cars = train.carriages.size() + "両";
        g.drawString(font, cars, RIGHT_X, STATUS_Y, COLOR_TEXT, false);

        String status;
        int color;
        GlobalStation station = train.getCurrentStation();
        CrewStatus crew = CrewStatus.current();
        if (train.derailed) {
            status = "脱線";
            color = COLOR_RED;
        } else if (crew != null && crew.emergency()) {
            status = "車掌非常";
            color = COLOR_RED;
        } else if (station == null && crew != null && crew.doorsOpen()) {
            status = "戸開";
            color = COLOR_ORANGE;
        } else if (train.navigation.waitingForSignal != null) {
            status = "信号待ち";
            color = COLOR_ORANGE;
        } else if (station != null) {
            // With a conductor on board the driver waits for 戸閉 (doors opened and closed again) before departing.
            status = crew == null ? station.name
                : (crew.doorsOpen() ? "戸開 " : crew.departureReady() ? "戸閉 " : "到着 ") + station.name;
            color = crew == null || !crew.doorsOpen() && crew.departureReady() ? COLOR_GREEN : COLOR_ORANGE;
        } else {
            status = train.name == null ? "" : train.name.getString();
            color = COLOR_TEXT_DIM;
        }
        status = font.plainSubstrByWidth(status, RIGHT_W - font.width(cars) - 6);
        g.drawString(font, status, RIGHT_X + RIGHT_W - font.width(status), STATUS_Y, color, false);
    }

    /** "次 <station>" with the distance to its stop point: green inside the stop tolerance, red once overrun. */
    private static void drawNextStation(GuiGraphics g, Font font, int x, int w, int y) {
        StationInfo info = StationInfo.latest;
        if (!TrainCabConfig.stationAssist || info == null || StationInfo.age > 10 || info.name().isEmpty()) {
            return;
        }
        float d = info.distance();
        String distance = String.format(Math.abs(d) >= 100 ? "%.0fm" : "%.1fm", d);
        int color = Math.abs(d) <= TrainCabServerConfig.stopTolerance ? COLOR_GREEN : d < 0 ? COLOR_RED : COLOR_TEXT;
        g.drawString(font, distance, x + w - font.width(distance), y, color, false);
        String name = font.plainSubstrByWidth("次 " + info.name(), w - font.width(distance) - 6);
        g.drawString(font, name, x, y, COLOR_TEXT_DIM, false);
    }

    /** Role / speed, door lamp / next station, and the conductor's keys. Only called while on duty. */
    private static void renderConductor(GuiGraphics g, Font font) {
        CrewStatus crew = CrewStatus.current();

        pushPanel(g, CONDUCTOR_W, PAD + 14 + 4 + 14 + 5 + 9 + PAD);
        int right = CONDUCTOR_W - PAD;

        int y = PAD;
        drawLamp(g, font, y, crew.emergency() ? "非常" : "車掌", crew.emergency() ? COLOR_RED : COLOR_INACTIVE,
            COLOR_TEXT);
        float bpt = Math.abs(crew.speed());
        boolean kmh = TrainCabConfig.unit == TrainCabConfig.SpeedUnit.KMH;
        String speed = String.format("%.0f %s", kmh ? bpt * 20 * 3.6 : bpt * 20, kmh ? "km/h" : "blk/s");
        g.drawString(font, speed, right - font.width(speed), y + 3, COLOR_TEXT, false);

        // The lamp follows the real doors, which only open at a station when the conductor opens them.
        y += 18;
        boolean closed = !crew.doorsOpen();
        drawLamp(g, font, y, closed ? "戸閉" : "戸開", closed ? COLOR_GREEN : COLOR_ORANGE, 0xFF101010);
        drawNextStation(g, font, PAD + LAMP_W + 6, right - PAD - LAMP_W - 6, y + 3);

        y += 19;
        String keys = keyName(ConductorClient.DOORS) + " 戸閉/戸開  " + keyName(TrainDrivingState.EMERGENCY) + " 非常/解除  "
            + keyName(ConductorClient.BELL) + " 合図  " + keyName(ConductorClient.ANNOUNCE) + " 放送";
        g.drawString(font, font.plainSubstrByWidth(keys, CONDUCTOR_W - 2 * PAD), PAD, y, COLOR_TEXT_DIM, false);

        g.pose().popPose();
    }

    /**
     * "停止 0.3m手前 良": how far from the stop point the driver stopped, and a grade (優 / 良 / 可).
     * {@code error} is positive when short of the stop point, negative when past it.
     */
    private static void drawStopResult(GuiGraphics g, Font font, float error) {
        float off = Math.abs(error);
        String where = off < 0.05f ? "ぴったり" : String.format("%.1fm%s", off, error > 0 ? "手前" : "行過");
        String grade = off <= 0.25f ? "優" : off <= 0.75f ? "良" : "可";
        int color = off <= 0.25f ? COLOR_GREEN : off <= 0.75f ? COLOR_TEXT : COLOR_ORANGE;
        g.drawString(font, "停止 " + where, RIGHT_X, NEXT_STATION_Y, COLOR_TEXT_DIM, false);
        g.drawString(font, grade, RIGHT_X + RIGHT_W - font.width(grade), NEXT_STATION_Y, color, false);
    }

    /** Passengers on a driven train: "次は ○○" (sent every half second while a station is ahead). */
    private static void renderPassenger(GuiGraphics g, Font font) {
        StationInfo info = StationInfo.latest;
        if (info == null || StationInfo.age > 25 || info.name().isEmpty()) {
            return;
        }
        String text = "次は " + info.name();
        pushPanel(g, font.width(text) + 2 * PAD, 9 + 2 * PAD);
        g.drawString(font, text, PAD, PAD, COLOR_TEXT, false);
        g.pose().popPose();
    }

    /** The latest 車内放送, shown at the top centre for a few seconds to everyone who received it. */
    private static void drawAnnouncement(GuiGraphics g, Font font) {
        Announcement announcement = Announcement.latest;
        if (announcement == null || Announcement.age > ANNOUNCEMENT_TICKS) {
            return;
        }
        String text = "♪ " + announcement.text();
        int w = font.width(text) + 2 * PAD;
        int h = 9 + 2 * PAD;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate((g.guiWidth() - w) / 2f, 8, 0);
        drawPanel(g, w, h);
        g.drawString(font, text, PAD, PAD, COLOR_TEXT, false);
        pose.popPose();
    }

    private static void drawLamp(GuiGraphics g, Font font, int y, String label, int bg, int textColor) {
        g.fill(PAD, y, PAD + LAMP_W, y + 14, bg);
        g.drawString(font, label, PAD + (LAMP_W - font.width(label)) / 2, y + 3, textColor, false);
    }

    private static String keyName(KeyMapping key) {
        return key.getTranslatedKeyMessage().getString();
    }
}
