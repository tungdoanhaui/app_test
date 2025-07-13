// app/src/main/java/com/example/smartconfigapp/WeatherModels.kt
package com.example.smartconfigapp

data class WeatherResponse(
    val weather: List<WeatherDesc>,
    val main: Main
)
data class WeatherDesc(
    val description: String,
    val icon: String
)
data class Main(
    val temp: Double,
    val humidity: Int
)
