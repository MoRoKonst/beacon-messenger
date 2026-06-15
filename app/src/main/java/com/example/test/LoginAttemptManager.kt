package com.bcon.messenger

import android.content.Context

object LoginAttemptManager {

    private const val PREFS_NAME = "login_attempts"
    private const val KEY_ATTEMPTS = "attempts"
    private const val KEY_BLOCK_UNTIL = "block_until"
    private const val MAX_ATTEMPTS = 3
    private const val BLOCK_DURATION_MS = 5 * 60 * 1000L // 5 минут

    fun canAttemptLogin(context: Context): Pair<Boolean, Long> {
        val prefs = EncryptedStorage.getEncryptedPrefs(context, PREFS_NAME)
        val blockUntil = prefs.getLong(KEY_BLOCK_UNTIL, 0)
        val now = System.currentTimeMillis()

        if (blockUntil > now) {
            val remainingSeconds = (blockUntil - now) / 1000
            return Pair(false, remainingSeconds)
        }

        return Pair(true, 0)
    }

    fun recordFailedAttempt(context: Context) {
        val prefs = EncryptedStorage.getEncryptedPrefs(context, PREFS_NAME)
        val attempts = prefs.getInt(KEY_ATTEMPTS, 0) + 1

        if (attempts >= MAX_ATTEMPTS) {
            // Блокируем на 5 минут
            prefs.edit()
                .putInt(KEY_ATTEMPTS, 0)
                .putLong(KEY_BLOCK_UNTIL, System.currentTimeMillis() + BLOCK_DURATION_MS)
                .apply()
        } else {
            prefs.edit()
                .putInt(KEY_ATTEMPTS, attempts)
                .apply()
        }
    }

    fun recordSuccessfulLogin(context: Context) {
        val prefs = EncryptedStorage.getEncryptedPrefs(context, PREFS_NAME)
        prefs.edit()
            .putInt(KEY_ATTEMPTS, 0)
            .putLong(KEY_BLOCK_UNTIL, 0)
            .apply()
    }

    fun getRemainingAttempts(context: Context): Int {
        val prefs = EncryptedStorage.getEncryptedPrefs(context, PREFS_NAME)
        val attempts = prefs.getInt(KEY_ATTEMPTS, 0)
        return MAX_ATTEMPTS - attempts
    }
}