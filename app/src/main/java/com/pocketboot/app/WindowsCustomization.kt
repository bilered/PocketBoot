package com.pocketboot.app

import java.util.Base64
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
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

enum class WindowsOsKind(val label: String, val isModernWin11: Boolean, val supportsEsd: Boolean) {
    WIN11("Windows 11", true, true), WIN10("Windows 10", false, true),
    WIN81("Windows 8.1", false, false), WIN7("Windows 7", false, false),
    SERVER("Windows Server", false, true), UNKNOWN("Windows", true, true)
}

data class WindowsExperienceConfig(
    val bypassRequirements: Boolean = true, val bypassMicrosoftAccount: Boolean = true,
    val disableDataCollection: Boolean = true, val disableBitLocker: Boolean = true,
    val qolImprovements: Boolean = false, val disableSponsoredApps: Boolean = false,
    val classicContextMenu: Boolean = false, val skipWifiSetup: Boolean = true,
    val createLocalUser: Boolean = false, val localUsername: String = "", val localPassword: String = "",
    val skipOobe: Boolean = false, val useCa2023Bootloaders: Boolean = false,
    val applySkuSiPolicy: Boolean = false, val customPostInstallScriptPath: String = ""
) {
    val sanitizedLocalUsername: String
        get() = if (createLocalUser) WindowsCustomizationEngine.sanitizeWindowsUsername(localUsername) else ""
    /** Post-WinPE tweaks applied via Panther\unattend.xml (specialize/oobeSystem). */
    fun hasPostSetupTweaks(): Boolean = bypassMicrosoftAccount || disableDataCollection || disableBitLocker ||
        qolImprovements || disableSponsoredApps || skipWifiSetup || skipOobe ||
        sanitizedLocalUsername.isNotEmpty() || applySkuSiPolicy || customPostInstallScriptPath.isNotBlank()
    /** Tweaks that require staging PocketBoot_Specialize.cmd / SetupComplete.cmd beyond pure XML. */
    fun needsSpecializeScript(): Boolean = qolImprovements || disableSponsoredApps ||
        applySkuSiPolicy || customPostInstallScriptPath.isNotBlank()
    fun hasUnattendTweaks(): Boolean = bypassRequirements || hasPostSetupTweaks()
    fun hasAnyActive(): Boolean = hasUnattendTweaks() || useCa2023Bootloaders
    fun activeCount(): Int = listOf(bypassRequirements, bypassMicrosoftAccount, disableDataCollection, disableBitLocker,
        qolImprovements, disableSponsoredApps, skipWifiSetup, skipOobe,
        sanitizedLocalUsername.isNotEmpty(), useCa2023Bootloaders, applySkuSiPolicy, customPostInstallScriptPath.isNotBlank()).count { it }
    companion object {
        fun stock(): WindowsExperienceConfig = WindowsExperienceConfig(
            bypassRequirements = false, bypassMicrosoftAccount = false, disableDataCollection = false,
            disableBitLocker = false, qolImprovements = false, skipWifiSetup = false
        )
        fun defaultForOs(osKind: WindowsOsKind): WindowsExperienceConfig = when (osKind) {
            WindowsOsKind.WIN11, WindowsOsKind.UNKNOWN -> WindowsExperienceConfig()
            WindowsOsKind.WIN10 -> WindowsExperienceConfig(bypassRequirements = false, bypassMicrosoftAccount = true, disableDataCollection = true, disableBitLocker = true, qolImprovements = false, skipWifiSetup = true)
            WindowsOsKind.SERVER -> WindowsExperienceConfig(bypassRequirements = false, bypassMicrosoftAccount = false, disableDataCollection = true, disableBitLocker = false, qolImprovements = false, skipWifiSetup = false)
            WindowsOsKind.WIN81, WindowsOsKind.WIN7 -> stock()
        }
    }
}

data class WinUtilTweaksConfig(
    // Essential Tweaks (21)
    val createRestorePoint: Boolean = false,
    val deleteTempFiles: Boolean = false,
    val disableConsumerFeatures: Boolean = false,
    val disableTelemetry: Boolean = false,
    val disableActivityHistory: Boolean = false,
    val disableGameDvr: Boolean = false,
    val disableHibernation: Boolean = false,
    val disableHomegroup: Boolean = false,
    val disableLocationTracking: Boolean = false,
    val disableStorageSense: Boolean = false,
    val disableWifiSense: Boolean = false,
    val disableAutoFolderDiscovery: Boolean = false,
    val disableWpbt: Boolean = false,
    val endTaskWithRightClick: Boolean = false,
    val preventDeviceCompanionApps: Boolean = false,
    val disableBitLockerAuto: Boolean = false,
    val runDiskCleanup: Boolean = false,
    val servicesToManual: Boolean = false,
    val disablePowershell7Telemetry: Boolean = false,
    val revertStartMenuLayout: Boolean = false,
    val debloatDefaultAppx: Boolean = false,
    val excludedAppxPackages: Set<String> = emptySet(),
    // Customize Preferences (21)
    val darkThemeDefault: Boolean = false,
    val disableBingSearch: Boolean = false,
    val disableStartMenuRecommendations: Boolean = false,
    val numLockOnStartup: Boolean = false,
    val verboseBsodAndLogon: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val showFileExtensions: Boolean = false,
    val disableMouseAcceleration: Boolean = false,
    val disableStickyKeys: Boolean = false,
    val taskbarAlignLeft: Boolean = false,
    val hideTaskbarSearch: Boolean = false,
    val hideTaskView: Boolean = false,
    val showBatteryPercentage: Boolean = false,
    val disableLockScreen: Boolean = false,
    val disableLogonAcrylicBlur: Boolean = false,
    val alwaysShowScrollbars: Boolean = false,
    val disableGameMode: Boolean = false,
    val disableWindowSnapping: Boolean = false,
    val disableS0Network: Boolean = false,
    val hideSettingsHome: Boolean = false,
    val mpoMode: String = MPO_DEFAULT, // DEFAULT, DISABLE, ENABLE
    // Advanced Tweaks - Caution (29)
    val classicContextMenu: Boolean = false,
    val disableWindowsUpdates: Boolean = false, // Store-Safe 2099 Pause + Policy Guard
    val securityOnlyUpdates: Boolean = false, // 365-day Feature / 4-day Quality deferral
    val disableWindowsSecurity: Boolean = false,
    val adobeNetworkBlock: Boolean = false,
    val disableIpv6: Boolean = false,
    val preferIpv4OverIpv6: Boolean = false,
    val disableTeredo: Boolean = false,
    val disableBackgroundApps: Boolean = false,
    val disableFullscreenOptimizations: Boolean = false,
    val disableStoreSearch: Boolean = false,
    val disableNotificationCenter: Boolean = false,
    val disableReservedStorage: Boolean = false,
    val disableSearchIndexing: Boolean = false,
    val disableSvchostSplitting: Boolean = false,
    val blockRazerLogitechAutoInstall: Boolean = false,
    val disableUnsignedRdpWarnings: Boolean = false,
    val disableDeliveryOptimization: Boolean = false,
    val enableLongPaths: Boolean = false,
    val visualEffectsBestPerformance: Boolean = false,
    val setHardwareClockUtc: Boolean = false,
    val enableLegacyF8Boot: Boolean = false,
    val removeExplorerHomeGallery: Boolean = false,
    val debloatEdge: Boolean = false,
    val debloatBrave: Boolean = false,
    val removeEdge: Boolean = false,
    val removeOneDrive: Boolean = false,
    val disableAndRemoveWindowsAi: Boolean = false,
    val forceS3Sleep: Boolean = false,
    // Performance Plans (1)
    val ultimatePerformancePlan: Boolean = false,
    // Script Output Option (excluded from activeCount / preset-modified check)
    val addUndoScript: Boolean = false
) {
    fun activeAppxPackages(): List<Pair<String, String>> =
        if (!debloatDefaultAppx) emptyList()
        else DEFAULT_DEBLOAT_APPX.filterNot { it.first in excludedAppxPackages }

    fun essentialActiveCount(): Int = listOf(
        createRestorePoint, deleteTempFiles, disableConsumerFeatures, disableTelemetry,
        disableActivityHistory, disableGameDvr, disableHibernation, disableHomegroup,
        disableLocationTracking, disableStorageSense, disableWifiSense, disableAutoFolderDiscovery,
        disableWpbt, endTaskWithRightClick, preventDeviceCompanionApps, disableBitLockerAuto,
        runDiskCleanup, servicesToManual, disablePowershell7Telemetry, revertStartMenuLayout,
        debloatDefaultAppx && activeAppxPackages().isNotEmpty()
    ).count { it }

    fun preferencesActiveCount(): Int = listOf(
        darkThemeDefault, disableBingSearch, disableStartMenuRecommendations, numLockOnStartup,
        verboseBsodAndLogon, showHiddenFiles, showFileExtensions, disableMouseAcceleration,
        disableStickyKeys, taskbarAlignLeft, hideTaskbarSearch, hideTaskView,
        showBatteryPercentage, disableLockScreen, disableLogonAcrylicBlur, alwaysShowScrollbars,
        disableGameMode, disableWindowSnapping, disableS0Network, hideSettingsHome,
        mpoMode != MPO_DEFAULT
    ).count { it }

    fun advancedActiveCount(): Int = listOf(
        classicContextMenu, disableWindowsUpdates, securityOnlyUpdates, disableWindowsSecurity,
        adobeNetworkBlock, disableIpv6, preferIpv4OverIpv6, disableTeredo,
        disableBackgroundApps, disableFullscreenOptimizations, disableStoreSearch, disableNotificationCenter,
        disableReservedStorage, disableSearchIndexing, disableSvchostSplitting, blockRazerLogitechAutoInstall,
        disableUnsignedRdpWarnings, disableDeliveryOptimization, enableLongPaths, visualEffectsBestPerformance,
        setHardwareClockUtc, enableLegacyF8Boot, removeExplorerHomeGallery, debloatEdge,
        debloatBrave, removeEdge, removeOneDrive, disableAndRemoveWindowsAi, forceS3Sleep
    ).count { it }

    fun performanceActiveCount(): Int = if (ultimatePerformancePlan) 1 else 0

    fun activeCount(): Int = essentialActiveCount() + preferencesActiveCount() + advancedActiveCount() + performanceActiveCount()

    fun hasAnyActive(): Boolean = activeCount() > 0

    fun hasPostOobeTasks(): Boolean = createRestorePoint || deleteTempFiles || runDiskCleanup

    fun normalizedForComparison(): WinUtilTweaksConfig = copy(
        addUndoScript = false,
        excludedAppxPackages = if (debloatDefaultAppx) excludedAppxPackages else emptySet()
    )

    fun toJsonString(): String {
        if (!hasAnyActive() && !addUndoScript) return ""
        return JSONObject().apply {
            if (createRestorePoint) put("createRestorePoint", true)
            if (deleteTempFiles) put("deleteTempFiles", true)
            if (disableConsumerFeatures) put("disableConsumerFeatures", true)
            if (disableTelemetry) put("disableTelemetry", true)
            if (disableActivityHistory) put("disableActivityHistory", true)
            if (disableGameDvr) put("disableGameDvr", true)
            if (disableHibernation) put("disableHibernation", true)
            if (disableHomegroup) put("disableHomegroup", true)
            if (disableLocationTracking) put("disableLocationTracking", true)
            if (disableStorageSense) put("disableStorageSense", true)
            if (disableWifiSense) put("disableWifiSense", true)
            if (disableAutoFolderDiscovery) put("disableAutoFolderDiscovery", true)
            if (disableWpbt) put("disableWpbt", true)
            if (endTaskWithRightClick) put("endTaskWithRightClick", true)
            if (preventDeviceCompanionApps) put("preventDeviceCompanionApps", true)
            if (disableBitLockerAuto) put("disableBitLockerAuto", true)
            if (runDiskCleanup) put("runDiskCleanup", true)
            if (servicesToManual) put("servicesToManual", true)
            if (disablePowershell7Telemetry) put("disablePowershell7Telemetry", true)
            if (revertStartMenuLayout) put("revertStartMenuLayout", true)
            if (debloatDefaultAppx && activeAppxPackages().isNotEmpty()) {
                put("debloatDefaultAppx", true)
                if (excludedAppxPackages.isNotEmpty()) put("excludedAppxPackages", JSONArray(excludedAppxPackages.sorted()))
            }
            if (darkThemeDefault) put("darkThemeDefault", true)
            if (disableBingSearch) put("disableBingSearch", true)
            if (disableStartMenuRecommendations) put("disableStartMenuRecommendations", true)
            if (numLockOnStartup) put("numLockOnStartup", true)
            if (verboseBsodAndLogon) put("verboseBsodAndLogon", true)
            if (showHiddenFiles) put("showHiddenFiles", true)
            if (showFileExtensions) put("showFileExtensions", true)
            if (disableMouseAcceleration) put("disableMouseAcceleration", true)
            if (disableStickyKeys) put("disableStickyKeys", true)
            if (taskbarAlignLeft) put("taskbarAlignLeft", true)
            if (hideTaskbarSearch) put("hideTaskbarSearch", true)
            if (hideTaskView) put("hideTaskView", true)
            if (showBatteryPercentage) put("showBatteryPercentage", true)
            if (disableLockScreen) put("disableLockScreen", true)
            if (disableLogonAcrylicBlur) put("disableLogonAcrylicBlur", true)
            if (alwaysShowScrollbars) put("alwaysShowScrollbars", true)
            if (disableGameMode) put("disableGameMode", true)
            if (disableWindowSnapping) put("disableWindowSnapping", true)
            if (disableS0Network) put("disableS0Network", true)
            if (hideSettingsHome) put("hideSettingsHome", true)
            if (mpoMode != MPO_DEFAULT) put("mpoMode", mpoMode)
            if (classicContextMenu) put("classicContextMenu", true)
            if (disableWindowsUpdates) put("disableWindowsUpdates", true)
            if (securityOnlyUpdates) put("securityOnlyUpdates", true)
            if (disableWindowsSecurity) put("disableWindowsSecurity", true)
            if (adobeNetworkBlock) put("adobeNetworkBlock", true)
            if (disableIpv6) put("disableIpv6", true)
            if (preferIpv4OverIpv6) put("preferIpv4OverIpv6", true)
            if (disableTeredo) put("disableTeredo", true)
            if (disableBackgroundApps) put("disableBackgroundApps", true)
            if (disableFullscreenOptimizations) put("disableFullscreenOptimizations", true)
            if (disableStoreSearch) put("disableStoreSearch", true)
            if (disableNotificationCenter) put("disableNotificationCenter", true)
            if (disableReservedStorage) put("disableReservedStorage", true)
            if (disableSearchIndexing) put("disableSearchIndexing", true)
            if (disableSvchostSplitting) put("disableSvchostSplitting", true)
            if (blockRazerLogitechAutoInstall) put("blockRazerLogitechAutoInstall", true)
            if (disableUnsignedRdpWarnings) put("disableUnsignedRdpWarnings", true)
            if (disableDeliveryOptimization) put("disableDeliveryOptimization", true)
            if (enableLongPaths) put("enableLongPaths", true)
            if (visualEffectsBestPerformance) put("visualEffectsBestPerformance", true)
            if (setHardwareClockUtc) put("setHardwareClockUtc", true)
            if (enableLegacyF8Boot) put("enableLegacyF8Boot", true)
            if (removeExplorerHomeGallery) put("removeExplorerHomeGallery", true)
            if (debloatEdge) put("debloatEdge", true)
            if (debloatBrave) put("debloatBrave", true)
            if (removeEdge) put("removeEdge", true)
            if (removeOneDrive) put("removeOneDrive", true)
            if (disableAndRemoveWindowsAi) put("disableAndRemoveWindowsAi", true)
            if (forceS3Sleep) put("forceS3Sleep", true)
            if (ultimatePerformancePlan) put("ultimatePerformancePlan", true)
            if (addUndoScript) put("addUndoScript", true)
        }.toString()
    }

    companion object {
        const val MPO_DEFAULT = "DEFAULT"
        const val MPO_DISABLE = "DISABLE"
        const val MPO_ENABLE = "ENABLE"

        val DEFAULT_DEBLOAT_APPX: List<Pair<String, String>> = listOf(
            "Microsoft.BingNews" to "Bing News",
            "Microsoft.BingWeather" to "Bing Weather",
            "Microsoft.Clipchamp" to "Clipchamp",
            "Microsoft.GamingApp" to "Xbox App",
            "Microsoft.GetHelp" to "Get Help",
            "Microsoft.Getstarted" to "Microsoft Tips",
            "Microsoft.MicrosoftOfficeHub" to "Microsoft 365 Hub",
            "Microsoft.MicrosoftSolitaireCollection" to "Solitaire Collection",
            "Microsoft.MicrosoftStickyNotes" to "Sticky Notes",
            "Microsoft.People" to "Microsoft People",
            "Microsoft.PowerAutomateDesktop" to "Power Automate",
            "Microsoft.Todos" to "Microsoft To Do",
            "Microsoft.WindowsFeedbackHub" to "Feedback Hub",
            "Microsoft.YourPhone" to "Phone Link"
        )

        fun fromJsonString(raw: String, legacyClassicContextMenu: Boolean = false): WinUtilTweaksConfig {
            if (raw.isBlank()) {
                return if (legacyClassicContextMenu) WinUtilTweaksConfig(classicContextMenu = true) else WinUtilTweaksConfig()
            }
            return runCatching {
                val o = JSONObject(raw)
                val disIpv6 = o.optBoolean("disableIpv6", false)
                val fS3 = o.optBoolean("forceS3Sleep", false)
                val remEdge = o.optBoolean("removeEdge", false)
                val disWu = o.optBoolean("disableWindowsUpdates", false)
                val legacyMpo = o.optBoolean("disableMpo", false)
                val parsedMpo = o.optString("mpoMode", if (legacyMpo) MPO_DISABLE else MPO_DEFAULT)
                    .uppercase()
                    .takeIf { it in setOf(MPO_DEFAULT, MPO_DISABLE, MPO_ENABLE) }
                    ?: MPO_DEFAULT
                val excluded = mutableSetOf<String>()
                o.optJSONArray("excludedAppxPackages")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let { excluded.add(it) }
                    }
                }
                val rawDebloat = o.optBoolean("debloatDefaultAppx", false)
                val effectiveDebloat = rawDebloat && DEFAULT_DEBLOAT_APPX.any { it.first !in excluded }
                WinUtilTweaksConfig(
                    createRestorePoint = o.optBoolean("createRestorePoint", false),
                    deleteTempFiles = o.optBoolean("deleteTempFiles", false),
                    disableConsumerFeatures = o.optBoolean("disableConsumerFeatures", false),
                    disableTelemetry = o.optBoolean("disableTelemetry", false),
                    disableActivityHistory = o.optBoolean("disableActivityHistory", false),
                    disableGameDvr = o.optBoolean("disableGameDvr", false),
                    disableHibernation = o.optBoolean("disableHibernation", false),
                    disableHomegroup = o.optBoolean("disableHomegroup", false),
                    disableLocationTracking = o.optBoolean("disableLocationTracking", false),
                    disableStorageSense = o.optBoolean("disableStorageSense", false),
                    disableWifiSense = o.optBoolean("disableWifiSense", false),
                    disableAutoFolderDiscovery = o.optBoolean("disableAutoFolderDiscovery", false),
                    disableWpbt = o.optBoolean("disableWpbt", false),
                    endTaskWithRightClick = o.optBoolean("endTaskWithRightClick", false),
                    preventDeviceCompanionApps = o.optBoolean("preventDeviceCompanionApps", false),
                    disableBitLockerAuto = o.optBoolean("disableBitLockerAuto", false),
                    runDiskCleanup = o.optBoolean("runDiskCleanup", false),
                    servicesToManual = o.optBoolean("servicesToManual", false),
                    disablePowershell7Telemetry = o.optBoolean("disablePowershell7Telemetry", false),
                    revertStartMenuLayout = o.optBoolean("revertStartMenuLayout", false),
                    debloatDefaultAppx = effectiveDebloat,
                    excludedAppxPackages = if (effectiveDebloat) excluded else emptySet(),
                    darkThemeDefault = o.optBoolean("darkThemeDefault", false),
                    disableBingSearch = o.optBoolean("disableBingSearch", false),
                    disableStartMenuRecommendations = o.optBoolean("disableStartMenuRecommendations", false),
                    numLockOnStartup = o.optBoolean("numLockOnStartup", false),
                    verboseBsodAndLogon = o.optBoolean("verboseBsodAndLogon", false),
                    showHiddenFiles = o.optBoolean("showHiddenFiles", false),
                    showFileExtensions = o.optBoolean("showFileExtensions", false),
                    disableMouseAcceleration = o.optBoolean("disableMouseAcceleration", false),
                    disableStickyKeys = o.optBoolean("disableStickyKeys", false),
                    taskbarAlignLeft = o.optBoolean("taskbarAlignLeft", false),
                    hideTaskbarSearch = o.optBoolean("hideTaskbarSearch", false),
                    hideTaskView = o.optBoolean("hideTaskView", false),
                    showBatteryPercentage = o.optBoolean("showBatteryPercentage", false),
                    disableLockScreen = o.optBoolean("disableLockScreen", false),
                    disableLogonAcrylicBlur = o.optBoolean("disableLogonAcrylicBlur", false),
                    alwaysShowScrollbars = o.optBoolean("alwaysShowScrollbars", false),
                    disableGameMode = o.optBoolean("disableGameMode", false),
                    disableWindowSnapping = o.optBoolean("disableWindowSnapping", false),
                    disableS0Network = !fS3 && o.optBoolean("disableS0Network", false),
                    hideSettingsHome = o.optBoolean("hideSettingsHome", false),
                    mpoMode = parsedMpo,
                    classicContextMenu = o.optBoolean("classicContextMenu", false) || legacyClassicContextMenu,
                    disableWindowsUpdates = disWu,
                    securityOnlyUpdates = !disWu && o.optBoolean("securityOnlyUpdates", false),
                    disableWindowsSecurity = o.optBoolean("disableWindowsSecurity", false),
                    adobeNetworkBlock = o.optBoolean("adobeNetworkBlock", false),
                    disableIpv6 = disIpv6,
                    preferIpv4OverIpv6 = !disIpv6 && o.optBoolean("preferIpv4OverIpv6", false),
                    disableTeredo = o.optBoolean("disableTeredo", false),
                    disableBackgroundApps = o.optBoolean("disableBackgroundApps", false),
                    disableFullscreenOptimizations = o.optBoolean("disableFullscreenOptimizations", false),
                    disableStoreSearch = o.optBoolean("disableStoreSearch", false),
                    disableNotificationCenter = o.optBoolean("disableNotificationCenter", false),
                    disableReservedStorage = o.optBoolean("disableReservedStorage", false),
                    disableSearchIndexing = o.optBoolean("disableSearchIndexing", false),
                    disableSvchostSplitting = o.optBoolean("disableSvchostSplitting", false),
                    blockRazerLogitechAutoInstall = o.optBoolean("blockRazerLogitechAutoInstall", false),
                    disableUnsignedRdpWarnings = o.optBoolean("disableUnsignedRdpWarnings", false),
                    disableDeliveryOptimization = o.optBoolean("disableDeliveryOptimization", false),
                    enableLongPaths = o.optBoolean("enableLongPaths", false),
                    visualEffectsBestPerformance = o.optBoolean("visualEffectsBestPerformance", false),
                    setHardwareClockUtc = o.optBoolean("setHardwareClockUtc", false),
                    enableLegacyF8Boot = o.optBoolean("enableLegacyF8Boot", false),
                    removeExplorerHomeGallery = o.optBoolean("removeExplorerHomeGallery", false),
                    debloatEdge = !remEdge && o.optBoolean("debloatEdge", false),
                    debloatBrave = o.optBoolean("debloatBrave", false),
                    removeEdge = remEdge,
                    removeOneDrive = o.optBoolean("removeOneDrive", false),
                    disableAndRemoveWindowsAi = o.optBoolean("disableAndRemoveWindowsAi", false),
                    forceS3Sleep = fS3,
                    ultimatePerformancePlan = o.optBoolean("ultimatePerformancePlan", false),
                    addUndoScript = o.optBoolean("addUndoScript", false)
                )
            }.getOrDefault(if (legacyClassicContextMenu) WinUtilTweaksConfig(classicContextMenu = true) else WinUtilTweaksConfig())
        }

        // Exact upstream config/preset.json -> Standard (Recommended)
        fun standardPreset() = WinUtilTweaksConfig(
            createRestorePoint = true,
            disableConsumerFeatures = true,
            disableTelemetry = true,
            disableActivityHistory = true,
            disableGameDvr = true,
            disableLocationTracking = true,
            disableWifiSense = true,
            disableAutoFolderDiscovery = true,
            disableWpbt = true,
            endTaskWithRightClick = true,
            preventDeviceCompanionApps = true,
            servicesToManual = true,
            debloatDefaultAppx = true,
            disableDeliveryOptimization = true
        )

        // Exact upstream config/preset.json -> Minimal
        fun minimalPreset() = WinUtilTweaksConfig(
            disableConsumerFeatures = true,
            disableTelemetry = true,
            disableWpbt = true,
            servicesToManual = true
        )

        // Exact upstream config/preset.json -> Advanced
        fun advancedPreset() = standardPreset().copy(
            createRestorePoint = false,
            classicContextMenu = true,
            disableAndRemoveWindowsAi = true,
            debloatEdge = true,
            disableBackgroundApps = true,
            removeExplorerHomeGallery = true
        )

        // Desktop-optimal preset built on Standard baseline
        fun desktopPreset() = standardPreset().copy(
            disableHibernation = true,
            disableMouseAcceleration = true,
            numLockOnStartup = true,
            darkThemeDefault = true,
            showFileExtensions = true,
            disableStickyKeys = true,
            hideSettingsHome = true,
            enableLongPaths = true,
            ultimatePerformancePlan = true
        )

        // Laptop-safe preset built on Standard baseline (keeps Hibernation, trackpad accel, Balanced plan)
        fun laptopSafePreset() = standardPreset().copy(
            disableHibernation = false,
            disableMouseAcceleration = false,
            numLockOnStartup = false,
            disableS0Network = true,
            showBatteryPercentage = true,
            darkThemeDefault = true,
            showFileExtensions = true,
            disableStickyKeys = true,
            hideSettingsHome = true,
            enableLongPaths = true,
            ultimatePerformancePlan = false
        )

        fun matchedBuiltInPresetName(config: WinUtilTweaksConfig): String? {
            val norm = config.normalizedForComparison()
            return when (norm) {
                standardPreset() -> "Standard"
                minimalPreset() -> "Minimal"
                advancedPreset() -> "Advanced"
                desktopPreset() -> "Desktop"
                laptopSafePreset() -> "Laptop"
                else -> null
            }
        }
    }
}

object WindowsCustomizationEngine {

    private val RESERVED_WINDOWS_USERNAMES = setOf(
        "administrator", "guest", "defaultaccount", "wdagutilityaccount",
        "system", "localservice", "networkservice", "none", "users", "everyone",
        "con", "prn", "aux", "nul",
        "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
    )

    private const val ILLEGAL_USERNAME_CHARS = "/\\[]:;|=,+*?<>\"&@"

    private val CURATED_MANUAL_SERVICES = listOf(
        "DiagTrack", "dmwappushservice", "MapsBroker", "WerSvc", "Fax",
        "RemoteRegistry", "RetailDemo", "TrkWks", "WMPNetworkSvc",
        "SharedAccess", "WalletService", "WpcMonSvc", "icssvc", "wisvc"
    )

    private val ADOBE_BLOCK_HOSTS = listOf(
        "activate.adobe.com", "practivate.adobe.com", "ereg.adobe.com",
        "activate.wip3.adobe.com", "wip3.adobe.com", "3dns-3.adobe.com",
        "3dns-2.adobe.com", "adobe-dns.adobe.com", "adobe-dns-2.adobe.com",
        "adobe-dns-3.adobe.com", "ereg.wip3.adobe.com", "activate-sea.adobe.com",
        "wwis-dubc1-vip60.adobe.com", "activate-sjc0.adobe.com", "lmlicenses.wip4.adobe.com",
        "lm.licenses.adobe.com", "na1r.services.adobe.com", "hlrcv.stage.adobe.com",
        "genuine.adobe.com", "prod.adobegenuine.com", "cc-api-data.adobe.io"
    )

    fun filterUsernameInput(raw: String): String = raw
        .filter { ch -> ch.code >= 0x20 && ch !in ILLEGAL_USERNAME_CHARS }
        .take(20)

    fun sanitizeWindowsUsername(raw: String): String {
        val cleaned = filterUsernameInput(raw)
            .trim()
            .trim('.')
            .trim()
            .take(20)
            .trimEnd('.', ' ')
        if (cleaned.isEmpty()) return ""
        if (cleaned.lowercase() in RESERVED_WINDOWS_USERNAMES) return ""
        return cleaned
    }
    fun xmlEscape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
    fun detectWindowsOsKind(nameOrLabel: String): WindowsOsKind {
        val low = nameOrLabel.lowercase()
        return when {
            low.contains("server") -> WindowsOsKind.SERVER
            low.contains("win11") || low.contains("win 11") || low.contains("windows 11") || low.contains("windows_11") || low.contains("26h2") || low.contains("26h1") || low.contains("25h2") || low.contains("24h2") || low.contains("23h2") || low.contains("22621") || low.contains("22631") || low.contains("26100") || low.contains("26200") || low.contains("26300") || low.contains("28000") || low.contains("tiny11") -> WindowsOsKind.WIN11
            low.contains("win10") || low.contains("win 10") || low.contains("windows 10") || low.contains("windows_10") || low.contains("19045") || low.contains("19044") || low.contains("19043") || low.contains("18363") || low.contains("tiny10") -> WindowsOsKind.WIN10
            low.contains("win8") || low.contains("win 8") || low.contains("win_8") || low.contains("windows 8") || low.contains("windows_8") || low.contains("8.1") || low.contains("9600") -> WindowsOsKind.WIN81
            low.contains("win7") || low.contains("win 7") || low.contains("win_7") || low.contains("windows 7") || low.contains("windows_7") || low.contains("7601") || low.contains("7600") -> WindowsOsKind.WIN7
            else -> WindowsOsKind.UNKNOWN
        }
    }
    fun detectProcessorArchitecture(nameOrLabel: String): String {
        val low = nameOrLabel.lowercase()
        return when {
            low.contains("arm64") || low.contains("aarch64") -> "arm64"
            (low.contains("x86") && !low.contains("x86_64") && !low.contains("x86-64")) || low.contains("_x32") || low.contains("-x32") || low.contains(" x32") || low.contains("32bit") || low.contains("32-bit") || low.contains("i386") || low.contains("i686") -> "x86"
            else -> "amd64"
        }
    }

    fun buildSpecializeScript(experience: WindowsExperienceConfig, winUtil: WinUtilTweaksConfig): String {
        val sb = StringBuilder()
        sb.appendLine("@echo off")
        sb.appendLine("setlocal EnableExtensions EnableDelayedExpansion")
        sb.appendLine("cd /d C:\\Windows\\System32 >nul 2>&1")
        sb.appendLine("mkdir \"C:\\Windows\\Logs\" >nul 2>&1")
        sb.appendLine("set \"PB_LOG=C:\\Windows\\Logs\\PocketBoot_Setup.log\"")
        sb.appendLine("set \"PB_FLAG=C:\\Windows\\Setup\\Scripts\\.pb_spec_done\"")
        sb.appendLine("if exist \"%PB_FLAG%\" exit /b 0")
        sb.appendLine("mkdir \"C:\\Windows\\Setup\\Scripts\" >nul 2>&1")
        sb.appendLine("echo [PocketBoot] Specialize configuration started...")
        sb.appendLine("echo [PocketBoot v4.11.0] Specialize started %DATE% %TIME% >> \"%PB_LOG%\"")
        sb.appendLine("set \"PB_BUILD=0\"")
        sb.appendLine("for /f \"tokens=3\" %%B in ('reg query \"HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\" /v CurrentBuildNumber 2^>nul ^| findstr /i CurrentBuildNumber') do set \"PB_BUILD=%%B\"")

        // Deduplicated Pane 1 + Essential HKLM policies
        val effTelemetry = experience.disableDataCollection || winUtil.disableTelemetry
        val effBitLocker = experience.disableBitLocker || winUtil.disableBitLockerAuto
        val effConsumer = experience.disableSponsoredApps || experience.qolImprovements || winUtil.disableConsumerFeatures
        val effClassicMenu = winUtil.classicContextMenu || experience.classicContextMenu
        val effShowExt = experience.qolImprovements || winUtil.showFileExtensions
        val effBingSearch = experience.qolImprovements || winUtil.disableBingSearch
        val effRemoveOneDrive = winUtil.removeOneDrive

        if (experience.bypassMicrosoftAccount) sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\OOBE\" /v BypassNRO /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        if (effBitLocker) sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\BitLocker\" /v PreventDeviceEncryption /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        if (effTelemetry) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\DataCollection\" /v AllowTelemetry /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\DataCollection\" /v AllowTelemetry /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.disableTelemetry) {
                sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\DataCollection\" /v DoNotShowFeedbackNotifications /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("schtasks /Change /TN \"\\Microsoft\\Windows\\Application Experience\\Microsoft Compatibility Appraiser\" /DISABLE >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("schtasks /Change /TN \"\\Microsoft\\Windows\\Customer Experience Improvement Program\\Consolidator\" /DISABLE >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("schtasks /Change /TN \"\\Microsoft\\Windows\\Customer Experience Improvement Program\\UsbCeip\" /DISABLE >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("sc config DiagTrack start= disabled >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("sc config dmwappushservice start= disabled >> \"%PB_LOG%\" 2>&1")
            }
        }
        if (effConsumer) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\CloudContent\" /v DisableWindowsConsumerFeatures /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\CloudContent\" /v DisableSoftLanding /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (experience.qolImprovements) {
            sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Power\" /v HiberbootEnabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsCopilot\" /v TurnOffWindowsCopilot /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\OneDrive\" /v DisableFileSyncNGSC /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Communications\" /v ConfigureChatAutoInstall /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\WindowsUpdate\\Orchestrator\\UScheduler_Oobe\\OutlookUpdate\" /v workCompleted /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Dsh\" /v AllowNewsAndInterests /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\Windows Search\" /v EnableDynamicContentInWSB /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\\AU\" /v NoAutoRebootWithLoggedOnUsers /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (experience.applySkuSiPolicy) sb.appendLine("if exist \"C:\\Windows\\System32\\SecureBootUpdates\\SkuSiPolicy.p7b\" (mountvol S: /S >> \"%PB_LOG%\" 2>&1 & if exist \"S:\\EFI\\Microsoft\\Boot\" copy /y \"C:\\Windows\\System32\\SecureBootUpdates\\SkuSiPolicy.p7b\" \"S:\\EFI\\Microsoft\\Boot\\SkuSiPolicy.p7b\" >> \"%PB_LOG%\" 2>&1 & mountvol S: /D >> \"%PB_LOG%\" 2>&1)")

        // Essential Tweaks (HKLM / Services / System)
        if (winUtil.disableActivityHistory) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\System\" /v EnableActivityFeed /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\System\" /v PublishUserActivities /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\System\" /v UploadUserActivities /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.disableGameDvr) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\GameDVR\" /v AllowGameDVR /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\PolicyManager\\default\\ApplicationManagement\\AllowGameDVR\" /v value /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.disableHibernation) sb.appendLine("powercfg /h off >> \"%PB_LOG%\" 2>&1")
        if (winUtil.disableHomegroup) {
            sb.appendLine("sc config HomeGroupListener start= demand >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("sc config HomeGroupProvider start= demand >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.disableLocationTracking) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\location\" /v Value /t REG_SZ /d Deny /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("sc config lfsvc start= disabled >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.disableStorageSense) sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\StorageSense\" /v AllowStorageSenseGlobal /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.disableWifiSense) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\PolicyManager\\default\\WiFi\\AllowWiFiHotSpotReporting\" /v Value /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\PolicyManager\\default\\WiFi\\AllowAutoConnectToWiFiSenseHotspots\" /v Value /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.disableWpbt) sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\" /v DisableWpbtExecution /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.preventDeviceCompanionApps) sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Device Metadata\" /v PreventDeviceMetadataFromNetwork /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.servicesToManual) {
            val svcList = CURATED_MANUAL_SERVICES.filterNot { winUtil.disableTelemetry && it in setOf("DiagTrack", "dmwappushservice") }.joinToString(" ")
            sb.appendLine("for %%S in ($svcList) do sc config %%S start= demand >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.disablePowershell7Telemetry) {
            sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment\" /v POWERSHELL_TELEMETRY_OPTOUT /t REG_SZ /d 1 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.debloatDefaultAppx) {
            val targets = winUtil.activeAppxPackages().map { it.first }
            if (targets.isNotEmpty()) {
                val regex = targets.joinToString("|") { it.replace(".", "\\.") }
                sb.appendLine("powershell -NoProfile -ExecutionPolicy Bypass -Command \"Get-AppxProvisionedPackage -Online | Where-Object { \$_.DisplayName -match '^($regex)' } | ForEach-Object { Remove-AppxProvisionedPackage -Online -PackageName \$_.PackageName -ErrorAction SilentlyContinue }\" >> \"%PB_LOG%\" 2>&1")
            }
        }

        // Customize Preferences (HKLM)
        if (winUtil.disableLockScreen) sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\Personalization\" /v NoLockScreen /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.disableLogonAcrylicBlur) sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\System\" /v DisableAcrylicBackgroundOnLogon /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.verboseBsodAndLogon) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System\" /v VerboseStatus /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\CrashControl\" /v DisplayParameters /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.hideSettingsHome) sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\" /v SettingsPageVisibility /t REG_SZ /d \"hide:home\" /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.disableS0Network && !winUtil.forceS3Sleep) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Power\\PowerSettings\\f15576e8-98b7-4186-b944-eafa664402d9\" /v ACSettingIndex /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Power\\PowerSettings\\f15576e8-98b7-4186-b944-eafa664402d9\" /v DCSettingIndex /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
        }
        when (winUtil.mpoMode) {
            WinUtilTweaksConfig.MPO_DISABLE -> sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\Dwm\" /v OverlayTestMode /t REG_DWORD /d 5 /f >> \"%PB_LOG%\" 2>&1")
            WinUtilTweaksConfig.MPO_ENABLE -> {
                sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\Windows\\Dwm\" /v OverlayTestMode /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\Dwm\" /v OverlayMinFPS /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            }
        }

        // Advanced Tweaks (HKLM / System)
        if (winUtil.disableWindowsUpdates) {
            // Store-Safe Windows Update Pause (2099 UX Settings + Policy Guard; preserves Store, Game Pass, Optional Features & Drivers)
            sb.appendLine("powershell -NoProfile -ExecutionPolicy Bypass -Command \"\$p='HKLM:\\SOFTWARE\\Microsoft\\WindowsUpdate\\UX\\Settings'; New-Item -Path \$p -Force | Out-Null; \$now=[DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'); \$end='2099-12-31T23:59:59Z'; Set-ItemProperty -Path \$p -Name FlightSettingsMaxPauseDays -Type DWord -Value 30000; Set-ItemProperty -Path \$p -Name PauseFeatureUpdatesStartTime -Type String -Value \$now; Set-ItemProperty -Path \$p -Name PauseQualityUpdatesStartTime -Type String -Value \$now; Set-ItemProperty -Path \$p -Name PauseUpdatesStartTime -Type String -Value \$now; Set-ItemProperty -Path \$p -Name PauseFeatureUpdatesEndTime -Type String -Value \$end; Set-ItemProperty -Path \$p -Name PauseQualityUpdatesEndTime -Type String -Value \$end; Set-ItemProperty -Path \$p -Name PauseUpdatesExpiryTime -Type String -Value \$end\" >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\\AU\" /v NoAutoUpdate /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\\AU\" /v AUOptions /t REG_DWORD /d 2 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\\AU\" /v NoAutoRebootWithLoggedOnUsers /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        } else if (winUtil.securityOnlyUpdates) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\" /v DeferFeatureUpdates /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\" /v DeferFeatureUpdatesPeriodInDays /t REG_DWORD /d 365 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\" /v DeferQualityUpdates /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\" /v DeferQualityUpdatesPeriodInDays /t REG_DWORD /d 4 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\" /v BranchReadinessLevel /t REG_DWORD /d 20 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.disableWindowsSecurity) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender\" /v DisableAntiSpyware /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender\\Real-Time Protection\" /v DisableRealtimeMonitoring /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender\\Real-Time Protection\" /v DisableBehaviorMonitoring /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender\\Real-Time Protection\" /v DisableOnAccessProtection /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender\\Real-Time Protection\" /v DisableScanOnRealtimeEnable /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender\\Spynet\" /v SpynetReporting /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender\\Spynet\" /v SubmitSamplesConsent /t REG_DWORD /d 2 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender Security Center\\Systray\" /v HideSystray /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.adobeNetworkBlock) {
            sb.appendLine("findstr /c:\"# PocketBoot Adobe Block\" \"C:\\Windows\\System32\\drivers\\etc\\hosts\" >nul 2>&1 || (")
            sb.appendLine("  echo.>> \"C:\\Windows\\System32\\drivers\\etc\\hosts\"")
            sb.appendLine("  echo # PocketBoot Adobe Block START>> \"C:\\Windows\\System32\\drivers\\etc\\hosts\"")
            ADOBE_BLOCK_HOSTS.forEach { host ->
                sb.appendLine("  echo 0.0.0.0 $host>> \"C:\\Windows\\System32\\drivers\\etc\\hosts\"")
            }
            sb.appendLine("  echo # PocketBoot Adobe Block END>> \"C:\\Windows\\System32\\drivers\\etc\\hosts\"")
            sb.appendLine(")")
        }
        if (winUtil.disableIpv6) sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Services\\Tcpip6\\Parameters\" /v DisabledComponents /t REG_DWORD /d 255 /f >> \"%PB_LOG%\" 2>&1")
        else if (winUtil.preferIpv4OverIpv6) sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Services\\Tcpip6\\Parameters\" /v DisabledComponents /t REG_DWORD /d 32 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.disableTeredo) sb.appendLine("netsh interface teredo set state disabled >> \"%PB_LOG%\" 2>&1")
        if (winUtil.disableBackgroundApps) sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\AppPrivacy\" /v LetAppsRunInBackground /t REG_DWORD /d 2 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.disableStoreSearch) sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\Explorer\" /v NoUseStoreOpenWith /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.disableReservedStorage) sb.appendLine("dism /Online /Set-ReservedStorageState /State:Disabled >> \"%PB_LOG%\" 2>&1")
        if (winUtil.disableSearchIndexing) sb.appendLine("sc config WSearch start= disabled >> \"%PB_LOG%\" 2>&1")
        if (winUtil.disableSvchostSplitting) sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\" /v SvcHostSplitThresholdInKB /t REG_DWORD /d 67108864 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.blockRazerLogitechAutoInstall) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\DriverSearching\" /v SearchOrderConfig /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Device Metadata\" /v PreventDeviceMetadataFromNetwork /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.disableUnsignedRdpWarnings) sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows NT\\Terminal Services\\Client\" /v AuthenticationLevelOverride /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.disableDeliveryOptimization) sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\DeliveryOptimization\" /v DODownloadMode /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.enableLongPaths) sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\FileSystem\" /v LongPathsEnabled /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.setHardwareClockUtc) sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\TimeZoneInformation\" /v RealTimeIsUniversal /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.enableLegacyF8Boot) sb.appendLine("bcdedit /set {default} bootmenupolicy legacy >> \"%PB_LOG%\" 2>&1")
        if (winUtil.removeExplorerHomeGallery) sb.appendLine("if !PB_BUILD! GEQ 22000 (reg delete \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Desktop\\NameSpace\\{e88865ea-0e1c-4e20-9aa6-edcd0212c87c}\" /f >> \"%PB_LOG%\" 2>&1 & reg delete \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Desktop\\NameSpace\\{f874310e-b6b7-47dc-bc84-b9e6b38f5903}\" /f >> \"%PB_LOG%\" 2>&1)")
        if (winUtil.debloatEdge && !winUtil.removeEdge) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Edge\" /v HubsSidebarEnabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Edge\" /v EdgeShoppingAssistantEnabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Edge\" /v StartupBoostEnabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Edge\" /v BackgroundModeEnabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Edge\" /v ShowMicrosoftRewards /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.debloatBrave) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\BraveSoftware\\Brave\" /v BraveRewardsDisabled /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\BraveSoftware\\Brave\" /v BraveWalletDisabled /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\BraveSoftware\\Brave\" /v BraveVPNDisabled /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\BraveSoftware\\Brave\" /v BraveAIChatEnabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\BraveSoftware\\Brave\" /v TorDisabled /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.removeEdge) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\EdgeUpdate\" /v DoNotUpdateToEdgeWithChromium /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("for /d %%D in (\"C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\*\") do if exist \"%%D\\Installer\\setup.exe\" \"%%D\\Installer\\setup.exe\" --uninstall --system-level --verbose-logging --force-uninstall >> \"%PB_LOG%\" 2>&1")
        }
        if (effRemoveOneDrive) {
            sb.appendLine("if exist \"C:\\Windows\\SysWOW64\\OneDriveSetup.exe\" \"C:\\Windows\\SysWOW64\\OneDriveSetup.exe\" /uninstall >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("if exist \"C:\\Windows\\System32\\OneDriveSetup.exe\" \"C:\\Windows\\System32\\OneDriveSetup.exe\" /uninstall >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.disableAndRemoveWindowsAi) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsAI\" /v DisableAIDataAnalysis /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsAI\" /v TurnOffSavingSnapshots /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsCopilot\" /v TurnOffWindowsCopilot /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Policies\\WindowsNotepad\" /v DisableAIFeatures /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\Paint\" /v DisableCocreator /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("sc query WSAIFabricSvc >nul 2>&1 && sc config WSAIFabricSvc start= disabled >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("if !PB_BUILD! GEQ 22000 (")
            sb.appendLine("  dism /Online /Disable-Feature /FeatureName:Recall /NoRestart >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("  powershell -NoProfile -ExecutionPolicy Bypass -Command \"Get-AppxProvisionedPackage -Online | Where-Object { \$_.DisplayName -match 'Copilot|Recall' } | ForEach-Object { Remove-AppxProvisionedPackage -Online -PackageName \$_.PackageName -ErrorAction SilentlyContinue }\" >> \"%PB_LOG%\" 2>&1")
            sb.appendLine(")")
        }
        if (winUtil.forceS3Sleep) sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Power\" /v PlatformAoAcOverride /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
        if (winUtil.ultimatePerformancePlan) {
            sb.appendLine("powercfg -duplicatescheme e9a42b02-d5df-448d-aa00-03f14749eb61 >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("for /f \"tokens=4\" %%G in ('powercfg /list ^| findstr /i \"Ultimate Performance\"') do powercfg /setactive %%G >> \"%PB_LOG%\" 2>&1")
        }

        // Default User hive tweaks (C:\Users\Default\NTUSER.DAT)
        val hasDefaultUserTweaks = experience.qolImprovements || effConsumer || effClassicMenu || effShowExt || effBingSearch ||
            winUtil.endTaskWithRightClick || winUtil.disableAutoFolderDiscovery || winUtil.revertStartMenuLayout ||
            winUtil.darkThemeDefault || winUtil.disableStartMenuRecommendations || winUtil.showHiddenFiles ||
            winUtil.disableMouseAcceleration || winUtil.numLockOnStartup || winUtil.disableStickyKeys ||
            winUtil.taskbarAlignLeft || winUtil.hideTaskbarSearch || winUtil.hideTaskView || winUtil.showBatteryPercentage ||
            winUtil.alwaysShowScrollbars || winUtil.disableGameMode || winUtil.disableWindowSnapping ||
            winUtil.visualEffectsBestPerformance || winUtil.disableNotificationCenter || winUtil.disableFullscreenOptimizations ||
            winUtil.disableGameDvr || effRemoveOneDrive
        if (hasDefaultUserTweaks) {
            sb.appendLine("reg load \"HKU\\PB_DEF\" \"C:\\Users\\Default\\NTUSER.DAT\" >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("if !ERRORLEVEL! EQU 0 (")
            if (experience.qolImprovements || effRemoveOneDrive) sb.appendLine("  reg delete \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Run\" /v OneDriveSetup /f >> \"%PB_LOG%\" 2>&1")
            if (effConsumer) {
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\ContentDeliveryManager\" /v SilentInstalledAppsEnabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\ContentDeliveryManager\" /v SubscribedContent-338388Enabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\ContentDeliveryManager\" /v SubscribedContent-338389Enabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\UserProfileEngagement\" /v ScoobeSystemSettingEnabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            }
            if (effShowExt) sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v HideFileExt /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            if (effBingSearch) {
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Policies\\Microsoft\\Windows\\Explorer\" /v DisableSearchBoxSuggestions /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Search\" /v BingSearchEnabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            }
            if (experience.qolImprovements) {
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v LaunchTo /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v ShowCopilotButton /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v TaskbarMn /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v TaskbarDa /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\SearchSettings\" /v IsDynamicSearchBoxEnabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            }
            if (winUtil.disableStartMenuRecommendations) {
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v Start_IrisRecommendations /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v Start_TrackDocs /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            }
            if (effClassicMenu) sb.appendLine("  if !PB_BUILD! GEQ 22000 reg add \"HKU\\PB_DEF\\Software\\Classes\\CLSID\\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}\\InprocServer32\" /f /ve /d \"\" >> \"%PB_LOG%\" 2>&1")
            if (winUtil.endTaskWithRightClick) sb.appendLine("  if !PB_BUILD! GEQ 22000 reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\\TaskbarDeveloperSettings\" /v TaskbarEndTask /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.disableAutoFolderDiscovery) sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Classes\\Local Settings\\Software\\Microsoft\\Windows\\Shell\\Bags\\AllFolders\\Shell\" /v FolderType /t REG_SZ /d NotSpecified /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.revertStartMenuLayout) sb.appendLine("  if !PB_BUILD! GEQ 22000 reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v Start_Layout /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.darkThemeDefault) {
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\" /v AppsUseLightTheme /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\" /v SystemUsesLightTheme /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            }
            if (winUtil.showHiddenFiles) sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v Hidden /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.disableMouseAcceleration) {
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Control Panel\\Mouse\" /v MouseSpeed /t REG_SZ /d 0 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Control Panel\\Mouse\" /v MouseThreshold1 /t REG_SZ /d 0 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Control Panel\\Mouse\" /v MouseThreshold2 /t REG_SZ /d 0 /f >> \"%PB_LOG%\" 2>&1")
            }
            if (winUtil.numLockOnStartup) {
                sb.appendLine("  reg add \"HKU\\PB_DEF\\Control Panel\\Keyboard\" /v InitialKeyboardIndicators /t REG_SZ /d 2 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\.DEFAULT\\Control Panel\\Keyboard\" /v InitialKeyboardIndicators /t REG_SZ /d 2147483650 /f >> \"%PB_LOG%\" 2>&1")
            }
            if (winUtil.disableStickyKeys) sb.appendLine("  reg add \"HKU\\PB_DEF\\Control Panel\\Accessibility\\StickyKeys\" /v Flags /t REG_SZ /d 506 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.taskbarAlignLeft) sb.appendLine("  if !PB_BUILD! GEQ 22000 reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v TaskbarAl /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.hideTaskbarSearch) sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Search\" /v SearchboxTaskbarMode /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.hideTaskView) sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v ShowTaskViewButton /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.showBatteryPercentage) sb.appendLine("  if !PB_BUILD! GEQ 22000 reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v ShowBatteryPercentage /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.alwaysShowScrollbars) sb.appendLine("  reg add \"HKU\\PB_DEF\\Control Panel\\Accessibility\" /v DynamicScrollbars /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.disableGameMode) sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\GameBar\" /v AutoGameModeEnabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.disableGameDvr) sb.appendLine("  reg add \"HKU\\PB_DEF\\System\\GameConfigStore\" /v GameDVR_Enabled /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.disableFullscreenOptimizations) {
                sb.appendLine("  reg add \"HKU\\PB_DEF\\System\\GameConfigStore\" /v GameDVR_FSEBehaviorMode /t REG_DWORD /d 2 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\System\\GameConfigStore\" /v GameDVR_HonorUserFSEBehaviorMode /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
                sb.appendLine("  reg add \"HKU\\PB_DEF\\System\\GameConfigStore\" /v GameDVR_DXGIHonorFSEWindowsCompatible /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            }
            if (winUtil.disableWindowSnapping) sb.appendLine("  reg add \"HKU\\PB_DEF\\Control Panel\\Desktop\" /v WindowArrangementActive /t REG_SZ /d 0 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.visualEffectsBestPerformance) sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VisualEffects\" /v VisualFXSetting /t REG_DWORD /d 2 /f >> \"%PB_LOG%\" 2>&1")
            if (winUtil.disableNotificationCenter) sb.appendLine("  reg add \"HKU\\PB_DEF\\Software\\Policies\\Microsoft\\Windows\\Explorer\" /v DisableNotificationCenter /t REG_DWORD /d 1 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("  reg unload \"HKU\\PB_DEF\" >> \"%PB_LOG%\" 2>&1")
            sb.appendLine(")")
        }

        // Drop optional Undo script to Public Desktop only when explicitly enabled AND tweaks are active
        if (winUtil.addUndoScript && winUtil.hasAnyActive()) {
            sb.appendLine("if exist \"C:\\Windows\\Setup\\Scripts\\Undo_PocketBoot_Tweaks.cmd\" (")
            sb.appendLine("  mkdir \"C:\\Users\\Public\\Desktop\" >nul 2>&1")
            sb.appendLine("  copy /y \"C:\\Windows\\Setup\\Scripts\\Undo_PocketBoot_Tweaks.cmd\" \"C:\\Users\\Public\\Desktop\\Undo_PocketBoot_Tweaks.cmd\" >> \"%PB_LOG%\" 2>&1")
            sb.appendLine(")")
        }

        // OEM BIOS-keyed laptop safeguard: register SetupComplete.cmd under FirstBoot\PostOobe
        sb.appendLine("if exist \"C:\\Windows\\Setup\\Scripts\\SetupComplete.cmd\" reg add \"HKLM\\SYSTEM\\Setup\\FirstBoot\\PostOobe\" /v \"00\" /t REG_SZ /d \"cmd.exe /c C:\\Windows\\Setup\\Scripts\\SetupComplete.cmd & exit /b 0\" /f >> \"%PB_LOG%\" 2>&1")
        sb.appendLine("echo done > \"%PB_FLAG%\"")
        sb.appendLine("echo [PocketBoot v4.11.0] Specialize finished %DATE% %TIME% >> \"%PB_LOG%\"")
        sb.appendLine("exit /b 0")
        return sb.toString().replace("\r\n", "\n").replace("\n", "\r\n")
    }

    fun buildSetupCompleteScript(
        runComponentCleanup: Boolean = false,
        hasCustomScript: Boolean = false,
        customScriptFileName: String = "",
        winUtil: WinUtilTweaksConfig = WinUtilTweaksConfig()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("@echo off")
        sb.appendLine("setlocal EnableExtensions EnableDelayedExpansion")
        sb.appendLine("cd /d C:\\Windows\\System32 >nul 2>&1")
        sb.appendLine("mkdir \"C:\\Windows\\Logs\" >nul 2>&1")
        sb.appendLine("set \"PB_LOG=C:\\Windows\\Logs\\PocketBoot_Setup.log\"")
        sb.appendLine("set \"PB_SC_FLAG=C:\\Windows\\Setup\\Scripts\\.pb_setupcomplete_done\"")
        sb.appendLine("if exist \"%PB_SC_FLAG%\" exit /b 0")
        sb.appendLine("mkdir \"C:\\Windows\\Setup\\Scripts\" >nul 2>&1")
        sb.appendLine("echo done > \"%PB_SC_FLAG%\"")
        sb.appendLine("reg delete \"HKLM\\SYSTEM\\Setup\\FirstBoot\\PostOobe\" /v \"00\" /f >nul 2>&1")
        sb.appendLine("if not exist \"C:\\Windows\\Setup\\Scripts\\.pb_spec_done\" if exist \"C:\\Windows\\Setup\\Scripts\\PocketBoot_Specialize.cmd\" call \"C:\\Windows\\Setup\\Scripts\\PocketBoot_Specialize.cmd\"")
        if (winUtil.addUndoScript && winUtil.hasAnyActive()) {
            sb.appendLine("if not exist \"C:\\Users\\Public\\Desktop\\Undo_PocketBoot_Tweaks.cmd\" if exist \"C:\\Windows\\Setup\\Scripts\\Undo_PocketBoot_Tweaks.cmd\" copy /y \"C:\\Windows\\Setup\\Scripts\\Undo_PocketBoot_Tweaks.cmd\" \"C:\\Users\\Public\\Desktop\\Undo_PocketBoot_Tweaks.cmd\" >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.createRestorePoint) {
            sb.appendLine("echo [PocketBoot] Creating initial System Restore Point... >> \"%PB_LOG%\"")
            sb.appendLine("sc config VSS start= demand >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("net start VSS >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\SystemRestore\" /v SystemRestorePointCreationFrequency /t REG_DWORD /d 0 /f >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("powershell -NoProfile -ExecutionPolicy Bypass -Command \"Enable-ComputerRestore -Drive 'C:\\' -ErrorAction SilentlyContinue; Checkpoint-Computer -Description 'PocketBoot Fresh Install' -RestorePointType 'MODIFY_SETTINGS' -ErrorAction SilentlyContinue\" >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\SystemRestore\" /v SystemRestorePointCreationFrequency /f >> \"%PB_LOG%\" 2>&1")
        }
        if (winUtil.deleteTempFiles) {
            sb.appendLine("del /f /s /q \"%TEMP%\\*\" >nul 2>&1")
            sb.appendLine("del /f /s /q \"C:\\Windows\\Temp\\*\" >nul 2>&1")
        }
        if (winUtil.runDiskCleanup || runComponentCleanup) {
            // Headless cleanup (never invokes interactive cleanmgr GUI in Session 0)
            sb.appendLine("dism /Online /Cleanup-Image /StartComponentCleanup >> \"%PB_LOG%\" 2>&1")
            sb.appendLine("del /f /s /q \"C:\\Windows\\Prefetch\\*\" >nul 2>&1")
            sb.appendLine("del /f /s /q \"C:\\Windows\\SoftwareDistribution\\Download\\*\" >nul 2>&1")
        }
        if (hasCustomScript && customScriptFileName.isNotBlank()) {
            if (customScriptFileName.lowercase().endsWith(".ps1")) {
                sb.appendLine("if exist \"C:\\Windows\\Setup\\Custom\\$customScriptFileName\" powershell -NoProfile -ExecutionPolicy Bypass -File \"C:\\Windows\\Setup\\Custom\\$customScriptFileName\" >> \"%PB_LOG%\" 2>&1")
            } else {
                sb.appendLine("if exist \"C:\\Windows\\Setup\\Custom\\$customScriptFileName\" call \"C:\\Windows\\Setup\\Custom\\$customScriptFileName\" >> \"%PB_LOG%\" 2>&1")
            }
        }
        sb.appendLine("if exist \"C:\\Windows\\Setup\\Custom\" rmdir /s /q \"C:\\Windows\\Setup\\Custom\" >nul 2>&1")
        sb.appendLine("echo [PocketBoot v4.11.0] SetupComplete finished %DATE% %TIME% >> \"%PB_LOG%\"")
        sb.appendLine("start \"\" /b cmd /c \"ping -n 4 127.0.0.1 >nul & rmdir /s /q C:\\Windows\\Setup\\Scripts >nul 2>&1\"")
        sb.appendLine("exit /b 0")
        return sb.toString().replace("\r\n", "\n").replace("\n", "\r\n")
    }

    fun buildUndoScript(experience: WindowsExperienceConfig, winUtil: WinUtilTweaksConfig): String {
        val sb = StringBuilder()
        sb.appendLine("@echo off")
        sb.appendLine("setlocal EnableExtensions")
        sb.appendLine("net session >nul 2>&1 || (")
        sb.appendLine("  echo Requesting Administrator privileges...")
        sb.appendLine("  powershell -NoProfile -ExecutionPolicy Bypass -Command \"Start-Process -FilePath '%~f0' -Verb RunAs\"")
        sb.appendLine("  exit /b 0")
        sb.appendLine(")")
        sb.appendLine("echo =======================================================")
        sb.appendLine("echo   PocketBoot v4.11.0 - Revert Applied System Tweaks")
        sb.appendLine("echo =======================================================")
        sb.appendLine("echo.")
        if (winUtil.disableConsumerFeatures || experience.disableSponsoredApps) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\CloudContent\" /v DisableWindowsConsumerFeatures /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\CloudContent\" /v DisableSoftLanding /f >nul 2>&1")
            sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\ContentDeliveryManager\" /v SilentInstalledAppsEnabled /t REG_DWORD /d 1 /f >nul 2>&1")
        }
        if (winUtil.disableTelemetry || experience.disableDataCollection) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\DataCollection\" /v AllowTelemetry /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\DataCollection\" /v AllowTelemetry /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\DataCollection\" /v DoNotShowFeedbackNotifications /f >nul 2>&1")
            sb.appendLine("schtasks /Change /TN \"\\Microsoft\\Windows\\Application Experience\\Microsoft Compatibility Appraiser\" /ENABLE >nul 2>&1")
            sb.appendLine("schtasks /Change /TN \"\\Microsoft\\Windows\\Customer Experience Improvement Program\\Consolidator\" /ENABLE >nul 2>&1")
            sb.appendLine("schtasks /Change /TN \"\\Microsoft\\Windows\\Customer Experience Improvement Program\\UsbCeip\" /ENABLE >nul 2>&1")
            sb.appendLine("sc config DiagTrack start= auto >nul 2>&1")
            sb.appendLine("sc config dmwappushservice start= demand >nul 2>&1")
        }
        if (winUtil.disableBitLockerAuto || experience.disableBitLocker) {
            sb.appendLine("reg delete \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\BitLocker\" /v PreventDeviceEncryption /f >nul 2>&1")
        }
        if (winUtil.disableActivityHistory) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\System\" /v EnableActivityFeed /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\System\" /v PublishUserActivities /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\System\" /v UploadUserActivities /f >nul 2>&1")
        }
        if (winUtil.disableGameDvr) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\GameDVR\" /v AllowGameDVR /f >nul 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\PolicyManager\\default\\ApplicationManagement\\AllowGameDVR\" /v value /t REG_DWORD /d 1 /f >nul 2>&1")
            sb.appendLine("reg add \"HKCU\\System\\GameConfigStore\" /v GameDVR_Enabled /t REG_DWORD /d 1 /f >nul 2>&1")
        }
        if (winUtil.disableHibernation) sb.appendLine("powercfg /h on >nul 2>&1")
        if (winUtil.disableHomegroup) {
            sb.appendLine("sc config HomeGroupListener start= demand >nul 2>&1")
            sb.appendLine("sc config HomeGroupProvider start= demand >nul 2>&1")
        }
        if (winUtil.disableLocationTracking) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\CapabilityAccessManager\\ConsentStore\\location\" /v Value /t REG_SZ /d Allow /f >nul 2>&1")
            sb.appendLine("sc config lfsvc start= demand >nul 2>&1")
        }
        if (winUtil.disableStorageSense) sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\StorageSense\" /v AllowStorageSenseGlobal /f >nul 2>&1")
        if (winUtil.disableWifiSense) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\PolicyManager\\default\\WiFi\\AllowWiFiHotSpotReporting\" /v Value /t REG_DWORD /d 1 /f >nul 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\PolicyManager\\default\\WiFi\\AllowAutoConnectToWiFiSenseHotspots\" /v Value /t REG_DWORD /d 1 /f >nul 2>&1")
        }
        if (winUtil.disableAutoFolderDiscovery) sb.appendLine("reg delete \"HKCU\\Software\\Classes\\Local Settings\\Software\\Microsoft\\Windows\\Shell\\Bags\\AllFolders\\Shell\" /v FolderType /f >nul 2>&1")
        if (winUtil.disableWpbt) sb.appendLine("reg delete \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\" /v DisableWpbtExecution /f >nul 2>&1")
        if (winUtil.endTaskWithRightClick) sb.appendLine("reg delete \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\\TaskbarDeveloperSettings\" /v TaskbarEndTask /f >nul 2>&1")
        if (winUtil.preventDeviceCompanionApps || winUtil.blockRazerLogitechAutoInstall) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Device Metadata\" /v PreventDeviceMetadataFromNetwork /f >nul 2>&1")
        }
        if (winUtil.blockRazerLogitechAutoInstall) sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\DriverSearching\" /v SearchOrderConfig /f >nul 2>&1")
        if (winUtil.servicesToManual) {
            if (!winUtil.disableTelemetry && !experience.disableDataCollection) {
                sb.appendLine("sc config DiagTrack start= auto >nul 2>&1")
            }
            sb.appendLine("sc config MapsBroker start= delayed-auto >nul 2>&1")
            sb.appendLine("sc config TrkWks start= auto >nul 2>&1")
        }
        if (winUtil.disablePowershell7Telemetry) sb.appendLine("reg delete \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment\" /v POWERSHELL_TELEMETRY_OPTOUT /f >nul 2>&1")
        if (winUtil.revertStartMenuLayout) sb.appendLine("reg delete \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v Start_Layout /f >nul 2>&1")
        if (winUtil.darkThemeDefault) {
            sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\" /v AppsUseLightTheme /t REG_DWORD /d 1 /f >nul 2>&1")
            sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize\" /v SystemUsesLightTheme /t REG_DWORD /d 1 /f >nul 2>&1")
        }
        if (winUtil.disableBingSearch) {
            sb.appendLine("reg delete \"HKCU\\Software\\Policies\\Microsoft\\Windows\\Explorer\" /v DisableSearchBoxSuggestions /f >nul 2>&1")
            sb.appendLine("reg delete \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Search\" /v BingSearchEnabled /f >nul 2>&1")
        }
        if (winUtil.disableStartMenuRecommendations) {
            sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v Start_IrisRecommendations /t REG_DWORD /d 1 /f >nul 2>&1")
            sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v Start_TrackDocs /t REG_DWORD /d 1 /f >nul 2>&1")
        }
        if (winUtil.numLockOnStartup) {
            sb.appendLine("reg add \"HKCU\\Control Panel\\Keyboard\" /v InitialKeyboardIndicators /t REG_SZ /d 0 /f >nul 2>&1")
            sb.appendLine("reg add \"HKU\\.DEFAULT\\Control Panel\\Keyboard\" /v InitialKeyboardIndicators /t REG_SZ /d 2147483648 /f >nul 2>&1")
        }
        if (winUtil.verboseBsodAndLogon) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System\" /v VerboseStatus /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\CrashControl\" /v DisplayParameters /f >nul 2>&1")
        }
        if (winUtil.showHiddenFiles) sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v Hidden /t REG_DWORD /d 2 /f >nul 2>&1")
        if (winUtil.showFileExtensions) sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v HideFileExt /t REG_DWORD /d 1 /f >nul 2>&1")
        if (winUtil.disableMouseAcceleration) {
            sb.appendLine("reg add \"HKCU\\Control Panel\\Mouse\" /v MouseSpeed /t REG_SZ /d 1 /f >nul 2>&1")
            sb.appendLine("reg add \"HKCU\\Control Panel\\Mouse\" /v MouseThreshold1 /t REG_SZ /d 6 /f >nul 2>&1")
            sb.appendLine("reg add \"HKCU\\Control Panel\\Mouse\" /v MouseThreshold2 /t REG_SZ /d 10 /f >nul 2>&1")
        }
        if (winUtil.disableStickyKeys) sb.appendLine("reg add \"HKCU\\Control Panel\\Accessibility\\StickyKeys\" /v Flags /t REG_SZ /d 510 /f >nul 2>&1")
        if (winUtil.taskbarAlignLeft) sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v TaskbarAl /t REG_DWORD /d 1 /f >nul 2>&1")
        if (winUtil.hideTaskbarSearch) sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Search\" /v SearchboxTaskbarMode /t REG_DWORD /d 1 /f >nul 2>&1")
        if (winUtil.hideTaskView) sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v ShowTaskViewButton /t REG_DWORD /d 1 /f >nul 2>&1")
        if (winUtil.showBatteryPercentage) sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Advanced\" /v ShowBatteryPercentage /t REG_DWORD /d 0 /f >nul 2>&1")
        if (winUtil.disableLockScreen) sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\Personalization\" /v NoLockScreen /f >nul 2>&1")
        if (winUtil.disableLogonAcrylicBlur) sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\System\" /v DisableAcrylicBackgroundOnLogon /f >nul 2>&1")
        if (winUtil.alwaysShowScrollbars) sb.appendLine("reg add \"HKCU\\Control Panel\\Accessibility\" /v DynamicScrollbars /t REG_DWORD /d 1 /f >nul 2>&1")
        if (winUtil.disableGameMode) sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\GameBar\" /v AutoGameModeEnabled /t REG_DWORD /d 1 /f >nul 2>&1")
        if (winUtil.disableWindowSnapping) sb.appendLine("reg add \"HKCU\\Control Panel\\Desktop\" /v WindowArrangementActive /t REG_SZ /d 1 /f >nul 2>&1")
        if (winUtil.disableS0Network) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Power\\PowerSettings\\f15576e8-98b7-4186-b944-eafa664402d9\" /v ACSettingIndex /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Power\\PowerSettings\\f15576e8-98b7-4186-b944-eafa664402d9\" /v DCSettingIndex /f >nul 2>&1")
        }
        if (winUtil.hideSettingsHome) sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer\" /v SettingsPageVisibility /f >nul 2>&1")
        if (winUtil.mpoMode != WinUtilTweaksConfig.MPO_DEFAULT) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\Windows\\Dwm\" /v OverlayTestMode /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\Windows\\Dwm\" /v OverlayMinFPS /f >nul 2>&1")
        }
        if (winUtil.classicContextMenu) sb.appendLine("reg delete \"HKCU\\Software\\Classes\\CLSID\\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}\" /f >nul 2>&1")
        if (winUtil.disableWindowsUpdates) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\WindowsUpdate\\UX\\Settings\" /v FlightSettingsMaxPauseDays /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\WindowsUpdate\\UX\\Settings\" /v PauseFeatureUpdatesStartTime /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\WindowsUpdate\\UX\\Settings\" /v PauseQualityUpdatesStartTime /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\WindowsUpdate\\UX\\Settings\" /v PauseUpdatesStartTime /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\WindowsUpdate\\UX\\Settings\" /v PauseFeatureUpdatesEndTime /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\WindowsUpdate\\UX\\Settings\" /v PauseQualityUpdatesEndTime /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\WindowsUpdate\\UX\\Settings\" /v PauseUpdatesExpiryTime /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\\AU\" /v NoAutoUpdate /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\\AU\" /v AUOptions /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\\AU\" /v NoAutoRebootWithLoggedOnUsers /f >nul 2>&1")
        }
        if (winUtil.securityOnlyUpdates) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\" /v DeferFeatureUpdates /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\" /v DeferFeatureUpdatesPeriodInDays /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\" /v DeferQualityUpdates /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\" /v DeferQualityUpdatesPeriodInDays /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\" /v BranchReadinessLevel /f >nul 2>&1")
        }
        if (winUtil.disableWindowsSecurity) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender\" /v DisableAntiSpyware /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender\\Real-Time Protection\" /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender\\Spynet\" /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows Defender Security Center\\Systray\" /v HideSystray /f >nul 2>&1")
        }
        if (winUtil.adobeNetworkBlock) {
            sb.appendLine("powershell -NoProfile -ExecutionPolicy Bypass -Command \"\$h='C:\\Windows\\System32\\drivers\\etc\\hosts'; \$c=Get-Content \$h -Raw -ErrorAction SilentlyContinue; if (\$c) { \$c=[regex]::Replace(\$c,'(?s)# PocketBoot Adobe Block START.*?# PocketBoot Adobe Block END\\r?\\n?',''); Set-Content -Path \$h -Value \$c -NoNewline }\" >nul 2>&1")
        }
        if (winUtil.disableIpv6 || winUtil.preferIpv4OverIpv6) sb.appendLine("reg delete \"HKLM\\SYSTEM\\CurrentControlSet\\Services\\Tcpip6\\Parameters\" /v DisabledComponents /f >nul 2>&1")
        if (winUtil.disableTeredo) sb.appendLine("netsh interface teredo set state default >nul 2>&1")
        if (winUtil.disableBackgroundApps) sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\AppPrivacy\" /v LetAppsRunInBackground /f >nul 2>&1")
        if (winUtil.disableFullscreenOptimizations) {
            sb.appendLine("reg delete \"HKCU\\System\\GameConfigStore\" /v GameDVR_FSEBehaviorMode /f >nul 2>&1")
            sb.appendLine("reg delete \"HKCU\\System\\GameConfigStore\" /v GameDVR_HonorUserFSEBehaviorMode /f >nul 2>&1")
            sb.appendLine("reg delete \"HKCU\\System\\GameConfigStore\" /v GameDVR_DXGIHonorFSEWindowsCompatible /f >nul 2>&1")
        }
        if (winUtil.disableStoreSearch) sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\Explorer\" /v NoUseStoreOpenWith /f >nul 2>&1")
        if (winUtil.disableNotificationCenter) sb.appendLine("reg delete \"HKCU\\Software\\Policies\\Microsoft\\Windows\\Explorer\" /v DisableNotificationCenter /f >nul 2>&1")
        if (winUtil.disableReservedStorage) sb.appendLine("dism /Online /Set-ReservedStorageState /State:Enabled >nul 2>&1")
        if (winUtil.disableSearchIndexing) {
            sb.appendLine("sc config WSearch start= delayed-auto >nul 2>&1")
            sb.appendLine("net start WSearch >nul 2>&1")
        }
        if (winUtil.disableSvchostSplitting) sb.appendLine("reg delete \"HKLM\\SYSTEM\\CurrentControlSet\\Control\" /v SvcHostSplitThresholdInKB /f >nul 2>&1")
        if (winUtil.disableUnsignedRdpWarnings) sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows NT\\Terminal Services\\Client\" /v AuthenticationLevelOverride /f >nul 2>&1")
        if (winUtil.disableDeliveryOptimization) sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\DeliveryOptimization\" /v DODownloadMode /f >nul 2>&1")
        if (winUtil.enableLongPaths) sb.appendLine("reg add \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\FileSystem\" /v LongPathsEnabled /t REG_DWORD /d 0 /f >nul 2>&1")
        if (winUtil.visualEffectsBestPerformance) sb.appendLine("reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VisualEffects\" /v VisualFXSetting /t REG_DWORD /d 0 /f >nul 2>&1")
        if (winUtil.setHardwareClockUtc) sb.appendLine("reg delete \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\TimeZoneInformation\" /v RealTimeIsUniversal /f >nul 2>&1")
        if (winUtil.enableLegacyF8Boot) sb.appendLine("bcdedit /set {default} bootmenupolicy standard >nul 2>&1")
        if (winUtil.removeExplorerHomeGallery) {
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Desktop\\NameSpace\\{e88865ea-0e1c-4e20-9aa6-edcd0212c87c}\" /ve /t REG_SZ /d \"{e88865ea-0e1c-4e20-9aa6-edcd0212c87c}\" /f >nul 2>&1")
            sb.appendLine("reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Desktop\\NameSpace\\{f874310e-b6b7-47dc-bc84-b9e6b38f5903}\" /ve /t REG_SZ /d \"CLSID_MSGraphHomeFolder\" /f >nul 2>&1")
        }
        if (winUtil.debloatEdge) sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Edge\" /f >nul 2>&1")
        if (winUtil.debloatBrave) sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\BraveSoftware\\Brave\" /f >nul 2>&1")
        if (winUtil.disableAndRemoveWindowsAi) {
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsAI\" /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsCopilot\" /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Policies\\WindowsNotepad\" /v DisableAIFeatures /f >nul 2>&1")
            sb.appendLine("reg delete \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\Paint\" /v DisableCocreator /f >nul 2>&1")
            sb.appendLine("sc query WSAIFabricSvc >nul 2>&1 && sc config WSAIFabricSvc start= demand >nul 2>&1")
        }
        if (winUtil.forceS3Sleep) sb.appendLine("reg delete \"HKLM\\SYSTEM\\CurrentControlSet\\Control\\Power\" /v PlatformAoAcOverride /f >nul 2>&1")
        if (winUtil.ultimatePerformancePlan) sb.appendLine("powercfg /setactive 381b4222-f694-41f0-9685-ff5bb260df2e >nul 2>&1")
        sb.appendLine("echo.")
        sb.appendLine("echo All reversible PocketBoot tweaks have been reverted. Reboot your PC to finish applying changes.")
        if (winUtil.removeEdge || winUtil.removeOneDrive || (winUtil.debloatDefaultAppx && winUtil.activeAppxPackages().isNotEmpty())) {
            sb.appendLine("echo Note: Uninstalled apps or browsers ^(Edge, OneDrive, or AppX packages^) can be reinstalled anytime from Microsoft Store or microsoft.com.")
        }
        sb.appendLine("pause")
        sb.appendLine("exit /b 0")
        return sb.toString().replace("\r\n", "\n").replace("\n", "\r\n")
    }

    private fun gzipToBase64(text: String): String {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return Base64.getEncoder().encodeToString(baos.toByteArray())
    }

    fun generateAutounattendXml(
        experience: WindowsExperienceConfig,
        winUtil: WinUtilTweaksConfig,
        arch: String = "amd64",
        selectedEditionIndex: Int? = null,
        hasSpecializeScript: Boolean = false,
        hasOemUpdates: Boolean = false,
        includeWindowsPePass: Boolean = true,
        uiLang: String = "",
        autoLogonCount: Int = 1,
        includeLabConfigInPe: Boolean = true,
        extraSpecializeCommands: List<Pair<String, String>> = emptyList()
    ): String {
        val safeArch = if (arch.lowercase() in setOf("x86", "arm64", "amd64")) arch.lowercase() else "amd64"
        val peCommands = mutableListOf<Pair<String, String>>()
        if (includeLabConfigInPe && experience.bypassRequirements) {
            peCommands.add("Bypass TPM Check" to """reg add "HKLM\SYSTEM\Setup\LabConfig" /v BypassTPMCheck /t REG_DWORD /d 1 /f""")
            peCommands.add("Bypass Secure Boot Check" to """reg add "HKLM\SYSTEM\Setup\LabConfig" /v BypassSecureBootCheck /t REG_DWORD /d 1 /f""")
            peCommands.add("Bypass RAM Check" to """reg add "HKLM\SYSTEM\Setup\LabConfig" /v BypassRAMCheck /t REG_DWORD /d 1 /f""")
            peCommands.add("Bypass CPU Check" to """reg add "HKLM\SYSTEM\Setup\LabConfig" /v BypassCPUCheck /t REG_DWORD /d 1 /f""")
            peCommands.add("Bypass Storage Check" to """reg add "HKLM\SYSTEM\Setup\LabConfig" /v BypassStorageCheck /t REG_DWORD /d 1 /f""")
        }
        val specCommands = mutableListOf<Triple<String, String, Boolean>>()
        if (extraSpecializeCommands.isNotEmpty()) {
            extraSpecializeCommands.forEach { (desc, cmd) -> specCommands.add(Triple(desc, cmd, false)) }
        } else if (hasSpecializeScript) {
            specCommands.add(Triple("PocketBoot Specialize Tweaks & Updates", """cmd /c "C:\Windows\Setup\Scripts\PocketBoot_Specialize.cmd"""", hasOemUpdates))
        } else {
            if (experience.bypassMicrosoftAccount) specCommands.add(Triple("Bypass Microsoft Account", """reg add "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\OOBE" /v BypassNRO /t REG_DWORD /d 1 /f""", false))
            if (experience.disableBitLocker) specCommands.add(Triple("Disable Automatic BitLocker", """reg add "HKLM\SYSTEM\CurrentControlSet\Control\BitLocker" /v PreventDeviceEncryption /t REG_DWORD /d 1 /f""", false))
            if (experience.disableDataCollection) specCommands.add(Triple("Disable Telemetry", """reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\DataCollection" /v AllowTelemetry /t REG_DWORD /d 0 /f""", false))
            if (experience.disableSponsoredApps || experience.qolImprovements) specCommands.add(Triple("Disable Consumer Features", """reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\CloudContent" /v DisableWindowsConsumerFeatures /t REG_DWORD /d 1 /f""", false))
        }
        val cleanUser = experience.sanitizedLocalUsername
        val needOobePass = experience.skipOobe || experience.disableDataCollection || experience.skipWifiSetup || cleanUser.isNotEmpty()
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
        sb.appendLine("""<unattend xmlns="urn:schemas-microsoft-com:unattend">""")
        val cleanUiLang = uiLang.trim().takeIf { it.matches(Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,4}){1,2}$")) }.orEmpty()
        val addIntlPe = experience.skipOobe && cleanUiLang.isNotEmpty()
        if (includeWindowsPePass && (peCommands.isNotEmpty() || selectedEditionIndex != null || addIntlPe)) {
            sb.appendLine("""  <settings pass="windowsPE">""")
            if (addIntlPe) {
                sb.appendLine("""    <component name="Microsoft-Windows-International-Core-WinPE" processorArchitecture="$safeArch" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">""")
                sb.appendLine("""      <SetupUILanguage><UILanguage>$cleanUiLang</UILanguage></SetupUILanguage>""")
                sb.appendLine("""      <UILanguage>$cleanUiLang</UILanguage>""")
                sb.appendLine("""    </component>""")
            }
            if (peCommands.isNotEmpty() || selectedEditionIndex != null) {
                sb.appendLine("""    <component name="Microsoft-Windows-Setup" processorArchitecture="$safeArch" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">""")
                if (selectedEditionIndex != null) {
                    sb.appendLine("""      <ImageInstall><OSImage><InstallFrom><MetaData wcm:action="add"><Key>/IMAGE/INDEX</Key><Value>$selectedEditionIndex</Value></MetaData></InstallFrom></OSImage></ImageInstall>""")
                    sb.appendLine("""      <UserData><AcceptEula>true</AcceptEula><ProductKey><Key></Key><WillShowUI>Never</WillShowUI></ProductKey></UserData>""")
                }
                if (peCommands.isNotEmpty()) {
                    sb.appendLine("""      <RunSynchronous>""")
                    peCommands.forEachIndexed { idx, (desc, cmd) -> sb.appendLine("""        <RunSynchronousCommand wcm:action="add"><Order>${idx + 1}</Order><Description>${xmlEscape(desc)}</Description><Path>${xmlEscape(cmd)}</Path></RunSynchronousCommand>""") }
                    sb.appendLine("""      </RunSynchronous>""")
                }
                sb.appendLine("""    </component>""")
            }
            sb.appendLine("""  </settings>""")
        }
        if (specCommands.isNotEmpty()) {
            sb.appendLine("""  <settings pass="specialize">""")
            sb.appendLine("""    <component name="Microsoft-Windows-Deployment" processorArchitecture="$safeArch" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">""")
            sb.appendLine("""      <RunSynchronous>""")
            specCommands.forEachIndexed { idx, (desc, cmd, mayReboot) ->
                val reb = if (mayReboot) "<WillReboot>OnRequest</WillReboot>" else ""
                sb.appendLine("""        <RunSynchronousCommand wcm:action="add"><Order>${idx + 1}</Order><Description>${xmlEscape(desc)}</Description><Path>${xmlEscape(cmd)}</Path>$reb</RunSynchronousCommand>""")
            }
            sb.appendLine("""      </RunSynchronous>""")
            sb.appendLine("""    </component>""")
            sb.appendLine("""  </settings>""")
        }
        if (needOobePass) {
            sb.appendLine("""  <settings pass="oobeSystem">""")
            sb.appendLine("""    <component name="Microsoft-Windows-Shell-Setup" processorArchitecture="$safeArch" publicKeyToken="31bf3856ad364e35" language="neutral" versionScope="nonSxS" xmlns:wcm="http://schemas.microsoft.com/WMIConfig/2002/State">""")
            val safeUser = if (cleanUser.isNotEmpty()) xmlEscape(cleanUser) else ""
            val safePass = if (cleanUser.isNotEmpty()) xmlEscape(experience.localPassword.take(32)) else ""
            if (cleanUser.isNotEmpty()) {
                sb.appendLine("""      <AutoLogon><Enabled>true</Enabled><LogonCount>${autoLogonCount.coerceIn(1, 10)}</LogonCount><Password><Value>$safePass</Value><PlainText>true</PlainText></Password><Username>$safeUser</Username></AutoLogon>""")
                sb.appendLine("""      <FirstLogonCommands><SynchronousCommand wcm:action="add"><CommandLine>net accounts /maxpwage:unlimited</CommandLine><Description>Disable local account password expiration</Description><Order>1</Order></SynchronousCommand></FirstLogonCommands>""")
            }
            sb.appendLine("""      <OOBE>""")
            if (experience.skipOobe) sb.appendLine("""        <HideEULAPage>true</HideEULAPage>""")
            if (cleanUser.isNotEmpty()) sb.appendLine("""        <HideLocalAccountScreen>true</HideLocalAccountScreen>""")
            if (experience.disableDataCollection || experience.skipOobe) sb.appendLine("""        <HideOEMRegistrationScreen>true</HideOEMRegistrationScreen>""")
            if (cleanUser.isNotEmpty()) sb.appendLine("""        <HideOnlineAccountScreens>true</HideOnlineAccountScreens>""")
            if (experience.skipWifiSetup || experience.skipOobe) sb.appendLine("""        <HideWirelessSetupInOOBE>true</HideWirelessSetupInOOBE>""")
            if (experience.disableDataCollection || experience.skipOobe) sb.appendLine("""        <ProtectYourPC>3</ProtectYourPC>""")
            if (experience.skipOobe && cleanUser.isNotEmpty()) sb.appendLine("""        <SkipMachineOOBE>true</SkipMachineOOBE><SkipUserOOBE>true</SkipUserOOBE>""")
            sb.appendLine("""      </OOBE>""")
            if (cleanUser.isNotEmpty()) {
                sb.appendLine("""      <UserAccounts><LocalAccounts><LocalAccount wcm:action="add"><DisplayName>$safeUser</DisplayName><Group>Administrators</Group><Name>$safeUser</Name><Password><Value>$safePass</Value><PlainText>true</PlainText></Password></LocalAccount></LocalAccounts></UserAccounts>""")
            }
            sb.appendLine("""    </component>""")
            sb.appendLine("""  </settings>""")
        }
        sb.appendLine("""</unattend>""")
        return sb.toString()
    }

    /**
     * Multi-Boot self-contained unattend XML generator.
     * GZip-compresses and chunks PocketBoot_Specialize.cmd, SetupComplete.cmd, and optional Undo_PocketBoot_Tweaks.cmd
     * into short (~1200 char) RunSynchronousCommand stagers so Windows Setup's 8 KB command limit is never exceeded
     * and post-OOBE tasks (Restore Point, Cleanup, Undo Script) achieve 100% parity with Windows USB Builder.
     */
    fun generateVentoySelfContainedXml(experience: WindowsExperienceConfig, winUtil: WinUtilTweaksConfig, arch: String = "amd64"): String {
        val safeArch = if (arch.lowercase() in setOf("amd64", "x86", "arm64")) arch.lowercase() else "amd64"
        val needScript = experience.needsSpecializeScript() || winUtil.hasAnyActive()
        if (!needScript) {
            return generateAutounattendXml(experience, winUtil, safeArch, null, hasSpecializeScript = false, hasOemUpdates = false)
        }
        val stagers = mutableListOf<Pair<String, String>>()
        stagers.add("Prepare Scripts Directory" to """cmd /c mkdir "C:\Windows\Setup\Scripts" >nul 2>&1 & del /f /q "C:\Windows\Setup\Scripts\*.gzb64" >nul 2>&1 & exit /b 0""")

        fun appendChunkedFile(baseName: String, content: String) {
            val gzB64 = gzipToBase64(content)
            val chunks = gzB64.chunked(1200)
            chunks.forEachIndexed { idx, chunk ->
                stagers.add("Stage $baseName (${idx + 1}/${chunks.size})" to "cmd /c >>\"C:\\Windows\\Setup\\Scripts\\$baseName.gzb64\" echo($chunk")
            }
            val unpackPs = "${'$'}b=[Convert]::FromBase64String(((Get-Content -Raw 'C:\\Windows\\Setup\\Scripts\\$baseName.gzb64') -replace '\\s','')); ${'$'}ms=New-Object IO.MemoryStream(,${'$'}b); ${'$'}gz=New-Object IO.Compression.GZipStream(${'$'}ms,[IO.Compression.CompressionMode]::Decompress); ${'$'}out=[IO.File]::Create('C:\\Windows\\Setup\\Scripts\\$baseName'); ${'$'}gz.CopyTo(${'$'}out); ${'$'}out.Close(); ${'$'}gz.Close(); Remove-Item 'C:\\Windows\\Setup\\Scripts\\$baseName.gzb64' -Force -ErrorAction SilentlyContinue"
            stagers.add("Unpack $baseName" to "powershell -NoProfile -ExecutionPolicy Bypass -Command \"$unpackPs\"")
        }

        appendChunkedFile("PocketBoot_Specialize.cmd", buildSpecializeScript(experience, winUtil))
        appendChunkedFile("SetupComplete.cmd", buildSetupCompleteScript(false, false, "", winUtil))
        if (winUtil.addUndoScript && winUtil.hasAnyActive()) {
            appendChunkedFile("Undo_PocketBoot_Tweaks.cmd", buildUndoScript(experience, winUtil))
        }
        stagers.add("Run PocketBoot Specialize" to "cmd /c \"C:\\Windows\\Setup\\Scripts\\PocketBoot_Specialize.cmd\"")

        return generateAutounattendXml(
            experience = experience,
            winUtil = winUtil,
            arch = safeArch,
            selectedEditionIndex = null,
            hasSpecializeScript = true,
            hasOemUpdates = false,
            extraSpecializeCommands = stagers
        )
    }

    fun buildLabConfigRegContent(): String =
        "REGEDIT4\r\n\r\n" +
            "[HKEY_LOCAL_MACHINE\\SYSTEM\\Setup\\LabConfig]\r\n" +
            "\"BypassTPMCheck\"=dword:00000001\r\n" +
            "\"BypassSecureBootCheck\"=dword:00000001\r\n" +
            "\"BypassRAMCheck\"=dword:00000001\r\n" +
            "\"BypassCPUCheck\"=dword:00000001\r\n" +
            "\"BypassStorageCheck\"=dword:00000001\r\n\r\n"

    /**
     * Unified single-pass boot.wim Index 2 updater (Native Setup single-pass):
     * patches LabConfig into /Windows/System32/config/SYSTEM (when regFile + chntpw provided)
     * and/or injects /Autounattend.xml (when xmlFile provided) in ONE wimlib-imagex update call.
     */
    fun patchBootWimSnippet(
        qWimBin: String,
        qSourcesDir: String,
        qChntpwBin: String? = null,
        qRegFile: String? = null,
        qXmlFile: String? = null
    ): String {
        val wantLab = !qChntpwBin.isNullOrBlank() && !qRegFile.isNullOrBlank()
        val wantAu = !qXmlFile.isNullOrBlank()
        val labBlock = if (wantLab) {
            """
            $qWimBin extract "${'$'}BOOT_WIM" "${'$'}BOOT_IDX" "/Windows/System32/config/SYSTEM" --dest-dir="${'$'}BOOT_TMP" --no-acls --no-attributes >/dev/null 2>&1 || \
                $qWimBin extract "${'$'}BOOT_WIM" "${'$'}BOOT_IDX" "/windows/system32/config/system" --dest-dir="${'$'}BOOT_TMP" --no-acls --no-attributes >/dev/null 2>&1 || true
            SYS_HIVE=${'$'}(find "${'$'}BOOT_TMP" -maxdepth 1 -type f -iname 'system' | head -n 1)
            HIVE_SZ=${'$'}(stat -c '%s' "${'$'}SYS_HIVE" 2>/dev/null || echo 0)
            if [ -n "${'$'}SYS_HIVE" ] && [ "${'$'}{HIVE_SZ:-0}" -ge 4096 ]; then
                set +e
                printf 'y\n' | $qChntpwBin -C -I "${'$'}SYS_HIVE" 'HKEY_LOCAL_MACHINE\SYSTEM' $qRegFile >/dev/null 2>&1
                REG_RC=${'$'}?
                set -e
                AFTER_SZ=${'$'}(stat -c '%s' "${'$'}SYS_HIVE" 2>/dev/null || echo 0)
                if [ "${'$'}{AFTER_SZ:-0}" -ge 4096 ]; then
                    echo "add '${'$'}SYS_HIVE' /Windows/System32/config/SYSTEM" >> "${'$'}UPD_CMD"
                    LAB_READY=1
                else
                    echo "LOG|labconfig|reged hive patch failed (rc=${'$'}REG_RC, size=${'$'}AFTER_SZ)"
                fi
            else
                echo "LOG|labconfig|Could not extract valid SYSTEM hive from boot.wim index ${'$'}BOOT_IDX"
            fi
            """.trimIndent()
        } else ""
        val auBlock = if (wantAu) {
            """
            echo "delete --force /Autounattend.xml" >> "${'$'}UPD_CMD"
            echo "add $qXmlFile /Autounattend.xml" >> "${'$'}UPD_CMD"
            AU_READY=1
            """.trimIndent()
        } else ""
        return """
        set -e
        BOOT_TMP="/data/local/tmp/pb_boot_patch"
        rm -rf "${'$'}BOOT_TMP" && mkdir -p "${'$'}BOOT_TMP"
        export TMPDIR="${'$'}BOOT_TMP" HOME="${'$'}BOOT_TMP" WIMLIB_IMAGEX_IGNORE_CASE=1
        BOOT_WIM=${'$'}(find $qSourcesDir -maxdepth 1 -type f -iname 'boot.wim' 2>/dev/null | head -n 1)
        if [ -z "${'$'}BOOT_WIM" ] || [ ! -s "${'$'}BOOT_WIM" ]; then
            rm -rf "${'$'}BOOT_TMP"
            echo "LOG|labconfig|boot.wim not found in sources/"
            exit 1
        fi
        IDX_CNT=${'$'}($qWimBin info "${'$'}BOOT_WIM" 2>/dev/null | grep -c '^Index:' || echo 0)
        BOOT_IDX=2
        if [ "${'$'}{IDX_CNT:-0}" -lt 2 ]; then BOOT_IDX=1; fi
        UPD_CMD="${'$'}BOOT_TMP/update_cmds.txt"
        : > "${'$'}UPD_CMD"
        LAB_READY=0
        AU_READY=0
        $labBlock
        $auBlock
        if [ ! -s "${'$'}UPD_CMD" ]; then
            rm -rf "${'$'}BOOT_TMP"
            exit 1
        fi
        $qWimBin update "${'$'}BOOT_WIM" "${'$'}BOOT_IDX" < "${'$'}UPD_CMD" >/dev/null 2>&1
        rm -rf "${'$'}BOOT_TMP"
        if [ "${'$'}LAB_READY" = "1" ]; then
            echo "LOG|labconfig|Patched LabConfig hardware bypasses directly into boot.wim Index ${'$'}BOOT_IDX (Native Mode)"
            echo "PATCHED_LAB_OK"
        fi
        if [ "${'$'}AU_READY" = "1" ]; then
            echo "LOG|win-build|Injected /Autounattend.xml into boot.wim Index ${'$'}BOOT_IDX (Unattended Mode)"
            echo "PATCHED_AU_OK"
        fi
        """.trimIndent()
    }

    fun applyCa2023BootloadersSnippet(qWimBin: String, qInstallFile: String, qIsoRoot: String, qBootWim: String = ""): String = """
        CA_TMP="/data/local/tmp/pb_ca2023"
        rm -rf "${'$'}CA_TMP" && mkdir -p "${'$'}CA_TMP"
        export TMPDIR="${'$'}CA_TMP" HOME="${'$'}CA_TMP" WIMLIB_IMAGEX_IGNORE_CASE=1
        CA_APPLIED=""
        INST_SRC=$qInstallFile
        BOOT_WIM_SRC=${qBootWim.ifBlank { "''" }}
        ISO_ROOT=$qIsoRoot
        extract_wim_file() {
            local rel_path="${'$'}1"
            local out_name=${'$'}(basename "${'$'}rel_path")
            rm -f "${'$'}CA_TMP/${'$'}out_name"
            case "${'$'}INST_SRC" in
                *.swm|*.SWM)
                    local wdir=${'$'}(dirname "${'$'}INST_SRC")
                    local wbase=${'$'}(basename "${'$'}INST_SRC")
                    local wpre="${'$'}{wbase%.*}"
                    local wext="${'$'}{wbase##*.}"
                    $qWimBin extract "${'$'}INST_SRC" 1 "${'$'}rel_path" --ref="${'$'}{wdir}/${'$'}{wpre}*.${'$'}{wext}" --dest-dir="${'$'}CA_TMP" --no-acls --no-attributes 2>/dev/null || true
                    ;;
                *)
                    if [ -n "${'$'}INST_SRC" ] && [ -s "${'$'}INST_SRC" ]; then
                        $qWimBin extract "${'$'}INST_SRC" 1 "${'$'}rel_path" --dest-dir="${'$'}CA_TMP" --no-acls --no-attributes 2>/dev/null || true
                    fi
                    ;;
            esac
            if [ ! -s "${'$'}CA_TMP/${'$'}out_name" ] && [ -n "${'$'}BOOT_WIM_SRC" ] && [ -s "${'$'}BOOT_WIM_SRC" ]; then
                $qWimBin extract "${'$'}BOOT_WIM_SRC" 2 "${'$'}rel_path" --dest-dir="${'$'}CA_TMP" --no-acls --no-attributes 2>/dev/null || \
                    $qWimBin extract "${'$'}BOOT_WIM_SRC" 1 "${'$'}rel_path" --dest-dir="${'$'}CA_TMP" --no-acls --no-attributes 2>/dev/null || true
            fi
            [ -s "${'$'}CA_TMP/${'$'}out_name" ]
        }
        resolve_ci_dir() {
            local base="${'$'}1"; local child="${'$'}2"
            local hit=${'$'}(find "${'$'}base" -maxdepth 1 -mindepth 1 -type d -iname "${'$'}child" 2>/dev/null | head -n 1)
            if [ -n "${'$'}hit" ]; then printf '%s' "${'$'}hit"; else mkdir -p "${'$'}base/${'$'}child"; printf '%s' "${'$'}base/${'$'}child"; fi
        }
        copy_ci_file() {
            local src="${'$'}1"; local dir="${'$'}2"; local name="${'$'}3"
            local existing=${'$'}(find "${'$'}dir" -maxdepth 1 -mindepth 1 -type f -iname "${'$'}name" 2>/dev/null | head -n 1)
            if [ -n "${'$'}existing" ]; then cp -f "${'$'}src" "${'$'}existing"; else cp -f "${'$'}src" "${'$'}dir/${'$'}name"; fi
        }
        EFI_DIR=${'$'}(resolve_ci_dir "${'$'}ISO_ROOT" "efi")
        EFI_BOOT_DIR=${'$'}(resolve_ci_dir "${'$'}EFI_DIR" "boot")
        EFI_MS_DIR=${'$'}(resolve_ci_dir "${'$'}EFI_DIR" "microsoft")
        EFI_MS_BOOT_DIR=${'$'}(resolve_ci_dir "${'$'}EFI_MS_DIR" "boot")
        if extract_wim_file /Windows/Boot/EFI_EX/bootmgfw_EX.efi && [ -s "${'$'}CA_TMP/bootmgfw_EX.efi" ]; then
            if find "${'$'}EFI_BOOT_DIR" -maxdepth 1 -type f -iname 'bootaa64.efi' 2>/dev/null | grep -q . && ! find "${'$'}EFI_BOOT_DIR" -maxdepth 1 -type f -iname 'bootx64.efi' 2>/dev/null | grep -q .; then
                copy_ci_file "${'$'}CA_TMP/bootmgfw_EX.efi" "${'$'}EFI_BOOT_DIR" "bootaa64.efi"
            else
                copy_ci_file "${'$'}CA_TMP/bootmgfw_EX.efi" "${'$'}EFI_BOOT_DIR" "bootx64.efi"
            fi
            if find "${'$'}EFI_MS_BOOT_DIR" -maxdepth 1 -type f -iname 'bootmgfw.efi' 2>/dev/null | grep -q .; then
                copy_ci_file "${'$'}CA_TMP/bootmgfw_EX.efi" "${'$'}EFI_MS_BOOT_DIR" "bootmgfw.efi"
            fi
            CA_APPLIED="bootmgfw_EX.efi"
        fi
        if extract_wim_file /Windows/Boot/EFI_EX/bootmgr_EX.efi && [ -s "${'$'}CA_TMP/bootmgr_EX.efi" ]; then
            copy_ci_file "${'$'}CA_TMP/bootmgr_EX.efi" "${'$'}ISO_ROOT" "bootmgr.efi"
            CA_APPLIED="${'$'}{CA_APPLIED:+${'$'}CA_APPLIED + }bootmgr_EX.efi"
        fi
        if extract_wim_file /Windows/Boot/DVD/EFI/efisys_ex.bin && [ -s "${'$'}CA_TMP/efisys_ex.bin" ]; then
            copy_ci_file "${'$'}CA_TMP/efisys_ex.bin" "${'$'}EFI_MS_BOOT_DIR" "efisys.bin"
            CA_APPLIED="${'$'}{CA_APPLIED:+${'$'}CA_APPLIED + }efisys_ex.bin"
        fi
        if extract_wim_file /Windows/Boot/DVD/EFI/efisys_noprompt_ex.bin && [ -s "${'$'}CA_TMP/efisys_noprompt_ex.bin" ]; then
            copy_ci_file "${'$'}CA_TMP/efisys_noprompt_ex.bin" "${'$'}EFI_MS_BOOT_DIR" "efisys_noprompt.bin"
            CA_APPLIED="${'$'}{CA_APPLIED:+${'$'}CA_APPLIED + }efisys_noprompt_ex.bin"
        fi
        if [ -n "${'$'}CA_APPLIED" ]; then
            echo "LOG|ca2023|Applied Windows CA 2023 bootloaders (${'$'}CA_APPLIED)"
        else
            echo "LOG|ca2023|Source image lacks EFI_EX CA 2023 bootloaders; kept standard bootloaders"
        fi
        rm -rf "${'$'}CA_TMP"
    """.trimIndent()

    fun applyWin7UefiFixSnippet(qWimBin: String, qInstallFile: String, qIsoRoot: String): String = """
        ISO_ROOT=$qIsoRoot
        INST_SRC=$qInstallFile
        if ! find "${'$'}ISO_ROOT" -maxdepth 3 -type f \( -iname 'bootx64.efi' -o -iname 'bootaa64.efi' \) 2>/dev/null | grep -q .; then
            W7_TMP="/data/local/tmp/pb_win7_efi"
            rm -rf "${'$'}W7_TMP" && mkdir -p "${'$'}W7_TMP"
            export TMPDIR="${'$'}W7_TMP" HOME="${'$'}W7_TMP" WIMLIB_IMAGEX_IGNORE_CASE=1
            W7_OK=0
            case "${'$'}INST_SRC" in
                *.swm|*.SWM)
                    WDIR=${'$'}(dirname "${'$'}INST_SRC"); WBASE=${'$'}(basename "${'$'}INST_SRC"); WPRE="${'$'}{WBASE%.*}"; WEXT="${'$'}{WBASE##*.}"
                    if $qWimBin extract "${'$'}INST_SRC" 1 /Windows/Boot/EFI/bootmgfw.efi --ref="${'$'}{WDIR}/${'$'}{WPRE}*.${'$'}{WEXT}" --dest-dir="${'$'}W7_TMP" --no-acls --no-attributes 2>/dev/null; then W7_OK=1; fi
                    ;;
                *)
                    if $qWimBin extract "${'$'}INST_SRC" 1 /Windows/Boot/EFI/bootmgfw.efi --dest-dir="${'$'}W7_TMP" --no-acls --no-attributes 2>/dev/null; then W7_OK=1; fi
                    ;;
            esac
            if [ "${'$'}W7_OK" = "1" ] && [ -s "${'$'}W7_TMP/bootmgfw.efi" ]; then
                EFI_DIR=${'$'}(find "${'$'}ISO_ROOT" -maxdepth 1 -mindepth 1 -type d -iname 'efi' 2>/dev/null | head -n 1)
                [ -z "${'$'}EFI_DIR" ] && EFI_DIR="${'$'}ISO_ROOT/efi"
                EFI_BOOT_DIR=${'$'}(find "${'$'}EFI_DIR" -maxdepth 1 -mindepth 1 -type d -iname 'boot' 2>/dev/null | head -n 1)
                [ -z "${'$'}EFI_BOOT_DIR" ] && EFI_BOOT_DIR="${'$'}EFI_DIR/boot"
                mkdir -p "${'$'}EFI_BOOT_DIR"
                cp -f "${'$'}W7_TMP/bootmgfw.efi" "${'$'}EFI_BOOT_DIR/bootx64.efi"
                echo "LOG|win7|Extracted Windows 7 UEFI bootx64.efi from install image"
            fi
            rm -rf "${'$'}W7_TMP"
        fi
    """.trimIndent()
}

@Composable
fun WindowsTwoPaneCustomizer(
    osKind: WindowsOsKind, arch: String = "amd64", experience: WindowsExperienceConfig, winUtil: WinUtilTweaksConfig,
    presets: List<AutounattendPreset>,
    systemTweaksPresets: List<SystemTweaksPreset> = emptyList(),
    systemTweaksSearchHistory: List<String> = emptyList(),
    showCa2023Option: Boolean = true, showCustomScriptOption: Boolean = true,
    defaultBrowseDir: String = "/data/media/0/Download", selectedEditionIndex: Int? = null,
    onExperienceChange: (WindowsExperienceConfig) -> Unit, onWinUtilChange: (WinUtilTweaksConfig) -> Unit,
    onApplyPreset: (AutounattendPreset) -> Unit, onSavePreset: (String) -> Unit, onDeletePreset: (String) -> Unit,
    onSaveSystemTweaksPreset: (SystemTweaksPreset) -> Unit = {},
    onDeleteSystemTweaksPreset: (String) -> Unit = {},
    onUpdateSearchHistory: (List<String>) -> Unit = {}
) {
    var pane1Expanded by remember { mutableStateOf(false) }
    var showSystemTweaksScreen by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showScriptPicker by remember { mutableStateOf(false) }
    val expCount = experience.activeCount(); val winUtilCount = winUtil.activeCount()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.bounceClick { showSavePresetDialog = true }, shape = RoundedCornerShape(9.dp), color = PocketColors.accentBg, border = BorderStroke(0.75.dp, PocketColors.accent)) {
                Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.BookmarkAdd, null, tint = PocketColors.accentSoft, modifier = Modifier.size(14.dp)); Text("Save Preset", style = MaterialTheme.typography.labelSmall, color = PocketColors.accentSoft)
                }
            }
            Surface(modifier = Modifier.bounceClick { showPreviewDialog = true }, shape = RoundedCornerShape(9.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.75.dp, PocketColors.border)) {
                Row(modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Rounded.Preview, null, tint = PocketColors.textSecondary, modifier = Modifier.size(14.dp)); Text("Preview XML", style = MaterialTheme.typography.labelSmall, color = PocketColors.textPrimary)
                }
            }
            val isStock = expCount == 0 && winUtilCount == 0
            Surface(
                modifier = Modifier.bounceClick { onExperienceChange(WindowsExperienceConfig.stock()); onWinUtilChange(WinUtilTweaksConfig()) },
                shape = RoundedCornerShape(9.dp),
                color = if (isStock) PocketColors.successBg else PocketColors.surfaceInput,
                border = BorderStroke(0.75.dp, if (isStock) PocketColors.success else PocketColors.border)
            ) {
                Text(
                    text = "Stock (No Tweaks)",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isStock) PocketColors.success else PocketColors.textSecondary
                )
            }
            presets.forEach { preset ->
                Surface(shape = RoundedCornerShape(9.dp), color = PocketColors.surfaceSubtle, border = BorderStroke(0.75.dp, PocketColors.border)) {
                    Row(modifier = Modifier.padding(start = 9.dp, end = 4.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(preset.name, style = MaterialTheme.typography.labelSmall, color = PocketColors.textPrimary, modifier = Modifier.clickable { onApplyPreset(preset) })
                        Icon(Icons.Rounded.Close, "Delete preset", tint = PocketColors.textMuted, modifier = Modifier.size(14.dp).clickable { onDeletePreset(preset.name) })
                    }
                }
            }
        }

        // PANE 1: Windows Experience & Bypasses (Classic Right-Click Menu moved to System Tweaks -> Advanced)
        Surface(shape = RoundedCornerShape(14.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.75.dp, if (expCount > 0) PocketColors.accent.copy(alpha = 0.55f) else PocketColors.border)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().clickable { pane1Expanded = !pane1Expanded }.padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(PocketColors.accentBg), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Security, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp)) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Windows Experience & Bypasses", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                        Text(if (expCount > 0) "$expCount enabled · Native Setup bypass rules" else "Stock Windows install (tap to customize)", fontSize = 11.sp, color = PocketColors.textSecondary)
                    }
                    Surface(shape = RoundedCornerShape(6.dp), color = if (expCount > 0) PocketColors.accentBg else PocketColors.surfaceSubtle) { Text("$expCount", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = if (expCount > 0) PocketColors.accentSoft else PocketColors.textMuted) }
                    Icon(if (pane1Expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = PocketColors.textSecondary, modifier = Modifier.size(19.dp))
                }
                AnimatedVisibility(visible = pane1Expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        PocketHairlineDivider()
                        if (osKind == WindowsOsKind.WIN11 || osKind == WindowsOsKind.UNKNOWN) CustomSwitchRow("Bypass 4GB+ RAM, Secure Boot & TPM 2.0", "LabConfig hardware checks in WinPE", experience.bypassRequirements) { onExperienceChange(experience.copy(bypassRequirements = it)) }
                        if (osKind != WindowsOsKind.WIN7 && osKind != WindowsOsKind.WIN81 && osKind != WindowsOsKind.SERVER) CustomSwitchRow("Bypass Online Microsoft Account", "Offline local account (unplug Ethernet in OOBE, or create Local Admin below)", experience.bypassMicrosoftAccount) { onExperienceChange(experience.copy(bypassMicrosoftAccount = it)) }
                        CustomSwitchRow("Disable Data Collection", "Skip privacy questions & telemetry prompts", experience.disableDataCollection) { onExperienceChange(experience.copy(disableDataCollection = it)) }
                        CustomSwitchRow("Disable Automatic BitLocker Encryption", "PreventDeviceEncryption = 1", experience.disableBitLocker) { onExperienceChange(experience.copy(disableBitLocker = it)) }
                        CustomSwitchRow("QoL Improvements Bundle", "No Fast Startup, Copilot, OneDrive nag, Outlook, Widgets/Bing · show file extensions", experience.qolImprovements) { onExperienceChange(experience.copy(qolImprovements = it)) }
                        CustomSwitchRow("Remove Sponsored Bloat Apps", "Block silent promoted Start Menu installs", experience.disableSponsoredApps) { onExperienceChange(experience.copy(disableSponsoredApps = it)) }
                        CustomSwitchRow("Skip Wi-Fi / Network Setup Screen", "HideWirelessSetupInOOBE", experience.skipWifiSetup) { onExperienceChange(experience.copy(skipWifiSetup = it)) }
                        CustomSwitchRow("Create Local Admin Account", "Pre-create account & auto-logon once", experience.createLocalUser) { onExperienceChange(experience.copy(createLocalUser = it)) }
                        AnimatedVisibility(visible = experience.createLocalUser) {
                            val userInvalid = experience.localUsername.isNotBlank() && experience.sanitizedLocalUsername.isEmpty()
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = experience.localUsername, onValueChange = { onExperienceChange(experience.copy(localUsername = WindowsCustomizationEngine.filterUsernameInput(it))) }, label = { Text("Username") }, singleLine = true, isError = userInvalid, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PocketColors.accent, unfocusedBorderColor = PocketColors.border, focusedTextColor = PocketColors.textPrimary, unfocusedTextColor = PocketColors.textPrimary))
                                    OutlinedTextField(value = experience.localPassword, onValueChange = { onExperienceChange(experience.copy(localPassword = it.take(32))) }, label = { Text("Password (opt)") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PocketColors.accent, unfocusedBorderColor = PocketColors.border, focusedTextColor = PocketColors.textPrimary, unfocusedTextColor = PocketColors.textPrimary))
                                }
                                if (userInvalid) {
                                    Text("Reserved or invalid Windows username (avoid Administrator, Guest, CON, NUL, or symbols).", fontSize = 11.sp, color = PocketColors.warning)
                                }
                            }
                        }
                        CustomSwitchRow("Full Unattended OOBE Skip", "SkipMachineOOBE + SkipUserOOBE", experience.skipOobe) { onExperienceChange(experience.copy(skipOobe = it)) }
                        if (experience.skipOobe && experience.sanitizedLocalUsername.isEmpty()) {
                            Text(
                                text = "Tip: Enable \"Create Local Admin Account\" above to skip account screens completely; otherwise OOBE will still ask for a username.",
                                fontSize = 11.sp,
                                color = PocketColors.warning
                            )
                        }
                        if (showCa2023Option) CustomSwitchRow("Use 'Windows CA 2023' Signed Bootloaders", "Caution: target PC BIOS must support UEFI CA 2023", experience.useCa2023Bootloaders, warningAccent = true) { onExperienceChange(experience.copy(useCa2023Bootloaders = it, applySkuSiPolicy = if (!it) false else experience.applySkuSiPolicy)) }
                        CustomSwitchRow("Apply SkuSiPolicy.p7b (KB5042562)", "Auto-enables CA 2023 bootloaders · revokes pre-2023 boot media on target PC", experience.applySkuSiPolicy, warningAccent = true) { onExperienceChange(experience.copy(applySkuSiPolicy = it, useCa2023Bootloaders = if (it) true else experience.useCa2023Bootloaders)) }
                        if (showCustomScriptOption) {
                            Surface(modifier = Modifier.fillMaxWidth().bounceClick { showScriptPicker = true }, shape = RoundedCornerShape(10.dp), color = PocketColors.surfaceCard, border = BorderStroke(0.75.dp, if (experience.customPostInstallScriptPath.isNotBlank()) PocketColors.accent else PocketColors.border)) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Code, null, tint = PocketColors.accentSoft, modifier = Modifier.size(17.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Custom Post-Install Script (Optional)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                        Text(if (experience.customPostInstallScriptPath.isNotBlank()) experience.customPostInstallScriptPath.substringAfterLast('/') else "Pick a .cmd, .bat, or .ps1 script to run after setup", fontSize = 11.sp, color = if (experience.customPostInstallScriptPath.isNotBlank()) PocketColors.accentSoft else PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (experience.customPostInstallScriptPath.isNotBlank()) { IconButton(onClick = { onExperienceChange(experience.copy(customPostInstallScriptPath = "")) }, modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.Close, "Clear script", tint = PocketColors.textMuted, modifier = Modifier.size(15.dp)) } }
                                    else Icon(Icons.Rounded.ChevronRight, null, tint = PocketColors.textMuted, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // PANE 2: Dedicated Full-Screen System Tweaks Entry Card
        val matchedPreset = remember(winUtil, systemTweaksPresets) {
            val norm = winUtil.normalizedForComparison()
            WinUtilTweaksConfig.matchedBuiltInPresetName(norm)
                ?: systemTweaksPresets.firstOrNull { WinUtilTweaksConfig.fromJsonString(it.tweaksJson).normalizedForComparison() == norm }?.name
        }
        Surface(modifier = Modifier.fillMaxWidth().bounceClick { showSystemTweaksScreen = true }, shape = RoundedCornerShape(14.dp), color = PocketColors.surfaceInput, border = BorderStroke(0.75.dp, if (winUtilCount > 0) PocketColors.accent.copy(alpha = 0.65f) else PocketColors.border)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(if (winUtilCount > 0) PocketColors.accentBg else PocketColors.violetBg), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Tune, null, tint = if (winUtilCount > 0) PocketColors.accentSoft else PocketColors.violet, modifier = Modifier.size(18.dp)) }
                Column(modifier = Modifier.weight(1f)) {
                    Text("System Tweaks", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                    val undoNote = if (winUtil.addUndoScript && winUtilCount > 0) " · Undo Script" else ""
                    val presetNote = if (matchedPreset != null) " ($matchedPreset)" else ""
                    Text(
                        text = if (winUtilCount > 0) "$winUtilCount active$presetNote$undoNote · Essential, Preferences & Advanced"
                        else "Optional: Presets, Classic Menu, AI removal, Updates & Power",
                        fontSize = 11.sp,
                        color = PocketColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = if (winUtilCount > 0) PocketColors.accentBg else PocketColors.surfaceSubtle) { Text(if (winUtilCount > 0) "$winUtilCount" else "Open", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = if (winUtilCount > 0) PocketColors.accentSoft else PocketColors.textMuted) }
                Icon(Icons.Rounded.ChevronRight, null, tint = PocketColors.textSecondary, modifier = Modifier.size(19.dp))
            }
        }
    }

    if (showSystemTweaksScreen) {
        SystemTweaksScreen(
            config = winUtil,
            experience = experience,
            customPresets = systemTweaksPresets,
            searchHistory = systemTweaksSearchHistory,
            onApply = { updated ->
                onWinUtilChange(updated)
                showSystemTweaksScreen = false
            },
            onDismiss = { showSystemTweaksScreen = false },
            onSaveCustomPreset = onSaveSystemTweaksPreset,
            onDeleteCustomPreset = onDeleteSystemTweaksPreset,
            onUpdateSearchHistory = onUpdateSearchHistory
        )
    }
    if (showScriptPicker) RootSingleFilePickerSheet("Select Post-Install Script", "Runs automatically at end of SetupComplete.cmd", setOf("cmd", "bat", "ps1"), defaultBrowseDir, { showScriptPicker = false }) { onExperienceChange(experience.copy(customPostInstallScriptPath = it)); showScriptPicker = false }
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false }, containerColor = PocketColors.surfaceCard,
            title = { Text("Save Unified Preset", color = PocketColors.textPrimary) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Saves both Windows Experience bypasses and System Tweaks together.", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary); OutlinedTextField(value = newPresetName, onValueChange = { newPresetName = it.take(26) }, label = { Text("Preset name") }, placeholder = { Text("e.g., Gaming PC or Clean Laptop") }, singleLine = true, modifier = Modifier.fillMaxWidth()) } },
            confirmButton = { Button(onClick = { val n = newPresetName.trim(); if (n.isNotEmpty()) { onSavePreset(n); newPresetName = ""; showSavePresetDialog = false } }, enabled = newPresetName.trim().isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)) { Text("Save", fontWeight = FontWeight.SemiBold) } },
            dismissButton = { TextButton(onClick = { showSavePresetDialog = false }) { Text("Cancel", color = PocketColors.textSecondary) } }
        )
    }
    if (showPreviewDialog) {
        var previewTab by remember { mutableIntStateOf(0) }
        val previewUnattended = selectedEditionIndex != null
        val previewHasXml = previewUnattended || experience.hasPostSetupTweaks() || winUtil.hasAnyActive()
        val previewNeedsScript = experience.needsSpecializeScript() || winUtil.hasAnyActive()
        val showUndoTab = winUtil.addUndoScript && winUtil.hasAnyActive()
        val xmlText = remember(experience, winUtil, arch, selectedEditionIndex) {
            if (!previewHasXml) {
                if (experience.bypassRequirements) {
                    "<!-- boot.wim LabConfig patch only — no unattend.xml or scripts written (100% native Windows Setup & OOBE) -->"
                } else {
                    "<!-- Pure Stock ISO — no unattend.xml, boot.wim patch, or scripts written (100% untouched Microsoft Setup & OOBE) -->"
                }
            } else {
                WindowsCustomizationEngine.generateAutounattendXml(
                    experience, winUtil, arch,
                    selectedEditionIndex = selectedEditionIndex,
                    hasSpecializeScript = previewNeedsScript,
                    includeWindowsPePass = previewUnattended,
                    includeLabConfigInPe = false
                )
            }
        }
        val scriptText = remember(experience, winUtil) {
            when {
                previewNeedsScript -> WindowsCustomizationEngine.buildSpecializeScript(experience, winUtil) +
                    "\r\n\r\nREM ===== SetupComplete.cmd (Post-OOBE) =====\r\n" +
                    WindowsCustomizationEngine.buildSetupCompleteScript(false, experience.customPostInstallScriptPath.isNotBlank(), experience.customPostInstallScriptPath.substringAfterLast('/'), winUtil)
                !previewHasXml && !experience.bypassRequirements -> "; No Specialize.cmd needed (Pure Stock ISO — zero scripts injected)"
                else -> "; No Specialize.cmd needed (pure Panther\\unattend.xml + boot.wim LabConfig patch)"
            }
        }
        val undoText = remember(experience, winUtil) {
            if (showUndoTab) WindowsCustomizationEngine.buildUndoScript(experience, winUtil)
            else "; Add Undo Script is OFF — no undo file will be placed on the Desktop."
        }
        val tabs = if (showUndoTab) listOf(
            if (previewUnattended) "Autounattend.xml" else "unattend.xml",
            "Specialize.cmd",
            "Undo.cmd"
        ) else listOf(
            if (previewUnattended) "boot.wim\\Autounattend.xml" else "Panther\\unattend.xml",
            "Specialize.cmd"
        )
        AlertDialog(
            onDismissRequest = { showPreviewDialog = false }, containerColor = PocketColors.surfaceCard,
            title = { Text("Generated Setup Preview", color = PocketColors.textPrimary) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tabs.forEachIndexed { idx, lbl ->
                            val sel = previewTab == idx
                            Surface(modifier = Modifier.weight(1f).bounceClick { previewTab = idx }, shape = RoundedCornerShape(8.dp), color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput, border = BorderStroke(0.75.dp, if (sel) PocketColors.accent else PocketColors.border)) {
                                Box(modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) { Text(lbl, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (sel) PocketColors.accentSoft else PocketColors.textSecondary) }
                            }
                        }
                    }
                    Surface(modifier = Modifier.fillMaxWidth().height(290.dp), shape = RoundedCornerShape(10.dp), color = PocketColors.bgCanvas, border = BorderStroke(0.5.dp, PocketColors.border)) {
                        val content = when (previewTab) {
                            0 -> xmlText
                            1 -> scriptText
                            else -> undoText
                        }
                        Text(content, style = PocketColors.monoStyle, fontSize = 11.sp, color = PocketColors.textSecondary, modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState()))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPreviewDialog = false }) { Text("Close", color = PocketColors.accentSoft) } }
        )
    }
}

@Composable
internal fun CustomSwitchRow(title: String, subtitle: String, checked: Boolean, warningAccent: Boolean = false, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = PocketColors.surfaceCard, border = BorderStroke(0.5.dp, if (checked) (if (warningAccent) PocketColors.warning.copy(alpha = 0.6f) else PocketColors.accent.copy(alpha = 0.55f)) else PocketColors.border)) {
        Row(modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 11.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                Text(subtitle, fontSize = 11.sp, color = if (warningAccent && checked) PocketColors.warning else PocketColors.textSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            PocketSwitch(checked = checked, onCheckedChange = onChange, activeColor = if (warningAccent) PocketColors.warning else PocketColors.accent)
        }
    }
}
