package dev.local.sunwindowdemo.features.iconlayers;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class IconMaskTest {
    private static final int BLUE=0xff2299ee,RED=0xffdd2233,WHITE=0xffffffff;
    private static void rect(int[] p,int side,int l,int t,int r,int b,int c){
        for(int y=t;y<b;y++)for(int x=l;x<r;x++)p[y*side+x]=c;
    }
    @Test public void paddedSquareBackdropIsRemovedAndSourceAndAlignmentArePreserved(){
        int[] p=new int[144*144];rect(p,144,24,24,120,120,BLUE);rect(p,144,52,40,88,104,WHITE);
        int[] copy=p.clone(),result=IconMask.prepare(p,144,144);
        assertNotNull(result);assertArrayEquals(copy,p);
        assertEquals(0,result[24*144+24]);assertEquals(WHITE,result[40*144+52]);
        assertEquals(WHITE,result[103*144+87]);assertEquals(0,result[104*144+87]);
    }
    @Test public void roundedPaddedFieldIgnoresTransparentCornerRgbAndKeepsDisconnectedDetails(){
        int s=100;int[] p=new int[s*s];rect(p,s,15,15,85,85,BLUE);
        for(int y=15;y<85;y++)for(int x=15;x<85;x++){
            boolean corner=(x<21||x>=79)&&(y<21||y>=79);
            if(corner)p[y*s+x]=0x00ff00ff;
        }
        rect(p,s,35,30,65,65,WHITE);rect(p,s,25,70,30,75,RED);
        int[] result=IconMask.prepare(p,s,s);assertNotNull(result);
        assertEquals(0,result[15*s+40]);assertEquals(WHITE,result[45*s+45]);assertEquals(RED,result[72*s+27]);
    }
    @Test public void alreadySeparatedThinGlyphAndCircleRetainExactAlpha(){
        int[] thin=new int[10000];rect(thin,100,40,10,60,90,WHITE);
        assertArrayEquals(thin,IconMask.prepare(thin,100,100));
        int[] circle=new int[10000];
        for(int y=10;y<90;y++)for(int x=10;x<90;x++)if((x-50)*(x-50)+(y-50)*(y-50)<1600)circle[y*100+x]=WHITE;
        assertArrayEquals(circle,IconMask.prepare(circle,100,100));
    }
    @Test public void opaqueAndPaddedSolidFieldsAreRejectedInsteadOfBecomingBlocks(){
        int[] opaque=new int[64*64];Arrays.fill(opaque,BLUE);assertNull(IconMask.prepare(opaque,64,64));
        int[] padded=new int[144*144];rect(padded,144,24,24,120,120,BLUE);
        assertNull(IconMask.prepare(padded,144,144));
    }
    @Test public void complexBackdropWithNoReliableEdgeFallsBackInsteadOfInventingGlyph(){
        int[] p=new int[10000];
        for(int y=10;y<90;y++)for(int x=10;x<90;x++)p[y*100+x]=(x+y)%2==0?RED:BLUE;
        assertNull(IconMask.prepare(p,100,100));
    }
    @Test public void emptyTinyAndInvalidInputsAreRejected(){
        assertNull(IconMask.prepare(new int[10000],100,100));
        int[] tiny=new int[10000];tiny[50]=WHITE;assertNull(IconMask.prepare(tiny,100,100));
        assertThrows(IllegalArgumentException.class,()->IconMask.prepare(new int[3],2,2));
    }
}
