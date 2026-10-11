// BluePilot Remote v3 — app module.
// Clean Kotlin DSL build with KSP (no kapt), version catalog, zero deprecated APIs.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.bluepilot.remote"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bluepilot.remote"
        minSdk = 29          // Android 10+ (BluetoothHidDevice API requires 28; 29 confirmed by product decision)
        targetSdk = 34
        versionCode = 330
        versionName = "3.3.0"

        testInstrumentationRunner = "com.bluepilot.remote.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // ------------------------------------------------------------------
    // Signing:
    //  - If RELEASE_KEYSTORE_FILE (+ passwords) are provided (CI secrets or
    //    local gradle.properties), the release APK is signed with the real
    //    Play-Store key.
    //  - Otherwise it falls back to the debug key so CI builds always
    //    produce an installable APK without secrets.
    // ------------------------------------------------------------------
    val keystorePath = System.getenv("RELEASE_KEYSTORE_FILE") ?: findProperty("RELEASE_KEYSTORE_FILE") as String?
    val keystorePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: findProperty("RELEASE_KEYSTORE_PASSWORD") as String?
    val keyAliasName = System.getenv("RELEASE_KEY_ALIAS") ?: findProperty("RELEASE_KEY_ALIAS") as String?
    val keyPasswordValue = System.getenv("RELEASE_KEY_PASSWORD") ?: findProperty("RELEASE_KEY_PASSWORD") as String?
    val hasReleaseKeystore = !keystorePath.isNullOrBlank() && file(keystorePath!!).exists()

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // Performance: R8 shrink/optimize/obfuscate + drop unused resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // V2 PART A — Compose compiler metrics/reports (build-time only,
        // zero runtime cost): identifies unstable composables causing
        // recomposition. Output: app/build/compose_metrics + compose_reports.
        freeCompilerArgs += listOf(
            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" +
                layout.buildDirectory.dir("compose_metrics").get().asFile.absolutePath,
            "-P", "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
                layout.buildDirectory.dir("compose_reports").get().asFile.absolutePath
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true   // AGP 8 disables BuildConfig by default; we use it for Timber gating
    }
    composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// Keep unit-test JVMs small so builds work on constrained runners too.
tasks.withType<Test>().configureEach {
    maxHeapSize = "512m"
    maxParallelForks = 1
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.navigation.compose)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Async
    implementation(libs.coroutines.android)

    // Persistence
    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Serialization (layout import/export)
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.timber)
    // V2 PART A — Baseline Profile installer: faster cold start + less jank.
    implementation(libs.profileinstaller)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.arch.core.testing)
    // V2 PART C — reflection-verified property catalog (test-only; the
    // app itself never ships kotlin-reflect).
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")

    // Instrumented / Compose UI tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
