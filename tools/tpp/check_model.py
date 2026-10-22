#!/usr/bin/env python3
"""
Self-check for a coal CHP multiblock model before it goes into the repository.

The Immersive Geology multiblock pipeline is picky about a handful of things that
are invisible until the game crashes or the machine renders as a black cube: the
OBJ bounding box has to match the structure template, the material names have to
match what the datagen expects, UVs have to stay inside 0..1 and every texture the
MTL mentions has to exist. This script checks all of that offline.

Usage (from the repository root):

    python3 tools/tpp/check_model.py coal_boiler
    python3 tools/tpp/check_model.py --all

It never writes anything; it only reports errors (exit code 1) and warnings.
See docs/ART_BRIEF_COAL_CHP.md / .ru.md for what each rule means.
"""

from __future__ import annotations

import os
import re
import struct
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OBJ_DIR = os.path.join(
    ROOT, "src", "main", "resources", "assets", "immersivegeology",
    "models", "block", "multiblock", "obj",
)
TEX_DIR = os.path.join(
    ROOT, "src", "main", "resources", "assets", "immersivegeology",
    "textures", "block", "multiblock",
)
GEN_DIR = os.path.join(ROOT, "src", "generated", "resources", "assets", "immersivegeology")

# name -> (size, master, mtl_kind, extra_textures)
#   mtl_kind "injected": the MTL uses map_Kd #<name> and the datagen injects
#                        block/multiblock/<name>/default(_active) at build time.
#   mtl_kind "absolute": the MTL points straight at immersivegeology:block/...
MACHINES = {
    "coal_boiler":                ((5, 7, 5), (2, 1, 2), "injected", ["default", "default_active"]),
    "electrostatic_precipitator": ((5, 9, 4), (2, 0, 2), "absolute", ["default"]),
    "steam_condenser":            ((4, 3, 4), (1, 0, 1), "absolute", ["default"]),
    "cooling_tower":              ((7, 9, 7), (3, 0, 3), "absolute", ["default"]),
    "district_heater":            ((3, 4, 3), (1, 1, 1), "absolute", ["default"]),
}

MAX_TRIS = 6000  # warning threshold, IE multiblocks live in the hundreds..low thousands


def png_size(path: str):
    with open(path, "rb") as fh:
        head = fh.read(26)
    if head[:8] != b"\x89PNG\r\n\x1a\n":
        return None
    return struct.unpack(">II", head[16:24])


def parse_obj(path: str):
    verts, uvs, faces, mats, mtllib = [], [], [], set(), None
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            parts = line.split()
            if not parts:
                continue
            tag = parts[0]
            if tag == "v":
                verts.append(tuple(float(x) for x in parts[1:4]))
            elif tag == "vt":
                uvs.append(tuple(float(x) for x in parts[1:3]))
            elif tag == "f":
                idx = []
                for tok in parts[1:]:
                    a = tok.split("/")
                    idx.append((int(a[0]), int(a[1]) if len(a) > 1 and a[1] else None))
                faces.append(idx)
            elif tag == "usemtl":
                mats.add(parts[1])
            elif tag == "mtllib":
                mtllib = parts[1]
    return verts, uvs, faces, mats, mtllib


def parse_mtl(path: str):
    mats: dict[str, str] = {}
    name = None
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            parts = line.split()
            if not parts:
                continue
            if parts[0] == "newmtl":
                name = parts[1]
            elif parts[0] == "map_Kd" and name:
                mats[name] = parts[1]
    return mats


def check(name: str) -> int:
    size, master, kind, textures = MACHINES[name]
    errors, warnings = [], []
    print(f"== {name} ==")

    obj_dir = os.path.join(OBJ_DIR, name)
    obj_path = os.path.join(obj_dir, name + ".obj")
    mtl_path = os.path.join(obj_dir, name + ".mtl")
    for p in (obj_path, mtl_path):
        if not os.path.isfile(p):
            errors.append(f"missing file {os.path.relpath(p, ROOT)}")
    if errors:
        report(errors, warnings)
        return 1

    verts, uvs, faces, used_mats, mtllib = parse_obj(obj_path)
    mtl = parse_mtl(mtl_path)

    # 1. mtllib points at the sibling .mtl
    if mtllib != name + ".mtl":
        errors.append(f"OBJ mtllib is {mtllib!r}, expected {name + '.mtl'!r}")

    # 2. bounding box == structure space (block coords minus the master block)
    lo = [-m for m in master]
    hi = [s - m for s, m in zip(size, master)]
    got_lo = [min(v[i] for v in verts) for i in range(3)]
    got_hi = [max(v[i] for v in verts) for i in range(3)]
    if any(abs(a - b) > 1e-4 for a, b in zip(got_lo, lo)) or any(abs(a - b) > 1e-4 for a, b in zip(got_hi, hi)):
        errors.append(
            f"bounding box {got_lo}..{got_hi} does not match structure space "
            f"{lo}..{hi} (model space = structure coords - master {master})"
        )

    # 3. UVs inside 0..1
    bad_uv = [u for u in uvs if not (0.0 - 1e-6 <= u[0] <= 1.0 + 1e-6 and 0.0 - 1e-6 <= u[1] <= 1.0 + 1e-6)]
    if bad_uv:
        errors.append(f"{len(bad_uv)} vt entries outside 0..1 (e.g. {bad_uv[0]})")

    # 4. faces reference existing indices and carry UVs
    for fi, face in enumerate(faces):
        for vi, ti in face:
            if not 1 <= abs(vi) <= len(verts):
                errors.append(f"face {fi} references missing vertex {vi}")
                break
            if ti is None:
                errors.append(f"face {fi} has no UVs (every face needs vt)")
                break
            if not 1 <= abs(ti) <= len(uvs):
                errors.append(f"face {fi} references missing vt {ti}")
                break
        else:
            continue
        break

    # 5. material contract
    if kind == "injected":
        if used_mats != {name}:
            errors.append(f"usemtl set {sorted(used_mats)} must be exactly [{name}] (datagen injects #<{name}>)")
        for mat, path in mtl.items():
            if path != "#" + name:
                errors.append(f"MTL {mat}: map_Kd {path!r} must be '#{name}' for injected machines")
    else:
        for mat, path in mtl.items():
            if not path.startswith("immersivegeology:block/multiblock/" + name + "/"):
                errors.append(
                    f"MTL {mat}: map_Kd {path!r} must be an absolute asset path "
                    f"'immersivegeology:block/multiblock/{name}/<texture>' (no .png, no #)"
                )
                continue
            rel = path.split("immersivegeology:", 1)[1]
            tex = os.path.join(TEX_DIR, rel.split("block/multiblock/", 1)[1] + ".png")
            if not os.path.isfile(tex):
                errors.append(f"MTL {mat}: texture not on disk: {os.path.relpath(tex, ROOT)}")
            if os.path.isabs(path) or ":" in path.split(":", 1)[0] and not path.startswith("immersivegeology:"):
                errors.append(f"MTL {mat}: map_Kd looks like a filesystem path; use the asset path")
    for mat in used_mats:
        if mat not in mtl:
            errors.append(f"OBJ uses material {mat!r} that the MTL does not define")

    # 6. textures on disk
    for tex in textures:
        p = os.path.join(TEX_DIR, name, tex + ".png")
        if not os.path.isfile(p):
            errors.append(f"missing texture {os.path.relpath(p, ROOT)}")
        else:
            dims = png_size(p)
            if dims is None:
                errors.append(f"{os.path.relpath(p, ROOT)} is not a PNG")
            else:
                print(f"   texture {tex}.png {dims[0]}x{dims[1]}")

    # 7. generated split model references resolve
    for cand in (
        os.path.join(GEN_DIR, "models", "block", name + "_split.json"),
        os.path.join(GEN_DIR, "models", "block", "multiblock", "obj", name, name + "_split.json"),
    ):
        if os.path.isfile(cand):
            print(f"   split model: {os.path.relpath(cand, ROOT)}")
            break
    else:
        warnings.append("no generated *_split.json found (run datagen / datagen_mirror.py)")

    # 8. budget
    tris = sum(len(f) - 2 for f in faces)
    print(f"   geometry: {len(verts)} v / {len(uvs)} vt / {len(faces)} faces / ~{tris} tris")
    if tris > MAX_TRIS:
        warnings.append(f"~{tris} tris is heavy for a multiblock rendered per chunk section")
    if len(used_mats) == 1 and tris < 20:
        warnings.append("suspiciously little geometry - did the export lose parts?")

    return report(errors, warnings)


def report(errors, warnings) -> int:
    for w in warnings:
        print(f"   WARN: {w}")
    for e in errors:
        print(f"   ERROR: {e}")
    if not errors:
        print("   OK")
    return 1 if errors else 0


def main(argv):
    names = list(MACHINES) if argv == ["--all"] else argv
    if not names:
        print(__doc__)
        return 2
    bad = 0
    for n in names:
        if n not in MACHINES:
            print(f"unknown machine {n!r}; known: {', '.join(MACHINES)}")
            return 2
        bad |= check(n)
    return bad


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
