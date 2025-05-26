package com.xeraphion.laporbang.response

data class CreateResponse(
	val report: Report? = null,
	val message: String? = null,
)

data class Report(
    val severity: String? = null,
    val holesCount: Int? = null,
    val titles: String? = null,
    val userId: String? = null,
    val createdAt: String? = null,
    val depth: Float? = null,
    val diameter: Float? = null,
    val imageUrl: String? = null,
    val v: Int? = null,
    val location: Location? = null,
    val id: String? = null,
    val username: String? = null,
    val updatedAt: Any? = null,
    val segmentationPercentage: Float? = null,
)

