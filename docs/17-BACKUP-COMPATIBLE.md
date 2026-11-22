# 17 — Backup de playlists compatible con Lark Player original

Basado en el análisis de `jadx_full/sources/com/dywx/larkplayer/drive/server` y clases relacionadas.

## Formato real del original

El original **no exporta un ZIP restaurable**: sube a Google Drive tres archivos `.info` cifrados dentro de
`Backed-up music/Info/`, y opcionalmente audio en `Backed-up music/Music/`, portadas en `Cover/` y letras
en `Lyrics/`. La distinción "con/sin canciones" es una selección en la UI de Drive, no un flag.

Nombres exactos (apóstrofa tipográfica U+2019):

```
(Don’t delete) Playlists you backed up in Lark Player.info
(Don’t delete) Play count you backed up in Lark Player.info
(Don’t delete) Playtime you backed up in Lark Player.info
```

Cifrado: `AES/ECB/PKCS5Padding`, clave `Qvr1I4Zp10pJL2Zb3/XuwMTCk9m4b5gZ`, Base64. Si el contenido
empieza por `{` y termina por `}` se usa tal cual (backups antiguos sin cifrar).

JSON de playlists (campos exactos):

```json
{"playlistInfos":[{
  "playlist_name":"My Mix",
  "playlist_id":"<id>",
  "createTime":1730000000000,
  "cover":"<uri>",
  "items":[{"songs_name":"song.mp3","size":4321987,"play_count":3}]
}]}
```

La posición de cada canción es el índice en `items`. La identidad es solo `songs_name` (nombre de archivo);
título/artista/álbum se recuperan al reescanear el audio. `"playlist_id_favorite"` identifica favoritos.

## Implementación en la réplica

- `OriginalFormat.kt`: modelos con `@SerializedName` y `LarkBackupCrypto` (encrypt/decrypt con la clave exacta).
- `BackupManager.kt`:
  - exporta `backup.json` propio (con `playlistSongs`, antes perdido) **y** los tres `.info` cifrados;
  - opción `includeSongs` añade entradas `Music/<songs_name>` leyendo por `MediaFileUtils.songUri`;
  - importa ambos formatos, fusiona playlists por nombre (semántica `mergeTo` del original), empareja
    canciones por nombre de archivo y, si hay audio en el ZIP, lo copia a `getExternalFilesDir/Music/larkplayer/restore`
    y llama a `MediaScanner` antes de enlazarlo;
  - `findAutoBackup()` busca el ZIP más reciente en Descargas y en `<filesDir>/backups`.
- `BackupScreen.kt`: interruptor **Incluir canciones** y tarjeta **Restaurar copia detectada**.

## Importar directamente desde la app original

En **Respaldar y guardar** hay una tarjeta **Importar playlists de Lark Player**:
- selector múltiple para elegir los `.info` que hayas copiado de `Android/data/com.dywx.larkplayer/...`;
- lista de archivos detectados automáticamente en Descargas, en el almacenamiento externo de la app y en la
  carpeta de backups;
- cada archivo se descifra, se fusiona por nombre de playlist y sus canciones se vinculan por `songs_name`
  contra la biblioteca ya escaneada.

Si el original no permite copiar esos archivos desde la interfaz, el usuario puede obtenerlos desde su copia
de Drive o con un gestor de archivos con acceso a `Android/data`.

## Importar desde `vlc_database` (v24)

El original guarda su biblioteca en `/data/data/com.dywx.larkplayer/databases/vlc_database` (SQLite v75):

- `playlist_table(name, create_time, playlist_cover, playlist_order, playlist_drive_id, …)`
- `playlist_media_table(playlist_name, media_location = media_table._id, playlist_order)`
- `media_table(_id = URI en texto, title, artist, album, length, file_name, parent_file_path, …)`

`OriginalLibraryScanner` copia el archivo seleccionado por SAF a almacenamiento privado, ejecuta el mismo
JOIN que el original y empareja cada canción por ruta → nombre de archivo → título contra la biblioteca ya
escaneada por `MediaScanner`.

**No se puede leer directamente**: los 16 `ContentProvider` del original tienen `exported="false"`. Hay que
cerrar Lark Player y copiar `vlc_database` (y `-wal`/`-shm` si existen) con un gestor con acceso a
`Android/data` o con `adb pull` en dispositivo rooteado. Las entradas online (`http…`) y las playlists
virtuales `INVALID_*` se omiten.

## Copia local LPBK (2027.3.x)

Archivo: `Backup_of_Lark_Player_<fecha>.lrc` en `Documents/Lark Player` o Descargas.

Formato invertido del APK `2027.3.6 (2027030609)`:

```
"LPBK" | version(uint16 BE) | 14 bytes | IV(12) | metaLen(uint32 BE)
meta JSON | AES-CTR payload | HMAC-SHA256(header+meta+payload)
```

- `encKey = HMAC-SHA256(clave histórica, "lpbk-enc")`, `macKey = HMAC-SHA256(clave histórica, "lpbk-mac")`.
- Cifrado `AES/CTR/NoPadding` con el IV de 12 bytes ampliado a 16.
- El payload descifrado es un ZIP con `manifest.json`, `sections/media.json`, `sections/playlists.json`, etc.
- `PlaylistEntry.members[].media_id` referencia `MediaEntry.id`; `MediaEntry.name/location/rel` permite
  emparejar con la biblioteca escaneada. `order` 0..n-1 es el orden real.
- Las portadas están en `files/covers/` y se restauran.

`LpbkImporter` hace todo esto automáticamente y `BackupScreen` detecta el archivo más reciente.

## Limitación

El APK original **no puede importar un archivo local**: solo restaura desde su propia cuenta de Drive. Por eso
la compatibilidad es de **formato** (la réplica lee y escribe los mismos `.info` cifrados dentro del ZIP), no de
flujo de importación directa en el original. Para pasar un backup al original haría falta subir esos archivos a
Drive con las mismas carpetas, lo cual requiere credenciales Google y está fuera de alcance.
