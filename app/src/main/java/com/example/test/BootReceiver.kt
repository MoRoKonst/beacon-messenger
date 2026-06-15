package com.bcon.messenger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Запускает MessengerService после перезагрузки устройства,
 * чтобы пользователь продолжал получать сообщения без открытия приложения.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Запускаем только если пользователь уже залогинен
            val username = UserStorage.getUsername(context)
            if (!username.isNullOrEmpty()) {
                val serviceIntent = Intent(context, MessengerService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            // Перепланировать DMS-таймер после перезагрузки
            if (DeadMansSwitchManager.isEnabled(context)) {
                DeadMansSwitchManager.scheduleFireAlarm(context)
            }
        }
    }
}
