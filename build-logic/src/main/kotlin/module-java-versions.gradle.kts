val java16 = setOf(
    "bukkit",
    "commons",
    "hook"
)

plugins {
    `java-library`
}

java {
    toolchain {
        val version = when (project.name) {
            in java16 -> 16
            else -> 16
        }
        languageVersion.set(JavaLanguageVersion.of(version))
    }
}