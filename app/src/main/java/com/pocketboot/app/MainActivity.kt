package com.pocketboot.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon as AndroidIcon
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Window
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_QUICK_MOUNT = "com.pocketboot.app.ACTION_QUICK_MOUNT"
        const val EXTRA_SHORTCUT_PATH = "extra_shortcut_path"
    }

    private var shortcutRequest by mutableStateOf<Pair<String, Int>?>(null)
    private var shortcutNonce = 0

    private fun handleQuickMountIntent(intent: Intent?) {
        if (intent?.action == ACTION_QUICK_MOUNT) {
            val path = intent.getStringExtra(EXTRA_SHORTCUT_PATH)?.trim().orEmpty()
            if (path.isNotBlank()) {
                shortcutNonce++
                shortcutRequest = path to shortcutNonce
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleQuickMountIntent(intent)
        setContent {
            PocketBootApp(shortcutRequest = shortcutRequest)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickMountIntent(intent)
    }
}

private enum class NavTab(val title: String, val navLabel: String, val index: Int) {
    HOME("PocketBoot", "Drives", 0),
    DOWNLOAD("Catalog", "Catalog", 1),
    SETTINGS("Settings", "Settings", 2)
}

private enum class SortBy(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    FOLDER("Folder")
}

private enum class DriveFilter(val label: String) {
    ALL("All"),
    WINDOWS("Windows"),
    LINUX("Linux"),
    MULTIBOOT("Multi-Boot"),
    IMAGES("Images")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketBootApp(shortcutRequest: Pair<String, Int>? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { AppPrefs(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeTab by rememberSaveable { mutableStateOf(NavTab.HOME) }
    BackHandler(enabled = activeTab != NavTab.HOME) { activeTab = NavTab.HOME }
    var settings by remember { mutableStateOf(prefs.load()) }
    var status by remember { mutableStateOf(GadgetStatus()) }
    var images by remember { mutableStateOf<List<ImageFile>>(emptyList()) }
    val isoTypes = remember { mutableStateMapOf<String, IsoOsType>() }
    val watchdogStatus by LinkWatchdogTracker.state.collectAsState()

    var busy by remember { mutableStateOf(false) }
    var statusBannerError by remember { mutableStateOf<String?>(null) }
    var forceExpandSettingsLog by remember { mutableStateOf(false) }
    val logs = remember { mutableStateListOf<LogEntry>() }

    var showCreateSheet by remember { mutableStateOf(false) }
    var showRootBrowserSheet by remember { mutableStateOf(false) }
    var pendingMountFile by remember { mutableStateOf<ImageFile?>(null) }
    var windowsBuilderTarget by remember { mutableStateOf<ImageFile?>(null) }
    var linuxBuilderTarget by remember { mutableStateOf<ImageFile?>(null) }
    var showVentoyBuilder by remember { mutableStateOf(false) }
    var ventoyBuilderInitialIso by remember { mutableStateOf<ImageFile?>(null) }
    var ventoyManagerTarget by remember { mutableStateOf<ImageFile?>(null) }
    var ventoyManagerBusy by remember { mutableStateOf(false) }
    var imageExplorerTarget by remember { mutableStateOf<ImageFile?>(null) }
    var imageExplorerBusy by remember { mutableStateOf(false) }
    var showDualLunCombosSheet by remember { mutableStateOf(false) }
    var decompressTarget by remember { mutableStateOf<ImageFile?>(null) }
    var showOtgFlasherSheet by remember { mutableStateOf(false) }
    var otgFlashInitialSource by remember { mutableStateOf<ImageFile?>(null) }
    var selectedInfoFile by remember { mutableStateOf<ImageFile?>(null) }
    var renameTargetFile by remember { mutableStateOf<ImageFile?>(null) }
    var confirmDeleteFile by remember { mutableStateOf<ImageFile?>(null) }
    var showQuickDiagnostics by remember { mutableStateOf(false) }
    var lastHandledShortcutNonce by remember { mutableIntStateOf(0) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var drivesFreeBytes by remember { mutableStateOf(0L) }
    var tempCacheBytes by remember { mutableStateOf(0L) }

    val isLightSurface = settings.surfaceMode == SurfaceMode.LIGHT
    val activeTurboDl by FastIsoDownloader.state.collectAsState()
    val activeUsbBuild by UsbBuildManager.state.collectAsState()
    val usbBuildCompletion by UsbBuildManager.completion.collectAsState()
    val anyBuildActive = activeUsbBuild != null || ventoyManagerBusy || imageExplorerBusy
    val anyBuildOrDlActive = anyBuildActive || (activeTurboDl != null && activeTurboDl?.isPaused != true)
    val keepScreenAwake = settings.keepScreenAwakeDuringBuilds && anyBuildOrDlActive

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = isLightSurface
        controller.isAppearanceLightNavigationBars = isLightSurface
        if (keepScreenAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(settings.buildThermalProfile, settings.autoThermalCooldown) {
        BuildThermalHelper.configureThermal(settings.buildThermalProfile, settings.autoThermalCooldown)
    }

    LaunchedEffect(images.size, settings.defaultDir, activeUsbBuild != null) {
        withContext(Dispatchers.IO) {
            drivesFreeBytes = UsbGadgetEngine.getFreeSpaceBytes(settings.defaultDir)
            tempCacheBytes = UsbGadgetEngine.getTempStorageStats(context, settings.scanDirs + settings.defaultDir).totalBytes
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    // Mirror watchdog recovery logs into Settings Activity Log
    LaunchedEffect(watchdogStatus.recentLogs) {
        watchdogStatus.recentLogs.forEach { entry ->
            if (logs.none { it.timestamp == entry.timestamp && it.message == entry.message }) {
                logs.add(entry)
            }
        }
    }

    // Refresh live Home telemetry immediately when hot-plug or UDC watchdog fires
    LaunchedEffect(watchdogStatus.refreshSignal) {
        if (watchdogStatus.refreshSignal > 0) {
            status = UsbGadgetEngine.getStatus()
        }
    }

    // Auto-inspect ISO architecture / OS type (Windows / Linux / Utility)
    LaunchedEffect(images) {
        images.forEach { img ->
            if (!isoTypes.containsKey(img.path)) {
                isoTypes[img.path] = IsoInspector.inspect(context, img.path, img.sizeBytes)
            }
        }
    }

    fun syncKeepAlive(st: GadgetStatus, forceRestart: Boolean = false) {
        if (st.isAnyMounted) {
            if (!forceRestart && LinkWatchdogTracker.state.value.serviceActive) return
            val primaryName = st.mountedFile.substringAfterLast('/').ifBlank { st.secondaryMountedFile.substringAfterLast('/') }
            val title = if (st.isMounted && st.isSecondaryMounted) "Dual LUN: $primaryName +1" else "Mounted: $primaryName"
            val sub = "${st.activeProtocolLabel} • Keep-Alive active"
            UsbKeepAliveService.start(context, title, sub, settings.keepAdbActive)
        } else {
            UsbKeepAliveService.stop(context)
            NotificationHelper.cancel(context)
        }
    }

    fun updateSettings(updated: AppSettings, rescan: Boolean = true) {
        settings = updated
        prefs.save(updated)
        if (rescan) {
            scope.launch {
                busy = true
                status = UsbGadgetEngine.getStatus()
                images = UsbGadgetEngine.scanImages(updated.scanDirs, updated.pinnedFiles, updated.hiddenFiles)
                syncKeepAlive(status)
                busy = false
            }
        }
    }

    fun refreshAll() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            busy = true
            try {
                status = UsbGadgetEngine.getStatus()
                images = UsbGadgetEngine.scanImages(settings.scanDirs, settings.pinnedFiles, settings.hiddenFiles)
                syncKeepAlive(status, forceRestart = false)
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshAll() }

    fun showFeedback(message: String, isError: Boolean = false) {
        statusBannerError = if (isError) message else null
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (isError) "View Log" else null,
                duration = if (isError) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (res == SnackbarResult.ActionPerformed) {
                forceExpandSettingsLog = true
                activeTab = NavTab.SETTINGS
            }
        }
    }

    fun addBrowsedFiles(paths: List<String>) {
        val valid = paths.filter {
            val n = it.substringAfterLast('/')
            IsoInspector.isSupportedDriveFileName(n)
        }.distinct()
        if (valid.isEmpty()) {
            showFeedback("Only .iso, .img, and compressed (.xz/.gz/.zst) images are supported.", true)
            return
        }
        val updated = settings.copy(
            pinnedFiles = (settings.pinnedFiles + valid).distinct(),
            hiddenFiles = settings.hiddenFiles - valid.toSet()
        )
        updateSettings(updated, rescan = true)
        val msg = if (valid.size == 1) {
            "Added ${valid.first().substringAfterLast('/')} to Your Drives."
        } else {
            "Added ${valid.size} files to Your Drives."
        }
        showFeedback(msg)
    }

    fun addBrowsedFile(resolvedPath: String) = addBrowsedFiles(listOf(resolvedPath))

    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            scope.launch {
                val resolved = UsbGadgetEngine.resolveUriToPath(context, uri)
                if (resolved != null) {
                    addBrowsedFile(resolved)
                } else {
                    showFeedback("Could not locate that file on storage. Try picking it in PocketBoot Root Browser.", true)
                }
            }
        }
    }

    fun openSystemFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        runCatching {
            systemPickerLauncher.launch(intent)
        }.onFailure {
            showFeedback("System file picker unavailable.", true)
        }
    }

    fun pinHomeScreenShortcut(file: ImageFile) {
        runCatching {
            val sm = context.getSystemService(ShortcutManager::class.java)
            if (sm == null || !sm.isRequestPinShortcutSupported) {
                showFeedback("Your launcher does not support pinned shortcuts.", true)
                return
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_QUICK_MOUNT
                putExtra(MainActivity.EXTRA_SHORTCUT_PATH, file.path)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val shortLabel = file.name.substringBeforeLast('.').take(20).ifBlank { "Mount Drive" }
            val shortcut = ShortcutInfo.Builder(context, "pb_mount_${file.path.hashCode()}")
                .setShortLabel(shortLabel)
                .setLongLabel("Mount ${file.name}")
                .setIcon(AndroidIcon.createWithResource(context, R.drawable.ic_usb))
                .setIntent(intent)
                .build()
            sm.requestPinShortcut(shortcut, null)
            showFeedback("Requested Home Screen shortcut for ${file.name}.")
        }.onFailure {
            showFeedback("Could not create shortcut: ${it.message}", true)
        }
    }

    fun mountPath(path: String, chosenMode: MountMode, lunIndex: Int = 0, rememberMode: Boolean = false) {
        scope.launch {
            busy = true
            try {
                val resolvedMode = chosenMode.resolveForFile(path, settings.fileModes)
                val res = UsbGadgetEngine.mountImage(path, chosenMode, settings, lunIndex)
                logs.addAll(res.entries)
                status = UsbGadgetEngine.getStatus()
                if (res.success) {
                    if (rememberMode && chosenMode != MountMode.AUTO) {
                        val updatedModes = settings.fileModes.toMutableMap().apply { put(path, resolvedMode.name) }
                        updateSettings(settings.copy(fileModes = updatedModes), rescan = false)
                    }
                    syncKeepAlive(status)
                }
                showFeedback(res.userMessage, !res.success)
            } catch (e: Exception) {
                logs.add(LogEntry("now", "crash-guard", e.message ?: "Mount failed", true))
                showFeedback("Mount failed: ${e.message}", true)
            } finally {
                busy = false
            }
        }
    }

    fun mountCombo(combo: DualLunCombo) {
        scope.launch {
            busy = true
            try {
                val res = UsbGadgetEngine.mountDualLunCombo(combo, settings)
                logs.addAll(res.entries)
                status = UsbGadgetEngine.getStatus()
                if (res.success) {
                    syncKeepAlive(status, forceRestart = true)
                }
                showFeedback(res.userMessage, !res.success)
            } catch (e: Exception) {
                logs.add(LogEntry("now", "crash-guard", e.message ?: "Dual-LUN mount failed", true))
                showFeedback("Dual-LUN mount failed: ${e.message}", true)
            } finally {
                busy = false
            }
        }
    }

    fun unmountSlot(lunIndex: Int? = null) {
        refreshJob?.cancel()
        scope.launch {
            busy = true
            try {
                if (lunIndex == null) {
                    UsbGadgetEngine.requestAbortWatchdog()
                    UsbKeepAliveService.stop(context)
                }
                val res = UsbGadgetEngine.unmountImage(lunIndex)
                logs.addAll(res.entries)
                status = UsbGadgetEngine.getStatus()
                syncKeepAlive(status, forceRestart = false)
                showFeedback(res.userMessage, !res.success)
            } finally {
                busy = false
            }
        }
    }

    fun forceReconnectUsb() {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val res = UsbGadgetEngine.forceUsbReconnect(settings.keepAdbActive)
                logs.addAll(res.entries)
                status = UsbGadgetEngine.getStatus()
                showFeedback(res.userMessage, !res.success)
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(shortcutRequest, status.hasRoot) {
        val req = shortcutRequest ?: return@LaunchedEffect
        val (shortcutPath, nonce) = req
        if (shortcutPath.isNotBlank() && nonce != lastHandledShortcutNonce && status.hasRoot) {
            lastHandledShortcutNonce = nonce
            val existingLun = status.mountedLunFor(shortcutPath)
            if (existingLun != null) {
                unmountSlot(existingLun)
            } else {
                mountPath(shortcutPath, MountMode.AUTO, lunIndex = 0, rememberMode = false)
            }
        }
    }

    LaunchedEffect(usbBuildCompletion?.id) {
        val evt = usbBuildCompletion ?: return@LaunchedEffect
        UsbBuildManager.consumeCompletion(evt.id)
        val res = evt.result
        logs.addAll(res.entries)
        if (evt.cancelled) {
            showFeedback(res.userMessage, false)
        } else if (res.success && evt.kind == UsbBuildKind.OTG_FLASH) {
            showOtgFlasherSheet = false
            otgFlashInitialSource = null
            refreshAll()
            showFeedback(res.userMessage, false)
        } else if (res.success && res.createdPath != null) {
            when (evt.kind) {
                UsbBuildKind.WINDOWS -> windowsBuilderTarget = null
                UsbBuildKind.LINUX -> linuxBuilderTarget = null
                UsbBuildKind.MULTIBOOT -> {
                    showVentoyBuilder = false
                    ventoyBuilderInitialIso = null
                    isoTypes[res.createdPath] = IsoOsType.VENTOY_IMAGE
                }
                UsbBuildKind.DECOMPRESS -> {
                    decompressTarget = null
                }
                UsbBuildKind.OTG_FLASH -> {
                    showOtgFlasherSheet = false
                    otgFlashInitialSource = null
                }
            }
            val updated = settings.copy(
                pinnedFiles = (settings.pinnedFiles + res.createdPath).distinct(),
                hiddenFiles = settings.hiddenFiles - res.createdPath
            )
            updateSettings(updated, rescan = true)
            if (evt.autoMountAfterBuild) {
                mountPath(res.createdPath, evt.autoMountMode, lunIndex = 0)
            } else {
                showFeedback(res.userMessage, false)
            }
        } else {
            showFeedback(res.userMessage, true)
        }
    }

    PocketBootTheme(
        surfaceMode = settings.surfaceMode,
        useMonet = settings.useMonet,
        accentHex = settings.accentHex
    ) {
        Scaffold(
            containerColor = PocketColors.bgCanvas,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PocketColors.bgCanvas,
                        titleContentColor = PocketColors.textPrimary
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Branded USB Icon Badge (restored from v3.6.3 & elevated with accent gradient)
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = if (status.isAnyMounted) PocketColors.successBg else PocketColors.accentBg,
                                border = BorderStroke(
                                    1.dp,
                                    if (status.isAnyMounted) PocketColors.success.copy(alpha = 0.6f) else PocketColors.accent.copy(alpha = 0.45f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Usb,
                                        contentDescription = null,
                                        tint = if (status.isAnyMounted) PocketColors.success else PocketColors.accentSoft,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    text = activeTab.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontSize = 18.sp,
                                    color = PocketColors.textPrimary
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    PulsingStatusDot(
                                        color = when {
                                            !status.hasRoot -> PocketColors.warning
                                            status.isAnyMounted -> PocketColors.success
                                            else -> PocketColors.accentSoft
                                        },
                                        pulsing = status.isAnyMounted,
                                        dotSize = 6.dp
                                    )
                                    Text(
                                        text = when {
                                            !status.hasRoot -> "Root access needed"
                                            status.isMounted && status.isSecondaryMounted -> "Dual-LUN active"
                                            status.isMounted -> "Mounted · ${status.activeModeLabel}"
                                            status.isSecondaryMounted -> "LUN 1 · ${status.activeSecondaryModeLabel}"
                                            else -> "Ready · Hot-plug active"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (status.isAnyMounted) PocketColors.success else PocketColors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (activeTab == NavTab.HOME) {
                            IconButton(onClick = { showQuickDiagnostics = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.HealthAndSafety,
                                    contentDescription = "Pre-Flight Diagnostics",
                                    tint = PocketColors.accentSoft,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        IconButton(onClick = { refreshAll() }, enabled = !busy) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = PocketColors.accent
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh",
                                    tint = PocketColors.textSecondary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    PocketHairlineDivider()
                    NavigationBar(
                        containerColor = PocketColors.surfaceCard,
                        tonalElevation = 0.dp
                    ) {
                        NavigationBarItem(
                            selected = activeTab == NavTab.HOME,
                            onClick = { activeTab = NavTab.HOME },
                            icon = { Icon(Icons.Rounded.Storage, contentDescription = "Drives", modifier = Modifier.size(21.dp)) },
                            label = { Text(NavTab.HOME.navLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PocketColors.accentSoft,
                                selectedTextColor = PocketColors.accentSoft,
                                indicatorColor = PocketColors.accentBg,
                                unselectedIconColor = PocketColors.textMuted,
                                unselectedTextColor = PocketColors.textMuted
                            )
                        )
                        NavigationBarItem(
                            selected = activeTab == NavTab.DOWNLOAD,
                            onClick = { activeTab = NavTab.DOWNLOAD },
                            icon = { Icon(Icons.Rounded.Download, contentDescription = "Catalog", modifier = Modifier.size(21.dp)) },
                            label = { Text(NavTab.DOWNLOAD.navLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PocketColors.accentSoft,
                                selectedTextColor = PocketColors.accentSoft,
                                indicatorColor = PocketColors.accentBg,
                                unselectedIconColor = PocketColors.textMuted,
                                unselectedTextColor = PocketColors.textMuted
                            )
                        )
                        NavigationBarItem(
                            selected = activeTab == NavTab.SETTINGS,
                            onClick = {
                                forceExpandSettingsLog = false
                                activeTab = NavTab.SETTINGS
                            },
                            icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings", modifier = Modifier.size(21.dp)) },
                            label = { Text(NavTab.SETTINGS.navLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PocketColors.accentSoft,
                                selectedTextColor = PocketColors.accentSoft,
                                indicatorColor = PocketColors.accentBg,
                                unselectedIconColor = PocketColors.textMuted,
                                unselectedTextColor = PocketColors.textMuted
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            // Smooth directional slide + fade transition between tabs
            AnimatedContent(
                targetState = activeTab,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                transitionSpec = {
                    val forward = targetState.index >= initialState.index
                    val slideIn = slideInHorizontally(
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) { width -> if (forward) width / 9 else -width / 9 }
                    val slideOut = slideOutHorizontally(
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    ) { width -> if (forward) -width / 9 else width / 9 }
                    (slideIn + fadeIn(tween(220))) togetherWith (slideOut + fadeOut(tween(150)))
                },
                label = "tabTransition"
            ) { tab ->
                when (tab) {
                    NavTab.HOME -> {
                        HomeScreen(
                            status = status,
                            watchdogStatus = watchdogStatus,
                            activeUsbBuild = activeUsbBuild,
                            onCancelUsbBuild = {
                                UsbBuildManager.cancelActiveBuild(context)
                                showFeedback("Cancelling ${activeUsbBuild?.kind?.label ?: "USB"} build...")
                            },
                            images = images,
                            isoTypes = isoTypes,
                            busy = busy,
                            errorBanner = statusBannerError,
                            onDismissError = { statusBannerError = null },
                            onDismissWatchdogBanner = { LinkWatchdogTracker.dismissBanner() },
                            onViewLog = {
                                forceExpandSettingsLog = true
                                activeTab = NavTab.SETTINGS
                            },
                            onRetryRoot = { refreshAll() },
                            verifiedPaths = settings.verifiedIsoPaths.toSet(),
                            rememberedModes = settings.fileModes,
                            onQuickMount = { img, mode -> mountPath(img.path, mode, lunIndex = 0, rememberMode = false) },
                            onRequestMount = { img -> pendingMountFile = img },
                            onUnmountSlot = { lun -> unmountSlot(lun) },
                            onForceReconnect = { forceReconnectUsb() },
                            onOpenCreateBlank = { showCreateSheet = true },
                            onOpenCreateVentoy = {
                                ventoyBuilderInitialIso = null
                                showVentoyBuilder = true
                            },
                            onOpenBrowse = { showRootBrowserSheet = true },
                            onOpenOtgFlasher = {
                                otgFlashInitialSource = null
                                showOtgFlasherSheet = true
                            },
                            onOpenDecompress = { img -> decompressTarget = img },
                            dualLunCombos = settings.dualLunCombos,
                            onMountDualLunCombo = { combo -> mountCombo(combo) },
                            onOpenDualLunCombos = { showDualLunCombosSheet = true },
                            onTapFileCard = { img -> selectedInfoFile = img },
                            freeSpaceBytes = drivesFreeBytes
                        )
                    }

                    NavTab.DOWNLOAD -> {
                        val multiBootDrives = remember(images, isoTypes.toMap()) {
                            images.filter {
                                (isoTypes[it.path] ?: IsoInspector.quickGuessByName(it.name)) == IsoOsType.VENTOY_IMAGE
                            }
                        }
                        DownloadScreen(
                            settings = settings,
                            existingFileNames = images.map { it.name.lowercase() }.toSet(),
                            multiBootDrives = multiBootDrives,
                            onUpdateSettings = { updated -> updateSettings(updated, rescan = false) },
                            onDownloadCompleted = { evt ->
                                val targetPath = evt.injectedMultiBootPath ?: evt.savedPath
                                val newVerified = if (evt.checksumStatus == ChecksumVerifyStatus.VERIFIED_MATCH && evt.injectedMultiBootPath == null) {
                                    (settings.verifiedIsoPaths + evt.savedPath).distinct()
                                } else {
                                    settings.verifiedIsoPaths
                                }
                                val updated = settings.copy(
                                    pinnedFiles = (settings.pinnedFiles + targetPath).distinct(),
                                    hiddenFiles = settings.hiddenFiles - targetPath,
                                    verifiedIsoPaths = newVerified
                                )
                                updateSettings(updated, rescan = true)

                                if (evt.checksumStatus == ChecksumVerifyStatus.MISMATCH) {
                                    showFeedback("Warning: SHA-256 mismatch on ${evt.fileName}!", true)
                                } else if (evt.autoMount && status.hasRoot) {
                                    activeTab = NavTab.HOME
                                    val mode = if (evt.injectedMultiBootPath != null) MountMode.USB_RW else MountMode.AUTO
                                    mountPath(targetPath, mode, lunIndex = 0)
                                } else if (evt.injectedMultiBootPath != null) {
                                    showFeedback("Added ${evt.fileName} directly into ${evt.injectedMultiBootPath.substringAfterLast('/')}.")
                                } else {
                                    val vTag = if (evt.checksumStatus == ChecksumVerifyStatus.VERIFIED_MATCH) " (Verified SHA-256)" else ""
                                    showFeedback("Downloaded ${evt.fileName}$vTag.")
                                }
                            },
                            onQuickMountCompleted = { path ->
                                val resolved = UsbGadgetEngine.resolveDirectPath(path)
                                val found = images.firstOrNull { UsbGadgetEngine.resolveDirectPath(it.path) == resolved }
                                if (found != null) {
                                    pendingMountFile = found
                                } else {
                                    mountPath(resolved, MountMode.AUTO, lunIndex = 0, rememberMode = false)
                                }
                            },
                            onOpenWindowsBuilderForPath = { path ->
                                val resolved = UsbGadgetEngine.resolveDirectPath(path)
                                val cached = images.firstOrNull { UsbGadgetEngine.resolveDirectPath(it.path) == resolved }
                                if (cached != null && cached.sizeBytes > 0L) {
                                    windowsBuilderTarget = cached
                                } else {
                                    scope.launch {
                                        val resolvedImg = withContext(Dispatchers.IO) {
                                            val publicF = java.io.File(resolved.replaceFirst("/data/media/0", "/storage/emulated/0"))
                                            val sz = if (publicF.exists() && publicF.length() > 0L) {
                                                publicF.length()
                                            } else {
                                                val q = "'${resolved.replace("'", "'\\''")}'"
                                                com.topjohnwu.superuser.Shell.cmd("stat -c '%s' $q 2>/dev/null").exec().out.firstOrNull()?.trim()?.toLongOrNull() ?: 0L
                                            }
                                            val parent = resolved.substringBeforeLast('/', "")
                                            ImageFile(
                                                path = resolved,
                                                name = resolved.substringAfterLast('/'),
                                                sizeBytes = sz,
                                                folderLabel = UsbGadgetEngine.friendlyFolderName(parent)
                                            )
                                        }
                                        windowsBuilderTarget = resolvedImg
                                    }
                                }
                            },
                            onAddCompletedToMultiBoot = { path ->
                                val resolved = UsbGadgetEngine.resolveDirectPath(path)
                                val targetMb = multiBootDrives.firstOrNull()
                                if (targetMb != null) {
                                    ventoyManagerTarget = targetMb
                                    showFeedback("Select ${resolved.substringAfterLast('/')} inside Multi-Boot Manager to add.")
                                }
                            },
                            onAppendLog = { entry -> logs.add(entry) },
                            onMessage = { msg, isErr -> showFeedback(msg, isErr) }
                        )
                    }

                    NavTab.SETTINGS -> {
                        SettingsScreen(
                            status = status,
                            settings = settings,
                            logs = logs,
                            forceExpandLogs = forceExpandSettingsLog,
                            isOperationActive = anyBuildActive || activeTurboDl != null,
                            onUpdateSettings = { updated -> updateSettings(updated, rescan = true) },
                            onResetNormalUsb = { unmountSlot(null) },
                            onClearLogs = { logs.clear() }
                        )
                    }
                }
            }
        }

        // First-Launch Default Folder Setup Dialog
        if (!settings.hasCompletedSetup) {
            FirstLaunchFolderDialog(
                onConfirm = { chosenDir ->
                    scope.launch {
                        UsbGadgetEngine.ensureDirectoryExists(chosenDir, context)
                        val updatedDirs = (listOf(chosenDir) + settings.scanDirs.filter { it != chosenDir }).distinct()
                        updateSettings(
                            settings.copy(
                                hasCompletedSetup = true,
                                defaultDir = chosenDir,
                                scanDirs = updatedDirs
                            ),
                            rescan = true
                        )
                        showFeedback("Default folder set to ${UsbGadgetEngine.friendlyFolderName(chosenDir)}.")
                    }
                }
            )
        }

        // OS-Aware Mount / Unmount / Multi-LUN Protocol Sheet (with >2.2 GB CD-ROM guard & Ventoy)
        pendingMountFile?.let { file ->
            val osType = isoTypes[file.path] ?: IsoInspector.quickGuessByName(file.name)
            val mountedLun = status.mountedLunFor(file.path)
            val existingVentoy = images.firstOrNull {
                (isoTypes[it.path] ?: IsoInspector.quickGuessByName(it.name)) == IsoOsType.VENTOY_IMAGE
            }
            MountProtocolSheet(
                file = file,
                osType = osType,
                mountedLun = mountedLun,
                multiLunSupported = status.multiLunSupported,
                primaryOccupied = status.isMounted && status.mountedFile != file.path,
                secondaryOccupied = status.isSecondaryMounted && status.secondaryMountedFile != file.path,
                existingVentoyDrive = existingVentoy,
                initialRememberMode = settings.fileModes.containsKey(file.path),
                onDismiss = { pendingMountFile = null },
                onSelectProtocol = { mode, lun, rememberFlag ->
                    pendingMountFile = null
                    if (!rememberFlag && settings.fileModes.containsKey(file.path)) {
                        val cleared = settings.fileModes - file.path
                        updateSettings(settings.copy(fileModes = cleared), rescan = false)
                    }
                    mountPath(file.path, mode, lun, rememberMode = rememberFlag)
                },
                onUnmountThis = { lun ->
                    pendingMountFile = null
                    unmountSlot(lun)
                },
                onOpenWindowsBuilder = {
                    pendingMountFile = null
                    windowsBuilderTarget = file
                },
                onOpenLinuxBuilder = {
                    pendingMountFile = null
                    linuxBuilderTarget = file
                },
                onOpenVentoyForIso = {
                    pendingMountFile = null
                    if (existingVentoy != null) {
                        ventoyManagerTarget = existingVentoy
                    } else {
                        ventoyBuilderInitialIso = file
                        showVentoyBuilder = true
                    }
                },
                onOpenVentoyManager = {
                    pendingMountFile = null
                    ventoyManagerTarget = file
                }
            )
        }

        // Rufus-Style Windows Bootable USB Builder Sheet (with Custom Presets)
        windowsBuilderTarget?.let { isoFile ->
            WindowsBuilderSheet(
                isoFile = isoFile,
                defaultDir = settings.defaultDir,
                presets = settings.autounattendPresets,
                systemTweaksPresets = settings.systemTweaksPresets,
                systemTweaksSearchHistory = settings.systemTweaksSearchHistory,
                building = activeUsbBuild?.kind == UsbBuildKind.WINDOWS,
                progress = if (activeUsbBuild?.kind == UsbBuildKind.WINDOWS) activeUsbBuild?.progress ?: WindowsBuildProgress() else WindowsBuildProgress(),
                onDismiss = { windowsBuilderTarget = null },
                onCancelBuild = {
                    UsbBuildManager.cancelActiveBuild(context)
                    showFeedback("Cancelling Windows USB build...")
                },
                onSavePreset = { preset ->
                    val updatedPresets = (settings.autounattendPresets.filterNot { it.name.equals(preset.name, true) } + preset)
                    updateSettings(settings.copy(autounattendPresets = updatedPresets), rescan = false)
                    showFeedback("Saved preset \"${preset.name}\".")
                },
                onDeletePreset = { presetName ->
                    val updatedPresets = settings.autounattendPresets.filterNot { it.name == presetName }
                    updateSettings(settings.copy(autounattendPresets = updatedPresets), rescan = false)
                    showFeedback("Deleted preset \"$presetName\".")
                },
                onSaveSystemTweaksPreset = { tp ->
                    val updated = settings.systemTweaksPresets.filterNot { it.name.equals(tp.name, true) } + tp
                    updateSettings(settings.copy(systemTweaksPresets = updated), rescan = false)
                    showFeedback("Saved System Tweaks preset \"${tp.name}\".")
                },
                onDeleteSystemTweaksPreset = { tpName ->
                    val updated = settings.systemTweaksPresets.filterNot { it.name == tpName }
                    updateSettings(settings.copy(systemTweaksPresets = updated), rescan = false)
                    showFeedback("Deleted System Tweaks preset \"$tpName\".")
                },
                onUpdateSearchHistory = { hist ->
                    updateSettings(settings.copy(systemTweaksSearchHistory = hist), rescan = false)
                },
                onStartBuild = { cfg ->
                    if (anyBuildActive || !UsbBuildManager.startWindowsBuild(context, cfg)) {
                        showFeedback("Another USB build is already running. Wait or cancel it first.", true)
                    }
                }
            )
        }

        // Linux Persistent USB Builder Sheet (casper-rw / persistence ext4)
        linuxBuilderTarget?.let { isoFile ->
            LinuxBuilderSheet(
                isoFile = isoFile,
                defaultDir = settings.defaultDir,
                building = activeUsbBuild?.kind == UsbBuildKind.LINUX,
                progress = if (activeUsbBuild?.kind == UsbBuildKind.LINUX) activeUsbBuild?.progress ?: WindowsBuildProgress() else WindowsBuildProgress(),
                onDismiss = { linuxBuilderTarget = null },
                onCancelBuild = {
                    UsbBuildManager.cancelActiveBuild(context)
                    showFeedback("Cancelling Linux USB build...")
                },
                onStartBuild = { cfg ->
                    if (anyBuildActive || !UsbBuildManager.startLinuxBuild(context, cfg)) {
                        showFeedback("Another USB build is already running. Wait or cancel it first.", true)
                    }
                }
            )
        }

        // Ventoy Multi-Boot .img Builder Sheet
        if (showVentoyBuilder) {
            VentoyBuilderSheet(
                defaultDir = settings.defaultDir,
                initialIso = ventoyBuilderInitialIso,
                presets = settings.autounattendPresets,
                systemTweaksPresets = settings.systemTweaksPresets,
                systemTweaksSearchHistory = settings.systemTweaksSearchHistory,
                building = activeUsbBuild?.kind == UsbBuildKind.MULTIBOOT,
                onSavePreset = { preset ->
                    val updatedPresets = (settings.autounattendPresets.filterNot { it.name.equals(preset.name, true) } + preset)
                    updateSettings(settings.copy(autounattendPresets = updatedPresets), rescan = false)
                    showFeedback("Saved preset \"${preset.name}\".")
                },
                onDeletePreset = { presetName ->
                    val updatedPresets = settings.autounattendPresets.filterNot { it.name == presetName }
                    updateSettings(settings.copy(autounattendPresets = updatedPresets), rescan = false)
                    showFeedback("Deleted preset \"$presetName\".")
                },
                onSaveSystemTweaksPreset = { tp ->
                    val updated = settings.systemTweaksPresets.filterNot { it.name.equals(tp.name, true) } + tp
                    updateSettings(settings.copy(systemTweaksPresets = updated), rescan = false)
                    showFeedback("Saved System Tweaks preset \"${tp.name}\".")
                },
                onDeleteSystemTweaksPreset = { tpName ->
                    val updated = settings.systemTweaksPresets.filterNot { it.name == tpName }
                    updateSettings(settings.copy(systemTweaksPresets = updated), rescan = false)
                    showFeedback("Deleted System Tweaks preset \"$tpName\".")
                },
                onUpdateSearchHistory = { hist ->
                    updateSettings(settings.copy(systemTweaksSearchHistory = hist), rescan = false)
                },
                progress = if (activeUsbBuild?.kind == UsbBuildKind.MULTIBOOT) activeUsbBuild?.progress ?: WindowsBuildProgress() else WindowsBuildProgress(),
                onDismiss = {
                    showVentoyBuilder = false
                    ventoyBuilderInitialIso = null
                },
                onCancelBuild = {
                    UsbBuildManager.cancelActiveBuild(context)
                    showFeedback("Cancelling Multi-Boot drive build...")
                },
                onStartBuild = { cfg ->
                    if (anyBuildActive || !UsbBuildManager.startMultiBootBuild(context, cfg)) {
                        showFeedback("Another USB build is already running. Wait or cancel it first.", true)
                    }
                },
                onMessage = { msg, isErr -> showFeedback(msg, isErr) }
            )
        }

        // In-App Ventoy ISO Manager Sheet (Add / Remove ISOs anytime)
        if (showDualLunCombosSheet) {
            DualLunCombosSheet(
                combos = settings.dualLunCombos,
                images = images.filterNot { IsoInspector.isCompressedImageName(it.name) },
                multiLunSupported = status.multiLunSupported,
                busy = busy,
                onDismiss = { showDualLunCombosSheet = false },
                onMountCombo = { combo ->
                    showDualLunCombosSheet = false
                    mountCombo(combo)
                },
                onSaveCombo = { newCombo ->
                    val updated = settings.dualLunCombos.filterNot { it.id == newCombo.id } + newCombo
                    updateSettings(settings.copy(dualLunCombos = updated), rescan = false)
                    showFeedback("Saved Dual-LUN combo '${newCombo.name}'.")
                },
                onDeleteCombo = { comboId ->
                    val updated = settings.dualLunCombos.filterNot { it.id == comboId }
                    updateSettings(settings.copy(dualLunCombos = updated), rescan = false)
                    showFeedback("Removed Dual-LUN combo.")
                }
            )
        }

        decompressTarget?.let { compFile ->
            val isDecompActive = activeUsbBuild?.kind == UsbBuildKind.DECOMPRESS
            val decompProgress = if (isDecompActive) activeUsbBuild?.progress ?: WindowsBuildProgress() else WindowsBuildProgress()
            CompressedImageSheet(
                sourceFile = compFile,
                defaultDir = settings.defaultDir,
                building = isDecompActive,
                progress = decompProgress,
                onDismiss = { if (!isDecompActive) decompressTarget = null },
                onCancelBuild = { UsbBuildManager.cancelActiveBuild(context) },
                onStartDecompress = { cfg ->
                    if (!UsbBuildManager.startDecompress(context, cfg)) {
                        showFeedback("Another build or flash job is already running.", true)
                    }
                }
            )
        }

        if (showOtgFlasherSheet) {
            val isOtgActive = activeUsbBuild?.kind == UsbBuildKind.OTG_FLASH
            val otgProgress = if (isOtgActive) activeUsbBuild?.progress ?: WindowsBuildProgress() else WindowsBuildProgress()
            OtgFlasherSheet(
                initialSource = otgFlashInitialSource,
                availableImages = images,
                defaultDir = settings.defaultDir,
                presets = settings.autounattendPresets,
                systemTweaksPresets = settings.systemTweaksPresets,
                systemTweaksSearchHistory = settings.systemTweaksSearchHistory,
                isAnyVirtualDriveMounted = status.isAnyMounted,
                flashing = isOtgActive,
                progress = otgProgress,
                onDismiss = {
                    if (!isOtgActive) {
                        showOtgFlasherSheet = false
                        otgFlashInitialSource = null
                    }
                },
                onUnmountAllVirtual = { unmountSlot(null) },
                onCancelFlash = { UsbBuildManager.cancelActiveBuild(context) },
                onSavePreset = { preset ->
                    val updatedPresets = (settings.autounattendPresets.filterNot { it.name.equals(preset.name, true) } + preset)
                    updateSettings(settings.copy(autounattendPresets = updatedPresets), rescan = false)
                    showFeedback("Saved preset \"${preset.name}\".")
                },
                onDeletePreset = { presetName ->
                    val updatedPresets = settings.autounattendPresets.filterNot { it.name == presetName }
                    updateSettings(settings.copy(autounattendPresets = updatedPresets), rescan = false)
                    showFeedback("Deleted preset \"$presetName\".")
                },
                onSaveSystemTweaksPreset = { tp ->
                    val updated = settings.systemTweaksPresets.filterNot { it.name.equals(tp.name, true) } + tp
                    updateSettings(settings.copy(systemTweaksPresets = updated), rescan = false)
                    showFeedback("Saved System Tweaks preset \"${tp.name}\".")
                },
                onDeleteSystemTweaksPreset = { tpName ->
                    val updated = settings.systemTweaksPresets.filterNot { it.name == tpName }
                    updateSettings(settings.copy(systemTweaksPresets = updated), rescan = false)
                    showFeedback("Deleted System Tweaks preset \"$tpName\".")
                },
                onUpdateSearchHistory = { hist ->
                    updateSettings(settings.copy(systemTweaksSearchHistory = hist), rescan = false)
                },
                onStartRawFlash = { cfg ->
                    if (!UsbBuildManager.startOtgFlash(context, cfg)) {
                        showFeedback("Another build or flash job is already running.", true)
                    }
                },
                onStartWindowsOtgFlash = { cfg ->
                    if (!UsbBuildManager.startOtgWindowsFlash(context, cfg)) {
                        showFeedback("Another build or flash job is already running.", true)
                    }
                },
                onStartLinuxOtgFlash = { cfg ->
                    if (!UsbBuildManager.startOtgLinuxFlash(context, cfg)) {
                        showFeedback("Another build or flash job is already running.", true)
                    }
                },
                onStartMultiBootOtgFlash = { cfg ->
                    if (!UsbBuildManager.startOtgMultiBootFlash(context, cfg)) {
                        showFeedback("Another build or flash job is already running.", true)
                    }
                }
            )
        }

        imageExplorerTarget?.let { expFile ->
            val expMountedLun = status.mountedLunFor(expFile.path)
            ImageExplorerSheet(
                imgFile = expFile,
                isMountedToPc = expMountedLun != null,
                defaultExportDir = settings.defaultDir,
                onDismiss = {
                    imageExplorerBusy = false
                    imageExplorerTarget = null
                },
                onUnmountFirst = { unmountSlot(expMountedLun) },
                onImageModified = {
                    scope.launch {
                        images = UsbGadgetEngine.scanImages(settings.scanDirs, settings.pinnedFiles, settings.hiddenFiles)
                    }
                },
                onMessage = { msg, err -> showFeedback(msg, err) },
                onBusyChanged = { imageExplorerBusy = it }
            )
        }

        ventoyManagerTarget?.let { vFile ->
            val vMountedLun = status.mountedLunFor(vFile.path)
            VentoyManagerSheet(
                ventoyFile = vFile,
                isMountedToPc = vMountedLun != null,
                availableIsos = images,
                presets = settings.autounattendPresets,
                systemTweaksPresets = settings.systemTweaksPresets,
                systemTweaksSearchHistory = settings.systemTweaksSearchHistory,
                defaultDir = settings.defaultDir,
                onSavePreset = { preset ->
                    val updatedPresets = (settings.autounattendPresets.filterNot { it.name.equals(preset.name, true) } + preset)
                    updateSettings(settings.copy(autounattendPresets = updatedPresets), rescan = false)
                    showFeedback("Saved preset \"${preset.name}\".")
                },
                onDeletePreset = { presetName ->
                    val updatedPresets = settings.autounattendPresets.filterNot { it.name == presetName }
                    updateSettings(settings.copy(autounattendPresets = updatedPresets), rescan = false)
                    showFeedback("Deleted preset \"$presetName\".")
                },
                onSaveSystemTweaksPreset = { tp ->
                    val updated = settings.systemTweaksPresets.filterNot { it.name.equals(tp.name, true) } + tp
                    updateSettings(settings.copy(systemTweaksPresets = updated), rescan = false)
                    showFeedback("Saved System Tweaks preset \"${tp.name}\".")
                },
                onDeleteSystemTweaksPreset = { tpName ->
                    val updated = settings.systemTweaksPresets.filterNot { it.name == tpName }
                    updateSettings(settings.copy(systemTweaksPresets = updated), rescan = false)
                    showFeedback("Deleted System Tweaks preset \"$tpName\".")
                },
                onUpdateSearchHistory = { hist ->
                    updateSettings(settings.copy(systemTweaksSearchHistory = hist), rescan = false)
                },
                onDismiss = {
                    ventoyManagerBusy = false
                    ventoyManagerTarget = null
                },
                onUnmountFirst = { unmountSlot(vMountedLun) },
                onLibraryChanged = { refreshAll() },
                onMessage = { msg, isErr -> showFeedback(msg, isErr) },
                onBusyChanged = { ventoyManagerBusy = it }
            )
        }

        // Built-in Root File Browser Sheet
        if (showRootBrowserSheet) {
            val mountedSet = buildSet {
                if (status.mountedFile.isNotEmpty()) add(status.mountedFile)
                if (status.secondaryMountedFile.isNotEmpty()) add(status.secondaryMountedFile)
            }
            val addedSet = remember(images) {
                images.map { UsbGadgetEngine.resolveDirectPath(it.path) }.toSet()
            }
            RootFileBrowserSheet(
                initialDir = settings.defaultDir,
                defaultDir = settings.defaultDir,
                addedPaths = addedSet,
                mountedPaths = mountedSet,
                onDismiss = { showRootBrowserSheet = false },
                onOpenSystemPicker = { openSystemFilePicker() },
                onSelectFiles = { selectedPaths ->
                    showRootBrowserSheet = false
                    addBrowsedFiles(selectedPaths)
                },
                onFileRenamed = { oldPath, newPath ->
                    val updatedPinned = settings.pinnedFiles.map { if (it == oldPath) newPath else it }
                    val updatedModes = settings.fileModes.toMutableMap().apply {
                        remove(oldPath)?.let { put(newPath, it) }
                    }
                    updateSettings(settings.copy(pinnedFiles = updatedPinned, fileModes = updatedModes), rescan = true)
                },
                onFileDeleted = { deletedPath ->
                    val updated = settings.copy(
                        pinnedFiles = settings.pinnedFiles - deletedPath,
                        hiddenFiles = settings.hiddenFiles - deletedPath
                    )
                    updateSettings(updated, rescan = true)
                },
                onMessage = { msg, isErr -> showFeedback(msg, isErr) }
            )
        }

        // Create Blank Drive Bottom Sheet
        if (showCreateSheet) {
            ModalBottomSheet(
                onDismissRequest = { if (!busy) showCreateSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = PocketColors.surfaceCard
            ) {
                CreateBlankSheetContent(
                    defaultDir = settings.defaultDir,
                    busy = busy,
                    onCreate = { dir, name, sizeMb, mountNow ->
                        scope.launch {
                            busy = true
                            val createRes = UsbGadgetEngine.createBlankImage(dir, name, sizeMb, context)
                            logs.addAll(createRes.entries)
                            images = UsbGadgetEngine.scanImages(settings.scanDirs, settings.pinnedFiles, settings.hiddenFiles)
                            showCreateSheet = false
                            if (createRes.success && mountNow && createRes.createdPath != null) {
                                busy = false
                                mountPath(createRes.createdPath, MountMode.USB_RW, lunIndex = 0)
                            } else {
                                busy = false
                                showFeedback(createRes.userMessage, !createRes.success)
                            }
                        }
                    }
                )
            }
        }

        // File Tap Action Sheet: Direct Mount / Unmount / OS Builder / Checksum / Rename / Delete
        selectedInfoFile?.let { file ->
            val osType = isoTypes[file.path] ?: IsoInspector.quickGuessByName(file.name)
            val mountedLun = status.mountedLunFor(file.path)
            FileInfoBottomSheet(
                file = file,
                mountedLun = mountedLun,
                osType = osType,
                hasRoot = status.hasRoot,
                busy = busy,
                rememberedModeLabel = settings.fileModes[file.path]?.let { runCatching { MountMode.valueOf(it).shortLabel }.getOrNull() },
                onDismiss = { selectedInfoFile = null },
                onOpenMountSheet = {
                    selectedInfoFile = null
                    pendingMountFile = file
                },
                onUnmountThis = { lun ->
                    selectedInfoFile = null
                    unmountSlot(lun)
                },
                onOpenWindowsBuilder = {
                    selectedInfoFile = null
                    windowsBuilderTarget = file
                },
                onOpenLinuxBuilder = {
                    selectedInfoFile = null
                    linuxBuilderTarget = file
                },
                onOpenVentoyManager = {
                    selectedInfoFile = null
                    ventoyManagerTarget = file
                },
                onOpenImageExplorer = {
                    selectedInfoFile = null
                    imageExplorerTarget = file
                },
                onOpenDecompress = {
                    selectedInfoFile = null
                    decompressTarget = file
                },
                onOpenOtgFlasherForFile = {
                    selectedInfoFile = null
                    otgFlashInitialSource = file
                    showOtgFlasherSheet = true
                },
                onClearRememberedMode = {
                    updateSettings(settings.copy(fileModes = settings.fileModes - file.path), rescan = false)
                    showFeedback("Cleared 1-tap quick mount mode for ${file.name}.")
                },
                onRequestRename = {
                    selectedInfoFile = null
                    renameTargetFile = file
                },
                onRemoveFromList = {
                    val updated = settings.copy(
                        pinnedFiles = settings.pinnedFiles - file.path,
                        hiddenFiles = (settings.hiddenFiles + file.path).distinct()
                    )
                    selectedInfoFile = null
                    updateSettings(updated, rescan = true)
                    showFeedback("Removed ${file.name} from list (file kept on phone).")
                },
                onRequestDelete = {
                    selectedInfoFile = null
                    confirmDeleteFile = file
                }
            )
        }

        // Rename File Dialog
        renameTargetFile?.let { file ->
            val isMounted = status.mountedLunFor(file.path) != null
            val ext = when {
                file.name.endsWith(".img.xz", true) -> ".img.xz"
                file.name.endsWith(".img.gz", true) -> ".img.gz"
                file.name.endsWith(".img.zst", true) -> ".img.zst"
                file.name.endsWith(".iso.xz", true) -> ".iso.xz"
                file.name.endsWith(".iso.gz", true) -> ".iso.gz"
                file.name.endsWith(".iso.zst", true) -> ".iso.zst"
                file.name.endsWith(".iso", true) -> ".iso"
                file.name.endsWith(".img", true) -> ".img"
                else -> ""
            }
            val baseName = if (ext.isNotEmpty()) file.name.dropLast(ext.length) else file.name
            var newName by remember(file.path) { mutableStateOf(baseName) }

            AlertDialog(
                onDismissRequest = { renameTargetFile = null },
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
                            enabled = !isMounted,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            renameTargetFile = null
                            scope.launch {
                                busy = true
                                val res = UsbGadgetEngine.renameImageFile(file.path, newName, isMounted, context)
                                logs.addAll(res.entries)
                                if (res.success && res.createdPath != null) {
                                    val newPath = res.createdPath
                                    UsbGadgetEngine.fixUserStoragePermissions(context, newPath, isDir = false)
                                    val oldResolved = UsbGadgetEngine.resolveDirectPath(file.path)
                                    val updatedPinned = settings.pinnedFiles.map { if (UsbGadgetEngine.resolveDirectPath(it) == oldResolved) newPath else it }
                                    val updatedModes = settings.fileModes.toMutableMap().apply {
                                        remove(file.path)?.let { put(newPath, it) }
                                    }
                                    val updatedCombos = settings.dualLunCombos.map { c ->
                                        c.copy(
                                            lun0Path = if (UsbGadgetEngine.resolveDirectPath(c.lun0Path) == oldResolved) newPath else c.lun0Path,
                                            lun1Path = if (UsbGadgetEngine.resolveDirectPath(c.lun1Path) == oldResolved) newPath else c.lun1Path
                                        )
                                    }
                                    val updatedFav = if (UsbGadgetEngine.resolveDirectPath(settings.favoriteQuickMountPath) == oldResolved) newPath else settings.favoriteQuickMountPath
                                    val updatedMbTarget = if (UsbGadgetEngine.resolveDirectPath(settings.downloadTargetMultiBootPath) == oldResolved) newPath else settings.downloadTargetMultiBootPath
                                    updateSettings(
                                        settings.copy(
                                            pinnedFiles = updatedPinned,
                                            fileModes = updatedModes,
                                            dualLunCombos = updatedCombos,
                                            favoriteQuickMountPath = updatedFav,
                                            downloadTargetMultiBootPath = updatedMbTarget
                                        ),
                                        rescan = true
                                    )
                                }
                                busy = false
                                showFeedback(res.userMessage, !res.success)
                            }
                        },
                        enabled = !isMounted && newName.trim().isNotEmpty() && !newName.contains('/'),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PocketColors.accent,
                            contentColor = PocketColors.onAccent
                        )
                    ) {
                        Text("Rename", fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTargetFile = null }) {
                        Text("Cancel", color = PocketColors.textSecondary)
                    }
                }
            )
        }

        // Permanent Delete Confirmation Dialog
        confirmDeleteFile?.let { file ->
            AlertDialog(
                onDismissRequest = { confirmDeleteFile = null },
                containerColor = PocketColors.surfaceCard,
                title = { Text("Delete Permanently?", color = PocketColors.textPrimary) },
                text = {
                    Text(
                        text = "Delete ${file.name} from your phone? This frees ${UsbGadgetEngine.formatBytes(file.sizeBytes)} and cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PocketColors.textSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val delLun = status.mountedLunFor(file.path)
                            confirmDeleteFile = null
                            scope.launch {
                                busy = true
                                val res = UsbGadgetEngine.deleteImageFile(file.path, delLun, context)
                                logs.addAll(res.entries)
                                if (delLun != null) {
                                    val afterStatus = UsbGadgetEngine.getStatus()
                                    if (!afterStatus.isAnyMounted) UsbKeepAliveService.stop(context)
                                }
                                val updated = settings.copy(
                                    pinnedFiles = settings.pinnedFiles - file.path,
                                    hiddenFiles = settings.hiddenFiles - file.path
                                )
                                updateSettings(updated, rescan = true)
                                busy = false
                                showFeedback(res.userMessage, !res.success)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PocketColors.danger,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDeleteFile = null }) {
                        Text("Cancel", color = PocketColors.textSecondary)
                    }
                }
            )
        }

        if (showQuickDiagnostics) {
            DiagnosticsSheet(onDismiss = { showQuickDiagnostics = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MountProtocolSheet(
    file: ImageFile,
    osType: IsoOsType,
    mountedLun: Int?,
    multiLunSupported: Boolean,
    primaryOccupied: Boolean,
    secondaryOccupied: Boolean,
    existingVentoyDrive: ImageFile?,
    initialRememberMode: Boolean,
    onDismiss: () -> Unit,
    onSelectProtocol: (MountMode, Int, Boolean) -> Unit,
    onUnmountThis: (Int) -> Unit,
    onOpenWindowsBuilder: () -> Unit,
    onOpenLinuxBuilder: () -> Unit,
    onOpenVentoyForIso: () -> Unit,
    onOpenVentoyManager: () -> Unit
) {
    val isIso = file.name.endsWith(".iso", ignoreCase = true)
    val isWindows = osType == IsoOsType.WINDOWS
    val isLinux = osType == IsoOsType.LINUX || osType == IsoOsType.UTILITY
    val isVentoy = osType == IsoOsType.VENTOY_IMAGE
    val exceedsKernelCdLimit = isIso && file.sizeBytes > IsoInspector.KERNEL_CDROM_MAX_BYTES

    val recommendedMode = when {
        isVentoy -> MountMode.USB_RW
        isLinux -> MountMode.USB_RO
        isIso && !exceedsKernelCdLimit -> MountMode.CDROM
        isIso -> MountMode.USB_RO
        else -> MountMode.USB_RW
    }
    var selectedLun by remember(file.path, mountedLun, primaryOccupied, secondaryOccupied) {
        mutableIntStateOf(
            when {
                mountedLun != null -> mountedLun
                primaryOccupied && multiLunSupported && !secondaryOccupied -> 1
                else -> 0
            }
        )
    }
    var showOtherModes by remember(osType) { mutableStateOf(!isWindows && !isLinux && !isVentoy) }
    var rememberForQuickMount by remember(file.path) { mutableStateOf(initialRememberMode) }
    val options = listOf(MountMode.CDROM, MountMode.USB_RO, MountMode.USB_RW)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            PocketSheetHeader(
                icon = when {
                    isVentoy -> Icons.Rounded.Layers
                    isWindows -> Icons.Rounded.Window
                    isLinux -> Icons.Rounded.Terminal
                    else -> Icons.Rounded.Usb
                },
                tint = when {
                    mountedLun != null -> PocketColors.success
                    isVentoy -> PocketColors.violet
                    isWindows -> PocketColors.infoBlue
                    else -> PocketColors.accentSoft
                },
                iconBg = when {
                    mountedLun != null -> PocketColors.successBg
                    isVentoy -> PocketColors.violetBg
                    isWindows -> PocketColors.infoBlueBg
                    else -> PocketColors.accentBg
                },
                title = when {
                    mountedLun != null -> "Mounted on LUN $mountedLun"
                    isVentoy -> "Multi-Boot Drive"
                    isWindows -> "Windows ISO"
                    isLinux -> "Linux ISO"
                    else -> "Mount Drive"
                },
                subtitle = "${file.name} · ${UsbGadgetEngine.formatBytes(file.sizeBytes)}",
                subtitleMono = true
            )

            if (mountedLun != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PocketColors.successBg,
                    border = BorderStroke(0.5.dp, PocketColors.success)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = PocketColors.success, modifier = Modifier.size(19.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Active on LUN $mountedLun", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text("Eject or select another mode to switch", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                        Button(
                            onClick = { onUnmountThis(mountedLun) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PocketColors.danger, contentColor = Color.White)
                        ) {
                            Text("Eject", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Segmented Multi-LUN Slot Picker
            if (multiLunSupported) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PocketColors.surfaceInput,
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        listOf(0 to "Primary (LUN 0)", 1 to "Secondary (LUN 1)").forEach { (slot, label) ->
                            val selected = selectedLun == slot
                            val occupied = if (slot == 0) primaryOccupied else secondaryOccupied
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) PocketColors.surfaceSubtle else Color.Transparent)
                                    .clickable { selectedLun = slot }
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) PocketColors.textPrimary else PocketColors.textSecondary
                                )
                                Text(
                                    text = if (occupied) "In use" else "Free",
                                    fontSize = 11.sp,
                                    color = if (occupied) PocketColors.warning else PocketColors.textMuted
                                )
                            }
                        }
                    }
                }
            }

            if (isVentoy) {
                MountActionCard(
                    icon = Icons.Rounded.Usb,
                    title = "Mount to PC",
                    badge = "Recommended",
                    subtitle = "Writable USB · boots all over 2.2 GB Windows & Linux ISOs cleanly",
                    highlighted = true,
                    onClick = { onSelectProtocol(MountMode.USB_RW, selectedLun, rememberForQuickMount) }
                )
                MountActionCard(
                    icon = Icons.Rounded.Layers,
                    title = "Manage ISOs",
                    badge = null,
                    subtitle = "Add, move, or remove ISOs and Win 11 bypasses",
                    highlighted = false,
                    onClick = onOpenVentoyManager
                )
                TextButton(
                    onClick = { showOtherModes = !showOtherModes },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = if (showOtherModes) "Hide read-only mode" else "Mount as Read-Only USB (write-protected)",
                        fontSize = 12.sp,
                        color = PocketColors.textSecondary
                    )
                }
            } else if (isWindows) {
                if (exceedsKernelCdLimit) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PocketColors.warningBg,
                        border = BorderStroke(0.5.dp, PocketColors.warning)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.warning, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Over 2.2 GB — Android truncates Virtual CD here. Use NTFS or Multi-Boot USB.",
                                style = MaterialTheme.typography.bodySmall,
                                color = PocketColors.textPrimary
                            )
                        }
                    }
                }

                MountActionCard(
                    icon = Icons.Rounded.Window,
                    title = "Windows USB Builder",
                    badge = "NTFS Boot",
                    subtitle = "GPT (UEFI) or MBR (Legacy BIOS) with Win 11 bypasses",
                    highlighted = exceedsKernelCdLimit,
                    onClick = onOpenWindowsBuilder
                )
                MountActionCard(
                    icon = Icons.Rounded.Layers,
                    title = if (existingVentoyDrive != null) "Add to Multi-Boot Drive" else "Create Multi-Boot Drive",
                    badge = "No Extract",
                    subtitle = if (existingVentoyDrive != null) {
                        "Copy or move into ${existingVentoyDrive.name}"
                    } else {
                        "Boot raw over 2.2 GB ISOs directly in UEFI & BIOS"
                    },
                    highlighted = false,
                    onClick = onOpenVentoyForIso
                )
                if (!exceedsKernelCdLimit) {
                    MountActionCard(
                        icon = Icons.Rounded.Album,
                        title = "Mount as Virtual CD",
                        badge = "Instant",
                        subtitle = "Under 2.2 GB — boots directly with zero extra space",
                        highlighted = true,
                        onClick = { onSelectProtocol(MountMode.CDROM, selectedLun, rememberForQuickMount) }
                    )
                }
                TextButton(
                    onClick = { showOtherModes = !showOtherModes },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = if (showOtherModes) "Hide raw mount modes" else "Raw mount modes",
                        fontSize = 12.sp,
                        color = PocketColors.textSecondary
                    )
                }
            } else if (isLinux) {
                MountActionCard(
                    icon = Icons.Rounded.Usb,
                    title = "Mount as Read-Only USB",
                    badge = "Recommended",
                    subtitle = "No size cap · boots hybrid Linux ISOs on UEFI & BIOS",
                    highlighted = true,
                    onClick = { onSelectProtocol(MountMode.USB_RO, selectedLun, rememberForQuickMount) }
                )
                MountActionCard(
                    icon = Icons.Rounded.Terminal,
                    title = "Linux & Tool USB Builder",
                    badge = "2 Modes",
                    subtitle = "With Persistence (ext4) or Without Persistence (FAT32/DD)",
                    highlighted = false,
                    onClick = onOpenLinuxBuilder
                )
                MountActionCard(
                    icon = Icons.Rounded.Layers,
                    title = if (existingVentoyDrive != null) "Add to ${existingVentoyDrive.name}" else "Create Multi-Boot Drive",
                    badge = null,
                    subtitle = "Keep multiple OS ISOs in one bootable image",
                    highlighted = false,
                    onClick = onOpenVentoyForIso
                )
                TextButton(
                    onClick = { showOtherModes = !showOtherModes },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = if (showOtherModes) "Hide other modes" else "Other mount modes",
                        fontSize = 12.sp,
                        color = PocketColors.textSecondary
                    )
                }
            }

            AnimatedVisibility(visible = showOtherModes) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val modesToShow = when {
                        isVentoy -> listOf(MountMode.USB_RO)
                        isWindows && exceedsKernelCdLimit -> listOf(MountMode.CDROM, MountMode.USB_RO, MountMode.USB_RW)
                        isWindows -> listOf(MountMode.USB_RO, MountMode.USB_RW)
                        isLinux -> listOf(MountMode.CDROM, MountMode.USB_RW)
                        else -> options
                    }
                    modesToShow.forEach { mode ->
                        val isRec = !isWindows && !isLinux && !isVentoy && mode == recommendedMode
                        val isTruncatedCd = mode == MountMode.CDROM && exceedsKernelCdLimit
                        val icon = when (mode) {
                            MountMode.CDROM -> Icons.Rounded.Album
                            MountMode.USB_RO -> Icons.Rounded.Usb
                            else -> Icons.Rounded.Edit
                        }
                        MountActionCard(
                            icon = icon,
                            title = mode.friendlyTitle,
                            badge = when {
                                isTruncatedCd -> ">2.2 GB Cap"
                                isRec -> "Recommended"
                                else -> null
                            },
                            subtitle = if (isTruncatedCd) "Android truncates Virtual CD at 2.2 GB" else mode.helperText,
                            highlighted = isRec,
                            warning = isTruncatedCd,
                            onClick = { onSelectProtocol(mode, selectedLun, rememberForQuickMount) }
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(11.dp),
                color = PocketColors.surfaceInput,
                border = BorderStroke(0.5.dp, PocketColors.border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rememberForQuickMount = !rememberForQuickMount }
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remember mode for 1-tap mount", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                        Text("Next time, tap Mount on Drives to mount instantly", fontSize = 11.sp, color = PocketColors.textSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    PocketSwitch(checked = rememberForQuickMount, onCheckedChange = { rememberForQuickMount = it }, activeColor = PocketColors.accent)
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun MountActionCard(
    icon: ImageVector,
    title: String,
    badge: String?,
    subtitle: String,
    highlighted: Boolean,
    warning: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) PocketColors.accentBg else PocketColors.surfaceInput,
        border = BorderStroke(
            if (highlighted) 1.dp else 0.5.dp,
            when {
                warning -> PocketColors.warning
                highlighted -> PocketColors.accent
                else -> PocketColors.border
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (highlighted) PocketColors.accent else PocketColors.surfaceSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlighted) PocketColors.onAccent else PocketColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                    if (!badge.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = if (warning) PocketColors.warningBg else PocketColors.successBg
                        ) {
                            Text(
                                text = badge,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (warning) PocketColors.warning else PocketColors.success
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning) PocketColors.warning else PocketColors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun FirstLaunchFolderDialog(
    onConfirm: (String) -> Unit
) {
    val presets = listOf(
        "Downloads" to "/data/media/0/Download",
        "Create \"PocketBoot\" folder" to "/data/media/0/PocketBoot",
        "Create \"ISOs\" folder" to "/data/media/0/ISOs",
        "Custom folder path" to "CUSTOM"
    )
    var selectedOption by remember { mutableStateOf(presets[0].second) }
    var customPath by remember { mutableStateOf("/sdcard/MyISOs") }

    AlertDialog(
        onDismissRequest = {},
        containerColor = PocketColors.surfaceCard,
        title = { Text("Choose Default Folder", color = PocketColors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Where should PocketBoot save downloaded ISOs and blank drives? You can change this anytime in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PocketColors.textSecondary
                )
                presets.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = value },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedOption == value, onClick = { selectedOption = value })
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = PocketColors.textPrimary)
                    }
                }
                if (selectedOption == "CUSTOM") {
                    OutlinedTextField(
                        value = customPath,
                        onValueChange = { customPath = it },
                        label = { Text("Folder path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = if (selectedOption == "CUSTOM") {
                        UsbGadgetEngine.resolveDirectPath(customPath).ifBlank { "/data/media/0/Download" }
                    } else {
                        selectedOption
                    }
                    onConfirm(chosen)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PocketColors.accent,
                    contentColor = PocketColors.onAccent
                )
            ) {
                Text("Continue", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileInfoBottomSheet(
    file: ImageFile,
    mountedLun: Int?,
    osType: IsoOsType,
    hasRoot: Boolean,
    busy: Boolean,
    rememberedModeLabel: String?,
    onDismiss: () -> Unit,
    onOpenMountSheet: () -> Unit,
    onUnmountThis: (Int) -> Unit,
    onOpenWindowsBuilder: () -> Unit,
    onOpenLinuxBuilder: () -> Unit,
    onOpenVentoyManager: () -> Unit,
    onOpenImageExplorer: () -> Unit,
    onOpenDecompress: () -> Unit,
    onOpenOtgFlasherForFile: () -> Unit,
    onClearRememberedMode: () -> Unit,
    onRequestRename: () -> Unit,
    onRemoveFromList: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sha256 by remember { mutableStateOf<String?>(null) }
    var hashing by remember { mutableStateOf(false) }
    var bootReport by remember(file.path) { mutableStateOf<BootReadinessReport?>(null) }
    val isActive = mountedLun != null
    val isIso = file.name.endsWith(".iso", true)
    val isCompressed = osType == IsoOsType.COMPRESSED_IMAGE || IsoInspector.isCompressedImageName(file.name)

    LaunchedEffect(file.path, isCompressed) {
        bootReport = if (isCompressed) null else IsoInspector.inspectBootReadiness(context, file.path, file.sizeBytes)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            PocketSheetHeader(
                icon = when {
                    osType == IsoOsType.WINDOWS -> Icons.Rounded.Window
                    osType == IsoOsType.LINUX -> Icons.Rounded.Terminal
                    osType == IsoOsType.VENTOY_IMAGE -> Icons.Rounded.Layers
                    isCompressed -> Icons.Rounded.FolderOpen
                    isIso -> Icons.Rounded.Album
                    else -> Icons.Rounded.Storage
                },
                tint = if (isActive) PocketColors.success else PocketColors.accentSoft,
                iconBg = if (isActive) PocketColors.successBg else PocketColors.accentBg,
                title = file.name,
                subtitle = if (isActive) "Mounted · LUN $mountedLun" else "${osType.displayType} · ${UsbGadgetEngine.formatBytes(file.sizeBytes)}",
                subtitleMono = true
            )

            // Primary Action Row
            if (isCompressed) {
                Button(
                    onClick = onOpenDecompress,
                    enabled = !busy && hasRoot,
                    modifier = Modifier.fillMaxWidth().height(PocketTokens.PrimaryButtonHeight),
                    shape = RoundedCornerShape(PocketTokens.ButtonRadius),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text("Decompress to ${CompressedImageEngine.targetExtensionFor(file.name)}", fontWeight = FontWeight.SemiBold)
                }
            } else if (mountedLun != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onUnmountThis(mountedLun) },
                        enabled = !busy && hasRoot,
                        modifier = Modifier.weight(1.2f).height(PocketTokens.PrimaryButtonHeight),
                        shape = RoundedCornerShape(PocketTokens.ButtonRadius),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.danger, contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.Eject, null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Eject LUN $mountedLun", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = onOpenMountSheet,
                        enabled = !busy && hasRoot,
                        modifier = Modifier.weight(1f).height(PocketTokens.PrimaryButtonHeight),
                        shape = RoundedCornerShape(PocketTokens.ButtonRadius),
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, null, tint = PocketColors.textPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Switch", color = PocketColors.textPrimary, fontSize = 13.sp)
                    }
                }
            } else {
                Button(
                    onClick = onOpenMountSheet,
                    enabled = !busy && hasRoot,
                    modifier = Modifier.fillMaxWidth().height(PocketTokens.PrimaryButtonHeight),
                    shape = RoundedCornerShape(PocketTokens.ButtonRadius),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Icon(Icons.Rounded.Usb, null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(7.dp))
                    Text("Mount to PC", fontWeight = FontWeight.SemiBold)
                }
            }

            // OS-Specific Tool (shown cleanly without clutter)
            when (osType) {
                IsoOsType.WINDOWS -> {
                    MountActionCard(
                        icon = Icons.Rounded.Build,
                        title = "Windows USB Builder",
                        badge = "NTFS",
                        subtitle = "GPT or MBR with bypasses & Driver folder injection",
                        highlighted = false,
                        onClick = onOpenWindowsBuilder
                    )
                }
                IsoOsType.LINUX, IsoOsType.UTILITY -> {
                    MountActionCard(
                        icon = Icons.Rounded.Terminal,
                        title = "Linux & Tool USB Builder",
                        badge = "Live / Persist",
                        subtitle = "With Persistence (writable/persistence) or Standard FAT32/DD",
                        highlighted = false,
                        onClick = onOpenLinuxBuilder
                    )
                }
                IsoOsType.VENTOY_IMAGE -> {
                    MountActionCard(
                        icon = Icons.Rounded.Layers,
                        title = "Manage ISOs",
                        badge = null,
                        subtitle = "Add, move, or remove ISOs inside this drive",
                        highlighted = false,
                        onClick = onOpenVentoyManager
                    )
                }
                else -> {}
            }

            if (!isCompressed && file.name.endsWith(".img", ignoreCase = true)) {
                MountActionCard(
                    icon = Icons.Rounded.FolderOpen,
                    title = "Explore / Edit Files Inside .img",
                    badge = "On-Phone",
                    subtitle = "Mount Partition 1 on your phone to browse, import, export, or delete files",
                    highlighted = false,
                    onClick = onOpenImageExplorer
                )
            }

            if (hasRoot) {
                MountActionCard(
                    icon = Icons.Rounded.Usb,
                    title = "Flash to USB-C OTG Pen Drive",
                    badge = if (isCompressed) "Stream" else "Physical USB",
                    subtitle = if (isCompressed) "Decompresses on-the-fly directly onto a physical USB pen drive" else "Erase & write directly to a physical USB-C OTG drive with verification",
                    highlighted = false,
                    onClick = onOpenOtgFlasherForFile
                )
            }

            // Boot Readiness Inspector Card (hidden for compressed archives)
            if (!isCompressed) Surface(
                shape = RoundedCornerShape(12.dp),
                color = PocketColors.surfaceInput,
                border = BorderStroke(0.5.dp, if (bootReport?.isBootReady == true) PocketColors.success.copy(alpha = 0.6f) else PocketColors.border)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("BOOT READINESS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = PocketColors.textMuted)
                        val rpt = bootReport
                        if (rpt == null) {
                            Text("Inspecting...", style = PocketColors.monoStyle, color = PocketColors.textMuted)
                        } else {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = if (rpt.isBootReady) PocketColors.successBg else PocketColors.warningBg
                            ) {
                                Text(
                                    text = if (rpt.isBootReady) "Boot Ready" else "Data / Unformatted",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (rpt.isBootReady) PocketColors.success else PocketColors.warning
                                )
                            }
                        }
                    }
                    bootReport?.let { rpt ->
                        Text("${rpt.partitionScheme} · ${rpt.filesystem}", style = PocketColors.monoStyle, color = PocketColors.textPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "UEFI x64" to rpt.hasUefiX64,
                                "UEFI ARM64" to rpt.hasUefiArm64,
                                "Legacy BIOS" to rpt.hasLegacyBios
                            ).forEach { (lbl, ok) ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (ok) PocketColors.successBg else PocketColors.surfaceSubtle
                                ) {
                                    Text(
                                        text = if (ok) "✓ $lbl" else lbl,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (ok) PocketColors.success else PocketColors.textMuted
                                    )
                                }
                            }
                        }
                        Text(rpt.summaryNote, style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                    }
                }
            }

            if (rememberedModeLabel != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PocketColors.accentBg,
                    border = BorderStroke(0.5.dp, PocketColors.accent)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1-Tap Quick Mount: $rememberedModeLabel", style = MaterialTheme.typography.labelMedium, color = PocketColors.accentSoft)
                        TextButton(onClick = onClearRememberedMode, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                            Text("Clear", fontSize = 11.5.sp, color = PocketColors.textPrimary)
                        }
                    }
                }
            }

            // Compact Metadata Surface
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = PocketColors.surfaceInput,
                border = BorderStroke(0.5.dp, PocketColors.border)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfoRow("Path", file.path)
                    val sizeLine = if (file.isSparseSaved) {
                        "${UsbGadgetEngine.formatBytes(file.actualDiskBytes)} on disk · ${UsbGadgetEngine.formatBytes(file.sizeBytes)} virtual"
                    } else {
                        UsbGadgetEngine.formatBytes(file.sizeBytes)
                    }
                    InfoRow("Folder & Size", "${file.folderLabel} · $sizeLine")
                }
            }

            // Checksum Row
            val resolvedSha = sha256
            if (resolvedSha != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PocketColors.surfaceInput,
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            sha256 = UsbGadgetEngine.computeSha256(file.path)
                            hashing = false
                            if (sha256 == null) Toast.makeText(context, "Could not calculate SHA-256.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !hashing,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    if (hashing) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = PocketColors.accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Calculating SHA-256...", fontSize = 12.5.sp, color = PocketColors.textPrimary)
                    } else {
                        Icon(Icons.Rounded.Fingerprint, null, tint = PocketColors.textSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verify SHA-256", fontSize = 12.5.sp, color = PocketColors.textPrimary)
                    }
                }
            }

            PocketHairlineDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRequestRename,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Icon(Icons.Rounded.DriveFileRenameOutline, null, tint = PocketColors.textSecondary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rename", color = PocketColors.textPrimary, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onRemoveFromList,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Icon(Icons.Rounded.VisibilityOff, null, tint = PocketColors.textSecondary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", color = PocketColors.textPrimary, fontSize = 12.sp)
                }

                Button(
                    onClick = onRequestDelete,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.dangerBg, contentColor = PocketColors.danger)
                ) {
                    Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = PocketColors.textMuted)
        Text(value, style = PocketColors.monoStyle, color = PocketColors.textPrimary)
    }
}

@Composable
private fun HomeScreen(
    status: GadgetStatus,
    watchdogStatus: LinkWatchdogStatus,
    activeUsbBuild: UsbBuildState? = null,
    onCancelUsbBuild: () -> Unit = {},
    images: List<ImageFile>,
    isoTypes: Map<String, IsoOsType>,
    busy: Boolean,
    errorBanner: String?,
    onDismissError: () -> Unit,
    onDismissWatchdogBanner: () -> Unit,
    onViewLog: () -> Unit,
    onRetryRoot: () -> Unit,
    verifiedPaths: Set<String>,
    rememberedModes: Map<String, String>,
    onQuickMount: (ImageFile, MountMode) -> Unit,
    onRequestMount: (ImageFile) -> Unit,
    onUnmountSlot: (Int?) -> Unit,
    onForceReconnect: () -> Unit,
    onOpenCreateBlank: () -> Unit,
    onOpenCreateVentoy: () -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenOtgFlasher: () -> Unit = {},
    onOpenDecompress: (ImageFile) -> Unit = {},
    dualLunCombos: List<DualLunCombo> = emptyList(),
    onMountDualLunCombo: (DualLunCombo) -> Unit = {},
    onOpenDualLunCombos: () -> Unit = {},
    onTapFileCard: (ImageFile) -> Unit,
    freeSpaceBytes: Long = 0L
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortBy by rememberSaveable { mutableStateOf(SortBy.NAME) }
    var activeFilter by rememberSaveable { mutableStateOf(DriveFilter.ALL) }

    val filteredImages = remember(images, searchQuery, sortBy, activeFilter, isoTypes.toMap()) {
        val q = searchQuery.trim().lowercase()
        val byQuery = if (q.isEmpty()) images else images.filter {
            it.name.lowercase().contains(q) || it.folderLabel.lowercase().contains(q)
        }
        val byFilter = when (activeFilter) {
            DriveFilter.ALL -> byQuery
            DriveFilter.WINDOWS -> byQuery.filter { (isoTypes[it.path] ?: IsoInspector.quickGuessByName(it.name)) == IsoOsType.WINDOWS }
            DriveFilter.LINUX -> byQuery.filter { (isoTypes[it.path] ?: IsoInspector.quickGuessByName(it.name)) == IsoOsType.LINUX }
            DriveFilter.MULTIBOOT -> byQuery.filter { (isoTypes[it.path] ?: IsoInspector.quickGuessByName(it.name)) == IsoOsType.VENTOY_IMAGE }
            DriveFilter.IMAGES -> byQuery.filter { it.name.endsWith(".img", true) || IsoInspector.isCompressedImageName(it.name) }
        }
        when (sortBy) {
            SortBy.NAME -> byFilter.sortedBy { it.name.lowercase() }
            SortBy.SIZE -> byFilter.sortedByDescending { it.sizeBytes }
            SortBy.FOLDER -> byFilter.sortedBy { it.folderLabel.lowercase() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Animated Error Banner
        AnimatedVisibility(
            visible = !errorBanner.isNullOrBlank(),
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(150)) + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PocketColors.dangerBg,
                border = BorderStroke(1.dp, PocketColors.danger)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = PocketColors.danger, modifier = Modifier.size(19.dp))
                    Text(errorBanner.orEmpty(), style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        text = "View Log",
                        style = MaterialTheme.typography.labelMedium,
                        color = PocketColors.accentSoft,
                        modifier = Modifier.clickable {
                            onDismissError()
                            onViewLog()
                        }
                    )
                }
            }
        }

        // Live Background USB Build Progress Banner
        AnimatedVisibility(
            visible = activeUsbBuild != null,
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(150)) + shrinkVertically()
        ) {
            activeUsbBuild?.let { b ->
                val pct = (b.progress.progress * 100f).toInt().coerceIn(0, 100)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PocketColors.surfaceCard,
                    border = BorderStroke(1.dp, PocketColors.accent)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = PocketColors.accent
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (b.progress.isIndeterminate) "${b.kind.label} · ${b.progress.stage}" else "${b.kind.label} · $pct%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PocketColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = b.progress.detail.ifBlank { b.targetName },
                                    style = PocketColors.monoStyle,
                                    color = PocketColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelMedium,
                                color = PocketColors.danger,
                                modifier = Modifier.clickable { onCancelUsbBuild() }.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        if (b.progress.isIndeterminate) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
                                color = PocketColors.accent,
                                trackColor = PocketColors.surfaceSubtle
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { b.progress.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(999.dp)),
                                color = PocketColors.accent,
                                trackColor = PocketColors.surfaceSubtle
                            )
                        }
                    }
                }
            }
        }

        // Animated Hot-Plug / Watchdog Pill
        val alertText = when {
            status.isAnyMounted && watchdogStatus.cableDisconnected -> "Waiting for USB-C cable · plug in anytime"
            !watchdogStatus.lastEventMessage.isNullOrBlank() -> watchdogStatus.lastEventMessage
            else -> null
        }
        AnimatedVisibility(
            visible = alertText != null,
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(150)) + shrinkVertically()
        ) {
            val isDisconnect = watchdogStatus.cableDisconnected
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDisconnect) PocketColors.warningBg else PocketColors.accentBg,
                border = BorderStroke(1.dp, if (isDisconnect) PocketColors.warning else PocketColors.accent)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isDisconnect) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = if (isDisconnect) PocketColors.warning else PocketColors.success,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(alertText.orEmpty(), style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = PocketColors.textSecondary,
                        modifier = Modifier.size(16.dp).clickable { onDismissWatchdogBanner() }
                    )
                }
            }
        }

        // Rich Hero Connection Card (restored telemetry depth + gradient glow + spring animations)
        ConnectionStatusCard(
            status = status,
            watchdogStatus = watchdogStatus,
            busy = busy,
            onRetryRoot = onRetryRoot,
            onUnmountSlot = onUnmountSlot,
            onForceReconnect = onForceReconnect
        )

        // Elevated 2x2 Quick Action Tiles (spacious tap targets, zero text clipping on 360dp screens)
        if (status.hasRoot) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    CommandActionTile(
                        icon = Icons.Rounded.Layers,
                        label = "Multi-Boot",
                        subtitle = "ISO hub",
                        tint = PocketColors.violet,
                        iconBg = PocketColors.violetBg,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenCreateVentoy
                    )
                    CommandActionTile(
                        icon = Icons.Rounded.AddCircleOutline,
                        label = "Blank USB",
                        subtitle = "New .img",
                        tint = PocketColors.infoBlue,
                        iconBg = PocketColors.infoBlueBg,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenCreateBlank
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    CommandActionTile(
                        icon = Icons.Rounded.Usb,
                        label = "OTG Flash",
                        subtitle = "Pen drive",
                        tint = PocketColors.warning,
                        iconBg = PocketColors.warningBg,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenOtgFlasher
                    )
                    CommandActionTile(
                        icon = Icons.Rounded.FolderOpen,
                        label = "Import",
                        subtitle = "Browse files",
                        tint = PocketColors.success,
                        iconBg = PocketColors.successBg,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenBrowse
                    )
                }
            }

            // Dual-LUN Quick-Mount Combos Bar (Wave 3)
            Surface(
                modifier = Modifier.fillMaxWidth().bounceClick { onOpenDualLunCombos() },
                shape = RoundedCornerShape(13.dp),
                color = PocketColors.surfaceCard,
                border = BorderStroke(0.5.dp, PocketColors.border)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(PocketColors.accentBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Dual-LUN Combos", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text(
                                text = when {
                                    !status.multiLunSupported -> "Kernel lacks LUN 1 support · tap for details"
                                    dualLunCombos.isEmpty() -> "Pair LUN 0 + LUN 1 for 1-tap atomic mount"
                                    else -> "${dualLunCombos.size} saved combo(s) · single USB rebind"
                                },
                                fontSize = 11.sp,
                                color = PocketColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (dualLunCombos.isEmpty()) "Create" else "Manage",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PocketColors.accentSoft
                        )
                    }
                    if (status.multiLunSupported && dualLunCombos.isNotEmpty()) {
                        val availablePaths = remember(images) { images.map { UsbGadgetEngine.resolveDirectPath(it.path) }.toSet() }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items(dualLunCombos, key = { it.id }) { combo ->
                                val hasBoth = UsbGadgetEngine.resolveDirectPath(combo.lun0Path) in availablePaths &&
                                    UsbGadgetEngine.resolveDirectPath(combo.lun1Path) in availablePaths
                                val canQuickMount = !busy && activeUsbBuild == null && hasBoth
                                Surface(
                                    modifier = Modifier.bounceClick(enabled = !busy && activeUsbBuild == null) {
                                        if (hasBoth) onMountDualLunCombo(combo) else onOpenDualLunCombos()
                                    },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (hasBoth) PocketColors.accentBg else PocketColors.warningBg,
                                    border = BorderStroke(
                                        0.75.dp,
                                        if (canQuickMount) PocketColors.accent.copy(alpha = 0.6f)
                                        else if (!hasBoth) PocketColors.warning
                                        else PocketColors.border
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (hasBoth) Icons.Rounded.Bolt else Icons.Rounded.WarningAmber,
                                            contentDescription = null,
                                            tint = if (hasBoth) PocketColors.accentSoft else PocketColors.warning,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (hasBoth) combo.name else "${combo.name} (missing)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (hasBoth) PocketColors.accentSoft else PocketColors.warning,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Drives Section
        Column(
            modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PocketSectionHeader(
                    title = "Your Drives · ${filteredImages.size}",
                    modifier = Modifier.weight(1f)
                )
                if (images.size >= 2) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = PocketColors.surfaceCard,
                        border = BorderStroke(0.75.dp, PocketColors.border),
                        modifier = Modifier.bounceClick {
                            val nextIdx = (SortBy.entries.indexOf(sortBy) + 1) % SortBy.entries.size
                            sortBy = SortBy.entries[nextIdx]
                        }
                    ) {
                        Text(
                            text = "Sort: ${sortBy.label}",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = PocketColors.accentSoft
                        )
                    }
                }
            }

            if (images.size >= 4) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search drives...", fontSize = 13.sp, color = PocketColors.textMuted) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp)) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, "Clear", tint = PocketColors.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PocketColors.accent,
                        unfocusedBorderColor = PocketColors.border,
                        focusedContainerColor = PocketColors.surfaceCard,
                        unfocusedContainerColor = PocketColors.surfaceCard,
                        focusedTextColor = PocketColors.textPrimary,
                        unfocusedTextColor = PocketColors.textPrimary
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    DriveFilter.entries.forEach { f ->
                        val selected = activeFilter == f
                        val chipBg by animateColorAsState(
                            if (selected) PocketColors.accentBg else PocketColors.surfaceCard,
                            tween(180),
                            label = "fltBg"
                        )
                        val chipBorder by animateColorAsState(
                            if (selected) PocketColors.accent else PocketColors.border,
                            tween(180),
                            label = "fltBorder"
                        )
                        val chipText by animateColorAsState(
                            if (selected) PocketColors.accentSoft else PocketColors.textSecondary,
                            tween(180),
                            label = "fltText"
                        )
                        Surface(
                            modifier = Modifier.bounceClick { activeFilter = f },
                            shape = RoundedCornerShape(999.dp),
                            color = chipBg,
                            border = BorderStroke(if (selected) 1.dp else 0.75.dp, chipBorder)
                        ) {
                            Text(
                                text = f.label,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = chipText
                            )
                        }
                    }
                }
            }

            if (filteredImages.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = PocketColors.surfaceCard,
                    border = BorderStroke(1.dp, PocketColors.border)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(PocketColors.accentBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Usb, null, tint = PocketColors.accentSoft, modifier = Modifier.size(26.dp))
                        }
                        Text(
                            text = if (images.isEmpty()) "No Drive Files Yet" else "No Matching Drives",
                            style = MaterialTheme.typography.titleMedium,
                            color = PocketColors.textPrimary
                        )
                        Text(
                            text = if (images.isEmpty()) {
                                "Import an ISO, create a Multi-Boot drive, or grab an OS from Catalog."
                            } else {
                                "Try clearing your search or switching the filter."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textSecondary
                        )
                    }
                }
            } else {
                // Distinct Elevated Drive Cards (v3.6.3 richness + OS color coding + smooth animations)
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    filteredImages.forEach { img ->
                        val mountedLun = status.mountedLunFor(img.path)
                        val isActive = mountedLun != null
                        val isIso = img.name.endsWith(".iso", ignoreCase = true)
                        val osType = isoTypes[img.path] ?: IsoInspector.quickGuessByName(img.name)

                        val (osIcon, osTint, osBg) = when {
                            isActive -> Triple(
                                when {
                                    osType == IsoOsType.WINDOWS -> Icons.Rounded.Window
                                    osType == IsoOsType.LINUX -> Icons.Rounded.Terminal
                                    osType == IsoOsType.VENTOY_IMAGE -> Icons.Rounded.Layers
                                    isIso -> Icons.Rounded.Album
                                    else -> Icons.Rounded.Storage
                                },
                                PocketColors.success,
                                PocketColors.successBg
                            )
                            osType == IsoOsType.WINDOWS -> Triple(Icons.Rounded.Window, PocketColors.infoBlue, PocketColors.infoBlueBg)
                            osType == IsoOsType.LINUX -> Triple(Icons.Rounded.Terminal, PocketColors.success, PocketColors.successBg)
                            osType == IsoOsType.VENTOY_IMAGE -> Triple(Icons.Rounded.Layers, PocketColors.violet, PocketColors.violetBg)
                            osType == IsoOsType.COMPRESSED_IMAGE -> Triple(Icons.Rounded.FolderOpen, PocketColors.infoBlue, PocketColors.infoBlueBg)
                            isIso -> Triple(Icons.Rounded.Album, PocketColors.accentSoft, PocketColors.accentBg)
                            else -> Triple(Icons.Rounded.Storage, PocketColors.warning, PocketColors.warningBg)
                        }

                        val cardBorder by animateColorAsState(
                            if (isActive) PocketColors.success else PocketColors.border,
                            tween(220),
                            label = "cardBorder"
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick { onTapFileCard(img) },
                            shape = RoundedCornerShape(14.dp),
                            color = PocketColors.surfaceCard,
                            border = BorderStroke(if (isActive) 1.5.dp else 1.dp, cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isActive) PocketColors.successBg.copy(alpha = 0.25f) else PocketColors.surfaceCard
                                    )
                                    .padding(horizontal = 13.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(osBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = osIcon,
                                        contentDescription = null,
                                        tint = osTint,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = img.name,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = PocketColors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (osType == IsoOsType.WINDOWS || osType == IsoOsType.LINUX || osType == IsoOsType.VENTOY_IMAGE || osType == IsoOsType.COMPRESSED_IMAGE) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = osBg) {
                                                Text(
                                                    text = osType.badgeLabel,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = osTint
                                                )
                                            }
                                        }
                                        if (verifiedPaths.contains(img.path)) {
                                            Icon(
                                                imageVector = Icons.Rounded.VerifiedUser,
                                                contentDescription = "Verified SHA-256",
                                                tint = PocketColors.success,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    val lunPrefix = if (mountedLun != null) "LUN $mountedLun · " else ""
                                    val sizeDisplay = if (img.isSparseSaved) {
                                        "${UsbGadgetEngine.formatBytes(img.actualDiskBytes)} on disk / ${UsbGadgetEngine.formatBytes(img.sizeBytes)}"
                                    } else {
                                        UsbGadgetEngine.formatBytes(img.sizeBytes)
                                    }
                                    Text(
                                        text = "$lunPrefix$sizeDisplay · ${img.folderLabel}",
                                        style = PocketColors.monoStyle,
                                        color = if (isActive) PocketColors.success else PocketColors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (mountedLun != null) {
                                    Surface(
                                        modifier = Modifier.bounceClick(enabled = !busy) { onUnmountSlot(mountedLun) },
                                        shape = RoundedCornerShape(999.dp),
                                        color = PocketColors.dangerBg,
                                        border = BorderStroke(1.dp, PocketColors.danger)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Rounded.Eject, null, tint = PocketColors.danger, modifier = Modifier.size(14.dp))
                                            Text(
                                                text = "Eject",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = PocketColors.danger
                                            )
                                        }
                                    }
                                } else if (osType == IsoOsType.COMPRESSED_IMAGE || IsoInspector.isCompressedImageName(img.name)) {
                                    Surface(
                                        modifier = Modifier.bounceClick(enabled = !busy && status.hasRoot) {
                                            onOpenDecompress(img)
                                        },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (!busy && status.hasRoot) PocketColors.infoBlueBg else PocketColors.surfaceSubtle,
                                        border = BorderStroke(0.75.dp, if (!busy && status.hasRoot) PocketColors.infoBlue else PocketColors.border)
                                    ) {
                                        Text(
                                            text = "Extract",
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (!busy && status.hasRoot) PocketColors.infoBlue else PocketColors.textMuted
                                        )
                                    }
                                } else {
                                    val savedMode = rememberedModes[img.path]?.let { mName ->
                                        runCatching { MountMode.valueOf(mName) }.getOrNull()?.takeIf { it != MountMode.AUTO }
                                    }
                                    Surface(
                                        modifier = Modifier.bounceClick(enabled = !busy && status.hasRoot) {
                                            if (savedMode != null) onQuickMount(img, savedMode) else onRequestMount(img)
                                        },
                                        shape = RoundedCornerShape(999.dp),
                                        color = if (!busy && status.hasRoot) PocketColors.accent else PocketColors.surfaceSubtle
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = if (savedMode != null) 11.dp else 14.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            if (savedMode != null) {
                                                Icon(
                                                    Icons.Rounded.Bolt,
                                                    "1-Tap Quick Mount",
                                                    tint = if (!busy && status.hasRoot) PocketColors.onAccent else PocketColors.textMuted,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Text(
                                                text = "Mount",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (!busy && status.hasRoot) PocketColors.onAccent else PocketColors.textMuted
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

        if (status.hasRoot && freeSpaceBytes > 0L) {
            val drivesUsedBytes = remember(images) { images.sumOf { it.actualDiskBytes } }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PocketColors.surfaceCard,
                border = BorderStroke(0.5.dp, PocketColors.border)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Storage, null, tint = PocketColors.accentSoft, modifier = Modifier.size(15.dp))
                    Text(
                        text = "${UsbGadgetEngine.formatBytes(freeSpaceBytes)} free · ${UsbGadgetEngine.formatBytes(drivesUsedBytes)} in ${images.size} drive(s)",
                        style = PocketColors.monoStyle,
                        color = PocketColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CommandActionTile(
    icon: ImageVector,
    label: String,
    subtitle: String,
    tint: Color,
    iconBg: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.bounceClick(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = PocketColors.surfaceCard,
        border = BorderStroke(1.dp, PocketColors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PocketColors.surfaceCard)
                .padding(horizontal = 10.dp, vertical = 11.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = PocketColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = PocketColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    status: GadgetStatus,
    watchdogStatus: LinkWatchdogStatus,
    busy: Boolean,
    onRetryRoot: () -> Unit,
    onUnmountSlot: (Int?) -> Unit,
    onForceReconnect: () -> Unit
) {
    var techExpanded by remember { mutableStateOf(false) }
    var ioBytesPerSec by remember { mutableStateOf(0L) }
    var peakBytesPerSec by remember { mutableStateOf(0L) }
    var sessionTransferredBytes by remember { mutableStateOf(0L) }
    var hasByteCounter by remember { mutableStateOf(false) }
    var usbIrqsPerSec by remember { mutableStateOf(0L) }
    var peakIrqsPerSec by remember { mutableStateOf(0L) }
    var sessionUsbIrqs by remember { mutableStateOf(0L) }
    var pcReadingActive by remember { mutableStateOf(false) }
    var liveLinkState by remember(status.usbLinkState) { mutableStateOf(status.usbLinkState) }
    var liveLinkSpeed by remember(status.usbLinkSpeed) { mutableStateOf(status.usbLinkSpeed) }

    LaunchedEffect(status.isAnyMounted, status.mountedFile, status.secondaryMountedFile) {
        if (!status.isAnyMounted) {
            ioBytesPerSec = 0L
            peakBytesPerSec = 0L
            sessionTransferredBytes = 0L
            hasByteCounter = false
            usbIrqsPerSec = 0L
            peakIrqsPerSec = 0L
            sessionUsbIrqs = 0L
            pcReadingActive = false
            return@LaunchedEffect
        }
        ioBytesPerSec = 0L
        peakBytesPerSec = 0L
        sessionTransferredBytes = 0L
        hasByteCounter = false
        usbIrqsPerSec = 0L
        peakIrqsPerSec = 0L
        sessionUsbIrqs = 0L
        var baselineBytes = -1L
        var prevBytes = -1L
        var baselineIrqs = -1L
        var prevIrqs = -1L
        var prevCpu = -1L
        var prevMs = System.currentTimeMillis()
        while (isActive) {
            if (busy) { delay(800L); continue }
            val sample = UsbGadgetEngine.readUsbActivitySample()
            val nowMs = System.currentTimeMillis()
            if (sample.linkState.isNotBlank()) liveLinkState = sample.linkState
            if (sample.speed.isNotBlank()) liveLinkSpeed = sample.speed

            val dt = (nowMs - prevMs).coerceAtLeast(1L)

            if (sample.ioBytesTotal > 0L) {
                hasByteCounter = true
                if (baselineBytes < 0L) baselineBytes = sample.ioBytesTotal
                if (sample.ioBytesTotal >= baselineBytes) {
                    sessionTransferredBytes = sample.ioBytesTotal - baselineBytes
                }
            }
            var byteDelta = 0L
            if (prevBytes >= 0L && sample.ioBytesTotal >= prevBytes) {
                byteDelta = sample.ioBytesTotal - prevBytes
                val rate = (byteDelta * 1000L) / dt
                ioBytesPerSec = rate
                if (rate > peakBytesPerSec) peakBytesPerSec = rate
            } else {
                ioBytesPerSec = 0L
            }

            var irqDelta = 0L
            if (sample.usbIrqCount > 0L) {
                if (baselineIrqs < 0L) baselineIrqs = sample.usbIrqCount
                if (sample.usbIrqCount >= baselineIrqs) {
                    sessionUsbIrqs = sample.usbIrqCount - baselineIrqs
                }
                if (prevIrqs >= 0L && sample.usbIrqCount >= prevIrqs) {
                    irqDelta = sample.usbIrqCount - prevIrqs
                    val irqRate = (irqDelta * 1000L) / dt
                    usbIrqsPerSec = irqRate
                    if (irqRate > peakIrqsPerSec) peakIrqsPerSec = irqRate
                }
            }
            val cpuDelta = if (prevCpu >= 0L && sample.threadCpuTicks >= prevCpu) sample.threadCpuTicks - prevCpu else 0L
            pcReadingActive = sample.kernelReadingActive || byteDelta > 4096L || cpuDelta > 0L || irqDelta > 25L

            prevBytes = sample.ioBytesTotal
            prevIrqs = sample.usbIrqCount
            prevCpu = sample.threadCpuTicks
            prevMs = nowMs
            delay(1500L)
        }
    }

    val cardStateKey = when {
        !status.hasRoot -> 0
        status.isAnyMounted -> 2
        else -> 1
    }

    AnimatedContent(
        targetState = cardStateKey,
        transitionSpec = {
            (fadeIn(tween(240)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow))) togetherWith
                (fadeOut(tween(160)) + shrinkVertically())
        },
        label = "statusHeroTransition"
    ) { stateMode ->
        when (stateMode) {
            0 -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = PocketColors.surfaceCard,
                    border = BorderStroke(1.5.dp, PocketColors.warning)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PocketColors.warningBg.copy(alpha = 0.28f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(PocketColors.warningBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.UsbOff, null, tint = PocketColors.warning, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text("Root Access Required", style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary)
                                Text("Grant root in Magisk, KernelSU, or APatch to emulate USB drives.", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                            }
                        }
                        Button(
                            onClick = onRetryRoot,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PocketColors.warning, contentColor = PocketColors.onBrightSurface)
                        ) {
                            Text("Check Root Again", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Rich Standby Hardware Hero (brings back v3.6.3 presence + sleek compact telemetry)
            1 -> {
                val isLinked = status.usbLinkState.equals("configured", true)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        .bounceClick { techExpanded = !techExpanded },
                    shape = RoundedCornerShape(16.dp),
                    color = PocketColors.surfaceCard,
                    border = BorderStroke(1.dp, PocketColors.border)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PocketColors.surfaceCard)
                            .padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PulsingStatusDot(
                                    color = if (isLinked) PocketColors.success else PocketColors.accentSoft,
                                    pulsing = isLinked,
                                    dotSize = 7.dp
                                )
                                Text(
                                    text = "READY TO MOUNT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.9.sp,
                                    color = PocketColors.accentSoft
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isLinked) PocketColors.successBg else PocketColors.surfaceSubtle
                                ) {
                                    Text(
                                        text = if (isLinked) "PC Linked" else "Unplugged",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isLinked) PocketColors.success else PocketColors.textSecondary
                                    )
                                }
                                Icon(
                                    imageVector = if (techExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = "Tech Details",
                                    tint = PocketColors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Compact 2-Pill Status Strip (full hardware telemetry tucked cleanly under Details)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, PocketColors.border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Usb, null, tint = PocketColors.accentSoft, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = if (status.udcName.isNotBlank()) "USB-C Ready" else "No UDC",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PocketColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, PocketColors.border)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Rounded.Bolt, null, tint = if (isLinked) PocketColors.success else PocketColors.accentSoft, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = if (isLinked) status.friendlySpeedLabel else "Hot-Plug Ready",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isLinked) PocketColors.success else PocketColors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = techExpanded,
                            enter = fadeIn(tween(180)) + expandVertically(),
                            exit = fadeOut(tween(140)) + shrinkVertically()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, PocketColors.border)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    TelemetryLine(
                                        label = "Hardware Port",
                                        value = if (status.udcName.isNotBlank()) "USB-C (${status.udcName})" else "Not detected",
                                        valueColor = PocketColors.textPrimary
                                    )
                                    TelemetryLine(
                                        label = "Link & Mode",
                                        value = if (isLinked) status.friendlySpeedLabel else "Hot-plug auto-connect ready",
                                        valueColor = if (isLinked) PocketColors.success else PocketColors.textSecondary
                                    )
                                    TelemetryLine(
                                        label = "ConfigFS",
                                        value = if (status.configFsReady) "${status.gadgetPath} · Multi-LUN=${if (status.multiLunSupported) "Yes" else "No"}" else "Unavailable",
                                        valueColor = PocketColors.textSecondary
                                    )
                                    TelemetryLine(
                                        label = "USB Daemon",
                                        value = "sys.usb.config=${status.activeUsbConfig.ifBlank { "none" }}",
                                        valueColor = PocketColors.textSecondary
                                    )
                                    TelemetryLine(
                                        label = "Security / Root",
                                        value = "${status.rootManager} · SELinux ${status.selinuxMode}",
                                        valueColor = PocketColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Rich Active Emerald Hero Card when MOUNTED (Single or Dual LUN)
            else -> {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
                    shape = RoundedCornerShape(16.dp),
                    color = PocketColors.surfaceCard,
                    border = BorderStroke(1.5.dp, PocketColors.success)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PocketColors.successBg.copy(alpha = 0.28f))
                            .padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                PulsingStatusDot(color = PocketColors.success, pulsing = true, dotSize = 7.dp)
                                Text(
                                    text = if (status.isMounted && status.isSecondaryMounted) "DUAL-LUN MOUNTED" else "MOUNTED TO PC",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.9.sp,
                                    color = PocketColors.success
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.bounceClick(enabled = !busy) { onForceReconnect() },
                                    shape = RoundedCornerShape(8.dp),
                                    color = PocketColors.surfaceSubtle,
                                    border = BorderStroke(0.5.dp, PocketColors.border)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Sync, "Force USB Re-Connect", tint = PocketColors.accentSoft, modifier = Modifier.size(13.dp))
                                        Text("Reconnect", style = MaterialTheme.typography.labelSmall, color = PocketColors.textPrimary)
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { techExpanded = !techExpanded }
                                        .padding(horizontal = 5.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text("Details", style = MaterialTheme.typography.labelSmall, color = PocketColors.textSecondary)
                                    Icon(
                                        imageVector = if (techExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = "Toggle details",
                                        tint = PocketColors.textSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        if (status.isMounted) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = status.mountedFile.substringAfterLast('/'),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontSize = 17.sp,
                                        color = PocketColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "LUN 0 · ${UsbGadgetEngine.formatBytes(status.mountedSizeBytes)} · ${status.activeModeLabel}",
                                        style = PocketColors.monoStyle,
                                        color = PocketColors.textSecondary
                                    )
                                }
                                if (status.isSecondaryMounted) {
                                    TextButton(onClick = { onUnmountSlot(0) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                        Text("Eject 0", fontSize = 12.sp, color = PocketColors.danger)
                                    }
                                }
                            }
                        }

                        if (status.isSecondaryMounted) {
                            PocketHairlineDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = status.secondaryMountedFile.substringAfterLast('/'),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PocketColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "LUN 1 · ${UsbGadgetEngine.formatBytes(status.secondaryMountedSizeBytes)} · ${status.activeSecondaryModeLabel}",
                                        style = PocketColors.monoStyle,
                                        color = PocketColors.textSecondary
                                    )
                                }
                                if (status.isMounted) {
                                    TextButton(onClick = { onUnmountSlot(1) }, enabled = !busy, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                        Text("Eject 1", fontSize = 12.sp, color = PocketColors.danger)
                                    }
                                }
                            }
                        }

                        // Live Hardware Telemetry Box
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = PocketColors.surfaceInput,
                            border = BorderStroke(0.5.dp, PocketColors.border)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                TelemetryLine("USB Protocol", status.activeProtocolLabel, PocketColors.success)
                                val isConfiguredLive = liveLinkState.equals("configured", true)
                                val liveSpeedLabel = status.copy(usbLinkState = liveLinkState, usbLinkSpeed = liveLinkSpeed).friendlySpeedLabel
                                TelemetryLine(
                                    label = "Link Speed",
                                    value = liveSpeedLabel,
                                    valueColor = if (isConfiguredLive) PocketColors.success else PocketColors.warning
                                )
                                val activityLabel = when {
                                    !isConfiguredLive -> "Waiting for PC host"
                                    ioBytesPerSec >= 1024L -> "PC Transferring · ${FastIsoDownloader.formatSpeed(ioBytesPerSec)}"
                                    pcReadingActive && usbIrqsPerSec >= 20L -> "PC Transferring · $usbIrqsPerSec irq/s"
                                    pcReadingActive -> "PC Actively Reading"
                                    else -> "Connected · Idle"
                                }
                                TelemetryLine(
                                    label = "USB-C Activity",
                                    value = activityLabel,
                                    valueColor = when {
                                        !isConfiguredLive -> PocketColors.warning
                                        pcReadingActive || ioBytesPerSec >= 1024L -> PocketColors.infoBlue
                                        else -> PocketColors.success
                                    }
                                )
                                if (isConfiguredLive || sessionTransferredBytes > 0L || sessionUsbIrqs > 0L) {
                                    val statsValue = if (hasByteCounter || sessionTransferredBytes >= 1024L || peakBytesPerSec >= 1024L) {
                                        "Session ${UsbGadgetEngine.formatBytes(sessionTransferredBytes)} · Peak ${FastIsoDownloader.formatSpeed(peakBytesPerSec)}"
                                    } else {
                                        "USB IRQs: $sessionUsbIrqs · Peak $peakIrqsPerSec/s"
                                    }
                                    TelemetryLine(
                                        label = "Transfer Stats",
                                        value = statsValue,
                                        valueColor = PocketColors.textSecondary
                                    )
                                }

                                AnimatedVisibility(
                                    visible = techExpanded,
                                    enter = fadeIn(tween(180)) + expandVertically(),
                                    exit = fadeOut(tween(140)) + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(top = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        PocketHairlineDivider()
                                        Spacer(modifier = Modifier.height(2.dp))
                                        TelemetryLine("Hardware Port", status.udcName.ifBlank { "None" }, PocketColors.textPrimary)
                                        TelemetryLine("Keep-Alive", if (watchdogStatus.serviceActive) "Active · ${watchdogStatus.recoveryCount} recoveries" else "Standby", PocketColors.textSecondary)
                                        TelemetryLine("LUN 0 Flags", if (status.isMounted) "ro=${if (status.isReadOnly) 1 else 0}, cdrom=${if (status.isCdrom) 1 else 0}" else "Empty", PocketColors.textSecondary)
                                        if (status.isSecondaryMounted) {
                                            TelemetryLine("LUN 1 Flags", "ro=${if (status.secondaryIsReadOnly) 1 else 0}, cdrom=${if (status.secondaryIsCdrom) 1 else 0}", PocketColors.textSecondary)
                                        }
                                    }
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .bounceClick(enabled = !busy) { onUnmountSlot(null) },
                            shape = RoundedCornerShape(12.dp),
                            color = PocketColors.danger
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Eject, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(7.dp))
                                Text(
                                    text = if (status.isMounted && status.isSecondaryMounted) "Eject All USB Drives" else "Eject USB Drive",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryLine(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = PocketColors.textMuted)
        Text(
            text = value,
            style = PocketColors.monoStyle,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DualLunCombosSheet(
    combos: List<DualLunCombo>,
    images: List<ImageFile>,
    multiLunSupported: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onMountCombo: (DualLunCombo) -> Unit,
    onSaveCombo: (DualLunCombo) -> Unit,
    onDeleteCombo: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingCombo by remember { mutableStateOf<DualLunCombo?>(null) }
    val availablePathSet = remember(images) { images.map { UsbGadgetEngine.resolveDirectPath(it.path) }.toSet() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            PocketSheetHeader(
                icon = Icons.Rounded.SwapHoriz,
                tint = PocketColors.accentSoft,
                iconBg = PocketColors.accentBg,
                title = "Dual-LUN Quick-Mount Combos",
                subtitle = "Mount two images (LUN 0 + LUN 1) in a single USB rebind"
            )

            if (!multiLunSupported) {
                Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.warningBg, border = BorderStroke(0.75.dp, PocketColors.warning)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.warning, modifier = Modifier.size(18.dp))
                        Text(
                            "Your phone kernel reported single-LUN only. Dual-LUN mounting requires a kernel that allows creating mass_storage/lun.1.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textPrimary
                        )
                    }
                }
            }

            Button(
                onClick = { showCreateDialog = true },
                enabled = images.size >= 2,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
            ) {
                Icon(Icons.Rounded.AddCircleOutline, null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (images.size < 2) "Add at least 2 images to create a combo" else "New Dual-LUN Combo", fontWeight = FontWeight.SemiBold)
            }

            if (combos.isEmpty()) {
                Surface(shape = RoundedCornerShape(12.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.5.dp, PocketColors.border)) {
                    Text(
                        "No saved combos yet. Example: Pair Windows 11 ISO on LUN 0 (CD-ROM) with a Writable Drivers/Tools .img on LUN 1.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    combos.forEach { combo ->
                        val l0Name = combo.lun0Path.substringAfterLast('/')
                        val l1Name = combo.lun1Path.substringAfterLast('/')
                        val m0Label = runCatching { MountMode.valueOf(combo.lun0Mode).shortLabel }.getOrDefault("Auto")
                        val m1Label = runCatching { MountMode.valueOf(combo.lun1Mode).shortLabel }.getOrDefault("USB RW")
                        val l0Missing = UsbGadgetEngine.resolveDirectPath(combo.lun0Path) !in availablePathSet
                        val l1Missing = UsbGadgetEngine.resolveDirectPath(combo.lun1Path) !in availablePathSet
                        val anyMissing = l0Missing || l1Missing
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PocketColors.surfaceInput,
                            border = BorderStroke(0.5.dp, if (anyMissing) PocketColors.warning else PocketColors.border)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(combo.name, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "LUN 0 ($m0Label): $l0Name" + if (l0Missing) " [Missing]" else "",
                                        fontSize = 11.sp,
                                        color = if (l0Missing) PocketColors.warning else PocketColors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "LUN 1 ($m1Label): $l1Name" + if (l1Missing) " [Missing]" else "",
                                        fontSize = 11.sp,
                                        color = if (l1Missing) PocketColors.warning else PocketColors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = { onMountCombo(combo) },
                                    enabled = !busy && multiLunSupported && !anyMissing,
                                    shape = RoundedCornerShape(9.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                                ) {
                                    Text("Mount", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                                if (images.size >= 2) {
                                    IconButton(
                                        onClick = {
                                            editingCombo = combo
                                            showCreateDialog = true
                                        },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(Icons.Rounded.Edit, "Edit combo", tint = PocketColors.textSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { onDeleteCombo(combo.id) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, "Delete combo", tint = PocketColors.danger, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showCreateDialog && images.size >= 2) {
        val editTarget = editingCombo
        var comboName by remember(editTarget?.id) { mutableStateOf(editTarget?.name.orEmpty()) }
        var lun0Path by remember(editTarget?.id) {
            mutableStateOf(
                editTarget?.lun0Path?.takeIf { UsbGadgetEngine.resolveDirectPath(it) in availablePathSet } ?: images[0].path
            )
        }
        var lun1Path by remember(editTarget?.id) {
            mutableStateOf(
                editTarget?.lun1Path?.takeIf { UsbGadgetEngine.resolveDirectPath(it) in availablePathSet } ?: images[1].path
            )
        }
        var lun0Mode by remember(editTarget?.id) {
            mutableStateOf(runCatching { MountMode.valueOf(editTarget?.lun0Mode.orEmpty()) }.getOrDefault(MountMode.AUTO))
        }
        var lun1Mode by remember(editTarget?.id) {
            mutableStateOf(runCatching { MountMode.valueOf(editTarget?.lun1Mode.orEmpty()) }.getOrDefault(MountMode.USB_RW))
        }
        val validationErr = UsbGadgetEngine.validateDualLunComboPaths(lun0Path, lun1Path)
        val canSave = comboName.trim().isNotEmpty() && validationErr == null

        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                editingCombo = null
            },
            containerColor = PocketColors.surfaceCard,
            title = { Text(if (editTarget != null) "Edit Dual-LUN Combo" else "Create Dual-LUN Combo", color = PocketColors.textPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = comboName,
                        onValueChange = { comboName = it.take(32) },
                        label = { Text("Combo Name") },
                        placeholder = { Text("e.g., Win11 + Drivers") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("LUN 0 (Primary Boot Drive · ${images.size} files)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 156.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        images.forEach { img ->
                            val sel = lun0Path == img.path
                            Surface(
                                modifier = Modifier.fillMaxWidth().bounceClick { lun0Path = img.path },
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                            ) {
                                Text(img.name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 11.5.sp, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(MountMode.AUTO, MountMode.CDROM, MountMode.USB_RW).forEach { m ->
                            val sel = lun0Mode == m
                            Surface(
                                modifier = Modifier.weight(1f).bounceClick { lun0Mode = m },
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                    Text(m.shortLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary)
                                }
                            }
                        }
                    }

                    Text("LUN 1 (Secondary Data / Tools Drive · ${images.size} files)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 156.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        images.forEach { img ->
                            val sel = lun1Path == img.path
                            Surface(
                                modifier = Modifier.fillMaxWidth().bounceClick { lun1Path = img.path },
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                            ) {
                                Text(img.name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 11.5.sp, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(MountMode.USB_RW, MountMode.USB_RO, MountMode.CDROM).forEach { m ->
                            val sel = lun1Mode == m
                            Surface(
                                modifier = Modifier.weight(1f).bounceClick { lun1Mode = m },
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                    Text(m.shortLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary)
                                }
                            }
                        }
                    }

                    if (validationErr != null) {
                        Text(validationErr, fontSize = 11.sp, color = PocketColors.danger)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = comboName.trim()
                        val targetId = editTarget?.id ?: "combo_${System.currentTimeMillis()}"
                        showCreateDialog = false
                        editingCombo = null
                        onSaveCombo(
                            DualLunCombo(
                                id = targetId,
                                name = trimmed,
                                lun0Path = lun0Path,
                                lun0Mode = lun0Mode.name,
                                lun1Path = lun1Path,
                                lun1Mode = lun1Mode.name
                            )
                        )
                    },
                    enabled = canSave,
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) { Text(if (editTarget != null) "Update Combo" else "Save Combo", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    editingCombo = null
                }) { Text("Cancel", color = PocketColors.textSecondary) }
            }
        )
    }
}
