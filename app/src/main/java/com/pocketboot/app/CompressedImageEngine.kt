package com.pocketboot.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CompressedFormat(val ext: String, val label: String) {
    XZ(".xz", "XZ (LZMA2)"),
    GZ(".gz", "GZip"),
    ZST(".zst", "Zstandard")
}

data class CompressedProbeInfo(
    val format: CompressedFormat,
    val compressedBytes: Long,
    val uncompressedBytes: Long,
    val sizeReliable: Boolean,
    val gzipWrappedSuspected: Boolean,
    val defaultOutputName: String,
    val note: String,
    val toolMissingMessage: String? = null
)

data class DecompressConfig(
    val sourceFile: ImageFile,
    val outputDir: String,
    val outputName: String,
    val deleteSourceAfter: Boolean = false,
    val autoMountAfter: Boolean = false
)

object CompressedImageEngine {

    private const val HOST_FLOOR_BYTES = 256L * 1024L * 1024L

    private fun nowTs(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    fun cancelActiveDecompress() {
        BuildThermalHelper.cancelActiveBuildProcess()
    }

    fun detectFormat(fileName: String): CompressedFormat? {
        val low = fileName.trim().lowercase(Locale.US)
        return when {
            low.endsWith(".img.xz") || low.endsWith(".iso.xz") -> CompressedFormat.XZ
            low.endsWith(".img.gz") || low.endsWith(".iso.gz") -> CompressedFormat.GZ
            low.endsWith(".img.zst") || low.endsWith(".iso.zst") -> CompressedFormat.ZST
            else -> null
        }
    }

    fun deriveOutputFileName(fileName: String): String {
        val trimmed = fileName.trim()
        val fmt = detectFormat(trimmed) ?: return trimmed
        val stripped = trimmed.dropLast(fmt.ext.length)
        return stripped.ifBlank { "decompressed.img" }
    }

    fun targetExtensionFor(fileName: String): String {
        val derived = deriveOutputFileName(fileName).lowercase(Locale.US)
        return if (derived.endsWith(".iso")) ".iso" else ".img"
    }

    fun parse7zUncompressedSize(lines: List<String>): Long {
        val sepIdx = lines.indexOfFirst { it.trim().startsWith("----------") }
        val searchLines = if (sepIdx >= 0 && sepIdx + 1 < lines.size) lines.subList(sepIdx + 1, lines.size) else lines
        for (raw in searchLines) {
            val l = raw.trim()
            if (l.startsWith("Size = ", ignoreCase = true)) {
                val v = l.substringAfter("=", "").trim().toLongOrNull()
                if (v != null && v > 0L) return v
            }
        }
        return -1L
    }

    /**
     * Parses uncompressed byte count from `zstd -lv` output, e.g.:
     * `Decompressed Size: 4.00 GiB (4294967296 B)` or `Decompressed Size: 512 B (512 B)`.
     * Returns -1L when zstd stream header omits content size (streaming frame).
     */
    fun parseZstdUncompressedSize(lines: List<String>): Long {
        val parenBytesRe = Regex("""\((\d+)\s*B\)""", RegexOption.IGNORE_CASE)
        for (raw in lines) {
            val l = raw.trim()
            if (l.startsWith("Decompressed Size:", ignoreCase = true)) {
                val m = parenBytesRe.find(l)
                if (m != null) {
                    val v = m.groupValues[1].toLongOrNull()
                    if (v != null && v > 0L) return v
                }
                val direct = Regex("""Decompressed Size:\s*(\d+)\s*B""", RegexOption.IGNORE_CASE).find(l)
                if (direct != null) {
                    val v = direct.groupValues[1].toLongOrNull()
                    if (v != null && v > 0L) return v
                }
            }
        }
        return -1L
    }

    /**
     * Gzip stores uncompressed size in a 32-bit `ISIZE` trailer (modulo 4 GiB).
     * If reported size is smaller than the compressed file itself, a >4 GiB wrap is certain.
     */
    private const val GZIP_ISIZE_MODULO = 4_294_967_296L // 2^32

    fun isGzipWrapSuspected(compressedBytes: Long, reportedUncompressedBytes: Long): Boolean =
        compressedBytes > 0L && reportedUncompressedBytes > 0L && reportedUncompressedBytes < compressedBytes

    /**
     * Validates extracted size against probed header size while accounting for GZip's 32-bit ISIZE wrap
     * (e.g., a 5.0 GiB image inside a 0.9 GiB `.gz` reports ISIZE = 1.0 GiB, which is exact modulo 2^32).
     */
    fun isExtractedSizeValid(format: CompressedFormat, expectedBytes: Long, actualBytes: Long): Boolean {
        if (actualBytes <= 0L) return false
        if (expectedBytes <= 0L) return true
        if (actualBytes == expectedBytes) return true
        return format == CompressedFormat.GZ &&
            actualBytes > expectedBytes &&
            (actualBytes - expectedBytes) % GZIP_ISIZE_MODULO == 0L
    }

    fun findZstdBinary(context: Context): String? =
        WindowsImageBuilder.findBundledBinary(
            context,
            "libzstd.so",
            listOf("zstd", "/system/bin/zstd", "/data/data/com.termux/files/usr/bin/zstd")
        )

    fun find7zBinary(context: Context): String? =
        WindowsImageBuilder.findBundledBinary(
            context,
            "lib7zzs.so",
            listOf("7zzs", "7zz", "7z", "/data/data/com.termux/files/usr/bin/7z")
        )

    suspend fun probeCompressedImage(context: Context, filePath: String): CompressedProbeInfo? =
        withContext(Dispatchers.IO) {
            val resolved = UsbGadgetEngine.resolveDirectPath(filePath)
            val leaf = resolved.substringAfterLast('/')
            val fmt = detectFormat(leaf) ?: return@withContext null
            val qPath = shellQuote(resolved)
            val compSize = Shell.cmd("stat -c '%s' $qPath 2>/dev/null").exec().out
                .firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            val outName = deriveOutputFileName(leaf)

            var rawUncomp = -1L
            var missingMsg: String? = null
            when (fmt) {
                CompressedFormat.ZST -> {
                    val zstd = findZstdBinary(context)
                    if (zstd != null) {
                        val out = Shell.cmd("${shellQuote(zstd)} -lv $qPath 2>/dev/null").exec().out
                        rawUncomp = parseZstdUncompressedSize(out)
                    } else {
                        missingMsg = "Bundled zstd extractor (libzstd.so) is missing on this build."
                    }
                }
                CompressedFormat.XZ, CompressedFormat.GZ -> {
                    val sevenZip = find7zBinary(context)
                    if (sevenZip != null) {
                        val out = Shell.cmd("${shellQuote(sevenZip)} l -slt $qPath 2>/dev/null").exec().out
                        rawUncomp = parse7zUncompressedSize(out)
                    } else {
                        missingMsg = "Bundled 7-Zip extractor (lib7zzs.so) is missing on this build."
                    }
                }
            }

            val gzWrap = fmt == CompressedFormat.GZ && isGzipWrapSuspected(compSize, rawUncomp)
            val reliable = rawUncomp > 0L && !gzWrap
            val note = when {
                missingMsg != null -> missingMsg
                gzWrap -> "GZip >4 GB detected (32-bit size trailer wrapped). Live storage guard will protect phone free space."
                reliable -> "Exact uncompressed size: ${UsbGadgetEngine.formatBytes(rawUncomp)}"
                fmt == CompressedFormat.ZST -> "Streaming Zstandard image (exact size unknown until extracted). Live storage guard active."
                else -> "Exact uncompressed size unavailable; live 256 MB storage floor guard active."
            }

            CompressedProbeInfo(
                format = fmt,
                compressedBytes = compSize,
                uncompressedBytes = if (reliable) rawUncomp else -1L,
                sizeReliable = reliable,
                gzipWrappedSuspected = gzWrap,
                defaultOutputName = outName,
                note = note,
                toolMissingMessage = missingMsg
            )
        }

    suspend fun decompressImage(
        context: Context,
        config: DecompressConfig,
        onProgress: (WindowsBuildProgress) -> Unit
    ): ActionResult = withContext(Dispatchers.IO) {
        BuildThermalHelper.resetCancelFlag()
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        val srcPath = UsbGadgetEngine.resolveDirectPath(config.sourceFile.path)
        val leaf = srcPath.substringAfterLast('/')
        val fmt = detectFormat(leaf)
            ?: return@withContext ActionResult(false, "Unsupported compressed image format: $leaf", entries = entries)

        val targetExt = targetExtensionFor(leaf)
        val safeDir = UsbGadgetEngine.resolveDirectPath(config.outputDir).trimEnd('/')
        val safeName = UsbGadgetEngine.sanitizeOutputFileName(
            config.outputName.ifBlank { deriveOutputFileName(leaf) },
            targetExt,
            deriveOutputFileName(leaf).substringBeforeLast('.')
        )
        val outPath = "$safeDir/$safeName"
        val tmpPath = "$outPath.pb_decompress_tmp"
        val qSrc = shellQuote(srcPath)
        val qOut = shellQuote(outPath)
        val qTmp = shellQuote(tmpPath)

        if (UsbGadgetEngine.getStatus().mountedLunFor(outPath) != null) {
            return@withContext ActionResult(false, "$safeName is currently mounted to your PC. Unmount it before overwriting.", entries = entries)
        }

        val toolBin = when (fmt) {
            CompressedFormat.ZST -> findZstdBinary(context)
                ?: return@withContext ActionResult(false, "Bundled zstd binary unavailable on this build.", entries = entries)
            CompressedFormat.XZ, CompressedFormat.GZ -> find7zBinary(context)
                ?: return@withContext ActionResult(false, "Bundled 7-Zip extractor unavailable on this build.", entries = entries)
        }
        val qTool = shellQuote(toolBin)

        return@withContext runCatching {
            onProgress(WindowsBuildProgress("Inspecting compressed image...", leaf, 0.04f, true))
            val probe = probeCompressedImage(context, srcPath)
            val compSize = probe?.compressedBytes?.takeIf { it > 0L } ?: config.sourceFile.sizeBytes
            val expectedBytes = if (probe?.sizeReliable == true) probe.uncompressedBytes else -1L

            // Atomic extraction writes to tmpPath while outPath still exists; never credit existingAlloc upfront!
            val rawFree = UsbGadgetEngine.getFreeSpaceBytes(safeDir)
            val minNeeded = if (expectedBytes > 0L) {
                expectedBytes + HOST_FLOOR_BYTES
            } else {
                (compSize * 2L).coerceAtLeast(512L * 1024L * 1024L)
            }
            if (rawFree in 1L until minNeeded) {
                return@withContext ActionResult(
                    false,
                    "Not enough free phone storage. Need at least ${UsbGadgetEngine.formatBytes(minNeeded)}, only ${UsbGadgetEngine.formatBytes(rawFree)} available.",
                    entries = entries
                )
            }

            entries.add(LogEntry(ts, "decompress", "Starting ${fmt.label} extraction: $leaf -> $safeName"))
            Shell.cmd("mkdir -p ${shellQuote(safeDir)} && rm -f $qTmp 2>/dev/null || true").exec()

            val prof = BuildThermalHelper.currentDefaultThermalProfile()
            val extractCmd = when (fmt) {
                CompressedFormat.ZST -> "\$THERM_PFX $qTool -d -f -c $qSrc > $qTmp"
                CompressedFormat.XZ, CompressedFormat.GZ -> "\$THERM_PFX $qTool x -y -so $qSrc > $qTmp"
            }
            val script = """
                set -e
                ${BuildThermalHelper.thermalExecPrefix(prof)}
                $extractCmd
            """.trimIndent()

            var lowSpaceAborted = false
            val pollJob = launch(Dispatchers.IO) {
                while (isActive) {
                    delay(500L)
                    val curBytes = Shell.cmd("stat -c '%s' $qTmp 2>/dev/null").exec().out
                        .firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                    val liveFree = UsbGadgetEngine.getFreeSpaceBytes(safeDir)
                    if (liveFree in 1L until HOST_FLOOR_BYTES) {
                        lowSpaceAborted = true
                        BuildThermalHelper.cancelActiveBuildProcess()
                        break
                    }
                    if (expectedBytes > 0L) {
                        val frac = (curBytes.toFloat() / expectedBytes.toFloat()).coerceIn(0f, 0.98f)
                        onProgress(
                            WindowsBuildProgress(
                                stage = "Decompressing ${fmt.label}...",
                                detail = "${UsbGadgetEngine.formatBytes(curBytes)} / ${UsbGadgetEngine.formatBytes(expectedBytes)}",
                                progress = frac,
                                isIndeterminate = false
                            )
                        )
                    } else {
                        onProgress(
                            WindowsBuildProgress(
                                stage = "Decompressing ${fmt.label}...",
                                detail = "Written ${UsbGadgetEngine.formatBytes(curBytes)} (Free: ${UsbGadgetEngine.formatBytes(liveFree)})",
                                progress = 0.5f,
                                isIndeterminate = true
                            )
                        )
                    }
                }
            }

            val res = try {
                BuildThermalHelper.runIsolatedRootScript(context, "pb_decompress", script, trackPid = true)
            } finally {
                pollJob.cancel()
            }

            if (lowSpaceAborted) {
                Shell.cmd("rm -f $qTmp 2>/dev/null || true").exec()
                return@withContext ActionResult(
                    false,
                    "Stopped decompression to protect phone storage (free space dropped below 256 MB).",
                    entries = entries
                )
            }
            if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                Shell.cmd("rm -f $qTmp 2>/dev/null || true").exec()
                return@withContext ActionResult(false, "Decompression cancelled.", entries = entries)
            }

            val actualSize = Shell.cmd("stat -c '%s' $qTmp 2>/dev/null").exec().out
                .firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            if (!res.isSuccess || !isExtractedSizeValid(fmt, expectedBytes, actualSize)) {
                Shell.cmd("rm -f $qTmp 2>/dev/null || true").exec()
                val errHint = (res.out + res.err).takeLast(2).joinToString(" ").take(140).trim()
                val sizeNote = if (expectedBytes > 0L) " (got ${UsbGadgetEngine.formatBytes(actualSize)} of ${UsbGadgetEngine.formatBytes(expectedBytes)})" else ""
                entries.add(LogEntry(ts, "decompress", "Extraction failed$sizeNote: $errHint", true))
                return@withContext ActionResult(
                    false,
                    "Decompression failed$sizeNote${if (errHint.isNotBlank()) ": $errHint" else "."}",
                    entries = entries
                )
            }

            onProgress(WindowsBuildProgress("Finalizing image...", safeName, 0.96f, false))
            val mvOk = Shell.cmd("mv -f $qTmp $qOut 2>&1").exec().isSuccess
            val finalSize = if (mvOk) {
                Shell.cmd("stat -c '%s' $qOut 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            } else 0L
            if (!mvOk || finalSize != actualSize) {
                Shell.cmd("rm -f $qTmp 2>/dev/null || true").exec()
                return@withContext ActionResult(false, "Could not finalize decompressed image $safeName.", entries = entries)
            }

            if (config.deleteSourceAfter && srcPath != outPath) {
                Shell.cmd("rm -f $qSrc 2>/dev/null || true").exec()
                entries.add(LogEntry(ts, "decompress", "Deleted compressed source $leaf after verified extraction"))
            }

            UsbGadgetEngine.fixUserStoragePermissions(context, safeDir, isDir = true)
            UsbGadgetEngine.fixUserStoragePermissions(context, outPath, isDir = false)
            UsbGadgetEngine.syncMediaStoreFileSize(context, outPath, finalSize)
            IsoInspector.invalidate(outPath)

            entries.add(LogEntry(ts, "decompress", "Extracted $safeName (${UsbGadgetEngine.formatBytes(finalSize)}) cleanly"))
            onProgress(WindowsBuildProgress("Done!", safeName, 1f, false))
            ActionResult(
                success = true,
                userMessage = "Extracted $safeName (${UsbGadgetEngine.formatBytes(finalSize)}).",
                createdPath = outPath,
                entries = entries
            )
        }.getOrElse { e ->
            Shell.cmd("rm -f $qTmp 2>/dev/null || true").exec()
            entries.add(LogEntry(ts, "decompress", "Error: ${e.message}", true))
            ActionResult(false, "Decompression failed: ${e.message ?: "Unexpected error"}", entries = entries)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressedImageSheet(
    sourceFile: ImageFile,
    defaultDir: String,
    building: Boolean,
    progress: WindowsBuildProgress,
    onDismiss: () -> Unit,
    onCancelBuild: () -> Unit,
    onStartDecompress: (DecompressConfig) -> Unit
) {
    val context = LocalContext.current
    val defaultOut = remember(sourceFile.name) { CompressedImageEngine.deriveOutputFileName(sourceFile.name) }
    val targetExt = remember(sourceFile.name) { CompressedImageEngine.targetExtensionFor(sourceFile.name) }
    var probe by remember(sourceFile.path) { mutableStateOf<CompressedProbeInfo?>(null) }
    var probing by remember(sourceFile.path) { mutableStateOf(true) }
    var outputName by remember(sourceFile.path) { mutableStateOf(defaultOut) }
    var deleteSourceAfter by remember(sourceFile.path) { mutableStateOf(false) }
    var autoMountAfter by remember(sourceFile.path) { mutableStateOf(false) }
    var freeSpace by remember(defaultDir) { mutableStateOf(0L) }
    var targetExists by remember(sourceFile.path) { mutableStateOf(false) }

    val resolvedName = remember(outputName, defaultOut, targetExt) {
        UsbGadgetEngine.sanitizeOutputFileName(outputName.ifBlank { defaultOut }, targetExt, defaultOut.substringBeforeLast('.'))
    }

    LaunchedEffect(sourceFile.path) {
        probing = true
        probe = CompressedImageEngine.probeCompressedImage(context, sourceFile.path)
        probing = false
    }

    LaunchedEffect(defaultDir, resolvedName) {
        withContext(Dispatchers.IO) {
            val dir = UsbGadgetEngine.resolveDirectPath(defaultDir).trimEnd('/')
            freeSpace = UsbGadgetEngine.getFreeSpaceBytes(dir)
            targetExists = UsbGadgetEngine.fileExistsInDir(dir, resolvedName)
        }
    }

    val pInfo = probe
    val estNeeded = if (pInfo != null && pInfo.sizeReliable && pInfo.uncompressedBytes > 0L) {
        pInfo.uncompressedBytes + (256L * 1024L * 1024L)
    } else {
        (sourceFile.sizeBytes * 2L).coerceAtLeast(512L * 1024L * 1024L)
    }
    val lowSpace = freeSpace in 1L until estNeeded
    val toolMissing = pInfo?.toolMissingMessage != null

    ModalBottomSheet(
        onDismissRequest = { if (!building) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PocketColors.surfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(PocketColors.infoBlueBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = PocketColors.infoBlue, modifier = Modifier.size(21.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Decompress Image", style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary)
                    Text(
                        "${sourceFile.name} (${UsbGadgetEngine.formatBytes(sourceFile.sizeBytes)})",
                        style = PocketColors.monoStyle,
                        color = PocketColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (building) {
                Surface(shape = RoundedCornerShape(14.dp), color = PocketColors.surfaceInput, border = BorderStroke(1.dp, PocketColors.accent)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(progress.stage, style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary)
                        if (progress.detail.isNotBlank()) {
                            Text(progress.detail, style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                        if (progress.isIndeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)),
                                color = PocketColors.accent,
                                trackColor = PocketColors.surfaceSubtle
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { progress.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)),
                                color = PocketColors.accent,
                                trackColor = PocketColors.surfaceSubtle
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onCancelBuild,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PocketColors.danger)
                ) {
                    Icon(Icons.Rounded.Close, null, tint = PocketColors.danger, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel Extraction", color = PocketColors.danger, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (pInfo?.gzipWrappedSuspected == true) PocketColors.warningBg else PocketColors.surfaceInput,
                    border = BorderStroke(0.5.dp, if (pInfo?.gzipWrappedSuspected == true) PocketColors.warning else PocketColors.border)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        if (probing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PocketColors.accent)
                            Text("Probing archive header...", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        } else {
                            Icon(
                                imageVector = if (pInfo?.gzipWrappedSuspected == true) Icons.Rounded.WarningAmber else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = if (pInfo?.gzipWrappedSuspected == true) PocketColors.warning else PocketColors.infoBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(pInfo?.format?.label ?: "Compressed Archive", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                Text(pInfo?.note ?: "Ready to extract.", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = { Text("Output Filename ($targetExt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PocketColors.accent,
                        unfocusedBorderColor = PocketColors.border,
                        focusedTextColor = PocketColors.textPrimary,
                        unfocusedTextColor = PocketColors.textPrimary
                    )
                )

                Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { deleteSourceAfter = !deleteSourceAfter }.padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Delete compressed archive after success", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                Text("Frees ${UsbGadgetEngine.formatBytes(sourceFile.sizeBytes)} once extraction is verified", fontSize = 11.sp, color = PocketColors.textSecondary)
                            }
                            PocketSwitch(checked = deleteSourceAfter, onCheckedChange = { deleteSourceAfter = it }, activeColor = PocketColors.accent)
                        }
                        PocketHairlineDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { autoMountAfter = !autoMountAfter }.padding(horizontal = 13.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-mount to PC when finished", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                Text("Mounts extracted image on LUN 0 automatically", fontSize = 11.sp, color = PocketColors.textSecondary)
                            }
                            PocketSwitch(checked = autoMountAfter, onCheckedChange = { autoMountAfter = it }, activeColor = PocketColors.accent)
                        }
                    }
                }

                if (targetExists) {
                    Surface(shape = RoundedCornerShape(10.dp), color = PocketColors.warningBg, border = BorderStroke(0.5.dp, PocketColors.warning)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.warning, modifier = Modifier.size(16.dp))
                            Text("$resolvedName already exists and will be atomically replaced after verified extraction.", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                        }
                    }
                }

                if (lowSpace) {
                    Surface(shape = RoundedCornerShape(10.dp), color = PocketColors.dangerBg, border = BorderStroke(0.75.dp, PocketColors.danger)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.danger, modifier = Modifier.size(16.dp))
                            Text("Need at least ${UsbGadgetEngine.formatBytes(estNeeded)}, have ${UsbGadgetEngine.formatBytes(freeSpace)} free.", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                        }
                    }
                }

                Button(
                    onClick = {
                        onStartDecompress(
                            DecompressConfig(
                                sourceFile = sourceFile,
                                outputDir = defaultDir,
                                outputName = resolvedName,
                                deleteSourceAfter = deleteSourceAfter,
                                autoMountAfter = autoMountAfter
                            )
                        )
                    },
                    enabled = !probing && resolvedName.isNotBlank() && !lowSpace && !toolMissing,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Text("Extract to $targetExt", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
