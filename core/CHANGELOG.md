# EMF Compat Core — Changelog

## 1.0.1

- Added an in-game settings screen with a tab for every installed addon — no more editing config files
- Added a global switch in the Core tab: turn off every addon at once, instantly, without touching their own settings
- The tab list now scrolls, so every addon is reachable in a windowed game
- Armour follows full-body poses on Fabric — sitting down no longer leaves your armour standing up
- When two mods want the same limb, the more specific one wins now (an attack takes the arms while a riding pose keeps the legs)
- Poses no longer flicker when several mods are active at once
- Every loader and game version now runs the same pose engine — Fabric and Forge got the smooth arm-follow, the head/hat fix and the limb priority that NeoForge already had
- Armour also follows full-body poses on Forge 1.20.1 now
