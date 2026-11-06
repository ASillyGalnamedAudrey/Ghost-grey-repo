# Pokémon Ghost Grey – Android player

A small Android app for playing the fan-made ROM hack **Pokémon Ghost Grey** on your phone.

**This repository and its releases contain no Nintendo / Game Freak game data.**
The app ships only the hack's own patch (`.bps`). You provide your own legally
obtained copy of **Pokémon FireRed v1.0 (USA)**, and the app patches it on your device.

## Install
1. Download `GhostGrey-Android.apk` from the Releases page and install it
   (Android will ask you to allow installs from this source).
2. Open the app, tap **Choose your FireRed ROM**, and pick your FireRed file.
   - It must be the original v1.0 USA release (CRC32 `DD88761C`). The app checks this.
3. The app patches and verifies the game, then tap **Play**.

## Controls
On-screen D-pad, A/B, L/R, Start/Select. **MENU** has save/load state and quit.
**FF** toggles 2× fast-forward. Bluetooth/USB gamepads work too.
In-game saves are stored privately on the device.

## Patches
- `app/src/main/assets/GhostGrey03.bps` – main patch (used by the app)
- `patches/GhostGrey_Korean_v103.bps` – Korean translation, applied on top of the main result
  (not yet integrated in the app; can be applied manually with any BPS patcher)

## Building
GitHub Actions builds the APK (see `.github/workflows/build.yml`). Run it from the Actions tab,
or publish a release with a tag like `v1.0.0` to attach the APK automatically.

## Credits & licenses
- Pokémon Ghost Grey – by its original hack team. All credit for the game belongs to them.
- Emulation: [mGBA](https://mgba.io) libretro core (MPL 2.0), run through
  [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) (GPL-3.0).
  Because of LibretroDroid, this app is distributed under GPL-3.0.
- Pokémon is a trademark of Nintendo, Creatures Inc. and Game Freak. This is an unofficial,
  non-commercial fan project and is not affiliated with or endorsed by them.
