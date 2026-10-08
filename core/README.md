# EMF Compat Core

## [Modrinth](https://modrinth.com/project/emf-compat-core)

Shared framework for the **EMF Compat** family of mods, plus the in-game settings screen for all of them.

**[Entity Model Features](https://modrinth.com/mod/entity-model-features)** lets resource packs animate the player. The catch: those animations override everything, so poses added by other mods — swinging a weapon, carrying a block, aiming a gun, sitting on a chair — get replaced by the pack's idle animation.

The EMF Compat addons fix that, one mod at a time. This core is the shared piece they all need. On its own it adds nothing visible, so install it together with at least one addon.

## Addons

| Addon | Makes these work with EMF |
|---|---|
| **[Better Combat](https://modrinth.com/project/emf-compat-better-combat)** | Weapon swings and attack poses (plus RPG Series spellcasting) |
| **[Carry On](https://modrinth.com/project/emf-compat-carry-on)** | Carrying blocks, chests and mobs |
| **[Create](https://modrinth.com/project/emf-compat-create)** | Skyhook, grappling hooks, handles, jetpacks and physics objects |
| **[Exposure](https://modrinth.com/project/emf-compat-exposure)** | Taking photos, selfies and tripod cameras |
| **[Gliders](https://modrinth.com/project/emf-compat-gliders)** | Paragliders, Gliders and Reliable Gliders |
| **Hackers 'n Slashers** | Attacks, blocks, rolls and weapon stances |
| **[Touch'n Motion](../extensions/touch_n_motion/README.md)** | Grounded feet, hands that interact with blocks and animals, horse sync, rowing and other procedural poses |
| **Horse Sync** *(archived)* | Horse sync is now part of Touch'n Motion; the old addon's source remains available |
| **[Immersive Melodies](https://modrinth.com/project/emf-compat-immersive-melodies)** | Playing instruments |
| **[Iron's Spells 'n Spellbooks](https://modrinth.com/project/emf-compat-irons-spells-n-spellbooks)** | Spellcasting poses |
| **[Not Enough Animations](https://modrinth.com/project/emf-compat-not-enough-animations)** | Eating, rowing, riding, petting and more |
| **ParCool** | Vaults, wall runs, rolls and climbing |
| **[Quark](https://modrinth.com/project/emf-compat-quark)** | Quark emotes |
| **[Supplementaries](https://modrinth.com/project/emf-compat-supplementaries)** | Flute, slingshot and bubble blower |
| **[TACZ](https://modrinth.com/project/emf-compat-tacz)** | Holding, aiming and reloading guns |
| **[Take a Seat](https://modrinth.com/project/emf-compat-take-a-seat)** | Sitting on chairs and benches |
| **[WATUT](https://modrinth.com/project/emf-compat-watut)** | Typing, menu and idle status poses |

## What it does

When another mod poses your character, the addon takes a snapshot of that pose and the core puts it back after EMF has animated the model — so you see the mod's pose instead of the resource-pack one. Armour follows the same pose, so it stays on your body.

When a mod takes a limb or lets it go, the pose blends into the pack's animation over about a fifth of a second instead of snapping in one frame. The core also fixes crouching while a mod animates the player — the model no longer sinks into the ground or hops up with every attack from a crouch — and keeps the pack animating while another mod plays an animation through Player Animation Library, where EMF would otherwise freeze the whole model.

Only the parts a mod actually needs are taken over. Everything else keeps its resource-pack animation, so your character still breathes, walks and idles normally while holding a gun or carrying a chest. When two mods want the same limb at once, the more specific one wins — for example an attack takes the arms while a riding pose keeps the legs.

The core also provides the shared support for foot placement and hand contacts used by Touch'n Motion, on every supported loader and Minecraft version.

## Settings

All addons share one settings screen, with a tab per addon: **Mods → EMF Compat Core → Config**.

Each installed addon also has a **Config** button in its own mod-list entry. It opens this shared screen directly on that addon's tab; **Done** returns to the mod list. Fabric requires Mod Menu to show the buttons; Forge and NeoForge use their built-in mod lists.

Settings and addon tabs scroll with the mouse wheel. Click a category heading to fold or unfold it; child options appear under their parent. Numeric options use fixed steps. Hover over an option to read its description, and use **Reset** to restore the selected tab's defaults. Changes apply without restarting the game.

The first tab, **Core**, has these options:

- **EMF compatibility** — the global switch. Turn it off and every installed addon stops working at once: the game looks exactly as if only EMF and your resource pack were installed. No restart needed, and each addon keeps its own settings for when you turn it back on.
- **Smooth pose transitions** — blends poses in and out instead of switching in one frame.
- **Crouch fix** — keeps the crouch height right while a mod animates the player.

Most addons offer:

- **EMF compatibility** — master switch for that addon.
- **Arm sync** — *Body-follow* keeps a captured pose attached to your moving torso; *Rotation-only* is the older, simpler behaviour, but in some cases it gives smoother animations.

Addons that cover several mods (Create, Gliders) add a toggle per supported mod, so you can turn individual features off.

## Dependencies

- **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** 3.3.2+ (3.3.11+ on Minecraft 26.3)
- **[Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures)** (required by EMF)
- A player animation resource pack, such as **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)**

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |
| Forge | 1.20.1 |
| Fabric | 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 |

Addon coverage varies per loader — check each addon's page.

## Build

```bash
./gradlew :core-neoforge-1.21.1:build
./gradlew :core-forge-1.20.1:build
./gradlew :core-fabric-1.21.1:build
./gradlew :core-fabric-1.21.11:build
./gradlew :core-fabric-26.1.2:build
./gradlew :core-fabric-26.2:build
./gradlew :core-fabric-26.3:build
```

enjoy ^_^
