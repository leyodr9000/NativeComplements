package dev.local.sunwindowdemo;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** SystemUI-only, non-focusable launch strip. It never starts activities directly. */
final class SidebarHost implements AutoCloseable {
    private final Context context;
    private final WindowManager wm;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Consumer<String> launch;
    private final Runnable changed;
    private View handle,panel;
    private SidebarSettings.State settings;
    private List<AppCatalog.App> apps=List.of();
    private boolean allowed,closed;
    private long leaseUntil;
    private String owner="";
    private final Runnable expire=()->{if(android.os.SystemClock.elapsedRealtime()>=leaseUntil){allowed=false;hide();}};
    SidebarHost(Context context,Consumer<String> launch,Runnable changed){
        this.context=context;this.wm=context.getSystemService(WindowManager.class);this.launch=launch;this.changed=changed;
    }
    void update(SidebarSettings.State next,List<AppCatalog.App> catalog,boolean show,String foreground,long validUntil){
        if(closed)return;
        boolean rebuild=!Objects.equals(settings,next)||apps!=catalog||!Objects.equals(owner,foreground);
        settings=next;apps=catalog;owner=foreground;allowed=show;leaseUntil=validUntil;
        main.removeCallbacks(expire);
        if(!show){hide();return;}
        main.postDelayed(expire,Math.max(1,validUntil-android.os.SystemClock.elapsedRealtime()));
        if(rebuild)hide();
        if(handle==null&&panel==null)showHandle();
    }
    private boolean canShow(){return !closed&&allowed&&android.os.SystemClock.elapsedRealtime()<leaseUntil;}
    private int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private WindowManager.LayoutParams params(int width,int height,String title){
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,SystemUiHost.OVERLAY_TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            |WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.setTitle(title);p.windowAnimations=0;p.setFitInsetsTypes(0);
        SystemUiHost.trust(p);return p;
    }
    private void place(WindowManager.LayoutParams p,int width,int height){
        WindowMetrics metrics=wm.getCurrentWindowMetrics();Rect b=metrics.getBounds();
        android.graphics.Insets in=metrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
        int left=in.left+dp(2),right=b.width()-in.right-dp(2),top=in.top+dp(8),bottom=b.height()-in.bottom-dp(8);
        p.width=Math.min(width,Math.max(1,right-left));p.height=Math.min(height,Math.max(1,bottom-top));
        p.x=settings.right()?Math.max(left,right-p.width):left;
        p.y=SidebarPolicy.top(top,Math.max(top+1,bottom),p.height,settings.position());
        p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    }
    private GradientDrawable background(int color,int opacity,int radius){
        GradientDrawable bg=new GradientDrawable();bg.setColor((Math.round(opacity*2.55f)<<24)|(color&0xffffff));bg.setCornerRadius(dp(radius));return bg;
    }
    private void showHandle(){
        if(!canShow())return;
        View view=new View(context);view.setContentDescription("展开小窗侧边栏");
        view.setBackground(background(0xff90a4ce,settings.opacity(),8));
        view.setOnClickListener(v->expand());
        view.setOnTouchListener(new View.OnTouchListener(){
            float down;boolean dragged;
            @Override public boolean onTouch(View v,MotionEvent e){
                switch(e.getActionMasked()){
                    case MotionEvent.ACTION_DOWN->{down=e.getRawX();dragged=false;return true;}
                    case MotionEvent.ACTION_MOVE->{
                        float inward=(e.getRawX()-down)*(settings.right()?-1:1);
                        if(!dragged&&inward>=dp(16)){dragged=true;expand();}return true;
                    }
                    case MotionEvent.ACTION_UP->{if(!dragged)v.performClick();return true;}
                    case MotionEvent.ACTION_CANCEL->{return true;}
                }
                return true;
            }
        });
        WindowManager.LayoutParams p=params(dp(10),dp(settings.handleHeight()),"NativeComplements-sidebar-handle");
        place(p,dp(10),dp(settings.handleHeight()));
        view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->changed.run());
        try{wm.addView(view,p);handle=view;changed.run();}catch(RuntimeException failure){android.util.Log.w("NcSidebar","handle unavailable",failure);}
    }
    private List<AppCatalog.App> selected(){
        List<AppCatalog.App> result=new ArrayList<>();
        for(String pkg:settings.apps())for(AppCatalog.App app:apps)if(app.pkg.equals(pkg)){result.add(app);break;}
        return result;
    }
    void expand(){
        if(!canShow()||panel!=null)return;
        removeHandle();
        LinearLayout column=new LinearLayout(context);column.setOrientation(LinearLayout.VERTICAL);column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(8),dp(8),dp(8),dp(8));column.setBackground(background(0xff303744,settings.opacity(),24));
        ScrollView scroller=new ScrollView(context){
            @Override public boolean onTouchEvent(MotionEvent event){
                if(event.getActionMasked()==MotionEvent.ACTION_OUTSIDE){collapse();return true;}
                return super.onTouchEvent(event);
            }
        };
        scroller.setFillViewport(false);scroller.addView(column);scroller.setVerticalScrollBarEnabled(false);
        TextView close=new TextView(context);close.setText("收起");close.setTextColor(Color.WHITE);close.setGravity(Gravity.CENTER);close.setTextSize(12);
        close.setContentDescription("收起小窗侧边栏");close.setOnClickListener(v->collapse());column.addView(close,new LinearLayout.LayoutParams(-1,dp(34)));
        List<AppCatalog.App> selected=selected();
        for(AppCatalog.App app:selected){
            LinearLayout item=new LinearLayout(context);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(0,dp(6),0,dp(6));
            ImageView image=new ImageView(context);image.setImageDrawable(app.icon==null?null:app.icon.getConstantState()==null?app.icon:app.icon.getConstantState().newDrawable(context.getResources()).mutate());
            item.addView(image,new LinearLayout.LayoutParams(dp(settings.iconSize()),dp(settings.iconSize())));
            TextView label=new TextView(context);label.setText(app.label);label.setTextColor(Color.WHITE);label.setTextSize(11);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);
            item.addView(label,new LinearLayout.LayoutParams(-1,dp(21)));item.setContentDescription("小窗打开 "+app.label);
            item.setOnClickListener(v->{if(!canShow())return;collapse();launch.accept(app.pkg);});
            column.addView(item,new LinearLayout.LayoutParams(-1,dp(settings.iconSize()+33)));
        }
        if(selected.isEmpty()){
            TextView empty=new TextView(context);empty.setText("请先设置\n小窗应用");empty.setTextColor(Color.WHITE);empty.setGravity(Gravity.CENTER);
            column.addView(empty,new LinearLayout.LayoutParams(-1,dp(80)));
        }
        int wanted=dp(50+Math.max(1,selected.size())*(settings.iconSize()+33));
        WindowManager.LayoutParams p=params(dp(settings.iconSize()+40),wanted,"NativeComplements-sidebar-panel");place(p,p.width,wanted);
        scroller.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->changed.run());
        try{wm.addView(scroller,p);panel=scroller;changed.run();}catch(RuntimeException failure){showHandle();}
    }
    void collapse(){removePanel();if(canShow()&&handle==null)showHandle();}
    void hide(){removePanel();removeHandle();}
    boolean expanded(){return panel!=null;}
    int[] protectedRects(){
        View v=panel!=null?panel:handle;if(v==null||!v.isAttachedToWindow())return new int[0];
        int[] at=new int[2];v.getLocationOnScreen(at);return new int[]{at[0],at[1],at[0]+v.getWidth(),at[1]+v.getHeight()};
    }
    private void removePanel(){if(panel!=null){remove(panel);panel=null;changed.run();}}
    private void removeHandle(){if(handle!=null){remove(handle);handle=null;changed.run();}}
    private void remove(View v){try{wm.removeViewImmediate(v);}catch(RuntimeException ignored){}}
    @Override public void close(){closed=true;allowed=false;main.removeCallbacksAndMessages(null);hide();}
}
