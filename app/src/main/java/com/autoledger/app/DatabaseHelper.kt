package com.autoledger.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 本地 SQLite 账本。所有数据仅存于本机，不上传任何服务器。
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "autoledger.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE tx (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount REAL NOT NULL,
                merchant TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT '其他',
                note TEXT NOT NULL DEFAULT '',
                raw TEXT NOT NULL DEFAULT '',
                time INTEGER NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
        db.execSQL("DROP TABLE IF EXISTS tx")
        onCreate(db)
    }

    fun insert(tx: Transaction): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("amount", tx.amount)
            put("merchant", tx.merchant)
            put("category", tx.category)
            put("note", tx.note)
            put("raw", tx.raw)
            put("time", tx.time)
        }
        val id = db.insert("tx", null, cv)
        db.close()
        return id
    }

    fun getAll(): List<Transaction> {
        val list = mutableListOf<Transaction>()
        val db = readableDatabase
        val c = db.rawQuery(
            "SELECT id,amount,merchant,category,note,raw,time FROM tx ORDER BY time DESC",
            null
        )
        if (c.moveToFirst()) {
            do {
                list.add(
                    Transaction(
                        id = c.getLong(0),
                        amount = c.getDouble(1),
                        merchant = c.getString(2) ?: "",
                        category = c.getString(3) ?: "其他",
                        note = c.getString(4) ?: "",
                        raw = c.getString(5) ?: "",
                        time = c.getLong(6)
                    )
                )
            } while (c.moveToNext())
        }
        c.close()
        db.close()
        return list
    }

    /** 本月支出(正值)、收入(正值) */
    fun getMonthSummary(): Pair<Double, Double> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = System.currentTimeMillis()
        var expense = 0.0
        var income = 0.0
        val db = readableDatabase
        val c = db.rawQuery(
            "SELECT amount FROM tx WHERE time >= ? AND time <= ?",
            arrayOf(start.toString(), end.toString())
        )
        if (c.moveToFirst()) {
            do {
                val a = c.getDouble(0)
                if (a < 0) expense += -a else income += a
            } while (c.moveToNext())
        }
        c.close()
        db.close()
        return Pair(expense, income)
    }

    /** 各分类支出合计（降序），仅含支出 */
    fun getCategoryBreakdown(): List<Pair<String, Double>> {
        val map = LinkedHashMap<String, Double>()
        val db = readableDatabase
        val c = db.rawQuery("SELECT category, SUM(amount) FROM tx GROUP BY category", null)
        if (c.moveToFirst()) {
            do {
                val cat = c.getString(0) ?: "其他"
                val sum = c.getDouble(1)
                if (sum < 0) map[cat] = (-sum)
            } while (c.moveToNext())
        }
        c.close()
        db.close()
        return map.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    fun updateCategory(id: Long, category: String) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("category", category) }
        db.update("tx", cv, "id=?", arrayOf(id.toString()))
        db.close()
    }

    fun delete(id: Long) {
        val db = writableDatabase
        db.delete("tx", "id=?", arrayOf(id.toString()))
        db.close()
    }

    fun exportCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("时间,收支,金额,商户,分类,备注")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        for (tx in getAll()) {
            val type = if (tx.amount < 0) "支出" else "收入"
            val amt = String.format(Locale.getDefault(), "%.2f", Math.abs(tx.amount))
            val note = tx.note.replace("\"", "'").replace("\n", " ")
            sb.appendLine(
                "${sdf.format(Date(tx.time))},$type,$amt," +
                        "\"${tx.merchant}\",\"${tx.category}\",\"$note\""
            )
        }
        return sb.toString()
    }
}
