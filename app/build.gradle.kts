plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// Each CI build gets a higher version number, so a new APK installs over the old one.
val buildNumber = System.getenv("VERSION_CODE")?.toInt() ?: 1

// The release signing key only exists on GitHub (as a secret) and is never committed.
// Every Newt app uses the same key (alias "dialer", from Newt Phone), so they can trust each other.
val releaseKeystore = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "io.github.secnewt.template"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.secnewt.template"
        // Android 13: apps' one-color (monochrome) icons, which Newt Home tints in Newt colors.
        // Lower it only if a feature needs an older phone, and say why here.
        minSdk = 33
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.1.$buildNumber"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "dialer"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Test builds install alongside the real app instead of clashing with it.
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Print why a test failed in the CI log, since reports are only in artifacts.
                it.testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
    }
    lint {
        // Fail the build on lint errors (this includes Android's security checks).
        abortOnError = true
        checkDependencies = true
        htmlReport = true
    }
}

dependencies {
    // The Newt look: theme, fonts, shared headers and outlines.
    implementation(libs.newt.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.espresso)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.roborazzi.accessibility.check)
    // newtScreenshot(): screenshots in the Newt theme plus accessibility checks.
    testImplementation(libs.newt.test)
}
