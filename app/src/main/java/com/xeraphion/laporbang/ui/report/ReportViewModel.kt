package com.xeraphion.laporbang.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeraphion.laporbang.response.CreateResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody

class ReportViewModel(private val repository: ReportRepository) : ViewModel() {

    private val _reportState = MutableStateFlow< CreateResponse?>(null)
    val reportState: StateFlow<CreateResponse?> get() = _reportState

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> get() = _errorState

    fun submitReport(
        titles: RequestBody,
        lat: RequestBody,
        lng: RequestBody,
        diameter: RequestBody,
        depth: RequestBody,
        holesCount: RequestBody,
        image: MultipartBody.Part?,
        segmentationPercentage: RequestBody,
    ) {
        viewModelScope.launch {
            try {
                val response = repository.submitReport(
                    titles, lat, lng, diameter, depth, holesCount, image, segmentationPercentage
                )
                if (response.isSuccessful) {
                    _reportState.value = response.body()
                } else {
                    _errorState.value = response.errorBody()?.string() ?: "Unknown error"
                }
            } catch (e: Exception) {
                _errorState.value = e.message
            }
        }
    }
}