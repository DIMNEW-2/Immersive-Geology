#!/usr/bin/env python3
"""
Mirrors the datagen output of the coal CHP (ТЭЦ) additions into ``src/generated/resources``.

Immersive Geology commits its datagen output and CI builds without running ``runData``, so anything
the datagen providers emit for new content has to exist in the generated folder for the built jar to
be complete. This script produces exactly what the providers in ``src/datagen`` would write for the
coal CHP chain:

* multiblock blockstates + ``basic_split`` models (computed from the structure templates),
* multiblock item models and loot tables,
* item models, fluid blockstates/models and tags for the new materials (fly ash, flue gas, hot water)
  and for the new coal powder/grit forms,
* the boiler fuel, coal preparation and fly ash concrete recipes.

Run ``python3 tools/tpp/datagen_mirror.py`` from the repository root after changing structures,
models or recipes. Re-running ``runData`` in a dev environment overwrites these files with identical
content.
"""

from __future__ import annotations

import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from structures import ALL_STRUCTURES, Structure  # noqa: E402

GEN = os.path.join("src", "generated", "resources")
MOD = "immersivegeology"

MIRRORED_MACHINES = ["electrostatic_precipitator", "steam_condenser", "cooling_tower", "district_heater"]
ACTIVE_MACHINES = ["coal_boiler"]

# held scale per machine, matching IGComplexItemModelProvider.generateCoalChpModel
HELD_SCALE = {
    "coal_boiler": 0.0625,
    "electrostatic_precipitator": 0.0625,
    "steam_condenser": 0.09375,
    "cooling_tower": 0.0625,
    "district_heater": 0.125,
}


def write_json(path: str, data) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def patch_json(path: str, mutate) -> None:
    with open(path, encoding="utf-8") as fh:
        data = json.load(fh)
    mutate(data)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2)
        fh.write("\n")


def split_parts(struct: Structure, mirror: bool = False):
    mx, my, mz = struct.master
    sx = struct.size[0]
    parts = []
    for pos, code in struct.blocks():
        if code == ".":
            continue
        x, y, z = pos
        if mirror:
            x = sx - x - 1
        parts.append([x - mx, y - my, z - mz])
    return parts


def obj_inner_model(name: str, texture_key: str | None, particle: str) -> dict:
    textures = {}
    if texture_key:
        textures[texture_key] = f"{MOD}:block/multiblock/{name}/{texture_key_suffix(texture_key, name)}"
    textures["particle"] = particle
    return {
        "parent": "minecraft:block/block",
        "automatic_culling": False,
        "flip_v": True,
        "loader": "forge:obj",
        "model": f"{MOD}:models/block/multiblock/obj/{name}/{name}.obj",
        "textures": textures,
    }


def texture_key_suffix(key: str, name: str) -> str:
    return "default"


def write_multiblock_assets(struct: Structure) -> None:
    name = struct.name
    mirrored = name in MIRRORED_MACHINES
    active = name in ACTIVE_MACHINES

    # ---- blockstate -------------------------------------------------------------------------
    variants = {}
    if active:
        for a in (False, True):
            for facing, y in (("east", 90), ("north", 0), ("south", 180), ("west", 270)):
                model = f"{MOD}:block/{name}_split" if not a else f"{MOD}:block/{name}_active_split"
                entry = {"model": model, "uvlock": True}
                if y:
                    entry["y"] = y
                variants[f"active={str(a).lower()},facing={facing}"] = entry
    else:
        for facing, y in (("east", 90), ("north", 0), ("south", 180), ("west", 270)):
            for m in (False, True):
                suffix = "_mirrored_split" if m else "_split"
                entry = {"model": f"{MOD}:block/multiblock/obj/{name}/{name}{suffix}", "uvlock": True}
                if y:
                    entry["y"] = y
                variants[f"facing={facing},mirrored={str(m).lower()}"] = entry
    write_json(os.path.join(GEN, "assets", MOD, "blockstates", f"{name}.json"), {"variants": variants})

    # ---- split models ------------------------------------------------------------------------
    if active:
        for a, suffix in ((False, ""), (True, "_active")):
            particle = f"{MOD}:block/multiblock/{name}/default{suffix}"
            model = {
                "parent": "minecraft:block/block",
                "dynamic": False,
                "inner_model": obj_inner_model(name, name, particle),
                "loader": "immersiveengineering:basic_split",
                "split_parts": split_parts(struct),
                "textures": {"particle": particle},
            }
            write_json(os.path.join(GEN, "assets", MOD, "models", "block", f"{name}{suffix}_split.json"), model)
    else:
        particle = f"{MOD}:block/multiblock/{name}/default"
        base = {
            "parent": "minecraft:block/block",
            "dynamic": False,
            "inner_model": obj_inner_model(name, None, particle),
            "loader": "immersiveengineering:basic_split",
            "split_parts": split_parts(struct),
            "textures": {"particle": particle},
        }
        write_json(os.path.join(GEN, "assets", MOD, "models", "block", "multiblock", "obj", name, f"{name}_split.json"), base)
        if mirrored:
            mirror_model = {
                "parent": "minecraft:block/block",
                "dynamic": False,
                "inner_model": {
                    "inner_model": obj_inner_model(name, None, particle),
                    "loader": "immersiveengineering:mirror",
                },
                "loader": "immersiveengineering:basic_split",
                "split_parts": split_parts(struct, mirror=True),
            }
            write_json(os.path.join(GEN, "assets", MOD, "models", "block", "multiblock", "obj", name,
                                    f"{name}_mirrored_split.json"), mirror_model)

    # ---- item model ---------------------------------------------------------------------------
    scale = HELD_SCALE[name]
    item = {
        "display": {
            "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [scale] * 3, "translation": [-1.5, 2.5, 1.25]},
            "firstperson_righthand": {"rotation": [0, 225, 0], "scale": [scale] * 3, "translation": [-1.5, 2.5, 1.75]},
            "fixed": {"scale": [scale] * 3, "translation": [-1, -8, -2]},
            "ground": {"scale": [scale] * 3, "translation": [-0.5, 3, -0.5]},
            "gui": {"rotation": [30, 225, 0], "scale": [scale * 2] * 3, "translation": [0, -3, 0]},
            "head": {"scale": [0.2, 0.2, 0.2], "translation": [0, 8, -8]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "scale": [scale] * 3, "translation": [-0.75, 0, -1.25]},
            "thirdperson_righthand": {"rotation": [0, 270, 0], "scale": [scale] * 3, "translation": [1, 0, -1.75]},
        },
        "flip_v": True,
        "loader": "forge:obj",
        "model": f"{MOD}:models/block/multiblock/obj/{name}/{name}.obj",
    }
    if active:
        item["textures"] = {name: f"{MOD}:block/multiblock/{name}/default"}
    write_json(os.path.join(GEN, "assets", MOD, "models", "item", f"{name}.json"), item)

    # ---- loot table ----------------------------------------------------------------------------
    loot = {
        "type": "minecraft:block",
        "pools": [
            {
                "bonus_rolls": 0.0,
                "conditions": [{"condition": "minecraft:survives_explosion"}],
                "entries": [{"type": "immersiveengineering:drop_inv"}],
                "rolls": 1.0,
            },
            {
                "bonus_rolls": 0.0,
                "conditions": [{"condition": "minecraft:survives_explosion"}],
                "entries": [{"type": "immersiveengineering:multiblock"}],
                "rolls": 1.0,
            },
        ],
        "random_sequence": f"{MOD}:blocks/{name}",
    }
    write_json(os.path.join(GEN, "data", MOD, "loot_tables", "blocks", f"{name}.json"), loot)


def write_material_assets() -> None:
    # fluid blocks
    for fluid in ("flue_gas", "hot_water"):
        write_json(os.path.join(GEN, "assets", MOD, "blockstates", f"fluid_{fluid}_block.json"),
                   {"variants": {"": {"model": f"{MOD}:block/fluid/fluid_{fluid}"}}})
        write_json(os.path.join(GEN, "assets", MOD, "models", "block", "fluid", f"fluid_{fluid}.json"),
                   {"textures": {"particle": f"{MOD}:block/fluid/default_still"}})
        write_json(os.path.join(GEN, "assets", MOD, "models", "item", f"clean_flask_{fluid}_fluid.json"),
                   {"parent": f"{MOD}:item/base/ig_base_item",
                    "textures": {"layer0": f"{MOD}:item/greyscale/fluid/bucket_base",
                                 "layer1": f"{MOD}:item/greyscale/fluid/bucket_fluid"}})
        write_json(os.path.join(GEN, "data", "forge", "tags", "fluids", f"{fluid}.json"),
                   {"values": [f"{MOD}:fluid_{fluid}"]})

    def add_replaceable(data):
        for block in ("immersivegeology:fluid_flue_gas_block", "immersivegeology:fluid_hot_water_block"):
            if block not in data["values"]:
                data["values"].append(block)

    patch_json(os.path.join(GEN, "data", "minecraft", "tags", "blocks", "replaceable.json"), add_replaceable)

    # fly ash items (palette textures are produced by the runtime declared-asset pack)
    write_json(os.path.join(GEN, "assets", MOD, "models", "item", "powder_fly_ash.json"),
               {"parent": f"{MOD}:item/base/ig_base_item",
                "textures": {"layer0": f"{MOD}:palette/item/powder/type_1_pristine_fly_ash"}})
    write_json(os.path.join(GEN, "assets", MOD, "models", "item", "grit_fly_ash.json"),
               {"parent": f"{MOD}:item/base/ig_base_item",
                "textures": {"layer0": f"{MOD}:palette/item/grit/type_1_pristine_fly_ash"}})

    # new coal powder / grit item models
    for coal in ("bituminous", "anthracite"):
        write_json(os.path.join(GEN, "assets", MOD, "models", "item", f"powder_{coal}.json"),
                   {"parent": f"{MOD}:item/base/ig_base_item",
                    "textures": {"layer0": f"{MOD}:palette/item/powder/type_1_pristine_{coal}"}})
        write_json(os.path.join(GEN, "assets", MOD, "models", "item", f"grit_{coal}.json"),
                   {"parent": f"{MOD}:item/base/ig_base_item",
                    "textures": {"layer0": f"{MOD}:palette/item/grit/type_1_pristine_{coal}"}})

    # per material wrapper tags
    write_json(os.path.join(GEN, "data", "forge", "tags", "items", "powders", "fly_ash.json"),
               {"values": [f"{MOD}:powder_fly_ash"]})
    write_json(os.path.join(GEN, "data", "forge", "tags", "items", "grits", "fly_ash.json"),
               {"values": [f"{MOD}:grit_fly_ash"]})
    write_json(os.path.join(GEN, "data", "forge", "tags", "items", "material", "fly_ash.json"),
               {"values": [f"{MOD}:powder_fly_ash", f"{MOD}:grit_fly_ash"]})
    for coal in ("bituminous", "anthracite"):
        write_json(os.path.join(GEN, "data", "forge", "tags", "items", "powders", f"{coal}.json"),
                   {"values": [f"{MOD}:powder_{coal}"]})
        write_json(os.path.join(GEN, "data", "forge", "tags", "items", "grits", f"{coal}.json"),
                   {"values": [f"{MOD}:grit_{coal}"]})

    def add_to(path, items):
        def mutate(data):
            for item in items:
                if item not in data["values"]:
                    data["values"].append(item)
        patch_json(path, mutate)

    add_to(os.path.join(GEN, "data", "forge", "tags", "items", "powders.json"),
           [f"{MOD}:powder_fly_ash", f"{MOD}:powder_bituminous", f"{MOD}:powder_anthracite"])
    add_to(os.path.join(GEN, "data", "forge", "tags", "items", "grits.json"),
           [f"{MOD}:grit_fly_ash", f"{MOD}:grit_bituminous", f"{MOD}:grit_anthracite"])
    for coal in ("bituminous", "anthracite", "lignite"):
        material_tag = os.path.join(GEN, "data", "forge", "tags", "items", "material", f"{coal}.json")
        additions = [f"{MOD}:powder_{coal}"]
        if coal != "lignite":
            additions.append(f"{MOD}:grit_{coal}")
        add_to(material_tag, additions)


def write_recipes() -> None:
    base = os.path.join(GEN, "data", MOD, "recipes")

    fuels = [
        ("pulverised_lignite", {"tag": f"forge:powders/lignite"}, 80, 300, 450, 0.30, 0.86),
        ("pulverised_bituminous", {"tag": f"forge:powders/bituminous"}, 100, 480, 720, 0.22, 0.92),
        ("pulverised_anthracite", {"tag": f"forge:powders/anthracite"}, 140, 560, 840, 0.16, 0.94),
        ("lump_lignite", {"item": f"{MOD}:normal_ore_lignite"}, 120, 240, 360, 0.34, 0.78),
        ("lump_bituminous", {"item": f"{MOD}:normal_ore_bituminous"}, 150, 380, 570, 0.26, 0.82),
        ("lump_anthracite", {"item": f"{MOD}:normal_ore_anthracite"}, 200, 440, 660, 0.20, 0.84),
        ("coal", {"item": "minecraft:coal"}, 140, 400, 600, 0.24, 0.82),
        ("charcoal", {"item": "minecraft:charcoal"}, 80, 260, 390, 0.12, 0.80),
        ("coal_coke", {"item": "immersiveengineering:coal_coke"}, 160, 520, 780, 0.10, 0.93),
        ("pulverised_coke", {"item": "immersiveengineering:dust_coke"}, 90, 460, 690, 0.08, 0.95),
    ]
    for name, ingredient, burn, steam, gas, ash, eff in fuels:
        write_json(os.path.join(base, "boiler_fuel", f"{name}.json"), {
            "type": f"{MOD}:boiler_fuel",
            "burnTime": burn,
            "bottom_ash_chance": ash,
            "efficiency": eff,
            "flue_gas": gas,
            "input": ingredient,
            "steam": steam,
            "water": steam // 2,
        })

    for coal in ("lignite", "bituminous", "anthracite"):
        write_json(os.path.join(base, "crushing", f"crush_coal_{coal}_to_grit.json"), {
            "type": "immersiveengineering:crusher",
            "energy": 2400,
            "input": {"item": f"{MOD}:normal_ore_{coal}"},
            "result": {"item": f"{MOD}:grit_{coal}"},
            "secondaries": [],
            "time": 200,
        })
        write_json(os.path.join(base, "ballmill", f"pulverise_{coal}.json"), {
            "type": f"{MOD}:ballmill",
            "energy": 24000,
            "input": {"tag": f"forge:grits/{coal}"},
            "result": {"item": f"{MOD}:powder_{coal}"},
            "time": 3000,
        })
        write_json(os.path.join(base, "ballmill", f"pulverise_{coal}_from_ore.json"), {
            "type": f"{MOD}:ballmill",
            "energy": 36000,
            "input": {"item": f"{MOD}:normal_ore_{coal}"},
            "result": {"item": f"{MOD}:powder_{coal}"},
            "time": 5000,
        })

    write_json(os.path.join(base, "crafting", "craft_reinforced_concrete_with_fly_ash.json"), {
        "type": "minecraft:crafting_shaped",
        "category": "building",
        "group": "ig_engineering",
        "key": {
            "C": {"item": "immersiveengineering:concrete_bucket"},
            "F": {"item": f"{MOD}:powder_fly_ash"},
            "P": {"tag": "forge:plates/steel"},
        },
        "pattern": ["PFP", "FCF", "PFP"],
        "result": {"count": 9, "item": f"{MOD}:storage_block_srconcrete"},
        "show_notification": True,
    })


def main() -> None:
    for struct in ALL_STRUCTURES:
        write_multiblock_assets(struct)
        print("mirrored assets for", struct.name)
    write_material_assets()
    print("mirrored material assets and tags")
    write_recipes()
    print("mirrored recipes")


if __name__ == "__main__":
    main()
