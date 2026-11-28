package com.pocketboot.app

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

enum class OtgFlashMode(val shortLabel: String, val title: String, val subtitle: String) {
    DIRECT_WINDOWS("Windows", "Direct Windows USB", "GPT/MBR + NTFS + UEFI:NTFS, Tweaks, Bypass & Drivers"),
    DIRECT_LINUX("Linux", "Direct Linux + Persistence", "Extracted FAT32 or Hybrid DD + ext4 Persistence"),
    DIRECT_MULTIBOOT("Multi-Boot", "Direct Multi-Boot USB", "Full-capacity Multi-Boot pen drive + Tweaks & Drivers"),
    RAW_DD("Raw DD", "Raw / Streaming DD", "Sector-for-sector .img / .xz / .gz / .zst + Verify")
}

data class OtgUsbDevice(
    val devName: String,
    val devPath: String,
    val vendor: String,
    val model: String,
    val sizeBytes: Long,
    val sysfsRealPath: String,
    val readOnly: Boolean,
    val mountedPoints: List<String> = emptyList(),
    val logicalBlockSize: Int = 512,
    val devMajorMinor: String = ""
) {
    val displayTitle: String
        get() = listOf(vendor, model).map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(" ").ifBlank { "USB Storage ($devName)" }
}

data class OtgFlashConfig(
    val sourceFile: ImageFile,
    val targetDevice: OtgUsbDevice,
    val verifyAfterFlash: Boolean = true,
    val fullSha256Verify: Boolean = false
)

/**
 * Physical USB-C OTG pen drive flasher with a 9-layer anti-brick safety lock:
 * 1. Whole-disk regex (`^sd[a-z]+$`), never partitions or mmcblk/loop/dm/zram.
 * 2. Strict canonical sysfs USB bus proof (`/usb\d+/`) + internal bus denylist (`ufshc`, `mmc`, `nvme`, `virtual`).
 * 3. `/proc/mounts` system-mount cross-check (disqualifies any disk touching `/`, `/system`, `/data`, `/vendor`, etc.).
 * 4. Writable (`ro == 0`) & minimum capacity >= 64 MB check.
 * 5. 512-byte logical sector size guard (rejects 4Kn enclosures that would corrupt 512-byte LBA tables).
 * 6. Source size <= target disk capacity fail-closed check (+ 768 MiB Windows boot.wim headroom).
 * 7. USB-C port mutual-exclusion guard (cannot run while ConfigFS LUN is mounted to PC).
 * 8. Multi-namespace Android `sm unmount` + exact `(major,minor)` `vold` unmount gate.
 * 9. Explicit typed `FLASH` confirmation displaying exact hardware fingerprint & mode summary.
 */
object OtgUsbFlasher {

    private const val MIN_OTG_BYTES = 64L * 1024L * 1024L
    private const val VERIFY_CHUNK_MB = 16L
    private const val VERIFY_CHUNK_BYTES = VERIFY_CHUNK_MB * 1024L * 1024L

    private val WHOLE_SD_DISK_RE = Regex("^sd[a-z]+$")
    private val USB_BUS_RE = Regex("/usb\\d+/")
    private val FORBIDDEN_BUS_TOKENS = listOf(
        "ufshc", "/ufs", "mmc", "nvme", "/virtual/", "/loop", "/zram", "/dm-", "/ram", "platform/soc/1d84000"
    )
    private val SYSTEM_MOUNT_PREFIXES = listOf(
        "/", "/system", "/vendor", "/data", "/metadata", "/apex", "/odm",
        "/product", "/efs", "/persist", "/mnt/vendor", "/cache", "/boot", "/system_ext"
    )

    private fun nowTs(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    fun cancelActiveFlash() {
        BuildThermalHelper.cancelActiveBuildProcess()
    }

    fun recommendModeForSource(source: ImageFile?): OtgFlashMode {
        if (source == null) return OtgFlashMode.RAW_DD
        val name = source.name
        if (IsoInspector.isCompressedImageName(name) || name.endsWith(".img", ignoreCase = true)) {
            return OtgFlashMode.RAW_DD
        }
        return when (IsoInspector.quickGuessByName(name)) {
            IsoOsType.WINDOWS -> OtgFlashMode.DIRECT_WINDOWS
            IsoOsType.LINUX, IsoOsType.UTILITY -> OtgFlashMode.DIRECT_LINUX
            else -> OtgFlashMode.RAW_DD
        }
    }

    /**
     * Pure-JVM testable hardware safety validator.
     * Returns `(true, "OK")` ONLY if the block device is proven to be a writable 512 B external USB pen drive.
     */
    fun evaluateBlockDeviceSafety(
        devName: String,
        sysfsRealPath: String,
        readOnly: Boolean,
        sizeBytes: Long,
        procMountsLines: List<String>,
        logicalBlockSize: Int = 512
    ): Pair<Boolean, String> {
        val cleanName = devName.trim()
        if (!WHOLE_SD_DISK_RE.matches(cleanName)) {
            return false to "Rejected $cleanName: not a whole SCSI/USB disk (sd*)."
        }
        val lowSys = sysfsRealPath.trim().lowercase(Locale.US)
        if (lowSys.isEmpty() || !USB_BUS_RE.containsMatchIn(lowSys)) {
            return false to "Rejected $cleanName: sysfs path is not on an external USB host bus."
        }
        FORBIDDEN_BUS_TOKENS.firstOrNull { lowSys.contains(it) }?.let { bad ->
            return false to "Rejected $cleanName: internal/virtual bus token '$bad' detected."
        }
        if (readOnly) {
            return false to "Rejected $cleanName: drive is hardware write-protected (read-only)."
        }
        if (logicalBlockSize != 512) {
            return false to "Rejected $cleanName: unsupported logical sector size ($logicalBlockSize B; 512 B required)."
        }
        if (sizeBytes < MIN_OTG_BYTES) {
            return false to "Rejected $cleanName: capacity too small (${UsbGadgetEngine.formatBytes(sizeBytes)})."
        }
        val devToken1 = "/dev/block/$cleanName"
        val devToken2 = "/dev/$cleanName"
        for (line in procMountsLines) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2) continue
            val src = parts[0]
            val mnt = parts[1]
            val matchesThisDisk =
                src == devToken1 || src == devToken2 ||
                    (src.startsWith(devToken1) && src.length > devToken1.length && src[devToken1.length].isDigit()) ||
                    (src.startsWith(devToken2) && src.length > devToken2.length && src[devToken2.length].isDigit())
            if (matchesThisDisk) {
                val isSystemMnt = SYSTEM_MOUNT_PREFIXES.any { pref ->
                    if (pref == "/") mnt == "/" else (mnt == pref || mnt.startsWith("$pref/"))
                }
                if (isSystemMnt) {
                    return false to "Rejected $cleanName: mounted on protected Android system path ($mnt)."
                }
            }
        }
        return true to "OK"
    }

    fun validateSourceFitsDevice(sourceBytes: Long, targetDiskBytes: Long): String? {
        if (sourceBytes <= 0L) return "Source image is empty or missing."
        if (targetDiskBytes < MIN_OTG_BYTES) return "Target USB drive size could not be determined."
        if (sourceBytes > targetDiskBytes) {
            return "Image (${UsbGadgetEngine.formatBytes(sourceBytes)}) is larger than target USB drive (${UsbGadgetEngine.formatBytes(targetDiskBytes)})."
        }
        return null
    }

    fun parseSectorsWrittenFromStat(statLine: String): Long {
        val fields = statLine.trim().split(Regex("\\s+"))
        return fields.getOrNull(6)?.toLongOrNull() ?: 0L
    }

    /**
     * Pure-JVM testable parser extracting ONLY Android `sm` public volume IDs (`public:major,minor`)
     * matching [targetDevNumbers] so sibling USB drives on a hub are never unmounted.
     */
    fun parseSmPublicVolumeIds(smLines: List<String>, targetDevNumbers: Set<String>): List<String> {
        val exactIds = targetDevNumbers
            .map { it.trim().replace(':', ',') }
            .filter { Regex("^\\d+,\\d+$").matches(it) }
            .map { "public:$it" }
            .toSet()
        if (exactIds.isEmpty()) return emptyList()
        val found = linkedSetOf<String>()
        for (raw in smLines) {
            val cols = raw.trim().split(Regex("\\s+"))
            if (cols.size >= 2) {
                val volId = cols[0]
                val state = cols[1].lowercase(Locale.US)
                if (volId in exactIds && state != "unmounted" && state != "ejected") {
                    found.add(volId)
                }
            }
        }
        return found.toList()
    }

    /**
     * Pure-JVM testable parser returning ONLY mount points belonging to [devName], its numeric partitions,
     * or the exact `(major:minor)` pairs in [targetDevNumbers] (never unmounting sibling `sd*` drives on major 8).
     * Decodes `/proc/mounts` octal space escapes (`\040`).
     */
    fun extractTargetMountPoints(
        devName: String,
        targetDevNumbers: Set<String>,
        procMountsLines: List<String>
    ): List<String> {
        val cleanDev = devName.trim()
        if (!WHOLE_SD_DISK_RE.matches(cleanDev)) return emptyList()
        val exactVoldTokens = targetDevNumbers
            .map { it.trim().replace(':', ',') }
            .filter { Regex("^\\d+,\\d+$").matches(it) }
            .map { "/dev/block/vold/public:$it" }
            .toSet()

        val p1 = "/dev/block/$cleanDev"
        val p2 = "/dev/$cleanDev"
        val result = linkedSetOf<String>()
        for (line in procMountsLines) {
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2) continue
            val src = parts[0]
            val rawMp = parts[1]
            val matches =
                src == p1 || src == p2 ||
                    (src.startsWith(p1) && src.length > p1.length && src[p1.length].isDigit()) ||
                    (src.startsWith(p2) && src.length > p2.length && src[p2.length].isDigit()) ||
                    src in exactVoldTokens
            if (matches && rawMp.isNotBlank()) {
                val decodedMp = rawMp
                    .replace("\\040", " ")
                    .replace("\\011", "\t")
                    .replace("\\134", "\\")
                result.add(decodedMp)
            }
        }
        return result.toList()
    }

    suspend fun scanUsbOtgDevices(): List<OtgUsbDevice> = withContext(Dispatchers.IO) {
        if (!Shell.getShell().isRoot) return@withContext emptyList()
        val procMounts = Shell.cmd("cat /proc/mounts 2>/dev/null").exec().out
        val rawOut = Shell.cmd(
            """
            for d in /sys/block/sd*; do
                [ -d "${'$'}d" ] || continue
                name=${'$'}{d##*/}
                real=${'$'}(readlink -f "${'$'}d" 2>/dev/null || echo "")
                sectors=${'$'}(cat "${'$'}d/size" 2>/dev/null || echo 0)
                ro=${'$'}(cat "${'$'}d/ro" 2>/dev/null || echo 1)
                lbs=${'$'}(cat "${'$'}d/queue/logical_block_size" 2>/dev/null || echo 512)
                devnum=${'$'}(cat "${'$'}d/dev" 2>/dev/null | tr -d '\n\r|' || echo "")
                vendor=${'$'}(cat "${'$'}d/device/vendor" 2>/dev/null | tr '\n\r|' '   ' || echo "")
                model=${'$'}(cat "${'$'}d/device/model" 2>/dev/null | tr '\n\r|' '   ' || echo "")
                printf '%s|%s|%s|%s|%s|%s|%s|%s\n' "${'$'}name" "${'$'}real" "${'$'}sectors" "${'$'}ro" "${'$'}lbs" "${'$'}devnum" "${'$'}vendor" "${'$'}model"
            done
            """.trimIndent()
        ).exec().out

        val devices = mutableListOf<OtgUsbDevice>()
        for (line in rawOut) {
            val p = line.split("|", limit = 8)
            if (p.size < 8) continue
            val name = p[0].trim()
            val real = p[1].trim()
            val sectors = p[2].trim().toLongOrNull() ?: 0L
            val ro = p[3].trim() != "0"
            val lbs = p[4].trim().toIntOrNull() ?: 512
            val devNum = p[5].trim()
            val vendor = p[6].trim()
            val model = p[7].trim()
            // Linux kernel ABI (/sys/block/<dev>/size) is ALWAYS reported in 512-byte units regardless of logical_block_size
            val sizeBytes = sectors * 512L

            val (safe, _) = evaluateBlockDeviceSafety(name, real, ro, sizeBytes, procMounts, logicalBlockSize = lbs)
            if (!safe) continue

            val devPath = if (Shell.cmd("[ -b ${shellQuote("/dev/block/$name")} ]").exec().isSuccess) {
                "/dev/block/$name"
            } else if (Shell.cmd("[ -b ${shellQuote("/dev/$name")} ]").exec().isSuccess) {
                "/dev/$name"
            } else {
                continue
            }

            val mntPoints = procMounts.mapNotNull { mLine ->
                val mp = mLine.trim().split(Regex("\\s+"))
                if (mp.size >= 2 && (mp[0].startsWith("/dev/block/$name") || mp[0].startsWith("/dev/$name"))) {
                    mp[1]
                } else null
            }.distinct()

            devices.add(
                OtgUsbDevice(
                    devName = name,
                    devPath = devPath,
                    vendor = vendor,
                    model = model,
                    sizeBytes = sizeBytes,
                    sysfsRealPath = real,
                    readOnly = ro,
                    mountedPoints = mntPoints,
                    logicalBlockSize = lbs,
                    devMajorMinor = devNum
                )
            )
        }
        devices
    }

    internal fun computeTailWipeSectorRange(totalDiskBytes: Long): Pair<Long, Long>? {
        val totalSectors = totalDiskBytes / 512L
        if (totalSectors <= 8192L) return null
        val wipeCount = 4096L // Exact 2 MiB right up to physical last sector (totalSectors - 1)
        return (totalSectors - wipeCount) to wipeCount
    }

    private fun queryTargetDevNumbers(devName: String): Set<String> {
        val qSys = shellQuote("/sys/block/$devName")
        val out = Shell.cmd(
            """
            cat $qSys/dev 2>/dev/null || true
            for p in $qSys/${devName}[0-9]*/dev; do
                [ -f "${'$'}p" ] && cat "${'$'}p" 2>/dev/null || true
            done
            """.trimIndent()
        ).exec().out
        return out.map { it.trim() }.filter { Regex("^\\d+:\\d+$").matches(it) }.toSet()
    }

    internal fun unmountOtgPartitionsClean(devName: String): Pair<Boolean, String> {
        val devNums = queryTargetDevNumbers(devName)
        val smOut = Shell.cmd("sm list-volumes public 2>/dev/null || true").exec().out
        val smVols = parseSmPublicVolumeIds(smOut, devNums)
        if (smVols.isNotEmpty()) {
            for (volId in smVols) {
                Shell.cmd("sm unmount ${shellQuote(volId)} 2>/dev/null || true").exec()
            }
            Shell.cmd("sleep 0.3").exec()
        }

        val initialMounts = extractTargetMountPoints(
            devName,
            devNums,
            Shell.cmd("cat /proc/mounts 2>/dev/null").exec().out
        )
        if (initialMounts.isNotEmpty()) {
            Shell.cmd("sync").exec()
            for (mp in initialMounts.sortedByDescending { it.length }) {
                val qMp = shellQuote(mp)
                Shell.cmd("umount $qMp 2>/dev/null || umount -l $qMp 2>/dev/null || true").exec()
            }
            Shell.cmd("sync").exec()
        }

        val remaining = extractTargetMountPoints(
            devName,
            devNums,
            Shell.cmd("cat /proc/mounts 2>/dev/null").exec().out
        )
        return if (remaining.isEmpty()) {
            true to "OK"
        } else {
            false to "USB drive partition is still busy/mounted (${remaining.first()}). Close any file manager using it and retry."
        }
    }

    internal fun isOtgDeviceStillConnected(devName: String): Boolean {
        if (!WHOLE_SD_DISK_RE.matches(devName.trim())) return false
        val qSys = shellQuote("/sys/block/$devName")
        val qBlk1 = shellQuote("/dev/block/$devName")
        val qBlk2 = shellQuote("/dev/$devName")
        return Shell.cmd("[ -d $qSys ] && ( [ -b $qBlk1 ] || [ -b $qBlk2 ] )").exec().isSuccess
    }

    /**
     * Full pre-flight preparation for direct writing on a physical OTG drive:
     * 1. ConfigFS mutual-exclusion guard.
     * 2. Re-scan & verify exact sysfs + 512 B sector safety.
     * 3. Multi-namespace `sm unmount` + exact `vold` unmount gate.
     * 4. Zero first 4 MiB + last 2 MiB to destroy stale MBR, primary/backup GPT, and filesystem signatures.
     */
    /**
     * Read-only pre-flight hardware verification (writes 0 bytes to disk).
     * Must be called BEFORE checking capacity, layouts, tools, and assets so a failed check NEVER wipes the USB drive.
     */
    /**
     * Layer 10 physical identity lock: verifies the re-scanned OTG drive matches exact device name,
     * canonical sysfs USB bus path, kernel major:minor (when present), 512-byte sector size, exact
     * byte capacity, vendor, and model so hot-swapping another USB drive into the same port fails closed.
     */
    internal fun matchesVerifiedOtgTarget(requested: OtgUsbDevice, fresh: OtgUsbDevice): Boolean {
        val majorMinorOk = requested.devMajorMinor.isBlank() ||
            fresh.devMajorMinor.isBlank() ||
            fresh.devMajorMinor == requested.devMajorMinor
        return fresh.devName == requested.devName &&
            fresh.sysfsRealPath == requested.sysfsRealPath &&
            fresh.logicalBlockSize == 512 &&
            !fresh.readOnly &&
            fresh.sizeBytes == requested.sizeBytes &&
            fresh.vendor == requested.vendor &&
            fresh.model == requested.model &&
            majorMinorOk
    }

    internal suspend fun verifyPhysicalOtgDisk(
        requestedTarget: OtgUsbDevice
    ): Pair<OtgUsbDevice?, String?> = withContext(Dispatchers.IO) {
        if (UsbGadgetEngine.getStatus().isAnyMounted) {
            return@withContext null to "Unmount your virtual PC drive before flashing a physical USB-C OTG pen drive."
        }
        val freshList = scanUsbOtgDevices()
        val verified = freshList.firstOrNull { matchesVerifiedOtgTarget(requestedTarget, it) }
            ?: return@withContext null to "Target USB drive (${requestedTarget.devPath}) disconnected, changed identity, or failed safety verification. Re-scan and retry."
        verified to null
    }

    /**
     * Destructive disk preparation called ONLY AFTER all capacity, layout, tool, and asset checks pass:
     * 1. Multi-namespace `sm unmount` + exact `(major:minor)` `vold` unmount gate.
     * 2. Zero first 4 MiB + exact final 4096 sectors (`totalSectors - 4096 .. totalSectors - 1`)
     *    so stale Backup GPT headers are destroyed even on non-MiB-aligned consumer pen drives.
     */
    internal suspend fun unmountAndWipeOtgDisk(
        verified: OtgUsbDevice,
        entries: MutableList<LogEntry>,
        ts: String
    ): String? = withContext(Dispatchers.IO) {
        val (unmountedOk, umntErr) = unmountOtgPartitionsClean(verified.devName)
        if (!unmountedOk) {
            return@withContext umntErr
        }
        val qDev = shellQuote(verified.devPath)
        val wipeHead = Shell.cmd("dd if=/dev/zero of=$qDev bs=1048576 count=4 2>&1").exec()
        if (!wipeHead.isSuccess) {
            val err = (wipeHead.out + wipeHead.err).takeLast(2).joinToString(" ").take(120)
            return@withContext "Could not erase USB drive header: ${err.ifBlank { "I/O error" }}"
        }
        computeTailWipeSectorRange(verified.sizeBytes)?.let { (tailSeekSec, tailCountSec) ->
            Shell.cmd("dd if=/dev/zero of=$qDev bs=512 seek=$tailSeekSec count=$tailCountSec 2>/dev/null || true").exec()
        }
        Shell.cmd("sync").exec()
        entries.add(LogEntry(ts, "otg-prep", "Unmounted & wiped header/tail on ${verified.devPath} (${verified.displayTitle}, ${UsbGadgetEngine.formatBytes(verified.sizeBytes)}, 512B sectors)"))
        null
    }

    private fun unmountSinglePartitionNode(devName: String, partIndex: Int) {
        val partName = "$devName$partIndex"
        val qSysPart = shellQuote("/sys/block/$devName/$partName/dev")
        val partDevNum = Shell.cmd("cat $qSysPart 2>/dev/null || true").exec().out.firstOrNull().orEmpty().trim()
        val devNums = if (Regex("^\\d+:\\d+$").matches(partDevNum)) setOf(partDevNum) else emptySet()
        if (devNums.isNotEmpty()) {
            val smOut = Shell.cmd("sm list-volumes public 2>/dev/null || true").exec().out
            for (volId in parseSmPublicVolumeIds(smOut, devNums)) {
                Shell.cmd("sm unmount ${shellQuote(volId)} 2>/dev/null || true").exec()
            }
            Shell.cmd("sleep 0.2").exec()
        }
        val exactVold = devNums.map { "/dev/block/vold/public:${it.replace(':', ',')}" }.toSet()
        val p1 = "/dev/block/$partName"
        val p2 = "/dev/$partName"
        val mps = Shell.cmd("cat /proc/mounts 2>/dev/null").exec().out.mapNotNull { line ->
            val cols = line.trim().split(Regex("\\s+"))
            if (cols.size >= 2 && (cols[0] == p1 || cols[0] == p2 || cols[0] in exactVold)) {
                cols[1].replace("\\040", " ").replace("\\011", "\t").replace("\\134", "\\")
            } else null
        }.distinct().sortedByDescending { it.length }
        for (mp in mps) {
            val qMp = shellQuote(mp)
            Shell.cmd("umount $qMp 2>/dev/null || umount -l $qMp 2>/dev/null || true").exec()
        }
    }

    /**
     * Attaches a partition slice on a physical OTG block device for formatting/mounting.
     * Prefers silent `losetup -o/-S` over `/dev/block/sdX` (zero kernel uevent -> Android `vold` cannot hijack mid-build).
     * Automatically falls back to `blockdev --rereadpt` + `/dev/block/sdX<partIndex>` if the kernel refuses loop-on-block.
     * Returns `(blockNodeToUse, isLoopDevice)`.
     */
    internal fun attachOtgPartitionSlice(
        dev: OtgUsbDevice,
        partIndex: Int,
        offsetBytes: Long,
        sizeBytes: Long
    ): Pair<String, Boolean> {
        val qDev = shellQuote(dev.devPath)
        val loopCmd = """
            L=${'$'}(losetup -f -s -o $offsetBytes -S $sizeBytes $qDev 2>/dev/null)
            if [ -z "${'$'}L" ]; then
                L=${'$'}(losetup -f --show -o $offsetBytes --sizelimit $sizeBytes $qDev 2>/dev/null)
            fi
            if [ -z "${'$'}L" ]; then
                CAND=${'$'}(losetup -f 2>/dev/null)
                if [ -n "${'$'}CAND" ]; then
                    if losetup -o $offsetBytes -S $sizeBytes "${'$'}CAND" $qDev 2>/dev/null || \
                       losetup -o $offsetBytes --sizelimit $sizeBytes "${'$'}CAND" $qDev 2>/dev/null; then
                        L="${'$'}CAND"
                    fi
                fi
            fi
            echo "${'$'}L"
        """.trimIndent()
        val loopNode = Shell.cmd(loopCmd).exec().out.lastOrNull().orEmpty().trim()
        if (loopNode.startsWith("/dev/")) {
            return loopNode to true
        }

        Shell.cmd("sync && blockdev --rereadpt $qDev 2>/dev/null || true").exec()
        val p1 = "/dev/block/${dev.devName}$partIndex"
        val p2 = "/dev/${dev.devName}$partIndex"
        var foundNode = ""
        for (attempt in 0 until 12) {
            if (Shell.cmd("[ -b ${shellQuote(p1)} ]").exec().isSuccess) {
                foundNode = p1
                break
            }
            if (Shell.cmd("[ -b ${shellQuote(p2)} ]").exec().isSuccess) {
                foundNode = p2
                break
            }
            Shell.cmd("sleep 0.2").exec()
        }
        if (foundNode.isNotBlank()) {
            // Unmount ONLY the specific partition being attached so sibling partitions stay intact
            unmountSinglePartitionNode(dev.devName, partIndex)
            return foundNode to false
        }
        return "" to false
    }

    internal fun finalizeOtgDiskClean(dev: OtgUsbDevice) {
        val qDev = shellQuote(dev.devPath)
        Shell.cmd("sync && blockdev --flushbufs $qDev 2>/dev/null || true").exec()
        Shell.cmd("blockdev --rereadpt $qDev 2>/dev/null || true").exec()
        Shell.cmd("sync").exec()
    }

    internal fun buildSha256RangeCommand(fileOrDevPath: String, totalBytesToRead: Long): String {
        val qPath = shellQuote(fileOrDevPath)
        val safeBytes = totalBytesToRead.coerceAtLeast(0L)
        val mb = safeBytes / 1_048_576L
        val remAfterMb = safeBytes % 1_048_576L
        val sec = remAfterMb / 512L
        val tail = remAfterMb % 512L
        val parts = mutableListOf<String>()
        if (mb > 0L) {
            parts.add("dd if=$qPath bs=1048576 count=$mb 2>/dev/null")
        }
        if (sec > 0L) {
            parts.add("dd if=$qPath bs=512 skip=${mb * 2048L} count=$sec 2>/dev/null")
        }
        if (tail > 0L) {
            parts.add("dd if=$qPath bs=1 skip=${mb * 1_048_576L + sec * 512L} count=$tail 2>/dev/null")
        }
        return when (parts.size) {
            0 -> "printf '' | sha256sum 2>/dev/null"
            1 -> "${parts[0]} | sha256sum 2>/dev/null"
            else -> "( ${parts.joinToString("; ")} ) | sha256sum 2>/dev/null"
        }
    }

    private fun sha256RangeToyboxSafe(fileOrDevPath: String, totalBytesToRead: Long): String {
        val cmd = buildSha256RangeCommand(fileOrDevPath, totalBytesToRead)
        return Shell.cmd(cmd).exec().out.firstOrNull().orEmpty().trim().substringBefore(' ')
    }

    suspend fun flashImageToOtg(
        context: Context,
        config: OtgFlashConfig,
        onProgress: (WindowsBuildProgress) -> Unit
    ): ActionResult = withContext(Dispatchers.IO) {
        BuildThermalHelper.resetCancelFlag()
        val ts = nowTs()
        val entries = mutableListOf<LogEntry>()
        val target = config.targetDevice
        val srcPath = UsbGadgetEngine.resolveDirectPath(config.sourceFile.path)
        val srcName = srcPath.substringAfterLast('/')
        val compFmt = CompressedImageEngine.detectFormat(srcName)

        if (UsbGadgetEngine.getStatus().isAnyMounted) {
            return@withContext ActionResult(
                false,
                "Unmount your virtual PC drive before flashing a physical USB-C OTG pen drive.",
                entries = entries
            )
        }

        val freshList = scanUsbOtgDevices()
        val verifiedTarget = freshList.firstOrNull {
            it.devName == target.devName && it.sysfsRealPath == target.sysfsRealPath
        } ?: return@withContext ActionResult(
            false,
            "Target USB drive (${target.devPath}) disconnected or failed safety verification. Re-scan and retry.",
            entries = entries
        )

        val qSrc = shellQuote(srcPath)
        val qDev = shellQuote(verifiedTarget.devPath)
        val rawSrcBytes = Shell.cmd("stat -c '%s' $qSrc 2>/dev/null").exec().out
            .firstOrNull()?.trim()?.toLongOrNull() ?: config.sourceFile.sizeBytes
        if (rawSrcBytes <= 0L) {
            return@withContext ActionResult(false, "Source image not found or empty: $srcName", entries = entries)
        }

        val expectedWriteBytes: Long
        var streamCmd = ""
        if (compFmt != null) {
            val probe = CompressedImageEngine.probeCompressedImage(context, srcPath)
            expectedWriteBytes = if (probe?.sizeReliable == true) probe.uncompressedBytes else -1L
            if (expectedWriteBytes > 0L) {
                validateSourceFitsDevice(expectedWriteBytes, verifiedTarget.sizeBytes)?.let { err ->
                    return@withContext ActionResult(false, err, entries = entries)
                }
            }
            val toolBin = when (compFmt) {
                CompressedFormat.ZST -> CompressedImageEngine.findZstdBinary(context)
                    ?: return@withContext ActionResult(false, "Bundled zstd binary unavailable on this build.", entries = entries)
                CompressedFormat.XZ, CompressedFormat.GZ -> CompressedImageEngine.find7zBinary(context)
                    ?: return@withContext ActionResult(false, "Bundled 7-Zip extractor unavailable on this build.", entries = entries)
            }
            val qTool = shellQuote(toolBin)
            streamCmd = when (compFmt) {
                CompressedFormat.ZST -> "$qTool -d -c $qSrc"
                CompressedFormat.XZ, CompressedFormat.GZ -> "$qTool x -y -so $qSrc"
            }
        } else {
            expectedWriteBytes = rawSrcBytes
            validateSourceFitsDevice(expectedWriteBytes, verifiedTarget.sizeBytes)?.let { err ->
                return@withContext ActionResult(false, err, entries = entries)
            }
        }

        return@withContext runCatching {
            onProgress(WindowsBuildProgress("Unmounting USB partitions...", verifiedTarget.displayTitle, 0.04f, true))
            val (unmountedOk, umntErr) = unmountOtgPartitionsClean(verifiedTarget.devName)
            if (!unmountedOk) {
                return@withContext ActionResult(false, umntErr, entries = entries)
            }

            entries.add(LogEntry(ts, "otg-flash", "Flashing $srcName -> ${verifiedTarget.devPath} (${verifiedTarget.displayTitle}, ${UsbGadgetEngine.formatBytes(verifiedTarget.sizeBytes)})"))

            val statPath = "/sys/block/${verifiedTarget.devName}/stat"
            val qStat = shellQuote(statPath)
            val startSectors = parseSectorsWrittenFromStat(
                Shell.cmd("cat $qStat 2>/dev/null").exec().out.firstOrNull().orEmpty()
            )
            val prof = BuildThermalHelper.currentDefaultThermalProfile()
            val rcFile = "/data/local/tmp/pb_otg_pipe_rc"
            val qRc = shellQuote(rcFile)
            val headTmpFile = "/data/local/tmp/pb_otg_head_chk.bin"
            val qHeadTmp = shellQuote(headTmpFile)

            val flashScript = if (compFmt == null) {
                """
                set -e
                ${BuildThermalHelper.thermalExecPrefix(prof)}
                ${'$'}THERM_PFX dd if=$qSrc of=$qDev bs=4M 2>&1
                sync
                """.trimIndent()
            } else {
                """
                set -e
                ${BuildThermalHelper.thermalExecPrefix(prof)}
                rm -f $qRc
                ( $streamCmd; echo ${'$'}? > $qRc ) | ${'$'}THERM_PFX dd of=$qDev ibs=64k obs=4M 2>&1
                PIPE_RC=${'$'}(cat $qRc 2>/dev/null || echo 1)
                rm -f $qRc
                [ "${'$'}PIPE_RC" = "0" ]
                sync
                """.trimIndent()
            }

            var disconnectedMidFlash = false
            val pollJob = launch(Dispatchers.IO) {
                while (isActive) {
                    delay(500L)
                    if (!isOtgDeviceStillConnected(verifiedTarget.devName)) {
                        disconnectedMidFlash = true
                        BuildThermalHelper.cancelActiveBuildProcess()
                        break
                    }
                    val curSectors = parseSectorsWrittenFromStat(
                        Shell.cmd("cat $qStat 2>/dev/null").exec().out.firstOrNull().orEmpty()
                    )
                    val writtenBytes = ((curSectors - startSectors).coerceAtLeast(0L)) * 512L
                    if (expectedWriteBytes > 0L) {
                        val frac = (writtenBytes.toFloat() / expectedWriteBytes.toFloat()).coerceIn(0f, 0.96f)
                        val scaled = 0.06f + frac * (if (config.verifyAfterFlash) 0.78f else 0.90f)
                        onProgress(
                            WindowsBuildProgress(
                                stage = "Flashing USB-C Pen Drive...",
                                detail = "${UsbGadgetEngine.formatBytes(writtenBytes)} / ${UsbGadgetEngine.formatBytes(expectedWriteBytes)}",
                                progress = scaled,
                                isIndeterminate = false
                            )
                        )
                    } else {
                        onProgress(
                            WindowsBuildProgress(
                                stage = "Streaming to USB-C Pen Drive...",
                                detail = "Written ${UsbGadgetEngine.formatBytes(writtenBytes)} to ${verifiedTarget.devName}",
                                progress = 0.5f,
                                isIndeterminate = true
                            )
                        )
                    }
                }
            }

            val flashRes = try {
                BuildThermalHelper.runIsolatedRootScript(context, "pb_otg_flash", flashScript, trackPid = true)
            } finally {
                pollJob.cancel()
            }

            if (disconnectedMidFlash) {
                Shell.cmd("rm -f $qRc $qHeadTmp 2>/dev/null || true").exec()
                return@withContext ActionResult(false, "USB-C pen drive was unplugged during flashing.", entries = entries)
            }
            if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                Shell.cmd("rm -f $qRc $qHeadTmp 2>/dev/null || true; sync; blockdev --flushbufs $qDev 2>/dev/null || true").exec()
                return@withContext ActionResult(false, "USB-C OTG flash cancelled.", entries = entries)
            }
            if (!flashRes.isSuccess) {
                Shell.cmd("rm -f $qRc $qHeadTmp 2>/dev/null || true").exec()
                val errHint = (flashRes.out + flashRes.err).takeLast(2).joinToString(" ").take(150).trim()
                entries.add(LogEntry(ts, "otg-flash", "Flash failed: $errHint", true))
                return@withContext ActionResult(false, "USB-C OTG flash failed: ${errHint.ifBlank { "I/O error writing to pen drive" }}", entries = entries)
            }

            onProgress(WindowsBuildProgress("Flushing USB write cache — do NOT unplug...", verifiedTarget.displayTitle, 0.85f, true))
            val syncRes = Shell.cmd("sync").exec()
            if (!syncRes.isSuccess) {
                entries.add(LogEntry(ts, "otg-flash", "Kernel sync returned non-zero status after write.", true))
                return@withContext ActionResult(
                    false,
                    "Flash wrote data, but final kernel sync failed. Do not unplug abruptly; retry or safely eject.",
                    entries = entries
                )
            }
            val flushRes = Shell.cmd("if command -v blockdev >/dev/null 2>&1; then blockdev --flushbufs $qDev 2>&1; else true; fi").exec()
            if (!flushRes.isSuccess) {
                val hint = (flushRes.out + flushRes.err).takeLast(1).joinToString(" ").take(90)
                entries.add(LogEntry(ts, "otg-flash", "blockdev --flushbufs unsupported/busy ($hint); sync succeeded — proceeding to read-back verification."))
                Shell.cmd("sync").exec()
            }

            var verifySummary = "Verification skipped"
            if (config.verifyAfterFlash) {
                if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                    return@withContext ActionResult(false, "USB-C OTG flash cancelled before verification.", entries = entries)
                }
                if (compFmt == null) {
                    if (config.fullSha256Verify) {
                        onProgress(WindowsBuildProgress("Verifying full SHA-256...", verifiedTarget.devName, 0.88f, true))
                        val srcHash = Shell.cmd("sha256sum $qSrc 2>/dev/null").exec().out.firstOrNull().orEmpty().trim().substringBefore(' ')
                        val dstHash = sha256RangeToyboxSafe(verifiedTarget.devPath, expectedWriteBytes)
                        if (srcHash.length != 64 || srcHash != dstHash) {
                            entries.add(LogEntry(ts, "otg-flash", "Full SHA-256 mismatch! src=$srcHash dst=$dstHash", true))
                            return@withContext ActionResult(false, "Verification FAILED: USB pen drive data does not match source image (possible bad/fake flash).", entries = entries)
                        }
                        verifySummary = "Full SHA-256 verified"
                    } else {
                        onProgress(WindowsBuildProgress("Verifying head & tail blocks...", verifiedTarget.devName, 0.88f, true))
                        val checkBytes = expectedWriteBytes.coerceAtMost(VERIFY_CHUNK_BYTES)
                        val srcHead = sha256RangeToyboxSafe(srcPath, checkBytes)
                        val dstHead = sha256RangeToyboxSafe(verifiedTarget.devPath, checkBytes)
                        if (srcHead.length != 64 || srcHead != dstHead) {
                            entries.add(LogEntry(ts, "otg-flash", "Head block mismatch! src=$srcHead dst=$dstHead", true))
                            return@withContext ActionResult(false, "Verification FAILED on head blocks (possible faulty USB pen drive).", entries = entries)
                        }
                        if (!isActive || BuildThermalHelper.isBuildCancelled()) {
                            return@withContext ActionResult(false, "USB-C OTG flash cancelled during verification.", entries = entries)
                        }
                        val totalFullMb = expectedWriteBytes / (1024L * 1024L)
                        val tailSkipMb = (totalFullMb - VERIFY_CHUNK_MB).coerceAtLeast(0L)
                        if (totalFullMb > VERIFY_CHUNK_MB && tailSkipMb >= 1L) {
                            val tailCountMb = (totalFullMb - tailSkipMb).coerceIn(1L, VERIFY_CHUNK_MB)
                            val srcTail = Shell.cmd("dd if=$qSrc bs=1048576 skip=$tailSkipMb count=$tailCountMb 2>/dev/null | sha256sum 2>/dev/null").exec().out.firstOrNull().orEmpty().trim().substringBefore(' ')
                            val dstTail = Shell.cmd("dd if=$qDev bs=1048576 skip=$tailSkipMb count=$tailCountMb 2>/dev/null | sha256sum 2>/dev/null").exec().out.firstOrNull().orEmpty().trim().substringBefore(' ')
                            if (srcTail.length != 64 || srcTail != dstTail) {
                                entries.add(LogEntry(ts, "otg-flash", "Tail block mismatch! src=$srcTail dst=$dstTail", true))
                                return@withContext ActionResult(false, "Verification FAILED on tail blocks (possible fake-capacity USB drive).", entries = entries)
                            }
                        }
                        verifySummary = "Head & Tail blocks verified"
                    }
                } else {
                    onProgress(WindowsBuildProgress("Verifying written head blocks...", verifiedTarget.devName, 0.88f, true))
                    val checkBytes = if (expectedWriteBytes > 0L) expectedWriteBytes.coerceAtMost(VERIFY_CHUNK_BYTES) else VERIFY_CHUNK_BYTES
                    val capRes = try {
                        Shell.cmd(
                            """
                            rm -f $qHeadTmp
                            if head -c 1 </dev/zero >/dev/null 2>&1; then
                                $streamCmd 2>/dev/null | head -c $checkBytes > $qHeadTmp 2>/dev/null || true
                            else
                                $streamCmd 2>/dev/null | dd bs=65536 count=${(checkBytes / 65536L).coerceAtLeast(1L)} > $qHeadTmp 2>/dev/null || true
                            fi
                            """.trimIndent()
                        ).exec()
                        val capBytes = Shell.cmd("stat -c '%s' $qHeadTmp 2>/dev/null").exec().out
                            .firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                        if (capBytes <= 0L) {
                            Triple(0L, "", "")
                        } else {
                            val srcHead = Shell.cmd("sha256sum $qHeadTmp 2>/dev/null").exec().out
                                .firstOrNull().orEmpty().trim().substringBefore(' ')
                            val dstHead = sha256RangeToyboxSafe(verifiedTarget.devPath, capBytes)
                            Triple(capBytes, srcHead, dstHead)
                        }
                    } finally {
                        Shell.cmd("rm -f $qHeadTmp 2>/dev/null || true").exec()
                    }
                    val (verifiedBytes, srcHead, dstHead) = capRes
                    if (verifiedBytes <= 0L || srcHead.length != 64 || srcHead != dstHead) {
                        entries.add(LogEntry(ts, "otg-flash", "Streamed head block mismatch ($verifiedBytes B)! src=$srcHead dst=$dstHead", true))
                        return@withContext ActionResult(false, "Verification FAILED on written head blocks.", entries = entries)
                    }
                    verifySummary = "Head ${UsbGadgetEngine.formatBytes(verifiedBytes)} verified"
                }
            }

            finalizeOtgDiskClean(verifiedTarget)
            entries.add(LogEntry(ts, "otg-flash", "Flashed $srcName to ${verifiedTarget.displayTitle} cleanly ($verifySummary)"))
            onProgress(WindowsBuildProgress("USB Ready!", verifySummary, 1f, false))
            ActionResult(
                success = true,
                userMessage = "Flashed $srcName to ${verifiedTarget.displayTitle} ($verifySummary). Safe to unplug USB-C OTG drive.",
                entries = entries
            )
        }.getOrElse { e ->
            Shell.cmd("rm -f /data/local/tmp/pb_otg_pipe_rc /data/local/tmp/pb_otg_head_chk.bin 2>/dev/null || true").exec()
            entries.add(LogEntry(ts, "otg-flash", "Error: ${e.message}", true))
            ActionResult(false, "USB-C OTG flash failed: ${e.message ?: "Unexpected error"}", entries = entries)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtgFlasherSheet(
    initialSource: ImageFile?,
    availableImages: List<ImageFile>,
    defaultDir: String,
    presets: List<AutounattendPreset>,
    systemTweaksPresets: List<SystemTweaksPreset>,
    systemTweaksSearchHistory: List<String>,
    isAnyVirtualDriveMounted: Boolean,
    flashing: Boolean,
    progress: WindowsBuildProgress,
    onDismiss: () -> Unit,
    onUnmountAllVirtual: () -> Unit,
    onCancelFlash: () -> Unit,
    onSavePreset: (AutounattendPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onSaveSystemTweaksPreset: (SystemTweaksPreset) -> Unit,
    onDeleteSystemTweaksPreset: (String) -> Unit,
    onUpdateSearchHistory: (List<String>) -> Unit,
    onStartRawFlash: (OtgFlashConfig) -> Unit,
    onStartWindowsOtgFlash: (WindowsBuildConfig) -> Unit,
    onStartLinuxOtgFlash: (LinuxBuildConfig) -> Unit,
    onStartMultiBootOtgFlash: (VentoyBuildConfig) -> Unit
) {
    val context = LocalContext.current
    val isoImages = remember(availableImages) { availableImages.filter { it.name.endsWith(".iso", ignoreCase = true) } }
    var selectedMode by remember(initialSource?.path) {
        mutableStateOf(OtgUsbFlasher.recommendModeForSource(initialSource))
    }
    var selectedSource by remember(initialSource?.path) {
        mutableStateOf(initialSource ?: availableImages.firstOrNull())
    }
    var otgDevices by remember { mutableStateOf<List<OtgUsbDevice>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<OtgUsbDevice?>(null) }
    var scanning by remember { mutableStateOf(true) }
    var scanNonce by remember { mutableIntStateOf(0) }

    // Raw DD options
    var verifyAfterFlash by remember { mutableStateOf(true) }
    var fullSha256Verify by remember { mutableStateOf(false) }

    // Direct Windows OTG options
    var winScheme by remember { mutableStateOf(PartitionScheme.GPT) }
    var winEditions by remember { mutableStateOf<List<WindowsEditionItem>>(emptyList()) }
    var winSelectedEdIdx by remember { mutableStateOf<Int?>(null) }
    var winLoadingEditions by remember { mutableStateOf(false) }
    var winExp by remember { mutableStateOf(WindowsExperienceConfig()) }
    var winTweaksCfg by remember { mutableStateOf(WinUtilTweaksConfig()) }
    var winEiCfgUnlock by remember { mutableStateOf(false) }
    var winVolumeLabel by remember { mutableStateOf("") }
    var winDriversDir by remember { mutableStateOf("") }
    var savedDriverPacks by remember { mutableStateOf<List<DriverPackItem>>(emptyList()) }
    var selectedDriverPackDirs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var driverPackReloadNonce by remember { mutableIntStateOf(0) }
    var showDriversFolderPicker by remember { mutableStateOf(false) }
    var showDriverPackManager by remember { mutableStateOf(false) }

    // Direct Linux OTG options
    var linuxUsbMode by remember { mutableStateOf(LinuxUsbMode.WITH_PERSISTENCE) }
    var linuxWriteMethod by remember { mutableStateOf(LinuxWriteMethod.EXTRACTED_FAT32) }
    var linuxPersistGb by remember { mutableIntStateOf(4) }
    var linuxStyle by remember { mutableStateOf(LinuxPersistenceStyle.UBUNTU_WRITABLE) }
    var linuxVolumeLabel by remember { mutableStateOf("") }
    var linuxIsoHasOver4Gb by remember { mutableStateOf(false) }
    var linuxProbingIso by remember { mutableStateOf(false) }

    // Direct Multi-Boot OTG options
    var mbScheme by remember { mutableStateOf(PartitionScheme.GPT) }
    var mbFs by remember { mutableStateOf(VentoyFilesystem.AUTO) }
    var mbVolumeLabel by remember { mutableStateOf("POCKETBOOT") }
    var mbInitialIso by remember(initialSource?.path) {
        mutableStateOf(initialSource?.takeIf { it.name.endsWith(".iso", ignoreCase = true) })
    }
    var mbMoveInitialIso by remember { mutableStateOf(false) }
    var mbShowTweaks by remember { mutableStateOf(false) }
    var mbExp by remember { mutableStateOf(WindowsExperienceConfig()) }
    var mbTweaksCfg by remember { mutableStateOf(WinUtilTweaksConfig()) }
    var mbDriversDir by remember { mutableStateOf("") }
    var mbSelectedPackDirs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showMbDriversPicker by remember { mutableStateOf(false) }

    // Confirm Dialog state
    var showConfirmFlashDialog by remember { mutableStateOf(false) }
    var confirmInput by remember { mutableStateOf("") }

    LaunchedEffect(scanNonce, isAnyVirtualDriveMounted) {
        if (isAnyVirtualDriveMounted) {
            scanning = false
            otgDevices = emptyList()
            selectedDevice = null
            return@LaunchedEffect
        }
        scanning = true
        val found = OtgUsbFlasher.scanUsbOtgDevices()
        otgDevices = found
        selectedDevice = found.firstOrNull { it.devPath == selectedDevice?.devPath } ?: found.firstOrNull()
        scanning = false
    }

    LaunchedEffect(defaultDir, driverPackReloadNonce) {
        val loaded = DriverPackManager.listPacks(defaultDir)
        savedDriverPacks = loaded
        val valid = loaded.map { it.dirPath }.toSet()
        selectedDriverPackDirs = selectedDriverPackDirs.intersect(valid)
        mbSelectedPackDirs = mbSelectedPackDirs.intersect(valid)
    }

    LaunchedEffect(selectedMode, isoImages) {
        if (selectedMode == OtgFlashMode.DIRECT_WINDOWS || selectedMode == OtgFlashMode.DIRECT_LINUX) {
            val cur = selectedSource
            if (cur == null || !cur.name.endsWith(".iso", ignoreCase = true)) {
                selectedSource = when (selectedMode) {
                    OtgFlashMode.DIRECT_WINDOWS -> isoImages.firstOrNull { IsoInspector.quickGuessByName(it.name) == IsoOsType.WINDOWS } ?: isoImages.firstOrNull()
                    OtgFlashMode.DIRECT_LINUX -> isoImages.firstOrNull { IsoInspector.quickGuessByName(it.name) != IsoOsType.WINDOWS } ?: isoImages.firstOrNull()
                    else -> cur
                }
            }
        }
    }

    val activeSource = selectedSource
    val activeTarget = selectedDevice
    LaunchedEffect(selectedMode, activeSource?.path) {
        if (selectedMode == OtgFlashMode.DIRECT_WINDOWS && activeSource != null && activeSource.name.endsWith(".iso", true)) {
            winLoadingEditions = true
            winSelectedEdIdx = null
            winEditions = WindowsImageBuilder.inspectWindowsEditions(context, activeSource.path)
            winLoadingEditions = false
        } else {
            winEditions = emptyList()
            winSelectedEdIdx = null
            winLoadingEditions = false
        }
        if (selectedMode == OtgFlashMode.DIRECT_LINUX && activeSource != null && activeSource.name.endsWith(".iso", true)) {
            linuxProbingIso = true
            val maxF = LinuxImageBuilder.inspectMaxFileBytesInIso(context, activeSource.path)
            linuxIsoHasOver4Gb = maxF > 4_294_967_295L
            if (linuxIsoHasOver4Gb) {
                linuxWriteMethod = LinuxWriteMethod.HYBRID_DD
            }
            val low = activeSource.name.lowercase(Locale.US)
            linuxStyle = when {
                low.contains("kali") || low.contains("debian") || low.contains("tails") || low.contains("gparted") -> LinuxPersistenceStyle.DEBIAN_KALI
                low.contains("pop") -> LinuxPersistenceStyle.CASPER_RW
                else -> LinuxPersistenceStyle.UBUNTU_WRITABLE
            }
            linuxProbingIso = false
        } else {
            linuxIsoHasOver4Gb = false
            linuxProbingIso = false
        }
    }

    val over2TiB = activeTarget != null && DiskPartitioner.isOverMbrLimit(activeTarget.sizeBytes)
    LaunchedEffect(over2TiB) {
        if (over2TiB) {
            winScheme = PartitionScheme.GPT
            mbScheme = PartitionScheme.GPT
        }
    }

    val maxLinuxPersist = remember(activeTarget?.sizeBytes, activeSource?.sizeBytes) {
        if (activeTarget != null && activeSource != null) {
            DiskPartitioner.maxLinuxPersistenceGb(activeTarget.sizeBytes, activeSource.sizeBytes)
        } else 16
    }
    LaunchedEffect(maxLinuxPersist) {
        if (maxLinuxPersist > 0) {
            linuxPersistGb = linuxPersistGb.coerceIn(1, maxLinuxPersist)
        }
    }

    val isCompressedSrc = activeSource != null && CompressedImageEngine.detectFormat(activeSource.name) != null
    val detectedWinOs = remember(activeSource?.name) {
        if (activeSource != null) WindowsCustomizationEngine.detectWindowsOsKind(activeSource.name) else WindowsOsKind.WIN11
    }
    val detectedWinArch = remember(activeSource?.name) {
        if (activeSource != null) WindowsCustomizationEngine.detectProcessorArchitecture(activeSource.name) else "amd64"
    }

    val winPacksBytes = remember(savedDriverPacks, selectedDriverPackDirs) {
        savedDriverPacks.filter { it.dirPath in selectedDriverPackDirs }.sumOf { it.sizeBytes }
    }
    val mbPacksBytes = remember(savedDriverPacks, mbSelectedPackDirs) {
        savedDriverPacks.filter { it.dirPath in mbSelectedPackDirs }.sumOf { it.sizeBytes }
    }

    val validationError: String? = when (selectedMode) {
        OtgFlashMode.DIRECT_WINDOWS -> when {
            activeTarget == null -> null
            activeSource == null || !activeSource.name.endsWith(".iso", true) -> "Select a Windows .iso file for Direct Windows USB Mode."
            activeTarget.sizeBytes < activeSource.sizeBytes + winPacksBytes + (768L * 1024L * 1024L) ->
                "USB drive (${UsbGadgetEngine.formatBytes(activeTarget.sizeBytes)}) needs at least ${UsbGadgetEngine.formatBytes(activeSource.sizeBytes + winPacksBytes + 768L * 1024L * 1024L)} for ISO + Driver Packs + boot.wim patching."
            else -> null
        }
        OtgFlashMode.DIRECT_LINUX -> when {
            activeTarget == null -> null
            activeSource == null || !activeSource.name.endsWith(".iso", true) -> "Select a Linux or Rescue .iso file."
            activeTarget.sizeBytes < activeSource.sizeBytes + (256L * 1024L * 1024L) ->
                "USB drive (${UsbGadgetEngine.formatBytes(activeTarget.sizeBytes)}) is too small for ${activeSource.name}."
            linuxUsbMode == LinuxUsbMode.WITH_PERSISTENCE && maxLinuxPersist < 1 ->
                "Not enough remaining space on USB drive for a 1 GB persistence partition. Turn off Persistence or use a larger drive."
            else -> null
        }
        OtgFlashMode.DIRECT_MULTIBOOT -> when {
            activeTarget == null -> null
            activeTarget.sizeBytes < (mbInitialIso?.sizeBytes ?: 0L) + mbPacksBytes + (256L * 1024L * 1024L) ->
                "USB drive (${UsbGadgetEngine.formatBytes(activeTarget.sizeBytes)}) is too small for Multi-Boot + selected ISO & Driver Packs."
            else -> null
        }
        OtgFlashMode.RAW_DD -> when {
            activeTarget == null -> null
            activeSource == null -> "Select an image file to flash."
            !isCompressedSrc -> OtgUsbFlasher.validateSourceFitsDevice(activeSource.sizeBytes, activeTarget.sizeBytes)
            else -> null
        }
    }

    val canStartFlash = activeTarget != null && validationError == null &&
        !(selectedMode == OtgFlashMode.DIRECT_LINUX && linuxProbingIso) &&
        (selectedMode == OtgFlashMode.DIRECT_MULTIBOOT || activeSource != null)

    val modeSummaryForConfirm = when (selectedMode) {
        OtgFlashMode.DIRECT_WINDOWS -> {
            val effSch = if (activeTarget != null) DiskPartitioner.effectiveSchemeForDisk(winScheme, activeTarget.sizeBytes) else winScheme
            val drvs = selectedDriverPackDirs.size + if (winDriversDir.isNotBlank()) 1 else 0
            "Direct Windows (${effSch.label}, NTFS) · ${activeSource?.name ?: ""} · ${winExp.activeCount()} Exp / ${winTweaksCfg.activeCount()} Tweaks" +
                if (drvs > 0) " · $drvs Driver Source(s)" else ""
        }
        OtgFlashMode.DIRECT_LINUX -> {
            val pTxt = if (linuxUsbMode == LinuxUsbMode.WITH_PERSISTENCE) " + $linuxPersistGb GB ${linuxStyle.volumeLabel}" else " (Live only)"
            "Direct Linux (${linuxWriteMethod.title})$pTxt · ${activeSource?.name ?: ""}"
        }
        OtgFlashMode.DIRECT_MULTIBOOT -> {
            val effSch = if (activeTarget != null) DiskPartitioner.effectiveSchemeForDisk(mbScheme, activeTarget.sizeBytes) else mbScheme
            "Direct Multi-Boot (${effSch.label}, ${mbFs.label})" + (mbInitialIso?.let { " + ${it.name}" } ?: " (Empty ready for ISOs)")
        }
        OtgFlashMode.RAW_DD -> "Raw / Stream DD · ${activeSource?.name ?: ""}"
    }

    ModalBottomSheet(
        onDismissRequest = { if (!flashing) onDismiss() },
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
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(PocketColors.warningBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Usb, null, tint = PocketColors.warning, modifier = Modifier.size(21.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("USB-C OTG Pen Drive Flasher", style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary)
                    Text("Direct Windows, Linux Persistence, Multi-Boot & Raw DD", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                }
                if (!flashing && !isAnyVirtualDriveMounted) {
                    IconButton(onClick = { scanNonce++ }) {
                        Icon(Icons.Rounded.Refresh, "Rescan USB-C OTG", tint = PocketColors.textSecondary, modifier = Modifier.size(19.dp))
                    }
                }
            }

            if (flashing) {
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
                        Text("Do not unplug your USB-C pen drive while flashing.", fontSize = 11.sp, color = PocketColors.warning)
                    }
                }
                OutlinedButton(
                    onClick = onCancelFlash,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PocketColors.danger)
                ) {
                    Icon(Icons.Rounded.Close, null, tint = PocketColors.danger, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel Flash", color = PocketColors.danger, fontWeight = FontWeight.SemiBold)
                }
            } else if (isAnyVirtualDriveMounted) {
                Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.warningBg, border = BorderStroke(1.dp, PocketColors.warning)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.warning, modifier = Modifier.size(19.dp))
                            Text("Virtual PC Drive Currently Active", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                        }
                        Text(
                            "Your phone's USB-C port cannot act as a virtual PC drive and a USB-C OTG Host at the same time. Eject all virtual drives first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textSecondary
                        )
                        Button(
                            onClick = onUnmountAllVirtual,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PocketColors.danger, contentColor = Color.White)
                        ) {
                            Icon(Icons.Rounded.Eject, null, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Eject Virtual Drive(s)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                // Step 1: Target Physical USB-C OTG Drive
                Text("1. Target Physical USB-C Pen Drive", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                if (scanning) {
                    Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PocketColors.accent)
                            Text("Scanning external USB-C OTG block devices...", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                    }
                } else if (otgDevices.isEmpty()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No external USB-C pen drive detected", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text(
                                "Plug a physical USB pen drive into your phone via a USB-C OTG adapter, then tap Refresh above. Internal UFS/eMMC phone storage and 4Kn enclosures are permanently blocked.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PocketColors.textSecondary
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        otgDevices.forEach { dev ->
                            val sel = selectedDevice?.devPath == dev.devPath
                            Surface(
                                modifier = Modifier.fillMaxWidth().bounceClick { selectedDevice = dev },
                                shape = RoundedCornerShape(12.dp),
                                color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                border = BorderStroke(if (sel) 1.5.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Rounded.Storage, null, tint = if (sel) PocketColors.accentSoft else PocketColors.textSecondary, modifier = Modifier.size(20.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(dev.displayTitle, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "${dev.devPath} · ${UsbGadgetEngine.formatBytes(dev.sizeBytes)} · ${dev.logicalBlockSize} B verified",
                                            style = PocketColors.monoStyle,
                                            color = PocketColors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(Icons.Rounded.VerifiedUser, null, tint = PocketColors.success, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Step 2: Flash Mode Selector (4 Modes)
                Text("2. Flash Mode (0 GB Phone Storage Used)", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OtgFlashMode.entries.forEach { mode ->
                        val sel = selectedMode == mode
                        Surface(
                            modifier = Modifier.weight(1f).bounceClick { selectedMode = mode },
                            shape = RoundedCornerShape(9.dp),
                            color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                            border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp, horizontal = 3.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = mode.shortLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Text(selectedMode.subtitle, fontSize = 11.sp, color = PocketColors.textSecondary)

                // Step 3: Source Selector (for Windows, Linux, Raw DD; optional for Multi-Boot)
                if (selectedMode != OtgFlashMode.DIRECT_MULTIBOOT) {
                    val candidates = if (selectedMode == OtgFlashMode.RAW_DD) availableImages else isoImages
                    Text(
                        if (selectedMode == OtgFlashMode.RAW_DD) "3. Source Image (${candidates.size} available)" else "3. Source ISO (${candidates.size} .iso files)",
                        style = MaterialTheme.typography.labelLarge,
                        color = PocketColors.textPrimary
                    )
                    if (candidates.isEmpty()) {
                        Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                            Text("No matching files found in Your Drives.", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 152.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            candidates.forEach { img ->
                                val sel = selectedSource?.path == img.path
                                Surface(
                                    modifier = Modifier.fillMaxWidth().bounceClick { selectedSource = img },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(img.name, modifier = Modifier.weight(1f), fontSize = 12.sp, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(UsbGadgetEngine.formatBytes(img.sizeBytes), style = PocketColors.monoStyle, color = PocketColors.textMuted)
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 4: Mode-Specific Controls
                when (selectedMode) {
                    OtgFlashMode.DIRECT_WINDOWS -> {
                        Text("4. Windows USB Options", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PartitionScheme.entries.forEach { s ->
                                val disabledMbr = over2TiB && s == PartitionScheme.MBR
                                val sel = winScheme == s
                                Surface(
                                    modifier = Modifier.weight(1f).bounceClick(enabled = !disabledMbr) { winScheme = s },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(s.label, style = MaterialTheme.typography.labelMedium, color = if (sel) PocketColors.accentSoft else if (disabledMbr) PocketColors.textMuted else PocketColors.textPrimary)
                                        Text(if (disabledMbr) "Locked (>2 TiB)" else s.subtitle, fontSize = 11.sp, color = PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        if (winLoadingEditions) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = PocketColors.accent)
                                Text("Inspecting Windows editions inside install.wim/esd...", fontSize = 11.sp, color = PocketColors.textSecondary)
                            }
                        } else if (winEditions.isNotEmpty()) {
                            Text("Windows Edition (Optional Pre-Select)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                            Column(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 128.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().bounceClick { winSelectedEdIdx = null },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (winSelectedEdIdx == null) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (winSelectedEdIdx == null) 1.dp else 0.5.dp, if (winSelectedEdIdx == null) PocketColors.accent else PocketColors.border)
                                ) {
                                    Text("Ask during Windows Setup (Default)", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 11.5.sp, color = PocketColors.textPrimary)
                                }
                                winEditions.forEach { ed ->
                                    val sel = winSelectedEdIdx == ed.index
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().bounceClick { winSelectedEdIdx = ed.index },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                        border = BorderStroke(if (sel) 1.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                    ) {
                                        Text("${ed.index}. ${ed.name}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 11.5.sp, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        WindowsTwoPaneCustomizer(
                            osKind = detectedWinOs,
                            arch = detectedWinArch,
                            experience = winExp,
                            winUtil = winTweaksCfg,
                            presets = presets,
                            systemTweaksPresets = systemTweaksPresets,
                            systemTweaksSearchHistory = systemTweaksSearchHistory,
                            showCa2023Option = true,
                            showCustomScriptOption = true,
                            defaultBrowseDir = defaultDir,
                            selectedEditionIndex = winSelectedEdIdx,
                            onExperienceChange = { winExp = it },
                            onWinUtilChange = { winTweaksCfg = it },
                            onApplyPreset = { p ->
                                winScheme = if (over2TiB) PartitionScheme.GPT else runCatching { PartitionScheme.valueOf(p.schemeName) }.getOrDefault(PartitionScheme.GPT)
                                winExp = WindowsExperienceConfig(p.bypassRequirements, p.bypassMicrosoftAccount, p.disableDataCollection, p.disableBitLocker, p.qolImprovements, p.disableSponsoredApps, false, p.skipWifiSetup, p.createLocalUser, p.localUsername, "", p.skipOobe, p.useCa2023Bootloaders, p.applySkuSiPolicy)
                                winTweaksCfg = WinUtilTweaksConfig.fromJsonString(p.winUtilJson, legacyClassicContextMenu = p.classicContextMenu)
                            },
                            onSavePreset = { n -> onSavePreset(AutounattendPreset(n, winScheme.name, winExp.bypassRequirements, winExp.bypassMicrosoftAccount, winExp.disableDataCollection, winExp.disableBitLocker, winExp.disableSponsoredApps, false, winExp.skipWifiSetup, winExp.createLocalUser, winExp.localUsername, winExp.skipOobe, winExp.qolImprovements, winExp.useCa2023Bootloaders, winExp.applySkuSiPolicy, winTweaksCfg.toJsonString())) },
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
                            onOpenManager = { showDriverPackManager = true }
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth().bounceClick { showDriversFolderPicker = true },
                            shape = RoundedCornerShape(12.dp),
                            color = PocketColors.surfaceInput,
                            border = BorderStroke(0.5.dp, if (winDriversDir.isNotBlank()) PocketColors.accent else PocketColors.border)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                Icon(Icons.Rounded.FolderOpen, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Extra Drivers / Apps Folder (/Drivers)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                    Text(if (winDriversDir.isNotBlank()) winDriversDir.replace("/data/media/0", "/sdcard") else "Optional — tap to browse", fontSize = 11.sp, color = PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (winDriversDir.isNotBlank()) {
                                    IconButton(onClick = { winDriversDir = "" }, modifier = Modifier.size(44.dp)) {
                                        Icon(Icons.Rounded.Close, "Clear folder", tint = PocketColors.textMuted, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }

                        Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { winEiCfgUnlock = !winEiCfgUnlock }.padding(horizontal = 13.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Unlock All Editions (ei.cfg)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                    Text("Bypass OEM motherboard Home key lock", fontSize = 11.sp, color = PocketColors.textSecondary)
                                }
                                PocketSwitch(checked = winEiCfgUnlock, onCheckedChange = { winEiCfgUnlock = it }, activeColor = PocketColors.accent)
                            }
                        }

                        OutlinedTextField(
                            value = winVolumeLabel,
                            onValueChange = { winVolumeLabel = it.take(32) },
                            label = { Text("USB Volume Label (Optional)") },
                            placeholder = { Text("Auto (e.g., WIN11_USB)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OtgFlashMode.DIRECT_LINUX -> {
                        Text("4. Linux USB & Persistence Options", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                        if (linuxProbingIso) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = PocketColors.accent)
                                Text("Checking ISO file sizes for FAT32 compatibility...", fontSize = 11.sp, color = PocketColors.textSecondary)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinuxUsbMode.entries.forEach { m ->
                                val sel = linuxUsbMode == m
                                Surface(
                                    modifier = Modifier.weight(1f).bounceClick { linuxUsbMode = m },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(m.title, style = MaterialTheme.typography.labelMedium, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary)
                                        Text(m.subtitle, fontSize = 11.sp, color = PocketColors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinuxWriteMethod.entries.forEach { wm ->
                                val lockedFat = linuxIsoHasOver4Gb && wm == LinuxWriteMethod.EXTRACTED_FAT32
                                val sel = linuxWriteMethod == wm
                                Surface(
                                    modifier = Modifier.weight(1f).bounceClick(enabled = !lockedFat) { linuxWriteMethod = wm },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(wm.title, style = MaterialTheme.typography.labelMedium, color = if (sel) PocketColors.accentSoft else if (lockedFat) PocketColors.textMuted else PocketColors.textPrimary)
                                        Text(if (lockedFat) "ISO has >4 GB file" else wm.subtitle, fontSize = 11.sp, color = PocketColors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        if (linuxUsbMode == LinuxUsbMode.WITH_PERSISTENCE) {
                            Text(
                                "Persistence Partition Size: $linuxPersistGb GB (Max $maxLinuxPersist GB on this drive)",
                                style = MaterialTheme.typography.labelMedium,
                                color = PocketColors.textPrimary
                            )
                            val presetsGb = listOf(1, 2, 4, 8, 16, maxLinuxPersist).filter { it in 1..maxLinuxPersist.coerceAtLeast(1) }.distinct()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                presetsGb.take(5).forEach { gb ->
                                    val sel = linuxPersistGb == gb
                                    Surface(
                                        modifier = Modifier.weight(1f).bounceClick { linuxPersistGb = gb },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                        border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                            Text(if (gb == maxLinuxPersist && gb > 16) "Max (${gb}G)" else "${gb} GB", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary)
                                        }
                                    }
                                }
                            }

                            Text("Persistence Style", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                LinuxPersistenceStyle.entries.forEach { st ->
                                    val sel = linuxStyle == st
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().bounceClick { linuxStyle = st },
                                        shape = RoundedCornerShape(9.dp),
                                        color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                        border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                                            Text("${st.title} (${st.volumeLabel})", style = MaterialTheme.typography.labelMedium, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary)
                                            Text(st.subtitle, fontSize = 11.sp, color = PocketColors.textSecondary)
                                        }
                                    }
                                }
                            }
                        }

                        if (linuxWriteMethod == LinuxWriteMethod.EXTRACTED_FAT32) {
                            OutlinedTextField(
                                value = linuxVolumeLabel,
                                onValueChange = { linuxVolumeLabel = it.take(11) },
                                label = { Text("FAT32 Volume Label (Optional)") },
                                placeholder = { Text("Auto from ISO") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    OtgFlashMode.DIRECT_MULTIBOOT -> {
                        Text("3. Direct Multi-Boot Pen Drive Options", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PartitionScheme.entries.forEach { s ->
                                val disabledMbr = over2TiB && s == PartitionScheme.MBR
                                val sel = mbScheme == s
                                Surface(
                                    modifier = Modifier.weight(1f).bounceClick(enabled = !disabledMbr) { mbScheme = s },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(s.label, style = MaterialTheme.typography.labelMedium, color = if (sel) PocketColors.accentSoft else if (disabledMbr) PocketColors.textMuted else PocketColors.textPrimary)
                                        Text(if (disabledMbr) "Locked (>2 TiB)" else s.subtitle, fontSize = 11.sp, color = PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            VentoyFilesystem.entries.forEach { fs ->
                                val sel = mbFs == fs
                                Surface(
                                    modifier = Modifier.weight(1f).bounceClick { mbFs = fs },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                        Text(fs.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = mbVolumeLabel,
                            onValueChange = { mbVolumeLabel = it.take(16) },
                            label = { Text("Multi-Boot Partition Label") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Initial ISO to Copy (Optional)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 128.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth().bounceClick { mbInitialIso = null },
                                shape = RoundedCornerShape(8.dp),
                                color = if (mbInitialIso == null) PocketColors.accentBg else PocketColors.surfaceInput,
                                border = BorderStroke(if (mbInitialIso == null) 1.dp else 0.5.dp, if (mbInitialIso == null) PocketColors.accent else PocketColors.border)
                            ) {
                                Text("None — install clean Multi-Boot pen drive only", modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 11.5.sp, color = PocketColors.textPrimary)
                            }
                            isoImages.forEach { img ->
                                val sel = mbInitialIso?.path == img.path
                                Surface(
                                    modifier = Modifier.fillMaxWidth().bounceClick { mbInitialIso = img },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (sel) 1.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(img.name, modifier = Modifier.weight(1f), fontSize = 11.5.sp, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(UsbGadgetEngine.formatBytes(img.sizeBytes), style = PocketColors.monoStyle, color = PocketColors.textMuted)
                                    }
                                }
                            }
                        }

                        if (mbInitialIso != null) {
                            Surface(shape = RoundedCornerShape(10.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { mbMoveInitialIso = !mbMoveInitialIso }.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Delete source ISO from phone after verified copy", fontSize = 11.5.sp, color = PocketColors.textPrimary, modifier = Modifier.weight(1f))
                                    PocketSwitch(checked = mbMoveInitialIso, onCheckedChange = { mbMoveInitialIso = it }, activeColor = PocketColors.accent)
                                }
                            }
                        }

                        val mbExpCnt = mbExp.activeCount()
                        val mbTweaksCnt = mbTweaksCfg.activeCount()
                        Surface(
                            modifier = Modifier.fillMaxWidth().bounceClick { mbShowTweaks = !mbShowTweaks },
                            shape = RoundedCornerShape(11.dp),
                            color = PocketColors.accentBg,
                            border = BorderStroke(0.75.dp, PocketColors.accent)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Windows Tweaks & WinPE Drivers ($mbExpCnt Exp · $mbTweaksCnt Tweaks)", style = MaterialTheme.typography.labelMedium, color = PocketColors.accentSoft)
                                Text(if (mbShowTweaks) "Hide" else "Configure", style = MaterialTheme.typography.labelSmall, color = PocketColors.accentSoft)
                            }
                        }
                        if (mbShowTweaks) {
                            WindowsTwoPaneCustomizer(
                                osKind = WindowsOsKind.WIN11,
                                arch = "amd64",
                                experience = mbExp,
                                winUtil = mbTweaksCfg,
                                presets = presets,
                                systemTweaksPresets = systemTweaksPresets,
                                systemTweaksSearchHistory = systemTweaksSearchHistory,
                                showCa2023Option = false,
                                showCustomScriptOption = false,
                                defaultBrowseDir = defaultDir,
                                onExperienceChange = { mbExp = it },
                                onWinUtilChange = { mbTweaksCfg = it },
                                onApplyPreset = { p ->
                                    mbExp = WindowsExperienceConfig(p.bypassRequirements, p.bypassMicrosoftAccount, p.disableDataCollection, p.disableBitLocker, p.qolImprovements, p.disableSponsoredApps, false, p.skipWifiSetup, p.createLocalUser, p.localUsername, "", p.skipOobe, p.useCa2023Bootloaders, p.applySkuSiPolicy)
                                    mbTweaksCfg = WinUtilTweaksConfig.fromJsonString(p.winUtilJson, legacyClassicContextMenu = p.classicContextMenu)
                                },
                                onSavePreset = { n -> onSavePreset(AutounattendPreset(n, mbScheme.name, mbExp.bypassRequirements, mbExp.bypassMicrosoftAccount, mbExp.disableDataCollection, mbExp.disableBitLocker, mbExp.disableSponsoredApps, false, mbExp.skipWifiSetup, mbExp.createLocalUser, mbExp.localUsername, mbExp.skipOobe, mbExp.qolImprovements, mbExp.useCa2023Bootloaders, mbExp.applySkuSiPolicy, mbTweaksCfg.toJsonString())) },
                                onDeletePreset = onDeletePreset,
                                onSaveSystemTweaksPreset = onSaveSystemTweaksPreset,
                                onDeleteSystemTweaksPreset = onDeleteSystemTweaksPreset,
                                onUpdateSearchHistory = onUpdateSearchHistory
                            )
                            DriverPackMultiSelectorSection(
                                packs = savedDriverPacks,
                                selectedPackDirs = mbSelectedPackDirs,
                                onTogglePack = { dir ->
                                    mbSelectedPackDirs = if (dir in mbSelectedPackDirs) mbSelectedPackDirs - dir else mbSelectedPackDirs + dir
                                },
                                onOpenManager = { showDriverPackManager = true }
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth().bounceClick { showMbDriversPicker = true },
                                shape = RoundedCornerShape(11.dp),
                                color = PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, if (mbDriversDir.isNotBlank()) PocketColors.accent else PocketColors.border)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.FolderOpen, null, tint = PocketColors.accentSoft, modifier = Modifier.size(17.dp))
                                    Text(if (mbDriversDir.isNotBlank()) mbDriversDir.replace("/data/media/0", "/sdcard") else "Extra WinPE Drivers Folder (Optional)", fontSize = 11.5.sp, color = PocketColors.textSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (mbDriversDir.isNotBlank()) {
                                        IconButton(onClick = { mbDriversDir = "" }, modifier = Modifier.size(44.dp)) {
                                            Icon(Icons.Rounded.Close, "Clear", tint = PocketColors.textMuted, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OtgFlashMode.RAW_DD -> {
                        Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { verifyAfterFlash = !verifyAfterFlash }.padding(horizontal = 13.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Read-Back Verification (Recommended)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                        Text("Catches faulty or fake-capacity USB drives immediately", fontSize = 11.sp, color = PocketColors.textSecondary)
                                    }
                                    PocketSwitch(checked = verifyAfterFlash, onCheckedChange = { verifyAfterFlash = it }, activeColor = PocketColors.success)
                                }
                                if (verifyAfterFlash && !isCompressedSrc) {
                                    PocketHairlineDivider()
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { fullSha256Verify = !fullSha256Verify }.padding(horizontal = 13.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Full Drive SHA-256 Check (Slower)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                            Text("Off = fast Head 16 MB + Tail 16 MB verification", fontSize = 11.sp, color = PocketColors.textSecondary)
                                        }
                                        PocketSwitch(checked = fullSha256Verify, onCheckedChange = { fullSha256Verify = it }, activeColor = PocketColors.accent)
                                    }
                                }
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Surface(shape = RoundedCornerShape(10.dp), color = PocketColors.dangerBg, border = BorderStroke(0.75.dp, PocketColors.danger)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.danger, modifier = Modifier.size(16.dp))
                            Text(validationError, style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                        }
                    }
                }

                Button(
                    onClick = {
                        confirmInput = ""
                        showConfirmFlashDialog = true
                    },
                    enabled = canStartFlash,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.danger, contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.WarningAmber, null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Erase & Flash ${selectedMode.title}...", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showDriversFolderPicker) {
        RootFolderPickerSheet("Select Drivers / Apps Folder", "Files inside will be copied to /Drivers on the USB drive", winDriversDir.ifBlank { defaultDir }, { showDriversFolderPicker = false }) {
            winDriversDir = it
            showDriversFolderPicker = false
        }
    }
    if (showMbDriversPicker) {
        RootFolderPickerSheet("Select WinPE Drivers Folder", "Packed into drivers_inject.zip on the Multi-Boot USB", mbDriversDir.ifBlank { defaultDir }, { showMbDriversPicker = false }) {
            mbDriversDir = it
            showMbDriversPicker = false
        }
    }
    if (showDriverPackManager) {
        DriverPackManagerSheet(
            defaultDir = defaultDir,
            onDismiss = { showDriverPackManager = false },
            onPacksChanged = { driverPackReloadNonce++ },
            onMessage = { msg, _ -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        )
    }

    if (showConfirmFlashDialog && activeTarget != null && canStartFlash) {
        val typedOk = confirmInput.trim().equals("FLASH", ignoreCase = true)
        AlertDialog(
            onDismissRequest = { showConfirmFlashDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Confirm Destructive Flash", color = PocketColors.danger) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "ALL DATA on this external USB drive will be permanently erased:",
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textPrimary
                    )
                    Surface(shape = RoundedCornerShape(9.dp), color = PocketColors.dangerBg, border = BorderStroke(0.75.dp, PocketColors.danger)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Target: ${activeTarget.displayTitle}", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            val devIdMeta = buildString {
                                append(activeTarget.devPath)
                                if (activeTarget.devMajorMinor.isNotBlank()) append(" · dev ${activeTarget.devMajorMinor}")
                                append(" · ${activeTarget.logicalBlockSize} B")
                            }
                            Text("Device: $devIdMeta (${UsbGadgetEngine.formatBytes(activeTarget.sizeBytes)})", style = PocketColors.monoStyle, color = PocketColors.textSecondary)
                            Text("Mode: $modeSummaryForConfirm", style = PocketColors.monoStyle, color = PocketColors.textSecondary)
                        }
                    }
                    OutlinedTextField(
                        value = confirmInput,
                        onValueChange = { confirmInput = it.take(10) },
                        label = { Text("Type FLASH to confirm") },
                        placeholder = { Text("FLASH") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmFlashDialog = false
                        when (selectedMode) {
                            OtgFlashMode.DIRECT_WINDOWS -> {
                                val src = activeSource ?: return@Button
                                onStartWindowsOtgFlash(
                                    WindowsBuildConfig(
                                        isoFile = src,
                                        outputDir = defaultDir,
                                        outputName = activeTarget.displayTitle,
                                        scheme = DiskPartitioner.effectiveSchemeForDisk(winScheme, activeTarget.sizeBytes),
                                        selectedEditionIndex = winSelectedEdIdx,
                                        selectedEditionName = winEditions.firstOrNull { it.index == winSelectedEdIdx }?.name,
                                        osKind = detectedWinOs,
                                        arch = detectedWinArch,
                                        experience = winExp,
                                        winUtil = winTweaksCfg,
                                        eiCfgUnlock = winEiCfgUnlock,
                                        extraDriversDir = winDriversDir,
                                        extraDriverPackDirs = selectedDriverPackDirs.toList(),
                                        autoMountAfterBuild = false,
                                        volumeLabel = winVolumeLabel.trim(),
                                        directBlockTarget = activeTarget
                                    )
                                )
                            }
                            OtgFlashMode.DIRECT_LINUX -> {
                                val src = activeSource ?: return@Button
                                onStartLinuxOtgFlash(
                                    LinuxBuildConfig(
                                        isoFile = src,
                                        outputDir = defaultDir,
                                        outputName = activeTarget.displayTitle,
                                        usbMode = linuxUsbMode,
                                        writeMethod = if (linuxIsoHasOver4Gb) LinuxWriteMethod.HYBRID_DD else linuxWriteMethod,
                                        scheme = PartitionScheme.MBR,
                                        persistenceGb = linuxPersistGb.coerceIn(1, maxLinuxPersist.coerceAtLeast(1)),
                                        style = linuxStyle,
                                        autoMountAfterBuild = false,
                                        volumeLabel = linuxVolumeLabel.trim(),
                                        directBlockTarget = activeTarget
                                    )
                                )
                            }
                            OtgFlashMode.DIRECT_MULTIBOOT -> {
                                onStartMultiBootOtgFlash(
                                    VentoyBuildConfig(
                                        outputDir = defaultDir,
                                        outputName = activeTarget.displayTitle,
                                        sizeMb = ((activeTarget.sizeBytes / 1_048_576L).coerceIn(512L, 131072L)).toInt(),
                                        scheme = DiskPartitioner.effectiveSchemeForDisk(mbScheme, activeTarget.sizeBytes),
                                        filesystem = mbFs,
                                        win11BypassCheck = mbExp.bypassRequirements,
                                        win11BypassNro = mbExp.bypassMicrosoftAccount,
                                        experience = mbExp,
                                        winUtil = mbTweaksCfg,
                                        driversDir = mbDriversDir,
                                        extraDriverPackDirs = mbSelectedPackDirs.toList(),
                                        initialIsoToAdd = mbInitialIso,
                                        moveInitialIso = mbMoveInitialIso,
                                        autoMountAfterBuild = false,
                                        volumeLabel = mbVolumeLabel.trim().ifBlank { "POCKETBOOT" },
                                        directBlockTarget = activeTarget
                                    )
                                )
                            }
                            OtgFlashMode.RAW_DD -> {
                                val src = activeSource ?: return@Button
                                onStartRawFlash(
                                    OtgFlashConfig(
                                        sourceFile = src,
                                        targetDevice = activeTarget,
                                        verifyAfterFlash = verifyAfterFlash,
                                        fullSha256Verify = fullSha256Verify && !isCompressedSrc
                                    )
                                )
                            }
                        }
                    },
                    enabled = typedOk,
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.danger, contentColor = Color.White)
                ) { Text("Start Flashing", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmFlashDialog = false }) { Text("Cancel", color = PocketColors.textSecondary) }
            }
        )
    }
}
