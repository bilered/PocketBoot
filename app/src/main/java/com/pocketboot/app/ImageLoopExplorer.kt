package com.pocketboot.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ImagePartitionSlice(
    val schemeLabel: String,
    val offsetBytes: Long,
    val sizeBytes: Long
)

data class ImageMountSession(
    val imgPath: String,
    val loopDev: String,
    val mountPoint: String,
    val fsLabel: String,
    val schemeLabel: String,
    val readOnly: Boolean,
    var modified: Boolean = false
)

data class ImageExplorerEntry(
    val relPath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long
)

object ImageLoopExplorer {
    private const val TEMP_EXPLORE_MNT = "/data/local/tmp/pb_img_explore_mnt"
    private const val ILLEGAL_FOLDER_CHARS = ":*?\"<>|"

    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    internal fun isSelfImportPath(hostFilePath: String, sessionImgPath: String): Boolean {
        val a = UsbGadgetEngine.resolveDirectPath(hostFilePath).trimEnd('/')
        val b = UsbGadgetEngine.resolveDirectPath(sessionImgPath).trimEnd('/')
        return a.isNotEmpty() && a == b
    }

    internal fun sanitizeFolderName(raw: String): String = raw
        .replace('/', ' ')
        .replace('\\', ' ')
        .replace("..", "")
        .filter { ch -> ch.code >= 0x20 && ch !in ILLEGAL_FOLDER_CHARS }
        .trim()
        .trim('.')

    /**
     * Pure-JVM testable GPT/MBR/Superfloppy Partition 1 slicer from the first 17,408 bytes (LBA 0..33) of an `.img`.
     */
    fun parsePartition1Slice(headerBytes: ByteArray, totalImgBytes: Long): ImagePartitionSlice {
        if (headerBytes.size >= 512 * 3) {
            val gptSig = String(headerBytes, 512, 8, Charsets.US_ASCII)
            if (gptSig == "EFI PART") {
                val p1FirstLba = ByteBuffer.wrap(headerBytes.copyOfRange(1024 + 32, 1024 + 40))
                    .order(ByteOrder.LITTLE_ENDIAN).long
                val p1LastLba = ByteBuffer.wrap(headerBytes.copyOfRange(1024 + 40, 1024 + 48))
                    .order(ByteOrder.LITTLE_ENDIAN).long
                if (p1FirstLba > 0L && p1LastLba >= p1FirstLba) {
                    val off = p1FirstLba * 512L
                    val sz = (p1LastLba - p1FirstLba + 1L) * 512L
                    if (off + sz <= totalImgBytes + 512L) {
                        return ImagePartitionSlice("GPT Part 1", off, sz.coerceAtMost(totalImgBytes - off))
                    }
                }
            }
        }
        if (headerBytes.size >= 512 &&
            headerBytes[510] == 0x55.toByte() &&
            headerBytes[511] == 0xAA.toByte()
        ) {
            for (slot in 0 until 4) {
                val base = 446 + slot * 16
                val pType = headerBytes[base + 4].toInt() and 0xFF
                if (pType == 0x00 || pType == 0xEE) continue
                val startLba = ByteBuffer.wrap(headerBytes.copyOfRange(base + 8, base + 12))
                    .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                val sectors = ByteBuffer.wrap(headerBytes.copyOfRange(base + 12, base + 16))
                    .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                if (startLba > 0L && sectors > 0L) {
                    val off = startLba * 512L
                    val sz = sectors * 512L
                    if (off < totalImgBytes) {
                        return ImagePartitionSlice("MBR Part ${slot + 1}", off, sz.coerceAtMost(totalImgBytes - off))
                    }
                }
            }
        }
        return ImagePartitionSlice("Raw Image", 0L, totalImgBytes.coerceAtLeast(0L))
    }

    suspend fun openSession(context: Context, imgPath: String): Pair<ImageMountSession?, String?> =
        withContext(Dispatchers.IO) {
            val resolved = UsbGadgetEngine.resolveDirectPath(imgPath)
            if (UsbGadgetEngine.getStatus().mountedLunFor(resolved) != null) {
                return@withContext null to "${resolved.substringAfterLast('/')} is currently mounted to your PC. Unmount it before exploring files."
            }
            val qImg = shellQuote(resolved)
            val totalSize = Shell.cmd("stat -c '%s' $qImg 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            if (totalSize < 65_536L) {
                return@withContext null to "Image file not found or too small to contain a filesystem."
            }

            val hdrTmp = File(context.cacheDir, "pb_explore_hdr_${System.nanoTime()}.bin")
            val slice = try {
                val qHdr = shellQuote(hdrTmp.absolutePath)
                Shell.cmd("dd if=$qImg of=$qHdr bs=512 count=34 2>/dev/null && chmod 0644 $qHdr").exec()
                val raw = if (hdrTmp.exists()) hdrTmp.readBytes() else ByteArray(0)
                parsePartition1Slice(raw, totalSize)
            } finally {
                hdrTmp.delete()
            }

            BuildThermalHelper.unmountAndDetachClean(listOf(TEMP_EXPLORE_MNT), emptyList(), removeDirs = true)

            val loopCmd = if (slice.offsetBytes > 0L && slice.sizeBytes > 0L) {
                """
                L=${'$'}(losetup -f -s -o ${slice.offsetBytes} -S ${slice.sizeBytes} $qImg 2>/dev/null)
                if [ -z "${'$'}L" ]; then L=${'$'}(losetup -f --show -o ${slice.offsetBytes} --sizelimit ${slice.sizeBytes} $qImg 2>/dev/null); fi
                if [ -z "${'$'}L" ]; then
                    CAND=${'$'}(losetup -f 2>/dev/null)
                    if [ -n "${'$'}CAND" ] && (losetup -o ${slice.offsetBytes} -S ${slice.sizeBytes} "${'$'}CAND" $qImg 2>/dev/null || losetup -o ${slice.offsetBytes} --sizelimit ${slice.sizeBytes} "${'$'}CAND" $qImg 2>/dev/null); then L="${'$'}CAND"; fi
                fi
                echo "${'$'}L"
                """.trimIndent()
            } else {
                """
                L=${'$'}(losetup -f -s $qImg 2>/dev/null)
                if [ -z "${'$'}L" ]; then L=${'$'}(losetup -f --show $qImg 2>/dev/null); fi
                if [ -z "${'$'}L" ]; then
                    CAND=${'$'}(losetup -f 2>/dev/null)
                    if [ -n "${'$'}CAND" ] && losetup "${'$'}CAND" $qImg 2>/dev/null; then L="${'$'}CAND"; fi
                fi
                echo "${'$'}L"
                """.trimIndent()
            }
            val loopDev = Shell.cmd(loopCmd).exec().out.lastOrNull().orEmpty().trim()
            if (loopDev.isBlank() || !loopDev.startsWith("/dev/")) {
                return@withContext null to "Could not attach kernel loop device for ${resolved.substringAfterLast('/')}."
            }

            val qLoop = shellQuote(loopDev)
            val procFs = Shell.cmd("cat /proc/filesystems 2>/dev/null").exec().out
                .map { it.trim().substringAfterLast('\t').substringAfterLast(' ').lowercase() }
                .filter { it.isNotEmpty() }.toSet()
            val ntfs3gBin = WindowsImageBuilder.findBundledBinary(context, "libntfs_3g.so", listOf("ntfs-3g", "/system/bin/ntfs-3g"))
            val hasFuse = ("fuse" in procFs || "fuseblk" in procFs) && Shell.cmd("[ -e /dev/fuse ]").exec().isSuccess

            Shell.cmd("mkdir -p $TEMP_EXPLORE_MNT").exec()

            val rwCandidates = mutableListOf<Pair<String, String>>()
            if ("ntfs3" in procFs) rwCandidates.add("NTFS (ntfs3)" to "mount -t ntfs3 -o rw $qLoop $TEMP_EXPLORE_MNT 2>&1")
            if ("tntfs" in procFs) rwCandidates.add("NTFS (tntfs)" to "mount -t tntfs -o rw $qLoop $TEMP_EXPLORE_MNT 2>&1")
            if ("exfat" in procFs) rwCandidates.add("exFAT" to "mount -t exfat -o rw $qLoop $TEMP_EXPLORE_MNT 2>&1")
            if ("sdfat" in procFs) rwCandidates.add("exFAT (sdfat)" to "mount -t sdfat -o rw $qLoop $TEMP_EXPLORE_MNT 2>&1")
            if (ntfs3gBin != null && hasFuse) {
                val qN3g = shellQuote(ntfs3gBin)
                rwCandidates.add("NTFS (ntfs-3g)" to "$qN3g -o rw,big_writes,noatime $qLoop $TEMP_EXPLORE_MNT 2>&1")
            }
            rwCandidates.add("FAT32" to "mount -t vfat -o rw $qLoop $TEMP_EXPLORE_MNT 2>&1")
            if ("ext4" in procFs) rwCandidates.add("ext4" to "mount -t ext4 -o rw $qLoop $TEMP_EXPLORE_MNT 2>&1")
            rwCandidates.add("Auto" to "mount -o rw $qLoop $TEMP_EXPLORE_MNT 2>&1")

            for ((label, cmd) in rwCandidates) {
                if (Shell.cmd(cmd).exec().isSuccess) {
                    return@withContext ImageMountSession(
                        imgPath = resolved,
                        loopDev = loopDev,
                        mountPoint = TEMP_EXPLORE_MNT,
                        fsLabel = label,
                        schemeLabel = slice.schemeLabel,
                        readOnly = false
                    ) to null
                }
            }

            val roCandidates = listOf(
                "Read-Only Auto" to "mount -o ro $qLoop $TEMP_EXPLORE_MNT 2>&1",
                "UDF (RO)" to "mount -t udf -o ro $qLoop $TEMP_EXPLORE_MNT 2>&1",
                "ISO9660 (RO)" to "mount -t iso9660 -o ro $qLoop $TEMP_EXPLORE_MNT 2>&1"
            )
            for ((label, cmd) in roCandidates) {
                if (Shell.cmd(cmd).exec().isSuccess) {
                    return@withContext ImageMountSession(
                        imgPath = resolved,
                        loopDev = loopDev,
                        mountPoint = TEMP_EXPLORE_MNT,
                        fsLabel = label,
                        schemeLabel = slice.schemeLabel,
                        readOnly = true
                    ) to null
                }
            }

            BuildThermalHelper.unmountAndDetachClean(listOf(TEMP_EXPLORE_MNT), listOf(loopDev), removeDirs = true)
            null to "No formatted filesystem found in ${slice.schemeLabel} (blank/unformatted images must be formatted on a PC first)."
        }

    fun closeSessionSync(context: Context?, session: ImageMountSession?) {
        if (session == null) return
        BuildThermalHelper.unmountAndDetachClean(
            mountPoints = listOf(session.mountPoint),
            loopDevices = listOf(session.loopDev),
            removeDirs = true
        )
        if (session.modified) {
            UsbGadgetEngine.fixUserStoragePermissions(context, session.imgPath, isDir = false)
            val sz = Shell.cmd("stat -c '%s' ${shellQuote(session.imgPath)} 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            if (sz > 0L) UsbGadgetEngine.syncMediaStoreFileSize(context, session.imgPath, sz)
            IsoInspector.invalidate(session.imgPath)
        }
    }

    suspend fun closeSession(context: Context?, session: ImageMountSession?) = withContext(Dispatchers.IO) {
        closeSessionSync(context, session)
    }

    private fun safeInsidePath(session: ImageMountSession, relPath: String): String {
        val clean = relPath.replace('\\', '/').split('/')
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")
        return if (clean.isEmpty()) session.mountPoint else "${session.mountPoint}/$clean"
    }

    suspend fun listDir(session: ImageMountSession, relDir: String): Pair<List<ImageExplorerEntry>, Pair<Long, Long>> =
        withContext(Dispatchers.IO) {
            val fullDir = safeInsidePath(session, relDir)
            val qDir = shellQuote(fullDir)
            val cleanRelDir = relDir.trim('/')
            val out = Shell.cmd("if [ -d $qDir ]; then find $qDir -mindepth 1 -maxdepth 1 -exec stat -c '%F|%s|%n' {} + 2>/dev/null || true; fi").exec().out
            val items = out.mapNotNull { line ->
                val p = line.split("|", limit = 3)
                if (p.size < 3) return@mapNotNull null
                val typeLow = p[0].lowercase()
                val isDir = typeLow.contains("directory")
                val isReg = typeLow.contains("regular")
                if (!isDir && !isReg) return@mapNotNull null
                val sz = if (isDir) 0L else (p[1].toLongOrNull() ?: 0L)
                val full = p[2]
                val name = full.substringAfterLast('/')
                if (name.isEmpty() || name == "*") return@mapNotNull null
                val childRel = if (cleanRelDir.isEmpty()) name else "$cleanRelDir/$name"
                ImageExplorerEntry(relPath = childRel, name = name, isDirectory = isDir, sizeBytes = sz)
            }.sortedWith(compareBy<ImageExplorerEntry> { !it.isDirectory }.thenBy { it.name.lowercase() })
            val space = VentoyImageBuilder.queryMountSpaceBytes(session.mountPoint)
            items to space
        }

    suspend fun createDir(session: ImageMountSession, relDir: String, rawFolderName: String): ActionResult =
        withContext(Dispatchers.IO) {
            if (session.readOnly) return@withContext ActionResult(false, "Image is mounted read-only.")
            val cleanName = sanitizeFolderName(rawFolderName)
            if (cleanName.isEmpty()) return@withContext ActionResult(false, "Enter a valid folder name.")
            val targetRel = if (relDir.trim('/').isEmpty()) cleanName else "${relDir.trim('/')}/$cleanName"
            val dst = safeInsidePath(session, targetRel)
            val ok = Shell.cmd("mkdir -p ${shellQuote(dst)} 2>&1").exec().isSuccess
            if (ok) {
                session.modified = true
                ActionResult(true, "Created folder $cleanName.")
            } else {
                ActionResult(false, "Could not create folder $cleanName.")
            }
        }

    suspend fun deleteEntry(session: ImageMountSession, relPath: String): ActionResult =
        withContext(Dispatchers.IO) {
            if (session.readOnly) return@withContext ActionResult(false, "Image is mounted read-only.")
            val cleanRel = relPath.trim('/')
            if (cleanRel.isEmpty()) return@withContext ActionResult(false, "Cannot delete root directory.")
            val target = safeInsidePath(session, cleanRel)
            if (target == session.mountPoint) return@withContext ActionResult(false, "Cannot delete root directory.")
            val ok = Shell.cmd("rm -rf ${shellQuote(target)} 2>&1").exec().isSuccess
            if (ok) {
                session.modified = true
                ActionResult(true, "Deleted ${cleanRel.substringAfterLast('/')}.")
            } else {
                ActionResult(false, "Could not delete ${cleanRel.substringAfterLast('/')}.")
            }
        }

    suspend fun importFileIntoImage(session: ImageMountSession, hostFilePath: String, relDir: String): ActionResult =
        withContext(Dispatchers.IO) {
            if (session.readOnly) return@withContext ActionResult(false, "Image is mounted read-only.")
            val resolvedSrc = UsbGadgetEngine.resolveDirectPath(hostFilePath)
            if (isSelfImportPath(resolvedSrc, session.imgPath)) {
                return@withContext ActionResult(false, "Cannot import an image file into itself.")
            }
            val qSrc = shellQuote(resolvedSrc)
            val srcSize = Shell.cmd("stat -c '%s' $qSrc 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: -1L
            if (srcSize < 0L) return@withContext ActionResult(false, "Source file not found.")
            val fileName = resolvedSrc.substringAfterLast('/')
            val targetRel = if (relDir.trim('/').isEmpty()) fileName else "${relDir.trim('/')}/$fileName"
            val dst = safeInsidePath(session, targetRel)
            val qDst = shellQuote(dst)

            val existingDst = Shell.cmd("stat -c '%s' $qDst 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
            val (_, rawFree) = VentoyImageBuilder.queryMountSpaceBytes(session.mountPoint)
            val effFree = if (rawFree >= 0L) rawFree + existingDst else -1L
            if (effFree >= 0L && srcSize > effFree) {
                return@withContext ActionResult(
                    false,
                    "Not enough free space inside image (need ${UsbGadgetEngine.formatBytes(srcSize)}, only ${UsbGadgetEngine.formatBytes(effFree)} free)."
                )
            }
            val imgParentDir = session.imgPath.substringBeforeLast('/')
            val rawHostFree = UsbGadgetEngine.getFreeSpaceBytes(imgParentDir)
            val effHostFree = if (rawHostFree > 0L) rawHostFree + existingDst else -1L
            if (effHostFree in 1L until (srcSize + (64L * 1024L * 1024L))) {
                return@withContext ActionResult(
                    false,
                    "Not enough free phone storage (${UsbGadgetEngine.formatBytes(effHostFree)} free) to grow this sparse image by ${UsbGadgetEngine.formatBytes(srcSize)}."
                )
            }
            val cpOk = Shell.cmd("cp -f $qSrc $qDst 2>&1").exec().isSuccess
            val copiedSz = Shell.cmd("stat -c '%s' $qDst 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: -1L
            if (!cpOk || copiedSz != srcSize) {
                Shell.cmd("rm -f $qDst 2>/dev/null || true").exec()
                return@withContext ActionResult(false, "Failed to copy $fileName into image (wrote ${UsbGadgetEngine.formatBytes(copiedSz.coerceAtLeast(0L))}).")
            }
            session.modified = true
            ActionResult(true, "Copied $fileName (${UsbGadgetEngine.formatBytes(srcSize)}) into image.")
        }

    suspend fun exportFileFromImage(
        context: Context,
        session: ImageMountSession,
        relFilePath: String,
        destHostDir: String
    ): ActionResult = withContext(Dispatchers.IO) {
        val src = safeInsidePath(session, relFilePath)
        val qSrc = shellQuote(src)
        val srcSize = Shell.cmd("stat -c '%s' $qSrc 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: -1L
        if (srcSize < 0L) return@withContext ActionResult(false, "File not found inside image.")
        val safeDir = UsbGadgetEngine.resolveDirectPath(destHostDir).trimEnd('/')
        val rawLeaf = relFilePath.replace('\\', '/').substringAfterLast('/').trim()
        if (rawLeaf.isEmpty() || rawLeaf == "." || rawLeaf == ".." || rawLeaf.any { it.code < 0x20 }) {
            return@withContext ActionResult(false, "Refused to export invalid filename.")
        }
        val fileName = rawLeaf
        val dst = "$safeDir/$fileName"
        if (!ShellSafety.isStrictChildPath(safeDir, dst)) {
            return@withContext ActionResult(false, "Refused to export outside destination folder.")
        }
        val qDst = shellQuote(dst)
        val existingHostDst = Shell.cmd("stat -c '%s' $qDst 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
        val rawFreeHost = UsbGadgetEngine.getFreeSpaceBytes(safeDir)
        val effFreeHost = if (rawFreeHost > 0L) rawFreeHost + existingHostDst else 0L
        if (effFreeHost in 1L until (srcSize + (32L * 1024L * 1024L))) {
            return@withContext ActionResult(false, "Not enough free phone storage (${UsbGadgetEngine.formatBytes(effFreeHost)} free).")
        }
        val cpOk = Shell.cmd("mkdir -p ${shellQuote(safeDir)} && cp -f $qSrc $qDst 2>&1").exec().isSuccess
        val copiedSz = Shell.cmd("stat -c '%s' $qDst 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: -1L
        if (!cpOk || copiedSz != srcSize) {
            Shell.cmd("rm -f $qDst 2>/dev/null || true").exec()
            return@withContext ActionResult(false, "Export failed for $fileName.")
        }
        UsbGadgetEngine.fixUserStoragePermissions(context, safeDir, isDir = true)
        UsbGadgetEngine.fixUserStoragePermissions(context, dst, isDir = false)
        if (copiedSz > 0L) UsbGadgetEngine.syncMediaStoreFileSize(context, dst, copiedSz)
        ActionResult(true, "Exported $fileName to ${UsbGadgetEngine.friendlyFolderName(safeDir)}.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageExplorerSheet(
    imgFile: ImageFile,
    isMountedToPc: Boolean,
    defaultExportDir: String,
    onDismiss: () -> Unit,
    onUnmountFirst: () -> Unit,
    onImageModified: () -> Unit,
    onMessage: (String, Boolean) -> Unit,
    onBusyChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<ImageMountSession?>(null) }
    var openError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var opBusy by remember { mutableStateOf(false) }
    var currentRelDir by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<ImageExplorerEntry>>(emptyList()) }
    var spaceTotal by remember { mutableStateOf(0L) }
    var spaceFree by remember { mutableStateOf(0L) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var showImportPicker by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ImageExplorerEntry?>(null) }

    LaunchedEffect(opBusy) { onBusyChanged(opBusy) }

    LaunchedEffect(imgFile.path, isMountedToPc) {
        if (isMountedToPc) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        openError = null
        val (sess, err) = ImageLoopExplorer.openSession(context, imgFile.path)
        session = sess
        openError = err
        loading = false
    }

    LaunchedEffect(session, currentRelDir, reloadNonce) {
        val s = session ?: return@LaunchedEffect
        loading = true
        val (list, space) = ImageLoopExplorer.listDir(s, currentRelDir)
        entries = list
        spaceTotal = space.first
        spaceFree = space.second
        loading = false
    }

    DisposableEffect(imgFile.path) {
        onDispose {
            val activeSession = session
            session = null
            onBusyChanged(false)
            if (activeSession != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    ImageLoopExplorer.closeSessionSync(context.applicationContext, activeSession)
                }
            }
        }
    }

    fun closeCleanly() {
        if (opBusy) return
        val activeSession = session
        session = null
        scope.launch {
            opBusy = true
            val wasMod = activeSession?.modified == true
            ImageLoopExplorer.closeSession(context, activeSession)
            opBusy = false
            if (wasMod) onImageModified()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { closeCleanly() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PocketColors.surfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(PocketColors.infoBlueBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = PocketColors.infoBlue, modifier = Modifier.size(21.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Image File Explorer", style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary)
                    Text(
                        text = session?.let { "${imgFile.name} · ${it.schemeLabel} (${it.fsLabel})" } ?: imgFile.name,
                        style = PocketColors.monoStyle,
                        color = PocketColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (session != null && !opBusy) {
                    IconButton(onClick = { reloadNonce++ }) {
                        Icon(Icons.Rounded.Refresh, "Refresh", tint = PocketColors.textSecondary, modifier = Modifier.size(19.dp))
                    }
                }
            }

            val currentSess = session
            if (isMountedToPc) {
                Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.warningBg, border = BorderStroke(1.dp, PocketColors.warning)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.warning, modifier = Modifier.size(19.dp))
                            Text("Drive Mounted to PC", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                        }
                        Text("Unmount this .img from your PC before mounting it on-phone to prevent filesystem corruption.", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
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
            } else if (openError != null) {
                Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.dangerBg, border = BorderStroke(1.dp, PocketColors.danger)) {
                    Text(openError.orEmpty(), modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                }
            } else if (currentSess != null) {
                Surface(shape = RoundedCornerShape(10.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "/${currentRelDir.trim('/')}",
                            style = PocketColors.monoStyle,
                            color = PocketColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (spaceTotal > 0L && spaceFree >= 0L) {
                            Text(
                                text = "${UsbGadgetEngine.formatBytes(spaceFree)} free",
                                style = MaterialTheme.typography.labelSmall,
                                color = PocketColors.success
                            )
                        }
                    }
                }

                if (!currentSess.readOnly) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { showImportPicker = true },
                            enabled = !opBusy,
                            modifier = Modifier.weight(1.2f).height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                        ) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Import File", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { newFolderName = ""; showNewFolderDialog = true },
                            enabled = !opBusy,
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            border = BorderStroke(0.5.dp, PocketColors.border)
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, null, tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("New Folder", fontSize = 12.sp, color = PocketColors.textPrimary)
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (loading || opBusy) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = PocketColors.accent)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (currentRelDir.isNotEmpty()) {
                                item("parent_up") {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            currentRelDir = currentRelDir.substringBeforeLast('/', "")
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = PocketColors.surfaceSubtle,
                                        border = BorderStroke(0.5.dp, PocketColors.border)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp))
                                            Text(".. (Parent Folder)", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                        }
                                    }
                                }
                            }
                            if (entries.isEmpty()) {
                                item("empty_dir") {
                                    Text(
                                        "This folder is empty.",
                                        modifier = Modifier.padding(14.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PocketColors.textSecondary
                                    )
                                }
                            }
                            items(entries, key = { it.relPath }) { item ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().clickable(enabled = item.isDirectory) {
                                        currentRelDir = item.relPath
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = PocketColors.surfaceInput,
                                    border = BorderStroke(0.5.dp, PocketColors.border)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (item.isDirectory) Icons.Rounded.Folder else Icons.AutoMirrored.Rounded.InsertDriveFile,
                                            contentDescription = null,
                                            tint = if (item.isDirectory) PocketColors.accentSoft else PocketColors.textSecondary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                if (item.isDirectory) "Folder" else UsbGadgetEngine.formatBytes(item.sizeBytes),
                                                style = PocketColors.monoStyle,
                                                color = PocketColors.textMuted
                                            )
                                        }
                                        if (!item.isDirectory) {
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        opBusy = true
                                                        val res = ImageLoopExplorer.exportFileFromImage(context, currentSess, item.relPath, defaultExportDir)
                                                        opBusy = false
                                                        onMessage(res.userMessage, !res.success)
                                                    }
                                                },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(Icons.Rounded.CloudDownload, "Export to phone", tint = PocketColors.infoBlue, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        if (!currentSess.readOnly) {
                                            IconButton(
                                                onClick = { pendingDelete = item },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(Icons.Rounded.DeleteOutline, "Delete", tint = PocketColors.danger, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PocketColors.accent)
                }
            }

            OutlinedButton(
                onClick = { closeCleanly() },
                enabled = !opBusy,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.75.dp, PocketColors.border)
            ) {
                Text("Save & Close Image", color = PocketColors.textPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    val importSess = session
    if (showImportPicker && importSess != null) {
        RootSingleFilePickerSheet(
            title = "Import File Into .img",
            subtitle = "Choose any file on your phone to copy into /${currentRelDir.trim('/')}",
            allowedExtensions = emptySet(),
            initialDir = defaultExportDir,
            onDismiss = { showImportPicker = false },
            onSelectFile = { chosenPath ->
                showImportPicker = false
                scope.launch {
                    opBusy = true
                    val res = ImageLoopExplorer.importFileIntoImage(importSess, chosenPath, currentRelDir)
                    opBusy = false
                    onMessage(res.userMessage, !res.success)
                    reloadNonce++
                }
            }
        )
    }

    val mkdirSess = session
    if (showNewFolderDialog && mkdirSess != null) {
        val safePreview = ImageLoopExplorer.sanitizeFolderName(newFolderName)
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Create Folder Inside .img", color = PocketColors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it.replace("/", "").replace("\\", "") },
                    label = { Text("Folder name") },
                    placeholder = { Text("e.g., Drivers") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNewFolderDialog = false
                        val folderToMake = safePreview
                        newFolderName = ""
                        scope.launch {
                            opBusy = true
                            val res = ImageLoopExplorer.createDir(mkdirSess, currentRelDir, folderToMake)
                            opBusy = false
                            onMessage(res.userMessage, !res.success)
                            reloadNonce++
                        }
                    },
                    enabled = safePreview.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) { Text("Create", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel", color = PocketColors.textSecondary) }
            }
        )
    }

    pendingDelete?.let { item ->
        val activeSess = session
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Delete Inside Image?", color = PocketColors.textPrimary) },
            text = {
                Text(
                    "Remove ${item.name} from ${imgFile.name}?",
                    style = MaterialTheme.typography.bodySmall,
                    color = PocketColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = null
                        if (activeSess != null) {
                            scope.launch {
                                opBusy = true
                                val res = ImageLoopExplorer.deleteEntry(activeSess, item.relPath)
                                opBusy = false
                                onMessage(res.userMessage, !res.success)
                                reloadNonce++
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.danger, contentColor = Color.White)
                ) { Text("Delete", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = PocketColors.textSecondary) }
            }
        )
    }
}
