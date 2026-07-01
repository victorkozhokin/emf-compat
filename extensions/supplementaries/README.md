# EMF Compat: Supplementaries

Client-side addon that preserves **Supplementaries** item-use arm poses when **Entity Model Features** player animations are active.

## Supported items

- **Flute** — two-handed playing pose.
- **Slingshot** — aiming pose.
- **Bubble Blower** — blowing pose.

## How it works

Supplementaries (via Moonlight) animates the player arms in `HumanoidModel#setupAnim`. EMF normally overwrites those poses with resource-pack animations. This addon captures the arm poses after Supplementaries sets them and lets the Core mixins restore them after EMF runs.

First-person hand rendering is handled separately so the item pose stays visible in first person.

## Modules

- `extensions/supplementaries/neoforge/1.21.1`
- `extensions/supplementaries/forge/1.20.1`

## Dependencies

- `emf_compat_core`
- `supplementaries`
- `entity_model_features`
