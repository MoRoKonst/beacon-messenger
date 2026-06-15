package com.bcon.messenger

/**
 * Конфигурация сетевой безопасности.
 *
 * Certificate pinning: привязывает приложение к конкретному TLS-сертификату сервера.
 * Даже если злоумышленник подменит сертификат через доверенный CA — соединение будет отклонено.
 *
 * Как получить хэш:
 *   openssl s_client -connect your-server.com:443 </dev/null 2>/dev/null \
 *     | openssl x509 -pubkey -noout \
 *     | openssl pkey -pubin -outform der \
 *     | openssl dgst -sha256 -binary | base64
 *
 * Или через браузер: DevTools → Security → View certificate → копируй SHA-256 fingerprint
 * и конвертируй в base64.
 *
 * Пример: const val CERT_PIN = "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
 *
 * Оставь CERT_PIN пустым — pinning отключён (режим разработки).
 */
object NetworkConfig {

    /**
     * SHA-256 pin публичного ключа сервера.
     * Формат: "sha256/BASE64_HASH="
     * Оставь "", чтобы отключить (не рекомендуется для production).
     */
    const val CERT_PIN = "" // Cloudflare ротирует сертификаты — pinning отключён, используется стандартная TLS валидация

    /**
     * Hostname сервера (должен совпадать с Common Name / SAN в сертификате).
     * Пример: "messenger.example.com"
     */
    const val SERVER_HOSTNAME = "api.beacon-app.org"

    // ─── TURN / STUN (WebRTC NAT traversal) ──────────────────────────────────
    // STUN используется для определения публичного IP.
    // TURN-учётные данные НЕ хранятся в APK — они доставляются сервером
    // после успешной аутентификации (сообщение "turn_config").
    // Установи на сервере переменные среды TURN_USER и TURN_PASS.
    const val STUN_URL = "stun:stun.l.google.com:19302"
    const val TURN_URL = "turn:turn.beacon-app.org:4433?transport=tcp"

    // ─── Динамические TURN-учётные данные (доставляются сервером) ────────────
    /**
     * TURN-credentials, полученные от сервера после ECDSA-аутентификации.
     * Хранятся ТОЛЬКО в памяти — не записываются на диск, не попадают в APK.
     * Если сервер ещё не прислал учётные данные — TURN недоступен,
     * WebRTC будет работать только через STUN (прямое P2P соединение).
     */
    object TurnCredentials {
        @Volatile var username: String = ""
        @Volatile var password: String = ""

        fun isAvailable() = username.isNotEmpty() && password.isNotEmpty()

        fun clear() {
            username = ""
            password = ""
        }
    }
}
