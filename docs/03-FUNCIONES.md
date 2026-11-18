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
| Idioma (es/en/fr/de/pt/ar…) | `feature:settings` | `ui/SettingsScreen.kt` | ⏳ |
| Pantalla de ajustes | `feature:settings` | `ui/SettingsScreen.kt` | ⏳ |

## Sistema

| Función | Módulo | Archivo | Estado |
|---|---|---|---|
| Notificación multimedia | `feature:widget` | `PlayerNotification.kt` | ⏳ |
| Widget pantalla de inicio | `feature:widget` | `LarkGlanceWidget.kt` | ⏳ |
| Pantalla de bloqueo | `feature:widget` | `PlayerNotification.kt` (MediaSession) | ⏳ |
| Permisos de almacenamiento/media | `core:common` | `MediaFileUtils.kt` | ⏳ |
| Navegación | `app` | `navigation/NavGraph.kt` | ⏳ |
| Application / Hilt | `app` | `LarkApp.kt` | ⏳ |

## Leyenda de estado

- ✅ Implementado
- 🔄 En progreso
- ⏳ Pendiente
- ❌ Descartado

---

** Referencia del original:** las clases equivalentes del APK decompilado están listadas en `../ANALISIS.md` y en [`04-MAPA-REPLICACION.md`](04-MAPA-REPLICACION.md).
