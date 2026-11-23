# 18 — Componente de visualizador de audio (modular)

Componente **independiente del reproductor**: solo dibuja encima de la barra de progreso y tiene su
propio panel de estilos. No incluye pantallas ni controles de reproducción.

## Archivos

| Archivo | Rol |
|---|---|
| `feature/player/.../VisualizerEngine.kt` | Captura, normalización, suavizado, peak decay y fallback. |
| `feature/player/.../ui/MusicVisualizerSheet.kt` | Panel (switch + 4 tarjetas) y `VisualizerCanvas` (60 fps con `withFrameNanos`). |
| `feature/player/.../ui/PlayerScreen.kt` | Ejemplo de uso: coloca `VisualizerCanvas` justo antes del `Slider`. |

## Conexión al reproductor

```kotlin
VisualizerCanvas(
    audioSessionId = state.audioSessionId, // ExoPlayer.audioSessionId / MediaPlayer.getAudioSessionId()
    enabled = state.visualizerEnabled,
    isPlaying = state.isPlaying,
    style = state.visualizerStyle,          // "armonia" | "rebote" | "ascenso" | "flujo"
    accent = accent,
    modifier = Modifier.fillMaxWidth().height(34.dp),
)
```

En esta app el `audioSessionId` se obtiene del servicio Media3 mediante un comando personalizado
(`COMMAND_GET_AUDIO_SESSION`) y se publica en `PlayerUiState`.

## Permisos

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

`android.media.audiofx.Visualizer` necesita ese permiso. Si no está concedido, o el `audioSessionId`
es `0`, el motor entra automáticamente en **modo fallback** con una señal simulada suave: la UI sigue
animándose, no lanza excepciones y el resto del reproductor funciona igual.

El panel solicita el permiso al activar el switch; si el usuario lo rechaza, el visualizador se activa
de todas formas en fallback.

## Ajuste de parámetros

En `VisualizerEngine.Companion`:

- `SENSITIVITY = 7f` → mayor valor = barras más altas para la misma señal (1..12 recomendado).
- `SMOOTHING = 0.72f` → inercia del suavizado (0 = muy nervioso, 0.9 = muy lento).
- `PEAK_DECAY = 0.018f` → velocidad de caída de las marcas de pico.
- `BAND_COUNT = 16` → número de bandas logarítmicas (40 Hz..16 kHz).

El color se pasa como `accent: Color` al canvas; el alpha se modula con `isPlaying` para que, al pausar,
el movimiento se atenúe progresivamente hasta reposar.

## Modos

- **Armonía**: tres capas de ondas semitransparentes (bajos/medios/agudos).
- **Rebote**: barras estrechas redondeadas con marcas de pico.
- **Ascenso**: barras con perfil creciente y pulso continuo.
- **Flujo**: envolvente orgánica superior e inferior alimentada por la forma de onda.
