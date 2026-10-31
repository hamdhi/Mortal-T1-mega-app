package lk.mortal.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.TextView

object Ui {
    val bg = Color.rgb(11, 16, 25)
    val panel = Color.rgb(21, 33, 48)
    val accent = Color.rgb(106, 228, 204)
    val text = Color.rgb(231, 239, 247)
    val muted = Color.rgb(151, 171, 193)
    fun dp(c: Context, n: Int) = (n * c.resources.displayMetrics.density).toInt()
    fun background(color: Int, stroke: Int = color): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = 14f; setStroke(3, stroke) }
    fun label(c: Context, value: String, size: Float = 18f, color: Int = text) = TextView(c).apply { this.text = value; textSize = size; setTextColor(color); setPadding(0, 6, 0, 6) }
    fun focus(view: View) {
        view.isFocusable = true
        view.background = background(panel)
        view.setOnFocusChangeListener { v, focused -> v.background = background(if (focused) Color.rgb(32, 65, 76) else panel, if (focused) accent else panel) }
    }
    fun button(c: Context, value: String, action: () -> Unit) = Button(c).apply {
        text = value; textSize = 14f; isAllCaps = false; setTextColor(Ui.text); typeface = Typeface.DEFAULT_BOLD
        minimumHeight = dp(c, 48); setPadding(dp(c, 14), dp(c, 6), dp(c, 14), dp(c, 6)); focus(this)
        setOnClickListener { action() }
    }
}
