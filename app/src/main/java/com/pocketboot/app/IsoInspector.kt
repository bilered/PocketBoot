package com.pocketboot.app

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class IsoOsType(val badgeLabel: String, val displayType: String) {
    WINDOWS("Windows", "Windows Installer ISO"),
    LINUX("Linux", "Linux / Live ISO"),
    UTILITY("Bootable ISO", "Bootable Utility ISO"),
    VENTOY_IMAGE("Multi-Boot", "PocketBoot Multi-Boot Drive (.img)"),
    DISK_IMAGE("Disk Image", "Disk Image (.img)"),
    COMPRESSED_IMAGE("Compressed", "Compressed Image (.xz / .gz / .zst)")
}

data class BootReadinessReport(
    val partitionScheme: String,
    val filesystem: String,
    val hasUefiX64: Boolean,
    val hasUefiArm64: Boolean,
    val hasLegacyBios: Boolean,
    val isBootReady: Boolean,
    val summaryNote: String
)

object IsoInspector {

    // Upstream Linux f_mass_storage CD-ROM (cdrom=1) limit: (256*60*75 - 1) * 2048 bytes (~2.20 GiB)
    const val KERNEL_CDROM_MAX_BYTES = 2_359_293_952L

    private val cache = ConcurrentHashMap<String, IsoOsType>()

    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    fun invalidate(path: String) {
        val prefix = "$path|"
        cache.keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }

    private val COMPRESSED_SUFFIXES = listOf(
        ".img.xz", ".img.gz", ".img.zst",
        ".iso.xz", ".iso.gz", ".iso.zst"
    )

    fun isCompressedImageName(fileName: String): Boolean {
        val low = fileName.trim().lowercase(Locale.US)
        return COMPRESSED_SUFFIXES.any { low.endsWith(it) }
    }

    fun isSupportedDriveFileName(fileName: String): Boolean {
        val low = fileName.trim().lowercase(Locale.US)
        return low.endsWith(".iso") || low.endsWith(".img") || isCompressedImageName(low)
    }

    fun quickGuessByName(fileName: String): IsoOsType {
        val low = fileName.lowercase()
        if (isCompressedImageName(low)) {
            return IsoOsType.COMPRESSED_IMAGE
        }
        if (low.endsWith(".img")) {
            return if (low.contains("multiboot") || low.contains("multi boot") || low.contains("multi-boot") || low.contains("multi iso") || low.contains("multi-iso") || low.contains("ventoy")) IsoOsType.VENTOY_IMAGE else IsoOsType.DISK_IMAGE
        }
        if (!low.endsWith(".iso")) return IsoOsType.DISK_IMAGE

        val winHints = listOf(
            "windows", "win11", "win10", "win8", "win7",
            "win_11", "win_10", "win_8", "win_7", "win-11", "win-10", "win-8", "win-7",
            "tiny11", "tiny10", "ltsc", "winpe", "w11", "w10", "en-us_windows",
            "cccoma", "26h2", "26h1", "25h2", "24h2", "23h2", "22h2",
            "server2019", "server2022", "server2025", "server_2019", "server_2022", "server_2025",
            "windows_server", "serverdc", "_server_"
        )
        if (winHints.any { low.contains(it) }) return IsoOsType.WINDOWS

        val linuxHints = listOf(
            "ubuntu", "debian", "kali", "fedora", "arch", "mint", "manjaro", "pop-os", "pop_os",
            "alpine", "tails", "endeavour", "garuda", "cachyos", "zorin", "elementary", "mx-",
            "opensuse", "nixos", "void", "gentoo", "parrot", "blackarch", "bazzite", "nobara",
            "linux", "knoppix", "slackware", "alma", "rocky", "centos", "puppy", "antix", "sparky"
        )
        if (linuxHints.any { low.contains(it) }) return IsoOsType.LINUX

        return IsoOsType.UTILITY
    }

    suspend fun inspect(context: Context, path: String, sizeBytes: Long = 0L): IsoOsType = withContext(Dispatchers.IO) {
        val name = path.substringAfterLast('/')
        val cacheKey = "$path|$sizeBytes"
        cache[cacheKey]?.let { return@withContext it }

        if (isCompressedImageName(name)) {
            cache[cacheKey] = IsoOsType.COMPRESSED_IMAGE
            return@withContext IsoOsType.COMPRESSED_IMAGE
        }

        if (name.endsWith(".img", ignoreCase = true)) {
            val isVentoy = if (sizeBytes >= 64L * 1024L * 1024L) {
                VentoyImageBuilder.probeVentoyImage(path) != null
            } else {
                false
            }
            val result = if (isVentoy) IsoOsType.VENTOY_IMAGE else IsoOsType.DISK_IMAGE
            cache[cacheKey] = result
            return@withContext result
        }

        if (!name.endsWith(".iso", ignoreCase = true)) return@withContext IsoOsType.DISK_IMAGE

        val resolved = UsbGadgetEngine.resolveDirectPath(path)
        val qPath = shellQuote(resolved)

        val bundled7z = File(context.applicationInfo.nativeLibraryDir, "lib7zzs.so")
        if (bundled7z.exists() && bundled7z.canExecute()) {
            val listing = runCatching {
                Shell.cmd(
                    "${shellQuote(bundled7z.absolutePath)} l -ba $qPath 2>/dev/null | head -n 400"
                ).exec().out.joinToString("\n").lowercase()
            }.getOrDefault("")

            if (listing.isNotBlank()) {
                val isWin = listing.contains("sources/install.wim") ||
                    listing.contains("sources/install.esd") ||
                    listing.contains("sources/install.swm") ||
                    (listing.contains("sources/boot.wim") && listing.contains("bootmgr"))
                if (isWin) {
                    cache[cacheKey] = IsoOsType.WINDOWS
                    return@withContext IsoOsType.WINDOWS
                }

                val isLinux = listing.contains("casper/") ||
                    listing.contains("isolinux/") ||
                    listing.contains("syslinux/") ||
                    listing.contains("live/filesystem.squashfs") ||
                    listing.contains("live/vmlinuz") ||
                    listing.contains("arch/boot/") ||
                    listing.contains("images/pxeboot/") ||
                    listing.contains("vmlinuz") ||
                    listing.contains("initrd")
                if (isLinux) {
                    cache[cacheKey] = IsoOsType.LINUX
                    return@withContext IsoOsType.LINUX
                }
            }
        }

        val volLabel = runCatching {
            Shell.cmd(
                "dd if=$qPath bs=1 skip=32808 count=32 2>/dev/null | tr -cd 'A-Za-z0-9_-'"
            ).exec().out.firstOrNull().orEmpty().uppercase()
        }.getOrDefault("")

        val winLabels = listOf("CCCOMA", "ESD-ISO", "IR5_", "CENA_", "CPBA_", "SSS_X64", "WIN10", "WIN11", "WINDOWS", "TINY11")
        if (winLabels.any { volLabel.contains(it) }) {
            cache[cacheKey] = IsoOsType.WINDOWS
            return@withContext IsoOsType.WINDOWS
        }

        val linuxLabels = listOf("UBUNTU", "DEBIAN", "KALI", "FEDORA", "ARCH", "MINT", "MANJARO", "POP_OS", "ALPINE", "LINUX", "PARROT", "ZORIN", "TAILS")
        if (linuxLabels.any { volLabel.contains(it) }) {
            cache[cacheKey] = IsoOsType.LINUX
            return@withContext IsoOsType.LINUX
        }

        val guessed = quickGuessByName(name)
        cache[cacheKey] = guessed
        guessed
    }

    suspend fun inspectBootReadiness(context: Context, path: String, sizeBytes: Long): BootReadinessReport =
        withContext(Dispatchers.IO) {
            val resolved = UsbGadgetEngine.resolveDirectPath(path)
            val qPath = shellQuote(resolved)
            val name = resolved.substringAfterLast('/')
            val isIso = name.endsWith(".iso", ignoreCase = true)

            if (isIso) {
                val bundled7z = File(context.applicationInfo.nativeLibraryDir, "lib7zzs.so")
                val listing = if (bundled7z.exists() && bundled7z.canExecute()) {
                    Shell.cmd("${shellQuote(bundled7z.absolutePath)} l -ba $qPath 2>/dev/null | head -n 600")
                        .exec().out.joinToString("\n").lowercase()
                } else ""

                val hasEfiX64 = listing.contains("efi/boot/bootx64.efi") ||
                    listing.contains("efi/microsoft/boot/efisys") ||
                    listing.contains("boot/grub/x86_64-efi") ||
                    listing.contains("efi.img")
                val hasEfiArm64 = listing.contains("efi/boot/bootaa64.efi") ||
                    listing.contains("boot/grub/arm64-efi")
                val hasBios = listing.contains("isolinux/") ||
                    listing.contains("syslinux/") ||
                    listing.contains("boot/etfsboot.com") ||
                    listing.contains("bootmgr") ||
                    listing.contains("[boot]")
                val hasUdf = Shell.cmd(
                    "dd if=$qPath bs=2048 skip=16 count=6 2>/dev/null | tr -cd 'A-Za-z0-9'"
                ).exec().out.joinToString("").uppercase().let { it.contains("BEA01") || it.contains("NSR0") }

                val fs = if (hasUdf) "UDF / ISO9660" else "ISO9660"
                val ready = hasEfiX64 || hasEfiArm64 || hasBios
                val overCdLimit = sizeBytes > KERNEL_CDROM_MAX_BYTES
                val note = when {
                    !ready -> "No standard UEFI or BIOS bootloader detected"
                    overCdLimit && listing.contains("sources/install.") -> "Bootable Windows ISO · Over 2.2 GB (use USB Builder or Multi-Boot)"
                    hasEfiX64 && hasBios -> "Hybrid UEFI x64 + Legacy BIOS ready"
                    hasEfiX64 -> "UEFI x64 bootable"
                    hasEfiArm64 -> "UEFI ARM64 bootable"
                    else -> "Legacy BIOS bootable"
                }
                return@withContext BootReadinessReport(
                    partitionScheme = "Optical ISO Image",
                    filesystem = fs,
                    hasUefiX64 = hasEfiX64,
                    hasUefiArm64 = hasEfiArm64,
                    hasLegacyBios = hasBios,
                    isBootReady = ready,
                    summaryNote = note
                )
            }

            val ventoyProbe = VentoyImageBuilder.probeVentoyImage(resolved)
            if (ventoyProbe != null) {
                val p1Magic = Shell.cmd(
                    "dd if=$qPath bs=512 skip=${ventoyProbe.part1OffsetBytes / 512L} count=1 2>/dev/null | tr -cd 'A-Za-z0-9 '"
                ).exec().out.joinToString("").uppercase()
                val vFs = when {
                    p1Magic.contains("NTFS") -> "NTFS"
                    p1Magic.contains("EXFAT") -> "exFAT"
                    p1Magic.contains("FAT32") -> "FAT32"
                    else -> "NTFS / exFAT"
                }
                return@withContext BootReadinessReport(
                    partitionScheme = "${ventoyProbe.scheme.label} (Hybrid)",
                    filesystem = vFs,
                    hasUefiX64 = true,
                    hasUefiArm64 = true,
                    hasLegacyBios = true,
                    isBootReady = true,
                    summaryNote = "Multi-Boot UEFI (x64/ARM64) + Legacy BIOS ready"
                )
            }

            val probeOut = Shell.cmd(
                """
                GPT_SIG=${'$'}(dd if=$qPath bs=1 skip=512 count=8 2>/dev/null | tr -cd 'A-Z ')
                MBR_SIG=${'$'}(dd if=$qPath bs=1 skip=510 count=2 2>/dev/null | od -An -tx1 2>/dev/null | tr -d ' \n')
                BLK=${'$'}(blkid $qPath 2>/dev/null || true)
                P1_MAGIC=${'$'}(dd if=$qPath bs=512 skip=2048 count=1 2>/dev/null | tr -cd 'A-Za-z0-9 ')
                echo "${'$'}GPT_SIG|${'$'}MBR_SIG|${'$'}P1_MAGIC|${'$'}BLK"
                """.trimIndent()
            ).exec().out.lastOrNull().orEmpty()

            val parts = probeOut.split("|", limit = 4)
            val isGpt = parts.getOrNull(0).orEmpty().contains("EFI PART")
            val hasMbrSig = parts.getOrNull(1).orEmpty().equals("55aa", ignoreCase = true)
            val p1Magic = parts.getOrNull(2).orEmpty().uppercase()
            val blk = parts.getOrNull(3).orEmpty().uppercase()

            val scheme = when {
                isGpt -> "GPT (UEFI)"
                hasMbrSig -> "MBR"
                else -> "Raw / Unformatted"
            }
            val fs = when {
                p1Magic.contains("NTFS") || blk.contains("TYPE=\"NTFS\"") -> "NTFS"
                p1Magic.contains("EXFAT") || blk.contains("TYPE=\"EXFAT\"") -> "exFAT"
                p1Magic.contains("FAT32") || blk.contains("TYPE=\"VFAT\"") -> "FAT32"
                blk.contains("TYPE=\"EXT4\"") -> "ext4"
                hasMbrSig -> "Partitioned Disk"
                else -> "Unformatted"
            }

            val hasUefiPart = isGpt && fs == "NTFS" && sizeBytes > 512L * 1024L * 1024L
            val hasEfi = hasUefiPart || (fs == "FAT32" && hasMbrSig)
            val hasBios = hasMbrSig && (fs == "NTFS" || fs == "FAT32" || fs == "exFAT")
            val ready = hasEfi || hasBios
            val note = when {
                scheme == "Raw / Unformatted" -> "Blank drive — format on PC after mounting"
                hasUefiPart -> "GPT NTFS + UEFI:NTFS bootable on modern PCs"
                hasBios && !isGpt -> "MBR Legacy BIOS bootable disk image"
                else -> "Standard disk image ($scheme · $fs)"
            }
            BootReadinessReport(
                partitionScheme = scheme,
                filesystem = fs,
                hasUefiX64 = hasEfi,
                hasUefiArm64 = hasUefiPart,
                hasLegacyBios = hasBios,
                isBootReady = ready,
                summaryNote = note
            )
        }

    const val DEFAULT_MULTIBOOT_NTFS_LABEL = "POCKETBOOT Multi ISO"
    const val DEFAULT_MULTIBOOT_EXFAT_LABEL = "PB MultiISO"
    const val DEFAULT_MULTIBOOT_FILE_BASE = "POCKETBOOT Multi ISO"
    const val EXPLORER_SINGLE_LINE_SAFE_MAX = 20

    fun sanitizeNtfsLabel(raw: String, fallback: String = "POCKETBOOT"): String {
        val cleaned = raw
            .filter { ch -> ch.code >= 0x20 && ch !in "\\/:*?\"<>|" }
            .trim()
            .take(32)
            .trimEnd()
        return cleaned.ifBlank { fallback.take(32) }
    }

    fun sanitizeFatLabel(raw: String, fallback: String = "POCKETBOOT", uppercase: Boolean = true): String {
        val base = raw
            .filter { ch -> ch.code in 0x20..0x7E && ch !in "\\/:*?\"<>|.,;+=[]'`" }
            .trim()
            .let { if (uppercase) it.uppercase(Locale.US).replace(' ', '_') else it }
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(11)
            .trimEnd('_', ' ')
        return base.ifBlank { fallback.take(11) }
    }

    fun defaultWindowsVolumeLabel(osKind: WindowsOsKind, isFat32: Boolean = false): String = if (isFat32) {
        when (osKind) {
            WindowsOsKind.WIN11 -> "PB-WIN11"
            WindowsOsKind.WIN10 -> "PB-WIN10"
            WindowsOsKind.WIN81 -> "PB-WIN81"
            WindowsOsKind.WIN7 -> "PB-WIN7"
            WindowsOsKind.SERVER -> "PB-WINSRV"
            WindowsOsKind.UNKNOWN -> "PB-WIN"
        }
    } else {
        when (osKind) {
            WindowsOsKind.WIN11 -> "POCKETBOOT-WIN 11"
            WindowsOsKind.WIN10 -> "POCKETBOOT-WIN 10"
            WindowsOsKind.WIN81 -> "POCKETBOOT-WIN 8.1"
            WindowsOsKind.WIN7 -> "POCKETBOOT-WIN 7"
            WindowsOsKind.SERVER -> "POCKETBOOT-WIN SRV"
            WindowsOsKind.UNKNOWN -> "POCKETBOOT-WINDOWS"
        }
    }

    fun defaultMultiBootLabel(useExfat: Boolean): String =
        if (useExfat) DEFAULT_MULTIBOOT_EXFAT_LABEL else DEFAULT_MULTIBOOT_NTFS_LABEL

    /**
     * Reads the original ISO-9660 Primary Volume Descriptor disc label (CD001 @ byte 32769, label @ 32808..32839).
     */
    fun readIso9660VolumeLabel(isoPath: String): String {
        val resolved = UsbGadgetEngine.resolveDirectPath(isoPath)
        val qIso = shellQuote(resolved)
        val raw = Shell.cmd(
            "MAG=\$(dd if=$qIso bs=1 skip=32769 count=5 2>/dev/null | tr -cd 'A-Z0-9'); " +
                "if [ \"\$MAG\" = \"CD001\" ]; then dd if=$qIso bs=1 skip=32808 count=32 2>/dev/null | tr -cd 'A-Za-z0-9 _-'; fi"
        ).exec().out.firstOrNull().orEmpty().trim()
        return raw
    }

    /**
     * Default Linux Extracted FAT32 label: original ISO-9660 disc name trimmed & sanitized to FAT32's 11-char cap
     * (matching standard ISO-mode conventions), falling back to the sanitized filename stem.
     */
    fun defaultLinuxFat32Label(isoPath: String, isoFileName: String): String {
        val pvd = readIso9660VolumeLabel(isoPath)
        val candidate = pvd.ifBlank { isoFileName.substringBeforeLast('.') }
        return sanitizeFatLabel(candidate, fallback = "POCKETBOOT", uppercase = true)
    }
}
