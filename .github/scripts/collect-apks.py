#!/usr/bin/env python3
import argparse
import hashlib
import json
import shutil
import sys
from pathlib import Path


REQUIRED_ABIS = {"armeabi-v7a", "arm64-v8a", "x86", "x86_64", "universal"}


def detect_abi(element):
    abi = "universal"
    for item_filter in element.get("filters", []):
        if item_filter.get("filterType") == "ABI":
            abi = item_filter.get("value")
    return abi


def load_elements(metadata_path):
    try:
        metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise SystemExit(f"metadata file not found: {metadata_path}")
    except json.JSONDecodeError as error:
        raise SystemExit(f"metadata file is not valid JSON: {metadata_path}: {error}")

    elements = metadata.get("elements")
    if not isinstance(elements, list) or not elements:
        raise SystemExit(f"metadata file has no APK elements: {metadata_path}")
    return elements


def sha256_file(path):
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(
        description="Collect AGP APK outputs into deterministic ABI-specific release names.",
    )
    parser.add_argument("--metadata", required=True, type=Path)
    parser.add_argument("--apk-dir", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--build-type", required=True)
    args = parser.parse_args()

    elements = load_elements(args.metadata)
    args.output_dir.mkdir(parents=True, exist_ok=True)

    copied = {}
    for element in elements:
        abi = detect_abi(element)
        if abi not in REQUIRED_ABIS:
            raise SystemExit(f"unexpected APK ABI output: {abi}")
        if abi in copied:
            raise SystemExit(f"duplicate APK ABI output: {abi}")

        output_file = element.get("outputFile")
        if not output_file:
            raise SystemExit(f"APK element is missing outputFile for ABI {abi}")

        source = args.apk_dir / output_file
        if not source.is_file():
            raise SystemExit(f"APK file not found for ABI {abi}: {source}")

        destination = args.output_dir / f"StremioMobile-v{args.version_name}-{abi}-{args.build_type}.apk"
        shutil.copy2(source, destination)
        copied[abi] = destination
        print(f"{abi}: {source} -> {destination}")

    missing = REQUIRED_ABIS - set(copied)
    if missing:
        raise SystemExit(f"missing required APK ABI outputs: {', '.join(sorted(missing))}")

    checksum_path = args.output_dir / f"StremioMobile-v{args.version_name}-SHA256SUMS.txt"
    with checksum_path.open("w", encoding="utf-8", newline="\n") as checksum_file:
        for abi in sorted(copied):
            path = copied[abi]
            checksum_file.write(f"{sha256_file(path)}  {path.name}\n")
    print(f"checksums: {checksum_path}")


if __name__ == "__main__":
    sys.exit(main())
