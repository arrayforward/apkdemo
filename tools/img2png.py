"""
img2png.py — Convert goldieos RGB565 .h asset files to PNG.

Usage:
    python img2png.py <input.h> <output.png> <width> <height>
    python img2png.py --all <src_dir> <dst_dir>

Input format: C array of `const unsigned char[]` with byte values 0x00..0xFF,
RGB565 little-endian (low byte first).
"""
import argparse
import os
import re
import struct
import sys

try:
    from PIL import Image
except ImportError:
    print("ERROR: pip install pillow", file=sys.stderr)
    sys.exit(1)


HEX_RE = re.compile(r"0[xX][0-9A-Fa-f]{2}")


def parse_bytes(text: str) -> bytes:
    """Extract all 0xNN bytes from the C source and return as bytes."""
    return bytes(int(m, 16) for m in HEX_RE.findall(text))


def rgb565_to_rgb888(lo: int, hi: int) -> tuple:
    """Convert a 16-bit RGB565 pixel (little-endian byte pair) to RGB888 tuple."""
    v = lo | (hi << 8)
    r5 = (v >> 11) & 0x1F
    g6 = (v >> 5) & 0x3F
    b5 = v & 0x1F
    r = (r5 << 3) | (r5 >> 2)
    g = (g6 << 2) | (g6 >> 4)
    b = (b5 << 3) | (b5 >> 2)
    return (r, g, b)


def convert_one(h_path: str, png_path: str, w: int, h: int) -> None:
    with open(h_path, "r", encoding="utf-8", errors="ignore") as f:
        text = f.read()
    raw = parse_bytes(text)
    if len(raw) < w * h * 2:
        # not all sprite sizes match the WxH; some icons (e.g. 64x41) are inferred from name
        # but 152x136, 88x85, 40x40 etc. are nominal
        pass
    img = Image.new("RGB", (w, h), (0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            i = (y * w + x) * 2
            if i + 1 >= len(raw):
                break
            px[x, y] = rgb565_to_rgb888(raw[i], raw[i + 1])
    os.makedirs(os.path.dirname(png_path) or ".", exist_ok=True)
    img.save(png_path)
    print(f"  {os.path.basename(h_path)} -> {os.path.basename(png_path)} ({w}x{h})")


SIZE_RE = re.compile(r"(\d+)[x_](\d+)")


def infer_size(name: str) -> tuple:
    """Infer WxH from filename like rgb16_avatar_female_152_136 -> (152,136)."""
    m = SIZE_RE.findall(name)
    if m:
        # use the last pair
        w, h = int(m[-1][0]), int(m[-1][1])
        return (w, h)
    return (0, 0)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("input", help="Input .h path or --all src")
    ap.add_argument("output", help="Output .png path or --all dst dir")
    ap.add_argument("width", type=int, nargs="?", default=0)
    ap.add_argument("height", type=int, nargs="?", default=0)
    ap.add_argument("--all", action="store_true")
    args = ap.parse_args()

    if args.all:
        if not os.path.isdir(args.input):
            print(f"not a dir: {args.input}", file=sys.stderr)
            return 1
        os.makedirs(args.output, exist_ok=True)
        for fn in sorted(os.listdir(args.input)):
            if not fn.endswith(".h"):
                continue
            h_path = os.path.join(args.input, fn)
            png_name = os.path.splitext(fn)[0] + ".png"
            w, h = infer_size(fn)
            if w == 0 or h == 0:
                print(f"  skip (no size): {fn}")
                continue
            try:
                convert_one(h_path, os.path.join(args.output, png_name), w, h)
            except Exception as e:
                print(f"  FAIL {fn}: {e}")
        return 0

    if not args.width or not args.height:
        w, h = infer_size(os.path.basename(args.input))
        if w and h:
            args.width, args.height = w, h
        else:
            print("width/height required", file=sys.stderr)
            return 2
    convert_one(args.input, args.output, args.width, args.height)
    return 0


if __name__ == "__main__":
    sys.exit(main())
