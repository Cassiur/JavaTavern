plugins {
    id("com.android.application")
}

/**
 * Release 签名从环境变量读取，由 CI 通过 GitHub Secrets 注入：
 *   JAVATAVERN_KEYSTORE_PATH / JAVATAVERN_KEYSTORE_PASSWORD /
 *   JAVATAVERN_KEY_ALIAS / JAVATAVERN_KEY_PASSWORD
 *
 * 本地开发没有这些变量时回退到 debug 签名，保证 `assembleRelease` 仍可运行，
 * 不会因为缺少密钥而卡住构建（参见 docs/RELEASE_PROCESS.md）。
 */
val releaseKeystorePath: String? = System.getenv("JAVATAVERN_KEYSTORE_PATH")
val hasReleaseSigning: Boolean =
    !releaseKeystorePath.isNullOrBlank() && file(releaseKeystorePath).exists()

android {
    namespace = "com.zcz.javatavern"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zcz.javatavern"
        minSdk = 24
        targetSdk = 36
        versionCode = 5
        versionName = "0.4.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = System.getenv("JAVATAVERN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JAVATAVERN_KEY_ALIAS")
                keyPassword = System.getenv("JAVATAVERN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.7")
    implementation("androidx.activity:activity:1.9.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
