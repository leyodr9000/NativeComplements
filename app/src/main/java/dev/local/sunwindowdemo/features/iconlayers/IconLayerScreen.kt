package dev.local.sunwindowdemo.features.iconlayers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import dev.local.sunwindowdemo.LaunchableApp
import dev.local.sunwindowdemo.core.ui.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconLayerScreen(apps: List<LaunchableApp>, bottomInset: Dp = 0.dp, onApp: (String) -> Unit = {},
                    selection: Set<String>? = null, onSelect: (String) -> Unit = {},
                    onSelectLibrary: (Set<String>) -> Unit = {}) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(IconLayerStore.read(context)) }
    val installed = apps
    var library by remember { mutableStateOf(emptySet<String>()) }
    var notificationLibrary by remember { mutableStateOf(emptySet<String>()) }
    var importNotification by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var removeNotification by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var fraction by remember { mutableFloatStateOf(-1f) }
    var pending by remember { mutableStateOf<IconArchive?>(null) }
    var showImport by remember { mutableStateOf(false) }
    val cancel = remember { AtomicBoolean(false) }
    DisposableEffect(Unit) { onDispose { cancel.set(true); if (!busy) pending?.close() } }
    LaunchedEffect(Unit) {
        while (true) {
            state = withContext(Dispatchers.IO) { IconLayerStore.read(context) }
            library = withContext(Dispatchers.IO) { IconLibrary.packages(context, state.getString("generation", "")).toSet() }
            notificationLibrary = withContext(Dispatchers.IO) { IconLibrary.packages(context, state.getString("notificationGeneration", "")).toSet() }
            delay(1500)
        }
    }
    val reporter = remember(scope) { IconArchive.Progress { text, done, total ->
        scope.launch { progress = text; fraction = if (total > 0) done.toFloat() / total else -1f }
    } }
    fun refreshState() { state = IconLayerStore.read(context) }
    fun saveNotificationScale(percent: Int) {
        try {
            IconLayerStore.notificationScale(context, percent)
            message = ""
        } catch (e: Exception) { message = e.message ?: "保存失败" }
        finally { refreshState() }
    }
    fun install(archive: IconArchive, source: IconArchive.Source) {
        pending = null; busy = true; cancel.set(false)
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    try { IconLibrary.install(context, archive, source, cancel, reporter, importNotification) } finally { archive.close() }
                }
                message = result.toString(); refreshState()
            } catch (_: CancellationException) { message = "导入已取消，原图标库已保留" }
            catch (e: Exception) { message = e.message ?: "导入失败，原图标库已保留" }
            finally { busy = false }
        }
    }
    fun prepare(uri: Uri, tree: Boolean) {
        busy = true; cancel.set(false); message = ""; fraction = -1f
        scope.launch {
            var archive: IconArchive? = null
            try {
                archive = withContext(Dispatchers.IO) { IconLibrary.prepare(context, uri, tree, cancel, reporter) }
                val ready = archive!!; busy = false
                if (ready.sources.size == 1) install(ready, ready.sources.first()) else pending = ready
            } catch (_: CancellationException) { archive?.close(); message = "导入已取消，原图标库已保留"; busy = false }
            catch (e: Exception) { archive?.close(); message = e.message ?: "读取失败"; busy = false }
        }
    }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) prepare(it, false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) prepare(it, true) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 680.dp).fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 32.dp + bottomInset)) {
            item {
                if (selection == null) SettingsGroup {
                    ToggleRow("启用图标适配", value = state.getBoolean("enabled")) {
                        IconLayerStore.enabled(context, it); refreshState()
                    }
                    ToggleRow("通知图标替换", value = state.getBoolean("notifications")) {
                        IconLayerStore.notifications(context, it); refreshState()
                    }
                    SliderRow("通知图标大小", "全局统一：所有被替换的通知图标共用此大小；100% 保持当前默认效果。",
                        value = state.getInt("notificationScale", IconPolicy.NOTIFICATION_SCALE_DEFAULT),
                        range = IconPolicy.NOTIFICATION_SCALE_MIN..IconPolicy.NOTIFICATION_SCALE_MAX, unit = "%",
                        onCommit = ::saveNotificationScale)
                    SettingRow("恢复默认通知大小", onClick = { saveNotificationScale(IconPolicy.NOTIFICATION_SCALE_DEFAULT) })
                    SettingRow("导入图标库",
                        if (state.getInt("count") > 0) "${state.getInt("count")} 个图标 · 已安装匹配 ${installed.count { it.packageName in library }} 个" else "ZIP 或文件夹",
                        onClick = if (busy || pending != null) null else { { importNotification = false; showImport = true } },
                        leading = { Icon(Icons.Default.Add, null) })
                    SettingRow("导入通知图标库",
                        if (state.getInt("notificationCount") > 0) "${state.getInt("notificationCount")} 个图标 · 已安装匹配 ${installed.count { it.packageName in notificationLibrary }} 个"
                        else "独立于桌面素材库，使用完美图标 ZIP 或文件夹",
                        onClick = if (busy || pending != null) null else ({ importNotification = true; showImport = true }),
                        leading = { Icon(Icons.Default.Add, null) })
                    if (state.getInt("notificationCount") > 0) SettingRow("移除通知图标库", "保留桌面图标库与逐应用设置",
                        onClick = if (busy || pending != null) null else ({ removeNotification = true }))
                }
                if (busy) {
                    Spacer(Modifier.height(16.dp))
                    if (fraction < 0f) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text(progress, Modifier.padding(12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { cancel.set(true) }) { Text("取消导入") }
                }
                if (selection != null) {
                    val matched = installed.map { it.packageName }.filter { it in library }.toSet()
                    SettingsGroup {
                        SettingRow("选择素材库匹配应用", "${matched.size} 个已安装应用；替换当前选择",
                            onClick = if (matched.isEmpty() || busy) null else ({ onSelectLibrary(matched) }))
                        val noticeMatched = installed.map { it.packageName }.filter { it in notificationLibrary }.toSet()
                        SettingRow("选择通知库匹配应用", "${noticeMatched.size} 个已安装应用；替换当前选择",
                            onClick = if (noticeMatched.isEmpty() || busy) null else ({ onSelectLibrary(noticeMatched) }))
                    }
                }
                if (message.isNotBlank()) Hint(message)
                GroupTitle("逐应用设置")
            }
            items(installed, key = { it.packageName }) { app ->
                val policy = remember(app.packageName, state.getString("policies"), state.getStringArrayList("originals").toString()) { IconPolicy.read(context, app.packageName) }
                SettingsGroup {
                    SettingRow(app.label, (when (policy.color) {
                        IconPolicy.MANUAL -> "手动前后景"
                        IconPolicy.ALGORITHM -> "算法补全"
                        IconPolicy.ORIGINAL -> "彩色图标不处理"
                        else -> if (app.packageName in library) "自动 · 素材库" else "自动 · 应用原图"
                    }) + " · 通知：" + when (policy.notification) {
                        IconPolicy.FOLLOW -> "跟随前景"
                        IconPolicy.UPLOAD -> "上传图片"
                        IconPolicy.NOTIFICATION_LIBRARY -> if (app.packageName in notificationLibrary) "通知图标库" else "通知库未匹配，保留原生"
                        else -> "不处理"
                    }, onClick = { if (selection == null) onApp(app.packageName) else onSelect(app.packageName) },
                        leading = { IconRowAppIcon(app) }, trailing = {
                            if (selection == null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            else Checkbox(app.packageName in selection, onCheckedChange = null)
                        })
                }
                Spacer(Modifier.height(2.dp))
            }
            item {
                if (installed.isEmpty()) Hint("暂无可显示的应用")
                if (!state.getString("error").isNullOrBlank()) Hint(state.getString("error")!!)
            }
        }
    }
    if (showImport) ModalBottomSheet(onDismissRequest = { showImport = false }) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(if (importNotification) "导入通知图标库" else "导入图标库", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
            SettingsGroup {
                SettingRow("选择 ZIP 文件", onClick = {
                    showImport = false
                    zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                })
                SettingRow("选择文件夹", onClick = {
                    showImport = false
                    folderPicker.launch(null)
                })
            }
        }
    }
    if (removeNotification) AlertDialog(onDismissRequest = { removeNotification = false },
        title = { Text("移除通知图标库？") }, text = { Text("使用通知库的应用将保留原生通知图标，桌面图标不变。") },
        confirmButton = { TextButton(onClick = {
            removeNotification = false; busy = true
            scope.launch {
                try { withContext(Dispatchers.IO) { IconLibrary.removeNotification(context) }; refreshState() }
                catch (e: Exception) { message = e.message ?: "移除失败" }
                finally { busy = false }
            }
        }) { Text("移除") } }, dismissButton = { TextButton(onClick = { removeNotification = false }) { Text("取消") } })
    pending?.let { archive ->
        fun dismiss() { pending = null; scope.launch(Dispatchers.IO) { archive.close() } }
        AlertDialog(onDismissRequest = { dismiss() }, title = { Text("选择图标源") },
            text = { LazyColumn { items(archive.sources) { source -> TextButton(onClick = { install(archive, source) }) { Text("${source.title} · ${source.completeCount()} 个", Modifier.fillMaxWidth()) } } } },
            confirmButton = {}, dismissButton = { TextButton(onClick = { dismiss() }) { Text("取消") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconBatchDialog(packages: Set<String>, onDismiss: () -> Unit, onApplied: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var color by remember { mutableIntStateOf(-1) }
    var monochrome by remember { mutableIntStateOf(-1) }
    var notification by remember { mutableIntStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val masks = listOf(-1 to "保持现有", IconPolicy.FOLLOW to "跟随前景", IconPolicy.UNCHANGED to "不处理")
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text("批量设置 · ${packages.size} 个应用", style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                IconBatchModes("桌面图标", color, listOf(-1 to "保持现有", IconPolicy.AUTO to "自动",
                    IconPolicy.ALGORITHM to "算法补全", IconPolicy.ORIGINAL to "原图"), !busy) { color = it }
                IconBatchModes("莫奈图标", monochrome, masks, !busy) { monochrome = it }
                IconBatchModes("通知图标", notification, masks + (IconPolicy.NOTIFICATION_LIBRARY to "通知图标库"), !busy) { notification = it }
            }
            if (error.isNotBlank()) Hint(error)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") }
                Button(enabled = !busy && (color != -1 || monochrome != -1 || notification != -1), onClick = {
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { IconPolicy.batch(context, packages, color, monochrome, notification) }
                            onApplied()
                        } catch (e: Exception) { error = e.message ?: "保存失败" }
                        finally { busy = false }
                    }
                }) { Text("应用") }
            }
        }
    }
}

@Composable
private fun IconBatchModes(title: String, selected: Int, modes: List<Pair<Int, String>>, enabled: Boolean,
                           onSelect: (Int) -> Unit) {
    GroupTitle(title)
    SettingsGroup {
        modes.forEach { (mode, label) ->
            SettingRow(label, onClick = if (enabled) ({ onSelect(mode) }) else null,
                trailing = { RadioButton(mode == selected, null, enabled = enabled) })
        }
    }
}

@Composable
fun IconDiagnosticsScreen() {
    val context = LocalContext.current
    var state by remember { mutableStateOf(IconLayerStore.read(context)) }
    SettingsGroup {
        SettingRow("桌面连接状态", "${state.getString("status")} · 已加载 ${state.getInt("loaded")} 个")
        SettingRow("刷新状态", onClick = { state = IconLayerStore.read(context) })
    }
    GroupTitle("图标分层实验")
    SettingsGroup {
        listOf("保留 Demo 原图", "粉色双层 Demo", "薄荷色双层 Demo").forEachIndexed { index, title ->
            SettingRow(title, onClick = { IconLayerStore.save(context, index); state = IconLayerStore.read(context) },
                trailing = { RadioButton(state.getInt("mode") == index, null) })
        }
    }
    if (!state.getString("error").isNullOrBlank()) Hint(state.getString("error")!!)
}

@Composable
private fun IconRowAppIcon(app: LaunchableApp) {
    val bitmap = remember(app.packageName, app.icon) {
        app.icon?.let { drawable ->
            Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also {
                val old = android.graphics.Rect(drawable.bounds)
                drawable.setBounds(0, 0, 96, 96)
                drawable.draw(Canvas(it))
                drawable.bounds = old
            }.asImageBitmap()
        }
    }
    if (bitmap == null) Glyph("apps") else Image(bitmap, null, Modifier.size(42.dp))
}
