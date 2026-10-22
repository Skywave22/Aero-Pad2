// AeroPad Remote v3 — app module.
// Clean Kotlin DSL build with KSP (no kapt), version catalog, zero deprecated APIs.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Signing inputs must be resolved BEFORE android {} so defaultConfig can
// record which key signs this build for every variant (a release-only
// placeholder makes debug manifest merging fail).
val keystorePath = System.getenv("RELEASE_KEYSTORE_FILE") ?: findProperty("RELEASE_KEYSTORE_FILE") as String?
val keystorePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: findProperty("RELEASE_KEYSTORE_PASSWORD") as String?
val keyAliasName = System.getenv("RELEASE_KEY_ALIAS") ?: findProperty("RELEASE_KEY_ALIAS") as String?
val keyPasswordValue = System.getenv("RELEASE_KEY_PASSWORD") ?: findProperty("RELEASE_KEY_PASSWORD") as String?
val hasReleaseKeystore = !keystorePath.isNullOrBlank() && file(keystorePath!!).exists()
val requireReleaseSigning =
    (System.getenv("REQUIRE_RELEASE_SIGNING")
        ?: findProperty("REQUIRE_RELEASE_SIGNING")?.toString()) == "1"

if (requireReleaseSigning && !hasReleaseKeystore) {
    throw GradleException(
        """
        |REQUIRE_RELEASE_SIGNING=1 but no release keystore was found.
        |
        |  looked for RELEASE_KEYSTORE_FILE = ${keystorePath ?: "(unset)"}
        |
        |A release build signed with the debug key cannot be installed over an
        |existing AeroPad install (signature mismatch), and it must never be
        |published. See docs/SIGNING.md.
        """.trimMargin()
    )
}


android {
    namespace = "com.aeropad.remote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aeropad.remote"
        minSdk = 29          // Android 10+ (BluetoothHidDevice API requires 28; 29 confirmed by product decision)
        targetSdk = 35
        versionCode = 360
        versionName = "3.6.0"

        // Round 13 — which key signed THIS build, for every variant. Read it
        // back with: aapt2 dump xmltree --file AndroidManifest.xml app.apk
        manifestPlaceholders["aeropadSigning"] = if (hasReleaseKeystore) "release" else "debug"

        testInstrumentationRunner = "com.aeropad.remote.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    // ------------------------------------------------------------------
    // Signing:
    //  - If RELEASE_KEYSTORE_FILE (+ passwords) are provided (CI secrets or
    //    local gradle.properties), the release APK is signed with the real
    //    AeroPad release key.
    //  - Otherwise it falls back to the debug key so a build still produces
    //    something installable without secrets.
    //
    // Round 13 — that silent fallback is exactly how "release" builds shipped
    // signed with CN=Android Debug while the v3.4.0/v3.5.0 releases used the
    // real key: a debug-signed update cannot be installed over an installed
    // real-key build ("App not installed"), so the fallback has to be loud.
    // Set REQUIRE_RELEASE_SIGNING=1 (CI does when the keystore secret exists)
    // to turn the fallback into a hard error instead.
    // ------------------------------------------------------------------
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
                storeType = if (keystorePath!!.endsWith(".p12") || keystorePath.endsWith(".pfx")) {
                    "pkcs12"
                } else {
                    "jks"
                }
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

// L5 — export Room schemas into app/schemas for migration verification.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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
    // Regression test: instantiate ViewModels with an Android Context on the JVM.
    testImplementation(libs.mockito.core)
    // V2 PART C — reflection-verified property catalog (test-only; the
    // app itself never ships kotlin-reflect).
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")

    // Instrumented / Compose UI tests
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    // Screenshot tour: drive/screenshot real screens without waiting on
    // Compose idleness (which this app never reaches).
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
