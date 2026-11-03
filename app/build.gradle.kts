import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// 固定签名：密钥库不入库，通过环境变量（CI）或 local.properties（本机）提供。
//   CLOUDTUNE_STORE_FILE / CLOUDTUNE_STORE_PASSWORD /
//   CLOUDTUNE_KEY_ALIAS  / CLOUDTUNE_KEY_PASSWORD
//   local.properties 中对应 cloudtune.storeFile 等键。
// 找不到密钥库时回落到默认 debug 签名，保证仍可编译。
// ---------------------------------------------------------------------------
val signingProps = Properties()
rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.reader(Charsets.UTF_8)
    ?.use { signingProps.load(it) }

fun signValue(envKey: String, propKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: signingProps.getProperty(propKey)?.takeIf { it.isNotBlank() }

val signStorePath = signValue("CLOUDTUNE_STORE_FILE", "cloudtune.storeFile")
val signStoreFile = signStorePath?.let { rootProject.file(it) }?.takeIf { it.exists() }
val signStorePassword = signValue("CLOUDTUNE_STORE_PASSWORD", "cloudtune.storePassword")
val signKeyAlias = signValue("CLOUDTUNE_KEY_ALIAS", "cloudtune.keyAlias") ?: "cloudtune"
val signKeyPassword = signValue("CLOUDTUNE_KEY_PASSWORD", "cloudtune.keyPassword") ?: signStorePassword

android {
    namespace = "com.cloudtune.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cloudtune.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 29
        versionName = "1.9.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // 固定签名：debug / release 都使用同一把密钥，保证每次安装的签名一致
        if (signStoreFile != null) {
            create("stable") {
                storeFile = signStoreFile
                storePassword = signStorePassword
                keyAlias = signKeyAlias
                keyPassword = signKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // 演示项目默认关闭混淆；如需开启，请配合 proguard-rules.pro 保留序列化模型
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signStoreFile != null) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
        debug {
            isMinifyEnabled = false
            if (signStoreFile != null) {
                signingConfig = signingConfigs.getByName("stable")
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
    // 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)

    // 生命周期
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 导航
    implementation(libs.androidx.navigation.compose)

    // 投屏（DLNA / UPnP）
    implementation(libs.androidx.mediarouter)

    // 媒体播放（Media3 / ExoPlayer）
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.ui)

    // 网络
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // 本地数据库（收藏）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 设置存储
    implementation(libs.androidx.datastore.preferences)

    // 二维码生成（扫码登录）
    implementation("com.google.zxing:core:3.5.3")

    // 第三方音源脚本执行（兼容洛雪音乐自定义源协议，QuickJS 支持 ES2020 / async）
    implementation("io.github.dokar3:quickjs-kt:1.0.0-alpha13")
}
