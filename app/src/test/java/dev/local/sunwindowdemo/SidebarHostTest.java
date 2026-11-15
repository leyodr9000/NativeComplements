package dev.local.sunwindowdemo;

import android.content.Context;
import android.os.SystemClock;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SidebarHostTest {
    private Context c;
    private SidebarHost host;
    @Before public void setup(){c=RuntimeEnvironment.getApplication();}
    @After public void cleanup(){if(host!=null)host.close();}
    private SidebarSettings.State state(boolean right,List<String> apps){
        return new SidebarSettings.State(true,true,right,60,50,72,40,Set.of("com.tencent.tmgp.sgame"),apps);
    }
    private List<View> views()throws Exception{
        Class<?> cls=Class.forName("android.view.WindowManagerGlobal");var get=cls.getDeclaredMethod("getInstance");get.setAccessible(true);
        Object global=get.invoke(null);var method=cls.getDeclaredMethod("getWindowViews");method.setAccessible(true);
        @SuppressWarnings("unchecked") List<View> result=(List<View>)method.invoke(global);return result;
    }
    private View find(View root,String description){
        if(description.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;
        if(root instanceof ViewGroup group)for(int n=0;n<group.getChildCount();n++){
            View result=find(group.getChildAt(n),description);if(result!=null)return result;
        }
        return null;
    }
    @Test public void appClickCollapsesBeforeExistingWindowLauncherCallbackAndFocusStaysWithGame()throws Exception{
        List<String> calls=new ArrayList<>();host=new SidebarHost(c,pkg->{assertFalse(host.expanded());calls.add(pkg);},()->{});
        var app=new AppCatalog.App("com.tencent.mobileqq","QQ",new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE));
        host.update(state(true,List.of(app.pkg)),List.of(app),true,"com.tencent.tmgp.sgame",SystemClock.elapsedRealtime()+2500);
        assertFalse(host.expanded());host.expand();assertTrue(host.expanded());
        View root=views().stream().filter(v->v.getLayoutParams() instanceof WindowManager.LayoutParams p&&"NativeComplements-sidebar-panel".contentEquals(p.getTitle())).findFirst().orElseThrow();
        WindowManager.LayoutParams params=(WindowManager.LayoutParams)root.getLayoutParams();
        assertTrue((params.flags&WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)!=0);
        View entry=find(root,"小窗打开 QQ");assertNotNull(entry);entry.performClick();
        assertEquals(List.of("com.tencent.mobileqq"),calls);assertFalse(host.expanded());
    }
    @Test public void blockedEnvironmentRemovesPanelAndHandleAndExpiredLeaseRejectsLaunch()throws Exception{
        List<String> calls=new ArrayList<>();host=new SidebarHost(c,calls::add,()->{});
        var app=new AppCatalog.App("com.tencent.mobileqq","QQ",null);var s=state(false,List.of(app.pkg));
        host.update(s,List.of(app),true,"com.tencent.tmgp.sgame",SystemClock.elapsedRealtime()+2500);host.expand();assertTrue(host.expanded());
        host.update(s,List.of(app),false,"",SystemClock.elapsedRealtime());
        assertFalse(host.expanded());assertEquals(0,host.protectedRects().length);
        host.update(s,List.of(app),true,"com.tencent.tmgp.sgame",SystemClock.elapsedRealtime()-1);host.expand();
        assertFalse(host.expanded());assertTrue(calls.isEmpty());
    }
    @Test public void changingForegroundClosesExpandedStripAndOnlyInstalledChosenAppsAreRendered()throws Exception{
        host=new SidebarHost(c,pkg->{},()->{});
        var app=new AppCatalog.App("com.tencent.mobileqq","QQ",null);var s=state(true,List.of("com.missing.app",app.pkg));
        host.update(s,List.of(app),true,"com.tencent.tmgp.sgame",SystemClock.elapsedRealtime()+2500);host.expand();assertTrue(host.expanded());
        host.update(s,List.of(app),true,"com.another.game",SystemClock.elapsedRealtime()+2500);assertFalse(host.expanded());
        host.close();assertEquals(0,host.protectedRects().length);
    }
}
