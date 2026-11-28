package com.pocketboot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

enum class DiagLevel { PASS, WARN, FAIL }

data class DiagCheckItem(
    val title: String,
    val detail: String,
    val level: DiagLevel
)

object PreFlightDiagnostics {

    suspend fun runAll(context: Context): List<DiagCheckItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<DiagCheckItem>()
        val status = UsbGadgetEngine.getStatus()

        items.add(
            if (status.hasRoot) {
                DiagCheckItem(
                    title = "Root Access (${status.rootManager})",
                    detail = "Root granted • SELinux: ${status.selinuxMode} • Kernel: ${status.kernelVersion}",
                    level = DiagLevel.PASS
                )
            } else {
                DiagCheckItem(
                    title = "Root Access Missing",
                    detail = "Grant root permission in Magisk, KernelSU, or APatch.",
                    level = DiagLevel.FAIL
                )
            }
        )

        items.add(
            when {
                !status.configFsReady -> DiagCheckItem(
                    title = "ConfigFS USB Gadget",
                    detail = "ConfigFS gadget directory not found on this kernel.",
                    level = DiagLevel.FAIL
                )
                status.multiLunSupported -> DiagCheckItem(
                    title = "ConfigFS + Multi-LUN (LUN 0 & LUN 1)",
                    detail = "${status.gadgetPath} ready • Dual simultaneous mounts supported",
                    level = DiagLevel.PASS
                )
                else -> DiagCheckItem(
                    title = "ConfigFS Ready (Single LUN)",
                    detail = "${status.gadgetPath} ready • Kernel supports LUN 0 only",
                    level = DiagLevel.WARN
                )
            }
        )

        val cfsProp = runCatching { Shell.cmd("getprop sys.usb.configfs 2>/dev/null").exec().out.firstOrNull().orEmpty().trim() }.getOrDefault("")
        items.add(
            if (status.udcName.isNotBlank()) {
                DiagCheckItem(
                    title = "Physical USB-C Controller",
                    detail = "UDC: ${status.udcName} • Link: ${status.friendlySpeedLabel} • configfs=$cfsProp",
                    level = DiagLevel.PASS
                )
            } else {
                DiagCheckItem(
                    title = "USB Controller (UDC)",
                    detail = "No physical controller detected under /sys/class/udc.",
                    level = DiagLevel.FAIL
                )
            }
        )

        val procFs = runCatching {
            Shell.cmd("cat /proc/filesystems 2>/dev/null").exec().out
                .map { it.trim().substringAfterLast('\t').substringAfterLast(' ').lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
        }.getOrDefault(emptySet())
        val hasNtfs3 = "ntfs3" in procFs || "tntfs" in procFs || "ufsd" in procFs
        val hasFuse = ("fuse" in procFs || "fuseblk" in procFs) && Shell.cmd("[ -e /dev/fuse ]").exec().isSuccess
        val hasExfat = "exfat" in procFs || "sdfat" in procFs
        val hasVfat = "vfat" in procFs
        val hasExt4 = "ext4" in procFs

        val libDir = context.applicationInfo.nativeLibraryDir
        fun hasExec(name: String): Boolean = File(libDir, name).let { it.exists() && it.canExecute() }
        val has7z = hasExec("lib7zzs.so")
        val hasZstd = hasExec("libzstd.so")
        val hasNtfs = hasExec("libmkfs_ntfs.so")
        val hasNtfs3g = hasExec("libntfs_3g.so")
        val hasMsSys = hasExec("libms_sys.so")
        val hasExfatBin = hasExec("libmkfs_exfat.so")
        val hasFatBin = hasExec("libmkfs_fat.so")
        val hasNtfsResize = hasExec("libntfsresize.so")
        val hasUefiAsset = runCatching { context.assets.open("uefi-ntfs.img").use { it.available() > 0 } }.getOrDefault(false)

        val ntfsMode = when {
            hasNtfs3 -> "Kernel NTFS"
            hasFuse && hasNtfs3g -> "FUSE NTFS"
            else -> "Unavailable"
        }
        val fsDetail = "NTFS=$ntfsMode • fuse=${if (hasFuse) "YES" else "NO"} • ext4=${if (hasExt4) "YES" else "NO"} • exfat=${if (hasExfat) "YES" else "NO"}"
        items.add(
            DiagCheckItem(
                title = "Kernel & FUSE Filesystems",
                detail = fsDetail,
                level = when {
                    (hasNtfs3 || (hasFuse && hasNtfs3g)) && hasExt4 -> DiagLevel.PASS
                    hasVfat -> DiagLevel.WARN
                    else -> DiagLevel.FAIL
                }
            )
        )

        val toolDetail = "7zzs=${if (has7z) "OK" else "NO"} • zstd=${if (hasZstd) "OK" else "NO"} • NTFS=${if (hasNtfs && hasNtfs3g) "OK" else "NO"} • Resize=${if (hasNtfsResize) "OK" else "NO"} • exFAT=${if (hasExfatBin) "OK" else "NO"} • FAT32=${if (hasFatBin) "OK" else "NO"} • MBR=${if (hasMsSys) "OK" else "NO"} • UEFI=${if (hasUefiAsset) "OK" else "NO"}"
        items.add(
            DiagCheckItem(
                title = "Bundled Static ARM64 Tools",
                detail = toolDetail,
                level = when {
                    has7z && hasZstd && hasNtfs && hasNtfs3g && hasExfatBin && hasFatBin && hasMsSys && hasUefiAsset -> DiagLevel.PASS
                    has7z && hasNtfs -> DiagLevel.WARN
                    else -> DiagLevel.FAIL
                }
            )
        )

        val stayOn = runCatching {
            Settings.Global.getInt(context.contentResolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0) > 0
        }.getOrDefault(false)
        val winTools = BuildThermalHelper.inspectWindowsTools(context)
        val winDetail = "7zzs=${if (winTools.sevenZip != null) "OK" else "NO"} • wimlib=${if (winTools.wimlib != null) "OK" else "NO"} • reged=${if (winTools.chntpw != null) "OK" else "NO"}"
        items.add(
            DiagCheckItem(
                title = "Windows Imaging Tools",
                detail = winDetail,
                level = if (winTools.ready) DiagLevel.PASS else DiagLevel.WARN
            )
        )

        val ventoyInfo = VentoyImageBuilder.getInstalledVentoyInfo(context)
        items.add(
            DiagCheckItem(
                title = "Multi-Boot Engine",
                detail = if (ventoyInfo.ready) {
                    "v${ventoyInfo.version} (${if (ventoyInfo.isUpdatedInApp) "Updated in-app" else "Bundled in APK"}) • Unlimited over 2.2 GB ISO boot via USB Disk"
                } else {
                    "Boot files missing — tap Settings → Multi-Boot Engine → Update"
                },
                level = if (ventoyInfo.ready) DiagLevel.PASS else DiagLevel.WARN
            )
        )

        items.add(
            DiagCheckItem(
                title = "USB Stability & Hot-Plug Guard",
                detail = if (stayOn) {
                    "Stay awake ON · Hot-plug auto-rebind active · Virtual CD cap: 2.2 GB (USB Disk unlimited)"
                } else {
                    "Enable 'Stay awake' in Developer Options · Hot-plug active · Virtual CD cap: 2.2 GB"
                },
                level = if (stayOn) DiagLevel.PASS else DiagLevel.WARN
            )
        )

        items
    }

    fun formatReport(items: List<DiagCheckItem>): String {
        val sb = StringBuilder()
        sb.appendLine("PocketBoot v4.11.0 Pre-Flight Diagnostic Report")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}, ABI ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"})")
        sb.appendLine("---")
        items.forEach { item ->
            sb.appendLine("[${item.level.name}] ${item.title}: ${item.detail}")
        }
        return sb.toString().trim()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checks by remember { mutableStateOf<List<DiagCheckItem>>(emptyList()) }
    var running by remember { mutableStateOf(true) }

    fun runChecks() {
        scope.launch {
            running = true
            checks = PreFlightDiagnostics.runAll(context)
            running = false
        }
    }

    LaunchedEffect(Unit) { runChecks() }

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
                    Icon(Icons.Rounded.HealthAndSafety, null, tint = PocketColors.accentSoft, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pre-Flight Diagnostics", style = MaterialTheme.typography.titleLarge, color = PocketColors.textPrimary)
                    Text(
                        "${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )
                }
            }

            if (running) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(color = PocketColors.accent)
                        Text("Inspecting kernel, ConfigFS & bundled tools...", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                    }
                }
            } else {
                checks.forEach { item ->
                    val (bg, tint, icon) = when (item.level) {
                        DiagLevel.PASS -> Triple(PocketColors.successBg, PocketColors.success, Icons.Rounded.CheckCircle)
                        DiagLevel.WARN -> Triple(PocketColors.warningBg, PocketColors.warning, Icons.Rounded.WarningAmber)
                        DiagLevel.FAIL -> Triple(PocketColors.dangerBg, PocketColors.danger, Icons.Rounded.ErrorOutline)
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PocketColors.surfaceInput,
                        border = BorderStroke(0.5.dp, if (item.level == DiagLevel.PASS) PocketColors.border else tint)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(bg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.title, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                Text(item.detail, style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { runChecks() },
                        modifier = Modifier
                            .weight(1f)
                            .height(PocketTokens.PrimaryButtonHeight),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PocketColors.border)
                    ) {
                        Icon(Icons.Rounded.Refresh, null, tint = PocketColors.textPrimary, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-run", fontSize = 13.sp, color = PocketColors.textPrimary)
                    }
                    Button(
                        onClick = {
                            val report = PreFlightDiagnostics.formatReport(checks)
                            val cm = context.getSystemService(ClipboardManager::class.java)
                            cm?.setPrimaryClip(ClipData.newPlainText("PocketBoot Diagnostics", report))
                            Toast.makeText(context, "Diagnostic report copied.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(PocketTokens.PrimaryButtonHeight),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PocketColors.accent,
                            contentColor = PocketColors.onAccent
                        )
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Report", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}
