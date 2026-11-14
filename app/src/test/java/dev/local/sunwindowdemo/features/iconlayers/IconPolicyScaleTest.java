package dev.local.sunwindowdemo.features.iconlayers;

import android.content.Context;
import android.content.SharedPreferences;
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

/** Persistence checks deliberately need no native graphics runtime. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class IconPolicyScaleTest {
    private static final String FIRST="test.icons.first", SECOND="test.icons.second";
    private Context context;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();
        IconLayerStore.prefs(context).edit().clear().commit();
    }
    @Test public void oldJsonDefaultsTo100AndUntrustedSizesAreBounded(){
        assertEquals(100,new IconPolicy().notificationScale);
        assertEquals(100,IconPolicy.parse("{\"test.icons.first\":{}}").get(FIRST).notificationScale);
        assertEquals(IconPolicy.UNCHANGED,IconPolicy.parse("{\"test.icons.first\":{}}").get(FIRST).notification);
        assertEquals(50,IconPolicy.parse("{\"test.icons.first\":{\"notificationScale\":-20}}").get(FIRST).notificationScale);
        assertEquals(200,IconPolicy.parse("{\"test.icons.first\":{\"notificationScale\":400}}").get(FIRST).notificationScale);
    }
    @Test public void batchSizePublishesOnceAndPreservesIndependentAssetsAndLegacyModes(){
        IconPolicy p=new IconPolicy();p.color=IconPolicy.MANUAL;p.monochrome=IconPolicy.UPLOAD;p.notification=IconPolicy.UPLOAD;
        p.notificationScale=125;p.background="a".repeat(64);p.foreground="b".repeat(64);
        p.monoImage="c".repeat(64);p.notificationImage="d".repeat(64);
        p.generatedBackground="e".repeat(64);p.generatedForeground="f".repeat(64);p.generatedVersion="retained";
        IconPolicy.save(context,FIRST,p);
        var prefs=IconLayerStore.prefs(context);
        prefs.edit().putStringSet("originals",Set.of(SECOND,"test.icons.untouched")).commit();
        int[] publications={0};
        SharedPreferences.OnSharedPreferenceChangeListener listener=(store,key)->{if("policies".equals(key))publications[0]++;};
        prefs.registerOnSharedPreferenceChangeListener(listener);
        try{IconPolicy.batch(context,Set.of(FIRST,SECOND),-1,-1,-1,150);}
        finally{prefs.unregisterOnSharedPreferenceChangeListener(listener);}
        assertEquals(1,publications[0]);
        IconPolicy saved=IconPolicy.read(context,FIRST);
        assertEquals(150,saved.notificationScale);assertEquals(IconPolicy.MANUAL,saved.color);
        assertEquals(IconPolicy.UPLOAD,saved.monochrome);assertEquals(IconPolicy.UPLOAD,saved.notification);
        assertEquals(p.background,saved.background);assertEquals(p.foreground,saved.foreground);
        assertEquals(p.monoImage,saved.monoImage);assertEquals(p.notificationImage,saved.notificationImage);
        assertEquals(p.generatedBackground,saved.generatedBackground);assertEquals(p.generatedForeground,saved.generatedForeground);
        assertEquals(p.generatedVersion,saved.generatedVersion);
        assertEquals(IconPolicy.ORIGINAL,IconPolicy.read(context,SECOND).color);
        assertEquals(150,IconPolicy.read(context,SECOND).notificationScale);
        assertEquals(100,IconPolicy.read(context,"test.icons.untouched").notificationScale);
        assertEquals(Set.of("test.icons.untouched"),prefs.getStringSet("originals",Set.of()));
    }
    @Test public void followingForegroundRetainsEachAppsSizeUnlessExplicitlyChanged(){
        IconPolicy p=new IconPolicy();p.notificationScale=135;IconPolicy.save(context,FIRST,p);
        IconPolicy.batch(context,Set.of(FIRST),-1,IconPolicy.FOLLOW,IconPolicy.FOLLOW);
        IconPolicy saved=IconPolicy.read(context,FIRST);
        assertEquals(135,saved.notificationScale);assertEquals(IconPolicy.FOLLOW,saved.notification);
        assertEquals(IconPolicy.FOLLOW,saved.monochrome);
        IconPolicy.batch(context,Set.of(FIRST),-1,-1,-1,100);
        assertEquals(100,IconPolicy.read(context,FIRST).notificationScale);
        assertEquals(IconPolicy.FOLLOW,IconPolicy.read(context,FIRST).notification);
    }
    @Test public void invalidBatchCannotPartiallyPublishAndKeepAllIsNoOp(){
        IconPolicy p=new IconPolicy();p.notificationScale=150;IconPolicy.save(context,FIRST,p);
        String before=IconLayerStore.prefs(context).getString("policies","{}");
        assertThrows(IllegalArgumentException.class,()->IconPolicy.batch(context,Set.of(FIRST),-1,-1,-1,49));
        assertThrows(IllegalArgumentException.class,()->IconPolicy.batch(context,Set.of(FIRST),-1,-1,-1,201));
        assertThrows(IllegalArgumentException.class,()->IconPolicy.batch(context,Set.of(FIRST,"invalid/path"),-1,-1,IconPolicy.FOLLOW,125));
        IconPolicy.batch(context,Set.of(FIRST),-1,-1,-1,-1);
        assertEquals(before,IconLayerStore.prefs(context).getString("policies","{}"));
    }
}
