package com.xeraphion.laporbang.ui.login


import androidx.lifecycle.ViewModel

class LoginViewModel(private val repository: LoginRepository) : ViewModel() {
    suspend fun login(email: String, password: String) = repository.login(email, password)
    suspend fun validateToken(token: String) = repository.validateToken(token)
}
