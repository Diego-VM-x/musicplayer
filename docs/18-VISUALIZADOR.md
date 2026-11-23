# 18 — Componente de visualizador de audio (modular)

Componente **independiente del reproductor**: solo dibuja encima de la barra de progreso y tiene su
propio panel de estilos. No incluye pantallas ni controles de reproducción.

## Arquitectura (idéntica al Lark Player original)

El APK original **no usa `android.media.audiofx.Visualizer` ni pide `RECORD_AUDIO`**. Inserciona un
`AudioProcessor` propio en la cadena de audio de ExoPlayer (`sources/o/jm2.java:111-123`), copia el PCM
decodificado antes de `AudioTrack`, lo baja a mono (`sources/o/j66.java:42-65`) y calcula la FFT en un
hilo aparte (`sources/o/uo2.java:76-212`, thread `lp-visualizer-fft`). El manifiesto original declara
`android.hardware.microphone` como `required=false` y no contiene `RECORD_AUDIO`.

Nuestra implementación sigue exactamente ese modelo:

| Archivo | Rol |
|---|---|
| `feature/player/.../PcmSpectrumProcessor.kt` | `AudioProcessor` pass-through que copia el PCM al ring buffer. |
| `feature/player/.../AudioSpectrumSource.kt` | Ring buffer + Goertzel (16 bandas) + forma de onda + AGC/RMS. |
| `feature/player/.../PlayerModule.kt` | Instala el procesador en `DefaultAudioSink` (`SpectrumRenderersFactory`). |
| `feature/player/.../ui/MusicVisualizerSheet.kt` | `rememberVisualizerBands` (suavizado/peak decay) y `VisualizerCanvas` (60 fps con `withFrameNanos`), panel con switch y 4 tarjetas. |
| `feature/player/.../ui/PlayerScreen.kt` | Ejemplo de uso: `VisualizerCanvas` justo antes del `Slider`. |

## Conexión al reproductor

No hay que pasar ningún `audioSessionId`: el procesador ya está en la cadena de audio del `ExoPlayer`
singleton que usa la app. Basta con:

```kotlin
VisualizerCanvas(
    enabled = state.visualizerEnabled,
    isPlaying = state.isPlaying,
    style = state.visualizerStyle,   // "armonia" | "rebote" | "ascenso" | "flujo"
    accent = accent,
    modifier = Modifier.fillMaxWidth().height(34.dp),
)
```

## Permisos

**Ninguno.** No se declara `RECORD_AUDIO` y el panel no solicita permisos. El audio se lee del mismo
buffer PCM que se envía al altavoz, igual que en el original.

## Ajuste de parámetros

- En `AudioSpectrumSource.kt`: `BAND_COUNT`, factor de ganancia (`0.45f / rms`), curva `1 - exp(-raw * 5f)`.
- En `rememberVisualizerBands` (MusicVisualizerSheet): `0.38f` de ataque / `0.12f` de caída y
  `peaks[i] - 0.02f` por frame (decay).
- En `VisualizerCanvas`: `k = 1 - exp(-dt / 70 ms)` para la interpolación a 60 fps.
- El color se pasa como `accent: Color`; el alpha se modula con `isPlaying` para que al pausar se atenúe.

## Modos

- **Armonía**: tres capas de ondas semitransparentes (bajos/medios/agudos).
- **Rebote**: barras estrechas redondeadas con marcas de pico.
- **Ascenso**: barras con perfil creciente y pulso continuo.
- **Flujo**: envolvente orgánica superior e inferior alimentada por la forma de onda PCM.
