package com.xeraphion.laporbang.yolov11.detectors

import android.content.Context
import com.xeraphion.laporbang.yolov11.ObjectDetectorHelper.Companion.MODEL_YOLO
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.util.LinkedList

class TaskVisionDetector(
    var options: ObjectDetector.ObjectDetectorOptions,
    var currentModel: Int = 0,
    val context: Context,

    ): com.xeraphion.laporbang.yolov11.detectors.ObjectDetector {

    private var objectDetector: ObjectDetector

    init {

        val modelName =
            when (currentModel) {
                MODEL_YOLO -> "yolov11_float32.tflite"
                else -> "yolov11_float32.tflite"
            }

        objectDetector = ObjectDetector.createFromFileAndOptions(context, modelName, options)

    }

    override fun detect(tensorImage: TensorImage, imageRotation: Int): DetectionResult {

        val tvDetections = objectDetector.detect(tensorImage)

        // Convert task view detections to common interface
        val detections = LinkedList<ObjectDetection>()
        for (tvDetection: Detection in tvDetections) {

            val cat = tvDetection.categories[0]

            val objDet = ObjectDetection(
                boundingBox = tvDetection.boundingBox,
                category = Category(
                    cat.label,
                    cat.score
                )
            )
            detections.add(objDet)
        }
        val results = DetectionResult(
            tensorImage.bitmap,
            detections
        )

        return results

    }
}