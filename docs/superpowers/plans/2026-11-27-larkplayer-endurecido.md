# Endurecimiento LarkClean Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corregir los bugs crÃ­ticos/altos detectados por auditorÃ­a estÃ¡tica, aÃ±adir las funciones ausentes de bajo riesgo y sincronizar la documentaciÃ³n, manteniendo la app compilable y con tests JVM.

**Architecture:** Se mantiene la arquitectura existente (Kotlin + Compose + Media3 + Room + DataStore + Hilt). La lÃ³gica pura que se puede probar sin Android se extrae a helpers `internal object` para cubrirla con JUnit4; las correcciones de Android se aplican en los archivos existentes y se validan con `assembleDebug`.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, JUnit4 4.13.2, Gradle 8.9 (wrapper), JDK 17 target.

**Spec:** `docs/superpowers/specs/2026-11-27-larkplayer-endurecido-design.md`

## Global Constraints

- No anuncios, Firebase, Crashlytics, MMKV, iJKPlayer ni permisos `AD_ID`.
- `minSdk = 24`, `targetSdk = 35`, `compileSdk = 35`, `jvmTarget = "17"`.
- `versionCode` actual = 55, `versionName` = "1.1.3"; cualquier cambio de versiÃ³n se documenta.
- Todo cambio de cÃ³digo se registra en `docs/05-CAMBIOS.md`.
- Toda lÃ³gica nueva se escribe con TDD (test rojo â†’ implementaciÃ³n â†’ verde).

## Review Focus

- MediaStore denegado / vacÃ­o: no debe borrar la biblioteca ni las letras cacheadas. (Task 1)
- ZIP con entradas `../../` o nombres duplicados: no debe escribir fuera del directorio ni perder audio. (Task 4)
- APK actualizado tras reinicio: no debe instalarse sin revalidar SHA/firma. (Task 3)
- Reproductor con shuffle OFF: debe conservar el modo de repeticiÃ³n elegido. (Task 5)
- NotificaciÃ³n: debe mostrar duraciÃ³n y progreso reales. (Task 6)
- Wake lock tras cerrar la notificaciÃ³n: debe readquirirse al volver a reproducir. (Task 7)
- Borrado de archivos: no debe anunciar Ã©xito si MediaStore devolviÃ³ 0 filas. (Task 8)
- Letras a 2Ã—: no debe parpadear por confundir un tick con un seek. (Task 9)
- Permiso de optimizaciÃ³n de baterÃ­a: debe declararse en el manifiesto. (Task 10)

---

### Task 1: Rescan de biblioteca sin pÃ©rdida de datos

**Files:**
- Modify: `core/common/.../MediaFileUtils.kt` (aÃ±adir `queryAudioOrNull`)
- Create: `feature/library/.../scanner/LibrarySync.kt` (extraÃ­do en el trabajo inicial)
- Test: `feature/library/src/test/.../scanner/LibrarySyncTest.kt`
- Modify: `feature/library/.../scanner/MediaScanner.kt`

**Interfaces:**
- Consumes: `MediaFileUtils.queryAudioOrNull(context): List<Song>?`
- Produces: `LibrarySync.merge(existing, fresh, querySucceeded): Result(shouldReplace, songs)`

- [x] **Step 1: Write the failing test** â€” `LibrarySyncTest` con 4 casos (fallo no reemplaza, conserva letras, descarta ausentes, deduplica).
- [x] **Step 2: Run test to verify it fails** â€” `gradlew :feature:library:testDebugUnitTest` â†’ NotImplementedError.
- [x] **Step 3: Implementar `LibrarySync.merge` y `MediaFileUtils.queryAudioOrNull`; conectar `MediaScanner`.**
- [x] **Step 4: Run tests to verify they pass** â€” `BUILD SUCCESSFUL`, 4 tests.
- [x] **Step 5: Commit** â€” pendiente: el usuario no pidiÃ³ commitear; los cambios estÃ¡n en el working tree.

### Task 2: Corregir fuente Ãºnica de letras cacheadas

**Files:** `feature/lyrics/.../LyricsRepository.kt:55-58,101-108`

- [x] Siempre llamar a `resolve(song, cached)` y elegir por `timedScore`; verificar con `:feature:lyrics:compileDebugKotlin`.

### Task 3: RevalidaciÃ³n del APK antes de instalar

**Files:** `core/update/.../UpdateManager.kt`, `UpdateInstallActivity.kt`

- [x] Revalidar con `AppUpdate.validateApk` en `installReady()`, `restoreState()` e `installFromPath()`.
- [x] AÃ±adir rama `else` y timeout al bucle de sondeo de descarga.
- [x] Verificar con `:core:update:compileDebugKotlin`.

### Task 4: RestauraciÃ³n de ZIP segura y completa

**Files:** `feature/backup/.../BackupManager.kt`
- Test: `feature/backup/src/test/.../BackupPathTest.kt`

- [x] TDD: helper `BackupPath.safeChild(dir, entryName)` con tests (rechaza `..`, absolutos, acepta nombre simple).
- [x] Extraer `restoreBundledMusic(entries)` y ejecutarla tambiÃ©n en la rama `backup.json`; llamar a `mediaScanner.scan()`.
- [x] Sanitizar nombres de entrada y usar nombres Ãºnicos `${song.id}-${name}` en export.
- [x] Verificar tests y `:feature:backup:compileDebugKotlin`.

### Task 5: Conservar repeatMode al desactivar shuffle

**Files:** `feature/player/.../ui/PlayerViewModel.kt:387-389`, `PlayerRepository.kt:609-621`

- [x] Llamar a `setShuffle(false)` en lugar de `setPlaybackMode(MODE_NORMAL)`; mapear `MODE_NORMAL -> REPEAT_MODE_OFF`.
- [x] Verificar con `:feature:player:compileDebugKotlin`.

### Task 6: DuraciÃ³n real en la notificaciÃ³n

**Files:** `feature/player/.../PlayerRepository.kt` (`toMediaItem`), `LarkMediaNotificationProvider.kt`

- [x] AÃ±adir `.setDuration(duration)` al `MediaMetadata.Builder`.
- [x] Verificar con `:feature:player:compileDebugKotlin`.

### Task 7: Wake lock tras reinicio de reproducciÃ³n

**Files:** `feature/player/.../PlaybackService.kt`

- [x] Llamar a `updateWakeLock()` en `onIsPlayingChanged(true)` (y no liberarlo en `stopPlaybackCompletely` o readquirirlo al reanudar).
- [x] Verificar con `:feature:player:compileDebugKotlin`.

### Task 8: Borrado real de archivos

**Files:** `feature/library/.../ui/LibraryScreen.kt:690-699`

- [x] Comprobar el `Int` devuelto por `ContentResolver.delete`; marcar `ok=false` si `<= 0`.

### Task 9: Tick de letras a alta velocidad

**Files:** `feature/lyrics/.../ui/LyricsPlaybackClock.kt:66-70`
- Test: `feature/lyrics/src/test/.../LyricsClockTest.kt`

- [x] TDD: helper `LyricsClock.isSeek(previous, current, speed)` con umbral `max(500, 400*speed)`; usarlo en el composable.
- [x] Verificar tests.

### Task 10: Permiso de baterÃ­a y versiÃ³n mostrada

**Files:** `app/src/main/AndroidManifest.xml`, `feature/settings/.../SettingsScreen.kt`, `ui/AboutScreen.kt`

- [x] AÃ±adir `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- [x] Mostrar `BuildConfig.VERSION_NAME` en ajustes y "Sobre".

### Task 11: Funciones ausentes: backup automÃ¡tico y ajuste manual de letras

**Files:** `feature/backup/.../BackupWorker.kt`, `feature/settings/.../ui/PlaybackSettingsScreen.kt`, `feature/lyrics/.../ui/LyricsScreen.kt`

- [x] Implementar `BackupWorker.doWork()` con export real a URI configurada (o quitar el worker y corregir docs si no es viable).
- [x] Conectar `LyricsViewModel.adjustOffset` a controles Â±100 ms en la pantalla de letras.

### Task 12: SincronizaciÃ³n de documentaciÃ³n y skill de auditorÃ­a

**Files:** `docs/03-FUNCIONES.md`, `docs/11-REA-ORIGINAL.md`, `docs/15-AUDITORIA-ORIGINAL.md`, `docs/05-CAMBIOS.md`, skill nueva.

- [x] Corregir estados y rutas obsoletos; registrar todos los cambios.
- [x] Crear skill `larkplayer-audit` con el procedimiento de auditorÃ­a estÃ¡tica repeatable.
- [x] VerificaciÃ³n final: `gradlew testDebugUnitTest assembleDebug`.


