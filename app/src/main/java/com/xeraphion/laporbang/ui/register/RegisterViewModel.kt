package com.xeraphion.laporbang.ui.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.json.JSONObject

class RegisterViewModel(private val repository: RegisterRepository) : ViewModel() {
    fun registerUser(
        username: RequestBody,
        email: RequestBody,
        password: RequestBody,
        confirmPassword: RequestBody,
        imagePart: MultipartBody.Part?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val response = repository.registerUser(username, email, password, confirmPassword, imagePart)
                if (response.isSuccessful) {
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = try {
                        JSONObject(errorBody ?: "").optString("error", response.message())
                    } catch (e: Exception) {
                        response.message()
                    }
                    onError(errorMessage)
                }
            } catch (e: Exception) {
                onError(e.message ?: "An error occurred")
            }
        }
    }
}