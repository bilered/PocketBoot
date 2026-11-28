package com.pocketboot.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

enum class UsbBuildKind(val label: String) {
    WINDOWS("Windows USB"),
    LINUX("Linux USB"),
    MULTIBOOT("Multi-Boot Drive"),
    DECOMPRESS("Decompress Image"),
    OTG_FLASH("USB-C OTG Flash")
}

data class UsbBuildState(
    val kind: UsbBuildKind,
    val targetName: String,
    val progress: WindowsBuildProgress = WindowsBuildProgress(),
    val autoMountAfterBuild: Boolean = false,
    val autoMountMode: MountMode = MountMode.USB_RW
)

data class UsbBuildCompletionEvent(
    val id: Long,
    val kind: UsbBuildKind,
    val targetName: String,
    val result: ActionResult,
    val autoMountAfterBuild: Boolean,
    val autoMountMode: MountMode,
    val cancelled: Boolean = false
)

class UsbBuildService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = NotificationHelper.buildUsbBuildNotification(this, UsbBuildManager.state.value)
        val promoted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.BUILD_NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NotificationHelper.BUILD_NOTIF_ID, notif)
            }
        }.isSuccess
        if (!promoted) {
            releaseWakeLock()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        acquireWakeLock()
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        runCatching {
            if (wakeLock == null) {
                val pm = getSystemService(PowerManager::class.java)
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketBoot::UsbBuild")?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(2L * 60L * 60L * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        UsbBuildManager.cancelActiveBuild(null)
        NotificationHelper.cancelBuildNotification(this)
        releaseWakeLock()
        stopSelf(startId)
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, UsbBuildService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, UsbBuildService::class.java))
            }
        }
    }
}

object UsbBuildManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val completionIdSeq = AtomicLong(0L)
    private val cancelRequested = AtomicBoolean(false)

    private val _state = MutableStateFlow<UsbBuildState?>(null)
    val state: StateFlow<UsbBuildState?> = _state.asStateFlow()

    private val _completion = MutableStateFlow<UsbBuildCompletionEvent?>(null)
    val completion: StateFlow<UsbBuildCompletionEvent?> = _completion.asStateFlow()

    @Volatile private var currentJob: Job? = null
    @Volatile private var lastNotifUpdateMs: Long = 0L
    @Volatile private var lastNotifStage: String = ""

    fun isBusy(): Boolean = currentJob?.isActive == true || _state.value != null

    fun consumeCompletion(id: Long) {
        if (_completion.value?.id == id) {
            _completion.value = null
        }
    }

    @Synchronized
    private fun launchBuild(
        context: Context,
        kind: UsbBuildKind,
        targetName: String,
        autoMountAfterBuild: Boolean,
        autoMountMode: MountMode,
        block: suspend (onProgress: (WindowsBuildProgress) -> Unit) -> ActionResult
    ): Boolean {
        if (isBusy()) return false
        val appCtx = context.applicationContext
        cancelRequested.set(false)
        lastNotifUpdateMs = 0L
        lastNotifStage = ""

        val initial = UsbBuildState(
            kind = kind,
            targetName = targetName,
            progress = WindowsBuildProgress("Starting...", targetName, 0.02f, true),
            autoMountAfterBuild = autoMountAfterBuild,
            autoMountMode = autoMountMode
        )
        _state.value = initial
        UsbBuildService.start(appCtx)

        currentJob = scope.launch {
            var outcome: ActionResult? = null
            var wasCancelled = false
            try {
                outcome = block { p ->
                    val updated = _state.value?.copy(progress = p)
                    if (updated != null) {
                        _state.value = updated
                        val now = System.currentTimeMillis()
                        if (p.stage != lastNotifStage || (now - lastNotifUpdateMs) >= 450L) {
                            lastNotifStage = p.stage
                            lastNotifUpdateMs = now
                            NotificationHelper.updateBuildProgress(appCtx, updated)
                        }
                    }
                }
                if (cancelRequested.get() || BuildThermalHelper.isBuildCancelled()) {
                    wasCancelled = true
                }
            } catch (_: CancellationException) {
                wasCancelled = true
            } catch (e: Exception) {
                outcome = ActionResult(false, "${kind.label} build failed: ${e.message ?: "Unexpected error"}")
            } finally {
                val finalResult = when {
                    wasCancelled -> ActionResult(false, "Cancelled ${kind.label} build.")
                    outcome != null -> outcome
                    else -> ActionResult(false, "${kind.label} build did not complete.")
                }
                _state.value = null
                currentJob = null
                UsbBuildService.stop(appCtx)
                NotificationHelper.cancelBuildNotification(appCtx)
                if (!wasCancelled) {
                    NotificationHelper.showBuildComplete(
                        context = appCtx,
                        title = if (finalResult.success) "${kind.label} Ready" else "${kind.label} Failed",
                        subtitle = finalResult.userMessage
                    )
                }
                _completion.value = UsbBuildCompletionEvent(
                    id = completionIdSeq.incrementAndGet(),
                    kind = kind,
                    targetName = targetName,
                    result = finalResult,
                    autoMountAfterBuild = autoMountAfterBuild && !wasCancelled,
                    autoMountMode = autoMountMode,
                    cancelled = wasCancelled
                )
            }
        }
        return true
    }

    fun startWindowsBuild(context: Context, config: WindowsBuildConfig): Boolean {
        val appCtx = context.applicationContext
        return launchBuild(
            context = appCtx,
            kind = UsbBuildKind.WINDOWS,
            targetName = config.outputName.ifBlank { config.isoFile.name },
            autoMountAfterBuild = config.autoMountAfterBuild,
            autoMountMode = MountMode.USB_RW
        ) { onProgress ->
            WindowsImageBuilder.buildWindowsUsbImage(appCtx, config, onProgress)
        }
    }

    fun startLinuxBuild(context: Context, config: LinuxBuildConfig): Boolean {
        val appCtx = context.applicationContext
        val mountMode = if (config.usbMode == LinuxUsbMode.WITH_PERSISTENCE) MountMode.USB_RW else MountMode.USB_RO
        return launchBuild(
            context = appCtx,
            kind = UsbBuildKind.LINUX,
            targetName = config.outputName.ifBlank { config.isoFile.name },
            autoMountAfterBuild = config.autoMountAfterBuild,
            autoMountMode = mountMode
        ) { onProgress ->
            LinuxImageBuilder.buildLinuxPersistentImage(appCtx, config, onProgress)
        }
    }

    fun startMultiBootBuild(context: Context, config: VentoyBuildConfig): Boolean {
        val appCtx = context.applicationContext
        return launchBuild(
            context = appCtx,
            kind = UsbBuildKind.MULTIBOOT,
            targetName = config.outputName,
            autoMountAfterBuild = config.autoMountAfterBuild,
            autoMountMode = MountMode.USB_RW
        ) { onProgress ->
            VentoyImageBuilder.buildVentoyImage(appCtx, config, onProgress)
        }
    }

    fun startDecompress(context: Context, config: DecompressConfig): Boolean {
        val appCtx = context.applicationContext
        val mountMode = if (CompressedImageEngine.targetExtensionFor(config.sourceFile.name) == ".iso") MountMode.AUTO else MountMode.USB_RW
        return launchBuild(
            context = appCtx,
            kind = UsbBuildKind.DECOMPRESS,
            targetName = config.outputName.ifBlank { config.sourceFile.name },
            autoMountAfterBuild = config.autoMountAfter,
            autoMountMode = mountMode
        ) { onProgress ->
            CompressedImageEngine.decompressImage(appCtx, config, onProgress)
        }
    }

    fun startOtgFlash(context: Context, config: OtgFlashConfig): Boolean {
        val appCtx = context.applicationContext
        return launchBuild(
            context = appCtx,
            kind = UsbBuildKind.OTG_FLASH,
            targetName = "${config.sourceFile.name} -> ${config.targetDevice.devName}",
            autoMountAfterBuild = false,
            autoMountMode = MountMode.AUTO
        ) { onProgress ->
            OtgUsbFlasher.flashImageToOtg(appCtx, config, onProgress)
        }
    }

    fun startOtgWindowsFlash(context: Context, config: WindowsBuildConfig): Boolean {
        val appCtx = context.applicationContext
        val devLbl = config.directBlockTarget?.devName ?: "OTG"
        return launchBuild(
            context = appCtx,
            kind = UsbBuildKind.OTG_FLASH,
            targetName = "Win USB: ${config.isoFile.name} -> $devLbl",
            autoMountAfterBuild = false,
            autoMountMode = MountMode.AUTO
        ) { onProgress ->
            WindowsImageBuilder.buildWindowsUsbImage(appCtx, config, onProgress)
        }
    }

    fun startOtgLinuxFlash(context: Context, config: LinuxBuildConfig): Boolean {
        val appCtx = context.applicationContext
        val devLbl = config.directBlockTarget?.devName ?: "OTG"
        return launchBuild(
            context = appCtx,
            kind = UsbBuildKind.OTG_FLASH,
            targetName = "Linux USB: ${config.isoFile.name} -> $devLbl",
            autoMountAfterBuild = false,
            autoMountMode = MountMode.AUTO
        ) { onProgress ->
            LinuxImageBuilder.buildLinuxPersistentImage(appCtx, config, onProgress)
        }
    }

    fun startOtgMultiBootFlash(context: Context, config: VentoyBuildConfig): Boolean {
        val appCtx = context.applicationContext
        val devLbl = config.directBlockTarget?.devName ?: "OTG"
        return launchBuild(
            context = appCtx,
            kind = UsbBuildKind.OTG_FLASH,
            targetName = "Multi-Boot -> $devLbl",
            autoMountAfterBuild = false,
            autoMountMode = MountMode.AUTO
        ) { onProgress ->
            VentoyImageBuilder.buildVentoyImage(appCtx, config, onProgress)
        }
    }

    fun cancelActiveBuild(context: Context? = null) {
        val activeKind = _state.value?.kind
        cancelRequested.set(true)
        BuildThermalHelper.markBuildCancelled()
        val job = currentJob
        context?.applicationContext?.let { ctx ->
            _state.value?.let { cur ->
                val cancelling = cur.copy(
                    progress = cur.progress.copy(stage = "Cancelling build...", isIndeterminate = true)
                )
                _state.value = cancelling
                NotificationHelper.updateBuildProgress(ctx, cancelling)
            }
        }
        scope.launch {
            runCatching {
                when (activeKind) {
                    UsbBuildKind.WINDOWS -> WindowsImageBuilder.cancelActiveBuild()
                    UsbBuildKind.LINUX -> LinuxImageBuilder.cancelActiveBuild()
                    UsbBuildKind.MULTIBOOT -> VentoyImageBuilder.cancelActiveBuild()
                    UsbBuildKind.DECOMPRESS -> CompressedImageEngine.cancelActiveDecompress()
                    UsbBuildKind.OTG_FLASH -> OtgUsbFlasher.cancelActiveFlash()
                    null -> BuildThermalHelper.cancelActiveBuildProcess()
                }
            }
            job?.cancel()
        }
    }
}
