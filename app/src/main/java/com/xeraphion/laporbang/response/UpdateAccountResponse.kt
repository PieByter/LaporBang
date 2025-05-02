package com.xeraphion.laporbang.response

data class UpdateAccountResponse(
	val message: String? = null,
	val user: User? = null
)

data class UserAccount(
	val password: Boolean? = null,
	val id: String? = null,
	val profileImage: String? = null,
	val email: String? = null,
	val username: String? = null
)

