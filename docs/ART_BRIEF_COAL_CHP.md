# Art brief: models and textures for the coal CHP chain

> Companion to [`MULTIBLOCK_MODELS.md`](MULTIBLOCK_MODELS.md) (pipeline mechanics: template →
> OBJ → textures → registration → datagen) and [`COAL_CHP_CHAIN.md`](COAL_CHP_CHAIN.md)
> (logic, ports, tuning). This document is the production one: **what exactly** to model and
> paint per machine, from which references, with which tools, exported how, placed where, and
> verified how. Russian version: [`ART_BRIEF_COAL_CHP.ru.md`](ART_BRIEF_COAL_CHP.ru.md).

Current state: all five machines run on **procedural placeholders** (built by
`tools/tpp/models.py`) with 128×128 technical atlases. The art task is to replace them with
real models and textures without touching logic or structure templates.

---

## 0. Scope: asset inventory

| Machine | OBJ + MTL (replace placeholder) | Textures | Notes |
|---|---|---|---|
| `coal_boiler` | `.../obj/coal_boiler/coal_boiler.obj` + `.mtl` | `default.png`, **`default_active.png`** | single material `coal_boiler`, MTL uses `map_Kd #coal_boiler` (datagen injects the texture); active (burning) state |
| `electrostatic_precipitator` | `.../obj/electrostatic_precipitator/...obj` + `.mtl` | `default.png` | MTL uses the absolute path `immersivegeology:block/multiblock/electrostatic_precipitator/default` |
| `steam_condenser` | `.../obj/steam_condenser/...obj` + `.mtl` | `default.png` | same, path `.../steam_condenser/default` |
| `cooling_tower` | `.../obj/cooling_tower/...obj` + `.mtl` | `default.png` | the mirrored variant is **not modelled**: the `immersiveengineering:mirror` loader derives it from the same OBJ |
| `district_heater` | `.../obj/district_heater/...obj` + `.mtl` | `default.png` | like the condenser |

Full paths:

```
src/main/resources/assets/immersivegeology/models/block/multiblock/obj/<mb>/<mb>.obj      <- model
src/main/resources/assets/immersivegeology/models/block/multiblock/obj/<mb>/<mb>.mtl      <- materials
src/main/resources/assets/immersivegeology/textures/block/multiblock/<mb>/default.png     <- atlas
src/main/resources/assets/immersivegeology/textures/block/multiblock/<mb>/default_active.png   <- coal_boiler only
```

Optional ("later" tier):

| Asset | Why | Where |
|---|---|---|
| `display_<mb>.obj` | simplified hand/GUI model if the full one is heavy or unreadable | same `obj/<mb>/` dir + one line in `IGComplexItemModelProvider.generateCoalChpModel(...)` (precedents: `ballmill/display_ballmill.obj`, `steam_turbine/steam_turbine_item.obj`) |
| `<skin>.png` skins | paint variants like `steam_turbine`'s green/industrial/steampunk | **needs code**: skins enum, `IMultiSkinBlock`, skinnable registration, `generateMultiskinMultiblock` — see §7 |
| `.png.mcmeta` | animation (furnace flicker, conveyors) | next to the texture; precedents `foundry/conveyor.png.mcmeta`, `block/fluid/default_still.png.mcmeta`; see §4.4 |

**Never hand-edited:** everything under `src/generated/resources/` (blockstates,
`*_split.json`, `*_mirrored_split.json`, item models, loot, recipes) — that is datagen output
(`./gradlew runData`) or its offline mirror `tools/tpp/datagen_mirror.py`. Structure `.nbt`
files are untouched as well: art adapts to the shape, never the other way round.

---

## 1. References

### 1.1 In-game style (check against these first)

The mod keeps a desaturated industrial palette. Sampled from textures already in the repo:

| Family | Sampled from | Characteristic colours |
|---|---|---|
| bare machine steel | `steam_turbine/default.png`, `alternator/default.png`, `geothermal_exchanger/default.png`, `pelletizer/default.png` | base `#5c5c5c`, shadows `#414141`/`#3c3c3c`, dark seams |
| dark furnace casing | `bulk_blast_furnace/default.png` | near-black brown `#231510`…`#2d1914` |
| light furnace | `reverberation_furnace/default.png` | sand `#c9b89b`, `#a5927d`, rust `#7b5b43` |
| brick-grey | `bloomery/default.png` | `#6e665c`, `#786e64`, seam `#393632` |
| **active glow** | `bulk_blast_furnace/default_active.png` | amber `#ffa100`, `#ffbf00`, rim `#805e2b`; only 0.2% of atlas pixels change |
| **active glow** | `reverberation_furnace/default_active.png` | `#ff5500`, `#ff6600`, `#aa5500`; 4.8% of pixels |
| **active glow** | `bloomery/default_active.png` | embers `#9a6b4b`, `#75533e`; 2.2% of pixels |

Multiblock atlas resolutions in the repo range from 64×64 (`bloomery`) to 512×512
(`foundry/foundry.png`); the typical value is 128×128 (`bulk_blast_furnace`, `pelletizer`,
`reverberation_furnace`, `geothermal_exchanger`), complex machines use 192…256 (`alternator`
192, `steam_turbine` 208, `coredrill` 256). **Recommendation for our five: 256×256**
(minimum 128), PNG 8-bit RGBA.

Live style examples: [steam_turbine/default.png](https://github.com/DIMNEW-2/Immersive-Geology/blob/forge-1.20.1/src/main/resources/assets/immersivegeology/textures/block/multiblock/steam_turbine/default.png),
[bulk_blast_furnace/default.png](https://github.com/DIMNEW-2/Immersive-Geology/blob/forge-1.20.1/src/main/resources/assets/immersivegeology/textures/block/multiblock/bulk_blast_furnace/default.png),
[Immersive Engineering screenshots](https://www.9minecraft.net/immersive-engineering-mod/) —
panelled cladding, riveted seams, dark outlines, no gloss, no photo gradients.

### 1.2 Real plant equipment (what to model from)

| Machine | Reference | What to take |
|---|---|---|
| `coal_boiler` | [pulverised coal steam boilers, unit photos](https://www.steam-generator.com/pulverized-coal-fired-steam-boilers/) | panelled casing with embossed diamonds, bright red valves/handwheels on dark cladding, hopper front |
| `coal_boiler` | [boiler steam drum](https://huadongboiler.en.made-in-china.com/product/EnupKmbTmURv/China-Industrial-CFB-Power-Plant-Oil-Boiler-Mud-Drum-Steam-Drum-in-Boiler-SGS-Certification.html) | drum: manholes, nozzles, insulation cladding |
| `electrostatic_precipitator` | [flue gas duct and stack](https://istockphoto.com/photos/flue-gas-stack) | weathered panelled ductwork, rust streaks from top edges, stack rain cap |
| `steam_condenser` | [surface (shell & tube) condenser](https://www.boyuheatexchanger.com/products/heat-exchanger/Steam-Condenser---Shell-and-Tube-Condenser-for-Power-Plants-and-Industry.html) | horizontal shell, dished waterboxes with bolted covers, hotwell below |
| `steam_condenser` | [turbine hall of a thermal station (Wikimedia, CC)](https://en.wikipedia.org/wiki/Thermal_power_station) | layout: condenser under the turbine, steam neck on top, pumps alongside |
| `cooling_tower` | [natural draft cooling towers and pipe racks](https://vesfil.com/natural-draft-cooling-tower) | hyperboloid silhouette with throat and flared rim, vertical formwork seams, yellow platform railings, plumes |
| `district_heater` | [plate heat exchanger for district heating](https://nordictec-store.com/blog/post/heat-exchanger-for-district-heating-how-to-choose) | insulated shell with banding, flanged nozzles, pipe work |
| `district_heater` | [heat exchanger substation, IEA DH Handbook fig 11.2](https://slideshare.net/slideshow/iea-districtheatingandcoolingconnectionhandbook/72813281) | substation composition: exchanger + pumps + control cabinet |
| everything | [Thermal power station (Wikimedia, CC BY-SA)](https://en.wikipedia.org/wiki/Thermal_power_station) | general boiler/turbine hall views for moodboards |

Reference rules:

1. Photos are **reference only**: never trace or paste them into textures (licensing).
   Wikimedia Commons (CC BY-SA) photos may go into moodboards with attribution.
2. Collect 3–5 photos per machine: 3/4 front overall, orthographic side, close-up of the
   characteristic detail (burners, waterboxes, louvers), cutaway/interior. Neutral light,
   little perspective distortion.
3. A local copy of the collected set plus a contact sheet (`moodboard_coal_chp.png`, one row
   per machine plus an IE style-anchor row) was assembled while writing this brief; it is
   intentionally **not** committed for licensing reasons — rebuild your own from the links.

### 1.3 Structural references (mandatory)

The model is a "shell" over the real block layout — players see the blocks first, then the
formed multiblock, so the silhouette must not contradict the layout. Full layouts live in
`tools/tpp/structures.py` (ASCII layers) and the `.nbt` templates. Legend:

```
C srconcrete   R industrial refractory   B refractory   S stainless sheetmetal   M hastelloy sheetmetal
N stainless block   A hastelloy block   X high-speed-steel block   Y hss sheetmetal   T/H engineering blocks
L/E/Q IE light/heavy/rs engineering   P fluid pipe   G generator   D radiator   1/2/3 coils
K steel sheetmetal   O steel block   U concrete   V concrete tile   F scaffolding   Z steel slab   W hopper
```

---

## 2. Per-machine composition

Coordinates below are **model space** = structure block coords minus the master block (see
`MULTIBLOCK_MODELS.md` §3.1). Front = −Z (FRONT ports face it), trigger/player = +Z.

| Machine | Size | Model bounding box (min..max) | Tris today (placeholder) |
|---|---|---|---|
| `coal_boiler` | 5×7×5 | (−2,−1,−2)..(3,6,3) | ~2064 |
| `electrostatic_precipitator` | 5×9×4 | (−2,0,−2)..(3,9,2) | ~1680 |
| `steam_condenser` | 4×3×4 | (−1,0,−1)..(3,3,3) | ~1068 |
| `cooling_tower` | 7×9×7 | (−3,0,−3)..(4,9,4) | ~4452 |
| `district_heater` | 3×4×3 | (−1,−1,−1)..(2,3,2) | ~792 |

Budget: existing multiblocks sit around ~150–450 faces (`ballmill` 145, `steam_turbine` 299,
`alternator` 446); target ceiling for the new ones is **3–5k tris**, cooling tower up to ~6k.
Enforced by `tools/tpp/check_model.py`.

### 2.1 `coal_boiler` — pulverised coal boiler unit

Layers (y=0 bottom): foundation and ash hopper → furnace with burner register → evaporator
waterwalls → superheater → drum with economizer → convective pass with ID fan → roof with
coal bunker and flue duct.

What must read in the model:

- **y0**: concrete plinth; bottom-centre fly/bottom ash hopper funnel with a slide gate
  (ash drop port, cell (2,0,5)); forced-draft fan housings at the corners.
- **y1 (furnace)**: refractory walls; on **front (−Z)** a register of 4 burners with peep
  doors — these double as the active-glow windows; on **back (+Z)** a door/panel (the
  formation trigger sits here).
- **y2–y3**: panelled waterwalls, vertical tube risers on the sides; on front y3 the
  **live steam flange** (port (2,3,0)).
- **y4**: horizontal drum (cylinder along X) with manhole and gauge glass, economizer
  coils; on the back the **feedwater inlet** (port (2,4,4)).
- **y5**: convective gas pass, round induced-draft fan housing on one side.
- **y6 (roof)**: front — **flue gas duct with flange** (port (2,6,0)); centre — pulverised
  coal bunker with a funnel (**fuel enters from the top**, port (2,6,2)); back — control
  cabinet (redstone port (2,6,4)). Energy port (4,1,4) as a terminal box right-rear.
- Active texture: glow in the burner peep doors and furnace window (amber `#ffa100/#ffbf00`),
  ≤2% of atlas pixels total, same UVs.

### 2.2 `electrostatic_precipitator` — ESP with stack

- **y0**: concrete foundation and 4 pyramidal fly ash hoppers with sluice valves (drop (2,0,4)).
- **y1**: housing; on the front the **dirty gas inlet duct** with flange (port (2,1,0));
  on the back a terminal box (energy port (2,1,3)).
- **y2…y4**: three electrode fields: panelled casing with vertical stiffener ribs, insulator
  boxes and rapping gear on the sides.
- **y5**: outlet plenum, rectifier transformers (finned boxes) on the side/roof.
- **y6**: roof; **y7–y8**: 1×1 stack in the middle with a flared rim/rain cap — cleaned gas
  visually leaves here.
- Surface character: large bolted sheets, rust streaks from top edges (see stack reference).

### 2.3 `steam_condenser` — surface condenser

- **y0**: hotwell (box bottom-centre) and waterboxes at both X ends: round bolted covers;
  circulating water in on the right (0,1,1), out on the left (3,1,1).
- **y1**: horizontal cylindrical shell along X with tube sheets; condensate pump skid with
  motor at the rear side; panel on the back (energy port (1,1,3)); condensate nozzle
  bottom-rear (1,0,3).
- **y2**: rectangular steam inlet neck with flange top-front (port (1,2,0) FRONT) plus a
  small air ejector (cylinder with a cone) next to it.

### 2.4 `cooling_tower` — hyperbolic natural draft tower (mirrored)

- **y0**: collecting basin — concrete ring, dark water inside; cold water outlet
  bottom-rear (3,0,6).
- **y1**: air inlet louvers around the perimeter — angled slats with gaps (gaps = alpha);
  the +Z side is the service entrance (trigger (3,1,6)).
- **y2–y3**: fill packing and drift eliminators — thin plate packs visible through louvers.
- **y4…y8**: hyperboloid shell: wide at y4–5, throat at y6–7, flared rim at y8; concrete with
  vertical formwork seams and streaks; hot water pipe enters top-rear (3,4,6), makeup on the
  right (0,0,3).
- Mirroring: the loader mirrors the model via the `mirrored` property; do **not** model
  asymmetric details twice and do **not** author a second OBJ.

### 2.5 `district_heater` — network water heater

- **y0**: frame and network water pump skid; formation trigger bottom-centre (1,0,1).
- **y1–y2**: vertical shell in insulation cladding with banding rings; flanged nozzles:
  return water on the right (0,1,1), hot supply on the left (2,1,1); control cabinet with
  gauges on the back at y2.
- **y3**: steam chest: dome/flat lid with the steam inlet flange top-front (port (1,3,0));
  condensate nozzle bottom-rear (1,0,2); energy port (0,0,1) as a terminal on the frame.

---

## 3. Authoring the model

### 3.1 Tools

- **Blender 4.x is the primary path**: every existing model in the repo was exported from it
  (`# Blender 4.2.3 LTS` headers in `.obj`/`.mtl`), so its export settings are known-good.
- **Blockbench is acceptable**, but verify V orientation with a labelled test texture after
  export (see §3.4): Blockbench UV space is Minecraft-style (y down) while the pipeline
  expects Blender-style + `flip_v: true`.
- Textures: Aseprite / Pixelorama / GIMP / Photoshop — anything with a pixel grid and 8-bit
  RGBA PNG export.

### 3.2 Scene and coordinate system

1. 1 Blender unit = 1 block; grid step 1.
2. Import the current placeholder OBJ (`File > Import > Wavefront`) as a skeleton/bounds
   cage: its bbox already equals the table in §2. Model over it, delete the skeleton at the end.
3. Axes: machine **front = −Z**, trigger/player = **+Z**, Y up, origin = the structure's
   master block (the master cell occupies cube (0,0,0)..(1,1,1)).
4. No negative scale; apply scale/rotation before export (`Ctrl+A > All Transforms`).

### 3.3 Geometry and materials

- Faces: quads/tris; n-gons get triangulated by the exporter but keep quads anyway.
- Delete faces that can never be seen (inside a closed shell), but do not perforate the
  shell: the split renderer draws the model piecewise per block, inner walls prevent
  see-through gaps at part boundaries.
- Offset coplanar overlays (plates, labels) by ≥1/256 block or you get z-fighting.
- **Exactly one material per machine**, named after the registry name (`coal_boiler`,
  `cooling_tower`, …). No `Material.001`: check `usemtl` in the OBJ after export.
- The MTL is hand-fixed after export (Blender writes absolute paths — old repo files still
  carry `C:/Users/...`, do not repeat that):
  - `coal_boiler`: `map_Kd #coal_boiler` (the hash means "datagen injects the texture");
  - the other four: `map_Kd immersivegeology:block/multiblock/<mb>/default` — no `.png`,
    no Windows slashes.

### 3.4 Unwrapping and flip_v

- One atlas per machine; islands with 2–4 px padding (at 256×256); important faces (front,
  panels, drum) larger, secondary smaller; keep islands axis-aligned where possible — that
  is what makes the texture read in the mod's style.
- UVs strictly inside 0..1 (`check_model.py` enforces it).
- Every generated model in the mod has `flip_v: true`. Export from Blender **without** a
  flip-V option — then what you see in Blender's UV editor over your PNG is what the game
  shows. For models from other tools: bake a test texture with an asymmetric label, place
  the machine; if the label is vertically mirrored, invert V in the UVs (or flip the whole
  PNG — then flip `default_active` too).
- UV colouring-book template: `python3 tools/tpp/uv_template.py <mb> --size 1024` (add
  `--overlay` to lay the island wireframe over the current `default.png`); output in
  `docs/art/uv/<mb>_uv.png`: green outlines = where each face lands on the atlas, V axis
  drawn the way the game/Blender sees it.

### 3.5 Self-check before committing

```bash
python3 tools/tpp/check_model.py <mb>      # bbox, materials, MTL paths, vt 0..1, textures on disk, split-json
python3 tools/tpp/check_model.py --all
```

It fails (exit 1) if the model leaves the structure bbox (the split renderer loses such
geometry), if a material name breaks the datagen contract, if the MTL carries an absolute
path, or if a referenced texture is missing. It warns above 6k tris.

---

## 4. Textures

### 4.1 Format and palette

- PNG, 8-bit, RGBA; 256×256 (minimum 128×128). Opaque areas alpha 255; holes (grating,
  louvers) alpha 0 — but see §4.3 on render types.
- Anchor palette (§1.1): steel `#5c5c5c`/`#414141`, seams `#1a1a1a`, rust `#7b5b43`/`#5d493b`,
  concrete `#8a8a86` with streaks, refractory `#6e665c`/`#786e64`; signal accents: red
  valves/handwheels `#a03020`, yellow railings `#c8a020` (as in the cooling tower photos).
- Pixel density: front faces and panels ≈16 px per block, secondary ≈8 px per block (at a
  256 atlas for a 5×7×5 machine that is enough).
- No antialiasing, no photo-style gradients: the mod's style is hard pixels, metal noise as
  dots/scratches, shadows baked into the texture (edge AO).

### 4.2 Active state (`coal_boiler`)

`default_active.png` is a **full copy** of `default.png` (same OBJ, same UVs) with only the
glow zones recoloured: burner peep doors and the furnace window. Mod precedents: amber
`#ffa100/#ffbf00/#805e2b` (bulk_blast_furnace) or coal-orange `#ff5500/#ff6600/#aa5500`
(reverberation_furnace); changed pixel share 0.2–5%. Move nothing else in the copy: an
island shifted by one pixel = "swimming" glow.

### 4.3 Transparency and render type

If the texture has alpha=0 (cooling tower louvers, grating, gaps), the machine block needs a
cutout layer or transparent pixels render black/solid. In this repo that is one line in
`IGClientRenderHandler.register()`:

```java
setRenderType(IGMultiblockProvider.COOLING_TOWER.block().get(), RenderTypeSkeleton.CUTOUT_MIPPED);
```

(Precedents: `COREDRILL`, `CHEMICAL_REACTOR`, `REVERBERATION_FURNACE`, `BLOOMERY`,
`GRAVITY_SEPARATOR`, `ROTARYKILN`.) The new machines do **not** have these lines yet — add
them yourself or ask. If you use no transparency, nothing is needed.

### 4.4 Animation (optional)

A `.png.mcmeta` next to a PNG animates the **whole** texture (frames = atlas frames), see
`foundry/conveyor.png.mcmeta`, `block/fluid/default_still.png.mcmeta`. For a whole-machine
atlas that is almost never acceptable; if you really want a flickering firebox, move it to a
separate material (like `ballmill`: 4 materials / 4 textures), which for `coal_boiler` means
switching from the injected scheme (`#coal_boiler`) to `genericmultiblock` with manual MTL
paths — see `MULTIBLOCK_MODELS.md` §3.2 and §5.2.

### 4.5 Chain fluids need no textures

`flue_gas` and `hot_water` render with the mod's stock textures: `IGFluidType` picks
`block/fluid/default_still|flowing` (gas via `IS_GAS`), colour comes from the material tint
(`0xff4d4743` sooty grey-brown, `0xffc07038` warm amber). Custom still/flowing for them would
require editing `IGFluidType` (the texture choice is hardcoded) — not an art task.

### 4.6 Item icon

The item model is the same OBJ with display transforms
(`IGComplexItemModelProvider.generateCoalChpModel`, held scales already set: 0.0625 /
0.09375 / 0.125). If the machine is unreadable in hand/GUI, make a simplified
`display_<mb>.obj` (precedents `ballmill/display_ballmill.obj`,
`steam_turbine/steam_turbine_item.obj`) and adjust the provider line.

---

## 5. Export and repository placement (step by step)

1. Blender: model + unwrap (§3).
2. `File > Export > Wavefront (.obj)`: apply modifiers, normals and UVs on, materials on,
   Path Mode strip/none; default axes (Forward −Z, Up Y) — same as every OBJ in the repo.
   An `.mtl` lands next to it.
3. Fix the `.mtl`: material name = machine name; `map_Kd` per §3.3. Strip `.001` suffixes
   from `usemtl`/`newmtl` if Blender added them.
4. Drop the files over the placeholders (names and dirs per §0), renaming nothing.
5. Paint/place `default.png` (and `default_active.png` for the boiler) into the texture dir.
6. `python3 tools/tpp/check_model.py <mb>` — until a green `OK`.
7. Regenerate generated assets: with JDK 17 and network — `./gradlew runData`; without —
   `python3 tools/tpp/datagen_mirror.py` (writes the same JSON datagen would: blockstates,
   split/mirrored, item models, loot). Commit `src/generated/resources`.
8. In game: form the structure with the engineer's hammer; check 4 rotations; for
   `cooling_tower` the mirror; for `coal_boiler` active (fuel + water + redstone); item in
   hand and GUI; break particles; `F3+T` after texture edits.
9. Commit: art + generated JSON in one commit; sources (.blend/.psd) and moodboards do
   **not** go into the repository.

---

## 6. Acceptance checklist

- [ ] `check_model.py <mb>` → OK (bbox, materials, MTL, vt, textures).
- [ ] `runData`/`datagen_mirror` regenerated, generated diff clean and sensible.
- [ ] In game: formation, 4 rotations, mirror (tower), active (boiler).
- [ ] No black squares (alpha without cutout), no z-fighting, no swimming glow.
- [ ] Unwrap inside 0..1, islands do not overlap each other.
- [ ] Item readable in GUI and hand (or `display_<mb>.obj` made).
- [ ] Weights: OBJ ≲ 200 KB, PNG ≲ 100 KB per machine.
- [ ] Style checked against §1.1 (palette/noise/seams), silhouette against the block
      layout (§1.3, §2).

---

## 7. Optional extensions (need code, not just art)

| Want | Needed |
|---|---|
| Paint skins (like `steam_turbine`: green/industrial/steampunk) | enum modelled on `IGSteamTurbineSkins` (+ `IIGMultiSkinHelper`, `IMultiSkinBlock`), register the machine via a skinnable flavour in `IGMultiblockProvider`, datagen call `generateMultiskinMultiblock(...)`, textures `<skin>.png` (+`<skin>_active.png`), skin item `<mb>_multiblock_skin_<name>` |
| Several materials/textures per machine (like `ballmill`: axle/gear/drum/base) | switch the machine's datagen to `genericmultiblock(...)` and manual MTL paths per texture |
| Custom fluid textures | edit `IGFluidType` (still/flowing choice is hardcoded: default/molten) |
| Transparency in the atlas | one `setRenderType(..., CUTOUT_MIPPED)` line in `IGClientRenderHandler.register()` (§4.3) |
| Animated parts | separate material + `.mcmeta` (§4.4) |

---

## 8. Path summary

| What | Path |
|---|---|
| Machine OBJ/MTL | `src/main/resources/assets/immersivegeology/models/block/multiblock/obj/<mb>/` |
| Machine textures | `src/main/resources/assets/immersivegeology/textures/block/multiblock/<mb>/` |
| Structure templates (do not touch) | `src/main/resources/data/immersivegeology/structures/multiblocks/<mb>.nbt` |
| Block layouts (read while modelling) | `tools/tpp/structures.py` |
| Generated assets (never hand-edit) | `src/generated/resources/assets/immersivegeology/...` |
| Model datagen wiring | `src/datagen/.../IGBlockStateProvider.java`, `IGComplexItemModelProvider.java` |
| Render type / tinting | `src/main/java/.../client/IGClientRenderHandler.java` |
| Model self-check | `tools/tpp/check_model.py` |
| UV template | `tools/tpp/uv_template.py` → `docs/art/uv/<mb>_uv.png` |
| Offline datagen mirror | `tools/tpp/datagen_mirror.py` |
| Pipeline mechanics (how it all works) | `docs/MULTIBLOCK_MODELS.md` |
| Chain logic/ports/tuning | `docs/COAL_CHP_CHAIN.md` |
