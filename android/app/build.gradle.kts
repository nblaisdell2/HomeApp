import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.openapi.generator)
}

// Per-machine settings (gitignored): android/local.properties
//   home.apiBaseUrl=http://localhost:3000/             debug builds (the default). Reaches the API on
//                                                      the PC through `adb reverse tcp:3000 tcp:3000`,
//                                                      which scripts/install.ps1 sets up for every device.
//   home.releaseApiBaseUrl=https://<railway-domain>/   release builds
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(name: String, default: String): String =
    (providers.gradleProperty(name).orNull ?: localProps.getProperty(name) ?: default)
        .let { if (it.endsWith("/")) it else "$it/" }

val debugApiBaseUrl = localProp("home.apiBaseUrl", "http://localhost:3000/")
val releaseApiBaseUrl = localProp("home.releaseApiBaseUrl", "https://home-api.example.invalid/")

// HOME-65: release signing. android/keystore.properties (gitignored) points at a keystore kept
// OUTSIDE the repo; see README "Release signing". Without it, release builds come out unsigned.
//   storeFile=C:/Users/<you>/.android-keys/home-release.jks
//   storePassword=...
//   keyAlias=home
//   keyPassword=...
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
// For checking a shrunk (R8) release build on an emulator before the real keystore exists:
// ./gradlew assembleRelease -Phome.debugSignRelease=true. Never install that build on the real
// devices: a later build signed with the real key would have to be uninstalled first.
val debugSignRelease = providers.gradleProperty("home.debugSignRelease").orNull == "true"

android {
    namespace = "com.nblaisdell.home"
    // compileSdk only decides which APIs we can compile against. Current AndroidX/Compose
    // releases require 37.1+, so it is ahead of targetSdk (which controls runtime behavior).
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.nblaisdell.home"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (!keystoreProps.isEmpty) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Debug and release can be installed side by side ("Home (debug)" and "Home").
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
            // Plain http is allowed only in debug, for the API running on the PC.
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            resValue("string", "app_name", "Home (debug)")
        }
        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
            // https (Railway) in practice; plain http only if you point a release build at one.
            manifestPlaceholders["usesCleartextTraffic"] = releaseApiBaseUrl.startsWith("http://").toString()
            resValue("string", "app_name", "Home")
            signingConfig = when {
                !keystoreProps.isEmpty -> signingConfigs.getByName("release")
                debugSignRelease -> signingConfigs.getByName("debug")
                else -> null
            }
            // R8: shrink and optimize. Libraries (Retrofit, kotlinx.serialization, Tink, Hilt)
            // ship their own keep rules; app-specific ones go in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
}

// ---------------------------------------------------------------------------------------------
// HOME-86: generate the API client from the spec the Fastify API publishes.
// After changing the API: `cd api && npm run openapi:export`, then rebuild the app. A renamed or
// removed field then shows up as a compile error here instead of a runtime bug.
// ---------------------------------------------------------------------------------------------
val openApiSpec = rootProject.layout.projectDirectory.file("../openapi/openapi.json").asFile
val openApiOutputDir = layout.buildDirectory.dir("generated/openapi").get().asFile

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    inputSpec.set(openApiSpec.absolutePath)
    outputDir.set(openApiOutputDir.absolutePath)
    packageName.set("com.nblaisdell.home.data.remote.generated")
    apiPackage.set("com.nblaisdell.home.data.remote.generated.api")
    modelPackage.set("com.nblaisdell.home.data.remote.generated.model")
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "useCoroutines" to "true",
            "dateLibrary" to "string",
            "sourceFolder" to "src/main/kotlin",
            "omitGradleWrapper" to "true",
        ),
    )
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    ignoreFileOverride.set(file("openapi-generator-ignore").absolutePath)
    cleanupOutput.set(true) // so files dropped from the spec (or ignored) disappear
}

android.sourceSets.getByName("main").kotlin.directories.add(
    openApiOutputDir.resolve("src/main/kotlin").absolutePath,
)
tasks.named("preBuild") { dependsOn("openApiGenerate") }

room {
    // Exported schema JSON is committed so future migrations can be tested.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // AndroidX core + lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    // Compose (Material 3)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hilt (DI), generated with KSP
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // WorkManager + Hilt workers
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room (local cache), generated with KSP
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Home-screen widget (HOME-68)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // DataStore, serialization, coroutines
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.okhttp.sse)

    // Unit tests (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests (device/emulator)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
}
