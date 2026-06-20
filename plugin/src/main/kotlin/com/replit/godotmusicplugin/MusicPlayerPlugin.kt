package com.replit.godotmusicplugin

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import org.godotengine.godot.Godot
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

/**
 * GodotMusicPlugin - Plugin nativo Android para Godot 4.x
 *
 * Expõe ao GDScript:
 *  - Escaneamento de arquivos de áudio (MP3, FLAC, OGG, AAC, WAV, OPUS)
 *  - Leitura de metadados (título, artista, álbum, duração, capa)
 *  - Controle de reprodução com fila ilimitada
 *  - Notificação e tela de bloqueio via MediaSession
 *  - Widget redimensionável na tela inicial
 */
class MusicPlayerPlugin(godot: Godot) : GodotPlugin(godot) {

    companion object {
        const val TAG = "GodotMusicPlugin"
    }

    private var musicService: MusicPlaybackService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MusicPlaybackService.LocalBinder
            musicService = localBinder.getService()
            serviceBound = true
            musicService?.setPluginCallback(pluginCallback)
            Log.d(TAG, "MusicPlaybackService conectado")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            musicService = null
            Log.d(TAG, "MusicPlaybackService desconectado")
        }
    }

    private val pluginCallback = object : MusicPlaybackService.PluginCallback {
        override fun onTrackChanged(index: Int, title: String, artist: String, album: String, duration: Long) {
            emitSignal("track_changed", index, title, artist, album, duration)
        }

        override fun onPlaybackStateChanged(isPlaying: Boolean, position: Long) {
            emitSignal("playback_state_changed", isPlaying, position)
        }

        override fun onPlaybackCompleted() {
            emitSignal("playback_completed")
        }

        override fun onError(message: String) {
            emitSignal("playback_error", message)
        }
    }

    override fun getPluginName(): String = "GodotMusicPlugin"

    override fun getPluginSignals(): Set<SignalInfo> = setOf(
        SignalInfo("track_changed", Int::class.java, String::class.java, String::class.java, String::class.java, Long::class.java),
        SignalInfo("playback_state_changed", Boolean::class.java, Long::class.java),
        SignalInfo("playback_completed"),
        SignalInfo("playback_error", String::class.java),
        SignalInfo("scan_completed", Int::class.java)
    )

    override fun onMainCreate(activity: android.app.Activity): android.view.View? {
        startAndBindService()
        return null
    }

    override fun onMainDestroy() {
        if (serviceBound) {
            activity?.unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun startAndBindService() {
        val ctx = activity ?: return
        val intent = Intent(ctx, MusicPlaybackService::class.java)
        ctx.startForegroundService(intent)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // ─────────────────────────────────────────────
    // Métodos expostos ao GDScript
    // ─────────────────────────────────────────────

    /** Escaneia o armazenamento e retorna lista de músicas como Array de Dicts */
    @UsedByGodot
    fun scanAudioFiles(): org.godotengine.godot.Dictionary {
        val ctx = activity ?: return org.godotengine.godot.Dictionary()
        val scanner = AudioScanner(ctx)
        val songs = scanner.scanAll()
        val result = org.godotengine.godot.Dictionary()
        result["count"] = songs.size
        val array = Array(songs.size) { i ->
            val d = org.godotengine.godot.Dictionary()
            d["id"] = songs[i].id
            d["title"] = songs[i].title
            d["artist"] = songs[i].artist
            d["album"] = songs[i].album
            d["duration"] = songs[i].duration
            d["path"] = songs[i].path
            d["albumArtUri"] = songs[i].albumArtUri
            d
        }
        result["songs"] = array
        emitSignal("scan_completed", songs.size)
        return result
    }

    /** Retorna capa do álbum como bytes (PNG) dado um URI de capa */
    @UsedByGodot
    fun getAlbumArtBytes(albumArtUri: String): ByteArray {
        val ctx = activity ?: return ByteArray(0)
        return AudioScanner(ctx).getAlbumArtBytes(albumArtUri)
    }

    /** Define a fila de reprodução com lista de caminhos de arquivo */
    @UsedByGodot
    fun setQueue(paths: Array<String>) {
        musicService?.setQueue(paths.toList())
    }

    /** Toca o item na posição especificada da fila */
    @UsedByGodot
    fun playAtIndex(index: Int) {
        musicService?.playAtIndex(index)
    }

    /** Reproduz / pausa */
    @UsedByGodot
    fun playPause() {
        musicService?.playPause()
    }

    /** Próxima faixa */
    @UsedByGodot
    fun skipToNext() {
        musicService?.skipToNext()
    }

    /** Faixa anterior */
    @UsedByGodot
    fun skipToPrevious() {
        musicService?.skipToPrevious()
    }

    /** Busca para posição em milissegundos */
    @UsedByGodot
    fun seekTo(positionMs: Long) {
        musicService?.seekTo(positionMs)
    }

    /** Retorna true se estiver reproduzindo */
    @UsedByGodot
    fun isPlaying(): Boolean = musicService?.isPlaying() ?: false

    /** Posição atual em milissegundos */
    @UsedByGodot
    fun getCurrentPosition(): Long = musicService?.getCurrentPosition() ?: 0L

    /** Duração total da faixa atual em milissegundos */
    @UsedByGodot
    fun getDuration(): Long = musicService?.getDuration() ?: 0L

    /** Índice da faixa atual na fila */
    @UsedByGodot
    fun getCurrentIndex(): Int = musicService?.getCurrentIndex() ?: 0

    /** Define modo de repetição: 0=sem repetição, 1=repetir tudo, 2=repetir uma */
    @UsedByGodot
    fun setRepeatMode(mode: Int) {
        musicService?.setRepeatMode(mode)
    }

    /** Ativa/desativa modo aleatório */
    @UsedByGodot
    fun setShuffleMode(enabled: Boolean) {
        musicService?.setShuffleMode(enabled)
    }

    /** Volume: 0.0 a 1.0 */
    @UsedByGodot
    fun setVolume(volume: Float) {
        musicService?.setVolume(volume)
    }

    /** Para a reprodução completamente */
    @UsedByGodot
    fun stop() {
        musicService?.stop()
    }
}
