package com.tony.explorador

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.common.audio.AudioProcessor
import android.content.Context
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Servicio para que la música siga sonando con la pantalla apagada o la app cerrada. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var restoring = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val saver = object : Runnable {
        override fun run() {
            if (session?.player?.isPlaying == true) savePlayback()
            handler.postDelayed(this, 5000)
        }
    }

    /** Guarda la lista, la canción y el segundo exacto, para seguir ahí aunque se cierre la app. */
    private fun savePlayback() {
        val p = session?.player ?: return
        if (restoring) return
        if (p.mediaItemCount == 0) { LibraryStore.clearPlayback(); return }
        val paths = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }
        LibraryStore.savePlayback(SavedPlayback(paths, p.currentMediaItemIndex, p.currentPosition, p.shuffleModeEnabled, p.repeatMode))
    }

    override fun onCreate() {
        super.onCreate()
        // Copia del audio para el ecualizador (las barras se mueven con la música real)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(TeeAudioProcessor(SpectrumAnalyzer)))
                .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        LibraryStore.init(this)

        // Recuperar lo que estaba sonando la última vez (sin darle play)
        LibraryStore.loadPlayback()?.let { saved ->
            val files = saved.paths.map { java.io.File(it) }.filter { it.isFile }
            if (files.isNotEmpty()) {
                val currentPath = saved.paths.getOrNull(saved.index)
                val idx = files.indexOfFirst { it.absolutePath == currentPath }.coerceAtLeast(0)
                restoring = true
                player.setMediaItems(mediaItemsFor(files), idx, if (files[idx].absolutePath == currentPath) saved.position else 0L)
                player.shuffleModeEnabled = saved.shuffle
                player.repeatMode = saved.repeat
                player.prepare()
                restoring = false
            }
        }

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Llevar la cuenta de las más escuchadas
                val path = mediaItem?.mediaId
                if (!restoring && path != null && path.startsWith("/")) LibraryStore.recordPlay(path)
                savePlayback()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) { savePlayback() }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { savePlayback() }
        })
        handler.postDelayed(saver, 5000)

        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlayback()
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(saver)
        savePlayback()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
