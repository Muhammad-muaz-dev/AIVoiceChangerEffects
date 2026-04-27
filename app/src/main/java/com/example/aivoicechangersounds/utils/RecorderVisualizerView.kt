package com.example.aivoicechangersounds.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class RecorderVisualizerView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val bars = mutableListOf<Float>()
    private val barWidth = 6f
    private val barSpace = 2f
    private val paint = Paint().apply {
        color = Color.parseColor("#6A5ACD")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun addAmplitude(amplitude: Int) {
        val scaledHeight = (amplitude / 32767f) * height
        bars.add(scaledHeight)

        // ✅ Keep only bars that fit in the current width
        val maxBars = (width / (barWidth + barSpace)).toInt()
        if (bars.size > maxBars) {
            bars.removeAt(0) // drop oldest
        }

        invalidate()
    }

    fun reset() {
        bars.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val middleY = height / 2f

        // ✅ Start drawing from right edge and move left
        var x = width - barWidth
        for (i in bars.size - 1 downTo 0) {
            val barHeight = bars[i]
            val top = middleY - barHeight / 2
            val bottom = middleY + barHeight / 2
            canvas.drawRoundRect(x, top, x + barWidth, bottom, 8f, 8f, paint)
            x -= (barWidth + barSpace)
            if (x < 0) break
        }
    }
}