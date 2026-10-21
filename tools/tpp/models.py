#!/usr/bin/env python3
"""
Model + texture authoring for the Immersive Geology coal CHP (ТЭЦ) multiblocks.

Immersive Geology renders a formed multiblock with a single OBJ model that covers the whole
structure. Datagen (``IGBlockStateProvider``) feeds that OBJ to Immersive Engineering's
``basic_split`` model loader, which cuts it into one part per non-air block of the structure
template. That means the model has to obey two rules:

* **1 unit = 1 block**, and the origin is the master block: a structure block at
  ``(x, y, z)`` occupies ``[x-masterX, x-masterX+1] x [y-masterY, ...] x [z-masterZ, ...]``
  in model space (this is exactly the offset ``split()`` applies to the template positions),
* the whole model uses **one material named after the multiblock**, whose ``map_Kd`` is either
  ``#<multiblock>`` (datagen injects the skin texture) or an absolute texture path.

The machines added here are generated procedurally so the chain is playable straight away;
``docs/MULTIBLOCK_MODELS.md`` explains how to replace any of them with hand made art.

Run ``python3 tools/tpp/models.py`` from the repository root.
"""

from __future__ import annotations

import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from structures import ALL_STRUCTURES, Structure  # noqa: E402

ASSETS = os.path.join("src", "main", "resources", "assets", "immersivegeology")
OBJ_DIR = os.path.join(ASSETS, "models", "block", "multiblock", "obj")
TEX_DIR = os.path.join(ASSETS, "textures", "block", "multiblock")

TILE = 32          # pixels per tile
TILES = 4          # tiles per row/column -> 128x128 texture

# Tile slots, (column, row) with the row counted from the TOP of the png.
T_STEEL_PANEL = (0, 0)
T_MACHINERY_LIGHT = (1, 0)
T_MACHINERY_HEAVY = (2, 0)
T_CONTROL = (3, 0)
T_REFRACTORY = (0, 1)
T_REFRACTORY_SOOTY = (1, 1)
T_PIPE = (2, 1)
T_RADIATOR = (3, 1)
T_CONCRETE = (0, 2)
T_CONCRETE_STEEL = (1, 2)
T_COIL = (2, 2)
T_SHEETMETAL = (3, 2)
T_SCAFFOLD = (0, 3)
T_GRATE = (1, 3)
T_HOPPER = (2, 3)
T_STEEL_PLATE = (3, 3)

# structure block code -> tiles used for (sides, top, bottom)
CODE_TILES = {
    "C": (T_CONCRETE_STEEL, T_CONCRETE_STEEL, T_CONCRETE_STEEL),
    "R": (T_REFRACTORY_SOOTY, T_REFRACTORY_SOOTY, T_REFRACTORY_SOOTY),
    "B": (T_REFRACTORY, T_REFRACTORY, T_REFRACTORY),
    "T": (T_MACHINERY_HEAVY, T_STEEL_PLATE, T_STEEL_PLATE),
    "H": (T_MACHINERY_HEAVY, T_STEEL_PLATE, T_STEEL_PLATE),
    "S": (T_SHEETMETAL, T_STEEL_PANEL, T_STEEL_PANEL),
    "M": (T_SHEETMETAL, T_STEEL_PANEL, T_STEEL_PANEL),
    "N": (T_STEEL_PANEL, T_STEEL_PANEL, T_STEEL_PANEL),
    "A": (T_STEEL_PANEL, T_STEEL_PANEL, T_STEEL_PANEL),
    "X": (T_STEEL_PANEL, T_STEEL_PANEL, T_STEEL_PANEL),
    "Y": (T_SHEETMETAL, T_STEEL_PANEL, T_STEEL_PANEL),
    "L": (T_MACHINERY_LIGHT, T_STEEL_PLATE, T_STEEL_PLATE),
    "E": (T_MACHINERY_HEAVY, T_STEEL_PLATE, T_STEEL_PLATE),
    "Q": (T_CONTROL, T_STEEL_PLATE, T_STEEL_PLATE),
    "P": (T_PIPE, T_PIPE, T_PIPE),
    "G": (T_COIL, T_STEEL_PLATE, T_STEEL_PLATE),
    "D": (T_RADIATOR, T_STEEL_PLATE, T_STEEL_PLATE),
    "1": (T_COIL, T_STEEL_PLATE, T_STEEL_PLATE),
    "2": (T_COIL, T_STEEL_PLATE, T_STEEL_PLATE),
    "3": (T_COIL, T_STEEL_PLATE, T_STEEL_PLATE),
    "K": (T_SHEETMETAL, T_STEEL_PANEL, T_STEEL_PANEL),
    "O": (T_STEEL_PLATE, T_STEEL_PLATE, T_STEEL_PLATE),
    "U": (T_CONCRETE, T_CONCRETE, T_CONCRETE),
    "V": (T_CONCRETE, T_CONCRETE, T_CONCRETE),
    "F": (T_SCAFFOLD, T_SCAFFOLD, T_SCAFFOLD),
    "Z": (T_GRATE, T_GRATE, T_GRATE),
    "W": (T_HOPPER, T_STEEL_PLATE, T_HOPPER),
}


# =================================================================================================
# OBJ builder
# =================================================================================================
class ObjBuilder:
    """Collects quads and writes a Minecraft/Forge friendly Wavefront file."""

    def __init__(self) -> None:
        self.vertices: list[tuple[float, float, float]] = []
        self.uvs: list[tuple[float, float]] = []
        self.normals: dict[tuple[int, int, int], int] = {}
        self.faces: list[tuple[list[int], list[int], int]] = []

    # -- primitives ---------------------------------------------------------------------------
    def _normal(self, n: tuple[int, int, int]) -> int:
        if n not in self.normals:
            self.normals[n] = len(self.normals) + 1
        return self.normals[n]

    def quad(self, pts, normal, tile, uv_origin, uv_axes):
        """``pts`` are 4 CCW (seen from outside) corners, ``uv_origin``/``uv_axes`` map a corner
        to (a, b) in [0, 1]^2 inside the tile: ``a`` runs along ``uv_axes[0]``, ``b`` along the
        second one. Both are given as (coordinate index, scale, offset)."""
        u0, u1, v0, v1 = tile_uv(tile)
        v_idx, t_idx, n_idx = [], [], self._normal(normal)
        for p in pts:
            self.vertices.append(p)
            v_idx.append(len(self.vertices))
            a = (p[uv_axes[0][0]] - uv_origin[0]) * uv_axes[0][1]
            b = (p[uv_axes[1][0]] - uv_origin[1]) * uv_axes[1][1]
            a = a - math.floor(a)
            b = b - math.floor(b)
            self.uvs.append((u0 + (u1 - u0) * a, v0 + (v1 - v0) * b))
            t_idx.append(len(self.uvs))
        self.faces.append((v_idx, t_idx, n_idx))

    def box(self, x0, y0, z0, x1, y1, z1, side, top=None, bottom=None):
        top = top or side
        bottom = bottom or side
        # +X
        self.quad([(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)], (1, 0, 0),
                  side, (z0, y0), ((2, 1.0, 0), (1, 1.0, 0)))
        # -X
        self.quad([(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], (-1, 0, 0),
                  side, (z0, y0), ((2, 1.0, 0), (1, 1.0, 0)))
        # +Y
        self.quad([(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)], (0, 1, 0),
                  top, (x0, z0), ((0, 1.0, 0), (2, 1.0, 0)))
        # -Y
        self.quad([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0),
                  bottom, (x0, z0), ((0, 1.0, 0), (2, 1.0, 0)))
        # +Z
        self.quad([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1),
                  side, (x0, y0), ((0, 1.0, 0), (1, 1.0, 0)))
        # -Z
        self.quad([(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)], (0, 0, -1),
                  side, (x0, y0), ((0, 1.0, 0), (1, 1.0, 0)))

    def prism(self, cx, y0, cz, radius, y1, tile, sides=8, cap=True):
        """A vertical polygonal prism, used for coils and stacks."""
        step = 2 * math.pi / sides
        ring_bottom, ring_top = [], []
        for i in range(sides):
            a0 = i * step
            a1 = (i + 1) * step
            x_a, z_a = cx + radius * math.cos(a0), cz + radius * math.sin(a0)
            x_b, z_b = cx + radius * math.cos(a1), cz + radius * math.sin(a1)
            nx = math.cos((a0 + a1) / 2)
            nz = math.sin((a0 + a1) / 2)
            normal = (int(round(nx * 3)) or 1, 0, int(round(nz * 3)) or 1)
            self.quad([(x_a, y0, z_a), (x_b, y0, z_b), (x_b, y1, z_b), (x_a, y1, z_a)],
                      normal, tile, (0, y0), ((0, 1.0, 0), (1, 1.0, 0)))
            ring_bottom.append((x_a, y0, z_a))
            ring_top.append((x_a, y1, z_a))
        if cap:
            self.fan(ring_top[::-1], (0, 1, 0), tile)
            self.fan(ring_bottom, (0, -1, 0), tile)

    def fan(self, ring, normal, tile):
        """Triangle fan for a convex polygonal cap."""
        u0, u1, v0, v1 = tile_uv(tile)
        cx = sum(p[0] for p in ring) / len(ring)
        cz = sum(p[2] for p in ring) / len(ring)
        y = ring[0][1]
        n_idx = self._normal(normal)
        for i in range(len(ring) - 1):
            pts = [(cx, y, cz), ring[i], ring[i + 1]]
            v_idx, t_idx = [], []
            for p in pts:
                self.vertices.append(p)
                v_idx.append(len(self.vertices))
                a = (p[0] - cx) / 2 + 0.5
                b = (p[2] - cz) / 2 + 0.5
                self.uvs.append((u0 + (u1 - u0) * a, v0 + (v1 - v0) * b))
                t_idx.append(len(self.uvs))
            self.faces.append((v_idx, t_idx, n_idx))

    # -- output -------------------------------------------------------------------------------
    def dedupe(self) -> None:
        """Merge identical vertex/uv/normal triples - the generated boxes share a lot of corners."""
        v_map: dict[tuple, int] = {}
        t_map: dict[tuple, int] = {}
        new_v: list[tuple[float, float, float]] = []
        new_t: list[tuple[float, float]] = []
        faces = []
        for v_idx, t_idx, n_idx in self.faces:
            nv, nt = [], []
            for v, t in zip(v_idx, t_idx):
                key_v = (round(self.vertices[v - 1][0], 5), round(self.vertices[v - 1][1], 5),
                         round(self.vertices[v - 1][2], 5))
                key_t = (round(self.uvs[t - 1][0], 5), round(self.uvs[t - 1][1], 5))
                if key_v not in v_map:
                    v_map[key_v] = len(new_v) + 1
                    new_v.append(self.vertices[v - 1])
                if key_t not in t_map:
                    t_map[key_t] = len(new_t) + 1
                    new_t.append(self.uvs[t - 1])
                nv.append(v_map[key_v])
                nt.append(t_map[key_t])
            faces.append((nv, nt, n_idx))
        self.vertices, self.uvs, self.faces = new_v, new_t, faces

    def write(self, path: str, material: str) -> None:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write("# Immersive Geology - procedural multiblock model\n")
            fh.write("# Regenerate with: python3 tools/tpp/models.py\n")
            fh.write(f"mtllib {os.path.basename(path)[:-4]}.mtl\n")
            fh.write(f"o {material}\n")
            for v in self.vertices:
                fh.write("v %.6f %.6f %.6f\n" % v)
            for t in self.uvs:
                fh.write("vt %.6f %.6f\n" % t)
            normals = sorted(self.normals.items(), key=lambda kv: kv[1])
            for n, _ in normals:
                length = math.sqrt(sum(c * c for c in n)) or 1.0
                fh.write("vn %.6f %.6f %.6f\n" % (n[0] / length, n[1] / length, n[2] / length))
            fh.write("s off\n")
            fh.write(f"usemtl {material}\n")
            for v_idx, t_idx, n_idx in self.faces:
                fh.write("f " + " ".join(f"{v}/{t}/{n_idx}" for v, t in zip(v_idx, t_idx)) + "\n")

    @property
    def stats(self) -> tuple[int, int]:
        return len(self.vertices), len(self.faces)


def tile_uv(tile) -> tuple[float, float, float, float]:
    """Return (u0, u1, v0, v1) for a tile. ``v`` is written in Blender convention (0 at the
    bottom) because the Forge OBJ loader is configured with ``flipV(true)`` by IG datagen."""
    tx, ty = tile
    u0 = tx / TILES
    u1 = (tx + 1) / TILES
    v0 = 1.0 - (ty + 1) / TILES
    v1 = 1.0 - ty / TILES
    return u0, u1, v0, v1


# =================================================================================================
# Per block code geometry
# =================================================================================================
def emit_block(b: ObjBuilder, code: str, x: float, y: float, z: float, solid) -> None:
    """Emit the geometry of one structure block.

    ``solid`` answers whether a neighbouring cell holds a block, so protruding detail is only
    added on faces that are actually visible - anything poking into a neighbour would z-fight.
    """
    side, top, bottom = CODE_TILES[code]
    open_front = not solid("north")
    open_back = not solid("south")
    if code == "P":  # fluid pipe: stub with two flanges
        b.box(x + 0.26, y, z + 0.26, x + 0.74, y + 1.0, z + 0.74, side, top, bottom)
        b.box(x + 0.18, y + 0.40, z + 0.18, x + 0.82, y + 0.60, z + 0.82, T_STEEL_PLATE)
    elif code == "F":  # scaffolding: four corner posts, reads as an open frame
        for dx, dz in ((0.0, 0.0), (0.86, 0.0), (0.0, 0.86), (0.86, 0.86)):
            b.box(x + dx, y, z + dz, x + dx + 0.14, y + 1.0, z + dz + 0.14, side, top, bottom)
        b.box(x, y + 0.86, z, x + 1.0, y + 1.0, z + 1.0, side, top, bottom)
    elif code == "Z":  # slab / packing: a thin plate in the middle of the cell
        b.box(x + 0.02, y + 0.34, z + 0.02, x + 0.98, y + 0.62, z + 0.98, side, top, bottom)
    elif code == "W":  # hopper: wide throat tapering into a chute
        b.box(x + 0.06, y + 0.42, z + 0.06, x + 0.94, y + 1.0, z + 0.94, side, top, bottom)
        b.box(x + 0.30, y, z + 0.30, x + 0.70, y + 0.44, z + 0.70, side, top, bottom)
    elif code == "D":  # radiator / tube bundle: core plus fin plates
        b.box(x + 0.08, y + 0.08, z + 0.08, x + 0.92, y + 0.92, z + 0.92, T_STEEL_PLATE)
        for i in range(4):
            o = 0.14 + i * 0.20
            b.box(x + o, y + 0.02, z + 0.02, x + o + 0.10, y + 0.98, z + 0.98, side, top, bottom)
    elif code in ("1", "2", "3", "G"):  # coils: winding column on a base plate
        b.box(x, y, z, x + 1.0, y + 0.12, z + 1.0, T_STEEL_PLATE)
        b.prism(x + 0.5, y + 0.12, z + 0.5, 0.40, y + 0.95, side)
        b.box(x + 0.06, y + 0.95, z + 0.06, x + 0.94, y + 1.0, z + 0.94, T_STEEL_PLATE)
    elif code in ("L", "E", "Q", "T", "H"):  # machinery: block with a bolted service panel
        b.box(x, y, z, x + 1.0, y + 1.0, z + 1.0, side, top, bottom)
        if open_front:
            b.box(x + 0.14, y + 0.14, z - 0.03, x + 0.86, y + 0.86, z + 0.01, T_STEEL_PLATE)
        if open_back:
            b.box(x + 0.14, y + 0.14, z + 0.99, x + 0.86, y + 0.86, z + 1.03, T_STEEL_PLATE)
    else:
        b.box(x, y, z, x + 1.0, y + 1.0, z + 1.0, side, top, bottom)


def build_obj(struct: Structure) -> ObjBuilder:
    b = ObjBuilder()
    mx, my, mz = struct.master
    sx, sy, sz = struct.size

    def solid_at(x: int, y: int, z: int) -> bool:
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            return False
        return struct.cell((x, y, z)) != "."

    for pos, code in struct.blocks():
        if code == ".":
            continue
        x, y, z = pos

        def solid(direction: str, x=x, y=y, z=z) -> bool:
            dx, dy, dz = {
                "north": (0, 0, -1), "south": (0, 0, 1),
                "west": (-1, 0, 0), "east": (1, 0, 0),
                "up": (0, 1, 0), "down": (0, -1, 0),
            }[direction]
            return solid_at(x + dx, y + dy, z + dz)

        emit_block(b, code, x - mx, y - my, z - mz, solid)
    return b


def write_mtl(struct: Structure, absolute_texture: bool) -> None:
    folder = os.path.join(OBJ_DIR, struct.name)
    os.makedirs(folder, exist_ok=True)
    texture = (
        f"immersivegeology:block/multiblock/{struct.name}/default"
        if absolute_texture
        else f"#{struct.name}"
    )
    with open(os.path.join(folder, struct.name + ".mtl"), "w", encoding="utf-8") as fh:
        fh.write("# Immersive Geology - procedural multiblock material\n")
        fh.write("# Regenerate with: python3 tools/tpp/models.py\n\n")
        fh.write(f"newmtl {struct.name}\n")
        fh.write("Ns 0.000000\nKa 1.000000 1.000000 1.000000\n")
        fh.write("Ks 0.000000 0.000000 0.000000\nKe 0.000000 0.000000 0.000000\n")
        fh.write("Ni 1.500000\nd 1.000000\nillum 1\n")
        fh.write(f"map_Kd {texture}\n")


# =================================================================================================
# Texture painting
# =================================================================================================
Palette = dict


def _noise(draw: ImageDraw.ImageDraw, x0: int, y0: int, size: int, colour, amount: int, seed: int) -> None:
    rnd = _rnd(seed)
    for _ in range(amount):
        px = x0 + int(rnd() * size)
        py = y0 + int(rnd() * size)
        draw.point((px, py), fill=colour)


def _rnd(seed: int):
    state = [seed * 1103515245 + 12345 & 0x7FFFFFFF]

    def next_value():
        state[0] = (state[0] * 1103515245 + 12345) & 0x7FFFFFFF
        return state[0] / 0x7FFFFFFF

    return next_value


def _shade(colour, factor: float):
    r, g, b = colour[:3]
    return (max(0, min(255, int(r * factor))), max(0, min(255, int(g * factor))),
            max(0, min(255, int(b * factor))), 255)


def tile_rect(tile) -> tuple[int, int, int, int]:
    tx, ty = tile
    return tx * TILE, ty * TILE, tx * TILE + TILE, ty * TILE + TILE


def paint_steel_panel(img, tile, pal: Palette) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["steel"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    _noise(d, x0, y0, TILE, _shade(base, 1.08), 90, 7)
    _noise(d, x0, y0, TILE, _shade(base, 0.9), 90, 11)
    # plate seams
    seam = _shade(base, 0.62)
    d.line([(x0, y0 + 15), (x1 - 1, y0 + 15)], fill=seam)
    d.line([(x0, y0 + 16), (x1 - 1, y0 + 16)], fill=_shade(base, 1.18))
    d.line([(x0 + 15, y0), (x0 + 15, y1 - 1)], fill=seam)
    d.line([(x0 + 16, y0), (x0 + 16, y1 - 1)], fill=_shade(base, 1.18))
    # rivets
    rivet = _shade(base, 1.35)
    for rx, ry in ((5, 5), (26, 5), (5, 26), (26, 26), (5, 21), (26, 21)):
        d.ellipse([x0 + rx - 1, y0 + ry - 1, x0 + rx + 1, y0 + ry + 1], fill=rivet)
        d.point((x0 + rx, y0 + ry + 1), fill=seam)


def paint_sheetmetal(img, tile, pal: Palette) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["steel"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    for i in range(0, TILE, 4):  # corrugation
        d.line([(x0 + i, y0), (x0 + i, y1 - 1)], fill=_shade(base, 0.78))
        d.line([(x0 + i + 2, y0), (x0 + i + 2, y1 - 1)], fill=_shade(base, 1.16))
    d.rectangle([x0, y0, x1 - 1, y1 - 1], outline=_shade(base, 0.6))
    _noise(d, x0, y0, TILE, _shade(base, 0.92), 60, 3)


def paint_steel_plate(img, tile, pal: Palette) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = _shade(pal["steel"], 1.06)
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    _noise(d, x0, y0, TILE, _shade(base, 0.93), 110, 13)
    d.rectangle([x0 + 1, y0 + 1, x1 - 2, y1 - 2], outline=_shade(base, 0.72))
    bolt = _shade(base, 1.4)
    for bx, by in ((4, 4), (27, 4), (4, 27), (27, 27)):
        d.rectangle([x0 + bx - 1, y0 + by - 1, x0 + bx + 1, y0 + by + 1], fill=bolt)


def paint_machinery(img, tile, pal: Palette, heavy: bool) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["machine_dark"] if heavy else pal["machine"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    _noise(d, x0, y0, TILE, _shade(base, 1.1), 70, 17)
    d.rectangle([x0 + 2, y0 + 2, x1 - 3, y1 - 3], outline=_shade(base, 0.66), width=1)
    d.rectangle([x0 + 5, y0 + 5, x1 - 6, y1 - 6], fill=_shade(base, 0.84))
    if heavy:
        # bolted hatch
        hatch = _shade(base, 1.22)
        d.ellipse([x0 + 8, y0 + 8, x0 + 23, y0 + 23], outline=hatch, width=2)
        d.line([(x0 + 11, y0 + 15), (x0 + 20, y0 + 15)], fill=hatch, width=2)
        for a in range(8):
            ang = a * math.pi / 4
            px = int(x0 + 15.5 + 10 * math.cos(ang))
            py = int(y0 + 15.5 + 10 * math.sin(ang))
            d.point((px, py), fill=hatch)
    else:
        # vent slits over a painted inspection panel
        slit = _shade(base, 0.55)
        for i in range(5):
            d.rectangle([x0 + 8, y0 + 7 + i * 3, x1 - 9, y0 + 8 + i * 3], fill=slit)
        d.rectangle([x0 + 5, y0 + 23, x1 - 6, y0 + 28], fill=_shade(base, 1.18))
        d.rectangle([x0 + 7, y0 + 24, x1 - 8, y0 + 27], fill=pal["accent"])


def paint_control(img, tile, pal: Palette, active: bool = False) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["machine"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    d.rectangle([x0 + 3, y0 + 3, x1 - 4, y1 - 4], fill=_shade(base, 0.7), outline=_shade(base, 1.3))
    d.rectangle([x0 + 6, y0 + 6, x1 - 7, y0 + 16], fill=(18, 26, 24, 255))
    screen = (96, 214, 128, 255) if active else (48, 96, 62, 255)
    for i in range(4):
        d.rectangle([x0 + 8, y0 + 8 + i * 2, x1 - 12 + i, y0 + 8 + i * 2], fill=screen)
    lamps = [(214, 74, 60, 255), (232, 190, 72, 255), (96, 214, 128, 255)]
    for i, colour in enumerate(lamps):
        lit = colour if active else _shade(colour, 0.4)
        d.ellipse([x0 + 7 + i * 6, y0 + 20, x0 + 11 + i * 6, y0 + 24], fill=lit)
    d.rectangle([x0 + 6, y0 + 26, x1 - 7, y1 - 6], fill=_shade(base, 1.2))


def paint_refractory(img, tile, pal: Palette, sooty: bool) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["refractory_soot"] if sooty else pal["refractory"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    mortar = _shade(base, 0.68)
    rows = 4
    for r in range(rows):
        yy = y0 + r * (TILE // rows)
        d.line([(x0, yy), (x1 - 1, yy)], fill=mortar)
        offset = (TILE // (rows * 2)) if r % 2 else 0
        for c in range(3):
            xx = x0 + offset + c * (TILE // 2)
            d.line([(xx, yy), (xx, yy + TILE // rows)], fill=mortar)
        for c in range(3):
            bx = x0 + offset + c * (TILE // 2) + 2
            by = yy + 2
            d.rectangle([bx, by, bx + TILE // 2 - 5, yy + TILE // rows - 2],
                        fill=_shade(base, 1.0 + (0.06 if (r + c) % 2 else -0.05)))
    _noise(d, x0, y0, TILE, _shade(base, 0.8), 130, 23)
    if sooty:
        _noise(d, x0, y0, TILE, (24, 20, 20, 255), 150, 29)


def paint_pipe(img, tile, pal: Palette) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["pipe"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    for i in range(0, TILE, 4):  # longitudinal shading, reads as a round pipe
        factor = 0.72 + 0.5 * math.sin(math.pi * i / TILE)
        d.line([(x0 + i, y0), (x0 + i, y1 - 1)], fill=_shade(base, factor))
    flange = _shade(base, 1.28)
    d.rectangle([x0, y0 + 4, x1 - 1, y0 + 8], fill=flange)
    d.rectangle([x0, y0 + 22, x1 - 1, y0 + 26], fill=flange)
    d.line([(x0, y0 + 4), (x1 - 1, y0 + 4)], fill=_shade(base, 0.6))
    d.line([(x0, y0 + 26), (x1 - 1, y0 + 26)], fill=_shade(base, 0.6))


def paint_radiator(img, tile, pal: Palette) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["steel"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=_shade(base, 0.7))
    for i in range(0, TILE, 3):
        d.rectangle([x0 + i, y0 + 1, x0 + i + 1, y1 - 2], fill=_shade(base, 1.24))
        d.line([(x0 + i + 2, y0 + 1), (x0 + i + 2, y1 - 2)], fill=_shade(base, 0.5))
    d.rectangle([x0, y0, x1 - 1, y1 - 1], outline=_shade(base, 0.45))


def paint_coil(img, tile, pal: Palette) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["coil"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    for i in range(0, TILE, 3):
        d.rectangle([x0, y0 + i, x1 - 1, y0 + i + 1], fill=_shade(base, 1.25))
        d.line([(x0, y0 + i + 2), (x1 - 1, y0 + i + 2)], fill=_shade(base, 0.62))
    d.rectangle([x0 + 2, y0 + 12, x1 - 3, y0 + 19], fill=pal["insulation"])
    _noise(d, x0, y0, TILE, _shade(base, 0.85), 60, 31)


def paint_concrete(img, tile, pal: Palette, reinforced: bool) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["concrete_reinforced"] if reinforced else pal["concrete"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    _noise(d, x0, y0, TILE, _shade(base, 1.09), 190, 37)
    _noise(d, x0, y0, TILE, _shade(base, 0.88), 190, 41)
    d.line([(x0, y0), (x1 - 1, y0)], fill=_shade(base, 1.14))
    d.line([(x0, y1 - 1), (x1 - 1, y1 - 1)], fill=_shade(base, 0.74))
    d.line([(x0, y0), (x0, y1 - 1)], fill=_shade(base, 1.1))
    d.line([(x1 - 1, y0), (x1 - 1, y1 - 1)], fill=_shade(base, 0.78))
    if reinforced:
        steel = pal["steel"]
        d.rectangle([x0 + 2, y0 + 2, x1 - 3, y0 + 5], fill=steel)
        d.rectangle([x0 + 2, y1 - 6, x1 - 3, y1 - 3], fill=steel)
        for bx in (6, 15, 24):
            d.point((x0 + bx, y0 + 3), fill=_shade(steel, 1.4))
            d.point((x0 + bx, y1 - 5), fill=_shade(steel, 1.4))
    else:
        for tx, ty in ((8, 8), (23, 20)):  # form tie holes
            d.ellipse([x0 + tx - 2, y0 + ty - 2, x0 + tx + 2, y0 + ty + 2], fill=_shade(base, 0.72))
            d.ellipse([x0 + tx - 1, y0 + ty - 1, x0 + tx + 1, y0 + ty + 1], fill=_shade(base, 0.55))


def paint_scaffold(img, tile, pal: Palette) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["scaffold"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    _noise(d, x0, y0, TILE, _shade(base, 1.12), 80, 43)
    d.line([(x0, y0 + 6), (x1 - 1, y1 - 7)], fill=_shade(base, 0.7), width=2)
    d.line([(x0, y1 - 7), (x1 - 1, y0 + 6)], fill=_shade(base, 0.7), width=2)
    d.rectangle([x0 + 1, y0 + 1, x1 - 2, y0 + 3], fill=_shade(base, 1.25))
    d.rectangle([x0 + 1, y1 - 4, x1 - 2, y1 - 2], fill=_shade(base, 1.25))


def paint_grate(img, tile, pal: Palette) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["steel"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=_shade(base, 0.55))
    for i in range(0, TILE, 4):
        d.rectangle([x0 + i, y0, x0 + i + 1, y1 - 1], fill=_shade(base, 1.1))
        d.rectangle([x0, y0 + i, x1 - 1, y0 + i + 1], fill=_shade(base, 0.95))
    for i in range(1, TILE, 4):
        for j in range(1, TILE, 4):
            d.rectangle([x0 + i, y0 + j, x0 + i + 2, y0 + j + 2], fill=(12, 12, 14, 255))


def paint_hopper(img, tile, pal: Palette) -> None:
    x0, y0, x1, y1 = tile_rect(tile)
    d = ImageDraw.Draw(img)
    base = pal["machine_dark"]
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=base)
    _noise(d, x0, y0, TILE, _shade(base, 1.12), 80, 47)
    for i in range(0, TILE, 8):  # hazard band
        d.polygon([(x0 + i, y0 + 12), (x0 + i + 4, y0 + 12), (x0 + i + 8, y0 + 20),
                   (x0 + i + 4, y0 + 20)], fill=pal["hazard"])
    d.rectangle([x0, y0 + 11, x1 - 1, y0 + 12], fill=_shade(base, 0.6))
    d.rectangle([x0, y0 + 20, x1 - 1, y0 + 21], fill=_shade(base, 0.6))


PALETTE_DEFAULT: Palette = dict(
    steel=(126, 132, 138),
    machine=(96, 102, 110),
    machine_dark=(64, 68, 74),
    refractory=(156, 108, 78),
    refractory_soot=(92, 66, 54),
    pipe=(140, 146, 152),
    coil=(176, 108, 62),
    insulation=(58, 58, 64),
    concrete=(150, 150, 148),
    concrete_reinforced=(126, 128, 130),
    scaffold=(104, 108, 112),
    accent=(70, 110, 150),
    hazard=(214, 178, 60),
)


def paint_atlas(pal: Palette, active: bool = False) -> Image.Image:
    img = Image.new("RGBA", (TILE * TILES, TILE * TILES), (0, 0, 0, 255))
    paint_steel_panel(img, T_STEEL_PANEL, pal)
    paint_machinery(img, T_MACHINERY_LIGHT, pal, heavy=False)
    paint_machinery(img, T_MACHINERY_HEAVY, pal, heavy=True)
    paint_control(img, T_CONTROL, pal, active=active)
    paint_refractory(img, T_REFRACTORY, pal, sooty=False)
    paint_refractory(img, T_REFRACTORY_SOOTY, pal, sooty=True)
    paint_pipe(img, T_PIPE, pal)
    paint_radiator(img, T_RADIATOR, pal)
    paint_concrete(img, T_CONCRETE, pal, reinforced=False)
    paint_concrete(img, T_CONCRETE_STEEL, pal, reinforced=True)
    paint_coil(img, T_COIL, pal)
    paint_sheetmetal(img, T_SHEETMETAL, pal)
    paint_scaffold(img, T_SCAFFOLD, pal)
    paint_grate(img, T_GRATE, pal)
    paint_hopper(img, T_HOPPER, pal)
    paint_steel_plate(img, T_STEEL_PLATE, pal)
    if active:
        # Firing: the refractory tiles glow, everything else picks up a warm bounce light.
        glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        for tile in (T_REFRACTORY, T_REFRACTORY_SOOTY):
            x0, y0, x1, y1 = tile_rect(tile)
            gd.rectangle([x0, y0, x1 - 1, y1 - 1], fill=(255, 122, 26, 150))
            for i in range(0, TILE, 3):
                gd.line([(x0 + i, y1 - 1), (x0 + i + 2, y0)], fill=(255, 190, 80, 90))
        img = Image.alpha_composite(img, glow)
    return img


def with_overrides(pal: Palette, **changes) -> Palette:
    out = dict(pal)
    out.update(changes)
    return out


MACHINE_PALETTES = {
    "coal_boiler": with_overrides(
        PALETTE_DEFAULT,
        steel=(112, 108, 104),
        machine=(84, 80, 78),
        machine_dark=(54, 50, 50),
        refractory=(168, 112, 74),
        refractory_soot=(78, 56, 46),
        concrete=(132, 128, 124),
        concrete_reinforced=(112, 110, 108),
        accent=(196, 92, 44),
    ),
    "electrostatic_precipitator": with_overrides(
        PALETTE_DEFAULT,
        steel=(122, 130, 142),
        machine=(88, 96, 110),
        machine_dark=(56, 62, 74),
        coil=(188, 118, 62),
        insulation=(48, 52, 66),
        concrete=(158, 158, 156),
        accent=(96, 140, 200),
    ),
    "steam_condenser": with_overrides(
        PALETTE_DEFAULT,
        steel=(158, 166, 172),
        machine=(112, 120, 128),
        machine_dark=(74, 80, 88),
        pipe=(168, 176, 182),
        coil=(196, 152, 84),
        accent=(70, 150, 170),
    ),
    "cooling_tower": with_overrides(
        PALETTE_DEFAULT,
        steel=(140, 144, 146),
        machine=(108, 112, 114),
        machine_dark=(72, 76, 78),
        concrete=(176, 176, 172),
        concrete_reinforced=(150, 152, 150),
        scaffold=(120, 124, 126),
        accent=(88, 148, 120),
    ),
    "district_heater": with_overrides(
        PALETTE_DEFAULT,
        steel=(146, 150, 156),
        machine=(92, 108, 122),
        machine_dark=(58, 70, 82),
        pipe=(154, 158, 164),
        coil=(182, 112, 66),
        accent=(198, 108, 62),
        hazard=(220, 182, 64),
    ),
}

# `genericmultiblock()` datagen reads the texture straight out of the mtl, so those machines use an
# absolute map_Kd. `generateActiveMultiblock()` injects the texture from Java (#<name>) and can
# therefore swap in the _active variant.
ABSOLUTE_TEXTURE = {
    "coal_boiler": False,
    "electrostatic_precipitator": True,
    "steam_condenser": True,
    "cooling_tower": True,
    "district_heater": True,
}
ACTIVE_VARIANTS = {"coal_boiler"}


def main() -> None:
    for struct in ALL_STRUCTURES:
        builder = build_obj(struct)
        builder.dedupe()
        folder = os.path.join(OBJ_DIR, struct.name)
        obj_path = os.path.join(folder, struct.name + ".obj")
        builder.write(obj_path, struct.name)
        write_mtl(struct, ABSOLUTE_TEXTURE[struct.name])
        verts, faces = builder.stats
        print(f"{struct.name:28s} obj {os.path.getsize(obj_path):7d} bytes  {verts:5d} verts {faces:5d} faces")

        tex_folder = os.path.join(TEX_DIR, struct.name)
        os.makedirs(tex_folder, exist_ok=True)
        pal = MACHINE_PALETTES[struct.name]
        default = paint_atlas(pal, active=False)
        default.save(os.path.join(tex_folder, "default.png"))
        if struct.name in ACTIVE_VARIANTS:
            paint_atlas(pal, active=True).save(os.path.join(tex_folder, "default_active.png"))
            print(f"{'':28s} texture default.png + default_active.png")
        else:
            print(f"{'':28s} texture default.png")


if __name__ == "__main__":
    main()
