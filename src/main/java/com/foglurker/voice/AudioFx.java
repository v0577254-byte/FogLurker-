package com.foglurker.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Small, dependency-free effects applied to stolen voices (48 kHz mono PCM). */
public final class AudioFx {
    public enum Style {
        /** Almost untouched: it should sound exactly like your friend. */
        NATURAL,
        /** Slightly too slow and too deep, with a faint echo. Something is wrong. */
        UNCANNY,
        /** Deep, slowed, crushed and echoing. Used when it stops pretending. */
        DEMONIC
    }

    private AudioFx() {
    }

    public static short[] apply(short[] in, Style style) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return switch (style) {
            case NATURAL -> resample(in, 0.97 + r.nextDouble() * 0.06);
            case UNCANNY -> echo(resample(in, 0.86 + r.nextDouble() * 0.05), 9000, 0.22F);
            case DEMONIC -> echo(crush(resample(in, 0.62 + r.nextDouble() * 0.06), 6), 7000, 0.45F);
        };
    }

    /** Joins pieces of several snippets into a new "sentence" the person never said. */
    public static short[] splice(List<short[]> snippets) {
        List<short[]> parts = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (short[] s : snippets) {
            if (s == null || s.length < 9600) {
                continue;
            }
            int len = Math.min(s.length, 9600 + r.nextInt(Math.max(1, Math.min(s.length, 48000) - 9600)));
            int start = r.nextInt(Math.max(1, s.length - len));
            short[] part = new short[len];
            System.arraycopy(s, start, part, 0, len);
            fade(part, 480);
            parts.add(part);
            parts.add(new short[2400 + r.nextInt(4800)]); // a breath of silence
        }
        int total = parts.stream().mapToInt(p -> p.length).sum();
        short[] out = new short[total];
        int pos = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }

    /** Playback-rate change: factor below 1 makes the voice slower and deeper. */
    static short[] resample(short[] in, double factor) {
        int outLen = (int) (in.length / factor);
        short[] out = new short[outLen];
        for (int i = 0; i < outLen; i++) {
            double src = i * factor;
            int i0 = (int) src;
            int i1 = Math.min(i0 + 1, in.length - 1);
            double frac = src - i0;
            out[i] = (short) (in[i0] * (1 - frac) + in[i1] * frac);
        }
        return out;
    }

    static short[] echo(short[] in, int delaySamples, float gain) {
        short[] out = new short[in.length + delaySamples * 2];
        for (int i = 0; i < out.length; i++) {
            float v = i < in.length ? in[i] : 0;
            if (i >= delaySamples && i - delaySamples < in.length) {
                v += in[i - delaySamples] * gain;
            }
            if (i >= delaySamples * 2 && i - delaySamples * 2 < in.length) {
                v += in[i - delaySamples * 2] * gain * gain;
            }
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }
        return out;
    }

    static short[] crush(short[] in, int bits) {
        int shift = 16 - bits;
        short[] out = new short[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (short) ((in[i] >> shift) << shift);
        }
        return out;
    }

    static void fade(short[] s, int samples) {
        int n = Math.min(samples, s.length / 2);
        for (int i = 0; i < n; i++) {
            float g = i / (float) n;
            s[i] = (short) (s[i] * g);
            s[s.length - 1 - i] = (short) (s[s.length - 1 - i] * g);
        }
    }

    public static double rms(short[] s, int from, int to) {
        double sum = 0;
        int n = 0;
        for (int i = Math.max(0, from); i < Math.min(s.length, to); i++) {
            sum += (double) s[i] * s[i];
            n++;
        }
        return n == 0 ? 0 : Math.sqrt(sum / n);
    }

    /** Cuts quiet lead-in / tail so snippets start right on the words. */
    public static short[] trimSilence(short[] s, double threshold) {
        int frame = 960;
        int start = 0;
        while (start + frame < s.length && rms(s, start, start + frame) < threshold) {
            start += frame;
        }
        int end = s.length;
        while (end - frame > start && rms(s, end - frame, end) < threshold) {
            end -= frame;
        }
        start = Math.max(0, start - frame);
        end = Math.min(s.length, end + frame);
        short[] out = new short[Math.max(0, end - start)];
        System.arraycopy(s, start, out, 0, out.length);
        return out;
    }
}
