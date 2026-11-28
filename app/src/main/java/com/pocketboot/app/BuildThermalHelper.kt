package com.pocketboot.app

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

enum class BuildThermalProfile(
    val label: String,
    val shortLabel: String,
    val subtitle: String,
    val sevenZipThreads: Int,
    val niceLevel: Int,
    val ionicePrio: Int,
    val cooldownTriggerC: Float,
    val cooldownResumeC: Float
) {
    COOL("Cool", "Cool", "2 threads · avoids Prime core · auto-cooldown @ 41.5°C", 2, 15, 7, 41.5f, 38.5f),
    BALANCED("Balanced", "Balanced", "4 threads · low priority · auto-cooldown @ 43.0°C", 4, 10, 5, 43.0f, 39.0f),
    MAX("Max Speed", "Max", "All cores · normal priority · no auto-cooldown", 0, 0, 0, 0f, 0f)
}

data class WindowsToolsStatus(
    val sevenZip: String?,
    val wimlib: String?,
    val chntpw: String? = null
) {
    val ready: Boolean get() = sevenZip != null && wimlib != null && chntpw != null
}

/**
 * Process-scoped thermal & CPU affinity helper used by Windows, Linux, and Ventoy USB Builders.
 * Wraps only heavy native build binaries (7zzs, cp, dd) and never touches USB Gadget mounting.
 */
object BuildThermalHelper {
    private const val ACTIVE_BUILD_PID_FILE = "/data/local/tmp/pb_active_build_pid"
    private val buildCancelledFlag = AtomicBoolean(false)
    @Volatile private var defaultThermalProfile: BuildThermalProfile = BuildThermalProfile.BALANCED
    @Volatile private var defaultAutoCooldown: Boolean = true
    @Volatile private var cachedCpuMasks: Triple<String, String, String>? = null

    fun resetCancelFlag() {
        buildCancelledFlag.set(false)
        Shell.cmd("rm -f $ACTIVE_BUILD_PID_FILE 2>/dev/null || true").exec()
    }

    fun markBuildCancelled() {
        buildCancelledFlag.set(true)
    }

    fun isBuildCancelled(): Boolean = buildCancelledFlag.get()

    /**
     * Immediately terminates the currently tracked native build binary (7zzs, cp, dd)
     * and its parent wrapper shell so cancelling a build stops CPU/storage work instantly.
     */
    fun cancelActiveBuildProcess() {
        buildCancelledFlag.set(true)
        Shell.cmd(
            """
            if [ -f $ACTIVE_BUILD_PID_FILE ]; then
                ROOT_PID=${'$'}(cat $ACTIVE_BUILD_PID_FILE 2>/dev/null | tr -dc '0-9')
                if [ -n "${'$'}ROOT_PID" ]; then
                    kill_tree() {
                        local p="${'$'}1"
                        for st in /proc/[0-9]*/status; do
                            [ -r "${'$'}st" ] || continue
                            if grep -q "^PPid:[[:space:]]*${'$'}{p}${'$'}" "${'$'}st" 2>/dev/null; then
                                local ch="${'$'}{st#/proc/}"
                                ch="${'$'}{ch%/status}"
                                kill_tree "${'$'}ch"
                            fi
                        done
                        kill -9 "${'$'}p" 2>/dev/null || true
                    }
                    kill_tree "${'$'}ROOT_PID"
                fi
                rm -f $ACTIVE_BUILD_PID_FILE 2>/dev/null || true
            fi
            """.trimIndent()
        ).exec()
    }

    private fun shellQuote(value: String): String = ShellSafety.shellQuote(value)

    fun configureThermal(profile: BuildThermalProfile, autoCooldown: Boolean) {
        defaultThermalProfile = profile
        defaultAutoCooldown = autoCooldown
    }

    fun currentDefaultThermalProfile(): BuildThermalProfile = defaultThermalProfile

    fun isAutoCooldownEnabled(): Boolean = defaultAutoCooldown

    private fun detectCpuMasks(): Triple<String, String, String> {
        cachedCpuMasks?.let { return it }
        val res = runCatching {
            val out = Shell.cmd(
                "for c in /sys/devices/system/cpu/cpu[0-9]*; do " +
                    "[ -d \"\$c\" ] || continue; " +
                    "id=\${c##*cpu}; " +
                    "f=\$(cat \"\$c/cpufreq/cpuinfo_max_freq\" 2>/dev/null || cat \"\$c/cpufreq/scaling_max_freq\" 2>/dev/null || echo 1000000); " +
                    "echo \"\$id \$f\"; done"
            ).exec().out
            val pairs = out.mapNotNull { line ->
                val p = line.trim().split(Regex("\\s+"))
                val id = p.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val freq = p.getOrNull(1)?.toLongOrNull() ?: 1_000_000L
                if (id in 0..30) id to freq else null
            }.sortedBy { it.first }
            if (pairs.size < 2) return@runCatching Triple("", "", "")
            var allMask = 0
            pairs.forEach { (id, _) -> allMask = allMask or (1 shl id) }
            val maxFreq = pairs.maxOf { it.second }
            val distinctFreqs = pairs.map { it.second }.distinct().sorted()
            val nonPrime = pairs.filter { it.second < maxFreq }
            var balancedMask = 0
            if (nonPrime.size >= 2) {
                nonPrime.forEach { (id, _) -> balancedMask = balancedMask or (1 shl id) }
            } else {
                val keepCnt = (pairs.size * 3 / 4).coerceAtLeast(2)
                pairs.take(keepCnt).forEach { (id, _) -> balancedMask = balancedMask or (1 shl id) }
            }
            var coolMask = 0
            if (distinctFreqs.size >= 3) {
                val minFreq = distinctFreqs.first()
                val little = pairs.filter { it.second == minFreq }
                val mid = pairs.filter { it.second > minFreq && it.second < maxFreq }
                val chosen = (little + mid.take(2)).ifEmpty { nonPrime }
                chosen.forEach { (id, _) -> coolMask = coolMask or (1 shl id) }
            } else if (nonPrime.size >= 2) {
                nonPrime.take(nonPrime.size.coerceAtMost(4)).forEach { (id, _) -> coolMask = coolMask or (1 shl id) }
            } else {
                pairs.take((pairs.size / 2).coerceAtLeast(2)).forEach { (id, _) -> coolMask = coolMask or (1 shl id) }
            }
            Triple(
                "0x" + coolMask.toString(16),
                "0x" + balancedMask.toString(16),
                "0x" + allMask.toString(16)
            )
        }.getOrDefault(Triple("", "", ""))
        cachedCpuMasks = res
        return res
    }

    internal fun cpuMaskForProfile(profile: BuildThermalProfile): String {
        val (coolMask, balMask, allMask) = detectCpuMasks()
        return when (profile) {
            BuildThermalProfile.COOL -> coolMask
            BuildThermalProfile.BALANCED -> balMask
            BuildThermalProfile.MAX -> allMask
        }
    }

    internal fun thermalExecPrefix(profile: BuildThermalProfile): String {
        val mask = cpuMaskForProfile(profile)
        val parts = mutableListOf("THERM_PFX=\"\"")
        if (profile.ionicePrio > 0) {
            parts += "if ionice -c 2 -n ${profile.ionicePrio} true >/dev/null 2>&1; then THERM_PFX=\"\$THERM_PFX ionice -c 2 -n ${profile.ionicePrio}\"; fi"
        }
        if (profile.niceLevel > 0) {
            parts += "if nice -n ${profile.niceLevel} true >/dev/null 2>&1; then THERM_PFX=\"\$THERM_PFX nice -n ${profile.niceLevel}\"; fi"
        }
        if (profile != BuildThermalProfile.MAX && mask.isNotBlank()) {
            parts += "if taskset $mask true >/dev/null 2>&1; then THERM_PFX=\"\$THERM_PFX taskset $mask\"; fi"
        }
        return parts.joinToString("; ")
    }

    internal fun sevenZipThreadArgFor(profile: BuildThermalProfile): String =
        if (profile.sevenZipThreads > 0) "-mmt=${profile.sevenZipThreads}" else ""

    fun readBatteryTempC(context: Context): Float {
        return runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            if (tenths > 0) tenths / 10f else 0f
        }.getOrDefault(0f)
    }

    /**
     * Pauses cleanly between heavy native build steps if battery temperature exceeds the selected
     * profile's trigger threshold, automatically resuming once temperature drops to resume threshold.
     */
    suspend fun waitForThermalCooldownIfNeeded(
        context: Context,
        profile: BuildThermalProfile = defaultThermalProfile,
        onStatus: ((tempC: Float, resumeAtC: Float) -> Unit)? = null
    ) {
        if (!defaultAutoCooldown || profile.cooldownTriggerC <= 0f) return
        var temp = readBatteryTempC(context)
        if (temp < profile.cooldownTriggerC) return
        var waitedSec = 0
        while (coroutineContext.isActive && !buildCancelledFlag.get() && temp > profile.cooldownResumeC && waitedSec < 300) {
            onStatus?.invoke(temp, profile.cooldownResumeC)
            delay(3_000L)
            waitedSec += 3
            temp = readBatteryTempC(context)
        }
    }

    internal fun runIsolatedRootScript(
        context: Context,
        tag: String,
        scriptBody: String,
        trackPid: Boolean = false
    ): Shell.Result {
        val stamp = System.nanoTime()
        val localScript = File(context.applicationContext.cacheDir, "pb_${tag}_$stamp.sh")
        val rootScriptPath = "/data/local/tmp/pb_${tag}_$stamp.sh"
        val qLocal = shellQuote(localScript.absolutePath)
        val qRoot = shellQuote(rootScriptPath)
        val pidHeader = if (trackPid) {
            "echo \"\$\$\" > $ACTIVE_BUILD_PID_FILE\ntrap 'rm -f $ACTIVE_BUILD_PID_FILE' EXIT\n"
        } else ""
        return try {
            localScript.writeText("#!/system/bin/sh\n$pidHeader$scriptBody\n", Charsets.UTF_8)
            val prep = Shell.cmd("cp -f $qLocal $qRoot 2>&1 && chmod 700 $qRoot 2>&1").exec()
            if (!prep.isSuccess) return prep
            Shell.cmd("sh $qRoot 2>&1").exec()
        } finally {
            localScript.delete()
            val rmPid = if (trackPid) " $ACTIVE_BUILD_PID_FILE" else ""
            Shell.cmd("rm -f $qRoot$rmPid 2>/dev/null || true").exec()
        }
    }

    /**
     * Clean-flush unmount and loop-detach helper used by Windows, Linux, and Multi-Boot builders.
     * Flushes dirty page caches and waits up to 10s for real umount (allowing ntfs-3g/ntfs3 writeback
     * to finish cleanly before USB Gadget binds the .img), falling back to lazy umount only if still busy.
     */
    fun unmountAndDetachClean(
        mountPoints: List<String>,
        loopDevices: List<String>,
        removeDirs: Boolean = true
    ) {
        val sb = StringBuilder()
        sb.appendLine("sync 2>/dev/null || true")
        mountPoints.filter { it.isNotBlank() }.forEach { mp ->
            val qMp = shellQuote(mp)
            sb.appendLine(
                """
                M=$qMp
                M_ESC=${'$'}(printf '%s' "${'$'}M" | sed 's/ /\\040/g')
                if [ -d "${'$'}M" ]; then
                    TRIES=0
                    while [ "${'$'}TRIES" -lt 10 ]; do
                        sync 2>/dev/null || true
                        if umount "${'$'}M" 2>/dev/null; then break; fi
                        if ! grep -qsF " ${'$'}M_ESC " /proc/mounts 2>/dev/null; then break; fi
                        sleep 1
                        TRIES=${'$'}((TRIES + 1))
                    done
                    if grep -qsF " ${'$'}M_ESC " /proc/mounts 2>/dev/null; then
                        umount -l "${'$'}M" 2>/dev/null || true
                        sleep 1
                    fi
                fi
                """.trimIndent()
            )
        }
        loopDevices.filter { it.isNotBlank() }.forEach { dev ->
            val qDev = shellQuote(dev)
            sb.appendLine(
                """
                L=$qDev
                if [ -n "${'$'}L" ]; then
                    DT=0
                    while [ "${'$'}DT" -lt 4 ]; do
                        if losetup -d "${'$'}L" 2>/dev/null; then break; fi
                        sleep 1
                        DT=${'$'}((DT + 1))
                    done
                fi
                """.trimIndent()
            )
        }
        if (removeDirs) {
            val dirs = mountPoints.filter { it.isNotBlank() }.joinToString(" ") { shellQuote(it) }
            if (dirs.isNotBlank()) sb.appendLine("rm -rf $dirs 2>/dev/null || true")
        }
        sb.appendLine("sync 2>/dev/null || true")
        Shell.cmd(sb.toString()).exec()
    }

    fun inspectWindowsTools(context: Context): WindowsToolsStatus = WindowsToolsStatus(
        sevenZip = WindowsImageBuilder.findBundledBinary(context, "lib7zzs.so", listOf("7zzs", "7zz", "7z")),
        wimlib = WindowsImageBuilder.findBundledBinary(context, "libwimlib_imagex.so", listOf("wimlib-imagex")),
        chntpw = WindowsImageBuilder.findBundledBinary(context, "libchntpw.so", listOf("chntpw", "reged"))
    )
}
