#!/usr/bin/env python3
"""
Prepares a Tripo/Blender .glb for the phone:
  * downsizes textures (8K/4K -> 2048/1024) and re-encodes them, so the model fits in mobile GPU memory
  * optionally rotates the model so it faces +Z (glTF "front"), applied on a new root node
  * drops editor-only extensions (FB_ngon_encoding)
  * repacks the binary buffer

Usage:
  python3 tools/prepare_glb.py input.glb output.glb [--face +X|-X|+Z|-Z] [--base 2048] [--maps 1024]

Requires Pillow (pip install pillow).
"""
import argparse
import io
import json
import math
import struct

from PIL import Image

FACE_TO_YAW = {"+Z": 0.0, "-Z": 180.0, "+X": -90.0, "-X": 90.0}


def read_glb(path):
    data = open(path, "rb").read()
    magic, version, _ = struct.unpack_from("<III", data, 0)
    if magic != 0x46546C67 or version != 2:
        raise SystemExit("Not a glTF 2.0 binary file")
    jlen, jtype = struct.unpack_from("<II", data, 12)
    gltf = json.loads(data[20:20 + jlen])
    off = 20 + jlen
    blen, btype = struct.unpack_from("<II", data, off)
    return gltf, data[off + 8: off + 8 + blen]


def write_glb(path, gltf, views):
    """views: list of bytes, one per bufferView, in order."""
    blob = bytearray()
    for i, chunk in enumerate(views):
        while len(blob) % 4:
            blob.append(0)
        gltf["bufferViews"][i]["byteOffset"] = len(blob)
        gltf["bufferViews"][i]["byteLength"] = len(chunk)
        blob.extend(chunk)
    while len(blob) % 4:
        blob.append(0)
    gltf["buffers"] = [{"byteLength": len(blob)}]
    js = json.dumps(gltf, separators=(",", ":")).encode()
    js += b" " * ((4 - len(js) % 4) % 4)
    total = 12 + 8 + len(js) + 8 + len(blob)
    with open(path, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, total))
        f.write(struct.pack("<II", len(js), 0x4E4F534A))
        f.write(js)
        f.write(struct.pack("<II", len(blob), 0x004E4942))
        f.write(blob)


def encode_image(raw, max_size, is_base_color):
    img = Image.open(io.BytesIO(raw))
    has_alpha = img.mode in ("RGBA", "LA") and img.getextrema()[-1][0] < 255
    img = img.convert("RGBA" if has_alpha else "RGB")
    if max(img.size) > max_size:
        img = img.resize((max_size, max_size), Image.LANCZOS)
    out = io.BytesIO()
    if has_alpha:
        img.save(out, "PNG", optimize=True)
        return out.getvalue(), "image/png"
    img.save(out, "JPEG", quality=90 if is_base_color else 92, optimize=True, progressive=False)
    return out.getvalue(), "image/jpeg"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("dst")
    ap.add_argument("--face", default="+Z", choices=FACE_TO_YAW.keys(), help="direction the model currently faces")
    ap.add_argument("--base", type=int, default=2048, help="max base-colour texture size")
    ap.add_argument("--maps", type=int, default=1024, help="max normal/roughness texture size")
    args = ap.parse_args()

    gltf, binary = read_glb(args.src)
    views = []
    for v in gltf["bufferViews"]:
        start = v.get("byteOffset", 0)
        views.append(binary[start:start + v["byteLength"]])

    base_sources = set()
    for m in gltf.get("materials", []):
        t = m.get("pbrMetallicRoughness", {}).get("baseColorTexture")
        if t is not None:
            base_sources.add(gltf["textures"][t["index"]]["source"])

    for i, im in enumerate(gltf.get("images", [])):
        bv = im["bufferView"]
        before = len(views[bv])
        is_base = i in base_sources
        views[bv], im["mimeType"] = encode_image(views[bv], args.base if is_base else args.maps, is_base)
        print(f"image {i}: {before // 1024} KB -> {len(views[bv]) // 1024} KB ({'base colour' if is_base else 'map'})")

    for key in ("extensionsUsed", "extensionsRequired"):
        if key in gltf:
            gltf[key] = [e for e in gltf[key] if e != "FB_ngon_encoding"]
            if not gltf[key]:
                del gltf[key]

    yaw = FACE_TO_YAW[args.face]
    if yaw:
        half = math.radians(yaw) / 2
        scene = gltf["scenes"][gltf.get("scene", 0)]
        root = {"name": "mochi_facing_fix", "rotation": [0.0, math.sin(half), 0.0, math.cos(half)], "children": scene["nodes"]}
        gltf["nodes"].append(root)
        scene["nodes"] = [len(gltf["nodes"]) - 1]
        print(f"rotated {yaw:+.0f} deg around Y so the face looks at +Z")

    write_glb(args.dst, gltf, views)
    print(f"wrote {args.dst}")


if __name__ == "__main__":
    main()
