package app.valenced.xposed.manager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

internal data class VxTheme(
    val id: String, val name: String, val description: String,
    val background: Int, val surface: Int, val raised: Int,
    val border: Int, val accent: Int, val secondary: Int,
    val text: Int, val muted: Int,
)
internal object VxThemes {
    private fun c(hex: String) = Color.parseColor("#" + hex)
    private fun t(id: String, name: String, description: String, bg: String, surface: String,
        raised: String, border: String, accent: String, secondary: String, text: String, muted: String) =
        VxTheme(id,name,description,c(bg),c(surface),c(raised),c(border),c(accent),c(secondary),c(text),c(muted))
    val all = listOf(
        t("dark","Veyra Dark","Graphit · Lavendel · Mint","0D0E12","17191F","21232C","31333D","BEAEFF","71E6C9","F5F5FC","A0A3B1"),
        t("amoled","Veyra AMOLED","Pures Schwarz · OLED-Kontrast","000000","101114","1A1B20","292A31","D0BFFF","8EECD8","F7F7FA","A6A6B2"),
        t("glass","Veyra Glass","Tiefes Pflaume · sanfter Schimmer","100F1A","201E2D","2B293B","49435F","C8B7FF","A7DDF3","F7F3FF","B3ABC9"),
        t("neon","Veyra Neon","Mitternacht · elektrische Akzente","080B13","141B29","1C2737","35435B","B5A1FF","48E5E2","F2F8FF","A4B4C7"),
        t("frost","Veyra Frost","Kühles Blau · ruhige Flächen","0B111B","142030","1D2B3E","304359","9ACBFF","9DE9DD","EFF6FF","A7B8CF"),
        t("core","Veyra Core","Carbon · dezentes Mint","101414","1A2222","252F2F","3B4B4A","A1E2CC","B9B2FF","EEF7F4","A7BBB6"),
    )
    fun get(id: String?) = all.firstOrNull { it.id == id } ?: all.first()
    val accents = listOf("Lavendel" to c("BEAEFF"),"Mint" to c("71E6C9"),"Eisblau" to c("9ACBFF"),
        "Rosé" to c("F4ACD5"),"Amber" to c("FFD18A"),"Silber" to c("D8DCE9"))
    fun blend(a: Int,b: Int,f: Float): Int = Color.rgb(
        (Color.red(a)*(1-f)+Color.red(b)*f).toInt(),
        (Color.green(a)*(1-f)+Color.green(b)*f).toInt(),
        (Color.blue(a)*(1-f)+Color.blue(b)*f).toInt())
}

/** A small, real vector icon set for the native manager, independent of fonts. */
internal class VxIcon(context: Context, private val icon: String, private val tint: Int): View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint; strokeWidth = 1.75f; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width,height).toFloat()
        canvas.save()
        canvas.translate((width-size)/2,(height-size)/2)
        canvas.scale(size/24,size/24)
        fun line(a:Float,b:Float,c:Float,d:Float) = canvas.drawLine(a,b,c,d,p)
        fun path(vararg coords: Float) {
            val v=Path(); v.moveTo(coords[0],coords[1])
            for (i in 2 until coords.size step 2) v.lineTo(coords[i],coords[i+1])
            canvas.drawPath(v,p)
        }
        fun rect(l:Float,t:Float,r:Float,b:Float, radius:Float=2f) =
            canvas.drawRoundRect(l,t,r,b,radius,radius,p)
        when(icon) {
            "home" -> { path(3f,11f,12f,3f,21f,11f,19f,11f,19f,21f,14f,21f,14f,15f,10f,15f,10f,21f,5f,21f,5f,11f,3f,11f) }
            "module" -> { path(3f,7f,12f,3f,21f,7f,12f,11f,3f,7f); path(3f,12f,12f,16f,21f,12f); path(3f,17f,12f,21f,21f,17f) }
            "plugins" -> { rect(3f,3f,10f,10f); rect(14f,3f,21f,10f); rect(3f,14f,10f,21f); rect(14f,14f,21f,21f) }
            "diagnose" -> path(2f,12f,7f,12f,10f,5f,14f,19f,17f,12f,22f,12f)
            "info" -> { canvas.drawCircle(12f,12f,9f,p); line(12f,11f,12f,17f); canvas.drawPoint(12f,7f,p) }
            "theme" -> { canvas.drawCircle(12f,12f,8.5f,p); line(12f,3.5f,12f,20.5f); p.style=Paint.Style.FILL; canvas.drawArc(3.5f,3.5f,20.5f,20.5f,-90f,180f,true,p); p.style=Paint.Style.STROKE }
            "refresh" -> { canvas.drawArc(4f,4f,20f,20f,-35f,305f,false,p); path(20f,3f,20f,10f,13f,10f) }
            "source" -> { rect(3f,4f,21f,10f); rect(3f,14f,21f,20f); canvas.drawPoint(7f,7f,p); canvas.drawPoint(7f,17f,p); line(11f,7f,17f,7f); line(11f,17f,17f,17f) }
        }
        canvas.restore()
    }
}

/** Scaled miniature UI, rendered with the very same palette as the full app. */
internal class VxThemePreview(context: Context, private val theme: VxTheme,
    private val radius: Int, private val glow: Boolean): View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val scale = width/320f
        if (scale <= 0f) return
        val h = height/scale
        canvas.save(); canvas.scale(scale,scale)
        fun box(x:Float,y:Float,w:Float,h:Float,r:Float,color:Int) {
            p.color=color; p.style=Paint.Style.FILL
            canvas.drawRoundRect(x,y,x+w,y+h,r,r,p)
        }
        fun text(s:String,x:Float,y:Float,size:Float,color:Int,bold:Boolean=false) {
            p.color=color; p.textSize=size; p.typeface=android.graphics.Typeface.create("sans-serif",if(bold) 1 else 0)
            canvas.drawText(s,x,y,p)
        }
        box(0f,0f,320f,h,15f,theme.background)
        box(14f,14f,25f,25f,7f,theme.raised)
        p.color=theme.accent;p.strokeWidth=2.8f;p.strokeCap=Paint.Cap.ROUND
        canvas.drawLine(20f,20f,33f,33f,p);p.color=theme.secondary;canvas.drawLine(33f,20f,20f,33f,p)
        text("ValancedX",48f,25f,15f,theme.text,true)
        text("XPOSED / MANAGER",48f,37f,7.5f,theme.muted)
        val cardY=51f;val cardH=(h-96f).coerceAtLeast(40f)
        if(glow) box(10f,cardY-2,300f,cardH+4,radius/2f+2,VxThemes.blend(theme.background,theme.accent,.28f))
        box(12f,cardY,296f,cardH,radius/2f,theme.surface)
        box(24f,cardY+13,6f,6f,3f,theme.secondary)
        text("MODULE HEALTH",36f,cardY+19,8f,theme.muted,true)
        text("Hermes geladen",24f,cardY+39,16f,theme.text,true)
        if(cardH>65) box(24f,cardY+49,272f,20f,6f,theme.accent)
        val navY=h-32
        box(12f,navY,296f,24f,11f,theme.raised)
        box(130f,navY+3,60f,18f,8f,VxThemes.blend(theme.raised,theme.accent,.22f))
        for (i in 0..4) box(39f+i*60,navY+9,10f,6f,3f,if(i==2) theme.accent else theme.muted)
        canvas.restore()
    }
}
