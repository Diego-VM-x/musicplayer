package com.larkplayer.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** Public release metadata (asset naming convention: larkplayer-<versionCode>.apk). */
data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String = "",
    val sizeBytes: Long = 0L,
    val notes: String = "",
    val minSdk: Int = 0,
)

sealed interface UpdateFetchResult {
    data class Found(val info: AppUpdateInfo) : UpdateFetchResult
    data object NotFound : UpdateFetchResult
    data class Error(val message: String) : UpdateFetchResult
}

data class DownloadSnapshot(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localUri: String?,
)

/** Network, download, verification and installation helpers for the in-app updater. */
object AppUpdate {
    private const val USER_AGENT = "LarkClean-Updater"
    private const val TIMEOUT_MS = 25_000
    private const val MAX_APK_BYTES = 200L * 1024L * 1024L
    private val APK_VERSION_CODE = Regex("""(?:^|[-_])v?(\d+)\.apk$""", RegexOption.IGNORE_CASE)
    private const val ASSET_PREFIX = "larkplayer"
    private val ASSET_VERSION_REGEX = Regex("""^[A-Za-z0-9_-]+-(\d+)\.apk$""", RegexOption.IGNORE_CASE)
    private val SHA256_REGEX = Regex("""^[0-9a-f]{64}$""")
    private val VERSION_CODE_REGEX = Regex("""(?i)versionCode[:\s]+(\d+)""")
    private val TECHNICAL_LINE_REGEX = Regex(
        """(?i)^(version\s*code|sha-?256|min\s*sdk|mandatory|tama[ñn]o|size|asset|apk)\s*[:=].*$""",
    )

    /** Distribution repository — fixed in code, not user-editable. */
    const val GITHUB_REPO = "Diego-VM-x/musicplayer"

    const val EXTRA_APK_PATH = "lark_update_apk_path"
    const val EXTRA_APK_URL = "lark_update_apk_url"
    const val EXTRA_VERSION_NAME = "lark_update_version_name"
    const val EXTRA_VERSION_CODE = "lark_update_version_code"
    const val EXTRA_APK_SHA256 = "lark_update_sha256"
    const val EXTRA_APK_SIZE = "lark_update_size"

    fun isSecureUrl(url: String): Boolean =
        url.trim().startsWith("https://", ignoreCase = true)

    fun isNewer(info: AppUpdateInfo, currentCode: Int, currentName: String = ""): Boolean {
        val newer = if (info.versionCode > 0) {
            info.versionCode > currentCode
        } else {
            info.versionName.isNotBlank() && info.versionName != currentName
        }
        return newer &&
            isSecureUrl(info.apkUrl) &&
            (info.minSdk <= 0 || info.minSdk <= Build.VERSION.SDK_INT)
    }

    /**
     * Lists all releases and picks the newest public one. The `/releases/latest`
     * endpoint returns 404 while the repository has no published release, which
     * used to look like an error; the list endpoint makes that case a clean
     * "no update" result.
     */
    suspend fun fetchLatest(): UpdateFetchResult = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$GITHUB_REPO/releases?per_page=20"
        val raw = runCatching {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            val code = connection.responseCode
            if (code == 404) return@withContext UpdateFetchResult.NotFound
            if (code !in 200..299) {
                return@withContext UpdateFetchResult.Error("GitHub respondió $code.")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        }.getOrElse {
            return@withContext UpdateFetchResult.Error(it.message ?: "No se pudo contactar con GitHub.")
        }
        val fromReleases = parseReleases(raw)
        if (fromReleases is UpdateFetchResult.Found) return@withContext fromReleases
        // An HTML/rate-limit body is an error, not "no update". Falling through
        // to contents() would replace it with a misleading NotFound.
        if (fromReleases is UpdateFetchResult.Error) return@withContext fromReleases
        // Repositories that publish APKs directly in the root (no releases yet)
        // are supported as a fallback, with an optional update.json manifest.
        fetchFromContents()
    }

    /** Extracts a simple versionCode suffix (`musicplayer-14.apk`, `player_v51.apk`). */
    fun versionCodeFromApkName(name: String): Int? =
        APK_VERSION_CODE.find(name)?.groupValues?.get(1)?.toIntOrNull()

    /** Fallback: detect APKs committed to the repository root. */
    private suspend fun fetchFromContents(): UpdateFetchResult = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$GITHUB_REPO/contents/"
        val raw = runCatching {
            val connection = URL(url).openConnection() as HttpsURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.inputStream.bufferedReader().use { it.readText() }
        }.getOrElse { return@withContext UpdateFetchResult.NotFound }

        val entries = runCatching { org.json.JSONArray(raw) }
            .getOrElse { return@withContext UpdateFetchResult.Error("Respuesta inválida de GitHub.") }
        val items = (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }

        val apkCandidates = items.mapNotNull { entry ->
            val name = entry.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
            // Previously `lastOrNull()` over all digits turned 2027.1.8 into 8.
            val code = versionCodeFromApkName(name) ?: return@mapNotNull null
            val download = entry.optString("download_url")
            if (!isSecureUrl(download)) return@mapNotNull null
            AppUpdateInfo(
                versionCode = code,
                versionName = name.removeSuffix(".apk"),
                apkUrl = download,
                sizeBytes = entry.optLong("size", 0L),
                notes = "APK publicado en el repositorio.",
            )
        }

        val manifestEntry = items.firstOrNull {
            it.optString("name").equals("update.json", ignoreCase = true)
        }
        val manifestInfo = manifestEntry?.let { entry ->
            val manifestUrl = entry.optString("download_url")
            if (!isSecureUrl(manifestUrl)) null else runCatching {
                val connection = URL(manifestUrl).openConnection() as HttpsURLConnection
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val json = org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val code = json.optInt("versionCode", 0)
                val apkUrl = json.optString("apkUrl")
                if (code <= 0 || !isSecureUrl(apkUrl)) null else AppUpdateInfo(
                    versionCode = code,
                    versionName = json.optString("versionName").ifBlank { code.toString() },
                    apkUrl = apkUrl,
                    sha256 = json.optString("sha256").lowercase(),
                    sizeBytes = json.optLong("sizeBytes", 0L),
                    notes = sanitizeNotes(json.optString("notes")),
                    minSdk = json.optInt("minSdk", 0),
                )
            }.getOrNull()
        }

        val best = listOfNotNull(manifestInfo, apkCandidates.maxByOrNull { it.versionCode })
            .maxByOrNull { it.versionCode }
            ?: return@withContext UpdateFetchResult.NotFound
        UpdateFetchResult.Found(best)
    }

    private fun parseReleases(raw: String): UpdateFetchResult {
        val array = runCatching { org.json.JSONArray(raw) }
            .getOrElse { return UpdateFetchResult.Error("Respuesta inválida de GitHub.") }
        // GitHub returns releases newest-first; filter drafts/prereleases explicitly.
        val candidates = (0 until array.length()).mapNotNull { index ->
            val release = array.optJSONObject(index) ?: return@mapNotNull null
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) return@mapNotNull null
            parseRelease(release)
        }
        val info = candidates.maxByOrNull { it.versionCode }
            ?: return UpdateFetchResult.NotFound
        return UpdateFetchResult.Found(info)
    }

    private fun parseRelease(release: JSONObject): AppUpdateInfo? {
        val tag = release.optString("tag_name").removePrefix("v").trim()
        val body = release.optString("body").trim()
        val assets = release.optJSONArray("assets") ?: return null
        val publishedAt = release.optString("published_at")

        val candidates = (0 until assets.length()).mapNotNull { index ->
            val asset = assets.optJSONObject(index) ?: return@mapNotNull null
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
            val code = ASSET_VERSION_REGEX.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: VERSION_CODE_REGEX.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: Regex("""\d+""").find(tag)?.value?.toIntOrNull()
                ?: Regex("""\d+""").find(name)?.value?.toIntOrNull()
                ?: 0
            val url = asset.optString("browser_download_url")
            if (!isSecureUrl(url)) return@mapNotNull null
            val digest = asset.optString("digest").removePrefix("sha256:").lowercase()
                .takeIf { SHA256_REGEX.matches(it) }
            AppUpdateInfo(
                versionCode = code,
                versionName = tag.ifBlank { code.toString() },
                apkUrl = url,
                sha256 = digest.orEmpty(),
                sizeBytes = asset.optLong("size", 0L),
                notes = sanitizeNotes(body).ifBlank { "Publicado el ${publishedAt.take(10)}" },
                minSdk = Regex("""(?i)minSdk[:\s]+(\d+)""")
                    .find(body)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0,
            )
        }
        return candidates.maxByOrNull { it.versionCode }
    }

    /**
     * GitHub release bodies are written for developers. The in-app updater
     * shows them to every user, so strip Markdown and build metadata and keep
     * only readable sentences.
     */
    fun sanitizeNotes(raw: String): String = raw.lines()
        .map { line ->
            line.trim()
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .trimStart('#', '*', '_', '-', '+', '>', ' ')
                .trim()
        }
        .filter { it.isNotEmpty() }
        .filterNot { line ->
            TECHNICAL_LINE_REGEX.matches(line) || SHA256_REGEX.matches(line)
        }
        .joinToString("\n")
        .trim()

    fun targetFile(context: Context, info: AppUpdateInfo): File {
        val dir = context.getExternalFilesDir("updates")
            ?: File(context.filesDir, "updates").apply { mkdirs() }
        val name = "$ASSET_PREFIX-${info.versionCode}.apk"
        return File(dir, name)
    }

    fun startDownload(context: Context, info: AppUpdateInfo, wifiOnly: Boolean): Long {
        if (!isSecureUrl(info.apkUrl)) return -1L
        val target = targetFile(context, info)
        if (target.exists()) target.delete()
        val request = android.app.DownloadManager.Request(info.apkUrl.toUri()).apply {
            setTitle("Actualizando Lark Player")
            setDescription("Descargando Lark Player ${info.versionName}")
            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE)
            setMimeType("application/vnd.android.package-archive")
            setAllowedOverMetered(!wifiOnly)
            setAllowedOverRoaming(false)
            if (context.getExternalFilesDir("updates") != null) {
                setDestinationInExternalFilesDir(context, "updates", target.name)
            } else {
                setDestinationUri(Uri.fromFile(target))
            }
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        return runCatching { manager.enqueue(request) }.getOrDefault(-1L)
    }

    fun queryDownload(context: Context, id: Long): DownloadSnapshot? {
        if (id <= 0) return null
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        val cursor = runCatching { manager.query(android.app.DownloadManager.Query().setFilterById(id)) }
            .getOrNull() ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return null
            val statusIndex = c.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
            if (statusIndex < 0) return null
            return DownloadSnapshot(
                status = c.getInt(statusIndex),
                downloadedBytes = c.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    .let { if (it >= 0) c.getLong(it) else 0L },
                totalBytes = c.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    .let { if (it >= 0) c.getLong(it) else 0L },
                localUri = c.getColumnIndex(android.app.DownloadManager.COLUMN_LOCAL_URI)
                    .let { if (it >= 0) c.getString(it) else null },
            )
        }
    }

    fun removeDownload(context: Context, id: Long) {
        if (id <= 0) return
        runCatching {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).remove(id)
        }
    }

    fun isUnmetered(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        manager.getNetworkCapabilities(network)?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_NOT_METERED,
        ) ?: false
    }.getOrDefault(false)

    /** Returns null when the APK is valid, otherwise the reason to reject it. */
    fun validateApk(context: Context, file: File, info: AppUpdateInfo, currentCode: Int): String? {
        if (!file.exists() || !file.isFile) return "El archivo descargado no existe."
        if (file.length() <= 0L) return "El archivo descargado está vacío."
        if (file.length() > MAX_APK_BYTES) return "El APK supera el tamaño máximo permitido."
        if (info.sizeBytes > 0L && file.length() != info.sizeBytes) {
            return "El tamaño del APK no coincide con el publicado."
        }
        if (info.sha256.isNotBlank()) {
            val actual = runCatching { sha256(file) }.getOrNull()
                ?: return "No se pudo calcular el hash del APK."
            if (!actual.equals(info.sha256, ignoreCase = true)) {
                return "El hash SHA-256 del APK no coincide."
            }
        }
        val archive = archiveInfo(context, file)
            ?: return "No se pudo leer el APK descargado."
        if (archive.packageName != context.packageName) {
            return "El APK pertenece a otra aplicación."
        }
        val code = longVersionCode(archive)
        if (code <= currentCode.toLong()) return "El APK no contiene una versión más nueva."
        if (info.versionCode > 0 && code != info.versionCode.toLong()) {
            return "La versión del APK no coincide con la publicada."
        }
        if (!signaturesMatch(context, archive)) {
            return "La firma del APK no coincide con la app instalada."
        }
        return null
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun signatureFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }

    private fun archiveInfo(context: Context, file: File): PackageInfo? = runCatching {
        context.packageManager.getPackageArchiveInfo(file.absolutePath, signatureFlags())
    }.getOrNull()

    private fun longVersionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else @Suppress("DEPRECATION") info.versionCode.toLong()

    private fun signaturesMatch(context: Context, archive: PackageInfo): Boolean {
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, signatureFlags())
        }.getOrNull() ?: return false
        val installedHashes = signatureHashes(installed)
        val archiveHashes = signatureHashes(archive)
        return archiveHashes.isNotEmpty() && archiveHashes.any { it in installedHashes }
    }

    private fun signatureHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION") info.signatures.orEmpty()
        }
        return signatures.mapNotNull { signature ->
            runCatching {
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }.toSet()
    }

    fun installApk(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.isSuccess
    }
}
