plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cos.lspit.gesture"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        applicationId = "com.cos.lspit.gesture"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures { buildConfig = false }
    packaging { resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*") }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.aidl)
    testImplementation("junit:junit:4.13.2")
}
