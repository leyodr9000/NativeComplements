package dev.local.sunwindowdemo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.local.sunwindowdemo.core.ui.*

@Composable
fun SidebarScreen(apps: List<LaunchableApp>, bottomInset: Dp = 0.dp) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(SidebarSettings.read(context)) }
    var picker by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    fun save(action: () -> Unit) {
        try { action(); error = "" } catch (failure: Exception) { error = failure.message ?: "保存失败" }
        state = SidebarSettings.read(context)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp + bottomInset)) {
        item {
            SettingsGroup {
                ToggleRow("启用小窗侧边栏", "仅作小窗启动入口，需要小窗总开关和系统宿主正常工作", state.enabled()) {
                    save { SidebarSettings.flag(context, "sidebarEnabled", it) }
                }
                ToggleRow("仅横屏显示", "关闭后，在指定应用的竖屏状态也显示", state.landscapeOnly()) {
                    save { SidebarSettings.flag(context, "sidebarLandscapeOnly", it) }
                }
                SettingRow("显示侧边栏的应用", "已选择 ${state.hosts().size} 个 · 例如王者荣耀", onClick = { picker = 1 })
                SettingRow("侧边栏小窗应用", "已选择 ${state.apps().size} 个 · 最多 12 个", onClick = { picker = 2 })
            }
            GroupTitle("侧边栏外观")
            SettingsGroup {
                ToggleRow("把手显示在右侧", "关闭则显示在左侧；不改变横屏小窗默认左侧布局", state.right()) {
                    save { SidebarSettings.flag(context, "sidebarRight", it) }
                }
                SliderRow("侧边栏不透明度", "只调整把手和面板背景，不改变小窗内容透明度", state.opacity(), 10..100, "%") {
                    save { SidebarSettings.number(context, "sidebarOpacity", it) }
                }
                SliderRow("把手垂直位置", "0% 顶部，100% 底部，避开可见系统栏和刘海", state.position(), 0..100, "%") {
                    save { SidebarSettings.number(context, "sidebarPosition", it) }
                }
                SliderRow("把手长度", value = state.handleHeight(), range = 40..140, unit = " dp") {
                    save { SidebarSettings.number(context, "sidebarHandleHeight", it) }
                }
                SliderRow("侧边栏图标大小", value = state.iconSize(), range = 32..56, unit = " dp") {
                    save { SidebarSettings.number(context, "sidebarIconSize", it) }
                }
            }
            GroupTitle("小窗应用顺序")
        }
        items(state.apps(), key = { it }) { pkg ->
            val index = state.apps().indexOf(pkg)
            SettingsGroup {
                SettingRow(apps.firstOrNull { it.packageName == pkg }?.label ?: pkg,
                    subtitle = "第 ${index + 1} 项", trailing = {
                        Row {
                            TextButton(enabled = index > 0, onClick = {
                                val next = state.apps().toMutableList()
                                java.util.Collections.swap(next, index, index - 1)
                                save { SidebarSettings.apps(context, next) }
                            }) { Text("上移") }
                            TextButton(enabled = index < state.apps().lastIndex, onClick = {
                                val next = state.apps().toMutableList()
                                java.util.Collections.swap(next, index, index + 1)
                                save { SidebarSettings.apps(context, next) }
                            }) { Text("下移") }
                        }
                    })
            }
        }
        item {
            Hint("点把手展开，点应用后自动收起。复用现有小窗与迷你窗恢复逻辑，横屏默认在左侧显示；可继续用原小窗控制条调整、最小化和挂起。锁屏、键盘、通知面板等系统界面显示时暂时隐藏，不提供录屏或游戏加速功能。")
            if (state.hosts().isEmpty()) Hint("尚未指定显示应用，侧边栏不会出现。")
            if (error.isNotBlank()) Hint(error)
        }
    }
    if (picker != 0) {
        val hostPicker = picker == 1
        var selected by remember(picker) { mutableStateOf(if (hostPicker) state.hosts().toList() else state.apps()) }
        var query by remember(picker) { mutableStateOf("") }
        var pickerError by remember(picker) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { picker = 0 }, title = { Text(if (hostPicker) "选择显示应用" else "选择小窗应用") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
                    OutlinedTextField(query, { query = it }, label = { Text("搜索应用") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("已选择 ${selected.size} / ${if (hostPicker) 100 else 12}", Modifier.padding(vertical = 8.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(apps.filter { it.packageName != "dev.nativecomplements.module" && (it.label.contains(query, true) || it.packageName.contains(query, true)) }, key = { it.packageName }) { app ->
                            SettingRow(app.label, onClick = {
                                if (app.packageName in selected) selected = selected - app.packageName
                                else if (selected.size < if (hostPicker) 100 else 12) selected = selected + app.packageName
                            }, trailing = { Checkbox(app.packageName in selected, onCheckedChange = null) })
                        }
                    }
                    if (pickerError.isNotBlank()) Text(pickerError, color = MaterialTheme.colorScheme.error)
                }
            }, confirmButton = {
                TextButton(onClick = {
                    try {
                        if (hostPicker) SidebarSettings.hosts(context, selected) else SidebarSettings.apps(context, selected)
                        state = SidebarSettings.read(context); picker = 0
                    } catch (failure: Exception) { pickerError = failure.message ?: "保存失败" }
                }) { Text("保存") }
            }, dismissButton = { TextButton(onClick = { picker = 0 }) { Text("取消") } })
    }
}
