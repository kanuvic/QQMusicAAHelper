import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val releaseSigningFile = rootProject.file("release-signing.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use { load(it) }
}
android {
    namespace = "dev.qqmusic.aahelper"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.qqmusic.aahelper"
        minSdk = 28
        targetSdk = 35
        versionCode = 7
        versionName = "0.1.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (releaseSigningFile.exists()) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            if (releaseSigningFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    lint {
        // Voice search is not supported by this QQ media-session bridge.
        disable += listOf("MissingIntentFilterForMediaSearch", "MissingOnPlayFromSearch")
    }
}
dependencies {
    implementation("androidx.media3:media3-session:1.9.3")
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.media:media:1.7.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
