package com.wusper.zeyao.ui.lock

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.wusper.zeyao.R

object BiometricGate {
    fun canAuthenticate(context: Context): Boolean = runCatching {
        val manager = BiometricManager.from(context)
        manager.canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS
    }.getOrDefault(false)

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: () -> Unit = {},
    ) {
        runCatching {
            if (!canAuthenticate(activity)) {
                onError()
                return
            }
            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onError()
                    }

                    override fun onAuthenticationFailed() {
                        // Stay on the lock screen; the user can retry.
                    }
                },
            )
            prompt.authenticate(promptInfo(activity))
        }.onFailure { onError() }
    }

    /**
     * BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL is rejected on
     * some API levels by PromptInfo.Builder.build(). Keep the combination
     * conservative so a lock toggle cannot crash the activity.
     */
    private fun authenticators(): Int =
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

    private fun promptInfo(activity: FragmentActivity): BiometricPrompt.PromptInfo {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_unlock))
            .setSubtitle(activity.getString(R.string.biometric_unlock_hint))
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(authenticators())
        } else {
            builder.setAllowedAuthenticators(authenticators())
            builder.setNegativeButtonText(activity.getString(R.string.cancel))
        }
        return builder.build()
    }
}
