# Adding models to Immersive Geology multiblock structures

This document describes, end to end, how a multiblock in this repository gets its 3D model: from the
structure template that defines *where the blocks are*, through the OBJ/MTL/texture assets, to the
datagen code that turns them into the blockstates and models the game loads. Everything here is
grounded in the files that actually ship with the mod; where a helper exists, its path is given.

There is also a Russian translation of this document: [`MULTIBLOCK_MODELS.ru.md`](MULTIBLOCK_MODELS.ru.md).
The coal CHP (ТЭЦ) machines added together with this document are worked examples - see
[`COAL_CHP_CHAIN.md`](COAL_CHP_CHAIN.md) for their design and port layout.

---

## 1. The pipeline in one picture

```
structure template (.nbt)          OBJ + MTL                     texture PNG
 data/immersivegeology/            models/block/multiblock/      textures/block/multiblock/
 structures/multiblocks/<mb>.nbt   obj/<mb>/<mb>.obj (+.mtl)     <mb>/<skin>.png
            |                               |                            |
            +---------------+---------------+----------------------------+
                            |
              datagen: IGBlockStateProvider
              (src/datagen/.../IGBlockStateProvider.java)
                            |
              IE "basic_split" loader cuts the OBJ into one part
              per non-air block of the template
                            |
              src/generated/resources/assets/immersivegeology/
                blockstates/<mb>.json
                models/block/.../<mb>_split.json (+ _mirrored / _active)
                models/item/<mb>.json            (IGComplexItemModelProvider)
```

Key facts that everything else follows from:

* A formed multiblock is rendered by **one OBJ model that covers the whole structure**, not by the
  individual blocks. The blocks the player places only exist until formation; after that every cell
  becomes the multiblock part block and the model supplies the visuals.
* Immersive Engineering's `immersiveengineering:basic_split` model loader cuts that OBJ into parts.
  Datagen tells it which parts exist by handing it the list of non-air template positions, offset by
  the master block (`IGBlockStateProvider.split(...)`,
  `src/datagen/java/com/igteam/immersivegeology/common/data/generators/IGBlockStateProvider.java`).
* Consequently the OBJ **must** be authored in the same coordinate space the split list uses:
  **1 unit = 1 block, origin at the master block** (see §3).

---

## 2. Step 1 - the structure template

File: `src/main/resources/data/immersivegeology/structures/multiblocks/<name>.nbt`
(gzipped vanilla structure NBT: `size`, `palette`, `blocks`, `entities`, `DataVersion = 3465`).

### 2.1 Conventions

* `size = (x, y, z)` with **y up**. The Java side repeats it in the `IGTemplateMultiblock` subclass
  constructor and `IGTemplateMultiblock.getTemplate` *asserts* that the template size equals it.
* The machine's **front** (`RelativeBlockFace.FRONT`) is **-Z**; the player forms the machine from the
  **+Z** side, which is where Immersive Geology puts the **trigger** block
  (`IEProperties.FACING_HORIZONTAL` is set to `clickDirection.getOpposite()` in
  `IGTemplateMultiblock.replaceStructureBlock`).
* `masterFromOrigin` is the cell that becomes the master block entity; `triggerFromOrigin` is the cell
  the player hits with the hammer. Both must be non-air.
* Only use block states that survive rotation and mirroring unambiguously. The shipped templates use:
  property-free cubes (`immersiveengineering:light_engineering`, `heavy_engineering`, `rs_engineering`,
  `sheetmetal_steel`, `storage_steel`, `immersivegeology:storage_block_*`, `engineering_block_*`,
  `sheetmetal_block_*`), `fluid_pipe`/`steel_scaffolding_standard` (`waterlogged=false`), bottom slabs
  (`type=bottom, waterlogged=false`) and `minecraft:hopper` (`facing=down, enabled=true`). Directional
  blocks (stairs, fences) appear only where the rotation math is worth it - avoid them in new work.

### 2.2 Authoring it

Two supported routes:

1. **In game**, with a vanilla structure block in "save" mode (3x3x3 ... up to your size), then copy
   the file into the path above. This is how the original templates were made.
2. **From a text layout**, with the generator used for the coal CHP chain:
   `tools/tpp/structures.py`. It builds the NBT from ASCII layers (`y` layers of `z` rows of `x`
   chars, `.` = air) and validates master/trigger/size. Run `python3 tools/tpp/structures.py`.

After changing a template, re-run `tools/tpp/datagen_mirror.py` (or `runData`, §6) because the split
part lists are derived from it.

---

## 3. Step 2 - the OBJ model

Location: `src/main/resources/assets/immersivegeology/models/block/multiblock/obj/<name>/<name>.obj`
plus a sibling `<name>.mtl`.

### 3.1 Coordinate space (the part that bites people)

Model coordinates are **structure coordinates minus the master position**:

```
model_pos = struct_pos - masterFromOrigin
```

so the master block's cell spans `[0,1]^3`, cells towards +X/+Y/+Z are positive, and cells on the
other side of the master are negative. Examples from the shipped models: `ballmill` (size 5x4x4,
master (2,0,1)) spans x [-2,3], y [0,4), z [-1,4); `bulk_blast_furnace` (3x8x3, master (1,0,1)) spans
x [-1,2], y [0,8], z [-1,2]. If you get this wrong the split model puts every part in the wrong cell
and the formed machine looks like an exploded cube.

### 3.2 Materials and the MTL

Exactly **one** material, named after the multiblock:

```
newmtl <name>
map_Kd #<name>
```

`map_Kd #<name>` is a *texture key*: datagen injects the real texture per skin/active variant
(`IGBlockStateProvider.obj(...)` passes `ImmutableMap.of(name, rl("block/multiblock/<name>/<skin>"))`).
Machines registered without skins may instead point straight at a texture, which is what the ball
mill does:

```
newmtl m_179a1da9-...
map_Kd immersivegeology:block/multiblock/ballmill/base
```

Use the `#key` form whenever the machine has an active form or skins; use the absolute form only for
plain `genericmultiblock(...)` machines (the four coal CHP machines other than the boiler do this).

### 3.3 Geometry rules

* Quads, wound counter-clockwise seen from outside; `f v/vt/vn` faces; `s off`; one `o <name>` object.
* UVs in `[0,1]` over the machine's texture. Datagen loads OBJs with **`flipV(true)`**, i.e. the v axis
  of the file is flipped on load - export from Blender/Blockbench with the default (bottom-origin) v
  and do not pre-flip.
* Keep every face **inside the cell it belongs to**, or exactly on a cell boundary shared with the
  neighbour that visually continues it. Geometry that pokes 0.02 into a neighbouring solid cell
  z-fights with that cell's own faces once split. (The procedural generator in
  `tools/tpp/models.py` only adds protruding detail on faces whose neighbour cell is air.)
* Budget: shipped machines run ~200-2300 faces (`reverberation_furnace` 192, `steam_turbine` 299,
  `coredrill` 2250). Aim below ~2500 faces; `scripts/obj_simplify.py` deduplicates vertices/UVs of an
  exported file if your DCC tool pads them.

### 3.4 Placeholder models

`tools/tpp/models.py` generates serviceable placeholder OBJs + MTLs + textures for any structure in
`tools/tpp/structures.py` (box-per-block with per-block-code detailing: pipes, scaffolding frames,
hoppers, coil columns, radiator fins, plus an `_active` texture variant with a glowing firebox).
They exist so the chain is playable immediately; replace them with real art per §7 without touching
any Java.

For the five coal CHP (ТЭЦ) machines the full production art brief - per-machine composition,
real-plant and in-game references, sampled palette, export and placement procedure - lives in
[`ART_BRIEF_COAL_CHP.md`](ART_BRIEF_COAL_CHP.md); the offline model self-check is
`tools/tpp/check_model.py` and UV colouring-book templates come from `tools/tpp/uv_template.py`
(output in `docs/art/uv/`).

---

## 4. Step 3 - textures

Location: `src/main/resources/assets/immersivegeology/textures/block/multiblock/<name>/`.

| file | when it is used |
|---|---|
| `default.png` | base skin / non-skinnable machines |
| `default_active.png` | machines with `IEProperties.ACTIVE` (firing glow etc.) |
| `<skin>.png` | one per entry of the machine's skins enum (`IGSteamTurbineSkins`, ...) |
| `<skin>_active.png` | skinned *and* active machines |

Conventions:

* Power-of-two, typically 128x128 or 256x256; the OBJ's UV islands decide what maps where, so keep the
  layout documented in the .blend/.bbmodel next to the obj (see §7).
* The material name in the MTL must match the texture key: `generateMultiskinMultiblock` binds
  `rl("block/multiblock/" + name + "/" + skinName + activeStr)` to the key `<name>`.
* Skins are registered as items automatically (`IGRegistrationHolder.registerMB(..., Skins.class)`
  creates `<name>_multiblock_skin_<skin>` items); the skins enum lives in
  `common/block/multiblocks/skins/` and implements `IIGMultiSkinHelper` (`multiblockName()`,
  `getSerializedName()`, credits). Adding a skin = enum entry + `<skin>.png` (+ item model is generated).

---

## 5. Step 4 - registration and datagen wiring

### 5.1 Java registration (runtime)

1. Logic: `common/block/multiblocks/logic/<Name>Logic.java` implementing `IMultiblockLogic<State>` plus
   `IServerTickableComponent` / `IClientTickableComponent` / `MBOverlayText` as needed.
2. Shape: `common/block/multiblocks/shapes/<Name>Shape.java` extending `GenericShape`
   (per-cell AABBs in `[0,1]` local space; `AlternatorShape` is the minimal example).
3. Registration in `core/registration/IGMultiblockProvider.java`. The flavour you pick **decides which
   blockstate flavour datagen must emit**:

   | helper | block class | properties | datagen call |
   |---|---|---|---|
   | `metal_skinnable(...)` / `stone_skinnable(...)` | your `SkinableMultiblockPart` subclass | `FACING_HORIZONTAL`, `MIRRORED`, skin enum, optional `ACTIVE` | `generateMultiskinMultiblock(block, name, hasActive, useActiveModels, hasMirror, useMirrorModels)` |
   | `stone(...)` | `NonMirrorableWithActiveBlock` | `FACING_HORIZONTAL`, `ACTIVE` | `generateActiveMultiblock(block, name)` |
   | `IGRegistrationHolder.registerMetalMultiblock(...)` / `mirroredStone(...)` | IE default part block | `FACING_HORIZONTAL`, `MIRRORED` | `genericmultiblock(name)` (or `genericmultiblockMirror(name)` for a hand mirrored obj) |

4. Template class: `common/block/multiblocks/IG<Name>Multiblock.java` extending `IGTemplateMultiblock`
   with `(loc, masterFromOrigin, triggerFromOrigin, size, IGMultiblockProvider.<REG>)`,
   `getManualScale()`, `initializeClient(...)` (manual preview offset = half the footprint) and
   `getName()`.
5. Register both in `IGRegistrationHolder.initializeMultiblocks()`:
   `registerMB("<name>", IG<Name>Multiblock.INSTANCE, IGMultiblockProvider.<REG>[, Skins.class])`.

### 5.2 Datagen (assets)

In `IGBlockStateProvider.registerStatesAndModels()` add the call from the table above. What each does:

* `generateMultiskinMultiblock` loops skins x active x mirrored x facing, builds
  `obj(<name>_<skin><_active><_mirrored>, rl("block/multiblock/obj/<name>/<model>.obj"), {name: skinTex})`
  and splits it with `split(model, template, mirrored)`. `model` is `<name>` plus `_ <skin>` when
  `skin.alternativeModel()`, plus `_active` / `_mirrored` when the flags say so - so an alternative
  skin model file is named e.g. `bloomery_industrial_active.obj`.
* `generateActiveMultiblock` does the same without skins/mirror, producing
  `models/block/<name>_split.json` and `<name>_active_split.json`.
* `genericmultiblock(name)` = `createMultiblock(innerObj("block/multiblock/obj/<name>/<name>.obj"), template)`:
  the MTL's absolute `map_Kd` supplies the texture, and mirroring is handled by
  `immersiveengineering:mirror` around the split model when the block has `MIRRORED`.

The split model JSONs land in `src/generated/resources` and embed the inner OBJ model inline; the
blockstate then rotates them per facing (`y = 0/90/180/270`, `uvlock: true`).

Item form: `IGComplexItemModelProvider.registerModels()` - add a call that builds a `TRSRModelBuilder`
over the same OBJ with display transforms (`generateCoalChpModel(...)` is the compact template; the
coal boiler also binds its texture because its MTL uses a `#key`).

### 5.3 Animated parts (optional)

Machines with moving internals (ball mill drum, turbine rotor, alternator wheel) keep the moving
geometry in a **second OBJ** next to the static one (`.../obj/ballmill/drum.obj`), register it in
`IGDynamicModelProvider` (`customLoader(ObjModelBuilder::begin).modelLocation(...).flipV(true)`),
bind it client side in `client/ClientModBusEventHandlers.java`
(`BallmillRenderer.DRUM = new IGDynamicModel(BallmillRenderer.DRUM_NAME)`), and draw it either from a
block entity renderer (`client/renderer/multiblocks/*Renderer.java`, registered for the master BE) or
from the manual preview via `IGClientMultiblockProperties.renderExtras(...)`
(see `IGBallmillMultiblock.initializeClient`).

---

## 6. Producing `src/generated/resources`

The generated folder is **committed** and CI builds without running datagen, so new content must exist
there in both worlds:

* Preferred: run the datagen run config - `./gradlew runData`
  (outputs to `src/generated/resources/`, `--validate --existing src/main/resources`). It rewrites
  everything the providers emit and updates `src/generated/resources/.cache/*`; files not listed in the
  cache are left alone.
* Without a Minecraft dev environment: `python3 tools/tpp/datagen_mirror.py` writes exactly the files
  the providers produce for the coal CHP chain (blockstates, split models, item models, loot tables,
  material models/tags, recipes). Extend that script rather than hand-editing generated JSON.
* Never place the same relative path in `src/main/resources` **and** `src/generated/resources`:
  `ProcessResources` uses `DuplicatesStrategy.FAIL`.

---

## 7. Replacing a placeholder model with real art - checklist

0. For the machines of the coal CHP chain read [`ART_BRIEF_COAL_CHP.md`](ART_BRIEF_COAL_CHP.md)
   first: references, per-machine composition and the acceptance checklist live there.
1. Open/import the structure template (§2) as a block-out reference; model against it.
2. Model in metres-with-1m-blocks, then translate the origin to the master block (§3.1). Keep the
   machine's front towards -Z.
3. One material named `<name>`; `map_Kd #<name>` if the machine has skins/active forms.
4. Unwrap UVs into `textures/block/multiblock/<name>/default.png` (and paint `default_active.png`,
   `<skin>.png` variants as needed). Keep the UV layout saved with the source file.
5. Export OBJ (+MTL): quads, `v/vt/vn`, `s off`; run `scripts/obj_simplify.py` if the exporter pads
   arrays; check the face budget (§3.3).
6. Drop the files into `models/block/multiblock/obj/<name>/` (overwriting the placeholder), keep the
   filenames the datagen expects (`<name>.obj`, plus `<name>_active.obj`/`<name>_<skin>.obj` only if
   the registration flavour references them).
7. `./gradlew runData` (or extend `tools/tpp/datagen_mirror.py`), then form the machine in game and
   check: parts land in the right cells, no z-fighting on shared faces, active state swaps textures,
   mirroring looks correct, manual preview (`getManualScale`/offsets) frames the machine.

## 8. Worked example

The coal CHP chain (`coal_boiler`, `electrostatic_precipitator`, `steam_condenser`, `cooling_tower`,
`district_heater`) exercises every route above: an active non-mirrored machine with a `#key` MTL
(coal boiler), four mirrored machines with absolute-path MTLs, procedural placeholder geometry and
textures (`tools/tpp/models.py`), script-generated templates (`tools/tpp/structures.py`) and mirrored
datagen output (`tools/tpp/datagen_mirror.py`). Their port layout and the real-plant numbers behind
them are documented in [`COAL_CHP_CHAIN.md`](COAL_CHP_CHAIN.md).
