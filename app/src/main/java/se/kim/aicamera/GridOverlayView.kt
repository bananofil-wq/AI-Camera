package se.kim.aicamera
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
class GridOverlayView @JvmOverloads constructor(c: Context, a: AttributeSet?=null): View(c,a){
 private val p=Paint().apply{color=Color.argb(150,255,255,255);strokeWidth=1f}
 override fun onDraw(c:Canvas){super.onDraw(c); val w=width/3f; val h=height/3f; c.drawLine(w,0f,w,height.toFloat(),p);c.drawLine(w*2,0f,w*2,height.toFloat(),p);c.drawLine(0f,h,width.toFloat(),h,p);c.drawLine(0f,h*2,width.toFloat(),h*2,p)}
}