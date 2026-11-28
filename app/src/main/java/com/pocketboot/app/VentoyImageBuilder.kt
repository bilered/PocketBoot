package com.pocketboot.app

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.SystemUpdateAlt
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

enum class VentoyFilesystem(val label: String, val subtitle: String, val badge: String? = null) {
    AUTO("Auto", "Fast exFAT when supported, else universal NTFS", "Recommended"),
    NTFS("NTFS", "Universal via bundled FUSE"),
    EXFAT("exFAT", "Fast native kernel format")
}

private enum class MultiBootSizeUnit(val label: String) {
    MB("MB"),
    GB("GB")
}

data class VentoyAssetInfo(
    val version: String,
    val isUpdatedInApp: Boolean,
    val ready: Boolean
)

data class VentoyBootBundle(
    val bootImg: File,
    val coreImgXz: File,
    val diskImgXz: File,
    val version: String
)

data class VentoyDiskInfo(
    val scheme: PartitionScheme,
    val part1OffsetBytes: Long,
    val part1SizeBytes: Long,
    val part2StartLba: Long,
    val part2Sectors: Long,
    val totalBytes: Long
)

data class VentoyIsoItem(
    val name: String,
    val relativePath: String,
    val sizeBytes: Long
)

data class VentoyDriveContents(
    val info: VentoyDiskInfo,
    val fsType: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val files: List<VentoyIsoItem>,
    val win11BypassCheck: Boolean,
    val win11BypassNro: Boolean,
    val savedExperience: WindowsExperienceConfig = WindowsExperienceConfig(bypassRequirements = win11BypassCheck, bypassMicrosoftAccount = win11BypassNro),
    val savedWinUtil: WinUtilTweaksConfig = WinUtilTweaksConfig(),
    val hasDriversZip: Boolean = false,
    val excludedAutoInstallIsos: Set<String> = emptySet(),
    val isoProfileIds: Map<String, List<String>> = emptyMap(),
    val savedCustomProfiles: Map<String, String> = emptyMap(),
    val menuConfig: MultiBootMenuConfig = MultiBootMenuConfig(),
    val persistenceEntries: List<MultiBootPersistenceEntry> = emptyList()
)

private data class PocketBootDriveMeta(
    val exists: Boolean,
    val experience: WindowsExperienceConfig,
    val winUtil: WinUtilTweaksConfig,
    val hasDriversZip: Boolean,
    val excludedAutoInstallIsos: Set<String> = emptySet(),
    val isoProfileIds: Map<String, List<String>> = emptyMap(),
    val savedCustomProfiles: Map<String, String> = emptyMap()
)

data class VentoyBuildConfig(
    val outputDir: String,
    val outputName: String,
    val sizeMb: Int = 8192,
    val scheme: PartitionScheme = PartitionScheme.GPT,
    val filesystem: VentoyFilesystem = VentoyFilesystem.AUTO,
    val win11BypassCheck: Boolean = true,
    val win11BypassNro: Boolean = true,
    val experience: WindowsExperienceConfig = WindowsExperienceConfig(bypassRequirements = win11BypassCheck, bypassMicrosoftAccount = win11BypassNro),
    val winUtil: WinUtilTweaksConfig = WinUtilTweaksConfig(),
    val driversDir: String = "",
    val extraDriverPackDirs: List<String> = emptyList(),
    val initialIsoToAdd: ImageFile? = null,
    val moveInitialIso: Boolean = false,
    val autoMountAfterBuild: Boolean = true,
    val volumeLabel: String = "",
    val directBlockTarget: OtgUsbDevice? = null
)

object VentoyImageBuilder {

    private const val VENTOY_LATEST_API = "https://api.github.com/repos/ventoy/Ventoy/releases/latest"
    internal const val TEMP_VENTOY_MNT = "/data/local/tmp/pb_ventoy_mnt"
    private const val ASSET_DIR = "ventoy"

    private fun nowTs(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    private fun userVentoyDir(context: Context): File = File(context.filesDir, "ventoy")

    fun getInstalledVentoyInfo(context: Context): VentoyAssetInfo {
        val uDir = userVentoyDir(context)
        val uBoot = File(uDir, "boot.img")
        val uCore = File(uDir, "core.img.xz")
        val uDisk = File(uDir, "ventoy.disk.img.xz")
        val uVer = File(uDir, "version.txt")
        if (uBoot.exists() && uCore.exists() && uDisk.exists() && uDisk.length() > 100_000L) {
            val ver = runCatching { uVer.readText().trim() }.getOrDefault("").ifBlank { "Updated" }
            return VentoyAssetInfo(version = ver, isUpdatedInApp = true, ready = true)
        }

        val bundledReady = runCatching {
            val list = context.assets.list(ASSET_DIR)?.toSet().orEmpty()
            "boot.img" in list && "core.img.xz" in list && "ventoy.disk.img.xz" in list
        }.getOrDefault(false)

        if (bundledReady) {
            val ver = runCatching {
                context.assets.open("$ASSET_DIR/version.txt").bufferedReader().use { it.readText().trim() }
            }.getOrDefault("Bundled")
            return VentoyAssetInfo(version = ver, isUpdatedInApp = false, ready = true)
        }

        return VentoyAssetInfo(version = "Not installed", isUpdatedInApp = false, ready = false)
    }

    fun resetVentoyToBundled(context: Context): Boolean {
        return runCatching {
            userVentoyDir(context).deleteRecursively()
        }.getOrDefault(false)
    }

    private fun resolveBootBundle(context: Context): VentoyBootBundle? {
        val uDir = userVentoyDir(context)
        val uBoot = File(uDir, "boot.img")
        val uCore = File(uDir, "core.img.xz")
        val uDisk = File(uDir, "ventoy.disk.img.xz")
        val uVer = File(uDir, "version.txt")
        if (uBoot.exists() && uCore.exists() && uDisk.exists() && uDisk.length() > 100_000L) {
            val ver = runCatching { uVer.readText().trim() }.getOrDefault("Updated")
            return VentoyBootBundle(uBoot, uCore, uDisk, ver)
        }

        val cacheDir = File(context.cacheDir, "ventoy_bundled").apply { mkdirs() }
        val cBoot = File(cacheDir, "boot.img")
        val cCore = File(cacheDir, "core.img.xz")
        val cDisk = File(cacheDir, "ventoy.disk.img.xz")

        return runCatching {
            listOf("boot.img" to cBoot, "core.img.xz" to cCore, "ventoy.disk.img.xz" to cDisk).forEach { (assetName, dest) ->
                context.assets.open("$ASSET_DIR/$assetName").use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
            }
            val ver = runCatching {
                context.assets.open("$ASSET_DIR/version.txt").bufferedReader().use { it.readText().trim() }
            }.getOrDefault("Bundled")
            if (cBoot.exists() && cCore.exists() && cDisk.length() > 100_000L) {
                VentoyBootBundle(cBoot, cCore, cDisk, ver)
            } else null
        }.getOrNull()
    }

    /**
     * Checks GitHub for the latest official Ventoy Linux release, downloads ventoy-X.Y.Z-linux.tar.gz,
     * extracts boot.img, core.img.xz, and ventoy.disk.img.xz into app-private storage,
     * overriding the bundled APK assets.
     */
    suspend fun updateVentoyFromGitHub(
        context: Context,
        onProgress: (WindowsBuildProgress) -> Unit
    ): ActionResult = withContext(Dispatchers.IO) {
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        val dlFile = File(context.cacheDir, "ventoy_release.tar.gz")
        val extractDir = File(context.cacheDir, "ventoy_extract")

        return@withContext try {
            onProgress(WindowsBuildProgress("Checking for updates...", "Querying latest Multi-Boot engine release", 0.08f, true))

            val apiUrl = URL(VENTOY_LATEST_API)
            require(apiUrl.protocol.equals("https", ignoreCase = true)) { "HTTPS is required for Multi-Boot engine updates." }
            val apiConn = (apiUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("User-Agent", "PocketBoot-Android")
                setRequestProperty("Accept", "application/vnd.github+json")
            }
            val jsonText = try {
                val apiCode = apiConn.responseCode
                if (!apiConn.url.protocol.equals("https", ignoreCase = true)) {
                    return@withContext ActionResult(false, "Rejected non-HTTPS redirect from GitHub API.", entries = entries)
                }
                if (apiCode !in 200..299) {
                    return@withContext ActionResult(false, "GitHub API returned HTTP $apiCode.", entries = entries)
                }
                apiConn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                apiConn.disconnect()
            }
            val root = JSONObject(jsonText)
            val tag = root.optString("tag_name", "").removePrefix("v").trim()
            val assets = root.optJSONArray("assets") ?: JSONArray()
            var downloadUrl = ""
            var assetName = ""
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val n = a.optString("name", "")
                if (n.endsWith("-linux.tar.gz")) {
                    downloadUrl = a.optString("browser_download_url", "")
                    assetName = n
                    break
                }
            }
            if (downloadUrl.isBlank() || tag.isBlank()) {
                return@withContext ActionResult(false, "Could not find Multi-Boot engine archive in latest release.", entries = entries)
            }
            if (!AppUpdateChecker.isTrustedReleaseUrl(downloadUrl)) {
                return@withContext ActionResult(false, "Refused untrusted Multi-Boot engine download URL.", entries = entries)
            }
            val parsedDlUrl = URL(downloadUrl)

            entries.add(LogEntry(ts, "multiboot-upd", "Found Multi-Boot engine v$tag ($assetName)"))
            onProgress(WindowsBuildProgress("Downloading Engine v$tag...", assetName, 0.15f, false))

            val dlConn = (parsedDlUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "PocketBoot-Android")
            }
            val maxArchiveBytes = 128L * 1024L * 1024L
            try {
                val dlCode = dlConn.responseCode
                if (!dlConn.url.protocol.equals("https", ignoreCase = true)) {
                    return@withContext ActionResult(false, "Refused insecure redirect while downloading Multi-Boot engine.", entries = entries)
                }
                if (dlCode !in 200..299) {
                    return@withContext ActionResult(false, "Download failed (HTTP $dlCode).", entries = entries)
                }
                val totalLen = dlConn.contentLengthLong
                if (totalLen > maxArchiveBytes) {
                    return@withContext ActionResult(false, "Multi-Boot engine archive exceeds 128 MB safety limit.", entries = entries)
                }
                var downloaded = 0L
                dlFile.delete()
                dlConn.inputStream.use { input ->
                    FileOutputStream(dlFile).use { output ->
                        val buf = ByteArray(65536)
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            downloaded += r
                            if (downloaded > maxArchiveBytes) {
                                throw IllegalStateException("Archive exceeded 128 MB safety limit")
                            }
                            output.write(buf, 0, r)
                            if (totalLen > 0L) {
                                val pct = 0.15f + (downloaded.toFloat() / totalLen.toFloat()) * 0.65f
                                onProgress(
                                    WindowsBuildProgress(
                                        "Downloading Engine v$tag...",
                                        "${UsbGadgetEngine.formatBytes(downloaded)} / ${UsbGadgetEngine.formatBytes(totalLen)}",
                                        pct,
                                        false
                                    )
                                )
                            }
                        }
                    }
                }
                if (downloaded <= 0L || (totalLen > 0L && downloaded != totalLen)) {
                    dlFile.delete()
                    return@withContext ActionResult(false, "Downloaded archive was truncated ($downloaded / $totalLen bytes).", entries = entries)
                }
            } finally {
                dlConn.disconnect()
            }

            onProgress(WindowsBuildProgress("Extracting boot engine...", "Preparing offline boot files", 0.86f, true))
            extractDir.deleteRecursively()
            extractDir.mkdirs()

            val qTar = shellQuote(dlFile.absolutePath)
            val qExt = shellQuote(extractDir.absolutePath)
            val sevenZip = WindowsImageBuilder.findBundledBinary(context, "lib7zzs.so")

            val extCmd = if (sevenZip != null) {
                val q7z = shellQuote(sevenZip)
                "(tar -xzf $qTar -C $qExt 2>/dev/null) || ($q7z x -y -so $qTar 2>/dev/null | $q7z x -y -si -ttar -o$qExt 2>&1)"
            } else {
                "tar -xzf $qTar -C $qExt 2>&1"
            }
            val extRes = Shell.cmd(extCmd).exec()
            if (!extRes.isSuccess) {
                return@withContext ActionResult(false, "Could not extract downloaded Multi-Boot engine archive.", entries = entries)
            }
            val canonicalRoot = extractDir.canonicalPath.trimEnd(File.separatorChar) + File.separator
            fun safeExtractedFile(targetName: String): File? = extractDir.walkTopDown().firstOrNull { f ->
                f.isFile && f.name == targetName &&
                    runCatching { f.canonicalPath.startsWith(canonicalRoot) }.getOrDefault(false)
            }

            val foundBoot = safeExtractedFile("boot.img")
            val foundCore = safeExtractedFile("core.img.xz")
            val foundDisk = safeExtractedFile("ventoy.disk.img.xz")

            if (foundBoot == null || foundCore == null || foundDisk == null || foundDisk.length() < 100_000L) {
                return@withContext ActionResult(false, "Downloaded archive did not contain expected boot engine files.", entries = entries)
            }

            val targetDir = userVentoyDir(context).apply { mkdirs() }
            val stagedFiles = listOf(
                foundBoot to "boot.img",
                foundCore to "core.img.xz",
                foundDisk to "ventoy.disk.img.xz"
            )
            val tmpCopies = mutableListOf<Pair<File, File>>()
            for ((srcF, leaf) in stagedFiles) {
                val tmpDst = File(targetDir, "$leaf.tmp")
                srcF.copyTo(tmpDst, overwrite = true)
                if (tmpDst.length() != srcF.length()) {
                    tmpDst.delete()
                    tmpCopies.forEach { it.first.delete() }
                    return@withContext ActionResult(false, "Failed to stage $leaf cleanly.", entries = entries)
                }
                tmpCopies.add(tmpDst to File(targetDir, leaf))
            }
            for ((tmpDst, finalDst) in tmpCopies) {
                if (!tmpDst.renameTo(finalDst)) {
                    tmpDst.copyTo(finalDst, overwrite = true)
                    tmpDst.delete()
                }
            }
            val tmpVer = File(targetDir, "version.txt.tmp").apply { writeText(tag, Charsets.UTF_8) }
            val finalVer = File(targetDir, "version.txt")
            if (!tmpVer.renameTo(finalVer)) {
                finalVer.writeText(tag, Charsets.UTF_8)
                tmpVer.delete()
            }

            onProgress(WindowsBuildProgress("Updated to Engine v$tag!", "Ready for offline multi-boot builds", 1f, false))
            entries.add(LogEntry(ts, "multiboot-upd", "Installed Multi-Boot engine v$tag in app storage"))
            ActionResult(true, "Updated Multi-Boot engine to v$tag.", entries = entries)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            entries.add(LogEntry(ts, "multiboot-upd", "Update error: ${e.message}", true))
            ActionResult(false, "Multi-Boot engine update failed: ${e.message ?: "Network error"}", entries = entries)
        } finally {
            dlFile.delete()
            extractDir.deleteRecursively()
        }
    }

    /**
     * Quickly probes an .img file to see if it is a genuine Ventoy Multi-Boot disk image
     * (works for both PocketBoot-created and PC Ventoy2Disk-created GPT/MBR images).
     */
    suspend fun probeVentoyImage(imgPath: String): VentoyDiskInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val resolved = UsbGadgetEngine.resolveDirectPath(imgPath)
            val qPath = shellQuote(resolved)
            val totalBytes = Shell.cmd("stat -c '%s' $qPath 2>/dev/null")
                .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: return@withContext null
            if (totalBytes < 64L * 1024L * 1024L) return@withContext null

            val hdrTmp = File("/data/local/tmp/pb_vtoy_probe_${System.nanoTime()}.bin")
            val qHdr = shellQuote(hdrTmp.absolutePath)
            try {
                // Read LBA 0..3 (2048 bytes: MBR + GPT Header + first 8 GPT entries)
                val ok = Shell.cmd("dd if=$qPath of=$qHdr bs=512 count=4 2>/dev/null && chmod 0644 $qHdr").exec().isSuccess
                if (!ok || !hdrTmp.exists() || hdrTmp.length() < 2048L) return@withContext null
                val raw = hdrTmp.readBytes()
                val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)

                // Check for GPT signature "EFI PART" at offset 512
                val isGpt = String(raw, 512, 8, Charsets.US_ASCII) == "EFI PART"
                val scheme: PartitionScheme
                val p1Start: Long
                val p1Sectors: Long
                val p2Start: Long
                val p2Sectors: Long

                if (isGpt) {
                    scheme = PartitionScheme.GPT
                    // Entry 0 at 1024, Entry 1 at 1152
                    val e0First = buf.getLong(1024 + 32)
                    val e0Last = buf.getLong(1024 + 40)
                    val e1First = buf.getLong(1152 + 32)
                    val e1Last = buf.getLong(1152 + 40)
                    if (e0First <= 0L || e0Last <= e0First || e1First <= 0L || e1Last <= e1First) return@withContext null
                    p1Start = e0First
                    p1Sectors = e0Last - e0First + 1L
                    p2Start = e1First
                    p2Sectors = e1Last - e1First + 1L
                } else {
                    scheme = PartitionScheme.MBR
                    val p2Type = raw[462 + 4].toInt() and 0xFF
                    if (p2Type != 0xEF) return@withContext null
                    p1Start = buf.getInt(446 + 8).toLong() and 0xFFFFFFFFL
                    p1Sectors = buf.getInt(446 + 12).toLong() and 0xFFFFFFFFL
                    p2Start = buf.getInt(462 + 8).toLong() and 0xFFFFFFFFL
                    p2Sectors = buf.getInt(462 + 12).toLong() and 0xFFFFFFFFL
                }

                // Ventoy Partition 2 is always 32 MiB (65536 sectors) with FAT16 volume label "VTOYEFI" at byte 43
                if (p2Sectors != DiskPartitioner.VENTOY_EFI_SECTORS) return@withContext null
                val label = Shell.cmd(
                    "dd if=$qPath bs=512 skip=$p2Start count=1 2>/dev/null | dd bs=1 skip=43 count=11 2>/dev/null | tr -cd 'A-Za-z0-9'"
                ).exec().out.firstOrNull().orEmpty().uppercase()

                if (!label.contains("VTOYEFI")) return@withContext null

                VentoyDiskInfo(
                    scheme = scheme,
                    part1OffsetBytes = p1Start * 512L,
                    part1SizeBytes = p1Sectors * 512L,
                    part2StartLba = p2Start,
                    part2Sectors = p2Sectors,
                    totalBytes = totalBytes
                )
            } finally {
                Shell.cmd("rm -f $qHdr 2>/dev/null").exec()
            }
        }.getOrNull()
    }

    private fun decompressXzFile(context: Context, xzFile: File, outFile: File): Boolean {
        val sevenZip = WindowsImageBuilder.findBundledBinary(context, "lib7zzs.so")
        val workDir = File(context.cacheDir, "vtoy_xz_${System.nanoTime()}").apply { mkdirs() }
        return try {
            outFile.delete()
            if (sevenZip != null) {
                val res = Shell.cmd(
                    "${shellQuote(sevenZip)} e -y -o${shellQuote(workDir.absolutePath)} ${shellQuote(xzFile.absolutePath)} 2>&1"
                ).exec()
                val extracted = workDir.listFiles()?.firstOrNull { it.isFile && it.length() > 0L }
                if (res.isSuccess && extracted != null) {
                    extracted.copyTo(outFile, overwrite = true)
                    return true
                }
            }
            val fallback = Shell.cmd("xz -dc ${shellQuote(xzFile.absolutePath)} > ${shellQuote(outFile.absolutePath)} 2>/dev/null").exec()
            fallback.isSuccess && outFile.exists() && outFile.length() > 0L
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun writeVentoyBootStructures(
        context: Context,
        outPath: String,
        scheme: PartitionScheme,
        layout: DiskLayoutSpec,
        bundle: VentoyBootBundle,
        writePartitionTable: Boolean
    ): String? {
        val qOut = shellQuote(outPath)
        val bootBytes = bundle.bootImg.readBytes()
        if (bootBytes.size < 446) return "Invalid Multi-Boot boot.img"

        val coreRaw = File(context.cacheDir, "vtoy_core.img")
        val efiRaw = File(context.cacheDir, "vtoy_efi.img")
        try {
            if (!decompressXzFile(context, bundle.coreImgXz, coreRaw)) {
                return "Failed to decompress Multi-Boot core.img.xz"
            }
            if (!decompressXzFile(context, bundle.diskImgXz, efiRaw)) {
                return "Failed to decompress Multi-Boot EFI disk image"
            }

            if (writePartitionTable) {
                if (scheme == PartitionScheme.GPT) {
                    val gpt = DiskPartitioner.buildVentoyGptBuffers(layout, bootBytes)
                    val fPrim = File(context.cacheDir, "pb_vtoy_prim.bin").apply { writeBytes(gpt.primarySectors) }
                    val fBack = File(context.cacheDir, "pb_vtoy_back.bin").apply { writeBytes(gpt.backupSectors) }
                    try {
                        val res = Shell.cmd(
                            "dd if=${shellQuote(fPrim.absolutePath)} of=$qOut bs=512 seek=0 count=34 conv=notrunc 2>&1 && " +
                                "dd if=${shellQuote(fBack.absolutePath)} of=$qOut bs=512 seek=${layout.totalSectors - 33L} count=33 conv=notrunc 2>&1"
                        ).exec()
                        if (!res.isSuccess) return (res.out + res.err).joinToString(" ").take(200)
                    } finally {
                        fPrim.delete()
                        fBack.delete()
                    }
                } else {
                    val mbr = DiskPartitioner.buildVentoyMbrSector(layout, bootBytes)
                    val fMbr = File(context.cacheDir, "pb_vtoy_mbr.bin").apply { writeBytes(mbr) }
                    try {
                        val res = Shell.cmd("dd if=${shellQuote(fMbr.absolutePath)} of=$qOut bs=512 seek=0 count=1 conv=notrunc 2>&1").exec()
                        if (!res.isSuccess) return (res.out + res.err).joinToString(" ").take(200)
                    } finally {
                        fMbr.delete()
                    }
                }
            } else {
                // Non-destructive bootloader upgrade: patch ONLY bytes 0..445 (+ byte 92=0x22 on GPT), keep partition table intact!
                val boot446 = bootBytes.copyOf(446)
                if (scheme == PartitionScheme.GPT) {
                    boot446[92] = 0x22.toByte()
                }
                val fBoot = File(context.cacheDir, "pb_vtoy_446.bin").apply { writeBytes(boot446) }
                try {
                    val res = Shell.cmd("dd if=${shellQuote(fBoot.absolutePath)} of=$qOut bs=1 seek=0 count=446 conv=notrunc 2>&1").exec()
                    if (!res.isSuccess) return (res.out + res.err).joinToString(" ").take(200)
                } finally {
                    fBoot.delete()
                }
            }

            // Write core.img (LBA 34 + byte 17908=0x23 for GPT; LBA 1 for MBR)
            if (scheme == PartitionScheme.GPT) {
                val coreBytes = coreRaw.readBytes()
                val patchedCore = coreBytes.copyOf(coreBytes.size.coerceAtMost(2014 * 512))
                // Byte 17908 on disk = (34 * 512) + 500 -> offset 500 inside core.img!
                if (patchedCore.size > 500) {
                    patchedCore[500] = 0x23.toByte()
                }
                val fCore = File(context.cacheDir, "pb_vtoy_core_gpt.bin").apply { writeBytes(patchedCore) }
                try {
                    val res = Shell.cmd("dd if=${shellQuote(fCore.absolutePath)} of=$qOut bs=512 seek=34 count=2014 conv=notrunc 2>&1").exec()
                    if (!res.isSuccess) return (res.out + res.err).joinToString(" ").take(200)
                } finally {
                    fCore.delete()
                }
            } else {
                val res = Shell.cmd("dd if=${shellQuote(coreRaw.absolutePath)} of=$qOut bs=512 seek=1 count=2047 conv=notrunc 2>&1").exec()
                if (!res.isSuccess) return (res.out + res.err).joinToString(" ").take(200)
            }

            // Write full 32 MiB VTOYEFI partition at part2StartLba (no pipe short-reads!)
            val efiRes = Shell.cmd(
                "dd if=${shellQuote(efiRaw.absolutePath)} of=$qOut bs=512 seek=${layout.part2StartLba} count=${DiskPartitioner.VENTOY_EFI_SECTORS} conv=notrunc 2>&1"
            ).exec()
            if (!efiRes.isSuccess) return (efiRes.out + efiRes.err).joinToString(" ").take(200)

            return null
        } finally {
            coreRaw.delete()
            efiRaw.delete()
        }
    }

    internal fun attachPartition1Loop(imgPath: String, offsetBytes: Long, sizeBytes: Long): String {
        val qImg = shellQuote(imgPath)
        val cmd = """
            L=${'$'}(losetup -f -s -o $offsetBytes -S $sizeBytes $qImg 2>/dev/null)
            if [ -z "${'$'}L" ]; then
                L=${'$'}(losetup -f --show -o $offsetBytes --sizelimit $sizeBytes $qImg 2>/dev/null)
            fi
            if [ -z "${'$'}L" ]; then
                CAND=${'$'}(losetup -f 2>/dev/null)
                if [ -n "${'$'}CAND" ]; then
                    if losetup -o $offsetBytes -S $sizeBytes "${'$'}CAND" $qImg 2>/dev/null || \
                       losetup -o $offsetBytes --sizelimit $sizeBytes "${'$'}CAND" $qImg 2>/dev/null; then
                        L="${'$'}CAND"
                    fi
                fi
            fi
            echo "${'$'}L"
        """.trimIndent()
        return Shell.cmd(cmd).exec().out.lastOrNull().orEmpty().trim()
    }

    internal fun mountVentoyPartition1(context: Context, loopDev: String): String? {
        val qLoop = shellQuote(loopDev)
        val procFs = Shell.cmd("cat /proc/filesystems 2>/dev/null").exec().out
            .map { it.trim().substringAfterLast('\t').substringAfterLast(' ').lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val ntfs3gBin = WindowsImageBuilder.findBundledBinary(
            context,
            "libntfs_3g.so",
            listOf("ntfs-3g", "/system/bin/ntfs-3g")
        )
        val hasFuse = ("fuse" in procFs || "fuseblk" in procFs) && Shell.cmd("[ -e /dev/fuse ]").exec().isSuccess

        Shell.cmd("mkdir -p $TEMP_VENTOY_MNT && umount $TEMP_VENTOY_MNT 2>/dev/null || true").exec()

        val candidates = mutableListOf<Pair<String, String>>()
        if ("ntfs3" in procFs) candidates.add("NTFS (ntfs3)" to "mount -t ntfs3 -o rw $qLoop $TEMP_VENTOY_MNT 2>&1")
        if ("tntfs" in procFs) candidates.add("NTFS (tntfs)" to "mount -t tntfs -o rw $qLoop $TEMP_VENTOY_MNT 2>&1")
        if ("exfat" in procFs) candidates.add("exFAT" to "mount -t exfat -o rw $qLoop $TEMP_VENTOY_MNT 2>&1")
        if ("sdfat" in procFs) candidates.add("exFAT (sdfat)" to "mount -t sdfat -o rw $qLoop $TEMP_VENTOY_MNT 2>&1")
        if (ntfs3gBin != null && hasFuse) {
            val qN3g = shellQuote(ntfs3gBin)
            candidates.add("NTFS (ntfs-3g FUSE)" to "$qN3g -o rw,big_writes,noatime $qLoop $TEMP_VENTOY_MNT 2>&1")
            candidates.add("NTFS (ntfs-3g FUSE)" to "$qN3g -o rw $qLoop $TEMP_VENTOY_MNT 2>&1")
        }
        candidates.add("FAT32" to "mount -t vfat -o rw $qLoop $TEMP_VENTOY_MNT 2>&1")
        candidates.add("Auto" to "mount -o rw $qLoop $TEMP_VENTOY_MNT 2>&1")

        for ((label, cmd) in candidates) {
            if (Shell.cmd(cmd).exec().isSuccess) return label
        }
        return null
    }

    internal fun unmountVentoyPartition1(loopDev: String) {
        BuildThermalHelper.unmountAndDetachClean(
            mountPoints = listOf(TEMP_VENTOY_MNT),
            loopDevices = if (loopDev.isNotBlank()) listOf(loopDev) else emptyList(),
            removeDirs = true
        )
    }

    /**
     * Android-universal filesystem capacity probe (works across toybox, busybox, and GNU).
     * Returns (totalBytes, availableBytes) or (-1L, -1L) if unavailable.
     * Avoids relying solely on GNU `df -B1` which is unsupported on many Android toybox builds.
     */
    internal fun queryMountSpaceBytes(mountOrDirPath: String): Pair<Long, Long> {
        val qPath = shellQuote(mountOrDirPath)
        val statLine = Shell.cmd("stat -f -c '%b %a %S' $qPath 2>/dev/null").exec().out.firstOrNull().orEmpty().trim()
        val statParts = statLine.split(Regex("\\s+"))
        if (statParts.size >= 3) {
            val blocks = statParts[0].toLongOrNull() ?: -1L
            val avail = statParts[1].toLongOrNull() ?: -1L
            val blkSize = statParts[2].toLongOrNull() ?: -1L
            if (blocks > 0L && avail >= 0L && blkSize > 0L) {
                return (blocks * blkSize) to (avail * blkSize)
            }
        }
        val dfKLine = Shell.cmd("df -k -P $qPath 2>/dev/null | tail -n 1").exec().out.firstOrNull().orEmpty().trim()
        val dfKParts = dfKLine.split(Regex("\\s+"))
        if (dfKParts.size >= 4) {
            val totK = dfKParts[1].toLongOrNull() ?: -1L
            val availK = dfKParts[3].toLongOrNull() ?: -1L
            if (totK > 0L && availK >= 0L) {
                return (totK * 1024L) to (availK * 1024L)
            }
        }
        val dfBLine = Shell.cmd("df -B1 $qPath 2>/dev/null | tail -n 1").exec().out.firstOrNull().orEmpty().trim()
        val dfBParts = dfBLine.split(Regex("\\s+"))
        if (dfBParts.size >= 4) {
            val totB = dfBParts[1].toLongOrNull() ?: -1L
            val availB = dfBParts[3].toLongOrNull() ?: -1L
            if (totB > 0L && availB >= 0L) {
                return totB to availB
            }
        }
        return -1L to -1L
    }

    internal fun queryHostFreeSpaceBytes(dirPath: String): Long {
        val statFsFree = UsbGadgetEngine.getFreeSpaceBytes(dirPath)
        if (statFsFree > 0L) return statFsFree
        val (_, shellFree) = queryMountSpaceBytes(dirPath)
        return shellFree
    }

    fun normalizeVentoyRelPath(relPath: String): String =
        "/" + relPath.trim().trimStart('/')

    fun isWindowsDesktopAutoInstallEligible(fileName: String): Boolean {
        if (!fileName.lowercase().endsWith(".iso")) return false
        if (IsoInspector.quickGuessByName(fileName) != IsoOsType.WINDOWS) return false
        val kind = WindowsCustomizationEngine.detectWindowsOsKind(fileName)
        return kind == WindowsOsKind.WIN11 || kind == WindowsOsKind.WIN10 || kind == WindowsOsKind.UNKNOWN
    }

    private fun findWindowsIsoRelPathsOnMounted(): List<String> {
        val out = Shell.cmd("cd $TEMP_VENTOY_MNT 2>/dev/null && find . -maxdepth 3 -type f -iname '*.iso' ! -path './ventoy/*' 2>/dev/null || true").exec().out
        return out.map { it.trim().removePrefix("./") }
            .filter { rel -> rel.isNotBlank() && IsoInspector.quickGuessByName(rel.substringAfterLast('/')) == IsoOsType.WINDOWS }
            .map { "/$it" }.distinct().sorted()
    }

    private fun packDriversInjectionOnMounted(context: Context, driversDirs: List<String>): Boolean {
        val cleanDirs = driversDirs.map { UsbGadgetEngine.resolveDirectPath(it).trimEnd('/') }
            .filter { it.isNotBlank() }.distinct()
        if (cleanDirs.isEmpty()) return false
        val sevenZip = WindowsImageBuilder.findBundledBinary(context, "lib7zzs.so", listOf("7zzs", "7zz", "7z")) ?: return false
        val stageDir = "/data/local/tmp/pb_vtoy_drv_stage"; val tmpZip = "/data/local/tmp/pb_vtoy_drivers.zip"
        val dstZip = "$TEMP_VENTOY_MNT/ventoy/drivers_inject.zip"
        val tmpBat = File(context.cacheDir, "VentoyAutoRun.bat").apply {
            writeText("@echo off\r\nif exist \"X:\\Drivers\" (for /r \"X:\\Drivers\" %%F in (*.inf) do drvload \"%%~fF\" >nul 2>&1)\r\nexit /b 0\r\n", Charsets.UTF_8)
        }
        val copyBlocks = cleanDirs.mapIndexed { idx, srcDir ->
            val subSlug = DriverPackManager.sanitizePackSlug(srcDir.substringAfterLast('/'))
            val dstSub = if (cleanDirs.size == 1) "$stageDir/Drivers" else "$stageDir/Drivers/${idx + 1}_$subSlug"
            "[ -d ${shellQuote(srcDir)} ] && mkdir -p ${shellQuote(dstSub)} && cp -rf ${shellQuote(srcDir)}/. ${shellQuote(dstSub)}/"
        }.joinToString("\n                    ")
        return try {
            val res = Shell.cmd("""
                (
                    set -e
                    rm -rf $stageDir $tmpZip && mkdir -p $stageDir/Drivers
                    $copyBlocks
                    cp -f ${shellQuote(tmpBat.absolutePath)} $stageDir/VentoyAutoRun.bat
                    cd $stageDir && ${shellQuote(sevenZip)} a -tzip -y $tmpZip . >/dev/null 2>&1
                    SRC_SZ=${'$'}(stat -c '%s' $tmpZip 2>/dev/null || echo 0)
                    [ "${'$'}SRC_SZ" -gt 0 ]
                    mkdir -p $TEMP_VENTOY_MNT/ventoy
                    rm -f $dstZip
                    cp -f $tmpZip $dstZip
                    DST_SZ=${'$'}(stat -c '%s' $dstZip 2>/dev/null || echo 0)
                    if [ "${'$'}DST_SZ" != "${'$'}SRC_SZ" ]; then
                        rm -f $dstZip
                        exit 1
                    fi
                )
            """.trimIndent()).exec()
            if (!res.isSuccess) {
                Shell.cmd("rm -f ${shellQuote(dstZip)} 2>/dev/null || true").exec()
            }
            res.isSuccess
        } finally {
            tmpBat.delete()
            Shell.cmd("rm -rf ${shellQuote(stageDir)} ${shellQuote(tmpZip)} 2>/dev/null || true").exec()
        }
    }

    internal const val PROFILE_ID_DEFAULT = "default"
    internal const val PROFILE_ID_STANDARD = "builtin:standard"
    internal const val PROFILE_ID_MINIMAL = "builtin:minimal"
    internal const val PROFILE_ID_ADVANCED = "builtin:advanced"
    internal const val PROFILE_ID_DESKTOP = "builtin:desktop"
    internal const val PROFILE_ID_LAPTOP = "builtin:laptop"

    internal fun profileSlug(profileId: String): String {
        val clean = profileId.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(36)
        return clean.ifBlank { "default" }
    }

    internal fun profileDisplayLabel(profileId: String): String = when (profileId) {
        PROFILE_ID_DEFAULT -> "Drive Default"
        PROFILE_ID_STANDARD -> "Standard"
        PROFILE_ID_MINIMAL -> "Minimal"
        PROFILE_ID_ADVANCED -> "Advanced"
        PROFILE_ID_DESKTOP -> "Desktop"
        PROFILE_ID_LAPTOP -> "Laptop"
        else -> profileId.removePrefix("custom:").ifBlank { "Custom" }
    }

    internal fun resolveProfileWinUtil(
        profileId: String,
        driveWinUtil: WinUtilTweaksConfig,
        customProfiles: Map<String, String>
    ): WinUtilTweaksConfig = when (profileId) {
        PROFILE_ID_DEFAULT -> driveWinUtil
        PROFILE_ID_STANDARD -> WinUtilTweaksConfig.standardPreset()
        PROFILE_ID_MINIMAL -> WinUtilTweaksConfig.minimalPreset()
        PROFILE_ID_ADVANCED -> WinUtilTweaksConfig.advancedPreset()
        PROFILE_ID_DESKTOP -> WinUtilTweaksConfig.desktopPreset()
        PROFILE_ID_LAPTOP -> WinUtilTweaksConfig.laptopSafePreset()
        else -> customProfiles[profileId]?.let { WinUtilTweaksConfig.fromJsonString(it) } ?: driveWinUtil
    }

    internal fun parseIsoProfileIdsJson(obj: JSONObject?): Map<String, List<String>> {
        if (obj == null) return emptyMap()
        val out = LinkedHashMap<String, List<String>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val arr = obj.optJSONArray(k) ?: continue
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val id = arr.optString(i, "").trim()
                if (id.isNotEmpty() && id !in list) list.add(id)
            }
            if (list.isNotEmpty()) out[normalizeVentoyRelPath(k)] = list
        }
        return out
    }

    internal fun encodeIsoProfileIdsJson(map: Map<String, List<String>>): JSONObject {
        val obj = JSONObject()
        map.entries.sortedBy { it.key.lowercase(Locale.US) }.forEach { (k, v) ->
            val distinct = v.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (distinct.isNotEmpty()) {
                obj.put(normalizeVentoyRelPath(k), JSONArray(distinct))
            }
        }
        return obj
    }

    private fun writeVentoyJsonOnMounted(
        context: Context,
        bypassCheck: Boolean,
        bypassNro: Boolean,
        experience: WindowsExperienceConfig? = null,
        winUtil: WinUtilTweaksConfig? = null,
        driversDir: String? = null,
        excludedAutoInstallIsos: Set<String>? = null,
        isoProfileIds: Map<String, List<String>>? = null,
        savedCustomProfiles: Map<String, String>? = null,
        extraDriverPackDirs: List<String>? = null
    ): Boolean {
        var driversOk = true
        val existingRaw = Shell.cmd("cat $TEMP_VENTOY_MNT/ventoy/ventoy.json 2>/dev/null").exec().out.joinToString("\n")
        val rootObj = runCatching { if (existingRaw.isNotBlank()) JSONObject(existingRaw) else JSONObject() }.getOrDefault(JSONObject())
        VentoyThemeCatalog.mergeBypassControlPreservingTimeout(rootObj, bypassCheck, bypassNro)

        val existingMeta = readPocketBootMetaOnMounted(bypassCheck, bypassNro)
        val winIsos = findWindowsIsoRelPathsOnMounted()
        val winIsoSet = winIsos.toSet()
        val effExp = experience ?: existingMeta.experience.copy(
            bypassRequirements = bypassCheck,
            bypassMicrosoftAccount = bypassNro
        )
        val effWu = winUtil ?: existingMeta.winUtil
        val rawExcluded = excludedAutoInstallIsos ?: existingMeta.excludedAutoInstallIsos
        val effExcluded = rawExcluded.map { normalizeVentoyRelPath(it) }.filter { it in winIsoSet }.toSet()

        val rawProfileMap = isoProfileIds ?: existingMeta.isoProfileIds
        val effProfileMap = rawProfileMap
            .mapKeys { normalizeVentoyRelPath(it.key) }
            .filterKeys { it in winIsoSet && it !in effExcluded }
            .mapValues { it.value.map { id -> id.trim() }.filter { id -> id.isNotEmpty() }.distinct() }
            .filterValues { it.isNotEmpty() }

        val mergedCustomProfiles = LinkedHashMap<String, String>(existingMeta.savedCustomProfiles)
        savedCustomProfiles?.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) mergedCustomProfiles[k] = v }

        val shouldPersistMeta = (experience != null && winUtil != null) ||
            excludedAutoInstallIsos != null ||
            isoProfileIds != null ||
            existingMeta.exists

        val eligibleWinIsos = winIsos.filter { rel ->
            isWindowsDesktopAutoInstallEligible(rel.substringAfterLast('/')) && rel !in effExcluded
        }

        if (shouldPersistMeta) {
            Shell.cmd("rm -f $TEMP_VENTOY_MNT/ventoy/scripts/pocketboot_unattend*.xml $TEMP_VENTOY_MNT/ventoy/scripts/pocketboot_profile_*.xml 2>/dev/null").exec()

            val requiredPairs = linkedSetOf<Pair<String, String>>()
            eligibleWinIsos.forEach { rel ->
                val arch = WindowsCustomizationEngine.detectProcessorArchitecture(rel.substringAfterLast('/'))
                val profiles = effProfileMap[rel].orEmpty().ifEmpty { listOf(PROFILE_ID_DEFAULT) }
                profiles.forEach { pid -> requiredPairs.add(pid to arch) }
            }
            if (requiredPairs.isEmpty() && (effExp.hasAnyActive() || effWu.hasAnyActive())) {
                requiredPairs.add(PROFILE_ID_DEFAULT to "amd64")
            }

            requiredPairs.forEach { (pid, arch) ->
                val pWu = resolveProfileWinUtil(pid, effWu, mergedCustomProfiles)
                if (effExp.hasAnyActive() || pWu.hasAnyActive()) {
                    val fileName = if (pid == PROFILE_ID_DEFAULT) {
                        "pocketboot_unattend_$arch.xml"
                    } else {
                        "pocketboot_profile_${profileSlug(pid)}_$arch.xml"
                    }
                    val tmpXml = File(context.cacheDir, fileName).apply {
                        writeText(WindowsCustomizationEngine.generateVentoySelfContainedXml(effExp, pWu, arch), Charsets.UTF_8)
                    }
                    try {
                        Shell.cmd("mkdir -p ${shellQuote("$TEMP_VENTOY_MNT/ventoy/scripts")} && cp -f ${shellQuote(tmpXml.absolutePath)} ${shellQuote("$TEMP_VENTOY_MNT/ventoy/scripts/$fileName")}").exec()
                    } finally {
                        tmpXml.delete()
                    }
                }
            }

            val customObj = JSONObject()
            mergedCustomProfiles.forEach { (k, v) -> customObj.put(k, v) }
            val metaObj = JSONObject().apply {
                put("bypassRequirements", effExp.bypassRequirements)
                put("bypassMicrosoftAccount", effExp.bypassMicrosoftAccount)
                put("disableDataCollection", effExp.disableDataCollection)
                put("disableBitLocker", effExp.disableBitLocker)
                put("qolImprovements", effExp.qolImprovements)
                put("disableSponsoredApps", effExp.disableSponsoredApps)
                put("classicContextMenu", false)
                put("skipWifiSetup", effExp.skipWifiSetup)
                put("createLocalUser", effExp.createLocalUser)
                put("localUsername", effExp.localUsername)
                put("skipOobe", effExp.skipOobe)
                put("applySkuSiPolicy", effExp.applySkuSiPolicy)
                put("winUtilJson", effWu.toJsonString())
                put("excludedAutoInstallIsos", JSONArray(effExcluded.sorted()))
                put("isoProfileIds", encodeIsoProfileIdsJson(effProfileMap))
                put("savedCustomProfiles", customObj)
            }
            val tmpMeta = File(context.cacheDir, "pb_vtoy_meta.json").apply { writeText(metaObj.toString(), Charsets.UTF_8) }
            try { Shell.cmd("mkdir -p $TEMP_VENTOY_MNT/ventoy && cp -f ${shellQuote(tmpMeta.absolutePath)} $TEMP_VENTOY_MNT/ventoy/pocketboot_meta.json").exec() } finally { tmpMeta.delete() }
        }
        if (driversDir != null || extraDriverPackDirs != null) {
            val combinedDrivers = (extraDriverPackDirs.orEmpty() + listOfNotNull(driversDir))
                .map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (combinedDrivers.isNotEmpty()) {
                driversOk = packDriversInjectionOnMounted(context, combinedDrivers)
            } else {
                Shell.cmd("rm -f $TEMP_VENTOY_MNT/ventoy/drivers_inject.zip 2>/dev/null").exec()
            }
        }
        val hasDrvZip = driversOk && Shell.cmd("[ -s $TEMP_VENTOY_MNT/ventoy/drivers_inject.zip ] && echo 1 || echo 0").exec().out.lastOrNull()?.trim() == "1"
        val autoArr = JSONArray()
        eligibleWinIsos.forEach { rel ->
            val arch = WindowsCustomizationEngine.detectProcessorArchitecture(rel.substringAfterLast('/'))
            val profiles = effProfileMap[rel].orEmpty().ifEmpty { listOf(PROFILE_ID_DEFAULT) }
            val templatePaths = mutableListOf<String>()
            profiles.forEach { pid ->
                val relXml = if (pid == PROFILE_ID_DEFAULT) {
                    "/ventoy/scripts/pocketboot_unattend_$arch.xml"
                } else {
                    "/ventoy/scripts/pocketboot_profile_${profileSlug(pid)}_$arch.xml"
                }
                val exists = Shell.cmd("[ -f ${shellQuote("$TEMP_VENTOY_MNT$relXml")} ] && echo 1 || echo 0").exec().out.lastOrNull()?.trim() == "1"
                if (exists && relXml !in templatePaths) {
                    templatePaths.add(relXml)
                }
            }
            if (templatePaths.isEmpty()) {
                val legacyXml = "/ventoy/scripts/pocketboot_unattend.xml"
                if (Shell.cmd("[ -f ${shellQuote("$TEMP_VENTOY_MNT$legacyXml")} ] && echo 1 || echo 0").exec().out.lastOrNull()?.trim() == "1") {
                    templatePaths.add(legacyXml)
                }
            }
            if (templatePaths.isNotEmpty()) {
                autoArr.put(JSONObject().apply {
                    put("image", rel)
                    put("template", JSONArray(templatePaths))
                    put("autosel", if (templatePaths.size == 1) 1 else 0)
                    put("timeout", if (templatePaths.size == 1) 3 else 10)
                })
            }
        }
        if (autoArr.length() > 0) rootObj.put("auto_install", autoArr) else rootObj.remove("auto_install")
        if (hasDrvZip && winIsos.isNotEmpty()) {
            val injArr = JSONArray()
            winIsos.forEach { rel -> injArr.put(JSONObject().apply { put("image", rel); put("archive", "/ventoy/drivers_inject.zip") }) }
            rootObj.put("injection", injArr)
        } else rootObj.remove("injection")
        val tmpJson = File(context.cacheDir, "ventoy.json").apply { writeText(rootObj.toString(2), Charsets.UTF_8) }
        try { Shell.cmd("mkdir -p $TEMP_VENTOY_MNT/ventoy && cp -f ${shellQuote(tmpJson.absolutePath)} $TEMP_VENTOY_MNT/ventoy/ventoy.json").exec() } finally { tmpJson.delete() }
        return driversOk
    }

    private fun readPocketBootMetaOnMounted(defaultCheck: Boolean, defaultNro: Boolean): PocketBootDriveMeta {
        val hasDrv = Shell.cmd("[ -f $TEMP_VENTOY_MNT/ventoy/drivers_inject.zip ] && echo 1 || echo 0").exec().out.lastOrNull()?.trim() == "1"
        val raw = Shell.cmd("cat $TEMP_VENTOY_MNT/ventoy/pocketboot_meta.json 2>/dev/null").exec().out.joinToString("\n")
        if (raw.isBlank()) {
            return PocketBootDriveMeta(
                exists = false,
                experience = WindowsExperienceConfig.stock().copy(bypassRequirements = defaultCheck, bypassMicrosoftAccount = defaultNro),
                winUtil = WinUtilTweaksConfig(),
                hasDriversZip = hasDrv,
                excludedAutoInstallIsos = emptySet()
            )
        }
        return runCatching {
            val o = JSONObject(raw)
            val exp = WindowsExperienceConfig(
                bypassRequirements = o.optBoolean("bypassRequirements", defaultCheck),
                bypassMicrosoftAccount = o.optBoolean("bypassMicrosoftAccount", defaultNro),
                disableDataCollection = o.optBoolean("disableDataCollection", true),
                disableBitLocker = o.optBoolean("disableBitLocker", true),
                qolImprovements = o.optBoolean("qolImprovements", true),
                disableSponsoredApps = o.optBoolean("disableSponsoredApps", false),
                classicContextMenu = false,
                skipWifiSetup = o.optBoolean("skipWifiSetup", true),
                createLocalUser = o.optBoolean("createLocalUser", false),
                localUsername = o.optString("localUsername", ""),
                skipOobe = o.optBoolean("skipOobe", false),
                applySkuSiPolicy = o.optBoolean("applySkuSiPolicy", false)
            )
            val legacyClassic = o.optBoolean("classicContextMenu", false)
            val excluded = mutableSetOf<String>()
            o.optJSONArray("excludedAutoInstallIsos")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let { excluded.add(normalizeVentoyRelPath(it)) }
                }
            }
            val profileIds = parseIsoProfileIdsJson(o.optJSONObject("isoProfileIds"))
            val customMap = LinkedHashMap<String, String>()
            o.optJSONObject("savedCustomProfiles")?.let { cp ->
                val keys = cp.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = cp.optString(k, "").trim()
                    if (k.isNotBlank() && v.isNotBlank()) customMap[k] = v
                }
            }
            PocketBootDriveMeta(
                exists = true,
                experience = exp,
                winUtil = WinUtilTweaksConfig.fromJsonString(o.optString("winUtilJson", ""), legacyClassicContextMenu = legacyClassic),
                hasDriversZip = hasDrv,
                excludedAutoInstallIsos = excluded,
                isoProfileIds = profileIds,
                savedCustomProfiles = customMap
            )
        }.getOrDefault(
            PocketBootDriveMeta(
                exists = false,
                experience = WindowsExperienceConfig.stock().copy(bypassRequirements = defaultCheck, bypassMicrosoftAccount = defaultNro),
                winUtil = WinUtilTweaksConfig(),
                hasDriversZip = hasDrv,
                excludedAutoInstallIsos = emptySet()
            )
        )
    }

    private fun readVentoyJsonOnMounted(): Pair<Boolean, Boolean> {
        val txt = Shell.cmd("cat $TEMP_VENTOY_MNT/ventoy/ventoy.json 2>/dev/null").exec().out.joinToString("\n")
        if (txt.isBlank()) return true to true
        return runCatching {
            val obj = JSONObject(txt)
            val arr = obj.optJSONArray("control") ?: return true to true
            var check = true
            var nro = true
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                if (item.has("VTOY_WIN11_BYPASS_CHECK")) check = item.optString("VTOY_WIN11_BYPASS_CHECK") != "0"
                if (item.has("VTOY_WIN11_BYPASS_NRO")) nro = item.optString("VTOY_WIN11_BYPASS_NRO") != "0"
            }
            check to nro
        }.getOrDefault(true to true)
    }

    fun cancelActiveBuild() {
        BuildThermalHelper.cancelActiveBuildProcess()
    }

    suspend fun buildVentoyImage(
        context: Context,
        config: VentoyBuildConfig,
        onProgress: (WindowsBuildProgress) -> Unit
    ): ActionResult = withContext(Dispatchers.IO) {
        BuildThermalHelper.resetCancelFlag()
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        val isDirectBlock = config.directBlockTarget != null
        val safeDir = UsbGadgetEngine.resolveDirectPath(config.outputDir).trimEnd('/')
        val safeName = if (isDirectBlock) {
            config.directBlockTarget?.displayTitle ?: "USB-C OTG Drive"
        } else {
            UsbGadgetEngine.sanitizeOutputFileName(config.outputName, ".img", IsoInspector.DEFAULT_MULTIBOOT_FILE_BASE)
        }
        var outPath = if (isDirectBlock) config.directBlockTarget?.devPath.orEmpty() else "$safeDir/$safeName"
        var qOut = shellQuote(outPath)
        val qDir = shellQuote(safeDir)
        var loopDev = ""
        var loopIsLoopNode = true
        var imgAllocated = false
        var verifiedOtg: OtgUsbDevice? = null

        fun cleanupVentoyBuild(deletePartial: Boolean) {
            if (loopDev.isNotEmpty()) {
                BuildThermalHelper.unmountAndDetachClean(
                    mountPoints = listOf(TEMP_VENTOY_MNT),
                    loopDevices = if (loopIsLoopNode) listOf(loopDev) else emptyList(),
                    removeDirs = true
                )
                loopDev = ""
            }
            if (deletePartial && imgAllocated && !isDirectBlock) {
                Shell.cmd("rm -f $qOut 2>/dev/null || true").exec()
            }
        }

        return@withContext runCatching {
            if (!Shell.getShell().isRoot) {
                return@withContext ActionResult(false, "Root access is required.", entries = entries)
            }
            if (!isDirectBlock) {
                config.initialIsoToAdd?.let { initIso ->
                    if (outPath.equals(UsbGadgetEngine.resolveDirectPath(initIso.path), ignoreCase = true)) {
                        return@withContext ActionResult(false, "Output drive cannot overwrite the source ISO.", entries = entries)
                    }
                }
                if (UsbGadgetEngine.getStatus().mountedLunFor(outPath) != null) {
                    return@withContext ActionResult(false, "$safeName is currently mounted to your PC. Unmount it before overwriting.", entries = entries)
                }
            }

            onProgress(WindowsBuildProgress("Checking Multi-Boot engine...", "Loading offline boot files", 0.06f, true))
            val bundle = resolveBootBundle(context)
                ?: return@withContext ActionResult(
                    false,
                    "Multi-Boot engine files missing. Open Settings → Multi-Boot Engine → Update.",
                    entries = entries
                )

            val allDriverDirs = (config.extraDriverPackDirs + listOf(config.driversDir))
                .map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val drvSizeBytes = allDriverDirs.sumOf { UsbGadgetEngine.queryDirSizeBytes(it) }
            val initIsoSizeBytes = config.initialIsoToAdd?.let { initIso ->
                val resolvedInit = UsbGadgetEngine.resolveDirectPath(initIso.path)
                val sz = Shell.cmd("stat -c '%s' ${shellQuote(resolvedInit)} 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: initIso.sizeBytes
                if (sz <= 0L) {
                    return@withContext ActionResult(false, "Source ISO not found or empty: ${initIso.name}", entries = entries)
                }
                sz
            } ?: 0L

            val effectiveScheme: PartitionScheme
            val layout: DiskLayoutSpec
            val sizeLabel: String
            if (isDirectBlock) {
                val reqTarget = config.directBlockTarget
                    ?: return@withContext ActionResult(false, "No physical USB-C OTG drive selected.", entries = entries)
                onProgress(WindowsBuildProgress("Verifying USB-C drive...", reqTarget.displayTitle, 0.10f, true))
                val (prepDev, prepErr) = OtgUsbFlasher.verifyPhysicalOtgDisk(reqTarget)
                if (prepDev == null) {
                    return@withContext ActionResult(false, prepErr ?: "USB-C OTG drive preparation failed.", entries = entries)
                }
                verifiedOtg = prepDev
                outPath = prepDev.devPath
                qOut = shellQuote(outPath)
                effectiveScheme = DiskPartitioner.effectiveSchemeForDisk(config.scheme, prepDev.sizeBytes)
                val minPart1Bytes = (initIsoSizeBytes + drvSizeBytes + (192L * 1024L * 1024L)).coerceAtLeast(128L * 1024L * 1024L)
                layout = DiskPartitioner.calculateFixedVentoyLayout(
                    totalDiskBytes = prepDev.sizeBytes,
                    minPart1Bytes = minPart1Bytes,
                    scheme = effectiveScheme
                ) ?: return@withContext ActionResult(
                    false,
                    "USB pen drive (${UsbGadgetEngine.formatBytes(prepDev.sizeBytes)}) is too small for Multi-Boot + selected ISO/drivers (need at least ${UsbGadgetEngine.formatBytes(minPart1Bytes + 64L * 1024L * 1024L)}).",
                    entries = entries
                )
                sizeLabel = UsbGadgetEngine.formatBytes(prepDev.sizeBytes)
            } else {
                effectiveScheme = config.scheme
                layout = DiskPartitioner.calculateVentoyLayoutMb(config.sizeMb, effectiveScheme)
                sizeLabel = if (config.sizeMb >= 1024) {
                    String.format(Locale.US, "%.1f GB", config.sizeMb / 1024.0).replace(".0 GB", " GB")
                } else {
                    "${config.sizeMb} MB"
                }
                config.initialIsoToAdd?.let { initIso ->
                    val requiredPart1Bytes = initIsoSizeBytes + drvSizeBytes + (192L * 1024L * 1024L)
                    if (layout.part1SizeBytes < requiredPart1Bytes) {
                        val minGb = ((requiredPart1Bytes + (1024L * 1024L * 1024L) - 1L) / (1024L * 1024L * 1024L)).coerceAtLeast(1L)
                        return@withContext ActionResult(
                            false,
                            "Drive size ($sizeLabel) is too small for ${initIso.name} (${UsbGadgetEngine.formatBytes(initIsoSizeBytes)}). Increase Drive Capacity to at least $minGb GB and try again.",
                            entries = entries
                        )
                    }
                    val rawPhoneFreeB = queryHostFreeSpaceBytes(safeDir)
                    val existingOutAllocB = Shell.cmd("stat -c '%b' $qOut 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull()?.times(512L)?.coerceAtLeast(0L) ?: 0L
                    val effectivePhoneFreeB = if (rawPhoneFreeB >= 0L) rawPhoneFreeB + existingOutAllocB else -1L
                    val phoneNeededB = initIsoSizeBytes + drvSizeBytes + (96L * 1024L * 1024L)
                    if (effectivePhoneFreeB in 0L until phoneNeededB) {
                        return@withContext ActionResult(
                            false,
                            "Not enough free phone storage (${UsbGadgetEngine.formatBytes(effectivePhoneFreeB)} available). Need at least ${UsbGadgetEngine.formatBytes(phoneNeededB)} free to copy ${initIso.name}.",
                            entries = entries
                        )
                    }
                }
                if (config.initialIsoToAdd == null && drvSizeBytes > 0L) {
                    val requiredDrvPart1Bytes = drvSizeBytes + (192L * 1024L * 1024L)
                    if (layout.part1SizeBytes < requiredDrvPart1Bytes) {
                        val minGb = ((requiredDrvPart1Bytes + (1024L * 1024L * 1024L) - 1L) / (1024L * 1024L * 1024L)).coerceAtLeast(1L)
                        return@withContext ActionResult(
                            false,
                            "Drive size ($sizeLabel) is too small for the selected Drivers folder (${UsbGadgetEngine.formatBytes(drvSizeBytes)}). Increase Drive Capacity to at least $minGb GB and try again.",
                            entries = entries
                        )
                    }
                    val rawPhoneFreeB = queryHostFreeSpaceBytes(safeDir)
                    val existingOutAllocB = Shell.cmd("stat -c '%b' $qOut 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull()?.times(512L)?.coerceAtLeast(0L) ?: 0L
                    val effectivePhoneFreeB = if (rawPhoneFreeB >= 0L) rawPhoneFreeB + existingOutAllocB else -1L
                    val phoneNeededB = drvSizeBytes + (96L * 1024L * 1024L)
                    if (effectivePhoneFreeB in 0L until phoneNeededB) {
                        return@withContext ActionResult(
                            false,
                            "Not enough free phone storage (${UsbGadgetEngine.formatBytes(effectivePhoneFreeB)} available). Need at least ${UsbGadgetEngine.formatBytes(phoneNeededB)} free for the Drivers folder.",
                            entries = entries
                        )
                    }
                }
            }
            val procFs = Shell.cmd("cat /proc/filesystems 2>/dev/null").exec().out
                .map { it.trim().substringAfterLast('\t').substringAfterLast(' ').lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()

            val exfatFormatter = WindowsImageBuilder.findBundledBinary(
                context,
                "libmkfs_exfat.so",
                listOf("mkfs.exfat", "mkfs.exfat_sec", "mkexfatfs")
            )
            val ntfsFormatter = WindowsImageBuilder.findBundledBinary(
                context,
                "libmkfs_ntfs.so",
                listOf("mkfs.ntfs", "mkntfs")
            )
            val hasKernelExfat = "exfat" in procFs || "sdfat" in procFs
            val useExfat = when (config.filesystem) {
                VentoyFilesystem.AUTO -> exfatFormatter != null && hasKernelExfat
                VentoyFilesystem.EXFAT -> exfatFormatter != null && hasKernelExfat
                VentoyFilesystem.NTFS -> false
            }
            if (!useExfat && ntfsFormatter == null) {
                return@withContext ActionResult(false, "No NTFS or exFAT formatter found.", entries = entries)
            }

            // Step 1: Allocate SPARSE .img (skipped when writing directly to physical OTG drive)
            if (!isDirectBlock) {
                onProgress(
                    WindowsBuildProgress(
                        "Creating $sizeLabel sparse Multi-Boot drive...",
                        "Engine v${bundle.version} (${effectiveScheme.label} · ${if (useExfat) "exFAT" else "NTFS"})",
                        0.16f,
                        false
                    )
                )
                val allocRes = Shell.cmd(
                    "mkdir -p $qDir && rm -f $qOut && (truncate -s ${layout.totalBytes} $qOut 2>/dev/null || dd if=/dev/zero of=$qOut bs=1M count=0 seek=${layout.totalBytes / (1024L * 1024L)} 2>/dev/null)"
                ).exec()
                if (!allocRes.isSuccess) {
                    return@withContext ActionResult(false, "Failed to create sparse image at $outPath.", entries = entries)
                }
                imgAllocated = true
            } else {
                val otgToWipe = verifiedOtg ?: return@withContext ActionResult(false, "USB-C OTG drive unavailable.", entries = entries)
                onProgress(WindowsBuildProgress("Unmounting & wiping USB drive...", otgToWipe.displayTitle, 0.18f, true))
                OtgUsbFlasher.unmountAndWipeOtgDisk(otgToWipe, entries, ts)?.let { err ->
                    return@withContext ActionResult(false, err, entries = entries)
                }
            }

            // Step 2: Write Multi-Boot GPT/MBR partition table + boot.img + core.img + 32 MB EFI
            onProgress(WindowsBuildProgress("Writing Multi-Boot bootloader...", "Preparing 32 MB EFI boot partition (${effectiveScheme.label})", 0.34f, false))
            val writeErr = writeVentoyBootStructures(
                context = context,
                outPath = outPath,
                scheme = effectiveScheme,
                layout = layout,
                bundle = bundle,
                writePartitionTable = true
            )
            if (writeErr != null) {
                cleanupVentoyBuild(deletePartial = true)
                return@withContext ActionResult(false, "Multi-Boot bootloader write failed: $writeErr", entries = entries)
            }
            entries.add(LogEntry(ts, "multiboot", "Wrote Multi-Boot v${bundle.version} ${effectiveScheme.label} + EFI partition at LBA=${layout.part2StartLba}"))

            // Step 3: Attach Partition 1 & format NTFS or exFAT
            onProgress(
                WindowsBuildProgress(
                    "Formatting Partition 1...",
                    if (useExfat) "exFAT" else "NTFS (Universal)",
                    0.56f,
                    false
                )
            )
            val otgForAttach = verifiedOtg
            if (otgForAttach != null) {
                val (attached, isLoop) = OtgUsbFlasher.attachOtgPartitionSlice(otgForAttach, 1, layout.part1OffsetBytes, layout.part1SizeBytes)
                loopDev = attached
                loopIsLoopNode = isLoop
            } else {
                loopDev = attachPartition1Loop(outPath, layout.part1OffsetBytes, layout.part1SizeBytes)
                loopIsLoopNode = true
            }
            if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) {
                cleanupVentoyBuild(deletePartial = true)
                return@withContext ActionResult(false, "Could not attach Multi-Boot Partition 1.", entries = entries)
            }
            val qLoop = shellQuote(loopDev)

            val defaultMbLbl = IsoInspector.defaultMultiBootLabel(useExfat)
            val volLabel = if (useExfat) {
                IsoInspector.sanitizeFatLabel(config.volumeLabel.ifBlank { defaultMbLbl }, defaultMbLbl, uppercase = false)
            } else {
                IsoInspector.sanitizeNtfsLabel(config.volumeLabel.ifBlank { defaultMbLbl }, defaultMbLbl)
            }
            val qVolLbl = shellQuote(volLabel)
            val fmtRes = if (useExfat) {
                val fmtBin = exfatFormatter ?: run {
                    cleanupVentoyBuild(deletePartial = true)
                    return@withContext ActionResult(false, "exFAT formatter unavailable on device.", entries = entries)
                }
                val qFmt = shellQuote(fmtBin)
                Shell.cmd("$qFmt -L $qVolLbl $qLoop 2>&1 || $qFmt -n $qVolLbl $qLoop 2>&1 || $qFmt $qLoop 2>&1").exec()
            } else {
                val fmtBin = ntfsFormatter ?: run {
                    cleanupVentoyBuild(deletePartial = true)
                    return@withContext ActionResult(false, "NTFS formatter unavailable on device.", entries = entries)
                }
                val qFmt = shellQuote(fmtBin)
                Shell.cmd("$qFmt -F -Q -c 4096 -L $qVolLbl -H 255 -S 63 -p ${layout.part1StartLba} $qLoop 2>&1").exec()
            }
            if (!fmtRes.isSuccess) {
                cleanupVentoyBuild(deletePartial = true)
                val err = (fmtRes.out + fmtRes.err).joinToString(" ").take(200)
                return@withContext ActionResult(false, "Multi-Boot format failed: $err", entries = entries)
            }

            // Step 4: Mount Partition 1, write ventoy.json bypasses, optionally copy/move initial ISO
            onProgress(WindowsBuildProgress("Configuring Multi-Boot...", "Writing boot configuration", 0.70f, false))
            val mntType = mountVentoyPartition1(context, loopDev)
            if (mntType == null) {
                cleanupVentoyBuild(deletePartial = true)
                return@withContext ActionResult(false, "Could not mount formatted Partition 1 to configure Multi-Boot drive.", entries = entries)
            }
            config.initialIsoToAdd?.let { iso ->
                val srcPath = UsbGadgetEngine.resolveDirectPath(iso.path)
                val destName = iso.name
                val destPath = "$TEMP_VENTOY_MNT/$destName"
                val srcSize = Shell.cmd("stat -c '%s' ${shellQuote(srcPath)} 2>/dev/null")
                    .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: iso.sizeBytes.coerceAtLeast(1L)
                val (_, partFreeB) = queryMountSpaceBytes(TEMP_VENTOY_MNT)
                val requiredInsideB = srcSize + drvSizeBytes + (32L * 1024L * 1024L)
                if (partFreeB <= 0L || partFreeB < requiredInsideB) {
                    val minGb = ((requiredInsideB + (192L * 1024L * 1024L) + (1024L * 1024L * 1024L) - 1L) / (1024L * 1024L * 1024L)).coerceAtLeast(1L)
                    cleanupVentoyBuild(deletePartial = true)
                    return@withContext ActionResult(
                        false,
                        "Partition 1 only has ${if (partFreeB > 0L) UsbGadgetEngine.formatBytes(partFreeB) else "insufficient"} usable space for $destName (${UsbGadgetEngine.formatBytes(srcSize)}). Increase Drive Capacity to at least $minGb GB and retry.",
                        entries = entries
                    )
                }
                onProgress(
                    WindowsBuildProgress(
                        stage = if (config.moveInitialIso) "Moving ISO into Multi-Boot..." else "Copying ISO into Multi-Boot...",
                        detail = "$destName (${UsbGadgetEngine.formatBytes(srcSize)})",
                        progress = 0.72f,
                        isIndeterminate = false
                    )
                )
                var otgYankedMb = false
                val otgCheckDev = verifiedOtg
                val copyJob = launch(Dispatchers.IO) {
                    while (isActive) {
                        delay(600L)
                        if (otgCheckDev != null && !OtgUsbFlasher.isOtgDeviceStillConnected(otgCheckDev.devName)) {
                            otgYankedMb = true
                            BuildThermalHelper.cancelActiveBuildProcess()
                            break
                        }
                        val cur = Shell.cmd("stat -c '%s' ${shellQuote(destPath)} 2>/dev/null")
                            .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                        val frac = (cur.toFloat() / srcSize.toFloat()).coerceIn(0f, 1f)
                        onProgress(
                            WindowsBuildProgress(
                                stage = if (config.moveInitialIso) "Moving ISO into Multi-Boot..." else "Copying ISO into Multi-Boot...",
                                detail = "$destName • ${UsbGadgetEngine.formatBytes(cur)} / ${UsbGadgetEngine.formatBytes(srcSize)}",
                                progress = 0.72f + frac * 0.24f,
                                isIndeterminate = false
                            )
                        )
                    }
                }
                val vProf = BuildThermalHelper.currentDefaultThermalProfile()
                val cpRes = try {
                    BuildThermalHelper.runIsolatedRootScript(
                        context,
                        "ventoy_cp",
                        "set -e\n${BuildThermalHelper.thermalExecPrefix(vProf)}\n\$THERM_PFX cp -f ${shellQuote(srcPath)} ${shellQuote(destPath)} 2>&1",
                        trackPid = true
                    )
                } finally {
                    copyJob.cancel()
                }
                if (otgYankedMb) {
                    cleanupVentoyBuild(deletePartial = true)
                    return@withContext ActionResult(false, "USB-C pen drive was unplugged while copying $destName.", entries = entries)
                }
                if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                    cleanupVentoyBuild(deletePartial = true)
                    return@withContext ActionResult(false, "Multi-Boot drive build cancelled.", entries = entries)
                }
                val copiedBytes = Shell.cmd("stat -c '%s' ${shellQuote(destPath)} 2>/dev/null")
                    .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                if (!cpRes.isSuccess || copiedBytes != srcSize) {
                    Shell.cmd("rm -f ${shellQuote(destPath)} 2>/dev/null || true").exec()
                    cleanupVentoyBuild(deletePartial = true)
                    val rawErr = (cpRes.out + cpRes.err).joinToString(" ").take(120).trim()
                    entries.add(LogEntry(ts, "multiboot", "Copy failed for $destName ($copiedBytes / $srcSize bytes): $rawErr", true))
                    return@withContext ActionResult(
                        false,
                        "Failed to copy $destName (wrote ${UsbGadgetEngine.formatBytes(copiedBytes)} of ${UsbGadgetEngine.formatBytes(srcSize)}). Check drive capacity and try again.",
                        entries = entries
                    )
                }
                if (config.moveInitialIso) {
                    Shell.cmd("rm -f ${shellQuote(srcPath)} 2>/dev/null").exec()
                }
                entries.add(LogEntry(ts, "multiboot", "${if (config.moveInitialIso) "Moved" else "Copied"} $destName (${UsbGadgetEngine.formatBytes(copiedBytes)} verified) into Multi-Boot drive"))
            }
            val driversPackedOk = writeVentoyJsonOnMounted(context, config.experience.bypassRequirements, config.experience.bypassMicrosoftAccount, config.experience, config.winUtil, config.driversDir, extraDriverPackDirs = config.extraDriverPackDirs)
            if (!driversPackedOk && allDriverDirs.isNotEmpty()) {
                entries.add(LogEntry(ts, "multiboot", "Warning: Drivers folder could not be packed into drivers_inject.zip.", true))
            }
            entries.add(LogEntry(ts, "multiboot", "Mounted Partition 1 via $mntType ($volLabel) and synced Windows boot config & drivers"))

            onProgress(
                WindowsBuildProgress(
                    stage = if (isDirectBlock) "Flushing USB write cache — do NOT unplug..." else "Finalizing Multi-Boot drive...",
                    detail = if (isDirectBlock) "Syncing partition table on $safeName" else "Syncing & fixing storage permissions",
                    progress = 0.96f,
                    isIndeterminate = isDirectBlock
                )
            )
            cleanupVentoyBuild(deletePartial = false)

            val drvWarn = if (!driversPackedOk && allDriverDirs.isNotEmpty()) " (warning: Drivers failed to pack)" else ""
            val otgDone = verifiedOtg
            if (otgDone != null) {
                if (!OtgUsbFlasher.isOtgDeviceStillConnected(otgDone.devName)) {
                    return@withContext ActionResult(false, "USB-C pen drive was disconnected before final flush completed.", entries = entries)
                }
                OtgUsbFlasher.finalizeOtgDiskClean(otgDone)
                onProgress(WindowsBuildProgress("USB Ready!", "Safe to unplug ${otgDone.displayTitle}", 1f, false))
                ActionResult(
                    success = true,
                    userMessage = "Installed Multi-Boot drive on ${otgDone.displayTitle} ($sizeLabel, ${effectiveScheme.label}, ${if (useExfat) "exFAT" else "NTFS"})$drvWarn. Safe to unplug USB-C OTG drive.",
                    createdPath = null,
                    entries = entries
                )
            } else {
                UsbGadgetEngine.fixUserStoragePermissions(context, safeDir, isDir = true)
                UsbGadgetEngine.fixUserStoragePermissions(context, outPath, isDir = false)
                val vOutBytes = Shell.cmd("stat -c '%s' $qOut 2>/dev/null || echo 0").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                if (vOutBytes > 0L) UsbGadgetEngine.syncMediaStoreFileSize(context, outPath, vOutBytes)
                IsoInspector.invalidate(outPath)

                onProgress(WindowsBuildProgress("Done!", safeName, 1f, false))
                ActionResult(
                    success = true,
                    userMessage = "Created Multi-Boot drive: $safeName ($sizeLabel sparse, ${if (useExfat) "exFAT" else "NTFS"})$drvWarn.",
                    createdPath = outPath,
                    entries = entries
                )
            }
        }.getOrElse { e ->
            cleanupVentoyBuild(deletePartial = true)
            entries.add(LogEntry(ts, "multiboot", "Error: ${e.message}", true))
            ActionResult(false, "Multi-Boot build failed: ${e.message ?: "Unexpected error"}", entries = entries)
        }
    }

    suspend fun inspectDriveContents(context: Context, imgPath: String): VentoyDriveContents? = withContext(Dispatchers.IO) {
        val resolved = UsbGadgetEngine.resolveDirectPath(imgPath)
        val info = probeVentoyImage(resolved) ?: return@withContext null
        val loopDev = attachPartition1Loop(resolved, info.part1OffsetBytes, info.part1SizeBytes)
        if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) return@withContext null
        return@withContext try {
            val fsLabel = mountVentoyPartition1(context, loopDev) ?: return@withContext null
            val (queriedTotalB, queriedFreeB) = queryMountSpaceBytes(TEMP_VENTOY_MNT)
            val totalB = if (queriedTotalB > 0L) queriedTotalB else info.part1SizeBytes
            val freeB = if (queriedFreeB >= 0L) queriedFreeB else 0L

            val fileLines = Shell.cmd(
                """
                find $TEMP_VENTOY_MNT -maxdepth 3 -type f \( -iname '*.iso' -o -iname '*.img' -o -iname '*.wim' -o -iname '*.vhd' -o -iname '*.vhdx' \) 2>/dev/null | while IFS= read -r f; do
                    sz=${'$'}(stat -c '%s' "${'$'}f" 2>/dev/null)
                    rel=${'$'}{f#$TEMP_VENTOY_MNT/}
                    echo "${'$'}sz|${'$'}rel"
                done
                """.trimIndent()
            ).exec().out

            val items = fileLines.mapNotNull { line ->
                val sep = line.indexOf('|')
                if (sep <= 0) return@mapNotNull null
                val sz = line.substring(0, sep).trim().toLongOrNull() ?: 0L
                val rel = line.substring(sep + 1).trim()
                if (rel.isEmpty() || rel.startsWith("ventoy/")) return@mapNotNull null
                VentoyIsoItem(name = rel.substringAfterLast('/'), relativePath = rel, sizeBytes = sz)
            }.sortedBy { it.name.lowercase() }

            val (bypassCheck, bypassNro) = readVentoyJsonOnMounted()
            val meta = readPocketBootMetaOnMounted(bypassCheck, bypassNro)
            val rawJson = Shell.cmd("cat $TEMP_VENTOY_MNT/ventoy/ventoy.json 2>/dev/null").exec().out.joinToString("\n")
            val rootJson = runCatching { if (rawJson.isNotBlank()) JSONObject(rawJson) else JSONObject() }.getOrDefault(JSONObject())
            val menuCfg = VentoyThemeCatalog.readMenuConfig(rootJson)
            val persList = VentoyThemeCatalog.readPersistenceEntries(rootJson).map { pe ->
                val datSz = Shell.cmd("stat -c '%s' ${shellQuote("$TEMP_VENTOY_MNT${pe.backendPath}")} 2>/dev/null")
                    .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                pe.copy(sizeBytes = datSz)
            }
            VentoyDriveContents(
                info = info,
                fsType = fsLabel,
                totalBytes = totalB,
                freeBytes = freeB,
                files = items,
                win11BypassCheck = bypassCheck,
                win11BypassNro = bypassNro,
                savedExperience = meta.experience,
                savedWinUtil = meta.winUtil,
                hasDriversZip = meta.hasDriversZip,
                excludedAutoInstallIsos = meta.excludedAutoInstallIsos,
                isoProfileIds = meta.isoProfileIds,
                savedCustomProfiles = meta.savedCustomProfiles,
                menuConfig = menuCfg,
                persistenceEntries = persList
            )
        } finally {
            unmountVentoyPartition1(loopDev)
        }
    }

    internal suspend fun mountedGuardError(resolvedImg: String, actionLabel: String): String? {
        return if (UsbGadgetEngine.getStatus().mountedLunFor(resolvedImg) != null) {
            "${resolvedImg.substringAfterLast('/')} is currently mounted to your PC. Unmount it before $actionLabel."
        } else null
    }

    suspend fun addIsoToVentoy(
        context: Context,
        ventoyImgPath: String,
        sourceIsoPath: String,
        moveFile: Boolean,
        onProgress: (WindowsBuildProgress) -> Unit
    ): ActionResult = withContext(Dispatchers.IO) {
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        val resolvedSrc = UsbGadgetEngine.resolveDirectPath(sourceIsoPath)
        mountedGuardError(resolvedImg, "adding ISOs")?.let { return@withContext ActionResult(false, it, entries = entries) }
        if (moveFile) {
            mountedGuardError(resolvedSrc, "moving it")?.let { return@withContext ActionResult(false, it, entries = entries) }
        }
        val isoName = resolvedSrc.substringAfterLast('/')
        val info = probeVentoyImage(resolvedImg)
            ?: return@withContext ActionResult(false, "Selected file is not a valid Multi-Boot .img drive.", entries = entries)

        val srcSize = Shell.cmd("stat -c '%s' ${shellQuote(resolvedSrc)} 2>/dev/null")
            .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
        if (srcSize <= 0L) {
            return@withContext ActionResult(false, "Source ISO not found or empty.", entries = entries)
        }

        val loopDev = attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
        if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) {
            return@withContext ActionResult(false, "Could not attach Multi-Boot partition.", entries = entries)
        }

        return@withContext try {
            onProgress(WindowsBuildProgress("Opening Multi-Boot drive...", "Mounting Partition 1", 0.05f, true))
            val fsLabel = mountVentoyPartition1(context, loopDev)
                ?: return@withContext ActionResult(false, "Could not mount Multi-Boot Partition 1.", entries = entries)

            val destPath = "$TEMP_VENTOY_MNT/$isoName"
            val existingDestSize = Shell.cmd("stat -c '%s' ${shellQuote(destPath)} 2>/dev/null")
                .exec().out.firstOrNull()?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val (_, rawFreeB) = queryMountSpaceBytes(TEMP_VENTOY_MNT)
            val effectiveFreeB = if (rawFreeB >= 0L) rawFreeB + existingDestSize else -1L
            val neededInsideB = srcSize + (32L * 1024L * 1024L)
            if (effectiveFreeB <= 0L || effectiveFreeB < neededInsideB) {
                return@withContext ActionResult(
                    false,
                    "Not enough free space inside Multi-Boot drive. Need ${UsbGadgetEngine.formatBytes(srcSize)}, only ${if (effectiveFreeB > 0L) UsbGadgetEngine.formatBytes(effectiveFreeB) else "0 B"} available. Use 'Resize Drive' to increase capacity first.",
                    entries = entries
                )
            }
            val imgParent = resolvedImg.substringBeforeLast('/', "/data/media/0/Download")
            val rawPhoneFreeB = queryHostFreeSpaceBytes(imgParent)
            val effectivePhoneFreeB = if (rawPhoneFreeB >= 0L) rawPhoneFreeB + existingDestSize else -1L
            if (effectivePhoneFreeB in 0L until (srcSize + (64L * 1024L * 1024L))) {
                return@withContext ActionResult(
                    false,
                    "Not enough free phone storage (${UsbGadgetEngine.formatBytes(effectivePhoneFreeB)} available) to copy $isoName (${UsbGadgetEngine.formatBytes(srcSize)}).",
                    entries = entries
                )
            }
            val verb = if (moveFile) "Moving" else "Copying"
            val progressJob = launch(Dispatchers.IO) {
                while (isActive) {
                    delay(550L)
                    val cur = Shell.cmd("stat -c '%s' ${shellQuote(destPath)} 2>/dev/null")
                        .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                    val frac = (cur.toFloat() / srcSize.toFloat()).coerceIn(0f, 1f)
                    onProgress(
                        WindowsBuildProgress(
                            stage = "$verb $isoName...",
                            detail = "${UsbGadgetEngine.formatBytes(cur)} / ${UsbGadgetEngine.formatBytes(srcSize)} ($fsLabel)",
                            progress = 0.08f + frac * 0.86f,
                            isIndeterminate = false
                        )
                    )
                }
            }

            val cpRes = Shell.cmd("cp -f ${shellQuote(resolvedSrc)} ${shellQuote(destPath)} 2>&1").exec()
            progressJob.cancel()

            val copiedBytes = Shell.cmd("stat -c '%s' ${shellQuote(destPath)} 2>/dev/null")
                .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            if (!cpRes.isSuccess || copiedBytes != srcSize) {
                Shell.cmd("rm -f ${shellQuote(destPath)} 2>/dev/null").exec()
                val err = (cpRes.out + cpRes.err).joinToString(" ").take(160).trim()
                return@withContext ActionResult(
                    false,
                    "Copy incomplete (wrote ${UsbGadgetEngine.formatBytes(copiedBytes)} of ${UsbGadgetEngine.formatBytes(srcSize)}). Partial file removed — resize drive or check free space.${if (err.isNotBlank()) " ($err)" else ""}",
                    entries = entries
                )
            }

            if (moveFile) {
                Shell.cmd("rm -f ${shellQuote(resolvedSrc)} 2>/dev/null").exec()
            }

            val (curChk, curNro) = readVentoyJsonOnMounted()
            writeVentoyJsonOnMounted(context, curChk, curNro)
            onProgress(WindowsBuildProgress("Syncing Multi-Boot drive...", "Flushing writes safely", 0.97f, false))
            entries.add(LogEntry(ts, "multiboot", "${if (moveFile) "Moved" else "Copied"} $isoName into ${resolvedImg.substringAfterLast('/')}"))
            ActionResult(true, "${if (moveFile) "Moved" else "Added"} $isoName to Multi-Boot drive.", entries = entries)
        } finally {
            unmountVentoyPartition1(loopDev)
        }
    }

    suspend fun removeIsoFromVentoy(context: Context, ventoyImgPath: String, relativePath: String): ActionResult = withContext(Dispatchers.IO) {
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        mountedGuardError(resolvedImg, "removing ISOs")?.let { return@withContext ActionResult(false, it) }
        val info = probeVentoyImage(resolvedImg)
            ?: return@withContext ActionResult(false, "Not a valid Multi-Boot image.")
        val loopDev = attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
        if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) {
            return@withContext ActionResult(false, "Could not attach Multi-Boot partition.")
        }
        return@withContext try {
            mountVentoyPartition1(context, loopDev)
                ?: return@withContext ActionResult(false, "Could not mount Multi-Boot partition.")
            val target = "$TEMP_VENTOY_MNT/${relativePath.trimStart('/')}"
            val res = Shell.cmd("rm -f ${shellQuote(target)} 2>&1").exec()
            if (res.isSuccess) {
                val normRemoved = normalizeVentoyRelPath(relativePath)
                val rawJ = Shell.cmd("cat $TEMP_VENTOY_MNT/ventoy/ventoy.json 2>/dev/null").exec().out.joinToString("\n")
                if (rawJ.isNotBlank()) {
                    runCatching {
                        val rootJ = JSONObject(rawJ)
                        val (_, oldDat) = VentoyThemeCatalog.removePersistenceEntry(rootJ, normRemoved)
                        if (!oldDat.isNullOrBlank()) {
                            Shell.cmd("rm -f ${shellQuote("$TEMP_VENTOY_MNT$oldDat")} 2>/dev/null || true").exec()
                        }
                        val curMenu = VentoyThemeCatalog.readMenuConfig(rootJ)
                        if (normRemoved in curMenu.menuAliases) {
                            VentoyThemeCatalog.applyMenuConfigPreservingAll(
                                rootJ,
                                curMenu.copy(menuAliases = curMenu.menuAliases - normRemoved)
                            )
                        }
                        val tmpClean = File(context.cacheDir, "pb_vtoy_rm_clean.json").apply { writeText(rootJ.toString(2), Charsets.UTF_8) }
                        try {
                            Shell.cmd("cp -f ${shellQuote(tmpClean.absolutePath)} $TEMP_VENTOY_MNT/ventoy/ventoy.json").exec()
                        } finally {
                            tmpClean.delete()
                        }
                    }
                }
                val (curChk, curNro) = readVentoyJsonOnMounted()
                writeVentoyJsonOnMounted(context, curChk, curNro)
            }
            ActionResult(
                success = res.isSuccess,
                userMessage = if (res.isSuccess) "Removed ${relativePath.substringAfterLast('/')} from Multi-Boot drive." else "Could not remove file."
            )
        } finally {
            unmountVentoyPartition1(loopDev)
        }
    }

    suspend fun updateVentoyJson(context: Context, ventoyImgPath: String, bypassCheck: Boolean, bypassNro: Boolean): ActionResult = withContext(Dispatchers.IO) {
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        mountedGuardError(resolvedImg, "updating boot settings")?.let { return@withContext ActionResult(false, it) }
        val info = probeVentoyImage(resolvedImg)
            ?: return@withContext ActionResult(false, "Not a valid Multi-Boot image.")
        val loopDev = attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
        if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) {
            return@withContext ActionResult(false, "Could not attach Multi-Boot partition.")
        }
        return@withContext try {
            mountVentoyPartition1(context, loopDev)
                ?: return@withContext ActionResult(false, "Could not mount Multi-Boot partition.")
            writeVentoyJsonOnMounted(context, bypassCheck, bypassNro)
            ActionResult(true, "Saved Multi-Boot Windows 11 bypass settings.")
        } finally {
            unmountVentoyPartition1(loopDev)
        }
    }

    suspend fun updateVentoyCustomization(
        context: Context,
        ventoyImgPath: String,
        experience: WindowsExperienceConfig,
        winUtil: WinUtilTweaksConfig,
        driversDir: String? = null,
        extraDriverPackDirs: List<String>? = null
    ): ActionResult = withContext(Dispatchers.IO) {
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        mountedGuardError(resolvedImg, "updating tweaks or drivers")?.let { return@withContext ActionResult(false, it) }
        val info = probeVentoyImage(resolvedImg) ?: return@withContext ActionResult(false, "Not a valid Multi-Boot image.")
        val loopDev = attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
        if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) return@withContext ActionResult(false, "Could not attach Multi-Boot partition.")
        return@withContext try {
            mountVentoyPartition1(context, loopDev) ?: return@withContext ActionResult(false, "Could not mount Multi-Boot partition.")
            val driversPackedOk = writeVentoyJsonOnMounted(
                context = context,
                bypassCheck = experience.bypassRequirements,
                bypassNro = experience.bypassMicrosoftAccount,
                experience = experience,
                winUtil = winUtil,
                driversDir = driversDir,
                extraDriverPackDirs = extraDriverPackDirs
            )
            val requestedAnyDrivers = !driversDir.isNullOrBlank() || !extraDriverPackDirs.isNullOrEmpty()
            if (!driversPackedOk && requestedAnyDrivers) {
                ActionResult(false, "Saved tweaks, but failed to pack the selected Drivers (check folder access & free space).")
            } else {
                ActionResult(true, "Saved Windows Experience, System Tweaks & drivers to Multi-Boot drive.")
            }
        } finally { unmountVentoyPartition1(loopDev) }
    }

    suspend fun setIsoAutoInstallEnabled(
        context: Context,
        ventoyImgPath: String,
        relativePath: String,
        enabled: Boolean
    ): ActionResult = withContext(Dispatchers.IO) {
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        mountedGuardError(resolvedImg, "changing per-ISO tweak settings")?.let { return@withContext ActionResult(false, it) }
        val info = probeVentoyImage(resolvedImg) ?: return@withContext ActionResult(false, "Not a valid Multi-Boot image.")
        val loopDev = attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
        if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) return@withContext ActionResult(false, "Could not attach Multi-Boot partition.")
        return@withContext try {
            mountVentoyPartition1(context, loopDev) ?: return@withContext ActionResult(false, "Could not mount Multi-Boot partition.")
            val (curChk, curNro) = readVentoyJsonOnMounted()
            val meta = readPocketBootMetaOnMounted(curChk, curNro)
            val normPath = normalizeVentoyRelPath(relativePath)
            val updatedExcluded = if (enabled) meta.excludedAutoInstallIsos - normPath else meta.excludedAutoInstallIsos + normPath
            writeVentoyJsonOnMounted(
                context = context,
                bypassCheck = curChk,
                bypassNro = curNro,
                experience = meta.experience,
                winUtil = meta.winUtil,
                excludedAutoInstallIsos = updatedExcluded
            )
            val isoName = relativePath.substringAfterLast('/')
            ActionResult(true, if (enabled) "Enabled Windows tweaks for $isoName." else "Set $isoName to Stock (no tweaks).")
        } finally {
            unmountVentoyPartition1(loopDev)
        }
    }

    suspend fun setIsoProfileSelection(
        context: Context,
        ventoyImgPath: String,
        relativePath: String,
        selectedProfileIds: List<String>,
        customProfileJsonById: Map<String, String> = emptyMap()
    ): ActionResult = withContext(Dispatchers.IO) {
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        mountedGuardError(resolvedImg, "updating per-ISO tweak profiles")?.let { return@withContext ActionResult(false, it) }
        val info = probeVentoyImage(resolvedImg) ?: return@withContext ActionResult(false, "Not a valid Multi-Boot image.")
        val loopDev = attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
        if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) return@withContext ActionResult(false, "Could not attach Multi-Boot partition.")
        return@withContext try {
            mountVentoyPartition1(context, loopDev) ?: return@withContext ActionResult(false, "Could not mount Multi-Boot partition.")
            val (curChk, curNro) = readVentoyJsonOnMounted()
            val meta = readPocketBootMetaOnMounted(curChk, curNro)
            val normPath = normalizeVentoyRelPath(relativePath)
            val cleanIds = selectedProfileIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            val updatedExcluded = if (cleanIds.isEmpty()) meta.excludedAutoInstallIsos + normPath else meta.excludedAutoInstallIsos - normPath
            val updatedMap = LinkedHashMap<String, List<String>>(meta.isoProfileIds)
            if (cleanIds.isEmpty()) updatedMap.remove(normPath) else updatedMap[normPath] = cleanIds
            writeVentoyJsonOnMounted(
                context = context,
                bypassCheck = curChk,
                bypassNro = curNro,
                experience = meta.experience,
                winUtil = meta.winUtil,
                driversDir = null,
                excludedAutoInstallIsos = updatedExcluded,
                isoProfileIds = updatedMap,
                savedCustomProfiles = customProfileJsonById
            )
            val leaf = relativePath.substringAfterLast('/')
            val msg = when (cleanIds.size) {
                0 -> "Set $leaf to Stock (no tweaks)."
                1 -> "Assigned ${profileDisplayLabel(cleanIds.first())} profile to $leaf."
                else -> "Attached ${cleanIds.size} boot-menu tweak profiles to $leaf."
            }
            ActionResult(true, msg)
        } finally {
            unmountVentoyPartition1(loopDev)
        }
    }

    suspend fun updateMenuConfig(
        context: Context,
        ventoyImgPath: String,
        menuConfig: MultiBootMenuConfig,
        existingRelPaths: Set<String>? = null
    ): ActionResult = withContext(Dispatchers.IO) {
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        mountedGuardError(resolvedImg, "updating boot menu settings")?.let { return@withContext ActionResult(false, it) }
        val info = probeVentoyImage(resolvedImg) ?: return@withContext ActionResult(false, "Not a valid Multi-Boot image.")
        val loopDev = attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
        if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) return@withContext ActionResult(false, "Could not attach Multi-Boot partition.")
        return@withContext try {
            mountVentoyPartition1(context, loopDev) ?: return@withContext ActionResult(false, "Could not mount Multi-Boot partition.")
            val raw = Shell.cmd("cat $TEMP_VENTOY_MNT/ventoy/ventoy.json 2>/dev/null").exec().out.joinToString("\n")
            val rootObj = runCatching { if (raw.isNotBlank()) JSONObject(raw) else JSONObject() }.getOrDefault(JSONObject())
            VentoyThemeCatalog.applyMenuConfigPreservingAll(rootObj, menuConfig, existingRelPaths)
            VentoyThemeCatalog.writeThemeAssetsOnMounted(context, TEMP_VENTOY_MNT, menuConfig.themePreset)
            val tmpJson = File(context.cacheDir, "pb_vtoy_menu.json").apply { writeText(rootObj.toString(2), Charsets.UTF_8) }
            try {
                Shell.cmd("mkdir -p $TEMP_VENTOY_MNT/ventoy && cp -f ${shellQuote(tmpJson.absolutePath)} $TEMP_VENTOY_MNT/ventoy/ventoy.json").exec()
            } finally {
                tmpJson.delete()
            }
            ActionResult(true, "Saved Multi-Boot menu appearance & aliases.")
        } finally {
            unmountVentoyPartition1(loopDev)
        }
    }

    /**
     * Non-destructively updates the Ventoy bootloader (boot.img, core.img, VTOYEFI Partition 2)
     * on an existing Ventoy .img without touching any ISOs on Partition 1.
     */
    suspend fun upgradeVentoyBootloader(context: Context, ventoyImgPath: String): ActionResult = withContext(Dispatchers.IO) {
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        mountedGuardError(resolvedImg, "upgrading the bootloader")?.let { return@withContext ActionResult(false, it) }
        val info = probeVentoyImage(resolvedImg)
            ?: return@withContext ActionResult(false, "Not a valid Multi-Boot image.")
        val bundle = resolveBootBundle(context)
            ?: return@withContext ActionResult(false, "Multi-Boot engine files missing. Update in Settings first.")

        val layout = DiskLayoutSpec(
            totalBytes = info.totalBytes,
            totalSectors = info.totalBytes / 512L,
            part1StartLba = info.part1OffsetBytes / 512L,
            part1Sectors = info.part1SizeBytes / 512L,
            part2StartLba = info.part2StartLba,
            part2Sectors = info.part2Sectors
        )
        val err = writeVentoyBootStructures(
            context = context,
            outPath = resolvedImg,
            scheme = info.scheme,
            layout = layout,
            bundle = bundle,
            writePartitionTable = false
        )
        if (err != null) {
            ActionResult(false, "Bootloader upgrade failed: $err")
        } else {
            ActionResult(true, "Updated Multi-Boot bootloader on ${resolvedImg.substringAfterLast('/')} to v${bundle.version} (ISOs preserved).")
        }
    }

    /**
     * Sparse Space Reclaim: zeros deleted/free space inside Partition 1 and punches sparse
     * holes (`fallocate -d` or sparse `cp --sparse=always`) so Android immediately regains
     * gigabytes freed after deleting ISOs from a Multi-Boot drive.
     */
    internal fun isAllZeroBlock(buf: ByteArray, len: Int): Boolean {
        var i = 0
        while (i < len) {
            if (buf[i] != 0.toByte()) return false
            i++
        }
        return true
    }

    /**
     * Android-universal sparse hole-puncher (works on 100% of Android Toybox ROMs without GNU `fallocate -d` or `cp --sparse=always`).
     * Streams the image sequentially via root `cat`, writes only non-zero 256 KiB blocks via `RandomAccessFile.seek()`
     * inside `context.cacheDir` (direct ext4/f2fs `/data` partition — never FUSE), sets exact virtual length,
     * verifies byte count, and atomically swaps into place.
     */
    private fun reclaimViaKotlinSparseCopy(
        context: Context,
        resolvedImg: String,
        expectedBytes: Long,
        onProgress: (WindowsBuildProgress) -> Unit
    ): Boolean {
        val localOut = File(context.applicationContext.cacheDir, "pb_sparse_${System.currentTimeMillis()}.img")
        val qImg = shellQuote(resolvedImg)
        val qLocal = shellQuote(localOut.absolutePath)
        var proc: Process? = null
        return try {
            if (localOut.exists()) localOut.delete()
            proc = ProcessBuilder("su", "-c", "cat $qImg").start()
            val buf = ByteArray(262_144)
            var offset = 0L
            var lastReportMs = 0L
            RandomAccessFile(localOut, "rw").use { raf ->
                proc.inputStream.buffered(262_144).use { input ->
                    while (true) {
                        var filled = 0
                        while (filled < buf.size) {
                            val r = input.read(buf, filled, buf.size - filled)
                            if (r <= 0) break
                            filled += r
                        }
                        if (filled <= 0) break
                        if (!isAllZeroBlock(buf, filled)) {
                            raf.seek(offset)
                            raf.write(buf, 0, filled)
                        }
                        offset += filled.toLong()
                        val now = System.currentTimeMillis()
                        if (expectedBytes > 0L && (now - lastReportMs) >= 350L) {
                            lastReportMs = now
                            val frac = (offset.toFloat() / expectedBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(
                                WindowsBuildProgress(
                                    "Punching sparse holes...",
                                    "${UsbGadgetEngine.formatBytes(offset)} / ${UsbGadgetEngine.formatBytes(expectedBytes)}",
                                    0.60f + frac * 0.35f,
                                    false
                                )
                            )
                        }
                    }
                }
                raf.setLength(expectedBytes)
                raf.fd.sync()
            }
            val exitCode = proc.waitFor()
            if (exitCode != 0 || offset != expectedBytes || localOut.length() != expectedBytes) {
                return false
            }
            val swapOk = Shell.cmd("mv -f $qLocal $qImg 2>/dev/null").exec().isSuccess
            if (!swapOk) return false
            val finalSz = Shell.cmd("stat -c '%s' $qImg 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            finalSz == expectedBytes
        } catch (_: Exception) {
            false
        } finally {
            runCatching { proc?.destroy() }
            if (localOut.exists()) runCatching { localOut.delete() }
        }
    }

    suspend fun reclaimSparseSpace(
        context: Context,
        ventoyImgPath: String,
        onProgress: (WindowsBuildProgress) -> Unit
    ): ActionResult = withContext(Dispatchers.IO) {
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        mountedGuardError(resolvedImg, "reclaiming sparse space")?.let { return@withContext ActionResult(false, it) }
        val info = probeVentoyImage(resolvedImg)
            ?: return@withContext ActionResult(false, "Not a valid Multi-Boot image.")
        val qImg = shellQuote(resolvedImg)
        val beforeBlocks = Shell.cmd("stat -c '%b' $qImg 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
        val beforeDisk = beforeBlocks * 512L

        var loopDev = attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
        if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) {
            return@withContext ActionResult(false, "Could not attach Multi-Boot partition.")
        }

        return@withContext runCatching {
            onProgress(WindowsBuildProgress("Scanning free blocks...", "Mounting Partition 1", 0.12f, true))
            val fsLabel = mountVentoyPartition1(context, loopDev)
                ?: return@withContext ActionResult(false, "Could not mount Multi-Boot partition.")

            val (partTotalB, partFreeB) = queryMountSpaceBytes(TEMP_VENTOY_MNT)
            val partUsedB = if (partTotalB > 0L && partFreeB >= 0L) (partTotalB - partFreeB).coerceAtLeast(0L) else -1L
            // Estimate expected compact disk footprint (used Partition 1 data + 32 MiB VTOYEFI + 48 MiB FS overhead)
            val alreadyCompact = partUsedB >= 0L && beforeDisk <= (partUsedB + 80L * 1024L * 1024L)
            if (alreadyCompact) {
                unmountVentoyPartition1(loopDev)
                loopDev = ""
                onProgress(WindowsBuildProgress("Done!", "Now ${UsbGadgetEngine.formatBytes(beforeDisk)} on disk", 1f, false))
                return@withContext ActionResult(
                    true,
                    "Drive already optimized (${UsbGadgetEngine.formatBytes(beforeDisk)} on disk)."
                )
            }

            onProgress(WindowsBuildProgress("Trimming deleted ISO blocks...", "Preparing sparse hole punch ($fsLabel)", 0.35f, true))
            val trimmed = Shell.cmd("command -v fstrim >/dev/null 2>&1 && fstrim $TEMP_VENTOY_MNT 2>/dev/null").exec().isSuccess
            if (!trimmed) {
                val parentDir = resolvedImg.substringBeforeLast('/')
                val hostFreeB = queryHostFreeSpaceBytes(parentDir)
                val safeZeroBudget = if (partFreeB > 0L && hostFreeB > 0L && hostFreeB >= partFreeB + (256L * 1024L * 1024L)) {
                    partFreeB
                } else 0L
                if (safeZeroBudget > 0L) {
                    Shell.cmd(
                        "dd if=/dev/zero of=$TEMP_VENTOY_MNT/.pb_zero_reclaim bs=4M 2>/dev/null || true; sync; rm -f $TEMP_VENTOY_MNT/.pb_zero_reclaim; sync"
                    ).exec()
                }
            }
            unmountVentoyPartition1(loopDev)
            loopDev = ""

            onProgress(WindowsBuildProgress("Reclaiming Android storage...", "Punching sparse holes in .img", 0.58f, true))
            val digOk = Shell.cmd("command -v fallocate >/dev/null 2>&1 && fallocate -d $qImg 2>/dev/null").exec().isSuccess
            if (!digOk) {
                val parentDir = resolvedImg.substringBeforeLast('/')
                val currentDisk = (Shell.cmd("stat -c '%b' $qImg 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: beforeBlocks) * 512L
                val expectedSparseOut = if (partUsedB >= 0L) (partUsedB + 128L * 1024L * 1024L) else (beforeDisk + 128L * 1024L * 1024L)
                val hostFreeB = queryHostFreeSpaceBytes(parentDir)
                val minNeed = minOf(currentDisk, expectedSparseOut).coerceAtLeast(128L * 1024L * 1024L)
                if (hostFreeB in 0L until minNeed) {
                    return@withContext ActionResult(
                        false,
                        "Not enough free phone storage for safe sparse reclaim (${UsbGadgetEngine.formatBytes(hostFreeB)} free, need ${UsbGadgetEngine.formatBytes(minNeed)})."
                    )
                }
                // Try GNU cp --sparse=always first; fall back to universal pure-Kotlin RandomAccessFile sparse copy
                val tmpWork = UsbGadgetEngine.createSafeTempWorkDir(context, parentDir, "reclaim_${System.currentTimeMillis() % 10000}")
                val qTmpImg = shellQuote("$tmpWork/sparse.img")
                val cpRes = Shell.cmd("cp --sparse=always $qImg $qTmpImg 2>/dev/null").exec()
                val tmpSize = if (cpRes.isSuccess) {
                    Shell.cmd("stat -c '%s' $qTmpImg 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                } else 0L
                val gnuSwapped = cpRes.isSuccess && tmpSize == info.totalBytes &&
                    Shell.cmd("mv -f $qTmpImg $qImg 2>/dev/null").exec().isSuccess
                UsbGadgetEngine.cleanupTempWorkDir(tmpWork)
                if (!gnuSwapped) {
                    val ktOk = reclaimViaKotlinSparseCopy(context, resolvedImg, info.totalBytes, onProgress)
                    if (!ktOk) {
                        return@withContext ActionResult(false, "Sparse reclaim aborted safely (could not write verified sparse copy).")
                    }
                }
            }

            UsbGadgetEngine.fixUserStoragePermissions(context, resolvedImg, isDir = false)
            Shell.cmd("stat -c '%s' $qImg 2>/dev/null || echo 0").exec().out.firstOrNull()?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.let {
                UsbGadgetEngine.syncMediaStoreFileSize(context, resolvedImg, it)
            }
            IsoInspector.invalidate(resolvedImg)
            val afterBlocks = Shell.cmd("stat -c '%b' $qImg 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: beforeBlocks
            val afterDisk = afterBlocks * 512L
            val saved = (beforeDisk - afterDisk).coerceAtLeast(0L)
            onProgress(WindowsBuildProgress("Done!", "Now ${UsbGadgetEngine.formatBytes(afterDisk)} on disk", 1f, false))
            ActionResult(
                success = true,
                userMessage = if (saved > 1024L * 1024L) {
                    "Reclaimed ${UsbGadgetEngine.formatBytes(saved)} (now ${UsbGadgetEngine.formatBytes(afterDisk)} on disk)."
                } else {
                    "Drive already optimized (${UsbGadgetEngine.formatBytes(afterDisk)} on disk)."
                }
            )
        }.getOrElse { e ->
            if (loopDev.isNotEmpty()) unmountVentoyPartition1(loopDev)
            ActionResult(false, "Reclaim failed: ${e.message ?: "Unexpected error"}")
        }
    }

    /**
     * Non-Destructive Multi-Boot Resize: expands an existing Multi-Boot .img to a larger capacity
     * (e.g., 8 GB -> 16/32/64/128 GB) while preserving every ISO and ventoy.json configuration.
     * Uses in-place `libntfsresize.so` for NTFS drives when available, and falls back to a verified
     * copy-and-swap rebuild inside `<parent>/.pocketboot_tmp/` for exFAT drives.
     */
    suspend fun resizeVentoyImage(
        context: Context,
        ventoyImgPath: String,
        newSizeGb: Int,
        onProgress: (WindowsBuildProgress) -> Unit
    ): ActionResult = withContext(Dispatchers.IO) {
        BuildThermalHelper.resetCancelFlag()
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        mountedGuardError(resolvedImg, "resizing this drive")?.let { return@withContext ActionResult(false, it) }
        val info = probeVentoyImage(resolvedImg)
            ?: return@withContext ActionResult(false, "Not a valid Multi-Boot image.")
        val bundle = resolveBootBundle(context)
            ?: return@withContext ActionResult(false, "Multi-Boot engine files missing.")

        val newSizeMb = newSizeGb.coerceIn(2, 128) * 1024
        val newLayout = DiskPartitioner.calculateVentoyLayoutMb(newSizeMb, info.scheme)
        if (newLayout.totalBytes <= info.totalBytes) {
            return@withContext ActionResult(false, "Select a size larger than the current drive capacity.")
        }

        val qImg = shellQuote(resolvedImg)
        val p1Magic = Shell.cmd(
            "dd if=$qImg bs=512 skip=${info.part1OffsetBytes / 512L} count=1 2>/dev/null | tr -cd 'A-Za-z0-9 '"
        ).exec().out.joinToString("").uppercase()
        val isNtfs = p1Magic.contains("NTFS")
        val ntfsResizeBin = WindowsImageBuilder.findBundledBinary(context, "libntfsresize.so", listOf("ntfsresize"))

        // Path A: Instant in-place NTFS expansion via bundled static libntfsresize.so
        if (isNtfs && ntfsResizeBin != null) {
            var loopDev = ""
            return@withContext runCatching {
                onProgress(WindowsBuildProgress("Expanding sparse image to $newSizeGb GB...", "Extending disk boundaries", 0.20f, false))
                val truncOk = Shell.cmd("truncate -s ${newLayout.totalBytes} $qImg 2>&1").exec().isSuccess
                val expandedSz = if (truncOk) Shell.cmd("stat -c '%s' $qImg 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L else 0L
                if (!truncOk || expandedSz != newLayout.totalBytes) return@withContext ActionResult(false, "Failed to expand image file to ${UsbGadgetEngine.formatBytes(newLayout.totalBytes)}.")

                onProgress(WindowsBuildProgress("Updating partition table & EFI...", "Relocating 32 MB VTOYEFI to new end", 0.48f, false))
                val tblErr = writeVentoyBootStructures(
                    context = context,
                    outPath = resolvedImg,
                    scheme = info.scheme,
                    layout = newLayout,
                    bundle = bundle,
                    writePartitionTable = true
                )
                if (tblErr != null) return@withContext ActionResult(false, "Partition table update failed: $tblErr")

                onProgress(WindowsBuildProgress("Expanding NTFS filesystem...", "Running ntfsresize (all ISOs kept)", 0.72f, true))
                loopDev = attachPartition1Loop(resolvedImg, newLayout.part1OffsetBytes, newLayout.part1SizeBytes)
                if (loopDev.isBlank()) return@withContext ActionResult(false, "Could not attach expanded partition.")
                val qLoop = shellQuote(loopDev)
                val qNr = shellQuote(ntfsResizeBin)
                val nrRes = Shell.cmd("printf 'y\\n' | $qNr -f -b $qLoop 2>&1 || printf 'y\\n' | $qNr -f $qLoop 2>&1").exec()
                unmountVentoyPartition1(loopDev)
                loopDev = ""
                if (!nrRes.isSuccess) {
                    return@withContext ActionResult(false, "ntfsresize failed: ${(nrRes.out + nrRes.err).takeLast(3).joinToString(" ").take(160)}")
                }
                UsbGadgetEngine.fixUserStoragePermissions(context, resolvedImg, isDir = false); Shell.cmd("stat -c '%s' $qImg 2>/dev/null || echo 0").exec().out.firstOrNull()?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.let { UsbGadgetEngine.syncMediaStoreFileSize(context, resolvedImg, it) }
                IsoInspector.invalidate(resolvedImg)
                onProgress(WindowsBuildProgress("Done!", "Expanded to $newSizeGb GB", 1f, false))
                ActionResult(true, "Expanded ${resolvedImg.substringAfterLast('/')} to $newSizeGb GB (all ISOs preserved).")
            }.getOrElse { e ->
                if (loopDev.isNotEmpty()) unmountVentoyPartition1(loopDev)
                ActionResult(false, "Resize failed: ${e.message ?: "Unexpected error"}")
            }
        }

        // Path B: Universal safe copy-and-swap (works for exFAT and NTFS without ntfsresize)
        val parentDir = resolvedImg.substringBeforeLast('/')
        val existingAllocBytes = Shell.cmd("stat -c '%b' $qImg 2>/dev/null")
            .exec().out.firstOrNull()?.trim()?.toLongOrNull()?.times(512L)?.coerceAtLeast(0L) ?: 0L
        val hostFreeForResize = queryHostFreeSpaceBytes(parentDir)
        val resizeTempNeeded = existingAllocBytes + (128L * 1024L * 1024L)
        if (hostFreeForResize in 0L until resizeTempNeeded) {
            return@withContext ActionResult(
                false,
                "Not enough free phone storage for safe exFAT resize (${UsbGadgetEngine.formatBytes(hostFreeForResize)} free). Need at least ${UsbGadgetEngine.formatBytes(resizeTempNeeded)} free to hold the temporary expanded copy."
            )
        }
        val workDir = UsbGadgetEngine.createSafeTempWorkDir(context, parentDir, "resize_${System.currentTimeMillis() % 10000}")
        val newImgName = "expanded.img"
        val newImgPath = "$workDir/$newImgName"
        val oldMnt = "$workDir/old_mnt"
        val newMnt = "$workDir/new_mnt"
        var oldLoop = ""
        var newLoop = ""
        return@withContext try {
            val fsChoice = if (isNtfs) VentoyFilesystem.NTFS else VentoyFilesystem.EXFAT
            onProgress(WindowsBuildProgress("Creating $newSizeGb GB container...", "Safe non-destructive rebuild", 0.15f, false))
            val existingLabel = run {
                val tmpLoop = attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
                if (tmpLoop.startsWith("/dev/")) {
                    val blkOut = Shell.cmd("blkid ${shellQuote(tmpLoop)} 2>/dev/null").exec().out.joinToString(" ")
                    BuildThermalHelper.unmountAndDetachClean(emptyList(), listOf(tmpLoop), removeDirs = false)
                    Regex("""\bLABEL=(?:"([^"]+)"|([^\s]+))""").find(blkOut)
                        ?.let { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } }
                        ?.trim()
                        .orEmpty()
                } else ""
            }
            val buildRes = buildVentoyImage(
                context = context,
                config = VentoyBuildConfig(
                    outputDir = workDir,
                    outputName = newImgName,
                    sizeMb = newSizeMb,
                    scheme = info.scheme,
                    filesystem = fsChoice,
                    win11BypassCheck = false,
                    win11BypassNro = false,
                    experience = WindowsExperienceConfig.stock(),
                    winUtil = WinUtilTweaksConfig(),
                    autoMountAfterBuild = false,
                    volumeLabel = existingLabel
                ),
                onProgress = { p -> onProgress(p.copy(progress = 0.15f + p.progress * 0.30f)) }
            )
            if (!buildRes.success) {
                return@withContext ActionResult(false, "Could not create expanded drive: ${buildRes.userMessage}")
            }

            onProgress(WindowsBuildProgress("Copying existing ISOs...", "Preserving all ISOs & boot config", 0.55f, true))
            oldLoop = attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
            newLoop = attachPartition1Loop(newImgPath, newLayout.part1OffsetBytes, newLayout.part1SizeBytes)
            if (oldLoop.isBlank() || newLoop.isBlank()) {
                return@withContext ActionResult(false, "Could not attach partitions for migration.")
            }

            val procFs = Shell.cmd("cat /proc/filesystems 2>/dev/null").exec().out.joinToString(" ").lowercase()
            val ntfs3g = WindowsImageBuilder.findBundledBinary(context, "libntfs_3g.so", listOf("ntfs-3g"))
            fun mntExpr(loop: String, dst: String): String {
                val ql = shellQuote(loop)
                val qd = shellQuote(dst)
                return buildList {
                    if (procFs.contains("ntfs3")) add("mount -t ntfs3 -o rw $ql $qd 2>/dev/null")
                    if (procFs.contains("exfat")) add("mount -t exfat -o rw $ql $qd 2>/dev/null")
                    if (procFs.contains("sdfat")) add("mount -t sdfat -o rw $ql $qd 2>/dev/null")
                    if (ntfs3g != null) add("${shellQuote(ntfs3g)} -o rw,big_writes,noatime $ql $qd 2>/dev/null")
                    add("mount -o rw $ql $qd 2>/dev/null")
                }.joinToString(" || ")
            }
            val mntBoth = Shell.cmd(
                "mkdir -p ${shellQuote(oldMnt)} ${shellQuote(newMnt)} && (${mntExpr(oldLoop, oldMnt)}) && (${mntExpr(newLoop, newMnt)})"
            ).exec()
            if (!mntBoth.isSuccess) return@withContext ActionResult(false, "Could not mount partitions for ISO copy.")

            val qOldMnt = shellQuote(oldMnt)
            val qNewMnt = shellQuote(newMnt)
            val migrateScript = """
                set -e
                OLD_MNT=$qOldMnt
                NEW_MNT=$qNewMnt
                rm -rf "${'$'}NEW_MNT/ventoy"
                cp -rf "${'$'}OLD_MNT/." "${'$'}NEW_MNT/" 2>&1
                cd "${'$'}OLD_MNT"
                find . -maxdepth 3 -type f \( -iname '*.iso' -o -iname '*.img' -o -iname '*.wim' -o -iname '*.vhd' -o -iname '*.vhdx' \) ! -path './ventoy/*' 2>/dev/null | while IFS= read -r rel; do
                    src_sz=${'$'}(stat -c '%s' "${'$'}rel" 2>/dev/null || echo -1)
                    dst_sz=${'$'}(stat -c '%s' "${'$'}NEW_MNT/${'$'}rel" 2>/dev/null || echo -2)
                    if [ "${'$'}src_sz" != "${'$'}dst_sz" ] || [ "${'$'}src_sz" -le 0 ]; then
                        echo "VERIFY_MISMATCH:${'$'}rel:${'$'}dst_sz/${'$'}src_sz"
                        exit 1
                    fi
                done
            """.trimIndent()
            val cpRes = BuildThermalHelper.runIsolatedRootScript(
                context,
                "multiboot_resize_cp",
                migrateScript,
                trackPid = true
            )
            BuildThermalHelper.unmountAndDetachClean(
                mountPoints = listOf(oldMnt, newMnt),
                loopDevices = listOf(oldLoop, newLoop).filter { it.isNotBlank() },
                removeDirs = false
            )
            oldLoop = ""
            newLoop = ""
            if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                return@withContext ActionResult(false, "Resize cancelled (original drive untouched).")
            }
            if (!cpRes.isSuccess) {
                val errHint = (cpRes.out + cpRes.err).takeLast(2).joinToString(" ").take(140).trim()
                return@withContext ActionResult(false, "ISO migration failed during resize (original drive untouched).${if (errHint.isNotBlank()) " $errHint" else ""}")
            }

            onProgress(WindowsBuildProgress("Replacing with $newSizeGb GB drive...", "Finalizing atomic swap", 0.94f, false))
            val swapRes = Shell.cmd("mv -f ${shellQuote(newImgPath)} $qImg 2>&1").exec()
            if (!swapRes.isSuccess) return@withContext ActionResult(false, "Failed to finalize resized drive.")

            UsbGadgetEngine.fixUserStoragePermissions(context, resolvedImg, isDir = false); Shell.cmd("stat -c '%s' $qImg 2>/dev/null || echo 0").exec().out.firstOrNull()?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.let { UsbGadgetEngine.syncMediaStoreFileSize(context, resolvedImg, it) }
            IsoInspector.invalidate(resolvedImg)
            onProgress(WindowsBuildProgress("Done!", "Expanded to $newSizeGb GB", 1f, false))
            ActionResult(true, "Expanded ${resolvedImg.substringAfterLast('/')} to $newSizeGb GB (all ISOs preserved).")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult(false, "Resize failed: ${e.message ?: "Unexpected error"}")
        } finally {
            val remMounts = buildList {
                if (oldLoop.isNotEmpty()) add(oldMnt)
                if (newLoop.isNotEmpty()) add(newMnt)
            }
            val remLoops = listOf(oldLoop, newLoop).filter { it.isNotEmpty() }
            if (remMounts.isNotEmpty() || remLoops.isNotEmpty()) {
                BuildThermalHelper.unmountAndDetachClean(remMounts, remLoops, removeDirs = false)
            }
            UsbGadgetEngine.cleanupTempWorkDir(workDir)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentoyBuilderSheet(
    defaultDir: String,
    initialIso: ImageFile?,
    presets: List<AutounattendPreset> = emptyList(),
    systemTweaksPresets: List<SystemTweaksPreset> = emptyList(),
    systemTweaksSearchHistory: List<String> = emptyList(),
    building: Boolean,
    progress: WindowsBuildProgress,
    onDismiss: () -> Unit,
    onCancelBuild: () -> Unit,
    onSavePreset: (AutounattendPreset) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    onSaveSystemTweaksPreset: (SystemTweaksPreset) -> Unit = {},
    onDeleteSystemTweaksPreset: (String) -> Unit = {},
    onUpdateSearchHistory: (List<String>) -> Unit = {},
    onStartBuild: (VentoyBuildConfig) -> Unit,
    onMessage: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var assetInfo by remember { mutableStateOf(VentoyImageBuilder.getInstalledVentoyInfo(context)) }
    var updatingAssets by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(WindowsBuildProgress()) }

    var outputName by remember { mutableStateOf(IsoInspector.DEFAULT_MULTIBOOT_FILE_BASE) }
    var volumeLabel by remember { mutableStateOf(IsoInspector.DEFAULT_MULTIBOOT_NTFS_LABEL) }
    var volumeLabelEdited by remember { mutableStateOf(false) }
    var selectedPresetMb by remember { mutableIntStateOf(8192) }
    var isCustomSize by remember { mutableStateOf(false) }
    var customSizeText by remember { mutableStateOf("24") }
    var customUnit by remember { mutableStateOf(MultiBootSizeUnit.GB) }
    var scheme by remember { mutableStateOf(PartitionScheme.GPT) }
    var filesystem by remember { mutableStateOf(VentoyFilesystem.AUTO) }
    var experience by remember { mutableStateOf(WindowsExperienceConfig()) }
    var winUtil by remember { mutableStateOf(WinUtilTweaksConfig()) }
    var driversDir by remember { mutableStateOf("") }
    var savedDriverPacks by remember { mutableStateOf<List<DriverPackItem>>(emptyList()) }
    var selectedDriverPackDirs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBuilderPackManager by remember { mutableStateOf(false) }
    var builderPackReloadNonce by remember { mutableIntStateOf(0) }
    var driversBytes by remember { mutableStateOf(0L) }
    var showDriversPicker by remember { mutableStateOf(false) }
    var includeInitial by remember(initialIso?.path) { mutableStateOf(initialIso != null) }
    var moveInitial by remember { mutableStateOf(false) }
    var autoMount by remember { mutableStateOf(true) }

    LaunchedEffect(defaultDir, builderPackReloadNonce) {
        val loaded = DriverPackManager.listPacks(defaultDir)
        savedDriverPacks = loaded
        val validDirs = loaded.map { it.dirPath }.toSet()
        selectedDriverPackDirs = selectedDriverPackDirs.intersect(validDirs)
    }
    LaunchedEffect(driversDir, selectedDriverPackDirs, savedDriverPacks) {
        val packBytes = savedDriverPacks.filter { it.dirPath in selectedDriverPackDirs }.sumOf { it.sizeBytes }
        val manualBytes = if (driversDir.isBlank()) 0L else withContext(Dispatchers.IO) {
            UsbGadgetEngine.queryDirSizeBytes(driversDir)
        }
        driversBytes = packBytes + manualBytes
    }

    val effectiveSizeMb = if (isCustomSize) {
        val rawNum = customSizeText.toDoubleOrNull() ?: 0.0
        val mb = if (customUnit == MultiBootSizeUnit.GB) (rawNum * 1024.0).roundToInt() else rawNum.roundToInt()
        mb.coerceIn(512, 131072)
    } else {
        selectedPresetMb
    }
    val customValid = !isCustomSize || ((customSizeText.toDoubleOrNull() ?: 0.0) > 0.0)
    val includeIsoBytes = if (includeInitial && initialIso != null) initialIso.sizeBytes.coerceAtLeast(0L) else 0L
    val hasPayloadToFit = (includeInitial && initialIso != null) || driversBytes > 0L
    val requiredBytesForInitial = if (hasPayloadToFit) {
        includeIsoBytes + driversBytes + (256L * 1024L * 1024L)
    } else 0L
    val part1CapacityBytes = effectiveSizeMb.toLong() * 1024L * 1024L
    val capacityInsufficient = hasPayloadToFit && part1CapacityBytes < requiredBytesForInitial
    val minRequiredGb = if (requiredBytesForInitial > 0L) {
        ((requiredBytesForInitial + (1024L * 1024L * 1024L) - 1L) / (1024L * 1024L * 1024L)).toInt().coerceAtLeast(1)
    } else 1
    val suggestedPresetGb = listOf(8, 16, 32, 64, 128).firstOrNull { it >= minRequiredGb }
    val displaySize = if (effectiveSizeMb >= 1024) {
        String.format(Locale.US, "%.2f GB", effectiveSizeMb / 1024.0)
            .replace(Regex("(\\.\\d*?[1-9])0+ GB$"), "$1 GB")
            .replace(".00 GB", " GB")
    } else {
        "$effectiveSizeMb MB"
    }
    val trimmedName = outputName.trim().ifBlank { IsoInspector.DEFAULT_MULTIBOOT_FILE_BASE }
    val finalImgName = if (trimmedName.endsWith(".img", ignoreCase = true)) trimmedName else "$trimmedName.img"

    var targetExists by remember(defaultDir) { mutableStateOf(false) }
    val resolvedOutName = remember(finalImgName) { UsbGadgetEngine.sanitizeOutputFileName(finalImgName, ".img", IsoInspector.DEFAULT_MULTIBOOT_FILE_BASE) }
    LaunchedEffect(defaultDir, resolvedOutName) {
        targetExists = UsbGadgetEngine.fileExistsInDir(defaultDir, resolvedOutName)
    }

    ModalBottomSheet(
        onDismissRequest = { if (!building && !updatingAssets) onDismiss() },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PocketColors.violetBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Layers, null, tint = PocketColors.violet, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Create Multi-Boot Drive", style = MaterialTheme.typography.titleLarge, color = PocketColors.textPrimary)
                    Text(
                        text = "Boot multiple Windows & Linux ISOs over 2.2 GB from one sparse drive",
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )
                }
            }

            // Multi-Boot Engine Version & In-App Update Bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PocketColors.surfaceInput,
                border = BorderStroke(0.5.dp, if (assetInfo.ready) PocketColors.border else PocketColors.warning)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (assetInfo.ready) {
                                "Multi-Boot Engine v${assetInfo.version} (${if (assetInfo.isUpdatedInApp) "Updated" else "Bundled"})"
                            } else {
                                "Multi-Boot Engine Missing"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (assetInfo.ready) PocketColors.textPrimary else PocketColors.warning
                        )
                        Text(
                            text = if (updatingAssets) updateProgress.detail.ifBlank { updateProgress.stage } else "Offline ready · Tap Update for latest engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                updatingAssets = true
                                val res = VentoyImageBuilder.updateVentoyFromGitHub(context) { updateProgress = it }
                                assetInfo = VentoyImageBuilder.getInstalledVentoyInfo(context)
                                updatingAssets = false
                                onMessage(res.userMessage, !res.success)
                            }
                        },
                        enabled = !building && !updatingAssets
                    ) {
                        if (updatingAssets) {
                            CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = PocketColors.accent)
                        } else {
                            Icon(Icons.Rounded.CloudDownload, null, tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Update", fontSize = 12.sp, color = PocketColors.accentSoft)
                        }
                    }
                }
            }

            if (building) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PocketColors.surfaceInput,
                    border = BorderStroke(1.dp, PocketColors.accent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PocketColors.danger)
                ) {
                    Icon(Icons.Rounded.Close, null, tint = PocketColors.danger, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel", color = PocketColors.danger, fontWeight = FontWeight.SemiBold)
                }
            } else {
                // Drive Name (default: "Pocket Multi Boot" + .img suffix like Blank Drive)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Drive Name", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                    OutlinedTextField(
                        value = outputName,
                        onValueChange = { outputName = it },
                        placeholder = { Text(IsoInspector.DEFAULT_MULTIBOOT_FILE_BASE) },
                        singleLine = true,
                        suffix = { Text(".img", style = MaterialTheme.typography.labelMedium, color = PocketColors.accentSoft) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PocketColors.accent,
                            unfocusedBorderColor = PocketColors.border,
                            focusedContainerColor = PocketColors.surfaceInput,
                            unfocusedContainerColor = PocketColors.surfaceInput,
                            focusedTextColor = PocketColors.textPrimary,
                            unfocusedTextColor = PocketColors.textPrimary
                        )
                    )
                }

                // USB Volume Label (NTFS default: "POCKETBOOT Multi ISO", exFAT default: "PB MultiISO", editable)
                val isExfatSelected = filesystem == VentoyFilesystem.EXFAT
                val maxLabelLen = if (isExfatSelected) 11 else 32
                LaunchedEffect(filesystem) {
                    if (!volumeLabelEdited) {
                        volumeLabel = IsoInspector.defaultMultiBootLabel(useExfat = isExfatSelected)
                    } else if (isExfatSelected && volumeLabel.length > 11) {
                        volumeLabel = volumeLabel.take(11)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isExfatSelected) "USB Volume Label (exFAT, max 11 chars)" else "USB Volume Label (Explorer Name)",
                        style = MaterialTheme.typography.labelLarge,
                        color = PocketColors.textPrimary
                    )
                    OutlinedTextField(
                        value = volumeLabel,
                        onValueChange = {
                            volumeLabelEdited = true
                            volumeLabel = it.filter { ch -> ch.code >= 0x20 && ch !in "\\/:*?\"<>|" }.take(maxLabelLen)
                        },
                        placeholder = { Text(IsoInspector.defaultMultiBootLabel(useExfat = isExfatSelected)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PocketColors.accent,
                            unfocusedBorderColor = PocketColors.border,
                            focusedContainerColor = PocketColors.surfaceInput,
                            unfocusedContainerColor = PocketColors.surfaceInput,
                            focusedTextColor = PocketColors.textPrimary,
                            unfocusedTextColor = PocketColors.textPrimary
                        )
                    )
                    if (!isExfatSelected && volumeLabel.trim().length > IsoInspector.EXPLORER_SINGLE_LINE_SAFE_MAX) {
                        Text(
                            text = "Over ${IsoInspector.EXPLORER_SINGLE_LINE_SAFE_MAX} chars may wrap in Windows Explorer Tiles view and hide free/total space.",
                            fontSize = 11.sp,
                            color = PocketColors.warning
                        )
                    } else if (filesystem == VentoyFilesystem.AUTO && volumeLabel.trim().length > 11) {
                        Text(
                            text = "Auto Select uses exFAT when supported (clamps label to 11 chars) or full label on NTFS.",
                            fontSize = 11.sp,
                            color = PocketColors.textSecondary
                        )
                    }
                }

                // Drive Capacity Selector (matching Blank USB Drive layout: 2x3 presets + Custom MB/GB switch)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Drive Capacity (Sparse)", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                        Text(displaySize, style = MaterialTheme.typography.labelLarge, color = if (capacityInsufficient) PocketColors.danger else PocketColors.accentSoft)
                    }
                    val presets = listOf(
                        "8 GB" to 8192,
                        "16 GB" to 16384,
                        "32 GB" to 32768,
                        "64 GB" to 65536,
                        "128 GB" to 131072,
                        "Custom" to -1
                    )
                    presets.chunked(3).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (label, mb) ->
                                val selected = if (mb == -1) isCustomSize else (!isCustomSize && selectedPresetMb == mb)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .bounceClick {
                                            if (mb == -1) isCustomSize = true else {
                                                isCustomSize = false
                                                selectedPresetMb = mb
                                            }
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(1.dp, if (selected) PocketColors.accent else PocketColors.border)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (selected) PocketColors.accentSoft else PocketColors.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = isCustomSize) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customSizeText,
                                onValueChange = { input ->
                                    customSizeText = if (customUnit == MultiBootSizeUnit.GB) {
                                        input.filter { it.isDigit() || it == '.' }
                                    } else {
                                        input.filter { it.isDigit() }
                                    }
                                },
                                label = { Text("Size (${customUnit.label})") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PocketColors.accent,
                                    unfocusedBorderColor = PocketColors.border,
                                    focusedContainerColor = PocketColors.surfaceInput,
                                    unfocusedContainerColor = PocketColors.surfaceInput,
                                    focusedTextColor = PocketColors.textPrimary,
                                    unfocusedTextColor = PocketColors.textPrimary
                                )
                            )

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, PocketColors.border)
                            ) {
                                Row(modifier = Modifier.padding(4.dp)) {
                                    MultiBootSizeUnit.entries.forEach { unit ->
                                        val active = customUnit == unit
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (active) PocketColors.accent else PocketColors.surfaceInput,
                                            modifier = Modifier.clickable {
                                                if (customUnit != unit) {
                                                    val cur = customSizeText.toDoubleOrNull()
                                                    customUnit = unit
                                                    if (cur != null && cur > 0.0) {
                                                        customSizeText = if (unit == MultiBootSizeUnit.GB) {
                                                            String.format(Locale.US, "%.1f", (cur / 1024.0).coerceAtLeast(1.0))
                                                                .removeSuffix(".0")
                                                        } else {
                                                            (cur * 1024.0).roundToInt().toString()
                                                        }
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = unit.label,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (active) PocketColors.onAccent else PocketColors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (capacityInsufficient) {
                        val presetHint = if (suggestedPresetGb != null && suggestedPresetGb != minRequiredGb) " (or tap $suggestedPresetGb GB)" else ""
                        val payloadDesc = if (includeInitial && initialIso != null) {
                            "${initialIso.name} (${UsbGadgetEngine.formatBytes(initialIso.sizeBytes)})" +
                                if (driversBytes > 0L) " + ${UsbGadgetEngine.formatBytes(driversBytes)} drivers" else ""
                        } else {
                            "selected Drivers folder (${UsbGadgetEngine.formatBytes(driversBytes)})"
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PocketColors.dangerBg,
                            border = BorderStroke(0.75.dp, PocketColors.danger)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.ErrorOutline, null, tint = PocketColors.danger, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Too small for $payloadDesc. Note: Increase size to at least $minRequiredGb GB$presetHint.",
                                    fontSize = 11.sp,
                                    color = PocketColors.textPrimary
                                )
                            }
                        }
                    }
                }

                // Partition Scheme
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Partition Style", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PartitionScheme.entries.forEach { s ->
                            val selected = scheme == s
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .bounceClick { scheme = s },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) PocketColors.accentBg else PocketColors.surfaceInput,
                                border = BorderStroke(if (selected) 1.5.dp else 0.5.dp, if (selected) PocketColors.accent else PocketColors.border)
                            ) {
                                Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(s.label, style = MaterialTheme.typography.labelLarge, color = if (selected) PocketColors.accentSoft else PocketColors.textPrimary)
                                    Text(if (s == PartitionScheme.GPT) "UEFI + BIOS hybrid" else "Legacy BIOS + UEFI", fontSize = 11.sp, color = PocketColors.textSecondary)
                                }
                            }
                        }
                    }
                }

                // ISO Partition Filesystem: Symmetrical Full-Width Auto (Recommended) + 50/50 NTFS & exFAT
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ISO Partition Filesystem", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)

                    val autoSelected = filesystem == VentoyFilesystem.AUTO
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick { filesystem = VentoyFilesystem.AUTO },
                        shape = RoundedCornerShape(12.dp),
                        color = if (autoSelected) PocketColors.accentBg else PocketColors.surfaceInput,
                        border = BorderStroke(if (autoSelected) 1.5.dp else 0.5.dp, if (autoSelected) PocketColors.accent else PocketColors.border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Auto Select",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (autoSelected) PocketColors.accentSoft else PocketColors.textPrimary
                                    )
                                    Surface(shape = RoundedCornerShape(6.dp), color = PocketColors.successBg) {
                                        Text(
                                            text = "Recommended",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PocketColors.success
                                        )
                                    }
                                }
                                Text(
                                    text = "Uses fast native exFAT when supported, else universal NTFS",
                                    fontSize = 11.sp,
                                    color = PocketColors.textSecondary
                                )
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(VentoyFilesystem.NTFS, VentoyFilesystem.EXFAT).forEach { fs ->
                            val selected = filesystem == fs
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .bounceClick { filesystem = fs },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) PocketColors.accentBg else PocketColors.surfaceInput,
                                border = BorderStroke(if (selected) 1.5.dp else 0.5.dp, if (selected) PocketColors.accent else PocketColors.border)
                            ) {
                                Column(
                                    modifier = Modifier.padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = fs.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (selected) PocketColors.accentSoft else PocketColors.textPrimary
                                    )
                                    Text(
                                        text = fs.subtitle,
                                        fontSize = 11.sp,
                                        color = PocketColors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                WindowsTwoPaneCustomizer(
                    osKind = WindowsOsKind.WIN11, arch = "amd64", experience = experience, winUtil = winUtil, presets = presets,
                    systemTweaksPresets = systemTweaksPresets,
                    systemTweaksSearchHistory = systemTweaksSearchHistory,
                    showCa2023Option = false, showCustomScriptOption = false, defaultBrowseDir = defaultDir,
                    onExperienceChange = { experience = it }, onWinUtilChange = { winUtil = it },
                    onApplyPreset = { p ->
                        experience = WindowsExperienceConfig(p.bypassRequirements, p.bypassMicrosoftAccount, p.disableDataCollection, p.disableBitLocker, p.qolImprovements, p.disableSponsoredApps, false, p.skipWifiSetup, p.createLocalUser, p.localUsername, "", p.skipOobe, p.useCa2023Bootloaders, p.applySkuSiPolicy)
                        winUtil = WinUtilTweaksConfig.fromJsonString(p.winUtilJson, legacyClassicContextMenu = p.classicContextMenu)
                    },
                    onSavePreset = { n -> onSavePreset(AutounattendPreset(n, scheme.name, experience.bypassRequirements, experience.bypassMicrosoftAccount, experience.disableDataCollection, experience.disableBitLocker, experience.disableSponsoredApps, false, experience.skipWifiSetup, experience.createLocalUser, experience.localUsername, experience.skipOobe, experience.qolImprovements, experience.useCa2023Bootloaders, experience.applySkuSiPolicy, winUtil.toJsonString())) },
                    onDeletePreset = onDeletePreset,
                    onSaveSystemTweaksPreset = onSaveSystemTweaksPreset,
                    onDeleteSystemTweaksPreset = onDeleteSystemTweaksPreset,
                    onUpdateSearchHistory = onUpdateSearchHistory
                )
                DriverPackMultiSelectorSection(
                    packs = savedDriverPacks,
                    selectedPackDirs = selectedDriverPackDirs,
                    onTogglePack = { dir ->
                        selectedDriverPackDirs = if (dir in selectedDriverPackDirs) selectedDriverPackDirs - dir else selectedDriverPackDirs + dir
                    },
                    onOpenManager = { showBuilderPackManager = true }
                )
                Surface(modifier = Modifier.fillMaxWidth().bounceClick { showDriversPicker = true }, shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.75.dp, if (driversDir.isNotBlank()) PocketColors.accent else PocketColors.border)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Inject Windows Drivers Folder (Optional)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                            Text(if (driversDir.isNotBlank()) driversDir.replace("/data/media/0", "/sdcard") else "Auto-loads .inf storage/Wi-Fi drivers automatically in WinPE", fontSize = 11.sp, color = if (driversDir.isNotBlank()) PocketColors.accentSoft else PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (driversDir.isNotBlank()) { IconButton(onClick = { driversDir = "" }, modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.Close, "Clear", tint = PocketColors.textMuted, modifier = Modifier.size(15.dp)) } }
                        else Text("Browse", style = MaterialTheme.typography.labelSmall, color = PocketColors.accentSoft)
                    }
                }

                // Optional initial ISO
                if (initialIso != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PocketColors.surfaceInput,
                        border = BorderStroke(1.dp, PocketColors.accent)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { includeInitial = !includeInitial }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Add ${initialIso.name} now", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(UsbGadgetEngine.formatBytes(initialIso.sizeBytes), style = PocketColors.monoStyle, color = PocketColors.textSecondary)
                                }
                                PocketSwitch(checked = includeInitial, onCheckedChange = { includeInitial = it }, activeColor = PocketColors.success)
                            }
                            if (includeInitial) {
                                PocketHairlineDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { moveInitial = !moveInitial }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Move instead of Copy (saves phone storage)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                        Text("Deletes source ISO after verified copy", fontSize = 11.sp, color = PocketColors.textSecondary)
                                    }
                                    PocketSwitch(checked = moveInitial, onCheckedChange = { moveInitial = it }, activeColor = PocketColors.warning)
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PocketColors.surfaceInput,
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { autoMount = !autoMount }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mount as USB Drive when done", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                            Text("Auto-mount as Writable USB when ready (recommended for NTFS/exFAT)", fontSize = 11.sp, color = PocketColors.textSecondary)
                        }
                        PocketSwitch(checked = autoMount, onCheckedChange = { autoMount = it }, activeColor = PocketColors.success)
                    }
                }

                if (capacityInsufficient) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PocketColors.dangerBg,
                        border = BorderStroke(1.dp, PocketColors.danger)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = PocketColors.danger, modifier = Modifier.size(18.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                val errTitle = if (includeInitial && initialIso != null) "Drive size is too small for this ISO" else "Drive size is too small for selected Drivers"
                                Text(errTitle, style = MaterialTheme.typography.labelLarge, color = PocketColors.danger)
                                val presetHint = if (suggestedPresetGb != null && suggestedPresetGb != minRequiredGb) " (or pick the $suggestedPresetGb GB preset)" else ""
                                val errDetail = if (includeInitial && initialIso != null) {
                                    val drvNote = if (driversBytes > 0L) " + ${UsbGadgetEngine.formatBytes(driversBytes)} drivers" else ""
                                    "${initialIso.name} is ${UsbGadgetEngine.formatBytes(initialIso.sizeBytes)}$drvNote, which will not fit inside a $displaySize drive. Note: Please increase Drive Capacity (Sparse) above to at least $minRequiredGb GB$presetHint to match your ISO size."
                                } else {
                                    "Your selected Drivers folder is ${UsbGadgetEngine.formatBytes(driversBytes)}, which will not fit inside a $displaySize drive. Note: Please increase Drive Capacity (Sparse) above to at least $minRequiredGb GB$presetHint."
                                }
                                Text(
                                    text = errDetail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PocketColors.textPrimary
                                )
                            }
                        }
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

                Button(
                    onClick = {
                        onStartBuild(
                            VentoyBuildConfig(
                                outputDir = defaultDir,
                                outputName = finalImgName,
                                sizeMb = effectiveSizeMb,
                                scheme = scheme,
                                filesystem = filesystem,
                                win11BypassCheck = experience.bypassRequirements,
                                win11BypassNro = experience.bypassMicrosoftAccount,
                                experience = experience,
                                winUtil = winUtil,
                                driversDir = driversDir,
                                extraDriverPackDirs = selectedDriverPackDirs.toList(),
                                initialIsoToAdd = if (includeInitial) initialIso else null,
                                moveInitialIso = moveInitial,
                                autoMountAfterBuild = autoMount,
                                volumeLabel = if (volumeLabelEdited) volumeLabel.trim() else ""
                            )
                        )
                    },
                    enabled = assetInfo.ready && trimmedName.isNotBlank() && customValid && !updatingAssets && !capacityInsufficient,
                    modifier = Modifier.fillMaxWidth().height(PocketTokens.PrimaryButtonHeight),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Text(
                        text = if (capacityInsufficient) "Increase Drive Size (Need $minRequiredGb GB+)" else "Create $displaySize Multi-Boot Drive",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }
    }

    if (showDriversPicker) RootFolderPickerSheet("Select Drivers Folder (WinPE)", "Packed into driver archive for Windows ISOs", driversDir.ifBlank { defaultDir }, { showDriversPicker = false }) { driversDir = it; showDriversPicker = false }
    if (showBuilderPackManager) {
        DriverPackManagerSheet(
            defaultDir = defaultDir,
            onDismiss = { showBuilderPackManager = false },
            onPacksChanged = { builderPackReloadNonce++ },
            onMessage = onMessage
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VentoyManagerSheet(
    ventoyFile: ImageFile,
    isMountedToPc: Boolean,
    availableIsos: List<ImageFile>,
    presets: List<AutounattendPreset> = emptyList(),
    systemTweaksPresets: List<SystemTweaksPreset> = emptyList(),
    systemTweaksSearchHistory: List<String> = emptyList(),
    defaultDir: String = "/data/media/0/Download",
    onSavePreset: (AutounattendPreset) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    onSaveSystemTweaksPreset: (SystemTweaksPreset) -> Unit = {},
    onDeleteSystemTweaksPreset: (String) -> Unit = {},
    onUpdateSearchHistory: (List<String>) -> Unit = {},
    onDismiss: () -> Unit,
    onUnmountFirst: () -> Unit,
    onLibraryChanged: () -> Unit,
    onMessage: (String, Boolean) -> Unit,
    onBusyChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var contents by remember { mutableStateOf<VentoyDriveContents?>(null) }
    var opBusy by remember { mutableStateOf(false) }
    var mgrExp by remember { mutableStateOf(WindowsExperienceConfig()) }
    var mgrWinUtil by remember { mutableStateOf(WinUtilTweaksConfig()) }
    var mgrDriversDir by remember { mutableStateOf("") }
    var mgrSavedPacks by remember { mutableStateOf<List<DriverPackItem>>(emptyList()) }
    var mgrSelectedPackDirs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMgrPackManager by remember { mutableStateOf(false) }
    var mgrPackReloadNonce by remember { mutableIntStateOf(0) }
    var showMgrDriversPicker by remember { mutableStateOf(false) }
    var showMgrTweaksPanel by remember { mutableStateOf(false) }
    var opProgress by remember { mutableStateOf(WindowsBuildProgress()) }
    var showAddIsoPicker by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var moveOnAdd by remember { mutableStateOf(false) }
    var assetInfo by remember { mutableStateOf(VentoyImageBuilder.getInstalledVentoyInfo(context)) }
    var showMenuAppearancePanel by remember { mutableStateOf(false) }
    var mgrThemeId by remember { mutableStateOf(MultiBootThemePreset.STOCK.id) }
    var mgrMenuTimeout by remember { mutableIntStateOf(0) }
    var aliasEditTarget by remember { mutableStateOf<VentoyIsoItem?>(null) }
    var profileEditTarget by remember { mutableStateOf<VentoyIsoItem?>(null) }
    var persistenceEditTarget by remember { mutableStateOf<VentoyIsoItem?>(null) }

    fun reloadContents() {
        if (isMountedToPc) {
            loading = false
            return
        }
        scope.launch {
            loading = true
            val loaded = VentoyImageBuilder.inspectDriveContents(context, ventoyFile.path)
            contents = loaded
            if (loaded != null) {
                mgrExp = loaded.savedExperience
                mgrWinUtil = loaded.savedWinUtil
                mgrThemeId = loaded.menuConfig.themeId
                mgrMenuTimeout = loaded.menuConfig.menuTimeoutSec
            }
            loading = false
        }
    }

    LaunchedEffect(opBusy) {
        onBusyChanged(opBusy)
    }

    LaunchedEffect(defaultDir, mgrPackReloadNonce) {
        val loaded = DriverPackManager.listPacks(defaultDir)
        mgrSavedPacks = loaded
        val validDirs = loaded.map { it.dirPath }.toSet()
        mgrSelectedPackDirs = mgrSelectedPackDirs.intersect(validDirs)
    }

    LaunchedEffect(ventoyFile.path, isMountedToPc) {
        reloadContents()
    }

    ModalBottomSheet(
        onDismissRequest = { if (!opBusy) onDismiss() },
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PocketColors.accentBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Layers, null, tint = PocketColors.accentSoft, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Multi-Boot Manager", style = MaterialTheme.typography.titleLarge, color = PocketColors.textPrimary)
                    Text(ventoyFile.name, style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!isMountedToPc && !opBusy) {
                    IconButton(onClick = { reloadContents() }) {
                        Icon(Icons.Rounded.Refresh, "Refresh", tint = PocketColors.textSecondary)
                    }
                }
            }

            if (isMountedToPc) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PocketColors.warningBg,
                    border = BorderStroke(1.dp, PocketColors.warning)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.warning, modifier = Modifier.size(20.dp))
                            Text("Drive Currently Mounted to PC", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                        }
                        Text(
                            "Unmount this Multi-Boot drive before adding or removing ISOs on your phone to prevent filesystem corruption.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textSecondary
                        )
                        Button(
                            onClick = onUnmountFirst,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PocketColors.danger, contentColor = Color.White)
                        ) {
                            Icon(Icons.Rounded.Eject, null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Unmount Drive Now", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else if (opBusy) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PocketColors.surfaceInput,
                    border = BorderStroke(1.dp, PocketColors.accent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(opProgress.stage, style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary)
                        if (opProgress.detail.isNotBlank()) {
                            Text(opProgress.detail, style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                        if (opProgress.isIndeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)),
                                color = PocketColors.accent,
                                trackColor = PocketColors.surfaceSubtle
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { opProgress.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)),
                                color = PocketColors.accent,
                                trackColor = PocketColors.surfaceSubtle
                            )
                        }
                    }
                }
            } else if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = PocketColors.accent)
                        Text("Inspecting Multi-Boot partition...", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                    }
                }
            } else {
                val c = contents
                if (c == null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PocketColors.dangerBg,
                        border = BorderStroke(1.dp, PocketColors.danger)
                    ) {
                        Text(
                            "Could not mount Multi-Boot Partition 1. Check Root & Filesystems in Pre-Flight Diagnostics.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textPrimary
                        )
                    }
                } else {
                val usedBytes = (c.totalBytes - c.freeBytes).coerceAtLeast(0L)
                val usedRatio = if (c.totalBytes > 0L) (usedBytes.toFloat() / c.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

                // Storage & Partition Info Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PocketColors.surfaceInput,
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${c.info.scheme.label} • ${c.fsType}", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text("${UsbGadgetEngine.formatBytes(c.freeBytes)} free", style = MaterialTheme.typography.labelMedium, color = PocketColors.success)
                        }
                        LinearProgressIndicator(
                            progress = { usedRatio },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                            color = PocketColors.accent,
                            trackColor = PocketColors.surfaceSubtle
                        )
                        Text(
                            "Used ${UsbGadgetEngine.formatBytes(usedBytes)} of ${UsbGadgetEngine.formatBytes(c.totalBytes)} • ${c.files.size} bootable file(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textSecondary
                        )
                    }
                }

                // Add ISO + Non-destructive Bootloader Upgrade buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showAddIsoPicker = true },
                        modifier = Modifier.weight(1.2f).height(PocketTokens.PrimaryButtonHeight),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add ISO", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                opBusy = true
                                opProgress = WindowsBuildProgress("Updating Multi-Boot bootloader...", "Writing Engine v${assetInfo.version} (ISOs kept)", 0.5f, true)
                                val res = VentoyImageBuilder.upgradeVentoyBootloader(context, ventoyFile.path)
                                opBusy = false
                                onMessage(res.userMessage, !res.success)
                            }
                        },
                        enabled = assetInfo.ready,
                        modifier = Modifier.weight(1f).height(PocketTokens.PrimaryButtonHeight),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Icon(Icons.Rounded.SystemUpdateAlt, null, tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync v${assetInfo.version}", fontSize = 12.sp, color = PocketColors.textPrimary, maxLines = 1)
                    }
                }

                // Reclaim Sparse Free Space + Non-Destructive Resize row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                opBusy = true
                                opProgress = WindowsBuildProgress("Reclaiming free space...", "Zeroing deleted blocks & punching sparse holes", 0.3f, true)
                                val res = VentoyImageBuilder.reclaimSparseSpace(context, ventoyFile.path) { opProgress = it }
                                opBusy = false
                                onMessage(res.userMessage, !res.success)
                                if (res.success) onLibraryChanged()
                                reloadContents()
                            }
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Icon(Icons.Rounded.CleaningServices, null, tint = PocketColors.success, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Reclaim Space", fontSize = 12.sp, color = PocketColors.textPrimary, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showResizeDialog = true },
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Icon(Icons.Rounded.AspectRatio, null, tint = PocketColors.infoBlue, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Resize Drive", fontSize = 12.sp, color = PocketColors.textPrimary, maxLines = 1)
                    }
                }

                // ISOs inside Ventoy drive
                Text("ISOs Inside This Drive", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                if (c.files.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PocketColors.surfaceInput,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Info, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp))
                            Text("No ISOs inside yet. Tap 'Add ISO' above to add Windows 11, Linux, or rescue ISOs.", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val hasDriveTweaks = c.savedExperience.hasAnyActive() || c.savedWinUtil.hasAnyActive()
                        c.files.forEach { item ->
                            val isWinIso = IsoInspector.quickGuessByName(item.name) == IsoOsType.WINDOWS
                            val winKind = if (isWinIso) WindowsCustomizationEngine.detectWindowsOsKind(item.name) else WindowsOsKind.UNKNOWN
                            val eligible = VentoyImageBuilder.isWindowsDesktopAutoInstallEligible(item.name)
                            val linuxPersistEligible = VentoyPersistenceHelper.isLinuxPersistenceEligible(item.name)
                            val isoArch = if (isWinIso) WindowsCustomizationEngine.detectProcessorArchitecture(item.name) else ""
                            val normRel = VentoyImageBuilder.normalizeVentoyRelPath(item.relativePath)
                            val tweaksOnForIso = eligible && normRel !in c.excludedAutoInstallIsos
                            val assignedProfiles = if (!tweaksOnForIso) emptyList() else c.isoProfileIds[normRel].orEmpty().ifEmpty { listOf(VentoyImageBuilder.PROFILE_ID_DEFAULT) }
                            val aliasText = c.menuConfig.menuAliases[normRel].orEmpty()
                            val persEntry = c.persistenceEntries.firstOrNull { it.imagePath.equals(normRel, ignoreCase = true) }
                            val metaSubtitle = buildString {
                                if (aliasText.isNotBlank()) {
                                    append(item.name)
                                    append(" · ")
                                }
                                append(UsbGadgetEngine.formatBytes(item.sizeBytes))
                                if (eligible) {
                                    append(" · ")
                                    append(winKind.label)
                                    append(" · ")
                                    append(isoArch)
                                } else if (isWinIso) {
                                    append(" · ")
                                    append(winKind.label)
                                    append(" (Stock only)")
                                } else if (persEntry != null) {
                                    append(" · Persist ")
                                    append(if (persEntry.sizeBytes > 0L) UsbGadgetEngine.formatBytes(persEntry.sizeBytes) else persEntry.styleLabel)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, PocketColors.border)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Album, null, tint = PocketColors.accentSoft, modifier = Modifier.size(19.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = aliasText.ifBlank { item.name },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = PocketColors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(metaSubtitle, style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (eligible) {
                                        val activePill = assignedProfiles.isNotEmpty() && (assignedProfiles.any { it != VentoyImageBuilder.PROFILE_ID_DEFAULT } || hasDriveTweaks)
                                        val pillText = when (assignedProfiles.size) {
                                            0 -> "Stock"
                                            1 -> if (assignedProfiles.first() == VentoyImageBuilder.PROFILE_ID_DEFAULT) "Tweaks" else VentoyImageBuilder.profileDisplayLabel(assignedProfiles.first()).take(10)
                                            else -> "${assignedProfiles.size} Profiles"
                                        }
                                        Surface(
                                            modifier = Modifier.bounceClick { profileEditTarget = item },
                                            shape = RoundedCornerShape(8.dp),
                                            color = when {
                                                activePill -> PocketColors.accentBg
                                                tweaksOnForIso -> PocketColors.surfaceSubtle
                                                else -> PocketColors.surfaceCard
                                            },
                                            border = BorderStroke(
                                                0.75.dp,
                                                if (activePill) PocketColors.accent else PocketColors.border
                                            )
                                        ) {
                                            Text(
                                                text = pillText,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = when {
                                                    activePill -> PocketColors.accentSoft
                                                    tweaksOnForIso -> PocketColors.textPrimary
                                                    else -> PocketColors.textMuted
                                                }
                                            )
                                        }
                                    } else if (linuxPersistEligible) {
                                        val hasPers = persEntry != null
                                        Surface(
                                            modifier = Modifier.bounceClick { persistenceEditTarget = item },
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (hasPers) PocketColors.successBg else PocketColors.surfaceCard,
                                            border = BorderStroke(0.75.dp, if (hasPers) PocketColors.success else PocketColors.border)
                                        ) {
                                            Text(
                                                text = if (hasPers) "Persist" else "+ Persist",
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (hasPers) PocketColors.success else PocketColors.textSecondary
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { aliasEditTarget = item },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, "Boot menu alias", tint = PocketColors.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                opBusy = true
                                                opProgress = WindowsBuildProgress("Removing ISO...", item.name, 0.5f, true)
                                                val res = VentoyImageBuilder.removeIsoFromVentoy(context, ventoyFile.path, item.relativePath)
                                                opBusy = false
                                                onMessage(res.userMessage, !res.success)
                                                reloadContents()
                                            }
                                        },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, "Remove", tint = PocketColors.danger, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Win 11 Bypass toggles inside ventoy.json
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PocketColors.surfaceInput,
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Win 11 TPM / Secure Boot Bypass", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                Text("Skips Win 11 TPM, Secure Boot & RAM checks", fontSize = 11.sp, color = PocketColors.textSecondary)
                            }
                            PocketSwitch(
                                checked = c.win11BypassCheck,
                                onCheckedChange = { newVal ->
                                    scope.launch {
                                        opBusy = true
                                        opProgress = WindowsBuildProgress("Updating boot config...", "", 0.5f, true)
                                        val res = VentoyImageBuilder.updateVentoyJson(context, ventoyFile.path, newVal, c.win11BypassNro)
                                        opBusy = false
                                        onMessage(res.userMessage, !res.success)
                                        reloadContents()
                                    }
                                },
                                activeColor = PocketColors.success
                            )
                        }
                        PocketHairlineDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Win 11 Offline Account Bypass (NRO)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                Text("Enables offline local account during Win 11 OOBE", fontSize = 11.sp, color = PocketColors.textSecondary)
                            }
                            PocketSwitch(
                                checked = c.win11BypassNro,
                                onCheckedChange = { newVal ->
                                    scope.launch {
                                        opBusy = true
                                        opProgress = WindowsBuildProgress("Updating boot config...", "", 0.5f, true)
                                        val res = VentoyImageBuilder.updateVentoyJson(context, ventoyFile.path, c.win11BypassCheck, newVal)
                                        opBusy = false
                                        onMessage(res.userMessage, !res.success)
                                        reloadContents()
                                    }
                                },
                                activeColor = PocketColors.success
                            )
                        }
                    }
                }

                // Boot Menu Theme & Timeout Card (Feature 2.2)
                Surface(
                    modifier = Modifier.fillMaxWidth().bounceClick { showMenuAppearancePanel = !showMenuAppearancePanel },
                    shape = RoundedCornerShape(12.dp),
                    color = PocketColors.surfaceInput,
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Palette, null, tint = PocketColors.infoBlue, modifier = Modifier.size(19.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Boot Menu Theme & Timeout", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text(
                                "${MultiBootThemePreset.fromId(mgrThemeId).label} · ${if (mgrMenuTimeout > 0) "${mgrMenuTimeout}s timeout" else "No auto-boot timeout"} · ${c.menuConfig.menuAliases.size} alias(es)",
                                fontSize = 11.sp,
                                color = PocketColors.textSecondary
                            )
                        }
                        Text(if (showMenuAppearancePanel) "Hide" else "Edit", style = MaterialTheme.typography.labelSmall, color = PocketColors.accentSoft)
                    }
                }
                if (showMenuAppearancePanel) {
                    Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("GRUB Menu Theme", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                MultiBootThemePreset.entries.forEach { preset ->
                                    val sel = mgrThemeId == preset.id
                                    Surface(
                                        modifier = Modifier.weight(1f).bounceClick { mgrThemeId = preset.id },
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (sel) PocketColors.accentBg else PocketColors.surfaceCard,
                                        border = BorderStroke(if (sel) 1.5.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                    ) {
                                        Box(modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                                            Text(preset.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            Text("Menu Timeout", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0 to "Off", 5 to "5s", 10 to "10s", 15 to "15s", 30 to "30s").forEach { (sec, lbl) ->
                                    val sel = mgrMenuTimeout == sec
                                    Surface(
                                        modifier = Modifier.weight(1f).bounceClick { mgrMenuTimeout = sec },
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (sel) PocketColors.accentBg else PocketColors.surfaceCard,
                                        border = BorderStroke(if (sel) 1.5.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary)
                                        }
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    scope.launch {
                                        opBusy = true
                                        opProgress = WindowsBuildProgress("Applying boot menu theme...", MultiBootThemePreset.fromId(mgrThemeId).label, 0.5f, true)
                                        val nextCfg = c.menuConfig.copy(themeId = mgrThemeId, menuTimeoutSec = mgrMenuTimeout)
                                        val existingRels = c.files.map { VentoyImageBuilder.normalizeVentoyRelPath(it.relativePath) }.toSet()
                                        val res = VentoyImageBuilder.updateMenuConfig(context, ventoyFile.path, nextCfg, existingRels)
                                        opBusy = false
                                        onMessage(res.userMessage, !res.success)
                                        reloadContents()
                                    }
                                },
                                enabled = !opBusy,
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                            ) {
                                Text("Save Menu Theme & Timeout", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Surface(modifier = Modifier.fillMaxWidth().bounceClick { showMgrTweaksPanel = !showMgrTweaksPanel }, shape = RoundedCornerShape(12.dp), color = PocketColors.accentBg, border = BorderStroke(0.75.dp, PocketColors.accent)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Windows Experience, System Tweaks & Drivers Injection", style = MaterialTheme.typography.labelLarge, color = PocketColors.accentSoft)
                            Text("Applies per architecture to Windows 10/11 ISOs marked Tweaks (Win 7/Server & Linux untouched)", fontSize = 11.sp, color = PocketColors.textSecondary)
                        }
                        Text(if (showMgrTweaksPanel) "Hide" else "Configure", style = MaterialTheme.typography.labelSmall, color = PocketColors.accentSoft)
                    }
                }
                if (showMgrTweaksPanel) {
                    WindowsTwoPaneCustomizer(
                        osKind = WindowsOsKind.WIN11, arch = "amd64", experience = mgrExp, winUtil = mgrWinUtil, presets = presets,
                        systemTweaksPresets = systemTweaksPresets,
                        systemTweaksSearchHistory = systemTweaksSearchHistory,
                        showCa2023Option = false, showCustomScriptOption = false, defaultBrowseDir = defaultDir,
                        onExperienceChange = { mgrExp = it }, onWinUtilChange = { mgrWinUtil = it },
                        onApplyPreset = { p ->
                            mgrExp = WindowsExperienceConfig(p.bypassRequirements, p.bypassMicrosoftAccount, p.disableDataCollection, p.disableBitLocker, p.qolImprovements, p.disableSponsoredApps, false, p.skipWifiSetup, p.createLocalUser, p.localUsername, "", p.skipOobe, p.useCa2023Bootloaders, p.applySkuSiPolicy)
                            mgrWinUtil = WinUtilTweaksConfig.fromJsonString(p.winUtilJson, legacyClassicContextMenu = p.classicContextMenu)
                        },
                        onSavePreset = { n -> onSavePreset(AutounattendPreset(n, PartitionScheme.GPT.name, mgrExp.bypassRequirements, mgrExp.bypassMicrosoftAccount, mgrExp.disableDataCollection, mgrExp.disableBitLocker, mgrExp.disableSponsoredApps, false, mgrExp.skipWifiSetup, mgrExp.createLocalUser, mgrExp.localUsername, mgrExp.skipOobe, mgrExp.qolImprovements, mgrExp.useCa2023Bootloaders, mgrExp.applySkuSiPolicy, mgrWinUtil.toJsonString())) },
                        onDeletePreset = onDeletePreset,
                        onSaveSystemTweaksPreset = onSaveSystemTweaksPreset,
                        onDeleteSystemTweaksPreset = onDeleteSystemTweaksPreset,
                        onUpdateSearchHistory = onUpdateSearchHistory
                    )
                    DriverPackMultiSelectorSection(
                        packs = mgrSavedPacks,
                        selectedPackDirs = mgrSelectedPackDirs,
                        onTogglePack = { dir ->
                            mgrSelectedPackDirs = if (dir in mgrSelectedPackDirs) mgrSelectedPackDirs - dir else mgrSelectedPackDirs + dir
                        },
                        onOpenManager = { showMgrPackManager = true }
                    )
                    Surface(modifier = Modifier.fillMaxWidth().bounceClick { showMgrDriversPicker = true }, shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.75.dp, if (mgrDriversDir.isNotBlank()) PocketColors.accent else PocketColors.border)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.FolderOpen, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Inject Drivers Folder (WinPE)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                Text(when { mgrDriversDir.isNotBlank() -> mgrDriversDir.replace("/data/media/0", "/sdcard"); c.hasDriversZip -> "Active (Driver archive ready) · Tap to replace"; else -> "Tap to browse folder" }, fontSize = 11.sp, color = PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (mgrDriversDir.isNotBlank()) {
                                IconButton(onClick = { mgrDriversDir = "" }, modifier = Modifier.size(44.dp)) {
                                    Icon(Icons.Rounded.Close, "Clear selected folder", tint = PocketColors.textMuted, modifier = Modifier.size(15.dp))
                                }
                            } else if (c.hasDriversZip) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            opBusy = true
                                            opProgress = WindowsBuildProgress("Removing drivers archive...", ventoyFile.name, 0.5f, true)
                                            val res = VentoyImageBuilder.updateVentoyCustomization(context, ventoyFile.path, mgrExp, mgrWinUtil, driversDir = "", extraDriverPackDirs = emptyList())
                                            opBusy = false
                                            onMessage(if (res.success) "Removed WinPE drivers archive from Multi-Boot drive." else res.userMessage, !res.success)
                                            reloadContents()
                                        }
                                    },
                                    enabled = !opBusy
                                ) {
                                    Text("Clear", fontSize = 11.sp, color = PocketColors.danger)
                                }
                            } else {
                                Text("Browse", style = MaterialTheme.typography.labelSmall, color = PocketColors.accentSoft)
                            }
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                opBusy = true; opProgress = WindowsBuildProgress("Applying Windows tweaks & drivers...", ventoyFile.name, 0.5f, true)
                                val res = VentoyImageBuilder.updateVentoyCustomization(
                                    context = context,
                                    ventoyImgPath = ventoyFile.path,
                                    experience = mgrExp,
                                    winUtil = mgrWinUtil,
                                    driversDir = mgrDriversDir.ifBlank { null },
                                    extraDriverPackDirs = mgrSelectedPackDirs.toList().takeIf { it.isNotEmpty() }
                                )
                                opBusy = false; onMessage(res.userMessage, !res.success); reloadContents()
                            }
                        },
                        enabled = !opBusy, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                    ) { Text("Apply Tweaks & Drivers to Multi-Boot", fontWeight = FontWeight.SemiBold) }
                }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }
    }

    // Pick ISO from PocketBoot Library to Copy or Move into Ventoy
    if (showAddIsoPicker) {
        val isoCandidates = remember(availableIsos) {
            availableIsos.filter { it.name.endsWith(".iso", true) }
        }
        AlertDialog(
            onDismissRequest = { showAddIsoPicker = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Add ISO to Multi-Boot Drive", color = PocketColors.textPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PocketColors.surfaceInput,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { moveOnAdd = !moveOnAdd }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Move ISO (instead of Copy)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                Text("Avoids keeping 2 copies on phone", fontSize = 11.sp, color = PocketColors.textSecondary)
                            }
                            PocketSwitch(checked = moveOnAdd, onCheckedChange = { moveOnAdd = it }, activeColor = PocketColors.warning)
                        }
                    }

                    contents?.let { cNow ->
                        Text(
                            text = "Available inside drive: ${UsbGadgetEngine.formatBytes(cNow.freeBytes)}",
                            fontSize = 11.sp,
                            color = PocketColors.textSecondary
                        )
                    }

                    if (isoCandidates.isEmpty()) {
                        Text(
                            "No .iso files found in Your Drive Files. Tap Browse on Home to add an ISO first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textSecondary
                        )
                    } else {
                        val currentDriveFree = contents?.freeBytes ?: -1L
                        val existingByName = contents?.files?.filter { !it.relativePath.trimStart('/').contains('/') }?.associateBy { it.name.lowercase() }.orEmpty()
                        isoCandidates.forEach { iso ->
                            val existingMatch = existingByName[iso.name.lowercase()]
                            val availForThisIso = if (currentDriveFree >= 0L) currentDriveFree + (existingMatch?.sizeBytes ?: 0L) else -1L
                            val isoTooLarge = availForThisIso >= 0L && (iso.sizeBytes + (32L * 1024L * 1024L)) > availForThisIso
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isoTooLarge) {
                                        showAddIsoPicker = false
                                        scope.launch {
                                            opBusy = true
                                            opProgress = WindowsBuildProgress("Preparing...", iso.name, 0.03f, true)
                                            val res = VentoyImageBuilder.addIsoToVentoy(
                                                context = context,
                                                ventoyImgPath = ventoyFile.path,
                                                sourceIsoPath = iso.path,
                                                moveFile = moveOnAdd,
                                                onProgress = { opProgress = it }
                                            )
                                            opBusy = false
                                            onMessage(res.userMessage, !res.success)
                                            if (res.success && moveOnAdd) onLibraryChanged()
                                            reloadContents()
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isoTooLarge) PocketColors.dangerBg else PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, if (isoTooLarge) PocketColors.danger else PocketColors.border)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        if (isoTooLarge) Icons.Rounded.ErrorOutline else Icons.Rounded.Album,
                                        null,
                                        tint = if (isoTooLarge) PocketColors.danger else PocketColors.accentSoft,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            iso.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (isoTooLarge) PocketColors.textSecondary else PocketColors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isoTooLarge) {
                                                "${UsbGadgetEngine.formatBytes(iso.sizeBytes)} · Too large (use Resize Drive first)"
                                            } else if (existingMatch != null) {
                                                "${UsbGadgetEngine.formatBytes(iso.sizeBytes)} · Replaces existing ISO"
                                            } else {
                                                UsbGadgetEngine.formatBytes(iso.sizeBytes)
                                            },
                                            fontSize = 11.sp,
                                            color = if (isoTooLarge) PocketColors.danger else PocketColors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddIsoPicker = false }) {
                    Text("Close", color = PocketColors.textSecondary)
                }
            }
        )
    }

    if (showResizeDialog) {
        val liveBytes = contents?.info?.totalBytes?.takeIf { it > 0L } ?: ventoyFile.sizeBytes
        val currentGb = ((liveBytes / (1024L * 1024L * 1024L)).toInt()).coerceAtLeast(1)
        val optionsGb = listOf(16, 32, 64, 128).filter { it > currentGb }
        var targetGb by remember(currentGb) { mutableIntStateOf(optionsGb.firstOrNull() ?: (currentGb + 16).coerceAtMost(128)) }

        AlertDialog(
            onDismissRequest = { showResizeDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Resize Multi-Boot Drive", color = PocketColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Non-destructively expand ${ventoyFile.name} (currently ~${currentGb} GB) while keeping all stored ISOs intact.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )
                    if (optionsGb.isEmpty()) {
                        Text("This drive is already at maximum capacity (128 GB).", style = MaterialTheme.typography.bodySmall, color = PocketColors.warning)
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            optionsGb.forEach { gb ->
                                val sel = targetGb == gb
                                Surface(
                                    modifier = Modifier.weight(1f).bounceClick { targetGb = gb },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (sel) 1.5.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                                        Text("${gb} GB", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResizeDialog = false
                        scope.launch {
                            opBusy = true
                            opProgress = WindowsBuildProgress("Resizing Multi-Boot drive...", "Expanding to $targetGb GB", 0.1f, true)
                            val res = VentoyImageBuilder.resizeVentoyImage(context, ventoyFile.path, targetGb) { opProgress = it }
                            opBusy = false
                            onMessage(res.userMessage, !res.success)
                            if (res.success) onLibraryChanged()
                            reloadContents()
                        }
                    },
                    enabled = optionsGb.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Text("Expand to $targetGb GB", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResizeDialog = false }) {
                    Text("Cancel", color = PocketColors.textSecondary)
                }
            }
        )
    }

    if (showMgrDriversPicker) RootFolderPickerSheet("Select Drivers Folder (WinPE)", "Packed into driver archive for Windows ISOs", mgrDriversDir.ifBlank { defaultDir }, { showMgrDriversPicker = false }) { mgrDriversDir = it; showMgrDriversPicker = false }
    if (showMgrPackManager) {
        DriverPackManagerSheet(
            defaultDir = defaultDir,
            onDismiss = { showMgrPackManager = false },
            onPacksChanged = { mgrPackReloadNonce++ },
            onMessage = onMessage
        )
    }

    // Boot Menu Alias Dialog (Feature 2.2)
    aliasEditTarget?.let { item ->
        val cNow = contents
        val normRel = VentoyImageBuilder.normalizeVentoyRelPath(item.relativePath)
        var aliasInput by remember(item.relativePath) { mutableStateOf(cNow?.menuConfig?.menuAliases?.get(normRel).orEmpty()) }
        AlertDialog(
            onDismissRequest = { aliasEditTarget = null },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Boot Menu Display Name", color = PocketColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Set a friendly name shown on your PC's Multi-Boot menu for ${item.name} (leave blank to use filename).",
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it.take(80) },
                        label = { Text("Menu Alias") },
                        placeholder = { Text("e.g., Windows 11 Pro (Clean)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetItem = item
                        val newAlias = aliasInput.trim()
                        aliasEditTarget = null
                        if (cNow != null) {
                            scope.launch {
                                opBusy = true
                                opProgress = WindowsBuildProgress("Saving boot alias...", targetItem.name, 0.5f, true)
                                val nextAliases = cNow.menuConfig.menuAliases.toMutableMap().apply {
                                    if (newAlias.isEmpty()) remove(normRel) else put(normRel, newAlias)
                                }
                                val existingRels = cNow.files.map { VentoyImageBuilder.normalizeVentoyRelPath(it.relativePath) }.toSet()
                                val res = VentoyImageBuilder.updateMenuConfig(
                                    context,
                                    ventoyFile.path,
                                    cNow.menuConfig.copy(menuAliases = nextAliases),
                                    existingRels
                                )
                                opBusy = false
                                onMessage(res.userMessage, !res.success)
                                reloadContents()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { aliasEditTarget = null }) { Text("Cancel", color = PocketColors.textSecondary) }
            }
        )
    }

    // Per-ISO Multi-Template Tweak Profile Picker Dialog (Feature 2.3)
    profileEditTarget?.let { item ->
        val cNow = contents
        val normRel = VentoyImageBuilder.normalizeVentoyRelPath(item.relativePath)
        val initialSelected = remember(item.relativePath, cNow) {
            if (cNow == null || normRel in cNow.excludedAutoInstallIsos) emptySet()
            else cNow.isoProfileIds[normRel].orEmpty().ifEmpty { listOf(VentoyImageBuilder.PROFILE_ID_DEFAULT) }.toSet()
        }
        var selectedIds by remember(item.relativePath) { mutableStateOf(initialSelected) }
        val builtInProfiles = listOf(
            VentoyImageBuilder.PROFILE_ID_DEFAULT to "Drive Default (uses your configured drive tweaks)",
            VentoyImageBuilder.PROFILE_ID_STANDARD to "Standard Preset (balanced privacy & clean UI)",
            VentoyImageBuilder.PROFILE_ID_MINIMAL to "Minimal Preset (lightweight safe tweaks)",
            VentoyImageBuilder.PROFILE_ID_ADVANCED to "Advanced Preset (deep debloat & services)",
            VentoyImageBuilder.PROFILE_ID_DESKTOP to "Desktop Preset (performance & power plan)",
            VentoyImageBuilder.PROFILE_ID_LAPTOP to "Laptop Preset (battery-friendly defaults)"
        )
        val customMap = remember(systemTweaksPresets) {
            systemTweaksPresets.associate { "custom:${it.name}" to it.tweaksJson }
        }

        AlertDialog(
            onDismissRequest = { profileEditTarget = null },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Boot-Menu Tweak Profiles", color = PocketColors.textPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Pick 0 profiles for Stock install, 1 for auto-apply, or 2+ to choose the tweak profile right at your PC boot menu for ${item.name}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth().bounceClick { selectedIds = emptySet() },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedIds.isEmpty()) PocketColors.warningBg else PocketColors.surfaceInput,
                        border = BorderStroke(if (selectedIds.isEmpty()) 1.25.dp else 0.5.dp, if (selectedIds.isEmpty()) PocketColors.warning else PocketColors.border)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(if (selectedIds.isEmpty()) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = if (selectedIds.isEmpty()) PocketColors.warning else PocketColors.textMuted, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Stock (No Auto-Unattend)", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                Text("Boot untouched ISO without injecting scripts", fontSize = 11.sp, color = PocketColors.textSecondary)
                            }
                        }
                    }
                    builtInProfiles.forEach { (pid, desc) ->
                        val checked = pid in selectedIds
                        Surface(
                            modifier = Modifier.fillMaxWidth().bounceClick {
                                selectedIds = if (checked) selectedIds - pid else selectedIds + pid
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (checked) PocketColors.accentBg else PocketColors.surfaceInput,
                            border = BorderStroke(if (checked) 1.25.dp else 0.5.dp, if (checked) PocketColors.accent else PocketColors.border)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = if (checked) PocketColors.accent else PocketColors.textMuted, modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(VentoyImageBuilder.profileDisplayLabel(pid), style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                    Text(desc, fontSize = 11.sp, color = PocketColors.textSecondary)
                                }
                            }
                        }
                    }
                    systemTweaksPresets.forEach { preset ->
                        val pid = "custom:${preset.name}"
                        val checked = pid in selectedIds
                        Surface(
                            modifier = Modifier.fillMaxWidth().bounceClick {
                                selectedIds = if (checked) selectedIds - pid else selectedIds + pid
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (checked) PocketColors.accentBg else PocketColors.surfaceInput,
                            border = BorderStroke(if (checked) 1.25.dp else 0.5.dp, if (checked) PocketColors.accent else PocketColors.border)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, tint = if (checked) PocketColors.accent else PocketColors.textMuted, modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(preset.name, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                    Text("Saved System Tweaks preset", fontSize = 11.sp, color = PocketColors.textSecondary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetItem = item
                        val chosen = selectedIds.toList()
                        profileEditTarget = null
                        scope.launch {
                            opBusy = true
                            opProgress = WindowsBuildProgress("Writing boot templates...", targetItem.name, 0.5f, true)
                            val res = VentoyImageBuilder.setIsoProfileSelection(context, ventoyFile.path, targetItem.relativePath, chosen, customMap)
                            opBusy = false
                            onMessage(res.userMessage, !res.success)
                            reloadContents()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) { Text("Apply (${selectedIds.size})", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { profileEditTarget = null }) { Text("Cancel", color = PocketColors.textSecondary) }
            }
        )
    }

    // Linux Persistence Dialog Inside Multi-Boot (Feature 2.1)
    persistenceEditTarget?.let { item ->
        val cNow = contents
        val normRel = VentoyImageBuilder.normalizeVentoyRelPath(item.relativePath)
        val existingPers = cNow?.persistenceEntries?.firstOrNull { it.imagePath.equals(normRel, ignoreCase = true) }
        val defaultStyle = remember(item.name) { VentoyPersistenceHelper.guessStyleForIso(item.name) }
        var persStyle by remember(item.relativePath) { mutableStateOf(defaultStyle) }
        var persSizeGb by remember(item.relativePath) { mutableIntStateOf(4) }

        AlertDialog(
            onDismissRequest = { persistenceEditTarget = null },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Linux Live Persistence", color = PocketColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Attach an ext4 persistence .dat volume inside Multi-Boot for ${item.name} so installed packages and files survive reboots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )
                    if (existingPers != null) {
                        Surface(shape = RoundedCornerShape(9.dp), color = PocketColors.successBg, border = BorderStroke(0.5.dp, PocketColors.success)) {
                            Text(
                                "Active: ${existingPers.backendPath.substringAfterLast('/')} (${if (existingPers.sizeBytes > 0L) UsbGadgetEngine.formatBytes(existingPers.sizeBytes) else existingPers.styleLabel})",
                                modifier = Modifier.fillMaxWidth().padding(9.dp),
                                fontSize = 11.sp,
                                color = PocketColors.textPrimary
                            )
                        }
                    }
                    Text("Distro Format", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            LinuxPersistenceStyle.UBUNTU_WRITABLE to "Ubuntu 20+",
                            LinuxPersistenceStyle.CASPER_RW to "Pop! / Legacy",
                            LinuxPersistenceStyle.DEBIAN_KALI to "Debian / Kali"
                        ).forEach { (st, lbl) ->
                            val sel = persStyle == st
                            Surface(
                                modifier = Modifier.weight(1f).bounceClick { persStyle = st },
                                shape = RoundedCornerShape(9.dp),
                                color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                border = BorderStroke(if (sel) 1.5.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                    Text(lbl, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary)
                                }
                            }
                        }
                    }
                    Text("Persistence Size", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 4, 8, 16).forEach { gb ->
                            val sel = persSizeGb == gb
                            Surface(
                                modifier = Modifier.weight(1f).bounceClick { persSizeGb = gb },
                                shape = RoundedCornerShape(9.dp),
                                color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                border = BorderStroke(if (sel) 1.5.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text("${gb}G", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary)
                                }
                            }
                        }
                    }
                    if (existingPers != null) {
                        OutlinedButton(
                            onClick = {
                                val targetItem = item
                                persistenceEditTarget = null
                                scope.launch {
                                    opBusy = true
                                    opProgress = WindowsBuildProgress("Removing persistence...", targetItem.name, 0.5f, true)
                                    val res = VentoyPersistenceHelper.removePersistenceForIso(context, ventoyFile.path, targetItem.relativePath)
                                    opBusy = false
                                    onMessage(res.userMessage, !res.success)
                                    if (res.success) onLibraryChanged()
                                    reloadContents()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            shape = RoundedCornerShape(9.dp),
                            border = BorderStroke(0.5.dp, PocketColors.danger)
                        ) {
                            Text("Remove Existing Persistence", fontSize = 12.sp, color = PocketColors.danger)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetItem = item
                        val gb = persSizeGb
                        val st = persStyle
                        persistenceEditTarget = null
                        scope.launch {
                            opBusy = true
                            opProgress = WindowsBuildProgress("Creating ${gb} GB persistence...", targetItem.name, 0.2f, true)
                            val res = VentoyPersistenceHelper.createPersistenceForIso(context, ventoyFile.path, targetItem.relativePath, gb, st) { opProgress = it }
                            opBusy = false
                            onMessage(res.userMessage, !res.success)
                            if (res.success) onLibraryChanged()
                            reloadContents()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) { Text(if (existingPers != null) "Replace (${persSizeGb} GB)" else "Create (${persSizeGb} GB)", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { persistenceEditTarget = null }) { Text("Cancel", color = PocketColors.textSecondary) }
            }
        )
    }
}
