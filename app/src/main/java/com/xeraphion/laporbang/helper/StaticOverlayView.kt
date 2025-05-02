package com.xeraphion.laporbang.helper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.tensorflow.lite.task.vision.detector.Detection

class StaticOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val boxPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 4.0f
    }

    private val textPaint = Paint().apply {
        color = Color.CYAN
        textSize = 16.0f
        style = Paint.Style.FILL
    }

    private val textBgPaint = Paint().apply {
        color = Color.TRANSPARENT
        style = Paint.Style.FILL
        alpha = 0
    }

    private var results: List<Detection> = emptyList()
    private var frameWidth: Int = 1
    private var frameHeight: Int = 1

    fun setResults(detectionResults: List<Detection>, width: Int, height: Int) {
        results = detectionResults
        frameWidth = width
        frameHeight = height
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val imageAspectRatio = frameWidth.toFloat() / frameHeight
        val viewAspectRatio = width.toFloat() / height

        val scaleX: Float
        val scaleY: Float
        val dx: Float
        val dy: Float

        if (imageAspectRatio > viewAspectRatio) {
            scaleX = width.toFloat() / frameWidth
            scaleY = scaleX
            dx = 0f
            dy = (height - frameHeight * scaleY)
        } else {
            scaleY = height.toFloat() / frameHeight
            scaleX = scaleY
            dx = (width - frameWidth * scaleX)
            dy = 0f
        }

        val verticalOffset = 240f
        val horizontalOffset = 120f

        for (result in results) {
            val boundingBox = result.boundingBox

            val left = boundingBox.left * scaleX * 1.45F + dx
            val top = boundingBox.top * scaleY * 1.45F+ dy
            val right = boundingBox.right * scaleX * 1.45F+ dx
            val bottom = boundingBox.bottom * scaleY * 1.6F+ dy

            canvas.drawRect(left, top, right, bottom, boxPaint)

            val label = "${result.categories[0].label} ${"%.2f".format(result.categories[0].score)}"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.textSize

            canvas.drawRect(
                left,
                top,
                left + textWidth + 8,
                top - textHeight - 8,
                textBgPaint
            )

            canvas.drawText(label, left + 4, top - 4, textPaint)
        }
    }
}