#!/usr/bin/env python3
"""
Structure template authoring for the Immersive Geology coal CHP (ТЭЦ) chain.

Every Immersive Geology multiblock is described by a vanilla structure template
(`src/main/resources/data/immersivegeology/structures/multiblocks/<name>.nbt`).
The templates are normally saved in game with a structure block; this script
builds exactly the same file from a text layout so the whole chain can be
regenerated (and reviewed) without a running client.

Layout conventions (identical to the templates shipped with the mod):

* the template bounding box is ``size = (x, y, z)`` with **y up**,
* the machine faces **-Z** (``RelativeBlockFace.FRONT``), the player that forms
  the structure stands on the **+Z** side (``RelativeBlockFace.BACK``), which is
  where Immersive Geology puts the formation trigger block,
* only block states that survive ``BlockState#rotate``/``#mirror`` unambiguously
  are used, so a structure can be formed in any of the four rotations.

Run ``python3 tools/tpp/structures.py`` from the repository root.
"""

from __future__ import annotations

import os
from dataclasses import dataclass

import nbtlib
from nbtlib.tag import Byte, Compound, Int, List, String

DATA_VERSION = 3465  # 1.20.1, matches every other template in the mod
OUT_DIR = os.path.join(
    "src", "main", "resources", "data", "immersivegeology", "structures", "multiblocks"
)

# ------------------------------------------------------------------------------------------------
# Palette: every entry below was copied from an existing Immersive Geology template so the stored
# block state (including its property map) is guaranteed to be a real, placeable default state.
# ------------------------------------------------------------------------------------------------
BLOCKS = {
    ".": ("minecraft:air", {}),
    # --- Immersive Geology ---
    "C": ("immersivegeology:storage_block_srconcrete", {}),
    "R": ("immersivegeology:storage_block_industrial_refractory_brick", {}),
    "B": ("immersivegeology:storage_block_refractory_brick", {}),
    "T": ("immersivegeology:engineering_block_tungsten", {}),
    "H": ("immersivegeology:engineering_block_hastelloy", {}),
    "S": ("immersivegeology:sheetmetal_block_stainless_steel", {}),
    "M": ("immersivegeology:sheetmetal_block_hastelloy", {}),
    "N": ("immersivegeology:storage_block_stainless_steel", {}),
    "A": ("immersivegeology:storage_block_hastelloy", {}),
    "X": ("immersivegeology:storage_block_high_speed_steel", {}),
    "Y": ("immersivegeology:sheetmetal_block_high_speed_steel", {}),
    # --- Immersive Engineering ---
    "L": ("immersiveengineering:light_engineering", {}),
    "E": ("immersiveengineering:heavy_engineering", {}),
    "Q": ("immersiveengineering:rs_engineering", {}),
    "P": ("immersiveengineering:fluid_pipe", {"waterlogged": "false"}),
    "G": ("immersiveengineering:generator", {}),
    "D": ("immersiveengineering:radiator", {}),
    "1": ("immersiveengineering:coil_lv", {}),
    "2": ("immersiveengineering:coil_mv", {}),
    "3": ("immersiveengineering:coil_hv", {}),
    "K": ("immersiveengineering:sheetmetal_steel", {}),
    "O": ("immersiveengineering:storage_steel", {}),
    "U": ("immersiveengineering:concrete", {}),
    "V": ("immersiveengineering:concrete_tile", {}),
    "F": ("immersiveengineering:steel_scaffolding_standard", {"waterlogged": "false"}),
    "Z": ("immersiveengineering:slab_sheetmetal_steel", {"type": "bottom", "waterlogged": "false"}),
    "I": ("immersiveengineering:sheetmetal_iron", {}),
    # --- Vanilla ---
    "W": ("minecraft:hopper", {"facing": "down", "enabled": "true"}),
}


@dataclass
class Structure:
    """A multiblock structure template plus the metadata the Java side needs."""

    name: str
    size: tuple[int, int, int]
    master: tuple[int, int, int]
    trigger: tuple[int, int, int]
    layers: list[list[str]]
    comment: str = ""

    def cell(self, pos: tuple[int, int, int]) -> str:
        x, y, z = pos
        return self.layers[y][z][x]

    def blocks(self):
        """Yield ``(pos, code)`` for every position in the bounding box."""
        sx, sy, sz = self.size
        for y in range(sy):
            for z in range(sz):
                for x in range(sx):
                    yield (x, y, z), self.cell((x, y, z))

    def validate(self) -> None:
        sx, sy, sz = self.size
        assert len(self.layers) == sy, f"{self.name}: expected {sy} layers, got {len(self.layers)}"
        for y, layer in enumerate(self.layers):
            assert len(layer) == sz, f"{self.name}: layer {y} has {len(layer)} rows, expected {sz}"
            for z, row in enumerate(layer):
                assert len(row) == sx, f"{self.name}: layer {y} row {z} is {len(row)} wide, expected {sx}"
                for ch in row:
                    assert ch in BLOCKS, f"{self.name}: unknown block code {ch!r}"
        assert self.cell(self.master) != ".", f"{self.name}: master {self.master} is air"
        assert self.cell(self.trigger) != ".", f"{self.name}: trigger {self.trigger} is air"


def ring(width: int, depth: int, code: str, fill: str = ".") -> list[str]:
    """A ``width x depth`` layer that is a solid perimeter ring of ``code``."""
    rows = []
    for z in range(depth):
        row = ""
        for x in range(width):
            edge = x in (0, width - 1) or z in (0, depth - 1)
            row += code if edge else fill
        rows.append(row)
    return rows


def put(layer: list[str], x: int, y_unused: int, z: int, code: str) -> list[str]:
    """Helper mirroring the ``(x, y, z)`` argument order used by the Java code."""
    row = list(layer[z])
    row[x] = code
    layer[z] = "".join(row)
    return layer


# =================================================================================================
# 1. Coal boiler - pulverised coal fired steam boiler (пылеугольный котлоагрегат)
#    y0 foundation / ash hopper / forced draft fan
#    y1 furnace (топка) with the burner register            <- formation trigger
#    y2 evaporator waterwalls (испарительные экраны)
#    y3 superheater (пароперегреватель)                     <- live steam outlet
#    y4 steam drum + economizer (барабан, экономайзер)      <- feed water inlet
#    y5 gas pass / induced draft fan (газоход, дымосос)
#    y6 roof, pulverised coal bunker, gas duct, control panel
# =================================================================================================
COAL_BOILER = Structure(
    name="coal_boiler",
    size=(5, 7, 5),
    master=(2, 1, 2),
    trigger=(2, 1, 4),
    comment="Pulverised coal fired steam boiler (котлоагрегат)",
    layers=[
        # y = 0 - foundation, bottom ash hopper, forced draft fan drives
        [
            "CCCCC",
            "CTTTC",
            "CTWTC",
            "CTTTC",
            "CCCCC",
        ],
        # y = 1 - furnace with the burner register, formation trigger on the back wall
        [
            "RRRRR",
            "R...R",
            "R.E.R",
            "R...R",
            "RRLRR",
        ],
        # y = 2 - evaporator waterwalls / risers
        [
            "RRRRR",
            "R.P.R",
            "RPEPR",
            "R.P.R",
            "RRRRR",
        ],
        # y = 3 - superheater, live steam leaves through the front wall
        [
            "SSSSS",
            "S.P.S",
            "SPEPS",
            "S.P.S",
            "SSSSS",
        ],
        # y = 4 - steam drum and economizer, feed water enters at the back
        [
            "OOOOO",
            "OPPPO",
            "OPOPO",
            "OPPPO",
            "OOOOO",
        ],
        # y = 5 - convective gas pass, induced draft fan
        [
            "SSSSS",
            "SMMMS",
            "SMHMS",
            "SMMMS",
            "SSSSS",
        ],
        # y = 6 - roof: flue gas duct (front), pulverised coal bunker, control panel (back)
        [
            "..P..",
            ".KKK.",
            "PKOKP",
            ".KKK.",
            "..Q..",
        ],
    ],
)

# =================================================================================================
# 2. Electrostatic precipitator - flue gas cleaning (электрофильтр)
#    y0 foundation with the fly ash hoppers
#    y1 housing, dirty gas duct (front)                     <- formation trigger
#    y2..y4 three electrode fields (corona discharge)
#    y5 outlet plenum with the HV rectifier sets
#    y6 roof
#    y7..y8 stack (дымовая труба)
# =================================================================================================
ELECTROSTATIC_PRECIPITATOR = Structure(
    name="electrostatic_precipitator",
    size=(5, 9, 4),
    master=(2, 0, 2),
    trigger=(2, 1, 3),
    comment="Electrostatic precipitator with stack (электрофильтр с дымовой трубой)",
    layers=[
        # y = 0 - foundation and fly ash hoppers
        [
            "CCCCC",
            "CWCWC",
            "CWCWC",
            "CCCCC",
        ],
        # y = 1 - housing, dirty flue gas enters through the front duct
        [
            "MMPMM",
            "M...M",
            "M...M",
            "MMLMM",
        ],
        # y = 2 - first electrode field
        [
            "MMMMM",
            "M.H.M",
            "M.H.M",
            "MMMMM",
        ],
        # y = 3 - second electrode field with the rapping gear and the HV control panel
        [
            "MMMMM",
            "M.E.M",
            "M.E.M",
            "MMQMM",
        ],
        # y = 4 - third electrode field
        [
            "MMMMM",
            "M.H.M",
            "M.H.M",
            "MMMMM",
        ],
        # y = 5 - outlet plenum, cleaned gas riser and the rectifier transformers
        [
            "MMPMM",
            "M3M3M",
            "MMMMM",
            "MMMMM",
        ],
        # y = 6 - roof
        [
            "KKUKK",
            "KKKKK",
            "KKKKK",
            "KKKKK",
        ],
        # y = 7 - stack
        [
            "..U..",
            ".....",
            ".....",
            ".....",
        ],
        # y = 8 - stack
        [
            "..U..",
            ".....",
            ".....",
            ".....",
        ],
    ],
)

# =================================================================================================
# 3. Steam condenser - surface condenser + condensate/deaerator pump set (поверхностный конденсатор)
#    y0 hotwell and the cooling water waterboxes
#    y1 tube bundle (radiator blocks) with the condensate pumps   <- formation trigger
#    y2 steam inlet chest from the turbine and the air ejector
# =================================================================================================
STEAM_CONDENSER = Structure(
    name="steam_condenser",
    size=(4, 3, 4),
    master=(1, 0, 1),
    trigger=(1, 1, 3),
    comment="Surface condenser with condensate pumps (поверхностный конденсатор)",
    layers=[
        # y = 0 - hotwell, cooling water waterboxes at both ends
        [
            "NPPN",
            "PNNP",
            "PNNP",
            "NPPN",
        ],
        # y = 1 - tube bundle, condensate pumps and the control panel
        [
            "NDDN",
            "D..D",
            "D..D",
            "NLQL",
        ],
        # y = 2 - steam inlet chest and the air ejector
        [
            "NPPN",
            "PNQP",
            "PNNP",
            "NPPN",
        ],
    ],
)

# =================================================================================================
# 4. Cooling tower - natural draft hyperbolic tower (градирня)
#    y0 collecting basin (cold water)
#    y1 air inlet louvers                                   <- formation trigger
#    y2 fill / packing (ороситель) with the spray header
#    y3 drift eliminator (каплеотбойник)
#    y4..y8 hyperboloid shell, throat at y6..y7
# =================================================================================================
def _cooling_tower_layers() -> list[list[str]]:
    w = d = 7

    # y0 - the collecting basin is a solid slab, the cold water outlet sits in its middle
    basin = [["C"] * w for _ in range(d)]
    basin = ["".join(row) for row in basin]
    put(basin, 3, 0, 3, "P")

    # y1 - air inlet louvers all the way around, open in the middle
    louvers = ring(w, d, "F", ".")
    put(louvers, 3, 0, 6, "L")

    # y2/y3 - fill packing and drift eliminator hanging inside a concrete ring
    fill = ring(w, d, "U", "Z")
    put(fill, 3, 0, 3, "P")
    eliminator = ring(w, d, "U", "Z")

    # y4..y8 - hyperboloid shell: wide, throat at y6/y7, flared rim on top
    shell_wide = ring(w, d, "U", ".")
    shell_throat = ring(w, d, ".", ".")
    for z in range(1, d - 1):
        for x in range(1, w - 1):
            if x in (1, w - 2) or z in (1, d - 2):
                put(shell_throat, x, 0, z, "U")

    return [
        basin,          # y0
        louvers,        # y1
        fill,           # y2
        eliminator,     # y3
        shell_wide,     # y4
        shell_wide,     # y5
        shell_throat,   # y6
        shell_throat,   # y7
        shell_wide,     # y8
    ]


COOLING_TOWER = Structure(
    name="cooling_tower",
    size=(7, 9, 7),
    master=(3, 0, 3),
    trigger=(3, 1, 6),
    comment="Natural draft hyperbolic cooling tower (градирня)",
    layers=_cooling_tower_layers(),
)

# =================================================================================================
# 5. District heater - heat extraction / network water heater (сетевой подогреватель, ПСВ)
#    y0 frame and network water pumps                       <- formation trigger
#    y1..y3 tube bundle with the steam chest on top
# =================================================================================================
DISTRICT_HEATER = Structure(
    name="district_heater",
    size=(3, 4, 3),
    master=(1, 1, 1),
    trigger=(1, 0, 1),
    comment="District heating water heater (сетевой подогреватель)",
    layers=[
        # y = 0 - frame, network water pumps, formation trigger
        [
            "NNN",
            "PLP",
            "NNN",
        ],
        # y = 1 - tube bundle, network water in / hot water out
        [
            "NPN",
            "PDP",
            "NPN",
        ],
        # y = 2 - tube bundle and the control panel
        [
            "NPN",
            "PDP",
            "NQN",
        ],
        # y = 3 - steam chest
        [
            "PPP",
            "PDP",
            "PPP",
        ],
    ],
)

ALL_STRUCTURES = [
    COAL_BOILER,
    ELECTROSTATIC_PRECIPITATOR,
    STEAM_CONDENSER,
    COOLING_TOWER,
    DISTRICT_HEATER,
]


def build_nbt(struct: Structure) -> nbtlib.File:
    struct.validate()
    palette: list[tuple[str, dict]] = []
    index: dict[tuple[str, tuple], int] = {}
    blocks = List[Compound]()

    for pos, code in struct.blocks():
        name, props = BLOCKS[code]
        key = (name, tuple(sorted(props.items())))
        if key not in index:
            index[key] = len(palette)
            palette.append((name, props))
        entry = Compound()
        entry["pos"] = List[Int]([Int(pos[0]), Int(pos[1]), Int(pos[2])])
        entry["state"] = Int(index[key])
        blocks.append(entry)

    palette_tag = List[Compound]()
    for name, props in palette:
        entry = Compound()
        entry["Name"] = String(name)
        if props:
            entry["Properties"] = Compound({k: String(v) for k, v in props.items()})
        palette_tag.append(entry)

    root = Compound()
    root["size"] = List[Int]([Int(struct.size[0]), Int(struct.size[1]), Int(struct.size[2])])
    root["entities"] = List[Compound]()
    root["blocks"] = blocks
    root["palette"] = palette_tag
    root["DataVersion"] = Int(DATA_VERSION)

    file = nbtlib.File(root, gzipped=True)
    file.root_name = ""
    return file


def ascii_map(struct: Structure) -> str:
    lines = [
        f"{struct.name}  size={struct.size}  master={struct.master}  trigger={struct.trigger}",
        f"  # {struct.comment}",
    ]
    solid = 0
    for y, layer in enumerate(struct.layers):
        lines.append(f"  y={y}")
        lines.append("      " + "".join(f"{x:>4}" for x in range(struct.size[0])))
        for z, row in enumerate(layer):
            lines.append(f"   z={z} " + "".join(f"{ch:>4}" for ch in row))
            solid += sum(1 for ch in row if ch != ".")
    lines.append(f"  solid blocks: {solid} / {struct.size[0] * struct.size[1] * struct.size[2]}")
    return "\n".join(lines)


def main() -> None:
    os.makedirs(OUT_DIR, exist_ok=True)
    for struct in ALL_STRUCTURES:
        path = os.path.join(OUT_DIR, struct.name + ".nbt")
        build_nbt(struct).save(path)
        print(f"wrote {path} ({os.path.getsize(path)} bytes)")
        print(ascii_map(struct))
        print()


if __name__ == "__main__":
    main()
