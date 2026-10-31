plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "lk.mortal.tv"
    compileSdk = 36
    defaultConfig {
        applicationId = "lk.mortal.tv"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("versionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("versionName").orElse("0.1.0").get()
    }
    signingConfigs {
        create("distribution") {
            if (System.getenv("SIGNING_STORE") != null) {
                storeFile = file(System.getenv("SIGNING_STORE"))
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = System.getenv("SIGNING_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes { release { signingConfig = signingConfigs.getByName("distribution"); isMinifyEnabled = false } }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.core:core-ktx:1.17.0")
    val media = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media")
    implementation("androidx.media3:media3-exoplayer-hls:$media")
    implementation("androidx.media3:media3-exoplayer-dash:$media")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media")
    implementation("androidx.media3:media3-ui:$media")
    implementation("androidx.media3:media3-session:$media")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
