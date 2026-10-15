# EMF Compat: Equipments

A small client-side mod that keeps what a player or a mob wears in the right place and the right look under **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** animation and armour packs: armour, worn items and other equipment.

## Armour packs

Some armour packs, such as **Armory Conglomery**, bring their own armour models and their own texture layout. Such a pack redraws the armour textures it knows for its new model. Every other armour piece (a mod's armour the pack has no texture for, or a vanilla one it skipped) still has a texture drawn for the vanilla model, and on the pack's model it comes out scrambled.

With this addon such a piece is worn with the **vanilla model** instead, in the same pose, so it looks the way its author drew it. Armour the pack did draw keeps the pack's look, and both can be worn at once. Nothing has to be redrawn, and it works for any mod's armour, on players and on mobs.

**How it tells them apart.** An armour model in a pack names the size of the texture it was made for (`textureSize`). A texture of another shape was not drawn for it. Armory Conglomery's models are 64×64 and vanilla armour textures are 64×32. Higher-resolution textures of the same shape (128×64 for a 64×32 model) still count as fitting. A pack model that names a texture of its own is left alone.

**Trims** are textures too. A trim drawn for one model would land in the wrong place on the other, so it is left off.

## No Man's Land

The **tortoise shell** is placed by No Man's Land on the body as a whole, not on the torso, with a fixed tilt for crouching. When a pack such as **[Fresh Animations: Player Extension](https://modrinth.com/resourcepack/fa-player-extension)** turns, leans or bends the torso, the shell stays behind and hangs off the side. With this addon it is worn on the torso, like a chestplate, and goes wherever the torso goes.

The **Ancient Bronze Mask** already follows the head and needs nothing.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Equipments** tab:

| Group | Option | What it does |
|---|---|---|
| Armour packs | Vanilla model for other armour | Armour whose texture the pack did not draw for its model is worn with the vanilla model. |
| Armour packs | Leave off unfitting trims | A trim drawn for the other model is not drawn at all. |
| No Man's Land | Tortoise shell on the back | The tortoise shell follows the torso of an animated player. |

## Supported versions

NeoForge 1.21.1. No Man's Land is optional: its fix only switches on when it is installed.

## Known issues

- A pack that keeps the vanilla texture size but moves the texture layout around cannot be told apart this way.
- Only the first texture layer decides. Leather's dye overlay always follows its base layer.
