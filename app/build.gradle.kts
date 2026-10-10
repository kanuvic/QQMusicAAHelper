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
        versionCode = 6
        versionName = "0.1.5"
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
        // This v1 intentionally exposes only resume + transport controls, not voice search.
        disable += listOf("MissingIntentFilterForMediaSearch", "MissingOnPlayFromSearch")
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.media:media:1.7.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
