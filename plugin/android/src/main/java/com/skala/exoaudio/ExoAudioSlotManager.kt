package com.skala.exoaudio

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import android.os.Handler
import android.os.Looper
/**
 * Maintains a collection of up to five ExoPlayer instances keyed by a logical
 * identifier. Each slot manages its own looping behaviour, volume and lifecycle.
 */
class ExoAudioSlotManager(private val appContext: Context) {

    // Limit to five concurrent players
    private val maxSlots = 5

    private data class Slot(val player: ExoPlayer, var looping: Boolean)

    private val slots: MutableMap<String, Slot> = ConcurrentHashMap()

    /**
     * Preload an audio file for a given slot ID. If the slot already has a
     * player it will be replaced. Only local file paths are supported.
     *
     * @throws IllegalArgumentException if too many slots are loaded or file does not exist.
     */
    @Synchronized
    fun preload(id: String, filePath: String, loop: Boolean) {
        // Log entry for debugging purposes
        Log.d("ExoNativeAudio", "preload(id=$id, filePath=$filePath, loop=$loop)")

        // Determine how to handle the provided path.  Android 11+ enforces scoped
        // storage which can block direct access to files on external storage via
        // absolute paths (e.g. /sdcard/foo.mp3).  We attempt to parse the
        // string into a URI; if the scheme is content or file we will use
        // that directly.  Otherwise we treat the string as a raw file path.
        val parsed = Uri.parse(filePath)
        val isContent = parsed.scheme != null && (parsed.scheme == "content" || parsed.scheme == "file")
        val file = if (!isContent) File(filePath) else null

        // Validate that a local file exists when no scheme was provided.  If a
        // content URI is supplied we skip the file existence check because the
        // underlying resource may not be on the file system (e.g. SAF or
        // content provider).  Throwing here allows the plugin to signal
        // invalid input back to JavaScript.
        if (!isContent) {
            require(file?.exists() == true) { "Audio file does not exist: $filePath" }
        }

        // Replace existing slot or allocate new one.  Enforce maximum slot count.
        if (slots.containsKey(id)) {
            unload(id)
        } else if (slots.size >= maxSlots) {
            throw IllegalStateException("Maximum number of audio slots ($maxSlots) reached")
        }

        // Configure audio attributes and wake mode up front.  Using USAGE_MEDIA and
        // CONTENT_TYPE_MUSIC signals to the system that this is long‑running media
        // playback and allows proper audio focus handling.  Setting the wake
        // mode ensures the CPU stays awake while playing from local files when
        // the screen is off.  See ExoPlayer.Builder#setWakeMode and
        // ExoPlayer.Builder#setAudioAttributes for details【884662227977462†L5535-L5546】.
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(appContext)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ false)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Resolve the media URI.  If the parsed URI has a recognized scheme we
        // use it as-is; otherwise we build a file:// URI from the File.  This
        // helps mitigate scoped storage issues on Android 11+ where absolute
        // paths are no longer directly accessible.  The caller may supply a
        // content URI obtained via the Storage Access Framework.
        val uri: Uri = when {
            isContent -> parsed
            file != null -> Uri.fromFile(file)
            else -> parsed
        }
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)

        // Prepare synchronously; ensures duration and meta data are available.
        player.prepare()
        player.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        slots[id] = Slot(player, loop)
    }

    /**
     * Begin playback of a slot if it has been preloaded. No-op if not.
     */
    @Synchronized
    fun play(id: String) {
        val slot = slots[id] ?: throw IllegalArgumentException("Slot '$id' not preloaded")
        val player = slot.player
        player.repeatMode = if (slot.looping) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        player.playWhenReady = true
    }

    /**
     * Stop playback and reset position on a slot. Does nothing if slot not loaded.
     */
    @Synchronized
    fun stop(id: String) {
        val slot = slots[id] ?: return
        val player = slot.player
        player.playWhenReady = false
        player.pause()
        player.seekTo(0)
    }

    /**
     * Fully unload a slot and release its underlying player. Safe to call on
     * slots that have never been loaded.
     */
    @Synchronized
    fun unload(id: String) {
        val slot = slots.remove(id) ?: return
        slot.player.release()
    }

    /**
     * Set the volume of a slot. No-op if not loaded.
     */
    @Synchronized
    fun setVolume(id: String, volume: Float) {
        val slot = slots[id] ?: return
        slot.player.volume = volume.coerceIn(0f, 1f)
    }

    /**
     * Retrieve the current status of a slot. If the slot isn't loaded,
     * returns default values.
     */
    @Synchronized
    fun getStatus(id: String): SlotStatus {
        val slot = slots[id]
        return if (slot != null) {
            val player = slot.player
            val duration = if (player.duration > 0) player.duration else 0
            val position = player.currentPosition
            SlotStatus(
                id = id,
                isPlaying = player.isPlaying,
                positionMs = position,
                durationMs = duration,
                volume = player.volume
            )
        } else {
            SlotStatus(id, false, 0, 0, 0f)
        }
    }

    /**
     * Stop playback on all loaded slots.  Used when a noisy audio event
     * (e.g. headphones unplugged) is detected.  Each slot will be paused
     * and reset to the beginning.  Does nothing if no slots are loaded.
     */
    @Synchronized
    fun stopAll() {
        Log.d("ExoNativeAudio", "Stopping all slots")
        for (key in slots.keys) {
            stop(key)
        }
    }

    /**
     * Determine whether any slot is currently playing. Used to manage
     * foreground service lifecycle.
     */
    @Synchronized
    fun anyPlaying(): Boolean {
        return slots.values.any { it.player.isPlaying }
    }

    /**
     * Release all players and clear slots. Should be called when plugin is
     * destroyed to free resources.
     */
@Synchronized
fun releaseAll() {
    // Release each player on the thread it was created on
    for ((_, slot) in slots) {
        val player = slot.player
        val looper: Looper = player.applicationLooper
        Handler(looper).post {
            player.release()
        }
    }
    slots.clear()
}

    /**
     * Data class representing public status of a slot.
     */
    data class SlotStatus(
        val id: String,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val volume: Float
    )

    /**
     * Determine if a given slot ID has been preloaded.  Exposed for the
     * plugin layer to validate operations before attempting playback or
     * manipulation.  Returns true if the ID exists in the internal map.
     */
    @Synchronized
    fun hasSlot(id: String): Boolean {
        return slots.containsKey(id)
    }
}