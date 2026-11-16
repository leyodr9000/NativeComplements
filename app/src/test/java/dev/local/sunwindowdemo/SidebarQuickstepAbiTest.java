package dev.local.sunwindowdemo;
import org.junit.Test;
import static org.junit.Assert.*;
public class SidebarQuickstepAbiTest {
 public static class Supported { public void finish(boolean home,Runnable done,boolean hint){} }
 public static class Reason { public Reason(String value){} }
 public static class SupportedReason { public void finishController(boolean home,Runnable done,boolean hint,Reason reason){} }
 public static class Wrong {public int finish(boolean home,Runnable done,boolean hint){return 0;} }
 public static class UnknownBoolean {public void finish(boolean home,Runnable done,boolean hint,boolean force){} }
 @Test public void rejectsUnknownCompletionAbiRatherThanGuessingArguments()throws Exception{
  assertEquals(3,LauncherSwipeDrop.completion(Supported.class).getParameterCount());
  assertEquals(4,LauncherSwipeDrop.completion(SupportedReason.class).getParameterCount());
  assertThrows(NoSuchMethodException.class,()->LauncherSwipeDrop.completion(Wrong.class));
  assertThrows(NoSuchMethodException.class,()->LauncherSwipeDrop.completion(UnknownBoolean.class));
 }
}
