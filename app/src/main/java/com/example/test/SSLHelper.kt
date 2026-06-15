package com.bcon.messenger

/**
 * SSLHelper — ОТКЛЮЧЁН по соображениям безопасности.
 *
 * Прежняя функция getTrustAllSocketFactory() принимала ЛЮБЫЕ TLS-сертификаты,
 * в том числе поддельные, что делало приложение уязвимым к MITM-атакам:
 * злоумышленник мог перехватить трафик, подставив свой сертификат.
 *
 * Вместо trust-all используется certificate pinning в NetworkConfig / MessengerService:
 *   NetworkConfig.CERT_PIN  — SHA-256 публичного ключа сервера
 *   NetworkConfig.SERVER_HOSTNAME — hostname для pinning
 *
 * Класс оставлен как stub, чтобы компилятор сразу сигнализировал об ошибке
 * при любой попытке вызвать небезопасный метод.
 */
object SSLHelper {

    @Deprecated(
        message = "Trust-all SSL УЯЗВИМ к MITM. Используй certificate pinning (NetworkConfig.CERT_PIN).",
        level = DeprecationLevel.ERROR
    )
    @Suppress("DEPRECATION_ERROR")
    fun getTrustAllSocketFactory(): javax.net.ssl.SSLSocketFactory {
        throw UnsupportedOperationException(
            "getTrustAllSocketFactory() отключён: принятие всех сертификатов — УЯЗВИМОСТЬ БЕЗОПАСНОСТИ. " +
            "Используй certificate pinning через NetworkConfig.CERT_PIN."
        )
    }
}
