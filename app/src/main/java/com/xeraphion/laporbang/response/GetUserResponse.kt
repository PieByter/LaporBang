package com.xeraphion.laporbang.response

data class GetUserResponse(
	val role: String? = null,
	val id: String? = null,
	val profileImage: String? = null,
	val email: String? = null,
	val username: String? = null,
	val createdAt: String? = null,
)

