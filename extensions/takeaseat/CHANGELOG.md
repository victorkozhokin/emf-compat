# EMF Compat: Take a Seat — Changelog

## Unreleased

### Build

- Forge 1.20.1: resolve Sitting+ by its canonical Modrinth project ID so builds with a fresh dependency cache pass Maven metadata validation. The Sitting+ version and in-game behaviour are unchanged

## 2.0.2

### Fixed

- Forge 1.20.1: the mod list now shows this addon's icon

### Dependencies

- Forge 1.20.1: corrected the minimum EMF Compat Core version to 2.3.0

## 2.0.1

### Fixed

- An error while keeping the seated pose no longer makes EMF stop animating the player's model for the rest of the session

## 2.0.0

### Changed

- Fabric 26.1.2: works with Take a Seat 1.0.3, the release that renamed Seatify. Older Seatify builds are no longer supported

### Dependencies

- Requires Entity Model Features 3.3.2 and EMF Compat Core 2.0.0

## 1.0.1 — Forge 1.20.1

### Fixed

- Fixed the addon asking for Better Combat instead of Sitting+, so it no longer refuses to start without a mod it never used

## 1.0.0

### Added

- First release
- Sitting poses stay visible instead of being overwritten by your resource pack
- Armour sits down with you instead of floating in a standing pose
- Your head still turns freely while seated
- Added a config tab
