plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.pocketagent.app"
    compileSdk = 37

    // assets/skills.zip 本身已经是 deflate 压缩过的 21MB 包，再压一遍只会让构建变慢、
    // 体积也不降（反而多一次解压）。直接原样存进 APK，安装后按原样读出。
    androidResources {
        noCompress += "zip"
    }

    defaultConfig {
        applicationId = "com.pocketagent.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 43
        versionName = "0.8.1"
    }

    buildTypes {
        release {
            // R8：去掉未用类、内联、重命名。Compose + kotlinx.serialization 的规则见 proguard-rules.pro
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 本机只有 debug keystore。release 先用它签名，方便直接装机做性能对照
            // （正式发版时换成自己的 release keystore 即可，其它配置不用动）
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // 本机构建不走 lint（lint 工具包未缓存、外网拉取会超时），直接跳过 release 的 lintVital
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        compose = true
        // 版本号从 Gradle 读取，避免 UI 里硬编码的版本与实际包不一致
        buildConfig = true
    }

    packaging {
        jniLibs {
            // proot 必须直接从 nativeLibraryDir 执行。AGP 8+ 默认不把 .so 解压出来
            // （直接从 APK 内 mmap），那样 nativeLibraryDir 里是空的，
            // 启动时会报 "proot executable not found"。
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.ui:ui:1.12.1")
    implementation("androidx.compose.foundation:foundation:1.12.1")
    implementation("androidx.compose.animation:animation:1.12.1")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.12.1")
    implementation("androidx.documentfile:documentfile:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}
