//package com.xeraphion.laporbang.ui.maps
//
//import android.annotation.SuppressLint
//import android.app.PendingIntent
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.os.Bundle
//import android.view.LayoutInflater
//import android.view.View
//import android.view.ViewGroup
//import android.widget.Toast
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.core.content.ContextCompat
//import androidx.fragment.app.Fragment
//import androidx.lifecycle.ViewModelProvider
//import androidx.lifecycle.lifecycleScope
//import com.google.android.gms.location.Geofence
//import com.google.android.gms.location.GeofencingClient
//import com.google.android.gms.location.LocationServices
//import com.google.android.gms.maps.CameraUpdateFactory
//import com.google.android.gms.maps.GoogleMap
//import com.google.android.gms.maps.OnMapReadyCallback
//import com.google.android.gms.maps.SupportMapFragment
//import com.google.android.gms.maps.model.LatLng
//import com.google.maps.android.clustering.ClusterManager
//import com.xeraphion.laporbang.R
//import com.xeraphion.laporbang.UserPreference
//import com.xeraphion.laporbang.api.ApiConfig
//import com.xeraphion.laporbang.databinding.FragmentMapsBinding
//import com.xeraphion.laporbang.response.ReportsResponseItem
//import kotlinx.coroutines.launch
//
//class MapsFragment : Fragment(), OnMapReadyCallback {
//
//    private var _binding: FragmentMapsBinding? = null
//    private val binding get() = _binding!!
//
//    private lateinit var mapsViewModel: MapsViewModel
//    private lateinit var googleMap: GoogleMap
//    private lateinit var geofencingClient: GeofencingClient
//    private lateinit var userPreference: UserPreference
//    private lateinit var clusterManager: ClusterManager<Place>
//
//    private val geofencePendingIntent by lazy {
//        val intent = Intent(requireContext(), GeofenceBroadcastReceiver::class.java)
//        intent.action = GeofenceBroadcastReceiver.ACTION_GEOFENCE_EVENT
//        PendingIntent.getBroadcast(
//            requireContext(),
//            0,
//            intent,
//            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
//        )
//    }
//
//    override fun onCreateView(
//        inflater: LayoutInflater,
//        container: ViewGroup?,
//        savedInstanceState: Bundle?,
//    ): View {
//        _binding = FragmentMapsBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        userPreference = UserPreference.getInstance(requireContext())
//
//        viewLifecycleOwner.lifecycleScope.launch {
//            val token = userPreference.getToken()
//            val apiService = ApiConfig.getApiService(token)
//
//            val factory = MapsViewModelFactory(apiService)
//            mapsViewModel = ViewModelProvider(this@MapsFragment, factory)[MapsViewModel::class.java]
//
//            val mapFragment =
//                childFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
//            mapFragment.getMapAsync(this@MapsFragment)
//
//            mapsViewModel.reports.observe(viewLifecycleOwner) { reports ->
//                addClusterItems(reports)
//            }
//
//            mapsViewModel.errorMessage.observe(viewLifecycleOwner) { message ->
//                if (!message.isNullOrEmpty()) {
//                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
//                }
//            }
//
//            mapsViewModel.fetchReports()
//        }
//    }
//
//    override fun onMapReady(map: GoogleMap) {
//        googleMap = map
//        val defaultLocation = LatLng(-0.7893, 113.9213) // Indonesia center
//        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 5f))
//
//        // Initialize clusterManager first
//        clusterManager = ClusterManager(requireContext(), googleMap)
//
//        // Now pass clusterManager to PlaceRenderer
//        val placeRenderer = PlaceRenderer(requireContext(), googleMap, clusterManager)
//        clusterManager.renderer = placeRenderer
//
//        // Set up the custom InfoWindowAdapter
//        val markerInfoWindowAdapter = MarkerInfoWindowAdapter(requireContext())
//        googleMap.setInfoWindowAdapter(markerInfoWindowAdapter)
//        clusterManager.markerCollection.setInfoWindowAdapter(markerInfoWindowAdapter)
//
//        // Set listeners for clusterManager
//        googleMap.setOnCameraIdleListener(clusterManager)
//        googleMap.setOnMarkerClickListener(clusterManager)
//
//        clusterManager.setOnClusterItemClickListener { clusterItem ->
//            val marker = placeRenderer.getMarker(clusterItem) // Use the getMarker method
//            marker?.showInfoWindow()
//            true
//        }
//
//        // Enable map UI settings
//        googleMap.uiSettings.isZoomControlsEnabled = true
//        googleMap.uiSettings.isIndoorLevelPickerEnabled = true
//        googleMap.uiSettings.isCompassEnabled = true
//        googleMap.uiSettings.isMapToolbarEnabled = true
//
//        getMyLocation()
//        geofencingClient = LocationServices.getGeofencingClient(requireContext())
//    }
//
//    private val requestPermissionLauncher =
//        registerForActivityResult(
//            ActivityResultContracts.RequestPermission()
//        ) { isGranted: Boolean ->
//            if (isGranted) {
//                getMyLocation()
//            }
//            else {
//                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT).show()
//            }
//        }
//
//    private fun getMyLocation() {
//        if (ContextCompat.checkSelfPermission(
//                requireContext(),
//                android.Manifest.permission.ACCESS_FINE_LOCATION
//            ) == PackageManager.PERMISSION_GRANTED
//        ) {
//            googleMap.isMyLocationEnabled = true
//        } else {
//            requestPermissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun addClusterItems(reports: List<ReportsResponseItem>) {
//        clusterManager.clearItems()
//        reports.forEach { report ->
//            val latLng = LatLng(report.location?.lat ?: 0.0, report.location?.lng ?: 0.0)
//            val place = Place(
//                name = report.titles ?: "No Title",
//                latLng = latLng,
//                address = "Lubang: ${report.holesCount ?: "N/A"}",
//                severity = "Keparahan: ${report.severity ?: "N/A"}",
//                report = report
//            )
//            clusterManager.addItem(place)
//
//            // Set the tag to the original ReportsResponseItem
//            clusterManager.markerCollection.setOnMarkerClickListener { marker ->
//                marker.tag = report
//                false
//            }
//
//            val geofenceHelper = GeofenceHelper(requireContext())
//            geofenceHelper.addGeofence(
//                id = report.id.toString(),
//                lat = latLng.latitude,
//                lng = latLng.longitude,
//                radius = 10f, // 10 meters
//                transitionType = Geofence.GEOFENCE_TRANSITION_ENTER
//            )
//        }
//        clusterManager.cluster()
//    }
//
//    private fun showToast(text: String) {
//        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//    }
//}