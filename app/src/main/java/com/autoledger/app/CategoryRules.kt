package com.autoledger.app

/**
 * 内置分类规则：按关键词把商户归入分类。
 * 关键词命中即返回对应分类；都未命中返回“其他”。
 */
object CategoryRules {
    private val rules: List<Pair<String, List<String>>> = listOf(
        "餐饮" to listOf(
            "餐厅", "饭店", "餐饮", "咖啡", "奶茶", "小吃", "美团", "饿了么",
            "肯德基", "麦当劳", "星巴克", "面包", "火锅", "烧烤", "食堂",
            "外卖", "必胜客", "海底捞", "甜品", "炸鸡"
        ),
        "交通" to listOf(
            "滴滴", "地铁", "公交", "加油", "停车", "高铁", "12306", "机票",
            "出租", "网约车", "加油站", "摩拜", "哈啰", "青桔", "船票", " ferry"
        ),
        "购物" to listOf(
            "淘宝", "京东", "拼多多", "超市", "便利店", "商城", "天猫",
            "唯品会", "亚马逊", "苏宁", "考拉", "名创", "沃尔玛"
        ),
        "娱乐" to listOf(
            "电影", "游戏", "视频", "音乐", "KTV", "网吧", "门票", "迪士尼",
            "环球", "爱奇艺", "优酷", "腾讯视频", "网易云", "直播"
        ),
        "生活" to listOf(
            "水电", "物业", "话费", "医院", "药店", "宽带", "燃气", "电费",
            "水费", "房租", "快递", "顺丰", "邮政", "理发"
        ),
        "收入" to listOf(
            "收款", "红包", "转账", "退款", "零钱", "工资", "报销", "到账"
        )
    )

    fun classify(merchant: String, full: String): String {
        val text = "$merchant $full"
        for ((cat, keys) in rules) {
            for (k in keys) {
                if (text.contains(k, ignoreCase = true)) return cat
            }
        }
        return "其他"
    }

    fun allCategories(): List<String> = rules.map { it.first } + "其他"
}
