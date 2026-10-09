# EMF Compat: Core

A shared client-side library that lets all **EMF Compat** addons fix animation conflicts with **[Entity Model Features](https://www.curseforge.com/minecraft/mc-mods/entity-model-features)**.

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

## Projects

- **[Touch'n Motion](https://www.curseforge.com/minecraft/mc-mods/touch-and-motion)**
- **[EMF Compat: Not Enough Animations](https://www.curseforge.com/minecraft/mc-mods/emf-compat-not-enough-animations)**
- **[EMF Compat: Create](https://www.curseforge.com/minecraft/mc-mods/emf-compat-create)**
- **[EMF Compat: Better Combat](https://www.curseforge.com/minecraft/mc-mods/emf-compat-better-combat)**
- **[EMF Compat: Carry On](https://www.curseforge.com/minecraft/mc-mods/emf-compat-carry-on)**
- **[EMF Compat: Immersive Melodies](https://www.curseforge.com/minecraft/mc-mods/emf-compat-immersive-melodies)**
- **[EMF Compat: Quark](https://www.curseforge.com/minecraft/mc-mods/emf-compat-quark)**
- **[EMF Compat: Supplementaries](https://www.curseforge.com/minecraft/mc-mods/emf-compat-supplementaries)**
- **[EMF Compat: Exposure](https://www.curseforge.com/minecraft/mc-mods/emf-compat-exposure)**
- **[EMF Compat: Gliders](https://www.curseforge.com/minecraft/mc-mods/emf-compat-gliders)**
- **[EMF Compat: Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/emf-compat-irons-spells-n-spellbooks)**
- **[EMF Compat: TACZ](https://www.curseforge.com/minecraft/mc-mods/emf-compat-tacz)**
- **[EMF Compat: Take a Seat](https://www.curseforge.com/minecraft/mc-mods/emf-compat-take-a-seat)**
- **[EMF Compat: WATUT](https://www.curseforge.com/minecraft/mc-mods/emf-compat-watut)**


<div class="spoiler">

![Reaching for and pulling a lever](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_lever.webp?raw=true)
![Balancing on a narrow fence](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_fence.webp?raw=true)
![Body movement and leaning with the surroundings](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_lean.webp?raw=true)
![Brushing past crops](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_plant_reach.webp?raw=true)
![Using a lead and shearing animals](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_lead_and_shear.webp?raw=true)
![Casting and fishing](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_fishing.webp?raw=true)
![Shaking off water](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_afterwater.webp?raw=true)
![Rowing and riding in a boat](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_boat.webp?raw=true)
![Resting hands on a fence](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_fence_interact.webp?raw=true)
![Looking towards nearby creatures](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_look_at.webp?raw=true)
![Reacting to a sudden scare](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_fear.webp?raw=true)
![Turning a crank with the whole body](https://github.com/victorkozhokin/emf-compat/blob/main/resources/previews/touch%20and%20motion/touch_and_motion_crank.webp?raw=true)
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

enjoy ^_^
