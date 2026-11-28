package com.pocketboot.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AutounattendPreset(
    val name: String,
    val schemeName: String = PartitionScheme.GPT.name,
    val bypassRequirements: Boolean = true,
    val bypassMicrosoftAccount: Boolean = true,
    val disableDataCollection: Boolean = true,
    val disableBitLocker: Boolean = true,
    val disableSponsoredApps: Boolean = false,
    val classicContextMenu: Boolean = false,
    val skipWifiSetup: Boolean = true,
    val createLocalUser: Boolean = false,
    val localUsername: String = "",
    val skipOobe: Boolean = false,
    val qolImprovements: Boolean = true,
    val useCa2023Bootloaders: Boolean = false,
    val applySkuSiPolicy: Boolean = false,
    val winUtilJson: String = ""
)

data class SystemTweaksPreset(
    val name: String,
    val tweaksJson: String
)

data class DualLunCombo(
    val id: String,
    val name: String,
    val lun0Path: String,
    val lun0Mode: String = MountMode.AUTO.name,
    val lun1Path: String,
    val lun1Mode: String = MountMode.USB_RW.name
)

data class AppSettings(
    val hasCompletedSetup: Boolean = false,
    val defaultDir: String = "/data/media/0/Download",
    val downloadTargetMultiBootPath: String = "",
    val favoriteQuickMountPath: String = "",
    val keepAdbActive: Boolean = false,
    val autoPatchSelinux: Boolean = true,
    val surfaceMode: SurfaceMode = SurfaceMode.DARK,
    val useMonet: Boolean = false,
    val accentHex: Long = DEFAULT_ACCENT_HEX,
    val scanDirs: List<String> = listOf("/data/media/0/Download"),
    val pinnedFiles: List<String> = emptyList(),
    val hiddenFiles: List<String> = emptyList(),
    val verifiedIsoPaths: List<String> = emptyList(),
    val fileModes: Map<String, String> = emptyMap(),
    val useDefaultDistroRepo: Boolean = true,
    val customRepoUrls: List<String> = emptyList(),
    val cachedDistrosJson: String = "",
    val distrosCatalogVersion: Int = CURRENT_CATALOG_VERSION,
    val autounattendPresets: List<AutounattendPreset> = emptyList(),
    val systemTweaksPresets: List<SystemTweaksPreset> = emptyList(),
    val systemTweaksSearchHistory: List<String> = emptyList(),
    val downloadConnections: Int = 8,
    val autoVerifySha256: Boolean = true,
    val buildThermalProfile: BuildThermalProfile = BuildThermalProfile.BALANCED,
    val autoThermalCooldown: Boolean = true,
    val keepScreenAwakeDuringBuilds: Boolean = false,
    val dualLunCombos: List<DualLunCombo> = emptyList()
) {
    companion object {
        const val DEFAULT_ACCENT_HEX = 0xFF15F5ABL
        const val LEGACY_DEFAULT_ACCENT_HEX = 0xFF6366F1L
        const val CURRENT_CATALOG_VERSION = 463
        const val MAX_TWEAKS_SEARCH_HISTORY = 8
    }
}

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("pocketboot_prefs", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val defaultDir = prefs.getString("default_dir", "/data/media/0/Download")
            ?.trim()?.ifBlank { "/data/media/0/Download" } ?: "/data/media/0/Download"
        val rawDirs = prefs.getString("scan_dirs", null)
        val parsedDirs = if (rawDirs.isNullOrBlank()) listOf(defaultDir) else rawDirs.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val scanDirs = (listOf(defaultDir) + parsedDirs.filter { it != defaultDir }).distinct()
        val pinned = prefs.getString("pinned_files", "").orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val hidden = prefs.getString("hidden_files", "").orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val verified = prefs.getString("verified_iso_paths", "").orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val customRepos = prefs.getString("custom_repo_urls", "").orEmpty().split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val modeMap = mutableMapOf<String, String>()
        prefs.getString("file_modes", "").orEmpty().split("\n").forEach { line ->
            val sep = line.lastIndexOf('|')
            if (sep > 0) {
                val path = line.substring(0, sep).trim()
                val modeName = line.substring(sep + 1).trim()
                if (path.isNotEmpty() && modeName.isNotEmpty()) modeMap[path] = modeName
            }
        }
        val presets = mutableListOf<AutounattendPreset>()
        val rawPresets = prefs.getString("autounattend_presets_json", "").orEmpty()
        if (rawPresets.isNotBlank()) {
            runCatching {
                val arr = JSONArray(rawPresets)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "").trim()
                    if (name.isNotEmpty()) {
                        val user = o.optString("localUsername", "")
                        val legacyClassic = o.optBoolean("classicContextMenu", false)
                        val migratedTweaksJson = migrateLegacyClassicMenuJson(o.optString("winUtilJson", ""), legacyClassic)
                        presets.add(
                            AutounattendPreset(
                                name = name,
                                schemeName = o.optString("schemeName", PartitionScheme.GPT.name),
                                bypassRequirements = o.optBoolean("bypassRequirements", true),
                                bypassMicrosoftAccount = o.optBoolean("bypassMicrosoftAccount", true),
                                disableDataCollection = o.optBoolean("disableDataCollection", true),
                                disableBitLocker = o.optBoolean("disableBitLocker", true),
                                disableSponsoredApps = o.optBoolean("disableSponsoredApps", false),
                                classicContextMenu = false,
                                skipWifiSetup = o.optBoolean("skipWifiSetup", true),
                                createLocalUser = o.optBoolean("createLocalUser", user.isNotBlank()),
                                localUsername = user,
                                skipOobe = o.optBoolean("skipOobe", false),
                                qolImprovements = o.optBoolean("qolImprovements", true),
                                useCa2023Bootloaders = o.optBoolean("useCa2023Bootloaders", false),
                                applySkuSiPolicy = o.optBoolean("applySkuSiPolicy", false),
                                winUtilJson = migratedTweaksJson
                            )
                        )
                    }
                }
            }
        }
        val tweaksPresets = mutableListOf<SystemTweaksPreset>()
        val rawTweaksPresets = prefs.getString("system_tweaks_presets_json", "").orEmpty()
        if (rawTweaksPresets.isNotBlank()) {
            runCatching {
                val arr = JSONArray(rawTweaksPresets)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "").trim()
                    val json = o.optString("tweaksJson", "")
                    if (name.isNotEmpty()) tweaksPresets.add(SystemTweaksPreset(name, json))
                }
            }
        }
        val tweaksSearchHistory = mutableListOf<String>()
        val rawHistory = prefs.getString("system_tweaks_search_history", "").orEmpty()
        if (rawHistory.isNotBlank()) {
            runCatching {
                val arr = JSONArray(rawHistory)
                for (i in 0 until arr.length()) {
                    val q = arr.optString(i, "").trim()
                    if (q.isNotEmpty() && tweaksSearchHistory.none { it.equals(q, ignoreCase = true) }) {
                        tweaksSearchHistory.add(q)
                    }
                }
            }
        }
        val dualLunCombos = parseDualLunCombosJson(prefs.getString("dual_lun_combos_json", "").orEmpty())
        val surfaceMode = runCatching { SurfaceMode.valueOf(prefs.getString("surface_mode", SurfaceMode.DARK.name).orEmpty()) }.getOrDefault(SurfaceMode.DARK)

        var accentHex = prefs.getLong("accent_hex", AppSettings.DEFAULT_ACCENT_HEX)
        if (!prefs.getBoolean("accent_migrated_v440", false)) {
            if (accentHex == AppSettings.LEGACY_DEFAULT_ACCENT_HEX) accentHex = AppSettings.DEFAULT_ACCENT_HEX
            prefs.edit().putLong("accent_hex", accentHex).putBoolean("accent_migrated_v440", true).apply()
        }

        val storedCatVer = prefs.getInt("distros_catalog_version", 0)
        val cachedDistros = if (storedCatVer < AppSettings.CURRENT_CATALOG_VERSION) {
            prefs.edit().remove("cached_distros_json").putInt("distros_catalog_version", AppSettings.CURRENT_CATALOG_VERSION).apply()
            ""
        } else prefs.getString("cached_distros_json", "").orEmpty()

        return AppSettings(
            hasCompletedSetup = prefs.getBoolean("setup_done", false),
            defaultDir = defaultDir,
            downloadTargetMultiBootPath = prefs.getString("dl_target_multiboot", "").orEmpty().trim(),
            favoriteQuickMountPath = prefs.getString("fav_quick_mount", "").orEmpty().trim(),
            keepAdbActive = prefs.getBoolean("keep_adb", false),
            autoPatchSelinux = prefs.getBoolean("auto_patch_selinux", true),
            surfaceMode = surfaceMode,
            useMonet = prefs.getBoolean("use_monet", false),
            accentHex = accentHex,
            scanDirs = scanDirs,
            pinnedFiles = pinned,
            hiddenFiles = hidden,
            verifiedIsoPaths = verified,
            fileModes = modeMap,
            useDefaultDistroRepo = prefs.getBoolean("use_default_distro_repo", true),
            customRepoUrls = customRepos,
            cachedDistrosJson = cachedDistros,
            distrosCatalogVersion = AppSettings.CURRENT_CATALOG_VERSION,
            autounattendPresets = presets,
            systemTweaksPresets = tweaksPresets,
            systemTweaksSearchHistory = tweaksSearchHistory.take(AppSettings.MAX_TWEAKS_SEARCH_HISTORY),
            downloadConnections = prefs.getInt("dl_connections", 8).coerceIn(1, 16),
            autoVerifySha256 = prefs.getBoolean("auto_verify_sha256", true),
            buildThermalProfile = runCatching {
                BuildThermalProfile.valueOf(prefs.getString("build_thermal_profile", BuildThermalProfile.BALANCED.name).orEmpty())
            }.getOrDefault(BuildThermalProfile.BALANCED),
            autoThermalCooldown = prefs.getBoolean("auto_thermal_cooldown", true),
            keepScreenAwakeDuringBuilds = prefs.getBoolean("keep_screen_awake_builds", false),
            dualLunCombos = dualLunCombos
        )
    }

    fun save(settings: AppSettings) {
        val serializedModes = settings.fileModes.entries.joinToString("\n") { "${it.key}|${it.value}" }
        val presetsArr = JSONArray()
        settings.autounattendPresets.forEach { p ->
            presetsArr.put(JSONObject().apply {
                put("name", p.name); put("schemeName", p.schemeName)
                put("bypassRequirements", p.bypassRequirements); put("bypassMicrosoftAccount", p.bypassMicrosoftAccount)
                put("disableDataCollection", p.disableDataCollection); put("disableBitLocker", p.disableBitLocker)
                put("disableSponsoredApps", p.disableSponsoredApps); put("classicContextMenu", false)
                put("skipWifiSetup", p.skipWifiSetup); put("createLocalUser", p.createLocalUser)
                put("localUsername", p.localUsername); put("skipOobe", p.skipOobe)
                put("qolImprovements", p.qolImprovements); put("useCa2023Bootloaders", p.useCa2023Bootloaders)
                put("applySkuSiPolicy", p.applySkuSiPolicy)
                if (p.winUtilJson.isNotBlank()) put("winUtilJson", p.winUtilJson)
            })
        }
        val tweaksPresetsArr = JSONArray()
        settings.systemTweaksPresets.forEach { tp ->
            tweaksPresetsArr.put(JSONObject().apply {
                put("name", tp.name)
                put("tweaksJson", tp.tweaksJson)
            })
        }
        val historyArr = JSONArray()
        settings.systemTweaksSearchHistory.take(AppSettings.MAX_TWEAKS_SEARCH_HISTORY).forEach { historyArr.put(it) }
        val combosArr = encodeDualLunCombosJson(settings.dualLunCombos)
        prefs.edit()
            .putBoolean("setup_done", settings.hasCompletedSetup)
            .putString("default_dir", settings.defaultDir)
            .putString("dl_target_multiboot", settings.downloadTargetMultiBootPath)
            .putString("fav_quick_mount", settings.favoriteQuickMountPath)
            .putBoolean("keep_adb", settings.keepAdbActive)
            .putBoolean("auto_patch_selinux", settings.autoPatchSelinux)
            .putString("surface_mode", settings.surfaceMode.name)
            .putBoolean("use_monet", settings.useMonet)
            .putLong("accent_hex", settings.accentHex)
            .putBoolean("accent_migrated_v440", true)
            .putString("scan_dirs", settings.scanDirs.distinct().joinToString("\n"))
            .putString("pinned_files", settings.pinnedFiles.distinct().joinToString("\n"))
            .putString("hidden_files", settings.hiddenFiles.distinct().joinToString("\n"))
            .putString("verified_iso_paths", settings.verifiedIsoPaths.distinct().joinToString("\n"))
            .putString("file_modes", serializedModes)
            .putBoolean("use_default_distro_repo", settings.useDefaultDistroRepo)
            .putString("custom_repo_urls", settings.customRepoUrls.distinct().joinToString("\n"))
            .putString("cached_distros_json", settings.cachedDistrosJson)
            .putInt("distros_catalog_version", AppSettings.CURRENT_CATALOG_VERSION)
            .putString("autounattend_presets_json", presetsArr.toString())
            .putString("system_tweaks_presets_json", tweaksPresetsArr.toString())
            .putString("system_tweaks_search_history", historyArr.toString())
            .putInt("dl_connections", settings.downloadConnections.coerceIn(1, 16))
            .putBoolean("auto_verify_sha256", settings.autoVerifySha256)
            .putString("build_thermal_profile", settings.buildThermalProfile.name)
            .putBoolean("auto_thermal_cooldown", settings.autoThermalCooldown)
            .putBoolean("keep_screen_awake_builds", settings.keepScreenAwakeDuringBuilds)
            .putString("dual_lun_combos_json", combosArr.toString())
            .apply()
    }

    companion object {
        fun encodeDualLunCombosJson(combos: List<DualLunCombo>): JSONArray {
            val arr = JSONArray()
            combos.forEach { c ->
                if (c.name.isNotBlank() && c.lun0Path.isNotBlank() && c.lun1Path.isNotBlank()) {
                    arr.put(JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("lun0Path", c.lun0Path)
                        put("lun0Mode", c.lun0Mode)
                        put("lun1Path", c.lun1Path)
                        put("lun1Mode", c.lun1Mode)
                    })
                }
            }
            return arr
        }

        fun parseDualLunCombosJson(raw: String): List<DualLunCombo> {
            if (raw.isBlank()) return emptyList()
            val out = mutableListOf<DualLunCombo>()
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "").trim()
                    val l0 = o.optString("lun0Path", "").trim()
                    val l1 = o.optString("lun1Path", "").trim()
                    if (name.isNotEmpty() && ShellSafety.isCleanAbsolutePath(l0) && ShellSafety.isCleanAbsolutePath(l1)) {
                        val id = o.optString("id", "").trim().ifBlank { "combo_${i}_${name.hashCode()}" }
                        val m0 = o.optString("lun0Mode", MountMode.AUTO.name).trim().ifBlank { MountMode.AUTO.name }
                        val m1 = o.optString("lun1Mode", MountMode.USB_RW.name).trim().ifBlank { MountMode.USB_RW.name }
                        out.add(DualLunCombo(id = id, name = name, lun0Path = l0, lun0Mode = m0, lun1Path = l1, lun1Mode = m1))
                    }
                }
            }
            return out
        }

        fun migrateLegacyClassicMenuJson(rawTweaksJson: String, legacyClassicContextMenu: Boolean): String {
            if (!legacyClassicContextMenu) return rawTweaksJson
            return runCatching {
                val obj = if (rawTweaksJson.isNotBlank()) JSONObject(rawTweaksJson) else JSONObject()
                obj.put("classicContextMenu", true)
                obj.toString()
            }.getOrDefault(rawTweaksJson)
        }

        fun recordSearchQuery(existing: List<String>, rawQuery: String): List<String> {
            val clean = rawQuery.trim()
            if (clean.length < 2) return existing.take(AppSettings.MAX_TWEAKS_SEARCH_HISTORY)
            return (listOf(clean) + existing.filterNot { it.equals(clean, ignoreCase = true) })
                .take(AppSettings.MAX_TWEAKS_SEARCH_HISTORY)
        }

        fun exportBackupJson(settings: AppSettings): String {
            val presetsArr = JSONArray()
            settings.autounattendPresets.forEach { p ->
                presetsArr.put(JSONObject().apply {
                    put("name", p.name); put("schemeName", p.schemeName)
                    put("bypassRequirements", p.bypassRequirements); put("bypassMicrosoftAccount", p.bypassMicrosoftAccount)
                    put("disableDataCollection", p.disableDataCollection); put("disableBitLocker", p.disableBitLocker)
                    put("disableSponsoredApps", p.disableSponsoredApps); put("classicContextMenu", false)
                    put("skipWifiSetup", p.skipWifiSetup); put("createLocalUser", p.createLocalUser)
                    put("localUsername", p.localUsername); put("skipOobe", p.skipOobe)
                    put("qolImprovements", p.qolImprovements); put("useCa2023Bootloaders", p.useCa2023Bootloaders)
                    put("applySkuSiPolicy", p.applySkuSiPolicy)
                    if (p.winUtilJson.isNotBlank()) put("winUtilJson", p.winUtilJson)
                })
            }
            val tweaksPresetsArr = JSONArray()
            settings.systemTweaksPresets.forEach { tp ->
                tweaksPresetsArr.put(JSONObject().apply {
                    put("name", tp.name)
                    put("tweaksJson", tp.tweaksJson)
                })
            }
            val modesObj = JSONObject()
            settings.fileModes.forEach { (k, v) -> modesObj.put(k, v) }
            return JSONObject().apply {
                put("pocketbootBackupVersion", 4); put("defaultDir", settings.defaultDir)
                put("downloadTargetMultiBootPath", settings.downloadTargetMultiBootPath)
                put("favoriteQuickMountPath", settings.favoriteQuickMountPath)
                put("keepAdbActive", settings.keepAdbActive); put("autoPatchSelinux", settings.autoPatchSelinux)
                put("surfaceMode", settings.surfaceMode.name); put("useMonet", settings.useMonet)
                put("accentHex", settings.accentHex); put("scanDirs", JSONArray(settings.scanDirs))
                put("useDefaultDistroRepo", settings.useDefaultDistroRepo); put("customRepoUrls", JSONArray(settings.customRepoUrls))
                put("downloadConnections", settings.downloadConnections); put("autoVerifySha256", settings.autoVerifySha256)
                put("buildThermalProfile", settings.buildThermalProfile.name); put("autoThermalCooldown", settings.autoThermalCooldown); put("keepScreenAwakeDuringBuilds", settings.keepScreenAwakeDuringBuilds)
                put("fileModes", modesObj); put("autounattendPresets", presetsArr)
                put("systemTweaksPresets", tweaksPresetsArr)
                put("systemTweaksSearchHistory", JSONArray(settings.systemTweaksSearchHistory.take(AppSettings.MAX_TWEAKS_SEARCH_HISTORY)))
                put("dualLunCombos", encodeDualLunCombosJson(settings.dualLunCombos))
                put("pinnedFiles", JSONArray(settings.pinnedFiles))
                put("hiddenFiles", JSONArray(settings.hiddenFiles))
            }.toString(2)
        }

        fun importBackupJson(rawJson: String, current: AppSettings): AppSettings? = runCatching {
            if (rawJson.length > 2 * 1024 * 1024) return null
            val obj = JSONObject(rawJson)
            if (!obj.has("pocketbootBackupVersion")) return null
            val rawDefDir = obj.optString("defaultDir", current.defaultDir).trim()
            val defDir = if (rawDefDir.isNotBlank() && ShellSafety.isCleanAbsolutePath(rawDefDir)) rawDefDir else current.defaultDir
            val parsedDirs = mutableListOf<String>()
            obj.optJSONArray("scanDirs")?.let { a ->
                for (i in 0 until a.length()) {
                    val d = a.optString(i, "").trim()
                    if (d.isNotEmpty() && ShellSafety.isCleanAbsolutePath(d)) parsedDirs.add(d)
                }
            }
            val scanDirs = (listOf(defDir) + parsedDirs.filter { it != defDir }).distinct()
            val parsedRepos = mutableListOf<String>()
            obj.optJSONArray("customRepoUrls")?.let { a ->
                for (i in 0 until a.length()) {
                    val u = a.optString(i, "").trim()
                    if (ShellSafety.isValidHttpUrl(u)) parsedRepos.add(u)
                }
            }
            val modesMap = mutableMapOf<String, String>()
            obj.optJSONObject("fileModes")?.let { m ->
                val keys = m.keys()
                while (keys.hasNext()) {
                    val k = keys.next().trim()
                    val v = m.optString(k, "").trim()
                    if (k.isNotBlank() && ShellSafety.isCleanAbsolutePath(k) && v.isNotBlank()) modesMap[k] = v
                }
            }
            fun parseCleanPathList(key: String, fallback: List<String>): List<String> {
                if (!obj.has(key)) return fallback
                val out = linkedSetOf<String>()
                obj.optJSONArray(key)?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val p = arr.optString(i, "").trim()
                        if (p.isNotEmpty() && ShellSafety.isCleanAbsolutePath(p)) out.add(p)
                    }
                }
                return out.toList()
            }
            val rawTargetMb = obj.optString("downloadTargetMultiBootPath", current.downloadTargetMultiBootPath).trim()
            val safeTargetMb = if (rawTargetMb.isEmpty() || ShellSafety.isCleanAbsolutePath(rawTargetMb)) rawTargetMb else current.downloadTargetMultiBootPath
            val rawFavMount = obj.optString("favoriteQuickMountPath", current.favoriteQuickMountPath).trim()
            val safeFavMount = if (rawFavMount.isEmpty() || ShellSafety.isCleanAbsolutePath(rawFavMount)) rawFavMount else current.favoriteQuickMountPath
            val presets = mutableListOf<AutounattendPreset>()
            obj.optJSONArray("autounattendPresets")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "").trim()
                    if (name.isNotEmpty()) {
                        val u = o.optString("localUsername", "")
                        val legacyClassic = o.optBoolean("classicContextMenu", false)
                        val migratedTweaksJson = migrateLegacyClassicMenuJson(o.optString("winUtilJson", ""), legacyClassic)
                        presets.add(AutounattendPreset(name, o.optString("schemeName", PartitionScheme.GPT.name), o.optBoolean("bypassRequirements", true), o.optBoolean("bypassMicrosoftAccount", true), o.optBoolean("disableDataCollection", true), o.optBoolean("disableBitLocker", true), o.optBoolean("disableSponsoredApps", false), false, o.optBoolean("skipWifiSetup", true), o.optBoolean("createLocalUser", u.isNotBlank()), u, o.optBoolean("skipOobe", false), o.optBoolean("qolImprovements", true), o.optBoolean("useCa2023Bootloaders", false), o.optBoolean("applySkuSiPolicy", false), migratedTweaksJson))
                    }
                }
            }
            val tweaksPresets = mutableListOf<SystemTweaksPreset>()
            obj.optJSONArray("systemTweaksPresets")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val name = o.optString("name", "").trim()
                    val json = o.optString("tweaksJson", "")
                    if (name.isNotEmpty()) tweaksPresets.add(SystemTweaksPreset(name, json))
                }
            }
            val parsedHistory = mutableListOf<String>()
            obj.optJSONArray("systemTweaksSearchHistory")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val q = arr.optString(i, "").trim()
                    if (q.isNotEmpty() && parsedHistory.none { it.equals(q, ignoreCase = true) }) parsedHistory.add(q)
                }
            }
            val surfMode = runCatching { SurfaceMode.valueOf(obj.optString("surfaceMode", current.surfaceMode.name)) }.getOrDefault(current.surfaceMode)
            current.copy(
                hasCompletedSetup = true,
                defaultDir = defDir,
                downloadTargetMultiBootPath = safeTargetMb,
                favoriteQuickMountPath = safeFavMount,
                keepAdbActive = obj.optBoolean("keepAdbActive", current.keepAdbActive),
                autoPatchSelinux = obj.optBoolean("autoPatchSelinux", current.autoPatchSelinux),
                surfaceMode = surfMode,
                useMonet = obj.optBoolean("useMonet", current.useMonet),
                accentHex = obj.optLong("accentHex", current.accentHex),
                scanDirs = scanDirs,
                useDefaultDistroRepo = obj.optBoolean("useDefaultDistroRepo", current.useDefaultDistroRepo),
                customRepoUrls = parsedRepos.distinct(),
                downloadConnections = obj.optInt("downloadConnections", current.downloadConnections).coerceIn(1, 16),
                autoVerifySha256 = obj.optBoolean("autoVerifySha256", current.autoVerifySha256),
                buildThermalProfile = runCatching { BuildThermalProfile.valueOf(obj.optString("buildThermalProfile", current.buildThermalProfile.name)) }.getOrDefault(current.buildThermalProfile),
                autoThermalCooldown = obj.optBoolean("autoThermalCooldown", current.autoThermalCooldown),
                keepScreenAwakeDuringBuilds = obj.optBoolean("keepScreenAwakeDuringBuilds", current.keepScreenAwakeDuringBuilds),
                fileModes = modesMap,
                autounattendPresets = presets,
                systemTweaksPresets = if (obj.has("systemTweaksPresets")) tweaksPresets else current.systemTweaksPresets,
                systemTweaksSearchHistory = if (obj.has("systemTweaksSearchHistory")) parsedHistory.take(AppSettings.MAX_TWEAKS_SEARCH_HISTORY) else current.systemTweaksSearchHistory,
                dualLunCombos = if (obj.has("dualLunCombos")) parseDualLunCombosJson(obj.optJSONArray("dualLunCombos")?.toString().orEmpty()) else current.dualLunCombos,
                pinnedFiles = parseCleanPathList("pinnedFiles", current.pinnedFiles),
                hiddenFiles = parseCleanPathList("hiddenFiles", current.hiddenFiles)
            )
        }.getOrNull()
    }
}
