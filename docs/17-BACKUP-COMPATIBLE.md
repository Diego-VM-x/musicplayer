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

## Limitación

El APK original **no puede importar un archivo local**: solo restaura desde su propia cuenta de Drive. Por eso
la compatibilidad es de **formato** (la réplica lee y escribe los mismos `.info` cifrados dentro del ZIP), no de
flujo de importación directa en el original. Para pasar un backup al original haría falta subir esos archivos a
Drive con las mismas carpetas, lo cual requiere credenciales Google y está fuera de alcance.
