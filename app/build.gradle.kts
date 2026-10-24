import java.io.File
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Version overrides.
// CI / the release workflow pass -PversionCode=... -PversionName=...; without
// those properties the historical values (2 / "1.1") are used unchanged.
// ---------------------------------------------------------------------------
val defaultVersionCode = 2
val defaultVersionName = "1.1"

val buildLogger = logger

val overrideVersionCode: Int? = providers.gradleProperty("versionCode").orNull?.trim()?.let { raw ->
    val parsed = raw.toIntOrNull()
    if (parsed == null || parsed <= 0) {
        throw GradleException("-PversionCode must be a positive integer, got '${raw}'")
    }
    parsed
}
val overrideVersionName: String? = providers.gradleProperty("versionName").orNull?.trim()?.takeIf { it.isNotEmpty() }

// ---------------------------------------------------------------------------
// Optional release signing.
// A keystore is used only when it is supplied through the environment (CI
// secrets) or Gradle project properties. No secret material is ever stored in
// the repository; KEYSTORE_BASE64 is decoded into the JVM temp directory.
// ---------------------------------------------------------------------------
fun secretOrProperty(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }

val keystorePassword: String? = secretOrProperty("KEYSTORE_PASSWORD")
val keyAliasValue: String? = secretOrProperty("KEY_ALIAS")
val keyPasswordValue: String? = secretOrProperty("KEY_PASSWORD")

val decodedKeystore: File? = secretOrProperty("KEYSTORE_BASE64")?.let { encoded ->
    val target = File(System.getProperty("java.io.tmpdir"), "cos-gesture-signing/release.keystore")
    target.parentFile.mkdirs()
    target.writeBytes(Base64.getMimeDecoder().decode(encoded))
    target
}

val releaseKeystore: File? = decodedKeystore
    ?: secretOrProperty("KEYSTORE_FILE")?.let { path -> file(path) }

val hasReleaseKeystore: Boolean =
    releaseKeystore?.isFile == true &&
        !keystorePassword.isNullOrBlank() &&
        !keyAliasValue.isNullOrBlank() &&
        !keyPasswordValue.isNullOrBlank()

android {
    namespace = "com.cos.lspit.gesture"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.cos.lspit.gesture"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = overrideVersionCode ?: defaultVersionCode
        versionName = overrideVersionName ?: defaultVersionName
    }

    // Only created when usable signing material is present.
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        getByName("release") {
            // Behaviour change out of scope: minification stays disabled.
            isMinifyEnabled = false
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                buildLogger.lifecycle(
                    "WARNING: no release keystore supplied " +
                        "(KEYSTORE_FILE or KEYSTORE_BASE64 plus KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD). " +
                        "Signing the release build with the DEBUG keystore so the APK stays installable. " +
                        "Do not ship a debug-signed build as a long-term release."
                )
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { buildConfig = false; compose = true }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
        implementation(libs.androidx.compose.foundation)
        implementation(libs.androidx.compose.material3)
                implementation(libs.androidx.compose.material.icons.core)
                implementation(libs.miuix.ui)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.aidl)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
}
