#!/usr/bin/env python3
"""
Renders the UV layout of a multiblock OBJ into a PNG "colouring book".

The output is the map of where every face of the model lands on the texture, so a
texture artist can paint straight onto it. The V axis is drawn the way the game
sees it: this repository's generated models all use ``flip_v: true``, which makes
OBJ ``vt v=0`` land on the BOTTOM row of the PNG - exactly what Blender's UV editor
shows over the image. So the rendered template can be opened as a layer on top of
``default.png`` and painted through.

Usage (from the repository root):

    python3 tools/tpp/uv_template.py coal_boiler                # docs/art/uv/coal_boiler_uv.png
    python3 tools/tpp/uv_template.py coal_boiler --size 1024
    python3 tools/tpp/uv_template.py --all

Re-run it after re-exporting a model: the template always follows the OBJ.
"""

from __future__ import annotations

import argparse
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OBJ_DIR = os.path.join(
    ROOT, "src", "main", "resources", "assets", "immersivegeology",
    "models", "block", "multiblock", "obj",
)
TEX_DIR = os.path.join(
    ROOT, "src", "main", "resources", "assets", "immersivegeology",
    "textures", "block", "multiblock",
)
OUT_DIR = os.path.join(ROOT, "docs", "art", "uv")

MACHINES = [
    "coal_boiler",
    "electrostatic_precipitator",
    "steam_condenser",
    "cooling_tower",
    "district_heater",
]


def read_faces(obj_path):
    uvs, faces = [], []
    with open(obj_path, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            parts = line.split()
            if not parts:
                continue
            if parts[0] == "vt":
                uvs.append((float(parts[1]), float(parts[2])))
            elif parts[0] == "f":
                poly = []
                for tok in parts[1:]:
                    bits = tok.split("/")
                    if len(bits) > 1 and bits[1]:
                        poly.append(int(bits[1]))
                if len(poly) >= 3:
                    faces.append(poly)
    return uvs, faces


def render(name: str, size: int, overlay: bool) -> str:
    obj = os.path.join(OBJ_DIR, name, name + ".obj")
    uvs, faces = read_faces(obj)
    if overlay:
        tex = os.path.join(TEX_DIR, name, "default.png")
        base = Image.open(tex).convert("RGBA").resize((size, size), Image.NEAREST)
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        img.paste(base, (0, 0))
    else:
        img = Image.new("RGBA", (size, size), (24, 24, 28, 255))
    draw = ImageDraw.Draw(img)

    # coarse 1/8 grid + border so islands can be located by eye
    for i in range(1, 8):
        p = round(size * i / 8)
        draw.line([(p, 0), (p, size)], fill=(255, 255, 255, 40), width=1)
        draw.line([(0, p), (size, p)], fill=(255, 255, 255, 40), width=1)
    draw.rectangle([0, 0, size - 1, size - 1], outline=(255, 200, 0, 255), width=2)

    def px(u, v):
        # flip_v: true in the generated models -> vt v=0 is the BOTTOM row of the PNG
        return (u * (size - 1), (1.0 - v) * (size - 1))

    for poly in faces:
        pts = []
        for idx in poly:
            i = idx - 1 if idx > 0 else len(uvs) + idx
            if 0 <= i < len(uvs):
                pts.append(px(*uvs[i]))
        if len(pts) >= 3:
            draw.line(pts + [pts[0]], fill=(0, 255, 128, 255), width=1)

    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, f"{name}_uv.png")
    img.save(out)
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("machines", nargs="*", help="machine names, or --all")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--size", type=int, default=512)
    ap.add_argument("--overlay", action="store_true", help="draw over the current default.png")
    args = ap.parse_args(argv)

    names = MACHINES if args.all else args.machines
    if not names:
        ap.print_help()
        return 2
    for n in names:
        if n not in MACHINES:
            print(f"unknown machine {n!r}; known: {', '.join(MACHINES)}")
            return 2
        print(render(n, args.size, args.overlay))
    return 0


if __name__ == "__main__":
    sys.exit(main())
