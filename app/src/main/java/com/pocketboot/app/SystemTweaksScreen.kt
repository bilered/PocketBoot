package com.pocketboot.app

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private enum class TweakSection(val title: String, val isCaution: Boolean = false) {
    ESSENTIAL("Essential Tweaks"),
    PREFERENCES("Customize Preferences"),
    ADVANCED("Advanced Tweaks (Caution)", isCaution = true),
    PERFORMANCE("Performance Plans")
}

private enum class TweakImpact(val label: String) {
    SAFE("Safe · Reversible"),
    CAUTION("Caution · Reversible"),
    DESTRUCTIVE("Caution · Removes Component")
}

private data class TweakInfoDetail(
    val title: String,
    val impact: TweakImpact,
    val summary: String,
    val technicalDetails: String,
    val sideEffects: String = ""
)

private data class TweakItemSpec(
    val id: String,
    val section: TweakSection,
    val title: String,
    val subtitle: String,
    val impact: TweakImpact,
    val technicalDetails: String,
    val sideEffects: String = "",
    val keywords: String = "",
    val isChecked: (WinUtilTweaksConfig) -> Boolean,
    val onToggle: (WinUtilTweaksConfig, Boolean) -> WinUtilTweaksConfig
)

private val STARTER_SEARCH_SUGGESTIONS = listOf("AI", "Edge", "Update", "Battery", "Context Menu")

private val TWEAK_CATALOG: List<TweakItemSpec> = listOf(
    // ===== ESSENTIAL TWEAKS (21) =====
    TweakItemSpec(
        id = "createRestorePoint",
        section = TweakSection.ESSENTIAL,
        title = "Create Restore Point",
        subtitle = "Enables System Protection on C: and creates a post-install snapshot",
        impact = TweakImpact.SAFE,
        technicalDetails = "Runs in SetupComplete.cmd: starts VSS, enables System Restore on C:\\, clears the 24-hour creation throttle temporarily, runs Checkpoint-Computer ('PocketBoot Fresh Install'), and restores the throttle key.",
        keywords = "backup snapshot recovery vss",
        isChecked = { it.createRestorePoint },
        onToggle = { c, v -> c.copy(createRestorePoint = v) }
    ),
    TweakItemSpec(
        id = "deleteTempFiles",
        section = TweakSection.ESSENTIAL,
        title = "Delete Temporary Files",
        subtitle = "Clears setup leftovers from %TEMP% and C:\\Windows\\Temp after OOBE",
        impact = TweakImpact.SAFE,
        technicalDetails = "Runs silently in SetupComplete.cmd: deletes leftover files in %TEMP% and C:\\Windows\\Temp without interrupting desktop login.",
        keywords = "clean storage space cache",
        isChecked = { it.deleteTempFiles },
        onToggle = { c, v -> c.copy(deleteTempFiles = v) }
    ),
    TweakItemSpec(
        id = "disableConsumerFeatures",
        section = TweakSection.ESSENTIAL,
        title = "Disable Consumer Features",
        subtitle = "Stops silent promoted Store apps & Start Menu suggestions",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\CloudContent (DisableWindowsConsumerFeatures=1, DisableSoftLanding=1) and Default User ContentDeliveryManager keys to 0.",
        keywords = "ads bloatware sponsored suggestions",
        isChecked = { it.disableConsumerFeatures },
        onToggle = { c, v -> c.copy(disableConsumerFeatures = v) }
    ),
    TweakItemSpec(
        id = "disableTelemetry",
        section = TweakSection.ESSENTIAL,
        title = "Disable Telemetry",
        subtitle = "Turns off diagnostic uploads, feedback prompts & telemetry tasks",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets AllowTelemetry=0 & DoNotShowFeedbackNotifications=1 under Policies\\DataCollection, disables DiagTrack & dmwappushservice, and disables CompatTelRunner, Consolidator, and UsbCeip scheduled tasks.",
        keywords = "privacy tracking diagnostics data collection",
        isChecked = { it.disableTelemetry },
        onToggle = { c, v -> c.copy(disableTelemetry = v) }
    ),
    TweakItemSpec(
        id = "disableActivityHistory",
        section = TweakSection.ESSENTIAL,
        title = "Disable Activity History",
        subtitle = "Turns off Windows Timeline & activity feed cloud sync",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets EnableActivityFeed=0, PublishUserActivities=0, and UploadUserActivities=0 under HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\System.",
        keywords = "timeline privacy sync",
        isChecked = { it.disableActivityHistory },
        onToggle = { c, v -> c.copy(disableActivityHistory = v) }
    ),
    TweakItemSpec(
        id = "disableGameDvr",
        section = TweakSection.ESSENTIAL,
        title = "Disable GameDVR",
        subtitle = "Disables background Xbox Game Bar clip recording",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets AllowGameDVR=0 in HKLM Policies\\GameDVR & PolicyManager, and GameDVR_Enabled=0 in Default User GameConfigStore.",
        sideEffects = "Xbox Game Bar screen recording is disabled until reverted.",
        keywords = "xbox gaming fps capture",
        isChecked = { it.disableGameDvr },
        onToggle = { c, v -> c.copy(disableGameDvr = v) }
    ),
    TweakItemSpec(
        id = "disableHibernation",
        section = TweakSection.ESSENTIAL,
        title = "Disable Hibernation",
        subtitle = "Removes hiberfil.sys to reclaim SSD space (best for desktops)",
        impact = TweakImpact.SAFE,
        technicalDetails = "Runs powercfg /h off during Specialize, removing hiberfil.sys and disabling Fast Startup.",
        sideEffects = "Not recommended for laptops that rely on Hibernate for critical-battery shutdown.",
        keywords = "power ssd fast startup hiberfil desktop",
        isChecked = { it.disableHibernation },
        onToggle = { c, v -> c.copy(disableHibernation = v) }
    ),
    TweakItemSpec(
        id = "disableHomegroup",
        section = TweakSection.ESSENTIAL,
        title = "Disable Homegroup",
        subtitle = "Sets legacy HomeGroup services to Manual startup",
        impact = TweakImpact.SAFE,
        technicalDetails = "Runs sc config HomeGroupListener start= demand and sc config HomeGroupProvider start= demand.",
        keywords = "network lan sharing service",
        isChecked = { it.disableHomegroup },
        onToggle = { c, v -> c.copy(disableHomegroup = v) }
    ),
    TweakItemSpec(
        id = "disableLocationTracking",
        section = TweakSection.ESSENTIAL,
        title = "Disable Location Tracking",
        subtitle = "Denies OS geolocation consent and disables lfsvc",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets CapabilityAccessManager\\ConsentStore\\location Value=Deny and disables the Geolocation Service (lfsvc).",
        sideEffects = "Weather/Maps apps and automatic time zone detection will not use GPS/Wi-Fi location.",
        keywords = "gps privacy maps lfsvc",
        isChecked = { it.disableLocationTracking },
        onToggle = { c, v -> c.copy(disableLocationTracking = v) }
    ),
    TweakItemSpec(
        id = "disableStorageSense",
        section = TweakSection.ESSENTIAL,
        title = "Disable Storage Sense",
        subtitle = "Turns off automatic background temp & Recycle Bin purging",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets AllowStorageSenseGlobal=0 under HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\StorageSense.",
        keywords = "disk cleanup storage recycle bin",
        isChecked = { it.disableStorageSense },
        onToggle = { c, v -> c.copy(disableStorageSense = v) }
    ),
    TweakItemSpec(
        id = "disableWifiSense",
        section = TweakSection.ESSENTIAL,
        title = "Disable Wi-Fi Sense",
        subtitle = "Stops automatic hotspot reporting and open-hotspot auto-connect",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets AllowWiFiHotSpotReporting=0 and AllowAutoConnectToWiFiSenseHotspots=0 under HKLM PolicyManager\\default\\WiFi.",
        keywords = "wireless network hotspot privacy",
        isChecked = { it.disableWifiSense },
        onToggle = { c, v -> c.copy(disableWifiSense = v) }
    ),
    TweakItemSpec(
        id = "disableAutoFolderDiscovery",
        section = TweakSection.ESSENTIAL,
        title = "Disable Explorer Auto Folder Discovery",
        subtitle = "Stops File Explorer randomly changing folder view templates",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets FolderType=NotSpecified under Default User Software\\Classes\\Local Settings\\Software\\Microsoft\\Windows\\Shell\\Bags\\AllFolders\\Shell.",
        keywords = "file explorer view pictures music downloads",
        isChecked = { it.disableAutoFolderDiscovery },
        onToggle = { c, v -> c.copy(disableAutoFolderDiscovery = v) }
    ),
    TweakItemSpec(
        id = "disableWpbt",
        section = TweakSection.ESSENTIAL,
        title = "Disable WPBT (Firmware Bloatware)",
        subtitle = "Blocks motherboard UEFI from injecting OEM apps at boot",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets DisableWpbtExecution=1 under HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager.",
        keywords = "uefi bios asus armoury crate oem",
        isChecked = { it.disableWpbt },
        onToggle = { c, v -> c.copy(disableWpbt = v) }
    ),
    TweakItemSpec(
        id = "endTaskWithRightClick",
        section = TweakSection.ESSENTIAL,
        title = "End Task With Right Click",
        subtitle = "Adds 'End task' directly to Windows 11 taskbar app menu",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets TaskbarEndTask=1 under Default User Explorer\\Advanced\\TaskbarDeveloperSettings (Windows 11 build 22000+).",
        keywords = "taskbar kill process developer",
        isChecked = { it.endTaskWithRightClick },
        onToggle = { c, v -> c.copy(endTaskWithRightClick = v) }
    ),
    TweakItemSpec(
        id = "preventDeviceCompanionApps",
        section = TweakSection.ESSENTIAL,
        title = "Prevent Device Companion Apps",
        subtitle = "Stops Windows downloading OEM companion apps from metadata",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets PreventDeviceMetadataFromNetwork=1 under HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Device Metadata.",
        keywords = "oem hardware printer mouse bloat",
        isChecked = { it.preventDeviceCompanionApps },
        onToggle = { c, v -> c.copy(preventDeviceCompanionApps = v) }
    ),
    TweakItemSpec(
        id = "disableBitLockerAuto",
        section = TweakSection.ESSENTIAL,
        title = "Disable BitLocker Auto-Encryption",
        subtitle = "Prevents Windows from silently encrypting drives during OOBE",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets PreventDeviceEncryption=1 under HKLM\\SYSTEM\\CurrentControlSet\\Control\\BitLocker.",
        sideEffects = "Manual BitLocker encryption remains available anytime if you want it.",
        keywords = "encryption disk recovery key security",
        isChecked = { it.disableBitLockerAuto },
        onToggle = { c, v -> c.copy(disableBitLockerAuto = v) }
    ),
    TweakItemSpec(
        id = "runDiskCleanup",
        section = TweakSection.ESSENTIAL,
        title = "Run Disk Cleanup (Headless)",
        subtitle = "Runs headless DISM component cleanup & cache purge after OOBE",
        impact = TweakImpact.SAFE,
        technicalDetails = "Runs headless dism /Online /Cleanup-Image /StartComponentCleanup and clears Prefetch & SoftwareDistribution\\Download in SetupComplete.cmd (never opens Session-0 cleanmgr GUI).",
        keywords = "winsxs dism space cleanup",
        isChecked = { it.runDiskCleanup },
        onToggle = { c, v -> c.copy(runDiskCleanup = v) }
    ),
    TweakItemSpec(
        id = "servicesToManual",
        section = TweakSection.ESSENTIAL,
        title = "Set Services to Manual",
        subtitle = "Switches 14 non-essential background services to Manual startup",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets DiagTrack, dmwappushservice, MapsBroker, WerSvc, Fax, RemoteRegistry, RetailDemo, TrkWks, WMPNetworkSvc, SharedAccess, WalletService, WpcMonSvc, icssvc, and wisvc to start= demand. Keeps XboxGipSvc, Audio, Networking, and Windows Update intact.",
        keywords = "ram cpu background optimization",
        isChecked = { it.servicesToManual },
        onToggle = { c, v -> c.copy(servicesToManual = v) }
    ),
    TweakItemSpec(
        id = "disablePowershell7Telemetry",
        section = TweakSection.ESSENTIAL,
        title = "Disable PowerShell 7 Telemetry",
        subtitle = "Sets machine-wide POWERSHELL_TELEMETRY_OPTOUT=1",
        impact = TweakImpact.SAFE,
        technicalDetails = "Adds POWERSHELL_TELEMETRY_OPTOUT=1 under HKLM\\SYSTEM\\CurrentControlSet\\Control\\Session Manager\\Environment.",
        keywords = "pwsh terminal privacy",
        isChecked = { it.disablePowershell7Telemetry },
        onToggle = { c, v -> c.copy(disablePowershell7Telemetry = v) }
    ),
    TweakItemSpec(
        id = "revertStartMenuLayout",
        section = TweakSection.ESSENTIAL,
        title = "Start Menu: More Pins Layout",
        subtitle = "Switches Windows 11 Start Menu to prioritize pinned apps",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets Start_Layout=1 under Default User Explorer\\Advanced (Windows 11 build 22000+).",
        keywords = "start pins ui layout",
        isChecked = { it.revertStartMenuLayout },
        onToggle = { c, v -> c.copy(revertStartMenuLayout = v) }
    ),
    TweakItemSpec(
        id = "debloatDefaultAppx",
        section = TweakSection.ESSENTIAL,
        title = "Debloat Default AppX Packages",
        subtitle = "Removes pre-installed bloat apps (expand below to keep specific apps)",
        impact = TweakImpact.DESTRUCTIVE,
        technicalDetails = "Runs Remove-AppxProvisionedPackage -Online for the selected pre-installed Microsoft bloat apps (never removes Microsoft Store, Calculator, Photos, or Notepad).",
        sideEffects = "Removed apps can be reinstalled anytime from the Microsoft Store.",
        keywords = "bloatware xbox clipchamp bing solitaire sticky notes",
        isChecked = { it.debloatDefaultAppx && it.activeAppxPackages().isNotEmpty() },
        onToggle = { c, v ->
            if (v) c.copy(debloatDefaultAppx = true, excludedAppxPackages = if (c.excludedAppxPackages.size >= WinUtilTweaksConfig.DEFAULT_DEBLOAT_APPX.size) emptySet() else c.excludedAppxPackages)
            else c.copy(debloatDefaultAppx = false)
        }
    ),

    // ===== CUSTOMIZE PREFERENCES (20 switches + 3-state MPO card) =====
    TweakItemSpec(
        id = "darkThemeDefault",
        section = TweakSection.PREFERENCES,
        title = "Dark Theme for Windows",
        subtitle = "Enables Dark Mode for apps and Windows shell by default",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets AppsUseLightTheme=0 and SystemUsesLightTheme=0 in Default User Themes\\Personalize.",
        keywords = "dark mode theme appearance",
        isChecked = { it.darkThemeDefault },
        onToggle = { c, v -> c.copy(darkThemeDefault = v) }
    ),
    TweakItemSpec(
        id = "disableBingSearch",
        section = TweakSection.PREFERENCES,
        title = "Disable Bing Search in Start Menu",
        subtitle = "Keeps Start Menu search local without web results",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets DisableSearchBoxSuggestions=1 under Default User Policies\\Explorer and BingSearchEnabled=0 under CurrentVersion\\Search.",
        keywords = "web search start menu internet",
        isChecked = { it.disableBingSearch },
        onToggle = { c, v -> c.copy(disableBingSearch = v) }
    ),
    TweakItemSpec(
        id = "disableStartMenuRecommendations",
        section = TweakSection.PREFERENCES,
        title = "Disable Start Menu Recommendations",
        subtitle = "Turns off tips, shortcuts & recent file recommendations in Start",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Sets Start_IrisRecommendations=0 and Start_TrackDocs=0 under Default User Explorer\\Advanced.",
        sideEffects = "Also disables Windows Spotlight rotating lock-screen wallpapers on Windows 11.",
        keywords = "spotlight tips start menu recent",
        isChecked = { it.disableStartMenuRecommendations },
        onToggle = { c, v -> c.copy(disableStartMenuRecommendations = v) }
    ),
    TweakItemSpec(
        id = "numLockOnStartup",
        section = TweakSection.PREFERENCES,
        title = "NumLock on Startup",
        subtitle = "Turns NumLock ON automatically at the sign-in screen",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets InitialKeyboardIndicators=2 in Default User and 2147483650 in HKU\\.DEFAULT\\Control Panel\\Keyboard.",
        sideEffects = "Best for desktops and laptops with a dedicated numeric keypad.",
        keywords = "keyboard numpad login desktop",
        isChecked = { it.numLockOnStartup },
        onToggle = { c, v -> c.copy(numLockOnStartup = v) }
    ),
    TweakItemSpec(
        id = "verboseBsodAndLogon",
        section = TweakSection.PREFERENCES,
        title = "Verbose Logon & BSoD Messages",
        subtitle = "Shows detailed startup/shutdown steps & stop-code parameters",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets VerboseStatus=1 under HKLM Policies\\System and DisplayParameters=1 under CurrentControlSet\\Control\\CrashControl.",
        keywords = "debug diagnostic boot bsod crash",
        isChecked = { it.verboseBsodAndLogon },
        onToggle = { c, v -> c.copy(verboseBsodAndLogon = v) }
    ),
    TweakItemSpec(
        id = "showHiddenFiles",
        section = TweakSection.PREFERENCES,
        title = "Show Hidden Files",
        subtitle = "Displays hidden files and folders in File Explorer",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets Hidden=1 under Default User Explorer\\Advanced.",
        keywords = "explorer appdata dot files",
        isChecked = { it.showHiddenFiles },
        onToggle = { c, v -> c.copy(showHiddenFiles = v) }
    ),
    TweakItemSpec(
        id = "showFileExtensions",
        section = TweakSection.PREFERENCES,
        title = "Show File Extensions",
        subtitle = "Always displays .exe, .iso, .zip & other file extensions",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets HideFileExt=0 under Default User Explorer\\Advanced.",
        keywords = "explorer security filename",
        isChecked = { it.showFileExtensions },
        onToggle = { c, v -> c.copy(showFileExtensions = v) }
    ),
    TweakItemSpec(
        id = "disableMouseAcceleration",
        section = TweakSection.PREFERENCES,
        title = "Disable Mouse Acceleration",
        subtitle = "Turns off 'Enhance pointer precision' for 1:1 mouse aim",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets MouseSpeed=0, MouseThreshold1=0, and MouseThreshold2=0 in Default User Control Panel\\Mouse.",
        sideEffects = "Best for mice/gaming desktops; laptops usually feel smoother with trackpad acceleration ON.",
        keywords = "pointer gaming fps raw input mouse",
        isChecked = { it.disableMouseAcceleration },
        onToggle = { c, v -> c.copy(disableMouseAcceleration = v) }
    ),
    TweakItemSpec(
        id = "disableStickyKeys",
        section = TweakSection.PREFERENCES,
        title = "Disable Sticky Keys",
        subtitle = "Stops the Shift×5 Sticky Keys popup while gaming or typing",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets Flags=506 under Default User Control Panel\\Accessibility\\StickyKeys.",
        keywords = "shift keyboard gaming accessibility",
        isChecked = { it.disableStickyKeys },
        onToggle = { c, v -> c.copy(disableStickyKeys = v) }
    ),
    TweakItemSpec(
        id = "taskbarAlignLeft",
        section = TweakSection.PREFERENCES,
        title = "Taskbar Align Left",
        subtitle = "Moves Windows 11 Start & taskbar icons to the left corner",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets TaskbarAl=0 under Default User Explorer\\Advanced (Windows 11 build 22000+).",
        keywords = "taskbar classic start left",
        isChecked = { it.taskbarAlignLeft },
        onToggle = { c, v -> c.copy(taskbarAlignLeft = v) }
    ),
    TweakItemSpec(
        id = "hideTaskbarSearch",
        section = TweakSection.PREFERENCES,
        title = "Hide Search Button in Taskbar",
        subtitle = "Removes the Search bar/icon from the taskbar",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets SearchboxTaskbarMode=0 under Default User CurrentVersion\\Search.",
        keywords = "taskbar search clean",
        isChecked = { it.hideTaskbarSearch },
        onToggle = { c, v -> c.copy(hideTaskbarSearch = v) }
    ),
    TweakItemSpec(
        id = "hideTaskView",
        section = TweakSection.PREFERENCES,
        title = "Hide Task View Button",
        subtitle = "Removes the Task View / virtual desktops icon from the taskbar",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets ShowTaskViewButton=0 under Default User Explorer\\Advanced.",
        keywords = "taskbar virtual desktop",
        isChecked = { it.hideTaskView },
        onToggle = { c, v -> c.copy(hideTaskView = v) }
    ),
    TweakItemSpec(
        id = "showBatteryPercentage",
        section = TweakSection.PREFERENCES,
        title = "Show Battery Percentage",
        subtitle = "Displays numeric battery % next to the laptop tray icon",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets ShowBatteryPercentage=1 under Default User Explorer\\Advanced (Windows 11 24H2/25H2+).",
        keywords = "laptop battery power tray",
        isChecked = { it.showBatteryPercentage },
        onToggle = { c, v -> c.copy(showBatteryPercentage = v) }
    ),
    TweakItemSpec(
        id = "disableLockScreen",
        section = TweakSection.PREFERENCES,
        title = "Disable Lock Screen",
        subtitle = "Skips the curtain image straight to password/PIN sign-in",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets NoLockScreen=1 under HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\Personalization.",
        keywords = "login sign in curtain",
        isChecked = { it.disableLockScreen },
        onToggle = { c, v -> c.copy(disableLockScreen = v) }
    ),
    TweakItemSpec(
        id = "disableLogonAcrylicBlur",
        section = TweakSection.PREFERENCES,
        title = "Disable Login Acrylic Blur",
        subtitle = "Keeps sign-in wallpaper crisp without blur effect",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets DisableAcrylicBackgroundOnLogon=1 under HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\System.",
        keywords = "wallpaper login blur",
        isChecked = { it.disableLogonAcrylicBlur },
        onToggle = { c, v -> c.copy(disableLogonAcrylicBlur = v) }
    ),
    TweakItemSpec(
        id = "alwaysShowScrollbars",
        section = TweakSection.PREFERENCES,
        title = "Always Show Scrollbars",
        subtitle = "Prevents modern Windows apps from auto-hiding scrollbars",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets DynamicScrollbars=0 under Default User Control Panel\\Accessibility.",
        keywords = "ui accessibility scroll",
        isChecked = { it.alwaysShowScrollbars },
        onToggle = { c, v -> c.copy(alwaysShowScrollbars = v) }
    ),
    TweakItemSpec(
        id = "disableGameMode",
        section = TweakSection.PREFERENCES,
        title = "Disable Game Mode",
        subtitle = "Turns off Windows automatic Game Mode scheduling",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets AutoGameModeEnabled=0 under Default User Software\\Microsoft\\GameBar.",
        keywords = "gaming stutter overlay",
        isChecked = { it.disableGameMode },
        onToggle = { c, v -> c.copy(disableGameMode = v) }
    ),
    TweakItemSpec(
        id = "disableWindowSnapping",
        section = TweakSection.PREFERENCES,
        title = "Disable Window Snapping",
        subtitle = "Disables Aero Snap & edge-drag window resizing",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets WindowArrangementActive=0 under Default User Control Panel\\Desktop.",
        keywords = "snap layout aero windows",
        isChecked = { it.disableWindowSnapping },
        onToggle = { c, v -> c.copy(disableWindowSnapping = v) }
    ),
    TweakItemSpec(
        id = "disableS0Network",
        section = TweakSection.PREFERENCES,
        title = "Disable S0 Sleep Network Connectivity",
        subtitle = "Stops Modern Standby laptops waking on Wi-Fi/drain in bag",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets ACSettingIndex=0 & DCSettingIndex=0 under Policies\\Power\\PowerSettings\\f15576e8-98b7-4186-b944-eafa664402d9. Mutually exclusive with Force Legacy S3 Sleep.",
        keywords = "laptop standby sleep battery drain s0",
        isChecked = { it.disableS0Network },
        onToggle = { c, v -> c.copy(disableS0Network = v, forceS3Sleep = if (v) false else c.forceS3Sleep) }
    ),
    TweakItemSpec(
        id = "hideSettingsHome",
        section = TweakSection.PREFERENCES,
        title = "Hide Settings Home Page",
        subtitle = "Removes the ad-heavy Home tab from Windows 11 Settings",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets SettingsPageVisibility='hide:home' under HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer.",
        keywords = "settings ads microsoft 365",
        isChecked = { it.hideSettingsHome },
        onToggle = { c, v -> c.copy(hideSettingsHome = v) }
    ),

    // ===== ADVANCED TWEAKS - CAUTION (29) =====
    TweakItemSpec(
        id = "classicContextMenu",
        section = TweakSection.ADVANCED,
        title = "Classic Windows 10 Right-Click Menu",
        subtitle = "Restores the full classic File Explorer context menu on Win 11",
        impact = TweakImpact.SAFE,
        technicalDetails = "Creates Default User Software\\Classes\\CLSID\\{86ca1aa0-34aa-4e8b-a509-50c905bae2a2}\\InprocServer32 with empty default value (Windows 11 build 22000+).",
        keywords = "context menu right click show more options explorer",
        isChecked = { it.classicContextMenu },
        onToggle = { c, v -> c.copy(classicContextMenu = v) }
    ),
    TweakItemSpec(
        id = "disableWindowsUpdates",
        section = TweakSection.ADVANCED,
        title = "Disable Windows Update (Store-Safe)",
        subtitle = "Pauses OS updates to 2099 + policy guard · keeps Store, Game Pass & drivers working",
        impact = TweakImpact.CAUTION,
        technicalDetails = "1. Long-Term Update Pause (HKLM\\SOFTWARE\\Microsoft\\WindowsUpdate\\UX\\Settings): Sets FlightSettingsMaxPauseDays=30000 and writes PauseFeatureUpdatesStartTime, PauseQualityUpdatesStartTime, PauseUpdatesStartTime (install UTC) + PauseFeatureUpdatesEndTime, PauseQualityUpdatesEndTime, PauseUpdatesExpiryTime = 2099-12-31T23:59:59Z (works on Home & Pro).\n2. Automatic Update Policy Guard (Policies\\WindowsUpdate\\AU): Sets NoAutoUpdate=1, AUOptions=2, NoAutoRebootWithLoggedOnUsers=1. Deliberately omits DoNotConnectToWindowsUpdateInternetLocations & ExcludeWUDriversInQualityUpdate so Microsoft Store, Xbox Game Pass, Optional Features (WSL/.NET), and driver installs keep working.",
        sideEffects = "OS security patches will stay paused until you click 'Resume updates' in Settings or run the Undo script. Mutually exclusive with Security-Only Updates.",
        keywords = "windows update pause 2099 store game pass wu",
        isChecked = { it.disableWindowsUpdates },
        onToggle = { c, v -> c.copy(disableWindowsUpdates = v, securityOnlyUpdates = if (v) false else c.securityOnlyUpdates) }
    ),
    TweakItemSpec(
        id = "securityOnlyUpdates",
        section = TweakSection.ADVANCED,
        title = "Security-Only Windows Updates",
        subtitle = "Delays Feature Updates 365 days & Quality Updates 4 days",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets DeferFeatureUpdates=1 (365 days), DeferQualityUpdates=1 (4 days), and BranchReadinessLevel=20 under HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate.",
        sideEffects = "Group Policy deferral is primarily enforced on Pro, Education, and Enterprise editions. Mutually exclusive with Disable Windows Update.",
        keywords = "windows update defer security patch tuesday",
        isChecked = { it.securityOnlyUpdates },
        onToggle = { c, v -> c.copy(securityOnlyUpdates = v, disableWindowsUpdates = if (v) false else c.disableWindowsUpdates) }
    ),
    TweakItemSpec(
        id = "disableWindowsSecurity",
        section = TweakSection.ADVANCED,
        title = "Disable Windows Security (Defender)",
        subtitle = "Applies Defender Real-Time Protection disable policies & hides tray",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Writes DisableAntiSpyware=1, DisableRealtimeMonitoring=1, DisableBehaviorMonitoring=1, DisableOnAccessProtection=1, DisableScanOnRealtimeEnable=1, SpynetReporting=0, SubmitSamplesConsent=2, and HideSystray=1 under HKLM Policies\\Windows Defender.",
        sideEffects = "Windows 11 Tamper Protection blocks Defender policy enforcement until you manually toggle 'Tamper Protection -> Off' once in Windows Security settings. Leaves PC without built-in antivirus unless you install another.",
        keywords = "defender antivirus security tamper protection",
        isChecked = { it.disableWindowsSecurity },
        onToggle = { c, v -> c.copy(disableWindowsSecurity = v) }
    ),
    TweakItemSpec(
        id = "disableAndRemoveWindowsAi",
        section = TweakSection.ADVANCED,
        title = "Disable & Remove Windows AI",
        subtitle = "Disables Recall, Copilot, Notepad/Paint AI & removes AI Appx",
        impact = TweakImpact.DESTRUCTIVE,
        technicalDetails = "Sets WindowsAI, WindowsCopilot, WindowsNotepad (DisableAIFeatures=1), and Paint (DisableCocreator=1) policies, disables WSAIFabricSvc if present, disables DISM Recall feature, and removes Copilot/Recall provisioned Appx packages.",
        keywords = "copilot recall ai notepad paint cocreator",
        isChecked = { it.disableAndRemoveWindowsAi },
        onToggle = { c, v -> c.copy(disableAndRemoveWindowsAi = v) }
    ),
    TweakItemSpec(
        id = "debloatEdge",
        section = TweakSection.ADVANCED,
        title = "Debloat Microsoft Edge",
        subtitle = "Disables Sidebar, Shopping, Rewards, Startup Boost & background mode",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets HubsSidebarEnabled=0, EdgeShoppingAssistantEnabled=0, StartupBoostEnabled=0, BackgroundModeEnabled=0, and ShowMicrosoftRewards=0 under HKLM\\SOFTWARE\\Policies\\Microsoft\\Edge.",
        keywords = "browser edge sidebar rewards startup",
        isChecked = { it.debloatEdge },
        onToggle = { c, v -> c.copy(debloatEdge = v, removeEdge = if (v) false else c.removeEdge) }
    ),
    TweakItemSpec(
        id = "debloatBrave",
        section = TweakSection.ADVANCED,
        title = "Debloat Brave Browser",
        subtitle = "Disables Brave Rewards, Wallet, VPN, Leo AI & Tor via policy",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets BraveRewardsDisabled=1, BraveWalletDisabled=1, BraveVPNDisabled=1, BraveAIChatEnabled=0, and TorDisabled=1 under HKLM\\SOFTWARE\\Policies\\BraveSoftware\\Brave.",
        keywords = "browser brave crypto rewards leo ai",
        isChecked = { it.debloatBrave },
        onToggle = { c, v -> c.copy(debloatBrave = v) }
    ),
    TweakItemSpec(
        id = "removeEdge",
        section = TweakSection.ADVANCED,
        title = "Remove Microsoft Edge",
        subtitle = "Force-uninstalls Edge browser (keeps WebView2 runtime intact)",
        impact = TweakImpact.DESTRUCTIVE,
        technicalDetails = "Sets EdgeUpdate DoNotUpdateToEdgeWithChromium=1 and executes Edge Installer setup.exe --uninstall --system-level --force-uninstall.",
        sideEffects = "Ensure you have another browser installer ready. Mutually exclusive with Debloat Microsoft Edge.",
        keywords = "browser edge uninstall remove",
        isChecked = { it.removeEdge },
        onToggle = { c, v -> c.copy(removeEdge = v, debloatEdge = if (v) false else c.debloatEdge) }
    ),
    TweakItemSpec(
        id = "removeOneDrive",
        section = TweakSection.ADVANCED,
        title = "Remove OneDrive",
        subtitle = "Uninstalls OneDrive and removes Default User auto-start",
        impact = TweakImpact.DESTRUCTIVE,
        technicalDetails = "Runs OneDriveSetup.exe /uninstall (SysWOW64 & System32) and deletes OneDriveSetup from Default User Run key.",
        sideEffects = "OneDrive cloud folder sync is removed (can be reinstalled from Microsoft later).",
        keywords = "cloud sync onedrive uninstall",
        isChecked = { it.removeOneDrive },
        onToggle = { c, v -> c.copy(removeOneDrive = v) }
    ),
    TweakItemSpec(
        id = "removeExplorerHomeGallery",
        section = TweakSection.ADVANCED,
        title = "Remove Explorer Home & Gallery",
        subtitle = "Removes Home & Gallery sidebar nodes in Windows 11 Explorer",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Deletes Desktop NameSpace CLSIDs {e88865ea-0e1c-4e20-9aa6-edcd0212c87c} (Gallery) and {f874310e-b6b7-47dc-bc84-b9e6b38f5903} (Home) on build 22000+.",
        keywords = "file explorer sidebar home gallery",
        isChecked = { it.removeExplorerHomeGallery },
        onToggle = { c, v -> c.copy(removeExplorerHomeGallery = v) }
    ),
    TweakItemSpec(
        id = "adobeNetworkBlock",
        section = TweakSection.ADVANCED,
        title = "Adobe Network Block",
        subtitle = "Blocks Adobe telemetry & genuine-service hosts in etc\\hosts",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Appends validated 0.0.0.0 entries for Adobe telemetry/activation endpoints inside # PocketBoot Adobe Block markers in C:\\Windows\\System32\\drivers\\etc\\hosts.",
        sideEffects = "Will prevent Creative Cloud sign-in/cloud sync while enabled (cleanly removed by Undo script).",
        keywords = "adobe hosts creative cloud telemetry",
        isChecked = { it.adobeNetworkBlock },
        onToggle = { c, v -> c.copy(adobeNetworkBlock = v) }
    ),
    TweakItemSpec(
        id = "disableDeliveryOptimization",
        section = TweakSection.ADVANCED,
        title = "Disable Delivery Optimization",
        subtitle = "Stops Windows uploading updates peer-to-peer over your network",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets DODownloadMode=0 under HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\DeliveryOptimization.",
        keywords = "p2p bandwidth upload windows update",
        isChecked = { it.disableDeliveryOptimization },
        onToggle = { c, v -> c.copy(disableDeliveryOptimization = v) }
    ),
    TweakItemSpec(
        id = "enableLongPaths",
        section = TweakSection.ADVANCED,
        title = "Enable Long File Paths",
        subtitle = "Removes Windows's legacy 260-character file path limit",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets LongPathsEnabled=1 under HKLM\\SYSTEM\\CurrentControlSet\\Control\\FileSystem.",
        keywords = "developer git path max_path",
        isChecked = { it.enableLongPaths },
        onToggle = { c, v -> c.copy(enableLongPaths = v) }
    ),
    TweakItemSpec(
        id = "disableBackgroundApps",
        section = TweakSection.ADVANCED,
        title = "Disable Background Apps",
        subtitle = "Stops Microsoft Store / UWP apps running in the background",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Sets LetAppsRunInBackground=2 under HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\AppPrivacy.",
        sideEffects = "UWP mail/calendar/chat apps won't show background notifications while closed.",
        keywords = "uwp store ram battery background",
        isChecked = { it.disableBackgroundApps },
        onToggle = { c, v -> c.copy(disableBackgroundApps = v) }
    ),
    TweakItemSpec(
        id = "disableFullscreenOptimizations",
        section = TweakSection.ADVANCED,
        title = "Disable Fullscreen Optimizations",
        subtitle = "Forces classic exclusive fullscreen behavior for DirectX games",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Sets GameDVR_FSEBehaviorMode=2, GameDVR_HonorUserFSEBehaviorMode=1, and GameDVR_DXGIHonorFSEWindowsCompatible=1 in Default User GameConfigStore.",
        keywords = "gaming directx fps latency fso",
        isChecked = { it.disableFullscreenOptimizations },
        onToggle = { c, v -> c.copy(disableFullscreenOptimizations = v) }
    ),
    TweakItemSpec(
        id = "disableStoreSearch",
        section = TweakSection.ADVANCED,
        title = "Disable Microsoft Store Search",
        subtitle = "Stops 'Look for an app in the Microsoft Store' file prompts",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets NoUseStoreOpenWith=1 under HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows\\Explorer.",
        keywords = "open with store file association",
        isChecked = { it.disableStoreSearch },
        onToggle = { c, v -> c.copy(disableStoreSearch = v) }
    ),
    TweakItemSpec(
        id = "disableNotificationCenter",
        section = TweakSection.ADVANCED,
        title = "Disable Notification Tray / Calendar",
        subtitle = "Disables Windows Action Center and toast notifications",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Sets DisableNotificationCenter=1 under Default User Policies\\Microsoft\\Windows\\Explorer.",
        sideEffects = "Also hides the taskbar calendar flyout when clicking the clock.",
        keywords = "toast alerts calendar action center",
        isChecked = { it.disableNotificationCenter },
        onToggle = { c, v -> c.copy(disableNotificationCenter = v) }
    ),
    TweakItemSpec(
        id = "disableReservedStorage",
        section = TweakSection.ADVANCED,
        title = "Disable Reserved Storage",
        subtitle = "Reclaims ~7 GB reserved by Windows Update on C:",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Runs dism /Online /Set-ReservedStorageState /State:Disabled.",
        keywords = "disk space ssd storage",
        isChecked = { it.disableReservedStorage },
        onToggle = { c, v -> c.copy(disableReservedStorage = v) }
    ),
    TweakItemSpec(
        id = "disableSearchIndexing",
        section = TweakSection.ADVANCED,
        title = "Disable Windows Search Indexing",
        subtitle = "Disables WSearch background disk indexer",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Runs sc config WSearch start= disabled.",
        sideEffects = "File Explorer and Outlook searches become noticeably slower (not recommended on SSDs unless you use Everything Search).",
        keywords = "wsearch disk cpu indexing search",
        isChecked = { it.disableSearchIndexing },
        onToggle = { c, v -> c.copy(disableSearchIndexing = v) }
    ),
    TweakItemSpec(
        id = "disableSvchostSplitting",
        section = TweakSection.ADVANCED,
        title = "Disable svchost.exe Splitting",
        subtitle = "Consolidates Windows services (reduces Task Manager process count)",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Sets SvcHostSplitThresholdInKB=67108864 (64 GB) under HKLM\\SYSTEM\\CurrentControlSet\\Control.",
        sideEffects = "Reduces service isolation; best suited for systems with 16 GB+ RAM.",
        keywords = "ram processes task manager memory",
        isChecked = { it.disableSvchostSplitting },
        onToggle = { c, v -> c.copy(disableSvchostSplitting = v) }
    ),
    TweakItemSpec(
        id = "blockRazerLogitechAutoInstall",
        section = TweakSection.ADVANCED,
        title = "Block Razer / Logitech Auto-Install",
        subtitle = "Stops vendor software popups when plugging in peripherals",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Sets Policies\\DriverSearching SearchOrderConfig=0 and Device Metadata PreventDeviceMetadataFromNetwork=1.",
        sideEffects = "Warning: SearchOrderConfig=0 stops Plug-and-Play from querying Windows Update automatically for newly connected hardware drivers.",
        keywords = "peripheral mouse keyboard synapse ghub driver",
        isChecked = { it.blockRazerLogitechAutoInstall },
        onToggle = { c, v -> c.copy(blockRazerLogitechAutoInstall = v) }
    ),
    TweakItemSpec(
        id = "disableUnsignedRdpWarnings",
        section = TweakSection.ADVANCED,
        title = "Disable Unsigned RDP Warnings",
        subtitle = "Suppresses self-signed certificate warnings in Remote Desktop",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Sets AuthenticationLevelOverride=0 under HKLM\\SOFTWARE\\Policies\\Microsoft\\Windows NT\\Terminal Services\\Client.",
        sideEffects = "Reduces MITM certificate warnings when connecting over untrusted networks.",
        keywords = "remote desktop rdp mstsc homelab",
        isChecked = { it.disableUnsignedRdpWarnings },
        onToggle = { c, v -> c.copy(disableUnsignedRdpWarnings = v) }
    ),
    TweakItemSpec(
        id = "preferIpv4OverIpv6",
        section = TweakSection.ADVANCED,
        title = "Prefer IPv4 over IPv6",
        subtitle = "Prioritizes IPv4 without disabling IPv6 stack (safer)",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets DisabledComponents=32 (0x20) under HKLM\\SYSTEM\\CurrentControlSet\\Services\\Tcpip6\\Parameters.",
        keywords = "network ipv4 ipv6 tcpip latency",
        isChecked = { it.preferIpv4OverIpv6 },
        onToggle = { c, v -> c.copy(preferIpv4OverIpv6 = v, disableIpv6 = if (v) false else c.disableIpv6) }
    ),
    TweakItemSpec(
        id = "disableIpv6",
        section = TweakSection.ADVANCED,
        title = "Disable IPv6 Completely",
        subtitle = "Turns off IPv6 on all interfaces (DisabledComponents = 0xFF)",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Sets DisabledComponents=255 (0xFF) under HKLM\\SYSTEM\\CurrentControlSet\\Services\\Tcpip6\\Parameters.",
        sideEffects = "May break IPv6-only networks, Xbox Live Teredo, or certain VPNs. Mutually exclusive with Prefer IPv4.",
        keywords = "network ipv6 tcpip",
        isChecked = { it.disableIpv6 },
        onToggle = { c, v -> c.copy(disableIpv6 = v, preferIpv4OverIpv6 = if (v) false else c.preferIpv4OverIpv6) }
    ),
    TweakItemSpec(
        id = "disableTeredo",
        section = TweakSection.ADVANCED,
        title = "Disable Teredo Tunneling",
        subtitle = "Turns off legacy IPv6-over-IPv4 NAT relay",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Runs netsh interface teredo set state disabled.",
        sideEffects = "Some older Xbox Live / Forza P2P multiplayer sessions use Teredo.",
        keywords = "network xbox p2p nat ipv6",
        isChecked = { it.disableTeredo },
        onToggle = { c, v -> c.copy(disableTeredo = v) }
    ),
    TweakItemSpec(
        id = "visualEffectsBestPerformance",
        section = TweakSection.ADVANCED,
        title = "Visual Effects: Best Performance",
        subtitle = "Disables window animations, fade effects & drop shadows",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets VisualFXSetting=2 under Default User Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\VisualEffects.",
        keywords = "animations shadows performance ui speed",
        isChecked = { it.visualEffectsBestPerformance },
        onToggle = { c, v -> c.copy(visualEffectsBestPerformance = v) }
    ),
    TweakItemSpec(
        id = "setHardwareClockUtc",
        section = TweakSection.ADVANCED,
        title = "Set Hardware Clock to UTC",
        subtitle = "Fixes 5.5-hour clock jump when dual-booting Linux & Windows",
        impact = TweakImpact.SAFE,
        technicalDetails = "Sets RealTimeIsUniversal=1 under HKLM\\SYSTEM\\CurrentControlSet\\Control\\TimeZoneInformation.",
        keywords = "dual boot linux time clock rtc utc",
        isChecked = { it.setHardwareClockUtc },
        onToggle = { c, v -> c.copy(setHardwareClockUtc = v) }
    ),
    TweakItemSpec(
        id = "enableLegacyF8Boot",
        section = TweakSection.ADVANCED,
        title = "Enable Legacy F8 Boot Menu",
        subtitle = "Restores classic F8 Safe Mode boot menu on startup",
        impact = TweakImpact.SAFE,
        technicalDetails = "Runs bcdedit /set {default} bootmenupolicy legacy.",
        keywords = "safe mode recovery boot f8 bcdedit",
        isChecked = { it.enableLegacyF8Boot },
        onToggle = { c, v -> c.copy(enableLegacyF8Boot = v) }
    ),
    TweakItemSpec(
        id = "forceS3Sleep",
        section = TweakSection.ADVANCED,
        title = "Force Legacy S3 Sleep",
        subtitle = "Overrides Modern Standby (S0) back to classic S3 suspend",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Sets PlatformAoAcOverride=0 under HKLM\\SYSTEM\\CurrentControlSet\\Control\\Power.",
        sideEffects = "Breaks sleep completely on modern laptops whose BIOS removed S3 tables. Mutually exclusive with Disable S0 Sleep Network Connectivity.",
        keywords = "sleep standby s3 s0 laptop power",
        isChecked = { it.forceS3Sleep },
        onToggle = { c, v -> c.copy(forceS3Sleep = v, disableS0Network = if (v) false else c.disableS0Network) }
    ),

    // ===== PERFORMANCE PLANS (1) =====
    TweakItemSpec(
        id = "ultimatePerformancePlan",
        section = TweakSection.PERFORMANCE,
        title = "Add & Activate Ultimate Performance Plan",
        subtitle = "Unlocks and activates Windows's hidden maximum-performance plan",
        impact = TweakImpact.CAUTION,
        technicalDetails = "Runs powercfg -duplicatescheme e9a42b02-d5df-448d-aa00-03f14749eb61 and activates the created scheme.",
        sideEffects = "Designed for plugged-in desktops; increases idle power draw and heat on laptops.",
        keywords = "power plan cpu gaming desktop performance",
        isChecked = { it.ultimatePerformancePlan },
        onToggle = { c, v -> c.copy(ultimatePerformancePlan = v) }
    )
)

@Composable
fun SystemTweaksScreen(
    config: WinUtilTweaksConfig,
    experience: WindowsExperienceConfig,
    customPresets: List<SystemTweaksPreset>,
    searchHistory: List<String>,
    onApply: (WinUtilTweaksConfig) -> Unit,
    onDismiss: () -> Unit,
    onSaveCustomPreset: (SystemTweaksPreset) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onUpdateSearchHistory: (List<String>) -> Unit
) {
    var draft by remember(config) { mutableStateOf(config) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeOnly by rememberSaveable { mutableStateOf(false) }
    var lastSelectedPreset by remember(config) {
        val norm = config.normalizedForComparison()
        mutableStateOf(
            WinUtilTweaksConfig.matchedBuiltInPresetName(norm)
                ?: customPresets.firstOrNull { WinUtilTweaksConfig.fromJsonString(it.tweaksJson).normalizedForComparison() == norm }?.name
        )
    }
    var essentialExpanded by remember { mutableStateOf(true) }
    var preferencesExpanded by remember { mutableStateOf(true) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var performanceExpanded by remember { mutableStateOf(true) }
    var appxPickerExpanded by remember { mutableStateOf(false) }

    var infoDialog by remember { mutableStateOf<TweakInfoDetail?>(null) }
    var showSaveCustomDialog by remember { mutableStateOf(false) }
    var customPresetName by remember { mutableStateOf("") }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val hasUnsavedChanges = draft != config
    val totalActive = draft.activeCount()
    val normDraft = remember(draft) { draft.normalizedForComparison() }
    val exactBuiltInMatch = remember(normDraft) { WinUtilTweaksConfig.matchedBuiltInPresetName(normDraft) }
    val exactCustomMatch = remember(normDraft, customPresets) {
        customPresets.firstOrNull { WinUtilTweaksConfig.fromJsonString(it.tweaksJson).normalizedForComparison() == normDraft }?.name
    }
    val exactMatch = exactBuiltInMatch ?: exactCustomMatch
    val presetStatusLabel = when {
        exactMatch != null -> exactMatch
        totalActive == 0 -> null
        lastSelectedPreset != null -> "$lastSelectedPreset (Modified)"
        else -> "Custom"
    }

    fun requestClose() {
        if (hasUnsavedChanges) showUnsavedDialog = true else onDismiss()
    }

    fun commitSearchToHistory(query: String) {
        val clean = query.trim()
        if (clean.length >= 2) {
            onUpdateSearchHistory(AppPrefs.recordSearchQuery(searchHistory, clean))
        }
    }

    val trimmedQuery = searchQuery.trim().lowercase()
    val isFiltering = trimmedQuery.isNotEmpty() || activeOnly

    fun matchesQuery(spec: TweakItemSpec): Boolean {
        if (activeOnly && !spec.isChecked(draft)) return false
        if (trimmedQuery.isEmpty()) return true
        return spec.title.lowercase().contains(trimmedQuery) ||
            spec.subtitle.lowercase().contains(trimmedQuery) ||
            spec.keywords.lowercase().contains(trimmedQuery) ||
            spec.technicalDetails.lowercase().contains(trimmedQuery) ||
            spec.section.title.lowercase().contains(trimmedQuery)
    }

    val mpoMatchesFilter = (!activeOnly || draft.mpoMode != WinUtilTweaksConfig.MPO_DEFAULT) && (
        trimmedQuery.isEmpty() ||
            "multiplane overlay mpo gpu flicker stutter dwm customize preferences".contains(trimmedQuery)
        )

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = PocketColors.bgCanvas
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // ===== TOP HEADER BAR =====
                Surface(
                    color = PocketColors.surfaceCard,
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(onClick = { requestClose() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = PocketColors.textPrimary)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("System Tweaks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = PocketColors.textPrimary)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (totalActive > 0) PocketColors.accentBg else PocketColors.surfaceSubtle
                                ) {
                                    Text(
                                        text = "$totalActive active",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (totalActive > 0) PocketColors.accentSoft else PocketColors.textMuted
                                    )
                                }
                            }
                            Text(
                                text = if (presetStatusLabel != null) "Profile: $presetStatusLabel · Offline script (ISO untouched)" else "Offline first-boot script · Zero ISO file edits",
                                fontSize = 11.sp,
                                color = PocketColors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                draft = WinUtilTweaksConfig(addUndoScript = draft.addUndoScript)
                                lastSelectedPreset = null
                            },
                            enabled = totalActive > 0
                        ) {
                            Icon(
                                Icons.Rounded.RestartAlt,
                                "Reset all tweaks",
                                tint = if (totalActive > 0) PocketColors.textPrimary else PocketColors.textMuted
                            )
                        }
                    }
                }

                // ===== SCROLLABLE BODY (Keyed LazyColumn) =====
                val visibleSectionsWithMatches = TweakSection.entries.mapNotNull { section ->
                    val sectionSpecs = TWEAK_CATALOG.filter { it.section == section && matchesQuery(it) }
                    val showMpoInSection = section == TweakSection.PREFERENCES && mpoMatchesFilter
                    if (sectionSpecs.isEmpty() && !showMpoInSection) null else Triple(section, sectionSpecs, showMpoInSection)
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "tweaks_controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Search Bar with Persistent History
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search tweaks (AI, Edge, Update, Battery, MPO...)", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Search, null, tint = PocketColors.textSecondary, modifier = Modifier.size(19.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, "Clear search", tint = PocketColors.textMuted, modifier = Modifier.size(17.dp))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            commitSearchToHistory(searchQuery)
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PocketColors.accent,
                            unfocusedBorderColor = PocketColors.border,
                            focusedContainerColor = PocketColors.surfaceInput,
                            unfocusedContainerColor = PocketColors.surfaceInput,
                            focusedTextColor = PocketColors.textPrimary,
                            unfocusedTextColor = PocketColors.textPrimary
                        )
                    )

                    // Search History Chips (or Starter Suggestions when empty)
                    if (searchQuery.isBlank()) {
                        if (searchHistory.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.History, null, tint = PocketColors.textMuted, modifier = Modifier.size(15.dp))
                                searchHistory.forEach { term ->
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = PocketColors.surfaceInput,
                                        border = BorderStroke(0.5.dp, PocketColors.border)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = term,
                                                fontSize = 11.5.sp,
                                                color = PocketColors.textPrimary,
                                                modifier = Modifier.clickable { searchQuery = term }
                                            )
                                            Icon(
                                                Icons.Rounded.Close,
                                                "Remove",
                                                tint = PocketColors.textMuted,
                                                modifier = Modifier.size(13.dp).clickable {
                                                    onUpdateSearchHistory(searchHistory.filterNot { it.equals(term, ignoreCase = true) })
                                                }
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Clear",
                                    fontSize = 11.sp,
                                    color = PocketColors.accentSoft,
                                    modifier = Modifier.clickable { onUpdateSearchHistory(emptyList()) }.padding(horizontal = 4.dp)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Try:", fontSize = 11.sp, color = PocketColors.textMuted)
                                STARTER_SEARCH_SUGGESTIONS.forEach { hint ->
                                    Surface(
                                        modifier = Modifier.bounceClick {
                                            searchQuery = hint
                                            commitSearchToHistory(hint)
                                        },
                                        shape = RoundedCornerShape(999.dp),
                                        color = PocketColors.surfaceInput,
                                        border = BorderStroke(0.5.dp, PocketColors.border)
                                    ) {
                                        Text(
                                            text = hint,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            fontSize = 11.sp,
                                            color = PocketColors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Filter & Optional "Add Undo Script" Badge Row
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Active Only filter chip
                        Surface(
                            modifier = Modifier.bounceClick { activeOnly = !activeOnly },
                            shape = RoundedCornerShape(9.dp),
                            color = if (activeOnly) PocketColors.accentBg else PocketColors.surfaceInput,
                            border = BorderStroke(0.75.dp, if (activeOnly) PocketColors.accent else PocketColors.border)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (activeOnly) Icon(Icons.Rounded.Check, null, tint = PocketColors.accentSoft, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Active Only ($totalActive)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (activeOnly) PocketColors.accentSoft else PocketColors.textSecondary
                                )
                            }
                        }

                        // Optional "Add Undo Script" Badge Toggle (OFF by default; won't drop to Desktop if unchecked)
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = if (draft.addUndoScript) PocketColors.successBg else PocketColors.surfaceInput,
                            border = BorderStroke(0.75.dp, if (draft.addUndoScript) PocketColors.success else PocketColors.border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clickable { draft = draft.copy(addUndoScript = !draft.addUndoScript) }
                                    .padding(start = 9.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    if (draft.addUndoScript) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                    null,
                                    tint = if (draft.addUndoScript) PocketColors.success else PocketColors.textMuted,
                                    modifier = Modifier.size(15.dp)
                                )
                                Icon(
                                    Icons.Rounded.Restore,
                                    null,
                                    tint = if (draft.addUndoScript) PocketColors.success else PocketColors.textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Add Undo Script",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (draft.addUndoScript) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (draft.addUndoScript) PocketColors.success else PocketColors.textPrimary
                                )
                                Icon(
                                    Icons.Rounded.Info,
                                    "About Add Undo Script",
                                    tint = if (draft.addUndoScript) PocketColors.success else PocketColors.textMuted,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .clickable {
                                            infoDialog = TweakInfoDetail(
                                                title = "Add Undo Script",
                                                impact = TweakImpact.SAFE,
                                                summary = "Drops an auto-elevated Undo_PocketBoot_Tweaks.cmd onto the Desktop so you can revert applied tweaks in one double-click.",
                                                technicalDetails = "When checked (and at least 1 tweak is active), PocketBoot copies C:\\Users\\Public\\Desktop\\Undo_PocketBoot_Tweaks.cmd with exact revert commands for every registry, service, Windows Update, Defender, MPO, and power tweak selected. When unchecked (default), nothing is placed on the Desktop.",
                                                sideEffects = "Excluded from tweak count and preset-modified status; never injects scripts on a pure stock ISO."
                                            )
                                        }
                                )
                            }
                        }
                    }

                    // Preset Strip (Official Presets + Desktop / Laptop + Custom Presets)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Presets", style = MaterialTheme.typography.labelMedium, color = PocketColors.textSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val builtIns = listOf(
                                Triple("Standard", "Recommended", WinUtilTweaksConfig.standardPreset()),
                                Triple("Minimal", null, WinUtilTweaksConfig.minimalPreset()),
                                Triple("Advanced", null, WinUtilTweaksConfig.advancedPreset()),
                                Triple("Desktop", null, WinUtilTweaksConfig.desktopPreset()),
                                Triple("Laptop", null, WinUtilTweaksConfig.laptopSafePreset())
                            )
                            builtIns.forEach { (name, tag, presetCfg) ->
                                val matched = exactBuiltInMatch == name
                                Surface(
                                    modifier = Modifier.bounceClick {
                                        draft = presetCfg.copy(addUndoScript = draft.addUndoScript)
                                        lastSelectedPreset = name
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (matched) PocketColors.accentBg else PocketColors.surfaceInput,
                                    border = BorderStroke(if (matched) 1.25.dp else 0.75.dp, if (matched) PocketColors.accent else PocketColors.border)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (matched) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (matched) PocketColors.accentSoft else PocketColors.textPrimary
                                        )
                                        if (tag != null) {
                                            Surface(shape = RoundedCornerShape(4.dp), color = PocketColors.successBg) {
                                                Text(tag, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp), fontSize = 11.sp, color = PocketColors.success)
                                            }
                                        }
                                    }
                                }
                            }

                            // Save Custom Preset Button
                            Surface(
                                modifier = Modifier.bounceClick { showSaveCustomDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                color = PocketColors.violetBg,
                                border = BorderStroke(0.75.dp, PocketColors.violet)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.BookmarkAdd, null, tint = PocketColors.violet, modifier = Modifier.size(14.dp))
                                    Text("Save Preset", style = MaterialTheme.typography.labelSmall, color = PocketColors.violet)
                                }
                            }

                            // Saved Custom Presets
                            customPresets.forEach { cp ->
                                val matched = exactCustomMatch == cp.name
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (matched) PocketColors.accentBg else PocketColors.surfaceSubtle,
                                    border = BorderStroke(if (matched) 1.25.dp else 0.75.dp, if (matched) PocketColors.accent else PocketColors.border)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = cp.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (matched) PocketColors.accentSoft else PocketColors.textPrimary,
                                            modifier = Modifier.clickable {
                                                draft = WinUtilTweaksConfig.fromJsonString(cp.tweaksJson).copy(addUndoScript = draft.addUndoScript)
                                                lastSelectedPreset = cp.name
                                            }
                                        )
                                        Icon(
                                            Icons.Rounded.Close,
                                            "Delete preset",
                                            tint = PocketColors.textMuted,
                                            modifier = Modifier.size(14.dp).clickable {
                                                if (lastSelectedPreset.equals(cp.name, ignoreCase = true)) {
                                                    lastSelectedPreset = null
                                                }
                                                onDeleteCustomPreset(cp.name)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // First-Time Subtle Hint Banner (when 0 tweaks active & not searching)
                    if (totalActive == 0 && !isFiltering) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PocketColors.infoBlueBg,
                            border = BorderStroke(0.5.dp, PocketColors.infoBlue.copy(alpha = 0.55f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Info, null, tint = PocketColors.infoBlue, modifier = Modifier.size(15.dp))
                                Text(
                                    text = "First time? Tap Standard, Desktop, or Laptop above to apply recommended tweaks, or leave empty for stock.",
                                    fontSize = 11.sp,
                                    color = PocketColors.textPrimary
                                )
                            }
                        }
                    }

                    }
                    }

                    // ===== SECTIONED TWEAK LIST (Lazily Recycled by Section) =====
                    items(visibleSectionsWithMatches, key = { "sec_${it.first.name}" }) { (section, sectionSpecs, showMpoInSection) ->

                        val activeInSection = when (section) {
                            TweakSection.ESSENTIAL -> draft.essentialActiveCount()
                            TweakSection.PREFERENCES -> draft.preferencesActiveCount()
                            TweakSection.ADVANCED -> draft.advancedActiveCount()
                            TweakSection.PERFORMANCE -> draft.performanceActiveCount()
                        }
                        val totalInSection = when (section) {
                            TweakSection.ESSENTIAL -> 21
                            TweakSection.PREFERENCES -> 21
                            TweakSection.ADVANCED -> 29
                            TweakSection.PERFORMANCE -> 1
                        }
                        val expanded = if (isFiltering) true else when (section) {
                            TweakSection.ESSENTIAL -> essentialExpanded
                            TweakSection.PREFERENCES -> preferencesExpanded
                            TweakSection.ADVANCED -> advancedExpanded
                            TweakSection.PERFORMANCE -> performanceExpanded
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = PocketColors.surfaceInput,
                            border = BorderStroke(
                                0.75.dp,
                                when {
                                    section.isCaution && activeInSection > 0 -> PocketColors.warning.copy(alpha = 0.65f)
                                    activeInSection > 0 -> PocketColors.accent.copy(alpha = 0.55f)
                                    else -> PocketColors.border
                                }
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            when (section) {
                                                TweakSection.ESSENTIAL -> essentialExpanded = !essentialExpanded
                                                TweakSection.PREFERENCES -> preferencesExpanded = !preferencesExpanded
                                                TweakSection.ADVANCED -> advancedExpanded = !advancedExpanded
                                                TweakSection.PERFORMANCE -> performanceExpanded = !performanceExpanded
                                            }
                                        }
                                        .padding(horizontal = 13.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (section.isCaution) {
                                            Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.warning, modifier = Modifier.size(17.dp))
                                        }
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (section.isCaution) PocketColors.warning else PocketColors.textPrimary
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when {
                                                activeInSection > 0 && section.isCaution -> PocketColors.warningBg
                                                activeInSection > 0 -> PocketColors.accentBg
                                                else -> PocketColors.surfaceSubtle
                                            }
                                        ) {
                                            Text(
                                                text = "$activeInSection/$totalInSection",
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when {
                                                    activeInSection > 0 && section.isCaution -> PocketColors.warning
                                                    activeInSection > 0 -> PocketColors.accentSoft
                                                    else -> PocketColors.textMuted
                                                }
                                            )
                                        }
                                        Icon(
                                            if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                            null,
                                            tint = PocketColors.textSecondary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(start = 11.dp, end = 11.dp, bottom = 11.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        if (section.isCaution) {
                                            Surface(
                                                shape = RoundedCornerShape(9.dp),
                                                color = PocketColors.warningBg,
                                                border = BorderStroke(0.5.dp, PocketColors.warning.copy(alpha = 0.6f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                                                ) {
                                                    Icon(Icons.Rounded.WarningAmber, null, tint = PocketColors.warning, modifier = Modifier.size(15.dp))
                                                    Text(
                                                        text = "Advanced tweaks modify core policies, browsers, services, or power plans. Tap (i) on any tweak to see exact changes.",
                                                        fontSize = 11.sp,
                                                        color = PocketColors.textPrimary
                                                    )
                                                }
                                            }
                                        }

                                        sectionSpecs.forEach { spec ->
                                            val checked = spec.isChecked(draft)
                                            TweakSwitchRowWithInfo(
                                                title = spec.title,
                                                subtitle = spec.subtitle,
                                                checked = checked,
                                                warningAccent = section.isCaution || spec.impact != TweakImpact.SAFE,
                                                onInfoClick = {
                                                    infoDialog = TweakInfoDetail(
                                                        title = spec.title,
                                                        impact = spec.impact,
                                                        summary = spec.subtitle,
                                                        technicalDetails = spec.technicalDetails,
                                                        sideEffects = spec.sideEffects
                                                    )
                                                },
                                                onChange = { next ->
                                                    if (trimmedQuery.isNotEmpty()) commitSearchToHistory(searchQuery)
                                                    draft = spec.onToggle(draft, next)
                                                }
                                            )

                                            // Expandable AppX Debloat Sub-Picker right below Debloat Default AppX
                                            if (spec.id == "debloatDefaultAppx" && checked) {
                                                val totalApps = WinUtilTweaksConfig.DEFAULT_DEBLOAT_APPX.size
                                                val removingCount = draft.activeAppxPackages().size
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = PocketColors.bgCanvas,
                                                    border = BorderStroke(0.5.dp, PocketColors.border)
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().clickable { appxPickerExpanded = !appxPickerExpanded },
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = "Removing $removingCount of $totalApps default apps (tap to keep specific apps)",
                                                                fontSize = 11.sp,
                                                                color = PocketColors.accentSoft,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Icon(
                                                                if (appxPickerExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                                null,
                                                                tint = PocketColors.accentSoft,
                                                                modifier = Modifier.size(17.dp)
                                                            )
                                                        }
                                                        AnimatedVisibility(visible = appxPickerExpanded) {
                                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                WinUtilTweaksConfig.DEFAULT_DEBLOAT_APPX.forEach { (pkg, display) ->
                                                                    val willRemove = pkg !in draft.excludedAppxPackages
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .clip(RoundedCornerShape(7.dp))
                                                                            .clickable {
                                                                                val nextExcluded = if (willRemove) draft.excludedAppxPackages + pkg else draft.excludedAppxPackages - pkg
                                                                                val allKept = nextExcluded.size >= WinUtilTweaksConfig.DEFAULT_DEBLOAT_APPX.size
                                                                                draft = draft.copy(
                                                                                    debloatDefaultAppx = !allKept,
                                                                                    excludedAppxPackages = if (allKept) emptySet() else nextExcluded
                                                                                )
                                                                            }
                                                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                                                        verticalAlignment = Alignment.CenterVertically,
                                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                                    ) {
                                                                        Icon(
                                                                            if (willRemove) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                                                                            null,
                                                                            tint = if (willRemove) PocketColors.accentSoft else PocketColors.textMuted,
                                                                            modifier = Modifier.size(16.dp)
                                                                        )
                                                                        Text(display, fontSize = 11.5.sp, color = PocketColors.textPrimary, modifier = Modifier.weight(1f))
                                                                        Text(if (willRemove) "Remove" else "Keep", fontSize = 11.sp, color = if (willRemove) PocketColors.warning else PocketColors.success)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 3-State Multiplane Overlay (MPO) Selector inside Customize Preferences
                                        if (showMpoInSection) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = PocketColors.surfaceCard,
                                                border = BorderStroke(
                                                    0.5.dp,
                                                    if (draft.mpoMode != WinUtilTweaksConfig.MPO_DEFAULT) PocketColors.accent.copy(alpha = 0.6f) else PocketColors.border
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Multiplane Overlay (MPO)", style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                                                            Text("Fixes GPU flicker/stutter in browsers & games on some drivers", fontSize = 11.sp, color = PocketColors.textSecondary)
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                infoDialog = TweakInfoDetail(
                                                                    title = "Multiplane Overlay (MPO)",
                                                                    impact = TweakImpact.SAFE,
                                                                    summary = "Controls Windows DWM Multiplane Overlay hardware composition.",
                                                                    technicalDetails = "• Default: Leaves DWM untouched.\n• Disable MPO: Sets HKLM\\SOFTWARE\\Microsoft\\Windows\\Dwm OverlayTestMode=5.\n• Force Enable: Removes OverlayTestMode and sets OverlayMinFPS=0.",
                                                                    sideEffects = "Disabling MPO fixes black-screen/flicker bugs on certain NVIDIA/AMD drivers with minimal overhead."
                                                                )
                                                            },
                                                            modifier = Modifier.size(44.dp)
                                                        ) {
                                                            Icon(Icons.Rounded.Info, "Info", tint = PocketColors.textMuted, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        listOf(
                                                            WinUtilTweaksConfig.MPO_DEFAULT to "Default",
                                                            WinUtilTweaksConfig.MPO_DISABLE to "Disable MPO",
                                                            WinUtilTweaksConfig.MPO_ENABLE to "Force Enable"
                                                        ).forEach { (modeKey, lbl) ->
                                                            val sel = draft.mpoMode == modeKey
                                                            Surface(
                                                                modifier = Modifier.weight(1f).bounceClick {
                                                                    if (trimmedQuery.isNotEmpty()) commitSearchToHistory(searchQuery)
                                                                    draft = draft.copy(mpoMode = modeKey)
                                                                },
                                                                shape = RoundedCornerShape(8.dp),
                                                                color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                                                border = BorderStroke(if (sel) 1.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                                                            ) {
                                                                Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                                                    Text(lbl, style = MaterialTheme.typography.labelSmall, color = if (sel) PocketColors.accentSoft else PocketColors.textSecondary)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (visibleSectionsWithMatches.isEmpty()) {
                        item(key = "tweaks_empty") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PocketColors.surfaceCard,
                                border = BorderStroke(0.5.dp, PocketColors.border)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (activeOnly && totalActive == 0) "No tweaks enabled yet" else "No matching tweaks found",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = PocketColors.textPrimary
                                    )
                                    Text(
                                        text = if (activeOnly && totalActive == 0) "Turn off Active Only or select a preset above." else "Try a broader keyword or clear your search filter.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PocketColors.textSecondary
                                    )
                                }
                            }
                        }
                    }

                    item(key = "tweaks_spacer") {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // ===== STICKY BOTTOM ACTION BAR =====
                Surface(
                    color = PocketColors.surfaceCard,
                    border = BorderStroke(0.5.dp, PocketColors.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showPreviewDialog = true },
                            modifier = Modifier.height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.75.dp, PocketColors.border)
                        ) {
                            Icon(Icons.Rounded.Preview, null, tint = PocketColors.textPrimary, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview .cmd", color = PocketColors.textPrimary, fontWeight = FontWeight.Medium)
                        }
                        Button(
                            onClick = {
                                if (trimmedQuery.isNotEmpty()) commitSearchToHistory(searchQuery)
                                onApply(draft)
                            },
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                        ) {
                            Text(
                                text = if (totalActive > 0) "Apply ($totalActive tweaks)" else "Apply (Stock)",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }

    // ===== PER-TWEAK (i) INFO DIALOG =====
    infoDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            containerColor = PocketColors.surfaceCard,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(info.title, style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (info.impact == TweakImpact.SAFE) PocketColors.successBg else PocketColors.warningBg
                    ) {
                        Text(
                            text = info.impact.label,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (info.impact == TweakImpact.SAFE) PocketColors.success else PocketColors.warning
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(info.summary, style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = PocketColors.bgCanvas,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Exact Changes", style = MaterialTheme.typography.labelSmall, color = PocketColors.accentSoft)
                            Text(info.technicalDetails, style = PocketColors.monoStyle, fontSize = 11.sp, color = PocketColors.textSecondary)
                        }
                    }
                    if (info.sideEffects.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = PocketColors.warningBg,
                            border = BorderStroke(0.5.dp, PocketColors.warning.copy(alpha = 0.6f))
                        ) {
                            Text(info.sideEffects, modifier = Modifier.padding(9.dp), fontSize = 11.sp, color = PocketColors.textPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { infoDialog = null }) {
                    Text("Got it", color = PocketColors.accentSoft)
                }
            }
        )
    }

    // ===== SAVE CUSTOM PRESET DIALOG =====
    if (showSaveCustomDialog) {
        val reservedNames = setOf("standard", "minimal", "advanced", "desktop", "laptop")
        val trimmedPresetName = customPresetName.trim()
        val isReservedName = trimmedPresetName.lowercase() in reservedNames
        val canSavePreset = trimmedPresetName.isNotEmpty() && !isReservedName && totalActive > 0
        AlertDialog(
            onDismissRequest = { showSaveCustomDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Save System Tweaks Preset", color = PocketColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (totalActive > 0) "Saves your current System Tweaks selection ($totalActive active) for quick reuse across Windows & Multi-Boot builds."
                        else "Select at least 1 tweak before saving a custom preset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (totalActive > 0) PocketColors.textSecondary else PocketColors.warning
                    )
                    OutlinedTextField(
                        value = customPresetName,
                        onValueChange = { customPresetName = it.take(26) },
                        label = { Text("Preset name") },
                        placeholder = { Text("e.g., My Gaming Rig") },
                        isError = isReservedName,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PocketColors.accent,
                            unfocusedBorderColor = PocketColors.border,
                            focusedContainerColor = PocketColors.surfaceInput,
                            unfocusedContainerColor = PocketColors.surfaceInput,
                            focusedTextColor = PocketColors.textPrimary,
                            unfocusedTextColor = PocketColors.textPrimary
                        )
                    )
                    if (isReservedName) {
                        Text(
                            text = "This name is reserved for a built-in preset. Choose a different name.",
                            fontSize = 11.sp,
                            color = PocketColors.warning
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (canSavePreset) {
                            onSaveCustomPreset(SystemTweaksPreset(trimmedPresetName, draft.normalizedForComparison().toJsonString()))
                            lastSelectedPreset = trimmedPresetName
                            customPresetName = ""
                            showSaveCustomDialog = false
                        }
                    },
                    enabled = canSavePreset,
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveCustomDialog = false }) {
                    Text("Cancel", color = PocketColors.textSecondary)
                }
            }
        )
    }

    // ===== INLINE .CMD PREVIEW DIALOG =====
    if (showPreviewDialog) {
        var previewTab by remember { mutableIntStateOf(0) }
        val showUndoTab = draft.addUndoScript && draft.hasAnyActive()
        val tabs = if (showUndoTab) listOf("Specialize.cmd", "SetupComplete.cmd", "Undo.cmd") else listOf("Specialize.cmd", "SetupComplete.cmd")
        val specText = remember(experience, draft) {
            if (draft.hasAnyActive() || experience.needsSpecializeScript()) WindowsCustomizationEngine.buildSpecializeScript(experience, draft)
            else "; No tweaks active — zero scripts will be added to your USB."
        }
        val scText = remember(experience, draft) {
            if (draft.hasAnyActive() || experience.needsSpecializeScript()) WindowsCustomizationEngine.buildSetupCompleteScript(false, experience.customPostInstallScriptPath.isNotBlank(), experience.customPostInstallScriptPath.substringAfterLast('/'), draft)
            else "; No post-OOBE script needed."
        }
        val undoText = remember(experience, draft) {
            if (showUndoTab) WindowsCustomizationEngine.buildUndoScript(experience, draft)
            else "; Add Undo Script is OFF."
        }
        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Generated Script Preview", color = PocketColors.textPrimary) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tabs.forEachIndexed { idx, lbl ->
                            val sel = previewTab == idx
                            Surface(
                                modifier = Modifier.weight(1f).bounceClick { previewTab = idx },
                                shape = RoundedCornerShape(8.dp),
                                color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                                border = BorderStroke(0.75.dp, if (sel) PocketColors.accent else PocketColors.border)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                                    Text(lbl, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (sel) PocketColors.accentSoft else PocketColors.textSecondary)
                                }
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = PocketColors.bgCanvas,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        val body = when (previewTab) {
                            0 -> specText
                            1 -> scText
                            else -> undoText
                        }
                        Text(body, style = PocketColors.monoStyle, fontSize = 11.sp, color = PocketColors.textSecondary, modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState()))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreviewDialog = false }) {
                    Text("Close", color = PocketColors.accentSoft)
                }
            }
        )
    }

    // ===== UNSAVED CHANGES GUARD DIALOG =====
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Apply System Tweaks?", color = PocketColors.textPrimary) },
            text = { Text("You changed your System Tweaks selection ($totalActive active). Apply these changes before going back?", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedDialog = false
                        onApply(draft)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Text("Apply & Close", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onDismiss()
                }) {
                    Text("Discard", color = PocketColors.danger)
                }
            }
        )
    }
}

@Composable
private fun TweakSwitchRowWithInfo(
    title: String,
    subtitle: String,
    checked: Boolean,
    warningAccent: Boolean = false,
    onInfoClick: () -> Unit,
    onChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = PocketColors.surfaceCard,
        border = BorderStroke(
            0.5.dp,
            if (checked) (if (warningAccent) PocketColors.warning.copy(alpha = 0.6f) else PocketColors.accent.copy(alpha = 0.55f)) else PocketColors.border
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onChange(!checked) }
                .padding(start = 11.dp, end = 9.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = PocketColors.textPrimary)
                Text(subtitle, fontSize = 11.sp, color = if (warningAccent && checked) PocketColors.warning else PocketColors.textSecondary)
            }
            IconButton(onClick = onInfoClick, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.Info, "Details for $title", tint = PocketColors.textMuted, modifier = Modifier.size(16.dp))
            }
            PocketSwitch(
                checked = checked,
                onCheckedChange = onChange,
                activeColor = if (warningAccent) PocketColors.warning else PocketColors.accent
            )
        }
    }
}
