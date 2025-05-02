package com.xeraphion.laporbang.response

data class LoginResponse(
    val token: String? = null,
    val user: User? = null
)
