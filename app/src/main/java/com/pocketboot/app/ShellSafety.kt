package com.pocketboot.app

import java.net.URL
import java.util.Locale

/**
 * Centralized POSIX shell quoting, path-containment, and URL-security helpers.
 * Pure-JVM testable with zero Android runtime dependencies so every security boundary
 * is directly verified by automated unit tests in CI.
 */
object ShellSafety {

    private val STRICT_HTTPS_DOMAINS = listOf(
        "microsoft.com",
        "microsoft.net",
        "msecnd.net",
        "azureedge.net",
        "windowsupdate.com",
        "github.com",
        "githubusercontent.com",
        "ntriver.org",
        "jsdelivr.net",
        "jsdelivr.com"
    )

    /**
     * Safely single-quotes any dynamic value for POSIX `/system/bin/sh` (`toybox` / `mksh` / `ash`).
     * Embedded single quotes are escaped as `'\''`.
     */
    fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    /**
     * Returns true if [rawPath] is an absolute path containing no control chars (NUL/CR/LF)
     * and no `..` path-traversal segments.
     */
    fun isCleanAbsolutePath(rawPath: String): Boolean {
        val trimmed = rawPath.trim()
        if (!trimmed.startsWith("/")) return false
        if (trimmed.any { it.code < 0x20 || it.code == 0x7F }) return false
        val segments = trimmed.replace('\\', '/').split('/')
        return segments.none { it == ".." }
    }

    /**
     * Verifies [candidatePath] is strictly a child inside [parentDir] after rejecting `..`
     * traversal segments, NUL/control characters, and parent self-references.
     */
    fun isStrictChildPath(parentDir: String, candidatePath: String): Boolean {
        if (!isCleanAbsolutePath(parentDir) || !isCleanAbsolutePath(candidatePath)) return false
        val normParent = parentDir.trim().replace('\\', '/').replace(Regex("/+"), "/").trimEnd('/')
        val normChild = candidatePath.trim().replace('\\', '/').replace(Regex("/+"), "/").trimEnd('/')
        if (normParent.isEmpty() || normChild.isEmpty()) return false
        return normChild.startsWith("$normParent/") && normChild.length > normParent.length + 1
    }

    /**
     * Strict exact-domain or dot-boundary subdomain matcher (`evilgithub.com` NEVER matches `github.com`).
     */
    fun isHostOrSubdomain(host: String, domain: String): Boolean {
        val h = host.trim().lowercase(Locale.US)
        val d = domain.trim().lowercase(Locale.US)
        if (h.isEmpty() || d.isEmpty()) return false
        return h == d || h.endsWith(".$d")
    }

    /**
     * Returns true if [host] belongs to a trusted domain that must never be downgraded to cleartext HTTP.
     */
    fun isStrictHttpsHost(host: String): Boolean =
        STRICT_HTTPS_DOMAINS.any { isHostOrSubdomain(host, it) }

    /**
     * Validates a user-configured HTTP/HTTPS URL (rejects `file://`, `javascript:`, `content://`, whitespace, etc.).
     */
    fun isValidHttpUrl(rawUrl: String, requireHttps: Boolean = false): Boolean {
        val clean = rawUrl.trim()
        if (clean.isEmpty() || clean.any { it.isWhitespace() || it.code < 0x20 }) return false
        val parsed = runCatching { URL(clean) }.getOrNull() ?: return false
        val proto = parsed.protocol.orEmpty().lowercase(Locale.US)
        val protoOk = if (requireHttps) proto == "https" else (proto == "https" || proto == "http")
        return protoOk && parsed.host.orEmpty().isNotBlank()
    }

    /**
     * Returns true if redirecting from [fromUrl] to [toUrl] constitutes a forbidden HTTPS -> HTTP
     * downgrade from or to a trusted HTTPS-only domain.
     */
    fun isForbiddenHttpsDowngrade(fromUrl: String, toUrl: String): Boolean {
        val from = runCatching { URL(fromUrl) }.getOrNull() ?: return true
        val to = runCatching { URL(toUrl) }.getOrNull() ?: return true
        val fromProto = from.protocol.orEmpty().lowercase(Locale.US)
        val toProto = to.protocol.orEmpty().lowercase(Locale.US)
        if (toProto != "https" && toProto != "http") return true
        if (toProto == "http") {
            if (isStrictHttpsHost(to.host.orEmpty())) return true
            if (fromProto == "https" && isStrictHttpsHost(from.host.orEmpty())) return true
        }
        return false
    }
}
