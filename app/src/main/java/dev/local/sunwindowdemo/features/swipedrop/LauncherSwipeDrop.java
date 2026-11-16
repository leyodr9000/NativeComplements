package dev.local.sunwindowdemo;

import android.app.KeyguardManager;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.function.Consumer;

/** Independent optional Pixel adapter. Observes native input; never skips native DOWN/MOVE/UP. */
final class LauncherSwipeDrop implements AutoCloseable {
    static final String DROP=Wire.PKG+".QUICKSTEP_DROP";
    private final Consumer<String> log;
    private final Method resource,activeConsumer,taskId,runningTask,isHome,finish,managerGet,displayId,cachedTaskId,cachedPackage;
    private final Field gesture,consumer,interaction,started,repository,controller,serviceHandler,consumerGesture;
    private Context context;
    private Object boundOwner;
    private String lastStatus="";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable hideTimeout=this::reset;
    private WindowManager wm;private View zone;private TextView label;private int iconColor=0xff526596;
    private boolean configured,tracking,armed,closed;
    private IBinder lease;
    private long stateAt,downTime,epoch;
    private int sourceTask=-1,width,height,rotation;
    private String sourcePackage;
    private Object sourceGesture,sourceSwipe,inputOwner,beforeDownGesture;
    LauncherSwipeDrop(ClassLoader loader,Consumer<String> log)throws Exception{
        this.log=log;
        Class<?> handler=Class.forName("com.android.quickstep.TouchInteractionHandler",false,loader);
        resource=method(handler,"getInputResource",int.class);
        serviceHandler=field(Class.forName("com.android.quickstep.TouchInteractionService",false,loader),"handler");
        gesture=field(resource.getReturnType(),"gestureState");consumer=field(resource.getReturnType(),"consumer");
        Class<?> state=Class.forName("com.android.quickstep.GestureState",false,loader);
        taskId=method(state,"getTopRunningTaskId");runningTask=method(state,"getRunningTask");
        isHome=method(runningTask.getReturnType(),"isHomeTask");
        cachedTaskId=method(runningTask.getReturnType(),"getTaskId");cachedPackage=method(runningTask.getReturnType(),"getPackageName");
        Class<?> input=Class.forName("com.android.quickstep.InputConsumer",false,loader);
        activeConsumer=method(input,"getActiveConsumerInHierarchy");
        interaction=field(Class.forName("com.android.quickstep.inputconsumers.OtherActivityInputConsumer",false,loader),"mInteractionHandler");
        started=field(Class.forName("com.android.quickstep.AbsSwipeUpHandler",false,loader),"mGestureStarted");
        consumerGesture=field(interaction.getDeclaringClass(),"mGestureState");
        repository=field(handler,"mTaskAnimationManagerRepository");managerGet=repository.getType().getMethod("get",int.class);managerGet.setAccessible(true);
        controller=field(Class.forName("com.android.quickstep.TaskAnimationManager",false,loader),"mController");
        // Accept only known completion signatures, never fabricate null arguments for an unknown ABI.
        finish=completion(controller.getType());
        displayId=method(android.view.InputEvent.class,"getDisplayId");
        if(taskId.getReturnType()!=int.class||isHome.getReturnType()!=boolean.class||finish.getReturnType()!=void.class)
            throw new NoSuchMethodException("Quickstep return type");
    }
    void connect(Context service)throws Exception{close();closed=false;context=service.getApplicationContext();boundOwner=serviceHandler.get(service);
        if(boundOwner==null)throw new IllegalStateException("TouchInteractionService.handler is null");
        Context display=service.createDisplayContext(service.getSystemService(android.hardware.display.DisplayManager.class).getDisplay(0));
        Context window=display.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,null);
        wm=window.getSystemService(WindowManager.class);
        LinearLayout card=new LinearLayout(window);card.setGravity(Gravity.CENTER);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14),dp(14),dp(14),dp(14));
        View symbol=new View(window){final Paint p=new Paint(3);
            @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);p.setColor(iconColor);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));
                float w=getWidth(),h=getHeight();canvas.drawRoundRect(w*.18f,h*.16f,w*.82f,h*.84f,dp(7),dp(7),p);
                p.setStyle(Paint.Style.FILL);canvas.drawRoundRect(w*.53f,h*.23f,w*.76f,h*.55f,dp(3),dp(3),p);}
        };card.addView(symbol,new LinearLayout.LayoutParams(dp(48),dp(40)));
        label=new TextView(window);label.setTextSize(13);label.setGravity(Gravity.CENTER);card.addView(label);
        zone=card;zone.setVisibility(View.GONE);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(dp(144),dp(104),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.RIGHT;p.x=dp(16);p.y=dp(16);p.setTitle("NativeComplements-swipe-drop");
        // Do not put the hint under a camera cutout; the target uses the same visible bounds as its hit test.
        p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
        wm.addView(zone,p);paint(false);status("接口与提示区已就绪，等待后端配置");log.accept("SWIPE_DROP_READY Pixel17/907 exact CachedTaskInfo API");
    }
    void configure(boolean enabled,IBinder hostLease){
        configured=enabled&&hostLease!=null&&hostLease.isBinderAlive();lease=hostLease;stateAt=SystemClock.elapsedRealtime();
        if(!configured){reset();status("入口未就绪：开关关闭、后端忙或系统代码未更新");}
        else status("就绪：从应用上滑，再拖入右上角");
    }
    private boolean ready(){return !closed&&context!=null&&zone!=null&&configured&&lease!=null&&lease.isBinderAlive()
        &&SystemClock.elapsedRealtime()-stateAt<=2500&&!context.getSystemService(KeyguardManager.class).isKeyguardLocked()
        &&context.getSystemService(PowerManager.class).isInteractive();}
    /** Immutable point copied before Quickstep can rotate/mutate the borrowed MotionEvent. */
    record Point(int action,float x,float y,int pointers,boolean finger,int display,long time,long down){}
    Point capture(MotionEvent e)throws Exception{return new Point(e.getActionMasked(),e.getRawX(),e.getRawY(),e.getPointerCount(),
        e.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)&&e.getToolType(0)==MotionEvent.TOOL_TYPE_FINGER,(Integer)displayId.invoke(e),e.getEventTime(),e.getDownTime());}
    void before(Object owner,Point e){
        if(e.action!=MotionEvent.ACTION_DOWN||owner!=boundOwner||e.display!=0)return;
        beforeDownGesture=null;
        try{Object r=resource.invoke(owner,0);if(r!=null)beforeDownGesture=gesture.get(r);}catch(Exception ignored){}
    }
    void after(Object owner,Point e){
        try{
            if(!ready()){reset();return;}
            if(e.display!=0||owner!=boundOwner)return;
            if(e.action==MotionEvent.ACTION_DOWN){
                reset();var b=wm.getCurrentWindowMetrics().getBounds();width=b.width();height=b.height();
                Object r=resource.invoke(owner,0);Object g=r==null?null:gesture.get(r);if(g==null)return;
                int id=(Integer)taskId.invoke(g);Object task=runningTask.invoke(g);
                Object actualConsumer=consumer.get(r);actualConsumer=actualConsumer==null?null:activeConsumer.invoke(actualConsumer);
                boolean nativeAdmission=g!=beforeDownGesture&&actualConsumer!=null&&consumerGesture.getDeclaringClass().isInstance(actualConsumer)&&consumerGesture.get(actualConsumer)==g;
                if(!SwipeDropPolicy.nativeStart(true,e.finger,e.display,e.pointers,id,task==null||(Boolean)isHome.invoke(task),width,height,nativeAdmission)){
                    status("未接入：不是原生应用上滑，或非主屏竖屏单指");return;
                }
                sourcePackage=packageOf(task,id);if(!Wire.allowedPackage(sourcePackage)||Wire.PKG.equals(sourcePackage)||LauncherGuard.PACKAGE.equals(sourcePackage)){
                    status("未接入：任务缓存身份不匹配");return;
                }
                status("已接收原生上滑，等待移动识别");
                sourceTask=id;sourceGesture=g;inputOwner=owner;downTime=e.down;tracking=true;main.postDelayed(hideTimeout,12000);return;
            }
            if(!tracking||owner!=inputOwner||e.down!=downTime||e.pointers!=1||!e.finger){reset();return;}
            if(e.action==MotionEvent.ACTION_CANCEL){reset();return;}
            var b=wm.getCurrentWindowMetrics().getBounds();
            if(b.width()!=width||b.height()!=height){reset();return;}
            Object r=resource.invoke(owner,0),g=r==null?null:gesture.get(r);Object c=r==null?null:consumer.get(r);
            c=c==null?null:activeConsumer.invoke(c);Object swipe=c!=null&&interaction.getDeclaringClass().isInstance(c)?interaction.get(c):null;
            boolean same=g==sourceGesture&&(Integer)taskId.invoke(g)==sourceTask&&c!=null&&consumerGesture.getDeclaringClass().isInstance(c)&&consumerGesture.get(c)==g;
            boolean recognized=swipe!=null&&started.getBoolean(swipe);
            if(sourceSwipe!=null&&sourceSwipe!=swipe){reset();return;}if(recognized)sourceSwipe=swipe;
            if(e.action==MotionEvent.ACTION_MOVE){
                if(!same||!recognized){if(zone!=null)zone.setVisibility(View.GONE);return;}
                boolean next=inTarget(e.x,e.y);if(next&&!armed)zone.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                armed=next;paint(armed);zone.setVisibility(View.VISIBLE);status(armed?"已进入目标区，松手挂起":"提示区已显示，拖入右上角");
            }
        }catch(Exception failure){reset();status("输入适配异常："+failure.getClass().getSimpleName());log.accept("SWIPE_DROP_INPUT_DISABLED "+failure);configured=false;}
    }
    /** Runs before native UP only to capture stable controller/task identity, not to suppress it. */
    Runnable release(Object owner,Point e){
        if(e.action!=MotionEvent.ACTION_UP||!ready()||!tracking||owner!=boundOwner||owner!=inputOwner||e.down!=downTime)return null;
        try{
            Object r=resource.invoke(owner,0),g=r==null?null:gesture.get(r);
            Object c=r==null?null:consumer.get(r);c=c==null?null:activeConsumer.invoke(c);
            Object swipe=c!=null&&interaction.getDeclaringClass().isInstance(c)?interaction.get(c):null;
            if(!SwipeDropPolicy.release(true,g==sourceGesture&&(Integer)taskId.invoke(g)==sourceTask&&c!=null&&consumerGesture.getDeclaringClass().isInstance(c)&&consumerGesture.get(c)==g,
                swipe!=null&&swipe==sourceSwipe&&started.getBoolean(swipe),inTarget(e.x,e.y),e.time-e.down,e.pointers))return null;
            Object manager=managerGet.invoke(repository.get(owner),0),captured=manager==null?null:controller.get(manager);
            if(captured==null)return null;
            int id=sourceTask;String pkg=sourcePackage;long token=++epoch;
            return ()->{
                resetTracking();
                if(!ready()||token!=epoch)return;
                try{
                    Object current=managerGet.invoke(repository.get(owner),0);
                    if(current==null)return;
                    Object liveController=controller.get(current);
                    if(liveController!=null&&liveController!=captured)return;
                    // Native UP is already delivered. Finish only this captured controller to Home,
                    // then hand the exact task to SystemUI. Never replay MAIN or select a different task.
                    Runnable completed=()->main.post(()->{
                        if(token!=epoch||!ready())return;
                        Intent drop=new Intent(DROP).setPackage(BackBridge.PACKAGE).putExtra("taskId",id).putExtra("package",pkg).putExtra("time",SystemClock.elapsedRealtime());
                        status("桌面动画已完成，正在交接精确任务");
                        context.sendBroadcast(drop,null,android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
                    });
                    Object[] args=new Object[finish.getParameterCount()];args[0]=true;args[1]=completed;args[2]=false;
                    if(args.length==4){
                        Class<?> reason=finish.getParameterTypes()[3];
                        try{var constructor=reason.getDeclaredConstructor(String.class);constructor.setAccessible(true);args[3]=constructor.newInstance("NativeComplements swipe drop");}
                        catch(NoSuchMethodException absent){var constructor=reason.getDeclaredConstructor(String.class,Object[].class);constructor.setAccessible(true);args[3]=constructor.newInstance("NativeComplements swipe drop",new Object[0]);}
                    }
                    finish.invoke(captured,args);
                }catch(Exception failure){log.accept("SWIPE_DROP_FINISH_FAILED "+failure.getClass().getSimpleName());}
            };
        }catch(Exception failed){return null;}
    }
    private String packageOf(Object value,int id)throws Exception{
        // Pixel17/907 CachedTaskInfo stores TaskInfo (not RunningTaskInfo). Use its exact reviewed public identity API.
        if(value==null||(Integer)cachedTaskId.invoke(value)!=id)return null;
        return (String)cachedPackage.invoke(value);
    }
    private void status(String text){
        if(context==null||text.equals(lastStatus))return;lastStatus=text;
        context.sendBroadcast(new Intent(DROP+".STATUS").setPackage(BackBridge.PACKAGE).putExtra("status",text),null,
            android.app.BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
    }
    private boolean inTarget(float x,float y){
        if(zone==null||zone.getVisibility()!=View.VISIBLE)return false;int[] at=new int[2];zone.getLocationOnScreen(at);
        return x>=at[0]&&x<at[0]+zone.getWidth()&&y>=at[1]&&y<at[1]+zone.getHeight();
    }
    private void paint(boolean active){
        if(zone==null)return;int accent=0xff526596;android.util.TypedValue v=new android.util.TypedValue();
        if(context.getTheme().resolveAttribute(android.R.attr.colorAccent,v,true))accent=v.resourceId!=0?context.getColor(v.resourceId):v.data;
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(active?accent:0xe6f0f2fc);bg.setCornerRadius(dp(28));bg.setStroke(dp(active?2:1),active?Color.WHITE:accent);zone.setBackground(bg);
        LinearLayout card=(LinearLayout)zone;iconColor=active?Color.WHITE:accent;card.getChildAt(0).invalidate();
        label.setTextColor(active?Color.WHITE:accent);label.setText(active?"松手挂起小窗":"拖到此处挂起");zone.setScaleX(active?1.04f:1f);zone.setScaleY(active?1.04f:1f);
    }
    private int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private void resetTracking(){main.removeCallbacks(hideTimeout);tracking=false;armed=false;sourceTask=-1;sourcePackage=null;sourceGesture=null;sourceSwipe=null;inputOwner=null;if(zone!=null)zone.setVisibility(View.GONE);}
    void reset(){epoch++;resetTracking();}
    @Override public void close(){reset();closed=true;configured=false;if(zone!=null&&wm!=null)try{wm.removeViewImmediate(zone);}catch(RuntimeException ignored){}zone=null;label=null;context=null;boundOwner=null;lastStatus="";}
    static Method completion(Class<?> type)throws Exception{
        for(String name:new String[]{"finishController","finish"})for(Method m:type.getMethods()){
            Class<?>[] p=m.getParameterTypes();
            if(!m.getName().equals(name)||m.getReturnType()!=void.class||p.length<3||p.length>4
                ||p[0]!=boolean.class||p[1]!=Runnable.class||p[2]!=boolean.class)continue;
            if(p.length==4){
                if(p[3]==boolean.class)continue;
                try{p[3].getDeclaredConstructor(String.class);}catch(NoSuchMethodException absent){try{p[3].getDeclaredConstructor(String.class,Object[].class);}catch(NoSuchMethodException invalid){continue;}}
            }
            m.setAccessible(true);return m;
        }
        throw new NoSuchMethodException("Known recents completion ABI missing");
    }
    private static Field field(Class<?> c,String name)throws Exception{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Method method(Class<?> c,String name,Class<?>...args)throws Exception{
        Method m;try{m=c.getDeclaredMethod(name,args);}catch(NoSuchMethodException missing){m=c.getMethod(name,args);}
        m.setAccessible(true);return m;
    }
}
