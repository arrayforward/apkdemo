package com.anomaly.goldiesettings.convai.proto;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * One envelope (text frame) sent on the wire. Mirrors the format defined in
 * {@code D:\vit\apkdemo\cloud_gateway\include\cloud_gateway\protocol.hpp}.
 *
 * <pre>
 * {
 *   "type": "&lt;MsgType&gt;",
 *   "seq":  &lt;uint32&gt;,
 *   "ts":   &lt;uint64&gt;,    // ms since unix epoch
 *   "body": { ... }        // type-specific
 * }
 * </pre>
 */
public final class Envelope {
    public MsgType type;
    public long seq;
    public long ts;            // ms since epoch
    public JSONObject body;    // never null (use empty object)

    public Envelope() {
        this.body = new JSONObject();
    }

    public Envelope(MsgType type, long seq, long ts, JSONObject body) {
        this.type = type;
        this.seq = seq;
        this.ts = ts;
        this.body = body != null ? body : new JSONObject();
    }

    public String toJson() throws JSONException {
        return new JSONObject()
            .put("type", type.wire())
            .put("seq", seq)
            .put("ts", ts)
            .put("body", body)
            .toString();
    }

    public static Envelope fromJson(String s) throws JSONException {
        JSONObject o = new JSONObject(s);
        Envelope e = new Envelope();
        e.type = MsgType.fromWire(o.optString("type"));
        e.seq = o.optLong("seq", 0L);
        e.ts = o.optLong("ts", 0L);
        JSONObject b = o.optJSONObject("body");
        e.body = b != null ? b : new JSONObject();
        return e;
    }

    /**
     * Build a JSON object for a binary audio frame body. The 13-byte mini-header
     * is built separately and prepended to the G.711A payload in
     * {@link com.anomaly.goldiesettings.convai.ConvaiClient}.
     */
    public static byte[] buildAudioHeader(AudioOp op, long seq, long ts) {
        ByteBuffer bb = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN);
        bb.put(op.code);
        bb.putInt((int) (seq & 0xFFFFFFFFL));
        bb.putLong(ts);
        return bb.array();
    }

    /** Parse the 13-byte header from a binary frame; returns null on too-short data. */
    public static AudioHeader parseAudioHeader(byte[] data, int len) {
        if (data == null || len < 13) return null;
        ByteBuffer bb = ByteBuffer.wrap(data, 0, 13).order(ByteOrder.BIG_ENDIAN);
        AudioOp op = AudioOp.fromByte(bb.get());
        long seq = bb.getInt() & 0xFFFFFFFFL;
        long ts = bb.getLong();
        return new AudioHeader(op, seq, ts);
    }

    public static final class AudioHeader {
        public final AudioOp op;
        public final long seq;
        public final long ts;
        public AudioHeader(AudioOp op, long seq, long ts) {
            this.op = op; this.seq = seq; this.ts = ts;
        }
    }
}
