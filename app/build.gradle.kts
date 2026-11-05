plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dup04205.photolog"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dup04205.photolog"
        minSdk = 29
        targetSdk = 34
        versionCode = 10
        versionName = "0.9.1"
    }

    // 서명 키는 저장소에 두지 않는다. GitHub Actions가 Secrets에서 app/release.keystore를 복원하고
    // 비밀번호는 환경 변수 SIGNING_PASSWORD로 받는다. 같은 키로 서명해야 덮어 설치(업데이트)가 된다.
    // 키가 없는 로컬 빌드는 안드로이드 기본 디버그 키로 서명된다.
    val releaseKeystore = file("release.keystore")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    val hasReleaseKey = releaseKeystore.exists() && !signingPassword.isNullOrEmpty()

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = releaseKeystore
                storePassword = signingPassword
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
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
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
