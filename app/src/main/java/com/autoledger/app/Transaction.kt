package com.autoledger.app

/**
 * 一笔账目。
 * amount 为正数表示收入，负数表示支出。
 */
data class Transaction(
    val id: Long = 0,
    val amount: Double,
    val merchant: String,
    val category: String,
    val note: String,
    val raw: String,
    val time: Long
)
