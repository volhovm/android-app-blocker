package dev.volhovm.appblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {

    @Test
    fun suspendsLaunchableAppsThatAreNotOnTheAllowlist() {
        val result = Policy.packagesToSuspend(
            launchable = setOf("com.instagram.android", "com.dialer", "org.browser"),
            allowlist = setOf("com.dialer"),
            homePackages = emptySet(),
            inputMethods = emptySet(),
            self = "dev.volhovm.appblocker",
        )
        assertEquals(setOf("com.instagram.android", "org.browser"), result)
    }

    @Test
    fun neverSuspendsItselfLaunchersOrKeyboards() {
        val result = Policy.packagesToSuspend(
            launchable = setOf("dev.volhovm.appblocker", "com.launcher", "com.keyboard", "com.instagram.android"),
            allowlist = emptySet(),
            homePackages = setOf("com.launcher", "dev.volhovm.appblocker"),
            inputMethods = setOf("com.keyboard"),
            self = "dev.volhovm.appblocker",
        )
        assertEquals(setOf("com.instagram.android"), result)
    }

    private val self = HomeApp("dev.volhovm.appblocker", "dev.volhovm.appblocker.MinimalLauncherActivity", isSystem = false, priority = 0)
    private val stock = HomeApp("com.android.launcher3", "com.android.launcher3.Launcher", isSystem = true, priority = 0)
    private val niagara = HomeApp("bitpit.launcher", "bitpit.launcher.ui.HomeActivity", isSystem = false, priority = 0)
    private val fallbackHome = HomeApp("com.android.settings", "com.android.settings.FallbackHome", isSystem = true, priority = -1000)

    @Test
    fun fullLauncherIsTheRememberedOneWhenItIsStillInstalled() {
        val result = Policy.chooseFullLauncher(listOf(self, stock, niagara), remembered = niagara.flatName, currentDefault = stock.packageName, self = self.packageName)
        assertEquals(niagara, result)
    }

    @Test
    fun fullLauncherIsTheCurrentDefaultWhenNothingIsRemembered() {
        val result = Policy.chooseFullLauncher(listOf(self, stock, niagara), remembered = null, currentDefault = niagara.packageName, self = self.packageName)
        assertEquals(niagara, result)
    }

    @Test
    fun fullLauncherFallsBackToTheSystemLauncherWhenTheDefaultIsThisApp() {
        val result = Policy.chooseFullLauncher(listOf(niagara, self, stock), remembered = "gone.launcher/.Home", currentDefault = self.packageName, self = self.packageName)
        assertEquals(stock, result)
    }

    @Test
    fun fullLauncherIsNeverTheBootFallbackHome() {
        val result = Policy.chooseFullLauncher(listOf(self, fallbackHome), remembered = null, currentDefault = fallbackHome.packageName, self = self.packageName)
        assertNull(result)
    }

    @Test
    fun keyIsPresentOnlyWhenAYubicoDeviceIsAttached() {
        assertTrue(Policy.isKeyPresent(listOf(0x18d1, 0x1050)))
        assertFalse(Policy.isKeyPresent(listOf(0x18d1)))
        assertFalse(Policy.isKeyPresent(emptyList()))
    }
}
