package com.bcon.messenger

import android.content.Context
import android.media.MediaRecorder
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import java.io.File

object AudioHelper {

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private val tempPlaybackFiles = mutableListOf<File>()

    /** Начать запись — пишет в незашифрованный temp файл в cacheDir */
    fun startRecording(context: Context): File {
        val outputFile = File(context.cacheDir, "voice_rec_temp.3gp")
        if (outputFile.exists()) outputFile.delete()

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        return outputFile
    }

    /** Остановить запись (файл остаётся в cacheDir как temp) */
    fun stopRecording(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            null
        } catch (e: Exception) {
            Log.e("AudioHelper", "Stop recording error: ${e.message}")
            null
        }
    }

    /** Прочитать файл как Base64 (используется для отправки — читает незашифрованный temp) */
    fun encodeToBase64(file: File): String {
        val bytes = file.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Сохранить принятое голосовое сообщение зашифрованным.
     * Возвращает .enc файл в filesDir.
     */
    fun decodeAndSave(context: Context, base64: String, voiceId: String): File {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val encFile = File(context.filesDir, "voice_$voiceId.3gp.enc")
        SecureFileStorage.write(context, encFile, bytes)
        return encFile
    }

    /**
     * Воспроизвести голосовое сообщение.
     * Если файл .enc — расшифровывает во temp и играет оттуда.
     * Temp файл удаляется по завершении или при stopAudio().
     */
    fun playAudio(context: Context, file: File?, onComplete: () -> Unit) {
        if (file == null) return
        stopAudio()

        val playFile = if (file.name.endsWith(".enc")) {
            try {
                val temp = SecureFileStorage.decryptToTemp(context, file, file.name.removeSuffix(".enc"))
                tempPlaybackFiles.add(temp)
                temp
            } catch (e: Exception) {
                Log.e("AudioHelper", "Decrypt for playback failed: ${e.message}")
                return
            }
        } else file

        player = MediaPlayer().apply {
            setDataSource(playFile.absolutePath)
            setOnCompletionListener {
                onComplete()
                cleanupTempPlayback()
            }
            prepare()
            start()
        }
    }

    fun stopAudio() {
        try {
            player?.apply { stop(); release() }
        } catch (_: Exception) {}
        player = null
        cleanupTempPlayback()
    }

    private fun cleanupTempPlayback() {
        tempPlaybackFiles.forEach { if (it.exists()) it.delete() }
        tempPlaybackFiles.clear()
    }
}
