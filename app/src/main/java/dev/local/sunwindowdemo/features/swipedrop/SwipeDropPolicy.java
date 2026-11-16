package dev.local.sunwindowdemo;

/** Independent, Android-free admission and target policy for a native Quickstep swipe. */
public final class SwipeDropPolicy {
    private SwipeDropPolicy() {}
    public static boolean canStart(boolean enabled,boolean backend,boolean unlocked,boolean touch,int display,int pointers,
                                   int task,boolean home,int width,int height,float y){
        return enabled&&backend&&unlocked&&touch&&display==0&&pointers==1&&task>=0&&!home
            &&width>0&&height>width&&Float.isFinite(y)&&y>=height-Math.min(height*.12f,160);
    }
    public static boolean nativeStart(boolean ready,boolean touch,int display,int pointers,int task,boolean home,int width,int height,boolean admitted){
        return ready&&touch&&display==0&&pointers==1&&task>=0&&!home&&width>0&&height>width&&admitted;
    }
    public static boolean inside(float x,float y,int width,int height,int density){
        if(!Float.isFinite(x)||!Float.isFinite(y)||width<=0||height<=0||density<=0)return false;
        float scale=density/160f,margin=16*scale,w=Math.min(width*.4f,144*scale),h=104*scale;
        return x>=width-margin-w&&x<=width-margin&&y>=margin&&y<=margin+h;
    }
    public static boolean release(boolean started,boolean sameTask,boolean tracking,boolean inZone,long age,int pointers){
        return started&&sameTask&&tracking&&inZone&&age>=80&&age<=12000&&pointers==1;
    }
}
