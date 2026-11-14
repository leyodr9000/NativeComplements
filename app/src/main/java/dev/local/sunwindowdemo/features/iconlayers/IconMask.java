package dev.local.sunwindowdemo.features.iconlayers;

import java.util.Arrays;

/** Android-free alpha-mask preparation. Never mutate a published source layer. */
final class IconMask {
    private IconMask() {}
    static int[] prepare(int[] source, int width, int height) {
        if (width <= 0 || height <= 0 || source.length != (long) width * height)
            throw new IllegalArgumentException("image bounds");
        int left=width,top=height,right=0,bottom=0,visible=0;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++)if(alpha(source[y*width+x])>24){
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);visible++;
        }
        if(visible<source.length*.002f)return null;
        int w=right-left,h=bottom-top,area=w*h;
        // Transparent adaptive padding must not hide an almost-solid square backdrop.
        // Thin rectangular glyphs, circles and sparse subjects retain their alpha shape.
        // Small filled glyphs (for example an uploaded square) are meaningful subjects, not full icon backdrops.
        boolean field=visible>area*.85f && (visible>source.length*.85f
            || visible>=source.length*.25f && Math.min(w,h)>=Math.max(w,h)*.85f);
        if(!field)return source.clone();
        int[] red=new int[2*w+2*h],green=new int[red.length],blue=new int[red.length];int count=0;
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if(x==left||x==right-1||y==top||y==bottom-1){
            int c=source[y*width+x];if(alpha(c)<128)continue;
            red[count]=(c>>16)&255;green[count]=(c>>8)&255;blue[count]=c&255;count++;
        }
        if(count==0)return null;
        Arrays.sort(red,0,count);Arrays.sort(green,0,count);Arrays.sort(blue,0,count);
        int color=0xff000000|(red[count/2]<<16)|(green[count/2]<<8)|blue[count/2];
        int matched=0,total=0;
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if(x==left||x==right-1||y==top||y==bottom-1){
            total++;if(close(source[y*width+x],color))matched++;
        }
        if(matched<total*.75f)return null;
        boolean[] seen=new boolean[source.length];int[] queue=new int[area];int head=0,tail=0;
        for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if(x==left||x==right-1||y==top||y==bottom-1){
            int at=y*width+x;if(!seen[at]&&close(source[at],color)){seen[at]=true;queue[tail++]=at;}
        }
        while(head<tail){int at=queue[head++],x=at%width,y=at/width;
            for(int direction=0;direction<4;direction++){
                int next=switch(direction){case 0->x>left?at-1:-1;case 1->x<right-1?at+1:-1;
                    case 2->y>top?at-width:-1;default->y<bottom-1?at+width:-1;};
                if(next>=0&&!seen[next]&&close(source[next],color)){seen[next]=true;queue[tail++]=next;}
            }
        }
        int[] result=source.clone();int remaining=0;
        for(int i=0;i<result.length;i++){
            if(seen[i])result[i]=0;
            if(alpha(result[i])>24)remaining++;
        }
        // Empty/solid outputs are not usable identity glyphs: let the caller retain native artwork.
        if(remaining<area*.015f||remaining>area*.85f)return null;
        return result;
    }
    private static int alpha(int color){return color>>>24;}
    private static boolean close(int a,int b){
        if(alpha(a)<24)return true;
        int r=((a>>16)&255)-((b>>16)&255),g=((a>>8)&255)-((b>>8)&255),v=(a&255)-(b&255);
        return r*r+g*g+v*v<42*42;
    }
}
