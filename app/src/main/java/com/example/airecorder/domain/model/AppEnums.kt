package com.example.airecorder.domain.model

enum class RecorderState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED_AWAITING_NAME,
    SAVING,
    ERROR,
}

enum class TranscriptStatus {
    NOT_STARTED,
    PROCESSING,
    COMPLETED,
    FAILED,
}

enum class SummaryStatus {
    NOT_STARTED,
    PROCESSING,
    COMPLETED,
    FAILED,
}

enum class SummaryType {
    CONCISE,
    KEY_POINTS,
    ACTION_ITEMS,
}

enum class RecordingMode {
    MIC,
    PLAYBACK_CAPTURE,
}
