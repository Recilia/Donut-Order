repositories {
    maven("https://repo.extendedclip.com/releases/") // placeholderapi
    maven("https://nexus.phoenixdevt.fr/repository/maven-public/") // mmo
}

dependencies {
    compileOnly(libs.placeholderapi)
    compileOnly(libs.bundles.mmo)

    // anti-dupe (multi-servers)
    compileOnly(libs.jedis)

    // database
    compileOnly(libs.zstd)
    compileOnly(libs.hikaricp)

    // license
    implementation(libs.sentinel) {
        exclude(group = "com.google.code.gson", module = "gson")
    }
}