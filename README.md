# LarkClean — Reimplementación nativa de Lark Player

> Reproductor de música y video para Android, **nativo, sin anuncios, optimizado y escalable**.
> Inspirado en Lark Player v2027.1.8 (`com.dywx.larkplayer`), reconstruido desde cero.

## Estado del proyecto

| Fase | Estado |
|---|---|
| Análisis ingeniería inversa del APK original | ✅ Completo (`ANALISIS.md`) |
| Configuración Gradle / version catalog | ✅ Completo |
| Documentación (estructura, arquitectura, funciones, cambios) | ✅ Completo |
| Módulos `core` (model, common, database, datastore, designsystem) | ✅ Completo |
| Módulos `feature` (player, library, lyrics, backup, settings, theme) | ⏳ Pendiente |
| App `app` (navegación, DI, pantallas) | ✅ Completo |
| Repo Git inicial | ✅ Completo (sin commit final) |
| Build `assembleDebug` y APK final | 🔄 En progreso (corrigiendo errores) |

## Stack (solo lo necesario)

- **Kotlin** + **Jetpack Compose** + **Material 3**
- **Media3 ExoPlayer** (reproductor — reemplaza iJKPlayer nativo)
- **Room** (playlists, historial) + **DataStore** (preferencias — reemplaza MMKV)
- **Coil** (imágenes — reemplaza Glide)
- **Hilt** (inyección de dependencias)
- **WorkManager** (backup en segundo plano)
- ❌ Sin anuncios ❌ Sin Firebase/Crashlytics/Analytics ❌ Sin permisos `AD_ID`

## Requisitos

- Android Studio Ladybug o superior
- JDK 17
- Android SDK 35 (target), minSdk 24

## Estructura rápida

```
lark-clean/
├── docs/               ← documentación de cambios, estructura y funciones
├── core/               ← capa base reutilizable
│   ├── model/          ← Song, Playlist, Lyric, ThemeConfig
│   ├── common/         ← utilidades, Result, diálogos
│   ├── database/       ← Room DAOs y entidades
│   ├── datastore/      ← preferencias
│   └── designsystem/   ← tema Material3 + CustomTheme
├── feature/            ← funcionalidades independientes
│   ├── player/         ← servicio Media3 + sesión + cola
│   ├── library/        ← escaneo, canciones, álbumes, playlists
│   ├── lyrics/         ← parser LRC + UI
│   ├── equalizer/      ← audiofx nativo
│   ├── backup/         ← export/import ZIP (+ opcional Drive)
│   ├── settings/       ← ajustes + personalización
│   └── widget/         ← widget + notificación multimedia
└── app/                ← aplicación, navegación, Hilt
```

Ver [`docs/01-ESTRUCTURA.md`](docs/01-ESTRUCTURA.md) y [`docs/03-FUNCIONES.md`](docs/03-FUNCIONES.md) para el detalle.

## Licencia / nota legal

Este proyecto es una **reimplementación limpia con fines educativos**. No incluye código, recursos ni claves del APK original. El nombre "Lark" se usa solo como referencia del comportamiento a replicar.
