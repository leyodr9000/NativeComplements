package dev.local.sunwindowdemo;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=35,qualifiers="w915dp-h412dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SidebarOriginalPanelTest {
    @Test public void moreUsesOriginalPanelIndexAndSurvivesCornerLandscapeDisable()throws Exception{
        Context c=RuntimeEnvironment.getApplication();Config.hostPreferences=null;
        var prefs=c.getSharedPreferences("corner",0);prefs.edit().clear().putBoolean("enabled",true).putBoolean("landscape",true)
            .putBoolean("sidebarEnabled",true).putStringSet("sidebarHosts",Set.of("com.tencent.tmgp.sgame")).commit();
        Shadows.shadowOf(c.getSystemService(PowerManager.class)).setIsInteractive(true);
        Shadows.shadowOf(c.getSystemService(android.app.KeyguardManager.class)).setKeyguardLocked(false);
        var constructor=SystemUiHost.class.getDeclaredConstructor(Context.class);constructor.setAccessible(true);SystemUiHost host=constructor.newInstance(c);
        ReflectionHelpers.setField(host,"config",new Config(c));ReflectionHelpers.setField(host,"wm",c.getSystemService(WindowManager.class));
        ReflectionHelpers.setField(host,"settingsReady",true);ReflectionHelpers.setField(host,"backendReady",true);ReflectionHelpers.setField(host,"backendState","idle");
        ReflectionHelpers.setField(host,"sidebarForeground","com.tencent.tmgp.sgame");ReflectionHelpers.setField(host,"foregroundSeen",SystemClock.elapsedRealtime());
        ReflectionHelpers.setField(host,"all",List.of(new AppCatalog.App("test.alpha.app","Alpha",null),new AppCatalog.App("com.tencent.mobileqq","QQ",null),new AppCatalog.App("test.zebra.app","Zebra",null)));
        try{
            ReflectionHelpers.callInstanceMethod(host,"showSidebarMore");
            View content=ReflectionHelpers.getField(host,"menuContent");assertNotNull(content);assertEquals("dev.local.sunwindowdemo.AppList$Tabs",content.getClass().getName());
            Object apps=ReflectionHelpers.getField(content,"apps");View index=ReflectionHelpers.getField(apps,"index");
            assertEquals("按字母滑动定位，中文应用按拼音分组",index.getContentDescription());
            int[] positions=ReflectionHelpers.getField(apps,"sectionPositions");assertTrue(positions[0]>=0);assertTrue(positions['Q'-'A']>=0);assertTrue(positions['Z'-'A']>=0);
            ReflectionHelpers.callInstanceMethod(host,"refresh");assertSame(content,ReflectionHelpers.getField(host,"menuContent"));
            ReflectionHelpers.setField(host,"sidebarForeground","test.other.app");
            ReflectionHelpers.callInstanceMethod(host,"refresh");assertNull(ReflectionHelpers.getField(host,"menuContent"));
        }finally{
            ReflectionHelpers.callInstanceMethod(host,"hidePanel");
            ((Handler)ReflectionHelpers.getField(host,"main")).removeCallbacksAndMessages(null);
            ((java.util.concurrent.ExecutorService)ReflectionHelpers.getField(host,"settingsWorker")).shutdownNow();
            Config.hostPreferences=null;
        }
    }
    @Test public void sidebarIconFactoryAndCornerUseTheSameOriginalThreeDotRenderer(){
        Context c=RuntimeEnvironment.getApplication();View icon=FanView.moreIcon(c);icon.layout(0,0,100,100);
        Bitmap actual=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888),expected=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
        icon.draw(new Canvas(actual));FanView.drawMoreIcon(c,new Canvas(expected),new Paint(3),50,50,90,255);
        int[] a=new int[10000],b=new int[10000];actual.getPixels(a,0,100,0,0,100,100);expected.getPixels(b,0,100,0,0,100,100);
        assertArrayEquals(a,b);
    }
}
