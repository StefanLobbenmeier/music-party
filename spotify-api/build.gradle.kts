@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins { alias(libs.plugins.kotlin); alias(libs.plugins.serialization) }
kotlin {
    jvm { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    wasmJs { browser() }
    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.core)
            implementation(libs.serialization)
            api(libs.coroutines)
        }
        wasmJsMain.dependencies { implementation(libs.ktor.js) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:${libs.versions.ktor.get()}")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libs.versions.coroutines.get()}")
        }
    }
}
