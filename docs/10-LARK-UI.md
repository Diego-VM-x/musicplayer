# Fases 7–8 — Rediseño Lark Player (fiel a las capturas)

La fase 7 reconstruyó la interfaz para coincidir con Lark Player (2027.1.8): tipografía
extra-negrita, píldoras azules, carátulas grandes redondeadas y **sin barra de navegación
inferior**. La fase 8 añade las funciones de playlist/selección y un acabado **liquid glass**
elegante: iconos suaves redondeados, menús y hojas flotantes y botones de reproducción de
cristal.

## 1. Sistema visual

| Elemento | Valor | Archivo |
|---|---|---|
| Fondo | `#0A0A0B` | `core/designsystem/.../theme/Theme.kt` |
| Tarjeta | cristal translúcido blanco 4–11 % + borde 10 % (radio 22 dp) | `LarkCard` + `GlassSurface` |
| Superficie | `#1B1B1D` | `ApplePalette.Surface` |
| Acento por defecto | `#0A84FF` (azul Lark) | `ThemeConfig.accentColor` |
| Acento por canción | pastel derivado de la carátula | `AlbumColorRepository` |
| Texto secundario | `#9A9AA2` | `ApplePalette.TextSecondary` |
| Píldora seleccionada | acento, texto blanco | `LarkPill`, `LarkSegmented` |

Componentes compartidos en `core/designsystem/.../component/LarkComponents.kt`:

- `LarkCard` — tarjeta agrupada con radio 22 dp.
- `CircleIconButton` — botón circular oscuro de «atrás» usado en todas las subpantallas.
- `SettingsRow` — icono + título + valor gris + chevron; admite `trailing` (switch).
- `LarkSwitch` — interruptor con acento azul.
- `LarkPill` — pestaña/píldora (biblioteca y filtros).
- `LarkSegmented` — selector de 3 opciones (Oscuro / Claro / Sistema).
- `LarkGradientBackground` — degradado vertical teñido con el acento de la carátula.
- `LarkProgressLine`, `LarkDivider`, `LarkSectionLabel`.

Primitivas liquid glass en `core/designsystem/.../component/LiquidGlass.kt` (fase 8):
`GlassIconButton`, `GlassIconCluster`/`GlassClusterIcon`, `GlassPillButton`, `GlassMenu`/
`GlassMenuItem`, `LarkSheet`, `LarkDialog`, `SoftCheck`, `GlassSelectionBar`,
`LarkTextField`, `GlassTag` y `GlassDivider`. Todas usan degradados translúcidos y un borde
luminoso de 1 dp en lugar de fondos opacos; no aplican blur por elemento para no penalizar
el desplazamiento.

## 2. Sin barra de navegación inferior

`AppNavGraph.kt` ya **no contiene** `NavigationBar` ni `GlassNavigationBar`. La navegación es:

- **Biblioteca** (destino inicial) → mini-player flotante encima de la lista.
- **Reproductor** a pantalla completa (se abre desde una canción o el mini-player).
- **Ajustes** desde el icono de engranaje de la cabecera, el menú de 3 puntos del reproductor o deslizando el `ModalNavigationDrawer`.
- Todas las subpantallas de ajustes cuelgan del `NavHost`.

Rutas: `library`, `player`, `playlist/{playlistId}`, `settings`, `lyrics`, `queue`, `sleep`,
`equalizer`, `backup`, `play_time`, `hidden_files`, `recently_deleted`,
`playback_settings`, `notifications`, `language`, `theme`, `about`, `feedback`.

## 3. Pantallas

### Biblioteca — `feature/library/ui/LibraryScreen.kt`

- Cabecera «Lark Player» + píldora de cristal con iconos redondeados: buscar, ordenar (menú flotante), «reproduciendo» y ajustes.
- Buscador opcional bajo la cabecera, con fondo translúcido y bordes invisibles.
- Pestañas en píldoras desplazables: Videos, Canciones, Playlists, Carpetas, Artistas, Álbumes.
- **Todas las filas van sin fondo ni contorno**: carátula redondeada + texto sobre el degradado de acento.
- Canciones: insignia `LETRAS` cuando existe un `.lrc` junto al archivo. El **tamaño de tema** cambia la lista: 0 = compacta (56 dp), 1 = media (64 dp), 2 = cuadrícula de 2 columnas.
- Playlists: entrada «Nueva playlist»; después carátula de portada, nombre y número de canciones. Tocar abre el detalle; pulsación larga ofrece eliminar.
- Artistas: arte circular; Álbumes y Carpetas: filas con subtítulo.
- Pulsación larga sobre una canción: entra en **selección múltiple** («N seleccionado(s)») con barra flotante de acciones: Agregar a, A continuación, Compartir, Ocultar y Borrar (borrado real vía MediaStore).
- El diálogo «Nueva playlist» es una tarjeta de cristal con campo de texto y contador 0/200; al crearla se pide de inmediato qué canciones agregar (o «Omitir»).
- Se puede **deslizar horizontalmente** sobre el contenido para cambiar de pestaña sin tocar las píldoras.
- Mensaje del último escaneo y botón «Volver a escanear» cuando no hay canciones.

### Detalle de playlist — `feature/library/ui/PlaylistDetailScreen.kt`

- Fondo degradado teñido con el color de la portada; botón «atrás» circular de cristal y píldora flotante con ordenar y «...».
- Carátula de 210 dp, nombre con lápiz para editar y contador «N Canciones».
- Botones de cristal «Reproducir» y «Aleatorio»; la selección de orden manual muestra flechas para mover canciones.
- Menú de orden: Más recientes, Más reproducido, A a Z y Organizar manualmente.
- Menú «...»: Agregar canciones, Administrar (selección) y Eliminar lista de reproducción.
- Editor a pantalla completa: imagen desde el selector de archivos del sistema, **recorte cuadrado** con pellizco/arrastre (`CoverCropper.kt`) y nombre con contador.
- La selección múltiple permite agregar a otra playlist, poner a continuación, compartir, ocultar y quitar de la playlist.

### Mini-player — `feature/player/ui/MiniPlayer.kt`

Píldora ancha teñida con el acento de la carátula y **fondo desenfocado de la propia portada**
con velo oscuro para que el texto se lea siempre. Carátula 48 dp, título del color de acento,
artista – álbum, botón de repetición con arco de progreso y botón circular de reproducir/pausar.

### Reproductor — `feature/player/ui/PlayerScreen.kt`

- Carátula cuadrada redondeada (radio 18 dp) a todo el ancho.
- Título grande en color de acento + artista; corazón de favorito y menú de 3 puntos
  (Ecualizador, Temporizador, Ajustes, Volver a la biblioteca).
- Línea de progreso fina con tiempos, anterior / reproducir / siguiente en color de acento.
- Fila inferior: ecualizador, **Letras**, cola.

### Ajustes — `feature/settings/ui/`

| Pantalla | Archivo |
|---|---|
| Ajustes principales (tarjetas agrupadas) | `SettingsScreen.kt` |
| Cabecera y filas reutilizables | `SettingsCommon.kt` (`SettingsPage`, `PlayToggleRow`, `LarkSegmented`) |
| Ajustes de reproducción | `PlaybackSettingsScreen.kt` |
| Idioma (sugeridos + todos) | `LanguageScreen.kt` |
| Tema (previsualizaciones, modo, acento, tamaño) | `ThemeScreen.kt` |
| Tiempo de reproducción | `PlayTimeScreen.kt` |
| Notificaciones | `NotificationSettingsScreen.kt` |
| Sobre | `AboutScreen.kt` |
| Opinión | `FeedbackScreen.kt` |
| Eliminado recientemente | `RecentlyDeletedScreen.kt` |
| Archivos ocultos | `feature/library/ui/HiddenFilesScreen.kt` |

La tarjeta «Sin anuncios — Activo» sustituye a «Quitar anuncios», porque la app ya no incluye
publicidad. «Respaldar y guardar» muestra «Copia local (ZIP)» en lugar de Google Drive.

### Pantallas secundarias restilizadas

`BackupScreen`, `QueueScreen`, `SleepTimerScreen`, `EqualizerScreen` y `LyricsScreen` usan
`CircleIconButton`, `LarkCard` y el acento del tema; todas reciben `onBack`.

## 4. Datos y estado

| Preferencia | Flujo | Setter |
|---|---|---|
| Acento | `themeConfig` | `setAccentColor(Long)` |
| Tamaño de tema (0/1/2) | `themeConfig` | `setThemeSize(Int)` |
| Jugar con otras apps | `playWithOthers` | `setPlayWithOthers(Boolean)` |
| Sin pausas | `gapless` | `setGapless(Boolean)` |
| Crossfade + segundos | `crossfadeEnabled`, `crossfadeSeconds` | `setCrossfade`, `setCrossfadeSeconds` |
| Idioma | `language` (`es` por defecto) | `setLanguage(String)` |
| Notificaciones | `notificationPlayback`, `notificationLockScreen` | setters homónimos |
| Favoritos / ocultos | `favoriteSongIds`, `hiddenSongIds` | `toggleFavorite`, `hideSong`, `unhideSong` |
| Tiempo escuchado | `totalPlayMs` | acumulado en `PlayerRepository` |
| Temporizador | `sleepTimerMinutes` | `setSleepTimer` |

`AlbumColorRepository` extrae un color pastel de la carátula con `androidx.palette`, lo cachea
en un `LruCache` y lo expone como `StateFlow<Long>`; el reproductor, el mini-player y la
biblioteca lo consumen para teñir fondos y títulos.

## 5. Verificación

- `assembleDebug` → **BUILD SUCCESSFUL**.
- Sin dependencias nuevas de publicidad, analítica ni librerías nativas `.so`.
- Dependencia añadida: `androidx.palette:palette-ktx:1.0.0` (solo `feature:player`).
- `feature:settings` añade `project(":feature:player")` para reutilizar `MiniPlayerPreview`
  en la vista previa del tema.
