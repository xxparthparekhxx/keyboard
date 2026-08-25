import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.composekeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.composekeyboard"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    val keystorePropsFile = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?: rootProject.file("app/keystore.properties").takeIf { it.exists() }

    val keystoreProps = Properties().apply {
        if (keystorePropsFile != null) {
            keystorePropsFile.inputStream().use { stream ->
                load(stream)
            }
        }
    }

    signingConfigs {
        if (keystorePropsFile != null) {
            create("release") {
                val storeFilePath = keystoreProps.getProperty("storeFile")
                val resolvedStoreFile = if (storeFilePath != null) {
                    val directFile = file(storeFilePath)
                    if (directFile.exists()) directFile else rootProject.file(storeFilePath)
                } else null

                if (resolvedStoreFile != null && resolvedStoreFile.exists()) {
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

    // One APK per ABI plus a universal fallback. The app itself is pure Kotlin,
    // but splitting keeps installs lean once native libs land (and lets stores
    // deliver only what a device can run). Per-ABI version code overrides are no
    // longer supported by AGP 8's variant API; Play multi-APK delivery handles
    // ordering server-side.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abi = output.getFilter(com.android.build.api.variant.FilterConfiguration.FilterType.ABI.name)
            if (abi != null) {
                output.outputFileName = "composekeyboard-${name}-${abi}.apk"
            } else {
                output.outputFileName = "composekeyboard-${name}-universal.apk"
            }
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

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
