package com.example.smartconfigapp
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import android.net.wifi.WifiInfo
import android.widget.Toast
import androidx.compose.material3.Text


class MainActivity : ComponentActivity() {
    lateinit var mqttHelper: MqttHelper
    var tuTrangThai = 0   // 0=open, 1=stop, 2=close
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Bạn phải cấp quyền Vị trí để quét Wi-Fi", Toast.LENGTH_LONG).show()
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        mqttHelper = MqttHelper(this)
        mqttHelper.connect()
        val nutTuKeo = findViewById<ImageButton>(R.id.tu_keo_01)
        nutTuKeo.setOnClickListener {
            when (tuTrangThai) {
                0 -> mqttHelper.publish("OPEN")
                1 -> mqttHelper.publish("STOP")
                2 -> mqttHelper.publish("CLOSE")
            }
            tuTrangThai = (tuTrangThai + 1) % 3
        }
        super.onCreate(savedInstanceState)

        val tokenStore = TokenStore(this)
        setContent {
            val ctx = LocalContext.current
            val store = remember { TokenStore(ctx) }
            val nav   = rememberNavController()

            // 1) Load token nullable
            val startRoute by produceState<String?>(initialValue = null) {
                val t = store.load()
                value = if (t != null) {
                    Api.setToken(t)
                    "home"
                } else {
                    "login"
                }
            }

            // 2) Loading nếu chưa xong
            if (startRoute == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // 3) Tạo DeviceVM
                val deviceVm: DeviceVM = viewModel(factory = object: ViewModelProvider.Factory {
                    override fun <T: ViewModel> create(c: Class<T>) = DeviceVM(store) as T
                })

                // 4) Một NavHost duy nhất
                NavHost(nav, startDestination = startRoute!!) {
                    composable("login") {
                        val vmLogin: AuthVM = viewModel(factory = object: ViewModelProvider.Factory {
                            override fun <T: ViewModel> create(c: Class<T>) = AuthVM(store) as T
                        })
                        AuthScreen(vmLogin, loginMode = true, nav)
                    }
                    composable("reg") {
                        val vmReg: AuthVM = viewModel(factory = object: ViewModelProvider.Factory {
                            override fun <T: ViewModel> create(c: Class<T>) = AuthVM(store) as T
                        })
                        AuthScreen(vmReg, loginMode = false, nav)
                    }
                    composable("home") {
                        XmlHomeScreen(nav, deviceVm)
                    }


                    composable("forgot") { ForgotPasswordScreen(nav) }
                    composable("add") {
                        val authVm: AuthVM = viewModel(factory = object: ViewModelProvider.Factory {
                            override fun <T: ViewModel> create(c: Class<T>) = AuthVM(store) as T
                        })
                        val ctx = LocalContext.current
                        AddDeviceScreen(ctx = ctx, vm = deviceVm, nav = nav)


                    }
                }
            }
        }
    }
}
@Composable
fun XmlHomeScreen(nav: NavHostController, deviceVm: DeviceVM) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val deviceList by deviceVm.deviceList.collectAsState()
    val showImage = deviceList.isNotEmpty()

    LaunchedEffect(Unit) {
        deviceVm.refreshDeviceListThrottled()
    }
    
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                LayoutInflater.from(ctx).inflate(R.layout.layout_home, null, false).apply {
                    findViewById<ImageButton>(R.id.btnAddDevice).setOnClickListener {
                        nav.navigate("add")
                    }
                    findViewById<ImageView>(R.id.ivAccount).setOnClickListener { v ->
                        PopupMenu(ctx, v).apply {
                            menuInflater.inflate(R.menu.account_menu, menu)
                            setOnMenuItemClickListener { item ->
                                when (item.itemId) {
                                    R.id.menu_logout -> {
                                        coroutineScope.launch {
                                            TokenStore(ctx).save("")
                                        }
                                        nav.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                        true
                                    }
                                    R.id.menu_delete -> {
                                        coroutineScope.launch {
                                            Api.auth.deleteAccount()
                                            TokenStore(ctx).save("")
                                        }
                                        nav.navigate("login") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                        true
                                    }
                                    else -> false
                                }
                            }
                        }.show()
                    }
                    val tuDevice = findViewById<ImageButton>(R.id.tu_device)
                    tuDevice?.visibility = if (showImage) View.VISIBLE else View.GONE
                    tuDevice?.setOnClickListener {
                        val intent = Intent(ctx, Screen01Activity::class.java)
                        ctx.startActivity(intent)
                    }

                }
            },
            update = { view ->
                val tuDevice = view.findViewById<ImageButton>(R.id.tu_device)
                tuDevice?.visibility = if (showImage) View.VISIBLE else View.GONE
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
