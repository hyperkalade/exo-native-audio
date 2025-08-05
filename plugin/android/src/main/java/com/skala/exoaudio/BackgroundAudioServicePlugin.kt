package com.skala.exoaudio

import android.util.Log
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

/**
 * Capacitor plugin exposing methods to JavaScript. Bridges calls into
 * ExoAudioSlotManager and manages the associated foreground service.
 */
// The Capacitor plugin name used to register this plugin.  Keeping this in sync
// with the TypeScript registration ensures that JavaScript calls are routed
// correctly.  The plugin name should be unique and avoid characters not
// allowed by the Capacitor runtime.  Here we expose "ExoNativeAudio" as the
// plugin id, matching the skala2/exo-native-audio package name.
@CapacitorPlugin(name = "ExoNativeAudio")
class ExoNativeAudioPlugin : Plugin() {

    private lateinit var slotManager: ExoAudioSlotManager

    // Receiver for audio becoming noisy (e.g. headphones unplugged).  We hold a
    // nullable reference so we can unregister on destroy.
    private var noisyReceiver: BroadcastReceiver? = null

    override fun load() {
        super.load()
        // Use applicationContext to avoid leaking plugin instance context
        slotManager = ExoAudioSlotManager(context.applicationContext)

        // Register a receiver for ACTION_AUDIO_BECOMING_NOISY to pause playback when
        // audio output changes (e.g. headphones unplugged).  When triggered we stop all
        // slots and the foreground service.
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    Log.d("ExoNativeAudio", "Audio becoming noisy; stopping all slots")
                    slotManager.stopAll()
                    // Stop the foreground service since nothing should be playing now
                    AudioForegroundService.stop(context)
                }
            }
        }
        try {
            context.registerReceiver(noisyReceiver, filter)
        } catch (e: Exception) {
            Log.e("ExoNativeAudio", "Failed to register noisy receiver", e)
        }
    }

    override fun handleOnDestroy() {
        // Unregister the noisy receiver if registered
        try {
            if (noisyReceiver != null) {
                context.unregisterReceiver(noisyReceiver)
                noisyReceiver = null
            }
        } catch (e: Exception) {
            Log.e("ExoNativeAudio", "Failed to unregister noisy receiver", e)
        }
        slotManager.releaseAll()
        // Ensure the service is stopped when plugin is destroyed
        AudioForegroundService.stop(context)
        super.handleOnDestroy()
    }

    @PluginMethod
    @Synchronized
    fun preload(call: PluginCall) {
        val id = call.getString("id")
        val filePath = call.getString("filePath")
        val loop = call.getBoolean("loop") ?: false
        if (id == null || filePath == null) {
            call.reject("Must provide id and filePath")
            return
        }
        try {
            Log.d("ExoNativeAudio", "JS preload request id=$id, filePath=$filePath, loop=$loop")
            slotManager.preload(id, filePath, loop)
            // If no slots are actively playing after (re)loading, ensure the
            // foreground service is stopped.  Preloading can replace an
            // existing player, which might have been playing.  Without this
            // check the service would remain active with nothing playing.
            if (!slotManager.anyPlaying()) {
                AudioForegroundService.stop(context)
            }
            call.resolve()
        } catch (e: Exception) {
            Log.e("ExoNativeAudio", "preload error", e)
            call.reject(e.message ?: "Preload failed")
        }
    }

    @PluginMethod
    @Synchronized
    fun play(call: PluginCall) {
        val id = call.getString("id")
        if (id == null) {
            call.reject("Must provide id")
            return
        }
        Log.d("ExoNativeAudio", "JS play request id=$id")
        try {
            // Validate that the slot exists before playing; otherwise
            // propagate an error back to JavaScript to aid debugging
            if (!slotManager.hasSlot(id)) {
                call.reject("Slot '$id' not preloaded")
                return
            }
            slotManager.play(id)
            // Start the foreground service unconditionally.  The service itself tracks
            // whether it is already running and will no‑op on subsequent starts.
            AudioForegroundService.start(context)
            call.resolve()
        } catch (e: Exception) {
            Log.e("ExoNativeAudio", "play error", e)
            call.reject(e.message ?: "Play failed")
        }
    }

    @PluginMethod
    @Synchronized
    fun stop(call: PluginCall) {
        val id = call.getString("id")
        if (id == null) {
            call.reject("Must provide id")
            return
        }
        Log.d("ExoNativeAudio", "JS stop request id=$id")
        if (!slotManager.hasSlot(id)) {
            // Nothing to stop; treat as no-op and resolve but log a warning
            Log.w("ExoNativeAudio", "Stop requested for unloaded slot id=$id")
            call.resolve()
            return
        }
        slotManager.stop(id)
        if (!slotManager.anyPlaying()) {
            AudioForegroundService.stop(context)
        }
        call.resolve()
    }

    @PluginMethod
    @Synchronized
    fun unload(call: PluginCall) {
        val id = call.getString("id")
        if (id == null) {
            call.reject("Must provide id")
            return
        }
        Log.d("ExoNativeAudio", "JS unload request id=$id")
        if (!slotManager.hasSlot(id)) {
            Log.w("ExoNativeAudio", "Unload requested for unloaded slot id=$id")
            call.resolve()
            return
        }
        slotManager.unload(id)
        if (!slotManager.anyPlaying()) {
            AudioForegroundService.stop(context)
        }
        call.resolve()
    }

    @PluginMethod
    @Synchronized
    fun setVolume(call: PluginCall) {
        val id = call.getString("id")
        val volume = call.getFloat("volume")
        if (id == null || volume == null) {
            call.reject("Must provide id and volume")
            return
        }
        Log.d("ExoNativeAudio", "JS setVolume request id=$id, volume=$volume")
        if (!slotManager.hasSlot(id)) {
            call.reject("Slot '$id' not preloaded")
            return
        }
        slotManager.setVolume(id, volume)
        call.resolve()
    }

    @PluginMethod
    @Synchronized
    fun getStatus(call: PluginCall) {
        val id = call.getString("id")
        if (id == null) {
            call.reject("Must provide id")
            return
        }
        Log.d("ExoNativeAudio", "JS getStatus request id=$id")
        val status = slotManager.getStatus(id)
        val ret = JSObject().apply {
            put("id", status.id)
            put("isPlaying", status.isPlaying)
            put("positionMs", status.positionMs)
            put("durationMs", status.durationMs)
            put("volume", status.volume)
        }
        call.resolve(ret)
    }
}