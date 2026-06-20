package com.replit.godotmusicplugin

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import java.io.ByteArrayOutputStream

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val albumArtUri: String
)

class AudioScanner(private val context: Context) {

    companion object {
        const val TAG = "AudioScanner"
        // Formatos suportados pelo ExoPlayer/Media3
        val SUPPORTED_MIME_TYPES = arrayOf(
            "audio/mpeg",           // MP3
            "audio/flac",           // FLAC
            "audio/x-flac",         // FLAC alternativo
            "audio/ogg",            // OGG Vorbis
            "audio/vorbis",
            "audio/mp4",            // M4A / AAC
            "audio/aac",            // AAC
            "audio/x-wav",          // WAV
            "audio/wav",
            "audio/opus",           // OPUS
            "audio/x-ms-wma",       // WMA
            "audio/3gpp",           // 3GP
            "audio/amr-nb",
            "audio/x-matroska"      // MKA
        )
    }

    fun scanAll(): List<AudioTrack> {
        val tracks = mutableListOf<AudioTrack>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                while (c.moveToNext()) {
                    val mime = c.getString(mimeCol) ?: ""
                    // Filtra apenas formatos suportados
                    if (SUPPORTED_MIME_TYPES.none { mime.startsWith(it) } && !mime.startsWith("audio/")) {
                        continue
                    }

                    val id = c.getLong(idCol)
                    val albumId = c.getLong(albumIdCol)
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    tracks.add(
                        AudioTrack(
                            id = id,
                            title = c.getString(titleCol) ?: "Título desconhecido",
                            artist = c.getString(artistCol) ?: "Artista desconhecido",
                            album = c.getString(albumCol) ?: "Álbum desconhecido",
                            duration = c.getLong(durationCol),
                            path = c.getString(dataCol) ?: "",
                            albumArtUri = albumArtUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao escanear áudio: ${e.message}", e)
        }

        Log.d(TAG, "Escaneamento concluído: ${tracks.size} faixas encontradas")
        return tracks
    }

    fun getAlbumArtBytes(albumArtUri: String): ByteArray {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = Uri.parse(albumArtUri)
                val bitmap = context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                bitmapToBytes(bitmap)
            } else {
                val uri = Uri.parse(albumArtUri)
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) bitmapToBytes(bitmap) else ByteArray(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível carregar capa: $albumArtUri - ${e.message}")
            ByteArray(0)
        }
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
