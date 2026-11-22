# 16 — Reproducción en fondo y caché (paridad con el original)

Análisis del APK original (`jadx_full/sources`, `jadx_full/resources/AndroidManifest.xml`) y adaptación
a la réplica Media3 sin librerías nativas, ads ni procesos extra.

## Qué hace el original

| Mecanismo | Evidencia | Réplica |
|---|---|---|
| Servicio en proceso `:playback` | `AndroidManifest.xml:491` `MultiProcessModePlaybackService`, `:498` `PlaybackContentProvider` | No replicado: un único `MediaSessionService` es suficiente para audio local y evita IPC. |
| Foreground service `mediaPlayback`, `stopWithTask=false` | `AndroidManifest.xml:485/491` | Igual en `app/src/main/AndroidManifest.xml`. |
| Canal `player_service_v2`, id 3, sin sonido/badge | `o\tb6.java`, `NotificationChannelHelper$Channel` | `PlaybackService.CHANNEL_ID/NOTIFICATION_ID` + `DefaultMediaNotificationProvider`. |
| `PARTIAL_WAKE_LOCK` `"PlaybackService:"` | `o\ge6.java:14-23` | `PlaybackService.updateWakeLock()` con `PARTIAL_WAKE_LOCK`. |
| Persistencia `current_song` + `current_play_pos` (MMKV) | `o\o85.java:291`, `o\r61.java:57` | `PlayerRepository.persistQueue()/restoreQueueIfEmpty()` en `SharedPreferences`. |
| Caché de carátulas `image_manager_disk_cache` (Glide) | `o\af2.java:45-65` | Coil `cacheDir/lark_image_cache`, 64 MB + memoria 25 %. |
| BD `vlc_database` con `picture BLOB` | `o\n55.java`, `o\m55.java` | Room (`songs`, `playlists`, `history`) + Coil para imágenes. |
| `SimpleCache` 50 MB | `o\al0.java:34-38` | No hace falta: es para videos de fondo/anuncios, no para música local. |
| `IdleHandler` para diferir trabajo | `MainProcessInitializer.java:266` | Coroutine en `LarkApp` con `delay(1_500)` + WorkManager manual. |
| Sin `START_STICKY`, sin alarma, sin `BOOT_COMPLETED` de playback | manifiesto y `PlaybackService.java` | Se mantiene `START_STICKY` solo como red de seguridad, no para arranque. |

## Cambios aplicados en v21

1. **Bootstrap**: eliminado `WorkManagerInitializer` del manifest; `LarkApp` ya no implementa
   `Configuration.Provider` y inicializa WorkManager manualmente tras 1,5 s.
2. **Caché de imágenes**: `Coil.setImageLoader { ... }` con `DiskCache` de 64 MB y `MemoryCache` del 25 %;
   el loader se construye en el primer uso, no en `onCreate`.
3. **Caché de sesión**: cola, índice, posición y estado se guardan en `lark_playback_state` y se restauran
   al conectar si el servicio está vacío; `onMediaItemTransition(null)`, "vaciar cola" y la X borran la caché.
4. **Notificación**: canal `player_service_v2`, id 3, sin sonido, sin badge, visibilidad pública.
5. **Release**: `signingConfig = signingConfigs.debug` para publicar APK R8-ofuscada instalable.

## No replicado a propósito

- Proceso multi-playback, `PlaybackContentProvider`, `IPlaybackRemoteCallback` y watchdog de importancia.
- `KeepSplashImportanceService`, `PlaybackBindSplashService`, `SplashProcessService` (retención para ads).
- iJKPlayer / `.so`, MMKV, Glide, Firebase, `SYSTEM_ALERT_WINDOW`.
- Cache de audio `SimpleCache`: la música es local y no necesita descarga streaming.
