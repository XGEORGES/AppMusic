package com.aura.music.service.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aura.music.MainActivity
import com.aura.music.R
import com.aura.music.data.repository.PlayerRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @Inject
    lateinit var audioPlayerManager: AudioPlayerManager

    @Inject
    lateinit var playerRepository: PlayerRepository

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private var notificationManager: NotificationManager? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "george_music_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY     = "com.aura.music.ACTION_PLAY"
        const val ACTION_PAUSE    = "com.aura.music.ACTION_PAUSE"
        const val ACTION_NEXT     = "com.aura.music.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.aura.music.ACTION_PREVIOUS"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        createNotificationChannel()

        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ForwardingPlayer para interceptar comandos de reproducción del sistema
        // (Notificación, lockscreen, auriculares Bluetooth, etc.) y coordinar el estado
        val forwardingPlayer = object : ForwardingPlayer(audioPlayerManager.player) {
            override fun pause() {
                audioPlayerManager.pause()
            }

            override fun play() {
                audioPlayerManager.play()
            }

            override fun setPlayWhenReady(playWhenReady: Boolean) {
                if (!playWhenReady) {
                    audioPlayerManager.pause()
                } else {
                    audioPlayerManager.play()
                }
            }
        }

        // Crear MediaSession para compatibilidad con controles externos
        // (Bluetooth, auriculares, Google Assistant, etc.)
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .build()

        // Listener del player: gestionar locks y notificación manualmente.
        // No usamos DefaultMediaNotificationProvider porque nuestra arquitectura
        // llama AudioPlayerManager directamente (sin MediaController), y el proveedor
        // automático solo se activa cuando hay un MediaController conectado a la sesión.
        audioPlayerManager.player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    acquireLocks()
                } else if (!audioPlayerManager.player.playWhenReady || audioPlayerManager.userInitiatedPause) {
                    // Solo liberar locks cuando el USUARIO pausó explícitamente.
                    releaseLocks()
                }
                updateNotification()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        acquireLocks()
                        updateNotification()
                    }
                    Player.STATE_READY -> {
                        if (audioPlayerManager.player.isPlaying) {
                            acquireLocks()
                        }
                        // No liberar locks en STATE_READY sin reproducción:
                        // puede ser una transición entre canciones donde el audio focus
                        // aún no se ha re-adquirido.
                        updateNotification()
                    }
                    Player.STATE_ENDED -> {
                        // Solo liberar locks si la cola realmente terminó
                        if (!audioPlayerManager.player.hasNextMediaItem() &&
                            audioPlayerManager.userInitiatedPause) {
                            releaseLocks()
                        }
                        updateNotification()
                    }
                    Player.STATE_IDLE -> {
                        if (audioPlayerManager.userInitiatedPause) {
                            releaseLocks()
                        }
                        updateNotification()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Siempre mantener locks durante transiciones de canciones
                acquireLocks()
                updateNotification()
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Manejar acciones explícitas de los botones de la notificación
        when (intent?.action) {
            ACTION_PLAY     -> playerRepository.play()
            ACTION_PAUSE    -> playerRepository.pause()
            ACTION_NEXT     -> playerRepository.seekToNext()
            ACTION_PREVIOUS -> playerRepository.seekToPrevious()
        }

        if (audioPlayerManager.player.isPlaying) {
            acquireLocks()
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = audioPlayerManager.player
        if (!player.isPlaying && player.playbackState != Player.STATE_BUFFERING) {
            releaseLocks()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val player = session.player
        val song = audioPlayerManager.currentSong.value

        if (song == null && player.mediaItemCount == 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            return
        }

        val notification = buildNotification()
        try {
            if (startInForegroundRequired) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // ForegroundServiceStartNotAllowedException en Android 12+
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    // ─── Notificación multimedia ───────────────────────────────────────────────

    private fun updateNotification() {
        val player = audioPlayerManager.player
        val song = audioPlayerManager.currentSong.value

        // Solo mostrar notificación si hay alguna canción cargada
        if (song == null && player.mediaItemCount == 0) return

        val notification = buildNotification()
        val isActivelyPlaying = player.isPlaying || player.playbackState == Player.STATE_BUFFERING
        // Mantener foreground durante transiciones del sistema (no pausadas por el usuario)
        // para evitar que MIUI/SmartPower baje la prioridad del proceso
        val isPausedByUser = audioPlayerManager.userInitiatedPause || !player.playWhenReady
        val keepForeground = isActivelyPlaying || !isPausedByUser

        try {
            if (keepForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                // El usuario pausó explícitamente: desanclar del foreground
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // ForegroundServiceStartNotAllowedException en Android 12+:
            // startForeground() no está permitido desde background.
            // Actualizar solo la notificación sin promover a foreground.
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val player = audioPlayerManager.player
        val song = audioPlayerManager.currentSong.value
        val isPlaying = player.isPlaying

        val title  = song?.title      ?: "Reproduciendo"
        val artist = song?.artistName ?: ""

        // PendingIntents para cada acción del controlador
        val playIntent    = Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY }
        val pauseIntent   = Intent(this, PlaybackService::class.java).apply { action = ACTION_PAUSE }
        val nextIntent    = Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT }
        val prevIntent    = Intent(this, PlaybackService::class.java).apply { action = ACTION_PREVIOUS }

        // Renombrado a 'piFlags' para no colisionar con Intent.flags dentro de los bloques apply
        val piFlags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        val playPendingIntent  = PendingIntent.getService(this, 1, playIntent,  piFlags)
        val pausePendingIntent = PendingIntent.getService(this, 2, pauseIntent, piFlags)
        val nextPendingIntent  = PendingIntent.getService(this, 3, nextIntent,  piFlags)
        val prevPendingIntent  = PendingIntent.getService(this, 4, prevIntent,  piFlags)

        // Intent para abrir la app al tocar la notificación
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Iconos de Media3 (incluidos en androidx.media3.ui)
        val playPauseIcon  = if (isPlaying) androidx.media3.ui.R.drawable.exo_notification_pause
                             else           androidx.media3.ui.R.drawable.exo_notification_play
        val playPauseLabel  = if (isPlaying) "Pausar" else "Reproducir"
        val playPausePendingIntent = if (isPlaying) pausePendingIntent else playPendingIntent

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(artist)
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setSilent(true)
            .setShowWhen(false)
            // Anterior (0)
            .addAction(androidx.media3.ui.R.drawable.exo_notification_previous, "Anterior", prevPendingIntent)
            // Play / Pause (1)
            .addAction(playPauseIcon, playPauseLabel, playPausePendingIntent)
            // Siguiente (2)
            .addAction(androidx.media3.ui.R.drawable.exo_notification_next, "Siguiente", nextPendingIntent)

        // Usar MediaStyleNotificationHelper.MediaStyle de Media3 (no requiere la librería compat legacy).
        // Conecta la notificación con la MediaSession para lock screen y widget multimedia del sistema.
        val session = mediaSession
        if (session != null) {
            builder.setStyle(
                androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        return builder.build()
    }

    // ─── WakeLocks ────────────────────────────────────────────────────────────

    private fun acquireLocks() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GeorgeMusic:PlaybackWakeLock")
        }
        // Siempre renovar el timeout al adquirir (cubre sesiones largas de DJ Aura)
        wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 horas de tiempo límite de seguridad

        if (wifiLock?.isHeld != true) {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "GeorgeMusic:PlaybackWifiLock")
            wifiLock?.acquire()
        }
    }

    private fun releaseLocks() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) {}
    }

    // ─── Canal de notificaciones ──────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Reproducción de música continua"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        releaseLocks()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
