# 05 — Registro de cambios (Changelog)

Formato: `YYYY-MM-DD — cambio — archivos afectados`

---

## 2026-11-23 - Fase 52: redibujo del visualizador, icono de cola y letras animadas (v45)

- `VisualizerCanvas`: el estado de captura se lee ahora en el cuerpo del composable, de modo que las
  escrituras de `withFrameNanos` invalidan realmente el `Canvas` (antes se leía solo dentro del draw
  lambda y parecía estático). Sigue usando el PCM real sin micrófono.
- Icono de «A continuación»: se usa el glifo de cola simple y un punto de modo separado, evitando la
  fusión visual con el icono de aleatorio.
- Letras: línea activa con escala/color/alpha/sombra animados; cuando la letra no tiene timestamps se
  distribuye proporcionalmente a la duración para que también siga la reproducción.
- Persistencia de todos los órdenes (apartado siguiente).
- `versionCode` 45 / `1.0.44-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.44` con `musicplayer-45.apk` (3.481.812 bytes, SHA-256 `04A8AEE943B5FA6F71FF43D9627B79573592870459E00A548CA91AFA62871922`), verificada con `aapt` (`versionCode=45`).

## 2026-11-23 - Persistencia de todos los órdenes visibles tras cierre completo de la app

**Qué no sobrevivía al cierre**

- `LibraryViewModel.sortMode` y `tab` eran `MutableStateFlow` en memoria: al reabrir siempre
  volvían a «Recientes» y a la pestaña Canciones.
- La pestaña Playlists no tenía selector de orden ni posición guardada: la lista salía siempre
  en `updatedAt DESC` (cambiar el nombre de una playlist alteraba su orden).
- `PlaylistDetailViewModel.sortMode` era un `var` en memoria: una playlist reordenada a mano
  se mostraba otra vez como «Más recientes» al volver a entrar, aunque `playlist_songs.position`
  sí estuviera guardado en Room.
- El shuffle se guardaba en SharedPreferences, pero durante el arranque se recalculaba con la
  cola todavía vacía y se sobrescribía con una cadena vacía: al reabrir se generaba un orden
  nuevo. La cola solo se cacheaba en SharedPreferences y una reordenación manual no se escribía.

**Corrección**

- `SettingsDataStore` añade `library_sort_mode`, `library_tab`, `playlist_sort_mode`,
  `playlist_detail_sort_<id>` y `shuffle_order` (con migración única desde la clave legacy de
  SharedPreferences). `shuffle`/`repeatMode` siguen siendo la fuente del modo de reproducción y
  ahora `PlayerViewModel` los lee desde DataStore nada más iniciar.
- Room: nueva columna `playlists.position` (migración 3→4, preserva el orden anterior) y DAOs
  para persistir el orden de las playlists. La pestaña Playlists ofrece Manual / Por nombre /
  Por creación y «Subir/Bajar en la lista» en el menú de la playlist; las nuevas playlists se
  añaden al final. `playlist_songs.position` se sigue usando y ahora el modo manual de cada
  playlist también se restaura.
- `PlayerRepository`: el orden aleatorio se lee de DataStore antes de conectar, no se pisa con
  una cola vacía y se reutiliza al reabrir; solo se regenera cuando se pulsa shuffle estando ya
  activo. La cola se refleja además en la tabla Room `queue` (`QueueDao.replace`) y se persiste
  tras añadir, quitar o mover canciones. El tamaño de tema ya se persistía vía `themeConfig`.

Archivos: `core/datastore/SettingsDataStore.kt`, `core/database/{LarkDatabase.kt,entity/Entities.kt,dao/Daos.kt,di/DatabaseModule.kt}`,
`feature/library/{PlaylistRepository.kt,ui/LibraryViewModel.kt,ui/PlaylistDetailViewModel.kt,ui/LibraryScreen.kt}`,
`feature/player/{PlayerRepository.kt,ui/PlayerViewModel.kt}`, `feature/backup/BackupManager.kt`.
Verificación: `./gradlew.bat :app:compileDebugKotlin --no-daemon` → BUILD SUCCESSFUL (2 m 22 s; solo warnings de iconos/API deprecados, sin errores).

## 2026-11-23 - Fase 51: visualizador PCM real sin micrófono (v44)

- Verificado en el APK original: **no usa `android.media.audiofx.Visualizer` ni declara `RECORD_AUDIO`**;
  inserta un `AudioProcessor` en Media3 que lee el PCM antes de `AudioTrack` (`o/jm2.java`), FFT en hilo
  aparte (`o/uo2.java`) y manifest con micrófono `required=false`.
- Nuestra UI dejó de consumir `VisualizerEngine` (Visualizer + permiso): ahora lee directamente
  `AudioSpectrumSource` (PCM tap real) con suavizado/peak decay y canvas a 60 fps. Eliminado el fallback
  simulado y el permiso `RECORD_AUDIO`. El visualizador ya no pide micrófono y muestra audio real.
- Letras: la línea activa ahora se anima (escala, color, alpha y sombra) siguiendo `currentPosition`.
- `versionCode` 44 / `1.0.43-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.43` con `musicplayer-44.apk` (3.466.164 bytes, SHA-256 `28263C70B95D5492401CC26048B9F7056D2EC48FD9BD6801CCFD5C5170750A78`), verificada con `aapt` (`versionCode=44`, sin permiso `RECORD_AUDIO`).

## 2026-11-23 - Fase 50: visualizador modular y lyrics definitivos (v43)

- **Visualizador modular** (`docs/18-VISUALIZADOR.md`): `VisualizerEngine` usa `android.media.audiofx.Visualizer`
  con el `audioSessionId` del reproductor, FFT + forma de onda, suavizado, peak decay y fallback simulado
  si falta `RECORD_AUDIO` o el id. `VisualizerCanvas` dibuja a 60 fps con `withFrameNanos` y 4 modos:
  Armonía, Rebote, Ascenso y Flujo. Panel con switch y tarjetas horizontales.
- **Letras definitivas**: lector único de ID3v2 (USLT/SYLT), FLAC, OGG y M4A, con decodificación
  BOM-aware, `.lrc`/`.txt` locales, letras planas y sincronizadas, caché en Room y auto-scroll.
- `versionCode` 43 / `1.0.42-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.42` con `musicplayer-43.apk` (3.466.184 bytes, SHA-256 `DCA41CFF771606F39891EF4823C075058E9C5EDAA5A0CA958900DA9542E0A4B8`), verificada con `aapt` (`versionCode=43`).

## 2026-11-23 - Diagnóstico y corrección integral del subsistema de letras

**Qué fallaba**

- Había **dos lectores de etiquetas duplicados** (`core:common/EmbeddedLyrics` y
  `feature:player/EmbeddedLyricsReader`) con parser ID3 incompleto: el de `core` usaba
  `UTF_16` (con BOM) para el encoding ID3 2 = UTF-16BE, y ninguno convertía **SYLT** a texto
  útil (los timestamps binarios quedaban como caracteres raros).
- ambos usaban `InputStream.readNBytes()`, que **solo existe desde API 33**: en Android 10/12
  la extracción lanzaba `NoSuchMethodError` y la canción parecía no tener letras.
- `EmbeddedLyricsReader` abría `file://...` mediante `ContentResolver` (no soportado en Android).
- Solo se buscaban archivos `.lrc`; los `.txt` y los archivos en **ISO-8859-1 / UTF-16 con BOM**
  fallaban porque `File.readText()` es UTF-8 estricto (excepción → "sin letras").
- `LrcParser` descartaba las **letras planas** (sin timestamps) y calculaba mal los milisegundos
  de un solo dígito (`.5` → 50 ms en vez de 500 ms).
- `PlayerViewModel` mantenía las líneas de la canción anterior al cambiar de pista (parpadeo de
  letras stale) y también descartaba las letras planas.
- La pantalla de letras solo recargaba cuando cambiaba el **título**, no al cambiar id/ruta, y
  el estado vacío no indicaba qué se había buscado.

**Corrección**

- `LyricsTextUtils.kt` (nuevo en `core:common`): decodificación BOM-aware (UTF-8/UTF-16LE/BE),
  fallback ISO-8859-1, normalización CRLF/CR, limpieza de BOM/caracteres de control y lectura
  de archivos/URIs sin APIs de API 33+.
- `EmbeddedLyrics.kt`: único lector real, con ID3v2.2/2.3/2.4 (USLT/ULT + **SYLT→LRC** con
  timestamps), FLAC/OGG (Vorbis comment `LYRICS`/`UNSYNCEDLYRICS`) y M4A (`©lyr`). Lee el archivo
  por ruta directa o por MediaStore. `EmbeddedLyricsReader.kt` pasa a ser una fachada que delega.
- `MediaFileUtils.kt`: prioridad embebido → archivos locales `.lrc`/`.txt` → MediaStore
  (`.lrc`/`.txt`), con ranking por nombre normalizado y decodificación robusta.
- `LrcParser.kt`: múltiples timestamps por línea, metadatos `[ar:]` ignorados, fracciones
  corregidas y letras planas representadas con `timestampMs = -1` (`Lyrics.isSynced`/`hasText`).
- `LyricsRepository.kt` + `SongDao.observeAllOnce()`: caché en `songs.lyricsText` (migración 2→3)
  y resolución por id, ruta o título sin escanear de más.
- `LyricsViewModel.kt`: cancela cargas obsoletas al cambiar de canción.
- `LyricsView.kt`: resalta la línea activa con auto-scroll suave, muestra letras planas
  desplazables y estado vacío "Sin letras disponibles"; recarga al cambiar id/título/ruta.
- `PlayerViewModel.kt`: limpia las líneas al cambiar de pista, carga en E/S y aplica en main,
  ignora metadatos, soporta varios timestamps y cachea el texto crudo en Room.
- Permisos: `READ_MEDIA_AUDIO` ya se solicita en `MainActivity` (Android 13+) y está en el
  manifiesto; el lector usa el `content://` de MediaStore cuando el acceso directo está restringido.

Archivos: `core/common/{LyricsTextUtils,EmbeddedLyrics,MediaFileUtils}.kt`, `core/model/Lyric.kt`,
`core/database/dao/Daos.kt`, `feature/player/{EmbeddedLyricsReader.kt,ui/PlayerViewModel.kt}`,
`feature/lyrics/{LrcParser,LyricsRepository}.kt`, `feature/lyrics/ui/{LyricsView,LyricsViewModel}.kt`.
Verificación: `./gradlew.bat :app:compileDebugKotlin --no-daemon` → sin errores.

## 2026-11-23 - Fase 49: lyrics cacheadas y espectro con AGC (v42)

- `EmbeddedLyricsReader` lee USLT/ULT del archivo de audio directamente en el reproductor y guarda el
  resultado en Room (`songs.lyricsText`, migración 2→3), de modo que la pantalla de letras lo reutiliza
  sin volver a escanear el archivo.
- `AudioSpectrumSource` añade control automático de ganancia y normalización exponencial lineal: el
  espectro sigue la intensidad real del PCM (RMS) sin saturarse ni quedarse quieto en canciones suaves.
- `versionCode` 42 / `1.0.41-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.41` con `musicplayer-42.apk` (3.466.164 bytes, SHA-256 `8E265262EA0C45A6B14F4326E41DAB626F3829673843235D58D93CD9D3855965`), verificada con `aapt` (`versionCode=42`).

## 2026-11-23 - Fase 48: lyrics incrustadas y barra de navegación teñida (v41)

- `EmbeddedLyrics`: lector de ID3v2 USLT/SYLT sin dependencias, para canciones que traen la letra dentro
  del MP3 (no como archivo `.lrc`). `MediaFileUtils.findLyricsText` lo intenta antes que los archivos.
- Armonía usa amplitudes mucho mayores (0.72 h / 0.48 h); rebote baja a 0.62 y ascenso a un techo de
  0.8 para que ninguna barra llegue arriba como antes.
- `NavBarTint`: la barra de navegación de Android toma el color del reproductor mientras suena música y
  vuelve a negro cuando no hay reproducción.
- `versionCode` 41 / `1.0.40-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.40` con `musicplayer-41.apk` (3.466.164 bytes, SHA-256 `56CDDB9D2A5532C0E84F53EC26CF43F06AECD45708B76B3C29E1FF7C4CA212BB`), verificada con `aapt` (`versionCode=41`).

## 2026-11-23 - Fase 47: mini barra, lyrics y visualizador intenso (v40)

- Mini-reproductor sin botón de siguiente y con esquinas superiores redondeadas.
- Búsqueda de `.lrc` en MediaStore con `LIKE '%.lrc'` y ranking por nombre normalizado (sin acentos ni
  puntuación), además de las rutas directas; más robusto en Android 13+.
- Visualizador con respuesta exponencial (1 - e^-x) recortada a 0..1: movimiento casi exagerado pero
  siempre dentro del canvas, sin saturación.
- `versionCode` 40 / `1.0.39-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.39` con `musicplayer-40.apk` (3.466.168 bytes, SHA-256 `FE96EF879E5560FCCEA9677C98CA50C9543C3546FCBAEDAAA027952816C92833`), verificada con `aapt` (`versionCode=40`).

## 2026-11-23 - Fase 46: fondo, mini-reproductor y letras (v39)

- **Fondo**: `LarkGradientBackground` deja pasar la imagen del usuario en modo plano (solo un velo
  ligero), así que se ve en biblioteca, canciones, playlists, cola, etc.; ajustes siguen sin imagen.
- **Mini-reproductor**: pasa a ser una barra inferior de ancho completo pegada abajo, como la original,
  sin borde en el botón de play y con botón de siguiente; superficie de color sólido derivado de la carátula.
- **Reproductor completo**: fondo sólido del mismo color de la carátula, sin afectar al resto de la app.
- **Letras**: `LyricsRepository.loadForSongId()` resuelve la fila exacta de MediaStore por id (ruta real),
  con fallback al título/artista; la pantalla de letras usa el id de la canción en reproducción.
- `versionCode` 39 / `1.0.38-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.38` con `musicplayer-39.apk` (3.466.168 bytes, SHA-256 `D96E61086FCA816B0F1789A78630DF7517605563D61BA2EC37AC1D98861E24EE`), verificada con `aapt` (`versionCode=39`).

## 2026-11-22 — Fase 45: visualizador PCM real sin permisos (v38)

- `PcmSpectrumProcessor`: `AudioProcessor` que deja pasar el PCM intacto y alimenta `AudioSpectrumSource`.
- `SpectrumRenderersFactory` instala el procesador en el `DefaultAudioSink` de ExoPlayer.
- `AudioSpectrumSource` calcula 16 bandas logarítmicas con el algoritmo de Goertzel sobre el PCM que va
  al altavoz; no se usa `android.media.audiofx.Visualizer` ni `RECORD_AUDIO`.
- `rememberAudioBands` sondea esas bandas a ~30 fps; las ondas/barras responden al audio real y no se
  dibuja nada cuando el visualizador está apagado.
- `versionCode` 38 / `1.0.37-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.37` con `musicplayer-38.apk` (3.466.168 bytes, SHA-256 `897D806545604450FD6B8858F9054A5B5696CC66558B0789F37DB7CA99FDC7CE`), verificada con `aapt` (`versionCode=38`).

## 2026-11-22 — Fase 44: navegación, modos, cola y fondo (v37)

- **Atrás** sale del menú actual y vuelve a la biblioteca (tanto el botón como el gesto del sistema),
  sin acumular el historial de ventanas.
- **Visualizador**: se eliminó `RECORD_AUDIO` y su solicitud; el capturador FFT sigue activo solo cuando
  el usuario lo enciende y no se dibuja nada cuando está apagado.
- **Modos aleatorios**: ahora son tres opciones excluyentes donde siempre hay una activa; pulsar aleatorio
  estando ya activo genera un orden nuevo. El orden se guarda en `SharedPreferences` y se reutiliza al
  reabrir la app mientras la cola sea la misma.
- **Cola**: al arrastrar una canción cerca de los bordes, la lista hace autoscroll para poder moverla lejos.
- **Fondo**: botón "Cambiar fondo de la app" amplio y en acento; la imagen de fondo se aplica en todas
  las vistas excepto ajustes.
- `versionCode` 37 / `1.0.36-clean`.

## 2026-11-22 — Fase 43: interfaz plana global y visualizador de estudio (v36)

- **Sin liquid glass en toda la app**: `LocalFlatSurface` por defecto `true` y proveedor global en
  `AppNavGraph`; todas las tarjetas, botones, menús y fondos usan superficies sólidas.
- **Visualizador solo si el usuario lo activa**: si está desactivado no se dibuja nada en la barra del
  reproductor (ni ondas ni línea de base). Se activa desde el panel de visualizador, solicitando
  `RECORD_AUDIO` cuando hace falta.
- **Espectro real por frecuencias**: `rememberAudioBands` captura FFT del `audioSessionId` de ExoPlayer
  (obtenido del servicio mediante comando personalizado) y calcula 16 bandas logarítmicas de 40 Hz a
  Nyquist; las barras y ondas responden a cada banda, no a una sinusoide fija.
- `versionCode` 36 / `1.0.35-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.35` con `musicplayer-36.apk` (3.466.184 bytes, SHA-256 `C54C7C05FE4D935459DAE1061F07B30ED202CD1CFCB11F1E0A2C8FF29D6842E1`), verificada con `aapt` (`versionCode=36`).

## 2026-11-22 — Fase 42: importación escaneando la BD local del original (v29)

- Pantalla de importación simplificada: solo **Importar de Lark Player**, basada en `vlc_database`.
- `BackupViewModel.findOriginalDatabase()` detecta el archivo copiado mediante `MediaStore.Downloads` y
  recorrido de Descargas/Documentos/almacenamiento de la app; si no existe, abre el selector SAF.
- `OriginalLibraryScanner` lee `playlist_table` + `playlist_media_table` + `media_table` con el mismo JOIN
  del original, ignora `INVALID_*`, empareja por ruta/nombre de archivo y conserva `playlist_order`.
- Se eliminaron definitivamente de la UI el importador LPBK, los `.info` de Drive, el ZIP y `vlc_database`
  como opciones separadas (ahora es la única).
- `versionCode` 29 / `1.0.28-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.28` con `musicplayer-29.apk` (3.422.811 bytes, SHA-256 `8DCEA66B6F000C429294870B61884E6399D5D5A46067FAF393C03DA58ADE4380`), verificada con `aapt` (`versionCode=29`).

## 2026-11-22 — Fase 41: importador local en solitario y diagnóstico ZIP (v28)

- `BackupScreen` reducida a una sola función: detectar/restaurar la copia local `Backup_of_Lark_Player_*.lrc`.
  Se eliminaron exportar ZIP, importar ZIP, `vlc_database` y los `.info` de Drive.
- `LpbkImporter` valida que el payload descifrado empiece por `PK`, prueba `ZipFile` y, si falla,
  `ZipInputStream`, e incluye el cabezal y el error real en el mensaje para diagnosticar en dispositivo.
- `versionCode` 28 / `1.0.27-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.27` con `musicplayer-28.apk` (3.422.811 bytes, SHA-256 `734CFAE75FE9558BA07B1EF2DBA421BD921E5E758EFD910A3E4894FEFB9390FD`), verificada con `aapt` (`versionCode=28`).

## 2026-11-22 — Fase 40: lectura ZIP robusta y detección MediaStore (v27)

- El payload descifrado se escribe a un archivo temporal y se abre con `java.util.zip.ZipFile`
  (directorio central) en vez de `ZipInputStream`; Android lanzaba *invalid stored block lengths* con los
  descriptores de datos del escritor original. Validado con Java sobre el archivo real: 7 playlists,
  377 canciones y portada.
- Detección mejorada: `BackupViewModel.findLocalBackup()` consulta primero `MediaStore.Downloads`
  (sin permisos de almacenamiento) y después las carpetas conocidas.
- `versionCode` 27 / `1.0.26-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.26` con `musicplayer-27.apk` (3.422.815 bytes, SHA-256 `2D8C6D694E73EABCB3102B7B36289E4CF6CCF7FFC88E4973C7E91E1BC2A2E971`), verificada con `aapt` (`versionCode=27`).

## 2026-11-22 — Fase 39: corrección del importador LPBK en release (v26)

- `LpbkImporter` ya no usa Gson: parsea `manifest.json`, `media.json` y `playlists.json` con `org.json`,
  evitando el error *“Abstract classes can't be instantiated”* que R8 causaba en las clases ofuscadas.
- Regla ProGuard añadida para conservar los modelos de `feature:backup` usados por Gson en el resto de
  importadores (`.info` de Drive).
- `versionCode` 26 / `1.0.25-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.25` con `musicplayer-26.apk` (3.422.815 bytes, SHA-256 `EC31BD1B4180EE30D98787510A2CEA7C19C85589B3A828005AB8430F008C7786`), verificada con `aapt` (`versionCode=26`).

## 2026-11-22 — Fase 38: importador de copia local LPBK (v25)

- Descargado y descompilado el APK original `2027.3.6 (2027030609)` para inversión del formato local.
- `LpbkImporter.kt`: lee los archivos `Backup_of_Lark_Player_*.lrc` (magic `LPBK`):
  - deriva las claves con `HMAC-SHA256` de la clave histórica y las etiquetas `lpbk-enc`/`lpbk-mac`;
  - verifica el HMAC de 32 bytes y descifra con `AES/CTR/NoPadding` usando el IV de la cabecera;
  - interpreta el payload ZIP (`manifest.json`, `sections/media.json`, `sections/playlists.json`);
  - ignora playlists virtuales `INVALID_*`, respeta `order`, empareja canciones por nombre/ruta y restaura
    también la carátula desde `files/covers/`.
- `BackupScreen` detecta automáticamente la copia más reciente en Descargas/Documentos y ofrece
  **Restaurar copia local detectada** con un toque, además del selector manual.
- Los tres `.info` de Drive y la opción de `vlc_database` siguen disponibles en la misma pantalla.
- `versionCode` 25 / `1.0.24-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.24` con `musicplayer-25.apk` (3.406.427 bytes, SHA-256 `935441D4E5EE21E0B48C44F61B1DA5A50F744F97FDCB428293A546590B8E1BD2`), verificada con `aapt` (`versionCode=25`).

## 2026-11-22 — Fase 37: importación desde `vlc_database` (v24)

- `OriginalLibraryScanner.kt`: lee una **copia** de `vlc_database` del original (SQLite v75) con el mismo
  JOIN que usa la app (`playlist_table` + `playlist_media_table` + `media_table`), ignora playlists
  `INVALID_*` y recrea las playlists en Room conservando `playlist_order`.
- Emparejamiento de canciones: ruta (`file:`/`content:` → MediaStore) → nombre de archivo → título, con
  normalización de acentos, reutilizando la biblioteca ya escaneada.
- **No es posible sin la copia del archivo**: los 16 providers del original son `exported=false`, así que
  `ContentResolver` no puede leer su BD. La UI indica que hay que cerrar el original y seleccionar el archivo
  por SAF (o extraerlo con `adb pull` en dispositivos rooteados).
- `BackupScreen` añade la tarjeta **Importar vlc_database del original**, con contadores de canciones
  vinculadas/sin archivo local.
- `versionCode` 24 / `1.0.23-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.23` con `musicplayer-24.apk` (3.406.427 bytes, SHA-256 `52932853E16DD758F4A6C130D9C40C8E7A384267A3162F86B0D53192FB60A316`), verificada con `aapt` (`versionCode=24`).

## 2026-11-22 — Fase 36: transiciones, controles grandes y fondo personalizable (v23)

- **Transiciones fluidas** (`AppNavGraph.kt`): entradas/salidas con `tween` y `FastOutSlowInEasing`; el
  reproductor entra y sale deslizándose desde abajo, el resto de vistas horizontalmente.
- **Controles del reproductor más grandes** (`PlayerScreen.kt`): anterior/siguiente 68 dp (icono 38),
  play 96 dp (icono 56) y botones con escala elástica al pulsar; crossfade del icono play/pausa más suave.
- **Fondo personalizable**:
  - `SettingsDataStore` guarda `app_background_uri` y `background_variant`.
  - `AppBackdrop` + `LocalAppBackground` en `core:designsystem` pintan el fondo (color o imagen con velo)
    detrás de toda la app; `LarkGradientBackground` deja ver la imagen mediante alfas.
  - `ThemeScreen` añade **20 variantes de color** en rejilla y un selector de imagen que se copia a
    `filesDir/app_background.jpg`, con opción de quitarla. Las pantallas de ajustes ahora son transparentes.
- **Backup de playlists compatible con el original** (`feature:backup`):
  - `OriginalFormat.kt` replica exactamente los nombres JSON (`playlistInfos`, `playlist_name`,
    `playlist_id`, `createTime`, `cover`, `items`, `songs_name`, `size`, `play_count`) y el cifrado
    AES/ECB/PKCS5Padding con la clave del APK original, incluido el atajo para JSON sin cifrar.
  - El ZIP exportado incluye `backup.json` (formato propio, ahora con las canciones de cada playlist) y
    los tres `.info` del original con sus nombres exactos. Opción **Incluir canciones** añade `Music/<archivo>`.
  - La importación detecta ambos formatos, fusiona playlists por nombre en vez de borrar la biblioteca,
    reempareja las canciones por nombre de archivo y, si el ZIP trae audio, lo copia y vuelve a escanear.
  - `BackupScreen` detecta automáticamente un ZIP en Descargas o en la carpeta de backups y ofrece
    restaurarlo con un toque.
  - **Importación directa desde Lark Player**: `BackupScreen` permite seleccionar uno o varios `.info`
    originales y muestra los que detecta en Descargas / carpetas de la app; `importOriginalInfo()` los
    descifra, fusiona las playlists y vincula las canciones por nombre de archivo con la biblioteca actual.
- `versionCode` 23 / `1.0.22-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.22` con `musicplayer-23.apk` (3.406.427 bytes, SHA-256 `786FA343C98147CF36A01D58932E3F2D0886B9D80A49A1B43AF6DF818F81009C`), verificada con `aapt` (`versionCode=23`). Detalle en [`17-BACKUP-COMPATIBLE.md`](17-BACKUP-COMPATIBLE.md).

## 2026-11-21 — Fase 35: arrastre tipo launcher y crossfade sin tirones (v22)

- **Cola y playlist**: las filas que no se están arrastrando usan `Modifier.animateItemPlacement()` con un
  `spring`, de modo que la lista **se desplaza suavemente** al reordenar, sin saltos. La fila levantada se
  mantiene aparte (escala 1.06, sombra 24 dp, ligera rotación y seguimiento con `spring` rígido), como un
  icono del escritorio de Android.
- **Crossfade** (`PlaybackService.kt`):
  - La duración del fundido es ahora el tiempo real que queda en la pista (mínimo 400 ms, máximo el ajuste),
    no siempre el valor completo: evita que al cambiar de reproductor se salten segundos de la canción.
  - El `seekTo` de traspaso usa esa misma duración, para que el punto de entrega coincida con lo que sonaría
    ya en el segundo reproductor.
  - El pause interno provocado por `pauseAtEndOfMediaItems` ya no cancela el crossfade; solo cancela una
    pausa real del usuario lejos del final. Resultado: transición curva, sin pausa ni bloqueo.
- `versionCode` 22 / `1.0.21-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.21` con `musicplayer-22.apk` (3.390.047 bytes, SHA-256 `05017DD4AD67440F69B2223C5A7436ADB471AE79F105DE641B3715B604964717`), verificada con `aapt` (`versionCode=22`).

## 2026-11-21 — Fase 34: bootstrap en segundo plano, caché de carátulas y estado de reproducción (v21)

- **WorkManager fuera del arranque**: se eliminó su `ContentProvider` inicializador del manifest y
  `LarkApp` ya no implementa `Configuration.Provider`. WorkManager se inicializa manualmente 1,5 s después,
  en cuanto la interfaz ya está visible; las reglas de actualización siguen funcionando.
- **Caché de carátulas**: `LarkApp` implementa `ImageLoaderFactory` de Coil con caché en disco
  (`lark_image_cache`, 64 MB) y memoria (25 %). El `ImageLoader` se crea solo ante la primera imagen, es
  decir, después del primer frame. Las carátulas y el `AlbumColorRepository` se sirven desde caché en
  arranques posteriores.
- **Caché del estado de reproducción** (`PlayerRepository.kt`): la cola, el índice, la posición y si sonaba
  se guardan en `SharedPreferences` mediante un snapshot tomado siempre en el hilo principal y serializado
  en IO. Al conectar el `MediaController`, si el servicio no tiene ítems, se restaura la cola y la posición,
  reanudando solo si estaba reproduciéndose. La X y "vaciar cola" borran la caché. Mantiene la reproducción
  aunque el proceso del servicio muera con la app cerrada.
- **Paridad con el servicio original** (analizado en `jadx_full`): canal de notificación `player_service_v2`
  (importancia por defecto, sin sonido ni badge, id 3) mediante `DefaultMediaNotificationProvider`, igual que
  el APK original. Se confirma que el original NO usa `START_STICKY`, alarmas ni `BOOT_COMPLETED` para
  reproducir: se apoya en FGS + `stopWithTask=false` + `PARTIAL_WAKE_LOCK`, que ya tenemos. No se replican
  el proceso `:playback`, iJKPlayer, MMKV ni los servicios de retención de anuncios.
- **APK de release**: el `buildType` release se firma con la clave debug para poder instalarlo desde el
  actualizador y publicar una APK ofuscada por R8 (menos DEX, arranque más rápido).
- `versionCode` 21 / `1.0.20-clean`; `assembleRelease` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.20` con **`musicplayer-21.apk` de release** (3.373.659 bytes, SHA-256 `1C7919993697B3AD0B9E9E392B45012B2D1D7501B29A500186166A6C7C4114D9`), frente a los 23.101.828 bytes de la APK debug. Verificada con `aapt`: `versionCode=21`, `versionName=1.0.20-clean`, actividad `com.larkplayer.app.MainActivity`. Detalle en [`16-REPRODUCCION-FONDO-Y-CACHE.md`](16-REPRODUCCION-FONDO-Y-CACHE.md).

## 2026-11-21 — Fase 33: apertura instantánea sin splash (v20)

- **Sin pantalla de carga**: `themes.xml` usa un fondo sólido igual al de la app (`app_background`,
  `#0A0A0B`), `windowSplashScreenAnimatedIcon=@null`, icono de splash transparente y
  `windowContentOverlay=@null`. La ventana ya está pintada con el color final antes del primer frame.
- **Nada de diálogos en el arranque**: los permisos se piden a los 1,2 s y el aviso de batería a los 4 s;
  la biblioteca se pinta de inmediato desde la caché de Room.
- **Menos trabajo en el primer frame**:
  - `AppNavGraph` ya no crea `PlayerViewModel` ni el efecto de mantener pantalla encendida al iniciar; ese
    efecto vive ahora en `PlayerScreen`.
  - La mini-reproductora (y su grafo Hilt) se compone 1 s después del primer frame.
  - `PlayerRepository` pospone la conexión del servicio 900 ms y el escaneo espera la señal posterior al frame.
  - `allowBackup=false`: se eliminan las reglas de backup/restauración del arranque.
- `versionCode` 20 / `1.0.19-clean`; `assembleDebug` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.19` con `musicplayer-20.apk` (23.101.828 bytes, SHA-256 `0FF741BEBC7CE5410FA8EA8F8766080769AE94AD330F4E4271DE2B27BABA4D15`). Verificada como última release, sin draft/pre-release y con el asset `uploaded`.

## 2026-11-21 — Fase 32: reproductor con pie anclado y controles grandes (v19)

- `PlayerScreen.kt`: el reproductor ampliado usa ahora un `Box` con la columna desplazable y un **pie fijo
  anclado al borde inferior** (incluido el inset de la barra de navegación). El contenido tiene 104 dp de
  padding inferior para no quedar tapado, así que desaparece el rectángulo negro bajo los controles.
- Controles ampliados y más separados: anterior/siguiente **62 dp** (icono 36), play **86 dp** (icono 52),
  separación **34 dp**; favorito 48 dp, más opciones 44 dp; EQ/cola **52 dp** (icono 27) y "Letras" 16 sp.
- `versionCode` 19 / `1.0.18-clean`; `assembleDebug` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.18` con `musicplayer-19.apk` (23.101.436 bytes, SHA-256 `B76B1098B02F817C9A04374095752D79B91E87F1BD6923FCD08D87C35FF335B1`). Verificada como última release, sin draft/pre-release y con el asset `uploaded`.

## 2026-11-21 — Fase 31: arranque, pie del reproductor y crossfade real (v18)

- **Arranque más rápido**:
  - `PlayerRepository`: no enlaza el servicio de reproducción durante los primeros frames; la conexión se
    pospone 900 ms y cualquier acción de play la fuerza de inmediato. Los colectores de ajustes ya no llaman
    al controlador al inicio (los valores se aplican al conectar).
  - `LibraryViewModel`: el escaneo de MediaStore espera al evento que `MainActivity` emite tras el primer frame;
    queda un fallback de 1,5 s para otras entradas. Evita consultar MediaStore compitiendo con la primera composición.
  - `MainActivity`: el diálogo de optimización de batería se pospone 2,5 s para no robar el foco inicial.
  - `LarkApp`: la comprobación de actualizaciones pasa de 3 s a 20 s.
- **Pie del reproductor** (`PlayerScreen.kt`, `LarkComponents.kt`): el degradado ya no termina en un bloque
  negro plano (ahora 0.68→1.0 es un degradado suave) y la fila EQ/Letras/Cola va sobre un pie sólido con
  esquinas redondeadas, de modo que la parte inferior no parece un recuadro vacío.
- **Crossfade real** (`PlaybackService.kt`): se usa un segundo `ExoPlayer` con la siguiente canción; ambos
  streams se mezclan en el mezclador de audio del sistema mientras sus volúmenes se cruzan con un `ValueAnimator`.
  El reproductor principal mantiene la pausa al final del tema (`pauseAtEndOfMediaItems`) y al terminar la mezcla
  retoma la siguiente canción en el punto alcanzado. Se cancela al pausar, saltar, parar o destruir el servicio.
  `PlayerRepository` ya no hace falsos fades de volumen; el gapless sin pausas sigue funcionando.
- `versionCode` 18 / `1.0.17-clean`; `assembleDebug` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.17` con `musicplayer-18.apk` (23.101.436 bytes, SHA-256 `A23588722E995B5EE92ED4C10BB133FA590D45740C4B87470772D714175AE2A9`). Verificada como última release, sin draft/pre-release y con el asset `uploaded`.

## 2026-11-21 — Fase 30: avance por orden, barra inferior y diseño plano (v17)

- **Avance correcto al terminar una canción** (`PlayerRepository.kt`):
  - `sendShuffleOrder()` instala el orden personalizado **antes** de activar `shuffleModeEnabled`; antes se activaba
    primero el shuffle y ExoPlayer generaba un orden aleatorio propio que podía avanzar la pista.
  - Listener de transiciones: si con shuffle activo ExoPlayer elige un tema distinto del siguiente mostrado en
    "A continuación", se corrige de inmediato con `seekTo` al índice esperado (respetando `REPEAT_MODE_ALL`).
- **Playlist sin hueco negro** (`PlaylistDetailScreen.kt`, `AppNavGraph.kt`):
  - La mini-reproductora flotante se oculta en la pantalla de playlist.
  - El `LazyColumn` ya no reserva 110 dp vacíos; en su lugar hay una barra inferior fija con botones grandes
    (58 dp de alto, 16 dp de separación): **Reproducir** (relleno) y **Aleatorio**.
- **Diseño plano en playlist y ajustes**:
  - Nuevo `LocalFlatSurface` en `core:designsystem`, activado por `AppNavGraph` en las rutas de playlist y ajustes.
  - `LarkCard`, `LarkGradientBackground`, `LarkPill`, `GlassIconButton`, `GlassIconCluster`, `GlassPillButton`,
    `GlassPill`, `GlassTag`, `GlassSelectionBar` y `LarkDialog` sustituyen degradados, bordes y sombras por
    superficies sólidas cuando el modo plano está activo. El reproductor conserva su aspecto original.
- `versionCode` 17 / `1.0.16-clean`; `assembleDebug` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.16` con `musicplayer-17.apk` (23.101.436 bytes, SHA-256 `62094B84BA3FC115794FD8F79D00FE19240604AF352D0FE913F0150C415F40A8`). Verificada como última release, sin draft/pre-release y con el asset `uploaded`.

## 2026-11-21 — Fase 29: orden visual del reproductor y arrastre elevado (v16)

- **`PlayerScreen.kt`**: reorganizado el reproductor para evitar elementos solapados y márgenes excesivos.
  - El contenido ahora es una columna con scroll y espaciado uniforme (`Arrangement.spacedBy(12.dp)`), sin
    espaciadores `weight` que comprimieran la pantalla cuando faltaba altura.
  - Carátula cuadrada de hasta 224 dp centrada (antes podía crecer hasta el ancho de la pantalla y empujar
    el título sobre la carátula/placeholder).
  - Título 20 sp / 2 líneas, artista 13 sp, controles uniformes: 52 dp (anterior/siguiente), 72 dp (play),
    44 dp (favorito, EQ, cola), 40 dp (más opciones); márgenes laterales 16 dp.
  - El gesto de cerrar se movió solo al chevron para no pelear con el scroll; el lavado de acento bajó a 0.34.
- **`PlaylistDetailScreen.kt`**: el orden manual ya no usa flechas; la fila tiene un tirador a la derecha que,
  al mantener pulsado, **se eleva** (escala 1.035, sombra 18 dp, fondo opaco) y sigue el dedo con un `spring`.
  Los intercambios se aplican en vivo y se persisten con `moveSong`.
- **`QueueScreen.kt`**: el mismo efecto de elevación y seguimiento animado al arrastrar canciones de la cola.
- `versionCode` 16 / `1.0.15-clean`; `assembleDebug` → BUILD SUCCESSFUL.
- Publicada la release `v1.0.15` con `musicplayer-16.apk` (23.101.436 bytes, SHA-256 `A81F45A8A36101AF8E96E951A7A7E84216F01A1731DB4C6C530E525CEB412492`). Verificada como última release, sin draft/pre-release y con el asset `uploaded`.

## 2026-11-21 — Fase 28: fidelidad al reproductor original y acciones funcionales (v15)

Dos commits: el primero ajusta la interfaz al APK original; el segundo conecta las acciones que antes no hacían nada.

**Commit 1 — Reproductor fiel al original**
- `PlayerScreen.kt`: eliminada la barra superior (flecha + "Reproduciendo" + 3 puntos), sustituida por un chevron centrado y gesto de arrastre hacia abajo para cerrar; portada limitada a 239 dp; título blanco a 2 líneas (antes acento y 1 línea); favorito y 3 puntos a la derecha del título; transporte reducido a anterior / play / siguiente; píldora de velocidad solo cuando es distinta de 1x; formato de velocidad con `Locale.US` (corrige "2,x" en español); modo como badge sobre el icono de cola; cola duplicada eliminada; EQ y cola sin etiqueta, solo "Letras" con texto.
- `LiquidGlass.kt`: nuevo parámetro `flat` en `GlassIconButton` para iconos planos con ripple (solo play mantiene el círculo).
- `MiniPlayer.kt`: botón de modo único (normal/repetir/repetir una/aleatorio) + siguiente, en lugar del shuffle aislado.
- `LibraryScreen.kt`: retirado el icono Radio de la cabecera; añadida marca de app junto al wordmark.
- `AndroidManifest.xml`: `android:label="Lark Player"`.

**Commit 2 — Acciones funcionales y motor**
- `PlayerScreen.kt` + `PlayerViewModel.kt`: la hoja de opciones ya permite elegir o crear playlist, compartir (ACTION_SEND), poner como tono (pide `WRITE_SETTINGS` y usa `RingtoneHelper`), ocultar y borrar (confirmación + `MediaStore.createDeleteRequest`). Eliminado el icono Editar inerte.
- `PlayerRepository.kt`: el shuffle mantiene `REPEAT_MODE_ALL` (la cola ya no se detiene al terminar); añadir/quitar/reordenar conserva el orden visible en vez de volver a barajar; `toggleShuffle` pasa por la API de modo único; "Reproducción sin pausas" ahora se aplica insertando una breve pausa cuando está desactivada; `clearQueue()`.
- `QueueScreen.kt`: acciones "ir a la canción actual" y "vaciar cola" con confirmación, propias del original.
- `PlaybackService.kt`: la X de la notificación ya no llama a `exitProcess(0)`; solo detiene, limpia y quita la notificación sin matar la UI abierta.
- `LibraryScreen.kt`: compartir varias seleccionadas usa `ACTION_SEND_MULTIPLE` en lugar de enviar solo la primera.
- `PlaybackSettingsScreen.kt`: subtítulo de crossfade honesto (desvanecimiento de entrada/salida, sin prometer superposición).
- `build`: `assembleDebug` → BUILD SUCCESSFUL; `versionCode` 15 / `1.0.14-clean`.
- Publicada la release `v1.0.14` con `musicplayer-15.apk` (23.085.052 bytes, SHA-256 `04BB5117967BD6E86E9ABE5EF8A3F5C89570F6088B1FC1054C1C00FD0A3C6E2A`). Verificada con la API: es la última release, no es draft ni pre-release y el asset está `uploaded`. Detalle completo en [`15-AUDITORIA-ORIGINAL.md`](15-AUDITORIA-ORIGINAL.md).

## 2026-11-21 — Fase 27: publicación v14

- Commits `49ba317` (diseño satinado y animaciones) y `d9a496d` (correcciones de aleatorio y drag-reorder), confirmados en `origin/master`.
- Release pública `v1.0.13` con `musicplayer-14.apk` (23.052.280 bytes, `versionCode` 14, SHA-256 `b54d4003c00eb2f07357a0b3778847527d399a2a8eb32168c16b9eaedc48133a`). Verificado con la API: es la última release, no es draft ni pre-release y el asset está cargado.

## 2026-11-21 — Fase 26: eliminación de liquid glass y nuevo diseño elegante satinado con animaciones fluidas (v13)

- **Eliminación del Liquid Glass**:
  - Reemplazo de los fondos hiper-traslúcidos y brillos de vidrio por superficies satinadas elegantes carbón mate (`0xFF26272C` a `0xFF1C1D21` y `0xFF1A1B1F`), eliminando la sensación "plástica" o transparente.
  - Se mantuvieron sutiles contornos de luz satinada y profundidad visual para garantizar un aspecto elegante, moderno y refinado sin caer en un diseño plano o aburrido.
- **Botón de reproducción táctil y fluido**:
  - Nuevo gradiente satinado con borde fino de relieve y micro-animación fluida de escala al presionar / pausar.
  - Transiciones fluidas en portadas, títulos y deslizamiento táctil horizontal con amortiguación suave.
- **Incremento a VersionCode 13 (`1.0.12-clean`)**: build exitoso con APK listo para distribución (`musicplayer-13.apk`, SHA-256 `7F196A9527738A0DAFD27604CD3A839776EEDEA53F4ECF49DD5F906C3AACBC46`).

## 2026-11-21 — Fase 25: publicación v12

- Commits `3e736a0` (animaciones/swipe y fixes de aleatorio) y `8db3b5c` (márgenes, espaciados y retroalimentación On/Off), hechos por otra IA, confirmados en `origin/master`.
- Release pública `v1.0.11` con el asset `musicplayer-12.apk` (23.052.280 bytes, `versionCode` 12, SHA-256 `5f361cd011f8d92e821d714a2ae6860ac1f176a5b378c8de9cc6065bd8bcbe96`). Publicada vía API de GitHub sin navegador; `/releases/latest` devuelve v1.0.11 con el asset cargado.

## 2026-11-21 — Fase 23: publicación v10

- Commit `fc8f044` (réplica de diseños/funciones) y `e22f068` (APK) enviados a `origin/master`.
- Release pública `v1.0.9` con el asset `musicplayer-10.apk` (23.052.280 bytes, `versionCode` 10, SHA-256 `2603f7c19e7485296442478606ead31a031eb13946dd3cdccfef4db6821fb8f8`). Publicada mediante la API de GitHub con las credenciales del repositorio, sin abrir el navegador.

## 2026-11-21 — Fase 24: corrección de márgenes, espaciados y retroalimentación On/Off

- **Retroalimentación animada On/Off en modo de reproducción**:
  - `PlayerScreen.kt`: botón de cambio de modo con micro-animación de escala (`1.08f` activo vs `1.0f` inactivo) y mayor contraste de fondo (`containerAlpha = 0.22f` cuando está activo vs `0.08f` inactivo).
  - `MusicVisualizerSheet.kt`: Switch con colores fieles y contraste sólido (blanco + color de acento y gris neutro `0xFF47474C` en OFF con bordes limpios).
- **Ajustes de márgenes y espaciados**:
  - Ampliado el espacio de la barra inferior de acciones (Ecualizador, Letras, A continuación) a `top = 16.dp, bottom = 12.dp` para evitar que quede pegada a la botonera principal o al borde del dispositivo.
  - Añadido margen horizontal a los textos de tiempo transcurrido y duración (`0:00` / `3:45`) para no rozar los bordes de la pantalla.
- **Incremento a VersionCode 12 (`1.0.11-clean`)**: build exitoso con APK listo para distribución.

## 2026-11-21 — Fase 23: animaciones de desplazamiento, gestos swipe y sincronización de aleatorio

- **Desplazamiento horizontal de canciones (Swipe en Player)**:
  - Soporte de gestos táctiles directos sobre la carátula (`detectHorizontalDragGestures`): deslizar hacia la izquierda pasa a la siguiente canción y deslizar hacia la derecha vuelve a la anterior.
  - Transición animada de desplazamiento lateral y desvanecimiento (`AnimatedContent` con `slideInHorizontally` + `slideOutHorizontally` + `fadeIn` / `fadeOut`) tanto en la portada como en el título de la canción.
- **Corrección de sincronización en aleatorio**:
  - Se añadió un listener de ciclo en `PlayerRepository.kt` para garantizar que cuando ExoPlayer avanza pistas de manera desatendida o automática, el orden visible (`visibleOrder`) y la cola se mantengan íntegros sin saltos no esperados.
- **Incremento a VersionCode 11 (`1.0.10-clean`)**: build exitoso con APK listo para distribución.

## 2026-11-21 — Fase 22: réplica exacta de diseños y funciones del Lark Player original

- **Visualizador de música interactivo (`MusicVisualizerSheet.kt` y `VisualizerWave`)**:
  - Hoja inferior idéntica a la captura del original: interruptor maestro para habilitar/deshabilitar y 3 modos seleccionables: **Armonía** (ondas líquidas fluidas), **Rebote** (barras con rebote rítmico) y **Ascenso** (espectro creciente).
  - La onda animada se integra sobre la barra de progreso en `PlayerScreen.kt` y pulsa sincronizada con la música; al tocarla abre el selector de estilos.
  - Persistencia de preferencias del visualizador en `SettingsDataStore` (`visualizer_enabled`, `visualizer_style`).
- **Hoja completa de opciones del reproductor (`PlayerMoreSheet.kt`)**:
  - Reemplazo del menú flotante por la hoja inferior fiel a la captura original: cabecera con miniatura, título, artista e icono de lápiz para edición.
  - Acciones organizadas por secciones: *Agregar a la lista de reproducción*, *Compartir*, *Visualizador de música*, *Velocidad*, *Establecer como tono de llamada*, *Temporizador para dormir*, *Ocultar* y *Borrar*.
- **Cola de reproducción ("Fila") (`QueueScreen.kt`)**:
  - Cabecera actualizada a «Fila» con contador y diseño de controles inferiores con cápsulas anchas (`width = 82.dp`), resaltando el botón de **Aleatorio** en fondo blanco cuando está activo, igual al original.
- **Pestaña Playlists y Mini-player (`LibraryScreen.kt` y `MiniPlayer.kt`)**:
  - Playlists ahora se muestran en tarjetas oscuras individuales con esquinas redondeadas (22 dp, color `#1B1B1E`), botón centrado «+ Nueva playlist» y carátula de 62 dp.
  - `MiniPlayer.kt` rediseñado con fondo carbón `#222326` y botón directo de aleatorio/shuffle junto al botón principal de reproducción.

## 2026-11-21 — Fase 21: sistema de reproducción, optimización de arranque y animaciones

- **Aleatorio (Shuffle) fiel al original**: `VisibleShuffleOrder` en `PlaybackService` implementa el contrato completo de `ShuffleOrder` con inserciones, eliminaciones y saltos circulares respetando `REPEAT_MODE_ALL`. `skipNext()` y `skipPrevious()` en `PlayerRepository` respetan el orden visible y reinician la canción a 0:00 si han transcurrido más de 3 segundos antes de retroceder.
- **Optimización de arranque**: en `LarkApp.onCreate` se delegaron la comprobación de actualizaciones y el instalador de excepciones a un hilo `Dispatchers.IO` secundario. En `MainActivity.onCreate` los chequeos de batería y permisos se ejecutan de manera diferida mediante `window.decorView.post` para pintar el primer frame de inmediato (< 150ms).
- **Selector de modo en Player**: botón interactivo en la botonera de `PlayerScreen` que cicla entre Normal, Repetir todo, Repetir una y Aleatorio con iconos y tinte de acento reactivos.
- **Animaciones al reproducir**:
  - Efecto de resorte (`spring`) y escalado dinámico en la carátula de `PlayerScreen` (se expande a 1.0f con 28 dp de elevación al reproducir y se contrae a 0.94f al pausar).
  - Micro-interacción con `animateFloatAsState` y transición fluida de iconos con `Crossfade` al pulsar Play/Pausa tanto en `PlayerScreen` como en `MiniPlayer`.

## 2026-11-21 — Fase 20: release publicado y versión 9

- **Actualización publicada**: release `v1.0.8` en `Diego-VM-x/musicplayer` con el asset `musicplayer-9.apk` (`versionCode` 9, SHA-256 `fe0f0464…`). El actualizador ya puede detectarlo: el parser obtiene el código del nombre del APK/body del release y el digest SHA-256 del asset.
- La causa del fallo anterior era externa: el repositorio no tenía releases ni APKs (API devolvía `[]`). Ya no es necesario subir nada manualmente.

## 2026-11-21 — Fase 19: aleatorio fresco, reordenado y navegación

- **Aleatorio**: cada activación genera un orden nuevo sin semilla fija; la canción actual va primero y el resto se baraja al azar. El orden se envía a Media3 vía `ShuffleOrder` y al desactivarse se restaura el original.
- **Cola reordenable**: tirador a la derecha de cada fila; pulsación larga y arrastre mueven la canción en la timeline real (`moveMediaItem`), con el orden local animado.
- **Botones de la cola**: se añadió padding inferior para que el mini-player no tape repetir/aleatorio.
- **Navegación**: todas las rutas usan `launchSingleTop` + `restoreState`, de modo que el botón atrás no reapila ventanas ya abiertas.
- **Mini-player**: fondo del color de la reproducción sin blur ni imagen de fondo.

## 2026-11-21 — Fase 18: logo, widget, recuerdos y aleatorio ordenado

- **Logo propio**: icono adaptativo vectorial (nota musical sobre degradado índigo/azul) con recursos `mipmap` para todas las densidades; eliminado el icono por defecto de Android.
- **Widget de pantalla de inicio**: `LarkWidgetProvider` con título/artista y controles anterior/play-pausa/siguiente. `PlayerRepository` publica el estado en `SharedPreferences` y emite `UPDATE_WIDGET` al cambiar de pista o de estado.
- **Recuerdos musicales** (`feature:library/ui/MemoriesScreen.kt`): nuevas consultas Room de canciones más reproducidas (`TopSong`) y accesible desde Ajustes; muestra tiempo total y top 5 reproducible.
- **Aleatorio ordenado**: al activar shuffle, la canción actual pasa a la primera posición y el resto se baraja de forma estable; el mismo orden se muestra en la cola y se envía a Media3 mediante un `SessionCommand` que aplica un `ShuffleOrder` personalizado, así reproducción y visualización coinciden (también desde la notificación). Al desactivarlo se restaura el orden original.
- **Blur del mini-player**: imagen de carátula desenfocada (32 dp) bajo un degradado radial teñido con el acento extraído; altura fija para evitar el desbordamiento anterior.
- **Actualizador**: añadido fallback a `/contents/` para detectar APKs subidos a la raíz del repositorio (o `update.json`) cuando aún no hay releases publicados.
- Generación de imagen externa falló por credenciales no válidas; el logo se creó como vector + PNG locales.

## 2026-11-21 — Fase 17: sesión pausada persistente

- El wake lock parcial ya no se libera al pausar: se mantiene mientras el ajuste «Reproducir en segundo plano» esté activo, porque al pausar y cerrar la app el sistema podía congelar el proceso y perder la sesión (al reproducir no ocurría).
- `onStartCommand` devuelve `START_STICKY` cuando se permite segundo plano y `onTaskRemoved` evita detener el servicio al deslizar la app desde Recientes.
- Se liberan el wake lock y la sesión solo desde el botón X de la notificación o al destruir el servicio.

## 2026-11-21 — Fase 16: repetición de cola, acento acotado y aleatorio visible

- **Fin de lista**: `REPEAT_MODE_ALL` es el modo por defecto (`SettingsDataStore` y `PlayerViewModel`), así la cola vuelve a empezar al terminar en vez de detenerse.
- **Acento por canción acotado**: la biblioteca usa un color fijo; solo el reproductor y el mini-player toman el color de la carátula.
- **Mini-player global**: se renderiza en la capa de navegación y aparece en todas las vistas salvo el reproductor, los ajustes y el editor de playlist (incluido el recorte de portada, que lo oculta vía `MiniPlayerOverlay`).
- **Aleatorio visible**: `PlayerRepository.displayQueue()` devuelve un orden barajado determinista mientras shuffle está activo; la cola lo muestra con animación y, al desactivar shuffle, recupera el orden original. Las pulsaciones se mapean al índice real del `MediaController`.

## 2026-11-21 — Fase 15: actualizador fijo y optimizaciones REA

- **Actualizador**: repositorio `Diego-VM-x/musicplayer` fijado en `AppUpdate.GITHUB_REPO`; eliminado por completo el campo editable de Ajustes → Actualizaciones. Consulta `/releases` (no `/releases/latest`) y elige el release público más reciente; un repo sin releases devuelve «sin novedades» en vez de error. Filtra drafts/prereleases y extrae versión del asset, del body, del tag o del nombre. Descarga automática activa por defecto, sin exigir SHA-256.
- **Tercer plano**: `android:stopWithTask="false"` en `PlaybackService`, wake lock parcial, notificación en primer plano y solicitud única de excepción de batería. Comprobado con REA/jadx que el original usa además un proceso `:playback` aislado (no replicado por su coste de IPC).
- **Arranque**: la comprobación de actualizaciones se difiere 1,5 s a un hilo de E/S para no competir con el primer frame; la biblioteca ya reutilizaba la caché de Room 10 min.
- **Velocidad de reproducción**: nueva preferencia 0,5x–2x, restaurada al conectar y accesible desde una píldora en el reproductor (función que faltaba frente al original).
- **Pantalla encendida**: se mantiene `FLAG_KEEP_SCREEN_ON` mientras suena, como en el original.

## 2026-11-19 — Fase 14: arranque rápido, animaciones y actualizador robusto

- **Actualizador**: repositorio fijado, descarga automática activada por defecto (ya no exige SHA-256), parser de releases tolerante con cualquier APK adjunto y versionCode obtenido del nombre, del body, del tag o del propio nombre del asset; `isNewer` compara también `versionName`.
- **Arranque rápido**: `SettingsDataStore.lastScanAt` guarda el último escaneo; la biblioteca reutiliza la caché de Room durante 10 minutos y solo vuelve a escanear si está vacía, si han pasado 10 minutos o si se fuerza («Volver a escanear» / tras borrar canciones).
- **Playlist desde selección**: al crear una playlist desde la barra «Agregar a», las canciones seleccionadas se añaden automáticamente y se abre su detalle.
- **Fluidez y animaciones**: transiciones deslizantes + fundido entre rutas (`NavHost`) y entre pestañas de la biblioteca (`AnimatedContent`).
- **Ajustes de reproducción**: gapless y crossfade son excluyentes; ambos están conectados al motor, incluido el desvanecimiento configurable 1–12 s.
- **Playlist**: botón de edición con área táctil amplia, «Agregar (N)» deshabilitado hasta elegir canciones y mensaje cuando no hay otras playlists destino.
- Versión 3 (`1.0.2-clean`) para publicar como `musicplayer-3.apk`.

## 2026-11-18 — Fase 13: letras por ruta, degradado, segundo plano y cierre

- **Letras**: el `MediaItem` lleva la ruta original en `MediaMetadata.extras` (`lark_song_path`), así que la pantalla busca el `.lrc` por ruta exacta primero y luego por título/artista. `MediaFileUtils.findLyricsText` explora la carpeta de la canción, `Music/Lyrics`, `Lyrics`, `Download/Lyrics`, `Documents/Lyrics` y MediaStore con `LIKE`, en varios patrones de nombre.
- **Mini-player**: se eliminó la imagen desenfocada; ahora muestra un degradado radial suave derivado del color principal de la carátula, con el título en el acento.
- **Segundo plano**: `PlaybackService` mantiene un `PARTIAL_WAKE_LOCK` mientras suena y conserva el servicio en primer plano, así la reproducción continúa con la pantalla apagada o la app cerrada.
- **Botón X de cierre total**: la notificación multimedia incluye la acción «Cerrar reproducción» (`SessionCommand`) que detiene el reproductor, libera el wake lock, quita la notificación y termina el proceso.
- **Versión 2** (`1.0.1-clean`) preparada para publicarse como `musicplayer-2.apk` en `Diego-VM-x/musicplayer`.

## 2026-11-18 — Fase 12: ajustes finales de UI, reproducción y letras

- **Actualizador configurado por defecto** con `Diego-VM-x/musicplayer`; ya no hace falta escribir el repo. El nombre del asset acepta cualquier prefijo terminado en `-<versionCode>.apk`.
- **Barra de navegación del sistema**: el contenido ahora aplica también `navigationBarsPadding()`, por lo que la interfaz deja de quedar debajo de los botones/gesto del teléfono.
- **Opciones de reproducción de selección única** (`PlayerRepository.setPlaybackMode`): Normal, Repetir, Repetir una y Aleatorio son mutuamente excluyentes; la cola resalta solo el modo activo.
- **Blur ultrasuave del mini-player**: radio 48 dp y velo oscuro 55 % para que el texto destaque sobre cualquier carátula.
- **Reproducción en tercer plano**: se solicita una única vez la excepción de optimización de batería al abrir la app, y el servicio en primer plano sigue vivo aunque se cierre la interfaz.
- **Detección de letras corregida**: se buscan archivos `.lrc` por `MediaStore.Files` (nombre del archivo, `título.lrc` y `artista - título.lrc`) además del fichero hermano, porque en Android 13+ el acceso directo por ruta está restringido. La pantalla de letras pasa también el artista para identificar la canción.

## 2026-11-18 — Fase 11: actualizador in-app (GitHub Releases)

- **Nuevo módulo `core:update`** con el flujo de la guía `ACTUALIZADOR-IMPLEMENTACION.md`, adaptado sin Firebase: `AppUpdate.kt` (GitHub + DownloadManager + verificación + instalación), `UpdateManager.kt` (estado y preferencias), `UpdateState.kt`, `UpdateScheduler.kt` (Workers), `ApkDownloadReceiver.kt` y `UpdateInstallActivity.kt`.
- **Ajustes → Actualizaciones** (`feature:settings/ui/UpdateSettingsScreen.kt`): versión instalada, estado (Buscando/Disponible/Descargando/Verificando/Lista/Error), botones Buscar, Descargar, Usar datos móviles, Cancelar e Instalar; campo para configurar el repositorio `usuario/proyecto` y opciones Descarga automática y Solo Wi-Fi. También accesible desde el drawer.
- **Seguridad**: solo HTTPS, tamaño + SHA-256 + package + versionCode + firma verificados antes de instalar; el receiver solo acepta el ID de descarga guardado; las notas del release se sanitizan; el estado se excluye del backup.
- **Convención de publicación**: asset `larkplayer-<versionCode>.apk` en un release público de GitHub. El repositorio se introduce en Ajustes → Actualizaciones (no hay valor por defecto).
- **Manifiesto**: permiso `REQUEST_INSTALL_PACKAGES`, `FileProvider` con `file_paths.xml`, actividad del instalador, receiver de `DOWNLOAD_COMPLETE` y reglas de backup que excluyen `lark_update.xml`.
- `LarkApp` y `MainActivity.onResume()` inicializan/programan las comprobaciones (mínimo 5 min entre consultas automáticas, Worker periódico de 15 min).

## 2026-11-18 — Fase 10: corrección del mini-player, EQ y segundo plano

- **Mini-player corregido** (`ui/MiniPlayer.kt`): la capa desenfocada usaba `fillMaxSize()` dentro de un contenedor sin altura, por lo que la carátula a tamaño original tapaba toda la pantalla al volver a la biblioteca. El contenedor ahora tiene altura fija de 74 dp y la imagen usa `matchParentSize()`.
- **Reproductor** (`ui/PlayerScreen.kt`): la carátula tiene un máximo de 300 dp para que la fila inferior (Ecualizador / Letras / A continuación) quede siempre visible, y ahora lleva etiquetas de texto.
- **Ecualizador funcional** (`feature:equalizer`): `EqualizerViewModel` expone las bandas como `StateFlow` y vuelve a adjuntar el efecto cuando el `audioSessionId` cambia (antes se intentaba una sola vez y podía quedar vacío). Los sliders se actualizan en tiempo real.
- **Segundo plano** (`SettingsDataStore`, `SettingsViewModel`, `PlaybackSettingsScreen`, `PlaybackService`): nuevo ajuste «Reproducir en segundo plano» que solicita excluir la app de la optimización de batería y mantiene el servicio en primer plano mientras suene música, aunque se oculte la notificación. Android exige esa notificación para el servicio.
- La cola «A continuación» conserva los tres botones (Repetir, Repetir una, Aleatorio) y el reproductor los de Ecualizador / Letras / A continuación.

## 2026-11-18 — Fase 9: transiciones, cola, gestos y barra de estado

- **Mini-player con desenfoque** (`ui/MiniPlayer.kt`): la carátula se dibuja desenfocada tras la píldora con un velo oscuro, para que el título y el artista se lean sobre cualquier portada.
- **Transición de sonido funcional** (`PlayerRepository.kt`): `crossfadeEnabled` + `crossfadeSeconds` (1–12 s, de Ajustes → Reproducción) controlan un fundido de salida al finalizar la pista y de entrada en la siguiente. Se detecta también el reinicio con «repetir una». Se respeta `gapless`.
- **Avance automático y preferencias persistentes**: Media3 pasa a la siguiente canción al terminar; `shuffle` y `repeatMode` se guardan en DataStore y se restauran al reconectar el `MediaController`. Los botones de la cola y del reproductor ya persisten el cambio.
- ** Cola con controles** (`ui/QueueScreen.kt`): botones flotantes de Repetir / Repetir una / Sin repetir y Aleatorio al pie de «A continuación», con el estado activo coloreado.
- **Deslizamiento entre pestañas** (`ui/LibraryScreen.kt`): gesto horizontal sobre el contenido cambia entre Videos, Canciones, Playlists, Carpetas, Artistas y Álbumes sin tocar las píldoras.
- **Barra de estado transparente** (`MainActivity.kt`): `decorFitsSystemWindows=false` + `statusBarColor=TRANSPARENT` y un degradado a pantalla completa; el contenido se desplaza con `statusBarsPadding()`, por lo que la interfaz no sube.
- **Crear playlist** (`LibraryScreen.kt`, `LibraryViewModel.kt`): el nombre introducido se guarda y se abre de inmediato una hoja para elegir canciones (o «Omitir»); al terminar se abre el detalle de la playlist.
- **Recorte de portada** (`ui/CoverCropper.kt`): el editor permite recortar la imagen elegida desde SAF con pellizco/arrastre y guarda un JPEG cuadrado de 512×512 en almacenamiento privado.

## 2026-11-18 — Fase 8: playlists completas + liquid glass

- **Detalle de playlist** (`feature:library/ui/PlaylistDetailScreen.kt`, `PlaylistDetailViewModel.kt`, ruta `playlist/{playlistId}`):
  - Carátula grande, nombre editable con lápiz, contador de canciones y botones flotantes «Reproducir» / «Aleatorio».
  - Menús flotantes de ordenación (Más recientes / Más reproducido / A a Z / Organizar manualmente con flechas) y menú «...» (Agregar canciones, Administrar, Eliminar lista de reproducción).
  - Editor a pantalla completa: nombre (0/200) e imagen personalizada desde SAF (`coverUri` persistido con `takePersistableUriPermission`).
  - Selección múltiple con «N seleccionado(s)» y barra flotante: Agregar a, A continuación, Compartir, Ocultar y Quitar.
  - «Agregar canciones» desde todas las canciones de la biblioteca, excluyendo las ya incluidas.
- **Biblioteca** (`LibraryScreen.kt`):
  - Todas las listas (canciones, playlists, álbumes, artistas, carpetas) **sin fondo ni contorno**: solo la carátula y el texto sobre el degradado.
  - Selección múltiple con la misma barra flotante; «Borrar» usa `MediaStore.createDeleteRequest` (API 30+) o `ContentResolver.delete` en versiones anteriores, y luego vuelve a escanear.
  - Cabecera con iconos redondeados agrupados en una píldora de cristal; menú de orden flotante; diálogo «Nueva playlist» como tarjeta de cristal.
  - Tocar una playlist abre su detalle (antes reproducía directamente); la eliminación queda en el menú contextual.
- **Sistema de diseño** (`core:designsystem/component/LiquidGlass.kt`): primitivas reutilizables `GlassIconButton`, `GlassIconCluster`, `GlassPillButton`, `GlassMenu`, `LarkSheet`, `LarkDialog`, `SoftCheck`, `GlassSelectionBar`, `LarkTextField`, `GlassTag`. `LarkCard`, `LarkPill`, `CircleIconButton`, `PlayerScreen` y `MiniPlayer` adoptan el mismo acabado.
- **Datos** (`core:database`): `PlaylistEntity.coverUri`, DAO para actualizar portada/nombre, `maxPosition`, `setPosition`, borrado múltiple y `PlayCount`; base de datos v2 con migración `1 → 2` que añade `ALTER TABLE playlists ADD COLUMN coverUri TEXT` (conserva las playlists existentes) y repositorio con `addSongs`, `removeSongs`, `reorder`, `playCounts`.

## 2026-11-18 — Fase 7.1: corrección de cierres y acceso a ajustes

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

## 2026-11-18 — Fase 7.1: corrección de cierres y acceso a ajustes

- **Crashes corregidos:**
  - `PlayerRepository` accedía a `MediaController` desde `Dispatchers.Default` (crossfade y contador de escucha) — Media3 lanza `IllegalStateException` al tocar un controlador fuera de su hilo. El scope del repositorio ahora es `Dispatchers.Main` y todas las lecturas van protegidas.
  - `PlaybackService.onDestroy()` liberaba el `ExoPlayer` singleton; al reiniciarse el servicio la sesión quedaba con un reproductor muerto. Ya no se libera: solo se libera la `MediaSession`.
  - Reconexión automática del `MediaController` (`MediaController.Listener.onDisconnected`) y reproducción pendiente hasta que la conexión esté lista.
  - `EqualizerManager`: `Equalizer(...)` y sus llamadas van en `runCatching` (dispositivos sin audiofx).
  - Operaciones de playlists/registro de reproducción envueltas para no tumbar la app.
  - `LarkApp` escribe el último crash en `Android/data/<paquete>/files/logs/crash.txt`.
- **Ajustes visibles:** icono de engranaje en la cabecera de la biblioteca (`LibraryScreen.onOpenSettings`), además del menú de 3 puntos del reproductor y el drawer.
- **Carátulas sin borde:** las filas de canciones, playlists, álbumes y carpetas usan la imagen a sangre (alto completo de la fila, esquinas redondeadas solo en el lado izquierdo) y las tarjetas de cuadrícula ocupan todo el ancho sin margen interno.

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



