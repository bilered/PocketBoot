# PocketBoot Pending Tasks

1. **GitHub Actions CI Verification (`v4.11.0`, `versionCode = 42`):** Push Wave 7 and verify `gradle testDebugUnitTest --stacktrace` (all 10 JUnit suites), `gradle lintDebug --stacktrace`, `gradle assembleDebug --stacktrace`, and all 10 ARM64 binary `test -x` checks pass green.
2. **On-Device Smoke Tests (Wave 7 — Security, Lifecycle & Regression):**
   - Verify **Settings → Backup & Restore** rejects malformed paths/URLs while cleanly restoring valid settings.
   - Verify **Driver Pack Manager** imports valid `.zip`/`.7z` packs and deletes packs cleanly.
   - Verify **Light / Dark / AMOLED** theme rendering on Home root-warning button and Accent color swatches.
3. **On-Device Smoke Tests (Wave 6 — NTriver Direct Windows Archive):**
   - Open **Download → Windows** → verify **Windows 11**, **Windows 10**, and collapsible **Other Windows Versions (4)** (**LTSC**, **8.1**, **7 SP1**, **Server**) load cleanly with 2-level **Main Version → Edition** chips.
   - Tap **Turbo Download** → verify fresh URL resolves, multi-connection download starts, Pause + Resume continues cleanly, and post-download SHA-256 verification matches green.
   - Check **Settings → Activity Log** for NTriver tier telemetry if any mirror falls back.
4. **On-Device Hardware Tests (Wave 5 — Direct-Mode OTG on Disposable USB Drive Only):**
   - Plug a disposable USB pen drive via USB-C OTG → open **Home → OTG Flash** → confirm target displays `512 B verified` and **4 mode tabs** (`Windows`, `Linux`, `Multi-Boot`, `Raw DD`).
   - Test hot-swap rejection (swap USB drive after scan without re-scanning → confirm Layer 10 identity lock blocks wipe/flash).
   - Test Modes 1–4 (`Windows`, `Linux + Persistence`, `Multi-Boot`, `Raw DD`) and record results using `templates/DEVICE_TEST_RECORD_TEMPLATE.md`.
5. **Supply-Chain & Release Hardening (Follow-up):**
   - Verify SHA-256 checksums for all downloaded CI source tarballs (`uefi-ntfs.img`, `7zzs`, `ntfs-3g`, `ms-sys`, `exfatprogs`, `dosfstools`, `wimlib`, `libxml2`, `chntpw`, `zstd`, pinned Ventoy `v1.1.18`) and pin GitHub Actions to commit SHAs.
   - Configure release signing and `assembleRelease` CI verification when publishing signed GitHub Releases.
