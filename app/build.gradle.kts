import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
}

// Lê keystore.properties no ROOT do projeto (mesmo nível do settings.gradle.kts)
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val hasKeystoreProps = keystorePropertiesFile.exists()

if (hasKeystoreProps) {
    FileInputStream(keystorePropertiesFile).use { fis ->
        keystoreProperties.load(fis)
    }
}

android {
    namespace = "com.anilyss.watchcompanion"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.anilyss.watchcompanion"
        minSdk = 27
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.3"
        // Match actual dependency versions from version catalog
        buildConfigField("String", "WEAR_REMOTE_INTERACTIONS_VERSION", "\"1.2.0-rc01\"")
        buildConfigField("String", "PLAY_SERVICES_WEARABLE_VERSION", "\"19.0.0\"")
    }

    // Signing configs (só cria se existir o keystore.properties)
    if (hasKeystoreProps) {
        signingConfigs {
            create("release") {
                val storeFilePath = keystoreProperties.getProperty("storeFile")
                val storePasswordValue = keystoreProperties.getProperty("storePassword")
                val keyAliasValue = keystoreProperties.getProperty("keyAlias")
                val keyPasswordValue = keystoreProperties.getProperty("keyPassword")

                // Fail-fast se tiver faltando algo no properties
                require(!storeFilePath.isNullOrBlank()) { "keystore.properties: storeFile ausente" }
                require(!storePasswordValue.isNullOrBlank()) { "keystore.properties: storePassword ausente" }
                require(!keyAliasValue.isNullOrBlank()) { "keystore.properties: keyAlias ausente" }
                require(!keyPasswordValue.isNullOrBlank()) { "keystore.properties: keyPassword ausente" }

                storeFile = file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            // Só aplica signing se o keystore.properties existir
            if (hasKeystoreProps) {
                signingConfig = signingConfigs.getByName("release")
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.wearable)
    implementation(libs.play.services.wearable)
    implementation(libs.swiperefreshlayout)
    implementation(libs.concurrent.futures)
    implementation(libs.recyclerview)
}
