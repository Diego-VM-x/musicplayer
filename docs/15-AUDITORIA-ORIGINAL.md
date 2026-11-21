# 15 — Auditoría frente al APK original (v15)

Comparación estática entre la réplica (`lark-clean/`) y los layouts/code descompilados del original
(`jadx_full/resources/res/layout/`, `jadx_full/sources/`). Sin `adb`/emulador: la verificación es de
jerarquía, código y recursos, no de ejecución en dispositivo.

## Evidencias del original

| Archivo original | Qué demuestra |
|---|---|
| `fragment_player_motion_audio.xml` | Sin barra superior; `iv_back` es un chevron `gone`; favorito, `action_mv` y `action_more` a la derecha del bloque título/subtítulo; transporte solo con `action_prev`, `action_play`, `action_next`; `action_mode_small` es un badge sobre `action_mode_container` (cola); EQ y cola son iconos sin texto, solo `tv_lyrics_tab` lleva "Letras"; `speed_container` empieza `gone`; `top_guide_line` ≈ 239 dp; título usa `?attr/content_main` y `maxLines="2"`. |
| `main_head.xml` | Cabecera de biblioteca: logo + wordmark a la izquierda; ajustes, ordenar y buscar a la derecha. No hay icono "Reproduciendo". |
| `fragment_motion_play_queue.xml` | Cola con `action_focus` (ir a la canción actual) y `action_clear` (vaciar), además de repetir una / repetir / aleatorio. |
| `fragment_player.xml` | Reproductor heredado con `action_mode`, cola y compartir como acciones separadas. |

## Correcciones aplicadas en v15

### Commit 1 — Interfaz
- `feature/player/.../PlayerScreen.kt`: barra superior eliminada; chevron + swipe-down; portada ≤ 239 dp;
  título blanco a 2 líneas; favorito y 3 puntos junto al título; velocidad condicional y con `Locale.US`;
  transporte de 3 botones; badge de modo sobre la cola; cola duplicada eliminada; EQ/cola sin etiqueta.
- `core/designsystem/.../LiquidGlass.kt`: `GlassIconButton(flat = true)` para controles planos.
- `feature/player/.../MiniPlayer.kt`: modo único + siguiente.
- `feature/library/.../LibraryScreen.kt`: sin icono Radio; marca de app en la cabecera; compartir múltiple.
- `app/src/main/AndroidManifest.xml`: etiqueta `Lark Player`.

### Commit 2 — Funciones
- `PlayerScreen.kt` / `PlayerViewModel.kt`: selector y creador de playlist, compartir, tono de llamada
  (con `WRITE_SETTINGS`), ocultar y borrar (confirmación + MediaStore). Editar se retiró por ser inerte.
- `PlayerRepository.kt`: shuffle con repeat ALL, reordenación persistente, `clearQueue()`, gapless leído
  por el motor y `toggleShuffle` unificado.
- `QueueScreen.kt`: ir a la canción actual y vaciar cola.
- `PlaybackService.kt`: la X ya no usa `exitProcess(0)`.
- `PlaybackSettingsScreen.kt`: descripción del crossfade ajustada a lo que hace realmente.

## Ajuste posterior (v16)

- Reproductor reordenado: scroll + espaciado constante, carátula cuadrada ≤ 224 dp y controles uniformes
  (52/72/44/40 dp) con márgenes de 16 dp. Evita que el título quede encima de la carátula/placeholder y
  que los botones queden desproporcionados en pantallas bajas.
- Arrastre con elevación: `DetailSongRow` (playlist) y `QueueScreen` (cola) usan `graphicsLayer` con
  `scale`, `shadowElevation` y `translationY` animados por `spring`; la fila se levanta al mantener pulsado
  y acompaña al dedo mientras el orden se recalcula y persiste en cada paso.

## Ajuste posterior (v17)

- El avance automático sigue el orden visible de la cola/custom ShuffleOrder, no el orden aleatorio interno
  de ExoPlayer: se instala la orden antes de habilitar shuffle y se corrige cualquier transición `AUTO` que no
  coincida con el siguiente esperado.
- La pantalla de playlist oculta la mini-reproductora, elimina el padding inferior vacío y usa una barra fija
  con botones de 58 dp (Reproducir / Aleatorio).
- Playlist y ajustes usan un `CompositionLocal` de superficie plana: sin degradados, bordes ni sombras; el
  reproductor mantiene su estilo.

## Pendiente / no replicado a propósito

- **Videos / MV**: la pestaña sigue vacía y no existe la acción `action_mv` (el original la muestra solo
  cuando el archivo tiene un videoclip asociado). No se implementó porque la réplica se centra en audio.
- **Crossfade real con superposición**: Media3 no mezcla dos fuentes con un solo `ExoPlayer`; la réplica
  hace fade-out/fade-in encadenados. El subtítulo de ajustes ya no promete superposición.
- **Edición de etiquetas (icono Editar)**: se retiró en lugar de dejar un botón muerto; requiere permisos
  de escritura MediaStore y no está implementada.

## Verificación

- `./gradlew assembleDebug --no-daemon` → BUILD SUCCESSFUL (445 tareas).
- `versionCode = 15`, `versionName = "1.0.14-clean"`.
- Sin `adb`: los cambios visuales y funcionales quedan documentados aquí para validación en dispositivo.
