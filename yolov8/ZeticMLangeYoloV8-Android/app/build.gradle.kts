import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.zeticai.zeticmlangeyolov8androidjava"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.zeticai.zeticmlangeyolov8androidjava"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val localProperties = Properties()
    localProperties.load(FileInputStream(project.file("local.properties")))

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            val personalKey = localProperties.getProperty("token.personal_key") ?: ""
            val yolov11ModelKey = localProperties.getProperty("token.yolov11_model_key") ?: ""
            buildConfigField("String", "PERSONAL_KEY", "\"$personalKey\"")
            buildConfigField("String", "YOLOV11_MODEL_KEY", "\"$yolov11ModelKey\"")
        }
        release {
            val personalKey = localProperties.getProperty("token.personal_key") ?: ""
            val yolov11ModelKey = localProperties.getProperty("token.yolov11_model_key") ?: ""
            buildConfigField("String", "PERSONAL_KEY", "\"$personalKey\"")
            buildConfigField("String", "YOLOV11_MODEL_KEY", "\"$yolov11ModelKey\"")

            isMinifyEnabled = false
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(files("libs/zeticlibs/zeticMLange.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}