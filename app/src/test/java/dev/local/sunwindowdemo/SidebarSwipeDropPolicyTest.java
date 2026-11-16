package dev.local.sunwindowdemo;

import org.junit.Test;
import static org.junit.Assert.*;

/** Pure tests are also part of the existing Sidebar* CI filter. */
public class SidebarSwipeDropPolicyTest {
 @Test public void onlyNativeBottomGestureOfAppOnPrimaryPortraitDisplayCanStart(){
  assertTrue(SwipeDropPolicy.canStart(true,true,true,true,0,1,42,false,1080,2400,2390));
  assertFalse(SwipeDropPolicy.canStart(false,true,true,true,0,1,42,false,1080,2400,2390));
  assertFalse(SwipeDropPolicy.canStart(true,false,true,true,0,1,42,false,1080,2400,2390));
  assertFalse(SwipeDropPolicy.canStart(true,true,false,true,0,1,42,false,1080,2400,2390));
  assertFalse(SwipeDropPolicy.canStart(true,true,true,true,1,1,42,false,1080,2400,2390));
  assertFalse(SwipeDropPolicy.canStart(true,true,true,true,0,2,42,false,1080,2400,2390));
  assertFalse(SwipeDropPolicy.canStart(true,true,true,true,0,1,42,true,1080,2400,2390));
  assertFalse(SwipeDropPolicy.canStart(true,true,true,true,0,1,-1,false,1080,2400,2390));
  assertFalse(SwipeDropPolicy.canStart(true,true,true,true,0,1,42,false,2400,1080,1070));
  assertFalse(SwipeDropPolicy.canStart(true,true,true,true,0,1,42,false,1080,2400,1200));
 }
 @Test public void releaseRequiresSameRecognizedSinglePointerAndBoundedGesture(){
  assertTrue(SwipeDropPolicy.release(true,true,true,true,500,1));
  assertFalse(SwipeDropPolicy.release(false,true,true,true,500,1));
  assertFalse(SwipeDropPolicy.release(true,false,true,true,500,1));
  assertFalse(SwipeDropPolicy.release(true,true,false,true,500,1));
  assertFalse(SwipeDropPolicy.release(true,true,true,false,500,1));
  assertFalse(SwipeDropPolicy.release(true,true,true,true,20,1));
  assertFalse(SwipeDropPolicy.release(true,true,true,true,13000,1));
  assertFalse(SwipeDropPolicy.release(true,true,true,true,500,2));
 }
 @Test public void targetIsBoundedAtPhysicalUpperRight(){
  assertTrue(SwipeDropPolicy.inside(960,70,1080,2400,160));
  assertFalse(SwipeDropPolicy.inside(10,70,1080,2400,160));
  assertFalse(SwipeDropPolicy.inside(960,900,1080,2400,160));
  assertFalse(SwipeDropPolicy.inside(Float.NaN,70,1080,2400,160));
 }
}
