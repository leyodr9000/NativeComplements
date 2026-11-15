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
    @Test public void nativeBackExclusionMatchesOnlyActualSidebarRectangle(){
        int[] rect={2,400,12,472};assertTrue(SidebarPolicy.contains(rect,5,430));
        assertFalse(SidebarPolicy.contains(rect,12,430));assertFalse(SidebarPolicy.contains(rect,5,472));
        assertFalse(SidebarPolicy.contains(new int[0],5,430));assertFalse(SidebarPolicy.contains(rect,Float.NaN,430));
    }
    @Test public void restingHandleUsesPhysicalEdgeButExpandedPanelRetainsCutoutInsets(){
        // Insets swap when landscape rotates. Neither rotation may pull the collapsed handle inward.
        for(int[] insets:List.of(new int[]{140,2},new int[]{2,140})){
            assertEquals(0,SidebarPolicy.horizontal(2560,28,insets[0],insets[1],false,true));
            assertEquals(2532,SidebarPolicy.horizontal(2560,28,insets[0],insets[1],true,true));
            assertEquals(insets[0],SidebarPolicy.horizontal(2560,230,insets[0],insets[1],false,false));
            assertEquals(2560-insets[1]-230,SidebarPolicy.horizontal(2560,230,insets[0],insets[1],true,false));
        }
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
