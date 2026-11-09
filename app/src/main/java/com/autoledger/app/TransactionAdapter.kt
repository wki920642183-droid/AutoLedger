package com.autoledger.app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.autoledger.app.databinding.ItemTransactionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 账目列表适配器。点击某条可修改分类或删除。
 */
class TransactionAdapter(
    private val items: List<Transaction>,
    private val onCategoryChange: (Transaction, String) -> Unit,
    private val onDelete: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.VH>() {

    private val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    class VH(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tx = items[position]
        val b = holder.binding
        b.tvMerchant.text = tx.merchant.ifEmpty { "未知" }
        b.tvCategory.text = tx.category

        val sign = if (tx.amount < 0) "-¥" else "+¥"
        val amt = String.format(Locale.getDefault(), "%.2f", Math.abs(tx.amount))
        b.tvAmount.text = "$sign$amt"
        b.tvAmount.setTextColor(
            b.root.context.getColor(
                if (tx.amount < 0) android.R.color.holo_red_dark
                else android.R.color.holo_green_dark
            )
        )
        b.tvTime.text = sdf.format(Date(tx.time))

        b.root.setOnClickListener {
            val menu = PopupMenu(b.root.context, b.root)
            for (cat in CategoryRules.allCategories()) {
                menu.menu.add(cat)
            }
            menu.menu.add("删除该笔")
            menu.setOnMenuItemClickListener { item ->
                val title = item.title.toString()
                if (title == "删除该笔") onDelete(tx) else onCategoryChange(tx, title)
                true
            }
            menu.show()
        }
    }
}
