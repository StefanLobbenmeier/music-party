plugins {
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.serialization)
    application
}

application { mainClass.set("music.plex.gateway.MainKt") }

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.serialization)
}
