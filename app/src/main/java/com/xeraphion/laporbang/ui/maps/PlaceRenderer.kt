package com.xeraphion.laporbang.ui.maps

import android.content.Context
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.xeraphion.laporbang.R

class PlaceRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<Place>,
) : DefaultClusterRenderer<Place>(context, map, clusterManager) {

    private val clusterItemMarkerMap = HashMap<Place, Marker>()

    private val mapsIcon: BitmapDescriptor by lazy {
        BitmapHelper.vectorToBitmapDescriptor(
            context,
            R.drawable.ic_marker_maps
        )
    }

    override fun onBeforeClusterItemRendered(item: Place, markerOptions: MarkerOptions) {
        markerOptions.title(item.name)
            .position(item.latLng)
//            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Default red marker
            .icon(mapsIcon)
    }

    override fun onBeforeClusterRendered(cluster: Cluster<Place>, markerOptions: MarkerOptions) {
        super.onBeforeClusterRendered(cluster, markerOptions) // Use default cluster rendering
    }

    override fun onClusterItemRendered(clusterItem: Place, marker: Marker) {
        marker.tag = clusterItem.report
        clusterItemMarkerMap[clusterItem] =
            marker // Set the tag to the original ReportsResponseItem
    }

    override fun getMarker(clusterItem: Place): Marker? {
        return clusterItemMarkerMap[clusterItem]
    }
    object BitmapHelper {
        fun vectorToBitmapDescriptor(context: Context, drawableId: Int): BitmapDescriptor {
            val vectorDrawable = ContextCompat.getDrawable(context, drawableId)!!
            val bitmap = createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
            val canvas = Canvas(bitmap)
            vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
            vectorDrawable.draw(canvas)
            return BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

}