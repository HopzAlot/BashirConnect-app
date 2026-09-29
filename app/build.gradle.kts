plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mrbashir.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mrbashir.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            // Official Google test IDs — safe for development, zero policy risk
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Read from ~/.gradle/gradle.properties or CI env — never hardcode in source
            manifestPlaceholders["admobAppId"] =
                (project.findProperty("PROD_ADMOB_APP_ID") as String?)
                    ?: "ca-app-pub-3940256099942544~3347511713" // fallback to test ID
            buildConfigField(
                "String", "ADMOB_BANNER_ID",
                "\"${project.findProperty("PROD_ADMOB_BANNER_ID") ?: "ca-app-pub-3940256099942544/6300978111"}\""
            )
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
        buildConfig = true  // Needed to expose ADMOB_BANNER_ID as BuildConfig.ADMOB_BANNER_ID
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Encrypted storage for credentials
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Networking (same job the Python `requests` library did)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines for background work off the main thread
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Google AdMob — banner ad at bottom of screen
    implementation("com.google.android.gms:play-services-ads:23.3.0")
}
