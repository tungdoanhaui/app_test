@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.smartconfigapp

// ─── Android / Network / Retrofit / ViewModel… ──────────────────────


import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel

import android.net.*
import android.os.Build
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import android.net.wifi.WifiNetworkSpecifier
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.*

// ─── Compose Foundation (layout, grid, shape…) ────────────────────
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape

// ─── Compose Material Icons (core + extended) ────────────────────
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings

import android.view.LayoutInflater
import android.view.WindowInsetsAnimation
import android.widget.ImageButton
import android.widget.Toast
import androidx.compose.ui.viewinterop.AndroidView
// ─── Compose Material3 UI ────────────────────────────────────────
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.material3.Button

// ─── Compose UI core ─────────────────────────────────────────────
import androidx.compose.runtime.*

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextField
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.io.IOException
import okhttp3.*
import android.net.wifi.WifiManager
import android.content.Context

import android.provider.Settings
import androidx.navigation.NavController
import kotlinx.coroutines.runBlocking
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage

// import đúng WeatherVM theo package của bạn, ví dụ:

// ───────────── Retrofit DTO & service definitions ─────────────

data class RegisterReq(
    val username: String,
    val email: String,
    val password: String
)

data class LoginReq(
    val username: String,
    val password: String
)
data class ConnectRequest(
    val ssid: String,
    val password: String,
    val token: String
)


data class AuthResp(val token: String)
data class GpioReq(val level: Int)
data class TokenReq(val email:String, val token:String)
data class PasswordResetReq(val email: String)
data class DeviceDTO(val ip: String, val mac: String = "", val online: Boolean = false, val name: String = "")
data class FoundDevice(val ssid: String, val type: DeviceType)

// API chính
interface ApiAuth {
    @POST("api/register-user/") suspend fun register(@Body body: RegisterReq): AuthResp
    @POST("api/login/")         suspend fun login   (@Body body: LoginReq):    AuthResp
    @DELETE("api/delete-user/")    suspend fun deleteAccount(): Response<Unit>
    @POST("api/password-reset-api/")
    suspend fun requestPasswordReset(@Body body: PasswordResetReq): Response<Unit>

}


interface ApiDevice {
    @POST("api/register-device/") suspend fun add(@Body body: DeviceDTO): Response<Unit>
    @GET("api/devices")           suspend fun list(): List<DeviceDTO>
}

interface ApiControl {
    @POST("/api/gpio") suspend fun gpio(@Body body: GpioReq): Response<Unit>
}

// API trên ESP32 Soft-AP (port 80)
interface EspConfigApi {
    @GET("scan") suspend fun scan(): List<String>
    @POST("connect") suspend fun connect(@Body body: ConnectRequest): ConnectResp
    @POST("token")   suspend fun sendToken(@Body body: TokenReq): Response<Unit>
}
data class ConnectResp(val status: String, val ip: String)

data class WifiReq(val ssid: String, val password: String)

object Api {
        private val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    var token: String? = null; private set
    fun setToken(t: String) { token = t }

    private fun client(): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val auth = Interceptor { chain ->
            val original = chain.request()
            val token = Api.token
            val req = if (!token.isNullOrBlank()) {
                original.newBuilder()
                    .addHeader("Authorization", "Token $token")
                    .build()
            } else {
                original
            }
            chain.proceed(req)
        }

        return OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logger)
            .build()
    }


    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(client())
        .build()

    private const val BASE_URL = "https://api.linhkiensmart.com/"


    val auth    by lazy { retrofit(BASE_URL).create(ApiAuth::class.java) }
    val device  get() = retrofit(BASE_URL).create(ApiDevice::class.java)
    val control get() = retrofit(BASE_URL).create(ApiControl::class.java)

}

// ───────────── DataStore helper ─────────────

private val Context.dataStore by preferencesDataStore(name = "auth")
class TokenStore(private val ctx: Context) {
    private val KEY = stringPreferencesKey("jwt")
    suspend fun save(t: String) = ctx.dataStore.edit { it[KEY] = t }
    suspend fun load(): String? = ctx.dataStore.data.first()[KEY]
}
//--------------Điều Khiển thiết bị --------------------
class MqttHelper(context: Context) {
    val clientId = "android_" + System.currentTimeMillis()
    val serverUri = "tcp://api.linhkiensmart.com:1883"
    val mqttUsername = "tungdoan"
    val mqttPassword = "tungdoan"
    val topic = "home/esp32/motor/cmd"
    val mqttAndroidClient = MqttAndroidClient(context, serverUri, clientId)

    fun connect(onConnected: () -> Unit = {}) {
        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            userName = mqttUsername
            password = mqttPassword.toCharArray()
        }
        mqttAndroidClient.connect(options, null, object: org.eclipse.paho.client.mqttv3.IMqttActionListener {
            override fun onSuccess(asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?) {
                onConnected()
            }
            override fun onFailure(asyncActionToken: org.eclipse.paho.client.mqttv3.IMqttToken?, exception: Throwable?) {
                // handle error
            }
        })
    }

    fun publish(cmd: String) {
        val message = MqttMessage()
        message.payload = cmd.toByteArray()
        mqttAndroidClient.publish(topic, message)
    }
}
// ───────────── ViewModels ─────────────

class AuthVM(private val store: TokenStore) : ViewModel() {
    var email by mutableStateOf("")
    var pass  by mutableStateOf("")
    var busy  by mutableStateOf(false)
    var err   by mutableStateOf<String?>(null)


    private fun doAuth(
        nav: NavHostController,
        call: suspend ()->AuthResp
    ) {
        viewModelScope.launch {
            busy = true
            err = null
            runCatching { call() }
                .onSuccess {
                    store.save(it.token)
                    Api.setToken(it.token)
                    nav.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
                .onFailure { err = it.localizedMessage }
            busy = false
        }
    }

    fun register(
        nav: NavHostController,
        email: String,
        pass: String
    ) = doAuth(nav) {
        Api.auth.register(
            RegisterReq(username = email, email = email, password = pass)
        )
    }

    fun login(
        nav: NavHostController,
        email: String,
        pass: String
    ) = doAuth(nav) {
        Api.auth.login(
            LoginReq(
                username = email,   // truyền email vào username
                password = pass
            )
        )
    }
    fun resetPassword(email: String, onDone: (Boolean)->Unit) {
        viewModelScope.launch {
            val success = runCatching {
                Api.auth.requestPasswordReset(PasswordResetReq(email))
            }.map { it.isSuccessful }.getOrDefault(false)
            onDone(success)
        }
    }

}
sealed class DeviceType(val prefix: String, val icon: Int) {
    object ESP32 : DeviceType("ESP_Tu", R.drawable.nut1)
    object Raspi : DeviceType("Paspi_Config", R.drawable.tu01)
    object Zigbee : DeviceType("Zigbee_Config", R.drawable.nut2)
    // ...thêm ở đây nếu có loại mới
    companion object {
        fun fromSSID(ssid: String): DeviceType? = when {
            ssid.startsWith(ESP32.prefix) -> ESP32
            ssid.startsWith(Raspi.prefix) -> Raspi
            ssid.startsWith(Zigbee.prefix) -> Zigbee
            else -> null
        }
    }
}



class DeviceVM(private val store: TokenStore) : ViewModel() {
    private val _deviceList = MutableStateFlow<List<DeviceDTO>>(emptyList())
    val deviceList = _deviceList.asStateFlow()
    private var lastRefreshTime: Long = 0
    var foundDevices by mutableStateOf<List<FoundDevice>>(emptyList())
    var selectedDevice: FoundDevice? by mutableStateOf(null)



    fun refreshDeviceListThrottled() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < 10_000) return // 10 giây chỉ cho refresh 1 lần
        lastRefreshTime = now
        refreshDeviceList()
    }
    fun refreshDeviceList() {
        viewModelScope.launch {
            val list = Api.device.list() // Gọi api, lấy list device từ server
            _deviceList.value = list
        }
    }
    // 1) Tạo Moshi với KotlinJsonAdapterFactory
    private val espMoshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // 2) Tạo OkHttpClient (bạn có thể config thêm logging/auth nếu cần)
    private val espClient = OkHttpClient.Builder().build()

    // 3) Dùng Moshi + client đó để build Retrofit duy nhất
    private val espRetrofit = Retrofit.Builder()
        .baseUrl("http://192.168.4.1/")
        .addConverterFactory(MoshiConverterFactory.create(espMoshi))
        .client(espClient)
        .build()
    private val espApi = espRetrofit.create(EspConfigApi::class.java)
    // server chính
    var userEmail by mutableStateOf("")
    var espIp by mutableStateOf<String?>(null)
    var busy  by mutableStateOf(false)
    var err   by mutableStateOf<String?>(null)

    // AP-config
    var espConfigNetworks by mutableStateOf<List<String>>(emptyList())
    var selectedSSID      by mutableStateOf("")
    var wifiPwd           by mutableStateOf("")
    var configResult      by mutableStateOf<String?>(null)

    private var netCallback: ConnectivityManager.NetworkCallback? = null

    // B1: kết nối vào AP của ESP32
    fun connectToEspAp(ctx: Context, onBound: (Network) -> Unit = {}) {
        val spec = WifiNetworkSpecifier.Builder()
            .setSsid("ESP_Config")
            .setWpa2Passphrase("12345678")
            .build()
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(spec)
            .build()
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        netCallback?.let { cm.unregisterNetworkCallback(it) }
        netCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // bind để gọi local ESP
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    cm.bindProcessToNetwork(network)
                onBound(network)           // cho mình biết đã bind xong
            }
        }
        cm.requestNetwork(req, netCallback!!)
    }
    fun scanAllDevices(ctx: Context) = viewModelScope.launch {
        val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiManager.startScan()  // Khởi động quét Wi-Fi, nên dùng trong coroutine hoặc callback

        // Đợi một chút cho scan xong (hoặc có thể dùng BroadcastReceiver chuẩn nếu cần)
        kotlinx.coroutines.delay(2000)

        val scanResults = wifiManager.scanResults
        val allSsids = scanResults.map { it.SSID }.distinct().filter { it.isNotBlank() }

        foundDevices = allSsids.mapNotNull { ssid ->
            DeviceType.fromSSID(ssid)?.let { type -> FoundDevice(ssid, type) }
        }
    }





    fun bindEspNetworkAndDo(ctx: Context, block: suspend () -> Unit) = runBlocking {
        val connectivityManager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = connectivityManager.allNetworks
        val wifiNetwork = networks.firstOrNull { network ->
            val caps = connectivityManager.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        if (wifiNetwork != null) {
            try {
                connectivityManager.bindProcessToNetwork(wifiNetwork)
                block()
            } finally {
                connectivityManager.bindProcessToNetwork(null)
            }
        } else {
            Log.e("ESP_BIND", "Không tìm thấy Wi-Fi network để bind.")
        }
    }

    // B2: quét Wi-Fi xung quanh do ESP32 scan rồi trả về JSON
    fun scanEspNetworks(ctx: Context) = viewModelScope.launch {
        bindEspNetworkAndDo(ctx) {
            try {
                val list = espApi.scan() // Gọi API GET http://192.168.4.1/scan
                espConfigNetworks = list
            } catch (e: Exception) {
                configResult = "Scan lỗi: ${e.localizedMessage}"
                espConfigNetworks = emptyList()
            }
        }
    }

    fun connectEspWithToken(
        ctx: Context,
        ssid: String,
        pwd: String,
        token: String,
        onDone: (Boolean) -> Unit
    ) = viewModelScope.launch {
        bindEspNetworkAndDo(ctx) {
            try {
                val request = ConnectRequest(ssid, pwd, token) // <-- ở đây là đúng!
                val resp = espApi.connect(request)
                onDone(resp.status == "ok")
            } catch (e: Exception) {
                onDone(false)
            }
        }
    }

    fun sendTokenToRaspi(token: String, device: FoundDevice) {
        val url = "http://192.168.200.200:5000/token" // IP default của Pi ở AP
        val jsonBody = """{"token":"$token"}"""
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonBody.toRequestBody(mediaType)
        val request = Request.Builder().url(url).post(body).build()
        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RASPI_TOKEN", "Gửi token tới Raspi thất bại: ${e.message}")
            }
            override fun onResponse(call: Call, response: okhttp3.Response) {
                Log.d("RASPI_TOKEN", "Raspi trả về: ${response.code}")
            }
        })
    }
    fun configWifiForRaspi(ssid: String, pwd: String, device: FoundDevice, cb: (Boolean) -> Unit) {
        val url = "http://192.168.200.200:5000/connect"
        val jsonBody = """{"ssid":"$ssid","password":"$pwd"}"""
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonBody.toRequestBody(mediaType)
        val request = Request.Builder().url(url).post(body).build()
        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("RASPI_WIFI", "Gửi cấu hình wifi Raspi thất bại: ${e.message}")
                cb(false)
            }
            override fun onResponse(call: Call, response: okhttp3.Response) {
                Log.d("RASPI_WIFI", "Raspi trả về: ${response.code}")
                cb(response.isSuccessful)
            }
        })
    }


    // B3: gửi SSID/PWD cho ESP32
    fun sendTokenToESP(token: String, espIp: String = "192.168.4.1") {
        val url = "http://$espIp/token"
        val jsonBody = """{"token":"$token"}"""
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val body = jsonBody.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val client = OkHttpClient()
        Log.d("TAG_TOKEN", "Gửi token tới ESP32: $token")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("TAG_TOKEN", "Gửi thất bại: ${e.message}")
            }
            override fun onResponse(call: Call, response: okhttp3.Response) {
                Log.d("TAG_TOKEN", "ESP trả về: ${response.code}")
                Log.d("TAG_TOKEN", "Response body: ${response.body?.string()}")
            }
        })
    }





    // server chính: đọc/danh sách + save + post token
    fun refreshFromServer() = viewModelScope.launch(Dispatchers.IO) {
        runCatching { Api.device.list() }
            .onSuccess { espIp = it.firstOrNull()?.ip }
    }
    fun saveToServer(ip: String) = viewModelScope.launch(Dispatchers.IO) {
        Api.device.add(DeviceDTO(ip))
    }
    private fun postTokenToEsp(ip: String, jwt: String) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val body = "{\"jwt\":\"$jwt\"}".toRequestBody("application/json".toMediaType())
            OkHttpClient().newCall(
                Request.Builder()
                    .url("http://$ip/token")
                    .post(body).build()
            ).execute().close()
        }
    }
    fun setGpio(level: Int) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { Api.control.gpio(GpioReq(level)) }
            .onFailure { err = it.localizedMessage }
    }
}

fun getCurrentWifiSSID(ctx: Context): String? {
    val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val info = wifiManager.connectionInfo
    var ssid = info.ssid
    if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
        ssid = ssid.substring(1, ssid.length - 1)
    }
    return ssid
}
// ───────────── Composables ─────────────
@Composable
fun DeviceCard(
    device: DeviceDTO,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // icon online/offline
                Box(
                    Modifier.size(16.dp)
                        .background(
                            if (device.online) Color.Green else Color.Red,
                            shape = CircleShape
                        )
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(device.name.ifEmpty { device.ip }, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(if (device.online) "Online" else "Offline", fontSize = 12.sp)
                }
            }
        }
    }
}


@Composable
fun AuthScreen(vm: AuthVM, loginMode: Boolean, nav: androidx.navigation.NavHostController) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = vm.email,
            onValueChange = { vm.email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.pass,
            onValueChange = { vm.pass = it },
            label = { Text("Mật khẩu") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (loginMode) {
                    vm.login(nav, vm.email.trim(), vm.pass.trim())
                } else {
                    vm.register(nav, vm.email.trim(), vm.pass.trim())
                }
            },
            enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loginMode) "Đăng nhập" else "Đăng ký")
        }
        Text(
            text = "Quên mật khẩu?",
            modifier = Modifier
                .clickable { nav.navigate("forgot") }
                .padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall.copy(color = Color.Blue)
        )

        vm.err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (vm.busy) CircularProgressIndicator()

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { nav.navigate(if (loginMode) "reg" else "login") }) {
            Text(if (loginMode) "Chưa có tài khoản? Đăng ký" else "Đã có tài khoản? Đăng nhập")
        }
    }

}
@Composable
fun ForgotPasswordScreen(nav: NavHostController) {
    val context = LocalContext.current
    val tokenStore = remember { TokenStore(context) }
    val vm: AuthVM = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AuthVM(tokenStore) as T
            }
        }
    )
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Nhập email để lấy lại mật khẩu",
            style = MaterialTheme.typography.titleLarge  // dùng titleLarge thay h6
        )
        Spacer(Modifier.height(16.dp))
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch {
                    vm.resetPassword(email) { success ->
                        message = if (success)
                            "Vui lòng kiểm tra email để lấy lại mật khẩu"
                        else
                            "Gửi mail thất bại, thử lại sau"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Lấy lại mật khẩu")
        }
        message?.let {
            Spacer(Modifier.height(16.dp))
            Text(
                it,
                color = if (it.startsWith("Vui lòng")) Color.Green else Color.Red
            )
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { nav.popBackStack() }) {
            Text("Quay về đăng nhập")
        }
    }
}


@Composable
fun WeatherCard(
    temperature: Int,
    humidity: Int,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(180.dp)
            .height(120.dp)
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEDF7FF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // icon + nhiet do
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("$temperature°C", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            // độ ẩm + mô tả
            Text("Độ ẩm $humidity%", fontSize = 14.sp)
            Text(description, fontSize = 12.sp, color = Color.Gray)
        }
    }
}


@Composable
fun HomeScreen(nav: NavHostController) {
    val weatherVM: WeatherVM = viewModel()
    val w by weatherVM.weather.collectAsState()
    LaunchedEffect(Unit) { weatherVM.fetchWeather() }

    Column(modifier = Modifier.fillMaxSize()) {
        // Phần header: weather + energy
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (w != null) {
                WeatherCard(
                    temperature = w!!.main.temp.toInt(),
                    humidity = w!!.main.humidity,
                    description = w!!.weather.firstOrNull()?.description ?: "",
                    modifier = Modifier
                )
            } else {
                // placeholder khi loading
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(120.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Đang tải…")
                }
            }

            // Giả sử bạn có card Home Energy kế bên
            Card(
                modifier = Modifier
                    .width(180.dp)
                    .height(120.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                // TODO: nội dung Home Energy
            }
        }

        Spacer(Modifier.height(8.dp))

        // Phần grid/scroll các devices
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
        }
    }
}
@Composable
fun DeviceListScreen(deviceVm: DeviceVM) {
    val deviceList by deviceVm.deviceList.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            deviceVm.refreshDeviceList()
            delay(10000)
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize()
    ) {
        items(deviceList) { device ->
            DeviceCard(device = device)
        }
    }
}



@Composable
fun TopScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        // 1. Inflate layout chứa tu01
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                LayoutInflater.from(context)
                    .inflate(R.layout.screen_top, null, false)
                    .apply {
                        // Khi nhấn tu01 → vào Screen01
                        findViewById<ImageButton>(R.id.tu01)
                            .setOnClickListener {
                                nav.navigate("screen01")
                            }
                        // (Bỏ qua nút bed nếu không dùng)
                    }
            }
        )

    }
}

data class Feature(val label: String, val icon: ImageVector)

@Composable
fun FeatureItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = CircleShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .size(80.dp)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
@Composable
fun Screen01(nav: NavHostController) {
    val ctx = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Gốc màn hình của bạn
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                LayoutInflater.from(context)
                    .inflate(R.layout.screen01, /*root*/null, /*attachToRoot*/false)
                    .apply {
                        // Next → Screen02
                        findViewById<ImageButton>(R.id.btnNext)
                            .setOnClickListener { nav.navigate("screen02") }
                        // Nếu bạn vẫn có nút settings cũ trong layout thì có thể remove listener này
                    }
            }
        )
        // 2. Gear icon “lơ lửng” trên cùng
        IconButton(
            onClick = { nav.navigate("add") },
            modifier = Modifier
                .offset(x = 120.dp, y = 30.dp)  // chỉnh X, Y ở đây
                .size(150.dp)                     // chỉnh kích thước ở đây
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Cài đặt",
                        tint = Color.White
            )
        }
    }
}

@Composable
fun Screen02(nav: NavHostController) {
    val ctx = LocalContext.current
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                LayoutInflater.from(context)
                    .inflate(R.layout.screen02, null, false)
                    .apply {
                        // Back → popBackStack
                        findViewById<ImageButton>(R.id.btnBack)
                            .setOnClickListener { nav.popBackStack() }
                    }
            }
        )
        IconButton(
            onClick = { nav.navigate("add") },
            modifier = Modifier
                .offset(x = 120.dp, y = 30.dp)
                .size(150.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Cài đặt",
                        tint = Color.White
            )
        }
    }
}

@Composable
fun AddDeviceScreen(
    ctx: Context,
    nav: NavController,
    vm: DeviceVM = viewModel()
) {
    var scannedWifiList by remember { mutableStateOf(listOf<String>()) }
    var selectedTargetWifi by remember { mutableStateOf("") }
    var inDeviceConfigMode by remember { mutableStateOf(false) }
    var currentSSID by remember { mutableStateOf(getCurrentWifiSSID(ctx)) }

    // Auto update currentSSID mỗi 1-2s
    LaunchedEffect(Unit) {
        while (true) {
            val ssid = getCurrentWifiSSID(ctx)
            if (ssid != currentSSID) {
                currentSSID = ssid
            }
            kotlinx.coroutines.delay(1500)
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {

        // Nếu chưa vào device config mode
        if (!inDeviceConfigMode) {
            val device = vm.selectedDevice

            if (device == null) {
                // 1. Chưa chọn thiết bị nào -> show list
                Button(onClick = { vm.scanAllDevices(ctx) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Quét tất cả thiết bị")
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(vm.foundDevices) { dev ->
                        Text(
                            "${dev.ssid} (${dev.type::class.simpleName})",
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.selectedDevice = dev }
                                .padding(8.dp)
                        )
                    }
                }
            } else {
                // 2. Đã chọn thiết bị, kiểm tra SSID
                if (currentSSID == device.ssid) {
                    // Đúng rồi, chuyển sang bước cấu hình thiết bị
                    inDeviceConfigMode = true
                } else {
                    // Hướng dẫn kết nối Wi-Fi
                    Text("Bạn cần kết nối Wi-Fi tới \"${device.ssid}\" để cấu hình thiết bị này.")
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            ctx.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Chuyển đến phần cài đặt Wi-Fi") }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            // Cho quay lại chọn thiết bị khác nếu muốn
                            vm.selectedDevice = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Quay lại chọn thiết bị") }
                }
            }
        }

        // Đã vào device config mode
        if (inDeviceConfigMode && vm.selectedDevice != null) {
            val device = vm.selectedDevice!!

            if (device.type is DeviceType.ESP32) {
                // Chỉ scan một lần khi mới vào mode config
                LaunchedEffect(device) {
                    vm.scanEspNetworks(ctx)
                }

                if (vm.espConfigNetworks.isEmpty()) {
                    Text("Đang quét Wi-Fi quanh thiết bị ESP32…")
                    CircularProgressIndicator()
                } else {
                    Text("Chọn Wi-Fi để cấu hình cho thiết bị:")
                    LazyColumn {
                        items(vm.espConfigNetworks) { ssid ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.selectedSSID = ssid }
                                    .background(
                                        if (vm.selectedSSID == ssid) Color(0xFFE0E0FF) else Color.Transparent
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    ssid,
                                    fontWeight = if (vm.selectedSSID == ssid) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    if (vm.selectedSSID.isNotEmpty()) {
                        OutlinedTextField(
                            value = vm.wifiPwd,
                            onValueChange = { vm.wifiPwd = it },
                            label = { Text("Mật khẩu Wi-Fi ${vm.selectedSSID}") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val jwt = Api.token ?: ""
                                vm.connectEspWithToken(ctx, vm.selectedSSID, vm.wifiPwd, jwt) { success ->
                                    if (success) {
                                        ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                            .edit().putBoolean("configured", true).apply()
                                        inDeviceConfigMode = false
                                        vm.selectedDevice = null
                                        nav.popBackStack()
                                    } else {
                                        vm.configResult = "Kết nối Wi-Fi thất bại"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cấu hình Wi-Fi cho thiết bị") }
                    }
                }
            } else if (device.type is DeviceType.Raspi) {
                Button(
                    onClick = {
                        val jwt = Api.token ?: ""
                        vm.sendTokenToRaspi(jwt, device)
                        Toast.makeText(ctx, "Đã gửi token cho Raspberry Pi", Toast.LENGTH_SHORT)
                            .show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("1. Gửi Token cho Raspberry Pi") }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        vm.configWifiForRaspi(device.ssid, vm.wifiPwd, device) { success ->
                            if (success) {
                                ctx.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                                    .edit().putBoolean("configured", true).apply()
                                inDeviceConfigMode = false
                                vm.selectedDevice = null
                                nav.popBackStack()
                            } else {
                                vm.configResult = "Connect Raspi lỗi"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("2. Cấu hình WiFi Raspi") }
            } else {
                Text("Thiết bị chưa hỗ trợ cấu hình tự động.")
            }
        }

        // Thông báo kết quả
        vm.configResult?.let { result -> Text("Kết quả: $result") }

        Spacer(Modifier.height(24.dp))
        Button(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Về trang chủ")
        }
    }
}

