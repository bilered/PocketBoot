package com.pocketboot.app

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.BuildCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Window
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding

data class DistroVersion(
    val label: String,
    val date: String = "",
    val sizeLabel: String,
    val approxBytes: Long,
    val fileName: String,
    val url: String,
    val sha256: String = ""
)

data class CuratedIso(
    val title: String,
    val subtitle: String,
    val category: String,
    val sizeLabel: String,
    val approxBytes: Long,
    val fileName: String,
    val url: String,
    val versions: List<DistroVersion> = emptyList()
) {
    val effectiveVersions: List<DistroVersion>
        get() = versions.ifEmpty {
            listOf(
                DistroVersion(
                    label = "Default Release",
                    date = "",
                    sizeLabel = sizeLabel,
                    approxBytes = approxBytes,
                    fileName = fileName,
                    url = url
                )
            )
        }
}

const val DISTROS_MANIFEST_URL = "https://raw.githubusercontent.com/bilered/PocketBoot/main/distros.json"

private val fallbackIsos: List<CuratedIso> = listOf(
    CuratedIso(
        title = "Ubuntu Desktop",
        subtitle = "Most popular Linux desktop with full hardware support",
        category = "Linux",
        sizeLabel = "5.9 GB",
        approxBytes = 6335076761L,
        fileName = "ubuntu-26.04.1-desktop-amd64.iso",
        url = "https://releases.ubuntu.com/resolute/ubuntu-26.04.1-desktop-amd64.iso",
        versions = listOf(
            DistroVersion("26.04.1 LTS (Latest · Resolute)", "2026", "5.9 GB", 6335076761L, "ubuntu-26.04.1-desktop-amd64.iso", "https://releases.ubuntu.com/resolute/ubuntu-26.04.1-desktop-amd64.iso", ""),
            DistroVersion("24.04.5 LTS (Mature · Noble)", "2026", "5.8 GB", 6227702579L, "ubuntu-24.04.5.1-desktop-amd64.iso", "https://releases.ubuntu.com/noble/ubuntu-24.04.5.1-desktop-amd64.iso", ""),
            DistroVersion("22.04.5 LTS (Legacy · Jammy)", "2024", "4.7 GB", 5046586572L, "ubuntu-22.04.5-desktop-amd64.iso", "https://releases.ubuntu.com/jammy/ubuntu-22.04.5-desktop-amd64.iso", "")
        )
    ),
    CuratedIso(
        title = "Pop!_OS",
        subtitle = "System76 COSMIC desktop with Intel/AMD & NVIDIA editions",
        category = "Linux",
        sizeLabel = "2.8 GB",
        approxBytes = 3006477107L,
        fileName = "pop-os_latest_24.04_intel.iso",
        url = "https://api.pop-os.org/builds/24.04/intel",
        versions = listOf(
            DistroVersion("24.04 LTS COSMIC (Intel / AMD · Live API)", "2026", "2.8 GB", 3006477107L, "pop-os_latest_24.04_intel.iso", "https://api.pop-os.org/builds/24.04/intel", ""),
            DistroVersion("24.04 LTS COSMIC (NVIDIA · Live API)", "2026", "3.2 GB", 3435973836L, "pop-os_latest_24.04_nvidia.iso", "https://api.pop-os.org/builds/24.04/nvidia", ""),
            DistroVersion("22.04 LTS GNOME (Intel / AMD · Live API)", "2025", "2.6 GB", 2791728742L, "pop-os_latest_22.04_intel.iso", "https://api.pop-os.org/builds/22.04/intel", ""),
            DistroVersion("22.04 LTS GNOME (NVIDIA · Live API)", "2025", "3.0 GB", 3221225472L, "pop-os_latest_22.04_nvidia.iso", "https://api.pop-os.org/builds/22.04/nvidia", "")
        )
    ),
    CuratedIso(
        title = "Linux Mint",
        subtitle = "Familiar, fast desktop ideal for Windows switchers",
        category = "Linux",
        sizeLabel = "2.9 GB",
        approxBytes = 3113851289L,
        fileName = "linuxmint-22.1-cinnamon-64bit.iso",
        url = "https://mirrors.edge.kernel.org/linuxmint/stable/22.1/linuxmint-22.1-cinnamon-64bit.iso",
        versions = listOf(
            DistroVersion("22.1 Xia (Cinnamon · Recommended)", "2025", "2.9 GB", 3113851289L, "linuxmint-22.1-cinnamon-64bit.iso", "https://mirrors.edge.kernel.org/linuxmint/stable/22.1/linuxmint-22.1-cinnamon-64bit.iso", ""),
            DistroVersion("22.1 Xia (Xfce Lightweight)", "2025", "2.8 GB", 3006477107L, "linuxmint-22.1-xfce-64bit.iso", "https://mirrors.edge.kernel.org/linuxmint/stable/22.1/linuxmint-22.1-xfce-64bit.iso", ""),
            DistroVersion("22 Wilma (Cinnamon)", "2024", "2.9 GB", 3113851289L, "linuxmint-22-cinnamon-64bit.iso", "https://mirrors.edge.kernel.org/linuxmint/stable/22/linuxmint-22-cinnamon-64bit.iso", "")
        )
    ),
    CuratedIso(
        title = "Debian",
        subtitle = "Rock-solid universal Linux OS (Live GNOME, KDE & Netinst)",
        category = "Linux",
        sizeLabel = "3.6 GB",
        approxBytes = 3865470566L,
        fileName = "debian-live-13.7.0-amd64-gnome.iso",
        url = "https://cdimage.debian.org/debian-cd/current-live/amd64/iso-hybrid/debian-live-13.7.0-amd64-gnome.iso",
        versions = listOf(
            DistroVersion("13.7 Trixie Live (GNOME)", "2026", "3.6 GB", 3865470566L, "debian-live-13.7.0-amd64-gnome.iso", "https://cdimage.debian.org/debian-cd/current-live/amd64/iso-hybrid/debian-live-13.7.0-amd64-gnome.iso", ""),
            DistroVersion("13.7 Trixie Live (KDE Plasma)", "2026", "3.8 GB", 4080218931L, "debian-live-13.7.0-amd64-kde.iso", "https://cdimage.debian.org/debian-cd/current-live/amd64/iso-hybrid/debian-live-13.7.0-amd64-kde.iso", ""),
            DistroVersion("13.7 Trixie Netinst (Minimal)", "2026", "680 MB", 713031680L, "debian-13.7.0-amd64-netinst.iso", "https://cdimage.debian.org/debian-cd/current/amd64/iso-cd/debian-13.7.0-amd64-netinst.iso", "")
        )
    ),
    CuratedIso(
        title = "Fedora Workstation",
        subtitle = "Cutting-edge GNOME desktop backed by Red Hat",
        category = "Linux",
        sizeLabel = "2.4 GB",
        approxBytes = 2576980377L,
        fileName = "Fedora-Workstation-Live-45-1.4.x86_64.iso",
        url = "https://download.fedoraproject.org/pub/fedora/linux/releases/45/Workstation/x86_64/iso/Fedora-Workstation-Live-45-1.4.x86_64.iso",
        versions = listOf(
            DistroVersion("Fedora 45 Workstation (Latest)", "2026", "2.4 GB", 2576980377L, "Fedora-Workstation-Live-45-1.4.x86_64.iso", "https://download.fedoraproject.org/pub/fedora/linux/releases/45/Workstation/x86_64/iso/Fedora-Workstation-Live-45-1.4.x86_64.iso", ""),
            DistroVersion("Fedora 44 Workstation", "2026", "2.4 GB", 2576980377L, "Fedora-Workstation-Live-44-1.7.x86_64.iso", "https://download.fedoraproject.org/pub/fedora/linux/releases/44/Workstation/x86_64/iso/Fedora-Workstation-Live-44-1.7.x86_64.iso", "")
        )
    ),
    CuratedIso(
        title = "Arch Linux",
        subtitle = "Minimalist rolling-release Linux with full DIY control",
        category = "Linux",
        sizeLabel = "1.2 GB",
        approxBytes = 1288490188L,
        fileName = "archlinux-x86_64.iso",
        url = "https://geo.mirror.pkgbuild.com/iso/latest/archlinux-x86_64.iso",
        versions = listOf(
            DistroVersion("Latest Monthly Rolling (x86_64)", "2026", "1.2 GB", 1288490188L, "archlinux-x86_64.iso", "https://geo.mirror.pkgbuild.com/iso/latest/archlinux-x86_64.iso", "")
        )
    ),
    CuratedIso(
        title = "openSUSE Tumbleweed",
        subtitle = "Reliable rolling release with YaST & Btrfs snapshots",
        category = "Linux",
        sizeLabel = "4.4 GB",
        approxBytes = 4724464025L,
        fileName = "openSUSE-Tumbleweed-DVD-x86_64-Current.iso",
        url = "https://download.opensuse.org/tumbleweed/iso/openSUSE-Tumbleweed-DVD-x86_64-Current.iso",
        versions = listOf(
            DistroVersion("Tumbleweed DVD (Offline Installer)", "2026", "4.4 GB", 4724464025L, "openSUSE-Tumbleweed-DVD-x86_64-Current.iso", "https://download.opensuse.org/tumbleweed/iso/openSUSE-Tumbleweed-DVD-x86_64-Current.iso", ""),
            DistroVersion("Tumbleweed GNOME Live", "2026", "1.1 GB", 1181116006L, "openSUSE-Tumbleweed-GNOME-Live-x86_64-Current.iso", "https://download.opensuse.org/tumbleweed/iso/openSUSE-Tumbleweed-GNOME-Live-x86_64-Current.iso", ""),
            DistroVersion("Tumbleweed KDE Live", "2026", "1.1 GB", 1181116006L, "openSUSE-Tumbleweed-KDE-Live-x86_64-Current.iso", "https://download.opensuse.org/tumbleweed/iso/openSUSE-Tumbleweed-KDE-Live-x86_64-Current.iso", "")
        )
    ),
    CuratedIso(
        title = "NixOS",
        subtitle = "Declarative, reproducible Linux distribution",
        category = "Linux",
        sizeLabel = "2.6 GB",
        approxBytes = 2791728742L,
        fileName = "latest-nixos-graphical-x86_64-linux.iso",
        url = "https://channels.nixos.org/nixos-unstable/latest-nixos-graphical-x86_64-linux.iso",
        versions = listOf(
            DistroVersion("Graphical GNOME (Latest x86_64)", "2026", "2.6 GB", 2791728742L, "latest-nixos-graphical-x86_64-linux.iso", "https://channels.nixos.org/nixos-unstable/latest-nixos-graphical-x86_64-linux.iso", ""),
            DistroVersion("Minimal CLI (Latest x86_64)", "2026", "1.1 GB", 1181116006L, "latest-nixos-minimal-x86_64-linux.iso", "https://channels.nixos.org/nixos-unstable/latest-nixos-minimal-x86_64-linux.iso", "")
        )
    ),
    CuratedIso(
        title = "Proxmox VE",
        subtitle = "Enterprise open-source hypervisor (KVM + LXC + ZFS)",
        category = "Server",
        sizeLabel = "1.71 GB",
        approxBytes = 1836098682L,
        fileName = "proxmox-ve_9.2-1.iso",
        url = "https://enterprise.proxmox.com/iso/proxmox-ve_9.2-1.iso",
        versions = listOf(
            DistroVersion("Proxmox VE 9.2-1 (x86_64 · Latest)", "2026", "1.71 GB", 1836098682L, "proxmox-ve_9.2-1.iso", "https://enterprise.proxmox.com/iso/proxmox-ve_9.2-1.iso", "4e88fe416df9b527624a175f24c9aa07c714d3332afb1ee3dbf3879573ef2c6c"),
            DistroVersion("Proxmox VE 8.4-1 (Legacy Stable)", "2025", "1.57 GB", 1685774663L, "proxmox-ve_8.4-1.iso", "https://enterprise.proxmox.com/iso/proxmox-ve_8.4-1.iso", "d237d70ca48a9f6eb47f95fd4fd337722c3f69f8106393844d027d28c26523d8")
        )
    ),
    CuratedIso(
        title = "Ubuntu Server",
        subtitle = "Headless LTS Linux server for homelabs & production",
        category = "Server",
        sizeLabel = "2.6 GB",
        approxBytes = 2791728742L,
        fileName = "ubuntu-26.04.1-live-server-amd64.iso",
        url = "https://releases.ubuntu.com/resolute/ubuntu-26.04.1-live-server-amd64.iso",
        versions = listOf(
            DistroVersion("26.04.1 LTS Server (Latest · Resolute)", "2026", "2.6 GB", 2791728742L, "ubuntu-26.04.1-live-server-amd64.iso", "https://releases.ubuntu.com/resolute/ubuntu-26.04.1-live-server-amd64.iso", ""),
            DistroVersion("24.04.5 LTS Server (Mature · Noble)", "2026", "2.6 GB", 2791728742L, "ubuntu-24.04.5-live-server-amd64.iso", "https://releases.ubuntu.com/noble/ubuntu-24.04.5-live-server-amd64.iso", "")
        )
    ),
    CuratedIso(
        title = "Alpine Linux",
        subtitle = "Ultra-lightweight security-oriented musl Linux",
        category = "Server",
        sizeLabel = "240 MB",
        approxBytes = 251658240L,
        fileName = "alpine-standard-3.24.4-x86_64.iso",
        url = "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/x86_64/alpine-standard-3.24.4-x86_64.iso",
        versions = listOf(
            DistroVersion("3.24.4 Standard (x86_64)", "2026", "240 MB", 251658240L, "alpine-standard-3.24.4-x86_64.iso", "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/x86_64/alpine-standard-3.24.4-x86_64.iso", ""),
            DistroVersion("3.24.4 Extended (Offline pkgs)", "2026", "860 MB", 901775360L, "alpine-extended-3.24.4-x86_64.iso", "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/x86_64/alpine-extended-3.24.4-x86_64.iso", "")
        )
    ),
    CuratedIso(
        title = "Kali Linux",
        subtitle = "Penetration testing, forensics & security auditing suite",
        category = "Security",
        sizeLabel = "4.3 GB",
        approxBytes = 4617089843L,
        fileName = "kali-linux-2026.3-live-amd64.iso",
        url = "https://cdimage.kali.org/kali-2026.3/kali-linux-2026.3-live-amd64.iso",
        versions = listOf(
            DistroVersion("2026.3 Live (amd64 · Recommended)", "2026", "4.3 GB", 4617089843L, "kali-linux-2026.3-live-amd64.iso", "https://cdimage.kali.org/kali-2026.3/kali-linux-2026.3-live-amd64.iso", ""),
            DistroVersion("2026.3 Installer (amd64)", "2026", "4.1 GB", 4402341478L, "kali-linux-2026.3-installer-amd64.iso", "https://cdimage.kali.org/kali-2026.3/kali-linux-2026.3-installer-amd64.iso", "")
        )
    ),
    CuratedIso(
        title = "Tails",
        subtitle = "Amnesic privacy OS routing all traffic through Tor",
        category = "Security",
        sizeLabel = "1.5 GB",
        approxBytes = 1610612736L,
        fileName = "tails-amd64-latest.img",
        url = "https://tails.net/install/v2/Tails/amd64/stable/latest.json",
        versions = listOf(
            DistroVersion("Latest Stable USB Image (Live API)", "2026", "1.5 GB", 1610612736L, "tails-amd64-latest.img", "https://tails.net/install/v2/Tails/amd64/stable/latest.json", "")
        )
    ),
    CuratedIso(
        title = "GParted Live",
        subtitle = "Resize, move, and repair disk partitions graphically",
        category = "Rescue",
        sizeLabel = "645 MB",
        approxBytes = 676331520L,
        fileName = "gparted-live-1.8.1-6-amd64.iso",
        url = "https://sourceforge.net/projects/gparted/files/gparted-live-stable/1.8.1-6/gparted-live-1.8.1-6-amd64.iso/download",
        versions = listOf(
            DistroVersion("1.8.1-6 (amd64 · Latest)", "2026", "645 MB", 676331520L, "gparted-live-1.8.1-6-amd64.iso", "https://sourceforge.net/projects/gparted/files/gparted-live-stable/1.8.1-6/gparted-live-1.8.1-6-amd64.iso/download", "d789c38779f0d6f7026c12f44c2c52a04f66e28a1aea7d51f3045ad1bbf28411"),
            DistroVersion("1.7.0-8 (amd64 · Legacy)", "2025", "560 MB", 587202560L, "gparted-live-1.7.0-8-amd64.iso", "https://sourceforge.net/projects/gparted/files/gparted-live-stable/1.7.0-8/gparted-live-1.7.0-8-amd64.iso/download", "")
        )
    ),
    CuratedIso(
        title = "Clonezilla Live",
        subtitle = "Bare-metal disk & partition cloning and imaging tool",
        category = "Rescue",
        sizeLabel = "460 MB",
        approxBytes = 482344960L,
        fileName = "clonezilla-live-3.3.3-37-amd64.iso",
        url = "https://sourceforge.net/projects/clonezilla/files/clonezilla_live_stable/3.3.3-37/clonezilla-live-3.3.3-37-amd64.iso/download",
        versions = listOf(
            DistroVersion("3.3.3-37 Stable (Debian amd64)", "2026", "460 MB", 482344960L, "clonezilla-live-3.3.3-37-amd64.iso", "https://sourceforge.net/projects/clonezilla/files/clonezilla_live_stable/3.3.3-37/clonezilla-live-3.3.3-37-amd64.iso/download", "")
        )
    ),
    CuratedIso(
        title = "Rescuezilla",
        subtitle = "Graphical disk backup & restore (Clonezilla compatible)",
        category = "Rescue",
        sizeLabel = "1.3 GB",
        approxBytes = 1395864371L,
        fileName = "rescuezilla-2.6.2-64bit.resolute.iso",
        url = "https://github.com/rescuezilla/rescuezilla/releases/download/2.6.2/rescuezilla-2.6.2-64bit.resolute.iso",
        versions = listOf(
            DistroVersion("2.6.2 (Ubuntu 26.04 Base)", "2026", "1.3 GB", 1395864371L, "rescuezilla-2.6.2-64bit.resolute.iso", "https://github.com/rescuezilla/rescuezilla/releases/download/2.6.2/rescuezilla-2.6.2-64bit.resolute.iso", ""),
            DistroVersion("2.5.1 (Ubuntu 24.04 Base)", "2024", "1.2 GB", 1288490188L, "rescuezilla-2.5.1-64bit.noble.iso", "https://github.com/rescuezilla/rescuezilla/releases/download/2.5.1/rescuezilla-2.5.1-64bit.noble.iso", "")
        )
    ),
    CuratedIso(
        title = "SystemRescue",
        subtitle = "Arch-based rescue toolkit for unbootable PCs & filesystems",
        category = "Rescue",
        sizeLabel = "1.2 GB",
        approxBytes = 1288490188L,
        fileName = "systemrescue-13.03-amd64.iso",
        url = "https://fastly-cdn.system-rescue.org/releases/13.03/systemrescue-13.03-amd64.iso",
        versions = listOf(
            DistroVersion("13.03 (Latest amd64)", "2026", "1.2 GB", 1288490188L, "systemrescue-13.03-amd64.iso", "https://fastly-cdn.system-rescue.org/releases/13.03/systemrescue-13.03-amd64.iso", ""),
            DistroVersion("12.01 (amd64)", "2025", "1.2 GB", 1288490188L, "systemrescue-12.01-amd64.iso", "https://fastly-cdn.system-rescue.org/releases/12.01/systemrescue-12.01-amd64.iso", "")
        )
    ),
    CuratedIso(
        title = "ShredOS (Nwipe)",
        subtitle = "Secure drive eraser with NVMe/SSD Hardware Sanitize",
        category = "Rescue",
        sizeLabel = "355 MB",
        approxBytes = 372244480L,
        fileName = "shredos-2025.11_32_x86-64_v0.43.1_20261010.img",
        url = "https://github.com/PartialVolume/shredos.x86_64/releases/download/v2025.11_32_x86-64_v0.43.1/shredos-2025.11_32_x86-64_v0.43.1_20261010.img",
        versions = listOf(
            DistroVersion("v0.43.1 USB Image (.img · x86_64)", "2026", "355 MB", 372244480L, "shredos-2025.11_32_x86-64_v0.43.1_20261010.img", "https://github.com/PartialVolume/shredos.x86_64/releases/download/v2025.11_32_x86-64_v0.43.1/shredos-2025.11_32_x86-64_v0.43.1_20261010.img", "c856cfb91ebd525e23caffc284d84764e9d9b5427ee1925783773642e3d50ae4"),
            DistroVersion("v0.43.1 Optical ISO (.iso · x86_64)", "2026", "342 MB", 358612992L, "shredos-2025.11_32_x86-64_v0.43.1_20261010.iso", "https://github.com/PartialVolume/shredos.x86_64/releases/download/v2025.11_32_x86-64_v0.43.1/shredos-2025.11_32_x86-64_v0.43.1_20261010.iso", "d0ffb8d7b346289db93094dbf1d2a64692b7f7cb179cc1ca76b74fdffbac1414")
        )
    ),
    CuratedIso(
        title = "Memtest86+",
        subtitle = "Open-source RAM diagnostic tool (auto-extracts .iso)",
        category = "Rescue",
        sizeLabel = "6 MB",
        approxBytes = 6291456L,
        fileName = "mt86plus_9.00_x86_64.iso.zip",
        url = "https://www.memtest.org/download/v9.00/mt86plus_9.00_x86_64.iso.zip",
        versions = listOf(
            DistroVersion("v9.00 (x86_64 · UEFI & BIOS)", "2026", "6 MB", 6291456L, "mt86plus_9.00_x86_64.iso.zip", "https://www.memtest.org/download/v9.00/mt86plus_9.00_x86_64.iso.zip", ""),
            DistroVersion("v9.00 (ARM64 · UEFI)", "2026", "5 MB", 5242880L, "mt86plus_9.00_arm64.iso.zip", "https://www.memtest.org/download/v9.00/mt86plus_9.00_arm64.iso.zip", "")
        )
    ),
    CuratedIso(
        title = "netboot.xyz",
        subtitle = "Network-boot 70+ OS installers & rescue tools over iPXE",
        category = "Lightweight",
        sizeLabel = "3 MB",
        approxBytes = 3145728L,
        fileName = "netboot.xyz.iso",
        url = "https://boot.netboot.xyz/ipxe/netboot.xyz.iso",
        versions = listOf(
            DistroVersion("Latest x86_64 iPXE ISO", "2026", "3 MB", 3145728L, "netboot.xyz.iso", "https://boot.netboot.xyz/ipxe/netboot.xyz.iso", ""),
            DistroVersion("Latest ARM64 iPXE ISO", "2026", "3 MB", 3145728L, "netboot.xyz-arm64.iso", "https://boot.netboot.xyz/ipxe/netboot.xyz-arm64.iso", "")
        )
    ),
    CuratedIso(
        title = "Tiny Core Linux",
        subtitle = "Ultra-tiny desktop Linux that runs entirely in RAM",
        category = "Lightweight",
        sizeLabel = "23 MB",
        approxBytes = 24117248L,
        fileName = "TinyCore-current.iso",
        url = "https://distro.ibiblio.org/tinycorelinux/15.x/x86/release/TinyCore-current.iso",
        versions = listOf(
            DistroVersion("15.x TinyCore (GUI · 23 MB)", "2025", "23 MB", 24117248L, "TinyCore-current.iso", "https://distro.ibiblio.org/tinycorelinux/15.x/x86/release/TinyCore-current.iso", ""),
            DistroVersion("15.x CorePlus (Wi-Fi + Tools · 248 MB)", "2025", "248 MB", 260046848L, "CorePlus-current.iso", "https://distro.ibiblio.org/tinycorelinux/15.x/x86/release/CorePlus-current.iso", "")
        )
    )
)

private fun parseDistrosJson(rawJson: String): List<CuratedIso> {
    if (rawJson.isBlank()) return emptyList()
    return runCatching {
        val trimmed = rawJson.trim()
        val arr: JSONArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val obj = JSONObject(trimmed)
            obj.optJSONArray("distros") ?: JSONArray()
        }
        val out = mutableListOf<CuratedIso>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val title = item.optString("title").trim()
            val url = item.optString("url").trim()
            val fileName = item.optString("fileName").trim()
            val category = item.optString("category", "Linux")
            if (category.equals("Windows", true) && url.contains("microsoft.com/software-download", true)) continue
            if (title.isEmpty() || url.isEmpty() || fileName.isEmpty()) continue

            val versArr = item.optJSONArray("versions")
            val parsedVersions = mutableListOf<DistroVersion>()
            if (versArr != null) {
                for (j in 0 until versArr.length()) {
                    val v = versArr.optJSONObject(j) ?: continue
                    val vUrl = v.optString("url").trim()
                    val vName = v.optString("fileName").trim()
                    if (vUrl.isEmpty() || vName.isEmpty()) continue
                    parsedVersions.add(
                        DistroVersion(
                            label = v.optString("label", vName),
                            date = v.optString("date", ""),
                            sizeLabel = v.optString("sizeLabel", "ISO"),
                            approxBytes = v.optLong("approxBytes", 0L),
                            fileName = vName,
                            url = vUrl,
                            sha256 = v.optString("sha256", "")
                        )
                    )
                }
            }
            out.add(
                CuratedIso(
                    title = title,
                    subtitle = item.optString("subtitle", "Bootable OS image"),
                    category = category,
                    sizeLabel = item.optString("sizeLabel", "ISO"),
                    approxBytes = item.optLong("approxBytes", 0L),
                    fileName = fileName,
                    url = url,
                    versions = parsedVersions
                )
            )
        }
        out
    }.getOrDefault(emptyList())
}

private fun serializeDistrosJson(list: List<CuratedIso>): String {
    val arr = JSONArray()
    list.forEach { d ->
        val obj = JSONObject().apply {
            put("title", d.title)
            put("subtitle", d.subtitle)
            put("category", d.category)
            put("sizeLabel", d.sizeLabel)
            put("approxBytes", d.approxBytes)
            put("fileName", d.fileName)
            put("url", d.url)
            if (d.versions.isNotEmpty()) {
                val vArr = JSONArray()
                d.versions.forEach { v ->
                    vArr.put(
                        JSONObject().apply {
                            put("label", v.label)
                            put("date", v.date)
                            put("sizeLabel", v.sizeLabel)
                            put("approxBytes", v.approxBytes)
                            put("fileName", v.fileName)
                            put("url", v.url)
                            if (v.sha256.isNotBlank()) put("sha256", v.sha256)
                        }
                    )
                }
                put("versions", vArr)
            }
        }
        arr.put(obj)
    }
    return JSONObject().put("version", 4).put("distros", arr).toString()
}

private suspend fun fetchUrlText(urlStr: String): String? = withContext(Dispatchers.IO) {
    if (!ShellSafety.isValidHttpUrl(urlStr)) return@withContext null
    runCatching {
        val conn = (URL(urlStr.trim()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 9000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PocketBoot-Android")
        }
        try {
            val code = conn.responseCode
            val finalUrl = conn.url.toString()
            if (ShellSafety.isForbiddenHttpsDowngrade(urlStr, finalUrl)) return@withContext null
            if (code in 200..299) {
                val maxChars = 4 * 1024 * 1024
                val sb = StringBuilder()
                val buf = CharArray(8192)
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val r = reader.read(buf)
                        if (r <= 0) break
                        if (sb.length + r > maxChars) return@withContext null
                        sb.append(buf, 0, r)
                    }
                }
                sb.toString()
            } else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

private suspend fun fetchAllRepos(repoUrls: List<String>): List<CuratedIso> {
    val merged = mutableListOf<CuratedIso>()
    val seenUrls = mutableSetOf<String>()
    for (url in repoUrls) {
        val raw = fetchUrlText(url) ?: continue
        val parsed = parseDistrosJson(raw)
        parsed.forEach { iso ->
            if (seenUrls.add(iso.url.lowercase())) {
                merged.add(iso)
            }
        }
    }
    return merged
}

@Composable
private fun CatalogGroupHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    tint: Color,
    bg: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Text(
            text = title.uppercase(),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
            color = tint
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.5.dp)
                .background(PocketColors.border)
        )
        Text(
            text = "$count",
            style = PocketColors.monoStyle,
            color = PocketColors.textMuted
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    settings: AppSettings,
    existingFileNames: Set<String>,
    multiBootDrives: List<ImageFile>,
    onUpdateSettings: (AppSettings) -> Unit,
    onDownloadCompleted: (DownloadCompletionEvent) -> Unit,
    onQuickMountCompleted: (String) -> Unit,
    onOpenWindowsBuilderForPath: (String) -> Unit,
    onAddCompletedToMultiBoot: (String) -> Unit,
    onAppendLog: (LogEntry) -> Unit,
    onMessage: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val defaultDir = settings.defaultDir
    val turboState by FastIsoDownloader.state.collectAsState()
    val lastCompletion by FastIsoDownloader.lastCompletion.collectAsState()

    val activeMultiBootTarget = remember(settings.downloadTargetMultiBootPath, multiBootDrives) {
        val target = settings.downloadTargetMultiBootPath
        if (target.isBlank()) null else multiBootDrives.firstOrNull {
            UsbGadgetEngine.resolveDirectPath(it.path) == UsbGadgetEngine.resolveDirectPath(target)
        }
    }

    var pendingResumables by remember { mutableStateOf<List<PendingResumableDownload>>(emptyList()) }
    val turboActive = turboState != null
    LaunchedEffect(turboActive, settings.defaultDir) {
        pendingResumables = FastIsoDownloader.scanPendingResumableDownloads(context)
    }

    val initialList = remember(settings.cachedDistrosJson) {
        val cached = parseDistrosJson(settings.cachedDistrosJson)
        if (cached.any { it.versions.size > 1 }) cached else fallbackIsos
    }
    var distroList by remember { mutableStateOf(initialList) }
    var syncing by remember { mutableStateOf(false) }
    var showReposDialog by remember { mutableStateOf(false) }
    var showSpeedSettingsDialog by remember { mutableStateOf(false) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }

    var selectedWindowsFamily by remember { mutableStateOf<WindowsCatalogFamily?>(null) }
    var showOtherWindowsVersions by rememberSaveable { mutableStateOf(false) }
    var selectedDistroForVersion by remember { mutableStateOf<CuratedIso?>(null) }
    var longPressedDistro by remember { mutableStateOf<CuratedIso?>(null) }
    var longPressedWindows by remember { mutableStateOf<WindowsCatalogFamily?>(null) }
    var catalogSearch by rememberSaveable { mutableStateOf("") }
    var customUrl by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var autoMount by remember { mutableStateOf(true) }

    val anyDownloadActive = turboState != null
    var freeBytes by remember(defaultDir) { mutableStateOf(0L) }
    var dirFreeMap by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    LaunchedEffect(defaultDir, turboState == null) {
        freeBytes = withContext(Dispatchers.IO) { UsbGadgetEngine.getFreeSpaceBytes(defaultDir) }
    }
    LaunchedEffect(folderMenuExpanded, settings.scanDirs) {
        if (folderMenuExpanded) {
            dirFreeMap = withContext(Dispatchers.IO) {
                settings.scanDirs.associateWith { d -> UsbGadgetEngine.getFreeSpaceBytes(d) }
            }
        }
    }

    val activeRepoUrls = remember(settings.useDefaultDistroRepo, settings.customRepoUrls) {
        buildList {
            if (settings.useDefaultDistroRepo) add(DISTROS_MANIFEST_URL)
            addAll(settings.customRepoUrls)
        }.distinct()
    }

    fun copyLinkToClipboard(label: String, url: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText(label, url))
        Toast.makeText(context, "Copied direct link for external download managers.", Toast.LENGTH_SHORT).show()
    }

    fun syncCatalogs(showToast: Boolean) {
        if (activeRepoUrls.isEmpty()) {
            distroList = fallbackIsos
            if (showToast) onMessage("No repositories enabled. Showing built-in catalog.", false)
            return
        }
        scope.launch {
            syncing = true
            val fetched = fetchAllRepos(activeRepoUrls)
            if (fetched.isNotEmpty() && fetched.any { it.versions.isNotEmpty() }) {
                distroList = fetched
                onUpdateSettings(settings.copy(cachedDistrosJson = serializeDistrosJson(fetched)))
                if (showToast) onMessage("Synced ${fetched.size} distros from ${activeRepoUrls.size} repo(s).", false)
            } else if (fetched.isNotEmpty()) {
                val knownTitles = fallbackIsos.map { it.title.lowercase() }.toSet()
                val extras = fetched.filter { it.title.lowercase() !in knownTitles }
                distroList = fallbackIsos + extras
                if (showToast) onMessage("Synced catalog (${distroList.size} distros).", false)
            } else {
                if (showToast) onMessage("Could not reach remote repo. Using built-in catalog.", true)
            }
            syncing = false
        }
    }

    LaunchedEffect(activeRepoUrls) {
        if (activeRepoUrls.isNotEmpty()) {
            syncCatalogs(showToast = false)
        }
    }

    fun startTurboDownload(
        title: String,
        url: String,
        fileName: String,
        approxBytes: Long = 0L,
        expectedSha256: String = "",
        refreshSourceKey: String = ""
    ) {
        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            onMessage("Please enter a valid http:// or https:// download link.", true)
            return
        }
        val cleanName = fileName.trim().ifBlank { "downloaded-image.iso" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val mbTargetPath = activeMultiBootTarget?.path.orEmpty()

        FastIsoDownloader.start(
            context = context,
            title = title,
            url = cleanUrl,
            fileName = cleanName,
            targetDir = defaultDir,
            targetMultiBootPath = mbTargetPath,
            approxBytes = approxBytes,
            expectedSha256 = expectedSha256,
            refreshSourceKey = refreshSourceKey,
            connections = settings.downloadConnections,
            autoMount = autoMount,
            autoVerifySha256 = settings.autoVerifySha256,
            onComplete = { evt ->
                scope.launch { onDownloadCompleted(evt) }
            },
            onError = { err ->
                scope.launch {
                    pendingResumables = FastIsoDownloader.scanPendingResumableDownloads(context)
                    onMessage(err, true)
                }
            },
            onLog = { entry -> scope.launch { onAppendLog(entry) } }
        )
        val destLabel = if (activeMultiBootTarget != null) " → ${activeMultiBootTarget.name}" else ""
        onMessage("Started Turbo (${settings.downloadConnections}x): $cleanName$destLabel", false)
    }

    val dynamicCategories = remember(distroList) {
        val cats = distroList.map { it.category }.filter { it.isNotBlank() && !it.equals("Windows", true) }.distinct()
        listOf("All", "Windows") + cats
    }
    val nonWinDistros = remember(distroList) {
        distroList.filterNot { it.category.equals("Windows", true) }
    }
    val q = catalogSearch.trim().lowercase()
    val matchedWinFamilies = remember(q) {
        if (q.isEmpty()) WindowsCatalogFamily.entries.toList()
        else WindowsCatalogFamily.entries.filter {
            it.title.lowercase().contains(q) ||
                it.subtitle.lowercase().contains(q) ||
                it.searchTags.lowercase().contains(q) ||
                "windows".contains(q)
        }
    }
    val matchedNonWin = remember(nonWinDistros, q) {
        if (q.isEmpty()) nonWinDistros
        else nonWinDistros.filter { iso ->
            iso.title.lowercase().contains(q) ||
                iso.subtitle.lowercase().contains(q) ||
                iso.category.lowercase().contains(q) ||
                iso.effectiveVersions.any { it.label.lowercase().contains(q) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "dl_header_controls") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        placeholder = { Text("Paste .iso or .img URL...", fontSize = 13.sp, color = PocketColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Rounded.Link, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(ClipboardManager::class.java)
                                    val clipText = cm?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                    if (clipText.isNotBlank()) customUrl = clipText.trim() else onMessage("Clipboard is empty.", false)
                                }
                            ) {
                                Icon(Icons.Rounded.ContentPaste, "Paste URL", tint = PocketColors.accentSoft, modifier = Modifier.size(17.dp))
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (!anyDownloadActive) {
                                    val u = customUrl.trim()
                                    if (u.startsWith("http://", true) || u.startsWith("https://", true)) {
                                        val name = Uri.parse(u).lastPathSegment
                                            ?.takeIf { it.contains('.') } ?: "custom-image.iso"
                                        startTurboDownload(name, u, name)
                                    }
                                }
                            }
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(PocketTokens.ButtonRadius),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PocketColors.accent,
                            unfocusedBorderColor = PocketColors.border,
                            focusedContainerColor = PocketColors.surfaceInput,
                            unfocusedContainerColor = PocketColors.surfaceInput,
                            focusedTextColor = PocketColors.textPrimary,
                            unfocusedTextColor = PocketColors.textPrimary
                        )
                    )
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (customUrl.isNotBlank() && !anyDownloadActive) PocketColors.accent else PocketColors.surfaceSubtle)
                            .bounceClick(enabled = customUrl.isNotBlank() && !anyDownloadActive) {
                                val guessedName = Uri.parse(customUrl.trim()).lastPathSegment
                                    ?.takeIf { it.contains('.') } ?: "custom-image.iso"
                                startTurboDownload(guessedName, customUrl, guessedName)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = "Download URL",
                            tint = if (customUrl.isNotBlank() && !anyDownloadActive) PocketColors.onAccent else PocketColors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        val isDirectMb = activeMultiBootTarget != null
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bounceClick { folderMenuExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDirectMb) PocketColors.violetBg else PocketColors.surfaceInput,
                            border = BorderStroke(
                                0.75.dp,
                                when {
                                    folderMenuExpanded -> PocketColors.accent
                                    isDirectMb -> PocketColors.violet.copy(alpha = 0.7f)
                                    else -> PocketColors.border
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isDirectMb) Icons.Rounded.Layers else Icons.Rounded.Storage,
                                    contentDescription = null,
                                    tint = if (isDirectMb) PocketColors.violet else PocketColors.accentSoft,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = activeMultiBootTarget?.name ?: UsbGadgetEngine.friendlyFolderName(defaultDir),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = PocketColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (!isDirectMb && freeBytes > 0L) {
                                    Text(
                                        text = "· ${UsbGadgetEngine.formatBytes(freeBytes)}",
                                        style = PocketColors.monoStyle,
                                        color = PocketColors.success,
                                        maxLines = 1
                                    )
                                }
                                Icon(Icons.Rounded.ExpandMore, "Select destination", tint = PocketColors.textSecondary, modifier = Modifier.size(15.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = folderMenuExpanded,
                            onDismissRequest = { folderMenuExpanded = false },
                            modifier = Modifier.background(PocketColors.surfaceCard)
                        ) {
                            settings.scanDirs.forEach { dir ->
                                val isSelected = activeMultiBootTarget == null && dir == defaultDir
                                val dirFree = dirFreeMap[dir] ?: 0L
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = UsbGadgetEngine.friendlyFolderName(dir),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = if (isSelected) PocketColors.accentSoft else PocketColors.textPrimary
                                            )
                                            Text(
                                                text = if (dirFree > 0L) {
                                                    "${dir.replaceFirst("/data/media/0", "Internal")} · ${UsbGadgetEngine.formatBytes(dirFree)} free"
                                                } else {
                                                    dir.replaceFirst("/data/media/0", "Internal")
                                                },
                                                style = PocketColors.monoStyle,
                                                color = PocketColors.textMuted,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Rounded.Check else Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = if (isSelected) PocketColors.accentSoft else PocketColors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        folderMenuExpanded = false
                                        val reordered = (listOf(dir) + settings.scanDirs.filter { it != dir }).distinct()
                                        onUpdateSettings(settings.copy(defaultDir = dir, scanDirs = reordered, downloadTargetMultiBootPath = ""))
                                        onMessage("Save folder set to ${UsbGadgetEngine.friendlyFolderName(dir)}.", false)
                                    }
                                )
                            }

                            if (multiBootDrives.isNotEmpty()) {
                                PocketHairlineDivider()
                                multiBootDrives.forEach { mb ->
                                    val isSelMb = activeMultiBootTarget?.path == mb.path
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = "Directly into ${mb.name}",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (isSelMb) PocketColors.violet else PocketColors.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Auto-moves downloaded ISO into Multi-Boot",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = PocketColors.textMuted
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (isSelMb) Icons.Rounded.Check else Icons.Rounded.Layers,
                                                contentDescription = null,
                                                tint = PocketColors.violet,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        onClick = {
                                            folderMenuExpanded = false
                                            onUpdateSettings(settings.copy(downloadTargetMultiBootPath = mb.path))
                                            onMessage("Downloads will move directly into ${mb.name}.", false)
                                        }
                                    )
                                }
                            }

                            PocketHairlineDivider()
                            DropdownMenuItem(
                                text = { Text("Add New Folder...", style = MaterialTheme.typography.labelLarge, color = PocketColors.accentSoft) },
                                leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    folderMenuExpanded = false
                                    showAddFolderDialog = true
                                }
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.bounceClick { showSpeedSettingsDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = PocketColors.warningBg,
                        border = BorderStroke(0.75.dp, PocketColors.warning.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Rounded.Bolt, "Turbo Speed", tint = PocketColors.warning, modifier = Modifier.size(15.dp))
                            Text("${settings.downloadConnections}x", style = PocketColors.monoStyle, color = PocketColors.warning)
                        }
                    }

                    Surface(
                        modifier = Modifier.bounceClick(enabled = !syncing) { syncCatalogs(showToast = true) },
                        shape = RoundedCornerShape(10.dp),
                        color = PocketColors.surfaceInput,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 9.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                            if (syncing) {
                                CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = PocketColors.accent)
                            } else {
                                Icon(Icons.Rounded.Sync, "Sync Catalog", tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.bounceClick { showReposDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        color = PocketColors.surfaceInput,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Rounded.Source, "Repos", tint = PocketColors.accentSoft, modifier = Modifier.size(14.dp))
                            Text("${activeRepoUrls.size}", style = PocketColors.monoStyle, color = PocketColors.textPrimary)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = lastCompletion != null && turboState == null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            lastCompletion?.let { done ->
                val isMismatch = done.checksumStatus == ChecksumVerifyStatus.MISMATCH
                val isVerified = done.checksumStatus == ChecksumVerifyStatus.VERIFIED_MATCH
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        isMismatch -> PocketColors.dangerBg
                        isVerified -> PocketColors.successBg
                        else -> PocketColors.surfaceCard
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isMismatch -> PocketColors.danger
                            isVerified -> PocketColors.success
                            else -> PocketColors.accent
                        }
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = if (isMismatch) Icons.Rounded.WarningAmber else Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = when {
                                    isMismatch -> PocketColors.danger
                                    isVerified -> PocketColors.success
                                    else -> PocketColors.accentSoft
                                },
                                modifier = Modifier.size(19.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        isMismatch -> "SHA-256 Checksum Mismatch!"
                                        isVerified -> "Verified Genuine ISO"
                                        done.injectedMultiBootPath != null -> "Added to ${done.injectedMultiBootPath.substringAfterLast('/')}"
                                        else -> "Download Complete"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PocketColors.textPrimary
                                )
                                Text(done.fileName, style = PocketColors.monoStyle, color = PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (!done.bootVerifiedLabel.isNullOrBlank() || !done.buildBenchmark.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!done.bootVerifiedLabel.isNullOrBlank()) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = PocketColors.successBg) {
                                        Text(done.bootVerifiedLabel, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = PocketColors.success)
                                    }
                                }
                                if (!done.buildBenchmark.isNullOrBlank()) {
                                    Text(done.buildBenchmark, style = PocketColors.monoStyle, color = PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        if (!done.computedSha256.isNullOrBlank()) {
                                IconButton(
                                    onClick = {
                                        val cm = context.getSystemService(ClipboardManager::class.java)
                                        cm?.setPrimaryClip(ClipData.newPlainText("SHA-256", done.computedSha256))
                                        Toast.makeText(context, "SHA-256 copied.", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, "Copy SHA-256", tint = PocketColors.accentSoft, modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(
                                onClick = { FastIsoDownloader.dismissLastCompletion() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Rounded.Close, "Dismiss", tint = PocketColors.textMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (!done.computedSha256.isNullOrBlank()) {
                            Text(
                                text = "SHA-256: ${done.computedSha256}",
                                style = PocketColors.monoStyle,
                                color = PocketColors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!isMismatch && done.injectedMultiBootPath == null) {
                            val isWinIso = done.fileName.endsWith(".iso", true) &&
                                (IsoInspector.quickGuessByName(done.fileName) == IsoOsType.WINDOWS || done.title.contains("Win", true))
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f).bounceClick { onQuickMountCompleted(done.savedPath) },
                                    shape = RoundedCornerShape(PocketTokens.CompactButtonRadius),
                                    color = PocketColors.accentBg,
                                    border = BorderStroke(1.dp, PocketColors.accent)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Usb, null, tint = PocketColors.accentSoft, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Mount", style = MaterialTheme.typography.labelSmall, color = PocketColors.accentSoft, maxLines = 1)
                                    }
                                }
                                if (isWinIso) {
                                    Surface(
                                        modifier = Modifier.weight(1.25f).bounceClick { onOpenWindowsBuilderForPath(done.savedPath) },
                                        shape = RoundedCornerShape(PocketTokens.CompactButtonRadius),
                                        color = PocketColors.infoBlueBg,
                                        border = BorderStroke(1.dp, PocketColors.infoBlue)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.Build, null, tint = PocketColors.infoBlue, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Win USB", style = MaterialTheme.typography.labelSmall, color = PocketColors.infoBlue, maxLines = 1)
                                        }
                                    }
                                }
                                if (done.fileName.endsWith(".iso", true) && multiBootDrives.isNotEmpty()) {
                                    Surface(
                                        modifier = Modifier.weight(1.25f).bounceClick { onAddCompletedToMultiBoot(done.savedPath) },
                                        shape = RoundedCornerShape(PocketTokens.CompactButtonRadius),
                                        color = PocketColors.violetBg,
                                        border = BorderStroke(1.dp, PocketColors.violet)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Rounded.Layers, null, tint = PocketColors.violet, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Multi-Boot", style = MaterialTheme.typography.labelSmall, color = PocketColors.violet, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = pendingResumables.isNotEmpty() && turboState == null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PocketSectionHeader("Unfinished Downloads · Resumable", trailing = "${pendingResumables.size}")
                pendingResumables.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PocketColors.surfaceCard,
                        border = BorderStroke(1.dp, PocketColors.warning.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(PocketColors.warningBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.History, null, tint = PocketColors.warning, modifier = Modifier.size(19.dp))
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(item.title, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = "${(item.progress * 100).toInt()}% · ${UsbGadgetEngine.formatBytes(item.downloadedBytes)} / ${UsbGadgetEngine.formatBytes(item.totalBytes)}",
                                    style = PocketColors.monoStyle,
                                    color = PocketColors.warning
                                )
                            }
                            IconButton(
                                onClick = {
                                    FastIsoDownloader.discardPendingDownload(item)
                                    pendingResumables = FastIsoDownloader.scanPendingResumableDownloads(context)
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, "Discard", tint = PocketColors.danger, modifier = Modifier.size(18.dp))
                            }
                            Surface(
                                modifier = Modifier.bounceClick {
                                    FastIsoDownloader.resumeFromSidecar(
                                        context = context,
                                        pending = item,
                                        autoVerifySha256 = settings.autoVerifySha256,
                                        onComplete = { evt -> scope.launch { onDownloadCompleted(evt) } },
                                        onError = { err ->
                                            scope.launch {
                                                pendingResumables = FastIsoDownloader.scanPendingResumableDownloads(context)
                                                onMessage(err, true)
                                            }
                                        },
                                        onLog = { entry -> scope.launch { onAppendLog(entry) } }
                                    )
                                },
                                shape = RoundedCornerShape(999.dp),
                                color = PocketColors.accent
                            ) {
                                Text("Resume", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = PocketColors.onAccent)
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = turboState != null,
            enter = fadeIn(tween(220)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
            exit = fadeOut(tween(180)) + shrinkVertically()
        ) {
            turboState?.let { dl ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = PocketColors.surfaceCard,
                    border = BorderStroke(1.5.dp, PocketColors.accent)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PocketColors.surfaceCard)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(dl.title, style = MaterialTheme.typography.titleMedium, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    Surface(shape = RoundedCornerShape(6.dp), color = PocketColors.warningBg) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = PocketColors.warning, modifier = Modifier.size(12.dp))
                                            Text("${dl.activeThreads}x", style = MaterialTheme.typography.labelSmall, color = PocketColors.warning)
                                        }
                                    }
                                }
                                Text(
                                    text = if (dl.targetMultiBootPath.isNotBlank()) "${dl.fileName} → ${dl.targetMultiBootPath.substringAfterLast('/')}" else dl.fileName,
                                    style = PocketColors.monoStyle,
                                    color = PocketColors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (dl.supportsRange && !dl.isVerifying && !dl.isInjectingMultiBoot) {
                                    IconButton(
                                        onClick = { if (dl.isPaused) FastIsoDownloader.resume() else FastIsoDownloader.pause() },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (dl.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                            contentDescription = if (dl.isPaused) "Resume" else "Pause",
                                            tint = PocketColors.accentSoft,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        FastIsoDownloader.cancel(deleteTemp = true)
                                        pendingResumables = FastIsoDownloader.scanPendingResumableDownloads(context)
                                        onMessage("Download cancelled.", false)
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(Icons.Rounded.Close, "Cancel", tint = PocketColors.danger, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (dl.isVerifying || dl.isInjectingMultiBoot) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                                color = if (dl.isVerifying) PocketColors.success else PocketColors.violet,
                                trackColor = PocketColors.surfaceSubtle
                            )
                            Text(dl.statusText, style = PocketColors.monoStyle, color = if (dl.isVerifying) PocketColors.success else PocketColors.violet)
                        } else if (dl.totalBytes > 0L) {
                            LinearProgressIndicator(
                                progress = { dl.progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                                color = PocketColors.accent,
                                trackColor = PocketColors.surfaceSubtle
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = "${(dl.progress * 100).toInt()}% · ${UsbGadgetEngine.formatBytes(dl.downloadedBytes)} / ${UsbGadgetEngine.formatBytes(dl.totalBytes)}",
                                    style = PocketColors.monoStyle,
                                    color = PocketColors.accentSoft
                                )
                                Text(
                                    text = if (dl.isPaused) "Paused · Resumable" else "${FastIsoDownloader.formatSpeed(dl.speedBytesPerSec)} · ${FastIsoDownloader.formatEta(dl.etaSeconds)}",
                                    style = PocketColors.monoStyle,
                                    color = PocketColors.success
                                )
                            }
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)),
                                color = PocketColors.accent,
                                trackColor = PocketColors.surfaceSubtle
                            )
                            Text("${UsbGadgetEngine.formatBytes(dl.downloadedBytes)} · ${FastIsoDownloader.formatSpeed(dl.speedBytesPerSec)}", style = PocketColors.monoStyle, color = PocketColors.accentSoft)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Auto-mount when finished", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                            PocketSwitch(
                                checked = autoMount,
                                onCheckedChange = {
                                    autoMount = it
                                    FastIsoDownloader.updateAutoMount(it)
                                }
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PocketSectionHeader(title = "OS Catalog", trailing = "Long-press to copy link")

            OutlinedTextField(
                value = catalogSearch,
                onValueChange = { catalogSearch = it },
                placeholder = { Text("Search OS catalog (Windows, Ubuntu, Kali, Rescue)...", fontSize = 12.5.sp, color = PocketColors.textMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, null, tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp)) },
                trailingIcon = if (catalogSearch.isNotEmpty()) {
                    {
                        IconButton(onClick = { catalogSearch = "" }) {
                            Icon(Icons.Rounded.Close, "Clear search", tint = PocketColors.textSecondary, modifier = Modifier.size(16.dp))
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
                dynamicCategories.forEach { cat ->
                    val selected = selectedCategory.equals(cat, ignoreCase = true)
                    val chipBg by animateColorAsState(if (selected) PocketColors.accentBg else PocketColors.surfaceCard, tween(180), label = "catBg")
                    val chipBorder by animateColorAsState(if (selected) PocketColors.accent else PocketColors.border, tween(180), label = "catBorder")
                    val chipText by animateColorAsState(if (selected) PocketColors.accentSoft else PocketColors.textSecondary, tween(180), label = "catText")
                    Surface(
                        modifier = Modifier.bounceClick { selectedCategory = cat },
                        shape = RoundedCornerShape(999.dp),
                        color = chipBg,
                        border = BorderStroke(if (selected) 1.dp else 0.75.dp, chipBorder)
                    ) {
                        Text(
                            text = cat,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = chipText
                        )
                    }
                }
            }

            if (selectedCategory == "All") {
                if (matchedWinFamilies.isEmpty() && matchedNonWin.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = PocketColors.surfaceCard,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Text(
                            text = "No OS images matched \"$catalogSearch\".",
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textSecondary
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (matchedWinFamilies.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                CatalogGroupHeader(
                                    icon = Icons.Rounded.Window,
                                    title = "Windows",
                                    count = matchedWinFamilies.size,
                                    tint = PocketColors.infoBlue,
                                    bg = PocketColors.infoBlueBg
                                )
                                val primaryWin = matchedWinFamilies.filter { it.isPrimaryFamily }
                                val secondaryWin = matchedWinFamilies.filterNot { it.isPrimaryFamily }
                                primaryWin.forEach { fam ->
                                    WindowsFamilyCard(
                                        fam = fam,
                                        onClick = { selectedWindowsFamily = fam },
                                        onLongClick = { longPressedWindows = fam }
                                    )
                                }
                                if (secondaryWin.isNotEmpty()) {
                                    val expandedOther = showOtherWindowsVersions || q.isNotEmpty()
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().bounceClick { showOtherWindowsVersions = !showOtherWindowsVersions },
                                        shape = RoundedCornerShape(13.dp),
                                        color = PocketColors.surfaceInput,
                                        border = BorderStroke(0.75.dp, if (expandedOther) PocketColors.infoBlue.copy(alpha = 0.6f) else PocketColors.border)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(PocketColors.infoBlueBg),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Rounded.Window, null, tint = PocketColors.infoBlue, modifier = Modifier.size(18.dp))
                                            }
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                Text(
                                                    "Other Windows Versions",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = PocketColors.textPrimary
                                                )
                                                Text(
                                                    secondaryWin.joinToString(" · ") { it.title.removePrefix("Windows ") },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = PocketColors.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Surface(shape = RoundedCornerShape(6.dp), color = PocketColors.infoBlueBg) {
                                                Text(
                                                    "${secondaryWin.size}",
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = PocketColors.infoBlue
                                                )
                                            }
                                            Icon(
                                                imageVector = if (expandedOther) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                                contentDescription = null,
                                                tint = PocketColors.textSecondary,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                    AnimatedVisibility(
                                        visible = expandedOther,
                                        enter = fadeIn(tween(180)) + expandVertically(),
                                        exit = fadeOut(tween(150)) + shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(start = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            secondaryWin.forEach { fam ->
                                                WindowsFamilyCard(
                                                    fam = fam,
                                                    onClick = { selectedWindowsFamily = fam },
                                                    onLongClick = { longPressedWindows = fam }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            } else if (selectedCategory.equals("Windows", true)) {
                if (matchedWinFamilies.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = PocketColors.surfaceCard,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Text(
                            text = "No Windows images matched \"$catalogSearch\".",
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textSecondary
                        )
                    }
                } else {
                val primaryWin = matchedWinFamilies.filter { it.isPrimaryFamily }
                val secondaryWin = matchedWinFamilies.filterNot { it.isPrimaryFamily }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    primaryWin.forEach { fam ->
                        WindowsFamilyCard(
                            fam = fam,
                            onClick = { selectedWindowsFamily = fam },
                            onLongClick = { longPressedWindows = fam }
                        )
                    }
                    if (secondaryWin.isNotEmpty()) {
                        val expandedOther = showOtherWindowsVersions || q.isNotEmpty()
                        Surface(
                            modifier = Modifier.fillMaxWidth().bounceClick { showOtherWindowsVersions = !showOtherWindowsVersions },
                            shape = RoundedCornerShape(13.dp),
                            color = PocketColors.surfaceInput,
                            border = BorderStroke(0.75.dp, if (expandedOther) PocketColors.infoBlue.copy(alpha = 0.6f) else PocketColors.border)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(PocketColors.infoBlueBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Window, null, tint = PocketColors.infoBlue, modifier = Modifier.size(18.dp))
                                }
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("Other Windows Versions", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                    Text(
                                        secondaryWin.joinToString(" · ") { it.title.removePrefix("Windows ") },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PocketColors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(shape = RoundedCornerShape(6.dp), color = PocketColors.infoBlueBg) {
                                    Text("${secondaryWin.size}", modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = PocketColors.infoBlue)
                                }
                                Icon(
                                    imageVector = if (expandedOther) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = PocketColors.textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        AnimatedVisibility(
                            visible = expandedOther,
                            enter = fadeIn(tween(180)) + expandVertically(),
                            exit = fadeOut(tween(150)) + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(start = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                secondaryWin.forEach { fam ->
                                    WindowsFamilyCard(
                                        fam = fam,
                                        onClick = { selectedWindowsFamily = fam },
                                        onLongClick = { longPressedWindows = fam }
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
        }

        if (selectedCategory == "All") {
            val grouped = matchedNonWin.groupBy { it.category }
            grouped.forEach { (catName, itemsInCat) ->
                item(key = "cat_hdr_$catName") {
                    val secIcon = when (catName.lowercase()) {
                        "linux" -> Icons.Rounded.Terminal
                        "server" -> Icons.Rounded.Dns
                        "security" -> Icons.Rounded.Security
                        "rescue" -> Icons.Rounded.BuildCircle
                        "lightweight" -> Icons.Rounded.Memory
                        else -> Icons.Rounded.Album
                    }
                    val secTint = when (catName.lowercase()) {
                        "linux" -> PocketColors.success
                        "server" -> PocketColors.violet
                        "security" -> PocketColors.danger
                        "rescue" -> PocketColors.warning
                        "lightweight" -> PocketColors.infoBlue
                        else -> PocketColors.accentSoft
                    }
                    val secBg = when (catName.lowercase()) {
                        "linux" -> PocketColors.successBg
                        "server" -> PocketColors.violetBg
                        "security" -> PocketColors.dangerBg
                        "rescue" -> PocketColors.warningBg
                        "lightweight" -> PocketColors.infoBlueBg
                        else -> PocketColors.accentBg
                    }
                    val secLabel = when (catName.lowercase()) {
                        "linux" -> "Linux Desktop"
                        "server" -> "Server & Homelab"
                        "security" -> "Security & Pentest"
                        "rescue" -> "Rescue & Repair"
                        "lightweight" -> "Lightweight & Netboot"
                        else -> catName
                    }
                    CatalogGroupHeader(
                        icon = secIcon,
                        title = secLabel,
                        count = itemsInCat.size,
                        tint = secTint,
                        bg = secBg
                    )
                }
                itemsIndexed(itemsInCat, key = { idx, iso -> "distro_${catName}_${idx}_${iso.title}" }) { _, iso ->
                    DistroCatalogCard(
                        iso = iso,
                        existingFileNames = existingFileNames,
                        onClick = { selectedDistroForVersion = iso },
                        onLongClick = { longPressedDistro = iso }
                    )
                }
            }
        } else if (!selectedCategory.equals("Windows", true)) {
            val filtered = matchedNonWin.filter { it.category.equals(selectedCategory, true) }
            if (filtered.isEmpty()) {
                item(key = "cat_empty_$selectedCategory") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = PocketColors.surfaceCard,
                        border = BorderStroke(0.5.dp, PocketColors.border)
                    ) {
                        Text(
                            text = if (q.isNotEmpty()) "No $selectedCategory images matched \"$catalogSearch\"." else "No $selectedCategory images available.",
                            modifier = Modifier.padding(18.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = PocketColors.textSecondary
                        )
                    }
                }
            } else {
                itemsIndexed(filtered, key = { idx, iso -> "filtered_${selectedCategory}_${idx}_${iso.title}" }) { _, iso ->
                    DistroCatalogCard(
                        iso = iso,
                        existingFileNames = existingFileNames,
                        onClick = { selectedDistroForVersion = iso },
                        onLongClick = { longPressedDistro = iso }
                    )
                }
            }
        }

        item(key = "dl_bottom_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    selectedWindowsFamily?.let { fam ->
        WindowsDownloadSheet(
            family = fam,
            turboConnections = settings.downloadConnections,
            existingFileNames = existingFileNames,
            onAppendLog = onAppendLog,
            onDismiss = { selectedWindowsFamily = null },
            onStartDirectDownload = { title, url, fileName, sha, refreshKey ->
                startTurboDownload(title, url, fileName, expectedSha256 = sha, refreshSourceKey = refreshKey)
            }
        )
    }

    selectedDistroForVersion?.let { iso ->
        ModalBottomSheet(
            onDismissRequest = { selectedDistroForVersion = null },
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(iso.title, style = MaterialTheme.typography.titleLarge, color = PocketColors.textPrimary)
                    Text("Choose a version to download or copy the direct link", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                }

                iso.effectiveVersions.forEach { ver ->
                    val saved = existingFileNames.contains(ver.fileName.lowercase())
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = PocketColors.surfaceInput,
                        border = BorderStroke(0.75.dp, if (saved) PocketColors.success else PocketColors.border)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(ver.label, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                                val meta = buildList {
                                    if (ver.date.isNotBlank()) add(ver.date)
                                    add(ver.sizeLabel)
                                }.joinToString(" · ")
                                Text(meta, style = PocketColors.monoStyle, color = PocketColors.textSecondary)
                                Text(ver.fileName, style = PocketColors.monoStyle, color = PocketColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(
                                onClick = { copyLinkToClipboard("${iso.title} ${ver.label}", ver.url) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, "Copy link", tint = PocketColors.accentSoft, modifier = Modifier.size(18.dp))
                            }
                            if (saved) {
                                Surface(shape = RoundedCornerShape(8.dp), color = PocketColors.successBg) {
                                    Text("Saved", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = PocketColors.success)
                                }
                            } else {
                                Surface(
                                    modifier = Modifier.bounceClick(enabled = !anyDownloadActive) {
                                        selectedDistroForVersion = null
                                        startTurboDownload("${iso.title} (${ver.label})", ver.url, ver.fileName, ver.approxBytes, ver.sha256)
                                    },
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (!anyDownloadActive) PocketColors.accent else PocketColors.surfaceSubtle
                                ) {
                                    Text(
                                        text = "⚡ Get",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (!anyDownloadActive) PocketColors.onAccent else PocketColors.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(22.dp))
            }
        }
    }

    longPressedDistro?.let { iso ->
        val primary = iso.effectiveVersions.first()
        AlertDialog(
            onDismissRequest = { longPressedDistro = null },
            containerColor = PocketColors.surfaceCard,
            title = { Text(iso.title, color = PocketColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(primary.label, style = MaterialTheme.typography.labelMedium, color = PocketColors.accentSoft)
                    Text(primary.url, style = PocketColors.monoStyle, color = PocketColors.textMuted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyLinkToClipboard(iso.title, primary.url)
                        longPressedDistro = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Link", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    longPressedDistro = null
                    selectedDistroForVersion = iso
                }) {
                    Text("All Versions", color = PocketColors.textSecondary)
                }
            }
        )
    }

    longPressedWindows?.let { fam ->
        var fetchingLink by remember(fam.id) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!fetchingLink) longPressedWindows = null },
            containerColor = PocketColors.surfaceCard,
            title = { Text(fam.title, color = PocketColors.textPrimary) },
            text = {
                Text(
                    text = "Copy the latest English (x64) direct ISO link for external download managers, or open the selector to pick another release, architecture, or language.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PocketColors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            fetchingLink = true
                            var copiedUrl: String? = null
                            // Primary: NTriver Archive (works for all 6 families including latest monthly MVS, LTSC, Win7, Server)
                            val ntRes = NtriverCatalog.fetchSections(context, fam, onLog = onAppendLog)
                            val ntSections = ntRes.data.orEmpty()
                            val prefSec = ntSections.firstOrNull { it.isRecommended } ?: ntSections.firstOrNull()
                            val x64Entries = prefSec?.entries?.filter { it.arch.equals("x64", true) }.orEmpty()
                                .ifEmpty { prefSec?.entries.orEmpty() }
                            val prefEntry = x64Entries.firstOrNull {
                                it.effectiveLanguageLabel.equals("English (US)", true) || it.effectiveLanguageLabel.equals("English", true)
                            } ?: x64Entries.firstOrNull { it.language.contains("English", true) }
                                ?: x64Entries.firstOrNull()
                            if (prefEntry != null) {
                                copiedUrl = NtriverCatalog.resolveDownloadUrl(context, prefEntry).url
                            }
                            // Fallback: Microsoft Direct CDN (for supported retail families)
                            if (copiedUrl == null && fam.supportsMicrosoftCdn) {
                                val rel = WindowsDirectCatalog.fetchReleases(fam).let { list -> list.firstOrNull { !it.isArm64Only } ?: list.firstOrNull() }
                                if (rel != null) {
                                    val skuRes = WindowsDirectCatalog.fetchLanguages(context, rel)
                                    val data = skuRes.data
                                    if (data != null) {
                                        val enSku = data.second.firstOrNull { it.localizedLanguage.contains("English (United States)", true) }
                                            ?: data.second.firstOrNull()
                                        if (enSku != null) {
                                            val dlRes = WindowsDirectCatalog.fetchDownloadLinks(context, rel, data.first, enSku.skuId)
                                            copiedUrl = (dlRes.data?.firstOrNull { it.arch.equals("x64", true) } ?: dlRes.data?.firstOrNull())?.url
                                        }
                                    }
                                }
                            }
                            fetchingLink = false
                            longPressedWindows = null
                            if (copiedUrl != null) {
                                copyLinkToClipboard("${fam.title} (English x64)", copiedUrl)
                            } else {
                                onMessage("Direct link unavailable right now. Open selector and tap Retry.", true)
                            }
                        }
                    },
                    enabled = !fetchingLink,
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    if (fetchingLink) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp, color = PocketColors.onAccent)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Signing...")
                    } else {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Direct Link", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        longPressedWindows = null
                        selectedWindowsFamily = fam
                    },
                    enabled = !fetchingLink
                ) {
                    Text("Open Selector", color = PocketColors.textSecondary)
                }
            }
        )
    }

    // Turbo Speed & Auto-Verify Settings Dialog
    if (showSpeedSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedSettingsDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Turbo Downloader Settings", color = PocketColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Runs in a dedicated background Foreground Service with automatic pause/resume across restarts and single-stream fallback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PocketColors.textSecondary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Parallel Connections", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text(if (settings.downloadConnections == 8) "8x (Recommended)" else "${settings.downloadConnections}x", style = PocketColors.monoStyle, color = PocketColors.accentSoft)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(4, 8, 12, 16).forEach { n ->
                                val isSel = settings.downloadConnections == n
                                Surface(
                                    modifier = Modifier.weight(1f).bounceClick { onUpdateSettings(settings.copy(downloadConnections = n)) },
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
                            Text("Check integrity right after download finishes", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
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
                TextButton(onClick = { showSpeedSettingsDialog = false }) {
                    Text("Done", color = PocketColors.accentSoft)
                }
            }
        )
    }

    if (showAddFolderDialog) {
        var newPath by remember { mutableStateOf("/sdcard/ISOs") }
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Add Download Folder", color = PocketColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a storage path. PocketBoot will create it if needed and set it as your active save folder.", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                    OutlinedTextField(value = newPath, onValueChange = { newPath = it }, label = { Text("Folder path") }, placeholder = { Text("/sdcard/ISOs") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val resolved = UsbGadgetEngine.resolveDirectPath(newPath)
                        if (resolved.isNotBlank()) {
                            scope.launch {
                                UsbGadgetEngine.ensureDirectoryExists(resolved, context)
                                val updatedDirs = (listOf(resolved) + settings.scanDirs.filter { it != resolved }).distinct()
                                onUpdateSettings(settings.copy(defaultDir = resolved, scanDirs = updatedDirs, downloadTargetMultiBootPath = ""))
                                onMessage("Added and selected ${UsbGadgetEngine.friendlyFolderName(resolved)}.", false)
                            }
                            showAddFolderDialog = false
                        }
                    },
                    enabled = newPath.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                ) {
                    Text("Add & Select", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) { Text("Cancel", color = PocketColors.textSecondary) }
            }
        )
    }

    if (showReposDialog) {
        var newRepoUrl by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReposDialog = false },
            containerColor = PocketColors.surfaceCard,
            title = { Text("Catalog Repositories", color = PocketColors.textPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Official PocketBoot Catalog", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                            Text("Curated multi-version Linux & rescue ISOs", style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary)
                        }
                        PocketSwitch(checked = settings.useDefaultDistroRepo, onCheckedChange = { onUpdateSettings(settings.copy(useDefaultDistroRepo = it)) })
                    }

                    PocketHairlineDivider()

                    if (settings.customRepoUrls.isNotEmpty()) {
                        PocketSectionHeader("Custom Repos")
                        settings.customRepoUrls.forEach { repo ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(repo, style = PocketColors.monoStyle, color = PocketColors.accentSoft, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                IconButton(onClick = { onUpdateSettings(settings.copy(customRepoUrls = settings.customRepoUrls - repo)) }) {
                                    Icon(Icons.Rounded.DeleteOutline, "Remove repo", tint = PocketColors.danger, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        PocketHairlineDivider()
                    }

                    Text("Add JSON Repo URL", style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary)
                    OutlinedTextField(value = newRepoUrl, onValueChange = { newRepoUrl = it }, placeholder = { Text("https://.../distros.json") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            val clean = newRepoUrl.trim()
                            if (clean.startsWith("http://") || clean.startsWith("https://")) {
                                onUpdateSettings(settings.copy(customRepoUrls = (settings.customRepoUrls + clean).distinct()))
                                newRepoUrl = ""
                            } else {
                                onMessage("Repo URL must start with http:// or https://", true)
                            }
                        },
                        enabled = newRepoUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PocketColors.accent, contentColor = PocketColors.onAccent)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Repository", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReposDialog = false }) { Text("Done", color = PocketColors.accentSoft) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WindowsFamilyCard(
    fam: WindowsCatalogFamily,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        color = PocketColors.surfaceCard,
        border = BorderStroke(1.dp, PocketColors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(PocketColors.infoBlueBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Window,
                    contentDescription = null,
                    tint = PocketColors.infoBlue,
                    modifier = Modifier.size(21.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(fam.title, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fam.subtitle, style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Surface(shape = RoundedCornerShape(5.dp), color = PocketColors.infoBlueBg) {
                        Text(
                            text = fam.badgeLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = PocketColors.infoBlue
                        )
                    }
                    Text(fam.sizeHint, style = PocketColors.monoStyle, color = PocketColors.textMuted)
                }
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = PocketColors.infoBlueBg,
                border = BorderStroke(0.75.dp, PocketColors.infoBlue)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Select", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = PocketColors.infoBlue)
                    Icon(Icons.Rounded.ChevronRight, null, tint = PocketColors.infoBlue, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DistroCatalogCard(
    iso: CuratedIso,
    existingFileNames: Set<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val versions = iso.effectiveVersions
    val anyInLibrary = versions.any { existingFileNames.contains(it.fileName.lowercase()) }
    val (icon, tint, iconBg) = when (iso.category.lowercase()) {
        "linux" -> Triple(Icons.Rounded.Terminal, PocketColors.success, PocketColors.successBg)
        "server" -> Triple(Icons.Rounded.Dns, PocketColors.violet, PocketColors.violetBg)
        "security" -> Triple(Icons.Rounded.Security, PocketColors.danger, PocketColors.dangerBg)
        "rescue" -> Triple(Icons.Rounded.BuildCircle, PocketColors.warning, PocketColors.warningBg)
        "lightweight", "utility" -> Triple(Icons.Rounded.Memory, PocketColors.infoBlue, PocketColors.infoBlueBg)
        else -> Triple(Icons.Rounded.Album, PocketColors.accentSoft, PocketColors.accentBg)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(14.dp),
        color = PocketColors.surfaceCard,
        border = BorderStroke(1.dp, if (anyInLibrary) PocketColors.success.copy(alpha = 0.6f) else PocketColors.border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(if (anyInLibrary) PocketColors.successBg else iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (anyInLibrary) PocketColors.success else tint, modifier = Modifier.size(21.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(iso.title, style = MaterialTheme.typography.labelLarge, color = PocketColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(iso.subtitle, style = MaterialTheme.typography.bodySmall, color = PocketColors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Surface(shape = RoundedCornerShape(5.dp), color = iconBg) {
                        Text(iso.category, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                    val verNote = if (versions.size > 1) "${versions.size} versions · ${iso.sizeLabel}" else iso.sizeLabel
                    Text(verNote, style = PocketColors.monoStyle, color = PocketColors.textMuted)
                }
            }
            if (anyInLibrary && versions.size == 1) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = PocketColors.successBg,
                    border = BorderStroke(0.75.dp, PocketColors.success)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = PocketColors.success, modifier = Modifier.size(13.dp))
                        Text("Saved", style = MaterialTheme.typography.labelSmall, color = PocketColors.success)
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.bounceClick(onClick = onClick),
                    shape = RoundedCornerShape(999.dp),
                    color = PocketColors.accent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = if (versions.size > 1) "Versions" else "Get",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PocketColors.onAccent
                        )
                        if (versions.size > 1) {
                            Icon(Icons.Rounded.ChevronRight, null, tint = PocketColors.onAccent, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
