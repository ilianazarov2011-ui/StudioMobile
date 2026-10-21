# Roblox Studio Mobile

Android app that runs the Windows version of Roblox Studio through Wine and Box64.
Port by Narezany. Package: `cat.narezany.studio`.

Unofficial, not affiliated with Roblox Corporation.

## What it does

- Material 3 launcher: unpacks the Linux userland, downloads the latest Roblox Studio
  straight from Roblox's CDN, launches it.
- Sign-in goes through the phone browser. The `roblox-studio-auth:` link coming back
  from the browser is handed to the running Studio.
- Studio keyboard: shortcuts (play, undo, tools, group...), full keys, F-keys and symbols,
  with show and hide buttons. Ctrl, Shift and Alt latch.
- Mouse buttons (L, M, R) that stay held while a finger drags: right button for
  camera rotation, middle for panning.
- Touchpad or direct touch, two finger scroll for zoom.

## Based on

[Winlator](https://github.com/brunodev85/winlator) by brunodev85 (LGPL-2.1): X server,
Wine/Box64 environment, graphics drivers. The Java package stays `com.winlator`, the
Android `applicationId` is `cat.narezany.studio`. Also Wine, Box64, DXVK, VKD3D, Mesa
Turnip and the Termux glibc patches.

## Building

Needs JDK 17, Android SDK (platform 35, build-tools, NDK r24 `24.0.8215888`,
CMake 3.22.1), Python 3 and `zstd`.

```sh
tools/fetch_upstream.sh          # clones Winlator and its app submodule
tools/prepare_assets.py upstream-winlator/app/app/src/main/assets app/app/src/main/assets
cd app && ./gradlew :app:assembleDebug
```

`prepare_assets.py` rewrites the install path baked into the glibc binaries
(`/data/data/com.winlator/files/rootfs`) to `/data/data/cat.narezany.studio/r////`, the
same length with extra slashes. Without it the userland does not start under another
package name.

Release builds are signed with `signing/keystore.properties` (kept out of git).

## Layout

- `app/app/src/main/java/cat/narezany/studio` - launcher, installer, settings, session overlay.
- `app/app/src/main/java/com/winlator` - Winlator core with a few changes
  (`XServerDisplayActivity`, `TouchpadView`).
- `tools/` - asset preparation, logo generator, Waydroid helper.

## Testing without a phone

`tools/wd` is an adb shortcut for a Waydroid container on `192.168.240.112`. Waydroid is
x86_64 Android, so it covers the interface, install flow and the keyboard, not Wine itself.
