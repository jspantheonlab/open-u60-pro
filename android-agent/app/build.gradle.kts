plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.openu60.agent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.openu60.agent"
        // Android 12: the ZTE U30 Air / F50 (MU300). The U30 Pro (MU5358) is Android 15.
        minSdk = 31
        targetSdk = 35
        // CI passes these from the release tag; local builds stay at 0.1.0-dev.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = findProperty("versionName") as String? ?: "0.1.0-dev"
    }

    // CI signs releases with a fixed key (secrets), so each release installs over the last
    // with `adb install -r`. Without it, release builds fall back to the local debug key.
    val keystore = System.getenv("ANDROID_KEYSTORE_FILE")
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (keystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // No runtime dependencies: the agent uses only the platform (org.json, Telecom, SmsManager).
    testImplementation("junit:junit:4.13.2")
    // The real org.json for JVM unit tests (android.jar only has stubs).
    testImplementation("org.json:json:20240303")
}
