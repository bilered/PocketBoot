package com.pocketboot.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NotificationHelper {
    const val CHANNEL_ID = "pocketboot_usb_status"
    const val NOTIF_ID = 1001
    const val DL_CHANNEL_ID = "pocketboot_turbo_download"
    const val DL_NOTIF_ID = 1002
    const val DL_DONE_NOTIF_ID = 1003
    const val BUILD_CHANNEL_ID = "pocketboot_usb_build"
    const val BUILD_NOTIF_ID = 1004
    const val BUILD_DONE_NOTIF_ID = 1005
    const val ACTION_DL_PAUSE = "com.pocketboot.app.ACTION_DL_PAUSE"
    const val ACTION_DL_RESUME = "com.pocketboot.app.ACTION_DL_RESUME"
    const val ACTION_DL_CANCEL = "com.pocketboot.app.ACTION_DL_CANCEL"
    const val ACTION_BUILD_CANCEL = "com.pocketboot.app.ACTION_BUILD_CANCEL"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Mounted USB Drive", NotificationManager.IMPORTANCE_LOW).apply { description = "Keeps the virtual USB drive active"; setShowBadge(false) })
            nm.createNotificationChannel(NotificationChannel(DL_CHANNEL_ID, "ISO Turbo Downloads", NotificationManager.IMPORTANCE_LOW).apply { description = "Live progress and controls for ISO downloads"; setShowBadge(false) })
            nm.createNotificationChannel(NotificationChannel(BUILD_CHANNEL_ID, "USB Image Builder", NotificationManager.IMPORTANCE_LOW).apply { description = "Live progress and controls for USB image builds"; setShowBadge(false) })
        }
    }

    private fun hasNotifPerm(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun openAppPending(context: Context, reqCode: Int): PendingIntent = PendingIntent.getActivity(
        context, reqCode,
        Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun buildMountedNotification(context: Context, title: String, subtitle: String): Notification {
        ensureChannel(context)
        val unmountPending = PendingIntent.getBroadcast(context, 1, Intent(context, DisconnectReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_usb).setContentTitle(title).setContentText(subtitle)
            .setOngoing(true).setSilent(true).setContentIntent(openAppPending(context, 0))
            .addAction(R.drawable.ic_usb, "Unmount All", unmountPending).build()
    }

    fun buildDownloadNotification(context: Context, state: TurboDownloadState?): Notification {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, DL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_usb).setOngoing(true).setSilent(true).setOnlyAlertOnce(true).setContentIntent(openAppPending(context, 10))
        if (state == null) return builder.setContentTitle("PocketBoot Turbo Downloader").setContentText("Preparing download...").setProgress(0, 0, true).build()
        val pct = (state.progress * 100f).toInt().coerceIn(0, 100)
        val isPostProcessing = state.isVerifying || state.isInjectingMultiBoot
        val title = when {
            state.isVerifying -> "Verifying: ${state.title}"
            state.isInjectingMultiBoot -> "Moving ISO: ${state.title}"
            state.isPaused -> "Paused: ${state.title}"
            else -> "${state.title} ($pct%)"
        }
        val detail = when {
            isPostProcessing -> state.statusText.ifBlank { "Finalizing download..." }
            state.isPaused -> "${UsbGadgetEngine.formatBytes(state.downloadedBytes)} / ${UsbGadgetEngine.formatBytes(state.totalBytes)} · Tap Resume"
            state.totalBytes > 0L -> "${FastIsoDownloader.formatSpeed(state.speedBytesPerSec)} · ETA ${FastIsoDownloader.formatEta(state.etaSeconds)} · ${state.activeThreads}x"
            else -> "${UsbGadgetEngine.formatBytes(state.downloadedBytes)} · ${FastIsoDownloader.formatSpeed(state.speedBytesPerSec)}"
        }
        builder.setContentTitle(title).setContentText(detail).setProgress(100, pct, isPostProcessing || state.totalBytes <= 0L)
        if (state.supportsRange && !isPostProcessing) {
            val togglePending = PendingIntent.getBroadcast(context, 11, Intent(context, TurboDownloadActionReceiver::class.java).setAction(if (state.isPaused) ACTION_DL_RESUME else ACTION_DL_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(R.drawable.ic_usb, if (state.isPaused) "Resume" else "Pause", togglePending)
        }
        val cancelPending = PendingIntent.getBroadcast(context, 12, Intent(context, TurboDownloadActionReceiver::class.java).setAction(ACTION_DL_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return builder.addAction(R.drawable.ic_usb, "Cancel", cancelPending).build()
    }

    fun updateDownloadProgress(context: Context, state: TurboDownloadState) {
        if (!hasNotifPerm(context)) return
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(DL_NOTIF_ID, buildDownloadNotification(context, state)) }
    }

    fun showDownloadComplete(context: Context, title: String, subtitle: String) {
        if (!hasNotifPerm(context)) return
        runCatching {
            ensureChannel(context)
            val notif = NotificationCompat.Builder(context, DL_CHANNEL_ID).setSmallIcon(R.drawable.ic_usb).setContentTitle("Downloaded: $title").setContentText(subtitle).setAutoCancel(true).setContentIntent(openAppPending(context, 13)).build()
            context.getSystemService(NotificationManager::class.java)?.notify(DL_DONE_NOTIF_ID, notif)
        }
    }

    fun showMounted(context: Context, fileName: String, modeLabel: String, extraText: String? = null) {
        runCatching {
            if (!hasNotifPerm(context)) return
            val sub = if (extraText.isNullOrBlank()) "$modeLabel • Keep-Alive active" else "$modeLabel • $extraText"
            context.getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildMountedNotification(context, "Mounted: $fileName", sub))
        }
    }

    fun buildUsbBuildNotification(context: Context, state: UsbBuildState?): Notification {
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, BUILD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_usb)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppPending(context, 20))
        if (state == null) {
            return builder
                .setContentTitle("PocketBoot USB Builder")
                .setContentText("Preparing build...")
                .setProgress(0, 0, true)
                .build()
        }
        val pct = (state.progress.progress * 100f).toInt().coerceIn(0, 100)
        val title = if (state.progress.isIndeterminate) {
            "${state.kind.label}: ${state.progress.stage}"
        } else {
            "${state.kind.label} ($pct%)"
        }
        val detail = state.progress.detail.ifBlank { state.progress.stage }
        val cancelPending = PendingIntent.getBroadcast(
            context,
            21,
            Intent(context, UsbBuildActionReceiver::class.java).setAction(ACTION_BUILD_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return builder
            .setContentTitle(title)
            .setContentText(detail)
            .setProgress(100, pct, state.progress.isIndeterminate)
            .addAction(R.drawable.ic_usb, "Cancel", cancelPending)
            .build()
    }

    fun updateBuildProgress(context: Context, state: UsbBuildState) {
        if (!hasNotifPerm(context)) return
        runCatching { context.getSystemService(NotificationManager::class.java)?.notify(BUILD_NOTIF_ID, buildUsbBuildNotification(context, state)) }
    }

    fun showBuildComplete(context: Context, title: String, subtitle: String) {
        if (!hasNotifPerm(context)) return
        runCatching {
            ensureChannel(context)
            val notif = NotificationCompat.Builder(context, BUILD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_usb)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setAutoCancel(true)
                .setContentIntent(openAppPending(context, 22))
                .build()
            context.getSystemService(NotificationManager::class.java)?.notify(BUILD_DONE_NOTIF_ID, notif)
        }
    }

    fun cancelBuildNotification(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(BUILD_NOTIF_ID) }
    }

    fun cancel(context: Context) { runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID) } }
}

class DisconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { UsbKeepAliveService.stop(context); UsbGadgetEngine.unmountImage(null); NotificationHelper.cancel(context) } finally { pendingResult.finish() }
        }
    }
}

class TurboDownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            NotificationHelper.ACTION_DL_PAUSE -> FastIsoDownloader.pause()
            NotificationHelper.ACTION_DL_RESUME -> FastIsoDownloader.resume()
            NotificationHelper.ACTION_DL_CANCEL -> FastIsoDownloader.cancel(true)
        }
    }
}

class UsbBuildActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == NotificationHelper.ACTION_BUILD_CANCEL) {
            UsbBuildManager.cancelActiveBuild(context)
        }
    }
}
