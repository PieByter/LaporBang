package com.xeraphion.laporbang.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.xeraphion.laporbang.UserPreference
import com.xeraphion.laporbang.api.ApiConfig
import com.xeraphion.laporbang.api.ApiService
import kotlinx.coroutines.runBlocking

class HomeViewModelFactory(
    private val userPreference: UserPreference,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            val token = runBlocking { userPreference.getToken() ?: "" } // karena suspend function
            val apiService: ApiService = ApiConfig.getApiService(token)
            return HomeViewModel(userPreference, apiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
