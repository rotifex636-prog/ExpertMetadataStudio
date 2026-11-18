plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "com.expert.metadata"
 compileSdk = 35
 defaultConfig { applicationId = "com.expert.metadata"; minSdk = 29; targetSdk = 35; versionCode = 12; versionName = "0.12.0-rc1" }
 val signingPath = System.getenv("RELEASE_KEYSTORE_PATH")
 val signingStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
 val signingAlias = System.getenv("RELEASE_KEY_ALIAS")
 val signingKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
 if (!signingPath.isNullOrBlank() && !signingStorePassword.isNullOrBlank() && !signingAlias.isNullOrBlank() && !signingKeyPassword.isNullOrBlank()) {
  signingConfigs {
   create("production") {
    storeFile = file(signingPath)
    storePassword = signingStorePassword
    keyAlias = signingAlias
    keyPassword = signingKeyPassword
   }
  }
 }
 buildTypes {
  release {
   isMinifyEnabled = false
   if (signingConfigs.findByName("production") != null) signingConfig = signingConfigs.getByName("production")
  }
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.ui:ui-tooling-preview")
 debugImplementation("androidx.compose.ui:ui-tooling")
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.exifinterface:exifinterface:1.3.7")
 implementation("androidx.media3:media3-transformer:1.5.1")
 implementation("androidx.media3:media3-effect:1.5.1")
 implementation("androidx.media3:media3-common:1.5.1")
}