package com.bcon.messenger

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class EmergencyService : AccessibilityService() {

    private var volumeDownCount = 0
    private var lastPressTime = 0L
    private val TIMEOUT = 3000L
    private val DEBOUNCE_MS = 150L  // защита от дребезга кнопки
    private lateinit var wakeLock: android.os.PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    android.os.PowerManager.ON_AFTER_RELEASE,
            "Beacon::EmergencyWipe"
        )
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Проверяем первой строкой — если функция выключена, выходим сразу
        // без лишних вычислений currentTimeMillis и т.д.
        if (!UserStorage.isEmergencyWipeEnabled(applicationContext)) {
            return super.onKeyEvent(event)
        }

        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()

            // Защита от дребезга — игнорируем нажатие если оно слишком быстро после предыдущего
            if (now - lastPressTime < DEBOUNCE_MS) {
                return super.onKeyEvent(event)
            }

            // Сброс счётчика если прошло больше TIMEOUT
            if (now - lastPressTime > TIMEOUT) {
                volumeDownCount = 0
            }

            volumeDownCount++
            lastPressTime = now

            android.util.Log.d("EmergencyService", "Volume Down: $volumeDownCount/5")

            if (volumeDownCount >= 5) {
                triggerEmergencyWipe()
                volumeDownCount = 0
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    private fun triggerEmergencyWipe() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(10000)
            }

            // FLAG_RECEIVER_FOREGROUND — broadcast обработается с наивысшим приоритетом
            // даже если система под нагрузкой
            val intent = Intent("com.bcon.messenger.EMERGENCY_WIPE").apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            sendBroadcast(intent)

        } catch (e: Exception) {
            android.util.Log.e("EmergencyService", "Ошибка активации: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onServiceConnected() {
        super.onServiceConnected()
        android.util.Log.d("EmergencyService", "=== СЕРВИС ЗАПУЩЕН ===")
    }
}
