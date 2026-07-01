# EMF Compat

A modular, client-side compatibility framework for **Minecraft** that makes **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations yield to animations added by other mods.

Each part of the project is published separately:

- **[EMF Compat Core](core/README.md)** — shared framework required by every addon.
- **[EMF Compat: Create](extensions/create/README.md)** — compatibility with Create's Skyhook, grabbing physics objects, and related addons.
- **[EMF Compat: Not Enough Animations](extensions/nea/README.md)** — compatibility with Not Enough Animations.
- **[EMF Compat: Carry On](extensions/carryon/README.md)** — compatibility with Carry On.
- **[EMF Compat: Corpse](extensions/corpse/README.md)** — compatibility with Corpse.
- **[EMF Compat: Immersive Melodies](extensions/immersive_melodies/README.md)** — compatibility with Immersive Melodies.
- **[EMF Compat: Supplementaries](extensions/supplementaries/README.md)** — compatibility with Supplementaries (Flute, Slingshot, Bubble Blower).
- **[EMF Compat: Quark](extensions/quark/README.md)** — compatibility with Quark emotes.

## Build

```bash
./gradlew build
```

Built jars are placed in `upload/<Project>/<loader>/<minecraft-version>/`.
