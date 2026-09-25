plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.btgamepad"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.btgamepad"
        // BluetoothHidDevice (HID Device profile) tersedia mulai Android 9 (API 28)
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Sengaja tanpa library tambahan: hanya Android framework + Kotlin stdlib.
}
