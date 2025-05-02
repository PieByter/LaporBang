package com.xeraphion.laporbang.helper

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.xeraphion.laporbang.yolov11.detectors.ObjectDetection
import com.xeraphion.laporbang.yolov11.detectors.YoloDetector
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.Rot90Op

class StaticDetectorHelper(
    var threshold: Float = 0.5f,
    var numThreads: Int = 2,
    var maxResults: Int = 3,
    var currentDelegate: Int = DELEGATE_NNAPI,
    val context: Context,
    val objectDetectorListener: DetectorListener?
) {

    private var objectDetector: YoloDetector? = null

    init {
        setupObjectDetector()
    }

    fun detectSync(image: Bitmap, imageRotation: Int): List<ObjectDetection> {
        if (objectDetector == null) {
            setupObjectDetector()
        }
        return try {
            val imageProcessor = ImageProcessor.Builder()
                .add(Rot90Op(-imageRotation / 90))
                .build()
            val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))
            val results = objectDetector?.detect(tensorImage, imageRotation)
            results?.detections ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }


    fun clearObjectDetector() {
        objectDetector = null
    }

    fun setupObjectDetector() {
        try {
            // Initialize YOLOv11-specific detector
            objectDetector = YoloDetector(
                confidenceThreshold = threshold,
                iouThreshold = 0.3f, // Non-Maximum Suppression threshold
                numThreads = numThreads,
                maxResults = maxResults,
                currentDelegate = currentDelegate,
                currentModel = MODEL_YOLOV11,
                context = context
            )
        } catch (e: Exception) {
            objectDetectorListener?.onError("Error initializing YOLOv11 detector: ${e.message}")
        }
    }

    fun detect(image: Bitmap, imageRotation: Int) {
        if (objectDetector == null) {
            setupObjectDetector()
        }

        try {
            // Preprocess the image
            val imageProcessor = ImageProcessor.Builder()
                .add(Rot90Op(-imageRotation / 90))
                .build()
            val tensorImage = imageProcessor.process(TensorImage.fromBitmap(image))

            // Measure inference time
            val startTime = SystemClock.uptimeMillis()
            val results = objectDetector?.detect(tensorImage, imageRotation)
            val inferenceTime = SystemClock.uptimeMillis() - startTime

            // Pass results to the listener
            if (results != null) {
                objectDetectorListener?.onResults(
                    results.detections,
                    inferenceTime,
                    results.image.height,
                    results.image.width
                )
            }
        } catch (e: Exception) {
            objectDetectorListener?.onError("Detection error: ${e.message}")
        }
    }

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(
            results: List<ObjectDetection>,
            inferenceTime: Long,
            imageHeight: Int,
            imageWidth: Int
        )
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DELEGATE_NNAPI = 2
        const val MODEL_YOLOV11 = 4
    }
}