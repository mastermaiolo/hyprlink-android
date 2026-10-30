// SPDX-License-Identifier: GPL-3.0-or-later
import java.security.MessageDigest

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "io.maggio.hyprlink"
    minSdk = 33
    targetSdk = 36
    versionCode = 65
    versionName = "0.1.3"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "DONATE_URL", "\"https://ko-fi.com/mastermaggio\"")

    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }
  }

  signingConfigs {
    val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
    val releaseStorePass = System.getenv("STORE_PASSWORD")
    val releaseKeyPass = System.getenv("KEY_PASSWORD")
    val hasReleaseKeystore = !releaseKeystorePath.isNullOrBlank() &&
        !releaseStorePass.isNullOrBlank() &&
        !releaseKeyPass.isNullOrBlank() &&
        file(releaseKeystorePath).exists()

    create("release") {
      if (hasReleaseKeystore) {
        storeFile = file(releaseKeystorePath!!)
        storePassword = releaseStorePass
        keyAlias = System.getenv("KEY_ALIAS") ?: "upload"
        keyPassword = releaseKeyPass
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
      val releaseStorePass = System.getenv("STORE_PASSWORD")
      val releaseKeyPass = System.getenv("KEY_PASSWORD")
      val hasReleaseKeystore = !releaseKeystorePath.isNullOrBlank() &&
          !releaseStorePass.isNullOrBlank() &&
          !releaseKeyPass.isNullOrBlank() &&
          file(releaseKeystorePath).exists()
      if (hasReleaseKeystore) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
    create("releaseTest") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      applicationIdSuffix = ".rt"
      matchingFallbacks += listOf("release")
    }
    debug {
      applicationIdSuffix = ".debug"
      // Usa a chave de debug padrão do Android (~/.android/debug.keystore)
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// HyprLink dependencies
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.extensions)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  // HyprLink Custom dependencies
  implementation(libs.tech.kwik.kwik)
  implementation(libs.bouncycastle.bcprov)
  implementation(libs.bouncycastle.bcpkix)
  implementation(libs.cbor.java)
  implementation(libs.androidx.security.crypto)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.androidx.media)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}

base {
  archivesName.set("hyprlink")
}

tasks.register<Exec>("generateReleaseChecksums") {
  description = "Gera nomes canónicos de release e ficheiros .sha256"
  group = "build"
  workingDir(rootDir)
  commandLine("bash", "scripts/gerar-checksum.sh")
}

tasks.matching { it.name == "assembleRelease" || it.name == "assembleReleaseTest" }.configureEach {
  finalizedBy("generateReleaseChecksums")
}
