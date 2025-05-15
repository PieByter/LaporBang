// Complete HomeViewModel.kt implementation
package com.xeraphion.laporbang.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeraphion.laporbang.UserPreference
import com.xeraphion.laporbang.api.ApiService
import com.xeraphion.laporbang.response.ReportsResponseItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val userPreference: UserPreference,
    private val apiService: ApiService,
) : ViewModel() {

    private val _allReports = MutableStateFlow<List<ReportsResponseItem>>(emptyList())
    private val _reports = MutableStateFlow<List<ReportsResponseItem>>(emptyList())
    val reports: StateFlow<List<ReportsResponseItem>> get() = _reports

    private val _dataChangedEvent = MutableSharedFlow<Unit>()
    val dataChangedEvent: SharedFlow<Unit> get() = _dataChangedEvent

    // Filter state properties
    private val _isFilteredById = MutableStateFlow(false)
    val isFilteredById: StateFlow<Boolean> = _isFilteredById

    private val _currentSort = MutableStateFlow(SortType.DATE)
    val currentSort: StateFlow<SortType> = _currentSort

    private val _isAscending = MutableStateFlow(false)
    val isAscending: StateFlow<Boolean> = _isAscending

    enum class SortType { DATE, SEVERITY, HOLES }

    // Methods to update filter state
    fun setFilterById(filtered: Boolean) {
        _isFilteredById.value = filtered
        if (filtered) {
            fetchReportsByUserId()
        } else {
            fetchReports()
        }
    }

    fun setSortType(sortType: SortType) {
        _currentSort.value = sortType
    }

    fun setSortDirection(ascending: Boolean) {
        _isAscending.value = ascending
    }

    fun fetchReports() {
        viewModelScope.launch {
            val token = userPreference.getToken()
            if (!token.isNullOrEmpty()) {
                val response = apiService.getAllReports()
                if (response.isSuccessful) {
                    val sortedReports = response.body()?.sortedByDescending { it.createdAt } ?: emptyList()
                    _allReports.value = sortedReports
                    _reports.value = sortedReports
                }
            }
        }
    }

    fun fetchReportsByUserId() {
        viewModelScope.launch {
            val userId = userPreference.getUserId()
            if (!userId.isNullOrEmpty()) {
                val token = userPreference.getToken()
                if (!token.isNullOrEmpty()) {
                    val filteredReports = withContext(Dispatchers.Default) {
                        _allReports.value.filter { it.userId == userId }.sortedByDescending { it.createdAt }
                    }
                    _reports.value = filteredReports
                } else {
                    _reports.value = emptyList()
                }
            } else {
                _reports.value = emptyList()
            }
        }
    }

    fun notifyDataChanged() {
        viewModelScope.launch {
            // Use current filter state when refreshing
            if (_isFilteredById.value) {
                fetchReportsByUserId()
            } else {
                fetchReports()
            }
            _dataChangedEvent.emit(Unit)
        }
    }
}