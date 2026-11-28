# PocketBoot Development Progress

## Current Milestone: v4.11.0 (r2) — Wave 7 Complete (Security, USB Safety, UI/UX WCAG AA, Performance & CI Hardening)
- **Wave 7 r2 Audit Remediation Implemented (CI & Hardware Verification Pending) (`v4.11.0`, `versionCode = 42`):**
  - Fixed P0 `UsbBuildService.onTimeout()` symbol (`cancelActiveBuild(this)`), sequenced native cancel before job cancel, fixed `UsbKeepAliveService` `START_STICKY` null-intent recovery, upgraded `FastIsoDownloader` to `AtomicLongArray` + atomic `.pbmeta` writes, checked OTG flush exit codes, and added `major:minor` + `512 B` details to `FLASH` confirmation.
  - Recalibrated Light/Dark/AMOLED palettes to pass **WCAG 2.1 AA (`>= 4.5:1`)** across all semantic pairs, upgraded `bounceClick` (`LocalIndication` + `Role.Button`), removed all 9 gradients & 2 raw emojis, lifted all `IconButton`s to `44.dp` and micro-labels to `>= 11.sp`, migrated `DownloadScreen` & `SystemTweaksScreen` to keyed `LazyColumn`s, added `rememberSaveable` + `BackHandler`, pinned Ventoy `v1.1.18` in CI, and added WCAG contrast + atomic sidecar JVM unit tests.
- **Wave 7 Implemented (CI & Hardware Verification Pending) (`v4.11.0`, `versionCode = 42`):**
  - **Security & USB Safety (`ShellSafety.kt`, `OtgUsbFlasher.kt`, `DriverPackManager.kt`, `UsbGadgetEngine.kt`, `VentoyImageBuilder.kt`, `AppPrefs.kt`, `AppUpdateChecker.kt`, `FastIsoDownloader.kt`):** Centralized `shellQuote()` across 12 modules, added 10-layer OTG physical identity lock (`devMajorMinor` + size/vendor/model), hardened archive extraction/deletion against symlinks & traversal, enforced HTTPS downgrade protection, and sanitized backup imports & updater links.
  - **Android 14/15 Lifecycle & Design System (`UsbBuildService.kt`, `UsbKeepAliveService.kt`, `FastIsoDownloader.kt`, `Theme.kt`, `MainActivity.kt`, `SettingsScreen.kt`):** Bounded WakeLock timeouts, Android 15 `onTimeout()` handlers, post-move `.iso.zip` verification before source deletion, and `PocketColors.onBrightSurface` token cleanup.
  - **CI & Test Hardening (`.github/workflows/build-apk.yml`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `Wave7ProductionReadinessTest.kt`):** Least-privilege CI permissions, all-10 native binary verification gate, `lintDebug`, APK SHA-256 output, ProGuard rules, and 10th JUnit suite.

## Previous Milestone: v4.10.0 — Wave 6 (NTriver Direct Windows Archive & Downloader Hardening)
- **Wave 6 Complete (`v4.10.0`, `versionCode = 41`):**
  - **NTriver Direct Windows Archive (`NtriverCatalog.kt`, `WindowsDownloadSheet.kt`, `DownloadScreen.kt`):** Added NTriver Archive as default tab (with Microsoft CDN preserved as Tab 2 on Win 11/10/8.1) and expanded Windows cards to 6 families (**Windows 11**, **Windows 10**, **Windows 10 / 11 LTSC**, **Windows 8.1**, **Windows 7 SP1**, **Windows Server 2025/2022/2019**) with official SHA-256 auto-verification, MS Static Link priority, dynamic GitHub Markdown fallback, and partial-language previous-month shortcut.
  - **Turbo Downloader & Security Hardening (`FastIsoDownloader.kt`, `network_security_config.xml`):** Host-scoped redirect cookies, `Mutex` single-flight expired-URL refresh on resume & `401/403/404/410`, verified `206` range probe, strict HTTPS on `ntriver.org`, and RTM `windows_7`/`_x32`/`serverdc` detection.
  - **Wave 6 JVM Unit Tests (`Wave6NtriverTest.kt`):** 9th JUnit suite verifying parsers, clutter filtering, JSON validation, cookie isolation, and fail-closed guards.

## Previous Milestone: v4.9.0 — Wave 5 (Direct-Mode USB-C OTG Upgrade — 4 Modes)
- **Wave 5 Complete (`v4.9.0`, `versionCode = 40`):**
  - **Direct 4-Mode USB-C OTG Flasher (`OtgUsbFlasher.kt`, `DiskPartitioner.kt`, `WindowsImageBuilder.kt`, `LinuxImageBuilder.kt`, `VentoyImageBuilder.kt`):** Direct-to-pen-drive **Direct Windows USB** (full tweaks, `boot.wim` bypass, Driver Packs), **Direct Linux + Persistence** (Extracted FAT32 / Hybrid DD + dynamic ext4 persistence), **Direct Multi-Boot USB** (full-capacity physical Multi-Boot + tweaks/drivers), and **Raw / Streaming DD**, with **0 GB phone `.img` overhead**.
  - **9-Layer Anti-Brick Protection:** Adds `512 B` sector guard, multi-namespace `sm unmount`, `4 MiB` head + `2 MiB` tail wipe, silent `losetup` + `rereadpt` fallback, `2 TiB` MBR lock, live disconnect watchdog, and safe-to-unplug write-cache flush.
  - **Wave 5 JVM Unit Tests (`Wave5OtgRufusTest.kt`):** 8th JUnit suite covering fixed-disk GPT/MBR layouts, 2 TiB/4Kn guards, and `sm` volume isolation.

## Previous Milestone: v4.8.0 — Wave 4 (Compressed Image Engine & Physical USB-C OTG Pen Drive Flasher)
- **Wave 4 Complete (`v4.8.0`, `versionCode = 39`):**
  - **Compressed Image Engine (`CompressedImageEngine.kt`, `build-apk.yml`):** Bundled static ARM64 `libzstd.so` + `lib7zzs.so` background extraction for `.img`/`.iso` `.xz`/`.gz`/`.zst` archives with gzip wrap guard, live 256 MB storage floor monitor, atomic temp-file swap, and mount/combo isolation.
  - **Physical USB-C OTG Pen Drive Flasher (`OtgUsbFlasher.kt`, `UsbBuildService.kt`, `MainActivity.kt`):** 7-layer anti-brick sysfs + `/proc/mounts` lock, clean `vold` unmount gate, raw + streaming compressed flash, `/sys/block/<dev>/stat` progress, Head+Tail / Full SHA-256 read-back verification, and typed `FLASH` confirmation.
  - **Wave 4 JVM Unit Tests (`Wave4FeaturesTest.kt`):** Unit tests for compressed format/size parsing and 10-case OTG anti-brick matrix.
  - *(USB HID BIOS Hotkey Helper omitted per project owner decision.)*

## Previous Milestone: v4.7.2 — Wave 3 (Driver Pack Library & Dual-LUN Quick-Mount Combos)
- **Wave 3 Complete (`v4.7.2`, `versionCode = 38`):**
  - **Storage & Wi-Fi Driver Pack Manager (`DriverPackManager.kt`, `WindowsImageBuilder.kt`, `VentoyImageBuilder.kt`, `SettingsScreen.kt`):** Reusable folder/`.zip`/`.7z` driver library with `.inf` validation, auto-categorization, multi-pack selection in Windows USB & Multi-Boot builders, and Settings library sheet.
  - **Dual-LUN Quick-Mount Combos (`AppPrefs.kt`, `UsbGadgetEngine.kt`, `MainActivity.kt`):** Atomic single-rebind `mountDualLunCombo()`, backup JSON persistence, and Home quick-mount bar + combo creator sheet.
  - **Wave 3 JVM Unit Tests (`Wave3FeaturesTest.kt`):** Unit tests covering Driver Pack sanitization/categorization, Dual-LUN backup round-trip, and Dual-LUN path validation.
  - *(Opt-In Wi-Fi Auto-Connect excluded per project owner request.)*

## Previous Milestone: v4.7.1 — Wave 2 (Multi-Boot Power Features & On-Phone `.img` Explorer)
- **Wave 2 Complete (`v4.7.1`, `versionCode = 37`):**
  - **Linux Persistence Inside Multi-Boot Drives (`VentoyPersistenceHelper.kt`, `VentoyImageBuilder.kt`):** Per-ISO 1–16 GB ext4 `.dat` persistence (`casper-rw` / `persistence.conf`) wired into `ventoy.json` with fail-closed space checks and clean removal.
  - **Boot Menu Aliases, Timeout & Built-In GRUB Themes (`VentoyThemeCatalog.kt`, `VentoyImageBuilder.kt`):** Per-ISO `menu_alias`, `VTOY_MENU_TIMEOUT`, and Stock / PocketBoot Dark / High-Contrast GRUB2 themes preserving all sibling JSON sections.
  - **Per-ISO Multi-Template Windows Tweak Profiles (`VentoyImageBuilder.kt`):** Assign 0 (Stock), 1, or multiple built-in/custom presets per Windows 10/11 ISO selectable at PC boot time.
  - **Direct `.img` File Explorer (`ImageLoopExplorer.kt`, `MainActivity.kt`):** On-phone GPT/MBR/Raw Partition 1 file manager (browse, import, export, mkdir, delete) with mounted-to-PC guard & guaranteed cleanup.
  - **Wave 2 JVM Unit Tests (`VentoyWave2Test.kt`):** 5 new test cases covering Wave 2 engines.

## Previous Milestone: v4.7.0 — Wave 1 (Background Build Service, Live Telemetry, In-App Updater & JVM CI Tests)
- **Wave 1 Complete (`v4.7.0`, `versionCode = 36`):**
  - **Foreground USB Build Service (`UsbBuildService.kt`, `NotificationHelper.kt`, `MainActivity.kt`):** App-scoped `UsbBuildManager` + `UsbBuildService` (`specialUse` FGS + `PARTIAL_WAKE_LOCK`) with live notification, notification Cancel button (`UsbBuildActionReceiver`), completion notification, and compact Home progress banner.
  - **Upgraded Live USB Transfer Telemetry (`MainActivity.kt`):** Tracks session transferred bytes and peak MB/s on top of existing `readUsbActivitySample()` polling.
  - **In-App Release Update Checker (`AppUpdateChecker.kt`, `SettingsScreen.kt`, `DownloadScreen.kt`):** Fixed repo URLs to `https://github.com/bilered/PocketBoot` (`DISTROS_MANIFEST_URL`, GitHub button, and GitHub Releases API updater with SemVer check + release notes dialog).
  - **Automated JVM Unit Tests & CI Gate (`app/src/test`, `app/build.gradle.kts`, `build-apk.yml`):** 4 JUnit test classes (`DiskPartitionerTest`, `WindowsCustomizationTest`, `AppPrefsBackupTest`, `FilenameAndVersionTest`) running via `gradle testDebugUnitTest` before `assembleDebug`.
- **Upcoming Planned Waves:**
