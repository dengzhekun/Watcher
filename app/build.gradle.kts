import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use(::load)
    }
}

val versionPropertiesFile = rootProject.file("version.properties")
fun loadVersionProperties(): Properties {
    return Properties().apply {
        if (versionPropertiesFile.exists()) {
            FileInputStream(versionPropertiesFile).use(::load)
        }
    }
}
val versionProperties = loadVersionProperties()

val versionNameBase = versionProperties.getProperty("VERSION_NAME_BASE", "1.0").trim()
val storedVersionCode = versionProperties.getProperty("VERSION_CODE", "1").toIntOrNull() ?: 1
val resolvedVersionCode = storedVersionCode
val resolvedVersionName = "$versionNameBase.$resolvedVersionCode"

fun buildConfigString(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.example.watcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xmax.watcher"
        minSdk = 29
        targetSdk = 35
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_KEY", buildConfigString(""))
        buildConfigField("String", "VOLCENGINE_ASR_APP_KEY", buildConfigString(""))
        buildConfigField("String", "VOLCENGINE_ASR_ACCESS_KEY", buildConfigString(""))
        buildConfigField("String", "VOLCENGINE_ASR_RESOURCE_ID", buildConfigString(""))
    }

    signingConfigs {
        val releaseStoreFile = localProperties.getProperty("RELEASE_STORE_FILE")?.trim().orEmpty()
        val releaseStorePassword = localProperties.getProperty("RELEASE_STORE_PASSWORD")?.trim().orEmpty()
        val releaseKeyAlias = localProperties.getProperty("RELEASE_KEY_ALIAS")?.trim().orEmpty()
        val releaseKeyPassword = localProperties.getProperty("RELEASE_KEY_PASSWORD")?.trim().orEmpty()

        if (
            releaseStoreFile.isNotBlank() &&
            releaseStorePassword.isNotBlank() &&
            releaseKeyAlias.isNotBlank() &&
            releaseKeyPassword.isNotBlank()
        ) {
            create("release") {
                val keystoreFile = rootProject.file(releaseStoreFile).let { candidate ->
                    if (candidate.isAbsolute) candidate else rootProject.file(releaseStoreFile)
                }
                storeFile = keystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            val apiKey = localProperties.getProperty("API_KEY", "")
            val volcengineAsrAppKey = localProperties.getProperty("VOLCENGINE_ASR_APP_KEY", "")
            val volcengineAsrAccessKey = localProperties.getProperty("VOLCENGINE_ASR_ACCESS_KEY", "")
            val volcengineAsrResourceId = localProperties.getProperty("VOLCENGINE_ASR_RESOURCE_ID", "")

            buildConfigField("String", "API_KEY", buildConfigString(apiKey))
            buildConfigField("String", "VOLCENGINE_ASR_APP_KEY", buildConfigString(volcengineAsrAppKey))
            buildConfigField("String", "VOLCENGINE_ASR_ACCESS_KEY", buildConfigString(volcengineAsrAccessKey))
            buildConfigField("String", "VOLCENGINE_ASR_RESOURCE_ID", buildConfigString(volcengineAsrResourceId))
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            buildConfigField("String", "API_KEY", buildConfigString(""))
            buildConfigField("String", "VOLCENGINE_ASR_APP_KEY", buildConfigString(""))
            buildConfigField("String", "VOLCENGINE_ASR_ACCESS_KEY", buildConfigString(""))
            buildConfigField("String", "VOLCENGINE_ASR_RESOURCE_ID", buildConfigString(""))
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    lint {
        checkReleaseBuilds = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += "litertlm"
    }
}

val renameReleaseApk by tasks.registering {
    doLast {
        val renamedApkName = "watcher-xmax-v${resolvedVersionName}-${resolvedVersionCode}-release.apk"
        val candidateApkFiles = listOf(
            layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile,
            rootProject.file("app/release/app-release.apk")
        )

        candidateApkFiles
            .filter { it.exists() }
            .forEach { sourceApk ->
                val renamedApk = sourceApk.parentFile.resolve(renamedApkName)
                sourceApk.copyTo(renamedApk, overwrite = true)
            }
    }
}

tasks.matching { it.name == "assembleRelease" || it.name == "packageRelease" }.configureEach {
    finalizedBy(renameReleaseApk)
}

val bumpReleaseVersion by tasks.registering {
    group = "release"
    description = "Increment VERSION_CODE in version.properties for the next release build."
    doLast {
        val latestProperties = loadVersionProperties()
        val latestBase = latestProperties.getProperty("VERSION_NAME_BASE", "1.0").trim()
        val latestCode = latestProperties.getProperty("VERSION_CODE", "1").toIntOrNull() ?: 1
        latestProperties["VERSION_NAME_BASE"] = latestBase
        latestProperties["VERSION_CODE"] = (latestCode + 1).toString()
        FileOutputStream(versionPropertiesFile).use { output ->
            latestProperties.store(output, "Auto-generated release version state")
        }
        println("Bumped release version to $latestBase.${latestCode + 1} (${latestCode + 1})")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.security.crypto)
    implementation(libs.play.services.location)

    // ViewModel Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Gson
    implementation(libs.gson)

    // MJPEG frame sequence to MP4 encoding
    implementation(libs.jcodec.android)

    // CameraX fallback for local front camera preview
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Embedded HTTP server for gateway API
    implementation(libs.nanohttpd)

    // LiteRT-LM on-device inference
    implementation(libs.litertlm.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
