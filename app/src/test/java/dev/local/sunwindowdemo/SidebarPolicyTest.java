package dev.local.sunwindowdemo;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SidebarPolicyTest {
    private boolean show(boolean enabled,boolean windows,boolean ready,boolean interactive,boolean locked,boolean overlay,
        boolean ime,boolean landscape,boolean landscapeOnly,String pkg,long age){
        return SidebarPolicy.visible(enabled,windows,ready,interactive,locked,overlay,ime,landscape,landscapeOnly,pkg,Set.of("com.tencent.tmgp.sgame"),age);
    }
    @Test public void requiresExplicitAllowedFullscreenAndFreshInteractiveUnlockedBackend(){
        assertTrue(show(true,true,true,true,false,false,false,true,true,"com.tencent.tmgp.sgame",750));
        assertFalse(show(false,true,true,true,false,false,false,true,true,"com.tencent.tmgp.sgame",0));
        assertFalse(show(true,false,true,true,false,false,false,true,true,"com.tencent.tmgp.sgame",0));
        assertFalse(show(true,true,false,true,false,false,false,true,true,"com.tencent.tmgp.sgame",0));
        assertFalse(show(true,true,true,false,false,false,false,true,true,"com.tencent.tmgp.sgame",0));
        assertFalse(show(true,true,true,true,true,false,false,true,true,"com.tencent.tmgp.sgame",0));
        assertFalse(show(true,true,true,true,false,true,false,true,true,"com.tencent.tmgp.sgame",0));
        assertFalse(show(true,true,true,true,false,false,true,true,true,"com.tencent.tmgp.sgame",0));
        assertFalse(show(true,true,true,true,false,false,false,true,true,null,0));
        assertFalse(show(true,true,true,true,false,false,false,true,true,"com.tencent.mobileqq",0));
        assertFalse(show(true,true,true,true,false,false,false,true,true,"com.tencent.tmgp.sgame",2501));
        assertFalse(show(true,true,true,true,false,false,false,true,true,"com.tencent.tmgp.sgame",-1));
    }
    @Test public void landscapeGateIsIndependentAndPortraitRequiresOptIn(){
        assertFalse(show(true,true,true,true,false,false,false,false,true,"com.tencent.tmgp.sgame",0));
        assertTrue(show(true,true,true,true,false,false,false,false,false,"com.tencent.tmgp.sgame",0));
    }
    @Test public void orderedTargetsAreValidatedBoundedAndDefensivelyCopied(){
        var mutable=new ArrayList<>(List.of("com.tencent.mobileqq","com.tencent.mm"));
        var ordered=SidebarPolicy.packages(mutable,12);mutable.clear();
        assertEquals(List.of("com.tencent.mobileqq","com.tencent.mm"),ordered);
        assertThrows(IllegalArgumentException.class,()->SidebarPolicy.packages(List.of("bad/path"),12));
        assertThrows(IllegalArgumentException.class,()->SidebarPolicy.packages(List.of("com.test.app","com.test.app"),12));
        assertThrows(IllegalArgumentException.class,()->SidebarPolicy.packages(Collections.nCopies(13,"com.test.app"),12));
    }
    @Test public void safeAreaPositionClampsAndFitsBothLandscapeRotations(){
        assertEquals(40,SidebarPolicy.top(40,1080,100,0));
        assertEquals(980,SidebarPolicy.top(40,1080,100,100));
        assertEquals(510,SidebarPolicy.top(40,1080,100,50));
        assertEquals(40,SidebarPolicy.top(40,1080,1800,100));
        assertEquals(40,SidebarPolicy.top(40,1080,100,-20));
        assertEquals(980,SidebarPolicy.top(40,1080,100,120));
    }
}
