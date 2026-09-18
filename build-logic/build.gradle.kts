plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenLocal()
}

dependencies {
    // TODO change after getting accepted into gradle plugin hub
    implementation("me.serbob.antipiracy:me.serbob.antipiracy.gradle.plugin:1.0.3")
}