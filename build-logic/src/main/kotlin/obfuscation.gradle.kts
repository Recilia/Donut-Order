import java.nio.file.Files
import java.nio.file.StandardCopyOption

tasks.register("obfuscatePlugin") {
    doLast {
        val scriptName = "scriptdonutorder.txt"
        val scriptDirectory = "donutsmp/"
        val scriptPath = "$scriptDirectory$scriptName"

        val baseName = "${project.name}-${project.version}"
        val unobfJarName = "$baseName-unobf.jar" // input from shadowJar
        val obfJarName = "$baseName.jar" // final output name

        val unobfJar = file("build/libs/$unobfJarName")
        val finalJar = file("build/libs/$obfJarName")
        val script = file(scriptName)

        val zelixPath = project.findProperty("zelix") as String? ?: return@doLast
        val zelix = File(zelixPath)

        val temp = file("temp")
        temp.writeText(script.readText().replace(
            $$"${version}",
            project.version.toString()))

        Files.copy(
            temp.toPath(),
            zelix.resolve(scriptPath).toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )

        temp.delete()

        /*
         * Plugin Obfuscation
         */

        val zelixObfplg = zelix.resolve("obfplg/$obfJarName")

        // copy latest jar (unobf -> zelix with normal name)
        Files.copy(
            unobfJar.toPath(),
            zelixObfplg.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )

        val runZkm = zelix.resolve("!run_zkm.bat")

        val process = ProcessBuilder("cmd", "/c", runZkm.path, scriptPath)
            .directory(zelix)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        println(output)

        val obfuscatedJar = zelix.resolve("obf output/$obfJarName")

        // copy back obfuscated file with normal name
        Files.copy(
            obfuscatedJar.toPath(),
            finalJar.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )

        println("Obfuscated jar copied back to build/libs/")
    }
}