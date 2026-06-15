package com.bcon.messenger

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf

/**
 * Реактивное хранилище аватаров контактов.
 * mutableStateMapOf — Compose-observable: любой composable, читающий avatars[id],
 * автоматически перерисуется при изменении значения.
 * Все записи должны производиться из главного потока (Dispatchers.Main).
 */
object AvatarStore {
    /** contactUserId → декодированный Bitmap */
    val avatars = mutableStateMapOf<String, Bitmap>()
}
