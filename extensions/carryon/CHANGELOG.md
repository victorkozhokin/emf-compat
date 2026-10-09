# EMF Compat: Carry On — Changelog

## 2.1.0

- Requires EMF Compat Core 2.3.0
- Fabric 1.21.11 and newer: carrying a villager no longer poses the player with the villager's own animation, and the carried mob is drawn by its own animation instead of the player's
- Added Fabric 1.21.1 and Fabric 26.3 builds, and the Fabric 26.2 build is back
- Fixed carried objects drifting away from the hands in the mirrored third-person camera
- Frozen now keeps a per-entity EMF pose in both first and third person, so another visible mob of the same type cannot animate the carried model
- Animated keeps the carried mob's own EMF animation while normalising Carry On's render interpolation to prevent shaking
- Animated mobs are actually alive in your hands now, in first person too: Carry On redraws a fresh copy of the mob every frame, which left its animation clock stuck at zero
- Frozen now holds on Fabric 1.21.11, 26.1.2 and 26.2, in both views, and carried mobs are handled in first person there
- Fixed a crash on Forge 1.20.1 when a carried mob was drawn in first person
- Carried mobs face the way Carry On means them to, whatever way they were facing when picked up

## 2.0.0

- Requires Entity Model Features 3.3.2 and EMF Compat Core 2.0.0
- Fixed a startup crash with EMF 3.3

## 1.1.0

- Added a config tab with arm sync and a toggle for the carried mob's model
- The carried block or mob stays in your hands while you move more accurately
