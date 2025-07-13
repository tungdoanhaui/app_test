plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)    // <– plugin "org.jetbrains.kotlin.plugin.compose"
}

android {
    namespace = "com.example.smartconfigapp"
    compileSdk = 35

    // → bật BuildConfig và Compose song song ở đây
    buildFeatures {
        buildConfig = true
        compose     = true
    }

    defaultConfig {
        applicationId = "com.example.smartconfigapp"
        minSdk        = 24
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // → chỉ định field, giờ sẽ sinh ra trong BuildConfig
        buildConfigField(
            "String",
            "OPEN_WEATHER_API_KEY",
            "\"${project.property("OPEN_WEATHER_API_KEY") as String}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.6.7"
    }
}


dependencies {
    // ─── Smart-Config ───────────────────────────────────────────────────────
    implementation("com.github.EspressifApp:lib-esptouch-android:1.1.1")


    implementation ("com.google.android.gms:play-services-location:21.0.1")
    // ─── Networking ─────────────────────────────────────────────────────────
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi:1.15.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")

    // ─── Jetpack Compose ────────────────────────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")

    // Cần cả 2 để dùng Icons.Filled.Fan và các icon khác
    implementation("androidx.compose.material:material-icons-core:1.6.0")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")

    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation(libs.material)
    implementation(libs.play.services.location)
    implementation(libs.androidx.navigation.runtime.android)
    implementation(libs.ads.mobile.sdk)

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // ─── DataStore & Coroutines ─────────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ─── AndroidX core / lifecycle ───────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    // MQTT
    implementation ("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation ("org.eclipse.paho:org.eclipse.paho.android.service:1.1.1")
    implementation ("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")



    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
