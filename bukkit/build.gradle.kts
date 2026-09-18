dependencies {
    compileOnly(project(":hook"))

    implementation(libs.signgui)
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}