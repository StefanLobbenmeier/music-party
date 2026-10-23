@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins { alias(libs.plugins.kotlin); alias(libs.plugins.compose); alias(libs.plugins.compose.compiler) }
kotlin {
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "music-party.js" } }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":music-domain")); implementation(project(":spotify-api"))
            implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3)
            implementation(libs.coroutines); implementation(libs.ktor.js)
            implementation(npm("qr-scanner", "1.4.2"))
        }
    }
}
