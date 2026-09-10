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
        versionCode = 1
        versionName = "1.0"
    }

    // Fixní debug keystore – zaručí, že každý build má stejný podpis
    // (díky tomu půjde APK instalovat přes sebe)
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
            isMinifyEnabled = false
        }
    }

    // Název výsledného APK souboru
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "TankONO_widget.apk"
        }
    }

    buildFeatures {
        compose = true
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
    // Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // DataStore – ukládání nastavení
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager – plánování aktualizací
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Jsoup – parsování HTML
    implementation("org.jsoup:jsoup:1.18.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // AndroidX základ
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}