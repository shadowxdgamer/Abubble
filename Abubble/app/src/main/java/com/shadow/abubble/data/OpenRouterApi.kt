package com.shadow.abubble.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApi {

    @GET("models")
    suspend fun getModels(): ModelsResponse

    @POST("chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Header("HTTP-Referer") referer: String = "https://abubble.app",
        @Header("X-Title") title: String = "Abubble",
        @Body request: ChatRequest
    ): ChatResponse
}
