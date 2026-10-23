@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins { alias(libs.plugins.kotlin); alias(libs.plugins.serialization) }
kotlin {
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    wasmJs { browser() }
    sourceSets {
        commonMain.dependencies { api(libs.ktor.core); implementation(libs.serialization) }
        wasmJsMain.dependencies { implementation(libs.ktor.js) }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
