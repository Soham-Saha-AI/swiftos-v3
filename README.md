# SwiftOS v3

**Created by Soham Saha** for Android TV and Google TV. SwiftOS is a customized launcher derived from CleanYourTV; upstream copyright and license notices remain included.

## Current version

SwiftOS **3.0.1, build 24** (`com.gathin.tvhome`). **[Download SwiftOS v3 for Android TV](https://github.com/Soham-Saha-AI/swiftos-v3/releases/latest/download/SwiftOS-v3-TV-build24.zip)** · [All releases](https://github.com/Soham-Saha-AI/swiftos-v3/releases). Android updates must use the compatible release signing key; private signing material is not stored in this repository. Community forks can build and distribute their own package, but a different signing key cannot update an existing installation under the same app identity.

## Special features

- A Liquid Glass Android TV interface with five themes and adjustable logo, opacity and font choices.
- Home and Library layouts with larger app tiles, headers and controls, designed for D-pad and remote navigation.
- Multiple languages and existing CleanYourTV launcher functions, including Inputs, Wi-Fi and Settings access.
- Local automatic wallpapers selected by the TV's weather and time period, with fallback selection when data or photos are unavailable.
- An optional companion transfer flow in TV Optimizer that prepares and verifies bounded photo batches before copying them to the TV.
- Bounded artwork caching and background work, with optimizations validated through focused tests rather than broad performance promises.

## Compatibility and validation

Designed for Android TV / Google TV 8 or later. Build 24 was tested on one Android TV. Its validation included 257 Android JVM tests, release lint, R8, signer continuity, and bounded on-device wallpaper checks. The tested cases included cloudy/evening selection, a generic fallback, Home display, and restoring Auto wallpaper to its original Off state. This is bounded validation for one device, not certification for every TV. Long-duration playback and some physical remote checks remain open.

The project is a selective CleanYourTV derivative and is not a full upstream CleanYourTV v3 rebase. See [WALLPAPER-ENGINE.md](docs/WALLPAPER-ENGINE.md) for photo setup and limits, and [CREDITS.md](CREDITS.md) for attribution and licensing.

## Testing warning — limited device coverage

Build 24 passed 257 Android JVM tests, release lint, R8 and signer-continuity checks. Bounded on-device wallpaper checks were completed on one Android TV, including a weather/time selection, fallback, and restoration of the original Auto setting. This is limited testing, not certification for all Android TV devices. Long-duration playback, every weather condition and time transition, and several physical remote-button checks remain untested. Other manufacturers, firmware versions, remotes and display sizes may behave differently.

Please keep a compatible backup and test carefully before setting SwiftOS as the default Home app. Do not remove the previous launcher until you have confirmed Home and recovery behavior on your TV.

## Test and improve SwiftOS

Users with different Android TV and Google TV models are invited to test SwiftOS and help improve compatibility, remote behavior, accessibility, documentation and translations. File an issue with the device model, Android version, firmware/build, SwiftOS version, reproduction steps and expected versus actual behavior. Never attach TV serial numbers, private IPs, account details, private preferences or personal photo collections. Pull requests are welcome; official releases are prepared by the maintainer and require the compatible signing key.

## Build

Open this directory in Android Studio or build with Gradle using JDK 17 and Android SDK platform 34. Do not commit `local.properties`, generated `build/` folders, signing keys, keystores or TV data. For releases that can update the official package, use the maintainer's compatible private signing key through a secure signing workflow; never publish that key.

Contributions are welcome through issues and pull requests. Official releases are published by the maintainer.
