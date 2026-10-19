package uk.iwaservice.traincab.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.lwjgl.glfw.GLFW;
import uk.iwaservice.traincab.TrainCabConfig;
import uk.iwaservice.traincab.TrainCabItems;
import uk.iwaservice.traincab.mascon.CrewStatus;
import uk.iwaservice.traincab.mascon.Mascon;
import uk.iwaservice.traincab.mascon.StationInfo;
import uk.iwaservice.traincab.mascon.StopResult;
import uk.iwaservice.traincab.mascon.MasconPacket;
import uk.iwaservice.traincab.mascon.TrainCabNetwork;

/**
 * Tracks, once per client tick, whether the local player is currently at a train's controls, and
 * runs the driver's side of the mascon: W / S step the notch towards power / brake (holding
 * repeats), the emergency key jumps straight to EB, the reverser keys move the reverser (前/中/後)
 * one step while stopped, and the notch is sent to the server.
 * Mirrors the check Create's own built-in HUD (com.simibubi.create.content.trains.TrainHUD) uses:
 * ControlsHandler only holds a reference while the local player is actively driving, not merely seated.
 */
public final class TrainDrivingState {

    public static final KeyMapping REVERSER_FORWARD =
        new KeyMapping("key.traincab.reverser_forward", GLFW.GLFW_KEY_R, "key.categories.traincab");
    public static final KeyMapping REVERSER_BACKWARD =
        new KeyMapping("key.traincab.reverser_backward", GLFW.GLFW_KEY_V, "key.categories.traincab");
    public static final KeyMapping EMERGENCY =
        new KeyMapping("key.traincab.emergency", GLFW.GLFW_KEY_X, "key.categories.traincab");

    private static final int RESEND_TICKS = 10;
    // Holding W / S: one step on press, then after REPEAT_DELAY ticks one more step every REPEAT_INTERVAL ticks.
    private static final int REPEAT_DELAY = 6;
    private static final int REPEAT_INTERVAL = 2;

    private static boolean driving = false;
    /** Driving from a 高度運転台: the HUD shows the pressure gauge. */
    private static boolean advancedCab = false;
    private static Train activeTrain = null;

    private static float prevSpeedBpt = 0f;
    private static float speedBpt = 0f;
    /** Brake cylinder pressure (kPa), for the HUD's pressure gauge only; braking itself follows the notch. */
    private static float prevBc = 0f;
    private static float bc = 0f;

    private static int notch = 0;
    /** +1 前, 0 中 (neutral), -1 後. */
    private static int reverser = 1;
    // Ticks W / S has been held. -1 = ignore until released, so a key already down when taking the
    // controls doesn't immediately move the notch.
    private static int upHeld = -1;
    private static int downHeld = -1;
    private static int resendCooldown = 0;

    private TrainDrivingState() {
    }

    public static void tick() {
        prevSpeedBpt = speedBpt;
        prevBc = bc;
        StationInfo.age++;
        StopResult.age++;
        // Net lever movement this tick: R moves one step towards 前, V one step towards 後.
        int reverserStep = 0;
        while (REVERSER_FORWARD.consumeClick()) {
            reverserStep++;
        }
        while (REVERSER_BACKWARD.consumeClick()) {
            reverserStep--;
        }
        boolean emergencyPressed = false;
        while (EMERGENCY.consumeClick()) {
            emergencyPressed = true;
        }

        if (!(ControlsHandler.getContraption() instanceof CarriageContraptionEntity cce) || cce.getCarriage() == null) {
            driving = false;
            activeTrain = null;
            speedBpt = 0f;
            bc = 0f;
            notch = 0;
            reverser = 1;
            upHeld = downHeld = -1;
            resendCooldown = 0;
            ConductorClient.tick(false, emergencyPressed);
            return;
        }
        ConductorClient.tick(true, false);

        Train train = cce.getCarriage().train;
        driving = true;
        activeTrain = train;
        speedBpt = (float) train.speed;
        StructureBlockInfo controls = cce.getContraption().getBlocks().get(ControlsHandler.getControlsPos());
        advancedCab = controls != null && controls.state().is(TrainCabItems.ADVANCED_CAB.get());

        // Create swallows W/S while driving (KeyMapping.isDown() is forced false), so read the raw keys.
        Minecraft mc = Minecraft.getInstance();
        boolean up = isPhysicallyDown(mc, mc.options.keyUp);
        boolean down = isPhysicallyDown(mc, mc.options.keyDown);
        int oldNotch = notch;
        int oldReverser = reverser;
        upHeld = nextHeld(upHeld, up);
        downHeld = nextHeld(downHeld, down);
        if (shouldStep(upHeld)) {
            notch = Math.min(notch + 1, Mascon.MAX_POWER);
        }
        if (shouldStep(downHeld)) {
            notch = Math.max(notch - 1, Mascon.EB);
        }
        if (emergencyPressed) {
            notch = Mascon.EB;
        }
        if (reverserStep != 0 && Math.abs(speedBpt) < 1e-4f) {
            reverser = Mth.clamp(reverser + reverserStep, -1, 1);
        }

        // Pressure chases the notch with a lag like a real 電磁直通 brake: B1–B7 50–350 kPa, EB / 車掌非常 440 kPa.
        CrewStatus crew = CrewStatus.current();
        int targetBc = notch == Mascon.EB || crew != null && crew.emergency() ? 440 : Math.max(-notch, 0) * 50;
        bc += Mth.clamp(targetBc - bc, -20f, 15f);

        if (notch != oldNotch || reverser != oldReverser || --resendCooldown <= 0) {
            TrainCabNetwork.CHANNEL.sendToServer(new MasconPacket(cce.getId(), notch, reverser * controlsFacingSign(cce),
                TrainCabConfig.stationAssist));
            resendCooldown = RESEND_TICKS;
        }
    }

    private static int nextHeld(int held, boolean down) {
        return !down ? 0 : held < 0 ? -1 : held + 1;
    }

    private static boolean shouldStep(int held) {
        return held == 1 || held > REPEAT_DELAY && (held - REPEAT_DELAY) % REPEAT_INTERVAL == 0;
    }

    private static boolean isPhysicallyDown(Minecraft mc, KeyMapping key) {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getKey().getValue());
    }

    /** Same "inverted" test as CarriageContraptionEntity.control: controls facing backwards drive with negative speed. */
    private static int controlsFacingSign(CarriageContraptionEntity cce) {
        StructureBlockInfo info = cce.getContraption().getBlocks().get(ControlsHandler.getControlsPos());
        if (info == null || !info.state().hasProperty(ControlsBlock.FACING)) {
            return 1;
        }
        BlockState state = info.state();
        return state.getValue(ControlsBlock.FACING) == cce.getInitialOrientation().getCounterClockWise() ? 1 : -1;
    }

    public static boolean isDriving() {
        return driving;
    }

    public static boolean hasAdvancedCab() {
        return advancedCab;
    }

    public static Train getActiveTrain() {
        return activeTrain;
    }

    public static int getNotch() {
        return notch;
    }

    /** +1 forward, 0 neutral, -1 backward. */
    public static int getReverser() {
        return reverser;
    }

    public static float getInterpolatedBc(float partialTick) {
        return Mth.lerp(partialTick, prevBc, bc);
    }

    /** Blocks/tick, linearly interpolated between the previous and current tick for smooth needle motion. */
    public static float getInterpolatedSpeedBlocksPerTick(float partialTick) {
        return Mth.lerp(partialTick, prevSpeedBpt, speedBpt);
    }
}
