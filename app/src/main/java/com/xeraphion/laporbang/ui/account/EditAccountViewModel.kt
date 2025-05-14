package com.xeraphion.laporbang.ui.account

import androidx.lifecycle.*
import com.xeraphion.laporbang.api.ApiConfig
import com.xeraphion.laporbang.response.UpdateAccountResponse
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response

class EditAccountViewModel(private val token: String) : ViewModel() {

    private val _editProfileResponse = MutableLiveData<Response<UpdateAccountResponse>>()
    val editProfileResponse: LiveData<Response<UpdateAccountResponse>> = _editProfileResponse

    private val _isProfileUpdated = MutableLiveData<Boolean>()
    val isProfileUpdated: LiveData<Boolean> = _isProfileUpdated

    fun editProfile(
        username: RequestBody?,
        newPassword: RequestBody? = null,
        currentPassword: RequestBody? = null,
        profileImage: MultipartBody.Part? = null
    ) {
        viewModelScope.launch {
            try {
                val apiService = ApiConfig.getApiService(token)
                val response = apiService.updateAccount(username, newPassword, currentPassword, profileImage)
                _editProfileResponse.value = response
                _isProfileUpdated.value = response.isSuccessful
            } catch (e: Exception) {
                e.printStackTrace()
                _isProfileUpdated.value = false
            }
        }
    }
}
