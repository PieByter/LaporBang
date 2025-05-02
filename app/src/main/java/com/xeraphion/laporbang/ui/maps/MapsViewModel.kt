package com.xeraphion.laporbang.ui.maps

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeraphion.laporbang.api.ApiService
import com.xeraphion.laporbang.response.ReportsResponseItem
import kotlinx.coroutines.launch

class MapsViewModel(private val apiService: ApiService) : ViewModel() {

    private val _reports = MutableLiveData<List<ReportsResponseItem>>()
    val reports: LiveData<List<ReportsResponseItem>> = _reports

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage

    fun fetchReports() {
        viewModelScope.launch {
            try {
                val response = apiService.getAllReports()
                if (response.isSuccessful) {
                    _reports.value = response.body()
                } else {
                    _errorMessage.value = "Error: ${response.message()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load reports: ${e.message}"
            }
        }
    }
}
