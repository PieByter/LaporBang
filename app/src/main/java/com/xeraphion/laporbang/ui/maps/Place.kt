package com.xeraphion.laporbang.ui.maps

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterItem
import com.xeraphion.laporbang.response.ReportsResponseItem

data class Place(
    val name: String,
    val latLng: LatLng,
    val address: String,
    val severity: String,
    val report: ReportsResponseItem
) : ClusterItem {
    override fun getPosition(): LatLng = latLng
    override fun getTitle(): String = name
    override fun getSnippet(): String = address
}