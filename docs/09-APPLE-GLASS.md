# 09 — Rediseño Apple Music / Liquid Glass y fix de detección

## Problema: "no detecta las músicas del celular"

**Causa raíz encontrada:** `LibraryViewModel` exponía `scan()` pero **nunca lo llamaba**. La base de datos Room siempre estaba vacía.

**Solución aplicada:**
1. `MediaScanBus` (`core:common`) — `SharedFlow` para pedir rescans entre módulos.
2. `LibraryViewModel.init` llama `scan()` al arrancar **y** escucha `scanBus.events`.
3. `MainActivity` emite `scanBus.requestScan()` al:
   - iniciar (si ya tiene permisos),
   - concederse permisos,
   - volver del background (`onResume`).
4. `MediaFileUtils.queryAudio` — query ampliada: antes `IS_MUSIC != 0` ocultaba `.flac/.m4a/.opus`; ahora filtra por `MIME_TYPE LIKE 'audio/%'` y ordena por `DATE_ADDED DESC`. Fallback de título/artista/álbum desde la ruta del archivo.
5. Permisos: añadido `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14) + `POST_NOTIFICATIONS`.
6. UI: mensaje de resultado del scan ("Found N songs") y empty-state con instrucción.

## Apariencia Apple Music / Liquid Glass

### Sistema de diseño (`core:designsystem`)
- `ApplePalette.kt` — rojo `#FA243C`, rosa, fondo `#08080A`, textos.
- `component/Glass.kt`:
  - **`GlassSurface`** — relleno degradado translúcido + borde hairline luminoso + sombra suave. Sin RenderEffect por tarjeta (optimizado).
  - **`BlurredBackdrop`** — carátula con `Modifier.blur(48.dp)` + gradiente oscuro de fondo (el look firma de Apple Music).
  - **`GlassNavigationBar`** — barra inferior translúcida.
  - **`GlassIconButton`** — botones circulares de cristal.
- Tema por defecto cambiado a **oscuro** con acento rojo (Apple Music), manteniendo DynamicColor opcional y los `ColorItem` personalizables.

### Pantallas rediseñadas
- **PlayerScreen**: fondo de carátula borrosa, barra superior glass, carátula 290dp con glow, título en negrita + artista en acento, slider de progreso, controles (white glass play button), botones glass de Lyrics/Queue/Sleep.
- **MiniPlayer**: "píldora" de cristal flotante con progress line.
- **LibraryScreen**: título gigante "Library", buscador glass, pestañas, carrusel "Recently added" con tarjetas de cristal, filas de canciones con carátula, selector de duración para stop.
- **LyricsScreen**: letras centradas con línea activa en acento.
- **EqualizerScreen**: bandas reales de `audiofx.Equalizer`.
- **QueueScreen / SleepTimerScreen**: nuevas.

## Funciones completadas de Lark

| Función | Estado |
|---|---|
| Escaneo fiable de música local | ✅ |
| Playlists (crear/renombrar/borrar) | ✅ |
| Favoritos / historial / cola persistida | ✅ Room |
| Letras .lrc locales | ✅ |
| Ecualizador real + visualizador | ✅ (audiofx) |
| Temporizador de sueño | ✅ |
| Ringtone | ✅ |
| Backup ZIP | ✅ |
| Notificación / lock screen / MediaSession | ✅ |
| Copia de seguridad en la nube (Drive) | ♻️ reemplazada por ZIP local |
| Transferencia por cámara/web | ⏳ fuera de alcance |
| Widget | ⏳ pendiente |
