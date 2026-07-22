package com.anomaly.goldiesettings.convai.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Process;
import android.util.Log;

import com.anomaly.goldiesettings.convai.codec.G711A;
import com.anomaly.goldiesettings.util.L;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 8 kHz mono PCM-16 stream playback for TTS audio.
 *
 * <p>Mirrors the C-side {@code playback_thread} in
 * {@code D:\vit\apkdemo\examples\goldieos\sdk_integration\convai_bridge.c}:
 * three-state machine (IDLE → PRIMING → PLAYING) with a ring buffer
 * between the network and the audio HW.
 *
 * <p>This Java version uses a {@link LinkedBlockingQueue} of small PCM
 * chunks (G.711A-decoded), with a small priming threshold before
 * {@code play()} is called.
 */
public class AudioPlayer {

    public static final int SAMPLE_RATE = 8_000;
    public static final int CHANNEL = AudioFormat.CHANNEL_OUT_MONO;
    public static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    /** 20 ms @ 8 kHz mono PCM-16 = 160 samples = 320 bytes. */
    public static final int FRAME_BYTES_8K = 320;
    /** ~160 ms of priming data, matching the C-side 480-byte threshold. */
    public static final int PRIMING_BYTES = 480;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;
    private AudioTrack track;
    private volatile boolean priming;
    private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();

    public synchronized boolean start() {
        if (running.get()) return true;
        int minBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING);
        if (minBuf <= 0) {
            L.e(L.TAG_AUDIO, "AudioTrack.getMinBufferSize: %d", minBuf);
            return false;
        }
        int bufBytes = Math.max(minBuf * 2, 8 * 1024);
        try {
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL)
                    .setEncoding(ENCODING)
                    .build())
                .setBufferSizeInBytes(bufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        } catch (Throwable t) {
            L.e(L.TAG_AUDIO, "AudioTrack ctor: %s", t.getMessage());
            return false;
        }
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            L.e(L.TAG_AUDIO, "AudioTrack not initialised");
            track.release();
            track = null;
            return false;
        }
        running.set(true);
        priming = true;
        thread = new Thread(this::playLoop, "ConvaiPlayback");
        thread.start();
        L.i(L.TAG_AUDIO, "playback started (sr=%d buf=%d)", SAMPLE_RATE, bufBytes);
        return true;
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) return;
        Thread t = thread;
        thread = null;
        queue.clear();
        if (t != null) {
            try { t.join(500); } catch (InterruptedException ignored) {}
        }
        AudioTrack tr = track;
        track = null;
        if (tr != null) {
            try { tr.pause(); tr.flush(); tr.stop(); } catch (Throwable ignored) {}
            tr.release();
        }
        L.i(L.TAG_AUDIO, "playback stopped");
    }

    /** Push a 20 ms G.711A frame; will be decoded and queued. */
    public void offerG711A(byte[] alaw, int len) {
        if (!running.get() || alaw == null || len <= 0) return;
        byte[] pcm = new byte[len * 2];
        G711A.decode(alaw, 0, len, pcm, 0);
        queue.offer(pcm);
    }

    /** Push already-decoded PCM16 LE bytes. */
    public void offerPcm(byte[] pcm, int len) {
        if (!running.get() || pcm == null || len <= 0) return;
        byte[] copy = new byte[len];
        System.arraycopy(pcm, 0, copy, 0, len);
        queue.offer(copy);
    }

    /** Drop buffered audio (used on INTERRUPTED). */
    public void drop() {
        queue.clear();
        priming = true;
        AudioTrack tr = track;
        if (tr != null) {
            try { tr.pause(); tr.flush(); } catch (Throwable ignored) {}
        }
    }

    private void playLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        int queuedBytes = 0;
        byte[] scratch = new byte[1024];
        try {
            while (running.get()) {
                byte[] chunk = queue.poll(10, TimeUnit.MILLISECONDS);
                if (chunk == null) continue;
                if (priming) {
                    queuedBytes += chunk.length;
                    if (queuedBytes >= PRIMING_BYTES) {
                        try { track.play(); } catch (Throwable t) {
                            Log.w(L.TAG_AUDIO, "track.play: " + t.getMessage());
                        }
                        priming = false;
                    }
                }
                if (!priming && track != null) {
                    int off = 0;
                    while (off < chunk.length) {
                        int n = Math.min(scratch.length, chunk.length - off);
                        System.arraycopy(chunk, off, scratch, 0, n);
                        int written = track.write(scratch, 0, n);
                        if (written < 0) {
                            Log.w(L.TAG_AUDIO, "track.write returned " + written);
                            break;
                        }
                        off += written;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            L.e(L.TAG_AUDIO, "play loop: %s", t.getMessage());
        }
    }
}
