# 11 — Escaneo REA del APK original (v2027.1.8)

Objetivo: identificar (a) funciones que faltan en LarkClean y (b) optimizaciones del original para reproducción en tercer plano y arranque.

## 0. Estado de REA y evidencia

- Herramientas MCP de REA **no registradas** en esta sesión de OpenCode (`doctor --client opencode`: `registration:opencode` = `config_drift`, motor JADX no configurado: `REA_JADX_MCP_JAR` ausente). Por tanto **no hay evidencia REA a nivel de clase/método**.
- Sí funcionó la ruta sin motor, `rea inspect-artifact`, sobre:
  `LarkPlayer-v2027.1.8-261103_1005_6669ba1-normal-os-universal.apk`
  - Evidence ID: `ev_b5d8c1878176f25930b4b8a286511357f5e7a4ec5dec314e0512b655794e8f09`
  - Sha256 raíz: `5629df561bb8abc949c03ecdba8a34b1b94a7905904391ef20754a0589d93b86`
  - Grafo: 2841 nodos, 2975 apariciones, 2974 aristas; cobertura `container-entries` completa.
  - Salida cruda guardada: `../rea_artifact.json`.
- Como **observación complementaria** (no REA Evidence) se usaron las fuentes decompiladas preexistentes en `../jadx_full/sources` (27 690 `.java`) y el manifiesto decodeado `../jadx_full/resources/AndroidManifest.xml`.

## 1. Inventario observado (REA)

- 5 DEX: `classes.dex` … `classes5.dex`.
- 58 bibliotecas nativas. Las relevantes:
  - Reproducción: `libijkffmpeg.so`, `libijkplayer.so`, `libijksdl.so`, `libflacJNI.so`, `libopensl_play.so`, `libnoise.so`, `libmp3cvt_v2.so`.
  - Datos: `libmmkv.so`, `libdatastore_shared_counter.so`, `libfile_lock_pgl.so`, `libbuffer_pgl.so`.
  - Crash/telemetría: `libcrashlytics*.so`, `libapminsighta.so`, `libapminsightb.so`.
  - Otras: `libc++_shared.so`, `libpglarmor.so`, `libnms.so`, `libtt_ugen_layout.so`.
- Manifiesto (decompilado): `minSdk=25`, `targetSdk=36`, `versionCode=2027010809`, `largeHeap=true`, `hardwareAccelerated=true`, `extractNativeLibs=true`, `allowBackup=false`.

## 2. Reproducción en tercer plano — optimizaciones del original

| Mecanismo | Evidencia (fuentes jadx, no REA) | Equivalente en LarkClean |
|---|---|---|
| Proceso de reproducción separado | `AndroidManifest.xml`: `MultiProcessModePlaybackService` con `android:process=":playback"` y `PlaybackContentProvider` en el mismo proceso | No; `PlaybackService` está en el proceso principal |
| Servicio no se detiene al eliminar la tarea | `android:stopWithTask="false"` en **ambos** servicios de reproducción | No declarado explícitamente |
| Servicio en primer plano de multimedia | `android:foregroundServiceType="mediaPlayback"` | Sí (`MediaSessionService`) |
| Wake lock parcial mientras suena | `o/ge6.java`: `newWakeLock(1, "PlaybackService:")`; `o/m86.java` lo adquiere al publicar `PlaybackEvent.PLAYING` y lo libera en `PAUSED`/`ENDED` | Sí (`PARTIAL_WAKE_LOCK` en `PlaybackService.onIsPlayingChanged`) |
| Arranque seguro de primer plano en Android 12+ | `o/tb6.java::j()`: `safeStartForeground`, captura `ForegroundServiceStartNotAllowedException`, reintenta con `startService` + extras y publica la notificación con `NotificationManagerCompat` | Parcial: Media3 gestiona el foreground; sin fallback explícito |
| Botones multimedia en otro proceso | `LarkPlayerMediaButtonReceiver` y widgets en `:playtool`, `intent-filter` con prioridad 999 | `MediaButtonReceiver` en proceso principal |
| Manejadores dedicados | `o/za6.java` construye handlers de control remoto, foco/estado telefónico, auriculares, notificación, asistente, caché, mensajes y callback externo | Solo `PlayerRepository`/`PlayerViewModel` |
| Exclusión de optimización de batería | `o/tb6.java` registra `isIgnoringBatteryOptimizations` en el log de diagnóstico | Sí: solicitud única en `MainActivity` |
| Velocidad de reproducción | `song_play_speed` leído desde SharedPreferences en `o/za6.java` | No |

## 3. Rendimiento al abrir — optimizaciones del original

1. **Arranque por proceso con `androidx.startup`** (`rousetime/android_startup`): `MainProcessInitializationProvider`, `PlaybackProcessInitializationProvider`, `SplashProcessInitializationProvider`, `MessageProcessInitializationProvider`, `PlayToolProcessInitializationProvider`, `LPInitializationProvider` y meta-data `ThemeStartup`. Cada proceso inicializa solo lo suyo.
2. **Inicialización fuera del hilo principal y sin bloqueo**: `ThemeStartup`, `ResourcesStartup`, `GsonStartup` y `ClutterStartup` devuelven `false` en `callCreateOnMainThread()` y en `waitOnMainThread()`.
3. **Precalentado de recursos en segundo plano**: `ResourcesStartup.create()` ejecuta `prebuildTypefaces()`, `preloadDrawables()` y `preloadLayouts()`; solo si `getCurrentProcessImportance(context) <= 100` (proceso en primer plano).
4. **Trabajo diferido**: `MainProcessInitializer.create()` marca `application_create_time`, registra callbacks de ciclo de vida y usa `Handler.post(...)`; aplica banderas de primera ejecución (`scan_rule_version`, `fixed_*`, `key_main_activity_launched`) para no repetir migraciones pesadas de MediaStore.
5. **Almacenamiento rápido**: `libmmkv.so` para key-value (además de SharedPreferences/DataStore).
6. **Heap y nativos**: `largeHeap=true`, `hardwareAccelerated=true`, `extractNativeLibs=true`.
7. **Ruta de lanzamiento**: `RedirectActivity` (tema splash, `singleTask`) → `MainActivity` (`singleTop`), evitando recreaciones.

Equivalente en LarkClean: caché de Room con `lastScanAt` (10 min) y migración de base de datos; **no** hay startup por procesos, precalentado de recursos ni MMKV.

## 4. Funciones que faltan frente al original

| Función original | Estado en LarkClean |
|---|---|
| Reproducción de vídeo con superficie y gestos (IJKPlayer) | Falta; los vídeos se listan pero se reproducen como audio |
| Widget de pantalla de inicio | Falta (`feature:widget` solo tiene la notificación) |
| Pantalla de bloqueo propia (overlay) | Falta; se usa la sesión multimedia del sistema |
| Visualizador de audio | Falta |
| Búsqueda de letras en internet | Falta; solo `.lrc` locales |
| Transferencia de archivos web / QR | Descartada |
| Velocidad de reproducción | Falta |
| Ecualizador con presets/bass/treble y visualizador | Parcial: `audiofx.Equalizer` por bandas |
| Backup automático programado | `BackupWorker` es un stub; solo ZIP manual |
| «Musical memories» / recap | Solo historial y tiempo de escucha |
| Mantener pantalla encendida, modos de ventana, bloqueo de app | No implementados |
| Cambio de idioma en caliente | El idioma se guarda pero no traduce la UI hasta reiniciar |
| Proceso de reproducción aislado y `stopWithTask=false` | Falta (ver tabla anterior) |
| Anuncios, analytics, Drive, iJKPlayer y `.so` propios | Eliminados intencionalmente |

## 5. Conclusiones y recomendaciones

- Para «seguir reproduciendo aunque cierren la app» LarkClean ya cubre lo esencial (foreground service + wake lock + excepción de batería), pero conviene declarar `android:stopWithTask="false"` explícitamente en `PlaybackService`, igual que el original.
- El aislamiento en proceso `:playback` del original es la razón principal por la que sobrevive a la muerte de la UI; reproducirlo en Media3/Hilt exigiría mover el `ExoPlayer` y la sesión a otro proceso (IPC), un cambio mayor. La alternativa práctica es el foreground service + wake lock actuales.
- Para arranque, la ganancia más barata ya aplicada es la caché de la biblioteca; el original además hace init concurrente y precalienta fuentes/layouts. En Compose esa pre-carga no aplica igual, pero sí se puede diferir cualquier inicialización pesada fuera del composable inicial.
- Límites: análisis **estático**; no se observó ejecución. Sin `REA_JADX_MCP_JAR` no hay Evidence de clases/métodos; las citas de código provienen del jadx previo y se etiquetan como observaciones no-REA.
