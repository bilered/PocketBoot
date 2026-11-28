package com.pocketboot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

@Composable
private fun SettingsIconBadge(
    icon: ImageVector,
    tint: Color,
    bg: Color
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    status: GadgetStatus,
    settings: AppSettings,
    logs: List<LogEntry>,
    forceExpandLogs: Boolean,
    isOperationActive: Boolean = false,
    onUpdateSettings: (AppSettings) -> Unit,
    onResetNormalUsb: () -> Unit,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dirCounts = remember { mutableStateMapOf<String, Int>() }
    var showAddDirDialog by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var showDiagnosticsSheet by remember { mutableStateOf(false) }
    var showActivityLogSheet by remember(forceExpandLogs) { mutableStateOf(forceExpandLogs) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    var showDlSpeedDialog by remember { mutableStateOf(false) }
    var ventoyInfo by remember { mutableStateOf(VentoyImageBuilder.getInstalledVentoyInfo(context)) }
    var updatingVentoy by remember { mutableStateOf(false) }
    var ventoyUpdateText by remember { mutableStateOf("") }
    var tempStats by remember { mutableStateOf(TempStorageStats()) }
    var cleaningTemp by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var availableRelease by remember { mutableStateOf<AppReleaseInfo?>(null) }
    var showDriverPackSheet by remember { mutableStateOf(false) }
    var driverPackCount by remember { mutableIntStateOf(0) }
    var driverPackRefreshNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(settings.defaultDir, driverPackRefreshNonce) {
        driverPackCount = DriverPackManager.listPacks(settings.defaultDir).size
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val json = AppPrefs.exportBackupJson(settings)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, "Settings & presets backup exported.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Export failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val restored = AppPrefs.importBackupJson(raw, settings)
                if (restored != null) {
                    onUpdateSettings(restored)
                    Toast.makeText(context, "Backup restored cleanly.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid PocketBoot backup file.", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(context, "Restore failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(settings.scanDirs) {
        tempStats = UsbGadgetEngine.getTempStorageStats(context, settings.scanDirs + settings.defaultDir)
    }

    LaunchedEffect(settings.scanDirs) {
        scope.launch {
            settings.scanDirs.forEach { dir ->
                dirCounts[dir] = UsbGadgetEngine.countImagesInDir(dir)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Section 1: USB & ENGINE
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketSectionHeader("USB & Engine")

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PocketColors.surfaceCard,
                border = BorderStroke(1.dp, PocketColors.border)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(Icons.Rounded.BugReport, PocketColors.infoBlue, PocketColors.infoBlueBg)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Keep USB Debugging On", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text("Keep ADB active alongside mounted drives", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                        PocketSwitch(
                            checked = settings.keepAdbActive,
                            onCheckedChange = { onUpdateSettings(settings.copy(keepAdbActive = it)) }
                        )
                    }

                    PocketHairlineDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(Icons.Rounded.Security, PocketColors.success, PocketColors.successBg)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Auto-Fix File Permissions", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text("Auto-patch SELinux for ISO read access", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                        PocketSwitch(
                            checked = settings.autoPatchSelinux,
                            onCheckedChange = { onUpdateSettings(settings.copy(autoPatchSelinux = it)) },
                            activeColor = PocketColors.success
                        )
                    }

                    PocketHairlineDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(Icons.Rounded.Layers, PocketColors.violet, PocketColors.violetBg)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Multi-Boot Engine", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text(
                                text = when {
                                    updatingVentoy -> ventoyUpdateText.ifBlank { "Downloading latest engine..." }
                                    ventoyInfo.ready -> "v${ventoyInfo.version} · ${if (ventoyInfo.isUpdatedInApp) "Updated" else "Bundled"}"
                                    else -> "Not installed — tap Update"
                                },
                                style = if (ventoyInfo.ready && !updatingVentoy) PocketColors.monoStyle else MaterialTheme.typography.bodySmall,
                                color = if (ventoyInfo.ready) PocketColors.textSecondary else PocketColors.warning
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (ventoyInfo.isUpdatedInApp && !updatingVentoy) {
                                TextButton(
                                    onClick = {
                                        VentoyImageBuilder.resetVentoyToBundled(context)
                                        ventoyInfo = VentoyImageBuilder.getInstalledVentoyInfo(context)
                                        Toast.makeText(context, "Restored bundled Multi-Boot engine.", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Text("Reset", fontSize = 11.5.sp, color = PocketColors.textSecondary)
                                }
                            }
                            Surface(
                                modifier = Modifier.bounceClick(enabled = !updatingVentoy) {
                                    scope.launch {
                                        updatingVentoy = true
                                        ventoyUpdateText = "Checking GitHub..."
                                        val res = VentoyImageBuilder.updateVentoyFromGitHub(context) { p ->
                                            ventoyUpdateText = p.detail.ifBlank { p.stage }
                                        }
                                        ventoyInfo = VentoyImageBuilder.getInstalledVentoyInfo(context)
                                        updatingVentoy = false
                                        Toast.makeText(context, res.userMessage, Toast.LENGTH_LONG).show()
                                    }
                                },
                                shape = RoundedCornerShape(9.dp),
                                color = PocketColors.accentBg,
                                border = BorderStroke(0.75.dp, PocketColors.accent.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    if (updatingVentoy) {
                                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = PocketColors.accent)
                                    } else {
                                        Icon(Icons.Rounded.CloudDownload, null, tint = PocketColors.accentSoft, modifier = Modifier.size(14.dp))
                                        Text("Update", style = MaterialTheme.typography.labelMedium, color = PocketColors.accentSoft)
                                    }
                                }
                            }
                        }
                    }

                    PocketHairlineDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick { showDlSpeedDialog = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(Icons.Rounded.Bolt, PocketColors.warning, PocketColors.warningBg)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Download Engine", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text(
                                text = "Turbo · ${settings.downloadConnections}x · ${if (settings.autoVerifySha256) "SHA-256 On" else "SHA-256 Off"}",
                                style = PocketColors.monoStyle,
                                color = PocketColors.textSecondary
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = PocketColors.textMuted, modifier = Modifier.size(18.dp))
                    }

                    PocketHairlineDivider()

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsIconBadge(Icons.Rounded.DeviceThermostat, PocketColors.infoBlue, PocketColors.infoBlueBg)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("USB Builder Thermal Profile", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                Text(settings.buildThermalProfile.subtitle, style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BuildThermalProfile.entries.forEach { prof ->
                                val sel = settings.buildThermalProfile == prof
                                Surface(
                                    modifier = Modifier.weight(1f).height(34.dp).bounceClick {
                                        onUpdateSettings(settings.copy(buildThermalProfile = prof))
                                    },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (sel) 1.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = prof.shortLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (sel) PocketColors.accentSoft else PocketColors.textSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onUpdateSettings(settings.copy(autoThermalCooldown = !settings.autoThermalCooldown))
                            }.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Thermal Cooldown", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                Text("Pauses between USB Builder steps if battery gets hot", fontSize = 11.sp, color = PocketColors.textSecondary)
                            }
                            PocketSwitch(
                                checked = settings.autoThermalCooldown,
                                onCheckedChange = { onUpdateSettings(settings.copy(autoThermalCooldown = it)) },
                                activeColor = PocketColors.accent
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onUpdateSettings(settings.copy(keepScreenAwakeDuringBuilds = !settings.keepScreenAwakeDuringBuilds))
                            }.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Keep Screen Awake During Builds", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                Text("Off (recommended): screen sleeps to reduce heat while wake lock runs", fontSize = 11.sp, color = PocketColors.textSecondary)
                            }
                            PocketSwitch(
                                checked = settings.keepScreenAwakeDuringBuilds,
                                onCheckedChange = { onUpdateSettings(settings.copy(keepScreenAwakeDuringBuilds = it)) },
                                activeColor = PocketColors.accent
                            )
                        }
                    }

                    PocketHairlineDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick { showDriverPackSheet = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(Icons.Rounded.Memory, PocketColors.infoBlue, PocketColors.infoBlueBg)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Driver Pack Library", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text(
                                text = if (driverPackCount > 0) "$driverPackCount saved pack(s) · Intel VMD, AMD RAID, Wi-Fi/LAN" else "Import reusable .inf folders or .zip/.7z packs",
                                style = MaterialTheme.typography.bodySmall,
                                color = PocketColors.textSecondary
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = PocketColors.textMuted, modifier = Modifier.size(18.dp))
                    }

                    PocketHairlineDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick { onResetNormalUsb() }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(Icons.Rounded.RestartAlt, PocketColors.danger, PocketColors.dangerBg)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Reset Normal USB", style = MaterialTheme.typography.labelLarge, color = PocketColors.danger)
                            Text("Restore standard MTP and charging", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = PocketColors.danger.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // Section 2: APPEARANCE
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketSectionHeader("Appearance")

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PocketColors.surfaceCard,
                border = BorderStroke(1.dp, PocketColors.border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Animated Segmented Control for Light / Dark / AMOLED
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        color = PocketColors.surfaceInput,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            SurfaceMode.entries.forEach { mode ->
                                val selected = settings.surfaceMode == mode
                                val segBg by animateColorAsState(
                                    if (selected) PocketColors.accentBg else Color.Transparent,
                                    tween(200),
                                    label = "segBg"
                                )
                                val segText by animateColorAsState(
                                    if (selected) PocketColors.accentSoft else PocketColors.textSecondary,
                                    tween(200),
                                    label = "segText"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(segBg)
                                        .border(
                                            width = if (selected) 0.75.dp else 0.dp,
                                            color = if (selected) PocketColors.accent.copy(alpha = 0.5f) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .bounceClick { onUpdateSettings(settings.copy(surfaceMode = mode)) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = segText
                                    )
                                }
                            }
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dynamic Wallpaper Accent", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                Text("Follow system Monet palette", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            PocketSwitch(
                                checked = settings.useMonet,
                                onCheckedChange = { onUpdateSettings(settings.copy(useMonet = it)) }
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Accent Color", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text(
                                text = if (settings.useMonet) "MONET" else String.format(Locale.US, "#%06X", 0xFFFFFF and settings.accentHex.toInt()),
                                style = PocketColors.monoStyle,
                                color = PocketColors.accentSoft
                            )
                        }

                        val swatches = listOf(
                            0xFF15F5ABL, // Mint Neon (Default)
                            0xFF6366F1L, // Linear Indigo
                            0xFF38BDF8L, // Electric Cyan
                            0xFFF59E0BL, // Amber
                            0xFFF43F5EL, // Rose
                            0xFFE4E4E7L  // Monochrome Zinc
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            swatches.forEach { hex ->
                                val swatchColor = Color(hex)
                                val selected = !settings.useMonet && settings.accentHex == hex
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) PocketColors.textPrimary else PocketColors.border,
                                            shape = CircleShape
                                        )
                                        .bounceClick {
                                            onUpdateSettings(settings.copy(useMonet = false, accentHex = hex))
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = if (swatchColor.luminance() > 0.48f) PocketColors.onBrightSurface else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = PocketColors.surfaceSubtle,
                                border = BorderStroke(0.75.dp, PocketColors.border),
                                modifier = Modifier.bounceClick { showColorPickerDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.Palette, null, tint = PocketColors.accentSoft, modifier = Modifier.size(14.dp))
                                    Text("Custom", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: STORAGE
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketSectionHeader(
                title = "Storage",
                trailing = if (settings.scanDirs.size == 1) "1 folder" else "${settings.scanDirs.size} folders"
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PocketColors.surfaceCard,
                border = BorderStroke(1.dp, PocketColors.border)
            ) {
                Column {
                    settings.scanDirs.forEachIndexed { idx, dir ->
                        if (idx > 0) PocketHairlineDivider()
                        val isDefault = dir == settings.defaultDir
                        val count = dirCounts[dir] ?: 0
                        var menuOpen by remember(dir) { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SettingsIconBadge(
                                icon = if (isDefault) Icons.Rounded.Lock else Icons.Rounded.Folder,
                                tint = if (isDefault) PocketColors.accentSoft else PocketColors.textSecondary,
                                bg = if (isDefault) PocketColors.accentBg else PocketColors.surfaceSubtle
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = UsbGadgetEngine.friendlyFolderName(dir),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = PocketColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isDefault) {
                                        Surface(shape = RoundedCornerShape(5.dp), color = PocketColors.accentBg) {
                                            Text(
                                                text = "Default",
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PocketColors.accentSoft
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "$dir · $count file(s)",
                                    style = PocketColors.monoStyle,
                                    color = PocketColors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!isDefault) {
                                Box {
                                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(44.dp)) {
                                        Icon(Icons.Rounded.MoreVert, "Folder options", tint = PocketColors.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Set as Default", color = PocketColors.textPrimary) },
                                            leadingIcon = { Icon(Icons.Rounded.Lock, null, tint = PocketColors.accentSoft, modifier = Modifier.size(17.dp)) },
                                            onClick = {
                                                menuOpen = false
                                                val reordered = (listOf(dir) + settings.scanDirs.filter { it != dir }).distinct()
                                                onUpdateSettings(settings.copy(defaultDir = dir, scanDirs = reordered))
                                                Toast.makeText(context, "Default folder: ${UsbGadgetEngine.friendlyFolderName(dir)}", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Remove Folder", color = PocketColors.danger) },
                                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = PocketColors.danger, modifier = Modifier.size(17.dp)) },
                                            onClick = {
                                                menuOpen = false
                                                onUpdateSettings(settings.copy(scanDirs = settings.scanDirs - dir))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    PocketHairlineDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { showAddDirDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, null, tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Folder", style = MaterialTheme.typography.labelMedium, color = PocketColors.accentSoft)
                        }
                        if (settings.hiddenFiles.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    onUpdateSettings(settings.copy(hiddenFiles = emptyList()))
                                    Toast.makeText(context, "Restored hidden files.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Restore, null, tint = PocketColors.textSecondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Unhide (${settings.hiddenFiles.size})", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                            }
                        }
                    }

                    PocketHairlineDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(Icons.Rounded.CleaningServices, PocketColors.warning, PocketColors.warningBg)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Temp & Build Cache", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text(
                                text = when {
                                    isOperationActive -> "Paused while a build or download is active"
                                    tempStats.totalBytes > 0L -> "${UsbGadgetEngine.formatBytes(tempStats.totalBytes)} in .pocketboot_tmp & downloads"
                                    else -> "0 B · Workspace is clean"
                                },
                                style = PocketColors.monoStyle,
                                color = if (!isOperationActive && tempStats.totalBytes > 0L) PocketColors.warning else PocketColors.textSecondary
                            )
                        }
                        val canCleanTemp = !cleaningTemp && !isOperationActive && tempStats.totalBytes > 0L
                        Surface(
                            modifier = Modifier.bounceClick(enabled = canCleanTemp) {
                                scope.launch {
                                    cleaningTemp = true
                                    val res = UsbGadgetEngine.cleanTempStorage(context, settings.scanDirs + settings.defaultDir)
                                    tempStats = UsbGadgetEngine.getTempStorageStats(context, settings.scanDirs + settings.defaultDir)
                                    cleaningTemp = false
                                    Toast.makeText(context, res.userMessage, Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(9.dp),
                            color = if (canCleanTemp) PocketColors.warningBg else PocketColors.surfaceSubtle,
                            border = BorderStroke(0.5.dp, if (canCleanTemp) PocketColors.warning else PocketColors.border)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), contentAlignment = Alignment.Center) {
                                if (cleaningTemp) {
                                    CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = PocketColors.warning)
                                } else {
                                    Text(
                                        text = "Clean",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (canCleanTemp) PocketColors.warning else PocketColors.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4: SYSTEM
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketSectionHeader("System")

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PocketColors.surfaceCard,
                border = BorderStroke(1.dp, PocketColors.border)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick { showDiagnosticsSheet = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsIconBadge(Icons.Rounded.HealthAndSafety, PocketColors.success, PocketColors.successBg)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Pre-Flight Diagnostics", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text("Root, ConfigFS, Multi-LUN, filesystems & tools", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = PocketColors.textMuted, modifier = Modifier.size(18.dp))
                    }

                    PocketHairlineDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .bounceClick { showActivityLogSheet = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsIconBadge(Icons.Rounded.Terminal, PocketColors.accentSoft, PocketColors.accentBg)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Activity Log", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text("${logs.size} event(s) this session", style = PocketColors.monoStyle, color = PocketColors.textSecondary)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = PocketColors.textMuted, modifier = Modifier.size(18.dp))
                    }

                    PocketHairlineDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SettingsIconBadge(Icons.Rounded.Backup, PocketColors.infoBlue, PocketColors.infoBlueBg)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Settings & Presets Backup", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text("Export or restore folders, theme & Windows presets", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                modifier = Modifier.bounceClick { exportBackupLauncher.launch("pocketboot-backup.json") },
                                shape = RoundedCornerShape(8.dp),
                                color = PocketColors.accentBg,
                                border = BorderStroke(0.5.dp, PocketColors.accent)
                            ) {
                                Text("Export", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = PocketColors.accentSoft)
                            }
                            Surface(
                                modifier = Modifier.bounceClick { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                                shape = RoundedCornerShape(8.dp),
                                color = PocketColors.surfaceSubtle,
                                border = BorderStroke(0.5.dp, PocketColors.border)
                            ) {
                                Text("Restore", modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = PocketColors.textPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Section 5: ABOUT (Rich gradient hero + crisp aligned single-line telemetry)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PocketSectionHeader("About")

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = PocketColors.surfaceCard,
                border = BorderStroke(1.dp, PocketColors.border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PocketColors.surfaceCard)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(PocketColors.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Usb, null, tint = PocketColors.onAccent, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PocketBoot v4.11.0", style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary)
                            Text(
                                text = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                                style = MaterialTheme.typography.bodySmall,
                                color = PocketColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            modifier = Modifier.bounceClick {
                                val info = "PocketBoot v4.11.0\nDevice: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nRoot: ${status.rootManager}\nSELinux: ${status.selinuxMode}\nUDC: ${status.udcName}\nKernel: ${status.kernelVersion}"
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(ClipData.newPlainText("Device Info", info))
                                Toast.makeText(context, "Device info copied.", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = PocketColors.surfaceSubtle,
                            border = BorderStroke(0.5.dp, PocketColors.border)
                        ) {
                            Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.ContentCopy, "Copy Device Info", tint = PocketColors.textSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Clean aligned hardware metadata strip (fixes awkward kernel line wrap)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = PocketColors.surfaceInput,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Kernel", style = MaterialTheme.typography.bodySmall, color = PocketColors.textMuted)
                                Text(
                                    text = status.kernelVersion.ifBlank { "Unknown" },
                                    style = PocketColors.monoStyle,
                                    color = PocketColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Security", style = MaterialTheme.typography.bodySmall, color = PocketColors.textMuted)
                                Text(
                                    text = "${status.rootManager} · SELinux ${status.selinuxMode}",
                                    style = PocketColors.monoStyle,
                                    color = PocketColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = !checkingUpdate,
                            onClick = {
                                scope.launch {
                                    checkingUpdate = true
                                    val res = AppUpdateChecker.checkForUpdate("4.11.0")
                                    checkingUpdate = false
                                    if (res.success && res.updateAvailable && res.release != null) {
                                        availableRelease = res.release
                                    } else {
                                        Toast.makeText(context, res.message, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            if (checkingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = PocketColors.accent)
                            } else {
                                Icon(Icons.Rounded.CloudDownload, null, tint = PocketColors.accentSoft, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(if (checkingUpdate) "Checking..." else "Updates", fontSize = 12.sp, color = PocketColors.accentSoft)
                        }
                        TextButton(onClick = { showCreditsDialog = true }) {
                            Icon(Icons.Rounded.Info, null, tint = PocketColors.textSecondary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Credits", fontSize = 12.sp, color = PocketColors.textSecondary)
                        }
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdateChecker.REPO_URL))
                                    )
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = PocketColors.accentSoft, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("GitHub", fontSize = 12.sp, color = PocketColors.accentSoft)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // App Release Update Dialog
    availableRelease?.let { rel ->
        AlertDialog(
            onDismissRequest = { availableRelease = null },
            containerColor = PocketColors.surfaceCard,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Update Available · ${rel.tagName}", color = PocketColors.textPrimary)
                    if (rel.publishedAt.isNotBlank()) {
                        Text("Published ${rel.publishedAt} · Current v4.11.0", style = PocketColors.monoStyle, color = PocketColors.textMuted)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = rel.releaseNotes.ifBlank { "A newer PocketBoot release (${rel.tagName}) is ready on GitHub." }.take(2400),
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetUrl = rel.apkDownloadUrl ?: rel.htmlUrl
                        availableRelease = null
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                        }.onFailure {
                            Toast.makeText(context, "Could not open browser for release link.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Text(if (rel.apkDownloadUrl != null) "Download APK" else "Open Release", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { availableRelease = null }) {
                    Text("Later", color = PocketColors.textSecondary)
                }
            }
        )
    }

    if (showDriverPackSheet) {
        DriverPackManagerSheet(
            defaultDir = settings.defaultDir,
            onDismiss = { showDriverPackSheet = false },
            onPacksChanged = { driverPackRefreshNonce++ },
            onMessage = { msg, _ -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
        )
    }

    // Activity Log Bottom Sheet
    if (showActivityLogSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActivityLogSheet = false },
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Activity Log", style = MaterialTheme.typography.titleLarge, color = PocketColors.textPrimary)
                        Text("${logs.size} event(s) recorded", style = PocketColors.monoStyle, color = PocketColors.textMuted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                val text = logs.joinToString("\n") { "[${it.timestamp}] ${it.tag}: ${it.message}" }
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(ClipData.newPlainText("PocketBoot Log", text))
                                Toast.makeText(context, "Log copied.", Toast.LENGTH_SHORT).show()
                            },
                            enabled = logs.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(0.5.dp, PocketColors.border)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, tint = PocketColors.textPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 12.sp, color = PocketColors.textPrimary)
                        }
                        OutlinedButton(
                            onClick = onClearLogs,
                            enabled = logs.isNotEmpty(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(0.5.dp, PocketColors.border)
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, null, tint = PocketColors.danger, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear", fontSize = 12.sp, color = PocketColors.danger)
                        }
                    }
                }

                if (logs.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = PocketColors.surfaceInput,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Text(
                            text = "No activity recorded yet.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textMuted
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = PocketColors.surfaceInput,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            logs.takeLast(80).reversed().forEach { entry ->
                                Text(
                                    text = "[${entry.timestamp}] ${entry.tag}: ${entry.message}",
                                    style = PocketColors.monoStyle,
                                    color = if (entry.isError) PocketColors.danger else PocketColors.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))
            }
        }
    }

    if (showAddDirDialog) {
        var newPath by remember { mutableStateOf("/sdcard/ISOs") }
        var makeDefault by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddDirDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Add Folder", color = PocketColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPath,
                        onValueChange = { newPath = it },
                        label = { Text("Folder path") },
                        placeholder = { Text("/sdcard/ISOs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { makeDefault = !makeDefault }
                    ) {
                        Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it })
                        Text("Set as default save folder", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val resolved = UsbGadgetEngine.resolveDirectPath(newPath)
                        if (resolved.isNotBlank()) {
                            scope.launch {
                                UsbGadgetEngine.ensureDirectoryExists(resolved, context)
                                val updatedDirs = if (makeDefault) {
                                    (listOf(resolved) + settings.scanDirs.filter { it != resolved }).distinct()
                                } else {
                                    (settings.scanDirs + resolved).distinct()
                                }
                                onUpdateSettings(
                                    settings.copy(
                                        defaultDir = if (makeDefault) resolved else settings.defaultDir,
                                        scanDirs = updatedDirs
                                    )
                                )
                            }
                            showAddDirDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Text("Add", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDirDialog = false }) {
                    Text("Cancel", color = PocketColors.textSecondary)
                }
            }
        )
    }

    if (showColorPickerDialog) {
        val initialHsv = remember(settings.accentHex) {
            val out = FloatArray(3)
            android.graphics.Color.colorToHSV(Color(settings.accentHex).toArgb(), out)
            out
        }
        var hue by remember { mutableFloatStateOf(initialHsv[0]) }
        var sat by remember { mutableFloatStateOf(initialHsv[1].coerceAtLeast(0.35f)) }
        val previewColor = Color.hsv(hue, sat, 0.96f)
        val previewLong = (0xFF000000L or (previewColor.toArgb().toLong() and 0x00FFFFFFL))

        AlertDialog(
            onDismissRequest = { showColorPickerDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Custom Accent", color = PocketColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(previewColor)
                                .border(0.5.dp, PocketColors.border, RoundedCornerShape(10.dp))
                        )
                        Text(
                            text = String.format(Locale.US, "#%06X", 0xFFFFFF and previewLong.toInt()),
                            style = PocketColors.monoStyle,
                            fontSize = 14.sp,
                            color = PocketColors.textPrimary
                        )
                    }
                    Column {
                        Text("Hue (${hue.toInt()}°)", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        Slider(
                            value = hue,
                            onValueChange = { hue = it },
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(thumbColor = previewColor, activeTrackColor = previewColor)
                        )
                    }
                    Column {
                        Text("Saturation (${(sat * 100).toInt()}%)", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        Slider(
                            value = sat,
                            onValueChange = { sat = it },
                            valueRange = 0.05f..1f,
                            colors = SliderDefaults.colors(thumbColor = previewColor, activeTrackColor = previewColor)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateSettings(settings.copy(useMonet = false, accentHex = previewLong))
                        showColorPickerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = previewColor, contentColor = if (previewColor.luminance() > 0.48f) Color.Black else Color.White)
                ) {
                    Text("Apply", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showColorPickerDialog = false }) {
                    Text("Cancel", color = PocketColors.textSecondary)
                }
            }
        )
    }

    if (showDiagnosticsSheet) {
        DiagnosticsSheet(onDismiss = { showDiagnosticsSheet = false })
    }

    if (showCreditsDialog) {
        AlertDialog(
            onDismissRequest = { showCreditsDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Open-Source Credits", color = PocketColors.textPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("PocketBoot uses the following open-source projects:", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                    Text("• Ventoy (GPLv3) — Multi-boot UEFI/BIOS engine", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    Text("• Rufus UEFI:NTFS (GPLv3) — UEFI NTFS boot driver", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    Text("• NTFS-3G / mkntfs / ntfsresize (GPLv2+) — NTFS formatter, resizer & FUSE driver", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    Text("• ms-sys (GPLv2+) — Legacy MBR & VBR bootsector writer", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    Text("• exfatprogs (GPLv2) — exFAT filesystem formatter", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    Text("• 7-Zip / 7zzs (LGPL / BSD) — ISO archive extractor", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    Text("• wimlib (GPLv3/LGPLv3) — WIM/ESD imaging engine", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    Text("• chntpw / reged (GPLv2) — Offline Windows registry editor", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    Text("• libsu (Apache 2.0) — Root shell execution", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    Text("• Chris Titus Tech WinUtil (MIT) — Windows system tweak catalog & presets", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    Text("• NTriver.org (WindowsAddict, CC-BY-4.0) — Archival Windows RTM ISO catalog & SHA-256 checksums", style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                }
            },
            confirmButton = {
                TextButton(onClick = { showCreditsDialog = false }) {
                    Text("Close", color = PocketColors.accentSoft)
                }
            }
        )
    }

    if (showDlSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showDlSpeedDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Turbo Downloader Settings", color = PocketColors.textPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Runs in a dedicated background Foreground Service with cross-restart resume (.pbmeta) and automatic single-stream fallback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Parallel Connections", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(4, 8, 12, 16).forEach { n ->
                                val isSel = settings.downloadConnections == n
                                Surface(
                                    modifier = Modifier.weight(1f).bounceClick {
                                        onUpdateSettings(settings.copy(downloadConnections = n))
                                    },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (isSel) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (isSel) 1.5.dp else 0.5.dp, if (isSel) PocketColors.accent else PocketColors.border)
                                ) {
                                    Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${n}x", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (isSel) PocketColors.accentSoft else PocketColors.textPrimary)
                                        if (n == 8) Text("Rec", fontSize = 11.sp, color = PocketColors.success)
                                    }
                                }
                            }
                        }
                    }

                    PocketHairlineDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Verify SHA-256", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text("Verify checksum right after download", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        PocketSwitch(
                            checked = settings.autoVerifySha256,
                            onCheckedChange = { onUpdateSettings(settings.copy(autoVerifySha256 = it)) },
                            activeColor = PocketColors.success
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDlSpeedDialog = false }) {
                    Text("Done", color = PocketColors.accentSoft)
                }
            }
        )
    }
}
