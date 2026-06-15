package com.bcon.messenger

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Файл-приманка (honeytoken): выглядит как кэш сессии, но никогда не
 * используется реальным кодом. Если содержимое изменилось — кто-то
 * вмешался в хранилище (Frida, ADB backup + restore, физический доступ).
 */
object HoneyTokenManager {

    private const val HONEY_FILE = "session_cache.dat"

    /** Вызывать в onCreate. Создаёт файл при первом запуске. */
    fun init(context: Context) {
        val file = File(context.filesDir, HONEY_FILE)
        if (!file.exists()) {
            val data = generateFakeData()
            file.writeBytes(data)
            UserStorage.setHoneyHash(context, sha256hex(data))
        }
    }

    /**
     * Возвращает true если файл не тронут.
     * false = файл изменён или удалён → угроза.
     */
    fun checkIntegrity(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, HONEY_FILE)
            if (!file.exists()) return false
            val expected = UserStorage.getHoneyHash(context)
            if (expected.isEmpty()) return true  // hash not yet saved → skip
            sha256hex(file.readBytes()) == expected
        } catch (_: Exception) { true }  // на ошибку чтения не паниковать
    }

    private fun generateFakeData(): ByteArray {
        val rng = SecureRandom()
        val fakeKey = ByteArray(32).also { rng.nextBytes(it) }
        val fakeIv = ByteArray(16).also { rng.nextBytes(it) }
        val fakeMac = ByteArray(32).also { rng.nextBytes(it) }
        val payload = buildString {
            appendLine("BCON_SESSION_CACHE_V1")
            appendLine("key=${Base64.encodeToString(fakeKey, Base64.NO_WRAP)}")
            appendLine("iv=${Base64.encodeToString(fakeIv, Base64.NO_WRAP)}")
            appendLine("mac=${Base64.encodeToString(fakeMac, Base64.NO_WRAP)}")
            appendLine("ts=${System.currentTimeMillis()}")
        }
        return payload.toByteArray(Charsets.UTF_8)
    }

    private fun sha256hex(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data).joinToString("") { "%02x".format(it) }
    }
}
