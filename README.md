# android-app-blocker

The phone works normally when a YubiKey is in the USB-C port. When the key is not in the port, the phone becomes a "dumb phone":

- The app suspends all apps that are not on the allowlist. A suspended app does not open, does not show notifications and does not show in the recent-apps screen.
- A minimal launcher replaces the normal launcher.

The allowlist is resolved on the device: the default phone app, contacts, the default SMS app, the clock, the camera, maps, and Settings. If you did not select a default app for a function, only the preinstalled apps for it are allowed. Launchers, keyboards and this app are never suspended.

The app uses the Device Owner API. It does not need root.

## Development environment

All tools come from the Nix flake: JDK 17, Gradle, the Android SDK, `adb` and the emulator.

```sh
nix develop
gradle testDebugUnitTest      # unit tests of the pure logic (Policy)
gradle assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
```

## Test on the emulator

A new emulator has no accounts, so the device owner command works immediately.

```sh
nix develop
export ANDROID_AVD_HOME=$PWD/.avd
echo no | avdmanager create avd -n blocker -k "system-images;android-35;google_apis;x86_64" -d pixel_6
emulator -avd blocker &        # add -no-window for a headless emulator
adb wait-for-device
gradle installDebug
adb shell dpm set-device-owner dev.volhovm.appblocker/.AdminReceiver
```

The emulator has no YubiKey, so the phone goes to dumb mode at once. Debug builds have a receiver that replaces the USB check:

```sh
R=dev.volhovm.appblocker/.DebugReceiver
adb shell am broadcast -n $R --es key present   # full mode
adb shell am broadcast -n $R --es key absent    # dumb mode
adb shell am broadcast -n $R --es key real      # use the real USB state
adb shell am broadcast -n $R --es cmd release   # undo all changes and remove the device owner
adb shell am broadcast -n $R --es cmd status    # log USB devices, the allowlist and the apps that dumb mode suspends. No changes.
adb logcat -s AppBlocker
```

Only `adb shell` can send to this receiver: it needs the `DUMP` permission.

## Install on the phone

1. Enable USB debugging on the phone.
2. Sync your data and make a backup.
3. Do a force stop on messenger apps. These apps can add their account again automatically.
4. Remove all accounts in Settings → Passwords & accounts. Remove all extra users, work profiles and the Private Space.
5. Run these commands:
   ```sh
   nix develop
   gradle installDebug
   adb shell dpm set-device-owner dev.volhovm.appblocker/.AdminReceiver
   ```
6. Add the accounts again.

Android accepts the device owner command only if the phone has no accounts and only one user.

## Remove the app

Use the `release` command above. Then uninstall the app with `adb uninstall dev.volhovm.appblocker`.

The manifest has `android:testOnly="true"`. Thus, `adb shell dpm remove-active-admin dev.volhovm.appblocker/.AdminReceiver` also works. Use it if the debug receiver is not available. That command does not unsuspend the apps.

## How it works

| File | Function |
|---|---|
| `Policy.kt` | Pure decisions: which apps to suspend, which launcher is the full launcher, if the key is present |
| `ModeController.kt` | Applies the mode: suspends apps, sets the launcher, opens the home screen |
| `KeyMonitorService.kt` | Foreground service. It receives USB attach and detach, screen-on, unlock and new-app events. |
| `MinimalLauncherActivity.kt` | The home screen in dumb mode |
| `Apps.kt` | Package manager queries and the allowlist |
| `BootReceiver.kt` | Starts the service after a reboot and after an update |
| `src/debug/.../DebugReceiver.kt` | Debug commands |

The key check looks for USB vendor ID `0x1050` (Yubico). Any USB device can report this ID, so the check is not a security boundary.

When the key goes out, the app does these steps in this order: suspend the apps, set the minimal launcher, open the home screen. The launcher is set with `addPersistentPreferredActivity()`, so Settings cannot change it.
