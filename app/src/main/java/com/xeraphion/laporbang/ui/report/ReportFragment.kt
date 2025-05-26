package com.xeraphion.laporbang.ui.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.xeraphion.laporbang.R
import com.xeraphion.laporbang.UserPreference
import com.xeraphion.laporbang.api.ApiConfig
import com.xeraphion.laporbang.databinding.FragmentReportBinding
import com.xeraphion.laporbang.helper.StaticDetectorHelper
import com.xeraphion.laporbang.helper.UnetHelper
import com.xeraphion.laporbang.helper.getImageUri
import com.xeraphion.laporbang.helper.reduceFileImage
import com.xeraphion.laporbang.helper.uriToFile
import com.xeraphion.laporbang.ui.maps.BitmapHelper
import com.xeraphion.laporbang.yolov11.detectors.ObjectDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.task.vision.detector.Detection
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

class ReportFragment : Fragment(), StaticDetectorHelper.DetectorListener {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var launcherGallery: ActivityResultLauncher<String>
    private lateinit var launcherIntentCamera: ActivityResultLauncher<Uri>

    private var currentPhotoPath: String? = null
    private var selectedImageFile: File? = null
    private var selectedLat: Double? = null
    private var selectedLng: Double? = null
    private var imageBitmap: Bitmap? = null
    private lateinit var objectDetectorHelper: StaticDetectorHelper
    private lateinit var unetHelper: UnetHelper
    private var lastProcessedBitmap: Bitmap? = null
    private var segmentationPercentageValue: Float? = null

    private val viewModel: ReportViewModel by viewModels {
        val userPreference = UserPreference.getInstance(requireContext())
        val token = runBlocking { userPreference.getToken() }
        val apiService = ApiConfig.getApiService(token)
        val repository = ReportRepository(apiService)
        ReportViewModelFactory(repository)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        objectDetectorHelper = StaticDetectorHelper(
            context = requireContext(),
            objectDetectorListener = this
        )
        unetHelper = UnetHelper(loadUnetModel())

        launcherGallery =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    val imageFile = uriToFile(it, requireContext()).reduceFileImage()
                    binding.ivShowImage.setImageURI(Uri.fromFile(imageFile))
                    selectedImageFile = imageFile
                    imageBitmap = BitmapFactory.decodeFile(imageFile.path)
                } ?: Toast.makeText(
                    requireContext(),
                    "TIdak ada gambar yang dipilih!",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }

        launcherIntentCamera =
            registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
                if (isSuccess) {
                    currentPhotoPath?.let { path ->
                        val imageFile = uriToFile(path.toUri(), requireContext()).reduceFileImage()
                        binding.ivShowImage.setImageBitmap(BitmapFactory.decodeFile(imageFile.path))
                        selectedImageFile = imageFile
                        imageBitmap = BitmapFactory.decodeFile(imageFile.path)
                    }
                }
            }

        binding.btnGalleryReport.setOnClickListener {
            startGallery()
        }

        binding.btnCameraReport.setOnClickListener {
            startCamera()
        }

        binding.btnCreateReport.setOnClickListener {
            submitReport()
        }

        binding.btnAnalyzeReport.setOnClickListener {
            imageBitmap?.let { bitmap ->
//                objectDetectorHelper.detect(bitmap, 0)
                binding.contentLoadingBar.show()
                binding.btnAnalyzeReport.isEnabled = false
                runCombinedInference(bitmap)
            } ?: Toast.makeText(
                requireContext(),
                "Tidak ada gambar yang dipilih!",
                Toast.LENGTH_SHORT
            ).show()
        }

        var mapView = binding.mapViewReport
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { googleMap ->
            val defaultLocation = LatLng(3.5952, 98.6722)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 11f))
            googleMap.uiSettings.isScrollGesturesEnabled = true
            googleMap.uiSettings.isZoomGesturesEnabled = true
            googleMap.uiSettings.isTiltGesturesEnabled = true
            googleMap.uiSettings.isRotateGesturesEnabled = true
            googleMap.uiSettings.isZoomControlsEnabled = true

            // Set a click listener to get the selected location
            googleMap.setOnMapClickListener { latLng ->
                googleMap.clear()
                val bitmap =
                    BitmapHelper.vectorToBitmap(requireContext(), R.drawable.ic_marker_maps)
                val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
                googleMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Lokasi Lubang Jalan")
                        .icon(bitmapDescriptor)
                )
                selectedLat = latLng.latitude
                selectedLng = latLng.longitude

                binding.tvCoordinatesReport.text =
                    "Koordinat : ${latLng.latitude}, ${latLng.longitude}"
            }
            googleMap.setOnMarkerClickListener { true }
        }

        setupTextWatchers()
        observeViewModel()
    }

    private fun setupTextWatchers() {
        binding.tvDiameterReport.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateSeverityTextView()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.tvDepthReport.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateSeverityTextView()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun submitReport() {
        val titles =
            binding.etTitlesReport.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        if (binding.etTitlesReport.text!!.isEmpty()) {
            Toast.makeText(requireContext(), "Judul Tidak Boleh Kosong !!", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val lat = selectedLat?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val lng = selectedLng?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

        if (lat == null || lng == null) {
            Toast.makeText(requireContext(), "Koordinat tidak valid", Toast.LENGTH_SHORT).show()
            return
        }

//        val diameter = binding.tvDiameterReport.text.toString()
//            .replace(",", ".")
//            .toRequestBody("text/plain".toMediaTypeOrNull())
//        val depth =
//            binding.tvDepthReport.text.toString().replace(",", ".")
//                .toRequestBody("text/plain".toMediaTypeOrNull())
        val diameterStr = binding.tvDiameterReport.text.toString().replace(",", ".")
        val diameterValue = try {
            diameterStr.toFloat()
        } catch (e: NumberFormatException) {
            Toast.makeText(requireContext(), "Format diameter tidak valid", Toast.LENGTH_SHORT)
                .show()
            return
        }
        val diameter = diameterValue.toString().toRequestBody("text/plain".toMediaTypeOrNull())

        // Proper decimal handling for depth
        val depthStr = binding.tvDepthReport.text.toString().replace(",", ".")
        val depthValue = try {
            depthStr.toFloat()
        } catch (e: NumberFormatException) {
            Toast.makeText(requireContext(), "Format kedalaman tidak valid", Toast.LENGTH_SHORT)
                .show()
            return
        }
        val depth = depthValue.toString().toRequestBody("text/plain".toMediaTypeOrNull())

        val holesCount = binding.tvHolesCountReport.text.toString()
            .toRequestBody("text/plain".toMediaTypeOrNull())

        if (binding.tvDiameterReport.text!!.isEmpty() || binding.tvDepthReport.text!!.isEmpty() || binding.tvHolesCountReport.text!!.isEmpty()) {
            Toast.makeText(requireContext(), "Semua Data Harus Diisi !!", Toast.LENGTH_SHORT).show()
            return
        }

        val segmentationPercentage = segmentationPercentageValue
            ?.toString()
            ?.toRequestBody("text/plain".toMediaTypeOrNull())

        if (segmentationPercentage == null) {
            Toast.makeText(
                requireContext(),
                "Persentase segmentasi tidak ada!",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val imageFile = lastProcessedBitmap?.let {
            saveBitmapToFile(requireContext(), it, "result.jpg")
        } ?: selectedImageFile

        val imagePart = imageFile?.let {
            val reqFile = it.reduceFileImage().asRequestBody("image/jpeg".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("imageUrl", it.name, reqFile)
        }

        viewModel.submitReport(
            titles, lat, lng, diameter, depth, holesCount, imagePart, segmentationPercentage
        )
    }

    private fun startCamera() {
        val photoURI: Uri = getImageUri(requireContext())
        currentPhotoPath = photoURI.toString()
        launcherIntentCamera.launch(photoURI)
    }

    private fun startGallery() {
        launcherGallery.launch("image/*")
    }

    //    private fun observeViewModel() {
//        lifecycleScope.launch {
//            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                launch {
//                    viewModel.reportState.collect { response ->
//                        response?.let {
//                            Toast.makeText(
//                                requireContext(),
//                                "Laporan Terkirim: ${it.message}",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                            findNavController().popBackStack()
//                        }
//                    }
//                }
//                launch {
//                    viewModel.errorState.collect { error ->
//                        error?.let {
//                            Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_SHORT)
//                                .show()
//                        }
//                    }
//                }
//            }
//        }
//    }
//    private fun observeViewModel() {
//        lifecycleScope.launch {
//            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
//                launch {
//                    viewModel.reportState.collect { response ->
//                        response?.let {
//                            try {
//                                // Log success for debugging
//                                Log.d("ReportFragment", "Report successful, attempting navigation")
//
//                                // Try direct navigation with findNavController
//                                try {
//                                    findNavController().navigate(R.id.nav_home)
//                                } catch (e: Exception) {
//                                    Log.e(
//                                        "ReportFragment",
//                                        "Direct navigation failed: ${e.message}"
//                                    )
//                                    // Fall back to popBackStack if navigate fails
//                                    findNavController().popBackStack(R.id.nav_home, false)
//                                }
//
//                                // Show toast AFTER navigation attempt
//                                try {
//                                    val message = it.message ?: "Laporan berhasil terkirim"
//                                    Toast.makeText(
//                                        requireContext(),
//                                        "Laporan Terkirim: $message",
//                                        Toast.LENGTH_SHORT
//                                    ).show()
//                                } catch (e: Exception) {
//                                    Log.e("ReportFragment", "Toast error: ${e.message}")
//                                }
//                            } catch (e: Exception) {
//                                Log.e("ReportFragment", "Navigation error: ${e.message}", e)
//                            }
//                        }
//                    }
//                }
//                launch {
//                    viewModel.errorState.collect { error ->
//                        error?.let {
//                            try {
//                                Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_SHORT)
//                                    .show()
//                                Log.d("ReportFragment", "Error toast displayed for: $it")
//                            } catch (e: Exception) {
//                                Log.e("ReportFragment", "Error toast failed: ${e.message}")
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.reportState.collect { response ->
                        response?.let {
                            val message = it.message ?: "Laporan berhasil terkirim"
                            try {
                                findNavController().navigate(R.id.nav_home)
                            } catch (e: Exception) {
                                findNavController().popBackStack(R.id.nav_home, false)
                            }

                            if (isAdded) {
                                Toast.makeText(
                                    requireContext(),
                                    "Laporan Terkirim: $message",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.errorState.collect { error ->
                        error?.let {
                            if (isAdded) {
                                Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun classifySeverity(diameterMm: Float, areaPercentage: Float): String {
        val areaCategory = when {
            areaPercentage < 10.0f -> 1
            areaPercentage < 25.0f -> 2
            else -> 3
        }
        val diameterCategory = when {
            diameterMm < 200.0f -> 1
            diameterMm < 450.0f -> 2
            diameterMm <= 750.0f -> 3
            else -> 3
        }
        return when {
            areaCategory == 1 && diameterCategory == 1 -> "Rendah"
            areaCategory == 1 && diameterCategory == 2 -> "Rendah"
            areaCategory == 1 && diameterCategory == 3 -> "Sedang"
            areaCategory == 2 && diameterCategory == 1 -> "Rendah"
            areaCategory == 2 && diameterCategory == 2 -> "Sedang"
            areaCategory == 2 && diameterCategory == 3 -> "Tinggi"
            areaCategory == 3 && diameterCategory == 1 -> "Sedang"
            areaCategory == 3 && diameterCategory == 2 -> "Tinggi"
            areaCategory == 3 && diameterCategory == 3 -> "Tinggi"
            else -> "Tidak diketahui"
        }
    }

    private fun updateSeverityTextView() {
        val diameter = binding.tvDiameterReport.text.toString().toFloatOrNull() ?: 0f
        val areaPercentage = segmentationPercentageValue ?: 0f

        if (segmentationPercentageValue != null) {
            val severity = classifySeverity(diameter, areaPercentage)
            binding.tvSeverityReport.text = "Tingkat Keparahan: $severity"
            binding.tvSegmentationPercentageReport.text =
                "Persentase Segmentasi: ${String.format("%.2f", areaPercentage).replace(".", ",")}%"
        } else if (imageBitmap != null) {
            // Use viewLifecycleOwner to cancel coroutine when view is destroyed
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    // Run inference on background thread
                    val maskOutput = withContext(Dispatchers.Default) {
                        unetHelper.runInference(imageBitmap!!)
                    }
                    val percent = withContext(Dispatchers.Default) {
                        unetHelper.percentageSegmentation(maskOutput)
                    }

                    // Check if view is still attached before updating UI
                    if (isAdded && _binding != null) {
                        segmentationPercentageValue = percent.toFloat()
                        val severity = classifySeverity(diameter, percent.toFloat())
                        binding.tvSeverityReport.text = "Tingkat Keparahan: $severity"
                        binding.tvSegmentationPercentageReport.text =
                            "Persentase Segmentasi: ${
                                String.format("%.2f", percent).replace(".", ",")
                            }%"
                    }
                } catch (e: Exception) {
                    Log.e("ReportFragment", "Error updating severity", e)
                }
            }
        } else {
            segmentationPercentageValue = null
            binding.tvSeverityReport.text = "Tingkat Keparahan: -"
            binding.tvSegmentationPercentageReport.text = "Persentase Segmentasi: -"
        }
    }

    override fun onError(error: String) {
        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
    }

    override fun onResults(
        results: List<ObjectDetection>,
        inferenceTime: Long,
        imageHeight: Int,
        imageWidth: Int,
    ) {
        val tfliteResults = results.map {
            Detection.create(
                it.boundingBox,
                listOf(
                    Category.create(
                        it.category.label,
                        null,
                        it.category.confidence
                    )
                )
            )
        }
        val potholeCount = results.size
        Toast.makeText(
            requireContext(),
            "Lubang Jalan Terdeteksi : $potholeCount",
            Toast.LENGTH_SHORT
        ).show()

        binding.tvHolesCountReport.setText(potholeCount.toString())

        binding.ivResultImage.setImageBitmap(imageBitmap)

        binding.ivResultImage.post {
            val overlayWidth = binding.ivResultImage.width
            val overlayHeight = binding.ivResultImage.height

            binding.staticOverlayView.setResults(tfliteResults, overlayWidth, overlayHeight)
        }

        processDetections(results, imageBitmap!!)
    }

    private fun loadUnetModel(): Interpreter {
        return try {
            val assetManager = requireContext().assets
            val fd = assetManager.openFd("unet_train.tflite")
            val inputStream = FileInputStream(fd.fileDescriptor)
            val channel = inputStream.channel
            val startOffset = fd.startOffset
            val declaredLength = fd.declaredLength
            val buffer = channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
            }
            Interpreter(buffer, options)
        } catch (e: Exception) {
            Log.e("CameraFragment", "Error loading U-Net model", e)
            throw RuntimeException("Error loading U-Net model", e)
        }
    }

    private fun runCombinedInference(originalBitmap: Bitmap) {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val (yoloResult, maskedBitmap) = withContext(Dispatchers.Default) {
                    val yolo = async { detectObjects(originalBitmap) }
                    val unet = async { segmentImage(originalBitmap) }
                    Pair(yolo.await(), unet.await())
                }
                updateUI(originalBitmap, yoloResult, maskedBitmap)
            } finally {
                binding.contentLoadingBar.hide()
                binding.btnAnalyzeReport.isEnabled = true
            }
        }
    }

    private suspend fun detectObjects(bitmap: Bitmap): List<ObjectDetection> {
        return withContext(Dispatchers.Default) {
            try {
                objectDetectorHelper.detectSync(bitmap, 0)
            } catch (e: Exception) {
                Log.e("CameraFragment", "Exception during object detection call", e)
                emptyList()
            }
        }
    }

    private suspend fun segmentImage(bitmap: Bitmap): Bitmap {
        return withContext(Dispatchers.Default) {
            val maskOutput = unetHelper.runInference(bitmap)
            unetHelper.applyMaskToImage(bitmap, maskOutput)
        }
    }

    private fun updateUI(
        originalBitmap: Bitmap,
        detections: List<ObjectDetection>?,
        maskedBitmap: Bitmap?,
    ) {
        val resultBitmap = maskedBitmap ?: originalBitmap
        binding.ivResultImage.setImageBitmap(resultBitmap)

        // Process detections to ensure measurements are calculated and displayed
        if (!detections.isNullOrEmpty()) {
            processDetections(detections, originalBitmap)
        } else {
            binding.tvDiameterReport.setText("0.0")
            Toast.makeText(requireContext(), "Tidak terdeteksi lubang jalan", Toast.LENGTH_SHORT)
                .show()
        }

        val holesCount = detections?.size ?: 0
        binding.tvHolesCountReport.setText(holesCount.toString())

        lastProcessedBitmap = resultBitmap
        saveBitmapToFile(requireContext(), resultBitmap, "result.jpg")

        // Convert ObjectDetection to Detection for StaticOverlayView
        val tfliteResults = detections?.map {
            Detection.create(
                it.boundingBox,
                listOf(
                    Category.create(
                        it.category.label,
                        null,
                        it.category.confidence
                    )
                )
            )
        } ?: emptyList()

        val baseBitmap = maskedBitmap ?: originalBitmap

//
//        lastProcessedBitmap = drawDetectionsOnBitmap(
//            baseBitmap,
//            tfliteResults,
//            originalBitmap.width,
//            originalBitmap.height
//        )

        lastProcessedBitmap?.let {
            binding.ivResultImage.setImageBitmap(it)
            saveBitmapToFile(requireContext(), it, "overlay_result.jpg")
        }

        binding.ivResultImage.post {
            val overlayWidth = binding.ivResultImage.width
            val overlayHeight = binding.ivResultImage.height
            binding.staticOverlayView.setResults(tfliteResults, overlayWidth, overlayHeight)
        }

        showToast("Lubang jalan terdeteksi : ${detections?.size ?: 0}")

        updateSeverityTextView()
    }

    private fun showToast(message: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }


    fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file
    }


    fun processDetections(
        detections: List<ObjectDetection>,
        bitmap: Bitmap,
    ) {
        val prefs = requireContext().getSharedPreferences("CalibrationPrefs", Context.MODE_PRIVATE)
        val basePixelsPerCm = prefs.getFloat("pixels_per_cm", 36.72f)
        val calibrationReferenceSize = 3120f

        val originalWidth = bitmap.width.toFloat()
        val originalHeight = bitmap.height.toFloat()


        val yoloInputSize = 640f

        val scaleX = originalWidth / yoloInputSize
        val scaleY = originalHeight / yoloInputSize


        val imageToCalibrationRatio =
            kotlin.math.max(originalWidth, originalHeight) / calibrationReferenceSize

        val currentImagePixelsPerCm = basePixelsPerCm * imageToCalibrationRatio


        val yoloPixelsPerCm = currentImagePixelsPerCm / kotlin.math.max(scaleX, scaleY)

        Log.d("Calibration", "Original image: ${bitmap.width}x${bitmap.height}")
        Log.d("Calibration", "Scale factors: X=$scaleX, Y=$scaleY")
        Log.d("Calibration", "Base calibration: $basePixelsPerCm pixels/cm (at 3120px)")
        Log.d("Calibration", "Current image calibration: $currentImagePixelsPerCm pixels/cm")
        Log.d("Calibration", "YOLO space calibration: $yoloPixelsPerCm pixels/cm (at 640px)")

        var maxDiameterCm = 0f
        var detectedPotholes = 0
        val measurementMap = mutableMapOf<Int, String>()

        detections.forEachIndexed { index, detection ->
            if (detection.category.label.contains("pothole", ignoreCase = true) ||
                detection.category.label.contains("lubang", ignoreCase = true) ||
                detection.category.label == "0" ||
                detection.category.label == "Lubang"
            ) {
                val bbox = detection.boundingBox

                // YOLO returns coordinates in 640x640 space
                val widthPixelsYolo = bbox.width()
                val heightPixelsYolo = bbox.height()

                // Convert to cm using the calibration for YOLO space
                val widthCm = widthPixelsYolo / yoloPixelsPerCm
                val heightCm = heightPixelsYolo / yoloPixelsPerCm

                // Calculate diagonal using Pythagorean theorem
                val diagonalPixelsYolo = kotlin.math.sqrt(
                    (widthPixelsYolo * widthPixelsYolo + heightPixelsYolo * heightPixelsYolo).toDouble()
                ).toFloat()
                val diagonalCm = diagonalPixelsYolo / yoloPixelsPerCm

                android.util.Log.d(
                    "DiameterCalc",
                    "Detection $index: width=${widthPixelsYolo}px (${widthCm}cm), " +
                            "height=${heightPixelsYolo}px (${heightCm}cm), diagonal=${diagonalCm}cm"
                )

                if (diagonalCm > maxDiameterCm) {
                    maxDiameterCm = diagonalCm
                }

                detectedPotholes++
                measurementMap[index] =
                    "Ø (W: ${String.format("%.2f", widthCm).replace(".", ",")} × H: ${
                        String.format(
                            "%.2f",
                            heightCm
                        ).replace(".", ",")
                    })"
            }
        }

        activity?.runOnUiThread {
            if (detectedPotholes > 0) {
                val diameterText = String.format("%.2f", maxDiameterCm)
                    .replace(".", ",")
                binding.tvDiameterReport.setText(diameterText)

                val tfliteResults = detections.map { detection ->
                    Detection.create(
                        detection.boundingBox,
                        listOf(
                            Category.create(
                                detection.category.label,
                                null,
                                detection.category.confidence
                            )
                        )
                    )
                }

                binding.ivResultImage.post {
                    binding.staticOverlayView.setResultsWithMeasurements(
                        tfliteResults,
                        binding.ivResultImage.width,
                        binding.ivResultImage.height,
                        measurementMap
                    )
                }

                binding.tvHolesCountReport.setText(detectedPotholes.toString())

                updateSeverityTextView()
            } else {
                binding.tvDiameterReport.setText("0,0")
                binding.tvHolesCountReport.setText("0")
                binding.staticOverlayView.setResults(emptyList(), 0, 0)
                Toast.makeText(requireContext(), "Tidak ada lubang terdeteksi", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun drawDetectionsOnBitmap(
        bitmap: Bitmap,
        detections: List<Detection>,
        measurements: Map<Int, String> = emptyMap()
    ): Bitmap {
        // Create a mutable copy of the bitmap to draw on
        val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(resultBitmap)

        // Define paint objects similar to StaticOverlayView
        val boxPaint = Paint().apply {
            color = Color.CYAN
            style = Paint.Style.STROKE
            strokeWidth = 8.0f
        }

        val textPaint = Paint().apply {
            color = Color.CYAN
            textSize = 40.0f
            style = Paint.Style.FILL
        }

        val textBgPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            alpha = 128
        }

        // Draw each detection on the bitmap
        for ((index, result) in detections.withIndex()) {
            val boundingBox = result.boundingBox

            // Draw the bounding box
            canvas.drawRect(boundingBox, boxPaint)

            // Prepare the text
            val confidenceText = "${result.categories[0].label} ${"%.2f".format(result.categories[0].score)}"
            val measurementText = measurements[index] ?: ""
            val label = if (measurementText.isNotEmpty())
                "$confidenceText\n$measurementText"
            else
                confidenceText

            // Draw text with measurement
            val lines = label.split("\n")
            var yPos = boundingBox.top - 8

            for (line in lines) {
                val textWidth = textPaint.measureText(line)
                canvas.drawRect(
                    boundingBox.left,
                    yPos - textPaint.textSize,
                    boundingBox.left + textWidth + 8,
                    yPos + 4,
                    textBgPaint
                )
                canvas.drawText(line, boundingBox.left + 4, yPos, textPaint)
                yPos += textPaint.textSize + 4
            }
        }

        return resultBitmap
    }
    override fun onResume() {
        super.onResume()
        binding.mapViewReport.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapViewReport.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapViewReport.onLowMemory()
    }

    override fun onStart() {
        super.onStart()
        binding.mapViewReport.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.mapViewReport.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}