package app.netstrip.statusbar.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.max
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

    /** One pixel between the ink of the two rows. Font boxes are taller than the digits. */
    private fun drawPair(canvas: Canvas, paint: Paint, down: String, up: String, size: Int) {
        val edge = 1f
        var textSize = minOf(
            fitTextSize(paint, down, size, size / 2f),
            fitTextSize(paint, up, size, size / 2f),
        )
        while (textSize > 18f && pairBlock(paint, down, up, textSize) > size - edge * 2f) {
            textSize -= 1f
        }
        paint.textSize = textSize
        val downBox = textBounds(paint, down)
        val upBox = textBounds(paint, up)
        val arrowH = textSize * ARROW * ARROW_TALL
        val downH = max(downBox.height().toFloat(), arrowH)
        val upH = max(upBox.height().toFloat(), arrowH)
        val top = ((size - (downH + ROW_GAP + upH)) / 2f).coerceAtLeast(0f)
        drawInkRow(canvas, paint, down, pointingUp = false, size, top, downH, downBox, textSize)
        drawInkRow(canvas, paint, up, pointingUp = true, size, top + downH + ROW_GAP, upH, upBox, textSize)
    }

    private fun drawInkRow(
        canvas: Canvas,
        paint: Paint,
        text: String,
        pointingUp: Boolean,
        size: Int,
        top: Float,
        slotH: Float,
        box: Rect,
        textSize: Float,
    ) {
        paint.textSize = textSize
        val inkH = box.height().toFloat().coerceAtLeast(1f)
        val inkTop = top + (slotH - inkH) / 2f
        val baseline = inkTop - box.top
        val centerY = inkTop + inkH / 2f
        val textWidth = paint.measureText(text)
        val marker = textSize * ARROW
        val left = ((size - (marker + GAP + textWidth)) / 2f).coerceAtLeast(0f)
        drawArrow(canvas, paint, left + marker / 2f, centerY, marker, pointingUp)
        canvas.drawText(text, left + marker + GAP, baseline, paint)
    }

    private fun pairBlock(paint: Paint, down: String, up: String, textSize: Float): Float {
        paint.textSize = textSize
        val arrowH = textSize * ARROW * ARROW_TALL
        val downH = max(textBounds(paint, down).height().toFloat(), arrowH)
        val upH = max(textBounds(paint, up).height().toFloat(), arrowH)
        return downH + ROW_GAP + upH
    }

    private fun fitTextSize(paint: Paint, text: String, size: Int, start: Float): Float {
        val maxWidth = size * 0.98f
        var textSize = start
        paint.textSize = textSize
        var marker = textSize * ARROW
        var textWidth = paint.measureText(text)
        while (marker + GAP + textWidth > maxWidth && textSize > 18f) {
            textSize -= 1f
            paint.textSize = textSize
            marker = textSize * ARROW
            textWidth = paint.measureText(text)
        }
        return textSize
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
