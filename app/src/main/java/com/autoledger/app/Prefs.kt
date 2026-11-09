package com.autoledger.app

import android.content.Context
import android.content.SharedPreferences

/**
 * 简单的本地偏好设置（SharedPreferences）。
 */
object Prefs {
    private const val NAME = "autoledger_prefs"
    private const val KEY_NOTIFY = "show_notify"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 是否在有新记账时弹一条轻提示通知（默认开启） */
    fun showRecordNotification(ctx: Context): Boolean =
        sp(ctx).getBoolean(KEY_NOTIFY, true)

    fun setShowRecordNotification(ctx: Context, value: Boolean) {
        sp(ctx).edit().putBoolean(KEY_NOTIFY, value).apply()
    }
}
