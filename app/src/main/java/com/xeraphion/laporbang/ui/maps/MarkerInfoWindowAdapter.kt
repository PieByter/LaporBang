package com.xeraphion.laporbang.ui.maps

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.bumptech.glide.Glide
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.xeraphion.laporbang.R
import com.xeraphion.laporbang.databinding.MarkerInfoContentsBinding
import com.xeraphion.laporbang.response.ReportsResponseItem

class MarkerInfoWindowAdapter(
    private val context: Context
) : GoogleMap.InfoWindowAdapter {
    override fun getInfoContents(marker: Marker): View? =null

    override fun getInfoWindow(marker: Marker): View? {
        val binding = MarkerInfoContentsBinding.inflate(LayoutInflater.from(context), null, false)

        val data = marker.tag as? ReportsResponseItem
        data?.let {
            Glide.with(context)
                .load(it.imageUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .into(binding.ivMapsMarker)

            binding.tvMapsTitles.text = it.titles ?: "-"
            binding.tvMapsHoles.text = "Lubang: ${it.holesCount ?: "N/A"}"
            binding.tvMapsSeverity.text = "Keparahan: ${it.severity ?: "N/A"}"
        }

        return binding.root
    }

}