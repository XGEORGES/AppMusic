package com.aura.music.data.gemini

import com.aura.music.core.database.entity.SongEntity

data class DjTrackItem(
    val title: String,
    val artist: String
)

data class DjMixResponse(
    val djShoutout: String,
    val djComment: String,
    val vibeTag: String,
    val tracks: List<DjTrackItem>
)

data class DjSessionContext(
    var originalPrompt: String = "",
    val playedSongs: MutableList<String> = mutableListOf(),
    val skippedSongs: MutableList<String> = mutableListOf()
) {
    fun recordPlayed(songTitleWithArtist: String) {
        if (!playedSongs.contains(songTitleWithArtist)) {
            playedSongs.add(songTitleWithArtist)
        }
    }

    fun recordSkipped(songTitleWithArtist: String) {
        if (!skippedSongs.contains(songTitleWithArtist)) {
            skippedSongs.add(songTitleWithArtist)
        }
    }

    fun reset(prompt: String) {
        originalPrompt = prompt
        playedSongs.clear()
        skippedSongs.clear()
    }
}

enum class DjAdjustmentType(val label: String, val instruction: String) {
    MORE_ENERGETIC(
        label = "🔥 Más enérgico",
        instruction = "Aumenta la energía, el ritmo y el BPM al máximo con beats intensos, distorsión o percusión de alto impacto."
    ),
    SOFTER(
        label = "🍃 Más suave",
        instruction = "Baja las revoluciones, haz la mezcla más acústica, tranquila, atmosférica o relajante."
    ),
    SURPRISE_ME(
        label = "✨ Sorpréndeme",
        instruction = "Giro inesperado: cambia a un subgénero alternativo manteniendo la vibra de la actividad original de forma creativa."
    )
}

sealed interface DjAuraUiState {
    data object Idle : DjAuraUiState

    data class Loading(
        val message: String = "DJ Aura está preparando la mezcla..."
    ) : DjAuraUiState

    data class ActiveMix(
        val shoutout: String,
        val comment: String,
        val vibeTag: String,
        val songs: List<SongEntity>,
        val promptUsed: String,
        val isLoadingMore: Boolean = false,
        val showAdjustmentOptions: Boolean = false
    ) : DjAuraUiState

    data class Error(
        val message: String,
        val lastPrompt: String = ""
    ) : DjAuraUiState
}
