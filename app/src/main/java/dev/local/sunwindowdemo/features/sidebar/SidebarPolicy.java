package dev.local.sunwindowdemo;

import java.util.List;
import java.util.Set;

/** Pure policy: foreground must be explicitly allowlisted and the backend lease fresh. */
public final class SidebarPolicy {
    private SidebarPolicy() {}
    public static boolean visible(boolean enabled,boolean windowsEnabled,boolean ready,boolean interactive,
            boolean locked,boolean obscured,boolean keyboard,boolean landscape,boolean landscapeOnly,
            String foreground,Set<String> hosts,long ageMillis){
        return enabled&&windowsEnabled&&ready&&interactive&&!locked&&!obscured&&!keyboard
            &&(!landscapeOnly||landscape)&&ageMillis>=0&&ageMillis<=2500
            &&foreground!=null&&hosts.contains(foreground);
    }
    public static List<String> packages(List<String> values,int limit){
        if(values==null||values.size()>limit)throw new IllegalArgumentException("应用数量超出限制");
        java.util.LinkedHashSet<String> unique=new java.util.LinkedHashSet<>();
        for(String pkg:values){
            if(pkg==null||!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")||!unique.add(pkg))
                throw new IllegalArgumentException("应用包名无效或重复");
        }
        return List.copyOf(unique);
    }
    public static boolean contains(int[] rect,float x,float y){
        return rect!=null&&rect.length==4&&Float.isFinite(x)&&Float.isFinite(y)
            &&x>=rect[0]&&x<rect[2]&&y>=rect[1]&&y<rect[3];
    }
    /** Only the collapsed handle ignores horizontal cutout insets; the expanded panel retains its safe-area anchor. */
    public static int horizontal(int displayWidth,int width,int insetLeft,int insetRight,boolean right,boolean collapsed){
        if(displayWidth<=0||width<=0||width>displayWidth||insetLeft<0||insetRight<0)
            throw new IllegalArgumentException("bounds");
        int left=collapsed?0:Math.min(insetLeft,displayWidth-width);
        int edge=collapsed?displayWidth:Math.max(left+width,displayWidth-insetRight);
        return right?Math.max(left,edge-width):left;
    }
    public static int top(int availableTop,int availableBottom,int height,int percent){
        if(availableBottom<=availableTop||height<=0)throw new IllegalArgumentException("bounds");
        int free=Math.max(0,availableBottom-availableTop-height);
        return availableTop+Math.round(free*Math.max(0,Math.min(100,percent))/100f);
    }
}
