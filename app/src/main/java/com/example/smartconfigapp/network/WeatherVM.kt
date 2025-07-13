// app/src/main/java/com/example/smartconfigapp/WeatherVM.kt
package com.example.smartconfigapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartconfigapp.network.ApiClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WeatherVM(app: Application): AndroidViewModel(app) {
    private val fused = LocationServices.getFusedLocationProviderClient(app)
    private val _weather = MutableStateFlow<WeatherResponse?>(null)
    val weather: StateFlow<WeatherResponse?> = _weather

    fun fetchWeather() {
        fused.lastLocation.addOnSuccessListener { loc ->
            loc?.let {
                viewModelScope.launch {
                    runCatching {
                        ApiClient.weatherApi.getWeather(it.latitude, it.longitude)
                    }.onSuccess { _weather.value = it }
                }
            }
        }
    }
}
