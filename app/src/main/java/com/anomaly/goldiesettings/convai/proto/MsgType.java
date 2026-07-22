package com.anomaly.goldiesettings.convai.proto;

/**
 * Mirrors {@code cloud_gateway::MsgType} from {@code D:\vit\apkdemo\cloud_gateway\include\cloud_gateway\protocol.hpp}.
 *
 * <p>String forms are sent on the wire as the {@code type} field of the JSON envelope.
 */
public enum MsgType {
    HELLO("hello"),
    HELLO_ACK("hello_ack"),
    HELLO_ERR("hello_err"),
    BYE("bye"),
    PING("ping"),
    PONG("pong"),
    STATUS("status"),
    EVENT("event"),
    TEXT("text"),
    TEXT_DELTA("text_delta"),
    CONFIG_UPDATE("config_update"),
    CONFIG_UPDATE_ACK("config_update_ack"),
    CONFIG_UPDATE_ERR("config_update_err"),
    FUNCTION_CALL("function_call"),
    FUNCTION_CALL_OUTPUT("function_call_output"),
    ERROR("error"),
    ACK("ack");

    private final String wire;
    MsgType(String wire) { this.wire = wire; }
    public String wire() { return wire; }

    public static MsgType fromWire(String s) {
        if (s == null) return null;
        for (MsgType t : values()) {
            if (t.wire.equals(s)) return t;
        }
        return null;
    }
}
