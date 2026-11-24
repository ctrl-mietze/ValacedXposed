package app.valenced.xposed.manager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/** Locally rendered soft gradients. The same artwork is used for the preview
 * and the backing PNG in Discord; animation never covers foreground controls. */
internal object VxMotionRenderer {
    fun draw(canvas:Canvas,width:Float,height:Float,preset:VxMotionPresets.Preset,phase:Double=0.0) {
        if(width<=0||height<=0)return
        val angle=phase*2*Math.PI
        val dx=(sin(angle)*.13).toFloat()
        val dy=(cos(angle)*.09).toFloat()
        val base=preset.baseColor()
        val first=preset.firstColor()
        val second=preset.secondColor()
        val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        val scale=when(preset.motion) {
            "pulse"->(1.0+.16*sin(angle)).toFloat()
            else->1f
        }
        paint.shader=LinearGradient(0f,0f,width,height,
            intArrayOf(base,VxThemes.blend(base,second,.17f),base),null,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,width,height,paint)
        canvas.save()
        canvas.scale(width,height)
        fun glow(x:Float,y:Float,rx:Float,ry:Float,color:Int,strength:Float){
            canvas.save()
            canvas.translate(x,y)
            canvas.scale(rx,ry)
            val opacity=(strength*255).toInt().coerceIn(0,255)
            val center=Color.argb(opacity,Color.red(color),Color.green(color),Color.blue(color))
            val middle=Color.argb(opacity/3,Color.red(color),Color.green(color),Color.blue(color))
            val transparent=Color.argb(0,Color.red(color),Color.green(color),Color.blue(color))
            paint.shader=RadialGradient(0f,0f,1f,intArrayOf(center,middle,transparent),floatArrayOf(0f,.48f,1f),Shader.TileMode.CLAMP)
            canvas.drawRect(-1f,-1f,1f,1f,paint)
            canvas.restore()
        }
        when(preset.motion){
            "sweep"->{
                glow(.28f+dx,.22f+dy,.96f,.49f,first,.66f)
                glow(.82f-dx,.79f-dy,.79f,.39f,second,.56f)
            }
            "orbit"->{
                glow(.25f+dx,.26f+dy,.82f,.62f,first,.63f)
                glow(.76f-dx,.75f-dy,.76f,.52f,second,.60f)
            }
            "pulse"->{
                glow(.25f,.28f,.77f*scale,.69f*scale,first,.66f)
                glow(.81f,.70f,.86f/scale,.53f/scale,second,.56f)
            }
            else->{
                glow(.22f+dx,.31f+dy,.85f,.60f,first,.61f)
                glow(.79f-dx,.73f-dy,.88f,.60f,second,.57f)
            }
        }
        canvas.restore()
        paint.shader=null
    }
}
