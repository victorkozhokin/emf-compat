# libs

Drop unreleased **Entity Model Features** / **Entity Texture Features** jars here to build the
26.2 modules against them. The jars themselves are git-ignored: they are Traben's pre-release
builds and not ours to redistribute.

Expected names (the build picks them up by glob):

```
entity_model_features-<version>-26.2-fabric.jar
entity_texture_features-<version>-26.2-fabric.jar
```

With no jars present the 26.2 modules fall back to the published Modrinth releases pinned in
`gradle.properties`, so a clean checkout still builds.
