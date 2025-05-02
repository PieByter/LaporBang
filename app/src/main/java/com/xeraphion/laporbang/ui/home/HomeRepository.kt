package com.xeraphion.laporbang.ui.home

import com.xeraphion.laporbang.UserPreference
import com.xeraphion.laporbang.api.ApiConfig
import com.xeraphion.laporbang.api.ApiService
import com.xeraphion.laporbang.response.ReportsResponseItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeRepository(private val userPreference: UserPreference) {

    private fun getApiService(token: String): ApiService {
        return ApiConfig.getApiService(token)
    }

    suspend fun getReports(): List<ReportsResponseItem> {
        return withContext(Dispatchers.IO) {
            try {
                val token = userPreference.getToken()
                if (token.isNullOrEmpty()) return@withContext emptyList()

                val apiService = getApiService(token)
                val response = apiService.getAllReports()

                if (response.isSuccessful) {
                    response.body()?.sortedByDescending { it.createdAt } ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun getReportsByUserId(userId: String): List<ReportsResponseItem> {
        return withContext(Dispatchers.IO) {
            try {
                val token = userPreference.getToken()
                if (token.isNullOrEmpty()) return@withContext emptyList()

                val apiService = getApiService(token)
                val response = apiService.getAllReports() // Adjust API if needed to filter by userId

                if (response.isSuccessful) {
                    response.body()?.filter { it.userId == userId }?.sortedByDescending { it.createdAt } ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }}
