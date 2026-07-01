# EMF Compat: Immersive Melodies

Makes Immersive Melodies instrument poses work correctly with animated EMF player models.

Brings **Immersive Melodies** playing animations to **Entity Model Features**.

When a resource pack replaces the player model with an animated Entity Model Features variant, the instrument-playing arm poses from Immersive Melodies are often overwritten by the resource-pack animation. This addon captures the arm poses after Immersive Melodies sets them, then restores them after EMF runs so the player keeps the correct playing posture while holding an instrument.

First-person hand rendering is handled separately so your own instrument pose stays visible in first person.