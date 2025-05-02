package com.xeraphion.laporbang.ui.login

import com.xeraphion.laporbang.api.ApiConfig

class LoginRepository {
    suspend fun login(email: String, password: String) = ApiConfig.getApiService().login(email, password)
    suspend fun validateToken(token: String) = ApiConfig.getApiService(token).validateToken()
}
