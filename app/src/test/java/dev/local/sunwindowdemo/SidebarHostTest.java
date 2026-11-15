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
    @Test public void onlyCollapsedHandleMovesToPhysicalEdgeAndPanelKeepsOriginalSafeAnchor()throws Exception{
        WindowManager real=c.getSystemService(WindowManager.class);
        var insets=new WindowInsets.Builder().setInsets(WindowInsets.Type.displayCutout(),android.graphics.Insets.of(140,0,0,0))
            .setVisible(WindowInsets.Type.displayCutout(),true).build();
        var metrics=new WindowMetrics(new android.graphics.Rect(0,0,2560,1151),insets);
        WindowManager simulated=(WindowManager)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class[]{WindowManager.class},(proxy,method,args)->method.getName().equals("getCurrentWindowMetrics")?metrics:method.invoke(real,args));
        Context wrapper=new android.content.ContextWrapper(c){@Override public Object getSystemService(String name){return Context.WINDOW_SERVICE.equals(name)?simulated:super.getSystemService(name);}};
        host=new SidebarHost(wrapper,pkg->{},()->{});
        var app=new AppCatalog.App("com.tencent.mobileqq","QQ",null);
        host.update(state(false,List.of(app.pkg)),List.of(app),true,"com.tencent.tmgp.sgame",SystemClock.elapsedRealtime()+2500);
        View resting=views().stream().filter(v->"展开小窗侧边栏".contentEquals(v.getContentDescription()==null?"":v.getContentDescription())).findFirst().orElseThrow();
        assertEquals(0,((WindowManager.LayoutParams)resting.getLayoutParams()).x);
        int restingY=((WindowManager.LayoutParams)resting.getLayoutParams()).y;
        host.expand();
        View panel=views().stream().filter(v->v.getLayoutParams() instanceof WindowManager.LayoutParams p&&"NativeComplements-sidebar-panel".contentEquals(p.getTitle())).findFirst().orElseThrow();
        WindowManager.LayoutParams p=(WindowManager.LayoutParams)panel.getLayoutParams();
        int gap=Math.round(2*c.getResources().getDisplayMetrics().density);
        assertEquals(140+gap,p.x);
        host.collapse();
        View returned=views().stream().filter(v->"展开小窗侧边栏".contentEquals(v.getContentDescription()==null?"":v.getContentDescription())).findFirst().orElseThrow();
        WindowManager.LayoutParams after=(WindowManager.LayoutParams)returned.getLayoutParams();
        assertEquals(0,after.x);assertEquals(restingY,after.y);
    }
    @Test public void firstTapOnlyRestoresHandleSecondTapOpensAndIdleShrinksAgain()throws Exception{
        host=new SidebarHost(c,pkg->{},()->{});
        var s=state(false,List.of("com.tencent.mobileqq"));
        host.update(s,List.of(new AppCatalog.App("com.tencent.mobileqq","QQ",null)),true,"com.tencent.tmgp.sgame",SystemClock.elapsedRealtime()+10000);
        View mini=views().stream().filter(v->"展开小窗侧边栏".contentEquals(v.getContentDescription()==null?"":v.getContentDescription())).findFirst().orElseThrow();
        assertEquals(0,((WindowManager.LayoutParams)mini.getLayoutParams()).x);
        mini.performClick();assertFalse(host.expanded());
        View normal=views().stream().filter(v->"展开小窗侧边栏".contentEquals(v.getContentDescription()==null?"":v.getContentDescription())).findFirst().orElseThrow();
        assertEquals(Math.round(6*c.getResources().getDisplayMetrics().density),((WindowManager.LayoutParams)normal.getLayoutParams()).x);
        normal.performClick();assertTrue(host.expanded());host.collapse();
        View next=views().stream().filter(v->"展开小窗侧边栏".contentEquals(v.getContentDescription()==null?"":v.getContentDescription())).findFirst().orElseThrow();
        next.performClick();assertFalse(host.expanded());
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(3000));
        View shrunk=views().stream().filter(v->"展开小窗侧边栏".contentEquals(v.getContentDescription()==null?"":v.getContentDescription())).findFirst().orElseThrow();
        assertEquals(0,((WindowManager.LayoutParams)shrunk.getLayoutParams()).x);
        shrunk.performClick();assertFalse(host.expanded());
    }
    @Test public void moreIsAlwaysFirstAndOpensUnpinnedAppWithoutChangingConfiguredList()throws Exception{
        List<String> pinnedCalls=new ArrayList<>(),allCalls=new ArrayList<>();
        host=new SidebarHost(c,pinnedCalls::add,()->{},pkg->{assertFalse(host.expanded());allCalls.add(pkg);});
        var s=state(false,List.of());var qq=new AppCatalog.App("com.tencent.mobileqq","QQ",null);
        host.update(s,List.of(qq),true,"com.tencent.tmgp.sgame",SystemClock.elapsedRealtime()+10000);host.expand();
        View root=views().stream().filter(v->v.getLayoutParams() instanceof WindowManager.LayoutParams p&&"NativeComplements-sidebar-panel".contentEquals(p.getTitle())).findFirst().orElseThrow();
        View more=find(root,"侧边栏更多应用");assertNotNull(more);assertSame(more,((ViewGroup)root).getChildAt(0));
        more.performClick();assertTrue(host.expanded());
        View full=views().stream().filter(v->v.getLayoutParams() instanceof WindowManager.LayoutParams p&&"NativeComplements-sidebar-panel".contentEquals(p.getTitle())).findFirst().orElseThrow();
        assertNotNull(find(full,"返回侧边栏常用应用"));
        View item=find(full,"小窗打开 QQ");assertNotNull(item);item.performClick();
        assertEquals(List.of("com.tencent.mobileqq"),allCalls);assertTrue(pinnedCalls.isEmpty());assertTrue(s.apps().isEmpty());
    }
    @Test public void changingForegroundClosesExpandedStripAndOnlyInstalledChosenAppsAreRendered()throws Exception{
        host=new SidebarHost(c,pkg->{},()->{});
        var app=new AppCatalog.App("com.tencent.mobileqq","QQ",null);var s=state(true,List.of("com.missing.app",app.pkg));
        host.update(s,List.of(app),true,"com.tencent.tmgp.sgame",SystemClock.elapsedRealtime()+2500);host.expand();assertTrue(host.expanded());
        host.update(s,List.of(app),true,"com.another.game",SystemClock.elapsedRealtime()+2500);assertFalse(host.expanded());
        host.close();assertEquals(0,host.protectedRects().length);
    }
}
