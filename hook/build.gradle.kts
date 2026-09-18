repositories {
    maven("https://repo.auxilor.io/repository/maven-public/") // eco
}

dependencies {
    compileOnly(libs.vault)

    compileOnly(libs.economyshopgui)
    compileOnly(libs.shopgui)

    // enchanting books
    compileOnly(libs.eco)

    compileOnly(fileTree("libs") { include("*.jar") })
}