package com.anomaly.goldiesettings.convai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.anomaly.goldiesettings.convai.proto.AudioOp;
import com.anomaly.goldiesettings.convai.proto.Envelope;
import com.anomaly.goldiesettings.util.L;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Low-level WebSocket transport for the convai.v1 protocol.
 *
 * <p>Wraps an OkHttp {@link WebSocket} and routes:
 * <ul>
 *   <li>JSON text frames (parsed into {@link Envelope}) to the engine.</li>
 *   <li>Binary frames (13-byte audio header + G.711A) to the engine.</li>
 *   <li>Connect/close/failure to the engine.</li>
 * </ul>
 *
 * <p>OkHttp handles TCP, TLS, RFC 6455 framing, masking, fragmentation,
 * pings/pongs and heartbeats; we only deal with the convai.v1 application
 * protocol on top.
 */
public class ConvaiClient {

    public interface Listener {
        /** WS handshake completed. */
        void onOpen();
        /** WS closed by peer or by us. */
        void onClosed(int code, @NonNull String reason);
        /** WS connection failed (handshake error, network error). */
        void onFailure(@NonNull Throwable t, @Nullable String responseBody);
        /** A text envelope arrived. */
        void onText(@NonNull Envelope env);
        /** A binary audio frame arrived (header already parsed). */
        void onBinaryAudio(@NonNull Envelope.AudioHeader header, @NonNull byte[] g711a, int g711aLen);
    }

    /** WebSocket sub-protocol requested during the handshake. */
    public static final String SUBPROTOCOL = "convai.v1";

    private final OkHttpClient http;
    private WebSocket ws;
    private final AtomicLong txSeq = new AtomicLong(1);
    private volatile Listener listener;

    public ConvaiClient() {
        this.http = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)   // WS is long-lived
            .connectTimeout(15, TimeUnit.SECONDS)
            .build();
    }

    public void setListener(Listener l) { this.listener = l; }

    /** Open the WS. {@code wsUrl} is e.g. {@code ws://192.168.1.10:9000/}. */
    public void connect(@NonNull String wsUrl) {
        disconnect(-1, "reconnect");
        Request req = new Request.Builder()
            .url(wsUrl)
            .header("Sec-WebSocket-Protocol", SUBPROTOCOL)
            .build();
        L.i(L.TAG_WS, "connecting to %s (subproto=%s)", wsUrl, SUBPROTOCOL);
        ws = http.newWebSocket(req, new WsListener());
    }

    public void disconnect(int code, @NonNull String reason) {
        WebSocket w = ws;
        ws = null;
        if (w != null) {
            try {
                w.close(code < 0 ? 1000 : code, reason);
            } catch (Throwable t) {
                L.w(L.TAG_WS, "close error: %s", t.getMessage());
            }
        }
    }

    public boolean isOpen() {
        return ws != null;
    }

    public long nextSeq() { return txSeq.getAndIncrement(); }

    // ---------------------------------------------------------------------
    //  Send helpers
    // ---------------------------------------------------------------------

    /** Send a JSON envelope (text frame). */
    public boolean sendText(@NonNull Envelope env) {
        WebSocket w = ws;
        if (w == null) return false;
        try {
            String json = env.toJson();
            L.d(L.TAG_WS, "→ %s", json);
            return w.send(json);
        } catch (JSONException e) {
            L.e(L.TAG_WS, "envelope json error: %s", e.getMessage());
            return false;
        }
    }

    /**
     * Send an audio frame (binary, 13-byte header + G.711A).
     * @param op audio op (Frame / Start / End / Cancel)
     * @param g711a 8 kHz mono A-law bytes
     */
    public boolean sendAudio(@NonNull AudioOp op, @NonNull byte[] g711a) {
        return sendAudio(op, g711a, 0, g711a.length);
    }

    public boolean sendAudio(@NonNull AudioOp op, @NonNull byte[] g711a, int off, int len) {
        WebSocket w = ws;
        if (w == null) return false;
        ByteBuffer bb = ByteBuffer.allocate(13 + len).order(ByteOrder.BIG_ENDIAN);
        bb.put(op.code);
        bb.putInt((int) (nextSeq() & 0xFFFFFFFFL));
        bb.putLong(System.currentTimeMillis());
        bb.put(g711a, off, len);
        ByteString payload = ByteString.of(bb.array());
        return w.send(payload);
    }

    /** Convenience: send a {@code hello} envelope. */
    public boolean sendHello(JSONObject body) {
        try {
            Envelope env = new Envelope(
                com.anomaly.goldiesettings.convai.proto.MsgType.HELLO,
                nextSeq(),
                System.currentTimeMillis(),
                body != null ? body : new JSONObject()
            );
            return sendText(env);
        } catch (Throwable t) {
            L.e(L.TAG_WS, "sendHello: %s", t.getMessage());
            return false;
        }
    }

    public boolean sendConfigUpdate(JSONObject params) {
        try {
            Envelope env = new Envelope(
                com.anomaly.goldiesettings.convai.proto.MsgType.CONFIG_UPDATE,
                nextSeq(),
                System.currentTimeMillis(),
                params != null ? params : new JSONObject()
            );
            return sendText(env);
        } catch (Throwable t) {
            L.e(L.TAG_WS, "sendConfigUpdate: %s", t.getMessage());
            return false;
        }
    }

    public boolean sendFunctionCallOutput(String callId, String outputJson) {
        try {
            JSONObject item = new JSONObject()
                .put("type", "function_call_output")
                .put("call_id", callId)
                .put("output", outputJson);
            org.json.JSONArray items = new org.json.JSONArray().put(item);
            JSONObject body = new JSONObject().put("items", items);
            Envelope env = new Envelope(
                com.anomaly.goldiesettings.convai.proto.MsgType.FUNCTION_CALL_OUTPUT,
                nextSeq(),
                System.currentTimeMillis(),
                body
            );
            return sendText(env);
        } catch (Throwable t) {
            L.e(L.TAG_WS, "sendFunctionCallOutput: %s", t.getMessage());
            return false;
        }
    }

    public boolean sendBye() {
        try {
            Envelope env = new Envelope(
                com.anomaly.goldiesettings.convai.proto.MsgType.BYE,
                nextSeq(),
                System.currentTimeMillis(),
                new JSONObject()
            );
            return sendText(env);
        } catch (Throwable t) {
            L.e(L.TAG_WS, "sendBye: %s", t.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------------
    //  OkHttp listener
    // ---------------------------------------------------------------------

    private class WsListener extends WebSocketListener {
        @Override public void onOpen(WebSocket webSocket, Response response) {
            L.i(L.TAG_WS, "WS open: %s", response.message());
            Listener l = listener; if (l != null) l.onOpen();
        }

        @Override public void onMessage(WebSocket webSocket, String text) {
            L.d(L.TAG_WS, "← %s", text);
            try {
                Envelope env = Envelope.fromJson(text);
                Listener l = listener; if (l != null) l.onText(env);
            } catch (JSONException e) {
                L.w(L.TAG_WS, "bad json: %s", e.getMessage());
            }
        }

        @Override public void onMessage(WebSocket webSocket, ByteString bytes) {
            byte[] data = bytes.toByteArray();
            Envelope.AudioHeader h = Envelope.parseAudioHeader(data, data.length);
            if (h == null) {
                L.w(L.TAG_WS, "short audio frame (%d bytes)", data.length);
                return;
            }
            // Strip the 13-byte mini-header: the sink expects pure G.711A.
            int off = 13;
            int len = data.length - off;
            byte[] payload = new byte[len];
            System.arraycopy(data, off, payload, 0, len);
            Listener l = listener;
            if (l != null) l.onBinaryAudio(h, payload, payload.length);
        }

        @Override public void onClosing(WebSocket webSocket, int code, String reason) {
            L.i(L.TAG_WS, "WS closing: %d %s", code, reason);
            webSocket.close(code, reason);
        }

        @Override public void onClosed(WebSocket webSocket, int code, String reason) {
            L.i(L.TAG_WS, "WS closed: %d %s", code, reason);
            Listener l = listener; if (l != null) l.onClosed(code, reason);
        }

        @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            String body = null;
            int code = -1;
            if (response != null) {
                code = response.code();
                try { body = response.peekBody(2048).string(); } catch (Throwable ignored) {}
            }
            L.e(L.TAG_WS, "WS failure: %s (code=%d body=%s)", t.getMessage(), code, body);
            Listener l = listener; if (l != null) l.onFailure(t, body);
        }
    }
}
