package dev.local.sunwindowdemo.features.iconlayers;

import android.content.Context;
import java.io.File;
import java.nio.file.Files;
import java.util.Set;
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
public class NotificationLibraryStateTest {
    private Context c;
    @Before public void setup(){
        c=RuntimeEnvironment.getApplication();IconLayerStore.prefs(c).edit().clear().commit();
        IconArchive.delete(IconLibrary.root(c));
    }
    private String create(String prefix,char id,String pkg)throws Exception{
        String name=prefix+String.valueOf(id).repeat(32);
        File root=IconLibrary.generation(c,name);assertTrue(root.mkdirs());
        Files.writeString(new File(root,"index.json").toPath(),"[\""+pkg+"\"]");return name;
    }
    @Test public void bothIndexesCanBeReadAlternatelyWithoutReplacingEachOther()throws Exception{
        String g=create("g_",'1',"test.desktop.app"),n=create("n_",'2',"test.notice.app");
        for(int i=0;i<4;i++){
            assertEquals(Set.of("test.desktop.app"),Set.copyOf(IconLibrary.packages(c,g)));
            var notice=IconLibrary.packages(c,n);assertEquals(Set.of("test.notice.app"),Set.copyOf(notice));
            notice.clear();assertEquals(1,IconLibrary.packages(c,n).size());
        }
    }
    @Test public void launcherCleanupCannotDeleteNotificationGenerationsAndViceVersa()throws Exception{
        String g=create("g_",'3',"test.desktop.app"),oldG=create("g_",'4',"test.desktop.old");
        String n=create("n_",'5',"test.notice.app"),oldN=create("n_",'6',"test.notice.old");
        IconLayerStore.prefs(c).edit().putString("generation",g).putString("notificationGeneration",n).commit();
        IconLibrary.acknowledge(c,g);
        assertFalse(IconLibrary.generation(c,oldG).exists());assertTrue(IconLibrary.generation(c,g).exists());
        assertTrue(IconLibrary.generation(c,n).exists());assertTrue(IconLibrary.generation(c,oldN).exists());
        IconLibrary.acknowledgeNotification(c,oldN);assertTrue(IconLibrary.generation(c,oldN).exists());
        IconLibrary.acknowledgeNotification(c,n);
        assertFalse(IconLibrary.generation(c,oldN).exists());assertTrue(IconLibrary.generation(c,n).exists());
        assertTrue(IconLibrary.generation(c,g).exists());
    }
    @Test public void notificationRemovalLeavesDesktopAndPoliciesAndDefersFileDeletion()throws Exception{
        String g=create("g_",'7',"test.desktop.app"),n=create("n_",'8',"test.notice.app");
        var prefs=IconLayerStore.prefs(c);
        prefs.edit().putString("generation",g).putString("notificationGeneration",n).putInt("notificationCount",1)
            .putString("notificationTitle","sample").putString("policies","{\"test.notice.app\":{\"notification\":3}}" ).commit();
        IconLibrary.removeNotification(c);
        assertEquals(g,prefs.getString("generation",""));assertEquals("",prefs.getString("notificationGeneration",""));
        assertEquals(0,IconLayerStore.read(c).getInt("notificationCount"));
        assertEquals(IconPolicy.NOTIFICATION_LIBRARY,IconPolicy.read(c,"test.notice.app").notification);
        assertTrue(IconLibrary.generation(c,n).exists());
        IconLibrary.acknowledgeNotification(c,"");assertFalse(IconLibrary.generation(c,n).exists());
        assertTrue(IconLibrary.generation(c,g).exists());
    }
    @Test public void desktopRemovalLeavesNotificationSlotPublished()throws Exception{
        String g=create("g_",'9',"test.desktop.app"),n=create("n_",'a',"test.notice.app");
        var prefs=IconLayerStore.prefs(c);
        prefs.edit().putString("generation",g).putString("notificationGeneration",n).putInt("notificationCount",1).commit();
        IconLibrary.remove(c);
        assertEquals(n,prefs.getString("notificationGeneration",""));assertEquals(1,IconLayerStore.read(c).getInt("notificationCount"));
        assertTrue(IconLibrary.generation(c,n).exists());
    }
    @Test public void failedPublicationRollbackRestoresInMemorySlotWithoutTouchingDesktop()throws Exception{
        String g=create("g_",'b',"test.desktop.app"),n=create("n_",'c',"test.notice.app");
        var prefs=IconLayerStore.prefs(c);
        prefs.edit().putString("generation",g).putString("notificationGeneration",n).putInt("notificationCount",1).commit();
        var previous=new java.util.HashMap<String,Object>();
        previous.put("notificationGeneration",n);previous.put("notificationTitle",null);previous.put("notificationCount",1);
        // Model Android's commit failure after the editor has already published its new map in memory.
        prefs.edit().putString("notificationGeneration","n_"+"d".repeat(32)).putString("notificationTitle","new").putInt("notificationCount",2).commit();
        IconLibrary.restorePreferences(prefs,previous);
        assertEquals(n,prefs.getString("notificationGeneration",""));assertEquals(1,prefs.getInt("notificationCount",0));
        assertFalse(prefs.contains("notificationTitle"));assertEquals(g,prefs.getString("generation",""));
    }
    private IconArchive importArchive()throws Exception{
        File temp=Files.createTempDirectory(c.getCacheDir().toPath(),"notice-import-").toFile();
        byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAYAAADED76LAAAAFklEQVR4nGM8IafxnwEPYMInOXwUAAAhVgIdCzl+awAAAABJRU5ErkJggg==");
        try(var out=new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(new File(temp,"source.zip")))){
            for(int layer=0;layer<2;layer++){
                out.putNextEntry(new java.util.zip.ZipEntry("icons/test.notice.new/"+layer+".png"));out.write(png);out.closeEntry();
            }
        }
        return new IconArchive(temp,new java.util.concurrent.atomic.AtomicBoolean());
    }
    @Test public void importingNotificationSlotDoesNotReplaceDesktopAndFailedCommitRestoresOldSlot()throws Exception{
        String g=create("g_",'e',"test.desktop.app"),n=create("n_",'f',"test.notice.app");
        var prefs=IconLayerStore.prefs(c);
        prefs.edit().putString("generation",g).putString("notificationGeneration",n).putInt("notificationCount",1).commit();
        android.content.SharedPreferences failing=(android.content.SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class[]{android.content.SharedPreferences.class},(proxy,method,args)->{
                if(!method.getName().equals("edit"))return method.invoke(prefs,args);
                var editor=prefs.edit();
                return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{android.content.SharedPreferences.Editor.class},(ep,em,ea)->{
                    Object result=em.invoke(editor,ea);
                    if(em.getName().equals("commit"))return false; // New map was actually applied before reporting disk failure.
                    return result==editor?ep:result;
                });
            });
        Context broken=new android.content.ContextWrapper(c){
            @Override public Context createDeviceProtectedStorageContext(){return this;}
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return failing;}
        };
        try(IconArchive archive=importArchive()){
            assertThrows(java.io.IOException.class,()->IconLibrary.install(broken,archive,archive.sources.get(0),
                new java.util.concurrent.atomic.AtomicBoolean(),(text,done,total)->{},true));
        }
        assertEquals(g,prefs.getString("generation",""));assertEquals(n,prefs.getString("notificationGeneration",""));
        assertTrue(IconLibrary.generation(c,n).exists());assertEquals(1,prefs.getInt("notificationCount",0));
        try(IconArchive archive=importArchive()){
            assertEquals(1,IconLibrary.install(c,archive,archive.sources.get(0),new java.util.concurrent.atomic.AtomicBoolean(),(text,done,total)->{},true).imported);
        }
        String next=prefs.getString("notificationGeneration","");assertTrue(next.startsWith("n_"));assertNotEquals(n,next);
        assertEquals(g,prefs.getString("generation",""));assertTrue(IconLibrary.generation(c,n).exists());
        assertEquals(Set.of("test.notice.new"),Set.copyOf(IconLibrary.packages(c,next)));
    }
    @Test public void untrustedCallerCannotAcknowledgeNotificationCleanup()throws Exception{
        IconLayerProvider provider=org.robolectric.Robolectric.buildContentProvider(IconLayerProvider.class).create().get();
        assertThrows(SecurityException.class,()->provider.call("notificationLoaded","",null));
        assertFalse(IconLibrary.validGeneration("n_../../outside"));
    }
}
