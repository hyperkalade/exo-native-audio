package com.skala.exoaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * Minimal foreground service used to keep audio playback alive when the app
 * is backgrounded or the screen is off. It does not expose media controls
 * and remains persistent only while audio is playing.
 */
class AudioForegroundService : Service() {

    companion object {
        // Use a distinct channel ID tied to the plugin name.  Changing the channel
        // ID after a notification channel has been created will result in
        // creating a new channel; this is fine for development purposes.
        private const val CHANNEL_ID = "ExoNativeAudioChannel"
        private const val NOTIFICATION_ID = 51234

        @Volatile
        private var running = false

        // Hold a wake lock across service lifecycle to prevent the CPU from
        // sleeping while audio is playing.  This is only used on devices
        // where ExoPlayer's wake mode isn't sufficient.
        @Volatile
        private var wakeLock: PowerManager.WakeLock? = null

        /**
         * Start the foreground service if not already running.
         */
        fun start(context: Context) {
            if (!running) {
                val intent = Intent(context, AudioForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                android.util.Log.d("ExoNativeAudio", "Foreground service requested to start")
            }
        }

        /**
         * Stop the service if it is running.
         */
        fun stop(context: Context) {
            if (running) {
                val intent = Intent(context, AudioForegroundService::class.java)
                context.stopService(intent)
                android.util.Log.d("ExoNativeAudio", "Foreground service requested to stop")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Initialize the wake lock.  Use a unique tag for debugging.
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ExoNativeAudio::WakeLock")
        android.util.Log.d("ExoNativeAudio", "AudioForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        // Acquire the wake lock if not already held
        try {
            if (wakeLock?.isHeld != true) {
                wakeLock?.acquire()
            }
        } catch (e: Exception) {
            // Log but ignore wake lock acquisition failures
            e.printStackTrace()
        }
        android.util.Log.d("ExoNativeAudio", "AudioForegroundService started (id=$startId)")
        // We restart the service if killed to ensure audio stays alive
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        stopForeground(true)
        // Release the wake lock when the service is destroyed
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            wakeLock = null
        }
        android.util.Log.d("ExoNativeAudio", "AudioForegroundService destroyed")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Ambient audio"
            val descriptionText = "Background audio playback"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("SKALA-2")
            .setContentText("Anomalijų ir kitų zonų detektoriai veikia")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return builder.build()
    }
}