package com.xeraphion.laporbang.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
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
import com.xeraphion.laporbang.databinding.FragmentDetailHomeBinding
import com.xeraphion.laporbang.helper.LocationHelper
import com.xeraphion.laporbang.helper.formatDate
import com.xeraphion.laporbang.response.Location
import com.xeraphion.laporbang.response.ReportsResponseItem
import com.xeraphion.laporbang.ui.maps.BitmapHelper
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class DetailHomeFragment : Fragment() {

    private var _binding: FragmentDetailHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var locationHelper: LocationHelper
    private lateinit var googleMap: GoogleMap

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDetailHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val report = arguments?.getParcelable<ReportsResponseItem>("report")
        lifecycleScope.launch {
            report?.let {
                setupReportDetails(it)
                setupMapView(it.location, savedInstanceState)
                setupDeleteButton(it.id)
            }

            binding.mapView.getMapAsync { map ->
                googleMap = map
                locationHelper = LocationHelper(requireContext(), googleMap)
                locationHelper.getMyLocation(requestLocationPermissionLauncher)
            }

        }

        binding.btnUpdateReport.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val currentUserId = UserPreference.getInstance(requireContext()).getUserId()
                    val isAdmin = UserPreference.getInstance(requireContext()).isAdmin()

                    if (currentUserId != report?.userId && !isAdmin) {
                        Toast.makeText(
                            requireContext(),
                            "Anda tidak diizinkan mengubah laporan ini!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val reportId = report?.id
                        val bundle = Bundle().apply {
                            putString("reportId", reportId)
                        }
                        findNavController().navigate(
                            R.id.action_nav_detail_to_nav_update_report,
                            bundle
                        )
                    }
                } catch (e: CancellationException) {
                    Toast.makeText(requireContext(), "Operasi dibatalkan.", Toast.LENGTH_SHORT)
                        .show()
                } catch (e: Exception) {
                    Toast.makeText(
                        requireContext(),
                        "Terjadi kesalahan: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val reportId = arguments?.getParcelable<ReportsResponseItem>("report")?.id
        reportId?.let {
            fetchReportDetails(it) { updatedReport ->
                updatedReport?.let { report ->
                    setupMapView(report.location, null)
                }
            }
        }

        parentFragmentManager.setFragmentResultListener("update_request", viewLifecycleOwner) { _, bundle ->
            val isUpdated = bundle.getBoolean("isUpdated", false)
            if (isUpdated) {
                val reportId = arguments?.getParcelable<ReportsResponseItem>("report")?.id
                reportId?.let {
                    fetchReportDetails(it) { updatedReport ->
                        updatedReport?.let { report ->
                            setupReportDetails(report)
                            setupMapView(report.location, null)
                        }
                    }
                }
            }
        }
    }

    private fun setupReportDetails(report: ReportsResponseItem) {
        binding.tvDetailTitles.text = report.titles
        binding.tvDetailUser.text = report.username
        binding.tvDetailCoordinates.text =
            "Koordinat : ${report.location?.lat}, ${report.location?.lng}"
        binding.tvDetailSeverity.text = "Tingkat Keparahan : ${report.severity}"
        binding.tvDetailSegmentationPercentage.text = "Persentase Segmentasi : ${String.format("%.2f", report.segmentationPercentage ?: 0.0)}%"
        binding.tvHolesCount.text = "Jumlah Lubang : ${report.holesCount} lubang"
        binding.tvDetailDiameter.text = "Diameter Lubang : ${report.diameter} mm"
        binding.tvDetailDepth.text = "Kedalaman Lubang : ${report.depth} mm"

        val isUpdated = !report.updatedAt.isNullOrEmpty() && report.updatedAt != report.createdAt
        binding.tvDetailTimes.text = if (isUpdated) {
            formatDate(report.updatedAt)
        } else {
            formatDate(report.createdAt)
        }
        binding.ivDetailTimes.setImageResource(if (isUpdated) R.drawable.ic_update_report else R.drawable.ic_upload_report)

        Glide.with(requireContext())
            .load(report.imageUrl)
//            .centerCrop()
//            .transform(RoundedCorners(12))
            .placeholder(R.drawable.ic_image)
            .into(binding.ivDetailPotholes)

    }

    private fun setupMapView(location: Location?, savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync { map ->
            googleMap = map // Initialize googleMap here

            location?.let {
                val latLng = LatLng(it.lat ?: 0.0, it.lng ?: 0.0)
                val bitmap =
                    BitmapHelper.vectorToBitmap(requireContext(), R.drawable.ic_marker_maps)
                val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap)

                googleMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Lokasi Lubang Jalan")
                        .icon(bitmapDescriptor)
                )
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            }

            // Enable map UI settings
            googleMap.uiSettings.apply {
                isScrollGesturesEnabled = true
                isZoomGesturesEnabled = true
                isTiltGesturesEnabled = true
                isRotateGesturesEnabled = true
                isZoomControlsEnabled = true
                isIndoorLevelPickerEnabled = true
                isCompassEnabled = true
                isMapToolbarEnabled = true
            }

        }
    }

    private fun setupDeleteButton(reportId: String?) {
        binding.btnDeleteReport.setOnClickListener {
            reportId?.let { deleteReport(it) }
        }
    }

    private fun deleteReport(reportId: String) {
        lifecycleScope.launch {
            val token = UserPreference.getInstance(requireContext()).getToken()
            val apiService = ApiConfig.getApiService(token)
            val viewModel = HomeViewModel(
                UserPreference.getInstance(requireContext()),
                apiService
            )
            try {
                val response = apiService.deleteReport(reportId)
                if (response.isSuccessful) {
                    Toast.makeText(
                        requireContext(),
                        "Laporan berhasil dihapus!",
                        Toast.LENGTH_SHORT
                    ).show()
                    viewModel.notifyDataChanged()
                    findNavController().popBackStack()
                } else if (response.code() == 403) {
                    Toast.makeText(
                        requireContext(),
                        "Anda tidak dapat menghapus laporan ini!",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Gagal menghapus laporan: ${response.message()}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    requireContext(),
                    "Terjadi kesalahan saat menghapus laporan!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun fetchReportDetails(reportId: String, callback: (ReportsResponseItem?) -> Unit) {
        lifecycleScope.launch {
            try {
                val token = UserPreference.getInstance(requireContext()).getToken()
                val apiService = ApiConfig.getApiService(token)
                val response = apiService.getReportById(reportId)

                if (response.isSuccessful) {
                    callback(response.body())
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Failed to fetch report: ${response.message()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    callback(null)
                }
            } catch (e: Exception) {
                e.printStackTrace()
//                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                callback(null)
            }
        }
    }


    private val requestLocationPermissionLauncher: ActivityResultLauncher<String> =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                locationHelper.getMyLocation(requestLocationPermissionLauncher)
            } else {
                Toast.makeText(
                    requireContext(),
                    "Izin ditolak, Tidak dapat mengakses lokasi!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }


    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()

        val reportId = arguments?.getParcelable<ReportsResponseItem>("report")?.id
        reportId?.let {
            fetchReportDetails(it) { updatedReport ->
                updatedReport?.let { report ->
                    setupMapView(report.location, null)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}