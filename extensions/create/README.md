# EMF Compat: Create

## [Modrinth](https://modrinth.com/project/emf-compat-create)

A small client-side mod that makes **[Create](https://modrinth.com/mod/create)** player animations — and those of its add-ons — work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Tested with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** and **[Detailed Animations](https://modrinth.com/resourcepack/detailed-animations)** but it should work with any player animation resource pack.

**Also works when grabbing physics objects!**

## Features

- You keep the Skyhook hanging pose while riding chains and ropes, instead of half your body sliding back into the resource-pack animation.
- Grappling hook poses stay correct while you swing and hang.
- Holding an Aeronautics handle keeps your hands on the handle, following your moving body.
- Grabbed physics objects and ragdoll grabs no longer fight with EMF animations.
- Jetpack flight (Cosmonautics and Create S&A) can play your resource pack's flying animation (currently works only with **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**).
- Your character's expressions and idle motion keep playing during all of this — Skyhook and grappling no longer freeze the model.
- Works for other players too.

## Supported Create add-ons

Everything below is optional — install what you like, the matching feature turns itself on. Each one can also be toggled off in the config.

| Add-on | What it covers |
|---|---|
| **[Create Aeronautics](https://modrinth.com/mod/create-aeronautics)** | Handle grip pose |
| **[Climbable Ropes](https://modrinth.com/mod/create-aeronautics-climbable-rope)** | Rope climbing |
| **[Create Grappling Hooks](https://modrinth.com/mod/create-grappling-hooks)** | Grapple and cable-trolley poses |
| **[Sable Ragdolls](https://modrinth.com/mod/sable)** | Grabbing and being grabbed |
| **[Create Cosmonautics](https://modrinth.com/mod/create-cosmonautics)** | Jetpack flight animation |
| **[Create Stuff 'N Additions](https://modrinth.com/mod/create-stuff-n-additions)** | Jetpack flight, grappling whisk, block picker |

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Create** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch for the whole addon. |
| Arm sync | **Body-follow** keeps captured arm poses attached to your moving torso. **Rotation-only** is the older, simpler behaviour, but in some cases it gives smoother animations. |
| Skyhook | Keeps Create's hanging pose while riding chains and ropes. |
| Aeronautics handle | Keeps your hands on the handle. |
| Grappling Hooks | Keeps the grapple pose. |
| Sable Ragdolls | Keeps the grab pose. |
| Cosmonautics flight | Plays your pack's flight animation on a Cosmonautics jetpack. |
| Stuff 'N Additions | Jetpack flight plus whisk and block-picker poses. |
| NEA item-swap fix | Stops Not Enough Animations from playing its item-swap animation mid-activity. |

Options for add-ons you don't have installed are hidden.

## Compatibility

- **[Freecam](https://modrinth.com/mod/freecam)** — Skyhook poses stay correct even when the camera is detached.
- **[First Person Model](https://modrinth.com/mod/first-person-model)** — Skyhook poses stay visible on your body in first person.
- **[Not Enough Animations](https://modrinth.com/mod/not-enough-animations)** — its item-swap animation is suppressed while you're skyhooking or grappling.

## Dependencies

- [Create](https://modrinth.com/mod/create)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.2.4+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 1.0.1+

## Build

```bash
./gradlew :create-neoforge-1.21.1:build
./gradlew :create-fabric-1.21.11:build
./gradlew :create-fabric-26.1.2:build
./gradlew :create-fabric-26.2:build
```

enjoy ^_^
