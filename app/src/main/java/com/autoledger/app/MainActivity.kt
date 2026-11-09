package com.autoledger.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.autoledger.app.databinding.ActivityMainBinding
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = DatabaseHelper(this)
        binding.recycler.layoutManager = LinearLayoutManager(this)

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnExport.setOnClickListener { exportCsv() }

        // Android 13+ 发送“已记账”提示通知需要此权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val (expense, income) = db.getMonthSummary()
        binding.tvExpense.text = String.format(Locale.getDefault(), "-¥%.2f", expense)
        binding.tvIncome.text = String.format(Locale.getDefault(), "+¥%.2f", income)

        val breakdown = db.getCategoryBreakdown()
        if (breakdown.isEmpty()) {
            binding.tvBreakdown.visibility = View.GONE
        } else {
            binding.tvBreakdown.visibility = View.VISIBLE
            binding.tvBreakdown.text = breakdown.joinToString("\n") { (cat, amt) ->
                "$cat：${String.format(Locale.getDefault(), "¥%.2f", amt)}"
            }
        }

        val list = db.getAll()
        if (list.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recycler.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recycler.visibility = View.VISIBLE
            binding.recycler.adapter = TransactionAdapter(
                list,
                onCategoryChange = { tx, cat ->
                    db.updateCategory(tx.id, cat)
                    refresh()
                },
                onDelete = { tx ->
                    db.delete(tx.id)
                    refresh()
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun exportCsv() {
        val csv = db.exportCsv()
        try {
            val file = File(getExternalFilesDir(null), "autoledger_export.csv")
            file.writeText(csv, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(share, "导出账单"))
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 是否授予“发送通知”权限都不影响记账功能本身
    }
}
