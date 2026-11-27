# 03 — Mapa de funciones (dónde está cada cosa)

> Este archivo es la **fuente de verdad**. Cada función nueva se añade aquí con su ruta.

## Reproducción

| Función | Módulo | Archivo (planificado / real) | Estado |
|---|---|---|---|
| Servicio de playback | `feature:player` | `PlaybackService.kt` (MediaSessionService) | ✅ |
| Control de cola (siguiente/anterior/shuffle/repeat) | `feature:player` | `PlayerRepository.kt` + `ui/PlayerViewModel.kt` | ✅ |
| Repositorio de reproducción | `feature:player` | `PlayerRepository.kt` | ✅ |
| MediaSession / botones multimedia | `feature:player` | `PlaybackService.kt` + `MediaButtonReceiver` (Manifest) | ✅ |
| UI del reproductor (carátula, controles, progreso) | `feature:player` | `ui/PlayerScreen.kt` | ✅ |
| Mini-player | `feature:player` | `ui/MiniPlayer.kt` | ✅ |
| Reproducción en segundo plano | `feature:player` | `PlaybackService.kt` (first-party FGS) | ✅ |
| Transición/crossfade configurable (1–12 s) y avance automático | `feature:player` | `PlayerRepository.kt` (`crossfadeLoop`), `SettingsDataStore` | ✅ |
| Preferencias de shuffle/repetición persistentes | `feature:player` | `PlayerRepository.kt` (`setShuffle`, `setRepeat`) | ✅ |
| Controles de repetir/repetir una/aleatorio en la cola | `feature:player` | `ui/QueueScreen.kt` (`QueueToggle`) | ✅ |
| Mini-player con fondo desenfocado (altura acotada) | `feature:player` | `ui/MiniPlayer.kt` | ✅ |
| Reproducción en segundo plano configurable | `core:datastore`, `feature:settings`, `feature:player` | `SettingsDataStore.backgroundPlayback`, `PlaybackSettingsScreen`, `PlaybackService` | ✅ |
| Superficie de vídeo (mismo ExoPlayer, sin permisos extra) | `feature:player`, `feature:library`, `app` | `ui/VideoPlayerScreen.kt` (AndroidView + `PlayerView`), `Routes.VIDEO`, pestaña Vídeos | ✅ |
| Ecualizador con re-adjunción de audio session | `feature:equalizer` | `EqualizerViewModel.kt` | ✅ |

## Biblioteca

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Escaneo MediaStore (audio/video) | `feature:library` | `scanner/MediaScanner.kt` + `LibrarySync.kt` | ✅ |
| Listado canciones/álbumes/artistas/géneros | `feature:library` | `LibraryRepository.kt` | ✅ |
| Búsqueda y orden | `feature:library` | `LibraryRepository.kt` | ✅ |
| Crear/renombrar/eliminar playlists | `feature:library` | `PlaylistRepository.kt` | ✅ |
| Favoritos | `feature:library` | `PlaylistRepository.kt` | ✅ |
| Historial / "musical memories" | `feature:library` | `PlaylistRepository.kt` (tabla `history`) | ✅ |
| Escanear carpetas personalizadas | `feature:library` | `ui/HiddenFilesScreen.kt` | 🔄 |
| Asignar ringtone | `feature:player` | `RingtoneHelper.kt` (uso en `PlayerScreen.kt`) | ✅ |
| UI biblioteca | `feature:library` | `ui/LibraryScreen.kt` | ⏳ |
| Detalle de playlist (carátula, reproducir/aleatorio, orden) | `feature:library` | `ui/PlaylistDetailScreen.kt`, `ui/PlaylistDetailViewModel.kt` | ✅ |
| Editar playlist (nombre + portada SAF recortada) | `feature:library` | `ui/PlaylistDetailScreen.kt` (`PlaylistEditor`), `ui/CoverCropper.kt`, `PlaylistRepository.kt` | ✅ |
| Crear playlist y pedir canciones de inmediato | `feature:library` | `ui/LibraryScreen.kt`, `LibraryViewModel.createPlaylist()` | ✅ |
| Deslizar entre pestañas de la biblioteca | `feature:library` | `ui/LibraryScreen.kt` (`detectHorizontalDragGestures`) | ✅ |
| Agregar canciones a playlist / quitar / reordenar | `feature:library` | `PlaylistRepository.kt` (`addSongs`, `removeSongs`, `reorder`) | ✅ |
| Conteo de reproducciones ("Más reproducido") | `core:database` | `dao/Daos.kt` (`PlayCount`), `PlaylistRepository.playCounts()` | ✅ |
| Selección múltiple y barra flotante de acciones | `feature:library` | `ui/LibraryScreen.kt`, `ui/PlaylistDetailScreen.kt` | ✅ |
| Borrado real de archivos (MediaStore) | `feature:library` | `ui/LibraryScreen.kt` (`deleteSelected`) | ✅ |
| Primitivas liquid glass compartidas | `core:designsystem` | `component/LiquidGlass.kt` | ✅ |

## Letras

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Parser LRC (incluye enhanced LRC / SYLT) | `feature:lyrics` | `LrcParser.kt` | ✅ |
| Búsqueda de letras local (`.lrc`, tags) / internet | `feature:lyrics` | `LyricsRepository.kt`, `LrclibClient.kt` (LRCLIB, sin clave) | ✅ |
| Ajuste manual de sincronía ±0.1 s | `feature:lyrics` | `ui/LyricsView.kt`, `ui/LyricsViewModel.kt` | ✅ |
| Letras sincronizadas / scrolling | `feature:lyrics` | `ui/LyricsView.kt` | ✅ |
| Detección de `.lrc` vía MediaStore (Android 13+) | `core:common`, `feature:lyrics` | `MediaFileUtils.findLyricsText`, `LyricsRepository` | ✅ |
| Modos de reproducción excluyentes | `feature:player` | `PlayerRepository.setPlaybackMode`, `ui/QueueScreen.kt` | ✅ |

## Audio

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Ecualizador (audiofx nativo) | `feature:equalizer` | `EqualizerManager.kt` | ✅ |
| Visualizador PCM (4 estilos) | `feature:player` | `PcmSpectrumProcessor.kt`, `ui/MusicVisualizerSheet.kt` | ✅ |
| Temporizador de sueño | `feature:player` | `PlayerRepository.kt` (timer), `ui/SleepTimerScreen.kt` | ✅ |
| Velocidad de reproducción (0.5×–2×) | `feature:player` | `PlayerRepository.kt`, `SettingsDataStore.kt` | ✅ |

## Copia de seguridad

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Exportar ZIP local (con audio opcional) | `feature:backup` | `BackupManager.kt` | ✅ |
| Importar ZIP local / `.info` / LPBK / `vlc_database` | `feature:backup` | `BackupManager.kt`, `LpbkImporter.kt`, `OriginalLibraryScanner.kt` | ✅ |
| Backup automático programado | `feature:backup` | `BackupWorker.kt` (WorkManager, URI en `inputData`) | ✅ |
| UI backup | `feature:backup` | `ui/BackupScreen.kt` | ✅ |

## Personalización / ajustes

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Preferencias (tema, playback, UI) | `core:datastore` | `SettingsDataStore.kt` | ✅ |
| Temas personalizados (mode/attr/value) | `core:designsystem` | `theme/CustomTheme.kt` (`ColorScheme.withOverrides`) | ✅ |
| DynamicColor on/off | `core:designsystem` | `theme/Theme.kt` (`LarkTheme`) | ✅ |
| Modelos de dominio | `core:model` | `Song.kt`, `Playlist.kt`, `Lyric.kt`, `ThemeConfig.kt` | ✅ |
| Consulta MediaStore | `core:common` | `MediaFileUtils.kt` (`queryAudio`, `albumArtUri`) | ✅ |
| Persistencia Room (playlists/historial/cola) | `core:database` | `entity/Entities.kt`, `dao/Daos.kt`, `LarkDatabase.kt` | ✅ |
| Idioma (es/en/fr/de/pt/ar…) | `feature:settings` | `ui/LanguageScreen.kt`, `core:common/AppLocaleController.kt` | ✅ (aplicación en caliente; las cadenas UI siguen en español) |
| Mantener pantalla encendida durante la reproducción | `core:datastore`, `feature:settings`, `app` | `SettingsDataStore.keepScreenOn`, `ui/PlaybackSettingsScreen.kt`, `MainActivity.observeScreenWake()` | ✅ |
| Pantalla de ajustes | `feature:settings` | `ui/SettingsScreen.kt`, `ui/SettingsCommon.kt` | ✅ |
| Ajustes de reproducción (junto a otras apps, sin pausas, crossfade) | `feature:settings` | `ui/PlaybackSettingsScreen.kt` | ✅ |
| Tema (modo, acento, tamaño, previsualización) | `feature:settings` | `ui/ThemeScreen.kt` | ✅ |
| Tiempo de reproducción | `feature:settings` | `ui/PlayTimeScreen.kt` | ✅ |
| Notificaciones (reproducción / bloqueo) | `feature:settings` | `ui/NotificationSettingsScreen.kt` | ✅ |
| Sobre / Opinión / Eliminado recientemente | `feature:settings` | `ui/AboutScreen.kt`, `ui/FeedbackScreen.kt`, `ui/RecentlyDeletedScreen.kt` | ✅ |
| Archivos ocultos | `feature:library` | `ui/HiddenFilesScreen.kt` | ✅ |
| Acento pastel por carátula | `feature:player` | `ui/PlayerViewModel.kt` (`sampleSongColor`) | ✅ |

## Sistema

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Notificación multimedia (con duración y acciones) | `feature:player` | `LarkMediaNotificationProvider.kt`, `PlaybackService.kt` | ✅ |
| Widget pantalla de inicio | — | Eliminado (no funcionaba en varios launchers) | ❌ |
| Pantalla de bloqueo (sesión multimedia del sistema) | `feature:player` | `PlaybackService.kt` (MediaSession) | ✅ |
| Permisos de almacenamiento/media | `core:common` | `MediaFileUtils.kt` | ⏳ |
| Barra de estado transparente con degradado (sin desplazar la UI) | `app` | `MainActivity.kt` | ✅ |
| Actualizador in-app (GitHub Releases, verificación e instalación) | `core:update`, `feature:settings` | `AppUpdate.kt`, `UpdateManager.kt`, `UpdateSettingsScreen.kt` | ✅ |
| Pantalla «Actualizaciones» en ajustes y drawer | `feature:settings`, `app` | `UpdateSettingsScreen.kt`, `AppNavGraph.kt` | ✅ |
| Navegación | `app` | `navigation/AppNavGraph.kt` | ✅ |
| Application / Hilt | `app` | `LarkApp.kt` | ✅ |

## Leyenda de estado

- ✅ Implementado
- 🔄 En progreso
- ⏳ Pendiente
- ❌ Descartado

---

** Referencia del original:** las clases equivalentes del APK decompilado están listadas en `../ANALISIS.md` y en [`04-MAPA-REPLICACION.md`](04-MAPA-REPLICACION.md).
