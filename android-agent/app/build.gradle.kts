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
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Personal sideloaded build: sign with the debug key so `assembleRelease` installs.
            signingConfig = signingConfigs.getByName("debug")
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
