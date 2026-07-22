package com.anomaly.goldiesettings.convai;

/**
 * Mirrors the {@code convai_event_code_e} enum from
 * {@code D:\vit\apkdemo\include\convai\convai_event.h}.
 */
public enum ConvaiEvent {
    CONNECTED,
    DISCONNECTED,
    FAILED,
    UPDATED;

    public static ConvaiEvent fromWire(String s) {
        if (s == null) return null;
        switch (s) {
            case "connected": return CONNECTED;
            case "disconnected": return DISCONNECTED;
            case "failed": return FAILED;
            case "updated": return UPDATED;
        }
        return null;
    }
}
