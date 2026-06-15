package com.bcon.messenger

object SupportConfig {

    // Fingerprint аккаунта поддержки (из invite code: fp=...)
    const val FINGERPRINT = "D97FAC74456B5DC1"

    // Публичный ключ аккаунта поддержки (стандартный base64, '-'→'+', '_'→'/')
    const val PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEB8MWqC9i5B2bZNi/GTRtG4zhg7qk3hXzoEsR3ZW+KEIl4/KoezEbIHJIpkpRqUIt3prcEcfsbHVLcz2ZlXf5+w=="

    // Имя, под которым контакт поддержки будет отображаться
    const val NAME = "Команда B-CON"

    // Заголовок диалога
    const val DIALOG_TITLE = "Поддержка"

    // Текст диалога
    const val DIALOG_TEXT = "Если возникли вопросы или проблемы при использовании B-CON, свяжитесь с командой B-CON."

    val isConfigured: Boolean = true
}
