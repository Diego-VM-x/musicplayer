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
| Ecualizador con re-adjunción de audio session | `feature:equalizer` | `EqualizerViewModel.kt` | ✅ |

## Biblioteca

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Escaneo MediaStore (audio/video) | `feature:library` | `scanner/MediaScanner.kt` | ⏳ |
| Listado canciones/álbumes/artistas/géneros | `feature:library` | `LibraryRepository.kt` | ⏳ |
| Búsqueda y orden | `feature:library` | `LibraryRepository.kt` | ⏳ |
| Crear/renombrar/eliminar playlists | `feature:library` | `PlaylistRepository.kt` | ⏳ |
| Favoritos | `feature:library` | `PlaylistRepository.kt` | ⏳ |
| Historial / "musical memories" | `feature:library` | `PlaylistRepository.kt` (tabla `history`) | ⏳ |
| Escanear carpetas personalizadas | `feature:library` | `scanner/MediaScanner.kt` | ⏳ |
| Asignar ringtone | `feature:library` | `scanner/MediaScanner.kt` (SAF write) | ⏳ |
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
| Parser LRC | `feature:lyrics` | `LrcParser.kt` | ⏳ |
| Búsqueda de letras local/internet | `feature:lyrics` | `LyricsRepository.kt` | ⏳ |
| Letras sincronizadas / scrolling | `feature:lyrics` | `ui/LyricsView.kt` | ⏳ |

## Audio

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Ecualizador (audiofx nativo) | `feature:equalizer` | `EqualizerManager.kt` | ⏳ |
| Visualizador | `feature:equalizer` | `ui/EqualizerSheet.kt` | ⏳ |
| Temporizador de sueño | `feature:player` | `PlayerRepository.kt` (timer) | ⏳ |
| Gain / volumen | `feature:player` | `PlayerRepository.kt` | ⏳ |

## Copia de seguridad

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Exportar ZIP local | `feature:backup` | `BackupManager.kt` | ⏳ |
| Importar ZIP local | `feature:backup` | `BackupManager.kt` | ⏳ |
| Backup automático programado | `feature:backup` | `BackupWorker.kt` (WorkManager) | ⏳ |
| UI backup | `feature:backup` | `ui/BackupScreen.kt` | ⏳ |

## Personalización / ajustes

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Preferencias (tema, playback, UI) | `core:datastore` | `SettingsDataStore.kt` | ✅ |
| Temas personalizados (mode/attr/value) | `core:designsystem` | `theme/CustomTheme.kt` (`ColorScheme.withOverrides`) | ✅ |
| DynamicColor on/off | `core:designsystem` | `theme/Theme.kt` (`LarkTheme`) | ✅ |
| Modelos de dominio | `core:model` | `Song.kt`, `Playlist.kt`, `Lyric.kt`, `ThemeConfig.kt` | ✅ |
| Consulta MediaStore | `core:common` | `MediaFileUtils.kt` (`queryAudio`, `albumArtUri`) | ✅ |
| Persistencia Room (playlists/historial/cola) | `core:database` | `entity/Entities.kt`, `dao/Daos.kt`, `LarkDatabase.kt` | ✅ |
| Idioma (es/en/fr/de/pt/ar…) | `feature:settings` | `ui/LanguageScreen.kt` | ✅ |
| Pantalla de ajustes | `feature:settings` | `ui/SettingsScreen.kt`, `ui/SettingsCommon.kt` | ✅ |
| Ajustes de reproducción (junto a otras apps, sin pausas, crossfade) | `feature:settings` | `ui/PlaybackSettingsScreen.kt` | ✅ |
| Tema (modo, acento, tamaño, previsualización) | `feature:settings` | `ui/ThemeScreen.kt` | ✅ |
| Tiempo de reproducción | `feature:settings` | `ui/PlayTimeScreen.kt` | ✅ |
| Notificaciones (reproducción / bloqueo) | `feature:settings` | `ui/NotificationSettingsScreen.kt` | ✅ |
| Sobre / Opinión / Eliminado recientemente | `feature:settings` | `ui/AboutScreen.kt`, `ui/FeedbackScreen.kt`, `ui/RecentlyDeletedScreen.kt` | ✅ |
| Archivos ocultos | `feature:library` | `ui/HiddenFilesScreen.kt` | ✅ |
| Acento pastel por carátula | `feature:player` | `AlbumColorRepository.kt` | ✅ |

## Sistema

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Notificación multimedia | `feature:widget` | `PlayerNotification.kt` | ⏳ |
| Widget pantalla de inicio | `feature:widget` | `LarkGlanceWidget.kt` | ⏳ |
| Pantalla de bloqueo | `feature:widget` | `PlayerNotification.kt` (MediaSession) | ⏳ |
| Permisos de almacenamiento/media | `core:common` | `MediaFileUtils.kt` | ⏳ |
| Barra de estado transparente con degradado (sin desplazar la UI) | `app` | `MainActivity.kt` | ✅ |
| Navegación | `app` | `navigation/AppNavGraph.kt` | ✅ |
| Application / Hilt | `app` | `LarkApp.kt` | ⏳ |

## Leyenda de estado

- ✅ Implementado
- 🔄 En progreso
- ⏳ Pendiente
- ❌ Descartado

---

** Referencia del original:** las clases equivalentes del APK decompilado están listadas en `../ANALISIS.md` y en [`04-MAPA-REPLICACION.md`](04-MAPA-REPLICACION.md).
