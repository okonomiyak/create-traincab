package uk.iwaservice.traincab.client;

import java.util.SplittableRandom;

/**
 * Real-time GTO-VVVF traction sound (阪急8000系): PWM from a sine reference and a triangle carrier (asynchronous
 * 365 Hz at low speed, then synchronous 27P→1P), turned into motor current, plus torque ripple, gear whine, rolling
 * and fan noise, through the motor body's resonances. Ported from vvvf_run8000.py; the modulation index, gains and
 * resonances are by ear, the switching frequencies read off spectrograms.
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
    private static final double ASYNC_CARRIER = 365;
    /** In 力行 / 回生 the pulse count switches up at these fundamentals (Hz); 0 = asynchronous. */
    private static final double[] ACCEL_F = {0, 9, 16.4, 29.6, 41.3, 49.0, 52.4};
    private static final int[] ACCEL_N = {0, 27, 15, 9, 5, 3, 1};
    private static final double[] ACCEL_M_F = {0, 9, 16.4, 29.6, 41.3, 49.0};
    private static final double[] ACCEL_M = {0.08, 0.5, 0.6, 0.75, 0.9, 1.0};
    private static final double[] BRAKE_F = {0, 33, 43, 59, 67};
    private static final int[] BRAKE_N = {15, 9, 5, 3, 1};
    private static final double[] BRAKE_M_F = {12.3, 33, 43, 59, 67};
    private static final double[] BRAKE_M = {0.6, 0.7, 0.85, 0.95, 1.0};
    /** 回生 fades out below this; the air brake does the rest. */
    private static final double REGEN_MIN_F = 11;
    /** How far past a switching point the fundamental has to go before switching back. */
    private static final double HYSTERESIS = 0.5;
    /** (centre Hz, Q, gain) of the motor body's resonances. */
    private static final double[][] RESONANCES = {{420, 6, 1.6}, {780, 7, 1.3}, {1350, 8, 1.0}, {2100, 8, 0.7}, {3400, 8, 0.4}};
    // Measured on vvvf_run8000.py's station run: the motor current to std 1 while running, the filtered noise to
    // std 1, and the whole run to peak 0.8 like the reference WAV.
    private static final double CURRENT_GAIN = 1.575;
    private static final double ROLL_GAIN = 3.052;
    private static final double FAN_GAIN = 1.063;
    private static final double OUT_GAIN = 0.08;
    // By ear (raised from the reference's 0.18 / 0.05 / none for more noise).
    private static final double ROLL = 0.6;
    private static final double FAN = 0.15;
    private static final double MAGNETIC_NOISE = 0.6;

    volatile double target;
    volatile int state = COAST;
    volatile double volume = 1;

    private double f;
    private double gate;
    private int pulses;
    private double phase;
    private double carrierPhase;
    private double gearPhase;
    private final SplittableRandom random = new SplittableRandom();
    private final Biquad lowpass = Biquad.lowpass1(450);
    private final Biquad highpass = Biquad.highpass(70);
    private final Biquad rollHigh = Biquad.highpass(150);
    private final Biquad rollLow = Biquad.lowpass(2500);
    private final Biquad fanHigh = Biquad.highpass(2500);
    private final Biquad[] resonances = new Biquad[RESONANCES.length];

    VvvfSynth() {
        for (int i = 0; i < resonances.length; i++) {
            resonances[i] = Biquad.peak(RESONANCES[i][0], RESONANCES[i][1]);
        }
    }

    /** Fills {@code out} with mono samples in [-1, 1]. */
    void render(float[] out) {
        double target = Math.max(this.target, 0);
        int state = this.state;
        boolean brake = state == BRAKE;
        double[] tableF = brake ? BRAKE_F : ACCEL_F;
        int[] tableN = brake ? BRAKE_N : ACCEL_N;
        double gateTarget = state == POWER || brake && f > REGEN_MIN_F ? 1 : 0;
        for (int i = 0; i < out.length; i++) {
            // Follow the game's 20 Hz updates smoothly (~50 ms), and fade the inverter in / out over 0.2 s.
            f += (target - f) * (1.0 / 2400);
            gate += Math.max(-1.0 / 9600, Math.min(1.0 / 9600, gateTarget - gate));
            if (f < 1e-3 && gate <= 0) {
                out[i] = 0;
                continue;
            }
            double spd = Math.min(f / F_MAX, 1);

            double pwm = 0;
            if (gate > 0) {
                int n = pulses(tableF, tableN);
                double m = brake ? interp(BRAKE_M_F, BRAKE_M, f) : interp(ACCEL_M_F, ACCEL_M, f);
                for (int k = 0; k < OS; k++) {
                    phase = frac(phase + f / (FS * OS));
                    carrierPhase = n > 0 ? frac(n * phase) : frac(carrierPhase + ASYNC_CARRIER / (FS * OS));
                    double s = Math.sin(2 * Math.PI * phase);
                    double tri = 2 * Math.abs(2 * carrierPhase - 1) - 1;
                    pwm += n == 1 ? (s >= 0 ? 1 : -1) : m * s > tri ? 1 : -1;
                }
                pwm /= OS;
            } else {
                phase = frac(phase + f / FS);
            }
            double current = highpass.process(lowpass.process(pwm)) * CURRENT_GAIN * gate;

            double ripple = (0.35 * Math.sin(2 * Math.PI * 6 * phase) + 0.18 * Math.sin(2 * Math.PI * 12 * phase))
                * Math.min(0.3 + spd, 1.2) * gate;
            gearPhase = frac(gearPhase + 8 * f / FS);
            double gear = (Math.sin(2 * Math.PI * gearPhase) + 0.5 * Math.sin(4 * Math.PI * gearPhase)
                + 0.25 * Math.sin(6 * Math.PI * gearPhase)) * 0.22 * spd * (0.6 + 0.4 * gate);
            double noise = random.nextDouble(-1.7320508, 1.7320508);
            // Switching / magnetic hiss while the inverter runs, coloured by the resonances below.
            double src = current + ripple + gear + MAGNETIC_NOISE * noise * gate;
            double body = src;
            for (int r = 0; r < resonances.length; r++) {
                body += RESONANCES[r][2] * resonances[r].process(src);
            }

            double roll = rollLow.process(rollHigh.process(noise)) * ROLL_GAIN * ROLL * Math.pow(spd, 1.5);
            double fan = fanHigh.process(noise) * FAN_GAIN * FAN * Math.max(0, Math.min((f - 30) / 44, 1));
            out[i] = (float) Math.max(-1, Math.min(1, (body + roll + fan) * OUT_GAIN * volume));
        }
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

    /** Direct form I biquad at FS, designed by the bilinear transform like scipy's butter / iirpeak. */
    private static final class Biquad {

        private final double b0, b1, b2, a1, a2;
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
