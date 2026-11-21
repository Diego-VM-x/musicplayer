# 05 — Registro de cambios (Changelog)

Formato: `YYYY-MM-DD — cambio — archivos afectados`

---

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



