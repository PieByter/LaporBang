package com.xeraphion.laporbang.helper

import android.graphics.*
import android.util.Log
import androidx.core.graphics.scale
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UnetHelper(private val unetInterpreter: Interpreter) {

    private val inputWidth = 256
    private val inputHeight = 256
    private val inputChannels = 3
    private val outputChannels = 2
    private val bytesPerFloat = 4

    fun runInference(inputBitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val inputBuffer = preprocessBitmap(inputBitmap)
        val outputBuffer = Array(1) { Array(inputHeight) { Array(inputWidth) { FloatArray(outputChannels) } } }
        try {
            inputBuffer.rewind()
            unetInterpreter.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            Log.e("UnetHelper", "Error during U-Net inference", e)
            throw RuntimeException("U-Net inference failed", e)
        }
        return outputBuffer
    }

    fun applyMaskToImage(original: Bitmap, maskOutput: Array<Array<Array<FloatArray>>>): Bitmap {
        val resultBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)
        val paint = Paint().apply {
            color = Color.argb(128, 255, 0, 0)
            style = Paint.Style.FILL
        }
        val scaleX = original.width.toFloat() / inputWidth
        val scaleY = original.height.toFloat() / inputHeight
        val maskHeight = maskOutput[0].size
        val maskWidth = if (maskHeight > 0) maskOutput[0][0].size else 0
        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                val backgroundProb = maskOutput[0][y][x][0]
                val foregroundProb = maskOutput[0][y][x][1]
                if (foregroundProb > backgroundProb && foregroundProb > 0.5f) {
                    val rect = RectF(
                        x * scaleX, y * scaleY,
                        (x + 1) * scaleX, (y + 1) * scaleY
                    )
                    canvas.drawRect(rect, paint)
                }
            }
        }
        return resultBitmap
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val resizedBitmap = bitmap.scale(inputWidth, inputHeight)
        val byteBuffer = ByteBuffer.allocateDirect(inputWidth * inputHeight * inputChannels * bytesPerFloat)
        byteBuffer.order(ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()
        val intValues = IntArray(inputWidth * inputHeight)
        resizedBitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        var pixel = 0
        for (y in 0 until inputHeight) {
            for (x in 0 until inputWidth) {
                val value = intValues[pixel++]
                floatBuffer.put(((value shr 16 and 0xFF)) / 255.0f)
                floatBuffer.put(((value shr 8 and 0xFF)) / 255.0f)
                floatBuffer.put(((value and 0xFF)) / 255.0f)
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }
}