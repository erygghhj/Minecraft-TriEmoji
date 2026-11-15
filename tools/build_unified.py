#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Universal Emoji builder for Minecraft Java (1.10 .. 1.21.x).

Pipeline
--------
1. Fetch the full Twemoji 15.1 asset set (72x72 PNG) from the official repo.
2. Normalise every emoji to CELL x CELL pixels, 16 per row, into one tall sheet.
3. Emit a resource pack that APPENDS one bitmap provider to the vanilla
   default.json instead of replacing it (replacing it would wipe the vanilla
   ascii / unifont providers and turn every letter into a missing-glyph box).
4. Pack the result into Universal_Emoji.zip (+ legacy variant) and print SHA-1.
5. Emit ios.txt / android.txt / windows.txt as "row.column=codepoint-sequence".

Notes that matter
-----------------
* Minecraft splits a bitmap provider texture using the SHAPE of the "chars"
  array: N strings == N horizontal strips, len(string) == columns per strip.
  Every string must therefore have the SAME length, or the atlas gets warped.
  Trailing cells are padded with transparent placeholders.
* "height" in the provider is the ON-SCREEN height, not the pixel size of the
  cell. A 32px cell rendered with height=10 keeps emoji the same scale as the
  chat font while the sheet stays high resolution.
* Variation selector U+FE0F is stripped from every key, ZWJ (U+200D) is kept.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import shutil
import sys
import tarfile
import time
import urllib.request
import zipfile
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

from PIL import Image

# --------------------------------------------------------------------------- #
# constants
# --------------------------------------------------------------------------- #

TWEMOJI_TAG = "v15.1.0"
TWEMOJI_TARBALL = (
    f"https://github.com/jdecked/twemoji/archive/refs/tags/{TWEMOJI_TAG}.tar.gz"
)
EMOJI_DATA_URL = (
    "https://raw.githubusercontent.com/iamcal/emoji-data/master/emoji.json"
)
# vanilla default.json snapshots, used as append targets
VANILLA_TEMPLATES = {
    # 1.20+ uses reference/include providers
    "modern": "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21.4/assets/minecraft/font/default.json",
    # 1.13..1.19.4 (and 1.10+ still understands bitmap/legacy_unicode) use the
    # fully inlined provider list, snapshot from 1.16.5
    "legacy": "https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.16.5/assets/minecraft/font/default.json",
}

PUA_START = 0xE000
PUA_END = 0xF8FF
VS16 = 0xFE0F
ZWJ = 0x200D
SKINTONES = (0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF)

DEFAULT_CELL = 32
DEFAULT_COLS = 16
DEFAULT_ASCENT = 10
DEFAULT_HEIGHT = 10
PLATFORMS = ("ios", "android", "windows")


# --------------------------------------------------------------------------- #
# small helpers
# --------------------------------------------------------------------------- #

def log(msg: str) -> None:
    print(f"[build] {msg}", flush=True)


def http_get(url: str, dest: Optional[Path] = None, timeout: int = 180) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "universal-emoji-builder"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        data = resp.read()
    if dest is not None:
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
    return data


def hexseq(codepoints: Sequence[int]) -> str:
    """[0x1F468, 0x200D, 0x1F469] -> '1f468-200d-1f469'"""
    return "-".join(f"{cp:04x}" for cp in codepoints)


def parse_seq(seq: str) -> List[int]:
    return [int(part, 16) for part in seq.split("-") if part]


def strip_vs16(codepoints: Iterable[int]) -> List[int]:
    """Drop U+FE0F only. ZWJ, skin tones and everything else survive."""
    return [cp for cp in codepoints if cp != VS16]


def emoji_version(value: str) -> float:
    """'E14.0' / 'E1.0' -> 14.0 / 1.0"""
    raw = str(value or "E1.0").lstrip("E")
    try:
        return float(raw)
    except ValueError:
        return 1.0


# --------------------------------------------------------------------------- #
# step 1 - assets
# --------------------------------------------------------------------------- #

def ensure_twemoji(cache: Path, explicit_assets: Optional[Path]) -> Path:
    """Return a directory holding <codepoints>.png files."""
    if explicit_assets:
        if not explicit_assets.is_dir():
            sys.exit(f"assets dir not found: {explicit_assets}")
        return explicit_assets

    target = cache / f"twemoji-{TWEMOJI_TAG}"
    marker = target / ".ok"
    if marker.exists():
        log(f"reusing cached twemoji at {target}")
        return target

    log(f"downloading twemoji {TWEMOJI_TAG} ...")
    blob = http_get(TWEMOJI_TARBALL, cache / "twemoji.tar.gz")
    target.mkdir(parents=True, exist_ok=True)
    with tarfile.open(fileobj=io.BytesIO(blob), mode="r:gz") as tar:
        for member in tar.getmembers():
            parts = Path(member.name).parts
            if len(parts) == 4 and parts[1] == "assets" and parts[2] == "72x72":
                fh = tar.extractfile(member)
                if fh is None:
                    continue
                (target / parts[3]).write_bytes(fh.read())
    marker.write_text("ok")
    log(f"twemoji extracted: {len(list(target.glob('*.png')))} files")
    return target


def ensure_emoji_data(cache: Path) -> List[dict]:
    path = cache / "emoji-data.json"
    if not path.exists():
        log("downloading iamcal/emoji-data ...")
        http_get(EMOJI_DATA_URL, path)
    return json.loads(path.read_text(encoding="utf-8"))


# --------------------------------------------------------------------------- #
# step 2 - collect + de-duplicate
# --------------------------------------------------------------------------- #

def collect_entries(assets: Path) -> Tuple[List[Tuple[str, Path]], int]:
    """Build the ordered emoji table.

    Key  = codepoint sequence with U+FE0F stripped (used for chat matching).
    File = the PNG that renders best for that key.

    Duplicates appear because Twemoji ships both "2764.png" and "2764-fe0f.png".
    We keep the fully-qualified file (the one that contained FE0F) because it is
    the complete drawing, then sort by numeric codepoint order.
    """
    winners: Dict[str, Path] = {}
    qualified: Dict[str, bool] = {}

    for png in sorted(assets.glob("*.png")):
        stem = png.stem
        try:
            cps = parse_seq(stem)
        except ValueError:
            log(f"skip unparsable name: {png.name}")
            continue
        key = hexseq(strip_vs16(cps))
        has_vs16 = VS16 in cps
        if key not in winners or (has_vs16 and not qualified.get(key)):
            winners[key] = png
            qualified[key] = has_vs16

    entries = sorted(winners.items(), key=lambda kv: tuple(parse_seq(kv[0])))
    dups = len(winners)
    return [(k, v) for k, v in entries], dups


# --------------------------------------------------------------------------- #
# step 3 - sheet
# --------------------------------------------------------------------------- #

def build_sheet(entries: Sequence[Tuple[str, Path]], cell: int, cols: int) -> Tuple[Image.Image, int, int]:
    rows = (len(entries) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (0, 0, 0, 0))

    for index, (_key, png) in enumerate(entries):
        row, col = divmod(index, cols)
        with Image.open(png) as src:
            glyph = src.convert("RGBA")
            if glyph.size != (cell, cell):
                glyph = fit_square(glyph, cell)
            sheet.paste(glyph, (col * cell, row * cell))
        if index % 500 == 0:
            log(f"rasterising {index}/{len(entries)} ...")

    return sheet, rows, cols


def fit_square(img: Image.Image, size: int) -> Image.Image:
    """Letterbox into a transparent square, preserving aspect ratio."""
    w, h = img.size
    scale = min(size / w, size / h)
    new = img.resize((max(1, round(w * scale)), max(1, round(h * scale))), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(new, ((size - new.width) // 2, (size - new.height) // 2))
    return out


# --------------------------------------------------------------------------- #
# step 4 - provider json / pack meta
# --------------------------------------------------------------------------- #

def pua_chars(rows: int, cols: int, count: int) -> Tuple[List[str], int]:
    """Build the chars array: one string per row, identical length.

    Cells past `count` are transparent padding but still need a codepoint, so
    the PUA sequence simply keeps counting - the plugin never maps them because
    they are absent from the three txt tables.
    """
    total = rows * cols
    if PUA_START + total - 1 > PUA_END:
        sys.exit(f"PUA overflow: {total} glyphs exceeds {PUA_END - PUA_START + 1}")
    out: List[str] = []
    for row in range(rows):
        line = []
        for col in range(cols):
            line.append(chr(PUA_START + row * cols + col))
        out.append("".join(line))
    return out, total


def load_vanilla_template(kind: str, cache: Path) -> dict:
    path = cache / f"vanilla-default-{kind}.json"
    if not path.exists():
        try:
            http_get(VANILLA_TEMPLATES[kind], path)
        except Exception as exc:  # offline fallback
            log(f"template download failed ({exc}), using embedded fallback")
            path.write_text(json.dumps(EMBEDDED_TEMPLATES[kind], indent=4), encoding="utf-8")
    return json.loads(path.read_text(encoding="utf-8"))


EMBEDDED_TEMPLATES = {
    "modern": {
        "providers": [
            {"type": "reference", "id": "minecraft:include/space"},
            {"type": "reference", "id": "minecraft:include/default"},
            {"type": "reference", "id": "minecraft:include/unifont"},
        ]
    },
    "legacy": {
        "providers": [
            {"type": "space", "advances": {" ": 4, "\u200c": 0}},
            {"type": "bitmap", "file": "minecraft:font/ascii.png", "ascent": 7, "height": 8, "chars": []},
            {"type": "legacy_unicode", "sizes": "minecraft:font/glyph_sizes.bin",
             "template": "minecraft:font/unicode_page_%s.png"},
        ]
    },
}

MODERN_PACK_META = {
    "pack": {
        "pack_format": 1,
        "description": "Universal Emoji (Twemoji 15.1) - PUA glyph sheet",
        "supported_formats": {"min_inclusive": 1, "max_inclusive": 999},
    }
}


def build_default_json(template: dict, chars: List[str], ascent: int, height: int, texture: str) -> dict:
    provider = {
        "type": "bitmap",
        "file": texture,
        "ascent": ascent,
        "height": height,
        "chars": chars,
    }
    out = json.loads(json.dumps(template))
    providers = out.setdefault("providers", [])
    # our provider goes last so vanilla glyphs keep priority
    providers.append(provider)
    return out


# --------------------------------------------------------------------------- #
# step 5 - platform tables
# --------------------------------------------------------------------------- #

def build_platform_index(emoji_data: Sequence[dict]) -> Dict[str, dict]:
    idx: Dict[str, dict] = {}
    for rec in emoji_data:
        cps = parse_seq(rec.get("unified", ""))
        key = hexseq(strip_vs16(cps))
        if key:
            idx[key] = rec
    return idx


def strip_skintones(seq: str) -> str:
    cps = [cp for cp in parse_seq(seq) if cp not in SKINTONES]
    return hexseq(strip_vs16(cps))


def resolve_support(seq: str, index: Dict[str, dict], windows_max: float) -> Dict[str, bool]:
    """Decide per-platform availability.

    Unknown sequences (Twemoji has them, emoji-data does not) are treated as
    supported everywhere - a modern client almost certainly renders them, and a
    false negative would only produce noise.
    """
    rec = index.get(seq) or index.get(strip_skintones(seq))
    if rec is None:
        return {p: True for p in PLATFORMS}
    return {
        "ios": bool(rec.get("has_img_apple", True)),
        "android": bool(rec.get("has_img_google", True)),
        # Microsoft ships Segoe UI Emoji late; approximate with emoji version.
        "windows": emoji_version(rec.get("added_in", "E1.0")) <= windows_max,
    }


def apply_overrides(support: Dict[str, bool], seq: str, overrides: dict) -> Dict[str, bool]:
    patch = overrides.get(seq)
    if not isinstance(patch, dict):
        return support
    out = dict(support)
    for platform in PLATFORMS:
        if platform in patch:
            out[platform] = bool(patch[platform])
    return out


# --------------------------------------------------------------------------- #
# step 6 - writer
# --------------------------------------------------------------------------- #

def sha1_of(path: Path) -> str:
    h = hashlib.sha1()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


def write_pack(
    out_dir: Path,
    kind: str,
    sheet: Image.Image,
    chars: List[str],
    ascent: int,
    height: int,
    pack_meta: dict,
    template: dict,
    sheet_name: str = "unified_sheet.png",
) -> Path:
    staging = out_dir / f"_staging_{kind}"
    if staging.exists():
        shutil.rmtree(staging)
    font_dir = staging / "assets" / "minecraft" / "font"
    font_dir.mkdir(parents=True)

    sheet.save(font_dir / f"universal_emoji.png", optimize=True)

    default = build_default_json(template, chars, ascent, height, "minecraft:font/universal_emoji.png")
    text = json.dumps(default, ensure_ascii=False, indent=4)
    (font_dir / "default.json").write_text(text, encoding="utf-8")

    meta = json.loads(json.dumps(pack_meta))
    meta["pack"]["description"] = f"Universal Emoji {TWEMOJI_TAG} ({kind})"
    (staging / "pack.mcmeta").write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")

    zip_path = out_dir / ("Universal_Emoji.zip" if kind == "modern" else f"Universal_Emoji_{kind}.zip")
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
        for root, _dirs, files in os.walk(staging):
            for name in files:
                full = Path(root) / name
                arc = full.relative_to(staging).as_posix()
                zf.write(full, arc)
    shutil.rmtree(staging)

    # loose sheet copy for eyeballing
    sheet.save(out_dir / sheet_name)
    return zip_path


def write_txt(out_dir: Path, entries: Sequence[Tuple[str, Path]], support_rows: Sequence[Dict[str, bool]], cols: int) -> Dict[str, int]:
    counts: Dict[str, int] = {}
    for platform in PLATFORMS:
        lines = []
        for index, (seq, _png) in enumerate(entries):
            if not support_rows[index][platform]:
                continue
            row, col = divmod(index, cols)
            lines.append(f"{row + 1}.{col + 1}={seq}")
        target = out_dir / f"{platform}.txt"
        target.write_text("\n".join(lines) + "\n", encoding="utf-8")
        counts[platform] = len(lines)
    return counts


# --------------------------------------------------------------------------- #
# main
# --------------------------------------------------------------------------- #

def main() -> int:
    ap = argparse.ArgumentParser(description="Build the Universal Emoji resource pack + platform tables")
    ap.add_argument("--assets-dir", type=Path, default=None, help="pre-extracted 72x72 twemoji directory")
    ap.add_argument("--cache", type=Path, default=Path(".cache"))
    ap.add_argument("--out", type=Path, default=Path("dist"))
    ap.add_argument("--cell", type=int, default=DEFAULT_CELL)
    ap.add_argument("--cols", type=int, default=DEFAULT_COLS)
    ap.add_argument("--ascent", type=int, default=DEFAULT_ASCENT)
    ap.add_argument("--height", type=int, default=DEFAULT_HEIGHT)
    ap.add_argument("--windows-max-emoji", type=float, default=13.0,
                    help="emoji version Microsoft is assumed to ship")
    ap.add_argument("--skip-legacy", action="store_true", help="only build the 1.20+ pack")
    args = ap.parse_args()

    started = time.time()
    cache = args.cache.resolve()
    out_dir = args.out.resolve()
    cache.mkdir(parents=True, exist_ok=True)
    out_dir.mkdir(parents=True, exist_ok=True)

    assets = ensure_twemoji(cache, args.assets_dir)
    emoji_data = ensure_emoji_data(cache)

    entries, unique = collect_entries(assets)
    log(f"unique codepoint sequences: {unique}")

    sheet, rows, cols = build_sheet(entries, args.cell, args.cols)
    chars, total_cells = pua_chars(rows, cols, len(entries))
    log(f"sheet {sheet.width}x{sheet.height}px, grid {cols}x{rows} = {total_cells} cells")

    # sanity: every row must be the same length or the atlas warps
    assert len({len(line) for line in chars}) == 1, "chars rows must be equal length"

    template_modern = load_vanilla_template("modern", cache)
    modern_zip = write_pack(out_dir, "modern", sheet, chars, args.ascent, args.height,
                            MODERN_PACK_META, template_modern)
    log(f"modern pack -> {modern_zip}")

    legacy_zip = None
    if not args.skip_legacy:
        template_legacy = load_vanilla_template("legacy", cache)
        legacy_zip = write_pack(out_dir, "legacy", sheet, chars, args.ascent, args.height,
                                MODERN_PACK_META, template_legacy)
        log(f"legacy pack -> {legacy_zip}")

    # platform tables
    index = build_platform_index(emoji_data)
    overrides_path = Path(__file__).with_name("platform_overrides.json")
    overrides = json.loads(overrides_path.read_text(encoding="utf-8")) if overrides_path.exists() else {}

    support_rows = [
        apply_overrides(resolve_support(seq, index, args.windows_max_emoji), seq, overrides)
        for seq, _ in entries
    ]
    counts = write_txt(out_dir, entries, support_rows, cols)
    log(f"platform tables: {counts}")

    # manifest consumed by the plugin at build time / by CI
    modern_sha1 = sha1_of(modern_zip)
    manifest = {
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "twemoji": TWEMOJI_TAG,
        "assets": len(entries),
        "grid": {"cols": cols, "rows": rows, "cell": args.cell, "pua_total": total_cells},
        "font": {"ascent": args.ascent, "height": args.height},
        "pua_range": [f"U+{PUA_START:04X}", f"U+{PUA_START + total_cells - 1:04X}"],
        "packs": {
            "modern": {"file": modern_zip.name, "sha1": modern_sha1, "size": modern_zip.stat().st_size},
        },
        "platforms": counts,
    }
    if legacy_zip:
        manifest["packs"]["legacy"] = {
            "file": legacy_zip.name,
            "sha1": sha1_of(legacy_zip),
            "size": legacy_zip.stat().st_size,
        }

    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    (out_dir / "sha1.txt").write_text(modern_sha1 + "\n", encoding="utf-8")

    log("=" * 60)
    log(f"Universal_Emoji.zip  SHA-1 = {modern_sha1}")
    if legacy_zip:
        log(f"{legacy_zip.name}  SHA-1 = {manifest['packs']['legacy']['sha1']}")
    log("=" * 60)
    log(f"done in {time.time() - started:.1f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
