# 04 — Mapa de replicación (original → nuevo)

Correspondencia entre las clases del APK decompilado (`jadx_full/sources/com/dywx/larkplayer`) y la nueva app.

| Original (Lark Player) | Nuevo (LarkClean) | Notas |
|---|---|---|
| `feature/player/PlaybackService.java` | `feature:player/PlaybackService.kt` | Media3 `MediaSessionService` en vez de servicio propio |
| `feature/player/Multi/SingleProcessModePlaybackService` | `PlaybackService.kt` | Un solo proceso (más simple) |
| `feature/player/PlaybackMediaSessionHandler` | `MediaSessionHandler.kt` | Media3 `MediaSession` |
| `feature/player/AudioEffectParams`, `EqualizerConfig`, `VisualizerParams` | `feature:equalizer/*` | `audiofx.Equalizer` nativo |
| `feature/player/SongBehaviorRecordDatabase` | `core:database` tabla `history` + `feature:library/PlaylistRepository` | Room |
| `feature/lyrics/AbsLyricsView`, `LpLyricsPlayerView` | `feature:lyrics/ui/LyricsView.kt` | Compose |
| `feature/lyrics/LyricsSearchConfig` | `LyricsRepository.kt` | — |
| `feature/theme/CustomTheme$ColorItem(mode, attr, value)` | `core:designsystem/theme/CustomTheme.kt` | Misma idea: override de color por atributo/modo |
| `feature/theme/ThemeChangeEvent` | `ThemeChangeEvent` (SharedFlow) | — |
| `feature/scan/**` | `feature:library/scanner/MediaScanner.kt` | MediaStore |
| `feature/ringtone/**` | `MediaScanner.kt::setRingtone` | — |
| `feature/transfer/**` | ❌ Descartado (fuera de alcance inicial) | Se puede añadir después |
| `feature/ads/**`, `larkplayer/ads/**` | ❌ Eliminado | Sin anuncios |
| `drive/**` (Google Drive) | `feature:backup/BackupManager.kt` | Backup local ZIP en vez de Drive |
| `gui/audio/BaseSongsFragment`, `BaseAlbumArtistFragment` | `feature:library/ui/LibraryScreen.kt` | Compose |
| `gui/audio/SongListRepository`, `SongsOptimizeViewModel` | `LibraryRepository.kt` | — |
| `main/settings/**` | `feature:settings/ui/SettingsScreen.kt` | — |
| `core/util`, `media_util` | `core:common/MediaFileUtils.kt` | — |
| `glide/**` | Coil | Ver decisión |
| `dagger/**` | Hilt | — |
| `libijkffmpeg.so`, `libijkplayer.so`, `libijksdl.so` | Media3 ExoPlayer | Sin .so nativos propios |
| `libmmkv.so` | DataStore | Sin .so nativo |
| `libcrashlytics-*.so`, Firebase/AppsFlyer/Adjust | ❌ Eliminado | Sin analytics |

## Lo que NO se replica (y por qué)

- **Anuncios / Billing / Analytics:** eliminados a petición del usuario.
- **Ofuscación de recursos:** no se reutilizan los drawables ofuscados (nombres `-1.xml`); se recrea el UI en Compose.
- **iJKPlayer:** reemplazado por ExoPlayer (mantenimiento, tamaño, Compose).
- **Transferencia web / cámara QR:** fuera del alcance inicial (se puede añadir como módulo `feature:transfer` más adelante).

## Funciones nuevas (no existían así en el original)

- Exportación/importación de backup en **ZIP local** (sin depender de Google Drive).
- Tema totalmente personalizable mediante `CustomTheme.ColorItem` expuesto en ajustes.
- Arquitectura modular por feature (más escalable que el monolito original).
