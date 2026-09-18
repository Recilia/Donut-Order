plugins {
    java
    alias(libs.plugins.shadow)

    id("module-java-versions") apply false
    id("obfuscation")

    id("me.serbob.antipiracy") version "1.0.4"
}

allprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        mavenLocal()

        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")

        maven("https://repo.kodari.ai/releases") // signgui

        maven("https://repo.tcoded.com/releases") // folialib
        maven("https://repo.demeng.dev/releases") // sentinel
    }

    dependencies {
        compileOnly(rootProject.libs.lombok)
        annotationProcessor(rootProject.libs.lombok)

        compileOnly(rootProject.libs.paper.api)

        implementation(rootProject.libs.folialib)
        implementation(rootProject.libs.xseries)
    }
}

subprojects {
    apply(plugin = "module-java-versions")
}

configure(subprojects.filter { it.name != "commons" }) {
    dependencies {
        compileOnly(project(":commons"))
    }
}

dependencies {
    /*
     * Modules
     */
    implementation(project(":bukkit"))
    implementation(project(":commons"))
    implementation(project(":hook"))
}

tasks.shadowJar {
    // we don't add the license system, because it will be obfuscated
    relocate("com.tcoded.folialib", "me.rein.donutorder.libs.folialib")
    relocate("de.rapha149.signgui", "me.rein.donutorder.libs.signgui")
    relocate("com.cryptomorin.xseries", "me.rein.donutorder.libs.xseries")

    archiveClassifier.set("unobf")

    outputs.upToDateWhen { false } // to remake the -unobf every time
}

// if you don't use zelix to obfuscate, simply comment this
tasks.named("shadowJar") {
    finalizedBy("obfuscatePlugin")
}

tasks.named("obfuscatePlugin") {
    finalizedBy("injectLicenseFields")
}

