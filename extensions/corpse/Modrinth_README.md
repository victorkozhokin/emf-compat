# EMF Compat: Corpse

Keeps Corpse death bodies still when using Entity Model Features player models.

Brings **Corpse** dead bodies in line with **Entity Model Features**.

When a resource pack replaces the player model with an animated Entity Model Features variant, Corpse's internal dummy player can inherit those animations — making corpses twitch, walk or otherwise move when they should stay frozen in death. This addon forces the vanilla model and pauses EMF animations only while the Corpse renderer is drawing a body, so corpses keep Corpse's intended pose.