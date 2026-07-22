package com.anomaly.goldiesettings.convai.proto;

/**
 * Mirrors {@code cloud_gateway::AudioOp} from
 * {@code D:\vit\apkdemo\cloud_gateway\include\cloud_gateway\protocol.hpp}.
 *
 * <p>The first byte of every binary frame on the wire.
 */
public enum AudioOp {
    FRAME((byte) 0x10),   // one 20ms G.711A frame
    START((byte) 0x11),   // VAD begin
    END((byte) 0x12),     // VAD end (trigger ASR/LLM/TTS)
    CANCEL((byte) 0x13);  // user barge-in

    public final byte code;
    AudioOp(byte code) { this.code = code; }

    public static AudioOp fromByte(byte b) {
        for (AudioOp op : values()) {
            if (op.code == b) return op;
        }
        return null;
    }
}
