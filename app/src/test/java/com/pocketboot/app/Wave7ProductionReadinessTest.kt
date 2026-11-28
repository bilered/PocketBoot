package com.pocketboot.app

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import java.io.File
import kotlin.math.pow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Wave7ProductionReadinessTest {

    @Test
    fun shellSafety_quotesEscapesAndEnforcesStrictPathContainment() {
        assertEquals("'simple/path.iso'", ShellSafety.shellQuote("simple/path.iso"))
        assertEquals("'it'\\''s a $(cmd) `test`'", ShellSafety.shellQuote("it's a $(cmd) `test`"))

        assertTrue(ShellSafety.isCleanAbsolutePath("/data/media/0/Download"))
        assertFalse(ShellSafety.isCleanAbsolutePath("relative/path"))
        assertFalse(ShellSafety.isCleanAbsolutePath("/data/media/0/../system"))
        assertFalse(ShellSafety.isCleanAbsolutePath("/data/media/0/bad\npath"))

        val lib = "/data/media/0/Download/PocketBoot_Drivers"
        assertTrue(DriverPackManager.isSafeChildOfLibrary(lib, "$lib/intel_vmd"))
        assertFalse(DriverPackManager.isSafeChildOfLibrary(lib, lib))
        assertFalse(DriverPackManager.isSafeChildOfLibrary(lib, "$lib/../other"))
        assertFalse(DriverPackManager.isSafeChildOfLibrary(lib, "/data/media/0/Download/PocketBoot_Drivers_evil/a"))
    }

    @Test
    fun shellSafety_blocksHttpsDowngradeAndSpoofedHosts() {
        assertTrue(ShellSafety.isHostOrSubdomain("github.com", "github.com"))
        assertTrue(ShellSafety.isHostOrSubdomain("objects.githubusercontent.com", "githubusercontent.com"))
        assertFalse(ShellSafety.isHostOrSubdomain("evilgithub.com", "github.com"))

        assertTrue(ShellSafety.isValidHttpUrl("https://example.com/distros.json"))
        assertTrue(ShellSafety.isValidHttpUrl("http://192.168.1.50/distros.json"))
        assertFalse(ShellSafety.isValidHttpUrl("file:///etc/passwd"))
        assertFalse(ShellSafety.isValidHttpUrl("javascript:alert(1)"))
        assertFalse(ShellSafety.isValidHttpUrl("http://example.com/a.apk", requireHttps = true))

        assertTrue(ShellSafety.isForbiddenHttpsDowngrade("https://ntriver.org/drive?win11.iso", "http://mirror.example.com/win11.iso"))
        assertTrue(ShellSafety.isForbiddenHttpsDowngrade("https://github.com/org/repo/releases/download/v1/a.iso", "http://cdn.example.com/a.iso"))
        assertTrue(ShellSafety.isForbiddenHttpsDowngrade("http://mirror.example.com/a", "http://download.microsoft.com/a.iso"))
        assertFalse(ShellSafety.isForbiddenHttpsDowngrade("https://ntriver.org/drive?win11.iso", "https://storage.example.net/win11.iso"))
        assertFalse(ShellSafety.isForbiddenHttpsDowngrade("https://mirror.linux-distro.org/a.iso", "http://cdn.linux-distro.org/a.iso"))
    }

    @Test
    fun otgPhysicalIdentityLock_rejectsHotSwappedDriveOnSameNode() {
        val scanned = OtgUsbDevice(
            devName = "sda",
            devPath = "/dev/block/sda",
            vendor = "SanDisk",
            model = "Ultra Fit",
            sizeBytes = 32_000_000_000L,
            sysfsRealPath = "/sys/devices/platform/soc/a600000.ssusb/usb2/2-1/2-1:1.0/host0/target0:0:0/0:0:0:0/block/sda",
            readOnly = false,
            logicalBlockSize = 512,
            devMajorMinor = "8:0"
        )

        assertTrue(OtgUsbFlasher.matchesVerifiedOtgTarget(scanned, scanned.copy()))
        assertFalse(OtgUsbFlasher.matchesVerifiedOtgTarget(scanned, scanned.copy(sizeBytes = 64_000_000_000L)))
        assertFalse(OtgUsbFlasher.matchesVerifiedOtgTarget(scanned, scanned.copy(vendor = "Samsung")))
        assertFalse(OtgUsbFlasher.matchesVerifiedOtgTarget(scanned, scanned.copy(model = "BAR Plus")))
        assertFalse(OtgUsbFlasher.matchesVerifiedOtgTarget(scanned, scanned.copy(devMajorMinor = "8:16")))
        assertFalse(OtgUsbFlasher.matchesVerifiedOtgTarget(scanned, scanned.copy(readOnly = true)))
        assertFalse(OtgUsbFlasher.matchesVerifiedOtgTarget(scanned, scanned.copy(logicalBlockSize = 4096)))
    }

    @Test
    fun backupImportAndUpdateChecker_rejectMaliciousPathsAndUntrustedHosts() {
        val current = AppSettings(defaultDir = "/data/media/0/Download")
        val tampered = JSONObject().apply {
            put("pocketbootBackupVersion", 1)
            put("defaultDir", "/data/media/0/../../system")
            put("favoriteQuickMountPath", "/data/media/0/../etc/passwd")
            put("downloadTargetMultiBootPath", "/data/media/0/ISOs/multiboot.img")
            put("scanDirs", JSONArray(listOf("/data/media/0/ISOs", "/data/media/0/../etc", "relative/dir")))
            put("customRepoUrls", JSONArray(listOf("https://example.org/distros.json", "file:///etc/hosts", "javascript:void(0)")))
            put("pinnedFiles", JSONArray(listOf("/data/media/0/ISOs/win11.iso", "/data/media/0/../evil.iso")))
            put("hiddenFiles", JSONArray(listOf("/data/media/0/ISOs/old.iso", "bad_rel.iso")))
            put("dualLunCombos", JSONArray().apply {
                put(JSONObject().put("name", "Good").put("lun0Path", "/data/media/0/ISOs/a.iso").put("lun1Path", "/data/media/0/ISOs/b.img"))
                put(JSONObject().put("name", "Bad").put("lun0Path", "/data/media/0/../system/a.iso").put("lun1Path", "/data/media/0/ISOs/b.img"))
            })
        }.toString()

        val imported = AppPrefs.importBackupJson(tampered, current)
        assertNotNull(imported)
        val cfg = imported!!
        assertEquals("/data/media/0/Download", cfg.defaultDir)
        assertEquals("", cfg.favoriteQuickMountPath)
        assertEquals("/data/media/0/ISOs/multiboot.img", cfg.downloadTargetMultiBootPath)
        assertEquals(listOf("/data/media/0/Download", "/data/media/0/ISOs"), cfg.scanDirs)
        assertEquals(listOf("https://example.org/distros.json"), cfg.customRepoUrls)
        assertEquals(setOf("/data/media/0/ISOs/win11.iso"), cfg.pinnedFiles)
        assertEquals(setOf("/data/media/0/ISOs/old.iso"), cfg.hiddenFiles)
        assertEquals(1, cfg.dualLunCombos.size)
        assertEquals("Good", cfg.dualLunCombos.first().name)

        val oversized = " ".repeat(2 * 1024 * 1024 + 32)
        assertNull(AppPrefs.importBackupJson(oversized, current))

        assertTrue(AppUpdateChecker.isTrustedReleaseUrl("https://github.com/bilered/PocketBoot/releases/download/v4.11.0/app.apk"))
        assertTrue(AppUpdateChecker.isTrustedReleaseUrl("https://objects.githubusercontent.com/github-production-release-asset/app.apk"))
        assertFalse(AppUpdateChecker.isTrustedReleaseUrl("https://evilgithub.com/app.apk"))
        assertFalse(AppUpdateChecker.isTrustedReleaseUrl("http://github.com/bilered/PocketBoot/releases"))
    }

    @Test
    fun sidecarMeta_writesAtomicallyAndLeavesNoTempFile() {
        val dir = File(System.getProperty("java.io.tmpdir"), "pb_sidecar_test_${System.nanoTime()}").apply { mkdirs() }
        try {
            val meta = File(dir, "win11.iso.part.pbmeta")
            val payload = JSONObject().put("fileName", "win11.iso").put("totalBytes", 5_500_000_000L).toString()
            assertTrue(FastIsoDownloader.writeSidecarAtomically(meta, payload))
            assertTrue(meta.exists())
            assertFalse(File(dir, "${meta.name}.tmp").exists())
            assertEquals("win11.iso", JSONObject(meta.readText(Charsets.UTF_8)).getString("fileName"))
        } finally {
            dir.deleteRecursively()
        }

        val validObj = JSONObject()
            .put("chunkStarts", JSONArray(listOf(0L, 1000L)))
            .put("chunkEnds", JSONArray(listOf(999L, 1999L)))
            .put("chunkCursors", JSONArray(listOf(500L, 9999L)))
        val parsed = FastIsoDownloader.parseValidatedSidecarChunks(validObj)
        assertNotNull(parsed)
        assertEquals(500L, parsed!!.third[0])
        assertEquals(2000L, parsed.third[1]) // Clamped to end + 1

        val mismatchObj = JSONObject()
            .put("chunkStarts", JSONArray(listOf(0L, 1000L)))
            .put("chunkEnds", JSONArray(listOf(999L)))
            .put("chunkCursors", JSONArray(listOf(0L, 1000L)))
        assertNull(FastIsoDownloader.parseValidatedSidecarChunks(mismatchObj))

        val invertedObj = JSONObject()
            .put("chunkStarts", JSONArray(listOf(1000L)))
            .put("chunkEnds", JSONArray(listOf(500L)))
            .put("chunkCursors", JSONArray(listOf(1000L)))
        assertNull(FastIsoDownloader.parseValidatedSidecarChunks(invertedObj))

        val emptyObj = JSONObject()
            .put("chunkStarts", JSONArray())
            .put("chunkEnds", JSONArray())
            .put("chunkCursors", JSONArray())
        assertNull(FastIsoDownloader.parseValidatedSidecarChunks(emptyObj))
    }

    @Test
    fun themePalettes_meetWcag21AaContrastAcrossLightDarkAndAmoled() {
        fun relLum(c: Color): Double {
            fun ch(v: Float): Double {
                val d = v.toDouble()
                return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
        }
        fun contrast(fg: Color, bg: Color): Double {
            val l1 = relLum(fg)
            val l2 = relLum(bg)
            return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
        }

        for (mode in SurfaceMode.entries) {
            val p = buildPocketPalette(mode, useMonet = false, customAccent = Color(0xFF6366F1), monetAccent = null)
            val pairs = listOf(
                "textPrimary/card" to (p.textPrimary to p.surfaceCard),
                "textSecondary/card" to (p.textSecondary to p.surfaceCard),
                "textMuted/card" to (p.textMuted to p.surfaceCard),
                "textMuted/input" to (p.textMuted to p.surfaceInput),
                "success/bg" to (p.success to p.successBg),
                "warning/bg" to (p.warning to p.warningBg),
                "danger/bg" to (p.danger to p.dangerBg),
                "infoBlue/bg" to (p.infoBlue to p.infoBlueBg),
                "violet/bg" to (p.violet to p.violetBg),
                "warning/card" to (p.warning to p.surfaceCard),
                "success/card" to (p.success to p.surfaceCard)
            )
            for ((label, pair) in pairs) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue("${mode.name} $label contrast $ratio < 4.5:1", ratio >= 4.5)
            }
        }
    }
}
