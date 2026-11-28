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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class DriverPackCategory(val label: String) {
    INTEL_STORAGE("Intel VMD/RST"),
    AMD_STORAGE("AMD RAID"),
    NETWORK("Wi-Fi / LAN"),
    CUSTOM("Custom")
}

data class DriverPackItem(
    val id: String,
    val name: String,
    val dirPath: String,
    val infCount: Int,
    val fileCount: Int,
    val sizeBytes: Long,
    val category: DriverPackCategory
)

object DriverPackManager {
    const val LIBRARY_FOLDER_NAME = "PocketBoot_Drivers"

    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    internal const val MAX_EXTRACTED_PACK_BYTES = 2L * 1024L * 1024L * 1024L

    internal fun isSafeChildOfLibrary(libDir: String, candidatePath: String): Boolean =
        ShellSafety.isStrictChildPath(libDir, candidatePath)

    fun driverLibraryDir(defaultDir: String): String {
        val base = UsbGadgetEngine.resolveDirectPath(defaultDir).trimEnd('/')
        return "$base/$LIBRARY_FOLDER_NAME"
    }

    fun validateImportPaths(sourceDir: String, destDir: String): String? {
        val src = UsbGadgetEngine.resolveDirectPath(sourceDir).trimEnd('/')
        val dst = UsbGadgetEngine.resolveDirectPath(destDir).trimEnd('/')
        if (src.isEmpty() || dst.isEmpty()) return "Invalid source or destination folder."
        if (src == dst) return null // Already in place
        if (dst.startsWith("$src/")) return "Cannot import a parent folder that contains PocketBoot_Drivers itself (would cause recursive copy)."
        if (src.startsWith("$dst/")) return "Source folder is already inside the destination pack."
        return null
    }

    fun sanitizePackSlug(raw: String): String {
        val clean = raw.trim()
            .replace('\\', '/')
            .substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '.', ' ')
            .take(36)
        return clean.ifBlank { "driver_pack" }
    }

    fun detectCategory(packName: String, infFileNames: List<String>): DriverPackCategory {
        val hay = (listOf(packName) + infFileNames).joinToString(" ").lowercase(Locale.US)
        return when {
            hay.contains("iastor") || hay.contains("vmd") || hay.contains("rst") || hay.contains("intel_storage") ->
                DriverPackCategory.INTEL_STORAGE
            hay.contains("rcraid") || hay.contains("rcbottom") || hay.contains("amd_raid") || hay.contains("storemi") ->
                DriverPackCategory.AMD_STORAGE
            hay.contains("wifi") || hay.contains("wlan") || hay.contains("netw") || hay.contains("rtw") ||
                hay.contains("iwl") || hay.contains("mtkwl") || hay.contains("ath") || hay.contains("rt640") ||
                hay.contains("e1d") || hay.contains("e2f") || hay.contains("lan") || hay.contains("ethernet") ->
                DriverPackCategory.NETWORK
            else -> DriverPackCategory.CUSTOM
        }
    }

    suspend fun listPacks(defaultDir: String): List<DriverPackItem> = withContext(Dispatchers.IO) {
        val libDir = driverLibraryDir(defaultDir)
        val qLib = shellQuote(libDir)
        val dirs = Shell.cmd("if [ -d $qLib ]; then find $qLib -mindepth 1 -maxdepth 1 -type d 2>/dev/null; fi")
            .exec().out.map { it.trim() }.filter { it.isNotBlank() }.sortedBy { it.lowercase(Locale.US) }

        dirs.mapNotNull { dir ->
            val name = dir.substringAfterLast('/').trim()
            if (name.isEmpty() || name.startsWith(".")) return@mapNotNull null
            val qDir = shellQuote(dir)
            val infNames = Shell.cmd("find $qDir -type f -iname '*.inf' 2>/dev/null")
                .exec().out.map { it.trim().substringAfterLast('/') }.filter { it.isNotEmpty() }
            val fileCount = Shell.cmd("find $qDir -type f 2>/dev/null | wc -l")
                .exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
            val sizeBytes = UsbGadgetEngine.queryDirSizeBytes(dir)
            DriverPackItem(
                id = name,
                name = name.replace('_', ' '),
                dirPath = dir,
                infCount = infNames.size,
                fileCount = fileCount,
                sizeBytes = sizeBytes,
                category = detectCategory(name, infNames)
            )
        }
    }

    suspend fun importPackFromFolder(
        context: Context,
        defaultDir: String,
        sourceFolderPath: String,
        packName: String
    ): ActionResult = withContext(Dispatchers.IO) {
        val src = UsbGadgetEngine.resolveDirectPath(sourceFolderPath).trimEnd('/')
        val qSrc = shellQuote(src)
        val isDir = Shell.cmd("[ -d $qSrc ] && echo 1 || echo 0").exec().out.lastOrNull()?.trim() == "1"
        if (!isDir) return@withContext ActionResult(false, "Selected driver folder does not exist.")

        val infCount = Shell.cmd("find $qSrc -type f -iname '*.inf' 2>/dev/null | wc -l")
            .exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
        if (infCount <= 0) {
            return@withContext ActionResult(false, "No .inf driver files found inside selected folder. Extract driver installers first or import a .zip/.7z pack.")
        }

        val srcSize = UsbGadgetEngine.queryDirSizeBytes(src)
        val libDir = driverLibraryDir(defaultDir)
        val freeHost = UsbGadgetEngine.getFreeSpaceBytes(UsbGadgetEngine.resolveDirectPath(defaultDir))
        if (freeHost in 1L until (srcSize + (64L * 1024L * 1024L))) {
            return@withContext ActionResult(false, "Not enough free phone storage (${UsbGadgetEngine.formatBytes(freeHost)} free) to import this driver pack.")
        }

        val slug = sanitizePackSlug(packName.ifBlank { src.substringAfterLast('/') })
        val dst = "$libDir/$slug"
        validateImportPaths(src, dst)?.let { err ->
            return@withContext ActionResult(false, err)
        }
        if (dst == src) {
            return@withContext ActionResult(true, "Driver pack $slug is already in your PocketBoot Drivers library ($infCount .inf file(s)).")
        }
        val qLib = shellQuote(libDir)
        val qDst = shellQuote(dst)
        val copyRes = Shell.cmd(
            """
            (
                set -e
                mkdir -p $qLib
                rm -rf $qDst
                mkdir -p $qDst
                cp -rf $qSrc/. $qDst/
            )
            """.trimIndent()
        ).exec()
        val copiedInfs = if (copyRes.isSuccess) {
            Shell.cmd("find $qDst -type f -iname '*.inf' 2>/dev/null | wc -l").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
        } else 0
        if (!copyRes.isSuccess || copiedInfs != infCount) {
            Shell.cmd("rm -rf $qDst 2>/dev/null || true").exec()
            return@withContext ActionResult(false, "Failed to copy driver pack cleanly ($copiedInfs of $infCount .inf files copied).")
        }
        UsbGadgetEngine.fixUserStoragePermissions(context, libDir, isDir = true)
        UsbGadgetEngine.fixUserStoragePermissions(context, dst, isDir = true)
        ActionResult(true, "Imported driver pack '$slug' ($infCount .inf file(s), ${UsbGadgetEngine.formatBytes(srcSize)}).")
    }

    suspend fun importPackFromArchive(
        context: Context,
        defaultDir: String,
        archivePath: String,
        packName: String
    ): ActionResult = withContext(Dispatchers.IO) {
        val src = UsbGadgetEngine.resolveDirectPath(archivePath)
        val qSrc = shellQuote(src)
        val exists = Shell.cmd("[ -f $qSrc ] && echo 1 || echo 0").exec().out.lastOrNull()?.trim() == "1"
        if (!exists) return@withContext ActionResult(false, "Selected driver archive was not found.")

        val sevenZip = WindowsImageBuilder.findBundledBinary(context, "lib7zzs.so", listOf("7zzs", "7zz", "7z"))
            ?: return@withContext ActionResult(false, "Bundled 7-Zip extractor is unavailable on this build.")

        val libDir = driverLibraryDir(defaultDir)
        val slug = sanitizePackSlug(packName.ifBlank { src.substringAfterLast('/').substringBeforeLast('.') })
        val dst = "$libDir/$slug"
        val stageDir = "/data/local/tmp/pb_drv_extract_${System.currentTimeMillis() % 10000}"
        val qStage = shellQuote(stageDir)
        val qLib = shellQuote(libDir)
        val qDst = shellQuote(dst)
        val q7z = shellQuote(sevenZip)

        return@withContext try {
            val extRes = Shell.cmd(
                """
                (
                    set -e
                    rm -rf $qStage
                    mkdir -p $qStage
                    $q7z x -y -o$qStage $qSrc >/dev/null 2>&1
                    find $qStage -type l -delete 2>/dev/null || true
                )
                """.trimIndent()
            ).exec()
            if (!extRes.isSuccess) {
                return@withContext ActionResult(false, "Could not extract ${src.substringAfterLast('/')} (corrupt or unsupported archive).")
            }
            val infCount = Shell.cmd("find $qStage -type f -iname '*.inf' 2>/dev/null | wc -l")
                .exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
            if (infCount <= 0) {
                return@withContext ActionResult(false, "No .inf driver files found inside ${src.substringAfterLast('/')}.")
            }
            val stageSize = UsbGadgetEngine.queryDirSizeBytes(stageDir)
            if (stageSize > MAX_EXTRACTED_PACK_BYTES) {
                return@withContext ActionResult(false, "Driver archive exceeds the 2 GB safety limit (${UsbGadgetEngine.formatBytes(stageSize)}).")
            }
            val freeHost = UsbGadgetEngine.getFreeSpaceBytes(UsbGadgetEngine.resolveDirectPath(defaultDir))
            if (freeHost in 1L until (stageSize + (64L * 1024L * 1024L))) {
                return@withContext ActionResult(false, "Not enough free phone storage (${UsbGadgetEngine.formatBytes(freeHost)} free) for extracted driver pack (${UsbGadgetEngine.formatBytes(stageSize)}).")
            }
            val moveRes = Shell.cmd(
                """
                (
                    set -e
                    mkdir -p $qLib
                    rm -rf $qDst
                    mkdir -p $qDst
                    cp -rPf $qStage/. $qDst/ 2>/dev/null || cp -rf $qStage/. $qDst/
                )
                """.trimIndent()
            ).exec()
            val copiedInfs = if (moveRes.isSuccess) {
                Shell.cmd("find $qDst -type f -iname '*.inf' 2>/dev/null | wc -l").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 0
            } else 0
            if (!moveRes.isSuccess || copiedInfs != infCount) {
                Shell.cmd("rm -rf $qDst 2>/dev/null || true").exec()
                return@withContext ActionResult(false, "Failed to save extracted driver pack into PocketBoot Drivers.")
            }
            UsbGadgetEngine.fixUserStoragePermissions(context, libDir, isDir = true)
            UsbGadgetEngine.fixUserStoragePermissions(context, dst, isDir = true)
            ActionResult(true, "Imported driver pack '$slug' ($infCount .inf file(s), ${UsbGadgetEngine.formatBytes(stageSize)}).")
        } finally {
            Shell.cmd("rm -rf $qStage 2>/dev/null || true").exec()
        }
    }

    suspend fun deletePack(defaultDir: String, packDirPath: String): ActionResult = withContext(Dispatchers.IO) {
        val libDir = driverLibraryDir(defaultDir).trimEnd('/')
        val resolved = UsbGadgetEngine.resolveDirectPath(packDirPath).trimEnd('/')
        if (!isSafeChildOfLibrary(libDir, resolved)) {
            return@withContext ActionResult(false, "Refused to delete path outside PocketBoot Drivers library.")
        }
        val ok = Shell.cmd("rm -rf ${shellQuote(resolved)} 2>&1").exec().isSuccess
        if (ok) ActionResult(true, "Deleted driver pack ${resolved.substringAfterLast('/')}.")
        else ActionResult(false, "Could not delete driver pack.")
    }
}

@Composable
fun DriverPackMultiSelectorSection(
    packs: List<DriverPackItem>,
    selectedPackDirs: Set<String>,
    onTogglePack: (String) -> Unit,
    onOpenManager: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = PocketColors.surfaceInput,
        border = BorderStroke(0.5.dp, if (selectedPackDirs.isNotEmpty()) PocketColors.accent else PocketColors.border)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Icon(Icons.Rounded.Memory, null, tint = PocketColors.infoBlue, modifier = Modifier.size(17.dp))
                    Text("Saved Driver Packs", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                }
                Text(
                    text = if (packs.isEmpty()) "Import" else "Manage (${packs.size})",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PocketColors.accentSoft,
                    modifier = Modifier.clickable { onOpenManager() }.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            if (packs.isEmpty()) {
                Text(
                    "Save Intel VMD/RST, AMD RAID, or Wi-Fi/LAN driver packs once and reuse them across Windows & Multi-Boot builds.",
                    fontSize = 11.sp,
                    color = PocketColors.textSecondary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    packs.forEach { pack ->
                        val checked = pack.dirPath in selectedPackDirs
                        Surface(
                            modifier = Modifier.fillMaxWidth().bounceClick { onTogglePack(pack.dirPath) },
                            shape = RoundedCornerShape(9.dp),
                            color = if (checked) PocketColors.accentBg else PocketColors.surfaceCard,
                            border = BorderStroke(if (checked) 1.25.dp else 0.5.dp, if (checked) PocketColors.accent else PocketColors.border)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = if (checked) PocketColors.accent else PocketColors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pack.name, style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${pack.category.label} · ${pack.infCount} .inf · ${UsbGadgetEngine.formatBytes(pack.sizeBytes)}",
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
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverPackManagerSheet(
    defaultDir: String,
    onDismiss: () -> Unit,
    onPacksChanged: () -> Unit = {},
    onMessage: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var packs by remember { mutableStateOf<List<DriverPackItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var opBusy by remember { mutableStateOf(false) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showArchivePicker by remember { mutableStateOf(false) }
    var pendingNameSource by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // (path, isArchive)
    var packNameInput by remember { mutableStateOf("") }
    var pendingDeletePack by remember { mutableStateOf<DriverPackItem?>(null) }

    LaunchedEffect(defaultDir, reloadNonce) {
        loading = true
        packs = DriverPackManager.listPacks(defaultDir)
        loading = false
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(PocketColors.infoBlueBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Memory, null, tint = PocketColors.infoBlue, modifier = Modifier.size(21.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Driver Pack Library", style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary)
                    Text(
                        DriverPackManager.driverLibraryDir(defaultDir).replace("/data/media/0", "/sdcard"),
                        style = PocketColors.monoStyle,
                        color = PocketColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!opBusy) {
                    IconButton(onClick = { reloadNonce++ }) {
                        Icon(Icons.Rounded.Refresh, "Refresh", tint = PocketColors.textSecondary, modifier = Modifier.size(19.dp))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showFolderPicker = true },
                    enabled = !opBusy,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Import Folder", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { showArchivePicker = true },
                    enabled = !opBusy,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Icon(Icons.Rounded.Add, null, tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Import .zip/.7z", fontSize = 12.sp, color = PocketColors.textPrimary)
                }
            }

            if (loading || opBusy) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp), color = PocketColors.accent)
                        Text(if (opBusy) "Importing driver pack..." else "Scanning driver packs...", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                    }
                }
            } else if (packs.isEmpty()) {
                Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                    Text(
                        "No saved driver packs yet. Import an extracted folder or .zip/.7z archive containing .inf/.sys/.cat files (e.g., Intel VMD/RST, AMD RAID, or Realtek/Intel Wi-Fi).",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    packs.forEach { pack ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PocketColors.surfaceInput,
                            border = BorderStroke(0.5.dp, if (pack.infCount == 0) PocketColors.warning else PocketColors.border)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Icon(
                                    imageVector = if (pack.infCount == 0) Icons.Rounded.WarningAmber else Icons.Rounded.Memory,
                                    contentDescription = null,
                                    tint = if (pack.infCount == 0) PocketColors.warning else PocketColors.infoBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pack.name, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${pack.category.label} · ${pack.infCount} .inf (${pack.fileCount} files) · ${UsbGadgetEngine.formatBytes(pack.sizeBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (pack.infCount == 0) PocketColors.warning else PocketColors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { pendingDeletePack = pack },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, "Delete pack", tint = PocketColors.danger, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showFolderPicker) {
        RootFolderPickerSheet(
            title = "Select Driver Folder",
            subtitle = "Choose a folder containing .inf/.sys/.cat driver files",
            initialDir = defaultDir,
            onDismiss = { showFolderPicker = false },
            onSelectFolder = { chosen ->
                showFolderPicker = false
                packNameInput = DriverPackManager.sanitizePackSlug(chosen.substringAfterLast('/'))
                pendingNameSource = chosen to false
            }
        )
    }

    if (showArchivePicker) {
        RootSingleFilePickerSheet(
            title = "Select Driver Archive",
            subtitle = "Choose a .zip or .7z driver archive to extract & validate",
            allowedExtensions = setOf("zip", "7z", "tar", "gz"),
            initialDir = defaultDir,
            onDismiss = { showArchivePicker = false },
            onSelectFile = { chosen ->
                showArchivePicker = false
                packNameInput = DriverPackManager.sanitizePackSlug(chosen.substringAfterLast('/').substringBeforeLast('.'))
                pendingNameSource = chosen to true
            }
        )
    }

    pendingNameSource?.let { (srcPath, isArchive) ->
        val safeName = DriverPackManager.sanitizePackSlug(packNameInput)
        AlertDialog(
            onDismissRequest = { pendingNameSource = null },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Name Driver Pack", color = PocketColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "PocketBoot will validate .inf files and store this pack inside PocketBoot_Drivers/$safeName.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )
                    OutlinedTextField(
                        value = packNameInput,
                        onValueChange = { packNameInput = it.take(36) },
                        label = { Text("Pack name") },
                        placeholder = { Text("e.g., Intel_VMD_Gen13") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalName = safeName
                        pendingNameSource = null
                        scope.launch {
                            opBusy = true
                            val res = if (isArchive) {
                                DriverPackManager.importPackFromArchive(context, defaultDir, srcPath, finalName)
                            } else {
                                DriverPackManager.importPackFromFolder(context, defaultDir, srcPath, finalName)
                            }
                            opBusy = false
                            onMessage(res.userMessage, !res.success)
                            if (res.success) onPacksChanged()
                            reloadNonce++
                        }
                    },
                    enabled = safeName.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) { Text("Import", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingNameSource = null }) { Text("Cancel", color = PocketColors.textSecondary) }
            }
        )
    }

    pendingDeletePack?.let { pack ->
        AlertDialog(
            onDismissRequest = { pendingDeletePack = null },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Delete Driver Pack?", color = PocketColors.textPrimary) },
            text = {
                Text(
                    "Remove '${pack.name}' (${UsbGadgetEngine.formatBytes(pack.sizeBytes)}) from your PocketBoot Drivers library?",
                    style = MaterialTheme.typography.bodySmall,
                    color = PocketColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pack
                        pendingDeletePack = null
                        scope.launch {
                            opBusy = true
                            val res = DriverPackManager.deletePack(defaultDir, target.dirPath)
                            opBusy = false
                            onMessage(res.userMessage, !res.success)
                            if (res.success) onPacksChanged()
                            reloadNonce++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.danger, contentColor = Color.White)
                ) { Text("Delete", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePack = null }) { Text("Cancel", color = PocketColors.textSecondary) }
            }
        )
    }
}
