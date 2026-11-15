package dev.local.sunwindowdemo;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Uses the existing UID-checked corner settings snapshot; no new provider or scope. */
public final class SidebarSettings {
    private SidebarSettings() {}
    public record State(boolean enabled,boolean landscapeOnly,boolean right,int opacity,int position,
                        int handleHeight,int iconSize,Set<String> hosts,List<String> apps,int edgeGap) {
        public State(boolean enabled,boolean landscapeOnly,boolean right,int opacity,int position,int handleHeight,int iconSize,Set<String> hosts,List<String> apps){
            this(enabled,landscapeOnly,right,opacity,position,handleHeight,iconSize,hosts,apps,6);
        }
    }
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("corner",0);}
    public static State read(Context c){return read(prefs(c));}
    static State read(SharedPreferences p){
        Set<String> hosts=new LinkedHashSet<>();
        for(String pkg:p.getStringSet("sidebarHosts",Set.of()))if(Wire.allowedPackage(pkg)&&hosts.size()<100)hosts.add(pkg);
        List<String> apps=new ArrayList<>();
        for(String pkg:p.getString("sidebarApps","").split(","))
            if(Wire.allowedPackage(pkg)&&!apps.contains(pkg)&&apps.size()<12)apps.add(pkg);
        return new State(p.getBoolean("sidebarEnabled",false),p.getBoolean("sidebarLandscapeOnly",true),
            p.getBoolean("sidebarRight",true),number(p,"sidebarOpacity",60,10,100),number(p,"sidebarPosition",50,0,100),
            number(p,"sidebarHandleHeight",72,40,140),number(p,"sidebarIconSize",40,32,56),Set.copyOf(hosts),List.copyOf(apps),number(p,"sidebarEdgeGap",6,0,32));
    }
    private static int number(SharedPreferences p,String key,int def,int min,int max){return Math.max(min,Math.min(max,p.getInt(key,def)));}
    public static void flag(Context c,String key,boolean value){
        if(!Set.of("sidebarEnabled","sidebarLandscapeOnly","sidebarRight").contains(key))throw new IllegalArgumentException("key");
        save(c,prefs(c).edit().putBoolean(key,value));
    }
    public static void number(Context c,String key,int value){
        int min,max;
        switch(key){
            case "sidebarEdgeGap"->{min=0;max=32;}case "sidebarOpacity"->{min=10;max=100;}case "sidebarPosition"->{min=0;max=100;}
            case "sidebarHandleHeight"->{min=40;max=140;}case "sidebarIconSize"->{min=32;max=56;}
            default->throw new IllegalArgumentException("key");
        }
        if(value<min||value>max)throw new IllegalArgumentException("range");
        save(c,prefs(c).edit().putInt(key,value));
    }
    public static void hosts(Context c,List<String> packages){
        save(c,prefs(c).edit().putStringSet("sidebarHosts",new LinkedHashSet<>(SidebarPolicy.packages(packages,100))));
    }
    public static void apps(Context c,List<String> packages){
        save(c,prefs(c).edit().putString("sidebarApps",String.join(",",SidebarPolicy.packages(packages,12))));
    }
    private static void save(Context c,SharedPreferences.Editor editor){
        SharedPreferences p=prefs(c);Map<String,?> before=p.getAll();
        if(!editor.commit()){
            // Android may have applied the new map in memory even when the disk write failed.
            var rollback=p.edit();
            for(String key:List.of("sidebarEnabled","sidebarLandscapeOnly","sidebarRight","sidebarOpacity","sidebarPosition","sidebarHandleHeight","sidebarIconSize","sidebarHosts","sidebarApps","sidebarEdgeGap")){
                Object old=before.get(key);
                if(old==null)rollback.remove(key);
                else if(old instanceof Boolean flag)rollback.putBoolean(key,flag);
                else if(old instanceof Integer number)rollback.putInt(key,number);
                else if(old instanceof String text)rollback.putString(key,text);
                else if(old instanceof Set<?> values){Set<String> strings=new HashSet<>();for(Object item:values)strings.add((String)item);rollback.putStringSet(key,strings);}
            }
            rollback.apply();throw new IllegalStateException("侧边栏设置保存失败");
        }
        ConfigProvider.changed(c);
    }
}
