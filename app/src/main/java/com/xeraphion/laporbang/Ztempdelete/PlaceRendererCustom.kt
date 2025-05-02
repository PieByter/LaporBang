package com.xeraphion.laporbang.Ztempdelete

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.Cluster
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.xeraphion.laporbang.R
import androidx.core.graphics.createBitmap
import com.xeraphion.laporbang.ui.maps.BitmapHelper
import com.xeraphion.laporbang.ui.maps.Place

class PlaceRendererCustom(
    private val context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<Place>,
) : DefaultClusterRenderer<Place>(context, map, clusterManager) {

    private val customIcon: BitmapDescriptor by lazy {
        val color = ContextCompat.getColor(context, R.color.black)
        val bitmap = BitmapHelper.vectorToBitmap(
            context,
            R.drawable.ic_home,
            color
        )
        BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onBeforeClusterItemRendered(item: Place, markerOptions: MarkerOptions) {
        markerOptions.title(item.name)
            .position(item.latLng)
//            .icon(customIcon)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
    }

    override fun onBeforeClusterRendered(cluster: Cluster<Place>, markerOptions: MarkerOptions) {
        val clusterIcon = createClusterIcon(cluster.size)
        markerOptions.icon(clusterIcon)
    }

    override fun onClusterItemRendered(clusterItem: Place, marker: Marker) {
        marker.tag = clusterItem
    }

    private fun createClusterIcon(clusterSize: Int): BitmapDescriptor {
        val radius = 60 // Circle radius in pixels
        val bitmap = createBitmap(radius * 2, radius * 2)
        val canvas = Canvas(bitmap)

        // Draw circle
        val paint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.cyan) // Circle color
            isAntiAlias = true
        }
        canvas.drawCircle(radius.toFloat(), radius.toFloat(), radius.toFloat(), paint)

        // Draw text
        val textPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.white) // Text color
            textSize = 40f
//            Paint.setTypeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val textBounds = Rect()
        val text = clusterSize.toString()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(
            text,
            radius.toFloat(),
            radius + textBounds.height() / 2f,
            textPaint
        )

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}