// AccountViewModel.kt
package com.xeraphion.laporbang.ui.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.xeraphion.laporbang.UserPreference
import com.xeraphion.laporbang.api.ApiConfig
import com.xeraphion.laporbang.response.GetUserResponse
import kotlinx.coroutines.launch

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val _user = MutableLiveData<GetUserResponse>()
    val user: LiveData<GetUserResponse> = _user

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val userPreference = UserPreference.getInstance(application)

    fun fetchUserData() {
        viewModelScope.launch {
            val token = userPreference.getToken()
            if (token != null) {
                try {
                    val apiService = ApiConfig.getApiService(token)
                    val response = apiService.getUser()
                    if (response.isSuccessful) {
                        _user.value = response.body()
                    } else {
                        _error.value = "Gagal memuat data user: ${response.message()}"
                    }
                } catch (e: Exception) {
                    _error.value = "Terjadi kesalahan: ${e.localizedMessage}"
                }
            } else {
                _error.value = "Token tidak ditemukan"
            }
        }
    }
}
