package com.xeraphion.laporbang.Ztempdelete

import androidx.appcompat.app.AppCompatActivity

class CameraActivityCOPY : AppCompatActivity() {
//    private lateinit var binding: ActivityCameraBinding
//    private lateinit var detector: YOLOv11Detector
//    private val cameraExecutor = Executors.newSingleThreadExecutor()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        binding = ActivityCameraBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        detector = YOLOv11Detector(this)
//
//        if (ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.CAMERA
//            ) == PackageManager.PERMISSION_GRANTED
//        ) {
//            startCamera()
//        } else {
//            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
//        }
//
//        binding.captureButton.setOnClickListener {
//            binding.cameraPreview.bitmap?.let { bitmap ->
//                processImage(bitmap)
//            } ?: run {
//                // Tampilkan pesan error jika bitmap null
//                Toast.makeText(this, "Gagal mengambil gambar", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    private fun startCamera() {
//        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
//
//        cameraProviderFuture.addListener({
//            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
//
//            val preview = Preview.Builder()
//                .setTargetRotation(binding.cameraPreview.display.rotation)
//                .build()
//                .also {
//                    it.surfaceProvider = binding.cameraPreview.surfaceProvider
//                }
//
//            val imageAnalysis = ImageAnalysis.Builder()
//                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
//                .build()
//                .also {
//                    it.setAnalyzer(cameraExecutor) { imageProxy ->
//                        processCameraFrame(imageProxy)
//                    }
//                }
//
//            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
//
//            try {
//                cameraProvider.unbindAll()
//                cameraProvider.bindToLifecycle(
//                    this,
//                    cameraSelector,
//                    preview,
//                    imageAnalysis
//                )
//            } catch (exc: Exception) {
//                exc.printStackTrace()
//            }
//        }, ContextCompat.getMainExecutor(this))
//    }
//
//    private fun processCameraFrame(imageProxy: ImageProxy) {
//        imageProxy.use { proxy ->
//            val bitmap = proxy.toBitmap()
//            val detection = detector.detect(bitmap)
//
//            runOnUiThread {
//                binding.overlayView.clear()
//                detection?.let {
//                    binding.overlayView.drawDetection(
//                        DetectionOverlayView.Detection(
//                            it.boundingBox,
//                            it.score
//                        )
//                    )
//                    binding.detectionInfo.text =
//                        "Pothole detected (${"%.2f".format(it.score)})"
//                }
//            }
//        }
//    }
//
//    private fun processImage(bitmap: Bitmap) {
//        val detection = detector.detect(bitmap)
//        runOnUiThread {
//            binding.overlayView.clear()
//            detection?.let {
//                binding.overlayView.drawDetection(
//                    DetectionOverlayView.Detection(
//                        it.boundingBox,
//                        it.score
//                    )
//                )
//                binding.detectionInfo.text =
//                    "Pothole detected (${"%.2f".format(it.score)})"
//            }
//        }
//    }
//
//    override fun onRequestPermissionsResult(
//        requestCode: Int,
//        permissions: Array<String>,
//        grantResults: IntArray,
//    ) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == 101) {
//            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                startCamera()
//            } else {
//                finish()
//            }
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        cameraExecutor.shutdown()
//    }
}