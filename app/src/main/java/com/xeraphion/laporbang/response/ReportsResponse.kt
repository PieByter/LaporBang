package com.xeraphion.laporbang.response

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ReportsResponse(
	val reportsResponse: List<ReportsResponseItem?>? = null,
) : Parcelable

@Parcelize
data class Location(
	val lng: Double? = null,
	val lat: Double? = null
) : Parcelable

@Parcelize
data class ReportsResponseItem(
	val severity: String? = null,
	val createdAt: String? = null,
	val updatedAt: String? = null,
	val depth: Float? = null,
	val diameter: Float? = null,
	val holesCount: Int? = null,
	val imageUrl: String? = null,
	val v: Int? = null,
	val location: Location? = null,
	val id: String? = null,
	val titles: String? = null,
	val userId: String? = null,
	val username: String? = null,
) : Parcelable
