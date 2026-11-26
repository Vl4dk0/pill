plugins {
    id("com.android.application")
}

android {
    namespace = "sk.vladko.fakeplayer"
    compileSdk = 37
    defaultConfig {
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    buildFeatures { resValues = true }
    // Two installable copies of the player so a test can run two media sessions at once.
    flavorDimensions += "player"
    productFlavors {
        create("one") {
            dimension = "player"
            applicationId = "sk.vladko.fakeplayer.one"
            resValue("string", "app_name", "Fake One")
            resValue("string", "player_prefix", "One")
        }
        create("two") {
            dimension = "player"
            applicationId = "sk.vladko.fakeplayer.two"
            resValue("string", "app_name", "Fake Two")
            resValue("string", "player_prefix", "Two")
        }
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
}
