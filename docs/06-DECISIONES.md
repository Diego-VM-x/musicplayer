# 06 — Decisiones técnicas

## 1. ¿Por qué ExoPlayer (Media3) y no iJKPlayer?

El original usa `libijkffmpeg.so` (~3.9 MB por ABI) + `libijkplayer.so` + `libijksdl.so`. iJK es un fork nativo de ffplay con una capa JNI pesada.

**Decisión:** usar **Media3 ExoPlayer**.
- Mantenido por Google, soporta MediaSession y notificaciones de primera clase.
- Elimina ~10 MB de librerías nativas por ABI.
- Integración nativa con Compose y `MediaController`.
- Formatos: MP3, AAC, FLAC, OGG/Opus, WAV, M4A (los que usaba Lark) vía extractors puros Java.
- Si en el futuro se necesitara un formato exótico, se añade el módulo `media3-exoplayer-ffmpeg` (bajo demanda), no por defecto.

## 2. ¿Por qué DataStore y no MMKV?

MMKV (`libmmkv.so`) es un KV nativo rápido. **Decisión:** `DataStore Preferences`.
- Sin código nativo (reduce tamaño y superficie de crash).
- API `Flow`-first, encaja con Compose.
- Suficiente para ajustes (tema, igualdad, timers).

## 3. ¿Por qué Coil y no Glide?

Glide está en el original. **Decisión:** **Coil**.
- Escrito en Kotlin, usa coroutines, ~40% más ligero.
- Suficiente para carátulas locales (MediaStore / archivo).

## 4. ¿Por qué Compose y no XML?

Los recursos del APK están **ofuscados** (`-1.xml`, `-3.png`) y atados a `resources.arsc`. Reutilizarlos es frágil. **Decisión:** recrear UI en Compose.
- Escalable y personalizable (petición del usuario).
- `CustomTheme` del original se modela como `ColorItem(mode, attr, value)` y se expone en ajustes.
- Material3 DynamicColors nativo.

## 5. ¿Por qué Hilt?

El original usa Dagger (`feature/dagger`). **Decisión:** Hilt (wrapper oficial de Dagger para Android).
- Menos boilerplate, integración con Compose y WorkManager.
- Mantiene la escalabilidad modular.

## 6. Backup: ¿Drive o local?

El original usa Google Drive. **Decisión:** **ZIP local primero** (SAF `ACTION_CREATE_DOCUMENT`).
- Sin dependencias de Google Play Services (el original tenía `com.google.android.gms.*` y `ACCESS_ADSERVICES_*`).
- Funciona offline y en ROMs sin GApps.
- El formato ZIP (JSON dentro) permite importación futura desde Drive u otra nube.
- Se deja la puerta abierta a un módulo `feature:backup:drive` opcional.

## 7. Permisos

Se eliminan todos los permisos de anuncios/analytics del original:
`AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_TOPICS`, `AD_SERVICES_CONFIG`, `com.android.vending.BILLING`, `BIND_GET_INSTALL_REFERRER_SERVICE`, `c2dm.permission.RECEIVE`.

Se conservan solo los necesarios: `READ_MEDIA_AUDIO/VIDEO` (o `READ_EXTERNAL_STORAGE` ≤32), `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `WRITE_SETTINGS` (ringtone), `INTERNET` (solo si se activa búsqueda de letras online).

## 8. Estrategia de "super optimizado"

- Sin SDK de anuncios ni analytics (reduce ~60% del dex).
- Sin librerías nativas propias (ExoPlayer puro Java + codecs del sistema).
- R8 + shrinkResources en release.
- Per-app language (Android 13+) en vez de todos los idiomas embebidos.
- Módulos on-demand no incluidos en el APK base (futuro).
