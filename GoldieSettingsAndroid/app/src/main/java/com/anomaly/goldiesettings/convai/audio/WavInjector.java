package com.anomaly.goldiesettings.convai.audio;

import android.content.Context;

import com.anomaly.goldiesettings.R;
import com.anomaly.goldiesettings.convai.codec.G711A;
import com.anomaly.goldiesettings.util.L;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Debug-only replacement for {@link AudioCapture}: instead of reading the
 * microphone it streams {@code res/raw/test_voice.wav} (16 kHz mono PCM16)
 * in real time — 20 ms per frame — through the exact same
 * 16k → 8k → G.711A pipeline, then signals end-of-utterance.
 *
 * <p>Only wired up when {@code BuildConfig.DEBUG} and the debug switch in
 * the server-config page is on; the default (mic) path is untouched.
 */
public class WavInjector {

    public interface Sink {
        /** Receive a 20 ms 8 kHz mono G.711A frame (160 bytes typical). */
        void onG711AFrame(byte[] alaw, int len);
        /** Called once after the last frame has been delivered. */
        void onFinished(int frames);
    }

    private static final int FRAME_MS = 20;

    private final Context ctx;
    private final Sink sink;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;

    public WavInjector(Context ctx, Sink sink) {
        this.ctx = ctx;
        this.sink = sink;
    }

    public synchronized boolean start() {
        if (running.get()) return true;
        running.set(true);
        thread = new Thread(this::injectLoop, "WavInject");
        thread.start();
        return true;
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) return;
        Thread t = thread;
        thread = null;
        if (t != null) {
            try { t.join(500); } catch (InterruptedException ignored) {}
        }
        L.i(L.TAG_AUDIO, "wav inject stopped");
    }

    private void injectLoop() {
        byte[] pcm16k;
        try {
            pcm16k = loadWavPcm();
        } catch (Throwable t) {
            L.e(L.TAG_AUDIO, "wav inject load: %s", t.getMessage());
            running.set(false);
            return;
        }
        int totalFrames = pcm16k.length / AudioCapture.FRAME_BYTES_16K;
        L.i(L.TAG_AUDIO, "wav inject start: pcm=%d bytes frames=%d",
            pcm16k.length, totalFrames);

        byte[] downBuf = new byte[AudioCapture.FRAME_BYTES_16K / 2];
        byte[] alawBuf = new byte[AudioCapture.FRAME_BYTES_16K / 2];
        int frames = 0;
        long next = System.nanoTime();
        try {
            for (int off = 0; off + AudioCapture.FRAME_BYTES_16K <= pcm16k.length
                    && running.get(); off += AudioCapture.FRAME_BYTES_16K) {
                int pcm8kLen = Resampler.downsample16kTo8k(
                    pcm16k, off, AudioCapture.FRAME_BYTES_16K, downBuf, 0);
                int alawLen = G711A.encode(downBuf, 0, pcm8kLen, alawBuf, 0);
                sink.onG711AFrame(alawBuf, alawLen);
                frames++;
                // Pace at real time: one 20 ms frame every 20 ms.
                next += FRAME_MS * 1_000_000L;
                long sleepNs = next - System.nanoTime();
                if (sleepNs > 0) {
                    try { Thread.sleep(sleepNs / 1_000_000L, (int) (sleepNs % 1_000_000L)); }
                    catch (InterruptedException e) { break; }
                }
            }
        } catch (Throwable t) {
            L.e(L.TAG_AUDIO, "wav inject loop: %s", t.getMessage());
        } finally {
            running.set(false);
        }
        L.i(L.TAG_AUDIO, "wav inject end: sent %d frames", frames);
        if (frames > 0) sink.onFinished(frames);
    }

    /** Load the raw wav resource and return the PCM payload of its data chunk. */
    private byte[] loadWavPcm() throws Exception {
        byte[] file;
        try (InputStream in = ctx.getResources().openRawResource(R.raw.test_voice)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            file = out.toByteArray();
        }
        ByteBuffer bb = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
        if (file.length < 12 || bb.getInt(0) != 0x46464952 /* "RIFF" */) {
            throw new IllegalStateException("not a RIFF file");
        }
        // Walk chunks after "RIFF<size>WAVE" (12 bytes) until the data chunk.
        int off = 12;
        while (off + 8 <= file.length) {
            int id = bb.getInt(off);
            int size = bb.getInt(off + 4);
            if (id == 0x61746164 /* "data" */) {
                int len = Math.min(size, file.length - off - 8);
                byte[] pcm = new byte[len];
                System.arraycopy(file, off + 8, pcm, 0, len);
                return pcm;
            }
            off += 8 + size + (size & 1); // chunks are word-aligned
        }
        throw new IllegalStateException("no data chunk in wav");
    }
}
