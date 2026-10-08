# EMF Compat: Core

A shared client-side library that lets all **EMF Compat** addons fix animation conflicts with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)**.

This mod does not add visible gameplay features on its own, but it is **required** by every EMF Compat addon.

## What it does

When another mod plays its own player animation — such as Create's Skyhook, Better Combat attacks or Carry On carrying — the addon captures the current body, arms or head pose before EMF overrides it, then restores that pose so the external animation stays visible instead of being replaced by the resource-pack animation.

## Features

- Required shared library for all EMF Compat addons.
- Lets addon animations show correctly on animated EMF player models.
- Handles pose capture and restore behind the scenes.
- Keeps first-person and third-person poses consistent.
- Poses blend smoothly into your resource pack's animation instead of snapping to it in one frame. Armour follows the blend.
- Fixes crouching while a mod animates the player — no more sinking into the ground or jumping up with every attack from a crouch.
- Keeps your pack animating while another mod plays an animation through Player Animation Library, instead of freezing the whole model.
- One settings screen for every addon, with a global compatibility switch, smooth pose transitions and a crouch fix.
- Shared support for the foot placement and hand contacts used by Touch'n Motion.

## Settings

All addons share one settings screen, with a tab per addon: **Mods → EMF Compat Core → Config**.

Each installed addon also has a **Config** button in its own mod-list entry. It opens this shared screen directly on that addon's tab; **Done** returns to the mod list. Fabric requires Mod Menu to show the buttons; Forge and NeoForge use their built-in mod lists.

Settings and addon tabs scroll with the mouse wheel. Click a category heading to fold or unfold it; child options appear under their parent. Numeric options use fixed steps. Hover over an option to read its description, and use **Reset** to restore the selected tab's defaults. Changes apply without restarting the game.

The **Core** tab controls **EMF compatibility**, **Smooth pose transitions** and **Crouch fix**. The global compatibility switch disables all installed addons while keeping their individual settings.

## Dependencies

- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.3.2+ (3.3.11+ on Minecraft 26.3).
- Entity Texture Features, required by EMF.
- A resource pack that animates the player model.

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |
| Forge | 1.20.1 |
| Fabric | 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 |

Addon coverage varies per loader — check each addon's page.

## Projects

- **[Touch'n Motion](https://modrinth.com/mod/touch-and-motion)** — procedural animations, including horse sync, rowing and hands that interact with the world.
- **[EMF Compat: Not Enough Animations](https://modrinth.com/project/emf-compat-not-enough-animations)**
- **[EMF Compat: Create](https://modrinth.com/project/emf-compat-create)**
- **[EMF Compat: Better Combat](https://modrinth.com/project/emf-compat-better-combat)**
- **[EMF Compat: Carry On](https://modrinth.com/project/emf-compat-carry-on)**
- **[EMF Compat: Immersive Melodies](https://modrinth.com/project/emf-compat-immersive-melodies)**
- **[EMF Compat: Quark](https://modrinth.com/project/emf-compat-quark)**
- **[EMF Compat: Supplementaries](https://modrinth.com/project/emf-compat-supplementaries)**
- **[EMF Compat: Exposure](https://modrinth.com/project/emf-compat-exposure)**
- **[EMF Compat: Gliders](https://modrinth.com/project/emf-compat-gliders)**
- **[EMF Compat: Iron's Spells 'n Spellbooks](https://modrinth.com/project/emf-compat-irons-spells-n-spellbooks)**
- **[EMF Compat: TACZ](https://modrinth.com/project/emf-compat-tacz)**
- **[EMF Compat: Take a Seat](https://modrinth.com/project/emf-compat-take-a-seat)**
- **[EMF Compat: WATUT](https://modrinth.com/project/emf-compat-watut)**
- **[EMF Compat: Parcool](https://modrinth.com/project/emf-compat-parcool)** Currently unavailable
- **[EMF Compat: Hackers 'n Slashers](https://modrinth.com/project/emf-compat-hackers-n-slashers)** Currently unavailable

<details>
<summary>Spoiler</summary>


![Supplementaries](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/supplementaries.webp?raw=true)
![Carry On](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/carry-on.webp?raw=true)
![Better Combat](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/better-combat.webp?raw=true)
![Quark](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/quark.webp?raw=true)
![Immersive Melodies](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/immersive-melodies.webp?raw=true)
![Exposure](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/exposure.webp?raw=true)
![Gliders](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/gliders.webp?raw=true)
![Iron's Spells 'n Spellbooks](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/irons-spells-n-spellbooks.webp?raw=true)
![TACZ](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/tacz.webp?raw=true)
![Take a Seat](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/take-a-seat.webp?raw=true)
![WATUT](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/watut.webp?raw=true)
![Parcool](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/parcool.webp?raw=true)
![Hackers 'n Slashers](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/hackers-and-slashers.webp?raw=true)

</details>


enjoy ^_^
