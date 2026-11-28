package com.pocketboot.app

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LinkWatchdogStatus(
    val serviceActive: Boolean = false,
    val recoveryCount: Int = 0,
    val cableDisconnected: Boolean = false,
    val lastEventMessage: String? = null,
    val lastEventTime: String? = null,
    val refreshSignal: Int = 0,
    val recentLogs: List<LogEntry> = emptyList()
)

object LinkWatchdogTracker {
    private val _state = MutableStateFlow(LinkWatchdogStatus())
    val state: StateFlow<LinkWatchdogStatus> = _state.asStateFlow()

    fun setActive(active: Boolean) {
        _state.value = if (active) {
            _state.value.copy(serviceActive = true)
        } else {
            LinkWatchdogStatus(serviceActive = false)
        }
    }

    fun recordRecovery(message: String, countAsRecovery: Boolean = true) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val cur = _state.value
        val log = LogEntry(ts, "watchdog", message, false)
        _state.value = cur.copy(
            recoveryCount = if (countAsRecovery) cur.recoveryCount + 1 else cur.recoveryCount,
            lastEventMessage = message,
            lastEventTime = ts,
            refreshSignal = cur.refreshSignal + 1,
            recentLogs = (cur.recentLogs + log).takeLast(25)
        )
    }

    fun updateCableState(disconnected: Boolean) {
        val cur = _state.value
        if (cur.cableDisconnected != disconnected) {
            val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val msg = if (disconnected) {
                "Waiting for USB-C cable · plug into PC anytime"
            } else {
                "USB-C host connected and ready"
            }
            _state.value = cur.copy(
                cableDisconnected = disconnected,
                lastEventMessage = msg,
                lastEventTime = ts,
                refreshSignal = cur.refreshSignal + 1
            )
        }
    }

    fun dismissBanner() {
        _state.value = _state.value.copy(lastEventMessage = null)
    }
}

class UsbKeepAliveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tickMutex = Mutex()
    private var watchdogJob: Job? = null
    private var hotPlugJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentKeepAdb: Boolean = false
    private var hotPlugAttemptsThisAttach: Int = 0
    private var lastObservedPlugged: Boolean = false
    private var receiverRegistered: Boolean = false

    private fun isWiredPluggedIn(): Boolean {
        return runCatching {
            val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val plugged = bat?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            (plugged and (BatteryManager.BATTERY_PLUGGED_USB or BatteryManager.BATTERY_PLUGGED_AC)) != 0
        }.getOrDefault(false)
    }

    private val usbAttachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                Intent.ACTION_POWER_DISCONNECTED -> {
                    hotPlugAttemptsThisAttach = 0
                    lastObservedPlugged = false
                    LinkWatchdogTracker.updateCableState(true)
                }
                Intent.ACTION_POWER_CONNECTED, ACTION_USB_STATE -> {
                    val usbConnected = if (action == ACTION_USB_STATE) {
                        intent.getBooleanExtra("connected", false)
                    } else {
                        true
                    }
                    if (!usbConnected) {
                        hotPlugAttemptsThisAttach = 0
                        lastObservedPlugged = false
                        return
                    }
                    hotPlugAttemptsThisAttach = 0
                    lastObservedPlugged = true
                    hotPlugJob?.cancel()
                    hotPlugJob = scope.launch {
                        // Allow Type-C CC & VBUS negotiation (~1.1s), then wake PHY + re-bind gadget
                        delay(1100L)
                        tickMutex.withLock {
                            runCatching {
                                val tick = UsbGadgetEngine.watchdogTick(
                                    keepAdb = currentKeepAdb,
                                    allowHotPlugBounce = true,
                                    androidPluggedHint = true
                                )
                                handleTick(tick, wiredHint = true)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val persistedKeepAdb = runCatching { AppPrefs(this).load().keepAdbActive }.getOrDefault(false)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "PocketBoot Virtual USB"
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: "Keep-Alive & Hot-Plug Watchdog active"
        currentKeepAdb = if (intent != null && intent.hasExtra(EXTRA_KEEP_ADB)) {
            intent.getBooleanExtra(EXTRA_KEEP_ADB, persistedKeepAdb)
        } else {
            persistedKeepAdb
        }

        val notif = NotificationHelper.buildMountedNotification(this, title, subtitle)
        val promoted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.NOTIF_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NotificationHelper.NOTIF_ID, notif)
            }
        }.isSuccess
        if (!promoted) {
            releaseWakeLock()
            LinkWatchdogTracker.setActive(false)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        lastObservedPlugged = isWiredPluggedIn()
        hotPlugAttemptsThisAttach = 0
        acquireWakeLock()
        registerHotPlugReceiver()
        LinkWatchdogTracker.setActive(true)
        if (intent == null) {
            scope.launch {
                runCatching {
                    val live = UsbGadgetEngine.getStatus()
                    if (!live.isAnyMounted) {
                        stopSelf(startId)
                    } else {
                        val mountedLabel = live.mountedFile.substringAfterLast('/').ifBlank {
                            live.secondaryMountedFile.substringAfterLast('/').ifBlank { "Virtual Drive" }
                        }
                        val liveModeLabel = if (live.mountedFile.isNotBlank()) {
                            live.activeModeLabel
                        } else {
                            live.activeSecondaryModeLabel
                        }
                        NotificationHelper.showMounted(
                            this@UsbKeepAliveService,
                            mountedLabel,
                            liveModeLabel,
                            "Keep-Alive resumed"
                        )
                    }
                }
            }
        }
        startWatchdog()
        return START_STICKY
    }

    private fun registerHotPlugReceiver() {
        if (receiverRegistered) return
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(ACTION_USB_STATE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(usbAttachReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(usbAttachReceiver, filter)
            }
            receiverRegistered = true
        }
    }

    private fun acquireWakeLock() {
        runCatching {
            if (wakeLock == null) {
                val pm = getSystemService(PowerManager::class.java)
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketBoot::UsbKeepAlive")?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(6L * 60L * 60L * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        }
    }

    private fun handleTick(tick: WatchdogTickResult, wiredHint: Boolean) {
        if (!tick.hasMountedLun) {
            stopSelf()
            return
        }
        val configured = tick.linkState.equals("configured", ignoreCase = true)
        val pluggedNow = tick.vbusOnline || wiredHint

        if (configured || !pluggedNow) {
            hotPlugAttemptsThisAttach = 0
        }
        lastObservedPlugged = pluggedNow

        if (tick.recoveredUdc) {
            val count = LinkWatchdogTracker.state.value.recoveryCount + 1
            val msg = "USB link auto-recovered (${count}x) on ${tick.udcName.ifBlank { "USB-C" }}"
            LinkWatchdogTracker.recordRecovery(msg, countAsRecovery = true)
            NotificationHelper.showMounted(
                this,
                tick.primaryName.ifBlank { "Virtual Drive" },
                "Keep-Alive",
                "Auto-recovered ${count}x"
            )
        } else if (tick.bouncedHotPlug) {
            hotPlugAttemptsThisAttach++
            val msg = if (configured) {
                "USB-C cable plugged in · auto-connected to PC"
            } else {
                "USB cable attached · tap Reconnect if PC did not detect"
            }
            LinkWatchdogTracker.recordRecovery(msg, countAsRecovery = false)
        }

        val disconnected = !configured && !pluggedNow
        LinkWatchdogTracker.updateCableState(disconnected)
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            UsbGadgetEngine.setUsbPowerControl(alwaysOn = true)
            while (isActive) {
                delay(2500L)
                tickMutex.withLock {
                    runCatching {
                        val wiredNow = isWiredPluggedIn()
                        // Reset attempt counter whenever we see a fresh unplug -> plug transition
                        if (wiredNow && !lastObservedPlugged) {
                            hotPlugAttemptsThisAttach = 0
                        } else if (!wiredNow) {
                            hotPlugAttemptsThisAttach = 0
                        }
                        val allowBounce = wiredNow && hotPlugAttemptsThisAttach < 2
                        val tick = UsbGadgetEngine.watchdogTick(
                            keepAdb = currentKeepAdb,
                            allowHotPlugBounce = allowBounce,
                            androidPluggedHint = wiredNow
                        )
                        handleTick(tick, wiredHint = wiredNow)
                    }
                }
            }
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        hotPlugJob?.cancel()
        watchdogJob?.cancel()
        LinkWatchdogTracker.setActive(false)
        releaseWakeLock()
        stopSelf(startId)
    }

    override fun onDestroy() {
        hotPlugJob?.cancel()
        watchdogJob?.cancel()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(usbAttachReceiver) }
            receiverRegistered = false
        }
        // Power control restored directly by unmountImage()
        releaseWakeLock()
        LinkWatchdogTracker.setActive(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_SUBTITLE = "extra_subtitle"
        private const val EXTRA_KEEP_ADB = "extra_keep_adb"

        fun start(context: Context, title: String, subtitle: String, keepAdb: Boolean) {
            runCatching {
                val intent = Intent(context, UsbKeepAliveService::class.java).apply {
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_SUBTITLE, subtitle)
                    putExtra(EXTRA_KEEP_ADB, keepAdb)
                }
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, UsbKeepAliveService::class.java))
            }
            LinkWatchdogTracker.setActive(false)
        }
    }
}
