import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Kotlin is provided by AGP 9's built-in Kotlin support. KSP 2.3.6+ is compatible with it.
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    // Consumes :baselineprofile's output and packages it as baseline.prof (audit ST1).
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "tv.own.owntv"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "tv.own.owntv"
        // Personal fork: a distinct application id so this build installs ALONGSIDE an official
        // OwnTV (no signature-mismatch conflict). The matching versionNameSuffix makes the fork
        // obvious in the About screen. Drop both lines to track upstream's package id exactly.
        applicationIdSuffix = ".fork"
        versionNameSuffix = "-fork"
        minSdk = 26
        targetSdk = 36
        // CI injects these from the git tag (see .github/workflows/android.yml) so releases never
        // need a manual edit here. The fallbacks are only used for local/debug builds — pinned HIGH
        // (99999, mirroring versionName 99.99.99) so a local/debug APK is always "newer" than any
        // published release and installs straight over it (no INSTALL_FAILED_VERSION_DOWNGRADE).
        versionCode = (System.getenv("VERSION_CODE") ?: "99999").toInt()
        // CI injects VERSION_NAME from the git tag for releases. The fallback is only ever used by
        // LOCAL builds (i.e. debug), so we pin it to 99.99.99 — that way a dev build is always "newer"
        // than any published release and the in-app updater never offers an "update" while developing.
        versionName = System.getenv("VERSION_NAME") ?: "99.99.99"

        // Opt-in local diagnostic APKs keep the rolling playback trace enabled even when they are
        // release-signed (so they can update an installed production build without changing its data).
        buildConfigField(
            "boolean",
            "DIAGNOSTIC_BUILD",
            (providers.gradleProperty("diagnosticBuild").orNull == "true").toString(),
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // (The fork's old defaultConfig arm-only abiFilters is gone: upstream's `standard` flavor below
        // now ships exactly those two ARM ABIs, and defaultConfig filters would UNION into the x86_64
        // emulator flavor, bloating it with arm libs it never loads.)
    }

    // ABI split via product flavors: real Android TV / Fire TV hardware is arm (arm64-v8a covers
    // everything modern; armeabi-v7a keeps the original 32-bit Nvidia Shield TV 2015/2017, which runs
    // Android 9+ but is 32-bit). x86_64 is emulator-only — no real TV box uses it. Shipping them as
    // separate flavors halves the download users get via the Downloader code (~49MB vs the old 104MB
    // universal APK that bundled all 4 ABIs), which fixes the "parse error on install" reports caused
    // by truncated downloads. x86 (32-bit Intel) is dropped entirely — even emulators use x86_64.
    //
    // Local dev: pick a flavor in Android Studio's "Build Variants" panel before Run (standard for
    // real devices / arm emulators, x86_64 for an x86_64 emulator). `assembleRelease` builds BOTH.
    flavorDimensions += "abi"
    productFlavors {
        create("standard") {
            dimension = "abi"
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters += listOf("x86_64") }
        }
    }

    // Release signing: env vars first (that is how CI injects the GitHub secrets), then Gradle
    // properties as a local fallback. Put the local ones in the USER-WIDE file — never in the repo:
    //
    //   C:\Users\<you>\.gradle\gradle.properties
    //     owntv.keystoreFile=E:\\MEGA\\CODE\\Github_Keystore\\owntv.keystore
    //     owntv.keystorePassword=...
    //     owntv.keyAlias=...
    //     owntv.keyPassword=...
    //
    // With those set, `./gradlew :app:assembleStandardRelease` produces a release-signed APK in any
    // terminal with no env-var dance, so a local dev build installs straight over a published
    // release (`adb install -r`) and upgrade/migration testing works with real data.
    // When neither source is configured — fork CI, or a fresh clone — nothing here applies and
    // builds still succeed, just unsigned.
    // Third source: a standalone properties file kept outside the repo entirely. Gradle only reads
    // gradle.properties from GRADLE_USER_HOME or the project dir, so this one is loaded by hand.
    val localSigningProps = Properties().apply {
        val f = File("E:/MEGA/CODE/OwnTV_Gradle/owntv-signing.properties")
        if (f.isFile) f.inputStream().use { load(it) }
    }

    fun signingValue(env: String, property: String): String? =
        System.getenv(env)
            ?: providers.gradleProperty(property).orNull
            ?: localSigningProps.getProperty(property)

    val releaseKeystore = signingValue("KEYSTORE_FILE", "owntv.keystoreFile")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signingValue("KEYSTORE_PASSWORD", "owntv.keystorePassword")
                keyAlias = signingValue("KEY_ALIAS", "owntv.keyAlias")
                keyPassword = signingValue("KEY_PASSWORD", "owntv.keyPassword")
            }
        }
    }

    testOptions {
        // JVM unit tests hit android.util.Log / SystemClock in the code under test (StalkerAuthManager
        // etc.); return defaults (no-op log, 0 clock) instead of "not mocked" crashes.
        unitTests.isReturnDefaultValues = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                keepRules {
                    files("proguard-rules.pro")
                }
            }
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // CI gates on this (see .github/workflows/android.yml), so an error must mean something.
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = false
        // Media3's player API surface is almost entirely @UnstableApi; this app is built on it, so
        // the check fires ~90 times across the player, Home and Live code and carries no signal.
        // Opting in file-by-file would only move the same acknowledgement into ~12 annotations.
        disable += "UnsafeOptInUsageError"
        // local.properties is developer-local and never committed (its Windows SDK path can't be
        // escaped without breaking the local tooling that writes it). CI has no such file at all.
        disable += "PropertyEscape"
        // Reports are what a failed CI run is inspected from.
        htmlReport = true
        xmlReport = true
        textReport = true
    }

    sourceSets["androidTest"].assets.directories.add("$projectDir/schemas")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The profile is a list of code paths, not machine code, so one recording serves every ABI flavor.
// mergeIntoMain writes it to `src/main/generated/baselineProfiles/` instead of the recording flavor's
// own source set — required here because it has to be recorded on an x86_64 emulator (baseline
// profile collection needs API 33+, and the arm TV boxes this app targets are older) yet shipped in
// the `standard` arm APK.
baselineProfile {
    mergeIntoMain = true
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose (BOM-managed)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)

    // Compose for TV
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tvprovider)

    // Lifecycle / Navigation
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // Preferences
    implementation(libs.androidx.datastore.preferences)

    // WorkManager (durable background sync)
    implementation(libs.androidx.work.runtime)

    // Baseline profiles: installs the merged library profiles (Compose, Media3, Room, ...) into
    // ART on first launch. OwnTV is sideloaded, so without this the bundled profiles never apply.
    implementation(libs.androidx.profileinstaller)

    // Compat splash screen (audit ST3) — branded cold start instead of a blank window.
    implementation(libs.androidx.core.splashscreen)

    // The recorded startup journey (audit ST1). Regenerate with
    // `./gradlew :app:generateBaselineProfile` whenever the startup path changes. `mergeIntoMain`
    // collapses the per-variant tasks into that single one; it records against :app's x86_64 flavor
    // (see baselineprofile/build.gradle.kts) because collection needs an API 33+ device.
    baselineProfile(project(":baselineprofile"))

    // Database (Room, via KSP) + Paging
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.zxing.core) // QR generation for the Remote (companion) add-source flow
    implementation(libs.juniversalchardet) // local subtitle charset detection (subtitle plan §7.2)

    // Media playback — libmpv (FFmpeg) engine
    implementation(libs.libmpv)
    // Media3 / ExoPlayer — used ONLY for the VOD + image-subtitle (PGS/VOBSUB/DVB) handoff, where it
    // keeps video zero-copy AND renders bitmap subs on its own layer (mpv's direct path can't). Not a
    // sidecar: mpv is stopped first, so the provider only ever sees one connection.
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls) // HLS (.m3u8) support for the Live preview engine
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    // In-app YouTube trailer playback (plan §7.3) — WebView-backed IFrame player; the only ToS-clean
    // way to play YouTube trailers inside the app. Falls back to an "Open in YouTube" intent.
    implementation(libs.youtube.player)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Dependency injection
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)


    // Debug tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Test
    testImplementation(libs.junit)
    // Test-only, never packaged: android.jar's org.json is a stub, and isReturnDefaultValues turns
    // every JSONObject call into a silent null/0. Backup/restore is all JSON, so the unit tests need
    // the real implementation to mean anything.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
