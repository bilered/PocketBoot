package com.pocketboot.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

data class WindowsEditionItem(
    val index: Int,
    val name: String
)

data class WindowsBuildConfig(
    val isoFile: ImageFile, val outputDir: String, val outputName: String,
    val scheme: PartitionScheme = PartitionScheme.GPT,
    val selectedEditionIndex: Int? = null, val selectedEditionName: String? = null,
    val osKind: WindowsOsKind = WindowsOsKind.WIN11, val arch: String = "amd64",
    val experience: WindowsExperienceConfig = WindowsExperienceConfig(),
    val winUtil: WinUtilTweaksConfig = WinUtilTweaksConfig(),
    val eiCfgUnlock: Boolean = false,
    val extraDriversDir: String = "", val extraDriverPackDirs: List<String> = emptyList(), val autoMountAfterBuild: Boolean = true,
    val volumeLabel: String = "",
    val directBlockTarget: OtgUsbDevice? = null
)

data class WindowsBuildProgress(
    val stage: String = "Preparing...",
    val detail: String = "",
    val progress: Float = 0f,
    val isIndeterminate: Boolean = true
)

object WindowsImageBuilder {

    private const val UEFI_NTFS_URL = "https://raw.githubusercontent.com/pbatard/rufus/master/res/uefi/uefi-ntfs.img"
    private const val TEMP_MNT = "/data/local/tmp/pb_win_mnt"
    private const val TEMP_ISO_MNT = "/data/local/tmp/pb_iso_src"
    private const val TEMP_EDITION_MNT = "/data/local/tmp/pb_iso_editions"
    private const val FAT32_MAX_FILE = 4_294_967_295L

    private fun nowTs(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    fun isLikelyWindowsName(fileName: String): Boolean =
        IsoInspector.quickGuessByName(fileName) == IsoOsType.WINDOWS

    /**
     * Reads Windows editions directly from sources/install.wim or sources/install.esd inside an ISO
     * in ~50ms by loop-mounting read-only and reading the 208-byte WIMHEADER_V1_PACKED + UTF-16LE XML chunk.
     */
    suspend fun inspectWindowsEditions(context: Context, isoPath: String): List<WindowsEditionItem> = withContext(Dispatchers.IO) {
        val resolved = UsbGadgetEngine.resolveDirectPath(isoPath)
        val qIso = shellQuote(resolved)
        val hdrTmp = File(context.cacheDir, "pb_wim_hdr_${System.nanoTime()}.bin")
        val xmlTmp = File(context.cacheDir, "pb_wim_xml_${System.nanoTime()}.bin")
        val fastFound: List<WindowsEditionItem> = try {
            val mounted = Shell.cmd(
                """
                mkdir -p $TEMP_EDITION_MNT && umount $TEMP_EDITION_MNT 2>/dev/null || true
                mount -t udf -o loop,ro $qIso $TEMP_EDITION_MNT 2>/dev/null || \
                mount -t iso9660 -o loop,ro $qIso $TEMP_EDITION_MNT 2>/dev/null || \
                mount -o loop,ro $qIso $TEMP_EDITION_MNT 2>/dev/null
                """.trimIndent()
            ).exec().isSuccess
            if (!mounted) {
                emptyList()
            } else {
                val wimPath = Shell.cmd(
                    "find $TEMP_EDITION_MNT -maxdepth 2 -type f \\( -iname 'install.wim' -o -iname 'install.esd' -o -iname 'install.swm' \\) 2>/dev/null | head -n 1"
                ).exec().out.firstOrNull().orEmpty().trim()
                if (wimPath.isBlank()) {
                    emptyList()
                } else {
                    val qWim = shellQuote(wimPath)
                    val qHdr = shellQuote(hdrTmp.absolutePath)
                    val gotHdr = Shell.cmd("dd if=$qWim of=$qHdr bs=1 count=208 2>/dev/null && chmod 0644 $qHdr").exec().isSuccess
                    if (!gotHdr || !hdrTmp.exists() || hdrTmp.length() < 104L) {
                        emptyList()
                    } else {
                        val hdr = hdrTmp.readBytes()
                        val magic = String(hdr, 0, 5, Charsets.US_ASCII)
                        if (magic != "MSWIM" && magic != "WLPWM") {
                            emptyList()
                        } else {
                            // WIMHEADER_V1_PACKED: rhXmlData (RESHDR_DISK_SHORT, 24 bytes) starts at offset 72
                            // bytes 72..78: 7-byte LE size, byte 79: flags, bytes 80..87: 8-byte LE file offset
                            var xmlSize = 0L
                            for (i in 0 until 7) {
                                xmlSize = xmlSize or ((hdr[72 + i].toLong() and 0xFFL) shl (8 * i))
                            }
                            val xmlOffset = ByteBuffer.wrap(hdr, 80, 8).order(ByteOrder.LITTLE_ENDIAN).long
                            if (xmlOffset <= 0L || xmlSize <= 16L || xmlSize > 2L * 1024L * 1024L) {
                                emptyList()
                            } else {
                                val qXml = shellQuote(xmlTmp.absolutePath)
                                val skipBlocks = xmlOffset / 512L
                                val leadBytes = (xmlOffset % 512L).toInt()
                                val totalReadBytes = leadBytes + xmlSize
                                val blockCount = (totalReadBytes + 511L) / 512L
                                val gotXml = Shell.cmd(
                                    "dd if=$qWim of=$qXml bs=512 skip=$skipBlocks count=$blockCount 2>/dev/null && chmod 0644 $qXml"
                                ).exec().isSuccess
                                if (!gotXml || !xmlTmp.exists()) {
                                    emptyList()
                                } else {
                                    val rawAll = xmlTmp.readBytes()
                                    if (rawAll.size < leadBytes + 4) {
                                        emptyList()
                                    } else {
                                        val sliceLen = minOf(xmlSize.toInt(), rawAll.size - leadBytes)
                                        val xmlText = String(rawAll, leadBytes, sliceLen, Charsets.UTF_16LE)
                                        val imageRegex = Regex("""<IMAGE\s+INDEX="(\d+)"[^>]*>(.*?)</IMAGE>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                                        val displayRegex = Regex("""<DISPLAYNAME>(.*?)</DISPLAYNAME>""", RegexOption.IGNORE_CASE)
                                        val nameRegex = Regex("""<NAME>(.*?)</NAME>""", RegexOption.IGNORE_CASE)
                                        val found = mutableListOf<WindowsEditionItem>()
                                        imageRegex.findAll(xmlText).forEach { m ->
                                            val idx = m.groupValues[1].toIntOrNull() ?: return@forEach
                                            val body = m.groupValues[2]
                                            val label = (displayRegex.find(body)?.groupValues?.get(1)
                                                ?: nameRegex.find(body)?.groupValues?.get(1)
                                                ?: "Edition #$idx").trim()
                                            if (label.isNotEmpty()) {
                                                found.add(WindowsEditionItem(index = idx, name = label))
                                            }
                                        }
                                        found.sortedBy { it.index }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            hdrTmp.delete()
            xmlTmp.delete()
            Shell.cmd("umount $TEMP_EDITION_MNT 2>/dev/null || umount -l $TEMP_EDITION_MNT 2>/dev/null; rm -rf $TEMP_EDITION_MNT 2>/dev/null").exec()
        }
        if (fastFound.isNotEmpty()) fastFound else inspectEditionsViaBundledTools(context, resolved)
    }

    private fun inspectEditionsViaBundledTools(context: Context, resolvedIso: String): List<WindowsEditionItem> {
        val sevenZip = findBundledBinary(context, "lib7zzs.so", listOf("7zzs", "7zz", "7z")) ?: return emptyList()
        val wimlib = findBundledBinary(context, "libwimlib_imagex.so", listOf("wimlib-imagex")) ?: return emptyList()
        val tmpDir = File(context.cacheDir, "pb_ed_probe_${System.nanoTime()}").apply { mkdirs() }
        return try {
            val qIso = shellQuote(resolvedIso)
            val q7z = shellQuote(sevenZip)
            val qWim = shellQuote(wimlib)
            val qTmp = shellQuote(tmpDir.absolutePath)
            // Locate exact sources/install.(wim|esd|swm) path without relying on `awk` (handles spaces safely)
            val wimRegex = Regex("""(^|/)sources/install\.(wim|esd|swm)$""", RegexOption.IGNORE_CASE)
            val wimRel = Shell.cmd("$q7z l -slt $qIso 2>/dev/null").exec().out
                .asSequence()
                .map { it.trim() }
                .filter { it.startsWith("Path = ") }
                .map { it.removePrefix("Path = ").trim() }
                .firstOrNull { p -> wimRegex.containsMatchIn(p.replace('\\', '/')) }
                .orEmpty()
            if (wimRel.isBlank()) return emptyList()
            val extOk = Shell.cmd("export TMPDIR=$qTmp && $q7z e -y -o$qTmp $qIso ${shellQuote(wimRel)} >/dev/null 2>&1").exec().isSuccess
            val extracted = tmpDir.listFiles()?.firstOrNull { it.isFile && it.length() > 1024L } ?: return emptyList()
            if (!extOk) return emptyList()
            val lines = Shell.cmd("export TMPDIR=$qTmp && $qWim info ${shellQuote(extracted.absolutePath)} 2>/dev/null").exec().out
            val found = mutableListOf<WindowsEditionItem>()
            var curIdx = -1
            var curName = ""
            lines.forEach { raw ->
                val line = raw.trim()
                when {
                    line.startsWith("Index:") -> {
                        if (curIdx > 0 && curName.isNotBlank()) found.add(WindowsEditionItem(curIdx, curName))
                        curIdx = line.substringAfter(':').trim().toIntOrNull() ?: -1
                        curName = ""
                    }
                    line.startsWith("Display Name:") -> {
                        curName = line.substringAfter(':').trim()
                    }
                    line.startsWith("Name:") && curName.isBlank() -> {
                        curName = line.substringAfter(':').trim()
                    }
                }
            }
            if (curIdx > 0 && curName.isNotBlank()) found.add(WindowsEditionItem(curIdx, curName))
            found.sortedBy { it.index }
        } catch (_: Exception) {
            emptyList()
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    /**
     * Resolves clean 1-line Windows Explorer-safe NTFS label (default: "POCKETBOOT-WIN 11", <= 20 chars)
     * and FAT32 11-char fallback label ("PB-WIN11"), honoring any custom user volumeLabel override.
     */
    internal fun resolveWindowsVolumeLabels(
        osKind: WindowsOsKind,
        customLabel: String = ""
    ): Pair<String, String> {
        val defaultNtfs = IsoInspector.defaultWindowsVolumeLabel(osKind, isFat32 = false)
        val defaultFat = IsoInspector.defaultWindowsVolumeLabel(osKind, isFat32 = true)
        val trimmed = customLabel.trim()
        val ntfsLabel = if (trimmed.isNotEmpty()) IsoInspector.sanitizeNtfsLabel(trimmed, defaultNtfs) else defaultNtfs
        val fatLabel = if (trimmed.isNotEmpty()) IsoInspector.sanitizeFatLabel(trimmed, defaultFat, uppercase = true) else defaultFat
        return ntfsLabel to fatLabel
    }

    fun cancelActiveBuild() {
        BuildThermalHelper.cancelActiveBuildProcess()
    }

    fun generateAutounattendXml(
        config: WindowsBuildConfig,
        includeWindowsPePass: Boolean = true,
        uiLang: String = "",
        hasSpecializeScript: Boolean = config.experience.needsSpecializeScript() || config.winUtil.hasAnyActive(),
        includeLabConfigInPe: Boolean = true
    ): String = WindowsCustomizationEngine.generateAutounattendXml(
        experience = config.experience,
        winUtil = config.winUtil,
        arch = config.arch,
        selectedEditionIndex = config.selectedEditionIndex,
        hasSpecializeScript = hasSpecializeScript,
        hasOemUpdates = false,
        includeWindowsPePass = includeWindowsPePass,
        uiLang = uiLang,
        includeLabConfigInPe = includeLabConfigInPe
    )

    private fun loadUefiNtfsImage(context: Context): ByteArray? {
        runCatching {
            context.assets.open("uefi-ntfs.img").use { stream ->
                val bytes = stream.readBytes()
                if (bytes.size >= 65536) return bytes
            }
        }
        val cached = File(context.filesDir, "uefi-ntfs.img")
        if (cached.exists() && cached.length() >= 65536L) {
            return runCatching { cached.readBytes() }.getOrNull()
        }
        return runCatching {
            val url = URL(UEFI_NTFS_URL)
            require(url.protocol.equals("https", ignoreCase = true)) { "HTTPS required for UEFI:NTFS download." }
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = true
            }
            try {
                val code = conn.responseCode
                if (!conn.url.protocol.equals("https", ignoreCase = true)) return null
                if (code in 200..299) {
                    val maxBytes = 16 * 1024 * 1024
                    if (conn.contentLengthLong > maxBytes.toLong()) return null
                    val baos = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16384)
                    conn.inputStream.use { input ->
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            if (baos.size() + r > maxBytes) return null
                            baos.write(buf, 0, r)
                        }
                    }
                    val bytes = baos.toByteArray()
                    if (bytes.size >= 65536) {
                        val tmp = File(context.filesDir, "uefi-ntfs.img.tmp")
                        tmp.writeBytes(bytes)
                        if (!tmp.renameTo(cached)) {
                            cached.writeBytes(bytes)
                            tmp.delete()
                        }
                        return bytes
                    }
                }
                null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    fun findBundledBinary(context: Context, libName: String, systemFallbacks: List<String> = emptyList()): String? {
        val bundled = File(context.applicationInfo.nativeLibraryDir, libName)
        if (bundled.exists() && bundled.canExecute()) return bundled.absolutePath

        if (systemFallbacks.isEmpty()) return null
        val listStr = systemFallbacks.joinToString(" ")
        val found = Shell.cmd(
            "for b in $listStr; do if command -v \"${'$'}b\" >/dev/null 2>&1; then command -v \"${'$'}b\"; break; elif [ -x \"${'$'}b\" ]; then echo \"${'$'}b\"; break; fi; done"
        ).exec().out.firstOrNull()?.trim()
        return found?.ifBlank { null }
    }

    suspend fun buildWindowsUsbImage(
        context: Context,
        config: WindowsBuildConfig,
        onProgress: (WindowsBuildProgress) -> Unit
    ): ActionResult = withContext(Dispatchers.IO) {
        val ts = nowTs()
        BuildThermalHelper.resetCancelFlag()
        val entries = mutableListOf<LogEntry>()
        val isDirectBlock = config.directBlockTarget != null
        val safeDir = UsbGadgetEngine.resolveDirectPath(config.outputDir).trimEnd('/')
        val safeName = if (isDirectBlock) {
            config.directBlockTarget?.displayTitle ?: "USB-C OTG Drive"
        } else {
            UsbGadgetEngine.sanitizeOutputFileName(config.outputName, ".img", "windows-usb")
        }
        var outPath = if (isDirectBlock) config.directBlockTarget?.devPath.orEmpty() else "$safeDir/$safeName"
        val srcIsoPath = UsbGadgetEngine.resolveDirectPath(config.isoFile.path)
        val qIso = shellQuote(srcIsoPath)
        var qOut = shellQuote(outPath)
        val qDir = shellQuote(safeDir)

        var loopDev = ""
        var loopIsLoopNode = true
        var verifiedOtg: OtgUsbDevice? = null

        fun cleanup(deletePartial: Boolean) {
            BuildThermalHelper.unmountAndDetachClean(
                mountPoints = listOf(TEMP_MNT, TEMP_ISO_MNT),
                loopDevices = if (loopIsLoopNode && loopDev.isNotBlank()) listOf(loopDev) else emptyList(),
                removeDirs = true
            )
            // NEVER rm -f a physical /dev/block/sdX node!
            if (deletePartial && !isDirectBlock) {
                Shell.cmd("rm -f $qOut 2>/dev/null").exec()
            }
        }

        return@withContext runCatching {
            if (!Shell.getShell().isRoot) {
                return@withContext ActionResult(false, "Root access is required to build a bootable USB image.", entries = entries)
            }
            if (!isDirectBlock) {
                if (outPath.equals(srcIsoPath, ignoreCase = true)) {
                    return@withContext ActionResult(false, "Output image cannot overwrite the source ISO.", entries = entries)
                }
                if (UsbGadgetEngine.getStatus().mountedLunFor(outPath) != null) {
                    return@withContext ActionResult(false, "$safeName is currently mounted to your PC. Unmount it before overwriting.", entries = entries)
                }
            }

            onProgress(WindowsBuildProgress("Checking ISO & tools...", config.isoFile.name, 0.04f, true))

            val isoSize = Shell.cmd("stat -c '%s' $qIso 2>/dev/null")
                .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: config.isoFile.sizeBytes
            if (isoSize <= 0L) {
                return@withContext ActionResult(false, "Source ISO not found or empty.", entries = entries)
            }
            val allDriverDirs = (config.extraDriverPackDirs + listOf(config.extraDriversDir))
                .map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val extraDriversBytes = allDriverDirs.sumOf { UsbGadgetEngine.queryDirSizeBytes(it) }

            val uefiNtfsBytes = loadUefiNtfsImage(context)
            val uefiBytesLen = uefiNtfsBytes?.size?.toLong() ?: (1024L * 1024L)
            val effectiveScheme: PartitionScheme
            val layout: DiskLayoutSpec
            if (isDirectBlock) {
                val reqTarget = config.directBlockTarget
                    ?: return@withContext ActionResult(false, "No physical USB-C OTG drive selected.", entries = entries)
                onProgress(WindowsBuildProgress("Verifying USB-C drive...", reqTarget.displayTitle, 0.07f, true))
                val (prepDev, prepErr) = OtgUsbFlasher.verifyPhysicalOtgDisk(reqTarget)
                if (prepDev == null) {
                    return@withContext ActionResult(false, prepErr ?: "USB-C OTG drive preparation failed.", entries = entries)
                }
                verifiedOtg = prepDev
                outPath = prepDev.devPath
                qOut = shellQuote(outPath)
                effectiveScheme = DiskPartitioner.effectiveSchemeForDisk(config.scheme, prepDev.sizeBytes)
                val minPart1Bytes = isoSize + extraDriversBytes + (768L * 1024L * 1024L)
                layout = DiskPartitioner.calculateFixedDiskLayout(
                    totalDiskBytes = prepDev.sizeBytes,
                    minPart1Bytes = minPart1Bytes,
                    part2Bytes = uefiBytesLen,
                    scheme = effectiveScheme
                ) ?: return@withContext ActionResult(
                    false,
                    "USB pen drive (${UsbGadgetEngine.formatBytes(prepDev.sizeBytes)}) is too small for ${config.isoFile.name} + drivers + boot.wim patching headroom (need ~${UsbGadgetEngine.formatBytes(minPart1Bytes + uefiBytesLen)}).",
                    entries = entries
                )
            } else {
                effectiveScheme = config.scheme
                layout = DiskPartitioner.calculateLayout(isoSize + extraDriversBytes, uefiBytesLen)
                val rawFreeSpace = UsbGadgetEngine.getFreeSpaceBytes(safeDir)
                val existingOutAllocB = UsbGadgetEngine.queryExistingFileAllocatedBytes(outPath)
                val freeSpace = if (rawFreeSpace > 0L) rawFreeSpace + existingOutAllocB else 0L
                if (freeSpace > 0L && freeSpace < layout.totalBytes + (128L * 1024L * 1024L)) {
                    val needed = UsbGadgetEngine.formatBytes(layout.totalBytes)
                    val avail = UsbGadgetEngine.formatBytes(freeSpace)
                    val reason = "Not enough free space. Need $needed, only $avail available."
                    entries.add(LogEntry(ts, "win-build", reason, true))
                    return@withContext ActionResult(false, reason, entries = entries)
                }
            }

            val sevenZip = findBundledBinary(
                context,
                "lib7zzs.so",
                listOf("7zzs", "7zz", "7z", "/data/data/com.termux/files/usr/bin/7z", "/data/data/com.termux/files/usr/bin/7zz")
            )
            var hasOver4GbFile = isoSize > FAT32_MAX_FILE
            if (sevenZip != null) {
                entries.add(LogEntry(ts, "win-build", "Extractor: $sevenZip"))
                val maxEntrySize = Shell.cmd("${shellQuote(sevenZip)} l -slt $qIso 2>/dev/null").exec().out
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("Size = ") }
                    .mapNotNull { it.removePrefix("Size = ").trim().toLongOrNull() }
                    .maxOrNull() ?: 0L
                if (maxEntrySize > 0L) {
                    hasOver4GbFile = maxEntrySize > FAT32_MAX_FILE
                    entries.add(LogEntry(ts, "win-build", "Largest file in ISO: ${UsbGadgetEngine.formatBytes(maxEntrySize)}"))
                }
            }

            val ntfsFormatter = findBundledBinary(
                context,
                "libmkfs_ntfs.so",
                listOf("mkfs.ntfs", "mkntfs", "/system/bin/mkfs.ntfs", "/data/data/com.termux/files/usr/bin/mkntfs")
            )
            val ntfs3gBin = findBundledBinary(
                context,
                "libntfs_3g.so",
                listOf("ntfs-3g", "/system/bin/ntfs-3g", "/data/data/com.termux/files/usr/bin/ntfs-3g")
            )
            val msSysBin = findBundledBinary(
                context,
                "libms_sys.so",
                listOf("ms-sys", "/data/data/com.termux/files/usr/bin/ms-sys")
            )
            val procFs = Shell.cmd("cat /proc/filesystems 2>/dev/null").exec().out
                .map { it.trim().substringAfterLast('\t').substringAfterLast(' ').lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            val kernelNtfsDriver = when {
                "ntfs3" in procFs -> "ntfs3"
                "tntfs" in procFs -> "tntfs"
                "ufsd" in procFs -> "ufsd"
                else -> null
            }
            val hasFuse = ("fuse" in procFs || "fuseblk" in procFs) && Shell.cmd("[ -e /dev/fuse ]").exec().isSuccess
            val canMountNtfs = kernelNtfsDriver != null || (ntfs3gBin != null && hasFuse)
            val useNtfs = ntfsFormatter != null && canMountNtfs

            entries.add(
                LogEntry(
                    ts,
                    "win-build",
                    "NTFS check: mkntfs=${ntfsFormatter != null}, kernel=${kernelNtfsDriver ?: "none"}, fuse=${hasFuse}, ntfs-3g=${ntfs3gBin != null}, ms-sys=${msSysBin != null}"
                )
            )

            if (!useNtfs && hasOver4GbFile) {
                val reason = if (ntfsFormatter == null) {
                    "Bundled mkfs.ntfs binary not found. Rebuild APK via GitHub Actions."
                } else {
                    "Cannot mount NTFS: kernel lacks ntfs3 and bundled ntfs-3g FUSE driver is unavailable."
                }
                entries.add(LogEntry(ts, "win-build", reason, true))
                return@withContext ActionResult(false, reason, entries = entries)
            }

            val needUefiNtfs = useNtfs && effectiveScheme == PartitionScheme.GPT
            if (needUefiNtfs && uefiNtfsBytes == null) {
                val reason = "Missing UEFI NTFS boot partition asset. Connect to internet once or rebuild APK."
                entries.add(LogEntry(ts, "win-build", reason, true))
                return@withContext ActionResult(false, reason, entries = entries)
            }

            onProgress(
                WindowsBuildProgress(
                    stage = if (isDirectBlock) "Partitioning USB-C drive (${effectiveScheme.label})..." else "Creating ${effectiveScheme.label} image...",
                    detail = "${UsbGadgetEngine.formatBytes(layout.totalBytes)} (${if (useNtfs) "NTFS Boot Layout" else "FAT32"})",
                    progress = 0.12f,
                    isIndeterminate = false
                )
            )

            if (!isDirectBlock) {
                val allocRes = Shell.cmd(
                    """
                    mkdir -p $qDir && rm -f $qOut &&
                    (truncate -s ${layout.totalBytes} $qOut 2>/dev/null || dd if=/dev/zero of=$qOut bs=1M count=0 seek=${layout.totalBytes / (1024L * 1024L)} 2>/dev/null)
                    """.trimIndent()
                ).exec()
                if (!allocRes.isSuccess) {
                    cleanup(deletePartial = true)
                    return@withContext ActionResult(false, "Failed to allocate image file at $outPath.", entries = entries)
                }
            } else {
                val otgToWipe = verifiedOtg ?: return@withContext ActionResult(false, "USB-C OTG drive unavailable.", entries = entries)
                onProgress(WindowsBuildProgress("Unmounting & wiping USB drive...", otgToWipe.displayTitle, 0.14f, true))
                OtgUsbFlasher.unmountAndWipeOtgDisk(otgToWipe, entries, ts)?.let { err ->
                    return@withContext ActionResult(false, err, entries = entries)
                }
            }

            val part1IsFat32 = !useNtfs
            val includeUefiPart = useNtfs && uefiNtfsBytes != null
            val tableErr = writeTablesViaRoot(
                context = context,
                outImgPath = outPath,
                scheme = effectiveScheme,
                layout = layout,
                part1IsFat32 = part1IsFat32,
                uefiNtfsData = if (includeUefiPart) uefiNtfsBytes else null
            )
            if (tableErr != null) {
                entries.add(LogEntry(ts, "win-build", "Partition table write failed: $tableErr", true))
                cleanup(deletePartial = true)
                return@withContext ActionResult(false, "Failed to write partition table: $tableErr", entries = entries)
            }
            entries.add(
                LogEntry(
                    ts,
                    "win-build",
                    "Wrote ${effectiveScheme.label}: Part1 ${if (useNtfs) "NTFS" else "FAT32"} LBA=${layout.part1StartLba}" +
                        if (includeUefiPart) " + Part2 UEFI:NTFS LBA=${layout.part2StartLba}" else ""
                )
            )

            onProgress(WindowsBuildProgress("Formatting Partition 1...", if (useNtfs) "NTFS" else "FAT32", 0.22f, false))

            val otgDevForAttach = verifiedOtg
            if (otgDevForAttach != null) {
                val (attachedNode, isLoop) = OtgUsbFlasher.attachOtgPartitionSlice(
                    dev = otgDevForAttach,
                    partIndex = 1,
                    offsetBytes = layout.part1OffsetBytes,
                    sizeBytes = layout.part1SizeBytes
                )
                loopDev = attachedNode
                loopIsLoopNode = isLoop
            } else {
                val loopCmd = """
                    L=${'$'}(losetup -f -s -o ${layout.part1OffsetBytes} -S ${layout.part1SizeBytes} $qOut 2>/dev/null)
                    if [ -z "${'$'}L" ]; then
                        L=${'$'}(losetup -f --show -o ${layout.part1OffsetBytes} --sizelimit ${layout.part1SizeBytes} $qOut 2>/dev/null)
                    fi
                    if [ -z "${'$'}L" ]; then
                        CAND=${'$'}(losetup -f 2>/dev/null)
                        if [ -n "${'$'}CAND" ]; then
                            if losetup -o ${layout.part1OffsetBytes} -S ${layout.part1SizeBytes} "${'$'}CAND" $qOut 2>/dev/null || \
                               losetup -o ${layout.part1OffsetBytes} --sizelimit ${layout.part1SizeBytes} "${'$'}CAND" $qOut 2>/dev/null; then
                                L="${'$'}CAND"
                            fi
                        fi
                    fi
                    echo "${'$'}L"
                """.trimIndent()
                loopDev = Shell.cmd(loopCmd).exec().out.lastOrNull().orEmpty().trim()
                loopIsLoopNode = true
            }
            if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) {
                entries.add(LogEntry(ts, "win-build", "Failed to attach Partition 1 slice", true))
                cleanup(deletePartial = true)
                return@withContext ActionResult(false, "Could not attach Partition 1 on target drive.", entries = entries)
            }
            val qLoop = shellQuote(loopDev)

            val (ntfsLabel, fatLabel) = resolveWindowsVolumeLabels(
                osKind = config.osKind,
                customLabel = config.volumeLabel
            )

            val fmtRes = if (useNtfs) {
                val activeNtfsFormatter = ntfsFormatter ?: run {
                cleanup(deletePartial = true)
                return@withContext ActionResult(false, "NTFS formatter unavailable on device.", entries = entries)
            }
            val qFmt = shellQuote(activeNtfsFormatter)
                Shell.cmd(
                    "$qFmt -F -Q -c 4096 -L ${shellQuote(ntfsLabel)} -H 255 -S 63 -p ${layout.part1StartLba} $qLoop 2>&1"
                ).exec()
            } else {
                val mkFatBin = findBundledBinary(context, "libmkfs_fat.so", listOf("mkfs.vfat", "mkfs.fat", "newfs_msdos"))
                val fatCmd = when {
                    mkFatBin != null && mkFatBin.contains("newfs_msdos") -> "${shellQuote(mkFatBin)} -F 32 -L ${shellQuote(fatLabel)} $qLoop 2>&1"
                    mkFatBin != null -> "${shellQuote(mkFatBin)} -F 32 -n ${shellQuote(fatLabel)} $qLoop 2>&1"
                    else -> "echo 'No FAT32 formatter found'; exit 1"
                }
                Shell.cmd(fatCmd).exec()
            }
            if (!fmtRes.isSuccess) {
                val errTxt = (fmtRes.out + fmtRes.err).joinToString(" ").take(260)
                entries.add(LogEntry(ts, "win-build", "Format failed: $errTxt", true))
                cleanup(deletePartial = true)
                return@withContext ActionResult(false, "Filesystem format failed: $errTxt", entries = entries)
            }
            entries.add(LogEntry(ts, "win-build", "Formatted Partition 1 (${if (useNtfs) "NTFS 4K, label=$ntfsLabel" else "FAT32, label=$fatLabel"}) on $loopDev"))

            if (effectiveScheme == PartitionScheme.MBR && msSysBin != null) {
                onProgress(WindowsBuildProgress("Writing Windows boot sector...", "Injecting Windows bootmgr VBR & MBR", 0.28f, false))
                val qMs = shellQuote(msSysBin)
                val vbrFlag = if (useNtfs) "-n" else "-8"
                val vbrRes = Shell.cmd("$qMs $vbrFlag -f $qLoop 2>&1").exec()
                if (vbrRes.isSuccess) {
                    entries.add(LogEntry(ts, "win-build", "Wrote Windows bootmgr VBR ($vbrFlag) via ms-sys"))
                } else {
                    entries.add(LogEntry(ts, "win-build", "ms-sys VBR warning: ${(vbrRes.out + vbrRes.err).joinToString(" ").take(160)}", true))
                }
            }

            val mntCmd = if (useNtfs) {
                val attempts = mutableListOf<String>()
                if (kernelNtfsDriver != null) {
                    attempts.add("mount -t $kernelNtfsDriver -o rw $qLoop $TEMP_MNT 2>&1")
                }
                if (ntfs3gBin != null && hasFuse) {
                    val qN3g = shellQuote(ntfs3gBin)
                    attempts.add("$qN3g -o rw,big_writes,noatime $qLoop $TEMP_MNT 2>&1")
                    attempts.add("$qN3g -o rw $qLoop $TEMP_MNT 2>&1")
                }
                "mkdir -p $TEMP_MNT && (${attempts.joinToString(" || ")})"
            } else {
                "mkdir -p $TEMP_MNT && (mount -t vfat -o rw $qLoop $TEMP_MNT 2>&1 || mount -o rw $qLoop $TEMP_MNT 2>&1)"
            }
            val mntRes = Shell.cmd(mntCmd).exec()
            if (mntRes.isSuccess && useNtfs) {
                val usedDriver = if (kernelNtfsDriver != null) "$kernelNtfsDriver / ntfs-3g" else "ntfs-3g (FUSE)"
                entries.add(LogEntry(ts, "win-build", "Mounted NTFS Partition 1 via $usedDriver"))
            }
            if (!mntRes.isSuccess) {
                val errTxt = (mntRes.out + mntRes.err).joinToString(" ").take(220)
                entries.add(LogEntry(ts, "win-build", "Mount Partition 1 failed: $errTxt", true))
                cleanup(deletePartial = true)
                return@withContext ActionResult(false, "Could not mount target partition: $errTxt", entries = entries)
            }

            val usbProf = BuildThermalHelper.currentDefaultThermalProfile()
            BuildThermalHelper.waitForThermalCooldownIfNeeded(context, usbProf) { temp, resumeAt ->
                onProgress(
                    WindowsBuildProgress(
                        stage = "Cooling down phone...",
                        detail = String.format(Locale.US, "%.1f°C -> resuming at %.1f°C", temp, resumeAt),
                        progress = 0.40f,
                        isIndeterminate = true
                    )
                )
            }
            onProgress(
                WindowsBuildProgress(
                    stage = "Extracting Windows ISO...",
                    detail = "Copying setup files (${UsbGadgetEngine.formatBytes(isoSize)} · ${usbProf.shortLabel} thermal)",
                    progress = 0.42f,
                    isIndeterminate = true
                )
            )

            var otgYanked = false
            val otgWatchDev = verifiedOtg
            val otgWatchJob = if (otgWatchDev != null) {
                val qStat = shellQuote("/sys/block/${otgWatchDev.devName}/stat")
                val startSec = OtgUsbFlasher.parseSectorsWrittenFromStat(
                    Shell.cmd("cat $qStat 2>/dev/null").exec().out.firstOrNull().orEmpty()
                )
                launch(Dispatchers.IO) {
                    while (isActive) {
                        delay(700L)
                        if (!OtgUsbFlasher.isOtgDeviceStillConnected(otgWatchDev.devName)) {
                            otgYanked = true
                            BuildThermalHelper.cancelActiveBuildProcess()
                            break
                        }
                        val curSec = OtgUsbFlasher.parseSectorsWrittenFromStat(
                            Shell.cmd("cat $qStat 2>/dev/null").exec().out.firstOrNull().orEmpty()
                        )
                        val writtenB = ((curSec - startSec).coerceAtLeast(0L)) * 512L
                        val frac = (writtenB.toFloat() / isoSize.coerceAtLeast(1L).toFloat()).coerceIn(0f, 0.98f)
                        onProgress(
                            WindowsBuildProgress(
                                stage = "Extracting Windows ISO to USB...",
                                detail = "${UsbGadgetEngine.formatBytes(writtenB)} / ~${UsbGadgetEngine.formatBytes(isoSize)} · ${usbProf.shortLabel}",
                                progress = 0.42f + frac * 0.38f,
                                isIndeterminate = writtenB <= 0L
                            )
                        )
                    }
                }
            } else null

            val extractRes = try {
                if (sevenZip != null) {
                    val mmtArg = BuildThermalHelper.sevenZipThreadArgFor(usbProf)
                    BuildThermalHelper.runIsolatedRootScript(
                        context,
                        "winusb_ext",
                        "set -e\n${BuildThermalHelper.thermalExecPrefix(usbProf)}\n\$THERM_PFX ${shellQuote(sevenZip)} x -y $mmtArg -o$TEMP_MNT $qIso 2>&1",
                        trackPid = true
                    )
                } else {
                    BuildThermalHelper.runIsolatedRootScript(
                        context,
                        "winusb_loopcp",
                        """
                        set -e
                        ${BuildThermalHelper.thermalExecPrefix(usbProf)}
                        mkdir -p $TEMP_ISO_MNT
                        (mount -t udf -o loop,ro $qIso $TEMP_ISO_MNT 2>&1 || mount -t iso9660 -o loop,ro $qIso $TEMP_ISO_MNT 2>&1 || mount -o loop,ro $qIso $TEMP_ISO_MNT 2>&1)
                        ${'$'}THERM_PFX cp -rf $TEMP_ISO_MNT/. $TEMP_MNT/ 2>&1
                        """.trimIndent(),
                        trackPid = true
                    )
                }
            } finally {
                otgWatchJob?.cancel()
            }
            if (otgYanked) {
                cleanup(deletePartial = true)
                return@withContext ActionResult(false, "USB-C pen drive was unplugged during Windows ISO extraction.", entries = entries)
            }
            if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                cleanup(deletePartial = true)
                return@withContext ActionResult(false, "Build cancelled.", entries = entries)
            }
            if (!extractRes.isSuccess) {
                val errTxt = (extractRes.out + extractRes.err).takeLast(4).joinToString(" ").take(260)
                entries.add(LogEntry(ts, "win-build", "Extraction failed: $errTxt", true))
                cleanup(deletePartial = true)
                return@withContext ActionResult(false, "ISO extraction failed: $errTxt", entries = entries)
            }
            entries.add(LogEntry(ts, "win-build", "Extracted Windows ISO contents into Partition 1"))

            val wimBin = findBundledBinary(context, "libwimlib_imagex.so", listOf("wimlib-imagex"))
            val sourcesDir = Shell.cmd("find $TEMP_MNT -maxdepth 1 -mindepth 1 -type d -iname 'sources' 2>/dev/null | head -n 1").exec().out.firstOrNull().orEmpty().trim().ifBlank { "$TEMP_MNT/sources" }
            val qSourcesDir = shellQuote(sourcesDir)
            val installWimCandidate = Shell.cmd("find $qSourcesDir -maxdepth 1 -type f \\( -iname 'install.wim' -o -iname 'install.esd' -o -iname 'install.swm' \\) 2>/dev/null | head -n 1").exec().out.firstOrNull().orEmpty().trim()
            val bootWimCandidate = Shell.cmd("find $qSourcesDir -maxdepth 1 -type f -iname 'boot.wim' 2>/dev/null | head -n 1").exec().out.firstOrNull().orEmpty().trim()
            if (installWimCandidate.isBlank() && bootWimCandidate.isBlank()) {
                entries.add(LogEntry(ts, "win-build", "Extracted partition is missing sources/install.* and sources/boot.wim (UDF/ISO9660 bridge or invalid Windows ISO)", true))
                cleanup(deletePartial = true)
                return@withContext ActionResult(false, "Extracted ISO is missing Windows installation files (sources/install.* or boot.wim).", entries = entries)
            }

            if (wimBin != null && installWimCandidate.isNotBlank()) {
                val w7Res = BuildThermalHelper.runIsolatedRootScript(
                    context,
                    "winusb_win7efi",
                    WindowsCustomizationEngine.applyWin7UefiFixSnippet(shellQuote(wimBin), shellQuote(installWimCandidate), TEMP_MNT),
                    trackPid = true
                )
                if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                    cleanup(deletePartial = true)
                    return@withContext ActionResult(false, "Build cancelled.", entries = entries)
                }
                w7Res.out.filter { it.startsWith("LOG|") }.forEach { l -> val p = l.split("|", limit = 3); if (p.size == 3) entries.add(LogEntry(ts, p[1], p[2])) }
            }
            if ((config.experience.useCa2023Bootloaders || config.experience.applySkuSiPolicy) && wimBin != null && (installWimCandidate.isNotBlank() || bootWimCandidate.isNotBlank())) {
                onProgress(WindowsBuildProgress("Applying CA 2023 bootloaders...", "Extracting bootmgfw_EX.efi", 0.82f, false))
                val caRes = BuildThermalHelper.runIsolatedRootScript(
                    context,
                    "winusb_ca2023",
                    WindowsCustomizationEngine.applyCa2023BootloadersSnippet(
                        shellQuote(wimBin),
                        if (installWimCandidate.isNotBlank()) shellQuote(installWimCandidate) else "''",
                        TEMP_MNT,
                        if (bootWimCandidate.isNotBlank()) shellQuote(bootWimCandidate) else ""
                    ),
                    trackPid = true
                )
                if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                    cleanup(deletePartial = true)
                    return@withContext ActionResult(false, "Build cancelled.", entries = entries)
                }
                caRes.out.filter { it.startsWith("LOG|") }.forEach { l -> val p = l.split("|", limit = 3); if (p.size == 3) entries.add(LogEntry(ts, p[1], p[2])) }
            }

            // Official Rufus-parity Single-Pass boot.wim + Answer File Injection:
            // - Default Rufus Native Mode: patch LabConfig directly into boot.wim Index 2 SYSTEM hive,
            //   and write OOBE/Specialize tweaks ONLY into sources/$OEM$/$$/Panther/unattend.xml.
            // - Unattended WinPE Mode (pre-selected edition or Skip OOBE): patch LabConfig + inject
            //   /Autounattend.xml into boot.wim Index 2 in ONE wimlib-imagex update call (no double rewrite,
            //   no duplicate Panther/unattend.xml conflict).
            val chntpwBin = findBundledBinary(context, "libchntpw.so", listOf("chntpw", "reged"))
            val wantsUnattendedWinPe = (config.selectedEditionIndex != null)
            val needScripts = config.experience.needsSpecializeScript() || config.winUtil.hasAnyActive()
            val langIniPath = Shell.cmd("find $qSourcesDir -maxdepth 1 -type f -iname 'lang.ini' 2>/dev/null | head -n 1").exec().out.firstOrNull().orEmpty().trim()
            val detectedLang = if (langIniPath.isNotBlank()) {
                Shell.cmd("grep -iE '^[a-z]{2,3}(-[a-z0-9]{2,4}){1,2}[[:space:]]*=' ${shellQuote(langIniPath)} 2>/dev/null | head -n 1 | cut -d= -f1 | tr -d ' \\r\\n'").exec().out.firstOrNull().orEmpty().trim()
            } else ""

            var bootWimLabConfigPatched = false
            var bootWimAutoInjected = false
            val tempFiles = mutableListOf<File>()
            try {
                val wantLabPatch = config.experience.bypassRequirements && wimBin != null && chntpwBin != null
                val tmpLabReg = if (wantLabPatch) {
                    File(context.cacheDir, "pb_labconfig.reg").apply {
                        writeText(WindowsCustomizationEngine.buildLabConfigRegContent(), Charsets.US_ASCII)
                    }.also { tempFiles.add(it) }
                } else null
                val tmpUnattendedXml = if (wantsUnattendedWinPe) {
                    File(context.cacheDir, "Autounattend.xml").apply {
                        writeText(
                            generateAutounattendXml(
                                config,
                                includeWindowsPePass = true,
                                uiLang = detectedLang,
                                hasSpecializeScript = needScripts,
                                includeLabConfigInPe = config.experience.bypassRequirements && !wantLabPatch
                            ),
                            Charsets.UTF_8
                        )
                    }.also { tempFiles.add(it) }
                } else null

                if (wimBin != null && (tmpLabReg != null || tmpUnattendedXml != null)) {
                    val stageDetail = when {
                        tmpLabReg != null && tmpUnattendedXml != null -> "Writing LabConfig hive & Autounattend.xml in single pass"
                        tmpLabReg != null -> "Writing LabConfig bypasses into WinPE SYSTEM hive"
                        else -> "Injecting Autounattend.xml into boot.wim Index 2"
                    }
                    onProgress(WindowsBuildProgress("Patching boot.wim...", stageDetail, 0.85f, false))
                    val patchRes = BuildThermalHelper.runIsolatedRootScript(
                        context,
                        "winusb_bootpatch",
                        WindowsCustomizationEngine.patchBootWimSnippet(
                            qWimBin = shellQuote(wimBin),
                            qSourcesDir = qSourcesDir,
                            qChntpwBin = if (tmpLabReg != null && chntpwBin != null) shellQuote(chntpwBin) else null,
                            qRegFile = tmpLabReg?.let { shellQuote(it.absolutePath) },
                            qXmlFile = tmpUnattendedXml?.let { shellQuote(it.absolutePath) }
                        ),
                        trackPid = true
                    )
                    if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                        cleanup(deletePartial = true)
                        return@withContext ActionResult(false, "Build cancelled.", entries = entries)
                    }
                    patchRes.out.filter { it.startsWith("LOG|") }.forEach { l ->
                        val p = l.split("|", limit = 3)
                        if (p.size == 3) entries.add(LogEntry(ts, p[1], p[2]))
                    }
                    bootWimLabConfigPatched = patchRes.isSuccess && patchRes.out.any { it.trim() == "PATCHED_LAB_OK" }
                    bootWimAutoInjected = patchRes.isSuccess && patchRes.out.any { it.trim() == "PATCHED_AU_OK" }
                }

                // Fallback only if bypassRequirements was requested and offline LabConfig hive patch could not run
                val needLabFallbackXml = config.experience.bypassRequirements && !bootWimLabConfigPatched
                val needWinPeXml = wantsUnattendedWinPe || needLabFallbackXml
                val needPantherXml = !needWinPeXml && (config.experience.hasPostSetupTweaks() || config.winUtil.hasAnyActive())

                if (needLabFallbackXml) {
                    entries.add(LogEntry(ts, "labconfig", "Offline boot.wim LabConfig patch unavailable; falling back to Autounattend.xml", true))
                }

                val oemPanther = "$sourcesDir/\$OEM\$/\$\$/Panther"
                val oemScripts = "$sourcesDir/\$OEM\$/\$\$/Setup/Scripts"
                val oemCustom = "$sourcesDir/\$OEM\$/\$\$/Setup/Custom"
                val cmds = mutableListOf<String>()

                if (needWinPeXml && (!bootWimAutoInjected || needLabFallbackXml)) {
                    val fallbackXml = File(context.cacheDir, "Autounattend_fallback.xml").apply {
                        writeText(
                            generateAutounattendXml(
                                config,
                                includeWindowsPePass = true,
                                uiLang = detectedLang,
                                hasSpecializeScript = needScripts,
                                includeLabConfigInPe = !bootWimLabConfigPatched
                            ),
                            Charsets.UTF_8
                        )
                    }.also { tempFiles.add(it) }
                    var fallbackInBootWim = false
                    if (wimBin != null) {
                        val auRes = BuildThermalHelper.runIsolatedRootScript(
                            context,
                            "winusb_bootau_fb",
                            WindowsCustomizationEngine.patchBootWimSnippet(
                                qWimBin = shellQuote(wimBin),
                                qSourcesDir = qSourcesDir,
                                qXmlFile = shellQuote(fallbackXml.absolutePath)
                            ),
                            trackPid = true
                        )
                        if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                            cleanup(deletePartial = true)
                            return@withContext ActionResult(false, "Build cancelled.", entries = entries)
                        }
                        fallbackInBootWim = auRes.isSuccess && auRes.out.any { it.trim() == "PATCHED_AU_OK" }
                    }
                    bootWimAutoInjected = fallbackInBootWim
                    if (!fallbackInBootWim) {
                        cmds.add("cp -f ${shellQuote(fallbackXml.absolutePath)} $TEMP_MNT/autounattend.xml")
                    }
                } else if (needPantherXml) {
                    onProgress(WindowsBuildProgress("Applying Windows tweaks...", "Writing Panther/unattend.xml (Native Mode)", 0.87f, false))
                    val tmpPantherXml = File(context.cacheDir, "panther_unattend.xml").apply {
                        writeText(
                            generateAutounattendXml(
                                config,
                                includeWindowsPePass = false,
                                uiLang = detectedLang,
                                hasSpecializeScript = needScripts,
                                includeLabConfigInPe = false
                            ),
                            Charsets.UTF_8
                        )
                    }.also { tempFiles.add(it) }
                    cmds.add("mkdir -p ${shellQuote(oemPanther)} && cp -f ${shellQuote(tmpPantherXml.absolutePath)} ${shellQuote("$oemPanther/unattend.xml")}")
                }

                if (needScripts) {
                    val customPath = config.experience.customPostInstallScriptPath.trim()
                    val customFileName = if (customPath.isNotBlank()) customPath.substringAfterLast('/') else ""
                    val tmpSpec = File(context.cacheDir, "PocketBoot_Specialize.cmd").apply {
                        writeText(WindowsCustomizationEngine.buildSpecializeScript(config.experience, config.winUtil), Charsets.UTF_8)
                    }.also { tempFiles.add(it) }
                    val tmpComp = File(context.cacheDir, "SetupComplete.cmd").apply {
                        writeText(WindowsCustomizationEngine.buildSetupCompleteScript(false, customFileName.isNotBlank(), customFileName, config.winUtil), Charsets.UTF_8)
                    }.also { tempFiles.add(it) }
                    cmds.add("mkdir -p ${shellQuote(oemScripts)} && cp -f ${shellQuote(tmpSpec.absolutePath)} ${shellQuote("$oemScripts/PocketBoot_Specialize.cmd")} && cp -f ${shellQuote(tmpComp.absolutePath)} ${shellQuote("$oemScripts/SetupComplete.cmd")}")
                    if (config.winUtil.addUndoScript && config.winUtil.hasAnyActive()) {
                        val tmpUndo = File(context.cacheDir, "Undo_PocketBoot_Tweaks.cmd").apply {
                            writeText(WindowsCustomizationEngine.buildUndoScript(config.experience, config.winUtil), Charsets.UTF_8)
                        }.also { tempFiles.add(it) }
                        cmds.add("cp -f ${shellQuote(tmpUndo.absolutePath)} ${shellQuote("$oemScripts/Undo_PocketBoot_Tweaks.cmd")}")
                    }
                    if (customPath.isNotBlank()) {
                        cmds.add("mkdir -p ${shellQuote(oemCustom)} && cp -f ${shellQuote(UsbGadgetEngine.resolveDirectPath(customPath))} ${shellQuote(oemCustom)}/ 2>/dev/null || true")
                    }
                }

                if (cmds.isNotEmpty()) Shell.cmd(cmds.joinToString(" && ")).exec()

                if (needWinPeXml || needPantherXml || bootWimLabConfigPatched) {
                    val modeDesc = when {
                        needWinPeXml && bootWimAutoInjected -> "boot.wim/Autounattend.xml (Unattended Mode)"
                        needWinPeXml -> "USB-root autounattend.xml (fallback)"
                        needPantherXml -> "Panther/unattend.xml only (Native Mode: Previous Setup & EULA preserved)"
                        else -> "boot.wim LabConfig only (Native Mode)"
                    }
                    entries.add(LogEntry(ts, "win-build", "Applied $modeDesc (${config.arch}, scripts=${if (needScripts) "yes" else "none"}, Exp=${config.experience.activeCount()}, Tweaks=${config.winUtil.activeCount()})"))
                }
            } finally {
                tempFiles.forEach { it.delete() }
            }
            if (config.selectedEditionIndex != null || config.eiCfgUnlock) {
                val eiContent = if (config.eiCfgUnlock && config.selectedEditionIndex == null) {
                    "[EditionID]\r\n\r\n[Channel]\r\nRetail\r\n\r\n[VL]\r\n0\r\n"
                } else {
                    "[Channel]\r\n_Default\r\n[VL]\r\n0\r\n"
                }
                val tmpEi = File(context.cacheDir, "ei.cfg").apply { writeText(eiContent, Charsets.UTF_8) }
                Shell.cmd("mkdir -p $qSourcesDir && find $qSourcesDir -maxdepth 1 -type f -iname 'ei.cfg' -exec rm -f {} + 2>/dev/null || true && cp -f ${shellQuote(tmpEi.absolutePath)} ${shellQuote("$sourcesDir/ei.cfg")}").exec()
                tmpEi.delete()
                entries.add(LogEntry(ts, "win-build", "Wrote OEM-compatible sources/ei.cfg"))
            }

            if (allDriverDirs.isNotEmpty()) {
                onProgress(WindowsBuildProgress("Copying Drivers / Apps...", "${allDriverDirs.size} source(s)", 0.90f, true))
                for (rawDrvDir in allDriverDirs) {
                    val drvSrc = UsbGadgetEngine.resolveDirectPath(rawDrvDir).trimEnd('/')
                    val isPack = rawDrvDir in config.extraDriverPackDirs
                    val subDest = if (isPack) "$TEMP_MNT/Drivers/${DriverPackManager.sanitizePackSlug(drvSrc.substringAfterLast('/'))}" else "$TEMP_MNT/Drivers"
                    val qDrv = shellQuote(drvSrc)
                    val qSub = shellQuote(subDest)
                    val drvRes = BuildThermalHelper.runIsolatedRootScript(
                        context,
                        "winusb_drivers",
                        "set -e\n${BuildThermalHelper.thermalExecPrefix(usbProf)}\nif [ -d $qDrv ]; then mkdir -p $qSub && \$THERM_PFX cp -rf $qDrv/. $qSub/ 2>&1; fi",
                        trackPid = true
                    )
                    if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                        cleanup(deletePartial = true)
                        return@withContext ActionResult(false, "Build cancelled.", entries = entries)
                    }
                    if (drvRes.isSuccess) {
                        entries.add(LogEntry(ts, "win-build", "Injected drivers from $drvSrc into /Drivers"))
                    } else {
                        val drvErr = (drvRes.out + drvRes.err).joinToString(" ").take(140).trim()
                        entries.add(LogEntry(ts, "win-build", "Driver folder copy failed: $drvErr", true))
                        cleanup(deletePartial = true)
                        return@withContext ActionResult(false, "Failed to copy Drivers/Apps folder into Windows USB: ${drvErr.ifBlank { "check folder access & free space" }}", entries = entries)
                    }
                }
            }

            onProgress(
                WindowsBuildProgress(
                    stage = if (isDirectBlock) "Flushing USB write cache — do NOT unplug..." else "Finalizing bootable USB...",
                    detail = if (isDirectBlock) "Syncing NTFS & partition table on $safeName" else "Syncing NTFS & fixing permissions",
                    progress = 0.94f,
                    isIndeterminate = isDirectBlock
                )
            )
            cleanup(deletePartial = false)

            if (effectiveScheme == PartitionScheme.MBR && msSysBin != null) {
                val qMs = shellQuote(msSysBin)
                val mbrRes = Shell.cmd("$qMs -7 -f $qOut 2>&1").exec()
                if (mbrRes.isSuccess) {
                    entries.add(LogEntry(ts, "win-build", "Wrote Windows 7/10/11 MBR bootcode (-7) via ms-sys"))
                }
            }

            val otgDone = verifiedOtg
            if (otgDone != null) {
                if (!OtgUsbFlasher.isOtgDeviceStillConnected(otgDone.devName)) {
                    return@withContext ActionResult(false, "USB-C pen drive was disconnected before final flush completed.", entries = entries)
                }
                OtgUsbFlasher.finalizeOtgDiskClean(otgDone)
                onProgress(WindowsBuildProgress("USB Ready!", "Safe to unplug ${otgDone.displayTitle}", 1f, false))
                entries.add(LogEntry(ts, "win-build", "Flashed Direct Windows USB to ${otgDone.devPath}"))
                ActionResult(
                    success = true,
                    userMessage = "Flashed Direct Windows USB to ${otgDone.displayTitle} (${effectiveScheme.label}, ${if (useNtfs) "NTFS" else "FAT32"}). Safe to unplug USB-C OTG drive.",
                    createdPath = null,
                    entries = entries
                )
            } else {
                UsbGadgetEngine.fixUserStoragePermissions(context, safeDir, isDir = true)
                UsbGadgetEngine.fixUserStoragePermissions(context, outPath, isDir = false)
                val builtOutBytes = Shell.cmd("stat -c '%s' $qOut 2>/dev/null || echo 0").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                if (builtOutBytes > 0L) UsbGadgetEngine.syncMediaStoreFileSize(context, outPath, builtOutBytes)
                IsoInspector.invalidate(outPath)

                onProgress(WindowsBuildProgress("Done!", safeName, 1f, false))
                entries.add(LogEntry(ts, "win-build", "Built Windows USB image: $outPath"))

                ActionResult(
                    success = true,
                    userMessage = "Built $safeName (${effectiveScheme.label}, ${if (useNtfs) "NTFS" else "FAT32"}).",
                    createdPath = outPath,
                    entries = entries
                )
            }
        }.getOrElse { e ->
            cleanup(deletePartial = true)
            entries.add(LogEntry(ts, "win-build", "Build error: ${e.message}", true))
            ActionResult(false, "Build failed: ${e.message ?: "Unexpected error"}", entries = entries)
        }
    }

    private fun writeTablesViaRoot(
        context: Context,
        outImgPath: String,
        scheme: PartitionScheme,
        layout: DiskLayoutSpec,
        part1IsFat32: Boolean,
        uefiNtfsData: ByteArray?
    ): String? {
        val qOut = shellQuote(outImgPath)
        val includeUefiPart = uefiNtfsData != null && uefiNtfsData.isNotEmpty()

        if (scheme == PartitionScheme.MBR) {
            val mbrBytes = DiskPartitioner.buildMbrSector(
                layout = layout,
                part1IsFat32 = part1IsFat32,
                includeUefiPart = includeUefiPart
            )
            val fMbr = File(context.cacheDir, "pb_mbr.bin").apply { writeBytes(mbrBytes) }
            try {
                val res = Shell.cmd("dd if=${shellQuote(fMbr.absolutePath)} of=$qOut bs=512 seek=0 count=1 conv=notrunc 2>&1").exec()
                if (!res.isSuccess) return (res.out + res.err).joinToString(" ")
            } finally {
                fMbr.delete()
            }
        } else {
            val gpt = DiskPartitioner.buildGptBuffers(
                layout = layout,
                includeUefiPart = includeUefiPart
            )
            val fPrimary = File(context.cacheDir, "pb_gpt_prim.bin").apply { writeBytes(gpt.primarySectors) }
            val fBackup = File(context.cacheDir, "pb_gpt_back.bin").apply { writeBytes(gpt.backupSectors) }
            try {
                val res = Shell.cmd(
                    "dd if=${shellQuote(fPrimary.absolutePath)} of=$qOut bs=512 seek=0 count=34 conv=notrunc 2>&1 && " +
                        "dd if=${shellQuote(fBackup.absolutePath)} of=$qOut bs=512 seek=${layout.totalSectors - 33L} count=33 conv=notrunc 2>&1"
                ).exec()
                if (!res.isSuccess) return (res.out + res.err).joinToString(" ")
            } finally {
                fPrimary.delete()
                fBackup.delete()
            }
        }

        if (uefiNtfsData != null && uefiNtfsData.isNotEmpty()) {
            val fUefi = File(context.cacheDir, "pb_uefi_part.bin").apply { writeBytes(uefiNtfsData) }
            try {
                val res = Shell.cmd(
                    "dd if=${shellQuote(fUefi.absolutePath)} of=$qOut bs=512 seek=${layout.part2StartLba} conv=notrunc 2>&1"
                ).exec()
                if (!res.isSuccess) return (res.out + res.err).joinToString(" ")
            } finally {
                fUefi.delete()
            }
        }
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WindowsBuilderSheet(
    isoFile: ImageFile, defaultDir: String, presets: List<AutounattendPreset>,
    systemTweaksPresets: List<SystemTweaksPreset> = emptyList(),
    systemTweaksSearchHistory: List<String> = emptyList(),
    building: Boolean, progress: WindowsBuildProgress,
    onDismiss: () -> Unit, onCancelBuild: () -> Unit,
    onSavePreset: (AutounattendPreset) -> Unit, onDeletePreset: (String) -> Unit,
    onSaveSystemTweaksPreset: (SystemTweaksPreset) -> Unit = {},
    onDeleteSystemTweaksPreset: (String) -> Unit = {},
    onUpdateSearchHistory: (List<String>) -> Unit = {},
    onStartBuild: (WindowsBuildConfig) -> Unit
) {
    val context = LocalContext.current
    val detectedOs = remember(isoFile.name) { WindowsCustomizationEngine.detectWindowsOsKind(isoFile.name) }
    val detectedArch = remember(isoFile.name) { WindowsCustomizationEngine.detectProcessorArchitecture(isoFile.name) }
    val defaultScheme = remember(detectedOs, detectedArch) { if (detectedOs == WindowsOsKind.WIN7 || detectedArch == "x86") PartitionScheme.MBR else PartitionScheme.GPT }
    val baseName = remember(isoFile.name) { isoFile.name.substringBeforeLast('.').take(36) + "-USB.img" }
    var outputName by remember(isoFile.path) { mutableStateOf(baseName) }
    var effectiveOs by remember(isoFile.path, detectedOs) { mutableStateOf(detectedOs) }
    val defaultVolLabel = remember(effectiveOs) { IsoInspector.defaultWindowsVolumeLabel(effectiveOs, isFat32 = false) }
    var volumeLabel by remember(isoFile.path) { mutableStateOf(IsoInspector.defaultWindowsVolumeLabel(detectedOs, isFat32 = false)) }
    var volumeLabelEdited by remember(isoFile.path) { mutableStateOf(false) }
    var scheme by remember(isoFile.path) { mutableStateOf(defaultScheme) }
    var editions by remember(isoFile.path) { mutableStateOf<List<WindowsEditionItem>>(emptyList()) }
    var inspectingEditions by remember(isoFile.path) { mutableStateOf(true) }
    var selectedEditionIndex by remember(isoFile.path) { mutableStateOf<Int?>(null) }
    var experience by remember(isoFile.path) { mutableStateOf(WindowsExperienceConfig.defaultForOs(detectedOs)) }
    var winUtil by remember(isoFile.path) { mutableStateOf(WinUtilTweaksConfig()) }
    var eiCfgUnlock by remember(isoFile.path) { mutableStateOf(false) }
    var extraDriversDir by remember(isoFile.path) { mutableStateOf("") }
    var savedDriverPacks by remember { mutableStateOf<List<DriverPackItem>>(emptyList()) }
    var selectedDriverPackDirs by remember(isoFile.path) { mutableStateOf<Set<String>>(emptySet()) }
    var showDriverPackManager by remember { mutableStateOf(false) }
    var driverPackReloadNonce by remember { mutableIntStateOf(0) }
    var showDriversFolderPicker by remember { mutableStateOf(false) }
    var autoMount by remember { mutableStateOf(true) }

    var driversBytes by remember(isoFile.path) { mutableStateOf(0L) }
    var targetExists by remember(isoFile.path) { mutableStateOf(false) }
    var existingTargetAllocBytes by remember(isoFile.path) { mutableStateOf(0L) }
    val resolvedOutName = remember(outputName, baseName) { UsbGadgetEngine.sanitizeOutputFileName(outputName.ifBlank { baseName }, ".img", "windows-usb") }
    LaunchedEffect(isoFile.path) {
        inspectingEditions = true
        val foundEditions = WindowsImageBuilder.inspectWindowsEditions(context, isoFile.path)
        editions = foundEditions
        if (effectiveOs == WindowsOsKind.UNKNOWN && foundEditions.isNotEmpty()) {
            val inferred = WindowsCustomizationEngine.detectWindowsOsKind(foundEditions.first().name)
            if (inferred != WindowsOsKind.UNKNOWN) {
                effectiveOs = inferred
                if (!volumeLabelEdited) {
                    volumeLabel = IsoInspector.defaultWindowsVolumeLabel(inferred, isFat32 = false)
                }
            }
        }
        inspectingEditions = false
    }
    LaunchedEffect(defaultDir, driverPackReloadNonce) {
        val loaded = DriverPackManager.listPacks(defaultDir)
        savedDriverPacks = loaded
        val validDirs = loaded.map { it.dirPath }.toSet()
        selectedDriverPackDirs = selectedDriverPackDirs.intersect(validDirs)
    }
    LaunchedEffect(extraDriversDir, selectedDriverPackDirs, savedDriverPacks) {
        val packBytes = savedDriverPacks.filter { it.dirPath in selectedDriverPackDirs }.sumOf { it.sizeBytes }
        val manualBytes = if (extraDriversDir.isBlank()) 0L else withContext(Dispatchers.IO) {
            UsbGadgetEngine.queryDirSizeBytes(extraDriversDir)
        }
        driversBytes = packBytes + manualBytes
    }
    LaunchedEffect(defaultDir, resolvedOutName) {
        targetExists = UsbGadgetEngine.fileExistsInDir(defaultDir, resolvedOutName)
        existingTargetAllocBytes = if (targetExists) {
            withContext(Dispatchers.IO) { UsbGadgetEngine.queryExistingFileAllocatedBytes("${defaultDir.trimEnd('/')}/$resolvedOutName") }
        } else 0L
    }
    var rawFreeSpace by remember(defaultDir) { mutableStateOf(0L) }
    LaunchedEffect(defaultDir) {
        rawFreeSpace = withContext(Dispatchers.IO) { UsbGadgetEngine.getFreeSpaceBytes(defaultDir) }
    }
    val estNeeded = isoFile.sizeBytes + driversBytes + (512L * 1024L * 1024L)
    val freeSpace = if (rawFreeSpace > 0L) rawFreeSpace + existingTargetAllocBytes else 0L
    val lowSpace = freeSpace in 1 until estNeeded

    ModalBottomSheet(onDismissRequest = { if (!building) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = PocketColors.surfaceCard) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            PocketSheetHeader(icon = Icons.Rounded.Window, tint = PocketColors.infoBlue, iconBg = PocketColors.infoBlueBg, title = "Windows USB Builder", subtitle = "${effectiveOs.label} ($detectedArch) · ${UsbGadgetEngine.formatBytes(isoFile.sizeBytes)}", subtitleMono = true)
            if (building) {
                Surface(shape = RoundedCornerShape(14.dp), color = PocketColors.surfaceInput, border = BorderStroke(1.dp, PocketColors.accent)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(progress.stage, style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary)
                        if (progress.detail.isNotBlank()) Text(progress.detail, style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        if (progress.isIndeterminate) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)), color = PocketColors.accent, trackColor = PocketColors.surfaceSubtle)
                        else LinearProgressIndicator(progress = { progress.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)), color = PocketColors.accent, trackColor = PocketColors.surfaceSubtle)
                    }
                }
                OutlinedButton(onClick = onCancelBuild, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, PocketColors.danger)) {
                    Icon(Icons.Rounded.Close, null, tint = PocketColors.danger, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("Cancel Build", color = PocketColors.danger, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Partition Scheme", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PartitionScheme.entries.forEach { s ->
                            val selected = scheme == s
                            Surface(modifier = Modifier.weight(1f).bounceClick { scheme = s }, shape = RoundedCornerShape(12.dp), color = if (selected) PocketColors.accentBg else PocketColors.surfaceInput, border = BorderStroke(if (selected) 1.5.dp else 0.75.dp, if (selected) PocketColors.accent else PocketColors.border)) {
                                Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(s.label, style = MaterialTheme.typography.labelLarge, color = if (selected) PocketColors.accentSoft else PocketColors.textPrimary)
                                    Text(s.subtitle, fontSize = 11.sp, color = PocketColors.textSecondary)
                                }
                            }
                        }
                    }
                }
                if (inspectingEditions) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = PocketColors.accent); Text("Inspecting Windows editions...", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                    }
                } else if (editions.size >= 2) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pre-Select Edition (Optional)", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(modifier = Modifier.bounceClick { selectedEditionIndex = null }, shape = RoundedCornerShape(9.dp), color = if (selectedEditionIndex == null) PocketColors.accentBg else PocketColors.surfaceInput, border = BorderStroke(0.75.dp, if (selectedEditionIndex == null) PocketColors.accent else PocketColors.border)) {
                                Text("Ask during Setup", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = if (selectedEditionIndex == null) PocketColors.accentSoft else PocketColors.textSecondary)
                            }
                            editions.forEach { ed ->
                                val sel = selectedEditionIndex == ed.index
                                Surface(modifier = Modifier.bounceClick { selectedEditionIndex = ed.index }, shape = RoundedCornerShape(9.dp), color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput, border = BorderStroke(0.75.dp, if (sel) PocketColors.accent else PocketColors.border)) {
                                    Text(ed.name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary)
                                }
                            }
                        }
                        if (selectedEditionIndex != null) {
                            Text(
                                text = "Pre-selecting an edition injects Autounattend.xml into boot.wim and skips Product Key, Edition & EULA prompts. Keep \"Ask during Setup\" for full official Windows Setup UI.",
                                fontSize = 11.sp,
                                color = PocketColors.textSecondary
                            )
                        }
                    }
                }
                val isPureStock = !experience.hasAnyActive() && !winUtil.hasAnyActive() && selectedEditionIndex == null
                val isUnattendedWinPe = selectedEditionIndex != null
                val isNativeSkipOobe = selectedEditionIndex == null && experience.skipOobe
                val modePillBg = when {
                    isPureStock -> PocketColors.infoBlueBg
                    isUnattendedWinPe -> PocketColors.warningBg
                    else -> PocketColors.successBg
                }
                val modePillFg = when {
                    isPureStock -> PocketColors.infoBlue
                    isUnattendedWinPe -> PocketColors.warning
                    else -> PocketColors.success
                }
                val modePillText = when {
                    isPureStock -> "Pure Stock ISO · 100% untouched Microsoft Setup & OOBE"
                    isUnattendedWinPe -> "Unattended WinPE · Autounattend.xml in boot.wim (skips edition & EULA)"
                    isNativeSkipOobe -> "Native Setup + Unattended OOBE · Previous Setup & Edition kept, OOBE skipped"
                    else -> "Native Setup Mode · Previous Setup, Product Key & EULA preserved"
                }
                Surface(shape = RoundedCornerShape(10.dp), color = modePillBg, border = BorderStroke(0.5.dp, modePillFg.copy(alpha = 0.65f))) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isUnattendedWinPe) Icons.Rounded.WarningAmber else Icons.Rounded.Build, null, tint = modePillFg, modifier = Modifier.size(15.dp))
                        Text(modePillText, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = PocketColors.textPrimary)
                    }
                }
                WindowsTwoPaneCustomizer(
                    osKind = effectiveOs, arch = detectedArch, experience = experience, winUtil = winUtil, presets = presets,
                    systemTweaksPresets = systemTweaksPresets,
                    systemTweaksSearchHistory = systemTweaksSearchHistory,
                    showCa2023Option = true, showCustomScriptOption = true, defaultBrowseDir = defaultDir, selectedEditionIndex = selectedEditionIndex,
                    onExperienceChange = { experience = it }, onWinUtilChange = { winUtil = it },
                    onApplyPreset = { p ->
                        scheme = runCatching { PartitionScheme.valueOf(p.schemeName) }.getOrDefault(scheme)
                        experience = WindowsExperienceConfig(p.bypassRequirements, p.bypassMicrosoftAccount, p.disableDataCollection, p.disableBitLocker, p.qolImprovements, p.disableSponsoredApps, false, p.skipWifiSetup, p.createLocalUser, p.localUsername, "", p.skipOobe, p.useCa2023Bootloaders, p.applySkuSiPolicy)
                        winUtil = WinUtilTweaksConfig.fromJsonString(p.winUtilJson, legacyClassicContextMenu = p.classicContextMenu)
                    },
                    onSavePreset = { n -> onSavePreset(AutounattendPreset(n, scheme.name, experience.bypassRequirements, experience.bypassMicrosoftAccount, experience.disableDataCollection, experience.disableBitLocker, experience.disableSponsoredApps, false, experience.skipWifiSetup, experience.createLocalUser, experience.localUsername, experience.skipOobe, experience.qolImprovements, experience.useCa2023Bootloaders, experience.applySkuSiPolicy, winUtil.toJsonString())) },
                    onDeletePreset = onDeletePreset,
                    onSaveSystemTweaksPreset = onSaveSystemTweaksPreset,
                    onDeleteSystemTweaksPreset = onDeleteSystemTweaksPreset,
                    onUpdateSearchHistory = onUpdateSearchHistory
                )
                Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, if (eiCfgUnlock) PocketColors.accent.copy(alpha = 0.55f) else PocketColors.border)) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { eiCfgUnlock = !eiCfgUnlock }.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Unlock All Editions (ei.cfg)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                            Text("Lets you choose Pro/Edu even on OEM-keyed laptops", fontSize = 11.sp, color = PocketColors.textSecondary)
                        }
                        PocketSwitch(checked = eiCfgUnlock, onCheckedChange = { eiCfgUnlock = it }, activeColor = PocketColors.accent)
                    }
                }
                DriverPackMultiSelectorSection(
                    packs = savedDriverPacks,
                    selectedPackDirs = selectedDriverPackDirs,
                    onTogglePack = { dir ->
                        selectedDriverPackDirs = if (dir in selectedDriverPackDirs) selectedDriverPackDirs - dir else selectedDriverPackDirs + dir
                    },
                    onOpenManager = { showDriverPackManager = true }
                )
                Surface(modifier = Modifier.fillMaxWidth().bounceClick { showDriversFolderPicker = true }, shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.75.dp, if (extraDriversDir.isNotBlank()) PocketColors.accent else PocketColors.border)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = PocketColors.accentSoft, modifier = Modifier.size(19.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Include Driver / Apps Folder (/Drivers)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                            Text(if (extraDriversDir.isNotBlank()) extraDriversDir.replace("/data/media/0", "/sdcard") else "Tap to browse and select a folder (Intel RST, Wi-Fi, etc.)", fontSize = 11.sp, color = if (extraDriversDir.isNotBlank()) PocketColors.accentSoft else PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (extraDriversDir.isNotBlank()) { IconButton(onClick = { extraDriversDir = "" }, modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.Close, "Clear folder", tint = PocketColors.textMuted, modifier = Modifier.size(15.dp)) } }
                        else Text("Browse", style = MaterialTheme.typography.labelSmall, color = PocketColors.accentSoft)
                    }
                }
                OutlinedTextField(value = outputName, onValueChange = { outputName = it }, label = { Text("Output Image Filename (.img)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PocketColors.accent, unfocusedBorderColor = PocketColors.border, focusedTextColor = PocketColors.textPrimary, unfocusedTextColor = PocketColors.textPrimary))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = volumeLabel,
                        onValueChange = { volumeLabelEdited = true; volumeLabel = it.filter { ch -> ch.code >= 0x20 && ch !in "\\/:*?\"<>|" }.take(32) },
                        label = { Text("USB Volume Label (Explorer Name)") },
                        placeholder = { Text(defaultVolLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PocketColors.accent, unfocusedBorderColor = PocketColors.border, focusedTextColor = PocketColors.textPrimary, unfocusedTextColor = PocketColors.textPrimary)
                    )
                    if (volumeLabel.trim().length > IsoInspector.EXPLORER_SINGLE_LINE_SAFE_MAX) {
                        Text(
                            text = "Over ${IsoInspector.EXPLORER_SINGLE_LINE_SAFE_MAX} chars may wrap in Windows Explorer Tiles view and hide free/total space.",
                            fontSize = 11.sp,
                            color = PocketColors.warning
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { autoMount = !autoMount }.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-mount when finished", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                            Text("Mounts immediately as Writable USB (recommended for NTFS)", fontSize = 11.sp, color = PocketColors.textSecondary)
                        }
                        PocketSwitch(checked = autoMount, onCheckedChange = { autoMount = it }, activeColor = PocketColors.accent)
                    }
                }
                if (targetExists) {
                    Surface(shape = RoundedCornerShape(10.dp), color = PocketColors.warningBg, border = BorderStroke(0.5.dp, PocketColors.warning)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.warning, modifier = Modifier.size(16.dp))
                            Text("$resolvedOutName already exists in this folder and will be overwritten.", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                        }
                    }
                }
                if (lowSpace) {
                    Surface(shape = RoundedCornerShape(10.dp), color = PocketColors.warningBg, border = BorderStroke(0.5.dp, PocketColors.warning)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.warning, modifier = Modifier.size(16.dp))
                            Text("Need ~${UsbGadgetEngine.formatBytes(estNeeded)}, have ${UsbGadgetEngine.formatBytes(freeSpace)} free.", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                        }
                    }
                }
                Button(onClick = { onStartBuild(WindowsBuildConfig(isoFile, defaultDir, outputName.trim().ifBlank { baseName }, scheme, selectedEditionIndex, editions.firstOrNull { it.index == selectedEditionIndex }?.name, effectiveOs, detectedArch, experience, winUtil, eiCfgUnlock, extraDriversDir = extraDriversDir, extraDriverPackDirs = selectedDriverPackDirs.toList(), autoMountAfterBuild = autoMount, volumeLabel = volumeLabel.trim().ifBlank { defaultVolLabel })) }, enabled = outputName.trim().isNotEmpty() && !lowSpace, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)) {
                    Text("Build Bootable Windows USB (.img)", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
    if (showDriversFolderPicker) RootFolderPickerSheet("Select Drivers / Apps Folder", "Files inside will be copied to /Drivers on the Windows USB", extraDriversDir.ifBlank { defaultDir }, { showDriversFolderPicker = false }) { extraDriversDir = it; showDriversFolderPicker = false }
    if (showDriverPackManager) {
        DriverPackManagerSheet(
            defaultDir = defaultDir,
            onDismiss = { showDriverPackManager = false },
            onPacksChanged = { driverPackReloadNonce++ },
            onMessage = { msg, _ -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        )
    }
}
