package dev.local.sunwindowdemo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import dev.local.sunwindowdemo.core.ui.ComplementsTheme
import dev.local.sunwindowdemo.features.iconlayers.IconLayerStore
import dev.local.sunwindowdemo.features.iconlayers.IconPolicy
import dev.local.sunwindowdemo.features.iconlayers.IconBatchDialog
import dev.local.sunwindowdemo.features.iconlayers.IconAppScreen
import dev.local.sunwindowdemo.features.iconlayers.IconLayerScreen
import dev.local.sunwindowdemo.navigation.ModuleApp
import dev.local.sunwindowdemo.navigation.Page
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconBatchUiTest {
    @get:Rule val compose = createRobolectricComposeRule()

    @Test fun topMenuSelectionCancelAndThreeIndependentModes() = batchFlow()

    @Test @Config(qualifiers = "zh-rCN-w915dp-h412dp-land-night-420dpi")
    fun landscapeBatchKeepsActionsReachable() = batchFlow()

    @Test fun librarySelectionReplacesSelectionWithInstalledMatchesOnly() {
        val context = compose.activity
        val generation = "g_" + java.util.UUID.randomUUID().toString().replace("-", "")
        val folder = File(context.createDeviceProtectedStorageContext().filesDir, "icon-libraries/$generation")
        folder.mkdirs()
        File(folder, "index.json").writeText("[\"test.icons.one\",\"test.icons.notinstalled\"]")
        val notificationGeneration = "n_" + java.util.UUID.randomUUID().toString().replace("-", "")
        val notificationFolder = File(folder.parentFile, notificationGeneration).apply { mkdirs() }
        File(notificationFolder, "index.json").writeText("[\"test.icons.two\",\"test.icons.notinstalled\"]")
        IconLayerStore.prefs(context).edit().clear().putString("generation", generation)
            .putString("notificationGeneration", notificationGeneration).commit()
        var selected = setOf("test.icons.two")
        compose.setContent {
            ComplementsTheme(dynamic = false) {
                IconLayerScreen(listOf(LaunchableApp("test.icons.one", "示例一"), LaunchableApp("test.icons.two", "示例二")),
                    selection = selected, onSelectLibrary = { selected = it })
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("1 个已安装应用；替换当前选择").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("选择素材库匹配应用").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(setOf("test.icons.one"), selected) }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("1 个已安装应用；替换当前选择").fetchSemanticsNodes().size == 2
        }
        compose.onNodeWithText("选择通知库匹配应用").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(setOf("test.icons.two"), selected) }
    }

    @Test fun homepageHasOneGlobalSliderAndResetPreservesAppModesAndLegacyData() {
        val context = compose.activity
        IconLayerStore.prefs(context).edit().clear().commit()
        val original = IconPolicy().apply {
            color = IconPolicy.MANUAL
            monochrome = IconPolicy.UNCHANGED
            notification = IconPolicy.FOLLOW
            notificationScale = 135
        }
        IconPolicy.save(context, "test.icons.one", original)
        compose.setContent {
            ComplementsTheme(dynamic = false) {
                IconLayerScreen(listOf(LaunchableApp("test.icons.one", "示例一")))
            }
        }
        compose.onAllNodesWithContentDescription("通知图标大小").assertCountEquals(1)
        compose.onNodeWithContentDescription("通知图标大小").performScrollTo()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(145f) }
        compose.waitUntil(10_000) { IconLayerStore.notificationScale(context) == 145 }
        compose.onNodeWithText("恢复默认通知大小").performScrollTo().performClick()
        compose.waitUntil(10_000) { IconLayerStore.notificationScale(context) == 100 }
        val saved = IconPolicy.read(context, "test.icons.one")
        assertEquals("Retained only for old-config compatibility", 135, saved.notificationScale)
        assertEquals(IconPolicy.MANUAL, saved.color)
        assertEquals(IconPolicy.UNCHANGED, saved.monochrome)
        assertEquals(IconPolicy.FOLLOW, saved.notification)
        compose.onNodeWithText("手动前后景 · 通知：跟随前景").performScrollTo().assertIsDisplayed()
    }

    @Test fun perAppScreenExplainsGlobalSizeButHasNoIndependentSlider() {
        val context = compose.activity
        Shadows.shadowOf(context.packageManager).setApplicationIcon(context.packageName,
            android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE))
        IconLayerStore.prefs(context).edit().clear().commit()
        IconPolicy.save(context, context.packageName, IconPolicy().apply { notification = IconPolicy.FOLLOW })
        compose.setContent {
            ComplementsTheme(dynamic = false) {
                IconAppScreen(LaunchableApp(context.packageName, "本模块"))
            }
        }
        compose.onNodeWithContentDescription("通知图标大小").assertDoesNotExist()
        compose.onNodeWithText("恢复默认大小").assertDoesNotExist()
        compose.onNodeWithText("通知大小统一使用图标适配首页的全局设置；100% 保持当前默认效果。")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun batchDialogOnlyChangesModesAndHasNoSizeControl() {
        val context = compose.activity
        IconLayerStore.prefs(context).edit().clear().commit()
        IconLayerStore.notificationScale(context, 145)
        var applied = false
        compose.setContent {
            ComplementsTheme(dynamic = false) {
                IconBatchDialog(setOf("test.icons.one"), {}, { applied = true })
            }
        }
        compose.onNodeWithContentDescription("通知图标大小").assertDoesNotExist()
        compose.onNodeWithText("保持各应用现有大小").assertDoesNotExist()
        compose.onNodeWithText("恢复默认大小").assertDoesNotExist()
        compose.onNodeWithText("应用").assertIsNotEnabled()
        compose.onNodeWithText("通知图标库").performScrollTo().performClick()
        compose.onNodeWithText("应用").performClick()
        compose.waitUntil(10_000) { applied }
        assertEquals(IconPolicy.NOTIFICATION_LIBRARY, IconPolicy.read(context, "test.icons.one").notification)
        assertEquals(145, IconLayerStore.notificationScale(context))
    }

    private fun batchFlow() {
        val context = compose.activity
        IconLayerStore.prefs(context).edit().clear().commit()
        val controller = WindowController(context)
        try {
            compose.setContent {
                ComplementsTheme(dynamic = false) {
                    ModuleApp(controller, Page.IconLayers, {}, {})
                }
            }
            compose.waitUntil(10_000) {
                // The catalog worker publishes through Handler; Compose's clock alone does not drain that paused looper.
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                controller.catalogLoaded
            }
            compose.runOnIdle {
                ReflectionHelpers.getField<MutableState<List<LaunchableApp>>>(controller, "apps\$delegate").value =
                    listOf(LaunchableApp("test.icons.one", "示例一"), LaunchableApp("test.icons.two", "示例二"))
            }
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithText("批量设置").performClick()
            compose.onNodeWithText("启用图标适配").assertDoesNotExist()
            compose.onNodeWithText("下一步").assertIsNotEnabled()
            compose.onNodeWithText("全选").performClick()
            compose.onNodeWithTag("page-title-已选择 2 个应用").assertIsDisplayed()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("启用图标适配").assertIsDisplayed()
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithText("批量设置").performClick()
            compose.onNodeWithText("示例一").performScrollTo().performClick()
            compose.onNodeWithText("下一步").performClick()
            compose.onNodeWithText("应用").assertIsNotEnabled()
            compose.onNodeWithText("算法补全").performScrollTo().performClick()
            compose.onAllNodesWithText("跟随前景").onFirst().performScrollTo().performClick()
            compose.runOnIdle {
                val global = ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.view.WindowManagerGlobal"), "getInstance")
                val roots = ReflectionHelpers.callInstanceMethod<List<View>>(global, "getWindowViews")
                val root = roots.last()
                val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                root.draw(Canvas(bitmap))
                val orientation = if (root.width > root.height) "landscape" else "portrait"
                File(requireNotNull(System.getProperty("nativecomplements.screenshots")), "icon-batch-$orientation.png")
                    .also { it.parentFile?.mkdirs() }.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            compose.onNodeWithText("应用").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("启用图标适配").fetchSemanticsNodes().isNotEmpty() }
            val policy = IconPolicy.read(context, "test.icons.one")
            assertEquals(IconPolicy.ALGORITHM, policy.color)
            assertEquals(IconPolicy.FOLLOW, policy.monochrome)
            assertEquals(IconPolicy.UNCHANGED, policy.notification)
            assertEquals(IconPolicy.AUTO, IconPolicy.read(context, "test.icons.two").color)
        } finally { compose.runOnIdle { controller.close() } }
    }
}
