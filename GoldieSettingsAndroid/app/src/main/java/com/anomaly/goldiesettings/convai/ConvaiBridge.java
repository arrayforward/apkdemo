package com.anomaly.goldiesettings.convai;

import android.content.Context;

import androidx.annotation.NonNull;

import com.anomaly.goldiesettings.BuildConfig;
import com.anomaly.goldiesettings.convai.audio.AudioCapture;
import com.anomaly.goldiesettings.convai.audio.AudioPlayer;
import com.anomaly.goldiesettings.convai.audio.WavInjector;
import com.anomaly.goldiesettings.convai.codec.G711A;
import com.anomaly.goldiesettings.model.Settings;
import com.anomaly.goldiesettings.util.L;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Glue layer between the {@link ConvaiEngine} and the audio I/O.
 *
 * <p>Mirrors the C-side {@code convai_bridge.c} in
 * {@code D:\vit\apkdemo\examples\goldieos\sdk_integration\convai_bridge.c}.
 *
 * <ul>
 *   <li>On start: open the engine (which dials the WS and sends hello) and
 *       start the microphone capture thread.</li>
 *   <li>Capture thread pushes 20 ms G.711A frames into the engine
 *       (binary frame, op=Frame).</li>
 *   <li>Engine feeds inbound audio into the {@link AudioPlayer}, which
 *       decodes G.711A → PCM and writes to AudioTrack.</li>
 *   <li>State changes (LISTENING/ANSWERING/INTERRUPTED) drive player
 *       priming / dropping.</li>
 * </ul>
 */
public class ConvaiBridge implements ConvaiEngine.Handler {

    private final Context appContext;
    private final ConvaiEngine engine;
    private final AudioCapture capture;
    private final AudioPlayer player;
    private final WavInjector injector;
    private Listener listener;

    public interface Listener {
        void onEvent(@NonNull ConvaiEvent e, String details);
        void onStatus(@NonNull ConvaiStatus s);
        void onServerMessage(@NonNull ServerMessage m);
    }

    /** Debug-only: stream res/raw/test_voice.wav instead of mic audio. */
    private boolean wavInjectEnabled() {
        return BuildConfig.DEBUG && Settings.get().debugWavInject();
    }

    public ConvaiBridge(Context ctx) {
        this.appContext = ctx.getApplicationContext();
        this.engine = new ConvaiEngine();
        this.player = new AudioPlayer();
        this.capture = new AudioCapture(appContext, (alaw, len) -> {
            // Mic → server (binary frame)
            if (engine.isStarted()) {
                byte[] frame = new byte[len];
                System.arraycopy(alaw, 0, frame, 0, len);
                engine.sendAudio(frame);
            }
        });
        this.injector = new WavInjector(appContext, new WavInjector.Sink() {
            @Override public void onG711AFrame(byte[] alaw, int len) {
                // Injected wav → server (identical binary frame path as mic)
                if (engine.isStarted()) {
                    byte[] frame = new byte[len];
                    System.arraycopy(alaw, 0, frame, 0, len);
                    engine.sendAudio(frame);
                }
            }
            @Override public void onFinished(int frames) {
                if (engine.isStarted()) {
                    engine.sendAudioEnd();
                    L.i(L.TAG_AUDIO, "wav inject: audio END sent after %d frames", frames);
                }
            }
        });
        engine.setHandler(this);
    }

    public ConvaiEngine engine() { return engine; }
    public AudioPlayer player() { return player; }
    public void setListener(Listener l) { this.listener = l; }

    public void start(@NonNull String wsUrl, @NonNull String agentId) {
        L.i(L.TAG_BRIDGE, "start: %s agent=%s", wsUrl, agentId);
        JSONObject params = null;
        try {
            Settings s = Settings.get();
            params = new JSONObject()
                .put("product_id", s.productId())
                .put("product_key", s.productKey())
                .put("product_secret", s.productSecret())
                .put("device_name", s.deviceName());
        } catch (JSONException e) {
            L.e(L.TAG_BRIDGE, "build hello params: %s", e.getMessage());
        }
        engine.start(wsUrl, agentId, params);
        if (wavInjectEnabled()) {
            L.i(L.TAG_AUDIO, "wav inject: debug mode on, mic capture bypassed");
            // On a real device the server watermark clip (first clip of a new
            // uid) is played on the speaker and picked up by the mic, which
            // calibrates server-side AEC; the mediator drops upstream audio
            // until calibrated. On a -no-audio emulator there is no loopback,
            // so inject mode buffers the watermark and echoes it back as
            // uplink frames, then starts the wav injection.
            wmActive = true;
            wmDone = false;
            wmReceiving = false;
            wmBuf.reset();
            mainHandler.postDelayed(wmTimeout, WM_TIMEOUT_MS);
        } else {
            capture.start();
        }
    }

    public void stop() {
        L.i(L.TAG_BRIDGE, "stop");
        wmActive = false;
        mainHandler.removeCallbacks(wmTimeout);
        injector.stop();
        capture.stop();
        engine.stop();
        player.drop();
    }

    public void destroy() {
        stop();
        player.stop();
    }

    // ---------------------------------------------------------------------
    //  ConvaiEngine.Handler
    // ---------------------------------------------------------------------

    @Override public void onEvent(@NonNull ConvaiEvent event, String details) {
        if (event == ConvaiEvent.DISCONNECTED || event == ConvaiEvent.FAILED) {
            wmActive = false;
            mainHandler.removeCallbacks(wmTimeout);
            injector.stop();
            capture.stop();
            player.drop();
        } else if (event == ConvaiEvent.CONNECTED) {
            // Make sure playback is primed
            if (player != null) player.start();
        }
        if (listener != null) listener.onEvent(event, details);
    }

    @Override public void onStatus(@NonNull ConvaiStatus status) {
        switch (status) {
            case ANSWERING:
                if (player != null) player.start();
                if (wavInjectEnabled()) {
                    if (wmActive && !wmDone) {
                        // First clip of a fresh uid: the AEC watermark.
                        wmReceiving = true;
                        wmBuf.reset();
                        L.i(L.TAG_AUDIO, "wav inject: watermark clip start");
                    } else {
                        clipIdx++;
                        clipFrames = 0;
                        clipBytes = 0;
                        L.i(L.TAG_AUDIO, "wav inject: clip #%d start", clipIdx);
                    }
                }
                break;
            case INTERRUPTED:
                if (player != null) player.drop();
                // also signal server to drop buffered audio
                engine.sendAudioCancel();
                break;
            case LISTENING:
                if (wavInjectEnabled()) {
                    if (wmReceiving) {
                        wmReceiving = false;
                        wmDone = true;
                        mainHandler.removeCallbacks(wmTimeout);
                        echoWatermark();
                        injector.start();
                    } else if (clipIdx > 0 && clipFrames > 0) {
                        L.i(L.TAG_AUDIO, "wav inject: clip #%d done frames=%d pcm_bytes=%d",
                            clipIdx, clipFrames, clipBytes);
                        clipFrames = 0;
                    }
                } else if (!captureStarted() && engine.isStarted()) {
                    capture.start();
                }
                break;
            case IDLE:
                injector.stop();
                capture.stop();
                player.drop();
                break;
            default: break;
        }
        if (listener != null) listener.onStatus(status);
    }

    private boolean captureStarted;
    private boolean captureStarted() { return captureStarted; }

    // Debug wav-inject observability: per-clip (AudioOp START..END) counters.
    private int clipIdx;
    private int clipFrames;
    private long clipBytes;

    // Debug wav-inject: AEC watermark loopback state.
    private static final long WM_TIMEOUT_MS = 4_000;
    private final android.os.Handler mainHandler =
        new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean wmActive;
    private boolean wmDone;
    private boolean wmReceiving;
    private final java.io.ByteArrayOutputStream wmBuf = new java.io.ByteArrayOutputStream();

    private final Runnable wmTimeout = this::onWmTimeout;

    private void onWmTimeout() {
        if (wmActive && !wmDone) {
            wmDone = true;
            L.i(L.TAG_AUDIO, "wav inject: no watermark within %d ms, injecting directly",
                WM_TIMEOUT_MS);
            injector.start();
        }
    }

    /** Echo the buffered watermark clip back upstream (G.711A FRAMEs). */
    private void echoWatermark() {
        byte[] pcm = wmBuf.toByteArray();
        int frames = 0;
        byte[] alaw = new byte[160];
        for (int off = 0; off + 320 <= pcm.length && engine.isStarted(); off += 320) {
            int n = G711A.encode(pcm, off, 320, alaw, 0);
            byte[] f = new byte[n];
            System.arraycopy(alaw, 0, f, 0, n);
            engine.sendAudio(f);
            frames++;
        }
        L.i(L.TAG_AUDIO, "wav inject: watermark echoed (%d pcm bytes, %d frames)",
            pcm.length, frames);
    }

    @Override public void onAudioData(@NonNull byte[] pcm, int pcmLen) {
        if (wmReceiving) {
            wmBuf.write(pcm, 0, pcmLen);
        } else {
            clipFrames++;
            clipBytes += pcmLen;
        }
        if (player != null) player.offerPcm(pcm, pcmLen);
    }

    @Override public void onMessage(@NonNull ServerMessage msg) {
        if (wavInjectEnabled() && !msg.isFunctionCall() && msg.body != null) {
            String text = msg.body.optString("text", "");
            if (text.isEmpty()) text = msg.body.optString("delta", "");
            if (!text.isEmpty()) L.i(L.TAG_AUDIO, "wav inject: recv text: %s", text);
        }
        if (listener != null) listener.onServerMessage(msg);
    }
}
