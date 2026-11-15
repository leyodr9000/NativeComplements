package dev.local.sunwindowdemo;

import android.content.Context;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SidebarSettingsTest {
    Context c;
    @Before public void setup(){c=RuntimeEnvironment.getApplication();SidebarSettings.prefs(c).edit().clear().commit();}
    @Test public void freshInstallHasNoHostsAndIsOffByDefault(){
        var s=SidebarSettings.read(c);assertFalse(s.enabled());assertTrue(s.landscapeOnly());assertTrue(s.right());
        assertTrue(s.hosts().isEmpty());assertTrue(s.apps().isEmpty());assertEquals(60,s.opacity());
    }
    @Test public void selectionAndOrderSurviveExistingConfigSnapshotCopy(){
        SidebarSettings.apps(c,List.of("com.tencent.mm","com.tencent.mobileqq"));
        SidebarSettings.hosts(c,List.of("com.tencent.tmgp.sgame"));SidebarSettings.flag(c,"sidebarEnabled",true);
        SidebarSettings.number(c,"sidebarOpacity",35);SidebarSettings.flag(c,"sidebarRight",false);
        var source=SidebarSettings.prefs(c);var b=new android.os.Bundle();
        for(var e:source.getAll().entrySet()){
            if(e.getValue() instanceof Boolean flag)b.putBoolean(e.getKey(),flag);
            else if(e.getValue() instanceof String value)b.putString(e.getKey(),value);
            else if(e.getValue() instanceof Integer value)b.putInt(e.getKey(),value);
            else if(e.getValue() instanceof Set<?> values)b.putStringArrayList(e.getKey(),new ArrayList<>(source.getStringSet(e.getKey(),Set.of())));
        }
        var dest=c.getSharedPreferences("sidebar-host-test",0);ConfigProvider.copy(b,dest);
        var result=SidebarSettings.read(dest);
        assertEquals(List.of("com.tencent.mm","com.tencent.mobileqq"),result.apps());
        assertEquals(Set.of("com.tencent.tmgp.sgame"),result.hosts());assertTrue(result.enabled());assertFalse(result.right());assertEquals(35,result.opacity());
    }
    @Test public void restoredHandleGapIsClampedAndStoredInExistingSnapshot(){
        assertEquals(6,SidebarSettings.read(c).edgeGap());
        SidebarSettings.number(c,"sidebarEdgeGap",12);assertEquals(12,SidebarSettings.read(c).edgeGap());
        assertThrows(IllegalArgumentException.class,()->SidebarSettings.number(c,"sidebarEdgeGap",33));
        assertEquals(12,SidebarSettings.read(c).edgeGap());
    }
    @Test public void invalidUpdatesDoNotPublish(){
        SidebarSettings.apps(c,List.of("com.tencent.mm"));
        assertThrows(IllegalArgumentException.class,()->SidebarSettings.apps(c,List.of("invalid/pkg")));
        assertThrows(IllegalArgumentException.class,()->SidebarSettings.number(c,"sidebarOpacity",0));
        assertThrows(IllegalArgumentException.class,()->SidebarSettings.flag(c,"enabled",false));
        assertEquals(List.of("com.tencent.mm"),SidebarSettings.read(c).apps());
    }
    @Test public void failedCommitRestoresPreviousSidebarKeysAndKeepsOtherPreferences(){
        var prefs=SidebarSettings.prefs(c);prefs.edit().putBoolean("sidebarEnabled",false).putInt("windowDim",20).commit();
        android.content.SharedPreferences failing=(android.content.SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class[]{android.content.SharedPreferences.class},(proxy,method,args)->{
                if(!method.getName().equals("edit"))return method.invoke(prefs,args);
                var editor=prefs.edit();
                return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{android.content.SharedPreferences.Editor.class},(ep,em,ea)->{
                    Object result=em.invoke(editor,ea);if(em.getName().equals("commit"))return false;return result==editor?ep:result;
                });
            });
        Context broken=new android.content.ContextWrapper(c){@Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return failing;}};
        assertThrows(IllegalStateException.class,()->SidebarSettings.flag(broken,"sidebarEnabled",true));
        assertFalse(SidebarSettings.read(c).enabled());assertEquals(20,prefs.getInt("windowDim",0));
    }
}
