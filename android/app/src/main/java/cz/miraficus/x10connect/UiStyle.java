package cz.miraficus.x10connect;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.widget.Button;

/** Shared native-view styling for the simple interface. No bitmap icon assets. */
final class UiStyle {
    static final int BG=0xff0d141c,CARD=0xff18232f,TEXT=0xffedf3fa,MUTED=0xff96a9bb,ACCENT=0xff61e1d0,BORDER=0xff2b3c4b;
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context c,int color,int radius,boolean border){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));if(border)d.setStroke(dp(c,1),BORDER);return d;
    }
    static Drawable background(Context c,int color,int radius,boolean border){
        return new RippleDrawable(ColorStateList.valueOf(0x3061e1d0),shape(c,color,radius,border),shape(c,0xffffffff,radius,false));
    }
    static Button button(Context c,String label,String icon,boolean primary,boolean stacked,Runnable action){
        Button b=new Button(c);b.setAllCaps(false);b.setText(label);b.setTextSize(14);b.setTextColor(primary?BG:TEXT);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(c,48));b.setMinimumHeight(dp(c,48));
        b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));b.setGravity(Gravity.CENTER);
        b.setBackgroundTintList(null);b.setBackground(background(c,primary?ACCENT:CARD,22,!primary));
        if(icon!=null){Drawable d=icon(c,icon,primary?BG:TEXT,stacked?28:22);
            if(stacked)b.setCompoundDrawables(null,d,null,null);else b.setCompoundDrawablesRelative(d,null,null,null);
            b.setCompoundDrawablePadding(dp(c,stacked?10:8));}
        b.setOnClickListener(v->action.run());return b;
    }
    static Button iconButton(Context c,String description,String icon,Runnable action){
        Button b=button(c,"",null,false,false,action);b.setContentDescription(description);b.setTooltipText(description);
        b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));b.setCompoundDrawables(icon(c,icon,TEXT,24),null,null,null);return b;
    }
    static Drawable icon(Context c,String name,int color,int size){
        Drawable d=new Glyph(name,color);int px=dp(c,size);d.setBounds(0,0,px,px);return d;
    }
    private static final class Glyph extends Drawable {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final String name;private final int color;
        Glyph(String name,int color){this.name=name;this.color=color;}
        private void line(Canvas c,float... xy){Path path=new Path();path.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)path.lineTo(xy[i],xy[i+1]);c.drawPath(path,p);}
        @Override public void draw(Canvas canvas){
            int save=canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);
            p.setColor(color);p.setStrokeWidth(1.7f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            switch(name){
                case "camera":case "photo":
                    canvas.drawRoundRect(2,6,22,21,3,3,p);line(canvas,7,6,9,3,15,3,17,6);canvas.drawCircle(12,13,4,p);break;
                case "gallery":
                    canvas.drawRoundRect(3,3,21,21,3,3,p);canvas.drawCircle(8,8,1.5f,p);line(canvas,4,18,10,12,14,16,17,13,21,17);break;
                case "video":line(canvas,5,3,21,12,5,21,5,3);break;
                case "record":canvas.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,12,5,p);break;
                case "stop":canvas.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);canvas.drawRoundRect(8,8,16,16,1,1,p);break;
                case "connect":line(canvas,8,2,8,7);line(canvas,16,2,16,7);canvas.drawRoundRect(5,7,19,16,3,3,p);line(canvas,12,16,12,22);break;
                case "wifi":canvas.drawArc(1,3,23,25,225,90,false,p);canvas.drawArc(5,8,19,22,225,90,false,p);canvas.drawArc(9,13,15,19,225,90,false,p);p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,20,1,p);break;
                case "refresh":canvas.drawArc(4,4,20,20,40,285,false,p);line(canvas,15,2,20,5,16,9);break;
                case "download":line(canvas,12,3,12,16,7,11);line(canvas,12,16,17,11);line(canvas,4,17,4,21,20,21,20,17);break;
                case "check":line(canvas,5,12,10,17,20,6);break;
                case "clear":canvas.drawCircle(12,12,9,p);line(canvas,8,8,16,16);line(canvas,16,8,8,16);break;
                case "selected":case "unselected":
                    p.setColor(name.equals("selected")?ACCENT:0xd018232f);p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,12,10,p);
                    p.setStyle(Paint.Style.STROKE);p.setColor(name.equals("selected")?BG:TEXT);if(name.equals("selected"))line(canvas,7,12,10.5f,15.5f,17,8.5f);else canvas.drawCircle(12,12,9,p);break;
                case "dot":p.setStyle(Paint.Style.FILL);canvas.drawCircle(12,12,6,p);break;
                case "chevron":line(canvas,6,9,12,15,18,9);break;
                case "battery":canvas.drawRoundRect(2,6,20,18,2,2,p);line(canvas,22,10,22,14);break;
                case "expand":line(canvas,3,9,3,3,9,3);line(canvas,15,3,21,3,21,9);line(canvas,21,15,21,21,15,21);line(canvas,9,21,3,21,3,15);break;
                case "back":line(canvas,14,5,7,12,14,19);break;
                case "settings":
                    canvas.drawCircle(12,12,7,p);canvas.drawCircle(12,12,2.5f,p);
                    for(int i=0;i<8;i++){double a=i*Math.PI/4;line(canvas,12+(float)Math.cos(a)*7,12+(float)Math.sin(a)*7,12+(float)Math.cos(a)*10,12+(float)Math.sin(a)*10);}break;
                default:canvas.drawCircle(12,12,8,p);
            }
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int a){p.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
