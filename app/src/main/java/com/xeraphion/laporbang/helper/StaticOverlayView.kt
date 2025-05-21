package com.xeraphion.laporbang.helper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import org.tensorflow.lite.task.vision.detector.Detection
import kotlin.collections.get
import kotlin.collections.plusAssign
import kotlin.times

class StaticOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var detectionMeasurements: Map<Int, String> = emptyMap() // To store diameter info


    private val boxPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 4.0f
    }

    private val textPaint = Paint().apply {
        color = Color.CYAN
        textSize = 20.0f
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

    fun setResultsWithMeasurements(
        detectionResults: List<Detection>,
        width: Int,
        height: Int,
        measurements: Map<Int, String>
    ) {
        results = detectionResults
        frameWidth = width
        frameHeight = height
        detectionMeasurements = measurements
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

        for ((index, result) in results.withIndex()) {
            val boundingBox = result.boundingBox

            val left = boundingBox.left * scaleX * 1.45F + dx
            val top = boundingBox.top * scaleY * 1.45F + dy
            val right = boundingBox.right * scaleX * 1.45F + dx
            val bottom = boundingBox.bottom * scaleY * 1.6F + dy

            canvas.drawRect(left, top, right, bottom, boxPaint)

            // Get label with confidence + diameter measurement if available
            val confidenceText = "${result.categories[0].label} ${"%.2f".format(result.categories[0].score)}"
            val measurementText = detectionMeasurements[index] ?: ""
            val label = if (measurementText.isNotEmpty())
                "$confidenceText\n$measurementText"
            else
                confidenceText

            // Draw text with measurement
            val lines = label.split("\n")
            var yPos = top - 4

            for (line in lines) {
                val textWidth = textPaint.measureText(line)
                canvas.drawRect(
                    left,
                    yPos - textPaint.textSize,
                    left + textWidth + 8,
                    yPos + 4,
                    textBgPaint
                )
                canvas.drawText(line, left + 4, yPos, textPaint)
                yPos += textPaint.textSize + 4
            }
        }
    }
}