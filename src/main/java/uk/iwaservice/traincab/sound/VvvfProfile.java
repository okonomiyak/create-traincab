package uk.iwaservice.traincab.sound;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A VVVF inverter's sound as players edit it (VvvfEditorScreen) and keep it on the inverter block: the switching
 * pattern VvvfSynth plays. Fundamentals ({@code from}, Hz) map to speed through VvvfSynth.F_MAX, the fundamental at
 * Create's top speed.
 *
 * <p>Its text form, kept in the block's NBT and copied / pasted to share it, is
 * {@code VVVF1;a=<modes>;b=<modes>;r=<Hz>;c=<steps>;s=<0|1>;j=<share>;l=<share>;w=<share>;n=<name>}: 力行 and 回生
 * modes as {@code from:pulses:index}, carrier steps as {@code from:hz}, comma separated. The name comes last and runs
 * to the end, so it may hold any character. Missing fields take the default sound's, unknown ones are skipped.
 *
 * @param accel      力行: from each fundamental on, the pulses per period (0 = asynchronous, 1 = one pulse) and the
 *                   modulation index there (in between it slides to the next mode's)
 * @param brake      回生, the same way
 * @param regenMinF  回生 fades out below this fundamental
 * @param carrier    the asynchronous carrier from each fundamental on
 * @param stepped    the carrier jumps from step to step (a scale) instead of sliding between them
 * @param jitter     random modulation: the carrier wanders by up to this share of itself
 * @param asyncLevel how loud the asynchronous sound is at a standstill (full by the first synchronous mode)
 * @param asyncSwell how much it swells with the rotor's poles (2f) at a standstill
 */
public record VvvfProfile(String name, List<Mode> accel, List<Mode> brake, double regenMinF, List<Step> carrier,
                          boolean stepped, double jitter, double asyncLevel, double asyncSwell) {

    /** From fundamental {@code from} (Hz) on, {@code pulses} per period, at modulation index {@code index}. */
    public record Mode(double from, int pulses, double index) {
    }

    /** From fundamental {@code from} (Hz) on, the asynchronous carrier at {@code hz}. */
    public record Step(double from, double hz) {
    }

    public static final int MAX_MODES = 12;
    public static final int MAX_STEPS = 16;
    public static final int MAX_NAME = 32;
    /** Longest text form read (also what the packets carry). */
    public static final int MAX_CODE = 1024;
    public static final double MAX_F = 200;
    public static final int MAX_PULSES = 99;
    public static final double MAX_INDEX = 1.5;
    public static final double MIN_HZ = 20;
    public static final double MAX_HZ = 5000;
    public static final double MAX_JITTER = 0.5;
    private static final String PREFIX = "VVVF1;";

    /** GTO: asynchronous 365 Hz, then 27P→1P; 回生 from 15P. The default sound. */
    public static final VvvfProfile GTO = new VvvfProfile("GTO",
        List.of(new Mode(0, 0, 0.08), new Mode(9, 27, 0.5), new Mode(16.4, 15, 0.6), new Mode(29.6, 9, 0.75),
            new Mode(41.3, 5, 0.9), new Mode(49, 3, 1), new Mode(52.4, 1, 1)),
        List.of(new Mode(0, 15, 0.6), new Mode(33, 9, 0.7), new Mode(43, 5, 0.85), new Mode(59, 3, 0.95),
            new Mode(67, 1, 1)),
        11, List.of(new Step(0, 365)), false, 0, 0.35, 0.6);

    private static final List<Mode> SCALE_MODES = List.of(new Mode(0, 0, 0.1), new Mode(6, 7, 0.55),
        new Mode(20, 5, 0.7), new Mode(32, 3, 0.85), new Mode(44, 1, 1));

    /**
     * GTO・音階: the asynchronous carrier climbs the F major scale ファ ソ ラ シ♭ ド レ ミ ファ, then ソ, over the first
     * ~8 km/h, and walks back down when stopping.
     */
    public static final VvvfProfile SCALE = new VvvfProfile("GTO・音階", SCALE_MODES, SCALE_MODES, 1.2,
        List.of(new Step(0, 349.2), new Step(0.6, 392), new Step(1.2, 440), new Step(1.8, 466.2),
            new Step(2.4, 523.3), new Step(3, 587.3), new Step(3.6, 659.3), new Step(4.2, 698.5),
            new Step(4.8, 784)),
        true, 0, 1, 0);

    /** IGBT: an asynchronous carrier rising 750→1050 Hz (the ヒューン), then wide 3P and 1P. */
    public static final VvvfProfile IGBT = new VvvfProfile("IGBT",
        List.of(new Mode(0, 0, 0.05), new Mode(40, 3, 0.85), new Mode(52, 1, 1)),
        List.of(new Mode(0, 0, 0.05), new Mode(44, 3, 0.85), new Mode(56, 1, 1)),
        4, List.of(new Step(0, 750), new Step(20, 1050)), false, 0, 0.7, 0.3);

    /** SiC: a high asynchronous carrier with random modulation (a hiss rather than a tone), 1P at speed. */
    public static final VvvfProfile SIC = new VvvfProfile("SiC",
        List.of(new Mode(0, 0, 0.05), new Mode(56, 1, 1)),
        List.of(new Mode(0, 0, 0.05), new Mode(60, 1, 1)),
        3, List.of(new Step(0, 1200)), false, 0.25, 0.6, 0.2);

    public static final List<VvvfProfile> PRESETS = List.of(GTO, SCALE, IGBT, SIC);
    public static final VvvfProfile DEFAULT = GTO;

    /**
     * Within the limits: values clamped and rounded to 3 decimals, rows sorted by fundamental with the first from 0,
     * at most MAX_MODES / MAX_STEPS of them (an empty table takes the default sound's), the name cleaned up.
     */
    public VvvfProfile normalized() {
        return new VvvfProfile(cleanName(name), modes(accel, DEFAULT.accel), modes(brake, DEFAULT.brake),
            clamp(regenMinF, 0, MAX_F), steps(carrier), stepped, clamp(jitter, 0, MAX_JITTER),
            clamp(asyncLevel, 0, 1), clamp(asyncSwell, 0, 1));
    }

    /** The text form (see the class comment), of the normalized profile. */
    public String code() {
        VvvfProfile p = normalized();
        StringBuilder b = new StringBuilder(PREFIX);
        b.append("a=");
        modesText(b, p.accel);
        b.append(";b=");
        modesText(b, p.brake);
        b.append(";r=").append(num(p.regenMinF)).append(";c=");
        for (int i = 0; i < p.carrier.size(); i++) {
            Step s = p.carrier.get(i);
            b.append(i > 0 ? "," : "").append(num(s.from)).append(':').append(num(s.hz));
        }
        b.append(";s=").append(p.stepped ? 1 : 0).append(";j=").append(num(p.jitter)).append(";l=")
            .append(num(p.asyncLevel)).append(";w=").append(num(p.asyncSwell)).append(";n=").append(p.name);
        return b.toString();
    }

    /** The profile in {@code code} (surrounding blanks ignored), normalized, or null when it isn't one. */
    public static VvvfProfile parse(String code) {
        if (code == null) {
            return null;
        }
        code = code.strip();
        if (!code.startsWith(PREFIX) || code.length() > MAX_CODE) {
            return null;
        }
        List<Mode> accel = DEFAULT.accel;
        List<Mode> brake = DEFAULT.brake;
        List<Step> carrier = DEFAULT.carrier;
        double regenMinF = DEFAULT.regenMinF;
        boolean stepped = DEFAULT.stepped;
        double jitter = DEFAULT.jitter;
        double asyncLevel = DEFAULT.asyncLevel;
        double asyncSwell = DEFAULT.asyncSwell;
        String name = "";
        String rest = code.substring(PREFIX.length());
        try {
            while (!rest.isEmpty()) {
                if (rest.startsWith("n=")) {
                    name = rest.substring(2);
                    break;
                }
                int end = rest.indexOf(';');
                String field = end < 0 ? rest : rest.substring(0, end);
                rest = end < 0 ? "" : rest.substring(end + 1);
                if (field.isBlank()) {
                    continue;
                }
                int eq = field.indexOf('=');
                if (eq < 0) {
                    return null;
                }
                String value = field.substring(eq + 1).strip();
                switch (field.substring(0, eq).strip()) {
                    case "a" -> accel = parseModes(value);
                    case "b" -> brake = parseModes(value);
                    case "r" -> regenMinF = Double.parseDouble(value);
                    case "c" -> carrier = parseSteps(value);
                    case "s" -> stepped = Integer.parseInt(value) != 0;
                    case "j" -> jitter = Double.parseDouble(value);
                    case "l" -> asyncLevel = Double.parseDouble(value);
                    case "w" -> asyncSwell = Double.parseDouble(value);
                    default -> {
                        // From a newer version: skipped.
                    }
                }
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return new VvvfProfile(name, accel, brake, regenMinF, carrier, stepped, jitter, asyncLevel, asyncSwell)
            .normalized();
    }

    /** The profile kept as {@code code}, or the default sound when there is none or it can't be read. */
    public static VvvfProfile orDefault(String code) {
        VvvfProfile profile = code == null || code.isEmpty() ? null : parse(code);
        return profile == null ? DEFAULT : profile;
    }

    /** A number as the text form writes it: at most 3 decimals, no trailing zeros. */
    public static String num(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static void modesText(StringBuilder b, List<Mode> modes) {
        for (int i = 0; i < modes.size(); i++) {
            Mode m = modes.get(i);
            b.append(i > 0 ? "," : "").append(num(m.from)).append(':').append(m.pulses).append(':').append(num(m.index));
        }
    }

    private static List<Mode> parseModes(String text) {
        List<Mode> modes = new ArrayList<>();
        for (String part : text.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            String[] v = part.split(":", -1);
            if (v.length != 3) {
                throw new NumberFormatException(part);
            }
            modes.add(new Mode(Double.parseDouble(v[0].strip()), Integer.parseInt(v[1].strip()),
                Double.parseDouble(v[2].strip())));
        }
        return modes;
    }

    private static List<Step> parseSteps(String text) {
        List<Step> steps = new ArrayList<>();
        for (String part : text.split(",")) {
            if (part.isBlank()) {
                continue;
            }
            String[] v = part.split(":", -1);
            if (v.length != 2) {
                throw new NumberFormatException(part);
            }
            steps.add(new Step(Double.parseDouble(v[0].strip()), Double.parseDouble(v[1].strip())));
        }
        return steps;
    }

    private static List<Mode> modes(List<Mode> modes, List<Mode> fallback) {
        List<Mode> out = new ArrayList<>();
        for (Mode m : modes == null ? List.<Mode>of() : modes) {
            if (out.size() == MAX_MODES) {
                break;
            }
            out.add(new Mode(clamp(m.from, 0, MAX_F), Math.max(0, Math.min(m.pulses, MAX_PULSES)),
                clamp(m.index, 0, MAX_INDEX)));
        }
        if (out.isEmpty()) {
            return fallback;
        }
        out.sort(Comparator.comparingDouble(Mode::from));
        out.set(0, new Mode(0, out.get(0).pulses, out.get(0).index));
        return List.copyOf(out);
    }

    private static List<Step> steps(List<Step> steps) {
        List<Step> out = new ArrayList<>();
        for (Step s : steps == null ? List.<Step>of() : steps) {
            if (out.size() == MAX_STEPS) {
                break;
            }
            out.add(new Step(clamp(s.from, 0, MAX_F), clamp(s.hz, MIN_HZ, MAX_HZ)));
        }
        if (out.isEmpty()) {
            return DEFAULT.carrier;
        }
        out.sort(Comparator.comparingDouble(Step::from));
        out.set(0, new Step(0, out.get(0).hz));
        return List.copyOf(out);
    }

    /** In [min, max] (NaN → min), rounded to 3 decimals like the text form. */
    private static double clamp(double v, double min, double max) {
        double clamped = Double.isNaN(v) ? min : Math.min(Math.max(v, min), max);
        return Math.round(clamped * 1000) / 1000.0;
    }

    /** No control or formatting (§) characters, at most MAX_NAME of them. */
    private static String cleanName(String name) {
        StringBuilder b = new StringBuilder();
        (name == null ? "" : name).strip().codePoints().filter(c -> !Character.isISOControl(c) && c != '§')
            .limit(MAX_NAME).forEach(b::appendCodePoint);
        return b.toString().strip();
    }
}
