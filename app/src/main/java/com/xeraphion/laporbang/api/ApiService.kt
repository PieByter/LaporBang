package com.xeraphion.laporbang.api

import com.xeraphion.laporbang.response.CreateResponse
import com.xeraphion.laporbang.response.GetUserResponse
import com.xeraphion.laporbang.response.LoginResponse
import com.xeraphion.laporbang.response.RegisterResponse
import com.xeraphion.laporbang.response.ReportsResponseItem
import com.xeraphion.laporbang.response.UpdateAccountResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {
    @Multipart
    @POST("register")
    suspend fun register(
        @Part("username") username: RequestBody,
        @Part("email") email: RequestBody,
        @Part("password") password: RequestBody,
        @Part("confirmPassword") confirmPassword: RequestBody,
        @Part profileImage: MultipartBody.Part?,
    ): Response<RegisterResponse>

    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String,
    ): Response<LoginResponse>

    @GET("users/me")
    suspend fun getUser(): Response<GetUserResponse>

    @Multipart
    @PUT("users/update")
    suspend fun updateAccount(
        @Part("username") username: RequestBody?,
        @Part("password") newPassword: RequestBody?,
        @Part("currentPassword") currentPassword: RequestBody?,
        @Part profileImage: MultipartBody.Part?
    ): Response<UpdateAccountResponse>

    @GET("reports")
    suspend fun getAllReports(): Response<List<ReportsResponseItem>>

    @GET("validate-token")
    suspend fun validateToken(): Response<Void>

    @Multipart
    @POST("reports")
    suspend fun createReport(
        @Part("titles") titles: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lng") lng: RequestBody,
        @Part("diameter") diameter: RequestBody,
        @Part("depth") depth: RequestBody,
        @Part("holesCount") holesCount: RequestBody,
        @Part imageUrl: MultipartBody.Part? = null,
    ): Response<CreateResponse>

    @Multipart
    @PUT("reports/{id}")
    suspend fun updateReport(
        @Path("id") id: String,
        @Part("titles") titles: RequestBody?,
        @Part("lat") lat: RequestBody?,
        @Part("lng") lng: RequestBody?,
        @Part("holesCount") holesCount: RequestBody?,
        @Part("diameter") diameter: RequestBody?,
        @Part("depth") depth: RequestBody?,
        @Part imageUrl: MultipartBody.Part? = null
    ): Response<ReportsResponseItem>

    @DELETE("reports/{id}")
    suspend fun deleteReport(
        @Path("id") id: String,
    ): Response<Unit>

    @GET("reports/{id}")
    suspend fun getReportById(@Path("id") id: String): Response<ReportsResponseItem>

}
