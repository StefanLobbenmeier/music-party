pluginManagement { repositories { gradlePluginPortal(); mavenCentral(); google() } }
dependencyResolutionManagement { repositories { mavenCentral(); google() } }
rootProject.name = "music-party"
include(":music-domain", ":spotify-api", ":bingo-domain", ":plex-gateway", ":composeApp")
