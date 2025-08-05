# ExoNativeAudio

**Android-only Capacitor 7 plugin for reliable background audio playback**

ExoNativeAudio uses Google’s Media3 ExoPlayer to deliver low-latency, multi-slot audio on Android. It supports up to five independent looping players, runs in a minimal foreground service for background playback, and exposes a simple JavaScript API.

## Features

- **Up to 5 players**  
  Each slot is keyed by a string ID (e.g. "zone_3") and has its own loop flag and volume control.  
- **Background playback**  
  Starts a lightweight foreground service (with `FOREGROUND_SERVICE` and `WAKE_LOCK` permissions) whenever any slot is playing.  
- **Clean resource handling**  
  Automatically releases players on `unload` or when the plugin is destroyed; the service stops when no audio is active.  
- **Simple API**  
  Methods: `preload`, `play`, `stop`, `unload`, `setVolume`, `getStatus`.

## Requirements

- Capacitor 7.x  
- Android 10 (SDK 30) or higher

## Installation

1. **Clone the repo**  
   ```bash
   git clone https://github.com/your-org/exo-native-audio.git
   cd exo-native-audio
   ```
2. **Install dependencies**  
   ```bash
   npm install
   npm run build
   ```
3. **Add to your Capacitor app**  
   ```bash
   npm install --save ../path-to/exo-native-audio
   npx cap sync android
   ```

> The plugin’s Android manifest already declares `FOREGROUND_SERVICE` and `WAKE_LOCK`. No extra permissions are needed in your app.

## Usage

```ts
import { ExoNativeAudio } from '@skala2/exo-native-audio';

// 1. Preload
await ExoNativeAudio.preload({
  id: 'zone_3',
  filePath: '/storage/emulated/0/Download/zone3.ogg',
  loop: true,
});

// 2. Play
await ExoNativeAudio.play({ id: 'zone_3' });

// 3. Adjust volume (0.0–1.0)
await ExoNativeAudio.setVolume({ id: 'zone_3', volume: 0.5 });

// 4. Stop (resets to start)
await ExoNativeAudio.stop({ id: 'zone_3' });

// 5. Unload (frees resources)
await ExoNativeAudio.unload({ id: 'zone_3' });

// 6. Get status
const status = await ExoNativeAudio.getStatus({ id: 'zone_3' });
console.log(status); // { isPlaying, positionMs, durationMs, volume }
```

## API

| Method                | Description                                                       | Options                                             |
|-----------------------|-------------------------------------------------------------------|-----------------------------------------------------|
| `preload(options)`    | Load a local file into a slot (replaces existing player if any). | `{ id: string; filePath: string; loop: boolean }`   |
| `play(options)`       | Start playback of a preloaded slot.                              | `{ id: string }`                                    |
| `stop(options)`       | Stop and reset position (slot remains loaded).                   | `{ id: string }`                                    |
| `unload(options)`     | Release a slot’s resources.                                      | `{ id: string }`                                    |
| `setVolume(options)`  | Set volume (0.0–1.0) for a slot.                                 | `{ id: string; volume: number }`                   |
| `getStatus(options)`  | Return `{ isPlaying, positionMs, durationMs, volume }`.          | `{ id: string }`                                    |

## Limitations

- **Android-only** – No iOS or web support (stub for web to satisfy typings).  
- **Local files only** – No streaming of remote URLs.  
- **Minimal notification** – No MediaSession or lock-screen controls.  
- **Five-slot max** – To limit memory usage.

## License

Released under the [MIT License](LICENSE). See `package.json` for version and author details.
