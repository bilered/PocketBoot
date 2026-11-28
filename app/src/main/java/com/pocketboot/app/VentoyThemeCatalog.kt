package com.pocketboot.app

import android.content.Context
import com.topjohnwu.superuser.Shell
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class MultiBootThemePreset(
    val id: String,
    val label: String,
    val subtitle: String
) {
    STOCK("stock", "Stock Default", "Standard Multi-Boot text/menu layout"),
    POCKETBOOT_DARK("pocketboot_dark", "PocketBoot Dark", "Dark slate GRUB theme with emerald accent"),
    MINIMAL_HC("minimal_hc", "High-Contrast", "Crisp black & cyan theme for laptops & small displays");

    companion object {
        fun fromId(id: String?): MultiBootThemePreset =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: STOCK
    }
}

data class MultiBootMenuConfig(
    val themeId: String = MultiBootThemePreset.STOCK.id,
    val menuTimeoutSec: Int = 0,
    val menuAliases: Map<String, String> = emptyMap()
) {
    val themePreset: MultiBootThemePreset get() = MultiBootThemePreset.fromId(themeId)
}

data class MultiBootPersistenceEntry(
    val imagePath: String,
    val backendPath: String,
    val sizeBytes: Long = 0L,
    val styleLabel: String = ""
)

/**
 * Pure JSON & GRUB2 theme helpers for Multi-Boot `ventoy.json`.
 * Preserves sibling sections (`control`, `auto_install`, `injection`, `persistence`, `menu_alias`, `theme`)
 * across independent UI edits so changing one setting never clobbers another.
 */
object VentoyThemeCatalog {

    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    fun normalizeRelPath(raw: String): String = "/" + raw.trim().trimStart('/')

    fun themeFilePathFor(preset: MultiBootThemePreset): String =
        "/ventoy/theme/${preset.id}/theme.txt"

    fun readMenuConfig(rootObj: JSONObject): MultiBootMenuConfig {
        var timeoutSec = 0
        rootObj.optJSONArray("control")?.let { ctrl ->
            for (i in 0 until ctrl.length()) {
                val obj = ctrl.optJSONObject(i) ?: continue
                if (obj.has("VTOY_MENU_TIMEOUT")) {
                    timeoutSec = obj.optString("VTOY_MENU_TIMEOUT", "0").trim().toIntOrNull()?.coerceIn(0, 120) ?: 0
                }
            }
        }

        val aliases = LinkedHashMap<String, String>()
        rootObj.optJSONArray("menu_alias")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val img = o.optString("image", "").trim()
                val alias = o.optString("alias", "").trim()
                if (img.isNotEmpty() && alias.isNotEmpty()) {
                    aliases[normalizeRelPath(img)] = alias
                }
            }
        }

        val themeObj = rootObj.optJSONObject("theme")
        val themeFile = themeObj?.optString("file", "").orEmpty().trim()
        val themeId = when {
            themeFile.contains("/pocketboot_dark/", ignoreCase = true) -> MultiBootThemePreset.POCKETBOOT_DARK.id
            themeFile.contains("/minimal_hc/", ignoreCase = true) -> MultiBootThemePreset.MINIMAL_HC.id
            else -> MultiBootThemePreset.STOCK.id
        }

        return MultiBootMenuConfig(
            themeId = themeId,
            menuTimeoutSec = timeoutSec,
            menuAliases = aliases
        )
    }

    /**
     * Updates `control` (preserving bypasses and custom keys while updating `VTOY_MENU_TIMEOUT`),
     * `menu_alias`, and `theme` inside [rootObj] without disturbing `auto_install`, `injection`,
     * or `persistence`.
     */
    fun applyMenuConfigPreservingAll(
        rootObj: JSONObject,
        config: MultiBootMenuConfig,
        existingImageRelPaths: Set<String>? = null
    ): JSONObject {
        val normExisting = existingImageRelPaths?.map { normalizeRelPath(it) }?.toSet()

        val newControl = JSONArray()
        val oldControl = rootObj.optJSONArray("control")
        if (oldControl != null) {
            for (i in 0 until oldControl.length()) {
                val item = oldControl.optJSONObject(i) ?: continue
                if (item.has("VTOY_MENU_TIMEOUT")) continue
                newControl.put(item)
            }
        }
        val clampedTimeout = config.menuTimeoutSec.coerceIn(0, 120)
        if (clampedTimeout > 0) {
            newControl.put(JSONObject().put("VTOY_MENU_TIMEOUT", clampedTimeout.toString()))
        }
        if (newControl.length() > 0) {
            rootObj.put("control", newControl)
        } else {
            rootObj.remove("control")
        }

        val aliasArr = JSONArray()
        config.menuAliases.entries
            .map { (k, v) -> normalizeRelPath(k) to v.trim().take(80) }
            .filter { (k, v) -> v.isNotEmpty() && (normExisting == null || k in normExisting) }
            .sortedBy { it.first.lowercase() }
            .forEach { (img, alias) ->
                aliasArr.put(JSONObject().apply {
                    put("image", img)
                    put("alias", alias)
                })
            }
        if (aliasArr.length() > 0) {
            rootObj.put("menu_alias", aliasArr)
        } else {
            rootObj.remove("menu_alias")
        }

        val preset = config.themePreset
        if (preset == MultiBootThemePreset.STOCK) {
            rootObj.remove("theme")
        } else {
            rootObj.put("theme", JSONObject().apply {
                put("file", themeFilePathFor(preset))
                put("display_mode", "GUI")
            })
        }
        return rootObj
    }

    /**
     * Merges bypass flags into `control[]` while preserving `VTOY_MENU_TIMEOUT` and any other entries.
     */
    fun mergeBypassControlPreservingTimeout(
        rootObj: JSONObject,
        bypassCheck: Boolean,
        bypassNro: Boolean
    ): JSONObject {
        val merged = JSONArray()
        merged.put(JSONObject().put("VTOY_WIN11_BYPASS_CHECK", if (bypassCheck) "1" else "0"))
        merged.put(JSONObject().put("VTOY_WIN11_BYPASS_NRO", if (bypassNro) "1" else "0"))
        val oldControl = rootObj.optJSONArray("control")
        if (oldControl != null) {
            for (i in 0 until oldControl.length()) {
                val item = oldControl.optJSONObject(i) ?: continue
                if (item.has("VTOY_WIN11_BYPASS_CHECK") || item.has("VTOY_WIN11_BYPASS_NRO")) continue
                merged.put(item)
            }
        }
        rootObj.put("control", merged)
        return rootObj
    }

    fun readPersistenceEntries(rootObj: JSONObject): List<MultiBootPersistenceEntry> {
        val out = mutableListOf<MultiBootPersistenceEntry>()
        val arr = rootObj.optJSONArray("persistence") ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val img = o.optString("image", "").trim()
            if (img.isEmpty()) continue
            val backend = when (val rawB = o.opt("backend")) {
                is String -> rawB.trim()
                is JSONArray -> rawB.optString(0, "").trim()
                else -> ""
            }
            if (backend.isEmpty()) continue
            val leafLow = backend.substringAfterLast('/').lowercase()
            val styleHint = when {
                leafLow.contains("persistence") && !leafLow.contains("casper") && !leafLow.contains("writable") -> "Debian/Kali"
                leafLow.contains("casper") -> "Pop!/Legacy"
                else -> "Ubuntu/Mint"
            }
            out.add(
                MultiBootPersistenceEntry(
                    imagePath = normalizeRelPath(img),
                    backendPath = normalizeRelPath(backend),
                    styleLabel = styleHint
                )
            )
        }
        return out
    }

    fun upsertPersistenceEntry(
        rootObj: JSONObject,
        imagePath: String,
        backendPath: String
    ): JSONObject {
        val normImg = normalizeRelPath(imagePath)
        val normBackend = normalizeRelPath(backendPath)
        val existing = readPersistenceEntries(rootObj).filterNot { it.imagePath.equals(normImg, ignoreCase = true) }
        val arr = JSONArray()
        existing.forEach { e ->
            arr.put(JSONObject().apply {
                put("image", e.imagePath)
                put("backend", e.backendPath)
            })
        }
        arr.put(JSONObject().apply {
            put("image", normImg)
            put("backend", normBackend)
        })
        rootObj.put("persistence", arr)
        return rootObj
    }

    fun removePersistenceEntry(rootObj: JSONObject, imagePath: String): Pair<JSONObject, String?> {
        val normImg = normalizeRelPath(imagePath)
        val all = readPersistenceEntries(rootObj)
        val removed = all.firstOrNull { it.imagePath.equals(normImg, ignoreCase = true) }?.backendPath
        val remaining = all.filterNot { it.imagePath.equals(normImg, ignoreCase = true) }
        if (remaining.isEmpty()) {
            rootObj.remove("persistence")
        } else {
            val arr = JSONArray()
            remaining.forEach { e ->
                arr.put(JSONObject().apply {
                    put("image", e.imagePath)
                    put("backend", e.backendPath)
                })
            }
            rootObj.put("persistence", arr)
        }
        return rootObj to removed
    }

    fun buildGrubThemeTxt(preset: MultiBootThemePreset): String {
        val isDark = preset == MultiBootThemePreset.POCKETBOOT_DARK
        val bgHex = if (isDark) "#0B0E14" else "#000000"
        val titleHex = if (isDark) "#15F5AB" else "#38BDF8"
        val fgHex = "#E5E7EB"
        val selHex = if (isDark) "#15F5AB" else "#38BDF8"
        val mutedHex = "#9CA3AF"
        return """
            # PocketBoot Multi-Boot GRUB2 Theme (${preset.label})
            title-text: "PocketBoot Multi-Boot"
            title-color: "$titleHex"
            desktop-color: "$bgHex"
            terminal-left: "6%"
            terminal-top: "14%"
            terminal-width: "88%"
            terminal-height: "74%"
            terminal-border: "0"

            + boot_menu {
                left = 10%
                top = 18%
                width = 80%
                height = 64%
                item_color = "$fgHex"
                selected_item_color = "$selHex"
                item_height = 28
                item_padding = 6
                item_spacing = 6
                icon_width = 0
                icon_height = 0
                scrollbar = true
                scrollbar_width = 6
            }

            + label {
                left = 10%
                top = 86%
                width = 80%
                height = 24
                align = "center"
                color = "$mutedHex"
                text = "Enter: Boot  |  F1: Help  |  F2: Browse  |  F5: Tools"
            }

            + label {
                id = "__timeout__"
                left = 10%
                top = 91%
                width = 80%
                height = 22
                align = "center"
                color = "$titleHex"
                text = "Auto-booting in %d seconds..."
            }
        """.trimIndent() + "\n"
    }

    fun writeThemeAssetsOnMounted(context: Context, mountRoot: String, preset: MultiBootThemePreset): Boolean {
        val cleanRoot = mountRoot.trimEnd('/')
        if (preset == MultiBootThemePreset.STOCK) {
            Shell.cmd("rm -rf ${shellQuote("$cleanRoot/ventoy/theme")} 2>/dev/null || true").exec()
            return true
        }
        val themeDir = "$cleanRoot/ventoy/theme/${preset.id}"
        val themeDst = "$themeDir/theme.txt"
        val tmpFile = File(context.cacheDir, "pb_theme_${preset.id}.txt")
        return try {
            tmpFile.writeText(buildGrubThemeTxt(preset), Charsets.UTF_8)
            Shell.cmd(
                "rm -rf ${shellQuote("$cleanRoot/ventoy/theme")} 2>/dev/null; " +
                    "mkdir -p ${shellQuote(themeDir)} && cp -f ${shellQuote(tmpFile.absolutePath)} ${shellQuote(themeDst)}"
            ).exec().isSuccess
        } finally {
            tmpFile.delete()
        }
    }
}
