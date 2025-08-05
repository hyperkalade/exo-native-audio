## ProGuard rules for library consumers

# Keep all classes in this library so that R8/ProGuard does not strip
# components such as the foreground service or slot manager.  These rules
# ensure the plugin functions correctly in release builds.  Add additional
# keep rules here if you introduce new classes in this package.
-keep class com.skala.exoaudio.** { *; }

# Keep Media3 ExoPlayer classes to avoid R8 stripping them.  This is
# necessary because the plugin references ExoPlayer by reflection and
# might be removed otherwise.  Adjust the package if using a different
# version of media3.
-keep class androidx.media3.exoplayer.** { *; }
