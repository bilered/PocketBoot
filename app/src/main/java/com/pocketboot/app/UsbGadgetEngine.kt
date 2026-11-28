package com.pocketboot.app

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Date
import java.util.Locale

enum class MountMode(
    val shortLabel: String,
    val friendlyTitle: String,
    val helperText: String,
    val ro: Int,
    val cdrom: Int
) {
    AUTO(
        shortLabel = "Auto",
        friendlyTitle = "Auto Mode",
        helperText = "Uses Virtual CD for .iso and Writable USB for .img.",
        ro = 1,
        cdrom = 0
    ),
    CDROM(
        shortLabel = "Virtual CD",
        friendlyTitle = "Virtual CD (CD-ROM)",
        helperText = "Presents the file as an optical CD/DVD drive. Prevents Windows format prompts on ISO files.",
        ro = 1,
        cdrom = 1
    ),
    USB_RO(
        shortLabel = "Read-Only USB",
        friendlyTitle = "Read-Only USB Drive",
        helperText = "Presents the file as a read-only USB stick. Great if your PC BIOS doesn't list Virtual CD.",
        ro = 1,
        cdrom = 0
    ),
    USB_RW(
        shortLabel = "Writable USB",
        friendlyTitle = "Writable USB Drive",
        helperText = "Lets your PC format and save files onto the drive. Best for blank .img drives.",
        ro = 0,
        cdrom = 0
    );

    fun resolveForFile(path: String, rememberedModes: Map<String, String>): MountMode {
        if (this != AUTO) return this
        val savedName = rememberedModes[path]
        if (savedName != null) {
            entries.firstOrNull { it.name == savedName && it != AUTO }?.let { return it }
        }
        return if (path.endsWith(".iso", ignoreCase = true)) CDROM else USB_RW
    }
}

data class ImageFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val folderLabel: String,
    val isPinned: Boolean = false,
    val actualDiskBytes: Long = sizeBytes
) {
    val isSparseSaved: Boolean
        get() = name.endsWith(".img", ignoreCase = true) &&
            sizeBytes > 64L * 1024L * 1024L &&
            actualDiskBytes in 1L until ((sizeBytes * 85L) / 100L)
}

data class TempStorageStats(
    val workTempBytes: Long = 0L,
    val resumableBytes: Long = 0L,
    val rootTmpBytes: Long = 0L
) {
    val totalBytes: Long get() = workTempBytes + resumableBytes + rootTmpBytes
}

data class BrowserEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedEpochSec: Long = 0L
)

data class WatchdogTickResult(
    val hasMountedLun: Boolean = false,
    val recoveredUdc: Boolean = false,
    val bouncedHotPlug: Boolean = false,
    val vbusOnline: Boolean = false,
    val udcName: String = "",
    val linkState: String = "",
    val primaryName: String = ""
)

data class UsbActivitySample(
    val linkState: String = "",
    val speed: String = "",
    val ioBytesTotal: Long = 0L,
    val kernelReadingActive: Boolean = false,
    val threadCpuTicks: Long = 0L,
    val usbIrqCount: Long = 0L
)

data class GadgetStatus(
    val hasRoot: Boolean = false,
    val rootManager: String = "None",
    val gadgetPath: String = "/config/usb_gadget/g1",
    val udcName: String = "",
    val usbLinkSpeed: String = "Unknown",
    val usbLinkState: String = "Unknown",
    val activeUsbConfig: String = "",
    val configFsReady: Boolean = false,
    val isUdcBound: Boolean = false,
    val multiLunSupported: Boolean = false,
    val mountedFile: String = "",
    val mountedSizeBytes: Long = 0L,
    val isReadOnly: Boolean = true,
    val isCdrom: Boolean = false,
    val secondaryMountedFile: String = "",
    val secondaryMountedSizeBytes: Long = 0L,
    val secondaryIsReadOnly: Boolean = true,
    val secondaryIsCdrom: Boolean = false,
    val adbBound: Boolean = false,
    val selinuxMode: String = "Unknown",
    val kernelVersion: String = ""
) {
    val isMounted: Boolean get() = mountedFile.isNotEmpty()
    val isSecondaryMounted: Boolean get() = secondaryMountedFile.isNotEmpty()
    val isAnyMounted: Boolean get() = isMounted || isSecondaryMounted

    fun mountedLunFor(path: String): Int? = when {
        mountedFile == path -> 0
        secondaryMountedFile == path -> 1
        else -> null
    }

    val activeModeLabel: String
        get() = when {
            !isMounted -> "Not Mounted"
            isCdrom -> "Virtual CD Drive"
            isReadOnly -> "Read-Only USB Drive"
            else -> "Writable USB Drive"
        }

    val activeSecondaryModeLabel: String
        get() = when {
            !isSecondaryMounted -> "Empty"
            secondaryIsCdrom -> "Virtual CD Drive"
            secondaryIsReadOnly -> "Read-Only USB Drive"
            else -> "Writable USB Drive"
        }

    val activeProtocolLabel: String
        get() {
            val base = when {
                !isAnyMounted -> "Standard Phone USB (${activeUsbConfig.ifBlank { "MTP / Charging" }})"
                isMounted && isSecondaryMounted -> "Dual-LUN MSC (LUN 0 + LUN 1)"
                isMounted && isCdrom -> "MSC Bulk-Only (CD-ROM)"
                isMounted && isReadOnly -> "MSC Bulk-Only (Read-Only Disk)"
                isMounted -> "MSC Bulk-Only (Writable Disk)"
                secondaryIsCdrom -> "MSC Bulk-Only LUN 1 (CD-ROM)"
                secondaryIsReadOnly -> "MSC Bulk-Only LUN 1 (Read-Only Disk)"
                else -> "MSC Bulk-Only LUN 1 (Writable Disk)"
            }
            return if (isAnyMounted && adbBound) "$base + ADB" else base
        }

    val friendlySpeedLabel: String
        get() = when (usbLinkSpeed.lowercase()) {
            "super-speed", "super-speed-plus" -> "USB 3.0 SuperSpeed (5+ Gbps)"
            "high-speed" -> "USB 2.0 High-Speed (480 Mbps)"
            "full-speed" -> "USB 1.1 Full-Speed (12 Mbps)"
            else -> if (usbLinkState.equals("configured", ignoreCase = true)) {
                "Connected ($usbLinkSpeed)"
            } else {
                "Cable Disconnected"
            }
        }
}

data class LogEntry(
    val timestamp: String,
    val tag: String,
    val message: String,
    val isError: Boolean = false
)

data class ActionResult(
    val success: Boolean,
    val userMessage: String,
    val createdPath: String? = null,
    val entries: List<LogEntry> = emptyList()
)

object UsbGadgetEngine {
    private val ejectRequested = AtomicBoolean(false)
    fun requestAbortWatchdog() { ejectRequested.set(true) }
    fun clearAbortWatchdog() { ejectRequested.set(false) }

    private fun runIndependentRootScript(script: String): String {
        return runCatching {
            val proc = ProcessBuilder("su", "-c", "sh").redirectErrorStream(true).start()
            proc.outputStream.bufferedWriter(Charsets.UTF_8).use { w -> w.write(script); w.write("\nexit\n"); w.flush() }
            val lines = proc.inputStream.bufferedReader(Charsets.UTF_8).readLines()
            proc.waitFor()
            lines.lastOrNull { it.startsWith("OK|") || it == "NONE" } ?: lines.lastOrNull().orEmpty()
        }.getOrDefault("")
    }


    private const val STATE_FILE = "/data/local/tmp/.pocketboot_prev_usb"

    // Universal ConfigFS gadget & config auto-detection snippet for Samsung, Pixel, OnePlus, Xiaomi, MediaTek, etc.
    private const val DETECT_GADGET_SH = """
        GADGET=""
        if [ -d "/config/usb_gadget/g1/configs" ]; then
            GADGET="/config/usb_gadget/g1"
        else
            for g in /config/usb_gadget/*; do
                if [ -d "${'$'}g/configs" ] && [ -e "${'$'}g/UDC" ]; then
                    GADGET="${'$'}g"
                    break
                fi
            done
        fi
        CFG=""
        if [ -n "${'$'}GADGET" ]; then
            if [ -d "${'$'}GADGET/configs/b.1" ]; then
                CFG="${'$'}GADGET/configs/b.1"
            elif [ -d "${'$'}GADGET/configs/c.1" ]; then
                CFG="${'$'}GADGET/configs/c.1"
            else
                for c in "${'$'}GADGET"/configs/*; do
                    if [ -d "${'$'}c" ]; then
                        CFG="${'$'}c"
                        break
                    fi
                done
            fi
        fi
    """

    init {
        runCatching {
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(Shell.FLAG_MOUNT_MASTER)
                    .setTimeout(25)
            )
        }
    }

    private fun nowTs(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    fun resolveDirectPath(rawPath: String): String {
        return rawPath.trim()
            .replaceFirst(Regex("^/sdcard"), "/data/media/0")
            .replaceFirst(Regex("^/storage/emulated/0"), "/data/media/0")
    }

    fun friendlyFolderName(dirPath: String): String {
        return when (dirPath.trimEnd('/')) {
            "/data/media/0/Download" -> "Downloads"
            "/data/media/0/PocketBoot" -> "PocketBoot"
            "/data/media/0/DriveDroid" -> "DriveDroid"
            "/data/media/0/ISOs" -> "ISOs Folder"
            "/data/local/tmp" -> "Temporary Folder"
            "/data/media/0" -> "Internal Storage"
            else -> dirPath.replaceFirst("/data/media/0/", "").ifBlank { dirPath }
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun getFreeSpaceBytes(dirPath: String): Long {
        val publicPath = dirPath.replaceFirst("/data/media/0", "/storage/emulated/0")
        return runCatching {
            var probe: File? = File(publicPath)
            while (probe != null && !probe.exists()) {
                probe = probe.parentFile
            }
            val target = probe?.absolutePath ?: "/storage/emulated/0"
            StatFs(target).availableBytes
        }.getOrElse { 0L }
    }

    /**
     * Android-universal directory size helper (works on Toybox, BusyBox, and GNU without relying on `awk` or GNU `du -b`).
     * Tries `du -sb`, falls back to POSIX `du -sk` (* 1024), and returns 0L if missing/empty.
     */
    fun queryDirSizeBytes(dirPath: String): Long {
        val resolved = resolveDirectPath(dirPath).trimEnd('/')
        if (resolved.isBlank()) return 0L
        val qPath = shellQuote(resolved)
        val bLine = Shell.cmd("if [ -e $qPath ]; then du -sb $qPath 2>/dev/null; fi")
            .exec().out.firstOrNull().orEmpty().trim()
        val exactB = bLine.split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
        if (exactB != null && exactB >= 0L) return exactB
        val kLine = Shell.cmd("if [ -e $qPath ]; then du -sk $qPath 2>/dev/null; fi")
            .exec().out.firstOrNull().orEmpty().trim()
        val kb = kLine.split(Regex("\\s+")).firstOrNull()?.toLongOrNull()
        return if (kb != null && kb >= 0L) kb * 1024L else 0L
    }

    /**
     * Returns actual allocated on-disk bytes for an existing file (512-byte blocks via `stat -c '%b'`),
     * useful for overwrite-aware free-space checks on sparse or full images.
     */
    fun queryExistingFileAllocatedBytes(filePath: String): Long {
        val resolved = resolveDirectPath(filePath).trimEnd('/')
        if (resolved.isBlank()) return 0L
        val qPath = shellQuote(resolved)
        val blocks = Shell.cmd("stat -c '%b' $qPath 2>/dev/null")
            .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
        return if (blocks > 0L) blocks * 512L else 0L
    }

    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    fun fixUserStoragePermissions(context: Context?, path: String, isDir: Boolean = false) {
        runCatching {
            val resolved = resolveDirectPath(path).trimEnd('/')
            if (resolved.isBlank()) return
            val qPath = shellQuote(resolved)
            val isUserStorage = resolved.startsWith("/data/media/0") || resolved.startsWith("/storage/emulated/0")
            val modeFlag = if (isDir) "0775" else "0664"
            val script = if (isUserStorage) {
                """
                OWNER=${'$'}(stat -c '%u:%g' /data/media/0 2>/dev/null)
                [ -z "${'$'}OWNER" ] && OWNER="1023:1023"
                chown "${'$'}OWNER" $qPath 2>/dev/null || chown media_rw:media_rw $qPath 2>/dev/null || true
                chmod $modeFlag $qPath 2>/dev/null || true
                PARENT=${'$'}(dirname $qPath)
                if [ "${'$'}PARENT" != "/data/media/0" ] && [ -d "${'$'}PARENT" ]; then
                    chown "${'$'}OWNER" "${'$'}PARENT" 2>/dev/null || true
                    chmod 0775 "${'$'}PARENT" 2>/dev/null || true
                fi
                if command -v restorecon >/dev/null 2>&1; then
                    restorecon $qPath 2>/dev/null || true
                    [ "${'$'}PARENT" != "/data/media/0" ] && restorecon "${'$'}PARENT" 2>/dev/null || true
                fi
                """.trimIndent()
            } else {
                "chmod $modeFlag $qPath 2>/dev/null || true"
            }
            Shell.cmd(script).exec()

            if (context != null && isUserStorage) {
                val publicPath = resolved.replaceFirst("/data/media/0", "/storage/emulated/0")
                if (!isDir) {
                    // Non-destructive synchronous MediaProvider rescan: updates cached _size & date_modified for Android MTP
                    // when overwriting an existing ISO filename (NEVER call MediaStore delete on an existing file, as Android 11+ unlinks it!).
                    val qPub = shellQuote(publicPath)
                    Shell.cmd("touch $qPath 2>/dev/null || true; sync; stat $qPub >/dev/null 2>&1 || true; touch $qPub 2>/dev/null || true; content call --user 0 --uri content://media --method scan_file --arg $qPub >/dev/null 2>&1 || true").exec()
                }
                MediaScannerConnection.scanFile(context.applicationContext, arrayOf(publicPath), null, null)
            }
        }
    }

    /**
     * Verifies Android MediaStore's cached `_size` for an output ISO/IMG matches `expectedBytes`
     * so USB MTP transfers never truncate overwritten files to an older build's size.
     * Returns Pair(isSynced, mediaStoreReportedSize).
     */
    fun syncMediaStoreFileSize(context: Context?, path: String, expectedBytes: Long): Pair<Boolean, Long> {
        return runCatching {
            val resolved = resolveDirectPath(path).trimEnd('/')
            if (resolved.isBlank() || expectedBytes <= 0L) return Pair(false, -1L)
            val isUserStorage = resolved.startsWith("/data/media/0") || resolved.startsWith("/storage/emulated/0")
            if (!isUserStorage) return Pair(true, expectedBytes)
            val publicPath = resolved.replaceFirst("/data/media/0", "/storage/emulated/0")
            val qPub = shellQuote(publicPath)
            val escSql = publicPath.replace("'", "''")
            val qWhere = shellQuote("_data='$escSql'")

            fun triggerBlockingScan() {
                // Force FUSE dentry/stat refresh on /storage/emulated/0 before MediaProvider scans the file
                Shell.cmd("sync; stat $qPub >/dev/null 2>&1 || true; touch $qPub 2>/dev/null || true; content call --user 0 --uri content://media --method scan_file --arg $qPub >/dev/null 2>&1 || true").exec()
                if (context != null) {
                    val latch = CountDownLatch(1)
                    runCatching {
                        MediaScannerConnection.scanFile(
                            context.applicationContext,
                            arrayOf(publicPath),
                            arrayOf("application/octet-stream")
                        ) { _, _ -> latch.countDown() }
                        latch.await(6L, TimeUnit.SECONDS)
                    }
                }
            }

            fun querySize(): Long {
                val out = Shell.cmd("content query --user 0 --uri content://media/external/file --projection _size --where $qWhere 2>/dev/null || true").exec().out
                val m = Regex("""_size=(\d+)""").find(out.joinToString(" "))
                return m?.groupValues?.getOrNull(1)?.toLongOrNull() ?: -1L
            }

            triggerBlockingScan()
            var reported = querySize()
            if (reported != expectedBytes) {
                val mtimeSec = Shell.cmd("stat -c '%Y' ${shellQuote(resolved)} 2>/dev/null || date +%s").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: (System.currentTimeMillis() / 1000L)
                Shell.cmd("sync; content update --user 0 --uri content://media/external/file --bind _size:l:$expectedBytes --bind date_modified:l:$mtimeSec --where $qWhere >/dev/null 2>&1 || true").exec()
                triggerBlockingScan()
                reported = querySize()
            }
            Pair(reported == expectedBytes, reported)
        }.getOrDefault(Pair(false, -1L))
    }

    suspend fun ensureDirectoryExists(dirPath: String, context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!Shell.getShell().isRoot) return@withContext false
            val resolved = resolveDirectPath(dirPath)
            val qDir = shellQuote(resolved)
            val ok = Shell.cmd("mkdir -p $qDir").exec().isSuccess
            if (ok) {
                fixUserStoragePermissions(context, resolved, isDir = true)
            }
            ok
        }.getOrDefault(false)
    }

    suspend fun listDirectory(dirPath: String): List<BrowserEntry> = withContext(Dispatchers.IO) {
        runCatching {
            if (!Shell.getShell().isRoot) return@withContext emptyList()
            val resolved = resolveDirectPath(dirPath).trimEnd('/').ifBlank { "/data/media/0" }
            val qDir = shellQuote(resolved)
            // Single-process batch stat: ~30x faster than spawning basename + stat per file!
            val cmd = """
                if [ -d $qDir ]; then
                    stat -L -c '%F|%s|%Y|%n' $qDir/* 2>/dev/null || stat -c '%F|%s|%Y|%n' $qDir/* 2>/dev/null || true
                fi
            """.trimIndent()
            val out = Shell.cmd(cmd).exec().out
            out.mapNotNull { line ->
                val parts = line.split("|", limit = 4)
                if (parts.size < 4) return@mapNotNull null
                val typeStr = parts[0].lowercase()
                val isDir = typeStr.contains("directory")
                val isReg = typeStr.contains("regular")
                if (!isDir && !isReg) return@mapNotNull null
                val size = if (isDir) 0L else (parts[1].toLongOrNull() ?: 0L)
                val mtime = parts[2].toLongOrNull() ?: 0L
                val fullPath = parts[3]
                val name = fullPath.substringAfterLast('/')
                if (name.isEmpty() || name.startsWith(".") || name == "*") return@mapNotNull null
                BrowserEntry(
                    path = fullPath,
                    name = name,
                    isDirectory = isDir,
                    sizeBytes = size,
                    modifiedEpochSec = mtime
                )
            }.sortedWith(compareBy<BrowserEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
        }.getOrDefault(emptyList())
    }

    suspend fun resolveUriToPath(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (uri.scheme.equals("file", ignoreCase = true)) {
                uri.path?.let { return@withContext resolveDirectPath(it) }
            }

            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
                if (docId != null) {
                    if (docId.startsWith("raw:")) {
                        return@withContext resolveDirectPath(docId.removePrefix("raw:"))
                    }
                    val parts = docId.split(":", limit = 2)
                    if (parts.size == 2 && parts[0].equals("primary", ignoreCase = true)) {
                        return@withContext "/data/media/0/${parts[1]}"
                    }
                }
            }

            var displayName: String? = null
            var fileSize: Long = -1L
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (dataIdx >= 0) {
                            val rawData = cursor.getString(dataIdx)
                            if (!rawData.isNullOrBlank() && rawData.startsWith("/")) {
                                return@withContext resolveDirectPath(rawData)
                            }
                        }
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            val uriPath = uri.path.orEmpty()
            val storageMarker = uriPath.indexOf("/storage/emulated/0/")
            if (storageMarker >= 0) {
                val candidate = resolveDirectPath(uriPath.substring(storageMarker))
                if (Shell.cmd("[ -f ${shellQuote(candidate)} ]").exec().isSuccess) {
                    return@withContext candidate
                }
            }
            val sdcardMarker = uriPath.indexOf("/sdcard/")
            if (sdcardMarker >= 0) {
                val candidate = resolveDirectPath(uriPath.substring(sdcardMarker))
                if (Shell.cmd("[ -f ${shellQuote(candidate)} ]").exec().isSuccess) {
                    return@withContext candidate
                }
            }

            val name = displayName?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            if (!name.isNullOrBlank() && Shell.getShell().isRoot) {
                val qName = shellQuote(name)
                val findCmd = if (fileSize > 0L) {
                    """find /data/media/0 /data/local/tmp -maxdepth 5 -type f -name $qName 2>/dev/null | while IFS= read -r f; do sz=${'$'}(stat -c '%s' "${'$'}f" 2>/dev/null); if [ "${'$'}sz" = "$fileSize" ]; then echo "${'$'}f"; break; fi; done"""
                } else {
                    """find /data/media/0 /data/local/tmp -maxdepth 5 -type f -name $qName 2>/dev/null | head -n 1"""
                }
                val found = Shell.cmd(findCmd).exec().out.firstOrNull()?.trim()
                if (!found.isNullOrBlank()) {
                    return@withContext found
                }
            }
            null
        }.getOrNull()
    }

    suspend fun getStatus(): GadgetStatus = withContext(Dispatchers.IO) {
        runCatching {
            val isRoot = Shell.getShell().isRoot
            if (!isRoot) return@withContext GadgetStatus(hasRoot = false)

            val gadgetPath = Shell.cmd(
                "$DETECT_GADGET_SH\necho \"${'$'}GADGET\""
            ).exec().out.firstOrNull().orEmpty().trim().ifBlank { "/config/usb_gadget/g1" }

            val cfgPath = Shell.cmd(
                "$DETECT_GADGET_SH\necho \"${'$'}CFG\""
            ).exec().out.firstOrNull().orEmpty().trim().ifBlank { "$gadgetPath/configs/b.1" }

            val physicalUdcs = Shell.cmd("ls /sys/class/udc 2>/dev/null")
                .exec().out.map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("dummy_udc") }

            val qGadget = shellQuote(gadgetPath)
            val qCfg = shellQuote(cfgPath)
            val activeUdc = Shell.cmd("cat ${shellQuote("$gadgetPath/UDC")} 2>/dev/null")
                .exec().out.firstOrNull().orEmpty().trim()

            val udcName = when {
                activeUdc.isNotEmpty() && !activeUdc.startsWith("dummy_udc") -> activeUdc
                physicalUdcs.isNotEmpty() -> physicalUdcs.first()
                else -> activeUdc
            }

            val speed = if (udcName.isNotEmpty()) {
                Shell.cmd("cat ${shellQuote("/sys/class/udc/$udcName/current_speed")} 2>/dev/null")
                    .exec().out.firstOrNull().orEmpty().trim()
            } else ""

            val state = if (udcName.isNotEmpty()) {
                Shell.cmd("cat ${shellQuote("/sys/class/udc/$udcName/state")} 2>/dev/null")
                    .exec().out.firstOrNull().orEmpty().trim()
            } else ""

            val usbCfg = Shell.cmd("getprop sys.usb.config 2>/dev/null")
                .exec().out.firstOrNull().orEmpty().trim()

            val configFsReady = Shell.cmd("[ -d $qGadget ] && echo yes || echo no")
                .exec().out.firstOrNull().orEmpty().trim() == "yes"

            val rootMgr = Shell.cmd(
                "if command -v ksud >/dev/null 2>&1 || [ -d /data/adb/ksu ]; then echo KernelSU; " +
                    "elif command -v apd >/dev/null 2>&1 || [ -d /data/adb/ap ]; then echo APatch; " +
                    "elif command -v magisk >/dev/null 2>&1 || [ -d /data/adb/magisk ]; then echo Magisk; " +
                    "else echo Root; fi"
            ).exec().out.firstOrNull().orEmpty().trim().ifBlank { "Root" }

            val activeFuncPath = Shell.cmd(
                "for f in $qGadget/functions/mass_storage.*; do " +
                    "if [ -d \"${'$'}f/lun.0\" ]; then echo \"${'$'}f\"; break; fi; done"
            ).exec().out.firstOrNull().orEmpty().trim()
                .ifBlank { "$gadgetPath/functions/mass_storage.0" }

            val lun0Path = "$activeFuncPath/lun.0"
            val lun1Path = "$activeFuncPath/lun.1"
            val qFunc = shellQuote(activeFuncPath)
            val qLun1 = shellQuote(lun1Path)

            val mountedFile = Shell.cmd("cat ${shellQuote("$lun0Path/file")} 2>/dev/null")
                .exec().out.firstOrNull().orEmpty().trim()

            val mountedSize = if (mountedFile.isNotEmpty()) {
                val qFile = shellQuote(mountedFile)
                Shell.cmd("stat -c '%s' $qFile 2>/dev/null")
                    .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            } else 0L

            val ro = Shell.cmd("cat ${shellQuote("$lun0Path/ro")} 2>/dev/null")
                .exec().out.firstOrNull().orEmpty().trim() != "0"

            val cdrom = Shell.cmd("cat ${shellQuote("$lun0Path/cdrom")} 2>/dev/null")
                .exec().out.firstOrNull().orEmpty().trim() == "1"

            val secondaryMountedFile = Shell.cmd("cat ${shellQuote("$lun1Path/file")} 2>/dev/null")
                .exec().out.firstOrNull().orEmpty().trim()

            val secondaryMountedSize = if (secondaryMountedFile.isNotEmpty()) {
                val qFile2 = shellQuote(secondaryMountedFile)
                Shell.cmd("stat -c '%s' $qFile2 2>/dev/null")
                    .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            } else 0L

            val secRo = Shell.cmd("cat ${shellQuote("$lun1Path/ro")} 2>/dev/null")
                .exec().out.firstOrNull().orEmpty().trim() != "0"

            val secCdrom = Shell.cmd("cat ${shellQuote("$lun1Path/cdrom")} 2>/dev/null")
                .exec().out.firstOrNull().orEmpty().trim() == "1"

            val multiLunSupported = Shell.cmd(
                "if [ -d $qLun1 ]; then echo yes; " +
                    "elif [ -d $qFunc ] && mkdir $qLun1 2>/dev/null; then echo yes; " +
                    "elif [ ! -d $qFunc ] && [ -d ${shellQuote("$gadgetPath/functions")} ]; then echo maybe; " +
                    "else echo no; fi"
            ).exec().out.firstOrNull().orEmpty().trim().let { it == "yes" || it == "maybe" }

            val adbBound = Shell.cmd("ls -l $qCfg/ 2>/dev/null | grep -q 'ffs.adb' && echo yes || echo no")
                .exec().out.firstOrNull().orEmpty().trim() == "yes"

            val selinux = Shell.cmd("getenforce 2>/dev/null")
                .exec().out.firstOrNull().orEmpty().trim().ifBlank { "Unknown" }

            val kernel = Shell.cmd("uname -r 2>/dev/null")
                .exec().out.firstOrNull().orEmpty().trim()

            GadgetStatus(
                hasRoot = true,
                rootManager = rootMgr,
                gadgetPath = gadgetPath,
                udcName = udcName,
                usbLinkSpeed = speed,
                usbLinkState = state,
                activeUsbConfig = usbCfg,
                configFsReady = configFsReady,
                isUdcBound = activeUdc.isNotEmpty(),
                multiLunSupported = multiLunSupported,
                mountedFile = mountedFile,
                mountedSizeBytes = mountedSize,
                isReadOnly = ro,
                isCdrom = cdrom,
                secondaryMountedFile = secondaryMountedFile,
                secondaryMountedSizeBytes = secondaryMountedSize,
                secondaryIsReadOnly = secRo,
                secondaryIsCdrom = secCdrom,
                adbBound = adbBound,
                selinuxMode = selinux,
                kernelVersion = kernel
            )
        }.getOrElse { GadgetStatus(hasRoot = false) }
    }

    suspend fun scanImages(
        directories: List<String>,
        pinnedFiles: List<String> = emptyList(),
        hiddenFiles: List<String> = emptyList()
    ): List<ImageFile> = withContext(Dispatchers.IO) {
        runCatching {
            if (!Shell.getShell().isRoot) return@withContext emptyList()
            val hiddenSet = hiddenFiles.map { resolveDirectPath(it) }.toSet()
            val pinnedSet = pinnedFiles.map { resolveDirectPath(it) }.toSet()

            val found = LinkedHashMap<String, ImageFile>()

            val resolvedDirs = directories.map { resolveDirectPath(it) }.distinct()
            val quotedDirs = resolvedDirs.joinToString(" ") { shellQuote(it) }
            if (quotedDirs.isNotBlank()) {
                val cmd = """
                    find $quotedDirs -maxdepth 3 -not -path '*/.*' -type f \( -iname '*.iso' -o -iname '*.img' -o -iname '*.img.xz' -o -iname '*.img.gz' -o -iname '*.img.zst' -o -iname '*.iso.xz' -o -iname '*.iso.gz' -o -iname '*.iso.zst' \) 2>/dev/null | while IFS= read -r f; do
                        info=${'$'}(stat -c '%s|%b' "${'$'}f" 2>/dev/null || echo '0|0')
                        printf '%s|%s\n' "${'$'}info" "${'$'}f"
                    done | sort -t'|' -k3
                """.trimIndent()
                Shell.cmd(cmd).exec().out.forEach { line ->
                    val parts = line.split("|", limit = 3)
                    if (parts.size == 3) {
                        val size = parts[0].toLongOrNull() ?: 0L
                        val blocks = parts[1].toLongOrNull() ?: 0L
                        val actual = if (blocks > 0L) (blocks * 512L).coerceAtMost(size) else size
                        val path = parts[2].trim()
                        if (path.isNotBlank() && !hiddenSet.contains(path)) {
                            val parentDir = path.substringBeforeLast('/', "")
                            found[path] = ImageFile(
                                path = path,
                                name = path.substringAfterLast('/'),
                                sizeBytes = size,
                                folderLabel = friendlyFolderName(parentDir),
                                isPinned = pinnedSet.contains(path),
                                actualDiskBytes = actual
                            )
                        }
                    }
                }
            }

            pinnedSet.forEach { path ->
                if (!found.containsKey(path) && !hiddenSet.contains(path)) {
                    val qPath = shellQuote(path)
                    val out = Shell.cmd("[ -f $qPath ] && stat -c '%s|%b' $qPath 2>/dev/null").exec().out.firstOrNull()?.trim().orEmpty()
                    val parts = out.split("|")
                    val size = parts.getOrNull(0)?.toLongOrNull()
                    val blocks = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    if (size != null && size >= 0L) {
                        val actual = if (blocks > 0L) (blocks * 512L).coerceAtMost(size) else size
                        val parentDir = path.substringBeforeLast('/', "")
                        found[path] = ImageFile(
                            path = path,
                            name = path.substringAfterLast('/'),
                            sizeBytes = size,
                            folderLabel = friendlyFolderName(parentDir),
                            isPinned = true,
                            actualDiskBytes = actual
                        )
                    }
                }
            }

            found.values.toList()
        }.getOrDefault(emptyList())
    }

    suspend fun countImagesInDir(dir: String): Int = withContext(Dispatchers.IO) {
        runCatching {
            if (!Shell.getShell().isRoot) return@withContext 0
            val qDir = shellQuote(resolveDirectPath(dir))
            val out = Shell.cmd(
                "find $qDir -maxdepth 3 -not -path '*/.*' -type f \\( -iname '*.iso' -o -iname '*.img' -o -iname '*.img.xz' -o -iname '*.img.gz' -o -iname '*.img.zst' -o -iname '*.iso.xz' -o -iname '*.iso.gz' -o -iname '*.iso.zst' \\) 2>/dev/null | wc -l"
            ).exec().out.firstOrNull()?.trim()?.toIntOrNull()
            out ?: 0
        }.getOrDefault(0)
    }

    suspend fun computeSha256(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!Shell.getShell().isRoot) return@withContext null
            val qPath = shellQuote(resolveDirectPath(path))
            val out = Shell.cmd("sha256sum $qPath 2>/dev/null").exec().out.firstOrNull().orEmpty().trim()
            val hash = out.split(Regex("\\s+")).firstOrNull()?.trim()
            hash?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }
        }.getOrNull()
    }

    suspend fun renameImageFile(
        oldPath: String,
        newNameInput: String,
        isCurrentlyMounted: Boolean,
        context: Context? = null
    ): ActionResult = withContext(Dispatchers.IO) {
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        if (isCurrentlyMounted) {
            return@withContext ActionResult(false, "Unmount this drive before renaming it.")
        }
        val cleanInput = newNameInput.trim()
        if (cleanInput.isEmpty() || cleanInput.contains('/') || cleanInput.contains('\\') || cleanInput.contains("..")) {
            return@withContext ActionResult(false, "Enter a valid filename without slashes or '..'.")
        }
        return@withContext runCatching {
            val resolvedOld = resolveDirectPath(oldPath)
            val parentDir = resolvedOld.substringBeforeLast('/')
            val oldName = resolvedOld.substringAfterLast('/')
            val oldExt = when {
                oldName.endsWith(".img.xz", ignoreCase = true) -> ".img.xz"
                oldName.endsWith(".img.gz", ignoreCase = true) -> ".img.gz"
                oldName.endsWith(".img.zst", ignoreCase = true) -> ".img.zst"
                oldName.endsWith(".iso.xz", ignoreCase = true) -> ".iso.xz"
                oldName.endsWith(".iso.gz", ignoreCase = true) -> ".iso.gz"
                oldName.endsWith(".iso.zst", ignoreCase = true) -> ".iso.zst"
                oldName.endsWith(".iso", ignoreCase = true) -> ".iso"
                oldName.endsWith(".img", ignoreCase = true) -> ".img"
                else -> ""
            }
            val finalName = if (oldExt.isNotEmpty()) {
                sanitizeOutputFileName(cleanInput, oldExt, oldName.dropLast(oldExt.length).ifBlank { "pocketboot" })
            } else {
                cleanInput.replace(Regex("[\\x00-\\x1F:*?\"<>|]"), "").trim().trim('.')
            }
            if (finalName.isBlank()) {
                return@withContext ActionResult(false, "Enter a valid filename.")
            }
            val newPath = "$parentDir/$finalName"
            if (newPath == resolvedOld) {
                return@withContext ActionResult(true, "Filename unchanged.", createdPath = newPath)
            }
            val qOld = shellQuote(resolvedOld)
            val qNew = shellQuote(newPath)
            val exists = Shell.cmd("[ -e $qNew ] && echo yes || echo no").exec().out.firstOrNull()?.trim() == "yes"
            if (exists) {
                return@withContext ActionResult(false, "A file named $finalName already exists in this folder.")
            }
            val res = Shell.cmd("mv $qOld $qNew 2>&1").exec()
            if (res.isSuccess) {
                IsoInspector.invalidate(resolvedOld)
                IsoInspector.invalidate(newPath)
                if (context != null) {
                    val oldPub = resolvedOld.replaceFirst("/data/media/0", "/storage/emulated/0")
                    val newPub = newPath.replaceFirst("/data/media/0", "/storage/emulated/0")
                    runCatching {
                        MediaScannerConnection.scanFile(context.applicationContext, arrayOf(oldPub, newPub), null, null)
                    }
                }
                entries.add(LogEntry(ts, "rename", "Renamed $oldName -> $finalName", false))
                ActionResult(true, "Renamed to $finalName.", createdPath = newPath, entries = entries)
            } else {
                val err = (res.out + res.err).joinToString(" ").ifBlank { "Permission denied" }
                entries.add(LogEntry(ts, "rename", "Failed to rename: $err", true))
                ActionResult(false, "Could not rename file: $err", entries = entries)
            }
        }.getOrElse { e ->
            entries.add(LogEntry(ts, "rename", "Exception: ${e.message}", true))
            ActionResult(false, "Rename failed: ${e.message}", entries = entries)
        }
    }

    fun sanitizeOutputFileName(raw: String, requiredExt: String, fallbackBase: String = "pocketboot"): String {
        val ext = if (requiredExt.startsWith(".")) requiredExt else ".$requiredExt"
        val stripped = raw
            .replace('\\', '/')
            .substringAfterLast('/')
            .replace("..", "")
            .replace(Regex("[\\x00-\\x1F:*?\"<>|]"), "")
            .trim()
            .trim('.')
        val base = stripped.ifBlank { fallbackBase }
        return if (base.endsWith(ext, ignoreCase = true)) base else "$base$ext"
    }

    suspend fun fileExistsInDir(dir: String, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val safeDir = resolveDirectPath(dir).trimEnd('/')
        if (safeDir.isBlank() || fileName.isBlank()) return@withContext false
        val full = "$safeDir/$fileName"
        runCatching { File(full).exists() }.getOrDefault(false) ||
            Shell.cmd("[ -e ${shellQuote(full)} ]").exec().isSuccess
    }

    suspend fun deleteImageFile(path: String, mountedLun: Int? = null, context: Context? = null): ActionResult = withContext(Dispatchers.IO) {
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        runCatching {
            if (mountedLun != null) {
                val unmountRes = unmountImage(mountedLun)
                entries.addAll(unmountRes.entries)
            }
            val resolved = resolveDirectPath(path)
            val qPath = shellQuote(resolved)
            val res = Shell.cmd("rm -f $qPath 2>&1").exec()
            val fileName = resolved.substringAfterLast('/')
            if (res.isSuccess) {
                IsoInspector.invalidate(resolved)
                if (context != null) {
                    val pub = resolved.replaceFirst("/data/media/0", "/storage/emulated/0")
                    runCatching {
                        MediaScannerConnection.scanFile(context.applicationContext, arrayOf(pub), null, null)
                    }
                }
                entries.add(LogEntry(ts, "delete", "Deleted $resolved", false))
                ActionResult(true, "Deleted $fileName from phone.", entries = entries)
            } else {
                val err = (res.out + res.err).joinToString(" ").ifBlank { "Permission denied" }
                entries.add(LogEntry(ts, "delete", "Failed to delete $fileName: $err", true))
                ActionResult(false, "Could not delete $fileName: $err", entries = entries)
            }
        }.getOrElse { e ->
            entries.add(LogEntry(ts, "delete", "Exception: ${e.message}", true))
            ActionResult(false, "Delete failed: ${e.message}", entries = entries)
        }
    }

    fun setUsbPowerControl(alwaysOn: Boolean) {
        val value = if (alwaysOn) "on" else "auto"
        val script = """
            for p in \
                /sys/bus/platform/devices/*dwc3*/power/control \
                /sys/bus/platform/devices/*ssusb*/power/control \
                /sys/bus/platform/devices/*mtu3*/power/control \
                /sys/bus/platform/devices/*musb*/power/control \
                /sys/class/udc/*/device/power/control; do
                [ -f "${'$'}p" ] && echo $value > "${'$'}p" 2>/dev/null || true
            done
        """.trimIndent()
        runCatching { Shell.cmd(script).exec() }
    }

    /**
     * Called by UsbKeepAliveService every 3 seconds AND immediately on USB plug-in.
     * Fixes Samsung/Qualcomm msm-dwc3 hot-plug failure when mounting BEFORE connecting USB:
     * 1. Removes the PD_DRP false-positive block (PC USB-C ports advertise USB_PD_DRP!).
     * 2. Detects cable attach reliably via power_supply + Android BatteryManager plug hint.
     * 3. Stage 1: Soft UDC bounce + soft_connect kick.
     * 4. Stage 2 (if still not configured after soft bounce): Performs a full 1.5s USB PHY wake
     *    pulse (temporarily restoring sys.usb.configfs=1 + mtp so msm-dwc3 / usb_notify exits
     *    idle/charging-only state, then immediately re-locking configfs=0 and re-binding LUN 0 & LUN 1).
     */
    fun watchdogTick(keepAdb: Boolean, allowHotPlugBounce: Boolean = false, androidPluggedHint: Boolean = false): WatchdogTickResult {
        if (ejectRequested.get()) return WatchdogTickResult(hasMountedLun = false)
        return runCatching {
            val adbRebind = if (keepAdb) """if [ -d "${'$'}GADGET/functions/ffs.adb" ] && [ "${'$'}(getprop sys.usb.ffs.ready 2>/dev/null)" = "1" ]; then [ ! -e "${'$'}CFG/f2" ] && ln -s "${'$'}GADGET/functions/ffs.adb" "${'$'}CFG/f2" 2>/dev/null || true; fi""" else ""
            val bounceFlag = if (allowHotPlugBounce) "1" else "0"; val hintFlag = if (androidPluggedHint) "1" else "0"
            val script = """
                $DETECT_GADGET_SH
                if [ -z "${'$'}GADGET" ] || [ -z "${'$'}CFG" ]; then echo "NONE"; exit 0; fi
                ACTIVE_FUNC=""
                for existing in "${'$'}GADGET"/functions/mass_storage.*; do if [ -d "${'$'}existing/lun.0" ]; then ACTIVE_FUNC="${'$'}existing"; break; fi; done
                if [ -z "${'$'}ACTIVE_FUNC" ]; then echo "NONE"; exit 0; fi
                F0=${'$'}(cat "${'$'}ACTIVE_FUNC/lun.0/file" 2>/dev/null); RO0=${'$'}(cat "${'$'}ACTIVE_FUNC/lun.0/ro" 2>/dev/null); CD0=${'$'}(cat "${'$'}ACTIVE_FUNC/lun.0/cdrom" 2>/dev/null)
                F1=${'$'}(cat "${'$'}ACTIVE_FUNC/lun.1/file" 2>/dev/null); RO1=${'$'}(cat "${'$'}ACTIVE_FUNC/lun.1/ro" 2>/dev/null); CD1=${'$'}(cat "${'$'}ACTIVE_FUNC/lun.1/cdrom" 2>/dev/null)
                if [ -z "${'$'}F0" ] && [ -z "${'$'}F1" ]; then echo "NONE"; exit 0; fi
                UDC=${'$'}(ls /sys/class/udc 2>/dev/null | grep -v '^dummy_udc' | head -n 1)
                CUR_UDC=${'$'}(cat "${'$'}GADGET/UDC" 2>/dev/null); STATE=""
                [ -n "${'$'}UDC" ] && STATE=${'$'}(cat "/sys/class/udc/${'$'}UDC/state" 2>/dev/null)
                VBUS=$hintFlag; IS_WALL_BRICK=0
                for ps in /sys/class/power_supply/*usb* /sys/class/power_supply/pc_port /sys/class/power_supply/otg /sys/class/power_supply/ac; do
                    [ -d "${'$'}ps" ] || continue
                    ONL=${'$'}(cat "${'$'}ps/online" 2>/dev/null); PRES=${'$'}(cat "${'$'}ps/present" 2>/dev/null); PTYPE=${'$'}(cat "${'$'}ps/type" 2>/dev/null); REAL_TYPE=${'$'}(cat "${'$'}ps/real_type" 2>/dev/null)
                    if [ "${'$'}ONL" = "1" ] || [ "${'$'}PRES" = "1" ]; then
                        VBUS=1
                        case "${'$'}REAL_TYPE" in USB_DCP|DCP|HVDCP|HVDCP_3|AFC|PE) IS_WALL_BRICK=1 ;; esac
                        case "${'$'}PTYPE" in USB_DCP|Mains) [ -z "${'$'}REAL_TYPE" ] && IS_WALL_BRICK=1 ;; esac
                    fi
                done
                if [ "${'$'}VBUS" = "0" ]; then [ "${'$'}(cat /sys/class/power_supply/battery/status 2>/dev/null)" = "Charging" ] && VBUS=1; fi
                RECOVERED=0; BOUNCED=0
                rebind_gadget_clean() {
                    for p in /sys/bus/platform/devices/*dwc3*/power/control /sys/bus/platform/devices/*ssusb*/power/control /sys/bus/platform/devices/*mtu3*/power/control /sys/bus/platform/devices/*musb*/power/control /sys/class/udc/*/device/power/control; do [ -f "${'$'}p" ] && echo on > "${'$'}p" 2>/dev/null || true; done
                    echo "" > "${'$'}GADGET/UDC" 2>/dev/null; rm -f "${'$'}CFG"/f* 2>/dev/null
                    if [ -n "${'$'}F0" ]; then echo "" > "${'$'}ACTIVE_FUNC/lun.0/file" 2>/dev/null; echo 1 > "${'$'}ACTIVE_FUNC/lun.0/removable" 2>/dev/null; [ -n "${'$'}RO0" ] && echo "${'$'}RO0" > "${'$'}ACTIVE_FUNC/lun.0/ro" 2>/dev/null; [ -n "${'$'}CD0" ] && echo "${'$'}CD0" > "${'$'}ACTIVE_FUNC/lun.0/cdrom" 2>/dev/null; [ -f "${'$'}ACTIVE_FUNC/lun.0/nofua" ] && echo 1 > "${'$'}ACTIVE_FUNC/lun.0/nofua" 2>/dev/null; echo "${'$'}F0" > "${'$'}ACTIVE_FUNC/lun.0/file" 2>/dev/null; fi
                    if [ -n "${'$'}F1" ] && [ -d "${'$'}ACTIVE_FUNC/lun.1" ]; then echo "" > "${'$'}ACTIVE_FUNC/lun.1/file" 2>/dev/null; echo 1 > "${'$'}ACTIVE_FUNC/lun.1/removable" 2>/dev/null; [ -n "${'$'}RO1" ] && echo "${'$'}RO1" > "${'$'}ACTIVE_FUNC/lun.1/ro" 2>/dev/null; [ -n "${'$'}CD1" ] && echo "${'$'}CD1" > "${'$'}ACTIVE_FUNC/lun.1/cdrom" 2>/dev/null; [ -f "${'$'}ACTIVE_FUNC/lun.1/nofua" ] && echo 1 > "${'$'}ACTIVE_FUNC/lun.1/nofua" 2>/dev/null; echo "${'$'}F1" > "${'$'}ACTIVE_FUNC/lun.1/file" 2>/dev/null; fi
                    ln -s "${'$'}ACTIVE_FUNC" "${'$'}CFG/f1" 2>/dev/null || true; $adbRebind
                    if ! echo "${'$'}UDC" > "${'$'}GADGET/UDC" 2>/dev/null; then if [ -e "${'$'}CFG/f2" ]; then rm -f "${'$'}CFG/f2" 2>/dev/null || true; echo "${'$'}UDC" > "${'$'}GADGET/UDC" 2>/dev/null; fi; fi
                }
                if [ -z "${'$'}CUR_UDC" ] && [ -n "${'$'}UDC" ]; then
                    setprop sys.usb.configfs 0 2>/dev/null || true; rebind_gadget_clean; [ -n "${'$'}(cat "${'$'}GADGET/UDC" 2>/dev/null)" ] && RECOVERED=1
                elif [ "$bounceFlag" = "1" ] && [ -n "${'$'}UDC" ] && [ "${'$'}VBUS" = "1" ] && [ "${'$'}IS_WALL_BRICK" = "0" ] && [ "${'$'}STATE" != "configured" ]; then
                    echo "" > "${'$'}GADGET/UDC" 2>/dev/null; rm -f "${'$'}CFG"/f* 2>/dev/null
                    for p in /sys/bus/platform/devices/*dwc3*/power/control /sys/bus/platform/devices/*ssusb*/power/control /sys/bus/platform/devices/*mtu3*/power/control /sys/bus/platform/devices/*musb*/power/control /sys/class/udc/*/device/power/control; do [ -f "${'$'}p" ] && echo auto > "${'$'}p" 2>/dev/null || true; done
                    sleep 0.12
                    WAKE_CFG=""; [ -f "$STATE_FILE" ] && WAKE_CFG=${'$'}(cat "$STATE_FILE" 2>/dev/null)
                    if [ -z "${'$'}WAKE_CFG" ] || [ "${'$'}WAKE_CFG" = "none" ]; then WAKE_CFG=${'$'}(getprop persist.sys.usb.config 2>/dev/null); fi
                    if [ -z "${'$'}WAKE_CFG" ] || [ "${'$'}WAKE_CFG" = "none" ]; then WAKE_CFG="mtp,adb"; fi
                    setprop sys.usb.configfs 1 2>/dev/null || true; setprop sys.usb.config "${'$'}WAKE_CFG" 2>/dev/null || true; svc usb setFunctions mtp 2>/dev/null || true
                    W=0; while [ "${'$'}W" -lt 20 ]; do sleep 0.12; UST=${'$'}(getprop sys.usb.state 2>/dev/null); UDC_ST=${'$'}(cat "/sys/class/udc/${'$'}UDC/state" 2>/dev/null); if [ "${'$'}UDC_ST" = "configured" ] || ([ -n "${'$'}UST" ] && [ "${'$'}UST" != "none" ]); then break; fi; W=${'$'}((W + 1)); done
                    sleep 0.25; setprop sys.usb.config none 2>/dev/null || true
                    W=0; while [ "${'$'}W" -lt 18 ]; do sleep 0.1; UST=${'$'}(getprop sys.usb.state 2>/dev/null); [ "${'$'}UST" = "none" ] || [ -z "${'$'}UST" ] && break; W=${'$'}((W + 1)); done
                    setprop sys.usb.configfs 0 2>/dev/null || true; rebind_gadget_clean
                    W=0; while [ "${'$'}W" -lt 10 ]; do sleep 0.12; STATE=${'$'}(cat "/sys/class/udc/${'$'}UDC/state" 2>/dev/null); [ "${'$'}STATE" = "configured" ] && break; W=${'$'}((W + 1)); done
                    BOUNCED=1
                fi
                [ -n "${'$'}UDC" ] && [ -z "${'$'}STATE" ] && STATE=${'$'}(cat "/sys/class/udc/${'$'}UDC/state" 2>/dev/null)
                PRIMARY=${'$'}{F0##*/}; [ -z "${'$'}PRIMARY" ] && PRIMARY=${'$'}{F1##*/}
                echo "OK|${'$'}RECOVERED|${'$'}BOUNCED|${'$'}VBUS|${'$'}UDC|${'$'}STATE|${'$'}PRIMARY"
            """.trimIndent()
            val out = runIndependentRootScript(script).trim()
            if (!out.startsWith("OK|")) WatchdogTickResult(hasMountedLun = false) else {
                val parts = out.split("|")
                WatchdogTickResult(true, parts.getOrNull(1) == "1", parts.getOrNull(2) == "1", parts.getOrNull(3) == "1", parts.getOrNull(4).orEmpty(), parts.getOrNull(5).orEmpty(), parts.getOrNull(6).orEmpty())
            }
        }.getOrDefault(WatchdogTickResult(hasMountedLun = false))
    }

    suspend fun forceUsbReconnect(keepAdb: Boolean): ActionResult = withContext(Dispatchers.IO) {
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        val tick = watchdogTick(
            keepAdb = keepAdb,
            allowHotPlugBounce = true,
            androidPluggedHint = true
        )
        if (!tick.hasMountedLun) {
            return@withContext ActionResult(false, "No drive currently mounted to reconnect.", entries = entries)
        }
        val configured = tick.linkState.equals("configured", ignoreCase = true)
        val msg = if (configured) {
            "USB link re-enumerated and connected to PC."
        } else {
            "USB controller bounced (${tick.linkState.ifBlank { "waiting for PC" }})."
        }
        entries.add(LogEntry(ts, "reconnect", msg))
        ActionResult(true, msg, entries = entries)
    }

    suspend fun readUsbActivitySample(): UsbActivitySample = withContext(Dispatchers.IO) {
        if (ejectRequested.get()) return@withContext UsbActivitySample()
        runCatching {
            val script = """
                UDC=${'$'}(ls /sys/class/udc 2>/dev/null | grep -v '^dummy_udc' | head -n 1); ST=""; SPD=""
                if [ -n "${'$'}UDC" ]; then ST=${'$'}(cat "/sys/class/udc/${'$'}UDC/state" 2>/dev/null); SPD=${'$'}(cat "/sys/class/udc/${'$'}UDC/current_speed" 2>/dev/null); fi
                RW_CHARS=0; RW_BYTES=0; ACTIVE=0; CPU_TICKS=0; PIDS=""
                for cf in /proc/[0-9]*/comm; do
                    [ -r "${'$'}cf" ] || continue
                    read -r cname < "${'$'}cf" 2>/dev/null || continue
                    case "${'$'}cname" in
                        file-storage*|f_mass_storage*)
                            p="${'$'}{cf#/proc/}"; p="${'$'}{p%/comm}"
                            PIDS="${'$'}PIDS ${'$'}p"
                            ;;
                    esac
                done
                if [ -z "${'$'}PIDS" ]; then
                    PIDS=${'$'}(pidof file-storage 2>/dev/null || true)
                fi
                for pid in ${'$'}PIDS; do
                    [ -r "/proc/${'$'}pid/status" ] || continue
                    PSTATE=""
                    while IFS= read -r st_line; do
                        case "${'$'}st_line" in
                            State:*) set -- ${'$'}st_line; PSTATE="${'$'}2"; break ;;
                        esac
                    done < "/proc/${'$'}pid/status" 2>/dev/null
                    if [ "${'$'}PSTATE" = "R" ] || [ "${'$'}PSTATE" = "D" ]; then ACTIVE=1; fi
                    if [ -r "/proc/${'$'}pid/stat" ]; then
                        read -r stat_raw < "/proc/${'$'}pid/stat" 2>/dev/null || stat_raw=""
                        rest="${'$'}{stat_raw##*) }"
                        if [ -n "${'$'}rest" ] && [ "${'$'}rest" != "${'$'}stat_raw" ]; then
                            set -- ${'$'}rest
                            # After "(comm) ", field 12=utime, field 13=stime
                            ut="${'$'}{12:-0}"; st="${'$'}{13:-0}"
                            case "${'$'}ut" in ''|*[!0-9]*) ut=0 ;; esac
                            case "${'$'}st" in ''|*[!0-9]*) st=0 ;; esac
                            CPU_TICKS=${'$'}((CPU_TICKS + ut + st))
                        fi
                    fi
                    if [ -r "/proc/${'$'}pid/io" ]; then
                        while IFS= read -r io_line; do
                            case "${'$'}io_line" in
                                rchar:*|wchar:*)
                                    set -- ${'$'}io_line
                                    case "${'$'}2" in ''|*[!0-9]*) ;; *) RW_CHARS=${'$'}((RW_CHARS + ${'$'}2)) ;; esac
                                    ;;
                                read_bytes:*|write_bytes:*)
                                    set -- ${'$'}io_line
                                    case "${'$'}2" in ''|*[!0-9]*) ;; *) RW_BYTES=${'$'}((RW_BYTES + ${'$'}2)) ;; esac
                                    ;;
                            esac
                        done < "/proc/${'$'}pid/io" 2>/dev/null
                    fi
                done
                IO_BYTES="${'$'}RW_CHARS"
                if [ "${'$'}IO_BYTES" -le 0 ]; then IO_BYTES="${'$'}RW_BYTES"; fi
                IRQ_TOTAL=0
                if [ -r /proc/interrupts ]; then
                    while IFS= read -r irql; do
                        case "${'$'}irql" in
                            *dwc3*|*ssusb*|*mtu3*|*musb*|*xhci-hcd*|*a600000*)
                                set -- ${'$'}irql
                                shift 2>/dev/null || continue
                                for tok in "${'$'}@"; do
                                    case "${'$'}tok" in
                                        ''|*[!0-9]*) break ;;
                                        *) IRQ_TOTAL=${'$'}((IRQ_TOTAL + tok)) ;;
                                    esac
                                done
                                ;;
                        esac
                    done < /proc/interrupts 2>/dev/null
                fi
                echo "OK|${'$'}ST|${'$'}SPD|${'$'}IO_BYTES|${'$'}ACTIVE|${'$'}CPU_TICKS|${'$'}IRQ_TOTAL"
            """.trimIndent()
            val raw = runIndependentRootScript(script).trim()
            if (!raw.startsWith("OK|")) return@runCatching UsbActivitySample()
            val parts = raw.split("|")
            UsbActivitySample(
                linkState = parts.getOrNull(1).orEmpty(),
                speed = parts.getOrNull(2).orEmpty(),
                ioBytesTotal = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
                kernelReadingActive = parts.getOrNull(4) == "1",
                threadCpuTicks = parts.getOrNull(5)?.toLongOrNull() ?: 0L,
                usbIrqCount = parts.getOrNull(6)?.toLongOrNull() ?: 0L
            )
        }.getOrDefault(UsbActivitySample())
    }

    suspend fun mountImage(
        path: String,
        requestedMode: MountMode,
        settings: AppSettings,
        lunIndex: Int = 0
    ): ActionResult = withContext(Dispatchers.IO) {
        clearAbortWatchdog()
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        return@withContext runCatching {
            val img = resolveDirectPath(path)
            if (IsoInspector.isCompressedImageName(img.substringAfterLast('/'))) {
                return@withContext ActionResult(
                    false,
                    "Compressed images (.xz / .gz / .zst) cannot be mounted directly to a PC. Extract it first or stream-flash it to a USB-C OTG pen drive.",
                    entries = entries
                )
            }
            val mode = requestedMode.resolveForFile(img, settings.fileModes)
            val qImg = shellQuote(img)
            val targetLun = if (lunIndex == 1) 1 else 0

            val selinuxBlock = if (settings.autoPatchSelinux) {
                """
                if command -v magiskpolicy >/dev/null 2>&1; then
                    magiskpolicy --live "allow kernel media_rw_data_file file { read write open getattr }" 2>/dev/null
                    echo "LOG|permissions|Applied SELinux file access policy via magiskpolicy"
                elif command -v ksud >/dev/null 2>&1; then
                    ksud sepolicy patch "allow kernel media_rw_data_file file { read write open getattr }" 2>/dev/null
                    echo "LOG|permissions|Applied SELinux file access policy via ksud"
                elif command -v apd >/dev/null 2>&1; then
                    apd sepolicy patch "allow kernel media_rw_data_file file { read write open getattr }" 2>/dev/null
                    echo "LOG|permissions|Applied SELinux file access policy via apd"
                fi
                """.trimIndent()
            } else {
                """echo "LOG|permissions|Auto-fix file permissions disabled in settings""""
            }

            val adbBlock = if (settings.keepAdbActive) {
                """
                if [ -d "${'$'}GADGET/functions/ffs.adb" ]; then
                    start adbd 2>/dev/null || true
                    ADB_WAIT=0
                    while [ "${'$'}(getprop sys.usb.ffs.ready 2>/dev/null)" != "1" ] && [ "${'$'}ADB_WAIT" -lt 15 ]; do
                        sleep 0.1
                        ADB_WAIT=${'$'}((ADB_WAIT + 1))
                    done
                    if [ "${'$'}(getprop sys.usb.ffs.ready 2>/dev/null)" = "1" ]; then
                        ln -s "${'$'}GADGET/functions/ffs.adb" "${'$'}CFG/f2" 2>/dev/null
                        echo "LOG|usb|Enabled USB Debugging alongside virtual drive"
                    else
                        echo "LOG|usb|adbd endpoints not ready; mounting virtual drive only"
                    fi
                else
                    echo "LOG|usb|USB Debugging interface unavailable; mounted drive only"
                fi
                """.trimIndent()
            } else {
                """echo "LOG|usb|Linked virtual drive function""""
            }

            val script = """
                set +e
                pb_mount() {
                    if [ ! -f $qImg ]; then
                        echo "ERR|file|File not found: $img"
                        return 1
                    fi

                    $DETECT_GADGET_SH
                    if [ -z "${'$'}GADGET" ] || [ -z "${'$'}CFG" ]; then
                        echo "ERR|usb|ConfigFS USB gadget not found on this device"
                        return 1
                    fi

                    UDC=${'$'}(ls /sys/class/udc 2>/dev/null | grep -v '^dummy_udc' | head -n 1)
                    if [ -z "${'$'}UDC" ]; then
                        echo "ERR|usb|No physical USB-C controller found"
                        return 1
                    fi

                    if [ ! -f "$STATE_FILE" ]; then
                        CUR_PROP=${'$'}(getprop sys.usb.config 2>/dev/null)
                        [ -n "${'$'}CUR_PROP" ] && [ "${'$'}CUR_PROP" != "none" ] && echo "${'$'}CUR_PROP" > "$STATE_FILE"
                    fi

                    $selinuxBlock

                    for p in /sys/bus/platform/devices/*dwc3*/power/control /sys/bus/platform/devices/*ssusb*/power/control /sys/bus/platform/devices/*mtu3*/power/control /sys/bus/platform/devices/*musb*/power/control /sys/class/udc/*/device/power/control; do
                        [ -f "${'$'}p" ] && echo on > "${'$'}p" 2>/dev/null || true
                    done

                    if [ "${'$'}(getprop sys.usb.configfs 2>/dev/null)" != "0" ]; then
                        setprop sys.usb.config none 2>/dev/null
                        W=0
                        while [ "${'$'}W" -lt 12 ]; do sleep 0.1; UST=${'$'}(getprop sys.usb.state 2>/dev/null); [ "${'$'}UST" = "none" ] || [ -z "${'$'}UST" ] && break; W=${'$'}((W + 1)); done
                        setprop sys.usb.configfs 0 2>/dev/null
                        echo "LOG|usb|Locked ConfigFS against system USB daemon resets"
                    fi

                    echo "" > "${'$'}GADGET/UDC" 2>/dev/null
                    rm -f "${'$'}CFG"/f*

                    ACTIVE_FUNC=""
                    for existing in "${'$'}GADGET"/functions/mass_storage.*; do
                        if [ -d "${'$'}existing/lun.0" ]; then
                            ACTIVE_FUNC="${'$'}existing"
                            break
                        fi
                    done

                    if [ -z "${'$'}ACTIVE_FUNC" ]; then
                        ACTIVE_FUNC="${'$'}GADGET/functions/mass_storage.0"
                        MK_ERR=${'$'}(mkdir -p "${'$'}ACTIVE_FUNC" 2>&1)
                        if [ ! -d "${'$'}ACTIVE_FUNC/lun.0" ]; then
                            ALT_FUNC="${'$'}GADGET/functions/mass_storage.usb0"
                            MK_ERR2=${'$'}(mkdir -p "${'$'}ALT_FUNC" 2>&1)
                            if [ -d "${'$'}ALT_FUNC/lun.0" ]; then
                                ACTIVE_FUNC="${'$'}ALT_FUNC"
                                echo "LOG|usb|Using fallback function mass_storage.usb0"
                            else
                                setprop sys.usb.configfs 1 2>/dev/null
                                echo "ERR|usb|Kernel failed to create lun.0 (${'$'}MK_ERR ${'$'}MK_ERR2)"
                                return 1
                            fi
                        fi
                    fi

                    TARGET_LUN="${'$'}ACTIVE_FUNC/lun.$targetLun"
                    if [ "$targetLun" = "1" ] && [ ! -d "${'$'}TARGET_LUN" ]; then
                        if ! mkdir "${'$'}TARGET_LUN" 2>/dev/null; then
                            echo "ERR|usb|This phone kernel does not support Secondary Slot (LUN 1). Mount on Primary (LUN 0) instead."
                            L0_CUR=${'$'}(cat "${'$'}ACTIVE_FUNC/lun.0/file" 2>/dev/null | tr -d '\r\n')
                            [ -n "${'$'}L0_CUR" ] && ln -s "${'$'}ACTIVE_FUNC" "${'$'}CFG/f1" 2>/dev/null && echo "${'$'}UDC" > "${'$'}GADGET/UDC" 2>/dev/null
                            return 1
                        fi
                    fi

                    OTHER_LUN="${'$'}ACTIVE_FUNC/lun.${if (targetLun == 0) 1 else 0}"
                    if [ -f "${'$'}OTHER_LUN/file" ] && [ "${'$'}(cat "${'$'}OTHER_LUN/file" 2>/dev/null)" = $qImg ]; then
                        echo "" > "${'$'}OTHER_LUN/file" 2>/dev/null
                    fi

                    echo "" > "${'$'}TARGET_LUN/file" 2>/dev/null
                    echo 1 > "${'$'}TARGET_LUN/removable" 2>/dev/null
                    echo ${mode.ro} > "${'$'}TARGET_LUN/ro" 2>/dev/null
                    echo ${mode.cdrom} > "${'$'}TARGET_LUN/cdrom" 2>/dev/null
                    [ -f "${'$'}TARGET_LUN/nofua" ] && echo 1 > "${'$'}TARGET_LUN/nofua" 2>/dev/null

                    ACTUAL_CDROM=${'$'}(cat "${'$'}TARGET_LUN/cdrom" 2>/dev/null)
                    ACTUAL_RO=${'$'}(cat "${'$'}TARGET_LUN/ro" 2>/dev/null)
                    echo "LOG|drive|LUN $targetLun configured (cdrom=${'$'}ACTUAL_CDROM, ro=${'$'}ACTUAL_RO)"

                    if ! echo $qImg > "${'$'}TARGET_LUN/file" 2>/dev/null; then
                        setprop sys.usb.configfs 1 2>/dev/null
                        echo "ERR|permissions|System blocked access to $img. Check SELinux Auto-Fix in Settings."
                        return 1
                    fi
                    echo "LOG|drive|Loaded $img on LUN $targetLun"

                    ln -s "${'$'}ACTIVE_FUNC" "${'$'}CFG/f1"
                    $adbBlock

                    if ! echo "${'$'}UDC" > "${'$'}GADGET/UDC" 2>/dev/null; then
                        if [ -e "${'$'}CFG/f2" ]; then
                            rm -f "${'$'}CFG/f2"
                            echo "LOG|usb|USB Debugging could not co-bind on ${'$'}UDC; retrying drive only"
                            if ! echo "${'$'}UDC" > "${'$'}GADGET/UDC" 2>/dev/null; then
                                setprop sys.usb.configfs 1 2>/dev/null
                                echo "ERR|usb|Failed to bind USB controller ${'$'}UDC"
                                return 1
                            fi
                        else
                            setprop sys.usb.configfs 1 2>/dev/null
                            echo "ERR|usb|Failed to bind USB controller ${'$'}UDC"
                            return 1
                        fi
                    fi
                    BOUND=${'$'}(cat "${'$'}GADGET/UDC" 2>/dev/null)
                    echo "LOG|usb|Mounted LUN $targetLun on Physical USB-C (${'$'}BOUND)"
                }
                pb_mount
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            var firstError: String? = null
            res.out.forEach { line ->
                when {
                    line.startsWith("LOG|") -> {
                        val parts = line.split("|", limit = 3)
                        if (parts.size == 3) entries.add(LogEntry(ts, parts[1], parts[2], false))
                    }
                    line.startsWith("ERR|") -> {
                        val parts = line.split("|", limit = 3)
                        if (parts.size == 3) {
                            if (firstError == null) firstError = parts[2]
                            entries.add(LogEntry(ts, parts[1], parts[2], true))
                        }
                    }
                    line.isNotBlank() -> entries.add(LogEntry(ts, "system", line, false))
                }
            }
            res.err.filter { it.isNotBlank() }.forEach {
                if (firstError == null) firstError = it
                entries.add(LogEntry(ts, "error", it, true))
            }

            val fileName = img.substringAfterLast('/')
            val slotTag = if (targetLun == 1) " (LUN 1)" else ""
            val userMsg = if (res.isSuccess && firstError == null) {
                "Mounted $fileName$slotTag as ${mode.friendlyTitle}."
            } else {
                firstError ?: "Could not mount $fileName."
            }
            ActionResult(res.isSuccess && firstError == null, userMsg, null, entries)
        }.getOrElse { e ->
            entries.add(LogEntry(ts, "crash-guard", "Mount exception: ${e.message}", true))
            ActionResult(false, "Mount failed: ${e.message ?: "Unexpected error"}", null, entries)
        }
    }

    fun validateDualLunComboPaths(lun0Path: String, lun1Path: String): String? {
        val p0 = resolveDirectPath(lun0Path).trim()
        val p1 = resolveDirectPath(lun1Path).trim()
        if (p0.isEmpty() || p1.isEmpty()) return "Select both a LUN 0 and a LUN 1 image."
        if (p0 == p1) return "LUN 0 and LUN 1 must be two different image files."
        if (IsoInspector.isCompressedImageName(p0.substringAfterLast('/')) || IsoInspector.isCompressedImageName(p1.substringAfterLast('/'))) {
            return "Compressed archives (.xz/.gz/.zst) cannot be paired in Dual-LUN Combos. Decompress first."
        }
        return null
    }

    /**
     * Atomically mounts both LUN 0 and LUN 1 in a single USB ConfigFS controller rebind,
     * avoiding double disconnect/reconnect chimes on the host PC.
     */
    suspend fun mountDualLunCombo(
        combo: DualLunCombo,
        settings: AppSettings
    ): ActionResult = withContext(Dispatchers.IO) {
        clearAbortWatchdog()
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        validateDualLunComboPaths(combo.lun0Path, combo.lun1Path)?.let { err ->
            return@withContext ActionResult(false, err, entries = entries)
        }
        return@withContext runCatching {
            val img0 = resolveDirectPath(combo.lun0Path)
            val img1 = resolveDirectPath(combo.lun1Path)
            val reqMode0 = runCatching { MountMode.valueOf(combo.lun0Mode) }.getOrDefault(MountMode.AUTO)
            val reqMode1 = runCatching { MountMode.valueOf(combo.lun1Mode) }.getOrDefault(MountMode.USB_RW)
            val mode0 = reqMode0.resolveForFile(img0, settings.fileModes)
            val mode1 = reqMode1.resolveForFile(img1, settings.fileModes)
            val qImg0 = shellQuote(img0)
            val qImg1 = shellQuote(img1)

            val selinuxBlock = if (settings.autoPatchSelinux) {
                """
                if command -v magiskpolicy >/dev/null 2>&1; then
                    magiskpolicy --live "allow kernel media_rw_data_file file { read write open getattr }" 2>/dev/null
                    echo "LOG|permissions|Applied SELinux file access policy via magiskpolicy"
                elif command -v ksud >/dev/null 2>&1; then
                    ksud sepolicy patch "allow kernel media_rw_data_file file { read write open getattr }" 2>/dev/null
                    echo "LOG|permissions|Applied SELinux file access policy via ksud"
                elif command -v apd >/dev/null 2>&1; then
                    apd sepolicy patch "allow kernel media_rw_data_file file { read write open getattr }" 2>/dev/null
                    echo "LOG|permissions|Applied SELinux file access policy via apd"
                fi
                """.trimIndent()
            } else {
                """echo "LOG|permissions|Auto-fix file permissions disabled in settings""""
            }

            val adbBlock = if (settings.keepAdbActive) {
                """
                if [ -d "${'$'}GADGET/functions/ffs.adb" ]; then
                    start adbd 2>/dev/null || true
                    ADB_WAIT=0
                    while [ "${'$'}(getprop sys.usb.ffs.ready 2>/dev/null)" != "1" ] && [ "${'$'}ADB_WAIT" -lt 15 ]; do
                        sleep 0.1
                        ADB_WAIT=${'$'}((ADB_WAIT + 1))
                    done
                    if [ "${'$'}(getprop sys.usb.ffs.ready 2>/dev/null)" = "1" ]; then
                        ln -s "${'$'}GADGET/functions/ffs.adb" "${'$'}CFG/f2" 2>/dev/null
                        echo "LOG|usb|Enabled USB Debugging alongside Dual-LUN combo"
                    else
                        echo "LOG|usb|adbd endpoints not ready; mounting Dual-LUN drives only"
                    fi
                fi
                """.trimIndent()
            } else {
                """echo "LOG|usb|Linked Dual-LUN virtual drive function""""
            }

            val script = """
                set +e
                pb_mount_combo() {
                    if [ ! -f $qImg0 ]; then echo "ERR|file|LUN 0 file not found: $img0"; return 1; fi
                    if [ ! -f $qImg1 ]; then echo "ERR|file|LUN 1 file not found: $img1"; return 1; fi

                    $DETECT_GADGET_SH
                    if [ -z "${'$'}GADGET" ] || [ -z "${'$'}CFG" ]; then
                        echo "ERR|usb|ConfigFS USB gadget not found on this device"
                        return 1
                    fi

                    UDC=${'$'}(ls /sys/class/udc 2>/dev/null | grep -v '^dummy_udc' | head -n 1)
                    if [ -z "${'$'}UDC" ]; then
                        echo "ERR|usb|No physical USB-C controller found"
                        return 1
                    fi

                    if [ ! -f "$STATE_FILE" ]; then
                        CUR_PROP=${'$'}(getprop sys.usb.config 2>/dev/null)
                        [ -n "${'$'}CUR_PROP" ] && [ "${'$'}CUR_PROP" != "none" ] && echo "${'$'}CUR_PROP" > "$STATE_FILE"
                    fi

                    $selinuxBlock

                    for p in /sys/bus/platform/devices/*dwc3*/power/control /sys/bus/platform/devices/*ssusb*/power/control /sys/bus/platform/devices/*mtu3*/power/control /sys/bus/platform/devices/*musb*/power/control /sys/class/udc/*/device/power/control; do
                        [ -f "${'$'}p" ] && echo on > "${'$'}p" 2>/dev/null || true
                    done

                    pb_restore_usb_on_err() {
                        [ -n "${'$'}ACTIVE_FUNC" ] && echo "" > "${'$'}ACTIVE_FUNC/lun.0/file" 2>/dev/null || true
                        [ -n "${'$'}ACTIVE_FUNC" ] && [ -f "${'$'}ACTIVE_FUNC/lun.1/file" ] && echo "" > "${'$'}ACTIVE_FUNC/lun.1/file" 2>/dev/null || true
                        PREV_CFG="mtp"
                        if [ -f "$STATE_FILE" ]; then
                            READ_CFG=${'$'}(cat "$STATE_FILE" 2>/dev/null)
                            [ -n "${'$'}READ_CFG" ] && [ "${'$'}READ_CFG" != "none" ] && PREV_CFG="${'$'}READ_CFG"
                            rm -f "$STATE_FILE" 2>/dev/null || true
                        fi
                        setprop sys.usb.configfs 1 2>/dev/null || true
                        setprop sys.usb.config "${'$'}PREV_CFG" 2>/dev/null || true
                    }

                    if [ "${'$'}(getprop sys.usb.configfs 2>/dev/null)" != "0" ]; then
                        setprop sys.usb.config none 2>/dev/null
                        W=0
                        while [ "${'$'}W" -lt 12 ]; do sleep 0.1; UST=${'$'}(getprop sys.usb.state 2>/dev/null); [ "${'$'}UST" = "none" ] || [ -z "${'$'}UST" ] && break; W=${'$'}((W + 1)); done
                        setprop sys.usb.configfs 0 2>/dev/null
                        echo "LOG|usb|Locked ConfigFS against system USB daemon resets"
                    fi

                    echo "" > "${'$'}GADGET/UDC" 2>/dev/null
                    rm -f "${'$'}CFG"/f*

                    ACTIVE_FUNC=""
                    for existing in "${'$'}GADGET"/functions/mass_storage.*; do
                        if [ -d "${'$'}existing/lun.0" ]; then
                            ACTIVE_FUNC="${'$'}existing"
                            break
                        fi
                    done
                    if [ -z "${'$'}ACTIVE_FUNC" ]; then
                        ACTIVE_FUNC="${'$'}GADGET/functions/mass_storage.0"
                        mkdir -p "${'$'}ACTIVE_FUNC" 2>/dev/null
                        if [ ! -d "${'$'}ACTIVE_FUNC/lun.0" ]; then
                            ALT_FUNC="${'$'}GADGET/functions/mass_storage.usb0"
                            mkdir -p "${'$'}ALT_FUNC" 2>/dev/null
                            if [ -d "${'$'}ALT_FUNC/lun.0" ]; then
                                ACTIVE_FUNC="${'$'}ALT_FUNC"
                            else
                                pb_restore_usb_on_err
                                echo "ERR|usb|Kernel failed to create mass_storage lun.0"
                                return 1
                            fi
                        fi
                    fi

                    if [ ! -d "${'$'}ACTIVE_FUNC/lun.1" ]; then
                        if ! mkdir "${'$'}ACTIVE_FUNC/lun.1" 2>/dev/null; then
                            pb_restore_usb_on_err
                            echo "ERR|usb|This kernel does not support Dual-LUN (LUN 1). Mount images individually on LUN 0."
                            return 1
                        fi
                    fi

                    echo "" > "${'$'}ACTIVE_FUNC/lun.0/file" 2>/dev/null
                    echo "" > "${'$'}ACTIVE_FUNC/lun.1/file" 2>/dev/null

                    echo 1 > "${'$'}ACTIVE_FUNC/lun.0/removable" 2>/dev/null
                    echo ${mode0.ro} > "${'$'}ACTIVE_FUNC/lun.0/ro" 2>/dev/null
                    echo ${mode0.cdrom} > "${'$'}ACTIVE_FUNC/lun.0/cdrom" 2>/dev/null
                    [ -f "${'$'}ACTIVE_FUNC/lun.0/nofua" ] && echo 1 > "${'$'}ACTIVE_FUNC/lun.0/nofua" 2>/dev/null
                    if ! echo $qImg0 > "${'$'}ACTIVE_FUNC/lun.0/file" 2>/dev/null; then
                        pb_restore_usb_on_err
                        echo "ERR|permissions|System blocked access to LUN 0 image: $img0"
                        return 1
                    fi
                    echo "LOG|drive|Loaded $img0 on LUN 0 (cdrom=${mode0.cdrom}, ro=${mode0.ro})"

                    echo 1 > "${'$'}ACTIVE_FUNC/lun.1/removable" 2>/dev/null
                    echo ${mode1.ro} > "${'$'}ACTIVE_FUNC/lun.1/ro" 2>/dev/null
                    echo ${mode1.cdrom} > "${'$'}ACTIVE_FUNC/lun.1/cdrom" 2>/dev/null
                    [ -f "${'$'}ACTIVE_FUNC/lun.1/nofua" ] && echo 1 > "${'$'}ACTIVE_FUNC/lun.1/nofua" 2>/dev/null
                    if ! echo $qImg1 > "${'$'}ACTIVE_FUNC/lun.1/file" 2>/dev/null; then
                        pb_restore_usb_on_err
                        echo "ERR|permissions|System blocked access to LUN 1 image: $img1"
                        return 1
                    fi
                    echo "LOG|drive|Loaded $img1 on LUN 1 (cdrom=${mode1.cdrom}, ro=${mode1.ro})"

                    ln -s "${'$'}ACTIVE_FUNC" "${'$'}CFG/f1"
                    $adbBlock

                    if ! echo "${'$'}UDC" > "${'$'}GADGET/UDC" 2>/dev/null; then
                        if [ -e "${'$'}CFG/f2" ]; then
                            rm -f "${'$'}CFG/f2"
                            if ! echo "${'$'}UDC" > "${'$'}GADGET/UDC" 2>/dev/null; then
                                pb_restore_usb_on_err
                                echo "ERR|usb|Failed to bind USB controller ${'$'}UDC"
                                return 1
                            fi
                        else
                            pb_restore_usb_on_err
                            echo "ERR|usb|Failed to bind USB controller ${'$'}UDC"
                            return 1
                        fi
                    fi
                    BOUND=${'$'}(cat "${'$'}GADGET/UDC" 2>/dev/null)
                    echo "LOG|usb|Mounted Dual-LUN combo on Physical USB-C (${'$'}BOUND)"
                }
                pb_mount_combo
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            var firstError: String? = null
            res.out.forEach { line ->
                when {
                    line.startsWith("LOG|") -> {
                        val parts = line.split("|", limit = 3)
                        if (parts.size == 3) entries.add(LogEntry(ts, parts[1], parts[2], false))
                    }
                    line.startsWith("ERR|") -> {
                        val parts = line.split("|", limit = 3)
                        if (parts.size == 3) {
                            if (firstError == null) firstError = parts[2]
                            entries.add(LogEntry(ts, parts[1], parts[2], true))
                        }
                    }
                    line.isNotBlank() -> entries.add(LogEntry(ts, "system", line, false))
                }
            }
            res.err.filter { it.isNotBlank() }.forEach {
                if (firstError == null) firstError = it
                entries.add(LogEntry(ts, "error", it, true))
            }
            val ok = res.isSuccess && firstError == null
            val msg = if (ok) {
                "Mounted Dual-LUN combo '${combo.name}' (${img0.substringAfterLast('/')} + ${img1.substringAfterLast('/')})."
            } else {
                firstError ?: "Could not mount Dual-LUN combo '${combo.name}'."
            }
            ActionResult(ok, msg, null, entries)
        }.getOrElse { e ->
            entries.add(LogEntry(ts, "crash-guard", "Dual-LUN mount exception: ${e.message}", true))
            ActionResult(false, "Dual-LUN mount failed: ${e.message ?: "Unexpected error"}", null, entries)
        }
    }

    suspend fun unmountImage(lunIndex: Int? = null): ActionResult = withContext(Dispatchers.IO) {
        if (lunIndex == null) requestAbortWatchdog()
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        return@withContext try { runCatching {
            val script = if (lunIndex != null) {
                val otherLun = if (lunIndex == 0) 1 else 0
                """
                set +e
                $DETECT_GADGET_SH
                ACTIVE_FUNC=""
                if [ -n "${'$'}GADGET" ]; then
                    for f in "${'$'}GADGET"/functions/mass_storage.*; do
                        if [ -d "${'$'}f/lun.0" ]; then ACTIVE_FUNC="${'$'}f"; break; fi
                    done
                fi

                OTHER_MOUNTED=""
                if [ -n "${'$'}ACTIVE_FUNC" ] && [ -f "${'$'}ACTIVE_FUNC/lun.$otherLun/file" ]; then
                    OTHER_MOUNTED=${'$'}(cat "${'$'}ACTIVE_FUNC/lun.$otherLun/file" 2>/dev/null | tr -d '\r\n')
                fi
                if [ -n "${'$'}ACTIVE_FUNC" ] && [ -n "${'$'}OTHER_MOUNTED" ]; then
                    UDC=${'$'}(ls /sys/class/udc 2>/dev/null | grep -v '^dummy_udc' | head -n 1)
                    echo "" > "${'$'}GADGET/UDC" 2>/dev/null
                    [ -f "${'$'}ACTIVE_FUNC/lun.$lunIndex/file" ] && echo "" > "${'$'}ACTIVE_FUNC/lun.$lunIndex/file" 2>/dev/null
                    [ -n "${'$'}UDC" ] && echo "${'$'}UDC" > "${'$'}GADGET/UDC" 2>/dev/null
                    echo "LOG|usb|Unmounted LUN $lunIndex (LUN $otherLun remains mounted)"
                    echo "PARTIAL_OK"
                else
                    echo "FULL_UNMOUNT"
                fi
                """.trimIndent()
            } else {
                "echo FULL_UNMOUNT"
            }

            val partialRes = Shell.cmd(script).exec()
            if (lunIndex != null && partialRes.out.any { it.trim() == "PARTIAL_OK" }) {
                partialRes.out.filter { it.startsWith("LOG|") }.forEach { line ->
                    val parts = line.split("|", limit = 3)
                    if (parts.size == 3) entries.add(LogEntry(ts, parts[1], parts[2], false))
                }
                return@withContext ActionResult(
                    success = true,
                    userMessage = "Unmounted LUN $lunIndex.",
                    entries = entries
                )
            }

            val fullScript = """
                set +e
                $DETECT_GADGET_SH
                if [ -n "${'$'}GADGET" ]; then
                    echo "" > "${'$'}GADGET/UDC" 2>/dev/null
                    for lun in "${'$'}GADGET"/functions/mass_storage.*/lun.*/file; do
                        [ -f "${'$'}lun" ] && echo "" > "${'$'}lun" 2>/dev/null
                    done
                    [ -n "${'$'}CFG" ] && rm -f "${'$'}CFG"/f*
                fi
                for p in /sys/bus/platform/devices/*dwc3*/power/control /sys/bus/platform/devices/*ssusb*/power/control /sys/bus/platform/devices/*mtu3*/power/control /sys/bus/platform/devices/*musb*/power/control /sys/class/udc/*/device/power/control; do
                    [ -f "${'$'}p" ] && echo auto > "${'$'}p" 2>/dev/null || true
                done
                sleep 0.15

                PREV_CFG=""
                if [ -f "$STATE_FILE" ]; then
                    PREV_CFG=${'$'}(cat "$STATE_FILE")
                    rm -f "$STATE_FILE"
                fi
                if [ -z "${'$'}PREV_CFG" ] || [ "${'$'}PREV_CFG" = "none" ]; then
                    PREV_CFG=${'$'}(getprop persist.sys.usb.config 2>/dev/null)
                fi
                if [ -z "${'$'}PREV_CFG" ] || [ "${'$'}PREV_CFG" = "none" ]; then
                    PREV_CFG="mtp,adb"
                fi

                setprop sys.usb.configfs 1 2>/dev/null
                setprop sys.usb.config "${'$'}PREV_CFG"
                echo "LOG|usb|Unmounted drive(s) and restored normal USB (${'$'}PREV_CFG)"
            """.trimIndent()

            val res = Shell.cmd(fullScript).exec()
            res.out.forEach { line ->
                if (line.startsWith("LOG|")) {
                    val parts = line.split("|", limit = 3)
                    if (parts.size == 3) entries.add(LogEntry(ts, parts[1], parts[2], false))
                } else if (line.isNotBlank()) {
                    entries.add(LogEntry(ts, "system", line, false))
                }
            }
            res.err.filter { it.isNotBlank() }.forEach {
                entries.add(LogEntry(ts, "error", it, true))
            }

            ActionResult(
                success = true,
                userMessage = "Unmounted drive and restored normal phone USB.",
                entries = entries
            )
        }.getOrElse { e ->
            entries.add(LogEntry(ts, "crash-guard", "Unmount exception: ${e.message}", true))
            ActionResult(false, "Unmount failed: ${e.message}", null, entries)
        } } finally {
            clearAbortWatchdog()
        }
    }

    suspend fun createBlankImage(
        targetDir: String,
        fileName: String,
        sizeMb: Int,
        context: Context? = null
    ): ActionResult = withContext(Dispatchers.IO) {
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        return@withContext runCatching {
            val safeDir = resolveDirectPath(targetDir).trimEnd('/')
            val safeName = sanitizeOutputFileName(fileName, ".img", "blank-usb")
            val outPath = "$safeDir/$safeName"
            if (getStatus().mountedLunFor(outPath) != null) {
                return@withContext ActionResult(false, "$safeName is currently mounted to your PC. Unmount it before overwriting.", entries = entries)
            }
            val expectedBytes = sizeMb.toLong().coerceAtLeast(1L) * 1024L * 1024L
            val existingAllocB = queryExistingFileAllocatedBytes(outPath)
            val freeB = getFreeSpaceBytes(safeDir)
            if (freeB > 0L && (freeB + existingAllocB) < (16L * 1024L * 1024L)) {
                return@withContext ActionResult(false, "Phone storage is critically low (${formatBytes(freeB)} free). Free up space before creating a blank drive.", entries = entries)
            }
            val qDir = shellQuote(safeDir)
            val qOut = shellQuote(outPath)

            val script = """
                (
                    set -e
                    mkdir -p $qDir
                    rm -f $qOut
                    if command -v truncate >/dev/null 2>&1; then
                        truncate -s $expectedBytes $qOut 2>&1
                        echo "LOG|create|Created ${sizeMb} MB sparse drive at $outPath"
                    else
                        dd if=/dev/zero of=$qOut bs=1M count=0 seek=$sizeMb 2>/dev/null || dd if=/dev/zero of=$qOut bs=1M count=$sizeMb 2>&1
                        echo "LOG|create|Created ${sizeMb} MB drive at $outPath"
                    fi
                )
            """.trimIndent()

            val res = Shell.cmd(script).exec()
            res.out.forEach { line ->
                when {
                    line.startsWith("LOG|") -> {
                        val parts = line.split("|", limit = 3)
                        if (parts.size == 3) entries.add(LogEntry(ts, parts[1], parts[2], false))
                    }
                    line.startsWith("ERR|") -> {
                        val parts = line.split("|", limit = 3)
                        if (parts.size == 3) entries.add(LogEntry(ts, parts[1], parts[2], true))
                    }
                    line.isNotBlank() -> entries.add(LogEntry(ts, "create", line, false))
                }
            }
            res.err.filter { it.isNotBlank() }.forEach {
                entries.add(LogEntry(ts, "error", it, true))
            }

            val createdBytes = if (res.isSuccess) {
                Shell.cmd("stat -c '%s' $qOut 2>/dev/null || echo 0").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            } else 0L
            val verifiedOk = res.isSuccess && createdBytes == expectedBytes

            if (verifiedOk) {
                fixUserStoragePermissions(context, safeDir, isDir = true)
                fixUserStoragePermissions(context, outPath, isDir = false)
                syncMediaStoreFileSize(context, outPath, createdBytes)
                IsoInspector.invalidate(outPath)
            } else {
                Shell.cmd("rm -f $qOut 2>/dev/null || true").exec()
                entries.add(LogEntry(ts, "create", "Verification failed ($createdBytes / $expectedBytes bytes)", true))
            }

            ActionResult(
                success = verifiedOk,
                userMessage = if (verifiedOk) "Created $safeName (${sizeMb} MB)." else "Could not create $safeName (${formatBytes(createdBytes)} / ${formatBytes(expectedBytes)}).",
                createdPath = if (verifiedOk) outPath else null,
                entries = entries
            )
        }.getOrElse { e ->
            entries.add(LogEntry(ts, "crash-guard", "Create failed: ${e.message}", true))
            ActionResult(false, "Could not create drive: ${e.message}", null, entries)
        }
    }

    fun createSafeTempWorkDir(context: Context, defaultDir: String, prefix: String): String {
        val resolvedDefault = resolveDirectPath(defaultDir).trimEnd('/')
        val parentTmp = "$resolvedDefault/.pocketboot_tmp"
        val candidate = "$parentTmp/$prefix"
        val qParent = shellQuote(parentTmp)
        val qCand = shellQuote(candidate)

        val posixOk = Shell.cmd(
            """
            mkdir -p $qCand 2>/dev/null &&
            touch $qParent/.nomedia 2>/dev/null &&
            ln -sf . $qCand/.pb_symtest 2>/dev/null &&
            rm -f $qCand/.pb_symtest 2>/dev/null
            """.trimIndent()
        ).exec().isSuccess

        if (posixOk) {
            fixUserStoragePermissions(context, parentTmp, isDir = true)
            fixUserStoragePermissions(context, candidate, isDir = true)
            Shell.cmd("chmod -R 0777 $qCand 2>/dev/null || true").exec()
            return candidate
        }

        val fallback = "/data/local/tmp/$prefix"
        Shell.cmd("mkdir -p ${shellQuote(fallback)} && chmod 0777 ${shellQuote(fallback)}").exec()
        return fallback
    }

    fun cleanupTempWorkDir(workDir: String) {
        val qWork = shellQuote(workDir)
        Shell.cmd("rm -rf $qWork 2>/dev/null || true").exec()
        val parent = workDir.substringBeforeLast('/')
        if (parent.endsWith("/.pocketboot_tmp")) {
            val qParent = shellQuote(parent)
            Shell.cmd(
                "if [ -d $qParent ] && [ -z \"\$(ls -A $qParent 2>/dev/null | grep -v '^\\.nomedia\$')\" ]; then rm -rf $qParent 2>/dev/null || true; fi"
            ).exec()
        }
    }

    suspend fun getTempStorageStats(context: Context, scanDirs: List<String>): TempStorageStats =
        withContext(Dispatchers.IO) {
            runCatching {
                var workBytes = 0L
                scanDirs.map { resolveDirectPath(it).trimEnd('/') }.distinct().forEach { dir ->
                    workBytes += queryDirSizeBytes("$dir/.pocketboot_tmp")
                }
                val baseDir = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.cacheDir
                val dlDir = File(baseDir, "turbo_dl")
                val resumableBytes = dlDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                val rootTmpPaths = Shell.cmd("for p in /data/local/tmp/pb_*; do [ -e \"${'$'}p\" ] && echo \"${'$'}p\"; done 2>/dev/null").exec().out
                val rootTmpBytes = rootTmpPaths.map { it.trim() }.filter { it.isNotEmpty() }.sumOf { queryDirSizeBytes(it) }
                TempStorageStats(workTempBytes = workBytes, resumableBytes = resumableBytes, rootTmpBytes = rootTmpBytes)
            }.getOrDefault(TempStorageStats())
        }

    suspend fun cleanTempStorage(context: Context, scanDirs: List<String>): ActionResult =
        withContext(Dispatchers.IO) {
            val before = getTempStorageStats(context, scanDirs).totalBytes
            scanDirs.map { resolveDirectPath(it).trimEnd('/') }.distinct().forEach { dir ->
                val qTmp = shellQuote("$dir/.pocketboot_tmp")
                Shell.cmd("if [ -d $qTmp ]; then find $qTmp -mindepth 1 -maxdepth 1 ! -name '.nomedia' -exec rm -rf {} + 2>/dev/null || true; fi").exec()
            }
            val baseDir = context.applicationContext.getExternalFilesDir(null) ?: context.applicationContext.cacheDir
            val dlDir = File(baseDir, "turbo_dl")
            dlDir.listFiles()?.forEach { runCatching { it.deleteRecursively() } }
            val winStageDir = File(baseDir, "pb_winbuild_cache")
            runCatching { winStageDir.deleteRecursively() }
            Shell.cmd(
                """
                for p in /data/local/tmp/pb_*; do
                    [ -e "${'$'}p" ] || continue
                    umount "${'$'}p" 2>/dev/null || true
                    rm -rf "${'$'}p" 2>/dev/null || true
                done
                """.trimIndent()
            ).exec()
            ActionResult(true, "Cleaned ${formatBytes(before)} of temporary build & download cache.")
        }
}
