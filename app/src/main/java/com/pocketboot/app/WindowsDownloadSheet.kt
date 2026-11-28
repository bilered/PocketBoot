package com.pocketboot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Window
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Locale

private enum class WindowsDownloadSource(val label: String, val badge: String) {
    NTRIVER_ARCHIVE("NTriver Archive", "Latest · SHA-256"),
    MICROSOFT_CDN("Microsoft CDN", "Official 24h")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WindowsDownloadSheet(
    family: WindowsCatalogFamily,
    turboConnections: Int,
    existingFileNames: Set<String> = emptySet(),
    onAppendLog: (LogEntry) -> Unit = {},
    onDismiss: () -> Unit,
    onStartDirectDownload: (title: String, url: String, fileName: String, expectedSha256: String, refreshSourceKey: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeSource by remember(family.id) { mutableStateOf(WindowsDownloadSource.NTRIVER_ARCHIVE) }

    // ─── NTriver Archive State ───────────────────────────────────────────
    var ntLoading by remember(family.id) { mutableStateOf(false) }
    var ntSections by remember(family.id) { mutableStateOf<List<NtriverReleaseSection>>(emptyList()) }
    var ntShowAllEditions by remember(family.id) { mutableStateOf(false) }
    var selectedNtMajorVersion by remember(family.id) { mutableStateOf("") }
    var selectedNtSection by remember(family.id) { mutableStateOf<NtriverReleaseSection?>(null) }
    var selectedNtArch by remember(family.id) { mutableStateOf("x64") }
    var selectedNtFileName by remember(family.id) { mutableStateOf("") }
    var ntError by remember(family.id) { mutableStateOf<String?>(null) }
    var ntResolving by remember(family.id) { mutableStateOf(false) }
    var ntResolveGen by remember(family.id) { mutableIntStateOf(0) }
    var ntRetryNonce by remember(family.id) { mutableIntStateOf(0) }

    // ─── Microsoft Direct CDN State (lazy-loaded only when tab selected) ─
    var directLoadingReleases by remember(family.id) { mutableStateOf(false) }
    var directReleaseGroups by remember(family.id) { mutableStateOf<List<DirectReleaseGroup>>(emptyList()) }
    var selectedDirectGroup by remember(family.id) { mutableStateOf<DirectReleaseGroup?>(null) }
    var directWantArm64 by remember(family.id) { mutableStateOf(false) }
    var directWin10Arch by remember(family.id) { mutableStateOf("x64") }
    var directSessionId by remember(family.id) { mutableStateOf("") }
    var directSkus by remember(family.id) { mutableStateOf<List<DirectLanguageSku>>(emptyList()) }
    var directSkuLoading by remember(family.id) { mutableStateOf(false) }
    var selectedDirectSku by remember(family.id) { mutableStateOf<DirectLanguageSku?>(null) }
    var directLinks by remember(family.id) { mutableStateOf<List<DirectIsoLink>>(emptyList()) }
    var directLinkLoading by remember(family.id) { mutableStateOf(false) }
    var selectedDirectLink by remember(family.id) { mutableStateOf<DirectIsoLink?>(null) }
    var directError by remember(family.id) { mutableStateOf<String?>(null) }
    var directRefreshNonce by remember(family.id) { mutableIntStateOf(0) }
    var retryNonce by remember(family.id) { mutableIntStateOf(0) }

    var langMenuOpen by remember { mutableStateOf(false) }
    var langSearch by remember { mutableStateOf("") }

    val ntMajorVersions: List<String> = remember(ntSections) {
        ntSections.map { it.effectiveMajorVersion }.distinct()
    }
    val sectionsInSelectedMajor: List<NtriverReleaseSection> = remember(ntSections, selectedNtMajorVersion) {
        if (selectedNtMajorVersion.isBlank()) ntSections
        else ntSections.filter { it.effectiveMajorVersion.equals(selectedNtMajorVersion, ignoreCase = true) }.ifEmpty { ntSections }
    }
    val visibleNtSections: List<NtriverReleaseSection> = remember(sectionsInSelectedMajor, ntShowAllEditions) {
        if (ntShowAllEditions) sectionsInSelectedMajor
        else sectionsInSelectedMajor.filter { it.isRecommended }.ifEmpty { sectionsInSelectedMajor }
    }
    val hiddenNicheCount = (sectionsInSelectedMajor.size - sectionsInSelectedMajor.count { it.isRecommended }).coerceAtLeast(0)

    val ntArchesForSection: List<String> = remember(selectedNtSection) {
        selectedNtSection?.distinctArches.orEmpty()
    }
    val ntEntriesForArch: List<NtriverIsoEntry> = remember(selectedNtSection, selectedNtArch) {
        val sec = selectedNtSection ?: return@remember emptyList()
        val matched = sec.entries.filter { it.arch.equals(selectedNtArch, ignoreCase = true) }
        matched.ifEmpty { sec.entries }
    }
    fun pickDefaultNtEntry(entries: List<NtriverIsoEntry>): NtriverIsoEntry? =
        entries.firstOrNull { it.effectiveLanguageLabel.equals("English (US)", true) || it.effectiveLanguageLabel.equals("English", true) }
            ?: entries.firstOrNull { it.language.contains("English", true) }
            ?: entries.firstOrNull()

    val activeNtEntry: NtriverIsoEntry? = remember(ntEntriesForArch, selectedNtFileName) {
        ntEntriesForArch.firstOrNull { it.fileName.equals(selectedNtFileName, ignoreCase = true) }
            ?: pickDefaultNtEntry(ntEntriesForArch)
    }
    val fullLangFallbackSection: NtriverReleaseSection? = remember(selectedNtSection, visibleNtSections, ntSections) {
        val cur = selectedNtSection ?: return@remember null
        if (cur.distinctLanguages.size >= 5) return@remember null
        visibleNtSections.firstOrNull { it.id != cur.id && it.distinctLanguages.size >= 5 }
            ?: ntSections.firstOrNull { it.id != cur.id && it.isRecommended && it.distinctLanguages.size >= 5 }
            ?: ntSections.firstOrNull { it.id != cur.id && it.distinctLanguages.size >= 5 }
    }

    val activeDirectRelease: DirectWindowsRelease? = remember(selectedDirectGroup, directWantArm64) {
        val grp = selectedDirectGroup ?: return@remember null
        if (directWantArm64 && grp.arm64Release != null) grp.arm64Release else grp.defaultRelease
    }

    fun selectNtSectionAndDefaults(sec: NtriverReleaseSection?) {
        selectedNtSection = sec
        if (sec != null) {
            selectedNtMajorVersion = sec.effectiveMajorVersion
        }
        ntError = null
        ntResolveGen++
        ntResolving = false
        if (sec != null) {
            val arches = sec.distinctArches
            if (arches.none { it.equals(selectedNtArch, true) }) {
                selectedNtArch = arches.firstOrNull { it.equals("x64", true) } ?: arches.firstOrNull() ?: "x64"
            }
            val archEntries = sec.entries.filter { it.arch.equals(selectedNtArch, true) }.ifEmpty { sec.entries }
            if (archEntries.none { it.fileName.equals(selectedNtFileName, true) }) {
                selectedNtFileName = pickDefaultNtEntry(archEntries)?.fileName.orEmpty()
            }
        }
    }

    // Load NTriver Archive when opened or refreshed (structured cancellation prevents overlapping stale overwrites)
    LaunchedEffect(family.id, ntRetryNonce) {
        ntLoading = true
        ntError = null
        val res = NtriverCatalog.fetchSections(context, family, forceRefresh = ntRetryNonce > 0, onLog = onAppendLog)
        if (res.data != null) {
            ntSections = res.data
            val firstMajor = res.data.firstOrNull()?.effectiveMajorVersion.orEmpty()
            selectedNtMajorVersion = firstMajor
            val inFirstMajor = res.data.filter { it.effectiveMajorVersion.equals(firstMajor, true) }.ifEmpty { res.data }
            val initialSec = if (ntShowAllEditions) inFirstMajor.firstOrNull() else inFirstMajor.firstOrNull { it.isRecommended } ?: inFirstMajor.firstOrNull()
            selectNtSectionAndDefaults(initialSec)
        } else {
            ntSections = emptyList()
            selectedNtMajorVersion = ""
            selectNtSectionAndDefaults(null)
            ntError = res.errorMessage
        }
        ntLoading = false
    }

    // Keep selected section valid if user toggles Recommended vs All Editions
    LaunchedEffect(visibleNtSections) {
        if (visibleNtSections.isNotEmpty() && visibleNtSections.none { it.id == selectedNtSection?.id }) {
            selectNtSectionAndDefaults(visibleNtSections.firstOrNull())
        }
    }

    // Keep selected entry valid when user switches Architecture chip in NTriver
    LaunchedEffect(selectedNtArch, selectedNtSection?.id) {
        val sec = selectedNtSection ?: return@LaunchedEffect
        val archEntries = sec.entries.filter { it.arch.equals(selectedNtArch, true) }.ifEmpty { sec.entries }
        if (archEntries.none { it.fileName.equals(selectedNtFileName, true) }) {
            selectedNtFileName = pickDefaultNtEntry(archEntries)?.fileName.orEmpty()
        }
    }

    // Lazy-load Microsoft Direct CDN ONLY when user selects MS CDN tab or refreshes it (structured cancellation)
    LaunchedEffect(activeSource, family.id, directRefreshNonce) {
        if (activeSource != WindowsDownloadSource.MICROSOFT_CDN || !family.supportsMicrosoftCdn) return@LaunchedEffect
        if (directRefreshNonce == 0 && directReleaseGroups.isNotEmpty()) return@LaunchedEffect
        directLoadingReleases = true
        directError = null
        val raw = WindowsDirectCatalog.fetchReleases(family, forceRefresh = directRefreshNonce > 0)
        val groups = WindowsDirectCatalog.groupReleases(raw)
        directReleaseGroups = groups
        selectedDirectGroup = groups.firstOrNull()
        directWantArm64 = false
        directLoadingReleases = false
    }

    // Fetch languages when active MS CDN Edition ID changes (only while MS CDN tab is active & releases loaded)
    LaunchedEffect(activeSource, directLoadingReleases, activeDirectRelease?.editionId, retryNonce) {
        if (activeSource != WindowsDownloadSource.MICROSOFT_CDN || directLoadingReleases) return@LaunchedEffect
        val rel = activeDirectRelease ?: return@LaunchedEffect
        directSkuLoading = true
        directError = null
        directLinks = emptyList()
        selectedDirectLink = null
        val res = WindowsDirectCatalog.fetchLanguages(context, rel)
        if (res.data != null) {
            directSessionId = res.data.first
            directSkus = res.data.second
            selectedDirectSku = directSkus.firstOrNull { it.localizedLanguage.contains("English (United States)", true) }
                ?: directSkus.firstOrNull()
        } else {
            directSkus = emptyList()
            selectedDirectSku = null
            directError = res.errorMessage
        }
        directSkuLoading = false
    }

    // Fetch signed MS CDN links when language changes
    LaunchedEffect(activeSource, activeDirectRelease?.editionId, selectedDirectSku?.skuId, retryNonce) {
        if (activeSource != WindowsDownloadSource.MICROSOFT_CDN) return@LaunchedEffect
        val rel = activeDirectRelease ?: return@LaunchedEffect
        val sku = selectedDirectSku ?: return@LaunchedEffect
        if (directSessionId.isBlank()) return@LaunchedEffect
        directLinkLoading = true
        directError = null
        val res = WindowsDirectCatalog.fetchDownloadLinks(context, rel, directSessionId, sku.skuId)
        if (res.data != null) {
            directLinks = res.data
            selectedDirectLink = res.data.firstOrNull { it.arch.equals(directWin10Arch, true) } ?: res.data.firstOrNull()
        } else {
            directLinks = emptyList()
            selectedDirectLink = null
            directError = res.errorMessage
        }
        directLinkLoading = false
    }

    LaunchedEffect(directWin10Arch, directLinks) {
        if (directLinks.size > 1) {
            selectedDirectLink = directLinks.firstOrNull { it.arch.equals(directWin10Arch, true) } ?: directLinks.firstOrNull()
        }
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
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            PocketSheetHeader(
                icon = Icons.Rounded.Window,
                tint = PocketColors.infoBlue,
                iconBg = PocketColors.infoBlueBg,
                title = family.title,
                subtitle = if (activeSource == WindowsDownloadSource.NTRIVER_ARCHIVE) {
                    "Latest RTM & Monthly MVS ISOs · Built-in SHA-256"
                } else {
                    "Direct Official Retail ISO from Microsoft CDN"
                },
                trailing = {
                    IconButton(
                        onClick = {
                            if (activeSource == WindowsDownloadSource.NTRIVER_ARCHIVE) {
                                ntRetryNonce++
                            } else {
                                directRefreshNonce++
                                retryNonce++
                            }
                        }
                    ) {
                        Icon(Icons.Rounded.Refresh, "Refresh", tint = PocketColors.textSecondary)
                    }
                }
            )

            // 2-Tab Source Switcher (only shown for families supported by both NTriver & Microsoft CDN)
            if (family.supportsMicrosoftCdn) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WindowsDownloadSource.entries.forEach { src ->
                        val sel = activeSource == src
                        Surface(
                            modifier = Modifier.weight(1f).bounceClick {
                                activeSource = src
                                ntResolveGen++
                                ntResolving = false
                                langMenuOpen = false
                            },
                            shape = RoundedCornerShape(11.dp),
                            color = if (sel) PocketColors.accentBg else PocketColors.surfaceInput,
                            border = BorderStroke(if (sel) 1.25.dp else 0.5.dp, if (sel) PocketColors.accent else PocketColors.border)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = src.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (sel) PocketColors.accentSoft else PocketColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = src.badge,
                                    fontSize = 11.sp,
                                    color = if (sel) PocketColors.success else PocketColors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            if (activeSource == WindowsDownloadSource.NTRIVER_ARCHIVE) {
                // ════════════════════════════════════════════════════════════
                // NTRIVER ARCHIVE PANE
                // ════════════════════════════════════════════════════════════
                if (ntLoading) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = PocketColors.accent)
                            Text("Loading latest ${family.title} RTM catalog...", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                    }
                } else {
                    // Level 1: Main Version chips (e.g., 26H2, 25H2, 24H2 / Update 3, Update 1 / Server 2025, 2022, 2019)
                    if (ntMajorVersions.size > 1) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PocketSectionHeader("Main Version", trailing = "${ntMajorVersions.size} releases")
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                ntMajorVersions.forEachIndexed { idx, ver ->
                                    val isSel = ver.equals(selectedNtMajorVersion, ignoreCase = true)
                                    Surface(
                                        modifier = Modifier.bounceClick {
                                            selectedNtMajorVersion = ver
                                            ntShowAllEditions = false
                                            val inVer = ntSections.filter { it.effectiveMajorVersion.equals(ver, true) }
                                            val pick = inVer.firstOrNull { it.isRecommended } ?: inVer.firstOrNull()
                                            selectNtSectionAndDefaults(pick)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) PocketColors.accentBg else PocketColors.surfaceInput,
                                        border = BorderStroke(if (isSel) 1.dp else 0.5.dp, if (isSel) PocketColors.accent else PocketColors.border)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = ver,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (isSel) PocketColors.accentSoft else PocketColors.textSecondary
                                            )
                                            if (idx == 0) {
                                                Text("Latest", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PocketColors.success)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Level 2: Sub-Category / Edition chips belonging ONLY to the chosen Main Version
                    if (visibleNtSections.isNotEmpty() && (visibleNtSections.size > 1 || hiddenNicheCount > 0 || ntMajorVersions.size <= 1)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PocketSectionHeader(
                                title = if (ntMajorVersions.size > 1) "Edition · $selectedNtMajorVersion" else "Release / Edition",
                                trailing = if (hiddenNicheCount > 0) {
                                    if (ntShowAllEditions) "All (${sectionsInSelectedMajor.size})" else "${visibleNtSections.size} recommended"
                                } else "${visibleNtSections.size} available"
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                visibleNtSections.forEachIndexed { idx, sec ->
                                    val isSel = sec.id == selectedNtSection?.id
                                    val chipText = if (ntMajorVersions.size > 1) sec.effectiveEditionLabel else sec.shortChipLabel
                                    Surface(
                                        modifier = Modifier.bounceClick { selectNtSectionAndDefaults(sec) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) PocketColors.infoBlueBg else PocketColors.surfaceInput,
                                        border = BorderStroke(if (isSel) 1.dp else 0.5.dp, if (isSel) PocketColors.infoBlue else PocketColors.border)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = chipText,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (isSel) PocketColors.infoBlue else PocketColors.textSecondary
                                            )
                                            if (idx == 0 && ntMajorVersions.size <= 1) {
                                                Text("Latest", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PocketColors.success)
                                            }
                                        }
                                    }
                                }
                                if (hiddenNicheCount > 0) {
                                    Surface(
                                        modifier = Modifier.bounceClick { ntShowAllEditions = !ntShowAllEditions },
                                        shape = RoundedCornerShape(10.dp),
                                        color = PocketColors.surfaceSubtle,
                                        border = BorderStroke(0.5.dp, PocketColors.border)
                                    ) {
                                        Text(
                                            text = if (ntShowAllEditions) "Recommended Only" else "+$hiddenNicheCount More",
                                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PocketColors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    selectedNtSection?.let { sec ->
                        // Build & Source Info Badges
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, PocketColors.border)
                            ) {
                                Row(modifier = Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.CalendarMonth, null, tint = PocketColors.success, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("BUILD", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = PocketColors.textMuted)
                                        Text(sec.buildBadge.ifBlank { sec.shortChipLabel }, style = PocketColors.monoStyle, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, PocketColors.border)
                            ) {
                                val isStatic = activeNtEntry?.isMicrosoftStatic == true
                                Row(modifier = Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.VerifiedUser, null, tint = if (isStatic) PocketColors.success else PocketColors.infoBlue, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("SOURCE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = PocketColors.textMuted)
                                        Text(
                                            if (isStatic) "MS Static CDN" else "NTriver Drive",
                                            style = PocketColors.monoStyle,
                                            color = if (isStatic) PocketColors.success else PocketColors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Partial-language Note Banner + 1-Tap Previous Month Shortcut
                        if (sec.note.isNotBlank() || fullLangFallbackSection != null) {
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = PocketColors.infoBlueBg,
                                border = BorderStroke(0.6.dp, PocketColors.infoBlue)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (sec.note.isNotBlank()) {
                                        Text(sec.note, style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                                    }
                                    fullLangFallbackSection?.let { prevSec ->
                                        OutlinedButton(
                                            onClick = { selectNtSectionAndDefaults(prevSec) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            border = BorderStroke(0.75.dp, PocketColors.infoBlue)
                                        ) {
                                            Text(
                                                "Need another language? Switch to ${prevSec.shortChipLabel}",
                                                fontSize = 11.5.sp,
                                                color = PocketColors.infoBlue,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Architecture Chips (only if >1 arch exists in this section)
                        if (ntArchesForSection.size > 1) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                PocketSectionHeader("Architecture")
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ntArchesForSection.forEach { arch ->
                                        val isSel = selectedNtArch.equals(arch, true)
                                        Surface(
                                            modifier = Modifier.weight(1f).bounceClick {
                                                selectedNtArch = arch
                                                ntResolveGen++
                                                ntResolving = false
                                                ntError = null
                                            },
                                            shape = RoundedCornerShape(PocketTokens.CompactButtonRadius),
                                            color = if (isSel) PocketColors.infoBlueBg else PocketColors.surfaceInput,
                                            border = BorderStroke(if (isSel) 1.dp else 0.5.dp, if (isSel) PocketColors.infoBlue else PocketColors.border)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Rounded.Memory, null, tint = if (isSel) PocketColors.infoBlue else PocketColors.textMuted, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(arch, style = MaterialTheme.typography.labelMedium, color = if (isSel) PocketColors.infoBlue else PocketColors.textPrimary, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Language / Variant Selector (preserves every distinct ISO even when two variants share a language name)
                        if (ntEntriesForArch.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                PocketSectionHeader("Language", trailing = "${ntEntriesForArch.size} available")
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().bounceClick {
                                            langSearch = ""
                                            langMenuOpen = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = PocketColors.surfaceInput,
                                        border = BorderStroke(0.75.dp, PocketColors.accent.copy(alpha = 0.7f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(Icons.Rounded.Language, null, tint = PocketColors.accentSoft, modifier = Modifier.size(19.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(activeNtEntry?.effectiveLanguageLabel ?: "Select language", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    text = activeNtEntry?.fileName ?: "",
                                                    style = PocketColors.monoStyle,
                                                    color = PocketColors.textMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(Icons.Rounded.ExpandMore, null, tint = PocketColors.textSecondary, modifier = Modifier.size(19.dp))
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = langMenuOpen,
                                        onDismissRequest = { langMenuOpen = false },
                                        modifier = Modifier.fillMaxWidth(0.86f).heightIn(max = 340.dp).background(PocketColors.surfaceCard)
                                    ) {
                                        if (ntEntriesForArch.size > 6) {
                                            OutlinedTextField(
                                                value = langSearch,
                                                onValueChange = { langSearch = it },
                                                placeholder = { Text("Search language or edition...", fontSize = 12.sp) },
                                                leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(16.dp)) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                        ntEntriesForArch.filter { item ->
                                            val q = langSearch.trim()
                                            q.isBlank() ||
                                                item.effectiveLanguageLabel.contains(q, ignoreCase = true) ||
                                                item.fileName.contains(q, ignoreCase = true)
                                        }.forEach { item ->
                                            val isSel = item.fileName.equals(activeNtEntry?.fileName, true)
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        item.effectiveLanguageLabel,
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = if (isSel) PocketColors.accentSoft else PocketColors.textPrimary
                                                    )
                                                },
                                                leadingIcon = if (isSel) {
                                                    { Icon(Icons.Rounded.Check, null, tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp)) }
                                                } else null,
                                                onClick = {
                                                    selectedNtFileName = item.fileName
                                                    ntResolveGen++
                                                    ntResolving = false
                                                    ntError = null
                                                    langMenuOpen = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Selected File & SHA-256 Card (+ Already in Your Drives badge)
                        activeNtEntry?.let { entry ->
                            val cleanLocalName = entry.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                            val alreadyDownloaded = existingFileNames.contains(entry.fileName.lowercase(Locale.US)) ||
                                existingFileNames.contains(cleanLocalName.lowercase(Locale.US))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, if (alreadyDownloaded) PocketColors.success else PocketColors.border)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = entry.fileName,
                                            modifier = Modifier.weight(1f),
                                            style = PocketColors.monoStyle,
                                            color = PocketColors.textPrimary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (alreadyDownloaded) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = PocketColors.successBg) {
                                                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Icon(Icons.Rounded.CheckCircle, null, tint = PocketColors.success, modifier = Modifier.size(12.dp))
                                                    Text("Saved", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PocketColors.success)
                                                }
                                            }
                                        }
                                    }
                                    if (entry.sha256.length == 64) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().bounceClick {
                                                val cm = context.getSystemService(ClipboardManager::class.java)
                                                cm?.setPrimaryClip(ClipData.newPlainText("SHA-256", entry.sha256))
                                                Toast.makeText(context, "Copied official SHA-256 checksum.", Toast.LENGTH_SHORT).show()
                                            },
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(Icons.Rounded.VerifiedUser, null, tint = PocketColors.success, modifier = Modifier.size(13.dp))
                                            Text(
                                                text = "SHA-256: ${entry.sha256.take(16)}…${entry.sha256.takeLast(8)} (Auto-Verify)",
                                                style = PocketColors.monoStyle,
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

                    val curNtError = ntError
                    if (curNtError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PocketColors.warningBg,
                            border = BorderStroke(0.75.dp, PocketColors.warning)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.ErrorOutline, null, tint = PocketColors.warning, modifier = Modifier.size(19.dp))
                                    Text(curNtError, style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (ntSections.isEmpty()) {
                                        Button(
                                            onClick = { ntRetryNonce++ },
                                            shape = RoundedCornerShape(9.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                                        ) {
                                            Text("Retry Archive", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { ntError = null },
                                            shape = RoundedCornerShape(9.dp),
                                            border = BorderStroke(0.75.dp, PocketColors.border)
                                        ) {
                                            Text("Dismiss", fontSize = 12.sp, color = PocketColors.textPrimary)
                                        }
                                    }
                                    if (family.supportsMicrosoftCdn) {
                                        OutlinedButton(
                                            onClick = {
                                                ntResolveGen++
                                                ntResolving = false
                                                activeSource = WindowsDownloadSource.MICROSOFT_CDN
                                            },
                                            shape = RoundedCornerShape(9.dp),
                                            border = BorderStroke(0.75.dp, PocketColors.border)
                                        ) {
                                            Text("Try MS CDN", fontSize = 12.sp, color = PocketColors.textPrimary)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Action Buttons (Resolve fresh signed URL right when tapped!)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(
                            onClick = {
                                val entry = activeNtEntry ?: return@OutlinedButton
                                val reqGen = ++ntResolveGen
                                scope.launch {
                                    ntResolving = true
                                    ntError = null
                                    val res = NtriverCatalog.resolveDownloadUrl(context, entry)
                                    if (reqGen != ntResolveGen) return@launch
                                    ntResolving = false
                                    if (res.url != null) {
                                        val cm = context.getSystemService(ClipboardManager::class.java)
                                        cm?.setPrimaryClip(ClipData.newPlainText("Windows ISO Link", res.url))
                                        Toast.makeText(context, "Copied direct ISO link for external download managers.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        ntError = res.errorMessage ?: "Could not generate direct link."
                                    }
                                }
                            },
                            enabled = activeNtEntry != null && !ntResolving,
                            modifier = Modifier.weight(0.95f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            border = BorderStroke(0.75.dp, PocketColors.border)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Copy Link", fontSize = 12.5.sp, color = PocketColors.textPrimary)
                        }

                        Button(
                            onClick = {
                                val entry = activeNtEntry ?: return@Button
                                val secTitle = selectedNtSection?.shortChipLabel ?: family.title
                                val reqGen = ++ntResolveGen
                                scope.launch {
                                    ntResolving = true
                                    ntError = null
                                    val res = NtriverCatalog.resolveDownloadUrl(context, entry)
                                    if (reqGen != ntResolveGen) return@launch
                                    ntResolving = false
                                    val finalUrl = res.url
                                    if (finalUrl != null) {
                                        onDismiss()
                                        onStartDirectDownload(
                                            "$secTitle (${entry.effectiveLanguageLabel} ${entry.arch})",
                                            finalUrl,
                                            entry.fileName,
                                            entry.sha256,
                                            NtriverCatalog.buildRefreshKey(entry)
                                        )
                                    } else {
                                        ntError = res.errorMessage ?: "Could not generate direct link."
                                    }
                                }
                            },
                            enabled = activeNtEntry != null && !ntResolving,
                            modifier = Modifier.weight(1.35f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                        ) {
                            if (ntResolving) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PocketColors.onAccent)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resolving...")
                            } else {
                                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Turbo (${turboConnections}x)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                // ════════════════════════════════════════════════════════════
                // MICROSOFT DIRECT CDN PANE (Existing Fido flow intact)
                // ════════════════════════════════════════════════════════════
                if (directLoadingReleases) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(color = PocketColors.accent)
                            Text("Loading official Windows retail releases...", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                    }
                } else {
                    if (directReleaseGroups.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PocketSectionHeader("Release Version")
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                directReleaseGroups.forEachIndexed { idx, grp ->
                                    val isSel = grp.shortChipLabel == selectedDirectGroup?.shortChipLabel
                                    Surface(
                                        modifier = Modifier.bounceClick {
                                            selectedDirectGroup = grp
                                            if (directWantArm64 && grp.arm64Release == null) directWantArm64 = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSel) PocketColors.accentBg else PocketColors.surfaceInput,
                                        border = BorderStroke(if (isSel) 1.dp else 0.5.dp, if (isSel) PocketColors.accent else PocketColors.border)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = grp.shortChipLabel,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (isSel) PocketColors.accentSoft else PocketColors.textSecondary
                                            )
                                            if (idx == 0) {
                                                Text("Latest", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PocketColors.success)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    selectedDirectGroup?.let { grp ->
                        val hasWin11Pair = grp.x64Release != null && grp.arm64Release != null
                        val isWin10Dual = grp.osFamily == "win10" || grp.osFamily == "win81"
                        if (hasWin11Pair || isWin10Dual) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                PocketSectionHeader("Architecture")
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (hasWin11Pair) {
                                        listOf(false to "x64 (PC / Laptop)", true to "ARM64 (Snapdragon)").forEach { (isArm, lbl) ->
                                            val isSel = directWantArm64 == isArm
                                            Surface(
                                                modifier = Modifier.weight(1f).bounceClick { directWantArm64 = isArm },
                                                shape = RoundedCornerShape(PocketTokens.CompactButtonRadius),
                                                color = if (isSel) PocketColors.infoBlueBg else PocketColors.surfaceInput,
                                                border = BorderStroke(if (isSel) 1.dp else 0.5.dp, if (isSel) PocketColors.infoBlue else PocketColors.border)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Rounded.Memory, null, tint = if (isSel) PocketColors.infoBlue else PocketColors.textMuted, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(lbl, style = MaterialTheme.typography.labelMedium, color = if (isSel) PocketColors.infoBlue else PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    } else {
                                        listOf("x64" to "x64 (64-bit)", "x86" to "x86 (32-bit)").forEach { (archCode, lbl) ->
                                            val isSel = directWin10Arch.equals(archCode, true)
                                            Surface(
                                                modifier = Modifier.weight(1f).bounceClick { directWin10Arch = archCode },
                                                shape = RoundedCornerShape(PocketTokens.CompactButtonRadius),
                                                color = if (isSel) PocketColors.infoBlueBg else PocketColors.surfaceInput,
                                                border = BorderStroke(if (isSel) 1.dp else 0.5.dp, if (isSel) PocketColors.infoBlue else PocketColors.border)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(vertical = 9.dp, horizontal = 8.dp),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Rounded.Memory, null, tint = if (isSel) PocketColors.infoBlue else PocketColors.textMuted, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(lbl, style = MaterialTheme.typography.labelMedium, color = if (isSel) PocketColors.infoBlue else PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    activeDirectRelease?.let { rel ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, PocketColors.border)
                            ) {
                                Row(modifier = Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.Tag, null, tint = PocketColors.infoBlue, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("EDITION ID", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = PocketColors.textMuted)
                                        val badgeArch = if (rel.osFamily == "win10" || rel.osFamily == "win81") directWin10Arch else if (rel.isArm64Only) "ARM64" else "x64"
                                        Text("#${rel.editionId} · $badgeArch", style = PocketColors.monoStyle, color = PocketColors.textPrimary)
                                    }
                                }
                            }
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = PocketColors.surfaceInput,
                                border = BorderStroke(0.5.dp, PocketColors.border)
                            ) {
                                Row(modifier = Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.CalendarMonth, null, tint = PocketColors.success, modifier = Modifier.size(18.dp))
                                    Column {
                                        Text("RELEASE", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp, color = PocketColors.textMuted)
                                        Text(rel.releaseLabel.take(24), style = PocketColors.monoStyle, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }

                    val currentDirectError = directError
                    if (currentDirectError != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = PocketColors.warningBg,
                            border = BorderStroke(0.75.dp, PocketColors.warning)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Rounded.ErrorOutline, null, tint = PocketColors.warning, modifier = Modifier.size(19.dp))
                                    Text(currentDirectError, style = MaterialTheme.typography.bodySmall, color = PocketColors.textPrimary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { retryNonce++ },
                                        shape = RoundedCornerShape(9.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                                    ) {
                                        Text("Retry Microsoft CDN", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    OutlinedButton(
                                        onClick = { activeSource = WindowsDownloadSource.NTRIVER_ARCHIVE },
                                        shape = RoundedCornerShape(9.dp),
                                        border = BorderStroke(0.75.dp, PocketColors.border)
                                    ) {
                                        Text("Use Archive", fontSize = 12.sp, color = PocketColors.textPrimary)
                                    }
                                }
                            }
                        }
                    }

                    if (directSkuLoading || directSkus.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PocketSectionHeader("Language", trailing = if (directSkuLoading) "Connecting..." else "${directSkus.size} available")
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .bounceClick(enabled = !directSkuLoading && directSkus.isNotEmpty()) {
                                            langSearch = ""
                                            langMenuOpen = true
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = PocketColors.surfaceInput,
                                    border = BorderStroke(0.75.dp, PocketColors.accent.copy(alpha = 0.7f))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (directSkuLoading) {
                                            CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp, color = PocketColors.accent)
                                            Text("Connecting to Microsoft servers...", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                                        } else {
                                            Icon(Icons.Rounded.Language, null, tint = PocketColors.accentSoft, modifier = Modifier.size(19.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(selectedDirectSku?.localizedLanguage ?: "Select language", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    text = if (directLinkLoading) "Generating official 24h CDN URL..." else (selectedDirectLink?.fileName ?: "Official Microsoft CDN"),
                                                    style = PocketColors.monoStyle,
                                                    color = PocketColors.textMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(Icons.Rounded.ExpandMore, null, tint = PocketColors.textSecondary, modifier = Modifier.size(19.dp))
                                        }
                                    }
                                }

                                DropdownMenu(
                                    expanded = langMenuOpen,
                                    onDismissRequest = { langMenuOpen = false },
                                    modifier = Modifier.fillMaxWidth(0.86f).heightIn(max = 340.dp).background(PocketColors.surfaceCard)
                                ) {
                                    if (directSkus.size > 6) {
                                        OutlinedTextField(
                                            value = langSearch,
                                            onValueChange = { langSearch = it },
                                            placeholder = { Text("Search language...", fontSize = 12.sp) },
                                            leadingIcon = { Icon(Icons.Rounded.Search, null, modifier = Modifier.size(16.dp)) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                    directSkus.filter {
                                        langSearch.isBlank() || it.localizedLanguage.contains(langSearch.trim(), true) || it.language.contains(langSearch.trim(), true)
                                    }.forEach { sku ->
                                        val isSel = sku.skuId == selectedDirectSku?.skuId
                                        DropdownMenuItem(
                                            text = { Text(sku.localizedLanguage, style = MaterialTheme.typography.labelLarge, color = if (isSel) PocketColors.accentSoft else PocketColors.textPrimary) },
                                            leadingIcon = if (isSel) {
                                                { Icon(Icons.Rounded.Check, null, tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp)) }
                                            } else null,
                                            onClick = {
                                                selectedDirectSku = sku
                                                langMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OutlinedButton(
                            onClick = {
                                val link = selectedDirectLink ?: return@OutlinedButton
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(ClipData.newPlainText("Windows ISO Link", link.url))
                                Toast.makeText(context, "Copied direct link for external download managers.", Toast.LENGTH_SHORT).show()
                            },
                            enabled = selectedDirectLink != null && !directLinkLoading,
                            modifier = Modifier.weight(0.95f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            border = BorderStroke(0.75.dp, PocketColors.border)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Copy Link", fontSize = 12.5.sp, color = PocketColors.textPrimary)
                        }

                        Button(
                            onClick = {
                                val link = selectedDirectLink ?: return@Button
                                val relName = activeDirectRelease?.releaseLabel ?: family.title
                                onDismiss()
                                onStartDirectDownload("$relName (${selectedDirectSku?.localizedLanguage ?: link.arch})", link.url, link.fileName, "", "")
                            },
                            enabled = selectedDirectLink != null && !directLinkLoading,
                            modifier = Modifier.weight(1.35f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                        ) {
                            if (directLinkLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PocketColors.onAccent)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Signing...")
                            } else {
                                Icon(Icons.Rounded.Download, null, modifier = Modifier.size(17.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Turbo (${turboConnections}x)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}
