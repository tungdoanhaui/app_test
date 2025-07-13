package com.example.smartconfigapp.network

import com.example.smartconfigapp.BuildConfig    // ← import BuildConfig đúng package
import com.example.smartconfigapp.WeatherResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("data/2.5/weather")
    suspend fun getWeather(
        @Query("lat")   latitude: Double,
        @Query("lon")   longitude: Double,
        @Query("units") units: String = "metric",
        @Query("appid") appId: String = BuildConfig.OPEN_WEATHER_API_KEY
    ): WeatherResponse
}
