# 05 — Registro de cambios (Changelog)

Formato: `YYYY-MM-DD — cambio — archivos afectados`

---

## 2026-11-18 — Fase 0: Análisis

- **Análisis ingeniería inversa del APK original** → `../ANALISIS.md`
  - Extracción de 2974 entradas, decompilación con jadx (25 466 .java), manifiesto decodificado.
  - Identificados 13 redes de anuncios, 4 analytics, módulos `drive` (backup), `feature/theme`, etc.
- **Descarga/configuración de jadx 1.4.7** en `%TEMP%\jadx` (fuera del repo).
- **Scripts de análisis** (no forman parte del repo): `strings_scan.py`, `ui_strings.py`, `feature_extract.py`.

## 2026-11-18 — Fase 1: Estructura del proyecto

- **Creado `settings.gradle.kts`** con 13 módulos (app + 5 core + 7 feature).
- **Creado `build.gradle.kts` raíz** con plugins declarados (`apply false`).
- **Creado `gradle/libs.versions.toml`**: versiones centralizadas (AGP 8.7.3, Kotlin 2.0.21, Media3 1.4.1, Room 2.6.1, Compose BOM 2024.10.01, Hilt 2.51.1, Coil 2.6.0…).
- **Creado `gradle.properties`**: JVM 2 GB, parallel, caching, AndroidX, nonTransitiveRClass.
- **Creado `.gitignore`**: ignora `.gradle`, `build`, `.idea`, `local.properties`, APK/AAB.
- **Creado `README.md`**: visión, estado, stack, estructura.
- **Creada documentación `docs/00` a `docs/07`**: índice, estructura, arquitectura, mapa de funciones, mapa de replicación, changelog, decisiones, compatibilidad.

## 2026-11-18 — Fase 2: módulos `core`

- **`core:model`** ✅
  - `Song.kt` (con `contentUri`), `Playlist.kt`, `Lyric.kt` (`LyricLine`/`Lyrics`), `ThemeConfig.kt` (`ColorItem(mode, attr, value)` — réplica del `CustomTheme.ColorItem` original).
- **`core:common`** ✅
  - `Result.kt` (Success/Error/Loading + `getOrNull`), `MediaFileUtils.kt` (consulta MediaStore de audio), `di/CommonModule.kt` (`@IoDispatcher`). Añadida dependencia `kotlinx-coroutines-android`.
- **`core:database`** ✅
  - `entity/Entities.kt`: tablas `songs`, `playlists`, `playlist_songs`, `history`, `queue`.
  - `dao/Daos.kt`: `SongDao`, `PlaylistDao` (con `replaceSongs` transaccional), `HistoryDao`, `QueueDao`.
  - `LarkDatabase.kt` (v1) + `di/DatabaseModule.kt` (Hilt, `fallbackToDestructiveMigration`).
- **`core:datastore`** ✅
  - `SettingsDataStore.kt`: tema, shuffle, repeat, gapless, sleep timer, idioma y **color overrides** serializados. `di/DataStoreModule.kt`.
- **`core:designsystem`** ✅
  - `theme/Color.kt`, `theme/CustomTheme.kt` (`ColorScheme.withOverrides`), `theme/Theme.kt` (`LarkTheme` con DynamicColor, DayNight y overrides). Añadido `androidx.core.ktx`.

## 2026-11-18 — Fase 3: módulos `feature` + app

- **`feature:player`** ✅ — Media3
  - `PlaybackService.kt` (MediaSessionService, FGS), `PlayerModule.kt` (ExoPlayer singleton con audio focus), `PlayerRepository.kt` (MediaController → cola/play/pause/shuffle/repeat), `ui/PlayerViewModel.kt`, `ui/PlayerScreen.kt` (reproductor con gradiente, carátula 280dp, seek bar, controles), `ui/MiniPlayer.kt` (barra inferior).
- **`feature:library`** ✅ — `scanner/MediaScanner.kt` (MediaStore→Room), `LibraryRepository.kt`, `PlaylistRepository.kt`, `ui/LibraryViewModel.kt`, `ui/LibraryScreen.kt` (TopAppBar "Lark Player", búsqueda, pestañas Songs/Albums/Artists/Playlists/Genres, listas con carátula).
- **`feature:lyrics`** ✅ — `LrcParser.kt` (parser LRC + búsqueda binaria de línea activa), `ui/LyricsView.kt` (letras scroll sincronizadas).
- **`feature:equalizer`** ✅ — `EqualizerManager.kt` (audiofx nativo), `ui/EqualizerSheet.kt`.
- **`feature:backup`** ✅ — `BackupManager.kt` (export/import ZIP con Gson; playlists, songs, history, queue), `BackupModule.kt`, `BackupWorker.kt` (WorkManager/Hilt), `ui/BackupScreen.kt` + `BackupViewModel.kt` (SAF CreateDocument/OpenDocument).
- **`feature:settings`** ✅ — `ui/SettingsScreen.kt` (DynamicColor, tema, apartados), `SettingsViewModel.kt`.
- **`feature:widget`** ✅ — `PlayerNotification.kt` (documenta el provider de notificación de MediaSessionService).
- **`app`** ✅ — `build.gradle.kts` (release con R8 + shrinkResources), `AndroidManifest.xml` (sin permisos de anuncios, service + MediaButtonReceiver), `LarkApp.kt` (Hilt + WorkManager factory), `MainActivity.kt` (permisos media + `LarkTheme`), `navigation/AppNavGraph.kt` (bottom nav Library/Player/Settings + rutas player/lyrics/backup), `res/values/themes.xml`, `proguard-rules.pro`.
- Correcciones: `@ApplicationContext` en `SettingsDataStore`, `MediaScanner`, `PlayerRepository`; dependencias `animation`, icons-extended, activity-compose, hilt-work; proveedor de Gson; `HiltWorkerFactory` en Application.

## 2026-11-18 — Fase 4: build y correcciones

- **Gradle wrapper 8.9** generado (AGP 8.7.3 exige ≥8.9). Descargado Gradle 8.9 en `%TEMP%`.
- **Errores de compilación corregidos:**
  1. `core:common` le faltaba `implementation(project(":core:model"))` (MediaFileUtils usa `Song`).
  2. `core:database`: configurado `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` para exportar schema.
  3. `feature:widget` le faltaba `implementation(libs.androidx.media3.session)` (PlayerNotification importa MediaSessionService).
  4. `BackupScreen.kt`: añadido `import kotlinx.coroutines.launch`.
  5. `PlayerRepository.kt`: reescrito el listener de `MediaController` sin `it` ambiguo (usando `future` + `onSuccess`/`onFailure`).
- **Repo Git inicializado** en `lark-clean/` con identidad local → [`08-REPO.md`](08-REPO.md).
- `.gitignore` ampliado con `.kotlin/` y `build_debug.log`.

  6. `LyricsView.kt`: añadido `import androidx.compose.runtime.remember`.
  7. `app/build.gradle.kts`: `buildFeatures { buildConfig = true }` (AGP 8 no genera BuildConfig por defecto).

## 2026-11-18 — Fase 5: build exitoso + commit

- **`assembleDebug` → BUILD SUCCESSFUL** (415 tareas). APK generado: `app/build/outputs/apk/debug/app-debug.apk` (~22.4 MB, sin ofuscar).
- **Commit inicial del repo:** `f0b5579 feat: initial LarkClean native player (Fases 0-4)` — 80 archivos versionados, working tree limpio.
- Corregido `.gitignore`: `build/` (todos los niveles) en vez de `/build` (solo raíz) — evita indexar artefactos de build.

## 2026-11-18 — Fase 6: Apple Music glass + detección de música + funciones

Ver [`09-APPLE-GLASS.md`](09-APPLE-GLASS.md) para el detalle completo.

- **Fix detección:** `MediaScanBus`, auto-scan en `LibraryViewModel.init`/`onResume`, query MIME_TYPE en `MediaFileUtils`, permiso `READ_MEDIA_VISUAL_USER_SELECTED`.
- **Liquid glass:** `ApplePalette`, `GlassSurface`, `BlurredBackdrop`, `GlassNavigationBar`, `GlassIconButton`; tema oscuro por defecto.
- **Pantallas rediseñadas:** Player, MiniPlayer, Library, Lyrics, Equalizer + nuevas Queue y SleepTimer.
- **Funciones nuevas:** playlists UI, ringtone, letras .lrc locales, cola reproducible, temporizador de sueño, ecualizador real.
- **Navegación:** rutas `queue`, `sleep`, `equalizer`; bottom bar glass.

## 2026-11-18 — Fase 7: rediseño fiel a Lark Player (sin barra inferior)

Ver [`10-LARK-UI.md`](10-LARK-UI.md) para el detalle completo.

- **Diseño:** fondo `#0A0A0B`, tarjetas `#171719` con radio 22 dp, acento azul `#0A84FF` y acento pastel por carátula (`AlbumColorRepository` con `androidx.palette`).
- **Sin barra de navegación inferior:** `AppNavGraph` reescrito; biblioteca como destino inicial, mini-player flotante, ajustes por menú de 3 puntos o `ModalNavigationDrawer`.
- **Biblioteca:** cabecera Lark + buscar/ordenar/reproduciendo, pestañas en píldora (Videos, Canciones, Playlists, Carpetas, Artistas, Álbumes), filas redondeadas con insignia `LETRAS`, «Nueva playlist», artistas circulares y 3 tamaños de tema (compacto / medio / cuadrícula).
- **Reproductor:** carátula grande redondeada, título en color de acento, favorito + 3 puntos, progreso fino, transporte en acento, fila Ecualizador / Letras / Cola.
- **Mini-player:** píldora teñida por el acento, carátula, repetición con arco de progreso y botón circular.
- **Ajustes nuevos:** `SettingsScreen` agrupada, `PlaybackSettingsScreen` (junto a otras apps, sin pausas, crossfade con deslizador), `LanguageScreen`, `ThemeScreen` (previsualizaciones, Oscuro/Claro/Sistema, colores de acento, tamaño), `PlayTimeScreen`, `NotificationSettingsScreen`, `AboutScreen`, `FeedbackScreen`, `RecentlyDeletedScreen`, `HiddenFilesScreen`.
- **Restilizadas:** `BackupScreen` (ZIP local), `QueueScreen`, `SleepTimerScreen`, `EqualizerScreen`, `LyricsScreen`.
- **Datos:** `ThemeConfig.accentColor/themeSize`; `SettingsDataStore` con acento, tamaño, crossfade, junto a otras apps, favoritos, ocultos, tiempo escuchado, notificaciones e idioma; `PlaylistWithCover` + `observePlaylistsWithCovers()`.
- **Dependencias:** `androidx.palette:palette-ktx:1.0.0`; `feature:settings` → `feature:player` (vista previa del tema).
- **Build:** `assembleDebug` → BUILD SUCCESSFUL.

### Próximos cambios (Fase 8)
- [ ] Widget Glance (pendiente)
- [ ] Build release + push



