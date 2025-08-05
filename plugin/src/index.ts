import { registerPlugin } from '@capacitor/core';
import type { ExoNativeAudioPlugin } from './definitions';

// Register the native plugin under the name "ExoNativeAudio".  This must match
// the @CapacitorPlugin annotation in the Android implementation.  The web
// implementation will always warn that the plugin is unavailable.
const ExoNativeAudio = registerPlugin<ExoNativeAudioPlugin>('ExoNativeAudio', {
  web: () => import('./web').then(m => new m.default()),
});

export * from './definitions';
export { ExoNativeAudio };