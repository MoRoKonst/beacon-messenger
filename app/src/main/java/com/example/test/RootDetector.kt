package com.bcon.messenger

import android.os.Build
import com.bcon.messenger.BuildConfig
import java.io.File

object RootDetector {

    // Уровень угрозы — используется в UI
    enum class RootLevel {
        NONE,     // чисто
        WARNING,  // подозрительно, но не критично
        DANGER    // рут/инструментация/эмулятор подтверждены
    }

    data class RootCheckResult(
        val level: RootLevel,
        val reasons: List<String>
    )

    fun isDeviceRooted(): Boolean {
        return checkResult().level != RootLevel.NONE
    }

    fun checkResult(): RootCheckResult {
        val reasons = mutableListOf<String>()

        // ── Root ──────────────────────────────────────────────────────────────
        if (checkRootBinaries())    reasons.add("su бинарник найден")
        if (checkBuildTags())       reasons.add("test-keys сборка")
        if (checkRootPackages())    reasons.add("root-приложение установлено")
        if (checkWhichSu())         reasons.add("which su вернул результат")
        if (checkSystemWritable())  reasons.add("/system доступен на запись")

        // ── Anti-Debugging (только в release-сборках) ─────────────────────────
        if (!BuildConfig.DEBUG) {
            if (checkDebuggerConnected()) reasons.add("JDWP-отладчик подключён (adb/Android Studio)")
            if (checkTracerPid())         reasons.add("процесс трассируется (GDB/strace/LLDB)")
        }

        // ── Anti-Frida ────────────────────────────────────────────────────────
        if (checkFridaPort())       reasons.add("Frida-сервер активен (порт 27042)")
        if (checkFridaMaps())       reasons.add("Frida-агент найден в памяти процесса")
        if (checkFridaProcesses())  reasons.add("Frida-процесс обнаружен")

        // ── Anti-Xposed / LSPosed ─────────────────────────────────────────────
        if (checkXposedBridge())    reasons.add("Xposed/LSPosed активен в рантайме")
        if (checkXposedPackages())  reasons.add("Xposed/LSPosed менеджер установлен")
        if (checkXposedMaps())      reasons.add("XposedBridge.jar найден в памяти процесса")

        // ── Emulator / Sandbox ────────────────────────────────────────────────
        val emuScore = emulatorScore()
        if (emuScore >= 4)          reasons.add("эмулятор (высокая уверенность, score=$emuScore)")
        else if (emuScore >= 2)     reasons.add("возможно эмулятор (score=$emuScore)")

        val level = when {
            reasons.isEmpty()  -> RootLevel.NONE
            reasons.size == 1  -> RootLevel.WARNING
            else               -> RootLevel.DANGER
        }

        return RootCheckResult(level, reasons)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ROOT
    // ═══════════════════════════════════════════════════════════════════════════

    private fun checkRootBinaries(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        return paths.any { File(it).exists() }
    }

    private fun checkBuildTags(): Boolean {
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }

    private fun checkRootPackages(): Boolean {
        val packages = arrayOf(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",
            "com.fox2code.mmm"
        )
        return packages.any { isPackageInstalled(it) }
    }

    private fun checkWhichSu(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val result = process.inputStream.bufferedReader().readText().trim()
            process.destroy()
            result.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    private fun checkSystemWritable(): Boolean {
        return try {
            val testFile = File("/system/.beacon_rw_test")
            if (testFile.createNewFile()) { testFile.delete(); true } else false
        } catch (e: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ANTI-DEBUGGING
    // ═══════════════════════════════════════════════════════════════════════════

    /** JDWP — Java Debug Wire Protocol. Активен при подключении adb / Android Studio. */
    private fun checkDebuggerConnected(): Boolean =
        android.os.Debug.isDebuggerConnected()

    /**
     * TracerPid > 0 означает, что к процессу прикреплён нативный отладчик
     * (GDB, LLDB, strace). Читаем из /proc/self/status.
     */
    private fun checkTracerPid(): Boolean = try {
        File("/proc/self/status").readLines()
            .firstOrNull { it.startsWith("TracerPid:") }
            ?.substringAfter("TracerPid:")
            ?.trim()
            ?.toIntOrNull()
            ?.let { it > 0 }
            ?: false
    } catch (e: Exception) {
        false
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ANTI-FRIDA
    // ═══════════════════════════════════════════════════════════════════════════

    /** Frida-server по умолчанию слушает порт 27042. */
    private fun checkFridaPort(): Boolean {
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("127.0.0.1", 27042), 80)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Frida инжектирует gum-js и frida-agent в адресное пространство процесса. */
    private fun checkFridaMaps(): Boolean {
        return try {
            File("/proc/self/maps").readText().let { maps ->
                maps.contains("frida", ignoreCase = true) ||
                maps.contains("gum-js", ignoreCase = true) ||
                maps.contains("linjector", ignoreCase = true)
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Ищем frida-server/frida-inject в списке процессов. */
    private fun checkFridaProcesses(): Boolean {
        return try {
            File("/proc").listFiles()?.any { dir ->
                if (!dir.isDirectory) return@any false
                try {
                    File(dir, "cmdline").readText()
                        .contains("frida", ignoreCase = true)
                } catch (e: Exception) { false }
            } == true
        } catch (e: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ANTI-XPOSED / LSPOSED
    // ═══════════════════════════════════════════════════════════════════════════

    /** Если XposedBridge загружен — хукинг активен прямо сейчас. */
    private fun checkXposedBridge(): Boolean {
        return try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (e: ClassNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /** Менеджеры Xposed/LSPosed в системе. */
    private fun checkXposedPackages(): Boolean {
        val packages = arrayOf(
            "de.robv.android.xposed.installer",
            "io.github.lsposed.manager",
            "org.lsposed.manager",
            "com.solohsu.android.edxp.manager",  // EdXposed
            "me.weishu.exp"                       // VirtualXposed
        )
        return packages.any { isPackageInstalled(it) }
    }

    /** XposedBridge.jar появляется в /proc/self/maps при активном Xposed. */
    private fun checkXposedMaps(): Boolean {
        return try {
            File("/proc/self/maps").readText().contains("XposedBridge.jar")
        } catch (e: Exception) {
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EMULATOR / SANDBOX
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Каждый признак добавляет +1 к счёту.
     * Score ≥ 4 → высокая уверенность, Score ≥ 2 → возможно эмулятор.
     */
    private fun emulatorScore(): Int {
        var score = 0

        // Build.FINGERPRINT — самый надёжный признак
        val fp = Build.FINGERPRINT.lowercase()
        if (fp.startsWith("generic"))            score += 2
        if (fp.contains(":sdk_gphone"))          score += 2
        if (fp.contains("generic/sdk"))          score += 2
        if (fp.contains("emulator"))             score++

        // Аппаратная платформа эмулятора
        val hw = Build.HARDWARE.lowercase()
        if (hw == "goldfish" || hw == "ranchu")  score += 2

        // Модель
        val model = Build.MODEL.lowercase()
        if (model.contains("emulator"))          score += 2
        if (model.contains("android sdk built")) score += 2
        if (model.contains("google_sdk"))        score += 2

        // Производитель
        if (Build.MANUFACTURER.equals("unknown", ignoreCase = true)) score++

        // Product / Board
        val product = Build.PRODUCT.lowercase()
        if (product.startsWith("sdk") ||
            product.contains("_sdk") ||
            product.contains("sdk_") ||
            product.contains("emulator"))        score++

        if (Build.BOARD.equals("unknown", ignoreCase = true)) score++

        // Brand
        val brand = Build.BRAND.lowercase()
        if (brand.startsWith("generic") ||
            brand == "android")                  score++

        // Характерные файлы эмуляторов
        val emuFiles = arrayOf(
            "/dev/socket/qemud",      // QEMU
            "/dev/qemu_pipe",         // QEMU
            "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace",
            "/system/bin/qemu-props"
        )
        if (emuFiles.any { File(it).exists() })  score += 2

        return score
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════════════════════════════════════

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", packageName))
            val result = process.inputStream.bufferedReader().readText().trim()
            process.destroy()
            result.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
