package com.pocketboot.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipInputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

enum class ChecksumVerifyStatus {
    NONE,
    VERIFIED_MATCH,
    COMPUTED_ONLY,
    MISMATCH
}

data class DownloadCompletionEvent(
    val title: String,
    val fileName: String,
    val savedPath: String,
    val injectedMultiBootPath: String?,
    val computedSha256: String?,
    val expectedSha256: String?,
    val checksumStatus: ChecksumVerifyStatus,
    val autoMount: Boolean,
    val buildBenchmark: String? = null,
    val bootVerifiedLabel: String? = null
)

data class PendingResumableDownload(
    val metaFilePath: String,
    val partFilePath: String,
    val title: String,
    val fileName: String,
    val url: String,
    val targetDir: String,
    val targetMultiBootPath: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val expectedSha256: String,
    val autoMount: Boolean,
    val refreshSourceKey: String = ""
) {
    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class TurboDownloadState(
    val title: String,
    val fileName: String,
    val url: String,
    val targetDir: String,
    val targetMultiBootPath: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val etaSeconds: Long = -1L,
    val activeThreads: Int = 1,
    val supportsRange: Boolean = false,
    val isPaused: Boolean = false,
    val isVerifying: Boolean = false,
    val isInjectingMultiBoot: Boolean = false,
    val statusText: String = "Connecting...",
    val refreshSourceKey: String = ""
) {
    val progress: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

class TurboDownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val isPausedAction = intent?.action == ACTION_PAUSE_LOCKS
        val notif = NotificationHelper.buildDownloadNotification(this, FastIsoDownloader.state.value)
        val promoted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.DL_NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NotificationHelper.DL_NOTIF_ID, notif)
            }
        }.isSuccess
        if (!promoted) {
            releaseLocks()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (isPausedAction) {
            releaseLocks()
        } else {
            acquireLocks()
        }
        return START_NOT_STICKY
    }

    private fun acquireLocks() {
        runCatching {
            if (wakeLock == null) {
                val pm = getSystemService(PowerManager::class.java)
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketBoot::TurboDownload")?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(2L * 60L * 60L * 1000L)
        }
        runCatching {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(WifiManager::class.java)
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm?.createWifiLock(mode, "PocketBoot::TurboWifi")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) wifiLock?.acquire()
        }
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        FastIsoDownloader.pause()
        releaseLocks()
        stopSelf(startId)
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_PAUSE_LOCKS = "com.pocketboot.app.ACTION_TURBO_PAUSE_LOCKS"

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, TurboDownloadService::class.java))
            }
        }

        fun pauseLocks(context: Context) {
            runCatching {
                val intent = Intent(context, TurboDownloadService::class.java).setAction(ACTION_PAUSE_LOCKS)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, TurboDownloadService::class.java))
            }
        }
    }
}

object FastIsoDownloader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<TurboDownloadState?>(null)
    val state: StateFlow<TurboDownloadState?> = _state.asStateFlow()

    private val _lastCompletion = MutableStateFlow<DownloadCompletionEvent?>(null)
    val lastCompletion: StateFlow<DownloadCompletionEvent?> = _lastCompletion.asStateFlow()

    private var currentJob: Job? = null
    private val pausedFlag = AtomicBoolean(false)
    private val cancelledFlag = AtomicBoolean(false)

    private var chunkStarts: LongArray = LongArray(0)
    private var chunkEnds: LongArray = LongArray(0)
    @Volatile private var chunkCursors: AtomicLongArray = AtomicLongArray(0)
    private var stagingFile: File? = null
    private var sidecarFile: File? = null
    @Volatile private var cachedFinalUrl: String = ""
    private var cachedTotalBytes: Long = 0L
    private var cachedThreads: Int = 8
    private var cachedAutoMount: Boolean = true
    private var cachedAutoVerifySha256: Boolean = true
    private var cachedExpectedSha256: String = ""
    private var cachedTargetMultiBootPath: String = ""
    @Volatile private var cachedRefreshSourceKey: String = ""
    private val urlRefreshMutex = Mutex()
    @Volatile private var lastUrlRefreshMs: Long = 0L
    private var cachedOnComplete: ((DownloadCompletionEvent) -> Unit)? = null
    private var cachedOnError: ((String) -> Unit)? = null
    private var cachedOnLog: ((LogEntry) -> Unit)? = null
    @Volatile private var cachedUserAgent: String = BROWSER_UA
    private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 15) PocketBoot/4.11.0"
    private const val WGET_UA = "Wget/1.21.4"
    private const val CURL_UA = "curl/8.5.0"
    private fun logDl(msg: String, isError: Boolean = false) { cachedOnLog?.invoke(LogEntry(SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()), "download", msg, isError)) }
    private var cachedContext: Context? = null

    private fun sanitizeDownloadFileName(raw: String, fallback: String = "downloaded-image.iso"): String {
        val cleaned = raw
            .replace('\\', '/')
            .substringAfterLast('/')
            .replace("..", "")
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .trim('.')
        return cleaned.ifBlank { fallback }
    }

    fun dismissLastCompletion() {
        _lastCompletion.value = null
    }

    fun formatSpeed(bytesPerSec: Long): String = when {
        bytesPerSec >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
        bytesPerSec >= 1024L -> String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
        bytesPerSec > 0L -> "$bytesPerSec B/s"
        else -> "0 KB/s"
    }

    fun formatEta(seconds: Long): String {
        if (seconds < 0L || seconds > 86_400L) return "--:--"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%dh %02dm", h, m)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun stagingDir(context: Context): File {
        val baseDir = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.cacheDir
        return File(baseDir, "turbo_dl").apply { mkdirs() }
    }

    fun scanPendingResumableDownloads(context: Context): List<PendingResumableDownload> {
        val dir = stagingDir(context)
        val activePartPath = stagingFile?.absolutePath
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".pbmeta") }
            ?.mapNotNull { meta ->
                runCatching {
                    val obj = JSONObject(meta.readText(Charsets.UTF_8))
                    val partPath = obj.optString("partFilePath", "")
                    if (partPath.isBlank() || partPath == activePartPath) return@mapNotNull null
                    val partFile = File(partPath)
                    if (!partFile.exists()) {
                        meta.delete()
                        return@mapNotNull null
                    }
                    val starts = obj.optJSONArray("chunkStarts") ?: return@mapNotNull null
                    val cursors = obj.optJSONArray("chunkCursors") ?: return@mapNotNull null
                    var done = 0L
                    for (i in 0 until minOf(starts.length(), cursors.length())) {
                        done += (cursors.optLong(i) - starts.optLong(i)).coerceAtLeast(0L)
                    }
                    PendingResumableDownload(
                        metaFilePath = meta.absolutePath,
                        partFilePath = partPath,
                        title = obj.optString("title", partFile.name.removeSuffix(".part")),
                        fileName = obj.optString("fileName", partFile.name.removeSuffix(".part")),
                        url = obj.optString("url", ""),
                        targetDir = obj.optString("targetDir", "/data/media/0/Download"),
                        targetMultiBootPath = obj.optString("targetMultiBootPath", ""),
                        downloadedBytes = done,
                        totalBytes = obj.optLong("totalBytes", 0L),
                        expectedSha256 = obj.optString("expectedSha256", ""),
                        autoMount = obj.optBoolean("autoMount", true),
                        refreshSourceKey = obj.optString("refreshSourceKey", "")
                    )
                }.getOrNull()
            }
            ?.sortedByDescending { it.downloadedBytes }
            .orEmpty()
    }

    fun discardPendingDownload(pending: PendingResumableDownload) {
        runCatching { File(pending.partFilePath).delete() }
        runCatching { File(pending.metaFilePath).delete() }
    }

    /**
     * Atomically writes JSON sidecar metadata via a sibling `.tmp` file + rename so an unexpected
     * kill or power loss during periodic progress saves never truncates a valid `.pbmeta` file.
     */
    internal fun writeSidecarAtomically(metaFile: File, jsonPayload: String): Boolean {
        return runCatching {
            val tmpFile = File(metaFile.parentFile, "${metaFile.name}.tmp")
            tmpFile.writeText(jsonPayload, Charsets.UTF_8)
            if (tmpFile.renameTo(metaFile)) {
                true
            } else {
                metaFile.writeText(jsonPayload, Charsets.UTF_8)
                tmpFile.delete()
                true
            }
        }.getOrDefault(false)
    }

    @Synchronized
    private fun saveSidecarMeta() {
        val cur = _state.value ?: return
        val part = stagingFile ?: return
        val meta = sidecarFile ?: return
        val cursors = chunkCursors
        if (!cur.supportsRange || chunkStarts.isEmpty() || cursors.length() < chunkStarts.size) return
        runCatching {
            val obj = JSONObject().apply {
                put("title", cur.title)
                put("fileName", cur.fileName)
                put("url", cur.url)
                put("finalUrl", cachedFinalUrl)
                put("targetDir", cur.targetDir)
                put("targetMultiBootPath", cachedTargetMultiBootPath)
                put("partFilePath", part.absolutePath)
                put("totalBytes", cachedTotalBytes)
                put("expectedSha256", cachedExpectedSha256)
                put("refreshSourceKey", cachedRefreshSourceKey)
                put("autoMount", cachedAutoMount)
                put("autoVerifySha256", cachedAutoVerifySha256)
                put("threadCount", chunkStarts.size)
                put("chunkStarts", JSONArray().apply { chunkStarts.forEach { put(it) } })
                put("chunkEnds", JSONArray().apply { chunkEnds.forEach { put(it) } })
                put("chunkCursors", JSONArray().apply {
                    for (i in 0 until chunkStarts.size) put(cursors.get(i))
                })
            }
            writeSidecarAtomically(meta, obj.toString())
        }
    }

    private fun normalizeDownloadUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim(); val low = trimmed.lowercase()
        if (low.contains("sourceforge.net/projects/") && low.contains("/files/") && !low.endsWith("/download")) return trimmed.trimEnd('/') + "/download"
        if (low.contains("github.com/") && low.contains("/blob/")) return trimmed.replace("/blob/", "/raw/")
        return trimmed
    }
    private fun resolveDynamicApiUrl(rawUrl: String): Triple<String, String?, String?> {
        val low = rawUrl.lowercase()
        if (low.contains("api.pop-os.org/builds/")) {
            logDl("Resolving latest Pop!_OS ISO from System76 API...")
            val conn = openFollowingRedirects(rawUrl, "GET", userAgent = CURL_UA)
            try {
                if (conn.responseCode in 200..299) {
                    val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val isoUrl = obj.optString("url", "").trim(); val sha = obj.optString("sha_sum", "").trim()
                    if (isoUrl.startsWith("http")) { logDl("Pop!_OS resolved -> $isoUrl"); return Triple(isoUrl, sha.ifBlank { null }, isoUrl.substringAfterLast('/').substringBefore('?').ifBlank { null }) }
                }
            } finally { conn.disconnect() }
        }
        if (low.contains("tails.net/install/v2/tails/") && low.endsWith(".json")) {
            logDl("Resolving latest Tails stable image from tails.net...")
            val conn = openFollowingRedirects(rawUrl, "GET", userAgent = CURL_UA)
            try {
                if (conn.responseCode in 200..299) {
                    val paths = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("installations")?.optJSONObject(0)?.optJSONArray("installation-paths")
                    if (paths != null) for (i in 0 until paths.length()) {
                        val tfs = paths.optJSONObject(i)?.optJSONArray("target-files") ?: continue
                        for (j in 0 until tfs.length()) {
                            val tf = tfs.optJSONObject(j) ?: continue; val u = tf.optString("url", "").trim(); val sha = tf.optString("sha256", "").trim()
                            if (u.endsWith(".img") || u.endsWith(".iso")) { logDl("Tails resolved -> $u"); return Triple(u, sha.ifBlank { null }, u.substringAfterLast('/').ifBlank { null }) }
                        }
                    }
                }
            } finally { conn.disconnect() }
        }
        return Triple(rawUrl, null, null)
    }
    private fun prefersCliUserAgent(urlStr: String): Boolean { val low = urlStr.lowercase(); return low.contains("sourceforge.net") || low.contains("osdn.net") || low.contains("fosshub.com") }
    /**
     * Pure-JVM testable host-scoped cookie selector so foreign cookies (e.g. Cloudflare cookies from an index host)
     * are NEVER leaked to redirected S3/R2/OneDrive/CDN storage mirrors (which reject foreign cookies with HTTP 400/403).
     */
    internal fun selectCookiesForHost(cookiesByHost: Map<String, Map<String, String>>, host: String): String {
        val h = host.trim().lowercase(Locale.US)
        if (h.isEmpty()) return ""
        val hostJar = cookiesByHost[h].orEmpty()
        return if (hostJar.isEmpty()) "" else hostJar.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun openFollowingRedirects(urlStr: String, method: String, rangeHeader: String? = null, userAgent: String = cachedUserAgent): HttpURLConnection {
        var current = urlStr; var redirects = 0
        val cookiesByHost = LinkedHashMap<String, LinkedHashMap<String, String>>()
        while (redirects < 12) {
            val curUrl = URL(current)
            val curHost = curUrl.host.orEmpty().lowercase(Locale.US)
            val cookieHeader = selectCookiesForHost(cookiesByHost, curHost)
            val conn = (curUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = method; instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 25_000
                setRequestProperty("User-Agent", userAgent); setRequestProperty("Accept", "*/*"); setRequestProperty("Accept-Encoding", "identity"); setRequestProperty("Connection", "keep-alive")
                if (cookieHeader.isNotEmpty()) setRequestProperty("Cookie", cookieHeader)
                if (rangeHeader != null) setRequestProperty("Range", rangeHeader)
            }
            val code = conn.responseCode
            val hostJar = cookiesByHost.getOrPut(curHost) { LinkedHashMap() }
            conn.headerFields.entries
                .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
                .flatMap { it.value.orEmpty() }
                .forEach { rc ->
                    val pair = rc.substringBefore(';').trim()
                    val eq = pair.indexOf('=')
                    if (eq > 0) hostJar[pair.substring(0, eq).trim()] = pair.substring(eq + 1).trim()
                }
            if (code in listOf(301, 302, 303, 307, 308)) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw IllegalStateException("Redirect without Location header")
                val nextUrl = URL(curUrl, loc).toString()
                if (ShellSafety.isForbiddenHttpsDowngrade(current, nextUrl)) {
                    throw IllegalStateException("Blocked insecure HTTPS-to-HTTP redirect from trusted host")
                }
                current = nextUrl
                redirects++
            } else {
                return conn
            }
        }
        throw IllegalStateException("Too many redirects (12+)")
    }
    private data class ProbeResult(val finalUrl: String, val totalBytes: Long, val acceptsRanges: Boolean, val isHtmlPage: Boolean, val httpCode: Int = 200, val contentType: String = "", val negotiatedUserAgent: String = BROWSER_UA)
    private fun extractMirrorUrlFromHtml(baseUrl: String, html: String): String? {
        Regex("""<meta[^>]+http-equiv=["']?refresh["']?[^>]+content=["'][^"']*url=([^"'\s>]+)""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)?.let { raw ->
            val clean = raw.replace("&amp;", "&").trim(); if (clean.startsWith("http") || clean.startsWith("/")) return runCatching { URL(URL(baseUrl), clean).toString() }.getOrNull()
        }
        return Regex("""href=["'](https?://[^"']+\.(?:iso|img|iso\.zip|img\.zip)(?:\?[^"']*)?)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)?.replace("&amp;", "&")
    }
    internal fun isUnexpectedSmallErrorPayload(contentType: String, contentLength: Long): Boolean {
        val ct = contentType.trim().lowercase(Locale.US)
        val isStructuredError = ct.contains("application/json") || ct.contains("application/xml") || ct.contains("text/xml")
        return isStructuredError && contentLength in 0L..65_535L
    }

    private fun probeWithUa(urlStr: String, ua: String): Pair<ProbeResult, String?> {
        val conn = openFollowingRedirects(urlStr, "GET", rangeHeader = "bytes=0-32767", userAgent = ua)
        var retryWithoutRange = false
        try {
            val code = conn.responseCode; val finalUrl = conn.url.toString(); val cType = conn.contentType.orEmpty().lowercase(Locale.US)
            if (code == 416) {
                retryWithoutRange = true
            } else {
                if (cType.contains("text/html") || cType.contains("application/xhtml")) {
                    val snippet = runCatching { val s = if (code in 200..399) conn.inputStream else conn.errorStream; val buf = ByteArray(32_768); val n = s?.read(buf) ?: -1; if (n > 0) String(buf, 0, n, Charsets.UTF_8) else "" }.getOrDefault("")
                    return ProbeResult(finalUrl, -1L, false, true, code, cType, ua) to extractMirrorUrlFromHtml(finalUrl, snippet)
                }
                if (code == 206) {
                    val total = conn.getHeaderField("Content-Range").orEmpty().substringAfter('/', "").trim().toLongOrNull() ?: -1L
                    if (isUnexpectedSmallErrorPayload(cType, total)) {
                        throw IllegalStateException("Server returned unexpected $cType payload instead of binary image")
                    }
                    return ProbeResult(finalUrl, total, total > 0L, false, code, cType, ua) to null
                }
                if (code in 200..299) {
                    val len = conn.contentLengthLong
                    if (isUnexpectedSmallErrorPayload(cType, len)) {
                        throw IllegalStateException("Server returned unexpected $cType payload instead of binary image")
                    }
                    return ProbeResult(finalUrl, len, false, false, code, cType, ua) to null
                }
                throw IllegalStateException("Server returned HTTP $code ($finalUrl)")
            }
        } finally { conn.disconnect() }

        if (retryWithoutRange) {
            val plainConn = openFollowingRedirects(urlStr, "GET", rangeHeader = null, userAgent = ua)
            try {
                val code = plainConn.responseCode; val finalUrl = plainConn.url.toString(); val cType = plainConn.contentType.orEmpty().lowercase(Locale.US)
                if (cType.contains("text/html") || cType.contains("application/xhtml")) {
                    val snippet = runCatching { val s = if (code in 200..399) plainConn.inputStream else plainConn.errorStream; val buf = ByteArray(32_768); val n = s?.read(buf) ?: -1; if (n > 0) String(buf, 0, n, Charsets.UTF_8) else "" }.getOrDefault("")
                    return ProbeResult(finalUrl, -1L, false, true, code, cType, ua) to extractMirrorUrlFromHtml(finalUrl, snippet)
                }
                if (code in 200..299) {
                    val len = plainConn.contentLengthLong
                    if (isUnexpectedSmallErrorPayload(cType, len)) {
                        throw IllegalStateException("Server returned unexpected $cType payload instead of binary image")
                    }
                    return ProbeResult(finalUrl, len, false, false, code, cType, ua) to null
                }
                throw IllegalStateException("Server returned HTTP $code ($finalUrl)")
            } finally { plainConn.disconnect() }
        }
        throw IllegalStateException("Probe failed unexpectedly for $urlStr")
    }
    private fun probeServer(urlStr: String): ProbeResult {
        val uaOrder = if (prefersCliUserAgent(urlStr)) listOf(WGET_UA, CURL_UA, BROWSER_UA) else listOf(BROWSER_UA, WGET_UA, CURL_UA)
        var lastProbe = ProbeResult(urlStr, -1L, false, true, 0, "unknown", uaOrder.first()); var extractedMirror: String? = null; var lastException: Exception? = null
        for (ua in uaOrder) {
            try {
                val (res, mirror) = probeWithUa(urlStr, ua); lastProbe = res
                if (mirror != null && extractedMirror == null) extractedMirror = mirror
                if (!res.isHtmlPage) { if (ua != uaOrder.first()) logDl("Negotiated binary stream with UA=$ua -> ${res.finalUrl}"); return res }
                logDl("Host returned HTML (${res.httpCode}, ${res.contentType}) with UA=$ua; trying fallback...")
            } catch (e: Exception) { lastException = e; logDl("Probe attempt with UA=$ua failed: ${e.message}", isError = true) }
        }
        if (!extractedMirror.isNullOrBlank() && extractedMirror != urlStr) {
            logDl("Found mirror redirect in HTML -> $extractedMirror")
            runCatching { val (res, _) = probeWithUa(extractedMirror, WGET_UA); if (!res.isHtmlPage) return res }
        }
        if (lastProbe.httpCode == 0 && lastException != null) throw lastException
        return lastProbe
    }

    fun start(
        context: Context,
        title: String,
        url: String,
        fileName: String,
        targetDir: String,
        targetMultiBootPath: String = "",
        approxBytes: Long = 0L,
        expectedSha256: String = "",
        refreshSourceKey: String = "",
        connections: Int = 8,
        autoMount: Boolean = true,
        autoVerifySha256: Boolean = true,
        onComplete: (DownloadCompletionEvent) -> Unit,
        onError: (String) -> Unit,
        onLog: (LogEntry) -> Unit = {}
    ) {
        val wasPaused = _state.value?.isPaused == true
        if (wasPaused) {
            saveSidecarMeta()
            cancel(deleteTemp = false)
        } else {
            cancel(deleteTemp = true)
        }
        pausedFlag.set(false)
        cancelledFlag.set(false)

        val cleanName = sanitizeDownloadFileName(fileName, "downloaded-image.iso")
        val clampedThreads = connections.coerceIn(1, 16)

        cachedContext = context.applicationContext
        cachedThreads = clampedThreads
        cachedAutoMount = autoMount
        cachedAutoVerifySha256 = autoVerifySha256
        cachedExpectedSha256 = expectedSha256.trim().lowercase()
        cachedTargetMultiBootPath = targetMultiBootPath.trim()
        cachedRefreshSourceKey = refreshSourceKey.trim()
        lastUrlRefreshMs = 0L
        cachedOnComplete = onComplete
        cachedOnError = onError
        cachedOnLog = onLog
        cachedUserAgent = BROWSER_UA

        _state.value = TurboDownloadState(
            title = title,
            fileName = cleanName,
            url = url.trim(),
            targetDir = targetDir,
            targetMultiBootPath = cachedTargetMultiBootPath,
            totalBytes = approxBytes,
            activeThreads = clampedThreads,
            statusText = "Probing server...",
            refreshSourceKey = cachedRefreshSourceKey
        )
        TurboDownloadService.start(context.applicationContext)

        currentJob = scope.launch {
            try {
                val normalized = normalizeDownloadUrl(url)
                logDl("Starting download: $title ($normalized)")
                val (resolvedUrl, dynamicSha, dynamicName) = resolveDynamicApiUrl(normalized)
                if (!dynamicSha.isNullOrBlank() && cachedExpectedSha256.isBlank()) cachedExpectedSha256 = dynamicSha.lowercase()
                if (!dynamicName.isNullOrBlank() && (cleanName.startsWith("pop-os_latest", true) || cleanName.startsWith("tails-amd64-latest", true))) {
                    val safeDynamic = sanitizeDownloadFileName(dynamicName, cleanName)
                    _state.value = _state.value?.copy(fileName = safeDynamic)
                }
                val probe = probeServer(resolvedUrl)
                if (probe.isHtmlPage) {
                    val isMsLink = resolvedUrl.contains("microsoft.com", true) || resolvedUrl.contains("software-static", true)
                    val hint = if (isMsLink) "Disable VPN or switch networks and retry, or tap Copy Link." else "Check Activity Log for the resolved URL or try another mirror."
                    val msg = "Server returned a web page (HTTP ${probe.httpCode}, ${probe.contentType.ifBlank { "text/html" }}) instead of an ISO file. $hint"
                    logDl("HTML rejection at ${probe.finalUrl} (HTTP ${probe.httpCode}, ${probe.contentType})", isError = true)
                    throw IllegalStateException(msg)
                }
                cachedUserAgent = probe.negotiatedUserAgent
                val total = if (probe.totalBytes > 0L) probe.totalBytes else approxBytes
                val useThreads = if (probe.acceptsRanges && probe.totalBytes >= 4L * 1024L * 1024L) clampedThreads else 1
                cachedFinalUrl = probe.finalUrl
                cachedTotalBytes = probe.totalBytes
                logDl("Resolved stream: ${probe.finalUrl} (${UsbGadgetEngine.formatBytes(total)}, range=${probe.acceptsRanges}, ${useThreads}x)")

                val dlDir = stagingDir(context)
                val tmpFile = File(dlDir, "$cleanName.part")
                val metaFile = File(dlDir, "$cleanName.part.pbmeta")
                if (tmpFile.exists()) tmpFile.delete()
                if (metaFile.exists()) metaFile.delete()
                if (total > 0L) {
                    val freeStage = UsbGadgetEngine.getFreeSpaceBytes(dlDir.absolutePath)
                    val neededStage = total + (128L * 1024L * 1024L)
                    if (freeStage in 1L until neededStage) {
                        throw IllegalStateException("Not enough free phone storage (${UsbGadgetEngine.formatBytes(freeStage)} free). Need at least ${UsbGadgetEngine.formatBytes(neededStage)} for $cleanName.")
                    }
                }
                stagingFile = tmpFile
                sidecarFile = metaFile

                if (useThreads > 1 && probe.totalBytes > 0L) {
                    RandomAccessFile(tmpFile, "rw").use { it.setLength(probe.totalBytes) }
                    chunkStarts = LongArray(useThreads)
                    chunkEnds = LongArray(useThreads)
                    val initCursors = AtomicLongArray(useThreads)
                    val slice = probe.totalBytes / useThreads
                    for (i in 0 until useThreads) {
                        val s = i * slice
                        val e = if (i == useThreads - 1) probe.totalBytes - 1L else (s + slice - 1L)
                        chunkStarts[i] = s
                        chunkEnds[i] = e
                        initCursors.set(i, s)
                    }
                    chunkCursors = initCursors
                } else {
                    chunkStarts = longArrayOf(0L)
                    chunkEnds = longArrayOf(if (probe.totalBytes > 0L) probe.totalBytes - 1L else -1L)
                    chunkCursors = AtomicLongArray(longArrayOf(0L))
                }

                _state.value = _state.value?.copy(
                    totalBytes = total,
                    activeThreads = useThreads,
                    supportsRange = probe.acceptsRanges,
                    statusText = if (useThreads > 1) "Turbo · $useThreads connections" else "Single-stream download"
                )
                saveSidecarMeta()

                runDownloadWorkers(useThreads, probe.acceptsRanges)
            } catch (_: CancellationException) {
                // Handled by pause() or cancel()
            } catch (e: Exception) {
                saveSidecarMeta()
                _state.value = null
                cachedContext?.let { TurboDownloadService.stop(it) }
                logDl("Download failed: ${e.message ?: "Network error"}", isError = true)
                onError("Download failed: ${e.message ?: "Network error"}")
            }
        }
    }

    /**
     * Pure-JVM testable parser & validator for `.pbmeta` chunk arrays (`1..16` equal-length arrays,
     * non-negative ordered boundaries, and cursors clamped to `start..(end + 1)`).
     */
    internal fun parseValidatedSidecarChunks(obj: JSONObject): Triple<LongArray, LongArray, LongArray>? {
        val sArr = obj.optJSONArray("chunkStarts") ?: return null
        val eArr = obj.optJSONArray("chunkEnds") ?: return null
        val cArr = obj.optJSONArray("chunkCursors") ?: return null
        val n = sArr.length()
        if (n !in 1..16 || eArr.length() != n || cArr.length() != n) return null
        val starts = LongArray(n)
        val ends = LongArray(n)
        val cursors = LongArray(n)
        for (i in 0 until n) {
            val st = sArr.optLong(i, -1L)
            val en = eArr.optLong(i, -1L)
            if (st < 0L || en < st) return null
            starts[i] = st
            ends[i] = en
            cursors[i] = cArr.optLong(i, st).coerceIn(st, en + 1L)
        }
        return Triple(starts, ends, cursors)
    }

    fun resumeFromSidecar(
        context: Context,
        pending: PendingResumableDownload,
        autoVerifySha256: Boolean,
        onComplete: (DownloadCompletionEvent) -> Unit,
        onError: (String) -> Unit,
        onLog: (LogEntry) -> Unit = {}
    ) {
        val metaFile = File(pending.metaFilePath)
        val partFile = File(pending.partFilePath)
        if (!metaFile.exists() || !partFile.exists()) {
            onError("Resumable session files are no longer available.")
            return
        }
        val obj = runCatching { JSONObject(metaFile.readText(Charsets.UTF_8)) }.getOrNull()
        if (obj == null) {
            onError("Corrupted resume metadata.")
            return
        }

        val validated = parseValidatedSidecarChunks(obj)
        if (validated == null) {
            onError("Incomplete or corrupted resume metadata.")
            return
        }
        val (parsedStarts, parsedEnds, parsedCursors) = validated
        val n = parsedStarts.size

        cancel(deleteTemp = false)
        pausedFlag.set(false)
        cancelledFlag.set(false)

        chunkStarts = parsedStarts
        chunkEnds = parsedEnds
        chunkCursors = AtomicLongArray(parsedCursors)
        stagingFile = partFile
        sidecarFile = metaFile
        cachedContext = context.applicationContext
        cachedFinalUrl = obj.optString("finalUrl", pending.url).ifBlank { pending.url }
        cachedTotalBytes = obj.optLong("totalBytes", pending.totalBytes)
        cachedThreads = n
        cachedAutoMount = obj.optBoolean("autoMount", pending.autoMount)
        cachedAutoVerifySha256 = autoVerifySha256
        cachedExpectedSha256 = obj.optString("expectedSha256", pending.expectedSha256)
        cachedTargetMultiBootPath = obj.optString("targetMultiBootPath", pending.targetMultiBootPath)
        cachedRefreshSourceKey = obj.optString("refreshSourceKey", pending.refreshSourceKey)
        lastUrlRefreshMs = 0L
        cachedUserAgent = BROWSER_UA
        cachedOnComplete = onComplete
        cachedOnError = onError
        cachedOnLog = onLog

        val done = (0 until n).sumOf { i -> (chunkCursors.get(i) - chunkStarts[i]).coerceAtLeast(0L) }
        _state.value = TurboDownloadState(
            title = pending.title,
            fileName = pending.fileName,
            url = pending.url,
            targetDir = pending.targetDir,
            targetMultiBootPath = cachedTargetMultiBootPath,
            downloadedBytes = done,
            totalBytes = cachedTotalBytes,
            activeThreads = n,
            supportsRange = true,
            isPaused = false,
            statusText = "Resuming · $n connections",
            refreshSourceKey = cachedRefreshSourceKey
        )
        TurboDownloadService.start(context.applicationContext)

        currentJob = scope.launch {
            try {
                val candidateUrl = cachedFinalUrl.ifBlank { pending.url }
                val reqStartedMs = System.currentTimeMillis()
                var remoteSizeMismatch = false
                val probeOk = runCatching {
                    val probe = probeServer(candidateUrl)
                    val sizeOk = cachedTotalBytes <= 0L || probe.totalBytes <= 0L || probe.totalBytes == cachedTotalBytes
                    if (!sizeOk) remoteSizeMismatch = true
                    if (!probe.isHtmlPage && probe.finalUrl.isNotBlank() && probe.acceptsRanges && sizeOk) {
                        cachedFinalUrl = probe.finalUrl
                        cachedUserAgent = probe.negotiatedUserAgent
                        true
                    } else false
                }.getOrDefault(false)
                if (!probeOk) {
                    if (cachedRefreshSourceKey.isNotBlank()) {
                        if (!tryRefreshExpiredUrl(candidateUrl, reqStartedMs)) {
                            throw IllegalStateException("Download link expired and could not be refreshed. Tap Retry or re-download from the catalog.")
                        }
                    } else if (remoteSizeMismatch) {
                        throw IllegalStateException("Remote file size changed since download was paused; discard and restart to prevent a corrupt ISO.")
                    }
                }
                runDownloadWorkers(n, true)
            } catch (_: CancellationException) {
                // Paused or cancelled
            } catch (e: Exception) {
                saveSidecarMeta()
                _state.value = null
                cachedContext?.let { TurboDownloadService.stop(it) }
                onError("Resume failed: ${e.message ?: "Network error"}")
            }
        }
    }

    fun pause() {
        val cur = _state.value ?: return
        if (!cur.supportsRange || cur.isPaused || cur.isVerifying || cur.isInjectingMultiBoot) return
        pausedFlag.set(true)
        currentJob?.cancel()
        currentJob = null
        saveSidecarMeta()
        val pausedState = cur.copy(isPaused = true, speedBytesPerSec = 0L, etaSeconds = -1L, statusText = "Paused")
        _state.value = pausedState
        cachedContext?.let {
            TurboDownloadService.pauseLocks(it)
            NotificationHelper.updateDownloadProgress(it, pausedState)
        }
    }

    fun resume() {
        val cur = _state.value ?: return
        if (!cur.isPaused) return
        pausedFlag.set(false)
        cancelledFlag.set(false)
        val resumedState = cur.copy(isPaused = false, statusText = "Resuming · ${cur.activeThreads} connections")
        _state.value = resumedState
        cachedContext?.let { TurboDownloadService.start(it) }
        currentJob = scope.launch {
            try {
                if (cachedRefreshSourceKey.isNotBlank()) {
                    val reqStartedMs = System.currentTimeMillis()
                    val stillAlive = runCatching {
                        val p = probeServer(cachedFinalUrl)
                        val rangeOk = !cur.supportsRange || p.acceptsRanges
                        val sizeOk = cachedTotalBytes <= 0L || p.totalBytes <= 0L || p.totalBytes == cachedTotalBytes
                        if (!p.isHtmlPage && p.httpCode in 200..299 && rangeOk && sizeOk) {
                            cachedFinalUrl = p.finalUrl
                            cachedUserAgent = p.negotiatedUserAgent
                            true
                        } else false
                    }.getOrDefault(false)
                    if (!stillAlive) {
                        if (!tryRefreshExpiredUrl(cachedFinalUrl, reqStartedMs)) {
                            throw IllegalStateException("Download link expired while paused and could not be refreshed.")
                        }
                    }
                }
                runDownloadWorkers(cur.activeThreads, cur.supportsRange)
            } catch (_: CancellationException) {
                // Paused or cancelled
            } catch (e: Exception) {
                saveSidecarMeta()
                _state.value = null
                cachedContext?.let { TurboDownloadService.stop(it) }
                cachedOnError?.invoke("Resume failed: ${e.message ?: "Network error"}")
            }
        }
    }

    fun updateAutoMount(value: Boolean) {
        cachedAutoMount = value
        saveSidecarMeta()
    }

    fun cancel(deleteTemp: Boolean = true) {
        cancelledFlag.set(true)
        pausedFlag.set(false)
        currentJob?.cancel()
        currentJob = null
        if (deleteTemp) {
            runCatching { stagingFile?.delete() }
            runCatching { sidecarFile?.delete() }
            stagingFile = null
            sidecarFile = null
            cachedRefreshSourceKey = ""
        }
        _state.value = null
        cachedContext?.let { TurboDownloadService.stop(it) }
    }

    private suspend fun runDownloadWorkers(threadCount: Int, useRanges: Boolean) {
        val tmpFile = stagingFile ?: throw IllegalStateException("Missing staging file")
        val initialDone = (0 until threadCount).sumOf { i -> (chunkCursors.get(i) - chunkStarts[i]).coerceAtLeast(0L) }
        val totalDownloaded = AtomicLong(initialDone)

        val meterJob = scope.launch {
            var lastBytes = totalDownloaded.get()
            var lastTime = System.currentTimeMillis()
            var smoothedSpeed = 0.0
            var tickCounter = 0
            while (isActive && !pausedFlag.get() && !cancelledFlag.get()) {
                delay(400L)
                tickCounter++
                val now = System.currentTimeMillis()
                val curBytes = totalDownloaded.get()
                val dt = (now - lastTime).coerceAtLeast(1L)
                val instant = ((curBytes - lastBytes).coerceAtLeast(0L) * 1000.0) / dt.toDouble()
                smoothedSpeed = if (smoothedSpeed == 0.0) instant else (smoothedSpeed * 0.65 + instant * 0.35)
                lastBytes = curBytes
                lastTime = now

                val curState = _state.value ?: break
                val speed = smoothedSpeed.toLong()
                val remaining = (curState.totalBytes - curBytes).coerceAtLeast(0L)
                val eta = if (speed > 1024L && curState.totalBytes > 0L) remaining / speed else -1L
                val updated = curState.copy(
                    downloadedBytes = curBytes,
                    speedBytesPerSec = speed,
                    etaSeconds = eta
                )
                _state.value = updated
                if (tickCounter % 2 == 0) {
                    cachedContext?.let { NotificationHelper.updateDownloadProgress(it, updated) }
                }
                if (tickCounter % 5 == 0) {
                    saveSidecarMeta()
                }
            }
        }

        try {
            if (useRanges && threadCount >= 1 && cachedTotalBytes > 0L && chunkStarts.size >= threadCount) {
                coroutineScope {
                    (0 until threadCount).map { idx ->
                        async(Dispatchers.IO) {
                            downloadChunkWithRetries(tmpFile, idx, totalDownloaded)
                        }
                    }.awaitAll()
                }
            } else {
                downloadSingleStream(tmpFile, totalDownloaded)
            }
        } finally {
            meterJob.cancel()
        }

        if (pausedFlag.get() || cancelledFlag.get()) return

        val ctx = cachedContext ?: return
        val finalState = _state.value ?: return
        _state.value = finalState.copy(
            downloadedBytes = finalState.totalBytes.coerceAtLeast(totalDownloaded.get()),
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            statusText = "Saving to ${UsbGadgetEngine.friendlyFolderName(finalState.targetDir)}..."
        )

        val targetDir = UsbGadgetEngine.resolveDirectPath(finalState.targetDir).trimEnd('/')
        val expectedBytes = tmpFile.length()
        if (expectedBytes <= 0L) {
            throw IllegalStateException("Downloaded staging file is missing or empty.")
        }
        var destName = finalState.fileName
        var finalDest = "$targetDir/$destName"
        if (UsbGadgetEngine.getStatus().mountedLunFor(finalDest) != null) {
            val stem = destName.substringBeforeLast('.')
            val ext = if (destName.contains('.')) ".${destName.substringAfterLast('.')}" else ""
            destName = "${stem}_new_${System.currentTimeMillis() % 10000}$ext"
            finalDest = "$targetDir/$destName"
            logDl("Target file is currently mounted to PC; saving download safely as $destName")
        }
        val qSrc = "'${tmpFile.absolutePath.replace("'", "'\\''")}'"
        val qDir = "'${targetDir.replace("'", "'\\''")}'"
        val qDst = "'${finalDest.replace("'", "'\\''")}'"

        val movedViaRoot = runCatching {
            val mvRes = Shell.cmd("mkdir -p $qDir && mv -f $qSrc $qDst").exec()
            if (!mvRes.isSuccess) return@runCatching false
            val dlSz = Shell.cmd("stat -c '%s' $qDst 2>/dev/null || echo 0").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            if (dlSz != expectedBytes) {
                Shell.cmd("rm -f $qDst 2>/dev/null || true").exec()
                return@runCatching false
            }
            true
        }.getOrDefault(false)

        val savedPath = if (movedViaRoot) {
            UsbGadgetEngine.fixUserStoragePermissions(ctx, targetDir, isDir = true)
            UsbGadgetEngine.fixUserStoragePermissions(ctx, finalDest, isDir = false)
            UsbGadgetEngine.syncMediaStoreFileSize(ctx, finalDest, expectedBytes)
            finalDest
        } else {
            val fallback = File(targetDir.replaceFirst("/data/media/0", "/storage/emulated/0"), destName)
            val fallbackOk = runCatching {
                fallback.parentFile?.mkdirs()
                tmpFile.copyTo(fallback, overwrite = true)
                if (fallback.length() != expectedBytes) {
                    fallback.delete()
                    return@runCatching false
                }
                tmpFile.delete()
                UsbGadgetEngine.fixUserStoragePermissions(ctx, finalDest, isDir = false)
                UsbGadgetEngine.syncMediaStoreFileSize(ctx, finalDest, expectedBytes)
                true
            }.getOrDefault(false)
            if (!fallbackOk) {
                throw IllegalStateException("Could not save $destName to ${UsbGadgetEngine.friendlyFolderName(finalState.targetDir)} (check free space & folder permissions).")
            }
            finalDest
        }

        IsoInspector.invalidate(savedPath)
        runCatching { sidecarFile?.delete() }
        stagingFile = null; sidecarFile = null; cachedRefreshSourceKey = ""
        var effectiveSavedPath = savedPath; var effectiveFileName = destName
        if (effectiveFileName.endsWith(".iso.zip", true) || effectiveFileName.endsWith(".img.zip", true)) {
            _state.value = finalState.copy(statusText = "Extracting ISO from zip archive...")
            extractImageFromZipArchive(ctx, effectiveSavedPath, targetDir)?.let { effectiveSavedPath = it; effectiveFileName = it.substringAfterLast('/'); logDl("Auto-extracted archive -> $effectiveFileName") }
        }

        var computedHash: String? = null
        var checksumStatus = ChecksumVerifyStatus.NONE
        if (cachedAutoVerifySha256 || cachedExpectedSha256.isNotBlank()) {
            val verifyState = finalState.copy(
                downloadedBytes = finalState.totalBytes.coerceAtLeast(totalDownloaded.get()),
                isVerifying = true,
                statusText = "Verifying SHA-256 checksum..."
            )
            _state.value = verifyState
            NotificationHelper.updateDownloadProgress(ctx, verifyState)

            computedHash = UsbGadgetEngine.computeSha256(effectiveSavedPath)?.trim()?.lowercase()
            checksumStatus = when {
                computedHash == null -> ChecksumVerifyStatus.NONE
                cachedExpectedSha256.isNotBlank() && computedHash.equals(cachedExpectedSha256, ignoreCase = true) ->
                    ChecksumVerifyStatus.VERIFIED_MATCH
                cachedExpectedSha256.isNotBlank() ->
                    ChecksumVerifyStatus.MISMATCH
                else -> ChecksumVerifyStatus.COMPUTED_ONLY
            }
        }

        var injectedMultiBoot: String? = null
        if (cachedTargetMultiBootPath.isNotBlank() && checksumStatus != ChecksumVerifyStatus.MISMATCH) {
            val mbPath = UsbGadgetEngine.resolveDirectPath(cachedTargetMultiBootPath)
            val statusNow = UsbGadgetEngine.getStatus()
            val mbMounted = statusNow.mountedLunFor(mbPath) != null
            if (!mbMounted) {
                val mbName = mbPath.substringAfterLast('/')
                val injState = finalState.copy(
                    isVerifying = false,
                    isInjectingMultiBoot = true,
                    statusText = "Moving ISO into $mbName..."
                )
                _state.value = injState
                NotificationHelper.updateDownloadProgress(ctx, injState)

                val addRes = VentoyImageBuilder.addIsoToVentoy(
                    context = ctx,
                    ventoyImgPath = mbPath,
                    sourceIsoPath = effectiveSavedPath,
                    moveFile = true,
                    onProgress = { p ->
                        val st = _state.value?.copy(statusText = p.detail.ifBlank { p.stage })
                        if (st != null) _state.value = st
                    }
                )
                if (addRes.success) {
                    injectedMultiBoot = mbPath
                }
            }
        }

        val completion = DownloadCompletionEvent(
            title = finalState.title,
            fileName = effectiveFileName,
            savedPath = effectiveSavedPath,
            injectedMultiBootPath = injectedMultiBoot,
            computedSha256 = computedHash,
            expectedSha256 = cachedExpectedSha256.ifBlank { null },
            checksumStatus = checksumStatus,
            autoMount = cachedAutoMount
        )
        _lastCompletion.value = completion
        _state.value = null
        TurboDownloadService.stop(ctx)

        val notifSub = when {
            injectedMultiBoot != null -> "Added to ${injectedMultiBoot.substringAfterLast('/')}"
            checksumStatus == ChecksumVerifyStatus.VERIFIED_MATCH -> "Verified genuine SHA-256 · Ready to mount"
            checksumStatus == ChecksumVerifyStatus.MISMATCH -> "Warning: SHA-256 checksum mismatch!"
            else -> "Saved to ${UsbGadgetEngine.friendlyFolderName(targetDir)}"
        }
        NotificationHelper.showDownloadComplete(ctx, finalState.title, notifSub)
        cachedOnComplete?.invoke(completion)
    }

    private fun extractImageFromZipArchive(context: Context, zipPath: String, targetDir: String): String? = runCatching {
        val localZip = File(zipPath.replaceFirst("/data/media/0", "/storage/emulated/0"))
        val stagedZip = File(context.cacheDir, "pb_extract_in.zip")
        val qZip = ShellSafety.shellQuote(zipPath)
        if (!localZip.canRead()) Shell.cmd("cp -f $qZip ${ShellSafety.shellQuote(stagedZip.absolutePath)} 2>/dev/null").exec()
        val srcFile = if (localZip.canRead()) localZip else stagedZip
        if (!srcFile.exists()) return null
        var extractedName: String? = null
        val outTmp = File(context.cacheDir, "pb_extract_out.bin")
        if (outTmp.exists()) outTmp.delete()
        try {
            ZipInputStream(srcFile.inputStream().buffered()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val rawLeaf = entry.name.replace('\\', '/').substringAfterLast('/')
                        if (rawLeaf.endsWith(".iso", true) || rawLeaf.endsWith(".img", true)) {
                            val reqExt = if (rawLeaf.endsWith(".img", true)) ".img" else ".iso"
                            val safeLeaf = UsbGadgetEngine.sanitizeOutputFileName(rawLeaf, reqExt, "extracted")
                            val entrySize = entry.size
                            if (entrySize > 0L) {
                                val freeCache = UsbGadgetEngine.getFreeSpaceBytes(context.cacheDir.absolutePath)
                                if (freeCache in 1L until (entrySize + (64L * 1024L * 1024L))) {
                                    logDl("Not enough free space (${UsbGadgetEngine.formatBytes(freeCache)}) to extract $safeLeaf (${UsbGadgetEngine.formatBytes(entrySize)})", isError = true)
                                    return null
                                }
                            }
                            val maxZipExtractBytes = 32L * 1024L * 1024L * 1024L
                            var written = 0L
                            val copyBuf = ByteArray(262_144)
                            outTmp.outputStream().buffered().use { out ->
                                while (true) {
                                    val n = zis.read(copyBuf)
                                    if (n <= 0) break
                                    written += n.toLong()
                                    if (written > maxZipExtractBytes) {
                                        throw IllegalStateException("Archive entry exceeds 32 GB safety limit")
                                    }
                                    out.write(copyBuf, 0, n)
                                }
                            }
                            extractedName = safeLeaf
                            break
                        }
                    }
                }
            }
        } finally {
            stagedZip.delete()
        }
        val finalName = extractedName ?: run { outTmp.delete(); return null }
        if (!outTmp.exists() || outTmp.length() <= 0L) {
            outTmp.delete()
            return null
        }
        val expectedOutBytes = outTmp.length()
        val finalOutPath = "${targetDir.trimEnd('/')}/$finalName"
        val qTmp = ShellSafety.shellQuote(outTmp.absolutePath)
        val qDst = ShellSafety.shellQuote(finalOutPath)
        if (Shell.cmd("mv -f $qTmp $qDst").exec().isSuccess) {
            val zSz = Shell.cmd("stat -c '%s' $qDst 2>/dev/null || echo 0").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            if (zSz != expectedOutBytes) {
                Shell.cmd("rm -f $qDst 2>/dev/null || true").exec()
                logDl("Extracted image size verification failed ($zSz != $expectedOutBytes); preserved source archive.", isError = true)
                return null
            }
            Shell.cmd("rm -f $qZip 2>/dev/null || true").exec()
            UsbGadgetEngine.fixUserStoragePermissions(context, finalOutPath, false)
            UsbGadgetEngine.syncMediaStoreFileSize(context, finalOutPath, zSz)
            IsoInspector.invalidate(finalOutPath)
            finalOutPath
        } else {
            outTmp.delete()
            null
        }
    }.getOrNull()

    private suspend fun tryRefreshExpiredUrl(failedUrl: String, failedAtMs: Long = System.currentTimeMillis()): Boolean {
        val key = cachedRefreshSourceKey
        val ctx = cachedContext ?: return false
        if (key.isBlank()) return false
        return urlRefreshMutex.withLock {
            if (pausedFlag.get() || cancelledFlag.get()) return@withLock false
            if (lastUrlRefreshMs >= failedAtMs && cachedFinalUrl.isNotBlank()) {
                return@withLock true
            }
            if (cachedFinalUrl != failedUrl && (System.currentTimeMillis() - lastUrlRefreshMs) < 15_000L) {
                return@withLock true
            }
            logDl("Signed URL expired — refreshing download link ($key)...")
            val res = NtriverCatalog.resolveByRefreshKey(ctx, key)
            if (pausedFlag.get() || cancelledFlag.get()) return@withLock false
            val freshUrl = res.url ?: return@withLock false
            val probe = runCatching { probeServer(freshUrl) }.getOrNull() ?: return@withLock false
            if (pausedFlag.get() || cancelledFlag.get()) return@withLock false
            if (probe.isHtmlPage || probe.finalUrl.isBlank()) return@withLock false
            if ((chunkStarts.size > 1 || _state.value?.supportsRange == true) && !probe.acceptsRanges) {
                logDl("Refreshed URL does not support HTTP byte ranges; refusing to corrupt partial resume.", isError = true)
                return@withLock false
            }
            if (cachedTotalBytes > 0L && probe.totalBytes > 0L && probe.totalBytes != cachedTotalBytes) {
                logDl("Refreshed URL size mismatch (${probe.totalBytes} != $cachedTotalBytes); refusing to corrupt partial file.", isError = true)
                return@withLock false
            }
            cachedFinalUrl = probe.finalUrl
            cachedUserAgent = probe.negotiatedUserAgent
            lastUrlRefreshMs = System.currentTimeMillis()
            _state.value = _state.value?.copy(url = freshUrl)
            saveSidecarMeta()
            logDl("Refreshed signed download URL cleanly.")
            true
        }
    }

    private suspend fun downloadChunkWithRetries(file: File, idx: Int, totalCounter: AtomicLong) {
        val end = chunkEnds[idx]
        var attempt = 0
        var refreshCount = 0
        val buf = ByteArray(262_144)
        while (chunkCursors.get(idx) <= end && !pausedFlag.get() && !cancelledFlag.get()) {
            val usedUrl = cachedFinalUrl
            val reqStartedMs = System.currentTimeMillis()
            var needRefresh = false
            try {
                val startPos = chunkCursors.get(idx)
                val conn = openFollowingRedirects(usedUrl, "GET", rangeHeader = "bytes=$startPos-$end")
                try {
                    val code = conn.responseCode
                    if (startPos > 0L && code == 200) {
                        throw IllegalStateException("Server ignored Range header (HTTP 200 at offset $startPos) on chunk $idx")
                    }
                    if (code in listOf(401, 403, 404, 410) && cachedRefreshSourceKey.isNotBlank() && refreshCount < 3) {
                        needRefresh = true
                    } else {
                        if (code != 206 && code != 200) {
                            throw IllegalStateException("HTTP $code on chunk $idx")
                        }
                    }
                    if (!needRefresh) {
                    RandomAccessFile(file, "rw").use { raf ->
                        raf.seek(startPos)
                        conn.inputStream.use { input ->
                            while (!pausedFlag.get() && !cancelledFlag.get()) {
                                val read = input.read(buf)
                                if (read <= 0) break
                                val remainingInChunk = (end - chunkCursors.get(idx) + 1L).coerceAtLeast(0L)
                                val toWrite = minOf(read.toLong(), remainingInChunk).toInt()
                                if (toWrite <= 0) break
                                raf.write(buf, 0, toWrite)
                                chunkCursors.addAndGet(idx, toWrite.toLong())
                                totalCounter.addAndGet(toWrite.toLong())
                            }
                        }
                    }
                    }
                } finally {
                    conn.disconnect()
                }
                if (needRefresh) {
                    refreshCount++
                    if (tryRefreshExpiredUrl(usedUrl, reqStartedMs)) {
                        attempt = 0
                        continue
                    }
                    throw IllegalStateException("Signed download link expired and could not be refreshed on chunk $idx")
                }
                if (chunkCursors.get(idx) <= startPos && !pausedFlag.get() && !cancelledFlag.get()) {
                    throw IllegalStateException("Server closed stream with 0 bytes read on chunk $idx")
                }
                attempt = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (pausedFlag.get() || cancelledFlag.get()) return
                attempt++
                if (attempt > 5) throw e
                delay(700L * attempt)
            }
        }
    }

    private fun downloadSingleStream(file: File, totalCounter: AtomicLong) {
        val buf = ByteArray(262_144)
        val conn = openFollowingRedirects(cachedFinalUrl, "GET")
        var written = 0L
        var expectedLen = -1L
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            expectedLen = conn.contentLengthLong
            if (expectedLen > 0L) {
                _state.value = _state.value?.copy(totalBytes = expectedLen)
            }
            RandomAccessFile(file, "rw").use { raf ->
                raf.setLength(0L)
                conn.inputStream.use { input ->
                    while (!pausedFlag.get() && !cancelledFlag.get()) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        raf.write(buf, 0, read)
                        written += read.toLong()
                        totalCounter.addAndGet(read.toLong())
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!pausedFlag.get() && !cancelledFlag.get()) {
            if (written <= 0L) throw IllegalStateException("Download stream returned 0 bytes")
            if (expectedLen > 0L && written != expectedLen) {
                throw IllegalStateException("Truncated download ($written / $expectedLen bytes)")
            }
        }
    }

}
