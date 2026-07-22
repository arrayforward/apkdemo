package com.anomaly.goldiesettings.convai.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 16 kHz mono PCM-16 → 8 kHz mono PCM-16 linear-interpolation down-sampler.
 *
 * <p>Used because {@code convai_bridge.c} reads 16 kHz stereo from the mic
 * and down-samples to 8 kHz mono before G.711A encoding (the audio codec
 * declared in {@code hello.body.audio_codec = 1}).
 *
 * <p>For full quality we'd use a proper FIR decimation filter with
 * anti-aliasing; we use linear interpolation here as a faithful port of
 * the C bridge (which currently feeds a single mono channel and trusts the
 * G.711A log quantiser to mask any aliasing).
 */
public final class Resampler {

    private Resampler() {}

    /**
     * Down-sample 16 kHz mono PCM16 LE to 8 kHz mono PCM16 LE.
     *
     * @param in16k input buffer at 16 kHz, little-endian
     * @param inOff offset in bytes
     * @param inLen length in bytes (must be even)
     * @param out8k output buffer (inLen bytes is enough)
     * @return number of bytes written
     */
    public static int downsample16kTo8k(byte[] in16k, int inOff, int inLen, byte[] out8k, int outOff) {
        if ((inLen & 1) != 0) throw new IllegalArgumentException("inLen must be even");
        int inSamples = inLen / 2;
        int outSamples = inSamples / 2;

        ByteBuffer src = ByteBuffer.wrap(in16k, inOff, inLen).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer dst = ByteBuffer.wrap(out8k, outOff, outSamples * 2).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < outSamples; i++) {
            int idx0 = i * 2;
            int idx1 = Math.min(idx0 + 1, inSamples - 1);
            // NB: absolute ByteBuffer.getShort is array-relative, so inOff
            // must be added explicitly (wrap() only sets position/limit).
            short s0 = src.getShort(inOff + idx0 * 2);
            short s1 = src.getShort(inOff + idx1 * 2);
            // Linear interpolation at fractional position 0.5 between s0 and s1
            int mixed = ((int) s0 + (int) s1) >> 1;
            dst.putShort((short) mixed);
        }
        return outSamples * 2;
    }

    /**
     * Down-sample stereo 16 kHz PCM16 LE to mono 8 kHz PCM16 LE.
     *
     * <p>Takes the average of L and R per frame (the embedded C bridge
     * actually discards R and forwards L, then sets R=0 for cloud AEC; we
     * take the average which is closer to a real microphone).
     */
    public static int downsampleStereo16kToMono8k(byte[] inStereo16k, int inOff, int inLen,
                                                  byte[] outMono8k, int outOff) {
        if ((inLen & 3) != 0) throw new IllegalArgumentException("inLen must be multiple of 4 (stereo 16-bit)");
        int inFrames = inLen / 4;
        int outFrames = inFrames / 2;

        ByteBuffer src = ByteBuffer.wrap(inStereo16k, inOff, inLen).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer dst = ByteBuffer.wrap(outMono8k, outOff, outFrames * 2).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < outFrames; i++) {
            int idx0 = i * 4;        // L of frame 2i
            int idx1 = idx0 + 4;     // L of frame 2i+1
            // NB: absolute ByteBuffer.getShort is array-relative; add inOff.
            short l0 = src.getShort(inOff + idx0);
            short r0 = src.getShort(inOff + idx0 + 2);
            short l1 = src.getShort(inOff + idx1);
            short r1 = src.getShort(inOff + idx1 + 2);
            int mixed0 = (((int) l0 + (int) r0) >> 1);
            int mixed1 = (((int) l1 + (int) r1) >> 1);
            short sample = (short) ((mixed0 + mixed1) >> 1);
            dst.putShort(sample);
        }
        return outFrames * 2;
    }
}
