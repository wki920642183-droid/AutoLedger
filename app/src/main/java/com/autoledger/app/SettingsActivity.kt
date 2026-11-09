package com.autoledger.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.autoledger.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "设置"

        updateListenerStatus()

        binding.btnEnableListener.setOnClickListener {
            // 跳转到系统“通知使用权”设置页
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.switchNotify.isChecked = Prefs.showRecordNotification(this)
        binding.switchNotify.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setShowRecordNotification(this, isChecked)
        }

        binding.btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空所有账单")
                .setMessage("确定要删除全部记账数据吗？此操作不可恢复。")
                .setPositiveButton("清空") { _, _ ->
                    val d = DatabaseHelper(this)
                    val wdb = d.writableDatabase
                    wdb.execSQL("DELETE FROM tx")
                    wdb.close()
                    Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        binding.btnAbout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("关于")
                .setMessage(
                    "自动记账本 v1.0\n\n" +
                            "微信支付后自动记账。数据仅存于本机，不上传任何服务器。\n\n" +
                            "若某笔账商户识别不准，可在首页点击该笔修改分类或删除。"
                )
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        updateListenerStatus()
    }

    private fun updateListenerStatus() {
        val enabled = isListenerEnabled()
        binding.tvListenerStatus.text = if (enabled) "已开启 ✓" else "未开启 ✗"
        binding.tvListenerStatus.setTextColor(
            if (enabled) getColor(android.R.color.holo_green_dark)
            else getColor(android.R.color.holo_red_dark)
        )
        binding.btnEnableListener.text = if (enabled) "重新进入设置" else "去开启通知监听权限"
    }

    private fun isListenerEnabled(): Boolean {
        val cn = "$packageName/${TransactionService::class.java.name}"
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(cn) == true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
