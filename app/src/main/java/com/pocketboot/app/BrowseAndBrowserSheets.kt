package com.pocketboot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Code
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootFileBrowserSheet(
    initialDir: String,
    defaultDir: String,
    addedPaths: Set<String> = emptySet(),
    mountedPaths: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onOpenSystemPicker: () -> Unit,
    onSelectFiles: (List<String>) -> Unit,
    onFileRenamed: (oldPath: String, newPath: String) -> Unit = { _, _ -> },
    onFileDeleted: (deletedPath: String) -> Unit = {},
    onMessage: (String, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentDir by remember {
        mutableStateOf(
            if (initialDir.startsWith("/data/media/0")) initialDir else "/data/media/0"
        )
    }
    var entries by remember { mutableStateOf<List<BrowserEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlySupported by remember { mutableStateOf(true) }
    var multiSelectMode by remember { mutableStateOf(false) }
    val selectedPaths = remember { mutableStateListOf<String>() }

    var infoTarget by remember { mutableStateOf<BrowserEntry?>(null) }
    var renameTarget by remember { mutableStateOf<BrowserEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<BrowserEntry?>(null) }
    var fileOpBusy by remember { mutableStateOf(false) }

    val normalizedAdded = remember(addedPaths) {
        addedPaths.map { UsbGadgetEngine.resolveDirectPath(it) }.toSet()
    }

    LaunchedEffect(currentDir, reloadNonce) {
        loading = true
        entries = UsbGadgetEngine.listDirectory(currentDir)
        loading = false
    }

    val visibleEntries = remember(entries, searchQuery, showOnlySupported) {
        val q = searchQuery.trim().lowercase()
        entries.filter { item ->
            val isSupported = item.isDirectory ||
                IsoInspector.isSupportedDriveFileName(item.name)
            val passesType = if (showOnlySupported) isSupported else true
            val passesSearch = q.isEmpty() || item.name.lowercase().contains(q)
            passesType && passesSearch
        }
    }

    fun toggleSelect(path: String) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path)
        } else {
            selectedPaths.add(path)
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!fileOpBusy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PocketColors.surfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header + System Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val canGoUp = currentDir != "/data/media/0" && currentDir != "/"
                if (canGoUp) {
                    IconButton(
                        onClick = {
                            val parent = currentDir.substringBeforeLast('/', "/data/media/0").ifBlank { "/data/media/0" }
                            currentDir = parent
                            searchQuery = ""
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Go up", tint = PocketColors.textPrimary)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = UsbGadgetEngine.friendlyFolderName(currentDir),
                        style = MaterialTheme.typography.titleMedium,
                        color = PocketColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentDir.replaceFirst("/data/media/0", "Internal Storage"),
                        style = PocketColors.monoStyle,
                        color = PocketColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenSystemPicker()
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, tint = PocketColors.accentSoft, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("System Picker", fontSize = 11.5.sp, color = PocketColors.textPrimary)
                }
            }

            // Quick Folder Pills + Filter + Multi-Select Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (multiSelectMode) PocketColors.accentBg else PocketColors.surfaceInput,
                    border = BorderStroke(1.dp, if (multiSelectMode) PocketColors.accent else PocketColors.border),
                    modifier = Modifier.bounceClick {
                        multiSelectMode = !multiSelectMode
                        if (!multiSelectMode) selectedPaths.clear()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SelectAll,
                            null,
                            tint = if (multiSelectMode) PocketColors.accentSoft else PocketColors.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (multiSelectMode) "Selecting (${selectedPaths.size})" else "Multi-Select",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (multiSelectMode) PocketColors.accentSoft else PocketColors.textSecondary
                        )
                    }
                }

                val shortcuts = buildList {
                    add("Internal" to "/data/media/0")
                    add("Downloads" to "/data/media/0/Download")
                    if (defaultDir != "/data/media/0/Download" && defaultDir != "/data/media/0") {
                        add(UsbGadgetEngine.friendlyFolderName(defaultDir) to defaultDir)
                    }
                }
                shortcuts.forEach { (label, path) ->
                    val selected = currentDir == path
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (selected) PocketColors.accentBg else PocketColors.surfaceInput,
                        border = BorderStroke(1.dp, if (selected) PocketColors.accent else PocketColors.border),
                        modifier = Modifier.clickable {
                            currentDir = path
                            searchQuery = ""
                        }
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) PocketColors.accentSoft else PocketColors.textSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (showOnlySupported) PocketColors.successBg else PocketColors.surfaceInput,
                    border = BorderStroke(1.dp, if (showOnlySupported) PocketColors.success else PocketColors.border),
                    modifier = Modifier.clickable { showOnlySupported = !showOnlySupported }
                ) {
                    Text(
                        text = if (showOnlySupported) "ISO / IMG only" else "All files",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showOnlySupported) PocketColors.success else PocketColors.textSecondary
                    )
                }
            }

            // Search inside current folder
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter this folder...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = PocketColors.textSecondary, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PocketColors.accent,
                    unfocusedBorderColor = PocketColors.border,
                    focusedContainerColor = PocketColors.surfaceInput,
                    unfocusedContainerColor = PocketColors.surfaceInput
                )
            )

            PocketHairlineDivider()

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PocketColors.accent)
                }
            } else if (visibleEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (entries.isEmpty()) "This folder is empty" else "No .iso, .img, or compressed images here",
                            style = MaterialTheme.typography.titleMedium,
                            color = PocketColors.textPrimary
                        )
                        Text(
                            text = "Open another folder or use System Picker above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(visibleEntries, key = { it.path }) { item ->
                        val isIso = !item.isDirectory && item.name.endsWith(".iso", true)
                        val isImg = !item.isDirectory && item.name.endsWith(".img", true)
                        val isCompressed = !item.isDirectory && IsoInspector.isCompressedImageName(item.name)
                        val isImageFile = isIso || isImg || isCompressed
                        val isSelectable = item.isDirectory || isImageFile
                        val isAlreadyAdded = isImageFile && normalizedAdded.contains(UsbGadgetEngine.resolveDirectPath(item.path))
                        val isChecked = selectedPaths.contains(item.path)
                        val guessedOs = if (isIso) IsoInspector.quickGuessByName(item.name) else IsoOsType.DISK_IMAGE
                        val isWindows = guessedOs == IsoOsType.WINDOWS
                        val isLinux = guessedOs == IsoOsType.LINUX
                        var menuOpen by remember(item.path) { mutableStateOf(false) }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (isSelectable) 1f else 0.42f)
                                .then(
                                    if (isSelectable) {
                                        Modifier.clickable {
                                            when {
                                                item.isDirectory -> {
                                                    currentDir = item.path
                                                    searchQuery = ""
                                                }
                                                multiSelectMode && !isAlreadyAdded -> toggleSelect(item.path)
                                                isAlreadyAdded -> infoTarget = item
                                                else -> onSelectFiles(listOf(item.path))
                                            }
                                        }
                                    } else Modifier
                                ),
                            shape = RoundedCornerShape(13.dp),
                            color = when {
                                isChecked -> PocketColors.accentBg
                                isAlreadyAdded -> PocketColors.surfaceInput
                                isImageFile -> PocketColors.surfaceSubtle
                                else -> PocketColors.surfaceInput
                            },
                            border = BorderStroke(
                                if (isChecked) 1.5.dp else 0.75.dp,
                                when {
                                    isChecked -> PocketColors.accent
                                    isAlreadyAdded -> PocketColors.success.copy(alpha = 0.55f)
                                    isImageFile -> PocketColors.accent.copy(alpha = 0.45f)
                                    else -> PocketColors.border
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (multiSelectMode && isImageFile && !isAlreadyAdded) {
                                    Icon(
                                        imageVector = if (isChecked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                        contentDescription = "Select",
                                        tint = if (isChecked) PocketColors.accent else PocketColors.textSecondary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    item.isDirectory -> PocketColors.warningBg
                                                    isAlreadyAdded -> PocketColors.successBg
                                                    isWindows -> PocketColors.infoBlueBg
                                                    isLinux -> PocketColors.successBg
                                                    isCompressed -> PocketColors.infoBlueBg
                                                    isImageFile -> PocketColors.accentBg
                                                    else -> PocketColors.surfaceCard
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                item.isDirectory -> Icons.Rounded.Folder
                                                isWindows -> Icons.Rounded.Window
                                                isLinux -> Icons.Rounded.Terminal
                                                isIso -> Icons.Rounded.Album
                                                isImg -> Icons.Rounded.Storage
                                                isCompressed -> Icons.Rounded.FolderOpen
                                                else -> Icons.AutoMirrored.Rounded.InsertDriveFile
                                            },
                                            contentDescription = null,
                                            tint = when {
                                                item.isDirectory -> PocketColors.warning
                                                isAlreadyAdded -> PocketColors.success
                                                isWindows -> PocketColors.infoBlue
                                                isLinux -> PocketColors.success
                                                isCompressed -> PocketColors.infoBlue
                                                isImageFile -> PocketColors.accentSoft
                                                else -> PocketColors.textMuted
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (isSelectable) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelectable) PocketColors.textPrimary else PocketColors.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (isWindows || isLinux || isCompressed) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when {
                                                    isWindows || isCompressed -> PocketColors.infoBlueBg
                                                    else -> PocketColors.successBg
                                                }
                                            ) {
                                                Text(
                                                    text = when {
                                                        isWindows -> "Windows"
                                                        isLinux -> "Linux"
                                                        else -> "Compressed"
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = when {
                                                        isWindows || isCompressed -> PocketColors.infoBlue
                                                        else -> PocketColors.success
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = when {
                                            item.isDirectory -> "Folder"
                                            isImageFile -> UsbGadgetEngine.formatBytes(item.sizeBytes)
                                            else -> "${UsbGadgetEngine.formatBytes(item.sizeBytes)} · Not supported"
                                        },
                                        style = PocketColors.monoStyle,
                                        color = when {
                                            isImageFile -> PocketColors.success
                                            else -> PocketColors.textMuted
                                        }
                                    )
                                }

                                when {
                                    item.isDirectory -> {
                                        Icon(
                                            Icons.Rounded.ChevronRight,
                                            null,
                                            tint = PocketColors.textSecondary,
                                            modifier = Modifier.padding(end = 6.dp).size(18.dp)
                                        )
                                    }
                                    isImageFile -> {
                                        if (isAlreadyAdded) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = PocketColors.successBg,
                                                border = BorderStroke(0.5.dp, PocketColors.success)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Icon(Icons.Rounded.CheckCircle, null, tint = PocketColors.success, modifier = Modifier.size(13.dp))
                                                    Text("Added", style = MaterialTheme.typography.labelSmall, color = PocketColors.success)
                                                }
                                            }
                                        } else if (!multiSelectMode) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = PocketColors.accent,
                                                modifier = Modifier.bounceClick { onSelectFiles(listOf(item.path)) }
                                            ) {
                                                Text(
                                                    text = "Add",
                                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PocketColors.onAccent
                                                )
                                            }
                                        }
                                        Box {
                                            IconButton(
                                                onClick = { menuOpen = true },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.MoreVert,
                                                    "File options",
                                                    tint = PocketColors.textSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = menuOpen,
                                                onDismissRequest = { menuOpen = false },
                                                modifier = Modifier.background(PocketColors.surfaceCard)
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("File Info & Checksum", color = PocketColors.textPrimary) },
                                                    leadingIcon = { Icon(Icons.Rounded.Info, null, tint = PocketColors.infoBlue, modifier = Modifier.size(17.dp)) },
                                                    onClick = {
                                                        menuOpen = false
                                                        infoTarget = item
                                                    }
                                                )
                                                if (!isAlreadyAdded) {
                                                    DropdownMenuItem(
                                                        text = { Text(if (isChecked) "Deselect" else "Select for Multi-Add", color = PocketColors.textPrimary) },
                                                        leadingIcon = { Icon(Icons.Rounded.SelectAll, null, tint = PocketColors.accentSoft, modifier = Modifier.size(17.dp)) },
                                                        onClick = {
                                                            menuOpen = false
                                                            multiSelectMode = true
                                                            toggleSelect(item.path)
                                                        }
                                                    )
                                                }
                                                DropdownMenuItem(
                                                    text = { Text("Rename", color = PocketColors.textPrimary) },
                                                    leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null, tint = PocketColors.accentSoft, modifier = Modifier.size(17.dp)) },
                                                    onClick = {
                                                        menuOpen = false
                                                        renameTarget = item
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Delete Permanently", color = PocketColors.danger) },
                                                    leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = PocketColors.danger, modifier = Modifier.size(17.dp)) },
                                                    onClick = {
                                                        menuOpen = false
                                                        deleteTarget = item
                                                    }
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

            // Animated Multi-Select Action Bar
            AnimatedVisibility(
                visible = multiSelectMode && selectedPaths.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { selectedPaths.clear() },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Text("Clear", color = PocketColors.textSecondary, fontSize = 12.5.sp)
                    }
                    Button(
                        onClick = { onSelectFiles(selectedPaths.toList()) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add ${selectedPaths.size} Selected", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap ⋮ for File Info, Rename, or Delete",
                    style = MaterialTheme.typography.bodySmall,
                    color = PocketColors.textMuted
                )
                TextButton(onClick = onDismiss) {
                    Text("Close", color = PocketColors.textSecondary)
                }
            }
        }
    }

    // File Information Dialog right inside Root Browser
    infoTarget?.let { item ->
        val isAdded = normalizedAdded.contains(UsbGadgetEngine.resolveDirectPath(item.path))
        var detectedOs by remember(item.path) { mutableStateOf<IsoOsType?>(null) }
        var sha256 by remember(item.path) { mutableStateOf<String?>(null) }
        var hashing by remember(item.path) { mutableStateOf(false) }

        LaunchedEffect(item.path) {
            detectedOs = IsoInspector.inspect(context, item.path, item.sizeBytes)
        }

        val modText = remember(item.modifiedEpochSec) {
            if (item.modifiedEpochSec > 0L) {
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(item.modifiedEpochSec * 1000L))
            } else "Unknown"
        }

        AlertDialog(
            onDismissRequest = { infoTarget = null },
            containerColor = PocketColors.surfaceCard,
            title = {
                Text(item.name, style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
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
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            BrowserInfoRow("Status", if (isAdded) "Added to My Drives" else "Not in My Drives")
                            BrowserInfoRow("Detected Type", detectedOs?.displayType ?: "Inspecting...")
                            BrowserInfoRow("Size", "${UsbGadgetEngine.formatBytes(item.sizeBytes)} (${item.sizeBytes} B)")
                            BrowserInfoRow("Modified", modText)
                            BrowserInfoRow("Path", item.path)
                        }
                    }

                    val resolvedSha = sha256
                    if (resolvedSha != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PocketColors.surfaceInput,
                            border = BorderStroke(0.5.dp, PocketColors.border)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SHA-256", style = MaterialTheme.typography.labelSmall, color = PocketColors.textMuted)
                                    Text(resolvedSha, style = PocketColors.monoStyle, color = PocketColors.textPrimary)
                                }
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(ClipboardManager::class.java)
                                        cm?.setPrimaryClip(ClipData.newPlainText("SHA-256", resolvedSha))
                                        Toast.makeText(context, "SHA-256 copied.", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, "Copy", tint = PocketColors.accentSoft, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    hashing = true
                                    sha256 = UsbGadgetEngine.computeSha256(item.path)
                                    hashing = false
                                }
                            },
                            enabled = !hashing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(0.5.dp, PocketColors.border)
                        ) {
                            if (hashing) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = PocketColors.accent)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Calculating SHA-256...", fontSize = 12.sp, color = PocketColors.textPrimary)
                            } else {
                                Icon(Icons.Rounded.Fingerprint, null, tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Calculate SHA-256", fontSize = 12.sp, color = PocketColors.textPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (!isAdded) {
                    Button(
                        onClick = {
                            infoTarget = null
                            onSelectFiles(listOf(item.path))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                    ) {
                        Text("Add to Drives", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    TextButton(onClick = { infoTarget = null }) {
                        Text("Done", color = PocketColors.accentSoft)
                    }
                }
            },
            dismissButton = {
                if (!isAdded) {
                    TextButton(onClick = { infoTarget = null }) {
                        Text("Close", color = PocketColors.textSecondary)
                    }
                }
            }
        )
    }

    // Rename Dialog inside Root Browser
    renameTarget?.let { item ->
        val isMounted = mountedPaths.contains(item.path)
        val ext = when {
            item.name.endsWith(".img.xz", true) -> ".img.xz"
            item.name.endsWith(".img.gz", true) -> ".img.gz"
            item.name.endsWith(".img.zst", true) -> ".img.zst"
            item.name.endsWith(".iso.xz", true) -> ".iso.xz"
            item.name.endsWith(".iso.gz", true) -> ".iso.gz"
            item.name.endsWith(".iso.zst", true) -> ".iso.zst"
            item.name.endsWith(".iso", true) -> ".iso"
            item.name.endsWith(".img", true) -> ".img"
            else -> ""
        }
        val base = if (ext.isNotEmpty()) item.name.dropLast(ext.length) else item.name
        var newName by remember(item.path) { mutableStateOf(base) }

        AlertDialog(
            onDismissRequest = { if (!fileOpBusy) renameTarget = null },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Rename File", color = PocketColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isMounted) {
                        Text("Unmount this drive before renaming it.", style = MaterialTheme.typography.bodySmall, color = PocketColors.warning)
                    }
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New name") },
                        suffix = if (ext.isNotEmpty()) {
                            { Text(ext, style = MaterialTheme.typography.labelMedium, color = PocketColors.accentSoft) }
                        } else null,
                        singleLine = true,
                        enabled = !isMounted && !fileOpBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        renameTarget = null
                        scope.launch {
                            fileOpBusy = true
                            val res = UsbGadgetEngine.renameImageFile(item.path, newName, isMounted, context)
                            if (res.success && res.createdPath != null) {
                                UsbGadgetEngine.fixUserStoragePermissions(context, res.createdPath, isDir = false)
                                selectedPaths.remove(item.path)
                                onFileRenamed(item.path, res.createdPath)
                                reloadNonce++
                            }
                            fileOpBusy = false
                            onMessage(res.userMessage, !res.success)
                        }
                    },
                    enabled = !isMounted && !fileOpBusy && newName.trim().isNotEmpty() && !newName.contains('/'),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Text("Rename", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = PocketColors.textSecondary)
                }
            }
        )
    }

    // Delete Confirmation Dialog inside Root Browser
    deleteTarget?.let { item ->
        val isMounted = mountedPaths.contains(item.path)
        AlertDialog(
            onDismissRequest = { if (!fileOpBusy) deleteTarget = null },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Delete Permanently?", color = PocketColors.textPrimary) },
            text = {
                Text(
                    text = "Delete ${item.name} (${UsbGadgetEngine.formatBytes(item.sizeBytes)}) from your phone?${if (isMounted) "\n\nThis drive is currently mounted and will be unmounted first." else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PocketColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            fileOpBusy = true
                            val delLun = if (isMounted) UsbGadgetEngine.getStatus().mountedLunFor(item.path) else null
                            val res = UsbGadgetEngine.deleteImageFile(item.path, delLun, context)
                            if (delLun != null && !UsbGadgetEngine.getStatus().isAnyMounted) {
                                UsbKeepAliveService.stop(context)
                            }
                            if (res.success) {
                                selectedPaths.remove(item.path)
                                onFileDeleted(item.path)
                                reloadNonce++
                            }
                            fileOpBusy = false
                            onMessage(res.userMessage, !res.success)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.danger, contentColor = Color.White)
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = PocketColors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun BrowserInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = PocketColors.textMuted)
        Text(value, style = PocketColors.monoStyle, color = PocketColors.textPrimary)
    }
}


private fun resolveTreeUriToPath(uri: Uri): String? = runCatching {
    val parts = DocumentsContract.getTreeDocumentId(uri).split(":", limit = 2)
    if (parts.size == 2) {
        val vol = parts[0]; val rel = parts[1].trim('/')
        if (vol.equals("primary", true)) { if (rel.isEmpty()) "/data/media/0" else "/data/media/0/$rel" }
        else { if (rel.isEmpty()) "/storage/$vol" else "/storage/$vol/$rel" }
    } else null
}.getOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootFolderPickerSheet(
    title: String = "Select Folder",
    subtitle: String = "Browse storage and tap Select This Folder",
    initialDir: String = "/data/media/0",
    onDismiss: () -> Unit,
    onSelectFolder: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val startPath = remember(initialDir) { val r = UsbGadgetEngine.resolveDirectPath(initialDir).trimEnd('/'); if (r.startsWith("/data/media/0") || r.startsWith("/storage/")) r else "/data/media/0" }
    var currentDir by remember(startPath) { mutableStateOf(startPath) }
    var entries by remember { mutableStateOf<List<BrowserEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val systemTreeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val resolved = resolveTreeUriToPath(uri)
            if (!resolved.isNullOrBlank()) onSelectFolder(UsbGadgetEngine.resolveDirectPath(resolved))
            else Toast.makeText(context, "Could not resolve folder path.", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(currentDir, reloadNonce) { loading = true; entries = UsbGadgetEngine.listDirectory(currentDir); loading = false }
    val folders = remember(entries) { entries.filter { it.isDirectory }.sortedBy { it.name.lowercase() } }
    val filesCount = remember(entries) { entries.count { !it.isDirectory } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = PocketColors.surfaceCard) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.84f).navigationBarsPadding().imePadding().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(PocketColors.accentBg), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.FolderOpen, null, tint = PocketColors.accentSoft, modifier = Modifier.size(20.dp)) }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { showNewFolderDialog = true }) { Icon(Icons.Rounded.CreateNewFolder, "New Folder", tint = PocketColors.accentSoft, modifier = Modifier.size(20.dp)) }
                TextButton(onClick = { runCatching { systemTreeLauncher.launch(null) } }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) { Text("System", fontSize = 11.5.sp, color = PocketColors.accentSoft) }
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Internal" to "/data/media/0", "Download" to "/data/media/0/Download", "Drivers" to "/data/media/0/Drivers", "PocketBoot" to "/data/media/0/PocketBoot").forEach { (lbl, path) ->
                    val active = currentDir.trimEnd('/') == path
                    Surface(modifier = Modifier.bounceClick { currentDir = path }, shape = RoundedCornerShape(8.dp), color = if (active) PocketColors.accentBg else PocketColors.surfaceInput, border = BorderStroke(0.75.dp, if (active) PocketColors.accent else PocketColors.border)) {
                        Text(lbl, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = if (active) PocketColors.accentSoft else PocketColors.textSecondary)
                    }
                }
            }
            Surface(shape = RoundedCornerShape(10.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(currentDir.replace("/data/media/0", "/sdcard"), style = PocketColors.monoStyle, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${folders.size} folders · $filesCount files", style = MaterialTheme.typography.labelSmall, color = PocketColors.textMuted)
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (loading) Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp, color = PocketColors.accent) }
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val canGoUp = currentDir.trimEnd('/') != "/data/media/0" && currentDir.trimEnd('/') != "/storage" && currentDir.count { it == '/' } > 1
                    if (canGoUp) {
                        item("parent_dir") {
                            Surface(modifier = Modifier.fillMaxWidth().clickable { val p = currentDir.trimEnd('/').substringBeforeLast('/').ifBlank { "/data/media/0" }; currentDir = if (p == "/data/media" || p == "/data") "/data/media/0" else p }, shape = RoundedCornerShape(10.dp), color = PocketColors.surfaceSubtle, border = BorderStroke(0.5.dp, PocketColors.border)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp)); Text(".. (Parent Folder)", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                }
                            }
                        }
                    }
                    if (folders.isEmpty()) {
                        item("empty_subfolders") {
                            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                                Text(if (filesCount > 0) "$filesCount file(s) inside this folder. Tap \"Select This Folder\" below." else "No subfolders here. Tap \"Select This Folder\" or create a new folder.", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                            }
                        }
                    }
                    items(folders, key = { it.path }) { folder ->
                        Surface(modifier = Modifier.fillMaxWidth().clickable { currentDir = folder.path }, shape = RoundedCornerShape(10.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Rounded.Folder, null, tint = PocketColors.accentSoft, modifier = Modifier.size(20.dp))
                                Text(folder.name, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Icon(Icons.Rounded.ChevronRight, null, tint = PocketColors.textMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(0.8f).height(48.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(0.75.dp, PocketColors.border)) { Text("Cancel", color = PocketColors.textSecondary) }
                Button(onClick = { onSelectFolder(UsbGadgetEngine.resolveDirectPath(currentDir)) }, modifier = Modifier.weight(1.4f).height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)) {
                    Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("Select This Folder", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (showNewFolderDialog) {
        val safeFolderPreview = newFolderName
            .replace('/', ' ').replace('\\', ' ').replace("..", "")
            .replace(Regex("[\\x00-\\x1F:*?\"<>|]"), "")
            .trim().trim('.')
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false }, containerColor = PocketColors.surfaceCard,
            title = { Text("Create New Folder", color = PocketColors.textPrimary) },
            text = { OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it.replace("/", "").replace("\\", "") }, label = { Text("Folder name") }, placeholder = { Text("e.g., Drivers") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { Button(onClick = { if (safeFolderPreview.isNotEmpty()) { val t = "${currentDir.trimEnd('/')}/$safeFolderPreview"; showNewFolderDialog = false; newFolderName = ""; scope.launch { UsbGadgetEngine.ensureDirectoryExists(t, context); currentDir = t; reloadNonce++ } } }, enabled = safeFolderPreview.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)) { Text("Create", fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel", color = PocketColors.textSecondary) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootSingleFilePickerSheet(
    title: String = "Select Custom Script",
    subtitle: String = "Choose a .cmd, .bat, or .ps1 file to run after Windows setup",
    allowedExtensions: Set<String> = setOf("cmd", "bat", "ps1"),
    initialDir: String = "/data/media/0/Download",
    onDismiss: () -> Unit,
    onSelectFile: (String) -> Unit
) {
    val startPath = remember(initialDir) { val r = UsbGadgetEngine.resolveDirectPath(initialDir).trimEnd('/'); if (r.startsWith("/data/media/0") || r.startsWith("/storage/")) r else "/data/media/0" }
    var currentDir by remember(startPath) { mutableStateOf(startPath) }
    var entries by remember { mutableStateOf<List<BrowserEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(currentDir) { loading = true; entries = UsbGadgetEngine.listDirectory(currentDir); loading = false }
    val visibleEntries = remember(entries, allowedExtensions) {
        val dirs = entries.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
        val files = entries.filter { !it.isDirectory }.filter { f -> allowedExtensions.isEmpty() || f.name.substringAfterLast('.', "").lowercase() in allowedExtensions }.sortedBy { it.name.lowercase() }
        dirs + files
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = PocketColors.surfaceCard) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.80f).navigationBarsPadding().imePadding().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PocketSheetHeader(icon = Icons.Rounded.Code, tint = PocketColors.accentSoft, iconBg = PocketColors.accentBg, title = title, subtitle = subtitle)
            Surface(shape = RoundedCornerShape(10.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                Text(currentDir.replace("/data/media/0", "/sdcard"), modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp), style = PocketColors.monoStyle, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (loading) Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp, color = PocketColors.accent) }
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val canGoUp = currentDir.trimEnd('/') != "/data/media/0" && currentDir.count { it == '/' } > 1
                    if (canGoUp) {
                        item("parent_up") {
                            Surface(modifier = Modifier.fillMaxWidth().clickable { val p = currentDir.trimEnd('/').substringBeforeLast('/').ifBlank { "/data/media/0" }; currentDir = if (p == "/data/media" || p == "/data") "/data/media/0" else p }, shape = RoundedCornerShape(10.dp), color = PocketColors.surfaceSubtle, border = BorderStroke(0.5.dp, PocketColors.border)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp)); Text(".. (Parent Folder)", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                }
                            }
                        }
                    }
                    items(visibleEntries, key = { it.path }) { item ->
                        Surface(modifier = Modifier.fillMaxWidth().clickable { if (item.isDirectory) currentDir = item.path else onSelectFile(UsbGadgetEngine.resolveDirectPath(item.path)) }, shape = RoundedCornerShape(10.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, if (!item.isDirectory) PocketColors.accent.copy(alpha = 0.5f) else PocketColors.border)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(if (item.isDirectory) Icons.Rounded.Folder else Icons.Rounded.Code, null, tint = if (item.isDirectory) PocketColors.textSecondary else PocketColors.accentSoft, modifier = Modifier.size(19.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!item.isDirectory) Text(UsbGadgetEngine.formatBytes(item.sizeBytes), style = PocketColors.monoStyle, color = PocketColors.textMuted)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp).height(46.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(0.75.dp, PocketColors.border)) { Text("Cancel", color = PocketColors.textSecondary) }
        }
    }
}
