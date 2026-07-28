# Instant Death

A small mod that removes mobs the moment they die, skipping the falling-over death animation.

Vanilla keeps a dead mob lying on the ground for about half a second while it tips over and fades out. This mod deletes it immediately instead — the mob vanishes, drops its loot, and the fight moves on. Handy if you fight a lot of mobs and find the death animation slow or distracting, and it keeps piles of dying mobs from cluttering the screen.

## Features

- Mobs disappear the instant they die instead of playing the tip-over animation.
- Loot and experience drop exactly as they normally would.
- Death sounds and particles still play, so kills stay readable.
- Players can be excluded, so your own death still behaves normally.

## Config

The config file is created on first launch at `config/instant_death-common.toml`:

| Option | Default | What it does |
|---|---|---|
| `enabled` | `true` | Turn the instant removal on or off. |
| `exclude_players` | `false` | When on, players keep the normal vanilla death behaviour. |

enjoy ^_^
