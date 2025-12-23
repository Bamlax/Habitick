package com.example.habitick

// 版本信息的数据结构
data class VersionInfo(
    val version: String,
    val date: String,
    val changes: List<String>
)

val versionHistory = listOf(
    VersionInfo(
        version = "0.5.0",
        date = "2025-12-23",
        changes = listOf(
            "新增中位数的查看",
            "新增对颜色的导出",
        )
    ),
    VersionInfo(
        version = "0.4.3",
        date = "2025-12-20",
        changes = listOf(
            "新增自动前移选择过的标签",
            "修复新一天自动打卡的bug",
            "修复标签分布在标签过多时溢出的bug",
        )
    ),
    VersionInfo(
        version = "0.4.2",
        date = "2025-12-7",
        changes = listOf(
            "修复标签无法删除的bug",
            "优化标签对于习惯的唯一性判定",
            "优化数据库导出逻辑",
        )
    ),
    VersionInfo(
        version = "0.4.1",
        date = "2025-12-7",
        changes = listOf(
            "修正最佳连续完成次数的显示逻辑",
            "修正排序底色为白色",
            "新增历史界面对标签的修改",
            "修改标签的显示ui",
        )
    ),
    VersionInfo(
        version = "0.4.0",
        date = "2025-12-7",
        changes = listOf(
            "新增标签功能",
            "新增数据的导入和导出功能",
        )
    ),
    VersionInfo(
        version = "0.3.0",
        date = "2025-12-2",
        changes = listOf(
            "修改禁止对今日之后的日期打卡",
            "修正错误显示灰色字体",
            "新增习惯排序功能",
            "新增对习惯的修改功能",
            "新增查看最大最小值",
            "新增最长连续的所有情况的统计",
        )
    ),
    VersionInfo(
        version = "0.2.0",
        date = "2025-11-25",
        changes = listOf(
            "修复返回直接退出软件的问题",
            "新增历史可修改备注和显示备注",
            "新增平均数的功能",
            "更改数值图标的显示逻辑（不显示无数据日期）",
            "修复备注在日历的错误显示",
            "分离完成任务和备注的相关性",
        )
    ),
    VersionInfo(
        version = "0.1.0",
        date = "2025-11-23",
        changes = listOf(
            "初始公测版本发布",
            "全新的 UI 设计风格 (Material Design 3)",
            "支持普通、数值、计时多种习惯类型",
            "强大的数据统计：年度热力图、月度日历、数值折线图",
            "支持历史记录补卡、修改备注",
        )
    )

)