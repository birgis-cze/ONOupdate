plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "cz.tankono.widget"
    compileSdk = 35

    defaultConfig {
        applicationId = "cz.tankono.widget"
        minSdk = 26
        targetSdk = 35
        versionCode = 2                         // ################### zde zvýšit pro nový release  
        versionName = "2.6"                     // ################### zde zvýšit pro nový release 

        // GitHub token z prostředí (v CI) nebo prázdný (lokálně)
        val ghToken = System.getenv("GH_TOKEN") ?: ""
        buildConfigField("String", "GH_TOKEN", "\"$ghToken\"")
    }
    
    lint {
        checkReleaseBuilds = false   // Vypne kontrolu při release buildu
        abortOnError = false         // Nebude se zastavovat při chybě
    }

    // Fixní debug keystore – zaručí, že každý build má stejný podpis
    signingConfigs {
        create("debugFixed") {
            storeFile = file("../.github/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debugFixed")
            isMinifyEnabled = false
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("debugFixed")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "TankONO_widget.apk"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
}