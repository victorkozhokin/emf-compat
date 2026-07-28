# EMF Compat

A modular, client-side compatibility framework for **Minecraft** that lets poses added by other mods survive **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations.

Resource-pack animations through EMF override everything the player model does, so poses from other mods — swinging a weapon, carrying a chest, aiming a gun, sitting down — disappear. Each addon here fixes that for one mod, while leaving the rest of the body animated by the pack.

**[EMF Compat Core](core/README.md)** is the shared piece every addon needs, and holds the settings screen for all of them.

## Addons

| Addon | Makes these work with EMF | Modrinth |
|---|---|---|
| [Better Combat](extensions/better_combat/README.md) | Weapon swings, attack poses, RPG Series spellcasting | [link](https://modrinth.com/project/emf-compat-better-combat) |
| [Carry On](extensions/carryon/README.md) | Carrying blocks, chests and mobs | [link](https://modrinth.com/project/emf-compat-carry-on) |
| [Create](extensions/create/README.md) | Skyhook, grappling hooks, handles, jetpacks, physics objects | [link](https://modrinth.com/project/emf-compat-create) |
| [Exposure](extensions/exposure/README.md) | Photos, selfies and tripod cameras | [link](https://modrinth.com/project/emf-compat-exposure) |
| [Gliders](extensions/gliders/README.md) | Paragliders, Gliders, Reliable Gliders | [link](https://modrinth.com/project/emf-compat-gliders) |
| [Horse Sync](extensions/horse-sync/README.md) | Sitting steady on an animated horse | [link](https://modrinth.com/project/emf-compat-horse-sync) |
| [Immersive Melodies](extensions/immersive_melodies/README.md) | Playing instruments | [link](https://modrinth.com/project/emf-compat-immersive-melodies) |
| [Iron's Spells 'n Spellbooks](extensions/iron_spells/README.md) | Spellcasting poses | [link](https://modrinth.com/project/emf-compat-irons-spells-n-spellbooks) |
| [Not Enough Animations](extensions/nea/README.md) | Eating, rowing, riding, petting and more | [link](https://modrinth.com/project/emf-compat-not-enough-animations) |
| [Quark](extensions/quark/README.md) | Quark emotes | [link](https://modrinth.com/project/emf-compat-quark) |
| [Supplementaries](extensions/supplementaries/README.md) | Flute, slingshot, bubble blower | [link](https://modrinth.com/project/emf-compat-supplementaries) |
| [TACZ](extensions/tacz/README.md) | Holding, aiming and reloading guns | [link](https://modrinth.com/project/emf-compat-tacz) |
| [Take a Seat](extensions/takeaseat/README.md) | Sitting on chairs and benches | [link](https://modrinth.com/project/emf-compat-take-a-seat) |
| [WATUT](extensions/watut/README.md) | Typing, menu and idle status poses | [link](https://modrinth.com/project/emf-compat-watut) |

Also in this repository: **[Instant Death](extensions/instant_death/README.md)** — a standalone utility mod (not an EMF Compat addon) that removes mobs instantly on death.

## Build

```bash
./gradlew build
```

Built jars are placed in `upload/<Project>/<loader>/<minecraft-version>/`.
