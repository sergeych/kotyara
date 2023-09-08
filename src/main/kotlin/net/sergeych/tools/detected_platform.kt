package net.sergeych.tools

@Suppress("unused")
enum class PlatformType {
    Linux, Windows, Macos, Unknown;

    val isLinux: Boolean get() = this == Linux
}

val detectedPlatform: PlatformType by lazy {
    val p = System.getProperty("os.name").lowercase()
    println("system reports: $p")
    when {
        p == "linux" -> PlatformType.Linux
        p.startsWith("windows") -> PlatformType.Windows
        // I'm not sure about it as there is no reason to run it on mac
        p == "macos" -> PlatformType.Macos
        else -> PlatformType.Unknown
    }
}
