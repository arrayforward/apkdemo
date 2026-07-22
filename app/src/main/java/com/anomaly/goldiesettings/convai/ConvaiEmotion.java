package com.anomaly.goldiesettings.convai;

/** Server-reported emotion, parsed from the {@code emotion} function call. */
public enum ConvaiEmotion {
    NEUTRAL,
    HAPPY,
    ANGRY,
    SAD,
    DOUBT;

    public static ConvaiEmotion fromWire(String s) {
        if (s == null) return NEUTRAL;
        switch (s) {
            case "happy": return HAPPY;
            case "angry": return ANGRY;
            case "sad": return SAD;
            case "doubt": return DOUBT;
            case "neutral":
            default: return NEUTRAL;
        }
    }
}
