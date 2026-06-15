package com.bcon.messenger

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONObject

/**
 * FCM-сервис для пробуждения мессенджера при получении silent push.
 *
 * Требования для активации FCM:
 *  1. Создай проект в Firebase Console (https://console.firebase.google.com)
 *  2. Добавь Android-приложение (package: com.bcon.messenger)
 *  3. Скачай google-services.json → положи в app/
 *  4. В app/build.gradle.kts добавь в plugins:
 *       id("com.google.gms.google-services")
 *     и в dependencies:
 *       implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
 *       implementation("com.google.firebase:firebase-messaging-ktx")
 *  5. В корневом build.gradle.kts (в plugins):
 *       id("com.google.gms.google-services") version "4.4.1" apply false
 *  6. На сервере установи:
 *       pip install aiohttp
 *     и задай переменную FCM_SERVER_KEY=<твой_Legacy_Server_Key>
 *     (или настрой firebase-admin SDK — см. server.py)
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCM"
    }

    /**
     * Вызывается при обновлении FCM-токена.
     * Сохраняем и отправляем на сервер при следующем подключении.
     */
    override fun onNewToken(token: String) {
        // Сохраняем локально
        getSharedPreferences("fcm_prefs", MODE_PRIVATE)
            .edit().putString("fcm_token", token).apply()
        // Отправляем на сервер если сервис уже запущен
        sendTokenToServer(token)
    }

    /**
     * Вызывается при получении FCM-сообщения (в т.ч. silent push когда приложение в фоне).
     * Запускаем MessengerService чтобы он подключился к WebSocket и получил накопленные сообщения.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val type = remoteMessage.data["type"]
        if (type == "wakeup" || type == null) {
            // Запускаем сервис — он подключится и заберёт очередь с сервера
            try {
                val intent = Intent(this, MessengerService::class.java)
                startService(intent)
                Log.d(TAG, "MessengerService запущен по FCM wakeup")
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось запустить MessengerService: ${e.message}")
            }
        }
    }

    private fun sendTokenToServer(token: String) {
        try {
            val intent = Intent(this, MessengerService::class.java).apply {
                putExtra("fcm_token", token)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "sendTokenToServer error: ${e.message}")
        }
    }
}
