plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.anish1050.ledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.anish1050.ledger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", "\"https://spent-service.vercel.app\"")
        // Web client id from Google Cloud console; the backend verifies the audience against it.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"replace-with-web-client-id.apps.googleusercontent.com\"")
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Play build: notification + gallery + manual capture. No SMS permission.
        create("play") { dimension = "distribution" }
        // Sideload / F-Droid build: adds SMS capture via a manifest overlay.
        create("foss") { dimension = "distribution" }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            // 10.0.2.2 is the host machine from the Android emulator.
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3002\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        // Shared ruleset and fixtures are the single source of truth; the app
        // bundles rules.json as an asset (copied below) and tests read fixtures directly.
        getByName("main").assets.srcDirs(layout.buildDirectory.dir("generated/rules-assets"))
        getByName("test").resources.srcDirs("../../shared/parser-rules")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

val copyRules by tasks.registering(Copy::class) {
    from("../../shared/parser-rules/rules.json")
    into(layout.buildDirectory.dir("generated/rules-assets"))
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach { dependsOn(copyRules) }
tasks.matching { it.name.startsWith("generate") && it.name.endsWith("Assets") }.configureEach { dependsOn(copyRules) }

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.fragment.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)
    implementation(libs.security.crypto)
    implementation(libs.biometric)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.google.id)
    implementation(libs.mlkit.text)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
