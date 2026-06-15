package com.bcon.messenger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver для событий Dead Man's Switch.
 *
 * DMS_FIRE    — таймер истёк: показать предупреждение + запланировать wipe через grace period
 * DMS_WIPE    — grace period истёк: выполнить уничтожение данных
 * DMS_CHECKIN — пользователь нажал «Я в безопасности»: сбросить таймер
 */
class WipeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DeadMansSwitchManager.ACTION_DMS_FIRE -> {
                // Показываем предупреждение и даём grace period
                DeadMansSwitchManager.showWarningNotification(context)
                DeadMansSwitchManager.scheduleWipeAlarm(context, DeadMansSwitchManager.GRACE_PERIOD_MS)
            }

            DeadMansSwitchManager.ACTION_DMS_WIPE -> {
                // Grace period истёк — уничтожаем данные
                DeadMansSwitchManager.dismissWarningNotification(context)
                WipeManager.wipe(context, WipeManager.Level.NUCLEAR)
            }

            DeadMansSwitchManager.ACTION_DMS_CHECKIN -> {
                // Пользователь подтвердил безопасность из уведомления
                DeadMansSwitchManager.checkIn(context)
            }

            PanicNotificationManager.ACTION_EMERGENCY_WIPE -> {
                PanicNotificationManager.dismiss(context)
                if (UserStorage.getPanicButtonDecoy(context)) {
                    // Decoy-режим: показать фейковый экран, стереть данные в фоне
                    ParanoidMode.activateFromNotification()
                    // Открыть MainActivity чтобы DecoyScreen отобразился
                    val launchIntent = context.packageManager
                        .getLaunchIntentForPackage(context.packageName)
                        ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP }
                    if (launchIntent != null) context.startActivity(launchIntent)
                    // Вайп в фоне через 600 мс (процесс остаётся живым)
                    CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                        kotlinx.coroutines.delay(600)
                        WipeManager.wipeForDecoyKeepAlive(context)
                    }
                } else {
                    // Жёсткий вайп — немедленно, процесс убивается
                    WipeManager.wipe(context, WipeManager.Level.HARD)
                }
            }
        }
    }
}
