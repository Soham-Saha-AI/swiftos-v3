# SwiftOS v3

**Created by Soham Saha** for Android TV and Google TV. SwiftOS is a customized launcher derived from CleanYourTV; upstream copyright and license notices remain included.

## Current version

SwiftOS **3.0.2, build 25** (`com.gathin.tvhome`). **[Download SwiftOS v3 for Android TV](https://github.com/Soham-Saha-AI/swiftos-v3/releases/latest/download/SwiftOS-v3-TV-build25.zip)** · [All releases](https://github.com/Soham-Saha-AI/swiftos-v3/releases). Android updates must use the compatible release signing key; private signing material is not stored in this repository. Community forks can build and distribute their own package, but a different signing key cannot update an existing installation under the same app identity.

<img width="1920" height="1080" alt="tv-screenshot" src="https://github.com/user-attachments/assets/71436f10-8b92-4ce2-9d4e-c973cad2e465" />

## What's new in 3.0.2 (build 25)

- **Cold-boot persistence fix:** On-TV theme selections (`ThemePicker`) and icon background opacity / layout customizations now survive complete TV power cycles and cold reboots even when external storage mounts after Home starts. Settings save synchronously via `.commit()`, keep fallback copies in private internal app storage, and no longer reset TV customizations unless an explicit external CLI `theme_revision` is pushed.
- **App Library RecyclerView tuning:** Fixed-size adapter hint (`setHasFixedSize(true)`) and an 18-item offscreen view cache (`setItemViewCacheSize(18)`) in `AppLibraryDialog`.

## Special features

- A Liquid Glass Android TV interface with themes, adjustable logo, icon opacity (0%–100%) and font choices.
- Home and Library layouts with larger app tiles, headers and controls, designed for D-pad and remote navigation.
- Multiple languages and existing CleanYourTV launcher functions, including Inputs, Wi-Fi and Settings access.
- Local automatic wallpapers selected by the TV's weather and time period, with fallback selection when data or photos are unavailable.
- An optional companion transfer flow in [TV Optimizer](https://github.com/Soham-Saha-AI/tv-optimizer) that prepares and verifies bounded photo batches before copying them to the TV.
- Bounded artwork caching and background work, with optimizations validated through focused tests rather than broad performance promises.

## Compatibility and validation

Designed for Android TV / Google TV 8 or later. Build 25 (`3.0.2-swiftos`, `versionCode 25`) passed **260** Android JVM tests (257 core tests + 3 new cold-reboot persistence and preset lookup tests), release lint, R8, signer continuity, and 156 lossless-PNG pixel-integrity checks.

The cold-boot persistence fix and App Library optimization were installed and smoke-tested on one Android TV as build 24 (`3.0.1-swiftos`) on 2 November 2026; earlier bounded on-device wallpaper checks (cloudy/evening selection, generic fallback, Home display, and restoring Auto wallpaper to Off) were also performed on one Android TV. The renumbered build 25 APK itself passed all offline build and JVM test gates and has not yet been re-installed onto hardware. This is limited validation, not certification for every TV.

The project is a selective CleanYourTV derivative and is not a full upstream CleanYourTV v3 rebase. See [WALLPAPER-ENGINE.md](docs/WALLPAPER-ENGINE.md) for photo setup and limits, and [CREDITS.md](CREDITS.md) for attribution and licensing.

## Testing warning — limited device coverage

Other manufacturers, firmware versions, remotes and display sizes may behave differently. Long-duration playback, every weather condition and time transition, and several physical remote-button checks remain untested.

Please keep a compatible backup and test carefully before setting SwiftOS as the default Home app. Do not remove the previous launcher until you have confirmed Home and recovery behavior on your TV.

## Test and improve SwiftOS

Users with different Android TV and Google TV models are invited to test SwiftOS and help improve compatibility, remote behavior, accessibility, documentation and translations. File an issue with the device model, Android version, firmware/build, SwiftOS version, reproduction steps and expected versus actual behavior. Never attach TV serial numbers, private IPs, account details, private preferences or personal photo collections. Pull requests are welcome; official releases are prepared by the maintainer and require the compatible signing key.

## Build

Open this directory in Android Studio or build with Gradle using JDK 17 and Android SDK platform 34. Do not commit `local.properties`, generated `build/` folders, signing keys, keystores or TV data. For releases that can update the official package, use the maintainer's compatible private signing key through a secure signing workflow; never publish that key.

Contributions are welcome through issues and pull requests. Official releases are published by the maintainer.
