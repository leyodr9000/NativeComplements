package dev.local.sunwindowdemo

import androidx.compose.ui.test.*
import dev.local.sunwindowdemo.core.ui.ComplementsTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SidebarUiTest {
    @get:Rule val compose = createRobolectricComposeRule()

    @Test fun explicitGameSelectionAndOrderedAppsSaveWithoutEnablingByDefault() {
        val c = compose.activity
        SidebarSettings.prefs(c).edit().clear().commit()
        val apps = listOf(LaunchableApp("com.tencent.tmgp.sgame", "王者荣耀"), LaunchableApp("com.tencent.mobileqq", "QQ"), LaunchableApp("com.tencent.mm", "微信"))
        compose.setContent { ComplementsTheme(dynamic = false) { SidebarScreen(apps) } }
        assertFalse(SidebarSettings.read(c).enabled())
        compose.onNodeWithText("显示侧边栏的应用").performScrollTo().performClick()
        compose.onNodeWithText("王者荣耀").performClick()
        compose.onNodeWithText("保存").performClick()
        assertEquals(setOf("com.tencent.tmgp.sgame"), SidebarSettings.read(c).hosts())
        compose.onNodeWithText("侧边栏小窗应用").performScrollTo().performClick()
        compose.onNodeWithText("QQ").performClick()
        compose.onNodeWithText("微信").performClick()
        compose.onNodeWithText("保存").performClick()
        assertEquals(listOf("com.tencent.mobileqq", "com.tencent.mm"), SidebarSettings.read(c).apps())
        assertFalse(SidebarSettings.read(c).enabled())
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("下移"))
        compose.onAllNodesWithText("下移").onFirst().performClick()
        assertEquals(listOf("com.tencent.mm", "com.tencent.mobileqq"), SidebarSettings.read(c).apps())
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("启用小窗侧边栏"))
        compose.onNodeWithText("启用小窗侧边栏").performClick()
        assertTrue(SidebarSettings.read(c).enabled())
    }

    @Test fun opacityControlIsIndependentFromSmallWindowDim() {
        val c = compose.activity
        SidebarSettings.prefs(c).edit().clear().putInt("windowDim", 20).commit()
        compose.setContent { ComplementsTheme(dynamic = false) { SidebarScreen(emptyList()) } }
        compose.onNodeWithContentDescription("侧边栏不透明度").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(35f) }
        compose.runOnIdle {
            assertEquals(35, SidebarSettings.read(c).opacity())
            assertEquals(20, SidebarSettings.prefs(c).getInt("windowDim", 0))
        }
    }
}
