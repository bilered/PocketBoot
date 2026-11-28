package com.pocketboot.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val publishedAt: String
)

data class AppUpdateCheckResult(
    val success: Boolean,
    val updateAvailable: Boolean = false,
    val currentVersion: String = "",
    val release: AppReleaseInfo? = null,
    val message: String = ""
)

object AppUpdateChecker {
    const val REPO_URL = "https://github.com/bilered/PocketBoot"
    const val RELEASES_PAGE_URL = "https://github.com/bilered/PocketBoot/releases"
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/bilered/PocketBoot/releases/latest"

    /**
     * Compares two semantic version strings (e.g. "v4.7.1" vs "4.7.0", "4.10.0" vs "4.7.0").
     * Returns > 0 if [remoteVersion] is newer than [currentVersion], 0 if equal, < 0 if older.
     * Pre-release tags (e.g. "4.7.0-rc1") sort below the corresponding stable release ("4.7.0").
     */
    internal fun isTrustedReleaseUrl(rawUrl: String): Boolean {
        if (!ShellSafety.isValidHttpUrl(rawUrl, requireHttps = true)) return false
        val host = runCatching { URL(rawUrl.trim()).host.orEmpty() }.getOrDefault("")
        return ShellSafety.isHostOrSubdomain(host, "github.com") ||
            ShellSafety.isHostOrSubdomain(host, "githubusercontent.com")
    }

    fun compareSemVer(remoteVersion: String, currentVersion: String): Int {
        fun parse(raw: String): Pair<List<Int>, String> {
            val cleaned = raw.trim().removePrefix("v").removePrefix("V")
            val mainPart = cleaned.substringBefore('-').substringBefore('+')
            val prePart = if (cleaned.contains('-')) cleaned.substringAfter('-').substringBefore('+').trim().lowercase() else ""
            val nums = mainPart.split('.').map { seg ->
                seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }.ifEmpty { listOf(0) }
            return nums to prePart
        }

        val (rNums, rPre) = parse(remoteVersion)
        val (cNums, cPre) = parse(currentVersion)
        val maxLen = maxOf(rNums.size, cNums.size)
        for (i in 0 until maxLen) {
            val rv = rNums.getOrElse(i) { 0 }
            val cv = cNums.getOrElse(i) { 0 }
            if (rv != cv) return rv.compareTo(cv)
        }
        return when {
            rPre.isEmpty() && cPre.isNotEmpty() -> 1
            rPre.isNotEmpty() && cPre.isEmpty() -> -1
            else -> rPre.compareTo(cPre)
        }
    }

    suspend fun checkForUpdate(currentVersion: String): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(LATEST_RELEASE_API)
            require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS is required for release checks." }
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PocketBoot/$currentVersion")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val (code, body) = try {
                val c = conn.responseCode
                require(conn.url.protocol.equals("https", ignoreCase = true)) { "Rejected redirect to non-HTTPS URL." }
                val stream = if (c in 200..299) conn.inputStream else conn.errorStream
                c to stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            } finally {
                conn.disconnect()
            }

            if (code == 404) {
                return@withContext AppUpdateCheckResult(
                    success = true,
                    updateAvailable = false,
                    currentVersion = currentVersion,
                    message = "No published GitHub releases yet on bilered/PocketBoot."
                )
            }
            if (code == 403) {
                return@withContext AppUpdateCheckResult(
                    success = false,
                    currentVersion = currentVersion,
                    message = "GitHub API rate limit reached. Try again later or open Releases."
                )
            }
            if (code !in 200..299 || body.isBlank()) {
                return@withContext AppUpdateCheckResult(
                    success = false,
                    currentVersion = currentVersion,
                    message = "Update check failed (HTTP $code)."
                )
            }

            val obj = JSONObject(body)
            val tag = obj.optString("tag_name", "").trim()
            val version = tag.removePrefix("v").removePrefix("V").trim()
            if (version.isBlank()) {
                return@withContext AppUpdateCheckResult(
                    success = false,
                    currentVersion = currentVersion,
                    message = "Latest release tag could not be parsed."
                )
            }
            val title = obj.optString("name", "").trim().ifBlank { "PocketBoot $tag" }
            val notes = obj.optString("body", "").trim()
            val htmlUrl = obj.optString("html_url", "").trim()
                .takeIf { isTrustedReleaseUrl(it) }
                ?: RELEASES_PAGE_URL
            val published = obj.optString("published_at", "").trim().substringBefore('T')

            var apkUrl: String? = null
            val assets = obj.optJSONArray("assets") ?: JSONArray()
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val dl = asset.optString("browser_download_url", "").trim()
                if (name.endsWith(".apk", ignoreCase = true) && isTrustedReleaseUrl(dl)) {
                    apkUrl = dl
                    break
                }
            }

            val release = AppReleaseInfo(
                tagName = tag,
                versionName = version,
                releaseTitle = title,
                releaseNotes = notes,
                htmlUrl = htmlUrl,
                apkDownloadUrl = apkUrl,
                publishedAt = published
            )
            val newer = compareSemVer(version, currentVersion) > 0
            AppUpdateCheckResult(
                success = true,
                updateAvailable = newer,
                currentVersion = currentVersion,
                release = release,
                message = if (newer) "PocketBoot v$version is available!" else "PocketBoot v$currentVersion is up to date."
            )
        }.getOrElse { e ->
            AppUpdateCheckResult(
                success = false,
                currentVersion = currentVersion,
                message = "Could not check updates: ${e.message ?: "Network error"}"
            )
        }
    }
}
