package com.bcon.messenger

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKeys
import java.io.File

/**
 * Шифрованное файловое хранилище.
 * Все медиафайлы (фото, голосовые, файлы) хранятся зашифрованными
 * с использованием AES256-GCM через Android KeyStore.
 *
 * Файлы с шифрованием имеют расширение .enc
 * Файлы без .enc — legacy, читаются как есть (обратная совместимость).
 */
object SecureFileStorage {

    private fun masterKeyAlias(): String =
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    /** Записать байты в зашифрованный файл */
    fun write(context: Context, file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()   // EncryptedFile требует, чтобы файла не существовало
        EncryptedFile.Builder(
            file,
            context,
            masterKeyAlias(),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build().openFileOutput().use { it.write(bytes) }
    }

    /** Прочитать байты из зашифрованного файла */
    fun read(context: Context, file: File): ByteArray =
        EncryptedFile.Builder(
            file,
            context,
            masterKeyAlias(),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build().openFileInput().use { it.readBytes() }

    /**
     * Расшифровать .enc файл во временный файл в cacheDir.
     * Используется для MediaPlayer и FileProvider (которым нужен путь к файлу).
     * Temp файл удаляется автоматически через cleanupTemp().
     */
    fun decryptToTemp(context: Context, encFile: File, tempFileName: String): File {
        val bytes = read(context, encFile)
        val temp = File(context.cacheDir, "tmp_$tempFileName")
        temp.writeBytes(bytes)
        return temp
    }

    /** Удалить все временные файлы из cacheDir */
    fun cleanupTemp(context: Context) {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("tmp_")) file.delete()
        }
    }
}
