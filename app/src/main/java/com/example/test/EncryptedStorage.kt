package com.bcon.messenger

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object EncryptedStorage {

    private const val TAG = "EncryptedStorage"

    /**
     * Открыть зашифрованное хранилище.
     *
     * Стратегия отказоустойчивости:
     * 1. Первая попытка — обычное открытие EncryptedSharedPreferences.
     * 2. Если не удалось — ЖДЁМ 300 мс и повторяем. Это устраняет временные сбои
     *    (напр., AndroidKeyStore недоступен при быстром стоп/старт сервиса).
     * 3. Только при УСТОЙЧИВОЙ ошибке (обе попытки провалились) — удаляем и пересоздаём.
     *    Файл повреждён необратимо, данные всё равно недоступны.
     * 4. Если пересоздание не удалось — fallback на обычный SharedPreferences.
     *
     * ⚠️  НЕ удаляем файл при первой же ошибке: при перезапуске сервиса AndroidKeyStore
     *     может кратковременно не отвечать — это временная, не постоянная, проблема.
     */
    fun getEncryptedPrefs(context: Context, name: String): SharedPreferences {
        fun tryCreate(): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                name,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        // Попытка 1 — нормальный путь
        try {
            return tryCreate()
        } catch (e: Exception) {
            Log.w(TAG, "[$name] Попытка 1 не удалась: ${e.message}")
        }

        // Попытка 2 — пауза 300 мс, затем повтор (временный сбой KeyStore при рестарте сервиса)
        try {
            Thread.sleep(300)
            return tryCreate()
        } catch (e: Exception) {
            Log.w(TAG, "[$name] Попытка 2 не удалась: ${e.message}")
        }

        // Попытка 3 — устойчивая ошибка, файл повреждён: удаляем и пересоздаём
        Log.e(TAG, "[$name] Устойчивая ошибка — пересоздаём (данные недоступны в любом случае)")
        try {
            context.deleteSharedPreferences(name)
            return tryCreate()
        } catch (e: Exception) {
            // НЕ делаем fallback на незашифрованный SharedPreferences:
            // молчаливое хранение приватных ключей в открытом виде — критическая уязвимость.
            // Вызывающий код должен обработать исключение и показать пользователю ошибку.
            Log.e(TAG, "[$name] Зашифрованное хранилище недоступно: ${e.message}")
            throw SecurityException("Зашифрованное хранилище '$name' недоступно. Возможно, AndroidKeyStore повреждён.", e)
        }
    }
}
