package app.netstrip.statusbar.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
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
            IconMode.DOWNLOAD -> drawEntry(canvas, paint, down, pointingUp = false, size, size * 0.50f, size * 0.86f)
            IconMode.UPLOAD -> drawEntry(canvas, paint, up, pointingUp = true, size, size * 0.50f, size * 0.86f)
            IconMode.BOTH -> drawPair(canvas, paint, down, up, size)
        }
        return bitmap
    }

    /**
     * Top row is download, bottom row is upload.
     * Digit height fills each half of the slot. Each label is then scaled
     * sideways so its ink reaches the left and right edges.
     */
    private fun drawPair(canvas: Canvas, paint: Paint, down: String, up: String, size: Int) {
        val rowH = (size - ROW_GAP) / 2f
        paint.textScaleX = 1f
        paint.textSize = 100f
        val probeH = textBounds(paint, "8").height().toFloat().coerceAtLeast(1f)
        paint.textSize = rowH * 100f / probeH
        val ref = textBounds(paint, "8")
        drawWideRow(canvas, paint, down, 0f, ref, size)
        drawWideRow(canvas, paint, up, rowH + ROW_GAP, ref, size)
        paint.textScaleX = 1f
    }

    private fun drawWideRow(canvas: Canvas, paint: Paint, text: String, top: Float, box: Rect, size: Int) {
        paint.textScaleX = 1f
        val bounds = textBounds(paint, text)
        val inkW = bounds.width().toFloat().coerceAtLeast(1f)
        val scaleX = size.toFloat() / inkW
        paint.textScaleX = scaleX
        canvas.drawText(text, -bounds.left * scaleX, top - box.top, paint)
    }

    private fun textBounds(paint: Paint, text: String): Rect {
        val box = Rect()
        paint.getTextBounds(text, 0, text.length, box)
        return box
    }

    private fun drawEntry(
        canvas: Canvas,
        paint: Paint,
        text: String,
        pointingUp: Boolean,
        size: Int,
        centerY: Float,
        rowHeight: Float,
    ) {
        val maxWidth = size * 0.98f
        var textSize = rowHeight
        paint.textSize = textSize
        var textHeight = paint.descent() - paint.ascent()
        if (textHeight > rowHeight && textHeight > 0f) {
            textSize *= rowHeight / textHeight
            paint.textSize = textSize
        }
        var textWidth = paint.measureText(text)
        var marker = textSize * ARROW
        while (marker + GAP + textWidth > maxWidth && textSize > 18f) {
            textSize -= 1f
            paint.textSize = textSize
            textWidth = paint.measureText(text)
            marker = textSize * ARROW
        }
        val total = marker + GAP + textWidth
        val left = ((size - total) / 2f).coerceAtLeast(0f)
        drawArrow(canvas, paint, left + marker / 2f, centerY, marker, pointingUp)
        val baseline = centerY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, left + marker + GAP, baseline, paint)
    }

    private fun drawArrow(canvas: Canvas, paint: Paint, cx: Float, cy: Float, width: Float, pointingUp: Boolean) {
        val path = Path()
        val half = width / 2f
        val height = width * ARROW_TALL
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
        paint.style = Paint.Style.FILL
        canvas.drawPath(path, paint)
    }

    private companion object {
        const val GAP = 3f
        const val ARROW = 0.56f
        const val ARROW_TALL = 1.05f
        const val ROW_GAP = 1f
    }
}
