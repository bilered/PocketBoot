## v4.11.0 (2026-11-28) — Wave 7: Production-Readiness Security, USB Safety, UI/UX Accessibility, Lifecycle & CI Hardening
- **Critical Build, Concurrency & Runtime Fixes (`UsbBuildService.kt`, `UsbKeepAliveService.kt`, `FastIsoDownloader.kt`, `OtgUsbFlasher.kt`):**
  - Fixed `UsbBuildService.onTimeout()` to call `UsbBuildManager.cancelActiveBuild(this)` and sequenced child-process termination ahead of coroutine job cancellation.
  - Hardened `UsbKeepAliveService` `START_STICKY` null-intent restarts to restore persisted `keepAdbActive` from `AppPrefs` and live mounted LUN status from `UsbGadgetEngine.getStatus()`.
  - Upgraded `FastIsoDownloader.chunkCursors` to `AtomicLongArray` and made `.pbmeta` sidecar persistence atomic via sibling `.tmp` + `renameTo` (`writeSidecarAtomically`).
  - Verified `sync` + `blockdev --flushbufs` exit codes before reporting `"Safe to unplug"` in `OtgUsbFlasher`, and displayed kernel `major:minor` + logical sector size (`512 B`) in the typed `FLASH` confirmation dialog.
- **UI/UX Excellence, WCAG 2.1 AA Accessibility & Compose Performance (`Theme.kt`, `MainActivity.kt`, `DownloadScreen.kt`, `SystemTweaksScreen.kt`, `SettingsScreen.kt` + 6 sheets):**
  - Recalibrated `SurfaceMode.LIGHT`, `DARK`, and `AMOLED` semantic palette tokens so all text/status pairs exceed **WCAG 2.1 AA (`>= 4.5:1`)** (guarded by a new JUnit contrast-ratio test).
  - Upgraded `Modifier.bounceClick` with `LocalIndication.current` ripple/focus indication and `Role.Button` semantics, added reusable `PocketStatusBadge` and `PocketBanner` primitives, removed all 9 decorative gradients and both raw `⚡` emojis, raised all undersized interactive `IconButton`s to `44.dp`, and lifted all sub-`11.sp` text to `>= 11.sp`.
  - Migrated `DownloadScreen` and `SystemTweaksScreen` to keyed `LazyColumn`s, persisted primary navigation/filter/search state with `rememberSaveable`, and added `BackHandler` navigation back to Home.
- **CI Reproducibility (`.github/workflows/build-apk.yml`):**
  - Pinned official Ventoy asset bundling to immutable `v1.1.18` (replacing floating `releases/latest`).
- **Centralized Root-Shell & URL Security (`ShellSafety.kt`):**
  - Consolidated all 12 duplicated `shellQuote()` implementations into pure-JVM testable `ShellSafety.shellQuote()` alongside strict path-containment (`isCleanAbsolutePath`, `isStrictChildPath`), exact/dot-bounded host validation (`isHostOrSubdomain`), URL validation (`isValidHttpUrl`), and trusted-host HTTPS-to-HTTP redirect downgrade blocking (`isForbiddenHttpsDowngrade`).
- **10-Layer Physical OTG Identity Lock (`OtgUsbFlasher.kt`):**
  - Captured kernel `major:minor` (`/sys/block/<dev>/dev`) during OTG scanning and upgraded pre-wipe revalidation (`matchesVerifiedOtgTarget`) to require exact match across `devName`, canonical `sysfsRealPath`, `devMajorMinor`, `logicalBlockSize == 512`, writable state, exact `sizeBytes`, `vendor`, and `model` so hot-swapping another USB drive into the same port fails closed.
- **Root Shell & Archive Safety (`DriverPackManager.kt`, `UsbGadgetEngine.kt`, `VentoyImageBuilder.kt`, `FastIsoDownloader.kt`):**
  - Stripped symlinks after `7zzs` driver-pack extraction, enforced a `2 GiB` extracted driver-pack cap and non-dereferencing copy (`cp -rPf`), and guarded `deletePack()` with `ShellSafety.isStrictChildPath()`.
  - Quoted all 11 ConfigFS/UDC/LUN paths in `UsbGadgetEngine.getStatus()`.
  - Verified tarball extraction exit status and canonical path containment in `VentoyImageBuilder.downloadAndInstallVentoyEngine()`, and quoted remaining shell variables.
  - Added a `32 GiB` streaming cap to `FastIsoDownloader.extractImageFromZipArchive()`, verified extracted destination byte count **before** deleting source `.zip`, and blocked HTTPS-to-HTTP redirect downgrades from trusted catalog hosts.
- **Backup, Updater & Android 14/15 Lifecycle Hardening (`AppPrefs.kt`, `AppUpdateChecker.kt`, `UsbBuildService.kt`, `UsbKeepAliveService.kt`, `FastIsoDownloader.kt`):**
  - Added `2 MiB` backup JSON cap and path/URL scheme sanitization in `AppPrefs.importBackupJson()`.
  - Enforced post-redirect HTTPS and strict `github.com`/`githubusercontent.com` host allowlisting in `AppUpdateChecker`.
  - Replaced unbounded `wakeLock.acquire()` calls with bounded timeouts (`2h` builds/downloads, `6h` keep-alive) and added Android 15 `onTimeout(startId, fgsType)` handlers to `UsbBuildService` and `UsbKeepAliveService`.
- **UI Design-System Polish, ProGuard & CI Hardening (`Theme.kt`, `MainActivity.kt`, `SettingsScreen.kt`, `app/proguard-rules.pro`, `app/build.gradle.kts`, `.github/workflows/build-apk.yml`):**
  - Replaced the last 2 hardcoded `Color(0xFF09090B)` literals outside `Theme.kt` with semantic `PocketColors.onBrightSurface`.
  - Added `app/proguard-rules.pro` preserving `@JavascriptInterface` bridges and `libsu`, configured `lint` in `app/build.gradle.kts`, added least-privilege `permissions: contents: read`, enforced `test -x` on **all 10** static ARM64 binaries + `test -s` on boot assets, added `lintDebug` and APK SHA-256 logging in CI.
- **Wave 7 JVM Unit Tests (`Wave7ProductionReadinessTest.kt`):**
  - Added 10th JUnit suite covering `ShellSafety`, `DriverPackManager.isSafeChildOfLibrary`, `OtgUsbFlasher.matchesVerifiedOtgTarget`, `AppPrefs.importBackupJson` adversarial input rejection, and `AppUpdateChecker.isTrustedReleaseUrl`.

## v4.10.0 (2026-11-27) — Wave 6: NTriver Direct Windows Archive & Downloader Hardening
- **NTriver Direct Windows Archive Engine (`NtriverCatalog.kt`):**
  - Dynamically discovers OS routes from `https://ntriver.org/download-windows-office` (with verified fallback slugs), parses rendered Docusaurus HTML tables, and automatically falls back to raw GitHub Markdown (`ntriver-org/ntriver-org-site` via dynamic tree lookup) if Cloudflare blocks plain HTTP.
  - Automatically prefers permanent **Microsoft Static Links** (`MS Static`) whenever listed on a row before calling `GET https://ntriver.org/api/drive/generate-link?filename=...` (with a strict-SSL headless WebView fallback on browser challenge).
  - Strictly filters non-`.iso` files (`.esd`, `.exe`) and non-bootable clutter (`IA-64`, `ARM32 RT`, `SDK`, `WDK`, `WAIK`, `Symbols`, `Language Packs`, `rg-adguard`), defaults to recommended PC editions with a **"More Editions"** toggle, and surfaces partial-language notes plus a 1-tap **"Switch to previous month"** shortcut.
- **Expanded Windows Catalog & Dual-Source Sheet (`WindowsDirectCatalog.kt`, `WindowsDownloadSheet.kt`, `DownloadScreen.kt`):**
  - Adds **Windows 10 / 11 LTSC**, **Windows 7 SP1**, and **Windows Server 2025 / 2022 / 2019** cards alongside **Windows 11**, **Windows 10**, and **Windows 8.1** with a 2-tab **NTriver Archive / Microsoft CDN** switcher, SHA-256 auto-verification badge, **"Saved"** local-library detection, and NTriver-first long-press quick link copy.
- **Turbo Downloader & Detection Hardening (`FastIsoDownloader.kt`, `WindowsCustomization.kt`, `IsoInspector.kt`, `network_security_config.xml`):**
  - Scoped redirect cookies strictly by hostname (`selectCookiesForHost`) so foreign Cloudflare cookies never leak to external storage mirrors.
  - Added `Mutex`-guarded single-flight expired-URL auto-refresh (`refreshSourceKey` persisted in `.pbmeta`) on sidecar resume, in-memory resume, and mid-download HTTP `401`/`403`/`404`/`410`, with fail-closed `Content-Length` verification.
  - Switched range probing to verified `GET bytes=0-32767` (`206 Partial Content`) checks and enforced strict HTTPS on `ntriver.org`.
  - Extended filename OS/architecture detection for `windows_7`, `windows_8`, `_x32`, and `serverdc` RTM filenames.
- **Wave 6 JVM Unit Tests (`Wave6NtriverTest.kt`):**
  - Added 9th JUnit suite covering index discovery, HTML & Markdown table parsing, `.esd`/clutter filtering, `generate-link` JSON validation, host-scoped cookie isolation, CDN fail-closed guard, and RTM filename detection.

## v4.9.0 (2026-11-27) — Wave 5: Direct-Mode USB-C OTG Upgrade (4 Modes)
- **Fixed-Disk Physical Partition Engine (`DiskPartitioner.kt`):**
  - Added `calculateFixedDiskLayout()`, `calculateFixedVentoyLayout()`, `calculateFixedLinuxFat32Layout()`, `maxLinuxPersistenceGb()`, `findPersistenceSlotIndex()`, and `effectiveSchemeForDisk()` utilizing 100% of a physical pen drive's capacity, placing Backup GPT at exact `totalSectors - 1`, and enforcing a strict `2 TiB` MBR 32-bit LBA guard (auto-locking `> 2 TiB` USB SSDs to GPT).
- **9-Layer Anti-Brick OTG Hardening (`OtgUsbFlasher.kt`):**
  - Added `512 B` logical sector-size verification (`queue/logical_block_size`, rejecting `4Kn` enclosures), multi-namespace Android `sm unmount` (`parseSmPublicVolumeIds`) before `vold` unmount, `4 MiB` header + `2 MiB` tail wipe, silent `losetup`-over-block partition slicing with `blockdev --rereadpt` fallback, live USB-yank disconnect detection, and a safe-to-unplug write-cache flush gate.
- **4-Mode Direct USB-C OTG Flasher (`OtgUsbFlasher.kt`, `WindowsImageBuilder.kt`, `LinuxImageBuilder.kt`, `VentoyImageBuilder.kt`, `UsbBuildService.kt`, `MainActivity.kt`):**
  - **Mode 1 — Direct Windows USB:** Writes GPT/MBR + NTFS + UEFI:NTFS directly to `/dev/block/sdX` with Edition Picker, offline `boot.wim` `LabConfig` bypass, full Windows Experience & System Tweaks (`WindowsTwoPaneCustomizer` + saved presets), `ei.cfg`, CA 2023 bootloaders, and **Driver Pack Library** injection into `/Drivers` using **0 GB phone `.img` storage**.
  - **Mode 2 — Direct Linux + Persistence:** Writes Extracted FAT32 (with auto `grub.cfg`/`syslinux.cfg` label & `persistent` boot-flag patching) or Hybrid DD directly to `/dev/block/sdX` with dynamic ext4 persistence (`writable`, `casper-rw`, `persistence`) scaling up to the pen drive's real remaining space.
  - **Mode 3 — Direct Multi-Boot Pen Drive:** Installs full-capacity Multi-Boot (GPT/MBR, exFAT/NTFS) + optional initial ISO + Windows tweaks & WinPE driver packs directly onto a physical USB pen drive.
  - **Mode 4 — Raw / Streaming DD:** Preserves raw `.img`/`.iso` and streaming `.xz`/`.gz`/`.zst` flashing with Head+Tail / Full SHA-256 read-back verification.
- **Wave 5 JVM Unit Tests (`Wave5OtgRufusTest.kt`):**
  - Added tests verifying exact physical-end Backup GPT headers, `2 TiB` MBR guard, `32 MiB` Multi-Boot EFI placement, dynamic Linux persistence sizing, `4Kn` sector rejection, and exact `(major:minor)` `sm` public volume isolation.

## v4.8.0 (2026-11-27) — Wave 4: Compressed Image Engine & Physical USB-C OTG Pen Drive Flasher
- **Compressed Image Engine (`CompressedImageEngine.kt`, `IsoInspector.kt`, `UsbGadgetEngine.kt`, `BrowseAndBrowserSheets.kt`, `build-apk.yml`):**
  - Cross-compiled and bundled static ARM64 **`zstd`** (`libzstd.so`, stripped ELF, zero `PT_INTERP`) in GitHub Actions CI alongside `lib7zzs.so`.
  - Added full detection and on-phone background extraction (`UsbBuildKind.DECOMPRESS`) for `.img.xz`, `.img.gz`, `.img.zst`, `.iso.xz`, `.iso.gz`, and `.iso.zst` images with uncompressed-size probing, gzip 32-bit `ISIZE` wrap detection, live `256 MB` phone storage floor guard, atomic temp-file replacement, optional post-verify archive deletion, and auto-mount.
  - Enforced strict isolation blocking compressed archives from direct ConfigFS LUN mounting, Dual-LUN Combos, and `.img` File Explorer.
- **Physical USB-C OTG Pen Drive Flasher (`OtgUsbFlasher.kt`, `UsbBuildService.kt`, `MainActivity.kt`):**
  - Added direct raw (`dd bs=4M conv=fsync`) and streaming compressed flashing (`UsbBuildKind.OTG_FLASH`) onto physical USB-C OTG pen drives directly from **Home → OTG Flash** or any file's tap sheet.
  - Built a **7-Layer Anti-Brick Hardware Safety Lock** verifying whole-disk `sd[a-z]+`, strict canonical sysfs `/usb[0-9]+/` bus path + internal UFS/eMMC/NVMe/loop/dm/zram denylist, `/proc/mounts` system-path cross-check, writable & minimum capacity check, source-fit check, ConfigFS mutual-exclusion guard, clean Android `vold` unmount gate, and typed **`FLASH`** confirmation.
  - Added Toybox-universal `/sys/block/<dev>/stat` progress polling and post-flash read-back verification (Head `16 MB` + Tail `16 MB` or optional Full SHA-256).
- **Wave 4 JVM Unit Tests (`Wave4FeaturesTest.kt`):**
  - Added comprehensive tests covering `.xz`/`.gz`/`.zst` format & size parsing, gzip wrap detection, Dual-LUN compressed-file rejection, and a 10-case anti-brick sysfs + `/proc/mounts` safety matrix.

## v4.7.2 (2026-11-27) — Wave 3: Driver Pack Library & Dual-LUN Quick-Mount Combos
- **Storage & Wi-Fi Driver Pack Manager (`DriverPackManager.kt`, `WindowsImageBuilder.kt`, `VentoyImageBuilder.kt`, `SettingsScreen.kt`):**
  - Added a reusable **Driver Pack Library** (`<defaultDir>/PocketBoot_Drivers/`) reachable from **Settings → Driver Pack Library** and directly inside **Windows USB Builder** and **Multi-Boot Builder / Manager**.
  - Import packs from extracted folders or `.zip`/`.7z`/`.tar`/`.gz` archives (using bundled `lib7zzs.so`) with automatic `.inf` validation (fail-closed if zero `.inf` files exist), automatic category tagging (**Intel VMD/RST**, **AMD RAID**, **Wi-Fi / LAN**, **Custom**), file/size stats, and one-tap deletion.
  - Select multiple saved driver packs at once in **Windows USB Builder** (merged cleanly into `/Drivers/<pack>/`) and **Multi-Boot Builder / Manager** (merged into verified `drivers_inject.zip` alongside `VentoyAutoRun.bat`).
- **Dual-LUN Quick-Mount Combos (`AppPrefs.kt`, `UsbGadgetEngine.kt`, `MainActivity.kt`):**
  - Added `DualLunCombo` presets persisted in `AppPrefs` and included in **Backup & Restore JSON** (`pocketbootBackupVersion = 4`, backward-compatible).
  - Added `UsbGadgetEngine.mountDualLunCombo()` to configure both `lun.0` and `lun.1` in a **single atomic USB ConfigFS controller rebind** (avoiding double PC disconnect/reconnect chimes), with path validation and clean fallback when a kernel lacks LUN 1 support.
  - Added the **Dual-LUN Combos** bar and creator/manager sheet on the Home tab for 1-tap combo mounting.
- **Wave 3 JVM Unit Tests (`Wave3FeaturesTest.kt`):**
  - Added unit tests covering `DriverPackManager` slug sanitization & category detection, `DualLunCombo` backup JSON round-trip, and `UsbGadgetEngine.validateDualLunComboPaths()`.

## v4.7.1 (2026-11-27) — Wave 2: Multi-Boot Power Features & On-Phone `.img` Explorer
- **Linux Persistence Inside Multi-Boot Drives (`VentoyPersistenceHelper.kt`, `VentoyImageBuilder.kt`):**
  - Attach ext4 `.dat` persistence volumes (`1–16 GB`, Ubuntu/Mint `casper-rw` or Debian/Kali `persistence` with `/ union` `persistence.conf`) to any Linux ISO inside a Multi-Boot drive (`/ventoy/persistence/*.dat` + `ventoy.json` `"persistence"` array) with fail-closed Partition 1 & host storage checks, clean loop teardown, and one-tap removal.
- **Boot Menu Aliases, Timeout & Built-In GRUB Themes (`VentoyThemeCatalog.kt`, `VentoyImageBuilder.kt`):**
  - Edit per-ISO boot menu display names (`menu_alias`), configure auto-boot menu timeout (`VTOY_MENU_TIMEOUT`), and switch between **Stock Default**, **PocketBoot Dark**, and **High-Contrast** GRUB2 themes while preserving all sibling `ventoy.json` sections (`control`, `auto_install`, `injection`, `persistence`).
- **Per-ISO Multi-Template Windows Tweak Profiles (`VentoyImageBuilder.kt`):**
  - Upgraded `pocketboot_meta.json` (`isoProfileIds` + `savedCustomProfiles`, backward-compatible) so each eligible Windows 10/11 ISO can be set to **Stock**, assigned a single built-in/custom preset, or given **multiple boot-menu templates** (`autosel: 0, timeout: 10`) selectable directly at PC boot time.
- **Direct `.img` File Explorer (`ImageLoopExplorer.kt`, `MainActivity.kt`):**
  - Browse, import files from phone storage, export files to phone storage, create folders, and delete entries inside Partition 1 of any `.img` (GPT, MBR, or raw superfloppy) with mounted-to-PC guard, exact copy verification, and guaranteed `unmountAndDetachClean()` teardown on close or dispose.
- **Wave 2 JVM Unit Tests (`VentoyWave2Test.kt`):**
  - Added 5 unit test cases validating `VentoyThemeCatalog` JSON preservation, `VentoyPersistenceHelper` path sanitization, multi-template profile serialization, and `ImageLoopExplorer` GPT/MBR/Raw partition slicing + folder name sanitization.

## v4.7.0 (2026-11-27) — Wave 1: Background Build Reliability, Telemetry, Updater & CI Test Gate
- **Foreground USB Build Service (`UsbBuildService.kt`, `NotificationHelper.kt`, `MainActivity.kt`, `AndroidManifest.xml`):**
  - Moved Windows, Linux, and Multi-Boot image builds into a dedicated app-scoped `UsbBuildManager` + `UsbBuildService` (`foregroundServiceType="specialUse"`, `PARTIAL_WAKE_LOCK`, fail-safe `stopSelf()` on `startForeground()` error) so switching apps or locking the phone never kills active USB builds.
  - Added live build progress notifications (`BUILD_NOTIF_ID = 1004`), one-tap **Cancel** notification action (`UsbBuildActionReceiver`), completion alert (`BUILD_DONE_NOTIF_ID = 1005`), and a compact live progress banner on the Home tab whenever a build runs in the background.
- **Upgraded Live USB Transfer Telemetry (`UsbGadgetEngine.kt`, `MainActivity.kt`):**
  - Fixed `readUsbActivitySample()` to discover the `file-storage` kernel thread directly via `/proc/[0-9]*/comm` (Android Toybox `pidof`/`pgrep` skips kernel threads), eliminated `rchar + read_bytes` double-counting, added `file-storage` CPU tick + hardware USB controller IRQ (`/proc/interrupts`) fallbacks for GKI kernels, updated live Link Speed dynamically, and displayed **Transfer Stats** reliably whenever connected.
- **Fixed Blank `.img` Auto-Mount, Partial Unmount & Multi-Boot Sparse Reclaim (`UsbGadgetEngine.kt`, `VentoyImageBuilder.kt`):**
  - Wrapped `set -e` inside subshells `( set -e ... )` in `createBlankImage()` and `packDriversInjectionOnMounted()` and added defensive `set +e` to `mountImage()`/`unmountImage()`, preventing libsu's persistent root shell from aborting auto-mount right after locking ConfigFS.
  - Fixed ConfigFS `[ -s ".../lun.X/file" ]` false-positive on empty 4096-byte sysfs nodes in `unmountImage()` and `mountImage()` so deleting/unmounting a single LUN cleanly restores normal USB.
  - Added an Android-universal pure-Kotlin `RandomAccessFile` sparse hole-puncher (`reclaimViaKotlinSparseCopy`) + already-compact fast-path and host free-space guard in `reclaimSparseSpace()`, eliminating failures on Toybox ROMs lacking `fallocate -d` and `cp --sparse=always`.
- **In-App Release Update Checker & Repo Link Fix (`AppUpdateChecker.kt`, `SettingsScreen.kt`, `DownloadScreen.kt`):**
  - Fixed all repository URLs to point to **`https://github.com/bilered/PocketBoot`** (Settings GitHub link, release checker API, and `DISTROS_MANIFEST_URL` raw catalog URL).
  - Added **Settings → About → Updates** with HTTPS-enforced GitHub Releases checking, SemVer comparison, scrollable release notes preview, and one-tap **Download APK** / **Open Release** action.
- **Automated JVM Unit Test Suite & CI Gate (`app/src/test`, `app/build.gradle.kts`, `.github/workflows/build-apk.yml`, `WindowsCustomization.kt`):**
  - Added JUnit 4 test suites (`DiskPartitionerTest`, `WindowsCustomizationTest`, `AppPrefsBackupTest`, `FilenameAndVersionTest`), switched `WindowsCustomizationEngine.gzipToBase64()` to `java.util.Base64` for pure JVM compatibility, and wired `gradle testDebugUnitTest --stacktrace` into GitHub Actions ahead of `assembleDebug`.

## v4.6.9 (2026-11-26)
- **Multi-Boot Capacity Guard & Exact Copy Integrity (`VentoyImageBuilder.kt`):**
  - Added live capacity validation in **Multi-Boot Builder** without auto-changing the user's selected size: if the selected sparse capacity is smaller than the initial ISO (+ drivers + filesystem metadata headroom), shows an explicit danger error banner with a clear note telling you the minimum size required for your ISO and disables the Create button until you increase the drive capacity.
  - Added hard pre-flight checks in `buildVentoyImage()` and `addIsoToVentoy()` verifying Partition 1 capacity, Android host free storage, and fail-closed post-format `df -B1` free space before copying.
  - Added exact post-copy byte-count verification (`cpRes.isSuccess && copiedBytes == srcSize`): any truncated/incomplete copy now immediately deletes the partial file (and aborts/cleans up a failed initial build) instead of reporting false success.
- **Conflict-Free Multi-ISO Windows Tweaks (`VentoyImageBuilder.kt`, `IsoInspector.kt`):**
  - Replaced the single hardcoded `amd64` unattend template with per-architecture templates (`/ventoy/scripts/pocketboot_unattend_<arch>.xml` for `amd64`, `x86`, and `arm64`), automatically regenerated whenever ISOs are added, removed, or toggled.
  - Scoped Multi-Boot auto-install tweaks exclusively to **Windows 10 & Windows 11** desktop ISOs (leaving Windows 7, Windows 8.1, Windows Server, and Linux ISOs stock so Setup never hits answer-file schema errors).
  - Added a per-ISO **`Tweaks` / `Stock`** toggle pill on every eligible Windows 10/11 ISO inside **Multi-Boot Manager** (`excludedAutoInstallIsos` persisted in `pocketboot_meta.json`), so tweaked and pure-stock Windows ISOs can coexist cleanly on the same drive.
  - Unified Multi-Boot Windows ISO scanning depth to `-maxdepth 3` (matching Multi-Boot Manager) and expanded `IsoInspector.quickGuessByName()` with `26h1`, `26h2`, `win7`, `win8`, and `server2019` hints.
- **Full Codebase Audit Hardening (`PB-01`–`PB-16`):**
  - **Blank Drive Integrity (`UsbGadgetEngine.kt`):** Hardened `createBlankImage()` with `set -e`, `rm -f`, overwrite-aware free-space checks, exact post-create byte verification (`createdBytes == expectedBytes`), and `IsoInspector` cache invalidation.
  - **Android Toybox Portability (`UsbGadgetEngine.kt`, `WindowsImageBuilder.kt`, `VentoyImageBuilder.kt`):** Replaced all `du -sb | awk` calls with shared Android-universal `queryDirSizeBytes()` (`du -sb` → POSIX `du -sk * 1024`) and failed Windows USB builds loudly if Drivers folder injection fails.
  - **exFAT Resize & Linux DD Safety (`VentoyImageBuilder.kt`, `LinuxImageBuilder.kt`):** Passed `WindowsExperienceConfig.stock()` and cleared `$NEW_MNT/ventoy` before copying during exFAT resize (preventing stale tweaks from leaking into resized stock drives), added exact post-copy byte verification to Linux DD builds, and made Windows/Linux free-space checks overwrite-aware with a `dd` fallback for `truncate`.
  - **Concurrency, Downloads & Security (`MainActivity.kt`, `FastIsoDownloader.kt`, `UsbKeepAliveService.kt`, `WindowsDirectCatalog.kt`, `AndroidManifest.xml`):** Offloaded build cancellation root scripts to `Dispatchers.IO` with `try/finally` job cleanup, hardened ZIP extraction against path traversal (`\`/`..`) and low cache space, added pre-download free-space checks, exited foreground services cleanly (`stopSelf()`) if `startForeground()` fails, sanitized `renameImageFile()` with `IsoInspector` + MediaStore invalidation, filtered Multi-Boot Add-ISO replacement matching to root-level files, read live size in Resize Drive dialog, surfaced driver-pack errors, disabled WebView `allowFileAccess`/`allowContentAccess`, enforced HTTPS strictly on Microsoft/GitHub domains via `network_security_config.xml` + Kotlin protocol guards while preserving HTTP mirror redirects & LAN repos (`GAP-01`), removed unsafe UI `!!` assertions, and upgraded CI actions (`checkout@v5`, `setup-java@v5`).
  - **Second-Pass Audit Closure (`GAP-02`–`GAP-11`):** Added mounted-drive guards to all 8 mutating Multi-Boot operations, verified host free space & exact byte counts during sparse reclaim fallback and NTFS resize, invalidated `IsoInspector` on resize/reclaim, eliminated all remaining `awk` and `sed -i` Toybox dependencies across SHA-256 / USB I/O telemetry / ISO edition & size probes / Linux FAT32 config patching, made build cancellation race-free (`markBuildCancelled()` + `CancellationException` feedback), synced `downloadTargetMultiBootPath` in Settings backup/restore, made Windows/Linux sheet `lowSpace` indicators overwrite-aware, surfaced Multi-Boot driver-pack warnings in build completion feedback, quoted temp symlink test path, removed duplicate HTTP keep-alive header, and walked ancestor dirs in `getFreeSpaceBytes()`.
  - **Third-Pass Audit Closure (`R3-01`–`R3-04`):** Hardened Multi-Boot driver packing (`packDriversInjectionOnMounted`) with exact byte verification, partial-file removal, guaranteed `/data/local/tmp` cleanup, and `ventoy.json` injection gating; added mounted-file collision avoidance, exact byte verification, fail-loud error handling, and `IsoInspector` invalidation to `FastIsoDownloader` finalization; released `TurboDownloadService` WakeLock/WifiLock while paused and replaced blocking `Thread.sleep` with coroutine `delay()` in chunk retries; wired `VentoyManagerSheet` busy state into `FLAG_KEEP_SCREEN_ON` & temp-cache guard and removed unused `moveFileToDir()`.
  - **Fourth-Pass Airtight Closure (`FINAL-01`–`FINAL-07`):** Fixed `inspectWindowsEditions()` to fall back to bundled `7zzs` + `wimlib-imagex` when non-UDF Android kernels mount only the ISO9660 `README.TXT` bridge, and verified `sources/install.*` or `sources/boot.wim` after Windows ISO extraction; added `android:launchMode="singleTop"`, `FLAG_ACTIVITY_SINGLE_TOP`, and `onNewIntent()` to `MainActivity` so Home Screen quick-mount shortcuts never destroy running builds; resolved actual ISO byte size asynchronously before launching `WindowsBuilderSheet` from download completion; eliminated early-cancel WebView leak and added fast-fail `onReceivedError`/`onReceivedSslError` handlers in `WindowsDirectCatalog`; sanitized dynamic API filenames and new-folder inputs against `..`/`\`; preserved paused resumable downloads when starting a new download and removed ~700 lines of dead legacy downloader code; moved synchronous `StatFs` free-space checks in Compose sheets off the main thread onto `Dispatchers.IO`.

## v4.6.8 (2026-11-26)
- **Dedicated Full-Screen System Tweaks Screen (`SystemTweaksScreen.kt`, `WindowsCustomization.kt`, `AppPrefs.kt`):**
  - Replaced the nested bottom sheet with an edge-to-edge full-screen **System Tweaks** screen featuring collapsible sections with live active counts, an **"Active Only"** filter chip, unsaved-changes Back guard, inline `.cmd` preview (`Specialize.cmd`, `SetupComplete.cmd`, `Undo.cmd`), and a subtle first-time hint banner.
  - Added a live **Search Bar with Persistent Search History** (up to 8 recent queries stored in `AppPrefs.kt`, quick starter suggestion chips when empty, per-chip `×` removal, and Clear History).
  - Added a small **`ⓘ` Info Dialog** on every tweak explaining exact registry paths, services, scheduled tasks, impact level (`Safe` / `Caution` / `Removes Component`), and side effects.
  - Added **Custom System Tweaks Presets** (`+ Save Preset`, tap-to-apply, `×` delete, backed up in Settings Export/Import JSON v3) plus a live preset match / `(Modified)` badge.
  - Added an optional **"Add Undo Script"** badge toggle (OFF by default; excluded from active count and preset-modified status): when checked with active tweaks, drops an auto-elevated `Undo_PocketBoot_Tweaks.cmd` onto `%PUBLIC%\Desktop\`; when unchecked, drops nothing.
- **1:1 System Tweaks Parity, Official Presets & Pane 1 Cleanup (`WindowsCustomization.kt`, `SystemTweaksScreen.kt`):**
  - Expanded offline script catalog to 72 tweaks (DNS section, Aggressive WU Disable, and Block WU Driver Installs omitted): **Essential (21)** including an expandable **AppX Debloat Sub-Picker** (keep individual apps while removing the rest), **Customize Preferences (21)** including a 3-state **Multiplane Overlay (MPO)** selector (`Default`, `Disable MPO`, `Force Enable`), **Advanced — Caution (29)**, and **Performance Plans (1)**.
  - Added exact upstream **`Standard`**, **`Minimal`**, and **`Advanced`** presets alongside updated **`Desktop`** and **`Laptop`** presets built on the `Standard` baseline.
  - Moved **Classic Windows 10 Right-Click Menu** out of Pane 1 into **System Tweaks → Advanced** and auto-migrated legacy saved presets and Multi-Boot metadata cleanly (zero ghost counts).
- **Store-Safe Windows Update Pause, Security-Only Deferral & Windows Security (`WindowsCustomization.kt`, `SystemTweaksScreen.kt`):**
  - Added **Disable Windows Update (Store-Safe)**: sets dynamic install-UTC StartTime + `2099-12-31T23:59:59Z` EndTime/ExpiryTime + `FlightSettingsMaxPauseDays=30000` in `WindowsUpdate\UX\Settings` (Home & Pro) + `Policies\WindowsUpdate\AU` (`NoAutoUpdate=1`, `AUOptions=2`, `NoAutoRebootWithLoggedOnUsers=1`) while preserving Microsoft Store, Xbox Game Pass, Optional Features, and Windows Update driver installs.
  - Added **Security-Only Windows Updates** (365-day Feature / 4-day Quality deferral, mutually exclusive with Disable Windows Update) and **Disable Windows Security (Defender)** policies with clear `ⓘ` Tamper Protection guidance.
- **Reliability & Multi-Boot Parity Hardening (`WindowsCustomization.kt`, `WindowsImageBuilder.kt`):**
  - Upgraded Multi-Boot self-contained unattend XML to GZip-compress and chunk `PocketBoot_Specialize.cmd`, `SetupComplete.cmd`, and optional `Undo_PocketBoot_Tweaks.cmd` into short (~1,200-char) stagers, preventing 8 KB command-line overflows and giving Multi-Boot full parity for Restore Point, Cleanup, and Undo Script.
  - Wired `winUtil` into `buildSetupCompleteScript()` with headless DISM cleanup (no Session-0 `cleanmgr` hang) and VSS-ready Restore Point creation (`SystemRestorePointCreationFrequency` throttle bypass).
- **Clean Attribution (`SettingsScreen.kt`, `WindowsCustomization.kt`, `WindowsImageBuilder.kt`):**
  - Scrubbed all remaining user-facing `WinUtil` strings from UI and logs; credited **Chris Titus Tech WinUtil (MIT)** exclusively inside **Settings → Open-Source Credits**.

## v4.6.7 (2026-11-26)
- **Fixed Windows Setup "Install driver to show hardware" & Missing Drive on Auto-Mount (`MainActivity.kt`, `WindowsImageBuilder.kt`, `VentoyImageBuilder.kt`, `BuildThermalHelper.kt`):**
  - Switched post-build auto-mount for Windows USB and Multi-Boot drives (and Multi-Boot recommended mount mode) from Read-Only USB (`USB_RO`) to **Writable USB (`USB_RW`)** so Windows/WinPE `ntfs.sys` can cleanly mount fresh Linux-created NTFS volumes.
  - Added shared `BuildThermalHelper.unmountAndDetachClean()` clean-flush unmount loop (up to 10 × 1 s `sync` + real `umount` retries before lazy fallback, loop detach, and final `sync`) across Windows, Linux, and Multi-Boot builders (with `\040` space-escaped `/proc/mounts` matching) to prevent unflushed NTFS handoffs.
  - Updated auto-mount switch subtitles across Windows, Multi-Boot, and Linux builders (Linux now accurately reflects RW vs. RO based on persistence mode).
- **Hidden `UEFI:NTFS` Partition in Windows Explorer (`DiskPartitioner.kt`):**
  - Updated GPT Partition 2 (`UEFI:NTFS`) attributes from `0x9000000000000000` to **`0xD000000000000001`** (`PLATFORM_REQUIRED | READ_ONLY | HIDDEN | NO_DRIVE_LETTER`) so the 1 MB helper partition no longer appears as an extra drive letter (`G:`) in Windows Explorer while remaining UEFI-bootable and ignored by Windows Setup.
- **Hybrid 1-Line Editable Volume Labels & Multi-Boot Polish (`IsoInspector.kt`, `WindowsImageBuilder.kt`, `LinuxImageBuilder.kt`, `VentoyImageBuilder.kt`):**
  - **Windows USB:** Shortened default NTFS label to 1-line Explorer-safe **`POCKETBOOT-WIN 11`** (`WIN 10`, `WIN 8.1`, `WIN 7`, `WIN SRV`; FAT32 `PB-WIN11`) so Windows 11 Tiles view always displays free/total drive capacity, with an editable **USB Volume Label** box, >20-char wrap warning, and automatic WIM-edition OS upgrade for generic ISO filenames.
  - **Multi-Boot:** Changed default filename and NTFS label to **`POCKETBOOT Multi ISO`** (`PB MultiISO` on exFAT), added editable **USB Volume Label** box, added `-c 4096` to NTFS formatting, preserved custom labels during Resize Path B via Toybox-compatible `blkid` parsing (with PID-tracked cancel & clean flush), fixed untouched `Auto Select` exFAT default (`PB MultiISO`), cleaned partial `.img` on build error, and updated `IsoInspector.quickGuessByName()` to recognize `"multi iso"` / `"multi boot"` immediately.
  - **Linux & Tool (Extracted FAT32):** Defaulted FAT32 volume label to the **original ISO-9660 disc label trimmed to 11 chars** (editable), added case-insensitive boot-config label syncing (`CDLABEL=`, `root=live:LABEL=`, `archisolabel=`, `misolabel=`, and GRUB `search -l/--label`), deduplicated `persistent`/`persistence` injection across case-insensitive `.cfg` files, and wrote standard LBA CHS (`FE FF FF`) MBR entries via `DiskPartitioner.buildLinuxFat32MbrSector()`.
- **Native Setup Preserved with Full Unattended OOBE + Edition Choice (`WindowsImageBuilder.kt`, `WindowsCustomization.kt`):**
  - Routed `Full Unattended OOBE Skip` + `"Ask during Setup"` strictly through `Panther\unattend.xml` (no `windowsPE` pass), keeping *"Previous version of Setup"*, Edition picker, and *"I accept"* while skipping all OOBE screens; pre-selecting an edition chip still injects `/Autounattend.xml` into `boot.wim`.
- **App-Wide Brand Scrub (`MainActivity.kt`, `WindowsImageBuilder.kt`, `WindowsCustomization.kt`, `LinuxImageBuilder.kt`, `VentoyImageBuilder.kt`):**
  - Scrubbed all user-facing `IDM`, `Ventoy`, and `Rufus` branding from UI text, badges, progress dialogs, and Activity Log messages app-wide while keeping open-source attributions exclusively in **Settings → Open-Source Credits**.

## v4.6.6 (2026-11-25)
- **Versioned USB Volume Labels (`WindowsImageBuilder.kt`):**
  - Probed Windows version and exact `BUILD.SPBUILD` from the source ISO's WIM XML header (with filename and PID-tracked `boot.wim` fallbacks) to label NTFS Partition 1 as **`POCKETBOOT- Win 11 -26300.9974`** (up to 32 chars; FAT32 fallback `PB11-26300`).
- **Exact Rufus OOBE & Local Admin Account Parity (`WindowsCustomization.kt`):**
  - Decoupled `Bypass Online Microsoft Account` from `HideWirelessSetupInOOBE` (so turning OFF *Skip Wi-Fi Setup* is respected) and gated `HideLocalAccountScreen` + `HideOnlineAccountScreens` strictly to pre-created local accounts (preventing OOBE dead-ends when *Full Unattended OOBE Skip* is enabled without a username, with an inline UI tip).
  - Added `<HideLocalAccountScreen>true</HideLocalAccountScreen>` and `<FirstLogonCommands>` (`net accounts /maxpwage:unlimited`, disabling 42-day password expiry) for pre-created Local Admin accounts, with strict alphabetical XSD child element ordering across `Microsoft-Windows-Shell-Setup`, `<OOBE>`, `<AutoLogon>`, `<FirstLogonCommands>`, and `<LocalAccount>`.
- **Single-Pass `boot.wim` Update, Tracked Cancel & Accurate Preview (`WindowsCustomization.kt`, `WindowsImageBuilder.kt`):**
  - Unified `LabConfig` hive patching and `/Autounattend.xml` injection into a single `wimlib-imagex update` pass (`patchBootWimSnippet`), fixed the Unattended Mode fallback so replacement `Autounattend.xml` (with `LabConfig`) cleanly overwrites `/Autounattend.xml` in `boot.wim` if `reged` fails, and updated the Activity Log to `Part2 UEFI:NTFS`.
  - Routed Win 7 UEFI, CA 2023 bootloaders (with `sources/boot.wim` Index 2 fallback), Step 3 label-probe `7zzs`, and `/Drivers` `cp -rf` through `BuildThermalHelper.runIsolatedRootScript(..., trackPid = true)` with instant cancel checks.
  - Passed `selectedEditionIndex` into `WindowsTwoPaneCustomizer` so **Preview XML** accurately displays `boot.wim\Autounattend.xml`, `Panther\unattend.xml`, or a clean Pure Stock note.

## v4.6.5 (2026-11-25)
- **Fixed Windows Setup `PrepareSystemVolume` (`0x80004005`) Failure (`DiskPartitioner.kt`):**
  - Changed Windows USB GPT Partition 2 (`UEFI:NTFS`) from `GUID_ESP` (`Type: System`) to **Microsoft Basic Data Partition (`GUID_BASIC_DATA`)** with GPT attributes `0x9000000000000000` (`NO_DRIVE_LETTER | READ_ONLY`) and name `UEFI:NTFS`, matching official Rufus byte-for-byte.
  - Resolves *"Windows could not prepare the computer to boot into the next phase of installation"* across both Windows 11 Setup and *"Previous version of Setup"*, while keeping `GUID_ESP` explicitly enabled for Ventoy `VTOYEFI`.
- **Core Rufus Engine Parity (`WindowsCustomization.kt`, `WindowsImageBuilder.kt`):**
  - Exported `TMPDIR`, `HOME`, and `WIMLIB_IMAGEX_IGNORE_CASE=1` in all offline `wimlib-imagex` helpers (`boot.wim` LabConfig patch, `Autounattend.xml` injection, CA 2023 bootloaders, Win 7 UEFI fix) and added `>= 4096 B` hive integrity checks before/after `reged`.
  - Injected `/Autounattend.xml` directly into `sources/boot.wim` Index 2 root (official Rufus method) whenever WinPE unattended mode is needed (pre-selected edition, Skip OOBE, or LabConfig fallback), eliminating duplicate USB-root + `Panther/unattend.xml` conflicts.
  - Added `WindowsCustomizationEngine.sanitizeWindowsUsername()` (Rufus `#2991` parity) to strip `&` and Windows-illegal characters and reject reserved names (`Administrator`, `Guest`, `CON`, `NUL`, etc.) so OOBE account creation never fails.
  - Updated CA 2023 bootloader patching to also replace `efi/microsoft/boot/bootmgfw.efi` when present, and automatically enforced CA 2023 bootloaders whenever `Apply SkuSiPolicy.p7b` is enabled.
- **Filesystem & OEM Compatibility (`WindowsImageBuilder.kt`):**
  - Added explicit 4 KB cluster sizing (`mkntfs -c 4096`) and preserved the source ISO's PVD volume label (fallback `POCKETBOOT`) when formatting Partition 1 on NTFS and FAT32.
  - Upgraded `sources/ei.cfg` unlock template to `[EditionID]` (blank) + `[Channel] Retail` + `[VL] 0` so OEM Home-keyed laptops reliably show all Windows editions.
- **UI/UX Transparency (`WindowsCustomization.kt`, `WindowsImageBuilder.kt`):**
  - Added a live Setup Mode pill in `WindowsBuilderSheet` (*Pure Stock ISO*, *Rufus Native Mode*, or *Unattended Mode*), a one-tap **"Stock (No Tweaks)"** chip, live reserved-username error feedback, CA 2023 ↔ `SkuSiPolicy` switch interlocking, dynamic Preview XML tab title, and wired-Ethernet OOBE guidance on `Bypass Online Microsoft Account`.

## v4.6.4 (2026-11-25)
- **True Official Rufus Parity in Windows USB Builder (`WindowsImageBuilder.kt`, `WindowsCustomization.kt`, `build-apk.yml`):**
  - Re-bundled static ARM64 `libchntpw.so` (`reged-static`) to patch the 5 `Setup\LabConfig` hardware bypass keys directly into `sources/boot.wim` Index 2 offline `SYSTEM` hive.
  - Stopped writing USB-root `autounattend.xml` for standard builds—staging OOBE/Specialize tweaks solely in `sources/$OEM$/$$/Panther/unattend.xml` (`includeWindowsPePass = false`). Restores ***"Previous version of Setup"***, Product Key, Edition picker, and the ***"I accept"*** License Terms screen in Windows 11 24H2/25H2/26H2 Setup.
  - Defaulted `qolImprovements` to `false` and added `needsSpecializeScript()` so standard Rufus-style builds inject pure XML with zero `.cmd` scripts (`SetupComplete.cmd` / `PocketBoot_Specialize.cmd` are only written when QoL, Classic Menu, Sponsored Bloat, WinUtil, or a Custom Script is opted in).
  - Scoped `<HideEULAPage>true</HideEULAPage>` strictly to `skipOobe`, wired bundled `libmkfs_fat.so` into the FAT32 fallback, tracked PID on loop-mount fallback copy, updated Preview XML & Pre-Select Edition helper text, and removed dead `WindowsBuildConfig` fields and unused `xmlEscape()`.
- **Diagnostics & Credits (`BuildThermalHelper.kt`, `DiagnosticsSheet.kt`, `SettingsScreen.kt`):**
  - Added `reged` (`libchntpw.so`) check to **Pre-Flight Diagnostics → Windows Imaging Tools** and credited `chntpw / reged (GPLv2)` in Open-Source Credits.

## v4.6.3 (2026-11-25)
- **Android 15 Lifecycle & Edge-to-Edge Fixes (`AndroidManifest.xml`, `FastIsoDownloader.kt`, all 10 UI files):**
  - Preserved `MainActivity` across orientation/screen-size/keyboard config changes so rotating the phone never kills active USB or Ventoy builds.
  - Added `TurboDownloadService.onTimeout(startId, fgsType)` for Android 15 `dataSync` foreground service compliance (pauses cleanly with `.pbmeta` state saved).
  - Applied `.navigationBarsPadding().imePadding()` to all 12 `ModalBottomSheet`s so primary CTA buttons and text inputs never hide behind 3-button navigation bars or software keyboards.
- **Unified Thermal Control, PID-Tree Cancel & Conflict Guards (`LinuxImageBuilder.kt`, `VentoyImageBuilder.kt`, `MainActivity.kt`, `SettingsScreen.kt`):**
  - Wired `BuildThermalHelper` thermal limits, cooldown checks, and immediate PID-tree process termination on Cancel into both Linux and Ventoy builders.
  - Blocked concurrent USB/Ventoy builds and locked **Settings → Clean Temp** while any build or download is active.
- **Data Safety & Dual-LUN Fixes (`UsbGadgetEngine.kt`, `MainActivity.kt`, `BrowseAndBrowserSheets.kt`, `WindowsImageBuilder.kt`, `CreateBlankSheet.kt`):**
  - Fixed Dual-LUN deletion in both the Drives screen and Root File Browser so deleting a mounted image only unmounts its specific LUN and keeps `UsbKeepAliveService` alive if the other LUN is still mounted.
  - Added `UsbGadgetEngine.sanitizeOutputFileName()`, mounted-target & source-ISO overwrite guards, inline overwrite warning banners across all 4 image builders, `extraDriversDir` sizing in `WindowsImageBuilder.kt`, and `syncMediaStoreFileSize()` in `createBlankImage()`.
- **Network Thread Safety, Copy & Code Hygiene (`WindowsDirectCatalog.kt`, `FastIsoDownloader.kt`, `distros.json`, `AppPrefs.kt`):**
  - Replaced unsynchronized WebView flags with `AtomicBoolean`, isolated Microsoft CDN cookies to a local `CookieManager` (no global `CookieHandler.setDefault` mutation), updated all 6 stale `'All Channels'` error strings, synced catalog version to `463`, and removed all 38 unused imports across 7 Kotlin files.

## v4.6.2 (2026-11-25)
- **Removed Windows ISO Slimmer (`WindowsIsoSlimmer.kt`):** Completely removed `WindowsIsoSlimmer.kt`, its File Info action card, bottom sheet, and coroutine state in `MainActivity.kt`, plus unused `cleanStock()` in `WindowsCustomization.kt`.
- **Dropped Unused `GNU xorriso` Binary (`build-apk.yml`, `BuildThermalHelper.kt`, `DiagnosticsSheet.kt`, `SettingsScreen.kt`):** Removed `libxorriso.so` cross-compilation and `XOR_SMOKE` from GitHub Actions CI, removed `xorriso` from open-source credits and diagnostics (`Windows Imaging Tools` now checks `7zzs` and `wimlib`), and simplified `BuildThermalHelper.kt` for **Windows USB Builder**.

## v4.6.1 (2026-11-25)
- **ISO Slimmer Official-ISO Parity (`WindowsIsoSlimmer.kt`):**
  - Added case-insensitive resolution for `sources/`, `efi/`, and `boot/` folders plus full split-WIM (`install.swm` `--ref`) source support.
  - Defaulted ISO Slimmer to **Clean Official Stock** (all tweaks OFF unless enabled), auto-enabled `sources/ei.cfg` when trimming editions, decoupled `useCa2023Bootloaders` from `autounattend.xml`, and auto-detected `uiLang` from `lang.ini`.
  - Switched UEFI boot image preference to official `efisys.bin` (preventing Setup reboot loops), added >3.95 GiB multi-edition ESD auto-fallback to split WIM (`install*.swm`), added Win 7 UEFI fix, preserved source ISO-9660 volume IDs, and added atomic `.tmp.iso` packing with PVD size + `7zzs -tiso` verification.
- **Windows USB Builder & Customization Fixes (`WindowsImageBuilder.kt`, `WindowsCustomization.kt`):**
  - Made `sources/`, `efi/`, CA 2023, and Win 7 UEFI snippets case-safe and `.swm`-aware; updated `inspectEditionsViaBundledTools()` to detect `install.swm`.
  - Separated `Panther/unattend.xml` (`includeWindowsPePass = false`) from root `autounattend.xml` and passed auto-detected `uiLang`.
- **Reliable Cancel & MTP Sync (`BuildThermalHelper.kt`, `MainActivity.kt`, `FastIsoDownloader.kt`, `LinuxImageBuilder.kt`, `VentoyImageBuilder.kt`, `UsbGadgetEngine.kt`):**
  - Added native PID-tree tracking and immediate process termination when cancelling ISO Slimmer or Windows USB Builder.
  - Wired `UsbGadgetEngine.syncMediaStoreFileSize()` into all ISO/IMG builders and downloads so overwritten images sync byte-exact over USB MTP, and removed stale `pb_winbuild_cache` temp stat counting.

## v4.6.0 (2026-11-25)
- **Removed On-Phone UUP ISO Builder:** Removed `WindowsBuildEngine.kt`, `MicrosoftUupClient.kt`, `VirtualEditionsPlugin.kt`, `WindowsBuildMonitorSheet.kt`, `sync-uup-builds.yml`, `uup-builds.json`, UUP settings/caches, and `IsoBuilderService` / `IsoBuilderActionReceiver`. Eliminates OOBE servicing loops, offline Edge/AppX issues, and ~400 KB of high-maintenance code.
- **Standalone Build Thermal Helper (`BuildThermalHelper.kt`):** Preserved **Cool / Balanced / Max Speed** thermal profiles (`taskset` core affinity, `nice`/`ionice`, thread limits) and **Auto Thermal Cooldown** and wired them into both **Windows ISO Slimmer** and **Windows USB Builder** — with zero impact on normal USB Gadget ISO/IMG mounting.
- **Streamlined Official Windows Downloads (`WindowsDownloadSheet.kt`, `WindowsDirectCatalog.kt`):** Tapping Windows 11, 10, or 8.1 opens straight into Official Microsoft Retail ISOs with Release, Architecture, Language search, Copy Link, and Turbo Download.
- **Leaner CI & Cleaner Diagnostics (`build-apk.yml`, `DiagnosticsSheet.kt`, `UsbGadgetEngine.kt`):** Dropped unused `cabextract` and `chntpw` cross-compilation steps, updated Pre-Flight Diagnostics for `7zzs`/`wimlib`/`xorriso`, and simplified temp cache cleanup to reclaim any old `.pocketboot_tmp` UUP folders.

## v4.5.0-v26.4.1 (2026-11-25)
- **OOBE "Why did my PC restart?" Hotfix:** Removed the premature `Winlogon\Shell` overwrite and `taskkill /im explorer.exe` from the top of `PocketBoot_Updates.cmd` (which fired ~3 s after `SetupComplete.cmd` before `OOBEInProgress` flipped to `0x1`, crashing `defaultuser0` OOBE).
- **Safe Fullscreen Pre-Desktop Overlay on Boot 1 & Boot 2:** Boot 1 launches `PocketBoot_WaitScreen.ps1` directly from `HKLM\...\Run` (zero CMD flash, fullscreen `TopMost` dark overlay after OOBE without killing `explorer.exe`), while Boot 2 sets `Winlogon\Shell` only *after* Phase 1 DISM completes and verifies `OOBEInProgress`, `WWAHost.exe`, and `msoobe.exe` are all inactive.
- **Reliable Offline Edge MSI Install:** Deferred `MicrosoftEdgeEnterprise.msi` installation to `PocketBoot_Updates.cmd` (`SystemSetupInProgress=0`) when updates are queued so Windows Installer (`msiserver`) installs it cleanly on the first attempt.

## v4.5.0-v26.4 (2026-11-25)
- **All Inbox Store Apps Default ON + UI Simplification:** Removed extra 4-way Store App sub-options in Download & Offline Rebuild sheets; replaced with a single default-ON *Include All Inbox Store Apps* toggle (always visible for Win 11 builds).
- **Bulletproof Store Apps Fetcher (Zero Silent Loss):** Added 2.2 s UUPdump pacing, fixed `MicrosoftUupClient.kt` to always merge embedded `<Files>` from `GetExtendedUpdateInfo2`, added zero-rate-limit Direct Microsoft Store App failover, pre-build manifest audit logging, and a hard guard that aborts instead of building a 0-app ISO.
- **Guaranteed Offline Microsoft Edge:** Added automatic fallback to official Microsoft Edge Enterprise Stable MSI (`edgeupdates.microsoft.com/api/products?view=enterprise` + Microsoft fwlink fallback) cached in `PKGS/` and installed silently during `SetupComplete.cmd` (with post-boot retry) when `Edge.wim` is absent.
- **Restored "Previous version of Setup":** Patched the 5 `LabConfig` hardware bypass keys directly into `boot.wim` Index 2's offline `SYSTEM` hive (`chntpw`) and omitted root `autounattend.xml` while keeping `$OEM$\$$\Panther\unattend.xml` (Microsoft Implicit Search Order #3).
- **Full Pre-Desktop Installation & Dark-Mode Progress Screen:** Locked `Winlogon\Shell` immediately after `OOBEInProgress=0x0` (before Phase 1 DISM starts), suppressed early `explorer.exe`, and added a fullscreen dark-mode WinForms overlay (`PocketBoot_WaitScreen.ps1`) with live phase indicator, single progress bar, elapsed timer, and anti-lockout watchdog so the user never sees a `24H2` desktop before `26H2` finishes.
- **Servicing Reliability & Polish:** Checked CBS `RebootInProgress` before burning servicing boot attempts, extended post-LCU `TiWorker` settle wait, fixed `/ResetBase` via a self-deleting post-reboot background task, skipped known-failing `MicrosoftSolitaireCollection` retries, fixed unescaped `&` in batch `echo` lines, and auto-saved `PocketBoot_LastBuild.log` on every successful build.

# Changelog

## v4.5.0-v26.3.2 (2026-11-24)
- **Gate 2B (`7zzs`) Fix:** Renamed Step 4 atomic temp ISO from `.iso.tmp` to `.tmp.iso` and passed `-tiso` explicitly so `7zzs` never misidentifies or deletes a valid freshly packed ISO; cleaned orphan temp ISOs on cancel/failure.
- **MTP / MediaStore Sync Hardening (`Gate 2C`):** Added `--user 0` to all root `content` CLI calls, forced FUSE `/storage/emulated/0` dentry revalidation before scanning, and replaced the blind 600 ms sleep with a blocking `CountDownLatch` `MediaScannerConnection` callback.
- **UUP Retention & Cache UX Fixes:** Replaced GNU `\|` regex in `updateConversionKeepPackages()` with Toybox-compatible `sed -e` expressions; honored explicit OFF toggles in both Download and Offline Rebuild sheets; excluded kept `isCachedReady` `PKGS/` folders from Settings *Temp & Build Cache* size stats.
- **Offline `Edge.wim` Merge Fix:** Exported `WIMLIB_IMAGEX_IGNORE_CASE=1` and passed `--no-acls` on `wimlib-imagex update` so Edge overlays cleanly without case-duplicate directories on Android/Linux.

## v4.5.0-v26.3.1 (2026-11-24)
- Fixed Kotlin compilation errors in `WindowsBuildEngine.kt` (raw-string escaping for `sed`/`grep` commands and removed duplicate `qOutIso` declaration; credited existing ISO size in Step 4 free-space check).

## v4.5.0-v26.3 (2026-11-24)
- **MTP Truncation Fix (3-layer):** Pre-pack MediaProvider purge before `xorriso` writes, atomic `.tmp` ISO packing + rename, and `Gate 2C` MediaStore `_size` verification & force-sync.
- **UUP Package Retention Fix:** Persisted `keepUupPackagesAfterBuild` (default `true`) in `AppPrefs.kt`, synced live Build Monitor toggle to `pb_build.json`, protected existing cached builds on re-runs, and preserved `isCachedReady` folders during temporary cache cleanup.
- **Offline Microsoft Edge (`Edge.wim`):** Added `Edge.wim` manifest support and automatic Step 3/4 `install.wim` integration when present.
- **Documentation:** Added `PROGRESS.md`, `TODO.md`, `KNOWN_ISSUES.md`, and `CHANGELOG.md`.

## v4.5.0-v26.2 (2026-11-24)
- Fixed OOBE *"Why did my PC restart?"* loop by keeping `Winlogon\Shell = explorer.exe` during OOBE and polling `OOBEInProgress = 0` before servicing.
- Fixed non-elevated `PocketBoot_WaitScreen.cmd` watchdog check.
- Added `Gate 2A` ISO-9660 Primary Volume Descriptor exact byte-size audit.
