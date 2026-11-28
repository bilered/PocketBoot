package com.pocketboot.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager as WebCookieManager
import android.net.http.SslError
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean

enum class WindowsCatalogFamily(
    val id: String,
    val title: String,
    val subtitle: String,
    val badgeLabel: String,
    val sizeHint: String,
    val supportsMicrosoftCdn: Boolean,
    val ntriverKey: String,
    val searchTags: String = "",
    val isPrimaryFamily: Boolean = false
) {
    WIN11(
        id = "win11",
        title = "Windows 11",
        subtitle = "Latest Monthly MVS & Retail ISO (26H2 / 25H2 / 24H2)",
        badgeLabel = "Archive + MS CDN",
        sizeHint = "~5.5–8.5 GB",
        supportsMicrosoftCdn = true,
        ntriverKey = "win11",
        searchTags = "win11 26h2 25h2 24h2 consumer business arm64",
        isPrimaryFamily = true
    ),
    WIN10(
        id = "win10",
        title = "Windows 10",
        subtitle = "Latest 22H2 Consumer & Business ISO (x64 & x86)",
        badgeLabel = "Archive + MS CDN",
        sizeHint = "~4.8–6.0 GB",
        supportsMicrosoftCdn = true,
        ntriverKey = "win10",
        searchTags = "win10 22h2 consumer business x86 x64",
        isPrimaryFamily = true
    ),
    WIN_LTSC(
        id = "win_ltsc",
        title = "Windows 10 / 11 LTSC",
        subtitle = "Enterprise & IoT LTSC 2024 / 2021 / 2019 (x64, ARM64, x86)",
        badgeLabel = "Archive Direct ISO",
        sizeHint = "~4.2–5.6 GB",
        supportsMicrosoftCdn = false,
        ntriverKey = "ltsc",
        searchTags = "ltsc iot enterprise 2024 2021 2019 win11 win10",
        isPrimaryFamily = false
    ),
    WIN81(
        id = "win81",
        title = "Windows 8.1",
        subtitle = "Windows 8.1 RTM & Update 3 ISO (x64 & x86)",
        badgeLabel = "Archive + MS CDN",
        sizeHint = "~3.6–4.3 GB",
        supportsMicrosoftCdn = true,
        ntriverKey = "win81",
        searchTags = "win8.1 win81 8.1 update 3",
        isPrimaryFamily = false
    ),
    WIN7(
        id = "win7",
        title = "Windows 7 SP1",
        subtitle = "Windows 7 SP1 Ultimate / Pro / Home ISO (x64 & x86)",
        badgeLabel = "Archive Direct ISO",
        sizeHint = "~2.4–3.2 GB",
        supportsMicrosoftCdn = false,
        ntriverKey = "win7",
        searchTags = "win7 win 7 sp1 ultimate professional",
        isPrimaryFamily = false
    ),
    WIN_SERVER(
        id = "win_server",
        title = "Windows Server",
        subtitle = "Windows Server 2025 / 2022 / 2019 RTM ISO (x64)",
        badgeLabel = "Archive Direct ISO",
        sizeHint = "~4.5–6.2 GB",
        supportsMicrosoftCdn = false,
        ntriverKey = "server",
        searchTags = "server 2025 2022 2019 datacenter standard",
        isPrimaryFamily = false
    )
}

data class DirectWindowsRelease(
    val osFamily: String,
    val releaseLabel: String,
    val editionLabel: String,
    val editionId: Int,
    val isArm64Only: Boolean = false
)

/**
 * Groups x64 and ARM64 edition IDs under one clean release pill (e.g. "26H2", "25H2", "24H2").
 */
data class DirectReleaseGroup(
    val osFamily: String,
    val releaseLabel: String,
    val shortChipLabel: String,
    val x64Release: DirectWindowsRelease?,
    val arm64Release: DirectWindowsRelease?
) {
    val defaultRelease: DirectWindowsRelease
        get() = x64Release ?: requireNotNull(arm64Release)
}

data class DirectLanguageSku(
    val skuId: String,
    val language: String,
    val localizedLanguage: String
)

data class DirectIsoLink(
    val arch: String,
    val fileName: String,
    val url: String
)

data class DirectApiResult<T>(
    val data: T? = null,
    val errorMessage: String? = null,
    val isRateLimited: Boolean = false
)

object WindowsDirectCatalog {

    private const val UPSTREAM_CATALOG_URL = "https://raw.githubusercontent.com/pbatard/Fido/master/Fido.ps1"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"

    @Volatile private var profileId = "606624d44113"
    @Volatile private var mdtInstanceId = "560dc9f3-1aa5-4a2f-b63c-9e18f8d0e175"
    @Volatile private var orgId = "y6jn8c31"
    @Volatile private var mdtCustomerId = "71828191-7845-4120-8833-4a943b6b3b21"

    private val releasesCache = ConcurrentHashMap<String, List<DirectWindowsRelease>>()
    private val skuCache = ConcurrentHashMap<String, Pair<String, List<DirectLanguageSku>>>()

    private val fallbackReleases = listOf(
        DirectWindowsRelease("win11", "26H2 (Latest Retail)", "Windows 11 Home/Pro/Edu", 3813, isArm64Only = false),
        DirectWindowsRelease("win11", "26H2 (Latest Retail)", "Windows 11 Home/Pro/Edu (ARM64)", 3816, isArm64Only = true),
        DirectWindowsRelease("win11", "25H2 (Build 26200)", "Windows 11 Home/Pro/Edu", 3262, isArm64Only = false),
        DirectWindowsRelease("win11", "25H2 (Build 26200)", "Windows 11 Home/Pro/Edu (ARM64)", 3263, isArm64Only = true),
        DirectWindowsRelease("win11", "24H2 (Build 26100)", "Windows 11 Home/Pro/Edu", 3113, isArm64Only = false),
        DirectWindowsRelease("win11", "24H2 (Build 26100)", "Windows 11 Home/Pro/Edu (ARM64)", 3131, isArm64Only = true),
        DirectWindowsRelease("win10", "22H2 v1 (Build 19045)", "Windows 10 Home/Pro/Edu", 2618, isArm64Only = false),
        DirectWindowsRelease("win81", "8.1 Update 3 (Build 9600)", "Windows 8.1 Core/Pro", 52, isArm64Only = false)
    )

    private val localCookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    private fun httpGet(
        urlStr: String,
        referer: String? = null,
        acceptJson: Boolean = false,
        followRedirects: Boolean = true
    ): Pair<Int, String> {
        val url = URL(urlStr)
        require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS is required for official Windows catalog requests." }
        val uri = runCatching { url.toURI() }.getOrNull()
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 18_000
            instanceFollowRedirects = followRedirects
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Accept", if (acceptJson) "application/json, text/plain, */*" else "*/*")
            if (!referer.isNullOrBlank()) {
                setRequestProperty("Referer", referer)
                setRequestProperty("Origin", "https://www.microsoft.com")
            }
            if (uri != null) {
                runCatching {
                    val cookieVals = localCookieManager.get(uri, emptyMap())["Cookie"]
                    if (!cookieVals.isNullOrEmpty()) {
                        setRequestProperty("Cookie", cookieVals.joinToString("; "))
                    }
                }
            }
        }
        return try {
            val code = conn.responseCode
            require(conn.url.protocol.equals("https", ignoreCase = true)) { "Rejected redirect to non-HTTPS URL." }
            if (uri != null) {
                runCatching { localCookieManager.put(uri, conn.headerFields) }
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to body
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchReleases(family: WindowsCatalogFamily, forceRefresh: Boolean = false): List<DirectWindowsRelease> =
        withContext(Dispatchers.IO) {
            if (!family.supportsMicrosoftCdn) return@withContext emptyList()
            val famKey = when (family) {
                WindowsCatalogFamily.WIN11 -> "win11"
                WindowsCatalogFamily.WIN10 -> "win10"
                WindowsCatalogFamily.WIN81 -> "win81"
                else -> return@withContext emptyList()
            }
            if (!forceRefresh) {
                releasesCache[famKey]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
            }

            val parsed = runCatching {
                val (code, script) = httpGet(UPSTREAM_CATALOG_URL)
                if (code !in 200..299 || script.isBlank()) emptyList() else {
                    extractProtocolConstants(script)
                    parseReleaseTable(script)
                }
            }.getOrDefault(emptyList())

            val forFamily = parsed.filter { it.osFamily == famKey }
                .ifEmpty { fallbackReleases.filter { it.osFamily == famKey } }
                .distinctBy { it.editionId }
                .sortedWith(compareBy<DirectWindowsRelease> { it.isArm64Only }.thenByDescending { it.editionId })

            if (forFamily.isNotEmpty()) {
                releasesCache[famKey] = forFamily
            }
            forFamily
        }

    fun groupReleases(releases: List<DirectWindowsRelease>): List<DirectReleaseGroup> {
        val orderedKeys = LinkedHashMap<String, Pair<DirectWindowsRelease?, DirectWindowsRelease?>>()
        releases.sortedByDescending { it.editionId }.forEach { rel ->
            val key = extractShortReleaseKey(rel.releaseLabel)
            val existing = orderedKeys[key]
            if (existing == null) {
                orderedKeys[key] = if (rel.isArm64Only) (null to rel) else (rel to null)
            } else {
                val x64 = existing.first ?: if (!rel.isArm64Only) rel else null
                val arm = existing.second ?: if (rel.isArm64Only) rel else null
                orderedKeys[key] = x64 to arm
            }
        }
        return orderedKeys.entries.mapNotNull { (shortKey, pair) ->
            val primary = pair.first ?: pair.second ?: return@mapNotNull null
            DirectReleaseGroup(
                osFamily = primary.osFamily,
                releaseLabel = primary.releaseLabel,
                shortChipLabel = shortKey,
                x64Release = pair.first,
                arm64Release = pair.second
            )
        }
    }

    private fun extractShortReleaseKey(releaseLabel: String): String {
        val m = Regex("""\b(2[0-9]H[12](?:\s+v\d+)?)\b""", RegexOption.IGNORE_CASE).find(releaseLabel)
        if (m != null) return m.groupValues[1].uppercase()
        return releaseLabel.substringBefore('(').trim().take(18).ifBlank { "Latest" }
    }

    private fun extractProtocolConstants(script: String) {
        Regex("""\${'$'}ProfileId\s*=\s*"([0-9a-fA-F-]{8,40})"""").find(script)?.groupValues?.get(1)?.let {
            if (it.isNotBlank()) profileId = it
        }
        Regex("""instanceId=([0-9a-fA-F-]{16,40})""").find(script)?.groupValues?.get(1)?.let {
            if (it.isNotBlank()) mdtInstanceId = it
        }
        Regex("""org_id=([a-zA-Z0-9]{4,16})""").find(script)?.groupValues?.get(1)?.let {
            if (it.isNotBlank()) orgId = it
        }
        Regex("""CustomerId=([0-9a-fA-F-]{16,40})""").find(script)?.groupValues?.get(1)?.let {
            if (it.isNotBlank()) mdtCustomerId = it
        }
    }

    /**
     * Parses upstream `$WindowsVersions` PowerShell table accurately:
     * - Detects Windows 11 vs Windows 10 headers even with `windows10ISO` or short tags
     * - Supports BOTH array-tuple edition rows `@("Windows 11 Home/Pro/Edu", @(3813, 3816))`
     *   (where element 0 = x64 and element 1 = ARM64) AND single-ID rows `@("Windows 10 Home/Pro/Edu", 2618)`
     * - Prevents Win 10 edition IDs from ever leaking into Win 11.
     */
    private fun parseReleaseTable(script: String): List<DirectWindowsRelease> {
        val out = mutableListOf<DirectWindowsRelease>()
        val startIdx = script.indexOf("\$WindowsVersions")
        if (startIdx < 0) return emptyList()
        val body = script.substring(startIdx).substringBefore("\nfunction ").take(60_000)

        var currentOs = ""
        var currentRelease = ""

        val osWin11Regex = Regex("""@\(\s*"Windows\s+11"\s*,""", RegexOption.IGNORE_CASE)
        val osWin10Regex = Regex("""@\(\s*"Windows\s+10"\s*,""", RegexOption.IGNORE_CASE)
        val osWin81Regex = Regex("""@\(\s*"Windows\s+8\.1"\s*,""", RegexOption.IGNORE_CASE)
        val osOtherRegex = Regex("""@\(\s*"(?:Windows\s+7|UEFI\s+Shell)[^"]*"\s*,""", RegexOption.IGNORE_CASE)

        val releaseRegex = Regex("""@\(\s*"([^"]*(?:2[0-9]H[12]|v\d|Build\s+\d+)[^"]*)"\s*,""", RegexOption.IGNORE_CASE)
        // Tuple form: @("Windows 11 Home/Pro/Edu", @(3813, 3816))
        val tupleEditionRegex = Regex("""@\(\s*"([^"]+)"\s*,\s*@\(\s*(\d{3,5})\s*(?:,\s*(\d{3,5}))?\s*\)\s*\)""")
        // Single form: @("Windows 10 Home/Pro/Edu", 2618)
        val singleEditionRegex = Regex("""@\(\s*"([^"]+)"\s*,\s*(\d{1,5})\s*\)""")

        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("#")) return@forEach

            if (osWin11Regex.containsMatchIn(line)) {
                currentOs = "win11"
                currentRelease = ""
                return@forEach
            }
            if (osWin10Regex.containsMatchIn(line)) {
                currentOs = "win10"
                currentRelease = ""
                return@forEach
            }
            if (osWin81Regex.containsMatchIn(line)) {
                currentOs = "win81"
                currentRelease = ""
                return@forEach
            }
            if (osOtherRegex.containsMatchIn(line)) {
                currentOs = ""
                currentRelease = ""
                return@forEach
            }
            if (currentOs.isEmpty()) return@forEach

            // Check tuple edition format FIRST (Windows 11 x64 + ARM64 pairs)
            tupleEditionRegex.find(line)?.let { m ->
                val edLabel = m.groupValues[1].trim()
                if (!edLabel.contains("China", true)) {
                    val x64Id = m.groupValues[2].toIntOrNull()
                    val armId = m.groupValues[3].toIntOrNull()
                    val rel = currentRelease.ifBlank { "Latest Retail" }
                    if (x64Id != null) {
                        out.add(
                            DirectWindowsRelease(
                                osFamily = currentOs,
                                releaseLabel = rel,
                                editionLabel = edLabel,
                                editionId = x64Id,
                                isArm64Only = false
                            )
                        )
                    }
                    if (armId != null && armId != x64Id) {
                        out.add(
                            DirectWindowsRelease(
                                osFamily = currentOs,
                                releaseLabel = rel,
                                editionLabel = "$edLabel (ARM64)",
                                editionId = armId,
                                isArm64Only = true
                            )
                        )
                    }
                }
                return@forEach
            }

            // Check single-integer edition format (Windows 10 or single-arch Win 11)
            singleEditionRegex.find(line)?.let { m ->
                val edLabel = m.groupValues[1].trim()
                val edId = m.groupValues[2].toIntOrNull() ?: return@forEach
                if (!edLabel.contains("China", true)) {
                    // Sanity guard: never allow known Windows 10 IDs (<= 2700) inside win11
                    if (currentOs == "win11" && edId < 2700) return@forEach
                    val isArm = edLabel.contains("arm64", true) || currentRelease.contains("arm64", true)
                    val rel = currentRelease.ifBlank { "Latest Retail" }
                    out.add(
                        DirectWindowsRelease(
                            osFamily = currentOs,
                            releaseLabel = rel,
                            editionLabel = edLabel,
                            editionId = edId,
                            isArm64Only = isArm
                        )
                    )
                }
                return@forEach
            }

            releaseRegex.find(line)?.let { m ->
                val lbl = m.groupValues[1].trim()
                if (!lbl.startsWith("Windows ", true)) {
                    currentRelease = lbl
                }
            }
        }
        return out
    }

    private fun initializeSession(sessionId: String, osFamily: String) {
        val pageSlug = when (osFamily) { "win11" -> "windows11"; "win81" -> "windows8ISO"; else -> "windows10ISO" }
        val pageUrl = "https://www.microsoft.com/en-us/software-download/$pageSlug"

        runCatching { httpGet(pageUrl) }
        runCatching {
            httpGet(
                "https://vlscppe.microsoft.com/tags?org_id=$orgId&session_id=$sessionId",
                referer = pageUrl,
                followRedirects = false
            )
        }
        runCatching {
            val mdtUrl = "https://ov-df.microsoft.com/mdt.js?instanceId=$mdtInstanceId&PageId=si&session_id=$sessionId"
            val (_, jsBody) = httpGet(mdtUrl, referer = pageUrl)
            val wVal = Regex("""[?&]w=([^&'"\s]+)""").find(jsBody)?.groupValues?.get(1)
            val custVal = Regex("""CustomerId=([0-9a-fA-F-]{16,40})""").find(jsBody)?.groupValues?.get(1) ?: mdtCustomerId
            val rticks = Regex("""rticks['":=\s]+(\d+)""").find(jsBody)?.groupValues?.get(1)
            val nowMs = System.currentTimeMillis()
            if (!wVal.isNullOrBlank()) {
                val replyUrl = buildString {
                    append("https://ov-df.microsoft.com/?session_id=$sessionId")
                    append("&CustomerId=$custVal&PageId=si")
                    append("&w=${URLEncoder.encode(wVal, "UTF-8")}")
                    append("&mdt=$nowMs")
                    if (!rticks.isNullOrBlank()) append("&rticks=$rticks")
                }
                httpGet(replyUrl, referer = pageUrl)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun executeViaChromiumSession(
        context: Context,
        osFamily: String,
        apiUrl: String
    ): String? = withTimeoutOrNull(18_000L) {
        suspendCancellableCoroutine { cont ->
            val mainHandler = Handler(Looper.getMainLooper())
            val finished = AtomicBoolean(false)
            var wv: WebView? = null

            fun finish(result: String?) {
                if (!finished.compareAndSet(false, true)) return
                mainHandler.post {
                    runCatching {
                        wv?.stopLoading()
                        wv?.loadUrl("about:blank")
                        wv?.removeJavascriptInterface("PocketBootBridge")
                        wv?.destroy()
                    }
                    wv = null
                }
                if (cont.isActive) cont.resume(result)
            }

            cont.invokeOnCancellation { finish(null) }

            mainHandler.post {
                if (!cont.isActive || finished.get()) return@post
                val pageSlug = when (osFamily) { "win11" -> "windows11"; "win81" -> "windows8ISO"; else -> "windows10ISO" }
                val originUrl = "https://www.microsoft.com/en-us/software-download/$pageSlug"
                try {
                    val webView = WebView(context.applicationContext)
                    wv = webView
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.settings.allowFileAccess = false
                    webView.settings.allowContentAccess = false
                    webView.settings.loadsImagesAutomatically = false
                    webView.settings.blockNetworkImage = true
                    webView.settings.userAgentString = UA
                    WebCookieManager.getInstance().setAcceptCookie(true)
                    WebCookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

                    webView.addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onApiResult(json: String) {
                            finish(json)
                        }

                        @JavascriptInterface
                        fun onApiError(err: String) {
                            finish(null)
                        }
                    }, "PocketBootBridge")

                    val triggered = AtomicBoolean(false)
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            val loaded = url.orEmpty()
                            if (view == null || finished.get() || !loaded.startsWith("https://www.microsoft.com", ignoreCase = true)) return
                            if (!triggered.compareAndSet(false, true)) return
                            val safeUrl = apiUrl.replace("\\", "\\\\").replace("'", "\\'")
                            val js = """
                                (function(){
                                  setTimeout(function(){
                                    fetch('$safeUrl', { credentials: 'include', headers: { 'Accept': 'application/json' } })
                                      .then(function(r){ return r.text(); })
                                      .then(function(t){ PocketBootBridge.onApiResult(t); })
                                      .catch(function(e){ PocketBootBridge.onApiError(String(e)); });
                                  }, 900);
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(js, null)
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) {
                                finish(null)
                            }
                        }

                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            handler?.cancel()
                            finish(null)
                        }
                    }

                    if (!cont.isActive || finished.get()) {
                        finish(null)
                        return@post
                    }
                    webView.loadUrl(originUrl)
                } catch (_: Exception) {
                    finish(null)
                }
            }
        }
    }

    private fun parseSkusFromBody(body: String): DirectApiResult<List<DirectLanguageSku>> {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return DirectApiResult(errorMessage = "Invalid response from Microsoft servers.")
        val errors = root.optJSONArray("Errors")
        if (errors != null && errors.length() > 0) {
            val errVal = errors.optJSONObject(0)?.optString("Value", "").orEmpty()
            val blocked = errVal.contains("715-123130") || errVal.contains("automated", true) || errVal.contains("banned", true)
            return DirectApiResult(
                errorMessage = if (blocked) {
                    "Microsoft temporarily rate-limited downloads on this IP/VPN. Disable VPN, switch between Wi-Fi and mobile data, or tap Retry Microsoft CDN."
                } else errVal.ifBlank { "Microsoft rejected the language lookup." },
                isRateLimited = blocked
            )
        }
        val arr = root.optJSONArray("Skus") ?: JSONArray()
        val skus = mutableListOf<DirectLanguageSku>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val id = s.optString("Id", "").trim()
            val lang = s.optString("Language", "").trim()
            val loc = s.optString("LocalizedLanguage", lang).trim().ifBlank { lang }
            if (id.isNotEmpty() && loc.isNotEmpty()) {
                skus.add(DirectLanguageSku(skuId = id, language = lang, localizedLanguage = loc))
            }
        }
        val sorted = skus.sortedWith(
            compareBy<DirectLanguageSku> { !it.localizedLanguage.contains("English (United States)", true) }
                .thenBy { !it.localizedLanguage.startsWith("English", true) }
                .thenBy { it.localizedLanguage.lowercase() }
        )
        return if (sorted.isEmpty()) {
            DirectApiResult(errorMessage = "No languages returned for this release.", isRateLimited = true)
        } else {
            DirectApiResult(data = sorted)
        }
    }

    private fun parseLinksFromBody(body: String, release: DirectWindowsRelease): DirectApiResult<List<DirectIsoLink>> {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: return DirectApiResult(errorMessage = "Invalid download response from Microsoft.")
        val errors = root.optJSONArray("Errors")
        if (errors != null && errors.length() > 0) {
            val errVal = errors.optJSONObject(0)?.optString("Value", "").orEmpty()
            val blocked = errVal.contains("715-123130") || errVal.contains("automated", true) || errVal.contains("validate", true)
            return DirectApiResult(
                errorMessage = if (blocked) {
                    "Microsoft temporarily rate-limited link signing on this IP. Disable VPN or switch networks, then tap Retry Microsoft CDN."
                } else errVal.ifBlank { "Could not sign download link." },
                isRateLimited = blocked
            )
        }
        val arr = root.optJSONArray("ProductDownloadOptions") ?: JSONArray()
        val links = mutableListOf<DirectIsoLink>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val uri = item.optString("Uri", "").trim()
            if (!uri.startsWith("http", true)) continue
            val fileName = uri.substringAfterLast('/').substringBefore('?').ifBlank { "Windows.iso" }
            val dlType = item.optInt("DownloadType", -1)
            val arch = when {
                release.isArm64Only || fileName.contains("arm64", true) || fileName.contains("a64", true) -> "ARM64"
                dlType == 0 || fileName.contains("x32", true) || fileName.contains("x86", true) -> "x86"
                else -> "x64"
            }
            links.add(DirectIsoLink(arch = arch, fileName = fileName, url = uri))
        }
        val sorted = links.sortedBy {
            when (it.arch) {
                "x64" -> 0
                "ARM64" -> 1
                else -> 2
            }
        }
        return if (sorted.isEmpty()) {
            DirectApiResult(errorMessage = "Microsoft returned no download links for this language right now. Try another language or tap Retry Microsoft CDN.", isRateLimited = true)
        } else {
            DirectApiResult(data = sorted)
        }
    }

    suspend fun fetchLanguages(
        context: Context,
        release: DirectWindowsRelease,
        forceRefresh: Boolean = false
    ): DirectApiResult<Pair<String, List<DirectLanguageSku>>> = withContext(Dispatchers.IO) {
        val cacheKey = "${release.osFamily}_${release.editionId}"
        if (!forceRefresh) {
            skuCache[cacheKey]?.let { return@withContext DirectApiResult(data = it) }
        }

        val sessionId = UUID.randomUUID().toString()
        val pageSlug = when (release.osFamily) { "win11" -> "windows11"; "win81" -> "windows8ISO"; else -> "windows10ISO" }
        val referer = "https://www.microsoft.com/en-us/software-download/$pageSlug"
        val apiUrl = "https://www.microsoft.com/software-download-connector/api/getskuinformationbyproductedition" +
            "?profile=$profileId&productEditionId=${release.editionId}" +
            "&SKU=undefined&friendlyFileName=undefined&Locale=en-US&sessionID=$sessionId"

        runCatching {
            initializeSession(sessionId, release.osFamily)
            val (code, body) = httpGet(apiUrl, referer = referer, acceptJson = true)
            if (code in 200..299 && body.isNotBlank()) {
                val parsed = parseSkusFromBody(body)
                if (parsed.data != null) {
                    val pair = sessionId to parsed.data
                    skuCache[cacheKey] = pair
                    return@withContext DirectApiResult(data = pair)
                }
            }
        }

        val wvBody = executeViaChromiumSession(context, release.osFamily, apiUrl)
        if (!wvBody.isNullOrBlank()) {
            val parsed = parseSkusFromBody(wvBody)
            if (parsed.data != null) {
                val pair = sessionId to parsed.data
                skuCache[cacheKey] = pair
                return@withContext DirectApiResult(data = pair)
            }
            return@withContext DirectApiResult(errorMessage = parsed.errorMessage, isRateLimited = parsed.isRateLimited)
        }

        DirectApiResult(
            errorMessage = "Microsoft temporarily blocked direct retail queries on this IP/VPN. Disable VPN, switch networks, and tap Retry Microsoft CDN.",
            isRateLimited = true
        )
    }

    suspend fun fetchDownloadLinks(
        context: Context,
        release: DirectWindowsRelease,
        sessionId: String,
        skuId: String
    ): DirectApiResult<List<DirectIsoLink>> = withContext(Dispatchers.IO) {
        val pageSlug = when (release.osFamily) { "win11" -> "windows11"; "win81" -> "windows8ISO"; else -> "windows10ISO" }
        val referer = "https://www.microsoft.com/en-us/software-download/$pageSlug"
        val apiUrl = "https://www.microsoft.com/software-download-connector/api/GetProductDownloadLinksBySku" +
            "?profile=$profileId&productEditionId=undefined&SKU=$skuId" +
            "&friendlyFileName=undefined&Locale=en-US&sessionID=$sessionId"

        runCatching {
            val (code, body) = httpGet(apiUrl, referer = referer, acceptJson = true)
            if (code in 200..299 && body.isNotBlank()) {
                val parsed = parseLinksFromBody(body, release)
                if (parsed.data != null) return@withContext parsed
            }
        }

        val wvBody = executeViaChromiumSession(context, release.osFamily, apiUrl)
        if (!wvBody.isNullOrBlank()) {
            return@withContext parseLinksFromBody(wvBody, release)
        }

        DirectApiResult(
            errorMessage = "Microsoft temporarily rate-limited direct link signing on this connection. Switch networks or tap Retry Microsoft CDN.",
            isRateLimited = true
        )
    }
}
