package com.example.telecom

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log

/**
 * Official Default Dialer / Phone Role Manager.
 *
 * Implements Android's official RoleManager API (Android 10+ / API 29+)
 * with backwards-compatible fallback to TelecomManager for pre-Q devices.
 */
object DefaultDialerManager {

    private const val TAG = "DefaultDialerManager"

    /**
     * Checks if NovaDial is currently set as the system default phone/dialer application.
     * On Android 10+ (API 29+), uses RoleManager.isRoleHeld(RoleManager.ROLE_DIALER).
     * On earlier versions or fallback, checks TelecomManager.getDefaultDialerPackage().
     */
    fun isDefaultDialer(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
                } else {
                    checkTelecomManagerDefault(context)
                }
            } else {
                checkTelecomManagerDefault(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking default dialer status", e)
            checkTelecomManagerDefault(context)
        }
    }

    /**
     * Checks if NovaDial is default dialer via TelecomManager.
     */
    fun checkTelecomManagerDefault(context: Context): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val currentDefault = telecomManager?.defaultDialerPackage
            currentDefault == context.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error querying TelecomManager default dialer", e)
            false
        }
    }

    /**
     * Gets the package name of the device's currently configured default phone/dialer app.
     */
    fun getCurrentDefaultDialerPackage(context: Context): String? {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return telecomManager?.defaultDialerPackage
    }

    /**
     * Creates the official system Intent to prompt the user to set NovaDial as the default phone app.
     *
     * Uses RoleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER) on Android 10+,
     * or TelecomManager.ACTION_CHANGE_DEFAULT_DIALER with extra package name on earlier versions.
     */
    fun createRequestDefaultDialerIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            } else {
                legacyChangeDefaultDialerIntent(context)
            }
        } else {
            legacyChangeDefaultDialerIntent(context)
        }
    }

    private fun legacyChangeDefaultDialerIntent(context: Context): Intent {
        @Suppress("DEPRECATION")
        return Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
        }
    }

    /**
     * Launches the official role-selection flow to set NovaDial as the default phone app.
     */
    fun requestDefaultDialer(context: Context): Boolean {
        return try {
            val intent = createRequestDefaultDialerIntent(context)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch default dialer request intent via primary method", e)
            try {
                // Fallback 1: Legacy TelecomManager Intent
                val legacyIntent = legacyChangeDefaultDialerIntent(context)
                if (context !is Activity) legacyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(legacyIntent)
                true
            } catch (ex: Exception) {
                Log.e(TAG, "Failed legacy default dialer intent, launching Default Apps Settings", ex)
                try {
                    // Fallback 2: System Default Apps Settings screen
                    val settingsIntent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    true
                } catch (exc: Exception) {
                    Log.e(TAG, "Failed to open settings", exc)
                    false
                }
            }
        }
    }
}
