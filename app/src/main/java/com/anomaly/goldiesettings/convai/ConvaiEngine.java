package com.anomaly.goldiesettings.convai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.anomaly.goldiesettings.convai.codec.G711A;
import com.anomaly.goldiesettings.convai.proto.AudioOp;
import com.anomaly.goldiesettings.convai.proto.Envelope;
import com.anomaly.goldiesettings.convai.proto.MsgType;
import com.anomaly.goldiesettings.util.L;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Engine-level wrapper around {@link ConvaiClient}. Maps the C SDK
 * lifecycle ({@code convai_create / start / stop / update / send_audio / send_message})
 * to the convai.v1 protocol.
 *
 * <p>Also owns the auto-reply path for {@code response.function_call_arguments.done}
 * messages — the cloud pushes a tool invocation and we must echo a
 * {@code function_call_output} back within the same session.
 */
public class ConvaiEngine {

    public interface Handler {
        /** Lifecycle event: connected / disconnected / failed / updated. */
        void onEvent(@NonNull ConvaiEvent event, @Nullable String details);
        /** Conversation status changed. */
        void onStatus(@NonNull ConvaiStatus status);
        /** Incoming audio (PCM16 LE @ 8 kHz mono, decoded from G.711A). */
        void onAudioData(@NonNull byte[] pcm, int pcmLen);
        /** Incoming text message or function call. */
        void onMessage(@NonNull ServerMessage msg);
    }

    private final ConvaiClient client;
    private final EngineDispatch dispatcher;
    private volatile boolean started;
    private volatile ConvaiStatus status = ConvaiStatus.IDLE;
    private volatile String sessionId;
    private volatile int frameMs = 20;
    private volatile String audioCodec = "g711a";

    /** Pending hello params — sent right after WS open. */
    private volatile String pendingAgentId;
    private volatile JSONObject pendingParams;

    private Handler handler;

    public ConvaiEngine() {
        this.client = new ConvaiClient();
        this.dispatcher = new EngineDispatch();
        this.client.setListener(new ConvaiClient.Listener() {
            @Override public void onOpen() {
                sendPendingHello();
                dispatcher.onOpen();
            }
            @Override public void onClosed(int code, @NonNull String reason) {
                started = false;
                setStatus(ConvaiStatus.IDLE);
                dispatcher.onClosed(code, reason);
            }
            @Override public void onFailure(@NonNull Throwable t, @Nullable String body) {
                started = false;
                setStatus(ConvaiStatus.IDLE);
                dispatcher.onFailure(t, body);
            }
            @Override public void onText(@NonNull Envelope env) { dispatcher.onText(env); }
            @Override public void onBinaryAudio(@NonNull Envelope.AudioHeader h,
                                                 @NonNull byte[] g711a, int g711aLen) {
                dispatcher.onBinaryAudio(h, g711a, g711aLen);
            }
        });
    }

    public void setHandler(Handler h) { this.handler = h; }
    public Handler getHandler() { return handler; }
    public ConvaiStatus status() { return status; }
    public boolean isStarted() { return started; }
    public ConvaiClient client() { return client; }
    public String sessionId() { return sessionId; }

    // ---------------------------------------------------------------------
    //  Lifecycle
    // ---------------------------------------------------------------------

    /**
     * Open the WS and queue a hello to be sent as soon as the handshake completes.
     */
    public void start(@NonNull String wsUrl, @NonNull String agentId, @Nullable JSONObject params) {
        if (started) {
            L.w(L.TAG_BRIDGE, "start: already started");
            return;
        }
        this.pendingAgentId = agentId;
        this.pendingParams = params;
        client.connect(wsUrl);
    }

    private void sendPendingHello() {
        String agentId = pendingAgentId;
        JSONObject params = pendingParams;
        try {
            JSONObject body = new JSONObject()
                .put("product_id", "")
                .put("product_key", "")
                .put("product_secret", "")
                .put("device_name", "android-client")
                .put("audio_codec", 1)            // G.711A
                .put("sample_rate", 8000);
            if (params != null) {
                JSONArray names = params.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String k = names.optString(i);
                        body.put(k, params.opt(k));
                    }
                }
            }
            if (agentId != null) body.put("agent_id", agentId);
            client.sendHello(body);
            L.i(L.TAG_BRIDGE, "hello sent (agent_id=%s)", agentId);
        } catch (JSONException e) {
            L.e(L.TAG_BRIDGE, "build hello: %s", e.getMessage());
        }
    }

    public void stop() {
        if (!started) {
            client.disconnect(1000, "stop");
            return;
        }
        client.sendBye();
        client.disconnect(1000, "stop");
        started = false;
        setStatus(ConvaiStatus.IDLE);
    }

    public boolean update(@NonNull JSONObject params) {
        if (!started) {
            L.w(L.TAG_BRIDGE, "update: engine not started");
            return false;
        }
        return client.sendConfigUpdate(params);
    }

    public boolean sendAudio(@NonNull byte[] g711a) {
        return client.sendAudio(AudioOp.FRAME, g711a);
    }

    public boolean sendAudioStart() { return client.sendAudio(AudioOp.START, new byte[0]); }
    public boolean sendAudioEnd()   { return client.sendAudio(AudioOp.END,   new byte[0]); }
    public boolean sendAudioCancel(){ return client.sendAudio(AudioOp.CANCEL,new byte[0]); }

    // ---------------------------------------------------------------------
    //  Engine-internal dispatch
    // ---------------------------------------------------------------------

    private class EngineDispatch implements ConvaiClient.Listener {
        @Override public void onOpen() {
            // Hello already sent by outer wrapper
        }
        @Override public void onClosed(int code, @NonNull String reason) {
            if (handler != null) handler.onEvent(ConvaiEvent.DISCONNECTED, reason);
        }
        @Override public void onFailure(@NonNull Throwable t, @Nullable String body) {
            if (handler != null) handler.onEvent(ConvaiEvent.FAILED, t != null ? t.getMessage() : null);
        }
        @Override public void onText(@NonNull Envelope env) {
            handleText(env);
        }
        @Override public void onBinaryAudio(@NonNull Envelope.AudioHeader h,
                                             @NonNull byte[] g711a, int g711aLen) {
            if (h.op == AudioOp.FRAME) {
                byte[] pcm = new byte[g711aLen * 2];
                G711A.decode(g711a, 0, g711aLen, pcm, 0);
                if (handler != null) handler.onAudioData(pcm, pcm.length);
            } else if (h.op == AudioOp.START) {
                setStatus(ConvaiStatus.ANSWERING);
            } else if (h.op == AudioOp.END) {
                setStatus(ConvaiStatus.ANSWER_FINISHED);
                setStatus(ConvaiStatus.LISTENING);
            } else if (h.op == AudioOp.CANCEL) {
                setStatus(ConvaiStatus.INTERRUPTED);
            }
        }
    }

    private void handleText(Envelope env) {
        if (env.type == null) return;
        switch (env.type) {
            case HELLO_ACK: {
                JSONObject body = env.body;
                sessionId = body.optString("session_id");
                JSONObject ac = body.optJSONObject("audio_config");
                if (ac != null) {
                    frameMs = ac.optInt("frame_ms", 20);
                    audioCodec = ac.optString("codec", "g711a");
                }
                started = true;
                if (handler != null) handler.onEvent(ConvaiEvent.CONNECTED, sessionId);
                setStatus(ConvaiStatus.LISTENING);
                break;
            }
            case HELLO_ERR: {
                String msg = env.body.optString("message", "auth failed");
                if (handler != null) handler.onEvent(ConvaiEvent.FAILED, msg);
                client.disconnect(4401, msg);
                break;
            }
            case STATUS: {
                String s = env.body.optString("status");
                ConvaiStatus cs = statusFromWire(s);
                if (cs != null) setStatus(cs);
                break;
            }
            case EVENT: {
                String e = env.body.optString("event");
                String details = env.body.optString("details");
                ConvaiEvent ce = ConvaiEvent.fromWire(e);
                if (ce != null && handler != null) handler.onEvent(ce, details);
                break;
            }
            case TEXT:
            case TEXT_DELTA: {
                String raw;
                try { raw = env.toJson(); } catch (JSONException e) { raw = ""; }
                ServerMessage sm = ServerMessage.rawJson(raw, env.body);
                if (handler != null) handler.onMessage(sm);
                break;
            }
            case FUNCTION_CALL: {
                JSONArray calls = env.body.optJSONArray("calls");
                if (calls != null) {
                    for (int i = 0; i < calls.length(); i++) {
                        JSONObject c = calls.optJSONObject(i);
                        if (c == null) continue;
                        String callId = c.optString("call_id");
                        String name = c.optString("name");
                        String argsStr = c.optString("arguments", "{}");
                        JSONObject args;
                        try { args = new JSONObject(argsStr); }
                        catch (JSONException e) { args = new JSONObject(); }
                        // Auto-reply with success
                        client.sendFunctionCallOutput(callId, "{\"result\":\"success\",\"message\":\"成功\"}");
                        // Notify app
                        String raw;
                        try { raw = env.toJson(); } catch (JSONException e) { raw = ""; }
                        if (handler != null) {
                            handler.onMessage(ServerMessage.functionCall(raw, env.body, callId, name, args));
                        }
                    }
                }
                break;
            }
            case CONFIG_UPDATE_ACK:
                if (handler != null) handler.onEvent(ConvaiEvent.UPDATED, "config applied");
                break;
            case CONFIG_UPDATE_ERR:
                L.w(L.TAG_BRIDGE, "config update error: %s", env.body.optString("message"));
                break;
            default: {
                String raw;
                try { raw = env.toJson(); } catch (JSONException e) { raw = ""; }
                ServerMessage sm = ServerMessage.rawJson(raw, env.body);
                if (handler != null) handler.onMessage(sm);
                break;
            }
        }
    }

    private void setStatus(ConvaiStatus s) {
        if (s == status) return;
        status = s;
        if (handler != null) handler.onStatus(s);
    }

    private static ConvaiStatus statusFromWire(String s) {
        if (s == null) return null;
        switch (s) {
            case "idle": return ConvaiStatus.IDLE;
            case "listening": return ConvaiStatus.LISTENING;
            case "thinking": return ConvaiStatus.THINKING;
            case "answering": return ConvaiStatus.ANSWERING;
            case "interrupted": return ConvaiStatus.INTERRUPTED;
            case "answer_finished": return ConvaiStatus.ANSWER_FINISHED;
        }
        return null;
    }

    // ---------------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------------

    public JSONObject buildCreateConfig(String productId, String productKey, String productSecret, String deviceName) {
        try {
            return new JSONObject()
                .put("info", new JSONObject()
                    .put("product_id", productId)
                    .put("product_key", productKey)
                    .put("product_secret", productSecret)
                    .put("device_name", deviceName))
                .put("ws", new JSONObject()
                    .put("audio", new JSONObject()
                        .put("codec", 1)
                        .put("sample_rate", 8000)));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
