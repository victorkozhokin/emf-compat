# EMF Compat: Take a Seat

A small client-side mod that pauses **[Entity Model Features](https://www.curseforge.com/minecraft/mc-mods/entity-model-features)** player animations while **[Take a Seat](https://www.curseforge.com/minecraft/mc-mods/take-a-seat)** sitting poses are active.

## Features

- Pauses EMF player animations while Take a Seat sitting poses are active.
- Sitting poses stay visible instead of being overwritten by resource-pack animations.
- Smooth restore when the sitting pose ends.

## Dependencies

- [Take a Seat](https://www.curseforge.com/minecraft/mc-mods/take-a-seat) 1.0.1+
- [Entity Model Features](https://www.curseforge.com/minecraft/mc-mods/entity-model-features) 3.2.4+
- [Entity Texture Features](https://www.curseforge.com/minecraft/mc-mods/entity-texture-features-fabric) (required by EMF)
- EMF Compat Core

## Build

```bash
./gradlew :takeaseat-neoforge-1.21.1:build
./gradlew :takeaseat-fabric-1.21.11:build
./gradlew :takeaseat-fabric-26.1.2:build
./gradlew :takeaseat-fabric-26.2:build
```

enjoy ^_^
