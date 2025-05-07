package com.xeraphion.laporbang.ui.report

import com.xeraphion.laporbang.api.ApiService
import com.xeraphion.laporbang.response.CreateResponse
import com.xeraphion.laporbang.response.Report
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response

class ReportRepository(private val apiService: ApiService) {
    suspend fun submitReport(
        titles: RequestBody,
        lat: RequestBody,
        lng: RequestBody,
        diameter: RequestBody,
        depth: RequestBody,
        holesCount: RequestBody,
        image: MultipartBody.Part?,
        segmentationPercentage: RequestBody,
    ): Response<CreateResponse> {
        return apiService.createReport(
            titles = titles,
            lat = lat,
            lng = lng,
            diameter = diameter,
            depth = depth,
            imageUrl = image,
            holesCount = holesCount,
            segmentationPercentage = segmentationPercentage,
        )
    }
}