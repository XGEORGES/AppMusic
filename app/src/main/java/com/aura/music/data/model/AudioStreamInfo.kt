package com.aura.music.data.model

data class AudioStreamInfo(
    val url: String,
    val format: String,
    val bitrate: Int,
    val durationSeconds: Long = 0
)
