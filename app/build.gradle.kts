plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.qqmusic.aahelper"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.qqmusic.aahelper"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint {
        // This v1 intentionally exposes only resume + transport controls, not voice search.
        disable += listOf("MissingIntentFilterForMediaSearch", "MissingOnPlayFromSearch")
    }
}
dependencies {
    implementation("androidx.media:media:1.7.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
