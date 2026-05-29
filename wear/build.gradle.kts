plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.anilyss.watchcompanion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.anilyss.watchcompanion"
        minSdk = 28
        targetSdk = 35
        versionCode = 22
        versionName = "1.2.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.play.services.wearable)
}
