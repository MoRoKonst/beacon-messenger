package com.bcon.messenger

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object SignatureValidator {

    // SHA-256 отпечаток твоего production сертификата
    // Заполним после первой сборки
    private const val EXPECTED_SIGNATURE = "21f0e8f3b8f587dfea8fad0a6a65843f5c930a741847bfbf6e5bd62e89c08deb"

    fun isValidSignature(context: Context): Boolean {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            signatures?.forEach { signature ->
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(signature.toByteArray())
                val currentSignature = hash.joinToString("") { "%02x".format(it) }

                if (EXPECTED_SIGNATURE == "PLACEHOLDER") {
                    // В режиме разработки всегда true
                    return true
                }

                if (currentSignature == EXPECTED_SIGNATURE) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            android.util.Log.e("SignatureValidator", "Error checking signature", e)
            false
        }
    }
}