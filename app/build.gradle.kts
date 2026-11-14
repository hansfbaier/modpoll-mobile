plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun buildConfigString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val testAdMobAppId = "ca-app-pub-3940256099942544~3347511713"
val testAdMobBannerUnitId = "ca-app-pub-3940256099942544/6300978111"
val adMobAppId = providers.gradleProperty("admobAppId").orElse(testAdMobAppId).get()
val adMobBannerUnitId = providers.gradleProperty("admobBannerUnitId").orElse(testAdMobBannerUnitId).get()
val fdroidUrl =
    providers
        .gradleProperty("fdroidUrl")
        .orElse("https://f-droid.org/packages/io.github.hansfbaier.modpoll/")
        .get()
val donationUrl =
    providers
        .gradleProperty("donationUrl")
        .orElse("https://liberapay.com/hansfbaier")
        .get()
val privacyUrl =
    providers
        .gradleProperty("privacyUrl")
        .orElse("https://github.com/hansfbaier/modpoll-mobile/blob/main/PRIVACY.md")
        .get()
val fdroidPromo =
    providers
        .gradleProperty("fdroidPromo")
        .orElse("true")
        .get()
        .toBoolean()

android {
    namespace = "io.github.hansfbaier.modpoll"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hansfbaier.modpoll"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "PLAY_PRO_PACKAGE", buildConfigString("io.github.hansfbaier.modpoll.pro"))
        buildConfigField("String", "FDROID_URL", buildConfigString(fdroidUrl))
        buildConfigField("String", "DONATION_URL", buildConfigString(donationUrl))
        buildConfigField("String", "PRIVACY_URL", buildConfigString(privacyUrl))
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", buildConfigString(adMobBannerUnitId))
        manifestPlaceholders["admobAppId"] = adMobAppId
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_FOSS", "true")
            buildConfigField("boolean", "IS_PLAY_FREE", "false")
            buildConfigField("boolean", "IS_PRO", "false")
            buildConfigField("boolean", "SHOW_FDROID_PROMO", "false")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_FOSS", "false")
            buildConfigField("boolean", "IS_PLAY_FREE", "true")
            buildConfigField("boolean", "IS_PRO", "false")
            buildConfigField("boolean", "SHOW_FDROID_PROMO", fdroidPromo.toString())
        }
        create("pro") {
            dimension = "distribution"
            applicationIdSuffix = ".pro"
            buildConfigField("boolean", "IS_FOSS", "false")
            buildConfigField("boolean", "IS_PLAY_FREE", "false")
            buildConfigField("boolean", "IS_PRO", "true")
            buildConfigField("boolean", "SHOW_FDROID_PROMO", "false")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // USB serial drivers (CH34x, CP210x, FTDI, PL2303, CDC-ACM ...)
    implementation("com.github.mik3y:usb-serial-for-android:3.12.0")

    // Google Play free flavor only. The foss and pro flavors must stay free of
    // Google Play services and advertising SDKs.
    add("playImplementation", "com.google.android.gms:play-services-ads:24.9.0")
    add("playImplementation", "com.google.android.ump:user-messaging-platform:3.2.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
