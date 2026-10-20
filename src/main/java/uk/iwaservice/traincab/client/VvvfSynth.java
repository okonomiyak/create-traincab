package uk.iwaservice.traincab.client;

import java.util.SplittableRandom;

/**
 * Real-time VVVF traction sound: PWM from a sine reference and a triangle carrier (asynchronous at low speed, then
 * synchronous pulse modes), turned into motor current, plus torque ripple, gear whine, rolling and fan noise,
 * through the motor body's resonances. The switching pattern is a {@link Profile}: GTO (ported from a
 * reference script, switching frequencies read off spectrograms) and others by ear; the modulation index, gains and
 * resonances are by ear too.
 *
 * <p>The game thread sets {@link #target} and {@link #state}; the sound thread calls {@link #render}.
 */
final class VvvfSynth {

    static final int FS = 48000;
    static final int COAST = 0;
    static final int POWER = 1;
    static final int BRAKE = 2;
    /** Fundamental at top speed. */
    static final double F_MAX = 74;

    private static final int OS = 4;

    /**
     * One inverter's switching pattern. In 力行 (accel) / 回生 (brake) the pulse count switches up at the F
     * fundamentals (Hz) to the N counts, 0 = asynchronous; the modulation index follows M over MF. 回生 fades out
     * below regenMinF (the air brake does the rest). The asynchronous carrier follows carrierHz over carrierF,
     * in steps or interpolated, wandering by ±jitter of itself (random modulation). At standstill it is
     * asyncLevel loud (full by the first synchronous mode) and swells by asyncSwell with the rotor's poles (2f).
     */
    record Profile(double[] accelF, int[] accelN, double[] accelMF, double[] accelM,
                   double[] brakeF, int[] brakeN, double[] brakeMF, double[] brakeM, double regenMinF,
                   double[] carrierF, double[] carrierHz, boolean stepped, double jitter,
                   double asyncLevel, double asyncSwell) {
    }

    /** Indexed by the inverter block's {@code sound} (InverterBlock.SOUNDS). */
    static final Profile[] PROFILES = {
        // GTO: asynchronous 365 Hz, then 27P→1P; 回生 from 15P.
        new Profile(new double[] {0, 9, 16.4, 29.6, 41.3, 49.0, 52.4}, new int[] {0, 27, 15, 9, 5, 3, 1},
            new double[] {0, 9, 16.4, 29.6, 41.3, 49.0}, new double[] {0.08, 0.5, 0.6, 0.75, 0.9, 1.0},
            new double[] {0, 33, 43, 59, 67}, new int[] {15, 9, 5, 3, 1},
            new double[] {12.3, 33, 43, 59, 67}, new double[] {0.6, 0.7, 0.85, 0.95, 1.0}, 11,
            new double[] {0}, new double[] {365}, false, 0, 0.35, 0.6),
        // GTO・音階: the asynchronous carrier climbs the F major scale
        // ファ ソ ラ シ♭ ド レ ミ ファ, then ソ, and walks back down when stopping.
        new Profile(new double[] {0, 12, 20, 32, 44}, new int[] {0, 7, 5, 3, 1},
            new double[] {0, 12, 20, 32, 44}, new double[] {0.1, 0.55, 0.7, 0.85, 1.0},
            new double[] {0, 12, 20, 32, 44}, new int[] {0, 7, 5, 3, 1},
            new double[] {0, 12, 20, 32, 44}, new double[] {0.1, 0.55, 0.7, 0.85, 1.0}, 2.5,
            new double[] {0, 1.3, 2.6, 3.9, 5.2, 6.5, 7.8, 9.1, 10.4},
            new double[] {349.2, 392.0, 440.0, 466.2, 523.3, 587.3, 659.3, 698.5, 784.0}, true, 0, 1, 0),
        // IGBT: an asynchronous carrier rising 750→1050 Hz (the ヒューン), then wide 3P and 1P.
        new Profile(new double[] {0, 40, 52}, new int[] {0, 3, 1},
            new double[] {0, 40, 52}, new double[] {0.05, 0.85, 1.0},
            new double[] {0, 44, 56}, new int[] {0, 3, 1},
            new double[] {0, 44, 56}, new double[] {0.05, 0.85, 1.0}, 4,
            new double[] {0, 20}, new double[] {750, 1050}, false, 0, 0.7, 0.3),
        // SiC: a high asynchronous carrier with random modulation (a hiss rather than a tone), 1P at speed.
        new Profile(new double[] {0, 56}, new int[] {0, 1},
            new double[] {0, 56}, new double[] {0.05, 1.0},
            new double[] {0, 60}, new int[] {0, 1},
            new double[] {0, 60}, new double[] {0.05, 1.0}, 3,
            new double[] {0}, new double[] {1200}, false, 0.25, 0.6, 0.2),
    };
    /** How far past a switching point the fundamental has to go before switching back. */
    private static final double HYSTERESIS = 0.5;
    /** (centre Hz, Q, gain) of the motor body's resonances. */
    private static final double[][] RESONANCES = {{420, 6, 1.6}, {780, 7, 1.3}, {1350, 8, 1.0}, {2100, 8, 0.7}, {3400, 8, 0.4}};
    // Measured on the reference script's station run: the motor current to std 1 while running, the filtered noise to
    // std 1, and the whole run to peak 0.8 like the reference WAV.
    private static final double CURRENT_GAIN = 1.575;
    private static final double ROLL_GAIN = 3.052;
    private static final double FAN_GAIN = 1.063;
    private static final double OUT_GAIN = 0.08;
    // Rolling and fan noise as in the reference; the motor's hum and whine are by ear.
    private static final double ROLL = 0.18;
    private static final double FAN = 0.05;
    /** The 2f hum of the magnetic pull on the stator. */
    private static final double HUM = 0.35;
    /** Rotor slot whine at {@link #SLOT_RATIO} × f, rising with speed like a real motor (the ブーン when starting). */
    private static final double WHINE = 0.2;
    private static final double SLOT_RATIO = 22;
    // Heard from a distance / through the floor: highs fade and the reflections off the surroundings blur it.
    private static final double DISTANT_CUTOFF = 1000;
    /** Starting and stopping sound less muffled: the cutoff rises to this at standstill, back to normal by 25 Hz. */
    private static final double LOW_SPEED_CUTOFF = 2200;
    private static final double CLEAR_BELOW_F = 25;
    /** Reflection delays in samples (~48 / 77 / 109 / 146 ms) and their feedback; how much of them is mixed in. */
    private static final int[] ECHO_DELAYS = {2311, 3709, 5233, 7019};
    private static final double ECHO_FEEDBACK = 0.6;
    private static final double ECHO_MIX = 1.0;
    /** The direct sound, quieter than the reflections' share the farther away it sounds. */
    private static final double DIRECT = 0.55;

    volatile double target;
    volatile int state = COAST;
    volatile double volume = 1;
    volatile Profile profile = PROFILES[0];

    private double f;
    private double gate;
    private int pulses;
    private double phase;
    private double carrierPhase;
    /** The random modulation's current share of the carrier, picked anew every carrier period. */
    private double jitterFactor = 1;
    private double gearPhase;
    private double slotPhase;
    private final SplittableRandom random = new SplittableRandom();
    private final Biquad lowpass = Biquad.lowpass1(450);
    /** The windings' inductance also takes the edge off the PWM's highest harmonics. */
    private final Biquad inductance = Biquad.lowpass(1200);
    private final Biquad highpass = Biquad.highpass(70);
    private final Biquad rollHigh = Biquad.highpass(150);
    private final Biquad rollLow = Biquad.lowpass(2500);
    private final Biquad fanHigh = Biquad.highpass(2500);
    private final Biquad[] resonances = new Biquad[RESONANCES.length];
    private final Biquad distant = Biquad.lowpass(DISTANT_CUTOFF);
    private final Compressor compressor = new Compressor(random);
    private final double[][] echoes = new double[ECHO_DELAYS.length][];
    private final int[] echoAt = new int[ECHO_DELAYS.length];

    VvvfSynth() {
        for (int i = 0; i < resonances.length; i++) {
            resonances[i] = Biquad.peak(RESONANCES[i][0], RESONANCES[i][1]);
        }
        for (int i = 0; i < echoes.length; i++) {
            echoes[i] = new double[ECHO_DELAYS[i]];
        }
    }

    /** Fills {@code out} with mono samples in [-1, 1]. */
    void render(float[] out) {
        double target = Math.max(this.target, 0);
        int state = this.state;
        Profile p = profile;
        boolean brake = state == BRAKE;
        double[] tableF = brake ? p.brakeF() : p.accelF();
        int[] tableN = brake ? p.brakeN() : p.accelN();
        double gateTarget = state == POWER || brake && f > p.regenMinF() ? 1 : 0;
        // Once per chunk (100 ms) is smooth enough, and keeps tan() out of the per-sample loop.
        distant.set(Biquad.lowpass(DISTANT_CUTOFF
            + (LOW_SPEED_CUTOFF - DISTANT_CUTOFF) * Math.max(0, 1 - f / CLEAR_BELOW_F)));
        for (int i = 0; i < out.length; i++) {
            // Follow the game's 20 Hz updates smoothly (~50 ms), and fade the inverter in / out over 0.2 s.
            f += (target - f) * (1.0 / 2400);
            gate += Math.max(-1.0 / 9600, Math.min(1.0 / 9600, gateTarget - gate));
            // Only heard at low speed: fades out between 10 and 20 Hz (~14–27 km/h at Create's top speed).
            double cp = compressor.next() * Math.max(0, Math.min((20 - f) / 10, 1));
            if (f < 1e-3 && gate <= 0) {
                // Standing still: only the compressor, and the reflections ringing out.
                out[i] = (float) (distant(cp) * OUT_GAIN * volume);
                continue;
            }
            double spd = Math.min(f / F_MAX, 1);

            double pwm = 0;
            if (gate > 0) {
                int n = pulses(tableF, tableN);
                double m = brake ? interp(p.brakeMF(), p.brakeM(), f) : interp(p.accelMF(), p.accelM(), f);
                double carrier = (p.stepped() ? step(p.carrierF(), p.carrierHz(), f) : interp(p.carrierF(), p.carrierHz(), f))
                    * jitterFactor;
                for (int k = 0; k < OS; k++) {
                    phase = frac(phase + f / (FS * OS));
                    double last = carrierPhase;
                    carrierPhase = n > 0 ? frac(n * phase) : frac(carrierPhase + carrier / (FS * OS));
                    if (n == 0 && p.jitter() > 0 && carrierPhase < last) {
                        jitterFactor = 1 + random.nextDouble(-p.jitter(), p.jitter());
                    }
                    double s = Math.sin(2 * Math.PI * phase);
                    double tri = 2 * Math.abs(2 * carrierPhase - 1) - 1;
                    pwm += n == 1 ? (s >= 0 ? 1 : -1) : m * s > tri ? 1 : -1;
                }
                pwm /= OS;
                if (n == 0) {
                    // A bare carrier is shrill: start quieter and reach full level by the first sync mode.
                    // It also swells with the rotor's poles going round (2f), which makes it sound like a motor.
                    double low = 1 - Math.min(f / p.accelF()[1], 1);
                    pwm *= (1 - (1 - p.asyncLevel()) * low) * (1 + p.asyncSwell() * low * Math.sin(4 * Math.PI * phase));
                }
            } else {
                phase = frac(phase + f / FS);
            }
            double current = highpass.process(inductance.process(lowpass.process(pwm))) * CURRENT_GAIN * gate;

            double ripple = (0.35 * Math.sin(2 * Math.PI * 6 * phase) + 0.18 * Math.sin(2 * Math.PI * 12 * phase))
                * Math.min(0.3 + spd, 1.2) * gate;
            gearPhase = frac(gearPhase + 8 * f / FS);
            double gear = (Math.sin(2 * Math.PI * gearPhase) + 0.5 * Math.sin(4 * Math.PI * gearPhase)
                + 0.25 * Math.sin(6 * Math.PI * gearPhase)) * 0.22 * spd * (0.6 + 0.4 * gate);
            slotPhase = frac(slotPhase + SLOT_RATIO * f / FS);
            double noise = random.nextDouble(-1.7320508, 1.7320508);
            double motor = HUM * Math.sin(4 * Math.PI * phase) * Math.min(0.3 + spd, 1)
                + WHINE * Math.sin(2 * Math.PI * slotPhase) * (0.5 + 0.5 * Math.sqrt(spd));
            double src = current + ripple + gear + motor * gate;
            double body = src;
            for (int r = 0; r < resonances.length; r++) {
                body += RESONANCES[r][2] * resonances[r].process(src);
            }

            double roll = rollLow.process(rollHigh.process(noise)) * ROLL_GAIN * ROLL * Math.pow(spd, 1.5);
            double fan = fanHigh.process(noise) * FAN_GAIN * FAN * Math.max(0, Math.min((f - 30) / 44, 1));
            out[i] = (float) Math.max(-1, Math.min(1, distant(body + roll + fan + cp) * OUT_GAIN * volume));
        }
    }

    /** Muffles and adds reflections (parallel feedback combs). */
    private double distant(double x) {
        double dry = distant.process(x);
        double wet = 0;
        for (int i = 0; i < echoes.length; i++) {
            double delayed = echoes[i][echoAt[i]];
            echoes[i][echoAt[i]] = dry + delayed * ECHO_FEEDBACK;
            echoAt[i] = (echoAt[i] + 1) % echoes[i].length;
            wet += delayed;
        }
        return DIRECT * dry + wet * ECHO_MIX / echoes.length;
    }

    /** The table's pulse count for the current fundamental, holding the current one near a switching point. */
    private int pulses(double[] tableF, int[] tableN) {
        int below = lookup(tableF, tableN, f - HYSTERESIS);
        int above = lookup(tableF, tableN, f + HYSTERESIS);
        if (pulses != below && pulses != above) {
            pulses = lookup(tableF, tableN, f);
        }
        return pulses;
    }

    private static int lookup(double[] tableF, int[] tableN, double f) {
        int i = tableF.length - 1;
        while (i > 0 && f < tableF[i]) {
            i--;
        }
        return tableN[i];
    }

    /** ys at the last of xs that x has reached. */
    private static double step(double[] xs, double[] ys, double x) {
        int i = xs.length - 1;
        while (i > 0 && x < xs[i]) {
            i--;
        }
        return ys[i];
    }

    private static double interp(double[] xs, double[] ys, double x) {
        if (x <= xs[0]) {
            return ys[0];
        }
        for (int i = 1; i < xs.length; i++) {
            if (x < xs[i]) {
                return ys[i - 1] + (ys[i] - ys[i - 1]) * (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
            }
        }
        return ys[ys.length - 1];
    }

    private static double frac(double x) {
        return x - Math.floor(x);
    }

    /**
     * The air compressor (レシプロ式): every so often it runs for a while whether the train moves or
     * not, thudding twice per crank turn over the hum of its motor, then lets its unloader valve out with a プシュー.
     */
    private static final class Compressor {

        /** Thuds per second at full speed (two cylinders). */
        private static final double THUD_RATE = 22;
        private static final double LEVEL = 6;
        /** Seconds running, then seconds off, each picked at random in [min, max). */
        private static final double RUN_MIN = 12, RUN_MAX = 20, OFF_MIN = 30, OFF_MAX = 70;

        private final SplittableRandom random;
        private final Biquad thudHigh = Biquad.highpass(90);
        private final Biquad thudLow = Biquad.lowpass(600);
        private final Biquad exhaustHigh = Biquad.highpass(1200);
        private boolean running;
        /** Starts at a random point of its cycle so nearby trains don't run in step. */
        private long left;
        private double speed;
        private double crank;
        private double humPhase;
        private double thud;
        private boolean secondCylinder;
        private double exhaust;

        Compressor(SplittableRandom random) {
            this.random = random;
            left = (long) (random.nextDouble(0, OFF_MAX) * FS);
        }

        double next() {
            if (--left <= 0) {
                running = !running;
                left = (long) ((running ? random.nextDouble(RUN_MIN, RUN_MAX) : random.nextDouble(OFF_MIN, OFF_MAX)) * FS);
                if (!running) {
                    exhaust = 1;
                }
            }
            // Spins up and down over ~0.5 s.
            speed += ((running ? 1 : 0) - speed) * (1.0 / (0.5 * FS));
            if (speed < 1e-4 && exhaust < 1e-4) {
                return 0;
            }
            crank += THUD_RATE * speed / FS;
            if (crank >= 1) {
                crank -= 1;
                secondCylinder = !secondCylinder;
                thud = secondCylinder ? 0.7 : 1;
            }
            thud *= 0.9988; // ~17 ms decay
            exhaust *= 0.99996; // ~0.5 s decay
            humPhase = frac(humPhase + 120 * speed / FS);
            double noise = random.nextDouble(-1.7320508, 1.7320508);
            double hum = 0.1 * Math.sin(2 * Math.PI * humPhase) + 0.04 * Math.sin(4 * Math.PI * humPhase);
            return LEVEL * (thudLow.process(thudHigh.process(noise)) * thud * speed + hum * speed
                + 0.5 * exhaustHigh.process(noise) * exhaust);
        }
    }

    /** Direct form I biquad at FS, designed by the bilinear transform like scipy's butter / iirpeak. */
    private static final class Biquad {

        private double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        static Biquad lowpass1(double fc) {
            double k = Math.tan(Math.PI * fc / FS);
            return new Biquad(k, k, 0, 1 + k, k - 1, 0);
        }

        /** 2nd-order Butterworth. */
        static Biquad lowpass(double fc) {
            double k = Math.tan(Math.PI * fc / FS);
            double d = 1 + Math.sqrt(2) * k + k * k;
            return new Biquad(k * k, 2 * k * k, k * k, d, 2 * (k * k - 1), 1 - Math.sqrt(2) * k + k * k);
        }

        /** 2nd-order Butterworth. */
        static Biquad highpass(double fc) {
            double k = Math.tan(Math.PI * fc / FS);
            double d = 1 + Math.sqrt(2) * k + k * k;
            return new Biquad(1, -2, 1, d, 2 * (k * k - 1), 1 - Math.sqrt(2) * k + k * k);
        }

        /** scipy.signal.iirpeak: unity gain at fc. */
        static Biquad peak(double fc, double q) {
            double w0 = 2 * Math.PI * fc / FS;
            double g = 1 / (1 + Math.tan(w0 / q / 2));
            return new Biquad(1 - g, 0, g - 1, 1, -2 * g * Math.cos(w0), 2 * g - 1);
        }

        /** Takes over {@code design}'s response, keeping this filter's state so the sound doesn't click. */
        void set(Biquad design) {
            b0 = design.b0;
            b1 = design.b1;
            b2 = design.b2;
            a1 = design.a1;
            a2 = design.a2;
        }

        double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }
}
