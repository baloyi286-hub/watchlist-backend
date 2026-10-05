plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.watchcue.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.watchcue.tv"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "API_BASE_URL", "\"https://watchlist-backend-one.vercel.app/api/v1\"")
        buildConfigField("String", "DEVICE_ID", "\"living-room-tv\"")
        buildConfigField("String", "BRIDGE_KEY", "\""+(project.findProperty("WATCHCUE_TV_BRIDGE_KEY") ?: "")+"\"")
    }
    buildFeatures { buildConfig = true }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
