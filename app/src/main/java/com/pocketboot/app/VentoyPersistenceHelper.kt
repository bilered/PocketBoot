package com.pocketboot.app

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Manages ext4 `.dat` Linux persistence images stored inside a Multi-Boot drive's
 * `/ventoy/persistence/` directory and wired into `ventoy.json`'s `"persistence"` array.
 */
object VentoyPersistenceHelper {

    private const val TEMP_PERS_EXT4_MNT = "/data/local/tmp/pb_vtoy_pers_mnt"
    private const val TEMP_PERS_SEED_DIR = "/data/local/tmp/pb_vtoy_pers_seed"
    private const val TEMP_PERS_STAGE_FILE = "/data/local/tmp/pb_vtoy_pers_stage.dat"

    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    fun isLinuxPersistenceEligible(fileName: String): Boolean {
        if (!fileName.endsWith(".iso", ignoreCase = true)) return false
        return IsoInspector.quickGuessByName(fileName) != IsoOsType.WINDOWS
    }

    /**
     * Generates a clean, deterministic, traversal-safe persistence backend path inside Partition 1.
     */
    fun guessStyleForIso(isoName: String): LinuxPersistenceStyle {
        val low = isoName.lowercase()
        return when {
            low.contains("kali") || low.contains("debian") || low.contains("tails") ||
                low.contains("gparted") || low.contains("clonezilla") || low.contains("parrot") ->
                LinuxPersistenceStyle.DEBIAN_KALI
            low.contains("pop-os") || low.contains("pop_os") ->
                LinuxPersistenceStyle.CASPER_RW
            else ->
                LinuxPersistenceStyle.UBUNTU_WRITABLE
        }
    }

    fun buildPersistenceBackendPath(isoRelPath: String, style: LinuxPersistenceStyle): String {
        val leaf = isoRelPath.replace('\\', '/').substringAfterLast('/').substringBeforeLast('.')
        val safeStem = leaf
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '.')
            .take(32)
            .ifBlank { "linux" }
        return "/ventoy/persistence/${safeStem}_${style.volumeLabel}.dat"
    }

    private const val FAT32_MAX_FILE_BYTES = 4_294_967_295L

    internal fun isFat32PersistenceTooLarge(fsLabel: String, sizeBytes: Long): Boolean {
        val low = fsLabel.lowercase()
        val isFat = (low.contains("fat32") || low.contains("vfat")) && !low.contains("exfat")
        return isFat && sizeBytes > FAT32_MAX_FILE_BYTES
    }

    internal fun hasValidExt4SuperblockMagic(headerAt1080: ByteArray): Boolean =
        headerAt1080.size >= 2 &&
            headerAt1080[0] == 0x53.toByte() &&
            headerAt1080[1] == 0xEF.toByte()

    private fun verifyExt4FileSuperblock(context: Context, datFullPath: String): Boolean {
        val tmpMagic = File(context.cacheDir, "pb_ext4_magic_${System.nanoTime()}.bin")
        val qDat = shellQuote(datFullPath)
        val qTmp = shellQuote(tmpMagic.absolutePath)
        return try {
            Shell.cmd("dd if=$qDat of=$qTmp bs=1 skip=1080 count=2 2>/dev/null && chmod 0644 $qTmp").exec()
            val bytes = if (tmpMagic.exists()) tmpMagic.readBytes() else ByteArray(0)
            hasValidExt4SuperblockMagic(bytes)
        } catch (_: Exception) {
            false
        } finally {
            tmpMagic.delete()
        }
    }

    private fun findMke2fsBinary(): String? {
        val out = Shell.cmd(
            """
            if command -v mke2fs >/dev/null 2>&1; then command -v mke2fs
            elif command -v mkfs.ext4 >/dev/null 2>&1; then command -v mkfs.ext4
            elif [ -x /system/bin/mke2fs ]; then echo /system/bin/mke2fs
            fi
            """.trimIndent()
        ).exec().out.firstOrNull().orEmpty().trim()
        return out.ifBlank { null }
    }

    suspend fun createPersistenceForIso(
        context: Context,
        ventoyImgPath: String,
        isoRelativePath: String,
        sizeGb: Int,
        style: LinuxPersistenceStyle,
        onProgress: (WindowsBuildProgress) -> Unit
    ): ActionResult = withContext(Dispatchers.IO) {
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        VentoyImageBuilder.mountedGuardError(resolvedImg, "creating Linux persistence")
            ?.let { return@withContext ActionResult(false, it) }

        val info = VentoyImageBuilder.probeVentoyImage(resolvedImg)
            ?: return@withContext ActionResult(false, "Selected file is not a valid Multi-Boot image.")

        val mke2fsBin = findMke2fsBinary()
            ?: return@withContext ActionResult(false, "No mke2fs/mkfs.ext4 binary found on device to format ext4 persistence.")

        val clampedGb = sizeGb.coerceIn(1, 16)
        val sizeBytes = clampedGb.toLong() * 1024L * 1024L * 1024L
        val normIso = VentoyThemeCatalog.normalizeRelPath(isoRelativePath)
        val backendRel = buildPersistenceBackendPath(normIso, style)

        var partLoop = VentoyImageBuilder.attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
        if (partLoop.isBlank() || !partLoop.startsWith("/dev/")) {
            return@withContext ActionResult(false, "Could not attach Multi-Boot partition.")
        }
        var stageLoop = ""
        val datFullPath = "${VentoyImageBuilder.TEMP_VENTOY_MNT}$backendRel"
        val qDat = shellQuote(datFullPath)
        var datAllocated = false

        fun cleanupAll(deleteDat: Boolean) {
            if (stageLoop.isNotBlank()) {
                BuildThermalHelper.unmountAndDetachClean(
                    mountPoints = listOf(TEMP_PERS_EXT4_MNT),
                    loopDevices = listOf(stageLoop),
                    removeDirs = true
                )
                stageLoop = ""
            }
            Shell.cmd("rm -rf $TEMP_PERS_SEED_DIR $TEMP_PERS_STAGE_FILE 2>/dev/null || true").exec()
            if (deleteDat && datAllocated) {
                Shell.cmd("rm -f $qDat 2>/dev/null || true").exec()
            }
            if (partLoop.isNotBlank()) {
                VentoyImageBuilder.unmountVentoyPartition1(partLoop)
                partLoop = ""
            }
        }

        return@withContext runCatching {
            onProgress(WindowsBuildProgress("Opening Multi-Boot drive...", "Mounting Partition 1", 0.10f, true))
            val fsLabel = VentoyImageBuilder.mountVentoyPartition1(context, partLoop)
                ?: return@withContext ActionResult(false, "Could not mount Multi-Boot Partition 1.")

            if (isFat32PersistenceTooLarge(fsLabel, sizeBytes)) {
                cleanupAll(deleteDat = false)
                return@withContext ActionResult(
                    false,
                    "FAT32 cannot store files >= 4 GB. Pick 1 GB or 2 GB persistence, or recreate the Multi-Boot drive with exFAT/NTFS."
                )
            }

            val existingRaw = Shell.cmd("cat ${VentoyImageBuilder.TEMP_VENTOY_MNT}/ventoy/ventoy.json 2>/dev/null")
                .exec().out.joinToString("\n")
            val rootObj = runCatching { if (existingRaw.isNotBlank()) JSONObject(existingRaw) else JSONObject() }
                .getOrDefault(JSONObject())
            val oldBackend = VentoyThemeCatalog.readPersistenceEntries(rootObj)
                .firstOrNull { it.imagePath.equals(normIso, ignoreCase = true) }?.backendPath
            val oldBackendBytes = if (!oldBackend.isNullOrBlank()) {
                Shell.cmd("stat -c '%s' ${shellQuote("${VentoyImageBuilder.TEMP_VENTOY_MNT}$oldBackend")} 2>/dev/null")
                    .exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            } else 0L

            val (_, rawPartFree) = VentoyImageBuilder.queryMountSpaceBytes(VentoyImageBuilder.TEMP_VENTOY_MNT)
            val effPartFree = if (rawPartFree >= 0L) rawPartFree + oldBackendBytes else -1L
            val neededInside = sizeBytes + (64L * 1024L * 1024L)
            if (effPartFree <= 0L || effPartFree < neededInside) {
                cleanupAll(deleteDat = false)
                return@withContext ActionResult(
                    false,
                    "Not enough free space inside Multi-Boot drive for ${clampedGb} GB persistence (only ${if (effPartFree > 0L) UsbGadgetEngine.formatBytes(effPartFree) else "0 B"} free). Use Resize Drive first."
                )
            }
            val parentDir = resolvedImg.substringBeforeLast('/')
            val rawHostFree = VentoyImageBuilder.queryHostFreeSpaceBytes(parentDir)
            val effHostFree = if (rawHostFree >= 0L) rawHostFree + oldBackendBytes else -1L
            if (effHostFree in 0L until (sizeBytes + (128L * 1024L * 1024L))) {
                cleanupAll(deleteDat = false)
                return@withContext ActionResult(
                    false,
                    "Not enough free phone storage (${UsbGadgetEngine.formatBytes(effHostFree)} free) for ${clampedGb} GB persistence file."
                )
            }

            if (!oldBackend.isNullOrBlank() && oldBackend != backendRel) {
                Shell.cmd("rm -f ${shellQuote("${VentoyImageBuilder.TEMP_VENTOY_MNT}$oldBackend")} 2>/dev/null || true").exec()
            }

            onProgress(WindowsBuildProgress("Allocating ${clampedGb} GB persistence...", "${style.volumeLabel} ($fsLabel)", 0.30f, false))
            val persDir = "${VentoyImageBuilder.TEMP_VENTOY_MNT}/ventoy/persistence"
            val allocOk = Shell.cmd(
                "mkdir -p ${shellQuote(persDir)} && rm -f $qDat && " +
                    "(truncate -s $sizeBytes $qDat 2>/dev/null || dd if=/dev/zero of=$qDat bs=1M count=0 seek=${clampedGb * 1024} 2>&1)"
            ).exec().isSuccess
            datAllocated = true
            val actualDatSize = if (allocOk) {
                Shell.cmd("stat -c '%s' $qDat 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            } else 0L
            if (!allocOk || actualDatSize != sizeBytes) {
                cleanupAll(deleteDat = true)
                return@withContext ActionResult(false, "Failed to allocate ${clampedGb} GB persistence file inside Multi-Boot drive.")
            }

            // Format `.dat` DIRECTLY as a regular file with mke2fs — avoids nested loop devices over exFAT/FUSE!
            onProgress(WindowsBuildProgress("Formatting ext4 persistence...", "Label: ${style.volumeLabel} (direct format)", 0.58f, true))
            val qMke2fs = shellQuote(mke2fsBin)
            val qLabel = shellQuote(style.volumeLabel)
            var formatSucceeded = false
            var lastFmtErr = ""

            if (style == LinuxPersistenceStyle.DEBIAN_KALI) {
                // Seed `/persistence.conf` directly during mke2fs via `-d` (no losetup or mount needed!)
                val seedReady = Shell.cmd(
                    "rm -rf $TEMP_PERS_SEED_DIR && mkdir -p $TEMP_PERS_SEED_DIR && printf '/ union\\n' > $TEMP_PERS_SEED_DIR/persistence.conf"
                ).exec().isSuccess
                if (seedReady) {
                    val fmtRes = Shell.cmd("$qMke2fs -t ext4 -F -m 0 -L $qLabel -d $TEMP_PERS_SEED_DIR $qDat 2>&1").exec()
                    if (fmtRes.isSuccess && verifyExt4FileSuperblock(context, datFullPath)) {
                        formatSucceeded = true
                    } else {
                        lastFmtErr = (fmtRes.out + fmtRes.err).takeLast(2).joinToString(" ").take(160)
                    }
                }
                // Fallback if device mke2fs lacks `-d`: stage sparse file on native /data/local/tmp (never nested in exFAT/FUSE)
                if (!formatSucceeded) {
                    onProgress(WindowsBuildProgress("Formatting Debian/Kali persistence...", "Staging on native /data", 0.68f, true))
                    val qStage = shellQuote(TEMP_PERS_STAGE_FILE)
                    val stageAlloc = Shell.cmd(
                        "rm -f $qStage && (truncate -s $sizeBytes $qStage 2>/dev/null || dd if=/dev/zero of=$qStage bs=1M count=0 seek=${clampedGb * 1024} 2>&1)"
                    ).exec().isSuccess
                    val stageFmt = stageAlloc && Shell.cmd("$qMke2fs -t ext4 -F -m 0 -L $qLabel $qStage 2>&1").exec().isSuccess
                    if (stageFmt) {
                        stageLoop = Shell.cmd(
                            """
                            L=${'$'}(losetup -f -s $qStage 2>/dev/null)
                            if [ -z "${'$'}L" ]; then L=${'$'}(losetup -f --show $qStage 2>/dev/null); fi
                            if [ -z "${'$'}L" ]; then
                                CAND=${'$'}(losetup -f 2>/dev/null)
                                if [ -n "${'$'}CAND" ] && losetup "${'$'}CAND" $qStage 2>/dev/null; then L="${'$'}CAND"; fi
                            fi
                            echo "${'$'}L"
                            """.trimIndent()
                        ).exec().out.lastOrNull().orEmpty().trim()
                        val wroteConf = stageLoop.startsWith("/dev/") && Shell.cmd(
                            "mkdir -p $TEMP_PERS_EXT4_MNT && mount -t ext4 -o rw ${shellQuote(stageLoop)} $TEMP_PERS_EXT4_MNT 2>&1 && " +
                                "printf '/ union\\n' > $TEMP_PERS_EXT4_MNT/persistence.conf"
                        ).exec().isSuccess
                        if (stageLoop.isNotBlank()) {
                            BuildThermalHelper.unmountAndDetachClean(listOf(TEMP_PERS_EXT4_MNT), listOf(stageLoop), removeDirs = true)
                            stageLoop = ""
                        }
                        if (wroteConf) {
                            onProgress(WindowsBuildProgress("Copying persistence to Multi-Boot...", "${clampedGb} GB", 0.80f, true))
                            val cpOk = Shell.cmd("cp -f $qStage $qDat 2>&1").exec().isSuccess
                            val cpSz = Shell.cmd("stat -c '%s' $qDat 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                            if (cpOk && cpSz == sizeBytes && verifyExt4FileSuperblock(context, datFullPath)) {
                                formatSucceeded = true
                            }
                        }
                    }
                    Shell.cmd("rm -f $qStage 2>/dev/null || true").exec()
                }
            } else {
                val fmtRes = Shell.cmd("$qMke2fs -t ext4 -F -m 0 -L $qLabel $qDat 2>&1").exec()
                if (fmtRes.isSuccess && verifyExt4FileSuperblock(context, datFullPath)) {
                    formatSucceeded = true
                } else {
                    lastFmtErr = (fmtRes.out + fmtRes.err).takeLast(2).joinToString(" ").take(160)
                }
            }

            if (!formatSucceeded) {
                cleanupAll(deleteDat = true)
                val detail = if (lastFmtErr.isNotBlank()) ": $lastFmtErr" else ""
                return@withContext ActionResult(false, "ext4 persistence format failed$detail")
            }

            onProgress(WindowsBuildProgress("Updating Multi-Boot config...", backendRel, 0.90f, false))
            VentoyThemeCatalog.upsertPersistenceEntry(rootObj, normIso, backendRel)
            val tmpJson = File(context.cacheDir, "pb_vtoy_pers.json").apply {
                writeText(rootObj.toString(2), Charsets.UTF_8)
            }
            val wroteJson = try {
                Shell.cmd(
                    "mkdir -p ${VentoyImageBuilder.TEMP_VENTOY_MNT}/ventoy && " +
                        "cp -f ${shellQuote(tmpJson.absolutePath)} ${VentoyImageBuilder.TEMP_VENTOY_MNT}/ventoy/ventoy.json"
                ).exec().isSuccess
            } finally {
                tmpJson.delete()
            }
            if (!wroteJson) {
                cleanupAll(deleteDat = true)
                return@withContext ActionResult(false, "Failed to update ventoy.json with persistence entry.")
            }

            cleanupAll(deleteDat = false)
            UsbGadgetEngine.fixUserStoragePermissions(context, resolvedImg, isDir = false)
            val imgBytes = Shell.cmd("stat -c '%s' ${shellQuote(resolvedImg)} 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            if (imgBytes > 0L) UsbGadgetEngine.syncMediaStoreFileSize(context, resolvedImg, imgBytes)
            IsoInspector.invalidate(resolvedImg)

            onProgress(WindowsBuildProgress("Done!", "${clampedGb} GB ${style.volumeLabel}", 1f, false))
            ActionResult(true, "Added ${clampedGb} GB ${style.volumeLabel} persistence for ${normIso.substringAfterLast('/')}.")
        }.getOrElse { e ->
            cleanupAll(deleteDat = true)
            ActionResult(false, "Linux persistence failed: ${e.message ?: "Unexpected error"}")
        }
    }

    suspend fun removePersistenceForIso(
        context: Context,
        ventoyImgPath: String,
        isoRelativePath: String
    ): ActionResult = withContext(Dispatchers.IO) {
        val resolvedImg = UsbGadgetEngine.resolveDirectPath(ventoyImgPath)
        VentoyImageBuilder.mountedGuardError(resolvedImg, "removing Linux persistence")
            ?.let { return@withContext ActionResult(false, it) }

        val info = VentoyImageBuilder.probeVentoyImage(resolvedImg)
            ?: return@withContext ActionResult(false, "Not a valid Multi-Boot image.")
        val loopDev = VentoyImageBuilder.attachPartition1Loop(resolvedImg, info.part1OffsetBytes, info.part1SizeBytes)
        if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) {
            return@withContext ActionResult(false, "Could not attach Multi-Boot partition.")
        }
        return@withContext try {
            VentoyImageBuilder.mountVentoyPartition1(context, loopDev)
                ?: return@withContext ActionResult(false, "Could not mount Multi-Boot partition.")
            val raw = Shell.cmd("cat ${VentoyImageBuilder.TEMP_VENTOY_MNT}/ventoy/ventoy.json 2>/dev/null")
                .exec().out.joinToString("\n")
            val rootObj = runCatching { if (raw.isNotBlank()) JSONObject(raw) else JSONObject() }.getOrDefault(JSONObject())
            val (_, removedBackend) = VentoyThemeCatalog.removePersistenceEntry(rootObj, isoRelativePath)
            if (!removedBackend.isNullOrBlank()) {
                Shell.cmd("rm -f ${shellQuote("${VentoyImageBuilder.TEMP_VENTOY_MNT}$removedBackend")} 2>/dev/null || true").exec()
            }
            val tmpJson = File(context.cacheDir, "pb_vtoy_pers_rm.json").apply {
                writeText(rootObj.toString(2), Charsets.UTF_8)
            }
            try {
                Shell.cmd(
                    "mkdir -p ${VentoyImageBuilder.TEMP_VENTOY_MNT}/ventoy && " +
                        "cp -f ${shellQuote(tmpJson.absolutePath)} ${VentoyImageBuilder.TEMP_VENTOY_MNT}/ventoy/ventoy.json"
                ).exec()
            } finally {
                tmpJson.delete()
            }
            IsoInspector.invalidate(resolvedImg)
            ActionResult(true, "Removed Linux persistence for ${isoRelativePath.substringAfterLast('/')}. Tap Reclaim Space to free phone storage.")
        } finally {
            VentoyImageBuilder.unmountVentoyPartition1(loopDev)
        }
    }
}
