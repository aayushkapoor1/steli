package com.steli.app.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// Backend base URL: 10.0.2.2 is host machine from emulator
private const val BASE_URL = "http://10.0.2.2:8000/"

// OkHttp interceptor that adds auth token to every request
private val authInterceptor = Interceptor { chain ->
    val original = chain.request()
    val token = AuthManager.getToken()
    val request = if (token != null) {
        original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    } else {
        original
    }
    chain.proceed(request)
}

private val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(authInterceptor)
    .build()

private val retrofit = Retrofit.Builder()
    .baseUrl(BASE_URL)
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val steliApi: SteliApi = retrofit.create(SteliApi::class.java)

interface SteliApi {

    // ── Spots ─────────────────────────────────────────────────────

    @GET("api/spots")
    suspend fun getSpots(@Query("q") query: String = ""): List<StudySpot>

    // ── Rankings ──────────────────────────────────────────────────

    @PUT("api/rankings")
    suspend fun setRankings(@Body request: SetRankingsRequest): List<RankedSpot>

    @GET("api/rankings/matchup")
    suspend fun getMatchup(): MatchupResponse

    @GET("api/rankings/user/{username}")
    suspend fun getUserRankings(@Path("username") username: String): List<RankedSpot>
}
