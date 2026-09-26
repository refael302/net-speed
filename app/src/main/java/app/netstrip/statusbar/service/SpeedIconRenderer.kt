package app.netstrip.statusbar.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import app.netstrip.statusbar.data.IconMode

class SpeedIconRenderer {
    fun render(down: String, up: String, mode: IconMode): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }
        when (mode) {
            IconMode.DOWNLOAD -> drawEntry(canvas, paint, down, pointingUp = false, size, size * 0.50f, 72f)
            IconMode.UPLOAD -> drawEntry(canvas, paint, up, pointingUp = true, size, size * 0.50f, 72f)
            IconMode.BOTH -> {
                drawEntry(canvas, paint, down, pointingUp = false, size, size * 0.30f, 48f)
                drawEntry(canvas, paint, up, pointingUp = true, size, size * 0.72f, 48f)
            }
        }
        return bitmap
    }

    private fun drawEntry(
        canvas: Canvas,
        paint: Paint,
        text: String,
        pointingUp: Boolean,
        size: Int,
        centerY: Float,
        startSize: Float,
    ) {
        val maxWidth = size * 0.90f
        var textSize = startSize
        paint.textSize = textSize
        var textWidth = paint.measureText(text)
        var marker = textSize * 0.42f
        while (marker + GAP + textWidth > maxWidth && textSize > 16f) {
            textSize -= 2f
            paint.textSize = textSize
            textWidth = paint.measureText(text)
            marker = textSize * 0.42f
        }
        val total = marker + GAP + textWidth
        val left = (size - total) / 2f
        drawTriangle(canvas, paint, left + marker / 2f, centerY, marker, pointingUp)
        val baseline = centerY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, left + marker + GAP, baseline, paint)
    }

    private fun drawTriangle(canvas: Canvas, paint: Paint, cx: Float, cy: Float, width: Float, pointingUp: Boolean) {
        val path = Path()
        val half = width / 2f
        val height = width * 0.72f
        if (pointingUp) {
            path.moveTo(cx, cy - height / 2f)
            path.lineTo(cx - half, cy + height / 2f)
            path.lineTo(cx + half, cy + height / 2f)
        } else {
            path.moveTo(cx, cy + height / 2f)
            path.lineTo(cx - half, cy - height / 2f)
            path.lineTo(cx + half, cy - height / 2f)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private companion object {
        const val GAP = 6f
    }
}
