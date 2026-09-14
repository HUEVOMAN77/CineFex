package com.pageapp.cinefex.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

object PlayerManager {

    fun createExoPlayerWithSpanishPreference(context: Context): ExoPlayer {
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage("es")
                    .setPreferredAudioLanguages("es", "es-419", "spa")
            )
        }
        return ExoPlayer.Builder(context).setTrackSelector(trackSelector).build()
    }

    fun checkForSpanishAudio(player: ExoPlayer): Boolean {
        val tracks: Tracks = player.currentTracks
        return tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_AUDIO &&
                    (0 until group.mediaTrackGroup.length).any { i ->
                        group.getTrackFormat(i).language?.lowercase()?.let { lang ->
                            lang.startsWith("es") || lang.startsWith("spa")
                        } == true
                    }
        }
    }
}
