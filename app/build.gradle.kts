plugins { 
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
}

android {
  namespace = "com.konasl.nagad"
  compileSdk = 36
  defaultConfig { 
    applicationId = "com.konasl.nagad"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }
  compileOptions { 
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  buildFeatures { compose = true }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.12.0")
  implementation("androidx.activity:activity-compose:1.8.2")
  implementation(platform("androidx.compose:compose-bom:2024.09.03"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.media3:media3-exoplayer:1.2.1")
  implementation("androidx.media3:media3-ui:1.2.1")
  implementation("com.arthenica:ffmpeg-kit-full-gpl:6.0-2.LTS")
}
