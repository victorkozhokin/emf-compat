# EMF Compat: Core

## [Modrinth](https://modrinth.com/project/emf-compat-core)

A shared client-side library that lets all **EMF Compat** addons fix animation conflicts with **[Entity Model Features](https://modrinth.com/mod/entity-model-features)**.

This mod does not add visible gameplay features on its own, but it is **required** by every EMF Compat addon.

## What it does

When another mod plays its own player animation — such as Create's Skyhook, Better Combat attacks or Carry On carrying — the addon captures the current body pose before EMF overrides it, then restores that pose so the external animation stays visible instead of being replaced by the resource-pack animation.

## Features

- Required shared library for all EMF Compat addons.
- Lets addon animations show correctly on animated EMF player models.
- Handles pose capture and restore behind the scenes.
- Keeps first-person and third-person poses consistent.

enjoy ^_^
