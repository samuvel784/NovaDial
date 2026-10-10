package com.example.telecom

import android.content.Context
import android.content.SharedPreferences

/**
 * Settings for Focus mode, call protection, VIP numbers and the caller announcer.
 * Stored in a private SharedPreferences file; read by the screening service and the announcer.
 */
object CallProtectionPrefs {
    private const val FILE = "novadial_call_protection"

    private const val KEY_FOCUS = "focus_mode"
    private const val KEY_PROTECTION = "protection_level"
    private const val KEY_ALLOW_REPEAT = "allow_repeat_callers"
    private const val KEY_VIP = "vip_numbers"

    private const val KEY_ANN_ENABLED = "announcer_enabled"
    private const val KEY_ANN_TEMPLATE = "announcer_template"
    private const val KEY_ANN_USER = "announcer_user_name"
    private const val KEY_ANN_REPEAT = "announcer_repeat"
    private const val KEY_ANN_SPEED = "announcer_speed_percent"
    private const val KEY_ANN_PITCH = "announcer_pitch_percent"
    private const val KEY_ANN_VOLUME = "announcer_volume_percent"
    private const val KEY_ANN_HEADPHONES = "announcer_headphones_only"
    private const val KEY_ANN_UNKNOWN = "announcer_unknown_numbers"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---- Focus and protection -------------------------------------------------

    fun getFocusMode(context: Context): FocusMode =
        runCatching { FocusMode.valueOf(prefs(context).getString(KEY_FOCUS, FocusMode.OFF.name) ?: FocusMode.OFF.name) }
            .getOrDefault(FocusMode.OFF)

    fun setFocusMode(context: Context, mode: FocusMode) {
        prefs(context).edit().putString(KEY_FOCUS, mode.name).apply()
    }

    fun getProtectionLevel(context: Context): ProtectionLevel =
        runCatching { ProtectionLevel.valueOf(prefs(context).getString(KEY_PROTECTION, ProtectionLevel.STANDARD.name) ?: ProtectionLevel.STANDARD.name) }
            .getOrDefault(ProtectionLevel.STANDARD)

    fun setProtectionLevel(context: Context, level: ProtectionLevel) {
        prefs(context).edit().putString(KEY_PROTECTION, level.name).apply()
    }

    fun isRepeatCallerAllowed(context: Context): Boolean = prefs(context).getBoolean(KEY_ALLOW_REPEAT, true)

    fun setRepeatCallerAllowed(context: Context, allowed: Boolean) {
        prefs(context).edit().putBoolean(KEY_ALLOW_REPEAT, allowed).apply()
    }

    // ---- VIP numbers ----------------------------------------------------------

    fun getVipNumbers(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_VIP, emptySet())?.toSet() ?: emptySet()

    fun addVipNumber(context: Context, number: String) {
        val clean = number.trim()
        if (clean.isEmpty()) return
        prefs(context).edit().putStringSet(KEY_VIP, HashSet(getVipNumbers(context) + clean)).apply()
    }

    fun removeVipNumber(context: Context, number: String) {
        prefs(context).edit().putStringSet(KEY_VIP, HashSet(getVipNumbers(context) - number)).apply()
    }

    /** Compares with the phone's own number matching, so "+1 415 555 0100" equals "4155550100". */
    fun isVip(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        return getVipNumbers(context).any {
            android.telephony.PhoneNumberUtils.compare(context.applicationContext, it, number)
        }
    }

    // ---- Announcer ------------------------------------------------------------

    fun isAnnouncerEnabled(context: Context) = prefs(context).getBoolean(KEY_ANN_ENABLED, true)
    fun setAnnouncerEnabled(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_ANN_ENABLED, v).apply()

    fun getAnnouncerTemplate(context: Context): String =
        prefs(context).getString(KEY_ANN_TEMPLATE, AnnouncerScript.DEFAULT_TEMPLATE) ?: AnnouncerScript.DEFAULT_TEMPLATE
    fun setAnnouncerTemplate(context: Context, v: String) = prefs(context).edit().putString(KEY_ANN_TEMPLATE, v).apply()

    fun getAnnouncerUserName(context: Context): String = prefs(context).getString(KEY_ANN_USER, "") ?: ""
    fun setAnnouncerUserName(context: Context, v: String) =
        prefs(context).edit().putString(KEY_ANN_USER, v.take(AnnouncerScript.MAX_USER_NAME_LENGTH)).apply()

    fun getAnnouncerRepeat(context: Context): Int = prefs(context).getInt(KEY_ANN_REPEAT, 1).coerceIn(1, 3)
    fun setAnnouncerRepeat(context: Context, v: Int) = prefs(context).edit().putInt(KEY_ANN_REPEAT, v.coerceIn(1, 3)).apply()

    fun getAnnouncerSpeedPercent(context: Context): Int = prefs(context).getInt(KEY_ANN_SPEED, 100).coerceIn(50, 150)
    fun setAnnouncerSpeedPercent(context: Context, v: Int) = prefs(context).edit().putInt(KEY_ANN_SPEED, v.coerceIn(50, 150)).apply()

    fun getAnnouncerPitchPercent(context: Context): Int = prefs(context).getInt(KEY_ANN_PITCH, 100).coerceIn(50, 150)
    fun setAnnouncerPitchPercent(context: Context, v: Int) = prefs(context).edit().putInt(KEY_ANN_PITCH, v.coerceIn(50, 150)).apply()

    fun getAnnouncerVolumePercent(context: Context): Int = prefs(context).getInt(KEY_ANN_VOLUME, 70).coerceIn(10, 100)
    fun setAnnouncerVolumePercent(context: Context, v: Int) = prefs(context).edit().putInt(KEY_ANN_VOLUME, v.coerceIn(10, 100)).apply()

    fun isAnnouncerHeadphonesOnly(context: Context) = prefs(context).getBoolean(KEY_ANN_HEADPHONES, false)
    fun setAnnouncerHeadphonesOnly(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_ANN_HEADPHONES, v).apply()

    fun isAnnounceUnknown(context: Context) = prefs(context).getBoolean(KEY_ANN_UNKNOWN, true)
    fun setAnnounceUnknown(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_ANN_UNKNOWN, v).apply()
}
