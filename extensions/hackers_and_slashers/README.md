# EMF Compat: Hackers 'n Slashers

A small client-side mod that makes **Hackers 'n Slashers** combat poses work correctly with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)** player models.

Without it, swinging a weapon looks like nothing is happening — the resource-pack animation keeps your arms in their idle motion while the attack plays.

The same combat animation and correctly attached held items are preserved in first person.

## Covered Poses

| Pose | Captured parts |
|---|---|
| Attacks, blocks, rolls and other actions | Both arms, and the legs while standing still |
| Weapon stance (off by default) | Both arms |

The body always stays under EMF's control, so resource-pack animations keep playing while you fight. During attacks and blocks the head keeps looking where you aim, even while the swing twists the torso.

## Config

Open the in-game config screen (Mods → EMF Compat Core → Config) and pick the **Hackers 'n Slashers** tab:

| Option | What it does |
|---|---|
| EMF compatibility | Master switch — turn the whole addon off to get plain Hackers 'n Slashers behaviour. |
| Arm sync | **Body-follow** keeps the pose attached to your moving torso. **Rotation-only** is the older, simpler behaviour, but in some cases it gives smoother animations. |
| Action legs | Holds the legs too while you stand still, so a lunge or a roll keeps its stance. Moving always keeps the pack's walk cycle. |
| Weapon stances | Holds the stance a carried weapon puts you in. Off by default: it takes both arms for as long as the weapon is held. |
| Body and feet in attacks (experimental) | Off by default; needs **Touch'n Motion**. The torso leans into the attack, and while you stand still the feet step into a stance with the weight over it instead of holding the animation's legs. |
| Head during attacks | **Follows the camera** keeps the head on where you look while an attack or a block twists the body. **Follows the animation** lets the head turn with the body, as the attack animation keys it. |

## Notes

In first person the addon follows Hackers 'n Slashers' own visibility decision. Attacks, blocks
and weapon poses that request its full-model first-person renderer replace EMF's custom hands for
the duration of the animation; actions that H&S deliberately hides, such as rolls and dashes,
remain hidden.

With **First Person Model** installed, its body render is suppressed for those frames so H&S can
draw the combat arms once, without duplicated hands or held items.

Hackers 'n Slashers declares Better Combat incompatible, so this addon and **EMF Compat: Better
Combat** are never useful at the same time.

## Dependencies

- Hackers 'n Slashers 2.0-beta2.5+ (checked with 2.0-beta3)
- [Entity Model Features](https://modrinth.com/mod/entity-model-features) 3.3.2+
- [Entity Texture Features](https://modrinth.com/mod/entitytexturefeatures) (required by EMF)
- EMF Compat Core 2.1.0+

## Supported loaders / versions

| Loader | Minecraft versions |
|--------|-------------------|
| NeoForge | 1.21.1 |

## Build

```bash
./gradlew :hackers-and-slashers-neoforge-1.21.1:build
```

enjoy ^_^
