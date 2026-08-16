package org.openmeds.reminder.ui.navigation

enum class AppDestination(val route: String, val label: String) {
    HOME("home", "今日"),
    MEDICINES("medicines", "药箱"),
    HISTORY("history", "记录"),
    SETTINGS("settings", "设置")
}
