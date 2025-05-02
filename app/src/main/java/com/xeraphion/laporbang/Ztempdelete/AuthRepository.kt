package com.xeraphion.laporbang.Ztempdelete

import com.xeraphion.laporbang.api.ApiService
import com.xeraphion.laporbang.response.LoginResponse
import com.xeraphion.laporbang.response.RegisterResponse
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File

class AuthRepository(private val apiService: ApiService) {

    suspend fun login(email: String, password: String): Response<LoginResponse> {
        return apiService.login(email, password)
    }

    suspend fun register(
        username: String,
        email: String,
        password: String,
        confirmPassword: String,
        profileFile: File?
    ): Response<RegisterResponse> {
        val requestUsername = username.toRequestBody("text/plain".toMediaTypeOrNull())
        val requestEmail = email.toRequestBody("text/plain".toMediaTypeOrNull())
        val requestPassword = password.toRequestBody("text/plain".toMediaTypeOrNull())
        val requestConfirm = confirmPassword.toRequestBody("text/plain".toMediaTypeOrNull())

        val imagePart = profileFile?.let {
            val requestFile = it.asRequestBody("image/*".toMediaTypeOrNull())
            MultipartBody.Part.createFormData("profileImage", it.name, requestFile)
        }

        return apiService.register(
            username = requestUsername,
            email = requestEmail,
            password = requestPassword,
            confirmPassword = requestConfirm,
            profileImage = imagePart
        )
    }
}