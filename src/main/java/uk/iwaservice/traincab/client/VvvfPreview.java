package uk.iwaservice.traincab.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import uk.iwaservice.traincab.TrainCabConfig;

import java.util.Locale;

/**
 * The editor's 試聴: a station run of the sound being edited, heard as if from a train going by the listener: power
 * from a standstill to top speed, a short coast, then braking to a stop. Edits made while it plays are heard at once
 * (update).
 */
final class VvvfPreview extends VvvfSounds.SynthSound {

    private static final double POWER_SECONDS = 14;
    private static final double COAST_SECONDS = 1.5;
    private static final double BRAKE_SECONDS = 10;
    /** The reflections ringing out after the stop. */
    private static final double TAIL_SECONDS = 1.5;

    // Client thread only.
    private static VvvfPreview playing;

    private final VvvfSynth synth = new VvvfSynth();
    /** Sound thread only. */
    private long rendered;

    private VvvfPreview(VvvfSynth.Tables tables) {
        synth.tables = tables;
        relative = true;
        attenuation = SoundInstance.Attenuation.NONE;
    }

    static void play(VvvfSynth.Tables tables) {
        stop();
        playing = new VvvfPreview(tables);
        Minecraft.getInstance().getSoundManager().play(playing);
    }

    static void stop() {
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
    }

    static boolean isPlaying() {
        return playing != null && Minecraft.getInstance().getSoundManager().isActive(playing);
    }

    /** The sound was edited: hear the change from here on. */
    static void update(VvvfSynth.Tables tables) {
        if (playing != null) {
            playing.synth.tables = tables;
        }
    }

    /** Where the run is, e.g. "力行 23.4 Hz (≈32 km/h) 15P", or empty when nothing plays. */
    static String describe() {
        if (!isPlaying()) {
            return "";
        }
        VvvfSynth synth = playing.synth;
        int state = synth.state;
        double f = synth.shownF;
        String mode = !synth.shownGate ? (state == VvvfSynth.BRAKE ? "回生なし" : "")
            : synth.shownPulses == 0 ? "非同期" : synth.shownPulses + "P";
        return String.format(Locale.ROOT, "試聴中: %s %.1f Hz (≈%d km/h) %s",
            state == VvvfSynth.POWER ? "力行" : state == VvvfSynth.BRAKE ? "回生" : "惰行", f,
            VvvfEditorScreen.kmh(f), mode).strip();
    }

    @Override
    boolean fill(float[] out) {
        double t = rendered / (double) VvvfSynth.FS;
        double brakeFrom = POWER_SECONDS + COAST_SECONDS;
        double stop = brakeFrom + BRAKE_SECONDS;
        if (t >= stop + TAIL_SECONDS) {
            return false;
        }
        if (t < POWER_SECONDS) {
            synth.state = VvvfSynth.POWER;
            synth.target = VvvfSynth.F_MAX * t / POWER_SECONDS;
        } else if (t < brakeFrom) {
            synth.state = VvvfSynth.COAST;
            synth.target = VvvfSynth.F_MAX;
        } else if (t < stop) {
            synth.state = VvvfSynth.BRAKE;
            synth.target = VvvfSynth.F_MAX * (1 - (t - brakeFrom) / BRAKE_SECONDS);
        } else {
            synth.state = VvvfSynth.COAST;
            synth.target = 0;
        }
        synth.volume = TrainCabConfig.vvvfVolume;
        synth.render(out);
        rendered += out.length;
        return true;
    }
}
