package com.anomaly.goldiesettings.convai.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * G.711 A-law codec (ITU-T G.711). 1:1 port of
 * {@code D:\vit\apkdemo\examples\goldieos\sdk_integration\convai_codec_g711a.c}.
 *
 * <p>16-bit linear PCM (little-endian) ↔ 8-bit A-law byte stream.
 *
 * <p>The bridge layer also uses the planar (de-interleaved) mode, in which
 * the same algorithm is applied per-channel and the output is laid out
 * per-channel-block.
 */
public final class G711A {

    private G711A() {}

    // ---------------------------------------------------------------------
    // PCM16 → A-law
    // ---------------------------------------------------------------------
    private static byte pcm16ToAlaw(short pcmVal) {
        int mask;
        if (pcmVal >= 0) {
            mask = 0xD5;            // sign bit = 1 for positive
        } else {
            mask = 0x55;            // sign bit = 0 for negative
            pcmVal = (short) (-pcmVal - 8);  // avoid -32768 overflow
        }
        // 16-bit linear → 13-bit linear
        pcmVal = (short) (pcmVal >> 3);
        if (pcmVal > 4095) pcmVal = 4095;
        if (pcmVal < 0) pcmVal = 4095;

        int seg = 0;
        if (pcmVal >= 32)  { seg = 1; pcmVal = (short) (pcmVal >> 1);
        if (pcmVal >= 32)  { seg = 2; pcmVal = (short) (pcmVal >> 1);
        if (pcmVal >= 32)  { seg = 3; pcmVal = (short) (pcmVal >> 1);
        if (pcmVal >= 32)  { seg = 4; pcmVal = (short) (pcmVal >> 1);
        if (pcmVal >= 32)  { seg = 5; pcmVal = (short) (pcmVal >> 1);
        if (pcmVal >= 32)  { seg = 6; pcmVal = (short) (pcmVal >> 1);
        if (pcmVal >= 32)  { seg = 7; pcmVal = (short) (pcmVal >> 1); }}}}}}}

        int aval = (seg << 4) | ((pcmVal >> 1) & 0x0F);
        return (byte) ((aval ^ mask) & 0xFF);
    }

    // ---------------------------------------------------------------------
    // A-law → PCM16
    // ---------------------------------------------------------------------
    private static short alawToPcm16(byte alawVal) {
        int a = (alawVal & 0xFF) ^ 0x55;
        int t = (a & 0x0F) << 4;
        int seg = (a & 0x70) >> 4;
        switch (seg) {
            case 0:
                t += 8;
                break;
            case 1:
                t += 0x108;
                break;
            default:
                t += 0x108;
                t <<= (seg - 1);
                break;
        }
        return (short) ((a & 0x80) != 0 ? t : -t);
    }

    // ---------------------------------------------------------------------
    // Public helpers
    // ---------------------------------------------------------------------

    /**
     * Encode interleaved or planar PCM16 LE bytes to A-law bytes (1:1 sample ratio).
     * @return number of A-law bytes written.
     */
    public static int encode(byte[] pcm, int pcmOff, int pcmLen, byte[] out, int outOff) {
        if ((pcmLen & 1) != 0) throw new IllegalArgumentException("pcmLen must be even");
        int n = pcmLen / 2;
        for (int i = 0; i < n; i++) {
            int lo = pcm[pcmOff + i * 2] & 0xFF;
            int hi = pcm[pcmOff + i * 2 + 1] & 0xFF;
            short s = (short) (lo | (hi << 8));
            out[outOff + i] = pcm16ToAlaw(s);
        }
        return n;
    }

    /**
     * Decode A-law bytes to PCM16 LE bytes (1:2 ratio).
     * @return number of PCM bytes written.
     */
    public static int decode(byte[] alaw, int alawOff, int alawLen, byte[] out, int outOff) {
        for (int i = 0; i < alawLen; i++) {
            short s = alawToPcm16(alaw[alawOff + i]);
            out[outOff + i * 2]     = (byte) (s & 0xFF);
            out[outOff + i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        return alawLen * 2;
    }

    /**
     * Planar G.711A encode — input layout is [L0, L1, ... Ln, R0, R1, ... Rn]
     * (each channel is a contiguous block), output follows the same layout.
     * Matches {@code convai_g711a_encode(... channels=2 ...)}.
     *
     * @return number of A-law bytes written.
     */
    public static int encodePlanar(byte[] pcm, int pcmOff, int pcmLen, int channels,
                                   byte[] out, int outOff) {
        if (channels < 1) channels = 1;
        int totalSamples = pcmLen / 2;
        int frames = totalSamples / channels;
        for (int ch = 0; ch < channels; ch++) {
            for (int f = 0; f < frames; f++) {
                int idx = pcmOff + (ch * frames + f) * 2;
                int lo = pcm[idx] & 0xFF;
                int hi = pcm[idx + 1] & 0xFF;
                short s = (short) (lo | (hi << 8));
                out[outOff + ch * frames + f] = pcm16ToAlaw(s);
            }
        }
        return frames * channels;
    }

    /** Self-check: round-trip PCM → A-law → PCM; max error should be &lt; 256 (~1/256 of full scale). */
    public static int selfTest() {
        java.util.Random r = new java.util.Random(42);
        byte[] pcm = new byte[2048];
        ByteBuffer bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < pcm.length / 2; i++) {
            int v = r.nextInt(65536) - 32768;
            bb.putShort((short) v);
        }
        byte[] alaw = new byte[pcm.length / 2];
        encode(pcm, 0, pcm.length, alaw, 0);
        byte[] back = new byte[pcm.length];
        decode(alaw, 0, alaw.length, back, 0);
        int max = 0;
        ByteBuffer a = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer b = ByteBuffer.wrap(back).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < pcm.length / 2; i++) {
            int d = Math.abs(a.getShort(i * 2) - b.getShort(i * 2));
            if (d > max) max = d;
        }
        return max;
    }
}
