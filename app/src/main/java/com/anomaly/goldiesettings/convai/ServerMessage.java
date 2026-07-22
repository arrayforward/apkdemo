package com.anomaly.goldiesettings.convai;

import org.json.JSONObject;

/**
 * Server-pushed message that the engine routes to the application.
 *
 * Either a parsed JSON envelope (text frames) or a function-call descriptor
 * parsed out of the envelope's body.
 */
public final class ServerMessage {
    /** raw JSON text (for the function-call auto-reply path) */
    public final String raw;
    /** parsed envelope body, may be null */
    public final JSONObject body;
    /** non-null when this message is a function_call (emotion etc.) */
    public final String functionCallId;
    public final String functionName;
    public final JSONObject functionArgs;

    private ServerMessage(String raw, JSONObject body, String fid, String fname, JSONObject fargs) {
        this.raw = raw;
        this.body = body;
        this.functionCallId = fid;
        this.functionName = fname;
        this.functionArgs = fargs;
    }

    public static ServerMessage rawJson(String raw, JSONObject body) {
        return new ServerMessage(raw, body, null, null, null);
    }

    public static ServerMessage functionCall(String raw, JSONObject body, String callId, String name, JSONObject args) {
        return new ServerMessage(raw, body, callId, name, args);
    }

    public boolean isFunctionCall() { return functionCallId != null; }
}
