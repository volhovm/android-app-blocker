package dev.volhovm.appblocker

enum class Mode { FULL, DUMB }

/** A HOME activity. [priority] is the intent-filter priority: the boot-time FallbackHome of Settings has a negative one. */
data class HomeApp(val packageName: String, val className: String, val isSystem: Boolean, val priority: Int) {
    val flatName: String get() = "$packageName/$className"
}

/** Pure decisions, without Android framework calls, so that JVM unit tests can cover them. */
object Policy {
    fun packagesToSuspend(
        launchable: Set<String>,
        allowlist: Set<String>,
        homePackages: Set<String>,
        inputMethods: Set<String>,
        self: String,
    ): Set<String> = launchable - allowlist - homePackages - inputMethods - self

    /** Order of preference: the remembered launcher, the current default, a system launcher, any launcher. */
    fun chooseFullLauncher(candidates: List<HomeApp>, remembered: String?, currentDefault: String?, self: String): HomeApp? {
        val real = candidates.filter { it.packageName != self && it.priority >= 0 }
        return real.firstOrNull { it.flatName == remembered }
            ?: real.firstOrNull { it.packageName == currentDefault }
            ?: real.firstOrNull { it.isSystem }
            ?: real.firstOrNull()
    }

    const val YUBICO_VENDOR_ID = 0x1050

    fun isKeyPresent(usbVendorIds: Collection<Int>): Boolean = YUBICO_VENDOR_ID in usbVendorIds
}
