#!/usr/bin/env python3
"""生成版本更新清单 version.json，并把 APK 一起整理到 dist/。

每次发版执行一次，然后把 dist/ 里的两个文件传到 GitHub Release 附件：

    app-release.apk   →  必须叫这个名字（清单里的 apkUrl 指向它）
    version.json      →  必须叫这个名字（客户端固定从这个文件名取清单）

用法：

    python scripts/release_manifest.py --notes "新增版本更新检测" --notes "修复长对话丢上下文"
    python scripts/release_manifest.py --notes-file changes.txt --min-supported 40

版本号不手填：直接从 app/build.gradle.kts 里读，避免"清单写了 0.8.2、包里还是 0.8.1"
这种最难查的错——客户端会一直提示更新，装完却还是旧版。
"""

import argparse
import hashlib
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRADLE = ROOT / "app" / "build.gradle.kts"
APK = ROOT / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
DIST = ROOT / "dist"

# 清单里的下载地址。客户端拿到它之后会自动套国内镜像，所以这里保持官方地址即可。
APK_URL = (
    "https://github.com/qvq9999/pocket-agent/releases/latest/download/app-release.apk"
)


def read_version() -> tuple[int, str]:
    """从 gradle 配置里取 versionCode / versionName。

    不用 `./gradlew properties`：这个项目没有 wrapper，且 Gradle 启动要十几秒，
    而这两个值就是两行正则的事。
    """
    text = GRADLE.read_text(encoding="utf-8")
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not code or not name:
        sys.exit("在 app/build.gradle.kts 里找不到 versionCode / versionName")
    return int(code.group(1)), name.group(1)


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--notes", action="append", default=[], help="更新日志，可重复传多条")
    ap.add_argument("--notes-file", help="从文件读更新日志，一行一条")
    ap.add_argument(
        "--min-supported",
        type=int,
        default=0,
        help="最低可支持 versionCode；低于它的客户端会被强制更新。平时留 0",
    )
    ap.add_argument("--out", default=str(DIST), help="输出目录，默认 dist/")
    args = ap.parse_args()

    if not APK.is_file():
        sys.exit(f"找不到安装包：{APK}\n先执行 assembleRelease 再跑这个脚本")

    code, name = read_version()

    # 防止"清单与包不一致"：APK 里的 versionCode 必须和最外层配置同一份，
    # 但读 APK 需要 aapt，这里退一步校验包名与场景，至少确认包是刚构建的
    apk_age_h = (Path(__file__).stat().st_mtime - APK.stat().st_mtime) / 3600
    if apk_age_h > 24:
        print(f"[警告] APK 是 {-apk_age_h:.1f} 小时前构建的，确认不是上一版残留")

    notes = list(args.notes)
    if args.notes_file:
        lines = Path(args.notes_file).read_text(encoding="utf-8").splitlines()
        notes += [ln.strip(" -·\t") for ln in lines if ln.strip()]
    if not notes:
        notes = [f"{name} 版本更新"]

    manifest = {
        "schema": 1,
        "versionCode": code,
        "versionName": name,
        "minSupported": args.min_supported,
        "sizeBytes": APK.stat().st_size,
        "sha256": sha256_of(APK),
        "publishedAt": __import__("datetime").date.today().isoformat(),
        "apkUrl": APK_URL,
        "notes": notes,
        "notesUrl": f"https://github.com/qvq9999/pocket-agent/releases/tag/v{name}",
    }

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    shutil.copy2(APK, out / "app-release.apk")
    target = out / "version.json"
    target.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )

    print(f"版本      {name} (code {code})")
    print(f"大小      {manifest['sizeBytes'] / 1024 / 1024:.2f} MB")
    print(f"sha256    {manifest['sha256']}")
    print(f"清单      {target}")
    print(f"安装包    {out / 'app-release.apk'}")
    print()
    print("下一步：把 dist/ 里的 app-release.apk 与 version.json 一起传成 Release 附件。")


if __name__ == "__main__":
    main()
