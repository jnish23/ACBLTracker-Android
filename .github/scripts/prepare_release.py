"""Create the exact metadata consumed by the Android updater (stdlib only)."""
import hashlib
import json
import os
from pathlib import Path
import shutil

out = Path("dist")
out.mkdir(exist_ok=True)
apk = out / "ACBLTracker-android.apk"
shutil.copyfile("app/build/outputs/apk/release/app-release.apk", apk)
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
version = os.environ["VERSION_NAME"]
manifest = {
    "versionCode": int(os.environ["VERSION_CODE"]),
    "versionName": version,
    "apkUrl": f"https://github.com/{os.environ['GITHUB_REPOSITORY']}/releases/download/v{version}/{apk.name}",
    "sha256": digest,
    "size": apk.stat().st_size,
}
(out / "update.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
(out / (apk.name + ".sha256")).write_text(f"{digest}  {apk.name}\n", encoding="utf-8")
