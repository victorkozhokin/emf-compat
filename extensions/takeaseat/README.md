# EMF Compat: Take a Seat

## [Modrinth](https://modrinth.com/project/emf-compat-take-a-seat)

A small client-side mod that makes **[Take a Seat](https://modrinth.com/mod/take-a-seat)** sitting poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

Without it, you sit down and your character keeps standing — the resource-pack animation plays right through the chair.

## Covered Poses

| Pose | Captured parts |
|---|---|
| Sitting on a chair or bench | Whole body except the head |

The head stays under EMF's control, so you can still look around naturally while seated.

## Features

- Sitting poses stay visible instead of being overwritten by the resource-pack animation.
- **Armour sits with you** — chestplate, leggings and boots follow the pose instead of floating in a standing position.
- Held items and your first-person hands match the seated pose.
- Works for other players too, so everyone actually sits on the furniture.
- Your character stands back up cleanly when you leave the seat.

## On Fabric 1.21.11+

Entity Model Features already pauses its own animations while Take a Seat plays its pose, so sitting itself looks right without this addon. What it still fixes there is the **armour** — chestplate, leggings and boots follow the seated pose instead of staying in a standing position.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Take a Seat** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain Take a Seat behaviour. |

## Known issues

On Fabric, armour can sit slightly loose on the body while you move or crouch, so skin layers may poke through it. This comes from how EMF sizes the armour model and is not specific to this addon.

## Dependencies

- [Take a Seat](https://modrinth.com/mod/take-a-seat) 1.0.1+
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- [Player Animation Library](https://modrinth.com/mod/player-animation-library) (required by Take a Seat)
- EMF Compat Core 1.0.1+

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |
| Fabric | 1.21.11, 26.1.2, 26.2 |

## Build

```bash
./gradlew :takeaseat-neoforge-1.21.1:build
./gradlew :takeaseat-fabric-1.21.11:build
./gradlew :takeaseat-fabric-26.1.2:build
./gradlew :takeaseat-fabric-26.2:build
```

enjoy ^_^
