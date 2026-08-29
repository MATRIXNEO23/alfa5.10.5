plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val mlcRuntimePackaged = rootProject.file(
    "dist/lib/mlc4j/output/arm64-v8a/libtvm4j_runtime_packed.so"
).exists()

android {
    namespace = "com.neontides.nativeapp"
    compileSdk = 35

    signingConfigs {
        create("stableUpdate") {
            storeFile = file("signing/neontides-update.jks")
            storePassword = "neontides-alfa5"
            keyAlias = "neontides"
            keyPassword = "neontides-alfa5"
        }
    }

    defaultConfig {
        applicationId = "com.neontides.nativeapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 45
        versionName = "alfa8.10.8-luna-test"
        buildConfigField("boolean", "MLC_RUNTIME_PACKAGED", mlcRuntimePackaged.toString())

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
            }
        }

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableUpdate")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("stableUpdate")
            isDebuggable = false
            isMinifyEnabled = false
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
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation(project(":mlc4j"))
    testImplementation("junit:junit:4.13.2")
}
