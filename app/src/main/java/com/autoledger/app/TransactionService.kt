package com.autoledger.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.regex.Pattern

/**
 * 后台通知监听服务：监听微信（com.tencent.mm）的支付/收款通知，
 * 提取金额、商户，自动分类并写入本地账本。
 *
 * 需在系统“通知使用权”中给本 App 授权后才能工作（见 SettingsActivity）。
 */
class TransactionService : NotificationListenerService() {

    companion object {
        private const val TAG = "AutoLedger"
        private const val CHANNEL_ID = "autoledger_channel"
        private val WECHAT = setOf("com.tencent.mm")

        // 带前置负号的金额：-¥58.00 / -￥58
        private val AMOUNT_NEG = Pattern.compile(
            "-\\s*[¥￥]\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)"
        )
        // 普通金额：¥58.00 / ￥58
        private val AMOUNT_PLAIN = Pattern.compile(
            "[¥￥]\\s*(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)"
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            sbn ?: return
            val pkg = sbn.packageName ?: return
            if (pkg !in WECHAT) return

            val extras = sbn.notification.extras ?: return
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""
            val full = "$title $text $bigText".trim()

            if (!isWechatPay(full)) return
            parseAndSave(full)
        } catch (e: Exception) {
            Log.e(TAG, "parse error", e)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    private fun isWechatPay(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("微信支付") ||
                (t.contains("支付") && (t.contains("¥") || t.contains("￥"))) ||
                (t.contains("收款") && (t.contains("¥") || t.contains("￥")))
    }

    private fun parseAndSave(full: String) {
        // 1. 提取金额
        var amountText: String? = null
        var signed = false
        val negM = AMOUNT_NEG.matcher(full)
        if (negM.find()) {
            amountText = negM.group(1)
            signed = true
        } else {
            val m = AMOUNT_PLAIN.matcher(full)
            if (m.find()) amountText = m.group(1)
        }
        if (amountText == null) return
        val amount = amountText.replace(",", "").toDoubleOrNull() ?: return

        // 2. 收支判断
        val isIncome = full.contains("收款") || full.contains("来自") ||
                full.contains("红包") || full.contains("退款") ||
                (full.contains("转账") && !full.contains("向"))
        val isExpense = full.contains("付款") || full.contains("消费") ||
                full.contains("向") || full.contains("支出")
        val finalAmount: Double = when {
            signed -> -Math.abs(amount)     // 通知本身带负号 => 支出
            isIncome -> Math.abs(amount)
            isExpense -> -Math.abs(amount)
            else -> -Math.abs(amount)
        }

        // 3. 商户/对象
        val merchant = extractMerchant(full, amountText)

        // 4. 分类
        val category = CategoryRules.classify(merchant, full)

        // 5. 写入本地账本
        val db = DatabaseHelper(this)
        val tx = Transaction(
            amount = finalAmount,
            merchant = merchant,
            category = category,
            note = "",
            raw = full,
            time = System.currentTimeMillis()
        )
        db.insert(tx)
        db.close()

        // 6. 轻提示（全自动、不打扰，可在设置中关闭）
        if (Prefs.showRecordNotification(this)) {
            notifyRecorded(finalAmount, category)
        }
    }

    private fun extractMerchant(full: String, amountText: String): String {
        var m = Pattern.compile("向\\s*([^付\\s,，。]{1,20})\\s*付款").matcher(full)
        if (m.find()) return m.group(1).trim()
        m = Pattern.compile("来自\\s*([^¥￥\\s,，。]{1,20})").matcher(full)
        if (m.find()) return m.group(1).trim()
        m = Pattern.compile("支付给\\s*([^¥￥\\s,，。]{1,20})").matcher(full)
        if (m.find()) return m.group(1).trim()
        m = Pattern.compile("向\\s*([^\\s]{1,20})\\s*转账").matcher(full)
        if (m.find()) return m.group(1).trim()
        // 兜底：去掉金额与常见词，剩余文本作为备注
        var rest = full.replace(amountText, "")
            .replace("微信支付", "").replace("支付成功", "")
            .replace("付款", "").replace("收款", "")
            .replace("¥", "").replace("￥", "")
            .replace("(", "").replace(")", "").replace("：", "")
            .trim()
        return if (rest.isEmpty()) "微信支付" else rest.take(30)
    }

    private fun notifyRecorded(amount: Double, category: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "自动记账提示", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
        val sign = if (amount < 0) "-¥" else "+¥"
        val amt = String.format(Locale.getDefault(), "%.2f", Math.abs(amount))
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("已自动记账")
            .setContentText("$sign$amt · $category")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
