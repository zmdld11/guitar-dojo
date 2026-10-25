"""figure_gen 入口：生成 docs/画图提示词.md 里的插图。"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from figure_gen import figures, sourced  # noqa: E402

OUT_DIR = Path(__file__).resolve().parents[2] / "app/src/main/assets/figures"
REGISTRY = {**figures.REGISTRY, **sourced.SOURCED}


def main():
    ap = argparse.ArgumentParser(description="生成 guitar-dojo 教学插图")
    ap.add_argument("--only", nargs="*", help="只生成指定编号，如 a1 d1")
    ap.add_argument("--out", default=str(OUT_DIR), help="输出目录")
    args = ap.parse_args()

    out = Path(args.out)
    names = args.only or [k for k in REGISTRY if not k.startswith("_")]
    for key in names:
        fn = REGISTRY.get(key.lower())
        if fn is None:
            print(f"! 未知编号: {key}")
            continue
        path = fn(out)
        print(f"✓ {key}: {path}")


if __name__ == "__main__":
    main()
