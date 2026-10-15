# EMF Compat: Armor Packs

A small client-side mod for **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** armour packs that bring their own armour models and their own texture layout, such as **Armory Conglomery**.

Such a pack redraws the armour textures it knows for its new model. Every other armour piece (a mod's armour the pack has no texture for, or a vanilla one it skipped) still has a texture drawn for the vanilla model, and on the pack's model it comes out scrambled.

With this addon such a piece is worn with the **vanilla model** instead, in the same pose, so it looks the way its author drew it. Armour the pack did draw keeps the pack's look, and both can be worn at once.

Nothing has to be redrawn, and it works for any mod's armour, on players and on mobs.

## How it tells them apart

An armour model in a pack names the size of the texture it was made for (`textureSize`). A texture of another shape was not drawn for it. Armory Conglomery's models are 64×64 and vanilla armour textures are 64×32. Higher-resolution textures of the same shape (128×64 for a 64×32 model) still count as fitting.

A pack model that names a texture of its own is left alone.

## Trims

Trims are textures too. A trim drawn for one model would land in the wrong place on the other, so it is left off.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Armor Packs** tab:

| Option | What it does |
|---|---|
| Vanilla model for other armour | Armour whose texture the pack did not draw for its model is worn with the vanilla model. |
| Leave off unfitting trims | A trim drawn for the other model is not drawn at all. |

## Supported versions

NeoForge 1.21.1.

## Known issues

- A pack that keeps the vanilla texture size but moves the texture layout around cannot be told apart this way.
- Only the first texture layer decides. Leather's dye overlay always follows its base layer.
