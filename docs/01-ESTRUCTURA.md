# 01 — Estructura del proyecto

## Árbol completo (planificado)

```
lark-clean/
├── settings.gradle.kts          # Incluye todos los módulos
├── build.gradle.kts             # Plugins raíz (apply false)
├── gradle.properties
├── gradle/
│   └── libs.versions.toml       # Version catalog — TODAS las versiones aquí
├── .gitignore
├── README.md
├── docs/
│   ├── 00-INDICE.md
│   ├── 01-ESTRUCTURA.md
│   ├── 02-ARQUITECTURA.md
│   ├── 03-FUNCIONES.md
│   ├── 04-MAPA-REPLICACION.md
│   ├── 05-CAMBIOS.md
│   ├── 06-DECISIONES.md
│   └── 07-COMPATIBILIDAD.md
│
├── core/
│   ├── model/                   # Modelos de dominio (sin Android framework)
│   │   └── src/main/java/com/larkplayer/core/model/
│   │       ├── Song.kt
│   │       ├── Playlist.kt
│   │       ├── Lyric.kt
│   │       └── ThemeConfig.kt
│   ├── common/                  # Utilidades compartidas
│   │   └── .../core/common/
│   │       ├── Result.kt
│   │       ├── MediaFileUtils.kt
│   │       └── di/CommonModule.kt
│   ├── database/                # Room
│   │   └── .../core/database/
│   │       ├── entity/SongEntity.kt
│   │       ├── entity/PlaylistEntity.kt
│   │       ├── dao/SongDao.kt
│   │       ├── dao/PlaylistDao.kt
│   │       ├── LarkDatabase.kt
│   │       └── di/DatabaseModule.kt
│   ├── datastore/               # Preferencias
│   │   └── .../core/datastore/
│   │       ├── SettingsDataStore.kt
│   │       └── di/DataStoreModule.kt
│   └── designsystem/            # Tema y componentes UI reutilizables
│       └── .../core/designsystem/
│           ├── theme/Theme.kt
│           ├── theme/Color.kt
│           ├── theme/CustomTheme.kt
│           └── component/PlayerControls.kt
│
├── feature/
│   ├── player/                  # Reproducción (Media3)
│   │   ├── PlaybackService.kt
│   │   ├── PlayerRepository.kt
│   │   ├── QueueManager.kt
│   │   ├── MediaSessionHandler.kt
│   │   └── ui/PlayerScreen.kt
│   ├── library/                 # Biblioteca y escaneo
│   │   ├── scanner/MediaScanner.kt
│   │   ├── LibraryRepository.kt
│   │   ├── PlaylistRepository.kt
│   │   └── ui/LibraryScreen.kt
│   ├── lyrics/                  # Letras
│   │   ├── LrcParser.kt
│   │   ├── LyricsRepository.kt
│   │   └── ui/LyricsView.kt
│   ├── equalizer/               # Ecualizador + visualizador
│   │   ├── EqualizerManager.kt
│   │   └── ui/EqualizerSheet.kt
│   ├── backup/                  # Copia de seguridad
│   │   ├── BackupManager.kt
│   │   ├── BackupWorker.kt
│   │   └── ui/BackupScreen.kt
│   ├── settings/                # Ajustes y personalización
│   │   ├── SettingsRepository.kt
│   │   └── ui/SettingsScreen.kt
│   └── widget/                  # Widget + notificación
│       ├── LarkGlanceWidget.kt
│       └── PlayerNotification.kt
│
└── app/
    └── src/main/
        ├── AndroidManifest.xml
        └── java/com/larkplayer/app/
            ├── LarkApp.kt
            ├── MainActivity.kt
            ├── navigation/NavGraph.kt
            └── di/AppModule.kt
```

## Convenciones

- **Package:** `com.larkplayer.<modulo>` (ej. `com.larkplayer.feature.player`)
- **applicationId:** `com.larkplayer.clean`
- **Un módulo = una función.** `feature/*` nunca depende de otro `feature/*`; solo de `core/*`.
- **Versiones:** nunca hardcodear en `build.gradle.kts`; siempre `libs.xxx` del version catalog.
- **Documentación:** cada archivo nuevo se añade a `03-FUNCIONES.md` y `05-CAMBIOS.md`.
