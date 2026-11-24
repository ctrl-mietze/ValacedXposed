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
        fun rect(x:Float,y:Float,r:Float,b:Float,color:Int,radius:Float=0f){p.shader=null;p.color=color;canvas.drawRoundRect(x,y,r,b,radius,radius,p)}
        fun txt(s:String,x:Float,y:Float,size:Float,c:Int,bold:Boolean=false){p.shader=null;p.color=c;p.textSize=size;p.typeface=Typeface.create("sans-serif",if(bold)Typeface.BOLD else Typeface.NORMAL);canvas.drawText(s,x,y,p)}
        rect(0f,0f,320f,h,bg,12f)
        if(animated){
            val phase=(android.os.SystemClock.uptimeMillis()%10000L)/10000f
            val shift=(kotlin.math.sin(phase*6.2831853).toFloat()+1f)*.5f
            p.shader=LinearGradient(shift*160f,0f,320f-shift*100f,h,intArrayOf(bg,accent,side),null,Shader.TileMode.CLAMP)
            canvas.drawRoundRect(0f,0f,320f,h,12f,12f,p);p.shader=null
        }
        rect(0f,0f,78f,h,side,10f)
        rect(0f,0f,25f,h,card,8f)
        for(i in 0..3)rect(6f,12f+i*25f,19f,25f+i*25f,if(i==1)accent else VxThemes.blend(card,text,.15f),5f)
        txt("VEYRA",31f,19f,8f,text,true)
        txt("# info",32f,38f,8f,text)
        rect(29f,44f,74f,61f,VxThemes.blend(side,accent,.22f),4f)
        txt("# lounge",32f,55f,8f,text,true)
        txt("# media",32f,74f,8f,text)
        rect(79f,0f,320f,28f,card,6f)
        txt("# lounge",90f,18f,11f,text,true)
        val muted=VxThemes.blend(bg,text,.65f)
        rect(91f,39f,111f,59f,accent,10f)
        txt("Veyra",119f,46f,11f,accent,true)
        txt("Heute, 21:06",163f,46f,7f,muted)
        txt("Ein eigener Look für Discord.",119f,59f,9.5f,text)
        rect(91f,72f,111f,92f,VxThemes.blend(side,accent,.65f),10f)
        txt("Community",119f,80f,11f,text,true)
        txt("Schön, dass du da bist.",119f,93f,9.5f,text)
        if(h>132f){rect(119f,101f,221f,117f,card,5f);txt("  Veyra Studio",123f,112f,8f,accent,true)}
        rect(87f,h-24f,312f,h-6f,card,7f)
        txt("+    Nachricht schreiben …",94f,h-12f,8f,muted)
        canvas.restore()
        if(animated&&visible&&isShown)postInvalidateDelayed(50)
    }
    companion object{
        fun palette(values:JSONArray?,fallback:VxTheme):IntArray{
            val defaults=intArrayOf(fallback.background,fallback.surface,fallback.raised,fallback.accent,fallback.text,fallback.accent)
            return IntArray(6){i->
                val v=values?.optString(i).orEmpty()
                runCatching{require(v.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")));Color.parseColor(v.take(7))}.getOrDefault(defaults[i])
            }
        }
    }
}
