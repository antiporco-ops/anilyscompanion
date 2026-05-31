import java.io.File
import java.io.FileInputStream
import java.util.Properties
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.androidApplication)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val sharedDebugSigningProperties = Properties()
val sharedDebugSigningPropertiesFile = File("D:\\Android\\companion\\debug-shared\\anilys-debug-signing.properties")

if (keystorePropertiesFile.exists()) {
    FileInputStream(keystorePropertiesFile).use(keystoreProperties::load)
}
if (sharedDebugSigningPropertiesFile.exists()) {
    FileInputStream(sharedDebugSigningPropertiesFile).use(sharedDebugSigningProperties::load)
}

fun firstNonBlank(vararg values: String?): String? =
    values.firstOrNull { !it.isNullOrBlank() }?.trim()

fun signingValue(propertyKey: String, vararg envKeys: String): String? {
    val propertyValue = keystoreProperties.getProperty(propertyKey)
    val envValue =
        envKeys
            .asSequence()
            .map { System.getenv(it) }
            .firstOrNull { !it.isNullOrBlank() }
    return firstNonBlank(propertyValue, envValue)
}

fun normalizeStoreFilePath(path: String): String {
    val windowsPathRegex = Regex("^[A-Za-z]:\\\\.*")
    return if (File.separatorChar == '/' && windowsPathRegex.matches(path)) {
        "/mnt/${path[0].lowercaseChar()}/${path.substring(3).replace('\\', '/')}"
    } else {
        path
    }
}

val releaseStoreFilePath = signingValue("storeFile", "KEYSTORE_FILE", "ANDROID_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "KEYSTORE_PASSWORD", "ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "KEY_ALIAS", "ANDROID_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "KEY_PASSWORD", "ANDROID_KEY_PASSWORD")
val sharedDebugStoreFilePath = firstNonBlank(sharedDebugSigningProperties.getProperty("storeFile"))
val sharedDebugStorePassword = firstNonBlank(sharedDebugSigningProperties.getProperty("storePassword"))
val sharedDebugKeyAlias = firstNonBlank(sharedDebugSigningProperties.getProperty("keyAlias"))
val sharedDebugKeyPassword = firstNonBlank(sharedDebugSigningProperties.getProperty("keyPassword"))
val maintenancePinSha256 =
    firstNonBlank(
        (findProperty("ANILYS_MAINTENANCE_PIN_SHA256") as String?),
        System.getenv("ANILYS_MAINTENANCE_PIN_SHA256"),
        "e25f201f9014599e00073db598a2603a9c05766965336d9b9c68c3d4081ee9a3"
    ) ?: "e25f201f9014599e00073db598a2603a9c05766965336d9b9c68c3d4081ee9a3"
val resolvedReleaseStoreFilePath = releaseStoreFilePath?.let(::normalizeStoreFilePath)
val resolvedReleaseStoreFile = resolvedReleaseStoreFilePath?.let(::file)
val resolvedSharedDebugStoreFilePath = sharedDebugStoreFilePath?.let(::normalizeStoreFilePath)
val resolvedSharedDebugStoreFile = resolvedSharedDebugStoreFilePath?.let(::file)
val hasSharedDebugSigning =
    !sharedDebugStorePassword.isNullOrBlank() &&
        !sharedDebugKeyAlias.isNullOrBlank() &&
        !sharedDebugKeyPassword.isNullOrBlank() &&
        resolvedSharedDebugStoreFile != null &&
        resolvedSharedDebugStoreFile.exists()

val missingReleaseSigningFields =
    buildList {
        if (releaseStoreFilePath.isNullOrBlank()) add("storeFile / KEYSTORE_FILE")
        if (releaseStorePassword.isNullOrBlank()) add("storePassword / KEYSTORE_PASSWORD")
        if (releaseKeyAlias.isNullOrBlank()) add("keyAlias / KEY_ALIAS")
        if (releaseKeyPassword.isNullOrBlank()) add("keyPassword / KEY_PASSWORD")
        if (resolvedReleaseStoreFilePath != null && (resolvedReleaseStoreFile == null || !resolvedReleaseStoreFile.exists())) {
            add("keystore file not found at $resolvedReleaseStoreFilePath")
        }
    }
val hasReleaseSigning = missingReleaseSigningFields.isEmpty()

fun releaseSigningErrorMessage(): String {
    val missingPart =
        if (missingReleaseSigningFields.isEmpty()) "unknown issue"
        else missingReleaseSigningFields.joinToString(", ")
    return "Release signing is not configured: $missingPart. " +
        "Set storeFile/storePassword/keyAlias/keyPassword in ${keystorePropertiesFile.absolutePath} " +
        "or use KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD environment variables."
}

val requestedTaskNames = gradle.startParameter.taskNames.map { it.lowercase() }
val isReleaseBuildRequested =
    requestedTaskNames.any { taskName ->
        taskName.contains("release") ||
            taskName == "assemble" ||
            taskName.endsWith(":assemble") ||
            taskName == "bundle" ||
            taskName.endsWith(":bundle") ||
            taskName == "build" ||
            taskName.endsWith(":build")
    }

if (isReleaseBuildRequested && !hasReleaseSigning) {
    throw GradleException(releaseSigningErrorMessage())
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
        versionCode = 24
        versionName = "1.2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "WEAR_REMOTE_INTERACTIONS_VERSION", "\"1.2.0\"")
        buildConfigField("String", "PLAY_SERVICES_WEARABLE_VERSION", "\"19.0.0\"")
        buildConfigField("String", "MAINTENANCE_PIN_SHA256", "\"$maintenancePinSha256\"")
    }

    signingConfigs {
        getByName("debug") {
            if (hasSharedDebugSigning) {
                storeFile = resolvedSharedDebugStoreFile
                storePassword = sharedDebugStorePassword
                keyAlias = sharedDebugKeyAlias
                keyPassword = sharedDebugKeyPassword
            }
        }
        create("release") {
            if (hasReleaseSigning) {
                storeFile = resolvedReleaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            if (hasReleaseSigning) {
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
    implementation(libs.play.services.wearable)
    implementation(libs.swiperefreshlayout)

    implementation("androidx.wear:wear-remote-interactions:1.2.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("androidx.concurrent:concurrent-futures:1.3.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("com.github.bumptech.glide:glide:4.14.2")
    annotationProcessor("com.github.bumptech.glide:compiler:4.14.2")

    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    testImplementation(libs.junit)
}
