package dev.local.sunwindowdemo.features.iconlayers;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GlobalNotificationScaleTest {
    private Context c;
    @Before public void setup(){c=RuntimeEnvironment.getApplication();IconLayerStore.prefs(c).edit().clear().commit();}
    private Context recording(List<Intent> sent,SharedPreferences replacement){
        return new ContextWrapper(c){
            @Override public Context createDeviceProtectedStorageContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){return replacement;}
            @Override public void sendBroadcast(Intent intent){sent.add(intent);}
        };
    }
    @Test public void absentGlobalFieldUsesCurrent100BaselineAndDoesNotMigratePerAppValues(){
        String old="{\"test.one\":{\"notificationScale\":180},\"test.two\":{\"notificationScale\":65}}";
        IconLayerStore.prefs(c).edit().putString("policies",old).commit();
        assertEquals(100,IconLayerStore.notificationScale(c));
        assertEquals(100,IconLayerStore.read(c).getInt("notificationScale"));
        assertEquals(old,IconLayerStore.prefs(c).getString("policies",""));
        assertFalse(IconLayerStore.prefs(c).contains("notificationScale"));
    }
    @Test public void globalSaveTouchesOnlyOneKeyAndRefreshesEachHostOnce(){
        var prefs=IconLayerStore.prefs(c);String old="{\"test.one\":{\"notificationScale\":180,\"notification\":3}}";
        prefs.edit().putString("policies",old).putString("generation","desktop").putString("notificationGeneration","notification").commit();
        List<Intent> sent=new ArrayList<>();Context wrapped=recording(sent,prefs);
        IconLayerStore.notificationScale(wrapped,145);
        assertEquals(145,IconLayerStore.read(c).getInt("notificationScale"));
        assertEquals(old,prefs.getString("policies",""));
        assertEquals("desktop",prefs.getString("generation",""));assertEquals("notification",prefs.getString("notificationGeneration",""));
        assertEquals(4,sent.size());assertEquals(4,sent.stream().map(Intent::getPackage).distinct().count());
        assertTrue(sent.stream().allMatch(i->IconLayerStore.REFRESH.equals(i.getAction())));
        IconLayerStore.notificationScale(wrapped,145);assertEquals("Same value is a no-op",4,sent.size());
        IconLayerStore.notificationScale(wrapped,100);assertEquals(100,IconLayerStore.notificationScale(c));assertEquals(8,sent.size());
    }
    @Test public void untrustedReadIsClampedButInvalidWritesAreRejectedWithoutPublication(){
        var prefs=IconLayerStore.prefs(c);prefs.edit().putInt("notificationScale",999).commit();
        assertEquals(200,IconLayerStore.read(c).getInt("notificationScale"));
        prefs.edit().putInt("notificationScale",1).commit();assertEquals(50,IconLayerStore.notificationScale(c));
        List<Intent> sent=new ArrayList<>();Context wrapped=recording(sent,prefs);
        assertThrows(IllegalArgumentException.class,()->IconLayerStore.notificationScale(wrapped,49));
        assertThrows(IllegalArgumentException.class,()->IconLayerStore.notificationScale(wrapped,201));
        assertEquals(1,prefs.getInt("notificationScale",0));assertTrue(sent.isEmpty());
    }
    @Test public void commitFailureAfterMemoryPublicationRollsBackExistingAndAbsentGlobalKey(){
        var prefs=IconLayerStore.prefs(c);List<Intent> sent=new ArrayList<>();
        SharedPreferences failing=(SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.class},(proxy,method,args)->{
            if(!method.getName().equals("edit"))return method.invoke(prefs,args);
            var editor=prefs.edit();
            return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.Editor.class},(ep,em,ea)->{
                Object result=em.invoke(editor,ea);
                if(em.getName().equals("commit"))return false; // Actually publish the map before reporting failure.
                return result==editor?ep:result;
            });
        });
        Context broken=recording(sent,failing);
        assertThrows(IllegalStateException.class,()->IconLayerStore.notificationScale(broken,135));
        assertFalse(prefs.contains("notificationScale"));assertEquals(100,IconLayerStore.notificationScale(c));
        prefs.edit().putInt("notificationScale",120).commit();
        assertThrows(IllegalStateException.class,()->IconLayerStore.notificationScale(broken,135));
        assertEquals(120,IconLayerStore.notificationScale(c));assertTrue(sent.isEmpty());
    }
}
