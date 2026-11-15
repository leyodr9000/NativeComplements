package dev.local.sunwindowdemo;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SidebarCornerMenuTest {
    @Test public void emptyCornerAppListStillDrawsThreeDotsAndSelectsTheApplicationPanel(){
        for(boolean right:new boolean[]{false,true}){
            FanView fan=new FanView(RuntimeEnvironment.getApplication(),List.of(),right,44,180,0,90);
            fan.layout(0,0,400,800);ReflectionHelpers.setField(fan,"progress",1f);
            Bitmap image=Bitmap.createBitmap(400,800,Bitmap.Config.ARGB_8888);fan.draw(new Canvas(image));
            float offset=(float)(180/Math.sqrt(2));
            float x=(right?366:34)+(right?-offset:offset),y=766-offset;
            // Three separated dots retain foreground contrast against their accent circle.
            int center=image.getPixel(Math.round(x),Math.round(y));
            assertNotEquals(center,image.getPixel(Math.round(x+4),Math.round(y+8)));
            assertEquals(center,image.getPixel(Math.round(x-44*.2f),Math.round(y)));
            assertEquals(center,image.getPixel(Math.round(x+44*.2f),Math.round(y)));
            assertEquals(0,fan.pointer(x,y));assertEquals("",fan.picked());
            assertEquals(-1,fan.pointer(-100,-100));assertNull(fan.picked());
            image.recycle();
        }
    }
}
