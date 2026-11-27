plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.solimes.launcher" // <-- عدّل هذا إذا كان مختلفاً في مشروعك
    compileSdk = 35 // <-- عدّل هذا إذا كان مختلفاً في مشروعك

    defaultConfig {
        applicationId = "com.solimes.launcher" // <-- عدّل هذا إذا كان مختلفاً في مشروعك
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // مكتبات تحميل وفك ضغط ملف Java 8 بصيغة tar.xz
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("org.tukaani:xz:1.9")
}
