package com.anomaly.goldiesettings.convai;

/**
 * Mirrors the {@code convai_status_e} enum from
 * {@code D:\vit\apkdemo\include\convai\convai_types.h}.
 */
public enum ConvaiStatus {
    IDLE,
    LISTENING,
    THINKING,
    ANSWERING,
    INTERRUPTED,
    ANSWER_FINISHED;

    public String displayName() {
        switch (this) {
            case IDLE: return "空闲";
            case LISTENING: return "倾听中";
            case THINKING: return "思考中";
            case ANSWERING: return "回答中";
            case INTERRUPTED: return "已打断";
            case ANSWER_FINISHED: return "回答完毕";
        }
        return name();
    }
}
