package com.xeraphion.laporbang.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.xeraphion.laporbang.R
import com.xeraphion.laporbang.UserPreference
import com.xeraphion.laporbang.api.ApiConfig
import com.xeraphion.laporbang.databinding.FragmentUpdateReportBinding
import com.xeraphion.laporbang.helper.LocationHelper
import com.xeraphion.laporbang.helper.StaticDetectorHelper
import com.xeraphion.laporbang.helper.UnetHelper
import com.xeraphion.laporbang.helper.getImageUri
import com.xeraphion.laporbang.helper.reduceFileImage
import com.xeraphion.laporbang.helper.uriToFile
import com.xeraphion.laporbang.response.Location
import com.xeraphion.laporbang.response.ReportsResponseItem
import com.xeraphion.laporbang.ui.maps.BitmapHelper
import com.xeraphion.laporbang.yolov11.detectors.ObjectDetection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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

class UpdateReportFragment : Fragment(), StaticDetectorHelper.DetectorListener {

    private var _binding: FragmentUpdateReportBinding? = null
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

    private lateinit var locationHelper: LocationHelper
    private lateinit var googleMap: GoogleMap

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentUpdateReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        objectDetectorHelper = StaticDetectorHelper(
            context = requireContext(),
            objectDetectorListener = this
        )
        unetHelper = UnetHelper(loadUnetModel())

        setupImagePickers()
//        setupTextWatchers()

        val report = arguments?.getParcelable<ReportsResponseItem>("report")
        report?.let {
            populateUI(it)
        }

        val reportId = arguments?.getString("reportId")
        reportId?.let { fetchAndPopulateReport(it) }

        binding.btnUpdateReport.setOnClickListener {
            reportId?.let { id ->
                updateReport(id)
            }
        }

        binding.mapViewUpdateReport.onCreate(savedInstanceState)
        binding.mapViewUpdateReport.getMapAsync { map ->
            googleMap = map
            locationHelper = LocationHelper(requireContext(), googleMap)

            setupMapView(report?.location)
            locationHelper.getMyLocation(requestLocationPermissionLauncher)
        }

    }

    private fun setupImagePickers() {
        launcherGallery =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    val imageFile = uriToFile(it, requireContext()).reduceFileImage()
                    imageBitmap = BitmapFactory.decodeFile(imageFile.path)
                    binding.ivShowImage.setImageBitmap(imageBitmap)
                    selectedImageFile = imageFile
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
                        imageBitmap = BitmapFactory.decodeFile(imageFile.path) // Set imageBitmap
                        binding.ivShowImage.setImageBitmap(imageBitmap) // Display the new image
                        selectedImageFile = imageFile
                    }
                }
            }

        binding.btnGalleryUpdateReport.setOnClickListener { startGallery() }
        binding.btnCameraUpdateReport.setOnClickListener { startCamera() }

        binding.btnAnalyzeUpdateReport.setOnClickListener {
            imageBitmap?.let { bitmap ->
                binding.contentLoadingBar.show()
                binding.btnAnalyzeUpdateReport.isEnabled = false
                runCombinedInference(bitmap)
            } ?: Toast.makeText(requireContext(), "No image selected", Toast.LENGTH_SHORT).show()
        }
    }


    private fun setupTextWatchers() {
        binding.tvDiameterUpdateReport.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateSeverityTextView()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.tvDepthUpdateReport.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateSeverityTextView()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun populateUI(report: ReportsResponseItem) {
        binding.etTitlesUpdateReport.setText(report.titles)
        binding.tvCoordinatesUpdateReport.text =
            "Koordinat: ${report.location?.lat}, ${report.location?.lng}"
        binding.tvHolesCountUpdateReport.setText(report.holesCount.toString())
        binding.tvDiameterUpdateReport.setText(report.diameter.toString())
        binding.tvDepthUpdateReport.setText(report.depth.toString())
        segmentationPercentageValue = report.segmentationPercentage
        binding.tvSegmentationPercentageUpdateReport.text =
            "Persentase Segmentasi: %.2f%%".format(report.segmentationPercentage ?: 0.0)
        binding.tvSeverityUpdateReport.text = "Tingkat Keparahan: ${report.severity}"

        selectedLat = report.location?.lat
        selectedLng = report.location?.lng

        Glide.with(requireContext())
            .asBitmap()
            .load(report.imageUrl)
            .placeholder(R.drawable.ic_image)
            .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?,
                ) {
                    binding.ivShowImage.setImageBitmap(resource)
                    imageBitmap = resource // Set imageBitmap from server image
                }

                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                    // Optional: handle placeholder
                }
            })

        setupMapView(report.location)
    }

    private fun setupMapView(location: Location?) {
        googleMap.clear()

        // Use the report's location or default to Indonesia
        val initialLatLng = location?.let {
            LatLng(it.lat ?: 0.0, it.lng ?: 0.0)
        } ?: LatLng(3.5952, 98.6722)

        val bitmap = BitmapHelper.vectorToBitmap(requireContext(), R.drawable.ic_marker_maps)
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap)
        googleMap.addMarker(
            MarkerOptions()
                .position(initialLatLng)
                .title("Lokasi Lubang Jalan")
                .icon(bitmapDescriptor)
        )
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 15f))

        googleMap.uiSettings.apply {
            isScrollGesturesEnabled = true
            isZoomGesturesEnabled = true
            isTiltGesturesEnabled = true
            isRotateGesturesEnabled = true
            isZoomControlsEnabled = true
        }

        // Allow the user to select a new location
        googleMap.setOnMapClickListener { latLng ->
            googleMap.clear()
            googleMap.addMarker(
                MarkerOptions().position(latLng).title("Lokasi Terbaru Lubang Jalan")
                    .icon(bitmapDescriptor)
            )

            selectedLat = latLng.latitude
            selectedLng = latLng.longitude

            binding.tvCoordinatesUpdateReport.text =
                "Koordinat : ${latLng.latitude}, ${latLng.longitude}"
        }
        googleMap.setOnMarkerClickListener { true }
    }

    private fun updateReport(reportId: String) {
        val titles = binding.etTitlesUpdateReport.text.toString()
            .toRequestBody("text/plain".toMediaTypeOrNull())
        val lat = selectedLat?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())
        val lng = selectedLng?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

        if (lat == null || lng == null) {
            Toast.makeText(requireContext(), "Koordinat Tidak Valid", Toast.LENGTH_SHORT).show()
            return
        }
        val holesCount = binding.tvHolesCountUpdateReport.text.toString()
            .toRequestBody("text/plain".toMediaTypeOrNull())
        val diameter = binding.tvDiameterUpdateReport.text.toString()
            .toRequestBody("text/plain".toMediaTypeOrNull())
        val depth = binding.tvDepthUpdateReport.text.toString()
            .toRequestBody("text/plain".toMediaTypeOrNull())

//        val imagePart = selectedImageFile?.let {
//            val reqFile = it.reduceFileImage().asRequestBody("image/*".toMediaTypeOrNull())
//            MultipartBody.Part.createFormData("imageUrl", it.name, reqFile)
//        }

        val segmentationPercentage = segmentationPercentageValue
            ?.toString()
            ?.toRequestBody("text/plain".toMediaTypeOrNull())

        if (segmentationPercentage == null) {
            Toast.makeText(requireContext(), "Segmentation percentage is missing!", Toast.LENGTH_SHORT).show()
            return
        }

        val imageFile = lastProcessedBitmap?.let {
            saveBitmapToFile(requireContext(), it, "result.jpg")
        } ?: selectedImageFile

        val imagePart = imageFile?.let {
            val reqFile = it.reduceFileImage().asRequestBody("image/jpeg".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("imageUrl", it.name, reqFile)
        }

        lifecycleScope.launch {
            try {
                val token = UserPreference.getInstance(requireContext()).getToken()
                val response = ApiConfig.getApiService(token).updateReport(
                    id = reportId,
                    titles = titles,
                    lat = lat,
                    lng = lng,
                    holesCount = holesCount,
                    diameter = diameter,
                    depth = depth,
                    imageUrl = imagePart,
                    segmentationPercentage = segmentationPercentage
                )

                if (response.isSuccessful) {
                    setFragmentResult("update_request", Bundle().apply {
                        putBoolean("isUpdated", true)
                    })
                    Toast.makeText(
                        requireContext(),
                        "Laporan berhasil diperbarui",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().popBackStack()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Gagal memperbarui laporan: ${response.message()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchAndPopulateReport(reportId: String) {
        lifecycleScope.launch {
            try {
                val token = UserPreference.getInstance(requireContext()).getToken()
                val apiService = ApiConfig.getApiService(token)
                val response = apiService.getReportById(reportId)

                if (response.isSuccessful) {
                    response.body()?.let { populateUI(it) }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Gagal mengambil laporan: ${response.message()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

//    private fun classifySeverity(diameter: Float, depth: Float): String {
//        val row = when {
//            depth < 25 -> 1
//            depth in 25.0..49.9 -> 2
//            else -> 3
//        }
//
//        val col = when {
//            diameter < 200 -> 1
//            diameter in 200.0..449.9 -> 2
//            else -> 3
//        }
//
//        val matrix = mapOf(
//            "1,1" to "Rendah", "1,2" to "Rendah", "1,3" to "Sedang",
//            "2,1" to "Rendah", "2,2" to "Sedang", "2,3" to "Tinggi",
//            "3,1" to "Sedang", "3,2" to "Sedang", "3,3" to "Tinggi"
//        )
//
//        return matrix["$row,$col"] ?: "Tidak diketahui"
//    }

    private fun updateSeverityTextView() {
//        val diameter = binding.tvDiameterUpdateReport.text.toString().toFloatOrNull() ?: 0f
//        val depth = binding.tvDepthUpdateReport.text.toString().toFloatOrNull() ?: 0f
//        val severity = classifySeverity(diameter, depth)
//        binding.tvSeverityUpdateReport.text = "Tingkat Keparahan\n$severity"

        imageBitmap?.let { bitmap ->
            lifecycleScope.launch(Dispatchers.Default) {
                val maskOutput = unetHelper.runInference(bitmap)
                val (severity, percent) = unetHelper.classifySeverityByMask(maskOutput)
                withContext(Dispatchers.Main) {
                    segmentationPercentageValue = percent.toFloat()
                    binding.tvSeverityUpdateReport.text = "Tingkat Keparahan: $severity"
                    binding.tvSegmentationPercentageUpdateReport.text = "Persentase Segmentasi: %.2f%%".format(percent)
                }
            }
        } ?: run {
            segmentationPercentageValue = null
            binding.tvSeverityUpdateReport.text = "Tingkat Keparahan: -"
            binding.tvSegmentationPercentageUpdateReport.text = "Persentase Segmentasi: -"
        }
    }

    private val requestLocationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                locationHelper.getMyLocation(requestLocationPermissionLauncher)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permission denied. Unable to access location.",
                    Toast.LENGTH_SHORT
                ).show()
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
        // Convert custom ObjectDetection to TensorFlow Lite Detection
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

        // Display the number of potholes detected
        val potholeCount = results.size
        Toast.makeText(
            requireContext(),
            "Lubang Jalan Terdeteksi : $potholeCount",
            Toast.LENGTH_SHORT
        ).show()

        // Update the pothole count in the TextView
        binding.tvHolesCountUpdateReport.setText(potholeCount.toString())

        // Update the outputImageView with the detected image
        binding.ivResultImage.setImageBitmap(imageBitmap)

        // Update the DetectionOverlayView with results
        binding.ivResultImage.post {
            val overlayWidth = binding.ivResultImage.width
            val overlayHeight = binding.ivResultImage.height

            binding.staticOverlayView.setResults(tfliteResults, overlayWidth, overlayHeight)
        }
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
                binding.btnAnalyzeUpdateReport.isEnabled = true
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

        val holesCount = detections?.size ?: 0
        binding.tvHolesCountUpdateReport.setText(holesCount.toString())

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

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return file
    }

    private fun showToast(message: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }


    private fun startCamera() {
        val photoURI: Uri = getImageUri(requireContext())
        currentPhotoPath = photoURI.toString()
        launcherIntentCamera.launch(photoURI)
    }

    private fun startGallery() {
        launcherGallery.launch("image/*")
    }

    override fun onStart() {
        super.onStart()
        binding.mapViewUpdateReport.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapViewUpdateReport.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapViewUpdateReport.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapViewUpdateReport.onLowMemory()
    }

    override fun onStop() {
        super.onStop()
        binding.mapViewUpdateReport.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}