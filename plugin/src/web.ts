import { WebPlugin } from '@capacitor/core';
import type { ExoNativeAudioPlugin, ExoAudioStatus } from './definitions';

/**
 * Web implementation placeholder. Since this plugin is Android-only the
 * implementation simply warns when invoked. This avoids breaking builds
 * on unsupported platforms.
 */
export class ExoNativeAudioWeb extends WebPlugin implements ExoNativeAudioPlugin {
  async preload(options: { id: string; filePath: string; loop: boolean }): Promise<void> {
    console.warn('ExoNativeAudio is not supported on the web.', options);
    return Promise.reject('ExoNativeAudio is not supported on the web.');
  }

  async play(options: { id: string }): Promise<void> {
    console.warn('ExoNativeAudio is not supported on the web.', options);
    return Promise.reject('ExoNativeAudio is not supported on the web.');
  }

  async stop(options: { id: string }): Promise<void> {
    console.warn('ExoNativeAudio is not supported on the web.', options);
    return Promise.reject('ExoNativeAudio is not supported on the web.');
  }

  async unload(options: { id: string }): Promise<void> {
    console.warn('ExoNativeAudio is not supported on the web.', options);
    return Promise.reject('ExoNativeAudio is not supported on the web.');
  }

  async setVolume(options: { id: string; volume: number }): Promise<void> {
    console.warn('ExoNativeAudio is not supported on the web.', options);
    return Promise.reject('ExoNativeAudio is not supported on the web.');
  }

  async getStatus(options: { id: string }): Promise<ExoAudioStatus> {
    console.warn('ExoNativeAudio is not supported on the web.', options);
    return Promise.reject('ExoNativeAudio is not supported on the web.');
  }
}

export default ExoNativeAudioWeb;