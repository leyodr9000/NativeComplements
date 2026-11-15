"""Sign a verified fork APK with this fork's existing persistent key. Never generate replacement keys."""
from pathlib import Path
import argparse
import hashlib
import os
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FORK_CERTIFICATE = "c8c14badd2ebd5b04b15d943f602d72fe84c6dc24aebc9340c31d9be0f4595e0"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="Previously verified unsigned release APK")
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--sdk", type=Path, default=os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT"))
    args = parser.parse_args()
    if not args.sdk:
        parser.error("Set ANDROID_HOME/ANDROID_SDK_ROOT or --sdk")
    private = ROOT / ".local/fork-signing"
    key, password = private / "leyodr9000-nativecomplements.jks", private / "store-password"
    if not key.is_file() or not password.is_file():
        parser.error("Persistent fork signing key is absent. Restore its protected backup; do not generate a temporary replacement.")
    if not args.apk.is_file() or args.out.exists() or args.apk.resolve() == args.out.resolve():
        parser.error("Input must exist and output must be a new, different APK path")
    java = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")) if os.environ.get("JAVA_HOME") else "java"
    signer = args.sdk / "build-tools/36.1.0/lib/apksigner.jar"
    env = os.environ.copy()
    env["NC_SIGNING_PASSWORD"] = password.read_text().strip()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([java, "-jar", str(signer), "sign", "--ks", str(key), "--ks-key-alias", "nativecomplements-fork",
                    "--ks-pass", "env:NC_SIGNING_PASSWORD", "--key-pass", "env:NC_SIGNING_PASSWORD",
                    "--v1-signing-enabled", "false", "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
                    "--v4-signing-enabled", "false", "--out", str(args.out), str(args.apk)], env=env, check=True)
    verified = subprocess.run([java, "-jar", str(signer), "verify", "--min-sdk-version", "34", "--verbose", "--print-certs", str(args.out)],
                              capture_output=True, text=True, check=True)
    found = re.search(r"Signer #1 certificate SHA-256 digest: ([a-f0-9]+)", verified.stdout)
    if not found or found[1] != FORK_CERTIFICATE:
        raise RuntimeError("Unexpected signing certificate; do not install or publish the output")
    with zipfile.ZipFile(args.apk) as source, zipfile.ZipFile(args.out) as signed:
        assert signed.testzip() is None, "APK CRC failure"
        for name in source.namelist():
            assert source.read(name) == signed.read(name), f"Source entry changed during signing: {name}"
        assert not any(n.endswith((".jks", ".keystore", ".p12", ".pfx")) for n in signed.namelist()), "Private material in APK"
    print(verified.stdout)
    print(f"APK={args.out}")
    print(f"BYTES={args.out.stat().st_size}")
    print(f"SHA256={hashlib.sha256(args.out.read_bytes()).hexdigest()}")
    print("Verified persistent fork certificate; original APK entry bytes unchanged. No install or reboot performed.")


if __name__ == "__main__":
    main()
