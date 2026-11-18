# 02 — Arquitectura

## Capas

```
┌──────────────────────────────────────────────────────────┐
│  UI (Compose) — feature/*/ui + app/navigation           │
│  Estado: StateFlow/UiState, ViewModel por pantalla      │
└───────────────────────┬──────────────────────────────────┘
                        │ eventos / estado
┌───────────────────────▼──────────────────────────────────┐
│  Dominio — Repositorios (interfaces + implementación)   │
│  LibraryRepository, PlaylistRepository, BackupManager…  │
└───────────────────────┬──────────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────────┐
│  Datos — Room DAO, DataStore, MediaStore, Media3        │
└──────────────────────────────────────────────────────────┘
```

## Principios

1. **Unidireccional:** la UI observa `StateFlow` y envía eventos; nunca escribe directamente en DAO/DataStore.
2. **Offline-first:** toda la música es local (MediaStore). Sin red para funcionar.
3. **Service de reproducción independiente:** `PlaybackService` (Media3 `MediaSessionService`) vive en el proceso principal, expone una `MediaSession` y es la **única** fuente de verdad del playback.
4. **Cola en Room:** la cola y playlists se persistenen para sobrevivir a la muerte del proceso (Lark hacía algo similar con `SongBehaviorRecordDatabase`).
5. **Módulos desacoplados:** comunicación entre features vía interfaces en `core` o eventos (`SharedFlow`), nunca imports cruzados.

## Inyección de dependencias (Hilt)

- `@HiltAndroidApp` en `LarkApp`
- `@AndroidEntryPoint` en `MainActivity`, `PlaybackService`, Widget receiver
- Módulos: `DatabaseModule`, `DataStoreModule`, `CommonModule`, `AppModule`, `PlayerModule`
- `@Provides` con `@Singleton` para repositorios y `Player`

## Flujo de reproducción

```
UI (PlayerScreen)
   │  Play(songId)
   ▼
PlayerRepository  ──▶ QueueManager (Room: cola persistida)
   │
   ▼
MediaController (conectado a PlaybackService)
   ▼
ExoPlayer (Media3) ──▶ MediaSession ──▶ Notificación / Widget / Lock screen
```

## Flujo de backup

```
BackupManager.export(uri)
   ├─ Room → playlists, favoritos, historial, cola  → backup.json
   ├─ DataStore → ajustes y tema                    → settings.json
   └─ ZipOutputStream → backup.zip (Guardar en SAF)
BackupManager.import(uri)
   └─ lee ZIP → valida versión → reemplaza tablas → recarga
```

## Decisiones clave

Ver [`06-DECISIONES.md`](06-DECISIONES.md). Resumen: ExoPlayer en vez de iJK, Room en vez de SQLite directo, DataStore en vez de MMKV, Coil en vez de Glide, Compose en vez de XML ofuscado.
