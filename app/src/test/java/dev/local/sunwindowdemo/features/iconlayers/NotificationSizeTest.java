package dev.local.sunwindowdemo.features.iconlayers;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class NotificationSizeTest {
    @Test public void notificationUsesNativePixelDimensionsAndKeepsGlyphAspectRatio(){
        Context context=RuntimeEnvironment.getApplication();
        Bitmap source=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);
        Paint paint=new Paint();paint.setColor(Color.WHITE);
        new Canvas(source).drawRect(40,52,88,76,paint);
        Bitmap normalized=IconImages.monochrome(source,true);
        Rect bounds=IconImages.contentBounds(normalized);
        assertTrue(bounds.width()>=59&&bounds.width()<=61);
        assertEquals(2f,(float)bounds.width()/bounds.height(),.15f);
        Drawable replacement=new BitmapDrawable(context.getResources(),normalized);
        Drawable original=new ColorDrawable(Color.WHITE){
            @Override public int getIntrinsicWidth(){return 48;}
            @Override public int getIntrinsicHeight(){return 48;}
        };
        Drawable result=IconImages.notificationSize(context,replacement,original);
        assertEquals(48,result.getIntrinsicWidth());assertEquals(48,result.getIntrinsicHeight());
        Rect rendered=IconImages.contentBounds(IconImages.render(result,48));
        assertTrue(rendered.width()>=44&&rendered.width()<=47);
        assertEquals(2f,(float)rendered.width()/rendered.height(),.2f);
        assertSame(replacement,IconImages.notificationSize(context,replacement,null));
    }
    @Test public void customScaleKeepsNativeIntrinsicSizeAndCanDrawBeyondTheOriginalBounds(){
        Context context=RuntimeEnvironment.getApplication();
        Drawable original=new ColorDrawable(Color.WHITE){
            @Override public int getIntrinsicWidth(){return 20;}
            @Override public int getIntrinsicHeight(){return 20;}
        };
        Drawable result=IconImages.notificationSize(context,new ColorDrawable(Color.WHITE),original,150);
        assertEquals(20,result.getIntrinsicWidth());assertEquals(20,result.getIntrinsicHeight());
        Bitmap pixels=Bitmap.createBitmap(60,60,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(pixels);result.setBounds(20,20,40,40);result.draw(canvas);
        Rect content=IconImages.contentBounds(pixels);
        assertEquals(new Rect(15,15,45,45),content);
        assertTrue(canvas.getMatrix().isIdentity());
        Drawable copy=result.getConstantState().newDrawable(context.getResources()).mutate();
        assertNotSame(result,copy);assertEquals(20,copy.getIntrinsicWidth());
        Bitmap cloned=Bitmap.createBitmap(60,60,Bitmap.Config.ARGB_8888);
        copy.setBounds(20,20,40,40);copy.draw(new Canvas(cloned));
        assertEquals(content,IconImages.contentBounds(cloned));
    }
    @Test public void scaledDrawableForwardsTintStateAndClonedAlphaStaysIndependent(){
        Context context=RuntimeEnvironment.getApplication();
        Drawable source=new BitmapDrawable(context.getResources(),IconImages.solid(Color.WHITE,20));
        Drawable scaled=IconImages.notificationScale(source,150).mutate();
        scaled.setTintList(new android.content.res.ColorStateList(
            new int[][]{new int[]{android.R.attr.state_pressed},new int[]{}},new int[]{Color.RED,Color.BLUE}));
        scaled.setBounds(10,10,30,30);scaled.setState(new int[]{android.R.attr.state_pressed});
        Bitmap pressed=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);scaled.draw(new Canvas(pressed));
        assertEquals(Color.RED,pressed.getPixel(20,20));
        Drawable clone=scaled.getConstantState().newDrawable(context.getResources()).mutate();
        clone.setBounds(10,10,30,30);clone.setState(new int[]{});clone.setAlpha(100);
        Bitmap released=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);clone.draw(new Canvas(released));
        assertEquals(100,Color.alpha(released.getPixel(20,20)));
        assertEquals(255,scaled.getAlpha());
        Bitmap original=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);scaled.draw(new Canvas(original));
        assertEquals(Color.RED,original.getPixel(20,20));
    }
    @Test public void smallerScaleIsCenteredAndDefaultDoesNotWrap(){
        Drawable source=new ColorDrawable(Color.WHITE);
        assertSame(source,IconImages.notificationScale(source,100));
        Bitmap pixels=Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888);
        Drawable half=IconImages.notificationScale(source,50);half.setBounds(0,0,40,40);half.draw(new Canvas(pixels));
        assertEquals(new Rect(10,10,30,30),IconImages.contentBounds(pixels));
    }
}
