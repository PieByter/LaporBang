package com.xeraphion.laporbang.ui.maps

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterManager
import com.xeraphion.laporbang.R
import com.xeraphion.laporbang.UserPreference
import com.xeraphion.laporbang.api.ApiConfig
import com.xeraphion.laporbang.databinding.FragmentMapsBinding
import com.xeraphion.laporbang.response.ReportsResponseItem
import kotlinx.coroutines.launch

class MapsFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!

    private lateinit var mapsViewModel: MapsViewModel
    private lateinit var googleMap: GoogleMap
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var userPreference: UserPreference
    private lateinit var clusterManager: ClusterManager<Place>
    private var locationPermissionGranted = false

    private val geofencePendingIntent by lazy {
        val intent = Intent(requireContext(), GeofenceBroadcastReceiver::class.java)
        intent.action = GeofenceBroadcastReceiver.ACTION_GEOFENCE_EVENT
        PendingIntent.getBroadcast(
            requireContext(),
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

//    @RequiresApi(Build.VERSION_CODES.Q)
//    private val requestPermissionLauncher =
//        registerForActivityResult(
//            ActivityResultContracts.RequestPermission()
//        ) { isGranted: Boolean ->
//            if (isGranted) {
//                getMyLocation()
//            } else {
//                Toast.makeText(requireContext(), "Location permission denied", Toast.LENGTH_SHORT)
//                    .show()
//            }
//        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] == true
            val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

            if (notifGranted) {
                Toast.makeText(requireContext(), "\n" +
                        "Izin pemberitahuan diberikan!", Toast.LENGTH_SHORT).show()
            }
            if (locationGranted) {
                locationPermissionGranted = true
                // Only call getMyLocation if googleMap is initialized
                if (::googleMap.isInitialized) {
                    getMyLocation()
                }
            }
            if (!notifGranted || !locationGranted) {
                Toast.makeText(requireContext(), "Beberapa izin ditolak!", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userPreference = UserPreference.getInstance(requireContext())

        // Create a list to hold permissions we need to request
        val permissionsToRequest = mutableListOf<String>()

        // Check if location permission is already granted
        if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            locationPermissionGranted = true
        }

        // Only request notification permission if it's not already granted
        if (Build.VERSION.SDK_INT >= 33 &&
            !checkPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Only launch permission request if we have permissions to request
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val token = userPreference.getToken()
            val apiService = ApiConfig.getApiService(token)

            val factory = MapsViewModelFactory(apiService)
            mapsViewModel = ViewModelProvider(this@MapsFragment, factory)[MapsViewModel::class.java]

            val mapFragment =
                childFragmentManager.findFragmentById(R.id.map_fragment) as SupportMapFragment
            mapFragment.getMapAsync(this@MapsFragment)

            mapsViewModel.reports.observe(viewLifecycleOwner) { reports ->
                if (checkForegroundAndBackgroundLocationPermission()) {
                    addClusterItems(reports)
                }
            }

            mapsViewModel.errorMessage.observe(viewLifecycleOwner) { message ->
                if (!message.isNullOrEmpty()) {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                }
            }
            mapsViewModel.fetchReports()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val defaultLocation = LatLng(3.5952, 98.6722) //Kota Medan
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 11f))

        // Initialize clusterManager and custom renderer
        clusterManager = ClusterManager(requireContext(), googleMap)
        val placeRenderer = PlaceRenderer(requireContext(), googleMap, clusterManager)
        clusterManager.renderer = placeRenderer

        // Set up the custom InfoWindowAdapter
        val markerInfoWindowAdapter = MarkerInfoWindowAdapter(requireContext())
        googleMap.setInfoWindowAdapter(markerInfoWindowAdapter)
        clusterManager.markerCollection.setInfoWindowAdapter(markerInfoWindowAdapter)

        // Set listeners for clusterManager
        googleMap.setOnCameraIdleListener(clusterManager)
        googleMap.setOnMarkerClickListener(clusterManager)

        clusterManager.setOnClusterItemClickListener { clusterItem ->
            val marker = placeRenderer.getMarker(clusterItem) // Use the getMarker method
            marker?.showInfoWindow()

//            val bundle = Bundle().apply {
//                putParcelable("report", clusterItem.report) // Pass the report object
//            }
//            findNavController().navigate(R.id.action_nav_location_to_nav_detail, bundle)
            true
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

        if (locationPermissionGranted) {
            getMyLocation()
        }
        geofencingClient = LocationServices.getGeofencingClient(requireContext())
    }

    private val runningQOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    private val requestBackgroundLocationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                getMyLocation()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Background location permission is required for geofencing.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private val requestLocationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                if (runningQOrLater) {
                    requestBackgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    getMyLocation()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Foreground location permission is required.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkForegroundAndBackgroundLocationPermission(): Boolean {
        val foregroundLocationApproved = checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val backgroundPermissionApproved =
            if (runningQOrLater) {
                checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                true
            }
        return foregroundLocationApproved && backgroundPermissionApproved
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    private fun getMyLocation() {
        if (checkForegroundAndBackgroundLocationPermission()) {
            googleMap.isMyLocationEnabled = true
        } else {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

//    private fun getMyLocation() {
//        if (ContextCompat.checkSelfPermission(
//                requireContext(),
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) == PackageManager.PERMISSION_GRANTED
//        ) {
//            googleMap.isMyLocationEnabled = true
//        } else {
//            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
//        }
//    }

    @SuppressLint("MissingPermission")
    private fun addClusterItems(reports: List<ReportsResponseItem>) {
        clusterManager.clearItems()
        geofencingClient.removeGeofences(geofencePendingIntent).addOnCompleteListener {
            reports.forEach { report ->
                val latLng = LatLng(report.location?.lat ?: 0.0, report.location?.lng ?: 0.0)
                val place = Place(
                    name = report.titles ?: "No Title",
                    latLng = latLng,
                    address = "Lubang: ${report.holesCount ?: "N/A"}",
                    severity = "Keparahan: ${report.severity ?: "N/A"}",
                    report = report
                )
                clusterManager.addItem(place)

                val geofence = Geofence.Builder()
                    .setRequestId(report.id.toString())
                    .setCircularRegion(latLng.latitude, latLng.longitude, 1000f)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .build()

                val geofencingRequest = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofence(geofence)
                    .build()

                geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                    .addOnSuccessListener {
                        Log.d("Geofence", "Geofence added for ${report.titles}")
                    }
                    .addOnFailureListener { exception ->
                        Log.e("Geofence", "Failed to add geofence: ${exception.message}")
                    }
            }
            clusterManager.cluster()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}