# Oculus 1.12.2

An unofficial backport of [Oculus](https://github.com/Asek3/Oculus) (itself a fork of [Iris](https://github.com/IrisShaders/Iris)) to Minecraft **1.12.2** Forge.

OptiFine-style shader packs (BSL, Solas, etc.) running on Forge 1.12.2 without OptiFine.

## Status

Mostly functional. Tested primarily with BSL v10 on Windows and macOS.

**Working**
- Terrain, sky, entity, hand, water (reflections/refraction) rendering through the deferred gbuffer pipeline.
- Shadow maps with shader-pack distortion (terrain, entities, block entities).
- Shader pack selection + option editing GUI.
- Entity / block-entity ID uniforms.
- macOS GLSL-130 → built-in GLSL-110 fallback shadow path.

**Known limitations**
- No colored / translucent shadows (shadow pass is depth-only; stained glass casts grey).
- Shadow chunk visibility uses a non-culling frustum — re-renders all loaded sections per shadow frame. Functional but not optimal.
- Entity PBR (normal / specular maps for entities) not yet wired.
- Particle pipeline limited compared to 1.16+.

## Requirements

- Minecraft **1.12.2** with **Forge 14.23.5.2860+**
- [**MixinBooter**](https://www.curseforge.com/minecraft/mc-mods/mixin-booter) (8.9+)
- **Vintagium** (the included `vintagium-mc1.12.2-0.1.jar` — a modified Sodium 1.12 port with corrected vertex attribute layouts required by this build)

Drop both jars (`oculus-mc1.12.2-X.X.jar` + `vintagium-mc1.12.2-X.X.jar`) plus MixinBooter into `mods/`.

> Oculus is not, and will not be, compatible with OptiFine.

## Building

The Gradle root is `Oculus/`, not the repo root.

```sh
cd Oculus
./gradlew build
```

Output: `Oculus/build/libs/oculus-mc1.12.2-<version>.jar`.

`sodium-1.12/` is a vendored, modified copy of Sodium 1.12 (Vintagium) — its prebuilt jar lives in `Oculus/libs/`. Only rebuild it if you change its sources.

## Credits

- **Iris** — original authors: coderbot, IMS212, Justsnoopy30, FoundationGames
- **Oculus** (1.16+ Forge fork) — Asek3
- **Sodium / Vintagium** — CaffeineMC, embeddedt, NanoLive
- **1.12.2 backport** — this repository

## License

LGPL-3.0-only. See `Oculus/LICENSE`.
