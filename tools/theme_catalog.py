#!/usr/bin/env python3
"""Build the launcher's separately transferred theme catalog from themes/.

Usage: python tools/theme_catalog.py --output launcher/app/build/theme-catalog.zip
"""
import argparse
import json
import re
import sys
import zipfile
from pathlib import Path

NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,63}\Z")
IMAGE = re.compile(r"[A-Za-z0-9_.-]+\.(?:jpg|jpeg|png|webp)\Z", re.IGNORECASE)


def _files(folder):
    config = folder / "config.json"
    if not config.is_file() or config.is_symlink():
        return []
    try:
        parsed = json.loads(config.read_text(encoding="utf-8-sig"))
    except (OSError, ValueError) as error:
        raise ValueError(f"{config} is not valid JSON: {error}") from error
    if not isinstance(parsed, dict):
        raise ValueError(f"{config} must contain a JSON object")
    files = [config]
    single = folder / "wallpaper.jpg"
    if single.is_file() and not single.is_symlink():
        files.append(single)
    wallpapers = folder / "wallpapers"
    if wallpapers.is_dir() and not wallpapers.is_symlink():
        for path in sorted(wallpapers.iterdir(), key=lambda item: item.name.casefold()):
            if path.is_file() and not path.is_symlink() and (IMAGE.fullmatch(path.name) or path.name == "captions.txt"):
                files.append(path)
    return files


def appearance_overlay(config):
    """Keep only appearance keys; a selected theme must not replace the user's TV setup."""
    allowed = {
        "background_color", "sky_intensity", "wallpaper", "wallpaper_rotation_minutes", "wallpaper_order",
        "wallpaper_dim", "wallpaper_captions", "wallpaper_true_color", "wallpaper_moods", "dock_opacity",
        "dock_title", "dock_glass", "dock_even_padding", "dock_band", "cinematic_home", "ambient_glow",
        "app_glow", "glow_style", "glass_style", "focus_overshoot", "advanced_effects", "reduced_motion",
        "fade_rows", "clock_wallpaper", "clock_wallpaper_dim", "clock_glow", "cinematic_dots",
        "cinematic_white_focus", "cinematic_art", "cinematic_scrim", "festival", "seasonal_skin",
        "festival_until", "greeting", "accent_color", "wifi_status_color", "home_idle_clock_minutes",
        "icon_only_buttons", "icon_only_inputs",
    }
    return {key: value for key, value in config.items() if key in allowed}


def build_catalog(themes_dir, output):
    themes_dir = Path(themes_dir)
    output = Path(output)
    themes = []
    for folder in sorted(themes_dir.iterdir(), key=lambda item: item.name.casefold()):
        if not folder.is_dir() or folder.is_symlink() or not NAME.fullmatch(folder.name) or ".." in folder.name:
            continue
        files = _files(folder)
        if files:
            themes.append((folder.name, files))
    if not themes:
        raise ValueError(f"no valid themes found in {themes_dir}")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as catalog:
        for name, files in themes:
            for path in files:
                relative = path.relative_to(themes_dir / name).as_posix()
                if relative == "config.json":
                    config = json.loads(path.read_text(encoding="utf-8-sig"))
                    data = json.dumps(appearance_overlay(config), ensure_ascii=False,
                                      separators=(",", ":")).encode("utf-8")
                    catalog.writestr(f"{name}/{relative}", data)
                else:
                    catalog.write(path, f"{name}/{relative}")
    return len(themes)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--themes", type=Path, default=Path(__file__).resolve().parents[1] / "themes")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        count = build_catalog(args.themes, args.output)
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        print(f"cannot build theme catalog: {error}", file=sys.stderr)
        return 1
    print(f"Theme catalog: {count} themes, {args.output.stat().st_size} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
