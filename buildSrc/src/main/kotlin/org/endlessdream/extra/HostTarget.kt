package org.endlessdream.extra

import java.util.Locale

/** Native target of the machine running Gradle, used when -Dplatform/-Darch are omitted. */
object HostTarget {
    @JvmStatic
    fun platform(): String {
        val os = System.getProperty("os.name").lowercase(Locale.ROOT)
        return when {
            os.contains("mac") -> "macos"
            os.contains("win") -> "windows"
            else -> "linux"
        }
    }

    @JvmStatic
    fun arch(): String {
        val arch = System.getProperty("os.arch").lowercase(Locale.ROOT)
        return if (arch == "aarch64" || arch == "arm64") "aarch64" else "x86-64"
    }
}
