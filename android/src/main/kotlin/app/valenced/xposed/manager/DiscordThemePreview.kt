package app.valenced.xposed.manager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import org.json.JSONArray

/** A miniature Discord layout, not a screenshot; uses actual resolved palette. */
internal class DiscordThemePreview(context:Context, private val colors:IntArray,
    private val animated:Boolean=false):View(context) {
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private var visible=false
    override fun onVisibilityAggregated(isVisible:Boolean){super.onVisibilityAggregated(isVisible);visible=isVisible;if(animated&&isVisible)invalidate()}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        val w=width.toFloat();if(w<=0)return
        val scale=w/320f;val h=height/scale
        canvas.save();canvas.scale(scale,scale)
        val bg=colors[0];val side=colors[1];val card=colors[2];val accent=colors[3];val text=colors[4]
        val header=colors[6];val input=colors[7];val muted=colors[8];val border=colors[9];val selected=colors[10]
        fun rect(x:Float,y:Float,r:Float,b:Float,color:Int,radius:Float=0f){p.shader=null;p.color=color;canvas.drawRoundRect(x,y,r,b,radius,radius,p)}
        fun txt(s:String,x:Float,y:Float,size:Float,c:Int,bold:Boolean=false){p.shader=null;p.color=c;p.textSize=size;p.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL);canvas.drawText(s,x,y,p)}
        rect(0f,0f,320f,h,bg,12f)
        if(animated){
            val phase=(android.os.SystemClock.uptimeMillis()%10000L)/10000f
            val shift=(kotlin.math.sin(phase*6.2831853).toFloat()+1f)*.5f
            p.shader=LinearGradient(shift*160f,0f,320f-shift*100f,h,intArrayOf(bg,VxThemes.blend(bg,accent,.20f),side),null,Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f,0f,320f,h,12f,12f,p);p.shader=null
        }
        rect(0f,0f,78f,h,side,10f)
        rect(0f,0f,25f,h,card,8f)
        for(i in 0..3)rect(6f,12f+i*25f,19f,25f+i*25f,if(i==1)accent else VxThemes.blend(card,text,.15f),5f)
        txt("VEYRA",31f,19f,8f,text,true)
        txt("# info",32f,38f,8f,text)
        rect(29f,44f,74f,61f,selected,4f)
        txt("# lounge",32f,55f,8f,text,true)
        txt("# media",32f,74f,8f,text)
        rect(79f,0f,320f,28f,header,6f)
        rect(79f,27f,320f,28f,border)
        txt("# lounge",90f,18f,11f,text,true)
        rect(91f,39f,111f,59f,accent,10f)
        txt("Veyra",119f,46f,11f,accent,true)
        txt("Heute, 21:06",163f,46f,7f,muted)
        txt("Ein eigener Look für Discord.",119f,59f,9.5f,text)
        rect(91f,72f,111f,92f,VxThemes.blend(side,accent,.65f),10f)
        txt("Community",119f,80f,11f,text,true)
        txt("Schön, dass du da bist.",119f,93f,9.5f,text)
        if(h>132f){rect(119f,101f,221f,117f,card,5f);txt("  Veyra Studio",123f,112f,8f,accent,true)}
        rect(87f,h-25f,312f,h-5f,border,8f)
        rect(88f,h-24f,311f,h-6f,input,7f)
        txt("+    Nachricht schreiben …",94f,h-12f,8f,muted)
        canvas.restore()
        if(animated&&visible&&isShown)postInvalidateDelayed(50)
    }
    companion object{
        fun palette(values:JSONArray?,fallback:VxTheme):IntArray{
            fun read(i:Int,default:Int):Int {
                val v=values?.optString(i).orEmpty()
                return runCatching{
                    require(v.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")))
                    val rgb=Color.parseColor(v.take(7))
                    if(v.length==9) VxThemes.blend(default,rgb,v.takeLast(2).toInt(16)/255f) else rgb
                }.getOrDefault(default)
            }
            val bg=read(0,fallback.background)
            val side=read(1,fallback.surface)
            val card=read(2,fallback.raised)
            val accent=read(3,fallback.accent)
            val text=read(4,fallback.text)
            return intArrayOf(bg,side,card,accent,text,read(5,accent),
                read(6,side),read(7,card),read(8,VxThemes.blend(bg,text,.65f)),
                read(9,VxThemes.blend(side,text,.18f)),read(10,VxThemes.blend(side,accent,.18f)))
        }
    }
}
