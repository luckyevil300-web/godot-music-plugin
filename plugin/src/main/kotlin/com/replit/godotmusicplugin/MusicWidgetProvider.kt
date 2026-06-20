package com.replit.godotmusicplugin

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.widget.RemoteViews

/**
 * Widget redimensionável para a tela inicial.
 * Suporta 3 tamanhos: pequeno (2x1), médio (4x1), grande (4x2).
 * Funciona como os widgets do Samsung Music.
 */
class MusicWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.replit.godotmusicplugin.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.replit.godotmusicplugin.WIDGET_NEXT"
        const val ACTION_WIDGET_PREVIOUS = "com.replit.godotmusicplugin.WIDGET_PREVIOUS"

        // Estado global para o widget (atualizado pelo serviço)
        @Volatile var currentTitle: String = "Nenhuma música"
        @Volatile var currentArtist: String = ""
        @Volatile var isPlaying: Boolean = false
        @Volatile var albumArt: Bitmap? = null

        fun updateWidgets(
            context: Context,
            playing: Boolean,
            title: String,
            artist: String,
            art: Bitmap?
        ) {
            isPlaying = playing
            currentTitle = title
            currentArtist = artist
            albumArt = art

            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, MusicWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val options = manager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

            val views = when {
                minHeight >= 110 -> buildLargeViews(context)   // 4x2
                minWidth >= 220  -> buildMediumViews(context)  // 4x1
                else             -> buildSmallViews(context)   // 2x1
            }

            manager.updateAppWidget(widgetId, views)
        }

        private fun buildSmallViews(context: Context): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_small).apply {
                setTextViewText(R.id.widget_title, currentTitle)
                val playIcon = if (isPlaying) android.R.drawable.ic_media_pause
                               else android.R.drawable.ic_media_play
                setImageViewResource(R.id.widget_play_pause, playIcon)
                albumArt?.let { setImageViewBitmap(R.id.widget_album_art, it) }

                setOnClickPendingIntent(R.id.widget_play_pause, widgetPendingIntent(context, ACTION_WIDGET_PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, widgetPendingIntent(context, ACTION_WIDGET_NEXT))
            }
        }

        private fun buildMediumViews(context: Context): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_medium).apply {
                setTextViewText(R.id.widget_title, currentTitle)
                setTextViewText(R.id.widget_artist, currentArtist)
                val playIcon = if (isPlaying) android.R.drawable.ic_media_pause
                               else android.R.drawable.ic_media_play
                setImageViewResource(R.id.widget_play_pause, playIcon)
                albumArt?.let { setImageViewBitmap(R.id.widget_album_art, it) }

                setOnClickPendingIntent(R.id.widget_previous, widgetPendingIntent(context, ACTION_WIDGET_PREVIOUS))
                setOnClickPendingIntent(R.id.widget_play_pause, widgetPendingIntent(context, ACTION_WIDGET_PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, widgetPendingIntent(context, ACTION_WIDGET_NEXT))
            }
        }

        private fun buildLargeViews(context: Context): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_large).apply {
                setTextViewText(R.id.widget_title, currentTitle)
                setTextViewText(R.id.widget_artist, currentArtist)
                val playIcon = if (isPlaying) android.R.drawable.ic_media_pause
                               else android.R.drawable.ic_media_play
                setImageViewResource(R.id.widget_play_pause, playIcon)
                albumArt?.let { setImageViewBitmap(R.id.widget_album_art, it) }

                setOnClickPendingIntent(R.id.widget_previous, widgetPendingIntent(context, ACTION_WIDGET_PREVIOUS))
                setOnClickPendingIntent(R.id.widget_play_pause, widgetPendingIntent(context, ACTION_WIDGET_PLAY_PAUSE))
                setOnClickPendingIntent(R.id.widget_next, widgetPendingIntent(context, ACTION_WIDGET_NEXT))
            }
        }

        private fun widgetPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, MusicWidgetProvider::class.java).apply {
                this.action = action
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
            return PendingIntent.getBroadcast(context, action.hashCode(), intent, flags)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        // Redesenha ao redimensionar
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_PLAY_PAUSE
                }
                context.startService(serviceIntent)
            }
            ACTION_WIDGET_NEXT -> {
                val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_NEXT
                }
                context.startService(serviceIntent)
            }
            ACTION_WIDGET_PREVIOUS -> {
                val serviceIntent = Intent(context, MusicPlaybackService::class.java).apply {
                    action = MusicPlaybackService.ACTION_PREVIOUS
                }
                context.startService(serviceIntent)
            }
        }
    }
}
