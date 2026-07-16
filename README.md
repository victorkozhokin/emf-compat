# EMF Compat

A modular, client-side compatibility framework for **Minecraft** that makes **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player animations yield to animations added by other mods.

Each part of the project is published separately:

- **[EMF Compat Core](core/README.md)** — shared framework required by every addon.
- **[EMF Compat: Create](extensions/create/README.md)** — compatibility with Create's Skyhook, grabbing physics objects, and related addons.
- **[EMF Compat: Not Enough Animations](extensions/nea/README.md)** — compatibility with Not Enough Animations.
- **[EMF Compat: Carry On](extensions/carryon/README.md)** — compatibility with Carry On.
- **[EMF Compat: Immersive Melodies](extensions/immersive_melodies/README.md)** — compatibility with Immersive Melodies.
- **[EMF Compat: Supplementaries](extensions/supplementaries/README.md)** — compatibility with Supplementaries (Flute, Slingshot, Bubble Blower).
- **[EMF Compat: Quark](extensions/quark/README.md)** — compatibility with Quark emotes.

## Modrinth

Each addon is published as a separate project:

- **[EMF Compat Core](https://modrinth.com/project/emf-compat-core)**
- **[EMF Compat: Better Combat](https://modrinth.com/project/emf-compat-better-combat)**
- **[EMF Compat: Carry On](https://modrinth.com/project/emf-compat-carry-on)**
- **[EMF Compat: Create](https://modrinth.com/project/emf-compat-create)**
- **[EMF Compat: Horse Sync](https://modrinth.com/project/emf-compat-horse-sync)**
- **[EMF Compat: Immersive Melodies](https://modrinth.com/project/emf-compat-immersive-melodies)**
- **[EMF Compat: Not Enough Animations](https://modrinth.com/project/emf-compat-not-enough-animations)**
- **[EMF Compat: Quark](https://modrinth.com/project/emf-compat-quark)**
- **[EMF Compat: Supplementaries](https://modrinth.com/project/emf-compat-supplementaries)**

## Build

```bash
./gradlew build
```

Built jars are placed in `upload/<Project>/<loader>/<minecraft-version>/`.
