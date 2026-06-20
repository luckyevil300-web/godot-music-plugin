package com.replit.godotmusicplugin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.os.Handler
import android.os.Looper

/**
 * Serviço de reprodução em foreground com MediaSession.
 * Gerencia ExoPlayer, notificações e tela de bloqueio.
 */
class MusicPlaybackService : Service() {

    companion object {
        const val TAG = "MusicPlaybackService"
        const val NOTIFICATION_CHANNEL_ID = "music_player_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.replit.godotmusicplugin.PLAY_PAUSE"
        const val ACTION_NEXT = "com.replit.godotmusicplugin.NEXT"
        const val ACTION_PREVIOUS = "com.replit.godotmusicplugin.PREVIOUS"
        const val ACTION_STOP = "com.replit.godotmusicplugin.STOP"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }

    interface PluginCallback {
        fun onTrackChanged(index: Int, title: String, artist: String, album: String, duration: Long)
        fun onPlaybackStateChanged(isPlaying: Boolean, position: Long)
        fun onPlaybackCompleted()
        fun onError(message: String)
    }

    private val binder = LocalBinder()
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var callback: PluginCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var queue: List<String> = emptyList()

    private val progressRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            callback?.onPlaybackStateChanged(p.isPlaying, p.currentPosition)
            // Atualiza widget
            MusicWidgetProvider.updateWidgets(
                applicationContext,
                p.isPlaying,
                currentTrackTitle,
                currentTrackArtist,
                null
            )
            handler.postDelayed(this, 1000)
        }
    }

    private var currentTrackTitle = ""
    private var currentTrackArtist = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupPlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playPause()
            ACTION_NEXT -> skipToNext()
            ACTION_PREVIOUS -> skipToPrevious()
            ACTION_STOP -> {
                stop()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        handler.removeCallbacks(progressRunnable)
        mediaSession?.release()
        player?.release()
        player = null
        super.onDestroy()
    }

    fun setPluginCallback(cb: PluginCallback) {
        callback = cb
    }

    // ─────────────────────────────────────────────
    // Configuração do player
    // ─────────────────────────────────────────────

    private fun setupPlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { exoPlayer ->
                exoPlayer.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        callback?.onPlaybackStateChanged(isPlaying, exoPlayer.currentPosition)
                        updateNotification()
                        if (isPlaying) {
                            handler.post(progressRunnable)
                        } else {
                            handler.removeCallbacks(progressRunnable)
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val idx = exoPlayer.currentMediaItemIndex
                        val meta = mediaItem?.mediaMetadata
                        val title = meta?.title?.toString() ?: ""
                        val artist = meta?.artist?.toString() ?: ""
                        val album = meta?.albumTitle?.toString() ?: ""
                        val duration = exoPlayer.duration.coerceAtLeast(0L)
                        currentTrackTitle = title
                        currentTrackArtist = artist
                        callback?.onTrackChanged(idx, title, artist, album, duration)
                        updateNotification()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            callback?.onPlaybackCompleted()
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Log.e(TAG, "Erro de reprodução: ${error.message}")
                        callback?.onError(error.message ?: "Erro desconhecido")
                    }
                })

                mediaSession = MediaSession.Builder(this, exoPlayer).build()
            }
    }

    // ─────────────────────────────────────────────
    // Controles de reprodução
    // ─────────────────────────────────────────────

    fun setQueue(paths: List<String>) {
        queue = paths
        val mediaItems = paths.map { path ->
            MediaItem.fromUri(Uri.parse(if (path.startsWith("/")) "file://$path" else path))
        }
        player?.setMediaItems(mediaItems)
        player?.prepare()
    }

    fun playAtIndex(index: Int) {
        val p = player ?: return
        if (index < 0 || index >= (p.mediaItemCount)) return
        p.seekTo(index, 0L)
        p.play()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    fun playPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else {
            p.play()
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    fun skipToNext() {
        player?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        val p = player ?: return
        if (p.currentPosition > 3000) {
            p.seekTo(0L)
        } else {
            p.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun setRepeatMode(mode: Int) {
        player?.repeatMode = when (mode) {
            1 -> Player.REPEAT_MODE_ALL
            2 -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setShuffleMode(enabled: Boolean) {
        player?.shuffleModeEnabled = enabled
    }

    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    fun stop() {
        handler.removeCallbacks(progressRunnable)
        player?.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    fun isPlaying(): Boolean = player?.isPlaying ?: false
    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L
    fun getDuration(): Long = player?.duration?.coerceAtLeast(0L) ?: 0L
    fun getCurrentIndex(): Int = player?.currentMediaItemIndex ?: 0

    // ─────────────────────────────────────────────
    // Notificação (tela de bloqueio + área de notificações)
    // ─────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Player de Música",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controles de reprodução de música"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun pendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlaybackService::class.java).apply {
            this.action = action
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getService(this, 0, intent, flags)
    }

    private fun buildNotification(): Notification {
        val p = player
        val isPlaying = p?.isPlaying ?: false
        val title = currentTrackTitle.ifEmpty { "Música" }
        val artist = currentTrackArtist.ifEmpty { "" }

        val playPauseIcon = if (isPlaying)
            android.R.drawable.ic_media_pause
        else
            android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Anterior", pendingIntent(ACTION_PREVIOUS))
            .addAction(playPauseIcon, if (isPlaying) "Pausar" else "Reproduzir", pendingIntent(ACTION_PLAY_PAUSE))
            .addAction(android.R.drawable.ic_media_next, "Próximo", pendingIntent(ACTION_NEXT))
            .addAction(android.R.drawable.ic_delete, "Parar", pendingIntent(ACTION_STOP))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession?.sessionCompatToken)
            )
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }
}
