package com.steli.app.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

/** Backend base URL: 10.0.2.2 is host machine from emulator. */
private const val BASE_URL = "http://10.0.2.2:8000/"

/** OkHttp interceptor that adds the auth token to every request. */
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

    // ── Auth ──────────────────────────────────────────────────────

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    // ── Users ─────────────────────────────────────────────────────

    @GET("api/users/me")
    suspend fun getMe(): UserPublic

    @GET("api/users/search")
    suspend fun searchUsers(@Query("q") query: String): List<UserPublic>

    @GET("api/users/{username}")
    suspend fun getUser(@Path("username") username: String): UserPublic

    @GET("api/users/{username}/followers")
    suspend fun getFollowers(@Path("username") username: String): List<UserPublic>

    @GET("api/users/{username}/following")
    suspend fun getFollowing(@Path("username") username: String): List<UserPublic>

    @POST("api/users/{username}/follow")
    suspend fun followUser(@Path("username") username: String): Response<Unit>

    @DELETE("api/users/{username}/follow")
    suspend fun unfollowUser(@Path("username") username: String): Response<Unit>

    // ── Spots ─────────────────────────────────────────────────────

    @GET("api/spots")
    suspend fun getSpots(@Query("q") query: String = ""): List<StudySpot>

    // ── Rankings ──────────────────────────────────────────────────

    @PUT("api/rankings")
    suspend fun setRankings(@Body request: SetRankingsRequest): List<RankedSpot>

    @GET("api/rankings/feed")
    suspend fun getFeed(): List<FeedItem>

    @GET("api/rankings/recent")
    suspend fun getRecentRankings(@Query("limit") limit: Int = 20): List<FeedItem>

    @GET("api/rankings/matchup")
    suspend fun getMatchup(): MatchupResponse

    @GET("api/rankings/user/{username}")
    suspend fun getUserRankings(@Path("username") username: String): List<RankedSpot>
}
