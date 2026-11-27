import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "sk.vladko.pill"
    compileSdk = 37
    defaultConfig {
        applicationId = "sk.vladko.pill"
        minSdk = 36
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }
    buildFeatures { compose = true }
    // The release key lives outside the repo, in ~/.pill-signing/keystore.properties.
    // Without that file (any other clone) the release build is signed with the debug key.
    val signingFile = File(System.getProperty("user.home"), ".pill-signing/keystore.properties")
    val releaseSigning =
        if (signingFile.isFile) {
            val keys = Properties().apply { signingFile.inputStream().use { load(it) } }
            signingConfigs.create("release") {
                storeFile = file(keys.getProperty("storeFile"))
                storePassword = keys.getProperty("storePassword")
                keyAlias = keys.getProperty("keyAlias")
                keyPassword = keys.getProperty("keyPassword")
            }
        } else {
            signingConfigs.getByName("debug")
        }
    buildTypes {
        // The phone build: optimised and signed with the release key when present.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = releaseSigning
        }
    }
    lint {
        warningsAsErrors = true
        abortOnError = true
        disable += setOf("OldTargetApi", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.savedstate:savedstate-ktx:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.4.21")
    testImplementation("org.json:json:20240303")
}
