package com.anomaly.goldiesettings.convai;

import android.content.Context;

import androidx.annotation.NonNull;

import com.anomaly.goldiesettings.convai.audio.AudioCapture;
import com.anomaly.goldiesettings.convai.audio.AudioPlayer;
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
    private Listener listener;

    public interface Listener {
        void onEvent(@NonNull ConvaiEvent e, String details);
        void onStatus(@NonNull ConvaiStatus s);
        void onServerMessage(@NonNull ServerMessage m);
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
        capture.start();
    }

    public void stop() {
        L.i(L.TAG_BRIDGE, "stop");
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
                break;
            case INTERRUPTED:
                if (player != null) player.drop();
                // also signal server to drop buffered audio
                engine.sendAudioCancel();
                break;
            case LISTENING:
                if (!captureStarted() && engine.isStarted()) capture.start();
                break;
            case IDLE:
                capture.stop();
                player.drop();
                break;
            default: break;
        }
        if (listener != null) listener.onStatus(status);
    }

    private boolean captureStarted;
    private boolean captureStarted() { return captureStarted; }

    @Override public void onAudioData(@NonNull byte[] pcm, int pcmLen) {
        if (player != null) player.offerPcm(pcm, pcmLen);
    }

    @Override public void onMessage(@NonNull ServerMessage msg) {
        if (listener != null) listener.onServerMessage(msg);
    }
}
