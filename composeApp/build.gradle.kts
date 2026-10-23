@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins { alias(libs.plugins.kotlin); alias(libs.plugins.compose); alias(libs.plugins.compose.compiler) }
kotlin {
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "music-party.js" } }
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":music-domain")); implementation(project(":spotify-api"))
            implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3)
            implementation(libs.serialization); implementation(libs.coroutines)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        wasmJsMain.dependencies {
            implementation(libs.ktor.js)
            implementation(npm("qr-scanner", "1.4.2"))
        }
    }
}
