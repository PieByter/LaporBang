package com.xeraphion.laporbang.ui.register


import com.xeraphion.laporbang.api.ApiService
import okhttp3.MultipartBody
import okhttp3.RequestBody

class RegisterRepository(private val apiService: ApiService) {
    suspend fun registerUser(
        username: RequestBody,
        email: RequestBody,
        password: RequestBody,
        confirmPassword: RequestBody,
        imagePart: MultipartBody.Part?
    ) = apiService.register(username, email, password, confirmPassword, imagePart)
}