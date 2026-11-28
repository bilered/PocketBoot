package com.pocketboot.app

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

enum class LinuxUsbMode(val title: String, val subtitle: String) {
    WITHOUT_PERSISTENCE("Without Persistence", "Standard Live USB (Hybrid ISO or FAT32)"),
    WITH_PERSISTENCE("With Persistence", "Saves files, Wi-Fi & settings across reboots")
}

enum class LinuxWriteMethod(val title: String, val subtitle: String) {
    HYBRID_DD("Hybrid DD / ISO", "Fastest · keeps exact ISO sectors"),
    EXTRACTED_FAT32("Extracted FAT32 (ISO Mode)", "Clean FAT32 USB · best for tools & extra files")
}

enum class LinuxPersistenceStyle(val title: String, val volumeLabel: String, val subtitle: String) {
    UBUNTU_WRITABLE("Ubuntu 20+ / Mint / Rescuezilla", "writable", "ext4 labeled writable (modern Casper)"),
    CASPER_RW("Pop!_OS / Legacy Ubuntu", "casper-rw", "ext4 labeled casper-rw"),
    DEBIAN_KALI("Debian / Kali / GParted / Tails", "persistence", "ext4 labeled persistence + persistence.conf")
}

data class LinuxBuildConfig(
    val isoFile: ImageFile,
    val outputDir: String,
    val outputName: String,
    val usbMode: LinuxUsbMode = LinuxUsbMode.WITH_PERSISTENCE,
    val writeMethod: LinuxWriteMethod = LinuxWriteMethod.HYBRID_DD,
    val scheme: PartitionScheme = PartitionScheme.MBR,
    val persistenceGb: Int = 2,
    val style: LinuxPersistenceStyle = LinuxPersistenceStyle.UBUNTU_WRITABLE,
    val autoMountAfterBuild: Boolean = true,
    val volumeLabel: String = "",
    val directBlockTarget: OtgUsbDevice? = null
)

object LinuxImageBuilder {
    private const val TEMP_LINUX_MNT = "/data/local/tmp/pb_linux_persist"
    private const val TEMP_FAT_MNT = "/data/local/tmp/pb_linux_fat"
    private const val FAT32_MAX_FILE_BYTES = 4_294_967_295L

    private fun nowTs(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    suspend fun inspectMaxFileBytesInIso(context: Context, isoPath: String): Long = withContext(Dispatchers.IO) {
        val bundled7z = WindowsImageBuilder.findBundledBinary(context, "lib7zzs.so", listOf("7zz", "7z")) ?: return@withContext 0L
        Shell.cmd("${shellQuote(bundled7z)} l -slt ${shellQuote(UsbGadgetEngine.resolveDirectPath(isoPath))} 2>/dev/null").exec().out
            .asSequence()
            .map { it.trim() }
            .filter { it.startsWith("Size = ") }
            .mapNotNull { it.removePrefix("Size = ").trim().toLongOrNull() }
            .maxOrNull() ?: 0L
    }

    fun cancelActiveBuild() {
        BuildThermalHelper.cancelActiveBuildProcess()
    }

    suspend fun buildLinuxPersistentImage(context: Context, config: LinuxBuildConfig, onProgress: (WindowsBuildProgress) -> Unit): ActionResult = withContext(Dispatchers.IO) {
        BuildThermalHelper.resetCancelFlag()
        if (config.writeMethod == LinuxWriteMethod.EXTRACTED_FAT32) buildExtractedFat32Image(context, config, onProgress)
        else buildHybridDdImage(context, config, onProgress)
    }

    private suspend fun buildHybridDdImage(context: Context, config: LinuxBuildConfig, onProgress: (WindowsBuildProgress) -> Unit): ActionResult = withContext(Dispatchers.IO) {
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        val isDirectBlock = config.directBlockTarget != null
        val safeDir = UsbGadgetEngine.resolveDirectPath(config.outputDir).trimEnd('/')
        val safeName = if (isDirectBlock) config.directBlockTarget?.displayTitle ?: "USB-C OTG Drive" else UsbGadgetEngine.sanitizeOutputFileName(config.outputName, ".img", "linux-usb")
        var outPath = if (isDirectBlock) config.directBlockTarget?.devPath.orEmpty() else "$safeDir/$safeName"
        val srcIsoPath = UsbGadgetEngine.resolveDirectPath(config.isoFile.path)
        val qIso = shellQuote(srcIsoPath)
        var qOut = shellQuote(outPath); val qDir = shellQuote(safeDir)
        var loopDev = ""
        var loopIsLoopNode = true
        var verifiedOtg: OtgUsbDevice? = null
        fun cleanup(deletePartial: Boolean) {
            BuildThermalHelper.unmountAndDetachClean(
                mountPoints = listOf(TEMP_LINUX_MNT),
                loopDevices = if (loopIsLoopNode && loopDev.isNotBlank()) listOf(loopDev) else emptyList(),
                removeDirs = true
            )
            if (deletePartial && !isDirectBlock) Shell.cmd("rm -f $qOut 2>/dev/null").exec()
        }
        return@withContext runCatching {
            if (!Shell.getShell().isRoot) return@withContext ActionResult(false, "Root access is required.", entries = entries)
            if (!isDirectBlock) {
                if (outPath.equals(srcIsoPath, ignoreCase = true)) return@withContext ActionResult(false, "Output image cannot overwrite the source ISO.", entries = entries)
                if (UsbGadgetEngine.getStatus().mountedLunFor(outPath) != null) return@withContext ActionResult(false, "$safeName is currently mounted to your PC. Unmount it before overwriting.", entries = entries)
            }
            onProgress(WindowsBuildProgress("Checking ISO image...", config.isoFile.name, 0.05f, true))
            val isoSize = Shell.cmd("stat -c '%s' $qIso 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: config.isoFile.sizeBytes
            if (isoSize <= 0L) return@withContext ActionResult(false, "Source ISO not found or empty.", entries = entries)
            val withPersistence = config.usbMode == LinuxUsbMode.WITH_PERSISTENCE
            val alignBytes = 1024L * 1024L
            val persistOffsetBytes = ((isoSize + alignBytes - 1L) / alignBytes) * alignBytes
            val persistBytes: Long
            if (isDirectBlock) {
                val reqTarget = config.directBlockTarget ?: return@withContext ActionResult(false, "No physical USB-C OTG drive selected.", entries = entries)
                onProgress(WindowsBuildProgress("Verifying USB-C drive...", reqTarget.displayTitle, 0.08f, true))
                val (prepDev, prepErr) = OtgUsbFlasher.verifyPhysicalOtgDisk(reqTarget)
                if (prepDev == null) return@withContext ActionResult(false, prepErr ?: "USB-C OTG drive preparation failed.", entries = entries)
                verifiedOtg = prepDev
                outPath = prepDev.devPath
                qOut = shellQuote(outPath)
                val mbrCapBytes = (prepDev.sizeBytes / 512L).coerceAtMost(DiskPartitioner.MAX_MBR_USABLE_SECTORS) * 512L
                if (isoSize > prepDev.sizeBytes) {
                    return@withContext ActionResult(false, "ISO (${UsbGadgetEngine.formatBytes(isoSize)}) is larger than USB drive (${UsbGadgetEngine.formatBytes(prepDev.sizeBytes)}).", entries = entries)
                }
                if (withPersistence) {
                    val maxAvail = ((mbrCapBytes - persistOffsetBytes) / alignBytes) * alignBytes
                    if (maxAvail < 512L * 1024L * 1024L) {
                        return@withContext ActionResult(false, "Not enough space remaining on USB drive after ISO for ext4 persistence.", entries = entries)
                    }
                    val reqPersist = config.persistenceGb.coerceAtLeast(1).toLong() * 1024L * 1024L * 1024L
                    persistBytes = reqPersist.coerceAtMost(maxAvail)
                } else {
                    persistBytes = 0L
                }
            } else {
                persistBytes = if (withPersistence) config.persistenceGb.coerceIn(1, 32).toLong() * 1024L * 1024L * 1024L else 0L
                val totalBytes = if (withPersistence) persistOffsetBytes + persistBytes else isoSize
                val rawFreeSpace = UsbGadgetEngine.getFreeSpaceBytes(safeDir)
                val freeSpace = if (rawFreeSpace > 0L) rawFreeSpace + UsbGadgetEngine.queryExistingFileAllocatedBytes(outPath) else 0L
                if (freeSpace > 0L && freeSpace < totalBytes + (64L * 1024L * 1024L)) return@withContext ActionResult(false, "Not enough free space. Need ${UsbGadgetEngine.formatBytes(totalBytes)}, have ${UsbGadgetEngine.formatBytes(freeSpace)}.", entries = entries)
            }
            val totalBytes = if (withPersistence) persistOffsetBytes + persistBytes else isoSize

            // Fail-closed BEFORE wiping pen drive or copying GBs: verify source ISO LBA 0 has a free non-GPT MBR slot
            if (withPersistence) {
                val tmpPreMbr = File(context.cacheDir, "pb_linux_pre_mbr.bin")
                try {
                    Shell.cmd("dd if=$qIso of=${shellQuote(tmpPreMbr.absolutePath)} bs=512 count=1 2>/dev/null").exec()
                    val srcMbr = if (tmpPreMbr.exists()) tmpPreMbr.readBytes() else ByteArray(0)
                    if (DiskPartitioner.findPersistenceSlotIndex(srcMbr) < 2) {
                        return@withContext ActionResult(
                            false,
                            "This ISO uses a GPT Protective MBR or has all 4 MBR slots occupied, so Hybrid DD persistence cannot append a partition. Switch to Extracted FAT32 mode (or turn off Persistence).",
                            entries = entries
                        )
                    }
                } finally {
                    tmpPreMbr.delete()
                }
            }

            val persistGbLabel = ((persistBytes / (1024L * 1024L * 1024L)).coerceAtLeast(1L)).toInt()
            onProgress(WindowsBuildProgress(if (isDirectBlock) "Writing Hybrid ISO to USB..." else "Copying ISO image...", if (withPersistence) "${UsbGadgetEngine.formatBytes(isoSize)} + $persistGbLabel GB ${config.style.volumeLabel}" else UsbGadgetEngine.formatBytes(isoSize), 0.24f, true))
            val linuxProf = BuildThermalHelper.currentDefaultThermalProfile()
            BuildThermalHelper.waitForThermalCooldownIfNeeded(context, linuxProf) { temp, resumeAt ->
                onProgress(WindowsBuildProgress("Cooling down phone...", String.format(Locale.US, "%.1f°C -> resuming at %.1f°C", temp, resumeAt), 0.25f, true))
            }
            if (!isActive || BuildThermalHelper.isBuildCancelled()) { cleanup(true); return@withContext ActionResult(false, "Build cancelled.", entries = entries) }

            val otgToWrite = verifiedOtg
            if (otgToWrite != null) {
                onProgress(WindowsBuildProgress("Unmounting & wiping USB drive...", otgToWrite.displayTitle, 0.16f, true))
                OtgUsbFlasher.unmountAndWipeOtgDisk(otgToWrite, entries, ts)?.let { err ->
                    return@withContext ActionResult(false, err, entries = entries)
                }
            }
            var otgYankedDd = false
            val ddWatchJob = if (otgToWrite != null) {
                val qStat = shellQuote("/sys/block/${otgToWrite.devName}/stat")
                val startSec = OtgUsbFlasher.parseSectorsWrittenFromStat(
                    Shell.cmd("cat $qStat 2>/dev/null").exec().out.firstOrNull().orEmpty()
                )
                launch(Dispatchers.IO) {
                    while (isActive) {
                        delay(600L)
                        if (!OtgUsbFlasher.isOtgDeviceStillConnected(otgToWrite.devName)) {
                            otgYankedDd = true
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
                                stage = "Writing Hybrid ISO to USB...",
                                detail = "${UsbGadgetEngine.formatBytes(writtenB)} / ${UsbGadgetEngine.formatBytes(isoSize)}",
                                progress = 0.22f + frac * 0.34f,
                                isIndeterminate = writtenB <= 0L
                            )
                        )
                    }
                }
            } else null

            val cpRes = try {
                if (isDirectBlock) {
                    val ddScript = "set -e\n${BuildThermalHelper.thermalExecPrefix(linuxProf)}\n\$THERM_PFX dd if=$qIso of=$qOut bs=4M 2>&1\nsync"
                    BuildThermalHelper.runIsolatedRootScript(context, "linux_otg_dd", ddScript, trackPid = true)
                } else {
                    BuildThermalHelper.runIsolatedRootScript(
                        context,
                        "linux_cp",
                        "set -e\n${BuildThermalHelper.thermalExecPrefix(linuxProf)}\nmkdir -p $qDir && rm -f $qOut && \$THERM_PFX cp -f $qIso $qOut 2>&1",
                        trackPid = true
                    )
                }
            } finally {
                ddWatchJob?.cancel()
            }
            if (otgYankedDd) { cleanup(true); return@withContext ActionResult(false, "USB-C pen drive was unplugged during flashing.", entries = entries) }
            if (!isActive || BuildThermalHelper.isBuildCancelled()) { cleanup(true); return@withContext ActionResult(false, "Build cancelled.", entries = entries) }
            if (!isDirectBlock) {
                val copiedIsoBytes = Shell.cmd("stat -c '%s' $qOut 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                if (!cpRes.isSuccess || copiedIsoBytes != isoSize) {
                    cleanup(true)
                    val errTxt = (cpRes.out + cpRes.err).joinToString(" ").take(160).trim()
                    return@withContext ActionResult(
                        false,
                        "Failed to copy ISO (wrote ${UsbGadgetEngine.formatBytes(copiedIsoBytes)} of ${UsbGadgetEngine.formatBytes(isoSize)}).${if (errTxt.isNotBlank()) " $errTxt" else ""}",
                        entries = entries
                    )
                }
            } else if (!cpRes.isSuccess) {
                cleanup(true)
                val errTxt = (cpRes.out + cpRes.err).takeLast(2).joinToString(" ").take(160).trim()
                return@withContext ActionResult(false, "Failed to flash ISO to USB drive: ${errTxt.ifBlank { "I/O error" }}", entries = entries)
            }

            if (withPersistence) {
                if (!isDirectBlock) {
                    if (!Shell.cmd("truncate -s $totalBytes $qOut 2>&1 || dd if=/dev/zero of=$qOut bs=1M count=0 seek=${totalBytes / (1024L * 1024L)} 2>&1").exec().isSuccess) { cleanup(true); return@withContext ActionResult(false, "Failed to expand image for persistence.", entries = entries) }
                }
                onProgress(WindowsBuildProgress("Adding persistence partition...", "Updating MBR partition table", 0.58f, false))
                val tmpIn = File(context.cacheDir, "pb_linux_mbr_in.bin"); val tmpOut = File(context.cacheDir, "pb_linux_mbr_out.bin")
                val persistSlotNum: Int
                try {
                    Shell.cmd("dd if=$qOut of=${shellQuote(tmpIn.absolutePath)} bs=512 count=1 2>/dev/null").exec()
                    val origMbr = if (tmpIn.exists()) tmpIn.readBytes() else ByteArray(0)
                    persistSlotNum = DiskPartitioner.findPersistenceSlotIndex(origMbr)
                    val patched = DiskPartitioner.appendLinuxPersistenceMbr(origMbr, persistOffsetBytes / 512L, persistBytes / 512L)
                    if (patched == null || persistSlotNum < 2) { cleanup(true); return@withContext ActionResult(false, "Could not append persistence partition (try Extracted FAT32 mode).", entries = entries) }
                    tmpOut.writeBytes(patched)
                    val writeMbrOk = Shell.cmd("dd if=${shellQuote(tmpOut.absolutePath)} of=$qOut bs=512 count=1 conv=notrunc 2>&1").exec().isSuccess
                    if (!writeMbrOk) {
                        cleanup(true)
                        return@withContext ActionResult(false, "Failed to write updated MBR partition table.", entries = entries)
                    }
                } finally { tmpIn.delete(); tmpOut.delete() }
                onProgress(WindowsBuildProgress("Formatting ext4 persistence...", "Label: ${config.style.volumeLabel}", 0.74f, false))
                val otgForPersist = verifiedOtg
                if (otgForPersist != null) {
                    val (attachedNode, isLoop) = OtgUsbFlasher.attachOtgPartitionSlice(otgForPersist, persistSlotNum, persistOffsetBytes, persistBytes)
                    loopDev = attachedNode
                    loopIsLoopNode = isLoop
                } else {
                    loopDev = attachLoopSlice(qOut, persistOffsetBytes, persistBytes)
                    loopIsLoopNode = true
                }
                if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) { cleanup(true); return@withContext ActionResult(false, "Could not attach persistence partition.", entries = entries) }
                formatExt4AndInit(shellQuote(loopDev), config.style, entries, ts)?.let { cleanup(true); return@withContext ActionResult(false, it, entries = entries) }
            }
            onProgress(WindowsBuildProgress(if (isDirectBlock) "Flushing USB write cache — do NOT unplug..." else "Finalizing USB image...", "Syncing partitions", 0.95f, isDirectBlock))
            cleanup(false)
            val otgDone = verifiedOtg
            if (otgDone != null) {
                if (!OtgUsbFlasher.isOtgDeviceStillConnected(otgDone.devName)) {
                    return@withContext ActionResult(false, "USB-C pen drive was disconnected before final flush completed.", entries = entries)
                }
                OtgUsbFlasher.finalizeOtgDiskClean(otgDone)
                onProgress(WindowsBuildProgress("USB Ready!", "Safe to unplug ${otgDone.displayTitle}", 1f, false))
                val msg = if (withPersistence) "Flashed Linux Hybrid USB + $persistGbLabel GB ${config.style.volumeLabel} to ${otgDone.displayTitle}. Safe to unplug USB-C OTG drive." else "Flashed Linux Hybrid Live USB to ${otgDone.displayTitle}. Safe to unplug USB-C OTG drive."
                entries.add(LogEntry(ts, "linux-build", msg))
                ActionResult(true, msg, createdPath = null, entries = entries)
            } else {
                UsbGadgetEngine.fixUserStoragePermissions(context, safeDir, isDir = true)
                UsbGadgetEngine.fixUserStoragePermissions(context, outPath, isDir = false)
                val lOutBytes = Shell.cmd("stat -c '%s' $qOut 2>/dev/null || echo 0").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                if (lOutBytes > 0L) UsbGadgetEngine.syncMediaStoreFileSize(context, outPath, lOutBytes)
                IsoInspector.invalidate(outPath)
                onProgress(WindowsBuildProgress("Done!", safeName, 1f, false))
                val msg = if (withPersistence) "Built $safeName with ${config.persistenceGb} GB ${config.style.volumeLabel}." else "Built standard live USB image $safeName."
                entries.add(LogEntry(ts, "linux-build", msg))
                ActionResult(true, msg, createdPath = outPath, entries = entries)
            }
        }.getOrElse { e -> cleanup(true); ActionResult(false, "Linux/Tool build failed: ${e.message ?: "Unexpected error"}", entries = entries) }
    }

    private suspend fun buildExtractedFat32Image(context: Context, config: LinuxBuildConfig, onProgress: (WindowsBuildProgress) -> Unit): ActionResult = withContext(Dispatchers.IO) {
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        val isDirectBlock = config.directBlockTarget != null
        val safeDir = UsbGadgetEngine.resolveDirectPath(config.outputDir).trimEnd('/')
        val safeName = if (isDirectBlock) config.directBlockTarget?.displayTitle ?: "USB-C OTG Drive" else UsbGadgetEngine.sanitizeOutputFileName(config.outputName, ".img", "linux-usb")
        var outPath = if (isDirectBlock) config.directBlockTarget?.devPath.orEmpty() else "$safeDir/$safeName"
        val srcIsoPath = UsbGadgetEngine.resolveDirectPath(config.isoFile.path)
        val qIso = shellQuote(srcIsoPath); var qOut = shellQuote(outPath); val qDir = shellQuote(safeDir)
        var fatLoop = ""; var extLoop = ""
        var fatIsLoop = true; var extIsLoop = true
        var verifiedOtg: OtgUsbDevice? = null
        fun cleanup(deletePartial: Boolean) {
            val loopsToDetach = mutableListOf<String>()
            if (fatIsLoop && fatLoop.isNotBlank()) loopsToDetach.add(fatLoop)
            if (extIsLoop && extLoop.isNotBlank()) loopsToDetach.add(extLoop)
            BuildThermalHelper.unmountAndDetachClean(
                mountPoints = listOf(TEMP_FAT_MNT, TEMP_LINUX_MNT),
                loopDevices = loopsToDetach,
                removeDirs = true
            )
            if (deletePartial && !isDirectBlock) Shell.cmd("rm -f $qOut 2>/dev/null").exec()
        }
        return@withContext runCatching {
            if (!Shell.getShell().isRoot) return@withContext ActionResult(false, "Root access is required.", entries = entries)
            if (!isDirectBlock) {
                if (outPath.equals(srcIsoPath, ignoreCase = true)) return@withContext ActionResult(false, "Output image cannot overwrite the source ISO.", entries = entries)
                if (UsbGadgetEngine.getStatus().mountedLunFor(outPath) != null) return@withContext ActionResult(false, "$safeName is currently mounted to your PC. Unmount it before overwriting.", entries = entries)
            }
            val bin7z = WindowsImageBuilder.findBundledBinary(context, "lib7zzs.so", listOf("7zz", "7z")) ?: return@withContext ActionResult(false, "Bundled 7zz binary missing.", entries = entries)
            onProgress(WindowsBuildProgress("Checking ISO contents...", config.isoFile.name, 0.05f, true))
            val maxFile = inspectMaxFileBytesInIso(context, srcIsoPath)
            if (maxFile > FAT32_MAX_FILE_BYTES) return@withContext ActionResult(false, "ISO contains a file over 4 GB (${UsbGadgetEngine.formatBytes(maxFile)}). Use Hybrid DD / ISO mode instead.", entries = entries)
            val isoSize = Shell.cmd("stat -c '%s' $qIso 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: config.isoFile.sizeBytes
            val withPersistence = config.usbMode == LinuxUsbMode.WITH_PERSISTENCE
            val part1OffsetBytes: Long
            val fatBytes: Long
            val part2OffsetBytes: Long
            val persistBytes: Long
            val totalBytes: Long
            if (isDirectBlock) {
                val reqTarget = config.directBlockTarget ?: return@withContext ActionResult(false, "No physical USB-C OTG drive selected.", entries = entries)
                onProgress(WindowsBuildProgress("Verifying USB-C drive...", reqTarget.displayTitle, 0.09f, true))
                val (prepDev, prepErr) = OtgUsbFlasher.verifyPhysicalOtgDisk(reqTarget)
                if (prepDev == null) return@withContext ActionResult(false, prepErr ?: "USB-C OTG drive preparation failed.", entries = entries)
                verifiedOtg = prepDev
                outPath = prepDev.devPath
                qOut = shellQuote(outPath)
                val fixed = DiskPartitioner.calculateFixedLinuxFat32Layout(
                    totalDiskBytes = prepDev.sizeBytes,
                    isoSizeBytes = isoSize,
                    persistenceGb = config.persistenceGb,
                    includePersistence = withPersistence
                ) ?: return@withContext ActionResult(
                    false,
                    "USB pen drive (${UsbGadgetEngine.formatBytes(prepDev.sizeBytes)}) is too small for ${config.isoFile.name}${if (withPersistence) " + ext4 persistence" else ""}.",
                    entries = entries
                )
                part1OffsetBytes = fixed.part1OffsetBytes
                fatBytes = fixed.part1SizeBytes
                part2OffsetBytes = fixed.part2OffsetBytes
                persistBytes = fixed.part2Sectors * 512L
                totalBytes = fixed.totalBytes
            } else {
                fatBytes = ((isoSize / (1024L * 1024L)) + 256L).coerceAtLeast(512L) * 1024L * 1024L
                persistBytes = if (withPersistence) config.persistenceGb.coerceIn(1, 32).toLong() * 1024L * 1024L * 1024L else 0L
                part1OffsetBytes = 1024L * 1024L
                part2OffsetBytes = part1OffsetBytes + fatBytes
                totalBytes = part2OffsetBytes + persistBytes + (2L * 1024L * 1024L)
                val rawFreeSpace = UsbGadgetEngine.getFreeSpaceBytes(safeDir)
                val freeSpace = if (rawFreeSpace > 0L) rawFreeSpace + UsbGadgetEngine.queryExistingFileAllocatedBytes(outPath) else 0L
                if (freeSpace > 0L && freeSpace < totalBytes + (64L * 1024L * 1024L)) return@withContext ActionResult(false, "Not enough free space. Need ${UsbGadgetEngine.formatBytes(totalBytes)}, have ${UsbGadgetEngine.formatBytes(freeSpace)}.", entries = entries)
                onProgress(WindowsBuildProgress("Allocating FAT32 USB image...", UsbGadgetEngine.formatBytes(totalBytes), 0.14f, false))
                if (!Shell.cmd("mkdir -p $qDir && rm -f $qOut && (truncate -s $totalBytes $qOut 2>/dev/null || dd if=/dev/zero of=$qOut bs=1M count=0 seek=${totalBytes / (1024L * 1024L)} 2>&1)").exec().isSuccess) return@withContext ActionResult(false, "Failed to allocate image file.", entries = entries)
            }
            verifiedOtg?.let { otgToWipe ->
                onProgress(WindowsBuildProgress("Unmounting & wiping USB drive...", otgToWipe.displayTitle, 0.16f, true))
                OtgUsbFlasher.unmountAndWipeOtgDisk(otgToWipe, entries, ts)?.let { err ->
                    return@withContext ActionResult(false, err, entries = entries)
                }
            }
            val mbrBytes = DiskPartitioner.buildLinuxFat32MbrSector(
                part1StartLba = part1OffsetBytes / 512L,
                part1Sectors = fatBytes / 512L,
                part2StartLba = part2OffsetBytes / 512L,
                part2Sectors = persistBytes / 512L,
                includePersistence = withPersistence
            )
            val tmpMbr = File(context.cacheDir, "pb_fat_mbr.bin").apply { writeBytes(mbrBytes) }
            val mbrWriteOk = try {
                Shell.cmd("dd if=${shellQuote(tmpMbr.absolutePath)} of=$qOut bs=512 count=1 conv=notrunc 2>&1").exec().isSuccess
            } finally { tmpMbr.delete() }
            if (!mbrWriteOk) {
                cleanup(true)
                return@withContext ActionResult(false, "Failed to write MBR partition table to target.", entries = entries)
            }
            WindowsImageBuilder.findBundledBinary(context, "libms_sys.so", listOf("ms-sys"))?.let { msSys -> Shell.cmd("${shellQuote(msSys)} -s -f $qOut >/dev/null 2>&1 || ${shellQuote(msSys)} -7 -f $qOut >/dev/null 2>&1 || true").exec() }

            val fatLabel = if (config.volumeLabel.isNotBlank()) {
                IsoInspector.sanitizeFatLabel(config.volumeLabel, fallback = "POCKETBOOT", uppercase = true)
            } else {
                IsoInspector.defaultLinuxFat32Label(srcIsoPath, config.isoFile.name)
            }
            val qFatLbl = shellQuote(fatLabel)
            onProgress(WindowsBuildProgress("Formatting FAT32 partition...", "${fatBytes / (1024L * 1024L)} MB · $fatLabel", 0.28f, false))
            val otgForFat = verifiedOtg
            if (otgForFat != null) {
                val (attached, isLoop) = OtgUsbFlasher.attachOtgPartitionSlice(otgForFat, 1, part1OffsetBytes, fatBytes)
                fatLoop = attached
                fatIsLoop = isLoop
            } else {
                fatLoop = attachLoopSlice(qOut, part1OffsetBytes, fatBytes)
                fatIsLoop = true
            }
            if (fatLoop.isBlank() || !fatLoop.startsWith("/dev/")) { cleanup(true); return@withContext ActionResult(false, "Could not attach loop device for FAT32.", entries = entries) }
            val qFatLoop = shellQuote(fatLoop)
            val mkFatBin = WindowsImageBuilder.findBundledBinary(context, "libmkfs_fat.so", listOf("mkfs.vfat", "mkfs.fat", "newfs_msdos"))
            val fatFmtCmd = when {
                mkFatBin != null && mkFatBin.contains("newfs_msdos") -> "${shellQuote(mkFatBin)} -F 32 -L $qFatLbl $qFatLoop 2>&1"
                mkFatBin != null -> "${shellQuote(mkFatBin)} -F 32 -n $qFatLbl $qFatLoop 2>&1"
                else -> "mkfs.vfat -F 32 -n $qFatLbl $qFatLoop 2>&1 || newfs_msdos -F 32 -L $qFatLbl $qFatLoop 2>&1"
            }
            val fatFmtRes = Shell.cmd(fatFmtCmd).exec()
            if (!fatFmtRes.isSuccess) { cleanup(true); return@withContext ActionResult(false, "FAT32 format failed: ${(fatFmtRes.out + fatFmtRes.err).joinToString(" ").take(200)}", entries = entries) }

            val linuxProf = BuildThermalHelper.currentDefaultThermalProfile()
            BuildThermalHelper.waitForThermalCooldownIfNeeded(context, linuxProf) { temp, resumeAt ->
                onProgress(WindowsBuildProgress("Cooling down phone...", String.format(Locale.US, "%.1f°C -> resuming at %.1f°C", temp, resumeAt), 0.42f, true))
            }
            if (!isActive || BuildThermalHelper.isBuildCancelled()) { cleanup(true); return@withContext ActionResult(false, "Build cancelled.", entries = entries) }
            onProgress(WindowsBuildProgress("Extracting ISO files...", "${config.isoFile.name} · ${linuxProf.shortLabel} thermal", 0.45f, true))
            if (!Shell.cmd("mkdir -p $TEMP_FAT_MNT && mount -t vfat -o rw $qFatLoop $TEMP_FAT_MNT 2>&1").exec().isSuccess) { cleanup(true); return@withContext ActionResult(false, "Could not mount FAT32 partition.", entries = entries) }
            var otgYankedFat = false
            val fatWatchJob = verifiedOtg?.let { otgDev ->
                val qStat = shellQuote("/sys/block/${otgDev.devName}/stat")
                val startSec = OtgUsbFlasher.parseSectorsWrittenFromStat(
                    Shell.cmd("cat $qStat 2>/dev/null").exec().out.firstOrNull().orEmpty()
                )
                launch(Dispatchers.IO) {
                    while (isActive) {
                        delay(700L)
                        if (!OtgUsbFlasher.isOtgDeviceStillConnected(otgDev.devName)) {
                            otgYankedFat = true
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
                                stage = "Extracting Linux ISO to USB...",
                                detail = "${UsbGadgetEngine.formatBytes(writtenB)} / ~${UsbGadgetEngine.formatBytes(isoSize)} · ${linuxProf.shortLabel}",
                                progress = 0.45f + frac * 0.28f,
                                isIndeterminate = writtenB <= 0L
                            )
                        )
                    }
                }
            }
            val mmtArg = BuildThermalHelper.sevenZipThreadArgFor(linuxProf)
            val extRes = try {
                BuildThermalHelper.runIsolatedRootScript(
                    context,
                    "linux_ext",
                    "set -e\n${BuildThermalHelper.thermalExecPrefix(linuxProf)}\n\$THERM_PFX ${shellQuote(bin7z)} x -y $mmtArg -o$TEMP_FAT_MNT $qIso 2>&1",
                    trackPid = true
                )
            } finally {
                fatWatchJob?.cancel()
            }
            if (otgYankedFat) { cleanup(true); return@withContext ActionResult(false, "USB-C pen drive was unplugged during Linux ISO extraction.", entries = entries) }
            if (!isActive || BuildThermalHelper.isBuildCancelled()) { cleanup(true); return@withContext ActionResult(false, "Build cancelled.", entries = entries) }
            if (!extRes.isSuccess) { cleanup(true); return@withContext ActionResult(false, "ISO extraction failed: ${(extRes.out + extRes.err).takeLast(4).joinToString(" ").take(200)}", entries = entries) }

            // Sync label-dependent distro boot configs (CDLABEL=, root=live:LABEL=, archisolabel=, misolabel=, GRUB search -l/--label)
            val safeSedLbl = fatLabel.replace("/", "").replace("&", "").replace("|", "").replace("#", "")
            Shell.cmd(
                """
                find $TEMP_FAT_MNT -maxdepth 4 -type f \( -iname 'grub.cfg' -o -iname 'isolinux.cfg' -o -iname 'syslinux.cfg' -o -iname 'loopback.cfg' -o -iname 'txt.cfg' \) 2>/dev/null | while IFS= read -r cfg; do
                    if sed -E "s#(CDLABEL=)[^[:space:]\"']+#\1$safeSedLbl#g; s#(root=live:LABEL=)[^[:space:]\"']+#\1$safeSedLbl#g; s#(archisolabel=)[^[:space:]\"']+#\1$safeSedLbl#g; s#(misolabel=)[^[:space:]\"']+#\1$safeSedLbl#g; s#(search[[:space:]][^;#]*[[:space:]](-l|--label)[[:space:]]+['\"]?)[^[:space:]'\"]+(['\"]?)#\1$safeSedLbl\3#g" "${'$'}cfg" > "${'$'}{cfg}.pb_tmp" 2>/dev/null && [ -s "${'$'}{cfg}.pb_tmp" ]; then
                        mv -f "${'$'}{cfg}.pb_tmp" "${'$'}cfg" 2>/dev/null || rm -f "${'$'}{cfg}.pb_tmp" 2>/dev/null
                    else
                        rm -f "${'$'}{cfg}.pb_tmp" 2>/dev/null || true
                    fi
                done
                """.trimIndent()
            ).exec()
            entries.add(LogEntry(ts, "linux-build", "Formatted FAT32 ($fatLabel) & synced bootloader label entries"))

            if (withPersistence) {
                val bootParam = if (config.style == LinuxPersistenceStyle.DEBIAN_KALI) "persistence" else "persistent"
                Shell.cmd(
                    """
                    find $TEMP_FAT_MNT -maxdepth 4 -type f \( -iname 'grub.cfg' -o -iname 'isolinux.cfg' -o -iname 'syslinux.cfg' -o -iname 'loopback.cfg' -o -iname 'txt.cfg' \) 2>/dev/null | while IFS= read -r cfg; do
                        if sed "/$bootParam/!{s/boot=casper/boot=casper $bootParam/g; s/boot=live/boot=live $bootParam/g;}" "${'$'}cfg" > "${'$'}{cfg}.pb_tmp" 2>/dev/null && [ -s "${'$'}{cfg}.pb_tmp" ]; then
                            mv -f "${'$'}{cfg}.pb_tmp" "${'$'}cfg" 2>/dev/null || rm -f "${'$'}{cfg}.pb_tmp" 2>/dev/null
                        else
                            rm -f "${'$'}{cfg}.pb_tmp" 2>/dev/null || true
                        fi
                    done
                    """.trimIndent()
                ).exec()
                entries.add(LogEntry(ts, "linux-build", "Injected '$bootParam' kernel boot flag"))
                // Unmount & detach Partition 1 cleanly before attaching Partition 2 (eliminates any concurrent partition-node conflict)
                BuildThermalHelper.unmountAndDetachClean(
                    mountPoints = listOf(TEMP_FAT_MNT),
                    loopDevices = if (fatIsLoop && fatLoop.isNotBlank()) listOf(fatLoop) else emptyList(),
                    removeDirs = true
                )
                fatLoop = ""
                val actualPersistGb = ((persistBytes / (1024L * 1024L * 1024L)).coerceAtLeast(1L)).toInt()
                onProgress(WindowsBuildProgress("Formatting ext4 persistence...", "$actualPersistGb GB ${config.style.volumeLabel}", 0.78f, false))
                val otgForExt = verifiedOtg
                if (otgForExt != null) {
                    val (attached, isLoop) = OtgUsbFlasher.attachOtgPartitionSlice(otgForExt, 2, part2OffsetBytes, persistBytes)
                    extLoop = attached
                    extIsLoop = isLoop
                } else {
                    extLoop = attachLoopSlice(qOut, part2OffsetBytes, persistBytes)
                    extIsLoop = true
                }
                if (extLoop.isBlank() || !extLoop.startsWith("/dev/")) { cleanup(true); return@withContext ActionResult(false, "Could not attach persistence partition.", entries = entries) }
                formatExt4AndInit(shellQuote(extLoop), config.style, entries, ts)?.let { cleanup(true); return@withContext ActionResult(false, it, entries = entries) }
            }

            onProgress(WindowsBuildProgress(if (isDirectBlock) "Flushing USB write cache — do NOT unplug..." else "Finalizing FAT32 USB...", "Syncing partitions", 0.95f, isDirectBlock))
            cleanup(false)
            val otgDone = verifiedOtg
            if (otgDone != null) {
                if (!OtgUsbFlasher.isOtgDeviceStillConnected(otgDone.devName)) {
                    return@withContext ActionResult(false, "USB-C pen drive was disconnected before final flush completed.", entries = entries)
                }
                OtgUsbFlasher.finalizeOtgDiskClean(otgDone)
                val actualPersistGb = ((persistBytes / (1024L * 1024L * 1024L)).coerceAtLeast(1L)).toInt()
                val msg = if (withPersistence) "Flashed Direct Linux FAT32 + $actualPersistGb GB ${config.style.volumeLabel} to ${otgDone.displayTitle}. Safe to unplug USB-C OTG drive." else "Flashed Direct Linux FAT32 USB to ${otgDone.displayTitle}. Safe to unplug USB-C OTG drive."
                entries.add(LogEntry(ts, "linux-build", msg))
                onProgress(WindowsBuildProgress("USB Ready!", "Safe to unplug ${otgDone.displayTitle}", 1f, false))
                ActionResult(true, msg, createdPath = null, entries = entries)
            } else {
                UsbGadgetEngine.fixUserStoragePermissions(context, safeDir, isDir = true)
                UsbGadgetEngine.fixUserStoragePermissions(context, outPath, isDir = false)
                val lOutBytes = Shell.cmd("stat -c '%s' $qOut 2>/dev/null || echo 0").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                if (lOutBytes > 0L) UsbGadgetEngine.syncMediaStoreFileSize(context, outPath, lOutBytes)
                IsoInspector.invalidate(outPath)
                val msg = if (withPersistence) "Built FAT32 USB $safeName + ${config.persistenceGb} GB ${config.style.volumeLabel}." else "Built Extracted FAT32 USB $safeName."
                entries.add(LogEntry(ts, "linux-build", msg))
                onProgress(WindowsBuildProgress("Done!", safeName, 1f, false))
                ActionResult(true, msg, createdPath = outPath, entries = entries)
            }
        }.getOrElse { e -> cleanup(true); ActionResult(false, "Linux/Tool USB build failed: ${e.message ?: "Unexpected error"}", entries = entries) }
    }

    private fun attachLoopSlice(qOut: String, offsetBytes: Long, sizeBytes: Long): String {
        val loopCmd = """
            L=${'$'}(losetup -f -s -o $offsetBytes -S $sizeBytes $qOut 2>/dev/null)
            if [ -z "${'$'}L" ]; then L=${'$'}(losetup -f --show -o $offsetBytes --sizelimit $sizeBytes $qOut 2>/dev/null); fi
            if [ -z "${'$'}L" ]; then
                CAND=${'$'}(losetup -f 2>/dev/null)
                if [ -n "${'$'}CAND" ] && (losetup -o $offsetBytes -S $sizeBytes "${'$'}CAND" $qOut 2>/dev/null || losetup -o $offsetBytes --sizelimit $sizeBytes "${'$'}CAND" $qOut 2>/dev/null); then L="${'$'}CAND"; fi
            fi
            echo "${'$'}L"
        """.trimIndent()
        return Shell.cmd(loopCmd).exec().out.lastOrNull().orEmpty().trim()
    }

    private fun formatExt4AndInit(qLoop: String, style: LinuxPersistenceStyle, entries: MutableList<LogEntry>, ts: String): String? {
        val label = style.volumeLabel
        val ext4Res = Shell.cmd("""
            if command -v mke2fs >/dev/null 2>&1; then mke2fs -t ext4 -F -L $label $qLoop 2>&1
            elif command -v mkfs.ext4 >/dev/null 2>&1; then mkfs.ext4 -F -L $label $qLoop 2>&1
            elif [ -x /system/bin/mke2fs ]; then /system/bin/mke2fs -t ext4 -F -L $label $qLoop 2>&1
            else echo "No mke2fs/mkfs.ext4 binary found on device"; exit 1; fi
        """.trimIndent()).exec()
        if (!ext4Res.isSuccess) return "ext4 format failed: ${(ext4Res.out + ext4Res.err).joinToString(" ").take(200)}"
        if (style == LinuxPersistenceStyle.DEBIAN_KALI) {
            if (Shell.cmd("mkdir -p $TEMP_LINUX_MNT && mount -t ext4 -o rw $qLoop $TEMP_LINUX_MNT 2>&1 && printf '/ union\\n' > $TEMP_LINUX_MNT/persistence.conf").exec().isSuccess) {
                entries.add(LogEntry(ts, "linux-build", "Created /persistence.conf (/ union)"))
            }
        }
        return null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinuxBuilderSheet(
    isoFile: ImageFile, defaultDir: String, building: Boolean, progress: WindowsBuildProgress,
    onDismiss: () -> Unit, onCancelBuild: () -> Unit, onStartBuild: (LinuxBuildConfig) -> Unit
) {
    val context = LocalContext.current
    val defaultStyle = remember(isoFile.name) {
        val low = isoFile.name.lowercase()
        when {
            low.contains("kali") || low.contains("debian") || low.contains("tails") || low.contains("gparted") || low.contains("clonezilla") || low.contains("parrot") -> LinuxPersistenceStyle.DEBIAN_KALI
            low.contains("pop-os") || low.contains("pop_os") -> LinuxPersistenceStyle.CASPER_RW
            else -> LinuxPersistenceStyle.UBUNTU_WRITABLE
        }
    }
    var usbMode by remember(isoFile.path) { mutableStateOf(LinuxUsbMode.WITH_PERSISTENCE) }
    var writeMethod by remember(isoFile.path) { mutableStateOf(LinuxWriteMethod.HYBRID_DD) }
    var style by remember(isoFile.path) { mutableStateOf(defaultStyle) }
    var persistGb by remember { mutableIntStateOf(2) }
    var autoMount by remember { mutableStateOf(true) }
    var maxIsoFileBytes by remember(isoFile.path) { mutableStateOf(0L) }
    var detectedFatLabel by remember(isoFile.path) { mutableStateOf(IsoInspector.sanitizeFatLabel(isoFile.name.substringBeforeLast('.'), "POCKETBOOT", uppercase = true)) }
    var volumeLabel by remember(isoFile.path) { mutableStateOf(detectedFatLabel) }
    var volumeLabelEdited by remember(isoFile.path) { mutableStateOf(false) }
    LaunchedEffect(isoFile.path) {
        maxIsoFileBytes = LinuxImageBuilder.inspectMaxFileBytesInIso(context, isoFile.path)
        val probed = withContext(Dispatchers.IO) { IsoInspector.defaultLinuxFat32Label(isoFile.path, isoFile.name) }
        detectedFatLabel = probed
        if (!volumeLabelEdited) volumeLabel = probed
    }

    val defaultOutName = remember(isoFile.name, usbMode) {
        val stem = isoFile.name.substringBeforeLast('.').take(32)
        if (usbMode == LinuxUsbMode.WITH_PERSISTENCE) "$stem-Persist.img" else "$stem-USB.img"
    }
    var outputName by remember(defaultOutName) { mutableStateOf(defaultOutName) }
    val fat32Blocked = maxIsoFileBytes > 4_294_967_295L
    var targetExists by remember(isoFile.path) { mutableStateOf(false) }
    var existingTargetAllocBytes by remember(isoFile.path) { mutableStateOf(0L) }
    val resolvedOutName = remember(outputName, defaultOutName) { UsbGadgetEngine.sanitizeOutputFileName(outputName.ifBlank { defaultOutName }, ".img", "linux-usb") }
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
    val estNeeded = isoFile.sizeBytes + (if (usbMode == LinuxUsbMode.WITH_PERSISTENCE) persistGb.toLong() * 1024L * 1024L * 1024L else 0L) + (if (writeMethod == LinuxWriteMethod.EXTRACTED_FAT32) 256L * 1024L * 1024L else 16L * 1024L * 1024L)
    val freeSpace = if (rawFreeSpace > 0L) rawFreeSpace + existingTargetAllocBytes else 0L
    val lowSpace = freeSpace in 1 until estNeeded

    ModalBottomSheet(onDismissRequest = { if (!building) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = PocketColors.surfaceCard) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            PocketSheetHeader(icon = Icons.Rounded.Terminal, tint = PocketColors.accentSoft, iconBg = PocketColors.accentBg, title = "Linux & Tool USB Builder", subtitle = "${isoFile.name} · ${UsbGadgetEngine.formatBytes(isoFile.sizeBytes)}", subtitleMono = true)
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
                    Text("USB Mode", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinuxUsbMode.entries.forEach { mode ->
                            val selected = usbMode == mode
                            Surface(modifier = Modifier.weight(1f).bounceClick { usbMode = mode }, shape = RoundedCornerShape(12.dp), color = if (selected) PocketColors.accentBg else PocketColors.surfaceInput, border = BorderStroke(if (selected) 1.5.dp else 0.75.dp, if (selected) PocketColors.accent else PocketColors.border)) {
                                Column(modifier = Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(mode.title, style = MaterialTheme.typography.labelLarge, color = if (selected) PocketColors.accentSoft else PocketColors.textPrimary)
                                    Text(mode.subtitle, fontSize = 11.sp, color = PocketColors.textSecondary)
                                }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Write Method", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinuxWriteMethod.entries.forEach { m ->
                            val selected = writeMethod == m; val blocked = m == LinuxWriteMethod.EXTRACTED_FAT32 && fat32Blocked
                            Surface(modifier = Modifier.weight(1f).bounceClick(enabled = !blocked) { writeMethod = m }, shape = RoundedCornerShape(12.dp), color = if (selected) PocketColors.surfaceSubtle else PocketColors.surfaceInput, border = BorderStroke(if (selected) 1.dp else 0.5.dp, if (selected) PocketColors.accent else PocketColors.border)) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(m.title, style = MaterialTheme.typography.labelMedium, color = if (blocked) PocketColors.textMuted else PocketColors.textPrimary)
                                    Text(if (blocked) "Blocked (>4 GB file in ISO)" else m.subtitle, fontSize = 11.sp, color = PocketColors.textSecondary)
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(visible = usbMode == LinuxUsbMode.WITH_PERSISTENCE) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Persistence Partition Format", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            LinuxPersistenceStyle.entries.forEach { s ->
                                val selected = style == s
                                Surface(modifier = Modifier.fillMaxWidth().bounceClick { style = s }, shape = RoundedCornerShape(11.dp), color = if (selected) PocketColors.accentBg else PocketColors.surfaceInput, border = BorderStroke(if (selected) 1.dp else 0.5.dp, if (selected) PocketColors.accent else PocketColors.border)) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(s.title, style = MaterialTheme.typography.labelMedium, color = if (selected) PocketColors.accentSoft else PocketColors.textPrimary)
                                            Text(s.subtitle, fontSize = 11.sp, color = PocketColors.textSecondary)
                                        }
                                        Text(s.volumeLabel, style = PocketColors.monoStyle, color = PocketColors.accentSoft)
                                    }
                                }
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Persistent Storage Size (ext4)", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1, 2, 4, 8, 16).forEach { gb ->
                                    val selected = persistGb == gb
                                    Surface(modifier = Modifier.weight(1f).bounceClick { persistGb = gb }, shape = RoundedCornerShape(10.dp), color = if (selected) PocketColors.accentBg else PocketColors.surfaceInput, border = BorderStroke(if (selected) 1.25.dp else 0.75.dp, if (selected) PocketColors.accent else PocketColors.border)) {
                                        Box(modifier = Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) { Text("${gb}G", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) PocketColors.accentSoft else PocketColors.textPrimary) }
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(value = outputName, onValueChange = { outputName = it }, label = { Text("Output USB Image Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PocketColors.accent, unfocusedBorderColor = PocketColors.border, focusedTextColor = PocketColors.textPrimary, unfocusedTextColor = PocketColors.textPrimary))
                AnimatedVisibility(visible = writeMethod == LinuxWriteMethod.EXTRACTED_FAT32) {
                    OutlinedTextField(
                        value = volumeLabel,
                        onValueChange = { volumeLabelEdited = true; volumeLabel = it.filter { ch -> ch.code in 0x20..0x7E && ch !in "\\/:*?\"<>|.,;+=[]'`" }.uppercase(Locale.US).take(11) },
                        label = { Text("FAT32 Volume Label (max 11 chars)") },
                        placeholder = { Text(detectedFatLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PocketColors.accent, unfocusedBorderColor = PocketColors.border, focusedTextColor = PocketColors.textPrimary, unfocusedTextColor = PocketColors.textPrimary)
                    )
                }
                Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { autoMount = !autoMount }.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-mount when finished", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                            Text(if (usbMode == LinuxUsbMode.WITH_PERSISTENCE) "Mounts as Writable USB (RW) for persistence" else "Mounts as Read-Only USB (RO)", fontSize = 11.sp, color = PocketColors.textSecondary)
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
                            Text("Low storage: need ~${UsbGadgetEngine.formatBytes(estNeeded)}, have ${UsbGadgetEngine.formatBytes(freeSpace)}.", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = PocketColors.textMuted, modifier = Modifier.size(14.dp))
                        Text(if (writeMethod == LinuxWriteMethod.EXTRACTED_FAT32 && usbMode == LinuxUsbMode.WITH_PERSISTENCE) "Auto-injects persistence boot flag into GRUB/Syslinux." else "Estimated image size: ~${UsbGadgetEngine.formatBytes(estNeeded)}", fontSize = 11.sp, color = PocketColors.textSecondary)
                    }
                }
                Button(onClick = { onStartBuild(LinuxBuildConfig(isoFile, defaultDir, outputName.trim().ifBlank { defaultOutName }, usbMode, writeMethod, PartitionScheme.MBR, persistGb, style, autoMount, volumeLabel = volumeLabel.trim().ifBlank { detectedFatLabel })) }, enabled = outputName.trim().isNotEmpty() && !lowSpace && !(writeMethod == LinuxWriteMethod.EXTRACTED_FAT32 && fat32Blocked), modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)) {
                    Text(if (usbMode == LinuxUsbMode.WITH_PERSISTENCE) "Build Persistent USB (.img)" else "Build Live USB (.img)", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}
