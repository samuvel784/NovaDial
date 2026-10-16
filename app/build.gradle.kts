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
    applicationId = "com.aistudio.novadial.kpxm"
    minSdk = 24
    targetSdk = 36
    versionCode = 4
    versionName = "2.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val isDebugBuild = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Debug", ignoreCase = true) || taskName.contains("test", ignoreCase = true)
  }

  splits {
    abi {
      isEnable = !isDebugBuild
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      isUniversalApk = true
    }
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val keystoreFile = file(keystorePath)
      val pass = System.getenv("KEYSTORE_PASSWORD")
      val alias = System.getenv("KEY_ALIAS")
      val keyPass = System.getenv("KEY_PASSWORD")

      if (keystoreFile.exists() && !pass.isNullOrEmpty() && !alias.isNullOrEmpty() && !keyPass.isNullOrEmpty()) {
        storeFile = keystoreFile
        storePassword = pass
        keyAlias = alias
        keyPassword = keyPass
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val keystoreFile = file(keystorePath)
      val pass = System.getenv("KEYSTORE_PASSWORD")
      val alias = System.getenv("KEY_ALIAS")
      val keyPass = System.getenv("KEY_PASSWORD")

      signingConfig = if (keystoreFile.exists() && !pass.isNullOrEmpty() && !alias.isNullOrEmpty() && !keyPass.isNullOrEmpty()) {
        signingConfigs.getByName("release")
      } else {
        signingConfigs.getByName("debug")
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debug")
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
  lint {
    checkReleaseBuilds = false
    abortOnError = false
    disable += "ExtraTranslation"
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

configurations.all {
  resolutionStrategy {
    force("org.bouncycastle:bcprov-jdk18on:1.84")
    force("io.netty:netty-codec-http2:4.1.118.Final")
    force("io.netty:netty-codec-http:4.1.118.Final")
    force("io.netty:netty-handler:4.1.118.Final")
    force("io.netty:netty-codec:4.1.118.Final")
    force("io.netty:netty-common:4.1.118.Final")
    force("io.netty:netty-buffer:4.1.118.Final")
    force("io.netty:netty-transport:4.1.118.Final")
    force("org.apache.httpcomponents:httpclient:4.5.14")
    force("org.apache.commons:commons-lang3:3.14.0")
    force("org.jdom:jdom2:2.0.6.1")
    force("org.bitbucket.b_c:jose4j:0.9.6")
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.core.ktx)
  // installs the baseline profile (app/src/main/baseline-prof.txt) so the app starts and scrolls faster from the first launch
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.paging.runtime)
  implementation(libs.androidx.paging.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.paging)
  implementation(libs.sqlcipher)
  implementation(libs.coil.compose)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
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
  ksp(libs.androidx.room.compiler)
}
