package com.bcon.messenger

import android.util.Base64
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.util.UUID

object InviteCodeManager {

    // Инвайт-код действителен 7 дней
    private const val INVITE_TTL_SECONDS = 7L * 24 * 3600

    fun generateInviteCode(publicKey: PublicKey, privateKey: PrivateKey, displayName: String): String {
        val keyBytes = publicKey.encoded
        val keyBase64 = Base64.encodeToString(keyBytes, Base64.URL_SAFE or Base64.NO_WRAP)

        val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
        val fingerprint = digest.take(8).joinToString("") { "%02X".format(it) }

        val nonce = UUID.randomUUID().toString().replace("-", "").take(16)
        val ts = System.currentTimeMillis() / 1000

        // Кодируем имя
        val nameBase64 = Base64.encodeToString(displayName.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)

        // Подпись: sign(key + fp + nonce + name + ts)
        val dataToSign = "$keyBase64$fingerprint$nonce$nameBase64$ts"
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(dataToSign.toByteArray())
        }.sign()
        val sigBase64 = Base64.encodeToString(signature, Base64.URL_SAFE or Base64.NO_WRAP)

        return "beacon://invite?key=$keyBase64&fp=$fingerprint&nonce=$nonce&name=$nameBase64&ts=$ts&sig=$sigBase64"
    }

    fun parseInviteCode(inviteCode: String): InviteData? {
        return try {
            val uri = android.net.Uri.parse(inviteCode.trim())
            val key = uri.getQueryParameter("key") ?: return null
            val fp = uri.getQueryParameter("fp") ?: return null
            val nonce = uri.getQueryParameter("nonce") ?: return null
            val sig = uri.getQueryParameter("sig") ?: return null
            val nameBase64 = uri.getQueryParameter("name") ?: return null
            val ts = uri.getQueryParameter("ts")?.toLongOrNull()  // null для старых кодов

            val name = String(Base64.decode(nameBase64, Base64.URL_SAFE))

            InviteData(key, fp, nonce, sig, name, ts)
        } catch (e: Exception) {
            null
        }
    }

    fun verifyInviteCode(inviteData: InviteData): Boolean {
        return try {
            val keyBytes = Base64.decode(inviteData.publicKey, Base64.URL_SAFE)
            val publicKey = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(keyBytes))

            // Проверяем fingerprint
            val digest = MessageDigest.getInstance("SHA-256").digest(keyBytes)
            val expectedFp = digest.take(8).joinToString("") { "%02X".format(it) }
            if (expectedFp != inviteData.fingerprint) return false

            // Проверяем TTL (только если ts присутствует — обратная совместимость)
            if (inviteData.timestamp != null) {
                val now = System.currentTimeMillis() / 1000
                if (now - inviteData.timestamp > INVITE_TTL_SECONDS) return false
                if (inviteData.timestamp > now + 300) return false  // код из будущего (>5 мин) — отклоняем
            }

            // Проверяем подпись (поддержка старого формата без ts)
            val nameBase64 = Base64.encodeToString(inviteData.displayName.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
            val dataToVerify = if (inviteData.timestamp != null)
                "${inviteData.publicKey}${inviteData.fingerprint}${inviteData.nonce}$nameBase64${inviteData.timestamp}"
            else
                "${inviteData.publicKey}${inviteData.fingerprint}${inviteData.nonce}$nameBase64"

            val sigBytes = Base64.decode(inviteData.signature, Base64.URL_SAFE)

            Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(dataToVerify.toByteArray())
            }.verify(sigBytes)
        } catch (e: Exception) {
            false
        }
    }

    data class InviteData(
        val publicKey: String,
        val fingerprint: String,
        val nonce: String,
        val signature: String,
        val displayName: String,
        val timestamp: Long? = null  // null = старый формат, TTL не проверяется
    )
}
