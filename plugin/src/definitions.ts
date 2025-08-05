export interface ExoAudioStatus {
  id: string;
  isPlaying: boolean;
  positionMs: number;
  durationMs: number;
  volume: number;
}

export interface ExoNativeAudioPlugin {
  /**
   * Preload an audio file into a slot. If the slot already has an instance
   * loaded it will be replaced. The filePath must point to a local file on
   * the device that has been downloaded ahead of time.
   *
   * @param options.id Unique identifier for the slot (e.g. "zone_3").
   * @param options.filePath Absolute file system path to the audio file.
   * @param options.loop Whether the audio should loop continuously.
   */
  preload(options: { id: string; filePath: string; loop: boolean }): Promise<void>;

  /**
   * Begin playback of a previously preloaded slot. If the slot has not
   * been preloaded this call will reject. Always wrap this in a .catch() to
   * handle the case where the ID is unknown.
   */
  play(options: { id: string }): Promise<void>;

  /**
   * Stop playback of a slot. Playback will pause and the position will
   * be reset to the beginning of the audio. The audio will remain
   * preloaded until unload() is called.
   */
  stop(options: { id: string }): Promise<void>;

  /**
   * Unload a slot completely. This will release the underlying player
   * and free its resources. The slot must be preloaded again before
   * playing.
   */
  unload(options: { id: string }): Promise<void>;

  /**
   * Set the volume of a particular slot. Volume should be between 0.0 and 1.0.
   */
  setVolume(options: { id: string; volume: number }): Promise<void>;

  /**
   * Get the current status of a slot including whether it's playing,
   * current position and duration.
   */
  getStatus(options: { id: string }): Promise<ExoAudioStatus>;
}

// Export the plugin wrapper for Capacitor's automatic proxy generation
// NOTE: the actual plugin instance is created in index.ts via registerPlugin.  This
// file only contains type definitions to be consumed by TypeScript.