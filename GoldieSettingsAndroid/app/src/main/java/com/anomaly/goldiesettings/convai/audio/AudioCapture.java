package com.anomaly.goldiesettings.convai.audio;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.anomaly.goldiesettings.convai.codec.G711A;
import com.anomaly.goldiesettings.util.L;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 16 kHz mono PCM-16 microphone capture, down-sampled to 8 kHz and
 * G.711A-encoded.
 *
 * <p>Mirrors the C-side {@code audio_record_thread} in
 * {@code D:\vit\apkdemo\examples\goldieos\sdk_integration\convai_bridge.c}:
 * read 20 ms of PCM, convert, and hand the encoded bytes to a sink.
 */
public class AudioCapture {

    public interface Sink {
        /** Receive a 20 ms 8 kHz mono G.711A frame (160 bytes typical). */
        void onG711AFrame(byte[] alaw, int len);
    }

    public static final int SAMPLE_RATE_IN = 16_000;
    public static final int CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO;
    public static final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    /** 20 ms at 16 kHz mono = 320 samples = 640 bytes. */
    public static final int FRAME_BYTES_16K = 640;

    private final Context ctx;
    private final Sink sink;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread thread;

    public AudioCapture(Context ctx, Sink sink) {
        this.ctx = ctx;
        this.sink = sink;
    }

    public static boolean hasPermission(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED;
    }

    public synchronized boolean start() {
        if (running.get()) return true;
        if (!hasPermission(ctx)) {
            L.w(L.TAG_AUDIO, "RECORD_AUDIO not granted");
            return false;
        }
        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN, CHANNEL_IN, ENCODING);
        if (minBuf <= 0) {
            L.e(L.TAG_AUDIO, "getMinBufferSize failed: %d", minBuf);
            return false;
        }
        int bufBytes = Math.max(minBuf, FRAME_BYTES_16K * 4);
        AudioRecord ar;
        try {
            ar = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE_IN, CHANNEL_IN, ENCODING, bufBytes);
        } catch (Throwable t) {
            L.e(L.TAG_AUDIO, "AudioRecord ctor: %s", t.getMessage());
            return false;
        }
        if (ar.getState() != AudioRecord.STATE_INITIALIZED) {
            L.e(L.TAG_AUDIO, "AudioRecord not initialised");
            ar.release();
            return false;
        }
        running.set(true);
        final AudioRecord record = ar;
        thread = new Thread(() -> captureLoop(record), "ConvaiCapture");
        thread.start();
        L.i(L.TAG_AUDIO, "capture started (sr=%d buf=%d)", SAMPLE_RATE_IN, bufBytes);
        return true;
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) return;
        Thread t = thread;
        thread = null;
        if (t != null) {
            try { t.join(500); } catch (InterruptedException ignored) {}
        }
        L.i(L.TAG_AUDIO, "capture stopped");
    }

    private void captureLoop(AudioRecord ar) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        byte[] inBuf = new byte[FRAME_BYTES_16K * 2];
        byte[] downBuf = new byte[FRAME_BYTES_16K / 2];
        byte[] alawBuf = new byte[FRAME_BYTES_16K / 2];
        try {
            ar.startRecording();
            while (running.get()) {
                int n = ar.read(inBuf, 0, inBuf.length);
                if (n <= 0) {
                    Log.w(L.TAG_AUDIO, "AudioRecord.read returned " + n);
                    try { Thread.sleep(10); } catch (InterruptedException e) { break; }
                    continue;
                }
                // Round up to multiple of FRAME_BYTES_16K
                int consumed = 0;
                while (consumed + FRAME_BYTES_16K <= n) {
                    int pcm8kLen = Resampler.downsample16kTo8k(
                        inBuf, consumed, FRAME_BYTES_16K, downBuf, 0);
                    int alawLen = G711A.encode(downBuf, 0, pcm8kLen, alawBuf, 0);
                    sink.onG711AFrame(alawBuf, alawLen);
                    consumed += FRAME_BYTES_16K;
                }
            }
        } catch (Throwable t) {
            L.e(L.TAG_AUDIO, "capture loop: %s", t.getMessage());
        } finally {
            try { ar.stop(); } catch (Throwable ignored) {}
            ar.release();
        }
    }
}
