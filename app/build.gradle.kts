import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.xxparthparekhxx.composekeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.xxparthparekhxx.composekeyboard"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // NOTE: unitTests.isReturnDefaultValues used to be true here. It was
    // removed deliberately: silently returning defaults for un-mocked
    // framework calls hides real failures in future tests. Tests must provide
    // fakes (see SwipeDictionaryTest.DummyContext) instead.

    val keystorePropsFile = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?: rootProject.file("app/keystore.properties").takeIf { it.exists() }

    val keystoreProps = Properties().apply {
        if (keystorePropsFile != null) {
            keystorePropsFile.inputStream().use { stream ->
                load(stream)
            }
        }
    }

    // Store path is resolved once, up front, so the guard below can tell the
    // difference between "no keystore configured" and "configured but broken".
    val storeFilePath: String? = keystoreProps.getProperty("storeFile")
    val resolvedStoreFile: File? = storeFilePath?.let { path ->
        val direct = file(path)
        if (direct.exists()) direct else rootProject.file(path).takeIf { it.exists() }
    }
    val missingCredentials: List<String> =
        listOf("storePassword", "keyAlias", "keyPassword")
            .filter { keystoreProps.getProperty(it).isNullOrBlank() }

    signingConfigs {
        if (resolvedStoreFile != null && missingCredentials.isEmpty()) {
            create("release") {
                storeFile = resolvedStoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    // ---- Release signing guard --------------------------------------------
    //
    // assembleRelease used to print BUILD SUCCESSFUL while emitting an UNSIGNED
    // APK whenever keystore.properties was missing -- which is exactly how an
    // unsigned release once got as far as a passing verification run. A release
    // that cannot be signed is a failed build, not a quiet fallback.
    //
    // Escape hatch for deliberately unsigned local builds:
    //     ./gradlew assembleRelease -PallowUnsignedRelease
    // It only covers a *missing* keystore.properties. A file that is present but
    // broken always fails, because that is a misconfiguration and never intent.
    val allowUnsignedRelease = providers.gradleProperty("allowUnsignedRelease").isPresent
    val releaseTaskPattern = Regex("^(assemble|bundle|package).*Release.*")

    gradle.taskGraph.whenReady {
        if (allTasks.none { releaseTaskPattern.matches(it.name) }) return@whenReady

        val hint = "Recover it from your password manager, or mint a new one with " +
            "keytool -genkeypair -keystore app/release.keystore -storetype PKCS12 " +
            "-alias composekeyboard -keyalg RSA -keysize 4096 -validity 10000"

        when {
            keystorePropsFile == null && allowUnsignedRelease ->
                logger.warn(
                    "WARNING: no keystore.properties; building an UNSIGNED release " +
                        "because -PallowUnsignedRelease was passed. Do not distribute this APK."
                )

            keystorePropsFile == null -> throw GradleException(
                "Release build requested but no keystore.properties was found at " +
                    "${rootProject.file("keystore.properties")} (or app/keystore.properties).\n" +
                    "$hint\nOr pass -PallowUnsignedRelease to build an unsigned APK on purpose."
            )

            storeFilePath.isNullOrBlank() -> throw GradleException(
                "${keystorePropsFile.path} has no storeFile= entry. See keystore.properties.example."
            )

            resolvedStoreFile == null -> throw GradleException(
                "${keystorePropsFile.path} points at storeFile=$storeFilePath, which does not " +
                    "exist (looked in ${project.projectDir} and ${rootProject.projectDir}).\n$hint"
            )

            missingCredentials.isNotEmpty() -> throw GradleException(
                "${keystorePropsFile.path} is missing or has blank: " +
                    "${missingCredentials.joinToString(", ")}. See keystore.properties.example."
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Single universal APK.
    //
    // ABI splits were removed deliberately. whisper-android ships arm64-v8a
    // only, so armeabi-v7a/x86/x86_64 splits came out byte-identical
    // (~4.3 MB, zero native code) — three redundant artifacts for zero benefit.
    // Worse, AGP 8's variant API offers no per-ABI versionCode override, so
    // every split shipped versionCode=3 and Play rejects multi-APK releases
    // with duplicate version codes ("Play handles ordering server-side" is
    // not true; distinct versionCodes are a hard requirement). If splits ever
    // return they need distinct versionCodes via the androidComponents
    // versionCode-override API, plus a universal fallback with the lowest code.
    splits {
        abi {
            isEnable = false
            isUniversalApk = true
        }
    }

    applicationVariants.all {
        val variantName = name
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "composekeyboard-$variantName.apk"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.whisper.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
