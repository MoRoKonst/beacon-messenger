package com.bcon.messenger.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Три темы приложения ──────────────────────────────────────────────────────
enum class BeaconTheme { NAVY, DARK, LIGHT }

// ─── Семантическая палитра ────────────────────────────────────────────────────
data class BeaconColors(
    val gradientStart: Color,       // верх фона
    val gradientEnd: Color,         // низ фона
    val topBar: Color,              // цвет TopAppBar
    val dialog: Color,              // фон диалогов / AlertDialog
    val accent: Color,              // акцент: кнопки, ссылки, активные элементы
    val textPrimary: Color,         // основной текст
    val card: Color,                // карточки, элементы списка
    val cardAlt: Color,             // альтернативный фон карточки
    val fieldBorder: Color,         // рамка полей ввода
    val bubbleOwn: Color,           // пузырёк своего сообщения
    val bubbleOther: Color,         // пузырёк чужого сообщения
    val bubbleSystem: Color,        // системное сообщение
    val dangerCard: Color,          // фон карточки с предупреждением
    val error: Color,               // текст ошибки
    val primaryBlue: Color,         // синий бренда (аватары, ссылки)
    val inputBg: Color,             // фон поля ввода
    val callBg: Color,              // фон ActiveCallScreen
    val callGradientEdge: Color,    // края градиента IncomingCallScreen
    val isDark: Boolean             // тёмная ли тема (для системного UI)
)

// ─── Синяя (текущая) ──────────────────────────────────────────────────────────
val NavyBeaconColors = BeaconColors(
    gradientStart    = Color(0xFF141e4a),
    gradientEnd      = Color(0xFF0d1238),
    topBar           = Color(0xFF091a66),
    dialog           = Color(0xFF091a66),
    accent           = Color(0xFF00E5FF),
    textPrimary      = Color(0xFFE0E6FF),
    card             = Color(0xFF1F2B5E),
    cardAlt          = Color(0xFF1A2550),
    fieldBorder      = Color(0xFF2A3B8F),
    bubbleOwn        = Color(0xFF2A3B8F),
    bubbleOther      = Color(0xFF1F2B5E),
    bubbleSystem     = Color(0xFF1A2550),
    dangerCard       = Color(0xFF2A1F1F),
    error            = Color(0xFFFF4444),
    primaryBlue      = Color(0xFF2481CC),
    inputBg          = Color(0xFF0d1238),
    callBg           = Color(0xFF050d26),
    callGradientEdge = Color(0xFF0a1040),
    isDark           = true
)

// ─── Тёмная (AMOLED) ──────────────────────────────────────────────────────────
val DarkBeaconColors = BeaconColors(
    gradientStart    = Color(0xFF1C1C1C),
    gradientEnd      = Color(0xFF0D0D0D),
    topBar           = Color(0xFF242424),
    dialog           = Color(0xFF2A2A2A),
    accent           = Color(0xFF00E5FF),
    textPrimary      = Color(0xFFFFFFFF),
    card             = Color(0xFF2A2A2A),
    cardAlt          = Color(0xFF222222),
    fieldBorder      = Color(0xFF444444),
    bubbleOwn        = Color(0xFF1A5FA8),
    bubbleOther      = Color(0xFF303030),
    bubbleSystem     = Color(0xFF1E1E1E),
    dangerCard       = Color(0xFF3A1A1A),
    error            = Color(0xFFFF4444),
    primaryBlue      = Color(0xFF2481CC),
    inputBg          = Color(0xFF1A1A1A),
    callBg           = Color(0xFF0A0A0A),
    callGradientEdge = Color(0xFF111111),
    isDark           = true
)

// ─── Светлая ──────────────────────────────────────────────────────────────────
val LightBeaconColors = BeaconColors(
    gradientStart    = Color(0xFFF0F4FF),
    gradientEnd      = Color(0xFFE4EDFF),
    topBar           = Color(0xFF2481CC),
    dialog           = Color(0xFFFFFFFF),
    accent           = Color(0xFF2481CC),
    textPrimary      = Color(0xFF1A1A2E),
    card             = Color(0xFFFFFFFF),
    cardAlt          = Color(0xFFF5F7FF),
    fieldBorder      = Color(0xFFAEC6EF),
    bubbleOwn        = Color(0xFF2481CC),
    bubbleOther      = Color(0xFFFFFFFF),
    bubbleSystem     = Color(0xFFF0F0F5),
    dangerCard       = Color(0xFFFFF0F0),
    error            = Color(0xFFCC2222),
    primaryBlue      = Color(0xFF2481CC),
    inputBg          = Color(0xFFF5F8FF),
    callBg           = Color(0xFFEEF3FF),
    callGradientEdge = Color(0xFFD0E0FF),
    isDark           = false
)

// ─── CompositionLocal ─────────────────────────────────────────────────────────
val LocalBeaconColors = compositionLocalOf<BeaconColors> { NavyBeaconColors }

fun beaconColorsFor(theme: BeaconTheme): BeaconColors = when (theme) {
    BeaconTheme.NAVY  -> NavyBeaconColors
    BeaconTheme.DARK  -> DarkBeaconColors
    BeaconTheme.LIGHT -> LightBeaconColors
}
