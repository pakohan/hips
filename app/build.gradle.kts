plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pakohan.hips"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pakohan.hips"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The device tests run against debug unless told otherwise: -Phips.testBuildType=release checks the
    // build a person actually uses (see the release block).
    testBuildType = providers.gradleProperty("hips.testBuildType").getOrElse("debug")

    buildTypes {
        release {
            // A debug build started from Android Studio runs in ART's interpreter, where reading one photo
            // takes minutes instead of seconds. Release is therefore the variant to try the app with, and
            // until there is a release keystore it is signed with the debug key so it installs directly.
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                // Off: with the template's partial optimisation (androidx, kotlin, kotlinx only) the app
                // dies at start with "IllegalAccessError: Class kotlin.collections.j extended by class ih
                // is inaccessible". Open item in docs/plans/02-app.md.
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // The device tests read the same fixtures as the JVM tests (exported by poc/tools_export_*.py).
    sourceSets["androidTest"].assets.directories.add("src/test/resources")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.litert)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
