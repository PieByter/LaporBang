package com.xeraphion.laporbang.ui.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.createBitmap
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
                    "TIdak ada gambar yang dipilih",
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
            } ?: Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show()
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
                googleMap.clear() // Clear previous markers
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
            Toast.makeText(requireContext(), "Invalid coordinates", Toast.LENGTH_SHORT).show()
            return
        }

        val diameter =
            binding.tvDiameterReport.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val depth =
            binding.tvDepthReport.text.toString().toRequestBody("text/plain".toMediaTypeOrNull())
        val holesCount = binding.tvHolesCountReport.text.toString()
            .toRequestBody("text/plain".toMediaTypeOrNull())

        if (binding.tvDiameterReport.text!!.isEmpty() || binding.tvDepthReport.text!!.isEmpty() || binding.tvHolesCountReport.text!!.isEmpty()) {
            Toast.makeText(requireContext(), "Semua Data Harus Diisi !!", Toast.LENGTH_SHORT).show()
            return
        }

//        val imagePart = selectedImageFile?.let {
//            val reqFile = it.reduceFileImage().asRequestBody("image/*".toMediaTypeOrNull())
//            MultipartBody.Part.createFormData("imageUrl", it.name, reqFile)
//        }

        val segmentationPercentage = segmentationPercentageValue
            ?.toString()
            ?.toRequestBody("text/plain".toMediaTypeOrNull())

        if (segmentationPercentage == null) {
            Toast.makeText(
                requireContext(),
                "Segmentation percentage is missing!",
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

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.reportState.collect { response ->
                        response?.let {
                            Toast.makeText(
                                requireContext(),
                                "Laporan Terkirim: ${it.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            findNavController().popBackStack()
                        }
                    }
                }
                launch {
                    viewModel.errorState.collect { error ->
                        error?.let {
                            Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                }
            }
        }
    }

    private fun classifySeverity(diameter: Float, depth: Float): String {
        var row = 0
        var col = 0

        if (depth < 25) row = 1
        else if (depth in 25.0..49.9) row = 2
        else if (depth >= 50) row = 3

        if (diameter < 200) col = 1
        else if (diameter in 200.0..449.9) col = 2
        else if (diameter >= 450) col = 3

        // Severity matrix
        val matrix = mapOf(
            "1,1" to "Rendah",
            "1,2" to "Rendah",
            "1,3" to "Sedang",
            "2,1" to "Rendah",
            "2,2" to "Sedang",
            "2,3" to "Tinggi",
            "3,1" to "Sedang",
            "3,2" to "Sedang",
            "3,3" to "Tinggi"
        )

        val key = "$row,$col"
        return matrix[key] ?: "Tidak diketahui" // fallback
    }


    private fun updateSeverityTextView() {
//        val diameter = binding.tvDiameterReport.text.toString().toFloatOrNull() ?: 0f
//        val depth = binding.tvDepthReport.text.toString().toFloatOrNull() ?: 0f
//
//        val severity = classifySeverity(diameter, depth)
//        binding.tvSeverityReport.text = "Tingkat Keparahan : \n$severity"
        imageBitmap?.let { bitmap ->
            lifecycleScope.launch(Dispatchers.Default) {
                val maskOutput = unetHelper.runInference(bitmap)
                val (severity, percent) = unetHelper.classifySeverityByMask(maskOutput)
                withContext(Dispatchers.Main) {
                    binding.tvSeverityReport.text = "Tingkat Keparahan: $severity"
                    segmentationPercentageValue = percent.toFloat()
                    binding.tvSegmentationPercentageReport.text =
                        "Persentase Segmentasi: %.2f%%".format(percent)
                }
            }
        } ?: run {
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
            val fd = assetManager.openFd("unet_primer.tflite")
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
            android.util.Log.e("CameraFragment", "Error loading U-Net model", e)
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
                android.util.Log.e("CameraFragment", "Exception during object detection call", e)
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

        showToast("Potholes detected: ${detections?.size ?: 0}")

        updateSeverityTextView()
    }

    private fun showToast(message: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

//    fun drawDetectionsOnBitmap(
//        baseBitmap: Bitmap,
//        detections: List<Detection>,
//        frameWidth: Int,
//        frameHeight: Int,
//    ): Bitmap {
//        val resultBitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
//        val canvas = Canvas(resultBitmap)
//
//        val boxPaint = Paint().apply {
//            color = android.graphics.Color.CYAN
//            style = Paint.Style.STROKE
//            strokeWidth = 2.0f
//        }
//        val textPaint = Paint().apply {
//            color = android.graphics.Color.CYAN
//            textSize = 8.0f
//            style = Paint.Style.FILL
//        }
//
//        val imageAspectRatio = frameWidth.toFloat() / frameHeight
//        val viewAspectRatio = baseBitmap.width.toFloat() / baseBitmap.height
//
//        val scaleX: Float
//        val scaleY: Float
//        val dx: Float
//        val dy: Float
//
//        if (imageAspectRatio > viewAspectRatio) {
//            scaleX = baseBitmap.width.toFloat() / frameWidth
//            scaleY = scaleX
//            dx = 0f
//            dy = (baseBitmap.height - frameHeight * scaleY) / 2f
//        } else {
//            scaleY = baseBitmap.height.toFloat() / frameHeight
//            scaleX = scaleY
//            dx = (baseBitmap.width - frameWidth * scaleX) / 2f
//            dy = 0f
//        }
//
//        for (result in detections) {
//            val boundingBox = result.boundingBox
//            val left = boundingBox.left * scaleX + dx
//            val top = boundingBox.top * scaleY + dy
//            val right = boundingBox.right * scaleX + dx
//            val bottom = boundingBox.bottom * scaleY + dy
//
//            canvas.drawRect(left, top, right, bottom, boxPaint)
//            val label = "${result.categories[0].label} ${"%.2f".format(result.categories[0].score)}"
//            canvas.drawText(label, left + 4, top - 4, textPaint)
//        }
//
//        return resultBitmap
//    }

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file
    }

    // 1. Detect lane width in pixels (simple grayscale + edge detection)
    fun getLaneWidthPixels(bitmap: Bitmap): Float? {
        val gray = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(gray)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // You can use OpenCV for Android for better edge/line detection if needed
        // For now, just return null (stub)
        return null
    }

    fun processDetections(
        detections: List<ObjectDetection>,
        bitmap: Bitmap,
        realLaneWidthCm: Float = 300f,
    ) {
        val laneWidthPixels = getLaneWidthPixels(bitmap)
        if (laneWidthPixels == null) {
            binding.tvDiameterReport.text = Editable.Factory.getInstance().newEditable("Lane width not detected")
            return
        }

        val pixelsPerCm = laneWidthPixels / realLaneWidthCm

        detections.forEach { detection ->
            if (detection.category.label == "pothole") {
                val bbox = detection.boundingBox
                val diameterPixels = bbox.right - bbox.left
                val diameterCm = diameterPixels / pixelsPerCm

                // Update UI with the calculated diameter
                binding.tvDiameterReport.text = Editable.Factory.getInstance().newEditable("%.2f cm".format(diameterCm))
            }
        }
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