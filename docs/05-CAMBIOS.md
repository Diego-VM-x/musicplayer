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

### Próximos cambios (Fase 5)
- [ ] Confirmar `assembleDebug` exitoso y localizar el APK
- [ ] Verificar apariencia en emulador/dispositivo y ajustar
- [ ] Commit inicial del repo
- [ ] (Opcional) build `assembleRelease` con R8/shrinkResources para medir tamaño final


